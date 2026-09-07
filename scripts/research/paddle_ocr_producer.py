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
from urllib.parse import urlsplit

from paddle_ocr_producer_lib.contracts import (
    AttemptIdentity,
    ProducerValidationError,
    exact_object,
    jcs_bytes,
    load_json_bytes,
    require_sha256,
    sha256_hex,
    validate_input_lock,
)
from paddle_ocr_producer_lib.evidence import (
    MaterializationLimits,
    validate_evidence_file_manifest,
    validate_ledger_fragment,
    validate_oci_handoff,
    validate_producer_evidence,
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
    append_revocations,
    build_producer_result,
    merge_cleanup_fragments,
    validate_attempt,
    validate_cleanup_aggregate,
    validate_cleanup_fragment,
    validate_emergency_receipt,
    validate_reconciliation,
    validate_revocations,
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
    "schemaVersion", "attemptId", "producerStatus", "exitCode",
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
        "expectedStagingDigest": require_digest(expected_staging_digest, "staging digest"),
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
    command: Sequence[str], limit: int, timeout_seconds: int
) -> bytes:
    process: subprocess.Popen[bytes] | None = None
    try:
        process = subprocess.Popen(
            list(command),
            stdin=subprocess.DEVNULL,
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
            env=dict(os.environ),
        )
        if process.stdout is None:
            raise ProducerRejectedError("public evidence command stdout is unavailable")
        deadline = time.monotonic() + timeout_seconds
        chunks: list[bytes] = []
        total = 0
        while True:
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                raise ProducerInterrupted()
            readable, _, _ = select.select([process.stdout.fileno()], [], [], remaining)
            if not readable:
                raise ProducerInterrupted()
            chunk = os.read(process.stdout.fileno(), min(64 * 1024, limit + 1 - total))
            if not chunk:
                break
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
        if process is not None and process.stdout is not None:
            process.stdout.close()
    if return_code != 0:
        raise ProducerRejectedError("public evidence command failed")
    output = b"".join(chunks)
    if not output:
        raise ProducerRejectedError("public evidence command output violates byte limit")
    return output


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
