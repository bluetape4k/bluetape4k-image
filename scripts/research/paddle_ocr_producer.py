from __future__ import annotations

"""Fail-closed CLI for the Issue #638 PaddleOCR trusted producer."""

import argparse
import datetime
import hashlib
import logging
import os
import re
import select
import shutil
import signal
import stat
import subprocess
import sys
import tarfile
import time
from collections.abc import Iterator, Sequence
from contextlib import contextmanager
from pathlib import Path, PurePosixPath
from types import FrameType
from typing import Any
from urllib.parse import quote, urlsplit

from paddle_ocr_producer_lib.contracts import (
    AttemptIdentity,
    ProducerValidationError,
    exact_object,
    jcs_bytes,
    load_json_bytes,
    load_jsonl_bytes,
    require_sha256,
    sha256_hex,
    validate_input_lock,
)
from paddle_ocr_producer_lib.evidence import (
    MaterializationLimits,
    create_evidence_oci_layout,
    validate_attestation_identity,
    validate_evidence_file_manifest,
    validate_ledger_fragment,
    validate_oci_handoff,
    validate_producer_evidence,
    validate_release_digests,
    validate_same_run_artifact,
    verify_public_evidence,
)
from paddle_ocr_producer_lib.filesystem import (
    ARCHIVE_LIMITS,
    ORAS_LINUX_AMD64_SHA256,
    ORAS_LINUX_AMD64_URL,
    ORAS_VERSION,
    bootstrap_oras_archive,
    canonical_tree_manifest,
    extract_archive,
    fetch_to_regular_file,
    tree_sha256,
    verify_regular_file,
)
from paddle_ocr_producer_lib.lifecycle import (
    STATUS_CONTRACT,
    acknowledge_incident,
    append_revocations,
    build_producer_result,
    close_incident,
    finalize_attempt,
    merge_cleanup_fragments,
    plan_known_good_rollback,
    validate_attempt,
    validate_cleanup_aggregate,
    validate_cleanup_fragment,
    validate_emergency_receipt,
    validate_reconcile_expectations,
    validate_reconciliation,
    validate_revocations,
)
from paddle_ocr_producer_lib.registry import (
    HttpStatusError,
    run_with_retry,
    select_dispatched_run,
    select_exact_version,
)

MAX_DOCUMENT_BYTES = 1024 * 1024
LOGGER = logging.getLogger("paddle_ocr_producer")
DOCKERFILE_CONTRACT = b"""ARG BASE_IMAGE
FROM ${BASE_IMAGE}
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
WORKDIR /app
COPY wheelhouse/ /wheelhouse/
COPY requirements.cpu.lock.txt /app/requirements.cpu.lock.txt
RUN python -m pip install --disable-pip-version-check --no-index --no-deps --require-hashes --find-links=/wheelhouse -r /app/requirements.cpu.lock.txt
COPY models/ /opt/bluetape4k/paddleocr/models/
COPY model-manifest.json /opt/bluetape4k/paddleocr/model-manifest.json
COPY ocr-pipeline.yaml /opt/bluetape4k/paddleocr/ocr-pipeline.yaml
COPY legal-inventory.json /opt/bluetape4k/paddleocr/legal-inventory.json
COPY service.py /app/service.py
COPY bin/bluetape4k-paddleocr-service /opt/bluetape4k/bin/bluetape4k-paddleocr-service
USER 65532:65532
EXPOSE 8080
ENTRYPOINT [\"/opt/bluetape4k/bin/bluetape4k-paddleocr-service\"]
CMD [\"--host\", \"127.0.0.1\", \"--port\", \"8080\"]
"""
RECONCILE_PRIOR_STATUSES = {
    "PUBLISHED_UNVERIFIED",
    "PROMOTING",
    "RELEASE_UNVERIFIED",
    "QUARANTINE_PENDING",
    "QUARANTINED",
    "INTERRUPTED",
}
SUMMARY_KEYS = {
    "schemaVersion", "attemptId", "jobStatus", "producerStatus", "exitCode",
    "lastCompletedStage", "documentDigests", "imageDigest", "evidenceDigest",
    "retryCount", "cleanupVerified", "incidentCandidate",
}


class ProducerUsageError(ValueError):
    pass


class ProducerBlockedError(ValueError):
    pass


class ProducerLegalError(ValueError):
    pass


class ProducerRejectedError(ValueError):
    pass


class ProducerCancelled(BaseException):
    pass


class ProducerInterrupted(BaseException):
    pass


class StrictArgumentParser(argparse.ArgumentParser):
    def error(self, message: str) -> None:
        raise ProducerUsageError("invalid command arguments")


@contextmanager
def installed_signal_handlers() -> Iterator[None]:
    previous: dict[int, Any] = {}

    def handle_interrupt(signum: int, _: FrameType | None) -> None:
        if signum == signal.SIGINT:
            raise ProducerCancelled()
        raise ProducerInterrupted()

    for signum in (signal.SIGINT, signal.SIGTERM):
        previous[signum] = signal.getsignal(signum)
        signal.signal(signum, handle_interrupt)
    try:
        yield
    finally:
        for signum, handler in previous.items():
            signal.signal(signum, handler)


def _read_regular_bytes(path: Path, limit: int = MAX_DOCUMENT_BYTES) -> bytes:
    flags = os.O_RDONLY | getattr(os, "O_CLOEXEC", 0) | getattr(os, "O_NOFOLLOW", 0)
    try:
        descriptor = os.open(path, flags)
    except OSError as exc:
        raise ProducerBlockedError("input document is unavailable") from exc
    try:
        metadata = os.fstat(descriptor)
        if not stat.S_ISREG(metadata.st_mode):
            raise ProducerBlockedError("input document must be a regular file")
        if metadata.st_size > limit:
            raise ProducerBlockedError("input document exceeds byte limit")
        chunks: list[bytes] = []
        total = 0
        while True:
            chunk = os.read(descriptor, min(64 * 1024, limit + 1 - total))
            if not chunk:
                break
            total += len(chunk)
            if total > limit:
                raise ProducerBlockedError("input document exceeds byte limit")
            chunks.append(chunk)
        return b"".join(chunks)
    finally:
        os.close(descriptor)


def _load_canonical_document(
    path: Path, *, contract_error: bool = False
) -> tuple[dict[str, Any], bytes]:
    raw = _read_regular_bytes(path)
    try:
        document = load_json_bytes(raw, MAX_DOCUMENT_BYTES)
    except ProducerValidationError as exc:
        if contract_error:
            raise ProducerValidationError("input document violates the strict JSON contract") from exc
        raise ProducerBlockedError("input document violates the strict JSON contract") from exc
    if jcs_bytes(document) != raw:
        if contract_error:
            raise ProducerValidationError("input document must use canonical JSON without a trailing newline")
        raise ProducerBlockedError("input document must use canonical JSON without a trailing newline")
    return document, raw


def _policy_hosts(value: Any) -> set[str]:
    policy = exact_object(
        value,
        required={
            "schemaVersion",
            "repositories",
            "workflows",
            "refs",
            "actors",
            "runnerEnvironments",
            "oidcIssuers",
            "audiences",
            "hosts",
        },
    )
    if policy["schemaVersion"] != 1:
        raise ProducerBlockedError("trust policy schemaVersion must be 1")
    for name in (
        "repositories",
        "workflows",
        "refs",
        "actors",
        "runnerEnvironments",
        "oidcIssuers",
        "audiences",
        "hosts",
    ):
        values = policy[name]
        if not isinstance(values, list) or not values or any(
            not isinstance(item, str) or not item or "PENDING" in item.upper()
            for item in values
        ):
            raise ProducerBlockedError(f"trust policy {name} must be a non-empty resolved array")
        if len(values) != len(set(values)):
            raise ProducerBlockedError(f"trust policy {name} must be unique")
    return set(policy["hosts"])


def _validate_legal_inventory(value: Any) -> None:
    inventory = exact_object(value, required={"schemaVersion", "components"})
    if inventory["schemaVersion"] != 1:
        raise ProducerLegalError("legal inventory schemaVersion must be 1")
    components = inventory["components"]
    if not isinstance(components, list) or not components:
        raise ProducerLegalError("legal inventory must contain components")
    keys = {
        "id",
        "licenseExpression",
        "licenseSourceUrl",
        "licenseSha256",
        "notice",
        "noticeSha256",
    }
    ids: set[str] = set()
    for component in components:
        item = exact_object(component, required=keys)
        for name in ("id", "licenseExpression", "licenseSourceUrl", "notice"):
            value = item[name]
            if not isinstance(value, str) or not value or "PENDING" in value.upper():
                raise ProducerLegalError(f"legal inventory {name} is incomplete")
        require_sha256(item["licenseSha256"], "legal inventory licenseSha256")
        require_sha256(item["noticeSha256"], "legal inventory noticeSha256")
        if sha256_hex(item["notice"].encode("utf-8")) != item["noticeSha256"]:
            raise ProducerLegalError("legal inventory notice sha256 differs")
        if item["id"] in ids:
            raise ProducerLegalError("legal inventory component ids must be unique")
        ids.add(item["id"])


def _read_model_legal_file(root: Path, relative: Any) -> bytes:
    if not isinstance(relative, str) or not relative or "\\" in relative:
        raise ProducerLegalError("model legal path is invalid")
    path = PurePosixPath(relative)
    if path.is_absolute() or any(part in ("", ".", "..") for part in path.parts):
        raise ProducerLegalError("model legal path escapes the input root")
    current = root
    for part in path.parts[:-1]:
        current = current / part
        if current.is_symlink() or not current.is_dir():
            raise ProducerLegalError("model legal parent must be a non-symlink directory")
    try:
        return _read_regular_bytes(current / path.parts[-1])
    except ProducerBlockedError as exc:
        raise ProducerLegalError("model legal file is unavailable") from exc


def _validate_model_legal_files(lock: dict[str, Any], root: Path) -> None:
    for model in lock["models"]:
        for path_field, sha_field in (
            ("licenseSourcePath", "licenseSha256"),
            ("noticePath", "noticeSha256"),
        ):
            raw = _read_model_legal_file(root, model[path_field])
            if sha256_hex(raw) != model[sha_field]:
                raise ProducerLegalError("model legal file sha256 differs")


def _hosts_from_lock(lock: dict[str, Any]) -> set[str]:
    hosts: set[str] = set()

    def collect(value: Any) -> None:
        if isinstance(value, dict):
            url = value.get("url")
            if isinstance(url, str):
                host = urlsplit(url).hostname
                if host:
                    hosts.add(host.lower().rstrip("."))
            for nested in value.values():
                collect(nested)
        elif isinstance(value, list):
            for nested in value:
                collect(nested)

    collect(lock)
    return hosts


def _atomic_write_new(path: Path, raw: bytes) -> str:
    if path.exists() or path.is_symlink():
        raise ProducerBlockedError("output already exists")
    path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    temporary = path.parent / ("." + path.name + ".partial")
    descriptor: int | None = None
    try:
        descriptor = os.open(
            temporary,
            os.O_WRONLY | os.O_CREAT | os.O_EXCL | getattr(os, "O_CLOEXEC", 0),
            0o600,
        )
        offset = 0
        while offset < len(raw):
            offset += os.write(descriptor, raw[offset:])
        os.fsync(descriptor)
        os.close(descriptor)
        descriptor = None
        os.link(temporary, path)
        temporary.unlink()
    finally:
        if descriptor is not None:
            os.close(descriptor)
        try:
            temporary.unlink()
        except FileNotFoundError:
            pass
    return sha256_hex(raw)


def _success(command: str, data: dict[str, Any]) -> dict[str, Any]:
    return {
        "schemaVersion": 1,
        "command": command,
        "status": "PASS",
        "data": data,
        "errorCode": "NONE",
        "errorMessage": None,
    }


def _failure(command: str, status: str, code: str, message: str) -> dict[str, Any]:
    return {
        "schemaVersion": 1,
        "command": command,
        "status": status,
        "data": {},
        "errorCode": code,
        "errorMessage": message[:512],
    }


def _validate_inputs(args: argparse.Namespace) -> dict[str, Any]:
    policy, policy_raw = _load_canonical_document(args.policy)
    try:
        hosts = _policy_hosts(policy)
    except ProducerValidationError as exc:
        raise ProducerBlockedError("trust policy violates its contract") from exc
    lock, lock_raw = _load_canonical_document(args.lock)
    try:
        validated_lock = validate_input_lock(lock, allowed_hosts=hosts)
    except ProducerValidationError as exc:
        raise ProducerBlockedError("input lock violates its contract") from exc
    _validate_model_legal_files(validated_lock, args.lock.parent)
    legal, legal_raw = _load_canonical_document(args.legal)
    try:
        _validate_legal_inventory(legal)
    except ProducerValidationError as exc:
        raise ProducerLegalError("legal inventory violates its contract") from exc
    legal_sha256 = sha256_hex(legal_raw)
    if lock["legalInventorySha256"] != legal_sha256:
        raise ProducerLegalError("legal inventory sha256 differs from input lock")
    return _success(
        "validate-inputs",
        {
            "inputLockSha256": sha256_hex(lock_raw),
            "legalInventorySha256": legal_sha256,
            "trustPolicySha256": sha256_hex(policy_raw),
        },
    )


def validate_trust_context(
    policy: Any,
    repository: Any,
    workflow: Any,
    ref: Any,
    actor: Any,
    runner_environment: Any,
    oidc_issuer: Any,
    audience: Any,
) -> tuple[str, ...]:
    try:
        _policy_hosts(policy)
    except ProducerValidationError as exc:
        raise ProducerBlockedError("trust policy violates its contract") from exc
    expected = (
        ("repositories", repository),
        ("workflows", workflow),
        ("refs", ref),
        ("actors", actor),
        ("runnerEnvironments", runner_environment),
        ("oidcIssuers", oidc_issuer),
        ("audiences", audience),
    )
    for field, value in expected:
        if not isinstance(value, str) or value not in policy[field]:
            raise ProducerBlockedError(f"trust context {field} is not allowlisted")
    return tuple(value for _, value in expected)


def _validate_trust_context(args: argparse.Namespace) -> dict[str, Any]:
    policy, _ = _load_canonical_document(args.policy)
    values = validate_trust_context(
        policy,
        args.repository,
        args.workflow,
        args.ref,
        args.actor,
        args.runner_environment,
        args.oidc_issuer,
        args.audience,
    )
    validated_inputs = _validate_inputs(args)["data"]
    return _success(
        "validate-trust-context",
        {
            **validated_inputs,
            "repository": values[0],
            "workflow": values[1],
            "ref": values[2],
            "actor": values[3],
            "runnerEnvironment": values[4],
            "oidcIssuer": values[5],
            "audience": values[6],
        },
    )


def _input_value(args: argparse.Namespace) -> dict[str, Any]:
    lock, lock_raw = _load_canonical_document(args.lock)
    try:
        validated = validate_input_lock(lock, allowed_hosts=_hosts_from_lock(lock))
    except ProducerValidationError as exc:
        raise ProducerBlockedError("input lock violates its contract") from exc
    derived = [package for package in validated["packages"] if "derivedSourceId" in package]
    if len(derived) != 1:
        raise ProducerBlockedError("sourceDateEpoch requires one derived source package")
    package = derived[0]
    document = _success(
        "input-value",
        {
            "sourceDateEpoch": package["buildToolchain"]["sourceDateEpoch"],
            "derivedPackageId": package["id"],
            "inputLockSha256": sha256_hex(lock_raw),
        },
    )
    raw = jcs_bytes(document)
    _atomic_write_new(args.output, raw)
    return document


def verify_build_base(reference: Any, input_lock: Any) -> dict[str, Any]:
    """Return the validated base only when the caller uses the lock's exact reference."""
    try:
        validated = validate_input_lock(
            input_lock,
            allowed_hosts=_hosts_from_lock(input_lock),
        )
    except (ProducerValidationError, TypeError) as exc:
        raise ProducerValidationError("input lock violates its contract") from exc
    base = validated["baseImage"]
    if not isinstance(reference, str) or reference != base["reference"]:
        raise ProducerValidationError("build base must equal the input lock reference")
    repository, separator, digest = reference.partition("@")
    if (
        not separator
        or not repository
        or digest != base["indexDigest"]
        or base["os"] + "/" + base["architecture"] != validated["targetPlatform"]
        or base["variant"] is not None
    ):
        raise ProducerValidationError("build base digest or platform differs from the input lock")
    return base


def _verify_dockerfile(args: argparse.Namespace) -> dict[str, Any]:
    lock, _ = _load_canonical_document(args.input_lock)
    base = verify_build_base(lock["baseImage"]["reference"], lock)
    raw = _read_regular_bytes(args.dockerfile, MAX_DOCUMENT_BYTES)
    if raw != DOCKERFILE_CONTRACT:
        raise ProducerBlockedError("Dockerfile differs from the immutable image contract")
    return _success(
        "verify-dockerfile",
        {
            "baseReference": base["reference"],
            "baseDigests": {
                "index": base["indexDigest"],
                "platform": base["platformDigest"],
                "config": base["configDigest"],
            },
            "entrypoint": ["/opt/bluetape4k/bin/bluetape4k-paddleocr-service"],
            "command": ["--host", "127.0.0.1", "--port", "8080"],
        },
    )


def validate_dispatch_inputs(
    mode: Any,
    resume_attempt_id: Any,
    expected_prior_status: Any,
    expected_input_lock_sha256: Any,
    expected_staging_digest: Any,
    expected_release_digest: Any,
    expected_evidence_digest: Any,
) -> dict[str, str | None]:
    fields = {
        "resumeAttemptId": resume_attempt_id,
        "expectedPriorStatus": expected_prior_status,
        "expectedInputLockSha256": expected_input_lock_sha256,
        "expectedStagingDigest": expected_staging_digest,
        "expectedReleaseDigest": expected_release_digest,
        "expectedEvidenceDigest": expected_evidence_digest,
    }
    if mode == "PRODUCE":
        if any(value != "NONE" for value in fields.values()):
            raise ProducerValidationError("PRODUCE forbids reconcile input values")
        return {name: None for name in fields}
    if mode != "RECONCILE":
        raise ProducerValidationError("dispatch mode must be PRODUCE or RECONCILE")
    if not isinstance(resume_attempt_id, str) or re.fullmatch(r"[1-9][0-9]*\.[1-9][0-9]*", resume_attempt_id) is None:
        raise ProducerValidationError("resume attempt id is invalid")
    if expected_prior_status not in RECONCILE_PRIOR_STATUSES:
        raise ProducerValidationError("reconcile prior status is invalid")
    require_sha256(expected_input_lock_sha256, "expected input lock sha256")

    def require_digest(value: Any, label: str, *, nullable: bool = False) -> str | None:
        if nullable and value == "NONE":
            return None
        if not isinstance(value, str) or not value.startswith("sha256:"):
            raise ProducerValidationError(f"{label} must be a digest or exact NONE sentinel")
        require_sha256(value[7:], label)
        return value

    return {
        "resumeAttemptId": resume_attempt_id,
        "expectedPriorStatus": expected_prior_status,
        "expectedInputLockSha256": expected_input_lock_sha256,
        "expectedStagingDigest": require_digest(
            expected_staging_digest, "staging digest", nullable=True
        ),
        "expectedReleaseDigest": require_digest(
            expected_release_digest, "release digest", nullable=True
        ),
        "expectedEvidenceDigest": require_digest(
            expected_evidence_digest, "evidence digest", nullable=True
        ),
    }


def _validate_summary_document(value: Any) -> dict[str, Any]:
    document = exact_object(value, required=SUMMARY_KEYS)
    if document["schemaVersion"] != 1:
        raise ProducerValidationError("summary schemaVersion must be 1")
    if not isinstance(document["attemptId"], str) or re.fullmatch(
        r"[1-9][0-9]*\.[1-9][0-9]*", document["attemptId"]
    ) is None:
        raise ProducerValidationError("summary attemptId is invalid")
    if document["producerStatus"] not in STATUS_CONTRACT:
        raise ProducerValidationError("summary producerStatus is invalid")
    if document["jobStatus"] not in {"success", "failure", "cancelled"}:
        raise ProducerValidationError("summary jobStatus is invalid")
    if type(document["exitCode"]) is not int or not 0 <= document["exitCode"] <= 255:
        raise ProducerValidationError("summary exitCode is invalid")
    stages = {stage for contract in STATUS_CONTRACT.values() for stage in contract.allowed_stages}
    if document["lastCompletedStage"] not in stages:
        raise ProducerValidationError("summary lastCompletedStage is invalid")
    digests = document["documentDigests"]
    if not isinstance(digests, dict) or not digests or len(digests) > 16:
        raise ProducerValidationError("summary documentDigests is invalid")
    for name, digest in digests.items():
        if not isinstance(name, str) or re.fullmatch(r"[a-z][a-zA-Z0-9-]{0,63}", name) is None:
            raise ProducerValidationError("summary document digest name is invalid")
        require_sha256(digest, "summary document digest")
    for name in ("imageDigest", "evidenceDigest"):
        digest = document[name]
        if digest is not None:
            if not isinstance(digest, str) or not digest.startswith("sha256:"):
                raise ProducerValidationError(f"summary {name} is invalid")
            require_sha256(digest[7:], f"summary {name}")
    if type(document["retryCount"]) is not int or not 0 <= document["retryCount"] <= 100:
        raise ProducerValidationError("summary retryCount is invalid")
    if type(document["cleanupVerified"]) is not bool:
        raise ProducerValidationError("summary cleanupVerified is invalid")
    incident = document["incidentCandidate"]
    if incident is not None and (
        not isinstance(incident, str)
        or re.fullmatch(r"https://github\.com/bluetape4k/bluetape4k-image/issues/[1-9][0-9]*", incident) is None
    ):
        raise ProducerValidationError("summary incidentCandidate is invalid")
    return document


def write_step_summary(path: Path, value: Any) -> dict[str, Any]:
    document = _validate_summary_document(value)
    digest_lines = "\n".join(
        f"  - `{name}`: `{digest}`" for name, digest in sorted(document["documentDigests"].items())
    )
    payload = (
        "## PaddleOCR producer\n\n"
        f"- Attempt: `{document['attemptId']}`\n"
        f"- Job status: `{document['jobStatus']}`\n"
        f"- Status / exit: `{document['producerStatus']}` / `{document['exitCode']}`\n"
        f"- Last stage: `{document['lastCompletedStage']}`\n"
        f"- Image digest: `{document['imageDigest'] or 'NONE'}`\n"
        f"- Evidence digest: `{document['evidenceDigest'] or 'NONE'}`\n"
        f"- Retry count: `{document['retryCount']}`\n"
        f"- Cleanup verified: `{str(document['cleanupVerified']).lower()}`\n"
        f"- Incident candidate: `{document['incidentCandidate'] or 'NONE'}`\n"
        "- Document digests:\n"
        f"{digest_lines}\n"
    ).encode()
    if len(payload) > 16 * 1024:
        raise ProducerValidationError("summary payload exceeds byte limit")
    flags = os.O_WRONLY | os.O_APPEND | getattr(os, "O_CLOEXEC", 0) | getattr(os, "O_NOFOLLOW", 0)
    try:
        descriptor = os.open(path, flags)
    except OSError as exc:
        raise ProducerBlockedError("step summary is unavailable") from exc
    try:
        metadata = os.fstat(descriptor)
        if not stat.S_ISREG(metadata.st_mode) or metadata.st_nlink != 1:
            raise ProducerBlockedError("step summary must be a unique regular file")
        if metadata.st_size + len(payload) > MAX_DOCUMENT_BYTES:
            raise ProducerBlockedError("step summary exceeds byte limit")
        written = 0
        while written < len(payload):
            written += os.write(descriptor, payload[written:])
        os.fsync(descriptor)
    finally:
        os.close(descriptor)
    raw = _read_regular_bytes(path)
    if not raw.endswith(payload):
        raise ProducerBlockedError("step summary read-back differs")
    return {
        "summarySha256": sha256_hex(raw),
        "documentDigests": dict(document["documentDigests"]),
    }


def _validate_dispatch(args: argparse.Namespace) -> dict[str, Any]:
    result = validate_dispatch_inputs(
        args.mode,
        args.resume_attempt_id,
        args.expected_prior_status,
        args.expected_input_lock_sha256,
        args.expected_staging_digest,
        args.expected_release_digest,
        args.expected_evidence_digest,
    )
    return _success("validate-dispatch", result)


def _write_step_summary(args: argparse.Namespace) -> dict[str, Any]:
    document, _ = _load_canonical_document(args.input, contract_error=True)
    return _success("write-step-summary", write_step_summary(args.summary, document))


def _validate_requirements_lock(raw: bytes, packages: list[dict[str, Any]]) -> None:
    if len(raw) > 8 * 1024 * 1024:
        raise ProducerBlockedError("requirements lock exceeds byte limit")
    if not raw.endswith(b"\n") or b"\r" in raw:
        raise ProducerBlockedError("requirements lock must use LF-terminated UTF-8 lines")
    try:
        lines = raw.decode("utf-8", errors="strict").splitlines()
    except UnicodeDecodeError as exc:
        raise ProducerBlockedError("requirements lock must be valid UTF-8") from exc
    actual: set[tuple[str, str, str]] = set()
    pattern = re.compile(
        r"\A([A-Za-z0-9][A-Za-z0-9._-]*)==([^\s]+) --hash=sha256:([0-9a-f]{64})\Z"
    )
    for line in lines:
        payload = line.partition("#")[0].rstrip()
        if not payload:
            continue
        match = pattern.fullmatch(payload)
        if match is None:
            raise ProducerBlockedError("requirements lock line is not exactly hash-pinned")
        name = re.sub(r"[-_.]+", "-", match.group(1)).lower()
        identity = (name, match.group(2), match.group(3))
        if identity in actual:
            raise ProducerBlockedError("requirements lock contains a duplicate package")
        actual.add(identity)
    expected = {
        (
            re.sub(r"[-_.]+", "-", package["name"]).lower(),
            package["version"],
            package["sha256"],
        )
        for package in packages
    }
    if actual != expected:
        raise ProducerBlockedError("requirements lock differs from package input lock")


def _accept_resolved_inputs(args: argparse.Namespace) -> dict[str, Any]:
    root = args.resolution_root
    if root.is_symlink() or not root.is_dir():
        raise ProducerBlockedError("resolution root must be a non-symlink directory")
    lock, lock_raw = _load_canonical_document(root / "producer-input-candidate.json")
    try:
        validated = validate_input_lock(lock, allowed_hosts=_hosts_from_lock(lock))
    except ProducerValidationError as exc:
        raise ProducerBlockedError("resolved input lock violates its contract") from exc
    legal, legal_raw = _load_canonical_document(root / "legal-inventory.json")
    try:
        _validate_legal_inventory(legal)
    except ProducerValidationError as exc:
        raise ProducerLegalError("resolved legal inventory violates its contract") from exc
    if validated["legalInventorySha256"] != sha256_hex(legal_raw):
        raise ProducerLegalError("resolved legal inventory sha256 differs")
    _validate_model_legal_files(validated, root)
    requirements_raw = _read_regular_bytes(
        root / "requirements.cpu.lock.txt", 8 * 1024 * 1024
    )
    _validate_requirements_lock(requirements_raw, validated["packages"])

    destinations = (args.input_lock, args.requirements_lock, args.legal_inventory)
    if any(path.exists() or path.is_symlink() for path in destinations):
        raise ProducerBlockedError("accepted input destination already exists")
    input_sha = _atomic_write_new(args.input_lock, lock_raw)
    requirements_sha = _atomic_write_new(args.requirements_lock, requirements_raw)
    legal_sha = _atomic_write_new(args.legal_inventory, legal_raw)
    return _success(
        "accept-resolved-inputs",
        {
            "inputLockPath": str(args.input_lock),
            "inputLockSha256": input_sha,
            "requirementsPath": str(args.requirements_lock),
            "requirementsSha256": requirements_sha,
            "legalPath": str(args.legal_inventory),
            "legalSha256": legal_sha,
        },
    )


def _verify_source_reproducibility(args: argparse.Namespace) -> dict[str, Any]:
    lock, _ = _load_canonical_document(args.input_lock)
    try:
        validated = validate_input_lock(lock, allowed_hosts=_hosts_from_lock(lock))
    except ProducerValidationError as exc:
        raise ProducerBlockedError("input lock violates its contract") from exc
    derived = [package for package in validated["packages"] if "derivedSourceId" in package]
    if len(derived) != 1:
        raise ProducerBlockedError("source reproducibility requires one derived package")
    package = derived[0]
    hosts = _hosts_from_lock(validated)
    registry = [package for package in validated["packages"] if "url" in package]
    registry_wheelhouse = args.registry_wheelhouse
    if args.prebuilt_wheel:
        if len(args.prebuilt_wheel) != 2:
            raise ProducerBlockedError(
                "source reproducibility requires two independent wheel builds"
            )
        first, second = args.prebuilt_wheel
    else:
        if args.work_root.exists() or args.work_root.is_symlink():
            raise ProducerBlockedError("source reproducibility work root already exists")
        args.work_root.mkdir(parents=True, mode=0o700)
        source = next(
            item for item in validated["sources"] if item["id"] == package["derivedSourceId"]
        )
        source_archive = args.work_root / "source.tar.gz"
        try:
            fetch_to_regular_file(
                source,
                source_archive,
                allowed_hosts=hosts,
            )
            source_root = args.work_root / "source"
            extract_archive(
                source_archive,
                source_root,
                ARCHIVE_LIMITS,
                strip_single_root=True,
            )
            tool_root = args.work_root / "build-tools"
            tool_root.mkdir(mode=0o700)
            for artifact in package["buildToolchain"]["artifacts"]:
                fetch_to_regular_file(
                    artifact,
                    tool_root / artifact["filename"],
                    allowed_hosts=hosts,
                )
            builds: list[Path] = []
            environment_name = "SETUPTOOLS_SCM_PRETEND_VERSION_FOR_" + re.sub(
                r"[^A-Za-z0-9]", "_", package["name"]
            ).upper()
            platform_reference = (
                validated["baseImage"]["reference"].split("@", 1)[0]
                + "@"
                + validated["baseImage"]["platformDigest"]
            )
            command = (
                "python -m pip install --disable-pip-version-check --no-index "
                "--find-links=/tools /tools/*.whl >/tmp/build-tools.log && "
                "cp -a /source /tmp/source && "
                "python -m pip wheel --disable-pip-version-check --no-deps "
                "--no-build-isolation --wheel-dir /output /tmp/source >/tmp/build.log"
            )
            for build_number in (1, 2):
                build_root = args.work_root / f"build-{build_number}"
                build_root.mkdir(mode=0o700)
                completed = subprocess.run(
                    [
                        "docker",
                        "run",
                        "--rm",
                        "--platform",
                        validated["targetPlatform"],
                        "-v",
                        f"{source_root.resolve()}:/source:ro",
                        "-v",
                        f"{tool_root.resolve()}:/tools:ro",
                        "-v",
                        f"{build_root.resolve()}:/output",
                        "-e",
                        f"SOURCE_DATE_EPOCH={package['buildToolchain']['sourceDateEpoch']}",
                        "-e",
                        f"{environment_name}={package['version']}",
                        platform_reference,
                        "sh",
                        "-eu",
                        "-c",
                        command,
                    ],
                    stdin=subprocess.DEVNULL,
                    capture_output=True,
                    check=False,
                    timeout=5400,
                )
                if completed.returncode != 0:
                    raise ChildProcessError("derived wheel build failed")
                builds.append(build_root / package["filename"])
            first, second = builds
            if registry_wheelhouse is None:
                registry_wheelhouse = args.work_root / "registry-wheelhouse"
                registry_wheelhouse.mkdir(mode=0o700)
                for registry_package in registry:
                    fetch_to_regular_file(
                        registry_package,
                        registry_wheelhouse / registry_package["filename"],
                        allowed_hosts=hosts,
                    )
        except BaseException:
            shutil.rmtree(args.work_root, ignore_errors=True)
            raise
    try:
        for wheel in (first, second):
            verify_regular_file(
                wheel,
                expected_bytes=package["bytes"],
                expected_sha256=package["sha256"],
            )
    except ProducerValidationError as exc:
        raise ProducerRejectedError("derived wheel differs from its locked receipt") from exc
    if _read_regular_bytes(first, package["bytes"]) != _read_regular_bytes(
        second, package["bytes"]
    ):
        raise ProducerRejectedError("independent derived wheel builds differ")

    if registry and registry_wheelhouse is None:
        raise ProducerBlockedError("registry wheelhouse is required")
    sources: list[tuple[Path, dict[str, Any]]] = [(first, package)]
    if registry_wheelhouse is not None:
        if registry_wheelhouse.is_symlink() or not registry_wheelhouse.is_dir():
            raise ProducerBlockedError("registry wheelhouse must be a non-symlink directory")
        for registry_package in registry:
            source = registry_wheelhouse / registry_package["filename"]
            try:
                verify_regular_file(
                    source,
                    expected_bytes=registry_package["bytes"],
                    expected_sha256=registry_package["sha256"],
                )
            except ProducerValidationError as exc:
                raise ProducerRejectedError("registry wheel differs from its locked receipt") from exc
            sources.append((source, registry_package))

    if args.output_wheelhouse.exists() or args.output_wheelhouse.is_symlink():
        raise ProducerBlockedError("output wheelhouse already exists")
    args.output_wheelhouse.mkdir(parents=True, mode=0o700)
    try:
        for source, locked_package in sources:
            destination = args.output_wheelhouse / locked_package["filename"]
            with source.open("rb") as input_stream, destination.open("xb") as output_stream:
                shutil.copyfileobj(input_stream, output_stream, length=1024 * 1024)
        manifest = canonical_tree_manifest(args.output_wheelhouse)
    except BaseException:
        shutil.rmtree(args.output_wheelhouse, ignore_errors=True)
        raise
    return _success(
        "verify-source-reproducibility",
        {
            "wheelhousePath": str(args.output_wheelhouse),
            "wheelhouseSha256": sha256_hex(manifest),
            "sourceDateEpoch": package["buildToolchain"]["sourceDateEpoch"],
        },
    )


def _copy_regular_file(source: Path, destination: Path) -> None:
    destination.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    raw = _read_regular_bytes(source, max(source.stat().st_size, 1))
    _atomic_write_new(destination, raw)


def _parse_model_archives(values: list[str]) -> dict[str, Path]:
    result: dict[str, Path] = {}
    for value in values:
        role, separator, path = value.partition("=")
        if separator != "=" or role not in {"detector", "recognizer"} or not path:
            raise ProducerUsageError("invalid model archive binding")
        if role in result:
            raise ProducerUsageError("duplicate model archive binding")
        result[role] = Path(path)
    if set(result) != {"detector", "recognizer"}:
        raise ProducerUsageError("detector and recognizer archives are required")
    return result


def _stage_inputs(args: argparse.Namespace) -> dict[str, Any]:
    lock, lock_raw = _load_canonical_document(args.input_lock)
    try:
        validated = validate_input_lock(lock, allowed_hosts=_hosts_from_lock(lock))
    except ProducerValidationError as exc:
        raise ProducerBlockedError("input lock violates its contract") from exc
    legal, legal_raw = _load_canonical_document(args.legal)
    _validate_legal_inventory(legal)
    if validated["legalInventorySha256"] != sha256_hex(legal_raw):
        raise ProducerLegalError("legal inventory sha256 differs")
    _validate_model_legal_files(validated, args.input_lock.parent)
    requirements_raw = _read_regular_bytes(args.requirements_lock, 8 * 1024 * 1024)
    _validate_requirements_lock(requirements_raw, validated["packages"])
    if args.wheelhouse.is_symlink() or not args.wheelhouse.is_dir():
        raise ProducerBlockedError("wheelhouse must be a non-symlink directory")
    expected_wheels = {package["filename"] for package in validated["packages"]}
    actual_wheels = {
        path.name
        for path in args.wheelhouse.iterdir()
        if path.is_file() and not path.is_symlink()
    }
    if actual_wheels != expected_wheels:
        raise ProducerRejectedError("wheelhouse file inventory differs from input lock")
    for package in validated["packages"]:
        try:
            verify_regular_file(
                args.wheelhouse / package["filename"],
                expected_bytes=package["bytes"],
                expected_sha256=package["sha256"],
            )
        except ProducerValidationError as exc:
            raise ProducerRejectedError("wheelhouse package receipt differs") from exc
    model_archives = _parse_model_archives(args.model_archive)
    models_by_role = {model["role"]: model for model in validated["models"]}
    for role, model in models_by_role.items():
        try:
            verify_regular_file(
                model_archives[role],
                expected_bytes=model["bytes"],
                expected_sha256=model["sha256"],
            )
        except ProducerValidationError as exc:
            raise ProducerRejectedError("model archive receipt differs") from exc

    if args.output_artifact.exists() or args.output_artifact.is_symlink():
        raise ProducerBlockedError("staging artifact already exists")
    temporary = args.output_artifact.parent / ("." + args.output_artifact.name + ".partial")
    if temporary.exists() or temporary.is_symlink():
        raise ProducerBlockedError("staging temporary path already exists")
    temporary.mkdir(parents=True, mode=0o700)
    try:
        inputs = temporary / "inputs"
        _atomic_write_new(inputs / "producer-input.lock.json", lock_raw)
        _atomic_write_new(inputs / "requirements.cpu.lock.txt", requirements_raw)
        _atomic_write_new(inputs / "legal-inventory.json", legal_raw)
        for model in validated["models"]:
            for field in ("licenseSourcePath", "noticePath"):
                relative = PurePosixPath(model[field])
                source = args.input_lock.parent.joinpath(*relative.parts)
                destination = temporary.joinpath(*relative.parts)
                if not destination.exists():
                    _copy_regular_file(source, destination)
        for package in validated["packages"]:
            _copy_regular_file(
                args.wheelhouse / package["filename"],
                temporary / "wheelhouse" / package["filename"],
            )
        tree_digests: dict[str, str] = {}
        for role in ("detector", "recognizer"):
            model_root = temporary / "models" / role
            extract_archive(
                model_archives[role],
                model_root,
                ARCHIVE_LIMITS,
                strip_single_root=True,
            )
            manifest = canonical_tree_manifest(model_root)
            actual_tree = tree_sha256(model_root)
            if actual_tree != models_by_role[role]["treeSha256"]:
                raise ProducerRejectedError("model tree sha256 differs")
            tree_digests[role] = actual_tree
            _atomic_write_new(temporary / "models" / f"{role}.manifest.txt", manifest)
        pair_document = {
            "detector": tree_digests["detector"],
            "recognizer": tree_digests["recognizer"],
        }
        pair_raw = jcs_bytes(pair_document)
        pair_sha = sha256_hex(pair_raw)
        _atomic_write_new(temporary / "models" / "model-pair.json", pair_raw)
        staging_document = {
            "schemaVersion": 1,
            "inputLockSha256": sha256_hex(lock_raw),
            "legalInventorySha256": sha256_hex(legal_raw),
            "requirementsSha256": sha256_hex(requirements_raw),
            "modelTreeDigests": tree_digests,
            "modelPairSha256": pair_sha,
        }
        _atomic_write_new(temporary / "staging-manifest.json", jcs_bytes(staging_document))
        artifact_sha = sha256_hex(canonical_tree_manifest(temporary))
        os.rename(temporary, args.output_artifact)
    except BaseException:
        shutil.rmtree(temporary, ignore_errors=True)
        raise
    return _success(
        "stage-inputs",
        {
            "artifactPath": str(args.output_artifact),
            "artifactSha256": artifact_sha,
            "modelTreeDigests": tree_digests,
            "modelPairSha256": pair_sha,
        },
    )


def _validated_input_lock(path: Path) -> tuple[dict[str, Any], bytes]:
    lock, raw = _load_canonical_document(path)
    try:
        return validate_input_lock(lock, allowed_hosts=_hosts_from_lock(lock)), raw
    except ProducerValidationError as exc:
        raise ProducerBlockedError("input lock violates its contract") from exc


def _manifest_descriptors(root: Path) -> list[dict[str, Any]]:
    for current, directories, files in os.walk(root, followlinks=False):
        current_path = Path(current)
        for name in directories:
            if (current_path / name).is_symlink():
                raise ProducerRejectedError("artifact tree must not contain symlink directories")
        for name in files:
            metadata = (current_path / name).lstat()
            if metadata.st_nlink != 1:
                raise ProducerRejectedError("artifact tree must not contain hard links")
    rows = canonical_tree_manifest(root).decode("utf-8").splitlines()
    return [
        {"path": path, "bytes": int(size), "sha256": digest}
        for path, size, digest in (row.split("\t") for row in rows)
    ]


def _regular_file_receipt(path: Path, max_bytes: int) -> tuple[int, str]:
    flags = os.O_RDONLY | getattr(os, "O_CLOEXEC", 0) | getattr(os, "O_NOFOLLOW", 0)
    try:
        descriptor = os.open(path, flags)
    except OSError as exc:
        raise ProducerRejectedError("artifact must be a regular non-symlink file") from exc
    digest = hashlib.sha256()
    total = 0
    try:
        metadata = os.fstat(descriptor)
        if not stat.S_ISREG(metadata.st_mode) or metadata.st_nlink != 1:
            raise ProducerRejectedError("artifact must be a unique regular file")
        if metadata.st_size <= 0 or metadata.st_size > max_bytes:
            raise ProducerRejectedError("artifact file byte count exceeds limit")
        while True:
            chunk = os.read(descriptor, 1024 * 1024)
            if not chunk:
                break
            total += len(chunk)
            digest.update(chunk)
        if total != metadata.st_size:
            raise ProducerRejectedError("artifact file byte count changed")
    finally:
        os.close(descriptor)
    return total, digest.hexdigest()


def _validate_manifest_files(root: Path, files: Any, manifest_name: str) -> None:
    if not isinstance(files, list) or not files:
        raise ProducerRejectedError("artifact file manifest is invalid")
    expected: dict[str, tuple[int, str]] = {}
    for value in files:
        try:
            descriptor = exact_object(value, required={"path", "bytes", "sha256"})
            relative = PurePosixPath(descriptor["path"])
            if relative.is_absolute() or any(part in {"", ".", ".."} for part in relative.parts):
                raise ProducerValidationError("artifact path escapes root")
            if descriptor["path"] in expected:
                raise ProducerValidationError("artifact path is duplicated")
            if type(descriptor["bytes"]) is not int or descriptor["bytes"] <= 0:
                raise ProducerValidationError("artifact bytes must be positive")
            require_sha256(descriptor["sha256"], "artifact file sha256")
        except (KeyError, TypeError, ProducerValidationError) as exc:
            raise ProducerRejectedError("artifact file manifest is invalid") from exc
        expected[descriptor["path"]] = (descriptor["bytes"], descriptor["sha256"])
    actual = {
        item["path"]: (item["bytes"], item["sha256"])
        for item in _manifest_descriptors(root)
        if item["path"] != manifest_name
    }
    if actual != expected:
        raise ProducerRejectedError("artifact file inventory differs")


def _fetch_locked_inputs(args: argparse.Namespace) -> dict[str, Any]:
    lock, lock_raw = _validated_input_lock(args.input_lock)
    if args.output_root.exists() or args.output_root.is_symlink():
        raise ProducerBlockedError("locked input output root already exists")
    args.output_root.mkdir(parents=True, mode=0o700)
    hosts = _hosts_from_lock(lock)
    try:
        artifacts: list[tuple[dict[str, Any], Path]] = []
        if args.kind == "models":
            artifacts = [
                (model, args.output_root / f"{model['role']}.tar.gz")
                for model in lock["models"]
            ]
        else:
            artifacts = [
                (package, args.output_root / package["filename"])
                for package in lock["packages"]
                if "url" in package
            ]
        for artifact, destination in artifacts:
            fetch_to_regular_file(artifact, destination, allowed_hosts=hosts)
        content_sha = sha256_hex(canonical_tree_manifest(args.output_root))
    except BaseException:
        shutil.rmtree(args.output_root, ignore_errors=True)
        raise
    return _success(
        "fetch-locked-inputs",
        {
            "kind": args.kind.upper(),
            "inputLockSha256": sha256_hex(lock_raw),
            "fileCount": len(artifacts),
            "contentSha256": content_sha,
        },
    )


def _stage_models(args: argparse.Namespace) -> dict[str, Any]:
    lock, lock_raw = _validated_input_lock(args.input_lock)
    legal, legal_raw = _load_canonical_document(args.legal)
    _validate_legal_inventory(legal)
    if lock["legalInventorySha256"] != sha256_hex(legal_raw):
        raise ProducerLegalError("legal inventory sha256 differs")
    _validate_model_legal_files(lock, args.input_lock.parent)
    pipeline_raw = _read_regular_bytes(args.pipeline, 1024 * 1024)
    archives = _parse_model_archives(args.model_archive)
    models = {model["role"]: model for model in lock["models"]}
    for role, model in models.items():
        try:
            verify_regular_file(
                archives[role],
                expected_bytes=model["bytes"],
                expected_sha256=model["sha256"],
            )
        except ProducerValidationError as exc:
            raise ProducerRejectedError("model archive receipt differs") from exc
    identity = AttemptIdentity.from_run(args.run_id, args.run_attempt)
    expected_name = f"paddleocr-models-{identity.attempt_id}"
    if args.artifact_name != expected_name:
        raise ProducerBlockedError("model artifact name differs from run identity")
    if args.output_root.exists() or args.output_root.is_symlink():
        raise ProducerBlockedError("model artifact already exists")
    temporary = args.output_root.parent / ("." + args.output_root.name + ".partial")
    if temporary.exists() or temporary.is_symlink():
        raise ProducerBlockedError("model artifact temporary path already exists")
    temporary.mkdir(parents=True, mode=0o700)
    try:
        tree_digests: dict[str, str] = {}
        for role in ("detector", "recognizer"):
            model_root = temporary / "models" / role
            extract_archive(archives[role], model_root, ARCHIVE_LIMITS, strip_single_root=True)
            digest = tree_sha256(model_root)
            if digest != models[role]["treeSha256"]:
                raise ProducerRejectedError("model tree sha256 differs")
            tree_digests[role] = digest
            _atomic_write_new(
                temporary / "models" / f"{role}.manifest.txt",
                canonical_tree_manifest(model_root),
            )
        pair_sha = sha256_hex(jcs_bytes(tree_digests))
        pipeline_sha = _atomic_write_new(temporary / "ocr-pipeline.yaml", pipeline_raw)
        legal_sha = _atomic_write_new(temporary / "legal-inventory.json", legal_raw)
        model_manifest = {
            "schemaVersion": 1,
            "inputLockSha256": sha256_hex(lock_raw),
            "legalInventorySha256": legal_sha,
            "pipelineSha256": pipeline_sha,
            "modelTreeDigests": tree_digests,
            "modelPairSha256": pair_sha,
        }
        _atomic_write_new(temporary / "model-manifest.json", jcs_bytes(model_manifest))
        staging_manifest = {
            **model_manifest,
            "kind": "MODELS",
            "artifactName": expected_name,
            "runId": identity.run_id,
            "runAttempt": identity.run_attempt,
            "attemptId": identity.attempt_id,
            "files": _manifest_descriptors(temporary),
        }
        _atomic_write_new(temporary / "staging-manifest.json", jcs_bytes(staging_manifest))
        content_sha = sha256_hex(canonical_tree_manifest(temporary))
        os.rename(temporary, args.output_root)
    except BaseException:
        shutil.rmtree(temporary, ignore_errors=True)
        raise
    return _success(
        "stage-models",
        {
            "artifactName": expected_name,
            "contentSha256": content_sha,
            "modelTreeDigests": tree_digests,
            "modelPairSha256": pair_sha,
        },
    )


def _seal_wheelhouse(args: argparse.Namespace) -> dict[str, Any]:
    lock, lock_raw = _validated_input_lock(args.input_lock)
    requirements_raw = _read_regular_bytes(args.requirements_lock, 8 * 1024 * 1024)
    _validate_requirements_lock(requirements_raw, lock["packages"])
    if args.wheelhouse.is_symlink() or not args.wheelhouse.is_dir():
        raise ProducerBlockedError("wheelhouse must be a non-symlink directory")
    expected = {package["filename"] for package in lock["packages"]}
    actual = {path.name for path in args.wheelhouse.iterdir() if path.is_file() and not path.is_symlink()}
    if actual != expected:
        raise ProducerRejectedError("wheelhouse file inventory differs from input lock")
    for package in lock["packages"]:
        try:
            verify_regular_file(
                args.wheelhouse / package["filename"],
                expected_bytes=package["bytes"],
                expected_sha256=package["sha256"],
            )
        except ProducerValidationError as exc:
            raise ProducerRejectedError("wheelhouse package receipt differs") from exc
    identity = AttemptIdentity.from_run(args.run_id, args.run_attempt)
    expected_name = f"paddleocr-wheelhouse-{identity.attempt_id}"
    if args.artifact_name != expected_name:
        raise ProducerBlockedError("wheelhouse artifact name differs from run identity")
    if args.output_root.exists() or args.output_root.is_symlink():
        raise ProducerBlockedError("wheelhouse artifact already exists")
    temporary = args.output_root.parent / ("." + args.output_root.name + ".partial")
    if temporary.exists() or temporary.is_symlink():
        raise ProducerBlockedError("wheelhouse artifact temporary path already exists")
    temporary.mkdir(parents=True, mode=0o700)
    try:
        for package in lock["packages"]:
            _copy_regular_file(
                args.wheelhouse / package["filename"],
                temporary / "wheelhouse" / package["filename"],
            )
        requirements_sha = _atomic_write_new(
            temporary / "requirements.cpu.lock.txt", requirements_raw
        )
        manifest = {
            "schemaVersion": 1,
            "kind": "WHEELHOUSE",
            "artifactName": expected_name,
            "runId": identity.run_id,
            "runAttempt": identity.run_attempt,
            "attemptId": identity.attempt_id,
            "inputLockSha256": sha256_hex(lock_raw),
            "requirementsSha256": requirements_sha,
            "files": _manifest_descriptors(temporary),
        }
        _atomic_write_new(temporary / "wheelhouse-manifest.json", jcs_bytes(manifest))
        content_sha = sha256_hex(canonical_tree_manifest(temporary))
        os.rename(temporary, args.output_root)
    except BaseException:
        shutil.rmtree(temporary, ignore_errors=True)
        raise
    return _success(
        "seal-wheelhouse",
        {"artifactName": expected_name, "contentSha256": content_sha},
    )


def _validate_artifact_identity(
    manifest: dict[str, Any],
    *,
    kind: str,
    run_id: int,
    run_attempt: int,
    input_lock_sha256: str,
) -> None:
    identity = AttemptIdentity.from_run(run_id, run_attempt)
    expected_name = f"paddleocr-{kind.lower()}-{identity.attempt_id}"
    for field, expected in (
        ("schemaVersion", 1),
        ("kind", kind),
        ("artifactName", expected_name),
        ("runId", run_id),
        ("runAttempt", run_attempt),
        ("attemptId", identity.attempt_id),
        ("inputLockSha256", input_lock_sha256),
    ):
        if manifest.get(field) != expected:
            raise ProducerRejectedError(f"{kind.lower()} artifact {field} differs")


def _validate_model_artifact(
    root: Path, lock: dict[str, Any], lock_sha: str, run_id: int, run_attempt: int
) -> tuple[dict[str, Any], str]:
    manifest, _ = _load_canonical_document(root / "staging-manifest.json")
    required = {
        "schemaVersion", "kind", "artifactName", "runId", "runAttempt", "attemptId",
        "inputLockSha256", "legalInventorySha256", "pipelineSha256", "modelTreeDigests",
        "modelPairSha256", "files",
    }
    try:
        manifest = exact_object(manifest, required=required)
    except ProducerValidationError as exc:
        raise ProducerRejectedError("model artifact manifest violates its contract") from exc
    _validate_artifact_identity(
        manifest, kind="MODELS", run_id=run_id, run_attempt=run_attempt,
        input_lock_sha256=lock_sha,
    )
    _validate_manifest_files(root, manifest["files"], "staging-manifest.json")
    model_paths = {descriptor["path"] for descriptor in manifest["files"]}
    exact_root_files = {
        "legal-inventory.json", "model-manifest.json", "ocr-pipeline.yaml"
    }
    if not exact_root_files.issubset(model_paths) or any(
        path not in exact_root_files
        and path not in {"models/detector.manifest.txt", "models/recognizer.manifest.txt"}
        and not path.startswith("models/detector/")
        and not path.startswith("models/recognizer/")
        for path in model_paths
    ):
        raise ProducerRejectedError("model artifact path allowlist differs")
    if manifest["legalInventorySha256"] != lock["legalInventorySha256"]:
        raise ProducerRejectedError("model artifact legal inventory differs")
    legal_raw = _read_regular_bytes(root / "legal-inventory.json")
    pipeline_raw = _read_regular_bytes(root / "ocr-pipeline.yaml")
    if sha256_hex(legal_raw) != manifest["legalInventorySha256"]:
        raise ProducerRejectedError("model artifact legal inventory hash differs")
    if sha256_hex(pipeline_raw) != manifest["pipelineSha256"]:
        raise ProducerRejectedError("model artifact pipeline hash differs")
    trees = {role: tree_sha256(root / "models" / role) for role in ("detector", "recognizer")}
    expected_trees = {model["role"]: model["treeSha256"] for model in lock["models"]}
    if trees != expected_trees or trees != manifest["modelTreeDigests"]:
        raise ProducerRejectedError("model artifact tree digests differ")
    for role in ("detector", "recognizer"):
        role_manifest = _read_regular_bytes(root / "models" / f"{role}.manifest.txt")
        if role_manifest != canonical_tree_manifest(root / "models" / role):
            raise ProducerRejectedError(f"{role} model manifest differs")
        if sha256_hex(role_manifest) != trees[role]:
            raise ProducerRejectedError(f"{role} model manifest hash differs")
    if sha256_hex(jcs_bytes(trees)) != manifest["modelPairSha256"]:
        raise ProducerRejectedError("model artifact pair digest differs")
    model_manifest, _ = _load_canonical_document(root / "model-manifest.json")
    embedded = {
        key: manifest[key]
        for key in (
            "schemaVersion", "inputLockSha256", "legalInventorySha256",
            "pipelineSha256", "modelTreeDigests", "modelPairSha256",
        )
    }
    if model_manifest != embedded:
        raise ProducerRejectedError("embedded model manifest differs")
    return manifest, sha256_hex(canonical_tree_manifest(root))


def _validate_wheelhouse_artifact(
    root: Path, lock: dict[str, Any], lock_sha: str, run_id: int, run_attempt: int
) -> tuple[dict[str, Any], str]:
    manifest, _ = _load_canonical_document(root / "wheelhouse-manifest.json")
    required = {
        "schemaVersion", "kind", "artifactName", "runId", "runAttempt", "attemptId",
        "inputLockSha256", "requirementsSha256", "files",
    }
    try:
        manifest = exact_object(manifest, required=required)
    except ProducerValidationError as exc:
        raise ProducerRejectedError("wheelhouse artifact manifest violates its contract") from exc
    _validate_artifact_identity(
        manifest, kind="WHEELHOUSE", run_id=run_id, run_attempt=run_attempt,
        input_lock_sha256=lock_sha,
    )
    _validate_manifest_files(root, manifest["files"], "wheelhouse-manifest.json")
    expected_paths = {
        "requirements.cpu.lock.txt",
        *(f"wheelhouse/{package['filename']}" for package in lock["packages"]),
    }
    if {descriptor["path"] for descriptor in manifest["files"]} != expected_paths:
        raise ProducerRejectedError("wheelhouse artifact path allowlist differs")
    requirements_raw = _read_regular_bytes(root / "requirements.cpu.lock.txt", 8 * 1024 * 1024)
    if sha256_hex(requirements_raw) != manifest["requirementsSha256"]:
        raise ProducerRejectedError("wheelhouse requirements hash differs")
    _validate_requirements_lock(requirements_raw, lock["packages"])
    expected = {package["filename"] for package in lock["packages"]}
    actual = {path.name for path in (root / "wheelhouse").iterdir() if path.is_file() and not path.is_symlink()}
    if actual != expected:
        raise ProducerRejectedError("wheelhouse artifact files differ")
    for package in lock["packages"]:
        try:
            verify_regular_file(
                root / "wheelhouse" / package["filename"],
                expected_bytes=package["bytes"], expected_sha256=package["sha256"],
            )
        except ProducerValidationError as exc:
            raise ProducerRejectedError("wheelhouse artifact package differs") from exc
    return manifest, sha256_hex(canonical_tree_manifest(root))


def _artifact_receipt(
    manifest: dict[str, Any], *, artifact_id: int, artifact_digest: str, content_sha: str
) -> dict[str, Any]:
    return {
        "schemaVersion": 1,
        "kind": manifest["kind"],
        "artifactName": manifest["artifactName"],
        "artifactId": artifact_id,
        "artifactDigest": artifact_digest,
        "runId": manifest["runId"],
        "runAttempt": manifest["runAttempt"],
        "attemptId": manifest["attemptId"],
        "inputLockSha256": manifest["inputLockSha256"],
        "contentSha256": content_sha,
    }


def _validate_build_artifacts(args: argparse.Namespace) -> dict[str, Any]:
    lock, lock_raw = _validated_input_lock(args.input_lock)
    lock_sha = sha256_hex(lock_raw)
    model_manifest, model_content = _validate_model_artifact(
        args.models_root, lock, lock_sha, args.run_id, args.run_attempt
    )
    wheel_manifest, wheel_content = _validate_wheelhouse_artifact(
        args.wheelhouse_root, lock, lock_sha, args.run_id, args.run_attempt
    )
    for kind, manifest, artifact_id, artifact_digest, content, expected_content in (
        (
            "MODELS", model_manifest, args.models_artifact_id, args.models_artifact_digest,
            model_content, args.models_content_sha256,
        ),
        (
            "WHEELHOUSE", wheel_manifest, args.wheelhouse_artifact_id,
            args.wheelhouse_artifact_digest, wheel_content, args.wheelhouse_content_sha256,
        ),
    ):
        require_sha256(expected_content, f"{kind.lower()} expected content sha256")
        if content != expected_content:
            raise ProducerRejectedError(f"{kind.lower()} artifact content differs")
        receipt = _artifact_receipt(
            manifest, artifact_id=artifact_id, artifact_digest=artifact_digest,
            content_sha=content,
        )
        validate_same_run_artifact(
            receipt,
            expected_kind=kind,
            expected_artifact_name=manifest["artifactName"],
            expected_artifact_id=artifact_id,
            expected_artifact_digest=artifact_digest,
            expected_run_id=args.run_id,
            expected_run_attempt=args.run_attempt,
            expected_input_lock_sha256=lock_sha,
            expected_content_sha256=content,
        )
    return _success(
        "validate-build-artifacts",
        {"modelsContentSha256": model_content, "wheelhouseContentSha256": wheel_content},
    )


def _read_tar_member(archive: tarfile.TarFile, member: tarfile.TarInfo, limit: int) -> bytes:
    if member.size <= 0 or member.size > limit:
        raise ProducerRejectedError("OCI metadata blob byte count is invalid")
    stream = archive.extractfile(member)
    if stream is None:
        raise ProducerRejectedError("OCI archive member is unreadable")
    raw = stream.read(limit + 1)
    if len(raw) != member.size:
        raise ProducerRejectedError("OCI archive member byte count differs")
    return raw


def _inspect_oci_archive(path: Path) -> dict[str, str]:
    _regular_file_receipt(path, 8 * 1024 * 1024 * 1024)
    try:
        with tarfile.open(path, "r:*") as archive:
            members: dict[str, tarfile.TarInfo] = {}
            total = 0
            for member in archive.getmembers():
                if member.isdir():
                    continue
                relative = PurePosixPath(member.name)
                if (
                    not member.isfile()
                    or relative.is_absolute()
                    or any(part in {"", ".", ".."} for part in relative.parts)
                    or member.name in members
                ):
                    raise ProducerRejectedError("OCI archive contains an unsafe member")
                if member.name not in {"oci-layout", "index.json"} and re.fullmatch(
                    r"blobs/sha256/[0-9a-f]{64}", member.name
                ) is None:
                    raise ProducerRejectedError("OCI archive member is outside the allowlist")
                total += member.size
                if len(members) >= 1024 or total > 8 * 1024 * 1024 * 1024:
                    raise ProducerRejectedError("OCI archive exceeds limits")
                members[member.name] = member
            if not {"oci-layout", "index.json"}.issubset(members):
                raise ProducerRejectedError("OCI archive root metadata is incomplete")
            layout = load_json_bytes(_read_tar_member(archive, members["oci-layout"], 4096), 4096)
            if layout != {"imageLayoutVersion": "1.0.0"}:
                raise ProducerRejectedError("OCI layout version differs")
            index_raw = _read_tar_member(archive, members["index.json"], 4 * 1024 * 1024)
            index = load_json_bytes(index_raw, 4 * 1024 * 1024)

            cached: dict[str, bytes] = {}
            for name, member in members.items():
                if not name.startswith("blobs/sha256/"):
                    continue
                stream = archive.extractfile(member)
                if stream is None:
                    raise ProducerRejectedError("OCI blob is unreadable")
                digest = hashlib.sha256()
                collected = bytearray()
                read = 0
                while True:
                    chunk = stream.read(1024 * 1024)
                    if not chunk:
                        break
                    read += len(chunk)
                    digest.update(chunk)
                    if member.size <= 4 * 1024 * 1024:
                        collected.extend(chunk)
                if read != member.size or digest.hexdigest() != name.rsplit("/", 1)[1]:
                    raise ProducerRejectedError("OCI blob digest or byte count differs")
                if collected:
                    cached[name] = bytes(collected)

            def descriptor(value: Any, field: str) -> dict[str, Any]:
                if not isinstance(value, dict):
                    raise ProducerRejectedError(f"{field} descriptor is invalid")
                media_type = value.get("mediaType")
                digest = value.get("digest")
                size = value.get("size")
                if (
                    not isinstance(media_type, str)
                    or not isinstance(digest, str)
                    or re.fullmatch(r"sha256:[0-9a-f]{64}", digest) is None
                    or type(size) is not int
                    or size <= 0
                ):
                    raise ProducerRejectedError(f"{field} descriptor is invalid")
                name = "blobs/sha256/" + digest[7:]
                if name not in members or members[name].size != size:
                    raise ProducerRejectedError(f"{field} descriptor is unavailable")
                return value

            manifests = index.get("manifests")
            if index.get("schemaVersion") != 2 or not isinstance(manifests, list) or len(manifests) != 1:
                raise ProducerRejectedError("OCI index must contain exactly one root descriptor")
            top = descriptor(manifests[0], "root")
            expected = {"blobs/sha256/" + top["digest"][7:]}
            if top["mediaType"] == "application/vnd.oci.image.index.v1+json":
                nested_raw = cached.get(next(iter(expected)))
                if nested_raw is None:
                    raise ProducerRejectedError("OCI image index exceeds metadata limit")
                nested = load_json_bytes(nested_raw, 4 * 1024 * 1024)
                candidates = []
                for value in nested.get("manifests", []):
                    platform = value.get("platform") if isinstance(value, dict) else None
                    if isinstance(platform, dict) and platform.get("os") == "linux" and platform.get("architecture") == "amd64" and platform.get("variant") in (None, ""):
                        candidates.append(value)
                if len(candidates) != 1:
                    raise ProducerRejectedError("OCI index has no unique linux/amd64 manifest")
                platform = descriptor(candidates[0], "platform")
                expected.add("blobs/sha256/" + platform["digest"][7:])
            elif top["mediaType"] == "application/vnd.oci.image.manifest.v1+json":
                platform = top
            else:
                raise ProducerRejectedError("OCI root descriptor media type differs")
            manifest_name = "blobs/sha256/" + platform["digest"][7:]
            manifest_raw = cached.get(manifest_name)
            if manifest_raw is None:
                raise ProducerRejectedError("OCI platform manifest exceeds metadata limit")
            manifest = load_json_bytes(manifest_raw, 4 * 1024 * 1024)
            if manifest.get("schemaVersion") != 2 or manifest.get("mediaType") != "application/vnd.oci.image.manifest.v1+json":
                raise ProducerRejectedError("OCI platform manifest contract differs")
            config = descriptor(manifest.get("config"), "config")
            layers = manifest.get("layers")
            if not isinstance(layers, list) or not layers:
                raise ProducerRejectedError("OCI platform manifest layers are invalid")
            expected.add("blobs/sha256/" + config["digest"][7:])
            for index_value, layer in enumerate(layers):
                value = descriptor(layer, f"layer[{index_value}]")
                expected.add("blobs/sha256/" + value["digest"][7:])
            actual_blobs = {name for name in members if name.startswith("blobs/sha256/")}
            if actual_blobs != expected:
                raise ProducerRejectedError("OCI archive blob closure differs")
            config_name = "blobs/sha256/" + config["digest"][7:]
            config_raw = cached.get(config_name)
            if config_raw is None:
                raise ProducerRejectedError("OCI image config exceeds metadata limit")
            config_document = load_json_bytes(config_raw, 4 * 1024 * 1024)
            if config_document.get("os") != "linux" or config_document.get("architecture") != "amd64":
                raise ProducerRejectedError("OCI image config platform differs")
    except (tarfile.TarError, OSError, ProducerValidationError) as exc:
        if isinstance(exc, ProducerRejectedError):
            raise
        raise ProducerRejectedError("OCI archive could not be inspected") from exc
    return {
        "imageIndexDigest": top["digest"],
        "imagePlatformDigest": platform["digest"],
        "imageConfigDigest": config["digest"],
    }


def _create_oci_handoff(args: argparse.Namespace) -> dict[str, Any]:
    lock, lock_raw = _validated_input_lock(args.input_lock)
    lock_sha = sha256_hex(lock_raw)
    manifest, model_content = _validate_model_artifact(
        args.models_manifest.parent, lock, lock_sha, args.run_id, args.run_attempt
    )
    require_sha256(args.staging_artifact_sha256, "staging artifact sha256")
    if model_content != args.staging_artifact_sha256:
        raise ProducerRejectedError("staging artifact sha256 differs")
    if args.oci_tar.name != "paddleocr-service.oci.tar" or args.output != args.oci_tar.parent / "handoff.json":
        raise ProducerBlockedError("OCI handoff paths differ from the exact contract")
    digests = _inspect_oci_archive(args.oci_tar)
    derived = [package for package in lock["packages"] if "derivedSourceId" in package]
    if len(derived) != 1:
        raise ProducerBlockedError("source date epoch is ambiguous")
    epoch = derived[0]["buildToolchain"]["sourceDateEpoch"]
    build_receipt, _ = _load_canonical_document(args.build_receipt)
    identity = AttemptIdentity.from_run(args.run_id, args.run_attempt)
    document = {
        "schemaVersion": 1,
        "attemptId": identity.attempt_id,
        "runId": identity.run_id,
        "runAttempt": identity.run_attempt,
        "inputLockSha256": lock_sha,
        "stagingArtifactSha256": model_content,
        "imageTarSha256": _regular_file_receipt(
            args.oci_tar, 8 * 1024 * 1024 * 1024
        )[1],
        **digests,
        "baseDigest": lock["baseImage"]["platformDigest"],
        "targetPlatform": lock["targetPlatform"],
        "sourceDateEpoch": epoch,
        "modelTreeDigests": manifest["modelTreeDigests"],
        "modelPairSha256": manifest["modelPairSha256"],
        "createdAt": datetime.datetime.fromtimestamp(
            epoch, tz=datetime.timezone.utc
        ).isoformat().replace("+00:00", "Z"),
        "buildReceipt": build_receipt,
    }
    validate_oci_handoff(document)
    _atomic_write_new(args.output, jcs_bytes(document))
    content_sha = sha256_hex(canonical_tree_manifest(args.output.parent))
    return _success(
        "create-oci-handoff",
        {**digests, "contentSha256": content_sha, "handoffSha256": sha256_hex(jcs_bytes(document))},
    )


def _validate_oci_artifact(args: argparse.Namespace) -> dict[str, Any]:
    lock, lock_raw = _validated_input_lock(args.input_lock)
    lock_sha = sha256_hex(lock_raw)
    if args.root.is_symlink() or not args.root.is_dir():
        raise ProducerRejectedError("OCI artifact root must be a non-symlink directory")
    if {path.name for path in args.root.iterdir()} != {"paddleocr-service.oci.tar", "handoff.json"}:
        raise ProducerRejectedError("OCI artifact file inventory differs")
    handoff, _ = _load_canonical_document(args.root / "handoff.json")
    validate_oci_handoff(handoff)
    identity = AttemptIdentity.from_run(args.run_id, args.run_attempt)
    if (
        handoff["runId"] != identity.run_id
        or handoff["runAttempt"] != identity.run_attempt
        or handoff["attemptId"] != identity.attempt_id
        or handoff["inputLockSha256"] != lock_sha
    ):
        raise ProducerRejectedError("OCI artifact run or input binding differs")
    derived = [package for package in lock["packages"] if "derivedSourceId" in package]
    expected_trees = {model["role"]: model["treeSha256"] for model in lock["models"]}
    if (
        len(derived) != 1
        or handoff["baseDigest"] != lock["baseImage"]["platformDigest"]
        or handoff["targetPlatform"] != lock["targetPlatform"]
        or handoff["sourceDateEpoch"] != derived[0]["buildToolchain"]["sourceDateEpoch"]
        or handoff["modelTreeDigests"] != expected_trees
        or handoff["modelPairSha256"] != sha256_hex(jcs_bytes(expected_trees))
    ):
        raise ProducerRejectedError("OCI artifact locked input identity differs")
    tar_path = args.root / "paddleocr-service.oci.tar"
    tar_sha = _regular_file_receipt(tar_path, 8 * 1024 * 1024 * 1024)[1]
    if tar_sha != handoff["imageTarSha256"]:
        raise ProducerRejectedError("OCI artifact tar sha256 differs")
    digests = _inspect_oci_archive(tar_path)
    if any(handoff[name] != value for name, value in digests.items()):
        raise ProducerRejectedError("OCI artifact image identity differs")
    content_sha = sha256_hex(canonical_tree_manifest(args.root))
    require_sha256(args.content_sha256, "OCI expected content sha256")
    if content_sha != args.content_sha256:
        raise ProducerRejectedError("OCI artifact content differs")
    manifest = {
        "kind": "OCI", "artifactName": f"paddleocr-oci-{identity.attempt_id}",
        "runId": identity.run_id, "runAttempt": identity.run_attempt,
        "attemptId": identity.attempt_id, "inputLockSha256": lock_sha,
    }
    receipt = _artifact_receipt(
        manifest, artifact_id=args.artifact_id, artifact_digest=args.artifact_digest,
        content_sha=content_sha,
    )
    validate_same_run_artifact(
        receipt, expected_kind="OCI", expected_artifact_name=manifest["artifactName"],
        expected_artifact_id=args.artifact_id, expected_artifact_digest=args.artifact_digest,
        expected_run_id=identity.run_id, expected_run_attempt=identity.run_attempt,
        expected_input_lock_sha256=lock_sha,
        expected_content_sha256=content_sha,
    )
    return _success("validate-oci-artifact", {**digests, "contentSha256": content_sha})


def _inspect_image_raw(reference: str) -> bytes:
    completed = subprocess.run(
        ["docker", "buildx", "imagetools", "inspect", "--raw", reference],
        stdin=subprocess.DEVNULL,
        capture_output=True,
        check=False,
        timeout=60,
    )
    if completed.returncode != 0 or not completed.stdout:
        raise ProducerBlockedError("base image manifest could not be resolved")
    if len(completed.stdout) > 4 * 1024 * 1024:
        raise ProducerBlockedError("base image manifest exceeds byte limit")
    return completed.stdout


def _resolve_inputs(args: argparse.Namespace) -> dict[str, Any]:
    if args.target_platform != "linux/amd64" or args.python_version != "3.10":
        raise ProducerBlockedError("unsupported producer target")
    accepted_lock, _ = _load_canonical_document(
        args.accepted_root / "producer-input.lock.json"
    )
    try:
        validated = validate_input_lock(
            accepted_lock, allowed_hosts=_hosts_from_lock(accepted_lock)
        )
    except ProducerValidationError as exc:
        raise ProducerBlockedError("accepted input lock violates its contract") from exc
    sources = {source["id"]: source for source in validated["sources"]}
    requested_revisions = {
        "paddleocr": args.paddleocr_commit,
        "paddlex": args.paddlex_commit,
        "paddle": args.paddle_commit,
    }
    for source_id, revision in requested_revisions.items():
        if source_id not in sources or sources[source_id]["revision"] != revision:
            raise ProducerBlockedError("requested source revision differs from accepted input")
    models = {model["role"]: model["modelId"] for model in validated["models"]}
    if models != {"detector": args.detector, "recognizer": args.recognizer}:
        raise ProducerBlockedError("requested model pair differs from accepted input")

    legal, legal_raw = _load_canonical_document(args.accepted_root / "legal-inventory.json")
    _validate_legal_inventory(legal)
    if validated["legalInventorySha256"] != sha256_hex(legal_raw):
        raise ProducerLegalError("accepted legal inventory sha256 differs")
    _validate_model_legal_files(validated, args.accepted_root)
    requirements_raw = _read_regular_bytes(
        args.accepted_root / "requirements.cpu.lock.txt", 8 * 1024 * 1024
    )
    _validate_requirements_lock(requirements_raw, validated["packages"])

    index_raw = _inspect_image_raw(args.base_image_candidate)
    index = load_json_bytes(index_raw, 4 * 1024 * 1024)
    manifests = index.get("manifests")
    if not isinstance(manifests, list):
        raise ProducerBlockedError("base image candidate is not a multi-platform index")
    matches = []
    for manifest in manifests:
        if not isinstance(manifest, dict):
            continue
        platform = manifest.get("platform")
        if (
            isinstance(platform, dict)
            and platform.get("os") == "linux"
            and platform.get("architecture") == "amd64"
            and platform.get("variant") in (None, "")
        ):
            matches.append(manifest)
    if len(matches) != 1 or not isinstance(matches[0].get("digest"), str):
        raise ProducerBlockedError("base image has no unique linux/amd64 manifest")
    index_digest = "sha256:" + sha256_hex(index_raw)
    platform_digest = matches[0]["digest"]
    platform_raw = _inspect_image_raw(
        args.base_image_candidate.split(":", 1)[0] + "@" + platform_digest
    )
    platform_manifest = load_json_bytes(platform_raw, 4 * 1024 * 1024)
    config = platform_manifest.get("config")
    if not isinstance(config, dict) or not isinstance(config.get("digest"), str):
        raise ProducerBlockedError("base image platform manifest has no config digest")
    config_digest = config["digest"]

    candidate = dict(validated)
    candidate["baseImage"] = dict(validated["baseImage"])
    base_name = args.base_image_candidate.rsplit(":", 1)[0]
    candidate["baseImage"].update(
        {
            "reference": base_name + "@" + index_digest,
            "indexDigest": index_digest,
            "platformDigest": platform_digest,
            "configDigest": config_digest,
        }
    )
    candidate_raw = jcs_bytes(candidate)
    if args.output_root.exists() and not args.output_root.is_dir():
        raise ProducerBlockedError("resolution output root must be a directory")
    outputs = (
        args.output_root / "producer-input-candidate.json",
        args.output_root / "requirements.cpu.lock.txt",
        args.output_root / "legal-inventory.json",
    )
    if any(path.exists() or path.is_symlink() for path in outputs):
        raise ProducerBlockedError("resolution output already exists")
    args.output_root.mkdir(parents=True, exist_ok=True, mode=0o700)
    _atomic_write_new(outputs[0], candidate_raw)
    _atomic_write_new(outputs[1], requirements_raw)
    _atomic_write_new(outputs[2], legal_raw)
    for model in candidate["models"]:
        for field in ("licenseSourcePath", "noticePath"):
            relative = PurePosixPath(model[field])
            destination = args.output_root.joinpath(*relative.parts)
            if not destination.exists():
                _copy_regular_file(
                    args.accepted_root.joinpath(*relative.parts), destination
                )
    base_digests = {
        "indexDigest": index_digest,
        "platformDigest": platform_digest,
        "configDigest": config_digest,
    }
    return _success(
        "resolve-inputs",
        {
            "candidatePath": str(outputs[0]),
            "candidateSha256": sha256_hex(candidate_raw),
            "baseDigests": base_digests,
            "operationReceipts": [
                {
                    "operationId": "resolve-base-image",
                    "reference": args.base_image_candidate,
                    **base_digests,
                }
            ],
        },
    )


def _merge_cleanup(args: argparse.Namespace) -> dict[str, Any]:
    fragments = []
    for path in args.fragment:
        document, _ = _load_canonical_document(path, contract_error=True)
        fragments.append(document)
    aggregate = merge_cleanup_fragments(
        args.attempt_id,
        args.original_status,
        args.started_job,
        fragments,
        merged_at=args.merged_at,
    )
    raw = jcs_bytes(aggregate)
    digest = _atomic_write_new(args.output, raw)
    return _success(
        "merge-cleanup",
        {"attemptId": args.attempt_id, "cleanupSha256": digest, "cleanupVerified": aggregate["cleanupVerified"]},
    )


def _reconcile(args: argparse.Namespace) -> dict[str, Any]:
    document, raw = _load_canonical_document(args.reconciliation, contract_error=True)
    validated = validate_reconciliation(document)
    return _success(
        "reconcile",
        {
            "attemptId": validated["attemptId"],
            "producerStatus": validated["producerStatus"],
            "reconciliationSha256": sha256_hex(raw),
        },
    )


def _append_revocation(args: argparse.Namespace) -> dict[str, Any]:
    previous, _ = _load_canonical_document(args.previous, contract_error=True)
    entries = []
    for path in args.entry:
        entry, _ = _load_canonical_document(path, contract_error=True)
        entries.append(entry)
    candidate = append_revocations(previous, entries)
    raw = jcs_bytes(candidate)
    digest = _atomic_write_new(args.output, raw)
    return _success(
        "append-revocation",
        {"revocationsSha256": digest, "entryCount": len(candidate["digests"])},
    )


def _verify_emergency_receipt(args: argparse.Namespace) -> dict[str, Any]:
    receipt, receipt_raw = _load_canonical_document(args.receipt, contract_error=True)
    revocations, revocations_raw = _load_canonical_document(args.revocations, contract_error=True)
    validate_emergency_receipt(receipt, revocations)
    return _success(
        "verify-emergency-receipt",
        {
            "receiptSha256": sha256_hex(receipt_raw),
            "revocationsSha256": sha256_hex(revocations_raw),
            "commitSha": receipt["revocationsCommitSha"],
        },
    )


def _verify_cleanup_fragments(cleanup_path: Path, cleanup: dict[str, Any]) -> None:
    expected_paths = set()
    for descriptor in cleanup["fragments"]:
        relative = PurePosixPath(descriptor["path"])
        if relative.is_absolute() or ".." in relative.parts:
            raise ProducerValidationError("cleanup fragment path escapes its bundle")
        path = cleanup_path.parent.joinpath(*relative.parts)
        fragment, raw = _load_canonical_document(path, contract_error=True)
        validate_cleanup_fragment(fragment)
        if fragment["attemptId"] != cleanup["attemptId"]:
            raise ProducerValidationError("cleanup fragment attemptId differs")
        if fragment["jobId"] != descriptor["jobId"]:
            raise ProducerValidationError("cleanup fragment jobId differs")
        if len(raw) != descriptor["bytes"] or sha256_hex(raw) != descriptor["sha256"]:
            raise ProducerValidationError("cleanup fragment descriptor differs from bytes")
        expected_paths.add(path)
    directory = cleanup_path.parent / "cleanup-fragments"
    if directory.exists():
        actual_paths = {path for path in directory.iterdir() if path.is_file() or path.is_symlink()}
        if actual_paths != expected_paths:
            raise ProducerValidationError("cleanup fragment directory contains missing or extra files")


def _finalize(args: argparse.Namespace) -> dict[str, Any]:
    reconciliation, reconciliation_raw = _load_canonical_document(args.reconciliation, contract_error=True)
    cleanup, cleanup_raw = _load_canonical_document(args.cleanup, contract_error=True)
    _, ledger_raw = _load_canonical_document(args.ledger_fragment, contract_error=True)
    revocations, revocations_raw = _load_canonical_document(args.revocations, contract_error=True)
    validated_reconciliation = validate_reconciliation(reconciliation)
    validated_cleanup = validate_cleanup_aggregate(cleanup)
    validate_revocations(revocations)
    require_sha256(args.attempt_sha256, "attemptSha256")
    _verify_cleanup_fragments(args.cleanup, validated_cleanup)
    if validated_cleanup["attemptId"] != validated_reconciliation["attemptId"]:
        raise ProducerValidationError("cleanup attemptId differs from reconciliation")
    if validated_reconciliation["cleanupVerified"] != validated_cleanup["cleanupVerified"]:
        raise ProducerValidationError("cleanupVerified differs between documents")
    result = build_producer_result(
        validated_reconciliation,
        reconciliation_sha256=sha256_hex(reconciliation_raw),
        ledger_fragment_sha256=sha256_hex(ledger_raw),
        revocations_sha256=sha256_hex(revocations_raw),
    )
    result_sha = _atomic_write_new(args.output, jcs_bytes(result))
    return _success(
        "finalize",
        {
            "attemptId": result["attemptId"],
            "attemptSha256": args.attempt_sha256,
            "cleanupSha256": sha256_hex(cleanup_raw),
            "reconciliationSha256": result["reconciliationSha256"],
            "resultSha256": result_sha,
        },
    )


def _finalize_run(args: argparse.Namespace) -> dict[str, Any]:
    attempt, _ = _load_canonical_document(args.input, contract_error=True)
    cleanup, cleanup_raw = _load_canonical_document(args.cleanup, contract_error=True)
    validated_cleanup = validate_cleanup_aggregate(cleanup)
    if set(attempt) != {"producerStatus", "lastCompletedStage", "runConclusion"}:
        raise ProducerValidationError("finalizer input has an invalid shape")
    result = {
        "schemaVersion": 1,
        "attemptId": validated_cleanup["attemptId"],
        **finalize_attempt(
            attempt,
            {
                "cleanupVerified": validated_cleanup["cleanupVerified"],
                "fragmentsComplete": (
                    len(validated_cleanup["fragments"])
                    == len(validated_cleanup["startedJobIds"])
                ),
            },
        ),
    }
    result_sha = _atomic_write_new(args.output, jcs_bytes(result))
    return _success(
        "finalize-run",
        {
            "attemptId": result["attemptId"],
            "producerStatus": result["producerStatus"],
            "cleanupSha256": sha256_hex(cleanup_raw),
            "resultSha256": result_sha,
        },
    )


def _select_dispatched_run(args: argparse.Namespace) -> dict[str, Any]:
    before, _ = _load_canonical_document(args.before, contract_error=True)
    if set(before) != {"runIds"} or not isinstance(before["runIds"], list):
        raise ProducerValidationError("before snapshot has an invalid shape")
    if args.candidates is None:
        if not all((args.repo, args.workflow, args.branch, args.event)):
            raise ProducerValidationError("remote run selection arguments are incomplete")
        pages = _workflow_runs(args)
    else:
        candidates, _ = _load_canonical_document(args.candidates, contract_error=True)
        if set(candidates) != {"pages"} or not isinstance(candidates["pages"], list):
            raise ProducerValidationError("candidate snapshot has an invalid shape")
        pages = candidates["pages"]
    selected = select_dispatched_run(
        set(before["runIds"]),
        pages,
        expected_head=args.expected_head,
        expected_workflow=args.expected_workflow,
    )
    data = dict(selected)
    if args.output is not None:
        data["outputSha256"] = _atomic_write_new(args.output, jcs_bytes(selected))
    return _success("select-dispatched-run", data)


def _prepare_reconcile_inputs(args: argparse.Namespace) -> dict[str, Any]:
    result, result_raw = _load_canonical_document(
        args.prior_result, contract_error=True
    )
    reconciliation, reconciliation_raw = _load_canonical_document(
        args.prior_reconciliation, contract_error=True
    )
    evidence, evidence_raw = _load_canonical_document(
        args.prior_evidence, contract_error=True
    )
    cleanup, cleanup_raw = _load_canonical_document(
        args.prior_cleanup, contract_error=True
    )
    validated_reconciliation = validate_reconciliation(reconciliation)
    validated_evidence = validate_producer_evidence(evidence)
    validated_cleanup = validate_cleanup_aggregate(cleanup)
    result_keys = {
        "schemaVersion", "attemptId", "producerStatus", "lastCompletedStage",
        "mapped609Status", "imagePlatformDigest", "evidenceManifestDigest",
        "reconciliationSha256", "ledgerFragmentSha256", "revocationsSha256",
        "errorCode", "errorMessage",
    }
    if set(result) != result_keys or result.get("schemaVersion") != 1:
        raise ProducerValidationError("prior result has an invalid shape")
    attempt_id = validated_reconciliation["attemptId"]
    for field, document in (
        ("result", result),
        ("evidence", validated_evidence),
        ("cleanup", validated_cleanup),
    ):
        if document.get("attemptId") != attempt_id:
            raise ProducerValidationError(f"prior {field} attemptId differs")
    for field in ("producerStatus", "lastCompletedStage"):
        expected = validated_reconciliation[field]
        if result[field] != expected or validated_evidence[field] != expected:
            raise ProducerValidationError(f"prior {field} differs across documents")
    if result["mapped609Status"] != validated_reconciliation["mapped609Status"]:
        raise ProducerValidationError("prior mapped609Status differs across documents")
    reconciliation_sha = sha256_hex(reconciliation_raw)
    if result["reconciliationSha256"] != reconciliation_sha:
        raise ProducerValidationError("prior result reconciliationSha256 differs")
    if validated_cleanup["originalProducerStatus"] != result["producerStatus"]:
        raise ProducerValidationError("prior cleanup producerStatus differs")

    def prior_digest(field: str, digest_field: str = "manifestDigest") -> str:
        artifact = validated_reconciliation[field]
        return "NONE" if artifact is None else artifact[digest_field]

    prepared = {
        "resumeAttemptId": attempt_id,
        "expectedPriorStatus": validated_reconciliation["producerStatus"],
        "expectedInputLockSha256": validated_reconciliation["inputLockSha256"],
        "expectedStagingDigest": prior_digest("staging"),
        "expectedReleaseDigest": prior_digest("release"),
        "expectedEvidenceDigest": prior_digest("evidence"),
        "priorDocumentHashes": {
            "resultSha256": sha256_hex(result_raw),
            "reconciliationSha256": reconciliation_sha,
            "evidenceSha256": sha256_hex(evidence_raw),
            "cleanupSha256": sha256_hex(cleanup_raw),
        },
    }
    output_document = _success("prepare-reconcile-inputs", prepared)
    output_sha = _atomic_write_new(args.output, jcs_bytes(output_document))
    return _success(
        "prepare-reconcile-inputs", {**prepared, "outputSha256": output_sha}
    )


def _validate_reconcile_state(args: argparse.Namespace) -> dict[str, Any]:
    expected = validate_dispatch_inputs(
        "RECONCILE",
        args.resume_attempt_id,
        args.expected_prior_status,
        args.expected_input_lock_sha256,
        args.expected_staging_digest,
        args.expected_release_digest,
        args.expected_evidence_digest,
    )
    prior, prior_raw = _load_canonical_document(args.prior_state, contract_error=True)
    required_prior = {
        "schemaVersion",
        "attemptId",
        "workflowHeadSha",
        "producerStatus",
        "inputLockSha256",
        "staging",
        "release",
        "evidence",
        "documentHashes",
    }
    if set(prior) != required_prior or prior["schemaVersion"] != 1:
        raise ProducerValidationError("prior reconcile state has an invalid shape")
    if (
        not isinstance(prior["workflowHeadSha"], str)
        or re.fullmatch(r"[0-9a-f]{40}", prior["workflowHeadSha"]) is None
    ):
        raise ProducerValidationError("prior workflow head SHA is invalid")
    hashes = exact_object(
        prior["documentHashes"],
        required={
            "resultSha256",
            "evidenceSha256",
            "reconciliationSha256",
            "cleanupSha256",
        },
    )
    for field, value in hashes.items():
        require_sha256(value, field)
    prior_documents = {
        "resultSha256": args.prior_result,
        "evidenceSha256": args.prior_evidence,
        "reconciliationSha256": args.prior_reconciliation,
        "cleanupSha256": args.prior_cleanup,
    }
    for field, path in prior_documents.items():
        _, document_raw = _load_canonical_document(path, contract_error=True)
        if sha256_hex(document_raw) != hashes[field]:
            raise ProducerValidationError(f"{field} differs from prior reconcile state")

    readback, _ = _load_canonical_document(
        args.package_readback, contract_error=True
    )
    if (
        set(readback) != {"command", "data", "schemaVersion", "status"}
        or readback["schemaVersion"] != 1
        or readback["command"] != "readback-packages"
        or readback["status"] != "PASS"
        or not isinstance(readback["data"], dict)
    ):
        raise ProducerValidationError("package read-back envelope is invalid")
    data = readback["data"]
    if set(data) != {"staging", "release", "evidence", "retryReceipts"}:
        raise ProducerValidationError("package read-back data has an invalid shape")

    _, input_lock_raw = _validated_input_lock(args.input_lock)
    current_input_lock_sha = sha256_hex(input_lock_raw)
    if current_input_lock_sha != expected["expectedInputLockSha256"]:
        raise ProducerValidationError("current input lock differs from reconcile expectation")
    prior_core = {
        "attemptId": prior["attemptId"],
        "producerStatus": prior["producerStatus"],
        "inputLockSha256": prior["inputLockSha256"],
        "staging": prior["staging"],
        "release": prior["release"],
        "evidence": prior["evidence"],
    }
    remote_core = {
        "attemptId": prior["attemptId"],
        "producerStatus": prior["producerStatus"],
        "inputLockSha256": current_input_lock_sha,
        "staging": data["staging"],
        "release": data["release"],
        "evidence": data["evidence"],
    }
    validate_reconcile_expectations(expected, prior_core, remote_core)
    result = {
        "schemaVersion": 1,
        "replacesAttemptId": prior["attemptId"],
        "workflowHeadSha": prior["workflowHeadSha"],
        "producerStatus": prior["producerStatus"],
        "inputLockSha256": current_input_lock_sha,
        "staging": data["staging"],
        "release": data["release"],
        "evidence": data["evidence"],
        "priorDocumentHashes": hashes,
        "priorStateSha256": sha256_hex(prior_raw),
        "retryReceipts": data["retryReceipts"],
    }
    output_sha = _atomic_write_new(args.output, jcs_bytes(result))
    return _success(
        "validate-reconcile-state", {**result, "outputSha256": output_sha}
    )


def _acknowledge_incident(args: argparse.Namespace) -> dict[str, Any]:
    reconciliation, raw = _load_canonical_document(
        args.reconciliation, contract_error=True
    )
    if sha256_hex(raw) != require_sha256(args.expected_sha256, "expectedSha256"):
        raise ProducerValidationError("reconciliation SHA-256 differs")
    incident, _ = _load_canonical_document(args.incident, contract_error=True)
    if set(incident) != {"incidentUrl", "ownerAcknowledgedAt"}:
        raise ProducerValidationError("incident acknowledgement has an invalid shape")
    result = acknowledge_incident(
        reconciliation, incident["incidentUrl"], incident["ownerAcknowledgedAt"]
    )
    digest = _atomic_write_new(args.output, jcs_bytes(result))
    return _success(
        "acknowledge-incident",
        {"incidentUrl": result["incidentUrl"], "reconciliationSha256": digest},
    )


def _close_incident(args: argparse.Namespace) -> dict[str, Any]:
    original, raw = _load_canonical_document(
        args.reconciliation, contract_error=True
    )
    if sha256_hex(raw) != require_sha256(args.expected_sha256, "expectedSha256"):
        raise ProducerValidationError("reconciliation SHA-256 differs")
    reconciliation = original
    if args.incident is not None:
        reconciliation, _ = _load_canonical_document(args.incident, contract_error=True)
        for field, value in original.items():
            if field not in {"incidentUrl", "ownerAcknowledgedAt"} and reconciliation.get(field) != value:
                raise ProducerValidationError("incident acknowledgement differs from reconciliation")
    if not all(
        (
            args.require_denylist,
            args.require_visibility,
            args.require_cleanup,
            args.require_downstream,
        )
    ):
        raise ProducerValidationError("all incident closure read-backs are required")
    closed_at = args.closed_at or datetime.datetime.now(datetime.timezone.utc).isoformat().replace("+00:00", "Z")
    result = close_incident(reconciliation, closed_at)
    digest = _atomic_write_new(args.output, jcs_bytes(result))
    return _success(
        "close-incident",
        {"incidentUrl": result["incidentUrl"], "reconciliationSha256": digest},
    )


def _plan_known_good_rollback(args: argparse.Namespace) -> dict[str, Any]:
    candidates, _ = _load_canonical_document(args.candidates, contract_error=True)
    revocations, _ = _load_canonical_document(args.revocations, contract_error=True)
    validated = validate_revocations(revocations)
    if set(candidates) != {"candidates"} or not isinstance(
        candidates["candidates"], list
    ):
        raise ProducerValidationError("rollback candidates have an invalid shape")
    plan = plan_known_good_rollback(
        current_digest=args.current_digest,
        candidates=candidates["candidates"],
        revoked_digests={entry["digest"] for entry in validated["digests"]},
        downstream_notified=args.require_downstream_notification,
    )
    digest = _atomic_write_new(args.output, jcs_bytes(plan))
    return _success("plan-known-good-rollback", {**plan, "outputSha256": digest})


def _verify_attempt(args: argparse.Namespace) -> dict[str, Any]:
    attempt, _ = _load_canonical_document(args.attempt, contract_error=True)
    evidence, evidence_raw = _load_canonical_document(args.evidence, contract_error=True)
    reconciliation, reconciliation_raw = _load_canonical_document(args.reconciliation, contract_error=True)
    cleanup, cleanup_raw = _load_canonical_document(args.cleanup, contract_error=True)
    ledger, ledger_raw = _load_canonical_document(args.ledger_fragment, contract_error=True)
    revocations, revocations_raw = _load_canonical_document(args.revocations, contract_error=True)
    validated_attempt = validate_attempt(attempt)
    validated_evidence = validate_producer_evidence(evidence)
    validated_ledger = validate_ledger_fragment(ledger)
    validated_reconciliation = validate_reconciliation(reconciliation)
    validated_cleanup = validate_cleanup_aggregate(cleanup)
    validate_revocations(revocations)
    _verify_cleanup_fragments(args.cleanup, validated_cleanup)
    attempt_id = validated_attempt["attemptId"]
    for field, document in (
        ("evidence", validated_evidence), ("reconciliation", validated_reconciliation),
        ("cleanup", validated_cleanup), ("ledger fragment", validated_ledger),
    ):
        if document.get("attemptId") != attempt_id:
            raise ProducerValidationError(f"{field} attemptId differs")
    hashes = {
        "evidenceSha256": sha256_hex(evidence_raw),
        "reconciliationSha256": sha256_hex(reconciliation_raw),
        "cleanupSha256": sha256_hex(cleanup_raw),
        "ledgerFragmentSha256": sha256_hex(ledger_raw),
        "revocationsSha256": sha256_hex(revocations_raw),
    }
    for field, actual in hashes.items():
        if validated_attempt[field] != actual:
            raise ProducerValidationError(f"attempt {field} differs from companion bytes")
    for field in ("evidenceSha256", "reconciliationSha256", "cleanupSha256", "revocationsSha256"):
        if validated_ledger[field] != hashes[field]:
            raise ProducerValidationError(f"ledger fragment {field} differs from companion bytes")
    if validated_attempt["producerStatus"] != validated_reconciliation["producerStatus"]:
        raise ProducerValidationError("producerStatus differs between attempt and reconciliation")
    if validated_attempt["lastCompletedStage"] != validated_reconciliation["lastCompletedStage"]:
        raise ProducerValidationError("lastCompletedStage differs between attempt and reconciliation")
    if validated_attempt["inputLockSha256"] != validated_reconciliation["inputLockSha256"]:
        raise ProducerValidationError("inputLockSha256 differs between attempt and reconciliation")
    for field, document in (("evidence", validated_evidence), ("ledger fragment", validated_ledger)):
        if document["producerStatus"] != validated_attempt["producerStatus"]:
            raise ProducerValidationError(f"producerStatus differs in {field}")
        if document["lastCompletedStage"] != validated_attempt["lastCompletedStage"]:
            raise ProducerValidationError(f"lastCompletedStage differs in {field}")
        if document["inputLockSha256"] != validated_attempt["inputLockSha256"]:
            raise ProducerValidationError(f"inputLockSha256 differs in {field}")
    if validated_ledger["mapped609Status"] != validated_reconciliation["mapped609Status"]:
        raise ProducerValidationError("mapped609Status differs in ledger fragment")
    expected_image = (
        validated_reconciliation["release"]["manifestDigest"]
        if validated_reconciliation["release"] is not None else None
    )
    if validated_ledger["imagePlatformDigest"] != expected_image:
        raise ProducerValidationError("imagePlatformDigest differs in ledger fragment")
    if validated_attempt["revocationsCommitSha"] != validated_reconciliation["revocationsCommitSha"]:
        raise ProducerValidationError("revocationsCommitSha differs between attempt and reconciliation")
    if validated_attempt["previousDocumentSha256"] != validated_reconciliation["previousDocumentSha256"]:
        raise ProducerValidationError("previousDocumentSha256 differs between attempt and reconciliation")
    return build_producer_result(
        validated_reconciliation,
        reconciliation_sha256=hashes["reconciliationSha256"],
        ledger_fragment_sha256=hashes["ledgerFragmentSha256"],
        revocations_sha256=hashes["revocationsSha256"],
    )


def _validate_oci_handoff(args: argparse.Namespace) -> dict[str, Any]:
    document, raw = _load_canonical_document(args.handoff, contract_error=True)
    validated = validate_oci_handoff(document)
    return _success(
        "validate-oci-handoff",
        {
            "attemptId": validated["attemptId"],
            "imagePlatformDigest": validated["imagePlatformDigest"],
            "handoffSha256": sha256_hex(raw),
        },
    )


def _validate_evidence_manifest(args: argparse.Namespace) -> dict[str, Any]:
    document, raw = _load_canonical_document(args.manifest, contract_error=True)
    validated = validate_evidence_file_manifest(document)
    return _success(
        "validate-evidence-manifest",
        {
            "attemptId": validated["attemptId"],
            "subjectDigest": validated["subjectDigest"],
            "fileManifestSha256": sha256_hex(raw),
        },
    )


def _validate_attestation_identity(args: argparse.Namespace) -> dict[str, Any]:
    document, raw = _load_canonical_document(args.receipt, contract_error=True)
    validated = validate_attestation_identity(
        document,
        expected_subject_name=args.subject_name,
        expected_subject_digest=args.subject_digest,
        expected_predicate_type=args.predicate_type,
        expected_run_id=args.run_id,
        expected_run_attempt=args.run_attempt,
        expected_head_sha=args.head_sha,
    )
    return _success(
        "validate-attestation-identity",
        {
            "subjectDigest": validated["subjectDigest"],
            "predicateType": validated["predicateType"],
            "identitySha256": sha256_hex(raw),
        },
    )


def _validate_release_digest_chain(args: argparse.Namespace) -> dict[str, Any]:
    document, raw = _load_canonical_document(args.receipt, contract_error=True)
    validated = validate_release_digests(document)
    return _success(
        "validate-release-digests",
        {"releaseDigest": validated["releaseDigest"], "receiptSha256": sha256_hex(raw)},
    )


def _extract_platform_manifest(args: argparse.Namespace) -> dict[str, Any]:
    handoff, _ = _load_canonical_document(args.handoff, contract_error=True)
    validate_oci_handoff(handoff)
    member_name = "blobs/sha256/" + handoff["imagePlatformDigest"].split(":", 1)[1]
    try:
        with tarfile.open(args.oci_tar, "r") as archive:
            members = [member for member in archive.getmembers() if member.name == member_name]
            if len(members) != 1 or not members[0].isfile():
                raise ProducerRejectedError("OCI platform manifest member differs")
            raw = _read_tar_member(archive, members[0], 4 * 1024 * 1024)
    except (tarfile.TarError, OSError) as exc:
        raise ProducerRejectedError("OCI platform manifest could not be read") from exc
    if "sha256:" + sha256_hex(raw) != handoff["imagePlatformDigest"]:
        raise ProducerRejectedError("OCI platform manifest digest differs")
    digest = _atomic_write_new(args.output, raw)
    return _success(
        "extract-platform-manifest",
        {"imagePlatformDigest": "sha256:" + digest, "bytes": len(raw)},
    )


def _create_spdx(args: argparse.Namespace) -> dict[str, Any]:
    lock, _ = _validated_input_lock(args.input_lock)
    legal, legal_raw = _load_canonical_document(args.legal, contract_error=True)
    _validate_legal_inventory(legal)
    if lock["legalInventorySha256"] != sha256_hex(legal_raw):
        raise ProducerLegalError("legal inventory sha256 differs")
    identity = AttemptIdentity.from_run(args.run_id, args.run_attempt)
    packages = []
    describes = []
    values = [
        {
            "id": "base-image",
            "name": lock["baseImage"]["reference"],
            "version": lock["baseImage"]["platformDigest"],
            "sha256": lock["baseImage"]["platformDigest"].split(":", 1)[1],
            "license": "NOASSERTION",
            "url": "NOASSERTION",
        },
        *(
            {
                "id": "package-" + package["id"],
                "name": package["name"],
                "version": package["version"],
                "sha256": package["sha256"],
                "license": next(
                    component["licenseExpression"]
                    for component in legal["components"]
                    if component["id"] == package["id"]
                ),
                "url": package.get("url", "NOASSERTION"),
            }
            for package in lock["packages"]
        ),
        *(
            {
                "id": "model-" + model["role"],
                "name": model["modelId"],
                "version": model["modelRevision"],
                "sha256": model["sha256"],
                "license": model["licenseExpression"],
                "url": model["url"],
            }
            for model in lock["models"]
        ),
    ]
    for index, value in enumerate(values, start=1):
        spdx_id = f"SPDXRef-Package-{index}"
        describes.append(spdx_id)
        packages.append({
            "SPDXID": spdx_id,
            "name": value["name"],
            "versionInfo": value["version"],
            "downloadLocation": value["url"],
            "filesAnalyzed": False,
            "licenseConcluded": value["license"],
            "licenseDeclared": value["license"],
            "checksums": [{"algorithm": "SHA256", "checksumValue": value["sha256"]}],
            "supplier": "NOASSERTION",
        })
    source_epoch = next(
        package["buildToolchain"]["sourceDateEpoch"]
        for package in lock["packages"]
        if "derivedSourceId" in package
    )
    document = {
        "spdxVersion": "SPDX-2.3",
        "dataLicense": "CC0-1.0",
        "SPDXID": "SPDXRef-DOCUMENT",
        "name": "bluetape4k-paddleocr-service-" + identity.attempt_id,
        "documentNamespace": (
            "https://github.com/bluetape4k/bluetape4k-image/actions/runs/"
            f"{identity.run_id}/attempts/{identity.run_attempt}/sbom"
        ),
        "creationInfo": {
            "created": datetime.datetime.fromtimestamp(
                source_epoch, tz=datetime.timezone.utc
            ).isoformat().replace("+00:00", "Z"),
            "creators": ["Organization: bluetape4k"],
        },
        "documentDescribes": describes,
        "packages": packages,
    }
    digest = _atomic_write_new(args.output, jcs_bytes(document))
    return _success("create-spdx", {"sbomSha256": digest, "packageCount": len(packages)})


def _copy_evidence_file(source: Path, destination: Path) -> dict[str, Any]:
    raw = _read_regular_bytes(source, 128 * 1024 * 1024)
    _atomic_write_new(destination, raw)
    return {"path": destination.as_posix(), "bytes": len(raw), "sha256": sha256_hex(raw)}


def _prepare_evidence(args: argparse.Namespace) -> dict[str, Any]:
    lock, lock_raw = _validated_input_lock(args.input_lock)
    revocations, revocations_raw = _load_canonical_document(args.revocations, contract_error=True)
    validate_revocations(revocations)
    identity = AttemptIdentity.from_run(args.run_id, args.run_attempt)
    require_sha256(args.reconciliation_sha256, "reconciliationSha256")
    require_sha256(args.cleanup_sha256, "cleanupSha256")
    if args.output_root.exists() or args.output_root.is_symlink():
        raise ProducerBlockedError("evidence source already exists")
    args.output_root.mkdir(parents=True, mode=0o700)
    try:
        copies = {
            "inputs/producer-input.lock.json": args.input_lock,
            "platform-manifest.json": args.platform_manifest,
            "sbom.spdx.json": args.sbom,
            "legal-inventory.json": args.legal,
            "attestations/provenance.bundle.jsonl": args.provenance_bundle,
            "attestations/sbom.bundle.jsonl": args.sbom_bundle,
        }
        for relative, source in copies.items():
            _copy_evidence_file(source, args.output_root / relative)
        for bundle in (args.provenance_bundle, args.sbom_bundle):
            if not load_jsonl_bytes(
                _read_regular_bytes(bundle, 4 * 1024 * 1024),
                4 * 1024 * 1024,
                max_lines=30,
            ).envelopes:
                raise ProducerRejectedError("attestation bundle is empty")
        package_lock = {"schemaVersion": 1, "packages": lock["packages"]}
        _atomic_write_new(
            args.output_root / "manifests/package-lock.json", jcs_bytes(package_lock)
        )
        for model in lock["models"]:
            _atomic_write_new(
                args.output_root / f"manifests/model-{model['role']}.json", jcs_bytes(model)
            )
        subject_raw = _read_regular_bytes(args.output_root / "platform-manifest.json")
        subject = "sha256:" + sha256_hex(subject_raw)
        if subject != args.release_digest or args.staging_digest != args.release_digest:
            raise ProducerRejectedError("evidence image digest chain differs")

        def descriptor(relative: str) -> dict[str, Any]:
            raw = _read_regular_bytes(args.output_root / relative, 128 * 1024 * 1024)
            return {"path": relative, "bytes": len(raw), "sha256": sha256_hex(raw)}

        attestation = {
            **descriptor("attestations/provenance.bundle.jsonl"),
            "predicateType": "https://slsa.dev/provenance/v1",
            "subjectDigest": subject,
            "signer": "bluetape4k/bluetape4k-image/.github/workflows/paddleocr-producer.yml",
            "issuer": "https://token.actions.githubusercontent.com",
            "verified": True,
        }
        sbom_attestation = {
            **descriptor("attestations/sbom.bundle.jsonl"),
            **{key: value for key, value in attestation.items() if key not in {"path", "bytes", "sha256"}},
            "predicateType": "https://spdx.dev/Document/v2.3",
        }
        platform = descriptor("platform-manifest.json")
        evidence = {
            "schemaVersion": 1,
            "attemptId": identity.attempt_id,
            "producerStatus": "RELEASE_UNVERIFIED",
            "lastCompletedStage": "RELEASE",
            "inputLockSha256": sha256_hex(lock_raw),
            "staging": platform,
            "release": platform,
            "payload": {
                "packageLock": descriptor("manifests/package-lock.json"),
                "detectorModel": descriptor("manifests/model-detector.json"),
                "recognizerModel": descriptor("manifests/model-recognizer.json"),
                "legalInventory": descriptor("legal-inventory.json"),
                "spdx": descriptor("sbom.spdx.json"),
                "provenanceAttestation": attestation,
                "sbomAttestation": sbom_attestation,
            },
            "evidenceSubjectDigest": subject,
        }
        validate_producer_evidence(evidence)
        evidence_raw = jcs_bytes(evidence)
        _atomic_write_new(args.output_root / "producer-evidence.json", evidence_raw)
        ledger = {
            "schemaVersion": 1,
            "attemptId": identity.attempt_id,
            "producerStatus": "RELEASE_UNVERIFIED",
            "lastCompletedStage": "RELEASE",
            "mapped609Status": "PENDING",
            "inputLockSha256": sha256_hex(lock_raw),
            "imagePlatformDigest": subject,
            "evidenceSha256": sha256_hex(evidence_raw),
            "reconciliationSha256": args.reconciliation_sha256,
            "cleanupSha256": args.cleanup_sha256,
            "revocationsSha256": sha256_hex(revocations_raw),
            "revocationsCommitSha": args.revocations_commit_sha,
        }
        validate_ledger_fragment(ledger)
        _atomic_write_new(args.output_root / "artifact-ledger.fragment.json", jcs_bytes(ledger))
        if {path.relative_to(args.output_root).as_posix() for path in args.output_root.rglob("*") if path.is_file()} != {
            "producer-evidence.json", "artifact-ledger.fragment.json",
            "inputs/producer-input.lock.json", "manifests/package-lock.json",
            "manifests/model-detector.json", "manifests/model-recognizer.json",
            "platform-manifest.json", "sbom.spdx.json", "legal-inventory.json",
            "attestations/provenance.bundle.jsonl", "attestations/sbom.bundle.jsonl",
        }:
            raise ProducerRejectedError("evidence source file inventory differs")
    except BaseException:
        shutil.rmtree(args.output_root, ignore_errors=True)
        raise
    return _success(
        "prepare-evidence",
        {
            "attemptId": identity.attempt_id,
            "subjectDigest": subject,
            "evidenceSha256": sha256_hex(evidence_raw),
        },
    )


def _create_evidence_oci(args: argparse.Namespace) -> dict[str, Any]:
    receipt = create_evidence_oci_layout(
        args.source_root,
        args.output_root,
        image_index_digest=args.image_index_digest,
        image_platform_digest=args.image_platform_digest,
        image_config_digest=args.image_config_digest,
        base_digest=args.base_digest,
        run_id=args.run_id,
        run_attempt=args.run_attempt,
        input_lock_sha256=args.input_lock_sha256,
        revocations_sha256=args.revocations_sha256,
        revocations_commit_sha=args.revocations_commit_sha,
    )
    return _success("create-evidence-oci", receipt)


def _bootstrap_oras(args: argparse.Namespace) -> dict[str, Any]:
    oras_bin = bootstrap_oras_archive(args.archive, args.tool_root)
    return _success(
        "bootstrap-oras",
        {
            "version": ORAS_VERSION,
            "sourceUrl": ORAS_LINUX_AMD64_URL,
            "archiveSha256": ORAS_LINUX_AMD64_SHA256,
            "orasBin": str(oras_bin.resolve()),
        },
    )


def _run_bounded_command(
    command: Sequence[str],
    limit: int,
    timeout_seconds: int,
    *,
    classify_http_errors: bool = False,
) -> bytes:
    process: subprocess.Popen[bytes] | None = None
    try:
        process = subprocess.Popen(
            list(command),
            stdin=subprocess.DEVNULL,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE if classify_http_errors else subprocess.DEVNULL,
            env=dict(os.environ),
        )
        if process.stdout is None:
            raise ProducerRejectedError("public evidence command stdout is unavailable")
        deadline = time.monotonic() + timeout_seconds
        chunks: list[bytes] = []
        error_chunks: list[bytes] = []
        total = 0
        error_total = 0
        streams = {process.stdout.fileno(): (process.stdout, False)}
        if classify_http_errors and process.stderr is not None:
            streams[process.stderr.fileno()] = (process.stderr, True)
        while True:
            if not streams:
                break
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                raise ProducerInterrupted()
            readable, _, _ = select.select(list(streams), [], [], remaining)
            if not readable:
                raise ProducerInterrupted()
            for descriptor in readable:
                stream, is_error = streams[descriptor]
                chunk = os.read(descriptor, 64 * 1024)
                if not chunk:
                    stream.close()
                    del streams[descriptor]
                    continue
                if is_error:
                    error_total += len(chunk)
                    if error_total <= 16 * 1024:
                        error_chunks.append(chunk)
                    continue
                total += len(chunk)
                if total > limit:
                    raise ProducerRejectedError("public evidence command output violates byte limit")
                chunks.append(chunk)
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            raise ProducerInterrupted()
        return_code = process.wait(timeout=remaining)
    except subprocess.TimeoutExpired as exc:
        raise ProducerInterrupted() from exc
    except BaseException:
        if process is not None and process.poll() is None:
            process.kill()
            process.wait()
        raise
    finally:
        if process is not None and process.stdout is not None and not process.stdout.closed:
            process.stdout.close()
        if process is not None and process.stderr is not None and not process.stderr.closed:
            process.stderr.close()
    if return_code != 0:
        if classify_http_errors:
            error = b"".join(error_chunks).decode("utf-8", errors="replace")
            match = re.search(r"\bHTTP\s+([1-5][0-9]{2})\b", error, re.IGNORECASE)
            if match is not None:
                raise HttpStatusError(int(match.group(1)))
        raise ProducerRejectedError("public evidence command failed")
    output = b"".join(chunks)
    if not output:
        raise ProducerRejectedError("public evidence command output violates byte limit")
    return output


def _validated_repository(value: str) -> str:
    if value != "bluetape4k/bluetape4k-image":
        raise ProducerValidationError("repository is outside the producer contract")
    return value


def _resolved_tool(value: str | Path | None, name: str) -> Path:
    candidate = str(value) if value is not None else (shutil.which(name) or "")
    path = Path(candidate)
    if not path.is_absolute():
        located = shutil.which(candidate)
        path = Path(located) if located else path
    try:
        metadata = path.stat()
    except OSError as exc:
        raise ProducerBlockedError(f"{name} executable is unavailable") from exc
    if not path.is_absolute() or not stat.S_ISREG(metadata.st_mode) or not os.access(path, os.X_OK):
        raise ProducerBlockedError(f"{name} executable is unavailable")
    return path.resolve()


def _operation_timeout(args: argparse.Namespace) -> int:
    timeout_seconds = getattr(args, "operation_timeout_seconds", 60)
    if type(timeout_seconds) is not int or not 1 <= timeout_seconds <= 60:
        raise ProducerValidationError("operation timeout must be between 1 and 60 seconds")
    return timeout_seconds


def _gh_json(
    args: argparse.Namespace,
    endpoint: str,
    *,
    jq: str | None = None,
    max_bytes: int | None = None,
    method: str = "GET",
    fields: Sequence[str] = (),
) -> dict[str, Any]:
    gh_bin = _resolved_tool(getattr(args, "gh_bin", None), "gh")
    operation_timeout = _operation_timeout(args)
    connect_timeout = getattr(args, "connect_timeout_seconds", 10)
    read_timeout = getattr(args, "read_timeout_seconds", 30)
    if (
        type(connect_timeout) is not int
        or type(read_timeout) is not int
        or not 1 <= connect_timeout <= 60
        or not 1 <= read_timeout <= 60
    ):
        raise ProducerValidationError("GitHub API timeout limits are invalid")
    command = [str(gh_bin), "api", "--method", method, endpoint]
    for field in fields:
        command.extend(("-f", field))
    if jq is not None:
        command.extend(("--jq", jq))
    limit = max_bytes or getattr(args, "max_page_bytes", 2 * 1024 * 1024)

    def invoke() -> bytes:
        try:
            return _run_bounded_command(
                command,
                limit,
                min(read_timeout, max(1, (operation_timeout - 6) // 3)),
                classify_http_errors=True,
            )
        except ProducerInterrupted as exc:
            raise TimeoutError("GitHub API operation timed out") from exc

    try:
        raw, receipt = run_with_retry(
            f"github-api:{method}:{endpoint.split('?', 1)[0]}",
            "GITHUB_API",
            invoke,
            deadline_seconds=operation_timeout,
        )
    except HttpStatusError as exc:
        if exc.status in {401, 403}:
            raise ProducerBlockedError("GitHub API authorization is unavailable") from exc
        raise ProducerRejectedError("GitHub API rejected the request") from exc
    except ProducerValidationError as exc:
        if "transient" in str(exc) or "deadline" in str(exc):
            raise ProducerInterrupted() from exc
        raise
    receipts = getattr(args, "_retry_receipts", None)
    if receipts is None:
        receipts = []
        args._retry_receipts = receipts
    receipts.append(
        {
            **receipt,
            "connectTimeoutSeconds": connect_timeout,
            "readTimeoutSeconds": read_timeout,
            "operationTimeoutSeconds": operation_timeout,
        }
    )
    try:
        return load_json_bytes(raw, limit, max_depth=24, max_entries=20_000)
    except ProducerValidationError as exc:
        raise ProducerRejectedError("GitHub API returned malformed JSON") from exc


def _retry_receipts(args: argparse.Namespace) -> list[dict[str, Any]]:
    return list(getattr(args, "_retry_receipts", []))


def _workflow_path(value: Any) -> str:
    if not isinstance(value, str):
        raise ProducerRejectedError("workflow path is invalid")
    path = value.split("@", 1)[0]
    if path != ".github/workflows/paddleocr-producer.yml":
        raise ProducerRejectedError("workflow path differs")
    return path


def _normalized_run(value: Any) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise ProducerRejectedError("workflow run is invalid")
    required = ("id", "run_attempt", "head_sha", "path", "event", "status", "conclusion")
    if any(field not in value for field in required):
        raise ProducerRejectedError("workflow run is incomplete")
    run_id = value["id"]
    run_attempt = value["run_attempt"]
    if type(run_id) is not int or run_id <= 0 or type(run_attempt) is not int or run_attempt <= 0:
        raise ProducerRejectedError("workflow run identity is invalid")
    head_sha = value["head_sha"]
    if not isinstance(head_sha, str) or re.fullmatch(r"[0-9a-f]{40}", head_sha) is None:
        raise ProducerRejectedError("workflow head SHA is invalid")
    if value["event"] != "workflow_dispatch":
        raise ProducerRejectedError("workflow event differs")
    status = value["status"]
    conclusion = value["conclusion"]
    if not isinstance(status, str) or conclusion is not None and not isinstance(conclusion, str):
        raise ProducerRejectedError("workflow state is invalid")
    return {
        "databaseId": run_id,
        "runAttempt": run_attempt,
        "headSha": head_sha,
        "workflowPath": _workflow_path(value["path"]),
        "event": value["event"],
        "status": status,
        "conclusion": conclusion,
    }


def _workflow_runs(args: argparse.Namespace) -> list[list[dict[str, Any]]]:
    _validated_repository(args.repo)
    max_pages = getattr(args, "max_pages", 20)
    max_items = getattr(args, "max_page_items", 100)
    max_total = getattr(args, "max_total_bytes", 40 * 1024 * 1024)
    if not 1 <= max_pages <= 20 or not 1 <= max_items <= 100 or max_total > 40 * 1024 * 1024:
        raise ProducerValidationError("workflow pagination limits exceed the contract")
    pages: list[list[dict[str, Any]]] = []
    observed_bytes = 0
    for page in range(1, max_pages + 1):
        endpoint = (
            f"repos/{args.repo}/actions/workflows/{args.workflow}/runs"
            f"?branch={quote(args.branch)}&event={quote(args.event)}&per_page={max_items}&page={page}"
        )
        document = _gh_json(args, endpoint, jq='{\"items\":.workflow_runs}')
        items = document.get("items")
        if not isinstance(items, list) or len(items) > max_items:
            raise ProducerRejectedError("workflow run page violates item limits")
        normalized = [_normalized_run(item) for item in items]
        observed_bytes += len(jcs_bytes(document))
        if observed_bytes > max_total:
            raise ProducerRejectedError("workflow run pages exceed total byte limit")
        pages.append(normalized)
        if len(items) < max_items:
            return pages
    raise ProducerRejectedError("workflow run pagination exceeds page limit")


def _snapshot_workflow_runs(args: argparse.Namespace) -> dict[str, Any]:
    pages = _workflow_runs(args)
    run_ids = [run["databaseId"] for page in pages for run in page]
    if len(run_ids) != len(set(run_ids)):
        raise ProducerRejectedError("workflow run snapshot contains duplicate IDs")
    snapshot = {"runIds": run_ids}
    output_sha = _atomic_write_new(args.output, jcs_bytes(snapshot))
    return _success(
        "snapshot-workflow-runs", {**snapshot, "outputSha256": output_sha}
    )


def _readback_workflow_run(args: argparse.Namespace) -> dict[str, Any]:
    _validated_repository(args.repo)
    document = _gh_json(
        args,
        f"repos/{args.repo}/actions/runs/{args.run_id}/attempts/{args.run_attempt}",
    )
    run = _normalized_run(document)
    if run["databaseId"] != args.run_id or run["runAttempt"] != args.run_attempt:
        raise ProducerRejectedError("workflow run identity differs")
    if run["headSha"] != args.expected_head:
        raise ProducerRejectedError("workflow run head differs")
    receipt = {**run, "retryReceipts": _retry_receipts(args)}
    output_sha = _atomic_write_new(args.output, jcs_bytes(receipt))
    return _success(
        "readback-workflow-run", {**receipt, "outputSha256": output_sha}
    )


def _workflow_jobs(args: argparse.Namespace) -> list[dict[str, Any]]:
    _validated_repository(args.repo)
    document = _gh_json(
        args,
        f"repos/{args.repo}/actions/runs/{args.run_id}/attempts/{args.run_attempt}/jobs?per_page=100",
        jq='{\"items\":.jobs}',
    )
    jobs = document.get("items")
    if not isinstance(jobs, list) or len(jobs) > 100:
        raise ProducerRejectedError("workflow jobs violate item limits")
    normalized = []
    for job in jobs:
        if not isinstance(job, dict) or any(field not in job for field in ("name", "status", "conclusion")):
            raise ProducerRejectedError("workflow job is incomplete")
        if not all(job[field] is None or isinstance(job[field], str) for field in ("name", "status", "conclusion")):
            raise ProducerRejectedError("workflow job state is invalid")
        normalized.append({field: job[field] for field in ("name", "status", "conclusion")})
    return normalized


def _wait_workflow(args: argparse.Namespace, *, job_name: str | None) -> dict[str, Any]:
    if not 1 <= args.deadline_seconds <= 7200 or not 1 <= args.poll_seconds <= 60:
        raise ProducerValidationError("workflow wait limits exceed the contract")
    deadline = time.monotonic() + args.deadline_seconds
    while True:
        if job_name is None:
            document = _gh_json(
                args,
                f"repos/{args.repo}/actions/runs/{args.run_id}/attempts/{args.run_attempt}",
            )
            state = _normalized_run(document)
            if state["databaseId"] != args.run_id or state["runAttempt"] != args.run_attempt:
                raise ProducerRejectedError("workflow run identity differs")
            if state["headSha"] != args.expected_head:
                raise ProducerRejectedError("workflow run head differs")
        else:
            matches = [job for job in _workflow_jobs(args) if job["name"] == job_name]
            if len(matches) != 1:
                raise ProducerRejectedError("workflow job is absent or ambiguous")
            state = {
                "runId": args.run_id,
                "runAttempt": args.run_attempt,
                "jobName": job_name,
                **matches[0],
            }
        if state["status"] == "completed":
            return state
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            raise ProducerInterrupted()
        time.sleep(min(args.poll_seconds, remaining))


def _wait_workflow_job(args: argparse.Namespace) -> dict[str, Any]:
    state = _wait_workflow(args, job_name=args.job_name)
    return _success(
        "wait-workflow-job", {**state, "retryReceipts": _retry_receipts(args)}
    )


def _wait_workflow_run(args: argparse.Namespace) -> dict[str, Any]:
    state = _wait_workflow(args, job_name=None)
    receipt = {**state, "retryReceipts": _retry_receipts(args)}
    output_sha = _atomic_write_new(args.output, jcs_bytes(receipt))
    return _success("wait-workflow-run", {**receipt, "outputSha256": output_sha})


def _package_metadata(args: argparse.Namespace, owner: str, package: str) -> dict[str, Any]:
    document = _gh_json(
        args, f"orgs/{owner}/packages/container/{quote(package, safe='')}"
    )
    if document.get("name") != package or document.get("package_type") != "container":
        raise ProducerRejectedError("package identity differs")
    visibility = document.get("visibility")
    if visibility not in {"private", "public", "internal"}:
        raise ProducerRejectedError("package visibility is invalid")
    return {"package": package, "visibility": visibility, "packageId": document.get("id")}


def _package_version(args: argparse.Namespace, owner: str, package: str, tag: str) -> dict[str, Any] | None:
    if owner != "bluetape4k":
        raise ProducerValidationError("package owner is outside the producer contract")
    max_pages = min(getattr(args, "max_pages", 20), 20)
    max_items = min(getattr(args, "max_page_items", 100), 100)
    max_total = min(getattr(args, "max_total_bytes", 40 * 1024 * 1024), 40 * 1024 * 1024)
    pages = []
    observed_bytes = 0
    for page in range(1, max_pages + 1):
        document = _gh_json(
            args,
            f"orgs/{owner}/packages/container/{quote(package, safe='')}/versions"
            f"?per_page={max_items}&page={page}",
            jq='{\"items\":.}',
        )
        items = document.get("items")
        if not isinstance(items, list) or len(items) > max_items:
            raise ProducerRejectedError("package versions are invalid")
        observed_bytes += len(jcs_bytes(document))
        if observed_bytes > max_total:
            raise ProducerRejectedError("package versions exceed total byte limit")
        pages.append(items)
        if len(items) < max_items:
            break
    else:
        raise ProducerRejectedError("package version pagination exceeds page limit")
    selected = select_exact_version(pages, tag)
    if selected is None:
        return None
    return {
        "package": package,
        "versionId": selected["id"],
        "attemptTag": tag,
        "manifestDigest": selected["name"],
    }


def _readback_packages(args: argparse.Namespace) -> dict[str, Any]:
    if args.image_tag != "image-" + args.attempt_id or args.evidence_tag != "evidence-" + args.attempt_id:
        raise ProducerValidationError("package tags differ from attemptId")
    staging_meta = _package_metadata(args, args.owner, args.staging_package)
    release_meta = _package_metadata(args, args.owner, args.release_package)
    staging = _package_version(args, args.owner, args.staging_package, args.image_tag)
    release = _package_version(args, args.owner, args.release_package, args.image_tag)
    evidence = _package_version(args, args.owner, args.release_package, args.evidence_tag)
    for artifact, metadata in ((staging, staging_meta), (release, release_meta), (evidence, release_meta)):
        if artifact is not None:
            artifact["visibility"] = metadata["visibility"]
    return _success(
        "readback-packages",
        {
            "staging": staging,
            "release": release,
            "evidence": evidence,
            "retryReceipts": _retry_receipts(args),
        },
    )


def _readback_public_gate(args: argparse.Namespace) -> dict[str, Any]:
    package = _package_metadata(args, args.repo.split("/", 1)[0], args.package)
    if package["visibility"] != "public":
        raise ProducerBlockedError("release package is not public")
    jobs = _workflow_jobs(args)
    required = {
        "PaddleOCR producer / consumer-verify-public",
        "PaddleOCR producer / cleanup-aggregate",
        "PaddleOCR producer / release-readback-finalize",
    }
    selected = {job["name"]: job for job in jobs if job["name"] in required}
    if set(selected) != required or any(job["conclusion"] != "success" for job in selected.values()):
        raise ProducerBlockedError("public workflow gate is incomplete")
    if re.fullmatch(r"[0-9a-f]{40}", args.expected_head) is None:
        raise ProducerValidationError("expected head must be a full commit SHA")
    result = {
            "runId": args.run_id,
            "runAttempt": args.run_attempt,
            "expectedHead": args.expected_head,
            "visibility": package["visibility"],
            "jobs": selected,
            "retryReceipts": _retry_receipts(args),
    }
    output_sha = _atomic_write_new(args.output, jcs_bytes(result))
    return _success("readback-public-gate", {**result, "outputSha256": output_sha})


def _open_or_link_incident(args: argparse.Namespace) -> dict[str, Any]:
    _validated_repository(args.repo)
    reconciliation, raw = _load_canonical_document(args.reconciliation, contract_error=True)
    validated = validate_reconciliation(reconciliation)
    expected_sha = require_sha256(args.expected_sha256, "expectedSha256")
    if sha256_hex(raw) != expected_sha:
        raise ProducerValidationError("reconciliation SHA-256 differs")
    expected_marker = f"issue-638-incident:{expected_sha}"
    if args.approval_marker != expected_marker:
        raise ProducerValidationError("incident approval marker is absent or stale")
    comment_marker = f"<!-- issue-638-reconciliation:{expected_sha} -->"
    body = (
        f"Issue #638 producer incident: attempt `{validated['attemptId']}`, "
        f"status `{validated['producerStatus']}`, reconciliation `{expected_sha}`.\n\n"
        f"{comment_marker}"
    )
    items: list[Any] = []
    for page in range(1, 21):
        existing = _gh_json(
            args,
            f"repos/{args.repo}/issues/638/comments?per_page=100&page={page}",
            jq='{"items":.}',
        )
        page_items = existing.get("items")
        if not isinstance(page_items, list) or len(page_items) > 100:
            raise ProducerRejectedError("incident comment read-back is invalid")
        items.extend(page_items)
        if len(page_items) < 100:
            break
    else:
        raise ProducerRejectedError("incident comment pagination exceeds page limit")
    matches = [
        item for item in items
        if isinstance(item, dict)
        and isinstance(item.get("body"), str)
        and comment_marker in item["body"]
    ]
    if len(matches) > 1:
        raise ProducerRejectedError("duplicate incident comments exist")
    if matches:
        incident_url = matches[0].get("html_url")
    else:
        gh_bin = _resolved_tool(args.gh_bin, "gh")
        raw_url = _run_bounded_command(
            [str(gh_bin), "issue", "comment", "638", "--repo", args.repo, "--body", body],
            8 * 1024,
            _operation_timeout(args),
        )
        incident_url = raw_url.decode("utf-8", errors="strict").strip()
    if re.fullmatch(
        r"https://github\.com/bluetape4k/bluetape4k-image/issues/638#issuecomment-[1-9][0-9]*",
        incident_url,
    ) is None:
        raise ProducerRejectedError("incident comment URL is invalid")
    acknowledged_at = datetime.datetime.now(datetime.timezone.utc).isoformat().replace("+00:00", "Z")
    incident = {"incidentUrl": incident_url, "ownerAcknowledgedAt": acknowledged_at}
    output_sha = _atomic_write_new(args.output, jcs_bytes(incident))
    return _success(
        "open-or-link-incident",
        {
            "incidentUrl": incident_url,
            "statusChangedAt": validated["statusChangedAt"],
            "approvalMarkerSha256": sha256_hex(args.approval_marker.encode()),
            "outputSha256": output_sha,
        },
    )


def _readback_incident(args: argparse.Namespace) -> dict[str, Any]:
    _validated_repository(args.repo)
    reconciliation, raw = _load_canonical_document(args.reconciliation, contract_error=True)
    validated = validate_reconciliation(reconciliation)
    if validated["attemptId"] != f"{args.run_id}.{args.run_attempt}":
        raise ProducerValidationError("incident run identity differs")
    expected_sha = require_sha256(args.expected_sha256, "expectedSha256")
    if sha256_hex(raw) != expected_sha:
        raise ProducerValidationError("reconciliation SHA-256 differs")
    incident_url = validated["incidentUrl"]
    match = re.fullmatch(
        r"https://github\.com/bluetape4k/bluetape4k-image/issues/638#issuecomment-([1-9][0-9]*)",
        incident_url or "",
    )
    if match is None:
        raise ProducerValidationError("incident comment URL is invalid")
    live = _gh_json(args, f"repos/{args.repo}/issues/comments/{match.group(1)}")
    body = str(live.get("body", ""))
    marker = re.search(r"<!-- issue-638-reconciliation:([0-9a-f]{64}) -->", body)
    if (
        live.get("html_url") != incident_url
        or marker is None
        or f"attempt `{validated['attemptId']}`" not in body
    ):
        raise ProducerRejectedError("incident comment read-back differs")
    result = {
            "incidentUrl": incident_url,
            "ownerAcknowledgedAt": validated["ownerAcknowledgedAt"],
            "closedAt": validated["closedAt"],
            "cleanupVerified": validated["cleanupVerified"],
            "denylistVerified": validated["denylistVerified"],
            "visibilityReadBack": validated["visibilityReadBack"],
            "downstreamReadBack": validated["downstreamReadBack"],
            "reconciliationSha256": sha256_hex(raw),
    }
    output_sha = _atomic_write_new(args.output, jcs_bytes(result))
    return _success("readback-incident", {**result, "outputSha256": output_sha})


def _inspect_live_producer_settings(args: argparse.Namespace) -> dict[str, Any]:
    _validated_repository(args.repo)
    if re.fullmatch(r"[0-9a-f]{40}", args.expected_head) is None:
        raise ProducerValidationError("expected head must be a full commit SHA")
    commit = _gh_json(args, f"repos/{args.repo}/commits/{args.expected_head}")
    workflow = _gh_json(args, f"repos/{args.repo}/contents/{args.workflow}?ref={args.expected_head}")
    environment = _gh_json(args, f"repos/{args.repo}/environments/{args.environment}")
    rulesets = _gh_json(args, f"repos/{args.repo}/rulesets", jq='{\"items\":.}')
    packages = {}
    for name in (args.staging_package, args.release_package):
        packages[name] = _package_metadata(args, args.repo.split("/", 1)[0], name)
    if commit.get("sha") != args.expected_head:
        raise ProducerRejectedError("expected commit read-back differs")
    if (
        workflow.get("path") != args.workflow
        or not isinstance(workflow.get("sha"), str)
        or re.fullmatch(r"[0-9a-f]{40}", workflow["sha"]) is None
    ):
        raise ProducerRejectedError("workflow content read-back is incomplete")
    if environment.get("name") != args.environment:
        raise ProducerRejectedError("producer environment read-back differs")
    if not isinstance(rulesets.get("items"), list):
        raise ProducerRejectedError("repository ruleset read-back is incomplete")
    result = {
            "expectedHead": args.expected_head,
            "commitSha": commit["sha"],
            "workflowPath": args.workflow,
            "workflowBlobSha": workflow.get("sha"),
            "environment": environment.get("name"),
            "rulesets": rulesets.get("items"),
            "packages": packages,
    }
    output_sha = _atomic_write_new(args.output, jcs_bytes(result))
    return _success("inspect-live-producer-settings", {**result, "outputSha256": output_sha})


def _readback_retention(args: argparse.Namespace) -> dict[str, Any]:
    _validated_repository(args.repo)
    repository = _gh_json(args, f"repos/{args.repo}")
    run_days = repository.get("actions_retention_days")
    if type(run_days) is not int:
        raise ProducerBlockedError("GitHub API does not expose repository run retention")
    result = {
        "runDays": run_days,
        "artifactDays": run_days,
        "acceptedDays": args.required_accepted_days,
        "failedStagingDays": args.failed_staging_quarantine_days,
    }
    if run_days < args.required_run_days:
        raise ProducerBlockedError("repository run retention is below the producer minimum")
    output_sha = _atomic_write_new(args.output, jcs_bytes(result))
    return _success("readback-retention", {**result, "outputSha256": output_sha})


def _execute_known_good_rollback(args: argparse.Namespace) -> dict[str, Any]:
    _validated_repository(args.repo)
    if re.fullmatch(r"sha256:[0-9a-f]{64}", args.expected_current_digest) is None:
        raise ProducerValidationError("expectedCurrentDigest must be a sha256 OCI digest")
    if re.fullmatch(r"sha256:[0-9a-f]{64}", args.known_good_digest) is None:
        raise ProducerValidationError("knownGoodDigest must be a sha256 OCI digest")
    current = args.expected_current_digest
    known_good = args.known_good_digest
    plan, _ = _load_canonical_document(args.rollback_plan, contract_error=True)
    if plan.get("currentDigest") != current or plan.get("knownGoodDigest") != known_good or plan.get("dryRun") is not True:
        raise ProducerValidationError("rollback plan differs from requested mutation")
    marker = f"issue-638-rollback:{args.release_package_id}:{current}:{known_good}"
    if args.approval_marker != marker or args.mutation != "visibility-and-stable-tag":
        raise ProducerValidationError("rollback approval marker is absent or stale")
    owner = args.repo.split("/", 1)[0]
    package = "paddleocr-service"
    metadata = _package_metadata(args, owner, package)
    if metadata["packageId"] != args.release_package_id:
        raise ProducerRejectedError("release package ID differs")
    stable = _package_version(args, owner, package, "stable")
    if stable is None or stable["manifestDigest"] != current:
        raise ProducerRejectedError("current stable digest differs from approved rollback")
    gh_bin = _resolved_tool(args.gh_bin, "gh")
    oras_bin = _resolved_tool(args.oras_bin, "oras")
    registry_config = args.registry_config.resolve()
    if (
        args.registry_config.is_symlink()
        or not args.registry_config.is_file()
        or registry_config.stat().st_mode & 0o077
    ):
        raise ProducerBlockedError("registry config must be a private regular file")
    _run_bounded_command(
        [str(gh_bin), "api", "--method", "PATCH", f"orgs/{owner}/packages/container/{package}", "-f", "visibility=private"],
        2 * 1024 * 1024,
        _operation_timeout(args),
    )
    _run_bounded_command(
        [
            str(oras_bin),
            "copy",
            "--registry-config",
            str(registry_config),
            f"ghcr.io/{owner}/{package}@{known_good}",
            f"ghcr.io/{owner}/{package}:stable",
        ],
        64 * 1024,
        _operation_timeout(args),
    )
    result = {
        "packageId": args.release_package_id,
        "beforeDigest": current,
        "afterDigest": known_good,
        "visibility": "private",
        "stableTag": "stable",
        "approvalMarkerSha256": sha256_hex(args.approval_marker.encode()),
    }
    output_sha = _atomic_write_new(args.output, jcs_bytes(result))
    return _success("execute-known-good-rollback", {**result, "outputSha256": output_sha})


def _readback_known_good_rollback(args: argparse.Namespace) -> dict[str, Any]:
    _validated_repository(args.repo)
    if re.fullmatch(r"sha256:[0-9a-f]{64}", args.expected_digest) is None:
        raise ProducerValidationError("expectedDigest must be a sha256 OCI digest")
    expected = args.expected_digest
    owner = args.repo.split("/", 1)[0]
    metadata = _package_metadata(args, owner, "paddleocr-service")
    if metadata["packageId"] != args.release_package_id or metadata["visibility"] != "private":
        raise ProducerRejectedError("rollback package state differs")
    version = _package_version(args, owner, "paddleocr-service", "stable")
    if version is None or version["manifestDigest"] != expected:
        raise ProducerRejectedError("rollback stable tag digest differs")
    result = {
        "packageId": args.release_package_id,
        "digest": expected,
        "visibility": "private",
        "stableTag": "stable",
    }
    output_sha = _atomic_write_new(args.output, jcs_bytes(result))
    return _success("readback-known-good-rollback", {**result, "outputSha256": output_sha})


def _verify_public_evidence(args: argparse.Namespace) -> dict[str, Any]:
    if not 1 <= args.operation_timeout_seconds <= 60:
        raise ProducerValidationError("operation timeout exceeds the public verifier limit")
    if not 1 <= args.materialization_timeout_seconds <= 600:
        raise ProducerValidationError("materialization timeout exceeds the public verifier limit")
    limits = MaterializationLimits(
        manifest_timeout_seconds=args.operation_timeout_seconds,
        blob_read_timeout_seconds=args.operation_timeout_seconds,
        total_timeout_seconds=args.materialization_timeout_seconds,
    )

    def verify_attestation(artifact: Path, bundle: Path, predicate_type: str) -> None:
        raw = _run_bounded_command(
            [
                str(args.gh_bin),
                "attestation", "verify", str(artifact),
                "--repo", "bluetape4k/bluetape4k-image",
                "--bundle", str(bundle),
                "--signer-workflow", "bluetape4k/bluetape4k-image/.github/workflows/paddleocr-producer.yml",
                "--cert-oidc-issuer", "https://token.actions.githubusercontent.com",
                "--source-ref", "refs/heads/develop",
                "--predicate-type", predicate_type,
                "--deny-self-hosted-runners",
                "--format", "json",
            ],
            4 * 1024 * 1024,
            args.operation_timeout_seconds,
        )
        try:
            wrapped = load_json_bytes(b'{"results":' + raw + b"}", 4 * 1024 * 1024 + 12)
        except ProducerValidationError as exc:
            raise ProducerRejectedError("attestation verifier returned malformed JSON") from exc
        if not isinstance(wrapped["results"], list) or not wrapped["results"]:
            raise ProducerRejectedError("attestation verifier returned no verified result")

    result = verify_public_evidence(
        oras_bin=args.oras_bin,
        gh_bin=args.gh_bin,
        reference=args.ref,
        root=args.root,
        environment=dict(os.environ),
        run_command=_run_bounded_command,
        verify_attestation=verify_attestation,
        limits=limits,
    )
    return _success("verify-public-evidence", result)


def build_parser() -> StrictArgumentParser:
    parser = StrictArgumentParser(description="PaddleOCR trusted producer verifier")
    subparsers = parser.add_subparsers(dest="command", required=True)

    validate = subparsers.add_parser("validate-inputs")
    validate.add_argument("--lock", type=Path, required=True)
    validate.add_argument("--policy", type=Path, required=True)
    validate.add_argument("--legal", type=Path, required=True)
    validate.set_defaults(handler=_validate_inputs)

    trust = subparsers.add_parser("validate-trust-context")
    trust.add_argument("--lock", type=Path, required=True)
    trust.add_argument("--policy", type=Path, required=True)
    trust.add_argument("--legal", type=Path, required=True)
    trust.add_argument("--repository", required=True)
    trust.add_argument("--workflow", required=True)
    trust.add_argument("--ref", required=True)
    trust.add_argument("--actor", required=True)
    trust.add_argument("--runner-environment", required=True)
    trust.add_argument("--oidc-issuer", required=True)
    trust.add_argument("--audience", required=True)
    trust.set_defaults(handler=_validate_trust_context)

    input_value = subparsers.add_parser("input-value")
    input_value.add_argument("--lock", type=Path, required=True)
    input_value.add_argument("--field", choices=("sourceDateEpoch",), required=True)
    input_value.add_argument("--output", type=Path, required=True)
    input_value.set_defaults(handler=_input_value)

    accept = subparsers.add_parser("accept-resolved-inputs")
    accept.add_argument("--resolution-root", type=Path, required=True)
    accept.add_argument("--input-lock", type=Path, required=True)
    accept.add_argument("--requirements-lock", type=Path, required=True)
    accept.add_argument("--legal-inventory", type=Path, required=True)
    accept.set_defaults(handler=_accept_resolved_inputs)

    source_reproducibility = subparsers.add_parser("verify-source-reproducibility")
    source_reproducibility.add_argument("--input-lock", type=Path, required=True)
    source_reproducibility.add_argument("--work-root", type=Path, required=True)
    source_reproducibility.add_argument("--output-wheelhouse", type=Path, required=True)
    source_reproducibility.add_argument(
        "--prebuilt-wheel", type=Path, action="append", default=[]
    )
    source_reproducibility.add_argument("--registry-wheelhouse", type=Path)
    source_reproducibility.set_defaults(handler=_verify_source_reproducibility)

    dockerfile = subparsers.add_parser("verify-dockerfile")
    dockerfile.add_argument("--dockerfile", type=Path, required=True)
    dockerfile.add_argument("--input-lock", type=Path, required=True)
    dockerfile.set_defaults(handler=_verify_dockerfile)

    dispatch = subparsers.add_parser("validate-dispatch")
    dispatch.add_argument("--mode", required=True)
    dispatch.add_argument("--resume-attempt-id", required=True)
    dispatch.add_argument("--expected-prior-status", required=True)
    dispatch.add_argument("--expected-input-lock-sha256", required=True)
    dispatch.add_argument("--expected-staging-digest", required=True)
    dispatch.add_argument("--expected-release-digest", required=True)
    dispatch.add_argument("--expected-evidence-digest", required=True)
    dispatch.set_defaults(handler=_validate_dispatch)

    summary = subparsers.add_parser("write-step-summary")
    summary.add_argument("--input", type=Path, required=True)
    summary.add_argument("--summary", type=Path, required=True)
    summary.set_defaults(handler=_write_step_summary)

    stage = subparsers.add_parser("stage-inputs")
    stage.add_argument("--input-lock", type=Path, required=True)
    stage.add_argument("--requirements-lock", type=Path, required=True)
    stage.add_argument("--legal", type=Path, required=True)
    stage.add_argument("--wheelhouse", type=Path, required=True)
    stage.add_argument("--model-archive", action="append", default=[])
    stage.add_argument("--output-artifact", type=Path, required=True)
    stage.set_defaults(handler=_stage_inputs)

    fetch_locked = subparsers.add_parser("fetch-locked-inputs")
    fetch_locked.add_argument("--input-lock", type=Path, required=True)
    fetch_locked.add_argument("--kind", choices=("models", "packages"), required=True)
    fetch_locked.add_argument("--output-root", type=Path, required=True)
    fetch_locked.set_defaults(handler=_fetch_locked_inputs)

    stage_models = subparsers.add_parser("stage-models")
    stage_models.add_argument("--input-lock", type=Path, required=True)
    stage_models.add_argument("--legal", type=Path, required=True)
    stage_models.add_argument("--pipeline", type=Path, required=True)
    stage_models.add_argument("--model-archive", action="append", default=[])
    stage_models.add_argument("--run-id", type=int, required=True)
    stage_models.add_argument("--run-attempt", type=int, required=True)
    stage_models.add_argument("--artifact-name", required=True)
    stage_models.add_argument("--output-root", type=Path, required=True)
    stage_models.set_defaults(handler=_stage_models)

    seal_wheelhouse = subparsers.add_parser("seal-wheelhouse")
    seal_wheelhouse.add_argument("--input-lock", type=Path, required=True)
    seal_wheelhouse.add_argument("--requirements-lock", type=Path, required=True)
    seal_wheelhouse.add_argument("--wheelhouse", type=Path, required=True)
    seal_wheelhouse.add_argument("--run-id", type=int, required=True)
    seal_wheelhouse.add_argument("--run-attempt", type=int, required=True)
    seal_wheelhouse.add_argument("--artifact-name", required=True)
    seal_wheelhouse.add_argument("--output-root", type=Path, required=True)
    seal_wheelhouse.set_defaults(handler=_seal_wheelhouse)

    validate_build = subparsers.add_parser("validate-build-artifacts")
    validate_build.add_argument("--input-lock", type=Path, required=True)
    validate_build.add_argument("--models-root", type=Path, required=True)
    validate_build.add_argument("--models-artifact-id", type=int, required=True)
    validate_build.add_argument("--models-artifact-digest", required=True)
    validate_build.add_argument("--models-content-sha256", required=True)
    validate_build.add_argument("--wheelhouse-root", type=Path, required=True)
    validate_build.add_argument("--wheelhouse-artifact-id", type=int, required=True)
    validate_build.add_argument("--wheelhouse-artifact-digest", required=True)
    validate_build.add_argument("--wheelhouse-content-sha256", required=True)
    validate_build.add_argument("--run-id", type=int, required=True)
    validate_build.add_argument("--run-attempt", type=int, required=True)
    validate_build.set_defaults(handler=_validate_build_artifacts)

    create_handoff = subparsers.add_parser("create-oci-handoff")
    create_handoff.add_argument("--input-lock", type=Path, required=True)
    create_handoff.add_argument("--models-manifest", type=Path, required=True)
    create_handoff.add_argument("--oci-tar", type=Path, required=True)
    create_handoff.add_argument("--run-id", type=int, required=True)
    create_handoff.add_argument("--run-attempt", type=int, required=True)
    create_handoff.add_argument("--staging-artifact-sha256", required=True)
    create_handoff.add_argument("--build-receipt", type=Path, required=True)
    create_handoff.add_argument("--output", type=Path, required=True)
    create_handoff.set_defaults(handler=_create_oci_handoff)

    validate_oci = subparsers.add_parser("validate-oci-artifact")
    validate_oci.add_argument("--input-lock", type=Path, required=True)
    validate_oci.add_argument("--root", type=Path, required=True)
    validate_oci.add_argument("--artifact-id", type=int, required=True)
    validate_oci.add_argument("--artifact-digest", required=True)
    validate_oci.add_argument("--content-sha256", required=True)
    validate_oci.add_argument("--run-id", type=int, required=True)
    validate_oci.add_argument("--run-attempt", type=int, required=True)
    validate_oci.set_defaults(handler=_validate_oci_artifact)

    resolve = subparsers.add_parser("resolve-inputs")
    resolve.add_argument("--target-platform", required=True)
    resolve.add_argument("--python-version", required=True)
    resolve.add_argument("--base-image-candidate", required=True)
    resolve.add_argument("--paddleocr-commit", required=True)
    resolve.add_argument("--paddlex-commit", required=True)
    resolve.add_argument("--paddle-commit", required=True)
    resolve.add_argument("--detector", required=True)
    resolve.add_argument("--recognizer", required=True)
    resolve.add_argument("--accepted-root", type=Path, default=Path("docker/paddleocr"))
    resolve.add_argument("--output-root", type=Path, required=True)
    resolve.set_defaults(handler=_resolve_inputs)

    cleanup = subparsers.add_parser("merge-cleanup")
    cleanup.add_argument("--attempt-id", required=True)
    cleanup.add_argument("--original-status", choices=tuple(STATUS_CONTRACT), required=True)
    cleanup.add_argument("--started-job", action="append", default=[])
    cleanup.add_argument("--fragment", type=Path, action="append", default=[])
    cleanup.add_argument("--merged-at", required=True)
    cleanup.add_argument("--output", type=Path, required=True)
    cleanup.set_defaults(handler=_merge_cleanup)

    reconcile = subparsers.add_parser("reconcile")
    reconcile.add_argument("--reconciliation", type=Path, required=True)
    reconcile.set_defaults(handler=_reconcile)

    append = subparsers.add_parser("append-revocation")
    append.add_argument("--previous", type=Path, required=True)
    append.add_argument("--entry", type=Path, action="append", required=True)
    append.add_argument("--output", type=Path, required=True)
    append.set_defaults(handler=_append_revocation)

    emergency = subparsers.add_parser("verify-emergency-receipt")
    emergency.add_argument("--receipt", type=Path, required=True)
    emergency.add_argument("--revocations", type=Path, required=True)
    emergency.set_defaults(handler=_verify_emergency_receipt)

    finalize = subparsers.add_parser("finalize")
    finalize.add_argument("--reconciliation", type=Path, required=True)
    finalize.add_argument("--cleanup", type=Path, required=True)
    finalize.add_argument("--ledger-fragment", type=Path, required=True)
    finalize.add_argument("--revocations", type=Path, required=True)
    finalize.add_argument("--attempt-sha256", required=True)
    finalize.add_argument("--output", type=Path, required=True)
    finalize.set_defaults(handler=_finalize)

    finalize_run = subparsers.add_parser("finalize-run")
    finalize_run.add_argument("--input", type=Path, required=True)
    finalize_run.add_argument("--cleanup", type=Path, required=True)
    finalize_run.add_argument("--output", type=Path, required=True)
    finalize_run.set_defaults(handler=_finalize_run)

    selected_run = subparsers.add_parser("select-dispatched-run")
    selected_run.add_argument("--before", type=Path, required=True)
    selected_run.add_argument("--candidates", type=Path)
    selected_run.add_argument("--repo")
    selected_run.add_argument("--workflow")
    selected_run.add_argument("--branch")
    selected_run.add_argument("--event", default="workflow_dispatch")
    selected_run.add_argument("--gh-bin", type=Path)
    selected_run.add_argument("--operation-timeout-seconds", type=int, default=60)
    selected_run.add_argument("--connect-timeout-seconds", type=int, default=10)
    selected_run.add_argument("--read-timeout-seconds", type=int, default=30)
    selected_run.add_argument("--max-page-bytes", type=int, default=2 * 1024 * 1024)
    selected_run.add_argument("--max-page-items", type=int, default=100)
    selected_run.add_argument("--max-pages", type=int, default=20)
    selected_run.add_argument("--max-total-bytes", type=int, default=40 * 1024 * 1024)
    selected_run.add_argument("--expected-head", required=True)
    selected_run.add_argument("--expected-workflow", required=True)
    selected_run.add_argument("--output", type=Path)
    selected_run.set_defaults(handler=_select_dispatched_run)

    snapshot_runs = subparsers.add_parser("snapshot-workflow-runs")
    snapshot_runs.add_argument("--repo", required=True)
    snapshot_runs.add_argument("--workflow", required=True)
    snapshot_runs.add_argument("--branch", required=True)
    snapshot_runs.add_argument("--event", default="workflow_dispatch")
    snapshot_runs.add_argument("--gh-bin", type=Path)
    snapshot_runs.add_argument("--operation-timeout-seconds", type=int, default=60)
    snapshot_runs.add_argument("--connect-timeout-seconds", type=int, default=10)
    snapshot_runs.add_argument("--read-timeout-seconds", type=int, default=30)
    snapshot_runs.add_argument("--max-page-bytes", type=int, default=2 * 1024 * 1024)
    snapshot_runs.add_argument("--max-page-items", type=int, default=100)
    snapshot_runs.add_argument("--max-pages", type=int, default=20)
    snapshot_runs.add_argument("--max-total-bytes", type=int, default=40 * 1024 * 1024)
    snapshot_runs.add_argument("--output", type=Path, required=True)
    snapshot_runs.set_defaults(handler=_snapshot_workflow_runs)

    readback_run = subparsers.add_parser("readback-workflow-run")
    readback_run.add_argument("--repo", required=True)
    readback_run.add_argument("--run-id", type=int, required=True)
    readback_run.add_argument("--run-attempt", type=int, required=True)
    readback_run.add_argument("--expected-head", required=True)
    readback_run.add_argument("--gh-bin", type=Path)
    readback_run.add_argument("--operation-timeout-seconds", type=int, default=60)
    readback_run.add_argument("--connect-timeout-seconds", type=int, default=10)
    readback_run.add_argument("--read-timeout-seconds", type=int, default=30)
    readback_run.add_argument("--output", type=Path, required=True)
    readback_run.set_defaults(handler=_readback_workflow_run)

    wait_job = subparsers.add_parser("wait-workflow-job")
    wait_job.add_argument("--repo", required=True)
    wait_job.add_argument("--run-id", type=int, required=True)
    wait_job.add_argument("--run-attempt", type=int, required=True)
    wait_job.add_argument("--job-name", required=True)
    wait_job.add_argument("--gh-bin", type=Path)
    wait_job.add_argument("--operation-timeout-seconds", type=int, default=60)
    wait_job.add_argument("--connect-timeout-seconds", type=int, default=10)
    wait_job.add_argument("--read-timeout-seconds", type=int, default=30)
    wait_job.add_argument("--max-page-bytes", type=int, default=2 * 1024 * 1024)
    wait_job.add_argument("--max-page-items", type=int, default=100)
    wait_job.add_argument("--max-pages", type=int, default=20)
    wait_job.add_argument("--max-total-bytes", type=int, default=40 * 1024 * 1024)
    wait_job.add_argument("--poll-seconds", type=int, default=15)
    wait_job.add_argument("--deadline-seconds", type=int, default=7200)
    wait_job.set_defaults(handler=_wait_workflow_job)

    wait_run = subparsers.add_parser("wait-workflow-run")
    wait_run.add_argument("--repo", required=True)
    wait_run.add_argument("--run-id", type=int, required=True)
    wait_run.add_argument("--run-attempt", type=int, required=True)
    wait_run.add_argument("--expected-head", required=True)
    wait_run.add_argument("--gh-bin", type=Path)
    wait_run.add_argument("--operation-timeout-seconds", type=int, default=60)
    wait_run.add_argument("--connect-timeout-seconds", type=int, default=10)
    wait_run.add_argument("--read-timeout-seconds", type=int, default=30)
    wait_run.add_argument("--max-page-bytes", type=int, default=2 * 1024 * 1024)
    wait_run.add_argument("--max-page-items", type=int, default=100)
    wait_run.add_argument("--max-pages", type=int, default=20)
    wait_run.add_argument("--max-total-bytes", type=int, default=40 * 1024 * 1024)
    wait_run.add_argument("--poll-seconds", type=int, default=15)
    wait_run.add_argument("--deadline-seconds", type=int, default=7200)
    wait_run.add_argument("--output", type=Path, required=True)
    wait_run.set_defaults(handler=_wait_workflow_run)

    readback_packages = subparsers.add_parser("readback-packages")
    readback_packages.add_argument("--owner", required=True)
    readback_packages.add_argument("--release-package", required=True)
    readback_packages.add_argument("--staging-package", required=True)
    readback_packages.add_argument("--attempt-id", required=True)
    readback_packages.add_argument("--image-tag", required=True)
    readback_packages.add_argument("--evidence-tag", required=True)
    readback_packages.add_argument("--gh-bin", type=Path)
    readback_packages.add_argument("--operation-timeout-seconds", type=int, default=60)
    readback_packages.add_argument("--connect-timeout-seconds", type=int, default=10)
    readback_packages.add_argument("--read-timeout-seconds", type=int, default=30)
    readback_packages.add_argument("--max-page-bytes", type=int, default=2 * 1024 * 1024)
    readback_packages.add_argument("--max-page-items", type=int, default=100)
    readback_packages.add_argument("--max-pages", type=int, default=20)
    readback_packages.add_argument("--max-total-bytes", type=int, default=40 * 1024 * 1024)
    readback_packages.set_defaults(handler=_readback_packages)

    public_gate = subparsers.add_parser("readback-public-gate")
    public_gate.add_argument("--repo", required=True)
    public_gate.add_argument("--package", required=True)
    public_gate.add_argument("--run-id", type=int, required=True)
    public_gate.add_argument("--run-attempt", type=int, required=True)
    public_gate.add_argument("--expected-head", required=True)
    public_gate.add_argument("--gh-bin", type=Path)
    public_gate.add_argument("--operation-timeout-seconds", type=int, default=60)
    public_gate.add_argument("--connect-timeout-seconds", type=int, default=10)
    public_gate.add_argument("--read-timeout-seconds", type=int, default=30)
    public_gate.add_argument("--max-page-bytes", type=int, default=2 * 1024 * 1024)
    public_gate.add_argument("--max-page-items", type=int, default=100)
    public_gate.add_argument("--max-pages", type=int, default=20)
    public_gate.add_argument("--max-total-bytes", type=int, default=40 * 1024 * 1024)
    public_gate.add_argument("--output", type=Path, required=True)
    public_gate.set_defaults(handler=_readback_public_gate)

    prepare_reconcile = subparsers.add_parser("prepare-reconcile-inputs")
    prepare_reconcile.add_argument("--prior-result", type=Path, required=True)
    prepare_reconcile.add_argument("--prior-reconciliation", type=Path, required=True)
    prepare_reconcile.add_argument("--prior-evidence", type=Path, required=True)
    prepare_reconcile.add_argument("--prior-cleanup", type=Path, required=True)
    prepare_reconcile.add_argument("--output", type=Path, required=True)
    prepare_reconcile.set_defaults(handler=_prepare_reconcile_inputs)

    validate_reconcile = subparsers.add_parser("validate-reconcile-state")
    validate_reconcile.add_argument("--resume-attempt-id", required=True)
    validate_reconcile.add_argument("--expected-prior-status", required=True)
    validate_reconcile.add_argument("--expected-input-lock-sha256", required=True)
    validate_reconcile.add_argument("--expected-staging-digest", required=True)
    validate_reconcile.add_argument("--expected-release-digest", required=True)
    validate_reconcile.add_argument("--expected-evidence-digest", required=True)
    validate_reconcile.add_argument("--prior-state", type=Path, required=True)
    validate_reconcile.add_argument("--prior-result", type=Path, required=True)
    validate_reconcile.add_argument("--prior-evidence", type=Path, required=True)
    validate_reconcile.add_argument("--prior-reconciliation", type=Path, required=True)
    validate_reconcile.add_argument("--prior-cleanup", type=Path, required=True)
    validate_reconcile.add_argument("--package-readback", type=Path, required=True)
    validate_reconcile.add_argument("--input-lock", type=Path, required=True)
    validate_reconcile.add_argument("--output", type=Path, required=True)
    validate_reconcile.set_defaults(handler=_validate_reconcile_state)

    open_incident = subparsers.add_parser("open-or-link-incident")
    open_incident.add_argument("--repo", required=True)
    open_incident.add_argument("--reconciliation", type=Path, required=True)
    open_incident.add_argument("--expected-sha256", required=True)
    open_incident.add_argument("--approval-marker", required=True)
    open_incident.add_argument("--gh-bin", type=Path)
    open_incident.add_argument("--operation-timeout-seconds", type=int, default=60)
    open_incident.add_argument("--connect-timeout-seconds", type=int, default=10)
    open_incident.add_argument("--read-timeout-seconds", type=int, default=30)
    open_incident.add_argument("--output", type=Path, required=True)
    open_incident.set_defaults(handler=_open_or_link_incident)

    readback_incident = subparsers.add_parser("readback-incident")
    readback_incident.add_argument("--repo", required=True)
    readback_incident.add_argument("--run-id", type=int, required=True)
    readback_incident.add_argument("--run-attempt", type=int, required=True)
    readback_incident.add_argument("--reconciliation", type=Path, required=True)
    readback_incident.add_argument("--expected-sha256", required=True)
    readback_incident.add_argument("--gh-bin", type=Path)
    readback_incident.add_argument("--operation-timeout-seconds", type=int, default=60)
    readback_incident.add_argument("--connect-timeout-seconds", type=int, default=10)
    readback_incident.add_argument("--read-timeout-seconds", type=int, default=30)
    readback_incident.add_argument("--output", type=Path, required=True)
    readback_incident.set_defaults(handler=_readback_incident)

    acknowledge = subparsers.add_parser("acknowledge-incident")
    acknowledge.add_argument("--reconciliation", type=Path, required=True)
    acknowledge.add_argument("--expected-sha256", required=True)
    acknowledge.add_argument("--incident", type=Path, required=True)
    acknowledge.add_argument("--output", type=Path, required=True)
    acknowledge.set_defaults(handler=_acknowledge_incident)

    close = subparsers.add_parser("close-incident")
    close.add_argument("--reconciliation", type=Path, required=True)
    close.add_argument("--expected-sha256", required=True)
    close.add_argument("--incident", type=Path)
    close.add_argument("--closed-at")
    close.add_argument("--require-denylist", action="store_true")
    close.add_argument("--require-visibility", action="store_true")
    close.add_argument("--require-cleanup", action="store_true")
    close.add_argument("--require-downstream", action="store_true")
    close.add_argument("--output", type=Path, required=True)
    close.set_defaults(handler=_close_incident)

    rollback = subparsers.add_parser("plan-known-good-rollback")
    rollback.add_argument("--current-digest", required=True)
    rollback.add_argument("--candidates", type=Path, required=True)
    rollback.add_argument("--revocations", type=Path, required=True)
    rollback.add_argument("--require-downstream-notification", action="store_true")
    rollback.add_argument("--output", type=Path, required=True)
    rollback.set_defaults(handler=_plan_known_good_rollback)

    execute_rollback = subparsers.add_parser("execute-known-good-rollback")
    execute_rollback.add_argument("--repo", required=True)
    execute_rollback.add_argument("--release-package-id", type=int, required=True)
    execute_rollback.add_argument("--rollback-plan", type=Path, required=True)
    execute_rollback.add_argument("--approval-marker", required=True)
    execute_rollback.add_argument("--expected-current-digest", required=True)
    execute_rollback.add_argument("--known-good-digest", required=True)
    execute_rollback.add_argument("--mutation", required=True)
    execute_rollback.add_argument("--gh-bin", type=Path)
    execute_rollback.add_argument("--oras-bin", type=Path)
    execute_rollback.add_argument("--registry-config", type=Path, required=True)
    execute_rollback.add_argument("--operation-timeout-seconds", type=int, default=60)
    execute_rollback.add_argument("--connect-timeout-seconds", type=int, default=10)
    execute_rollback.add_argument("--read-timeout-seconds", type=int, default=30)
    execute_rollback.add_argument("--max-page-bytes", type=int, default=2 * 1024 * 1024)
    execute_rollback.add_argument("--max-page-items", type=int, default=100)
    execute_rollback.add_argument("--max-pages", type=int, default=20)
    execute_rollback.add_argument("--max-total-bytes", type=int, default=40 * 1024 * 1024)
    execute_rollback.add_argument("--output", type=Path, required=True)
    execute_rollback.set_defaults(handler=_execute_known_good_rollback)

    readback_rollback = subparsers.add_parser("readback-known-good-rollback")
    readback_rollback.add_argument("--repo", required=True)
    readback_rollback.add_argument("--release-package-id", type=int, required=True)
    readback_rollback.add_argument("--expected-digest", required=True)
    readback_rollback.add_argument("--gh-bin", type=Path)
    readback_rollback.add_argument("--operation-timeout-seconds", type=int, default=60)
    readback_rollback.add_argument("--connect-timeout-seconds", type=int, default=10)
    readback_rollback.add_argument("--read-timeout-seconds", type=int, default=30)
    readback_rollback.add_argument("--max-page-bytes", type=int, default=2 * 1024 * 1024)
    readback_rollback.add_argument("--max-page-items", type=int, default=100)
    readback_rollback.add_argument("--max-pages", type=int, default=20)
    readback_rollback.add_argument("--max-total-bytes", type=int, default=40 * 1024 * 1024)
    readback_rollback.add_argument("--output", type=Path, required=True)
    readback_rollback.set_defaults(handler=_readback_known_good_rollback)

    inspect_settings = subparsers.add_parser("inspect-live-producer-settings")
    inspect_settings.add_argument("--repo", required=True)
    inspect_settings.add_argument("--expected-head", required=True)
    inspect_settings.add_argument("--workflow", required=True)
    inspect_settings.add_argument("--environment", required=True)
    inspect_settings.add_argument("--staging-package", required=True)
    inspect_settings.add_argument("--release-package", required=True)
    inspect_settings.add_argument("--gh-bin", type=Path)
    inspect_settings.add_argument("--operation-timeout-seconds", type=int, default=60)
    inspect_settings.add_argument("--connect-timeout-seconds", type=int, default=10)
    inspect_settings.add_argument("--read-timeout-seconds", type=int, default=30)
    inspect_settings.add_argument("--max-page-bytes", type=int, default=2 * 1024 * 1024)
    inspect_settings.add_argument("--max-page-items", type=int, default=100)
    inspect_settings.add_argument("--max-pages", type=int, default=20)
    inspect_settings.add_argument("--max-total-bytes", type=int, default=40 * 1024 * 1024)
    inspect_settings.add_argument("--output", type=Path, required=True)
    inspect_settings.set_defaults(handler=_inspect_live_producer_settings)

    retention = subparsers.add_parser("readback-retention")
    retention.add_argument("--repo", required=True)
    retention.add_argument("--required-run-days", type=int, required=True)
    retention.add_argument("--required-accepted-days", type=int, required=True)
    retention.add_argument("--failed-staging-quarantine-days", type=int, required=True)
    retention.add_argument("--gh-bin", type=Path)
    retention.add_argument("--operation-timeout-seconds", type=int, default=60)
    retention.add_argument("--connect-timeout-seconds", type=int, default=10)
    retention.add_argument("--read-timeout-seconds", type=int, default=30)
    retention.add_argument("--output", type=Path, required=True)
    retention.set_defaults(handler=_readback_retention)

    verify = subparsers.add_parser("verify-attempt")
    verify.add_argument("--attempt", type=Path, required=True)
    verify.add_argument("--evidence", type=Path, required=True)
    verify.add_argument("--reconciliation", type=Path, required=True)
    verify.add_argument("--cleanup", type=Path, required=True)
    verify.add_argument("--ledger-fragment", type=Path, required=True)
    verify.add_argument("--revocations", type=Path, required=True)
    verify.set_defaults(handler=_verify_attempt)

    handoff = subparsers.add_parser("validate-oci-handoff")
    handoff.add_argument("--handoff", type=Path, required=True)
    handoff.set_defaults(handler=_validate_oci_handoff)

    evidence_manifest = subparsers.add_parser("validate-evidence-manifest")
    evidence_manifest.add_argument("--manifest", type=Path, required=True)
    evidence_manifest.set_defaults(handler=_validate_evidence_manifest)

    attestation_identity = subparsers.add_parser("validate-attestation-identity")
    attestation_identity.add_argument("--receipt", type=Path, required=True)
    attestation_identity.add_argument("--subject-name", required=True)
    attestation_identity.add_argument("--subject-digest", required=True)
    attestation_identity.add_argument("--predicate-type", required=True)
    attestation_identity.add_argument("--run-id", type=int, required=True)
    attestation_identity.add_argument("--run-attempt", type=int, required=True)
    attestation_identity.add_argument("--head-sha", required=True)
    attestation_identity.set_defaults(handler=_validate_attestation_identity)

    release_digests = subparsers.add_parser("validate-release-digests")
    release_digests.add_argument("--receipt", type=Path, required=True)
    release_digests.set_defaults(handler=_validate_release_digest_chain)

    platform_manifest = subparsers.add_parser("extract-platform-manifest")
    platform_manifest.add_argument("--oci-tar", type=Path, required=True)
    platform_manifest.add_argument("--handoff", type=Path, required=True)
    platform_manifest.add_argument("--output", type=Path, required=True)
    platform_manifest.set_defaults(handler=_extract_platform_manifest)

    spdx = subparsers.add_parser("create-spdx")
    spdx.add_argument("--input-lock", type=Path, required=True)
    spdx.add_argument("--legal", type=Path, required=True)
    spdx.add_argument("--run-id", type=int, required=True)
    spdx.add_argument("--run-attempt", type=int, required=True)
    spdx.add_argument("--output", type=Path, required=True)
    spdx.set_defaults(handler=_create_spdx)

    prepare_evidence = subparsers.add_parser("prepare-evidence")
    prepare_evidence.add_argument("--input-lock", type=Path, required=True)
    prepare_evidence.add_argument("--legal", type=Path, required=True)
    prepare_evidence.add_argument("--platform-manifest", type=Path, required=True)
    prepare_evidence.add_argument("--sbom", type=Path, required=True)
    prepare_evidence.add_argument("--provenance-bundle", type=Path, required=True)
    prepare_evidence.add_argument("--sbom-bundle", type=Path, required=True)
    prepare_evidence.add_argument("--staging-digest", required=True)
    prepare_evidence.add_argument("--release-digest", required=True)
    prepare_evidence.add_argument("--reconciliation-sha256", required=True)
    prepare_evidence.add_argument("--cleanup-sha256", required=True)
    prepare_evidence.add_argument("--revocations", type=Path, required=True)
    prepare_evidence.add_argument("--revocations-commit-sha", required=True)
    prepare_evidence.add_argument("--run-id", type=int, required=True)
    prepare_evidence.add_argument("--run-attempt", type=int, required=True)
    prepare_evidence.add_argument("--output-root", type=Path, required=True)
    prepare_evidence.set_defaults(handler=_prepare_evidence)

    evidence_oci = subparsers.add_parser("create-evidence-oci")
    evidence_oci.add_argument("--source-root", type=Path, required=True)
    evidence_oci.add_argument("--output-root", type=Path, required=True)
    evidence_oci.add_argument("--image-index-digest", required=True)
    evidence_oci.add_argument("--image-platform-digest", required=True)
    evidence_oci.add_argument("--image-config-digest", required=True)
    evidence_oci.add_argument("--base-digest", required=True)
    evidence_oci.add_argument("--run-id", type=int, required=True)
    evidence_oci.add_argument("--run-attempt", type=int, required=True)
    evidence_oci.add_argument("--input-lock-sha256", required=True)
    evidence_oci.add_argument("--revocations-sha256", required=True)
    evidence_oci.add_argument("--revocations-commit-sha", required=True)
    evidence_oci.set_defaults(handler=_create_evidence_oci)

    oras = subparsers.add_parser("bootstrap-oras")
    oras.add_argument("--archive", type=Path, required=True)
    oras.add_argument("--tool-root", type=Path, required=True)
    oras.set_defaults(handler=_bootstrap_oras)

    public_evidence = subparsers.add_parser("verify-public-evidence")
    public_evidence.add_argument("--oras-bin", type=Path, required=True)
    public_evidence.add_argument("--gh-bin", type=Path, required=True)
    public_evidence.add_argument("--ref", required=True)
    public_evidence.add_argument("--root", type=Path, required=True)
    public_evidence.add_argument("--operation-timeout-seconds", type=int, default=60)
    public_evidence.add_argument("--materialization-timeout-seconds", type=int, default=600)
    public_evidence.set_defaults(handler=_verify_public_evidence)
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    arguments = list(sys.argv[1:] if argv is None else argv)
    command = arguments[0] if arguments and not arguments[0].startswith("-") else "unknown"
    if any(argument in ("-h", "--help") for argument in arguments):
        build_parser().parse_args(arguments)
        return 0
    try:
        with installed_signal_handlers():
            args = build_parser().parse_args(arguments)
            document = args.handler(args)
        exit_code = 0
        if command == "verify-attempt":
            terminal_exit = STATUS_CONTRACT[document["producerStatus"]].exit_code
            exit_code = terminal_exit if terminal_exit is not None else 40
    except ProducerUsageError:
        document = _failure(command, "SCHEMA_INVALID", "BLOCKED_INPUT", "invalid command arguments")
        exit_code = 40
    except ProducerLegalError:
        document = _failure(command, "BLOCKED_LEGAL_INVENTORY", "LEGAL_INVENTORY", "legal inventory is incomplete or mismatched")
        exit_code = 11
    except ProducerBlockedError:
        document = _failure(command, "BLOCKED_INPUT", "BLOCKED_INPUT", "producer input is incomplete or invalid")
        exit_code = 10
    except ProducerRejectedError:
        document = _failure(command, "REJECTED", "REJECTED", "producer input was rejected")
        exit_code = 22
    except ProducerCancelled:
        document = _failure(command, "CANCELLED", "CANCELLED", "producer operation was cancelled")
        exit_code = 31
    except (ProducerInterrupted, TimeoutError):
        document = _failure(command, "INTERRUPTED", "INTERRUPTED", "producer operation was interrupted")
        exit_code = 32
    except KeyboardInterrupt:
        document = _failure(command, "CANCELLED", "CANCELLED", "producer operation was cancelled")
        exit_code = 31
    except ProducerValidationError:
        document = _failure(command, "SCHEMA_INVALID", "BLOCKED_INPUT", "input document violates its schema")
        exit_code = 40
    except OSError:
        LOGGER.error("producer command failed", extra={"command": command})
        document = _failure(command, "FAILED", "FAILED", "producer operation failed")
        exit_code = 30
    except Exception:  # noqa: BLE001 - the CLI boundary must emit one redacted result
        LOGGER.error("producer command failed", extra={"command": command})
        document = _failure(command, "FAILED", "FAILED", "producer operation failed")
        exit_code = 30
    sys.stdout.buffer.write(jcs_bytes(document) + b"\n")
    return exit_code


if __name__ == "__main__":
    raise SystemExit(main())
