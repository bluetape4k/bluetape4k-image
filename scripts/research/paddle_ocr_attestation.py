from __future__ import annotations

"""Fail-closed GitHub attestation verification for Issue #609-D.

``gh attestation verify`` performs the cryptographic verification.  This module
only accepts its bounded JSON read-back after checking the authenticated
certificate identity, verified timestamp, statement subject, predicate, and
the exact local DSSE payload that was supplied to ``gh``.
"""

import argparse
import base64
import binascii
import json
import os
import re
import stat
import subprocess
import sys
from collections.abc import Mapping, Sequence
from pathlib import Path
from typing import Any

from paddle_ocr_producer_lib.contracts import (
    ProducerValidationError,
    exact_object,
    jcs_bytes,
    load_json_bytes,
    load_jsonl_bytes,
    require_sha256,
    sha256_hex,
)
from paddle_ocr_producer_lib.evidence import (
    validate_attestation_identity,
    validate_evidence_file_manifest,
)


class AttestationValidationError(ProducerValidationError):
    """Raised when attestation evidence cannot be bound to the expected subject."""


MAX_RESULT_BYTES = 4 * 1024 * 1024
MAX_BUNDLE_BYTES = 4 * 1024 * 1024
MAX_RECEIPT_BYTES = 1024 * 1024
_COMMIT_RE = r"[0-9a-f]{40}"
_DIGEST_RE = r"sha256:[0-9a-f]{64}"
_SUBJECT_DIGEST_RE = r"[0-9a-f]{64}"
_REPOSITORY = "bluetape4k/bluetape4k-image"
_WORKFLOW = ".github/workflows/paddleocr-producer.yml"
_REF = "refs/heads/develop"
_ISSUER = "https://token.actions.githubusercontent.com"
_RUNNER_ENVIRONMENT = "github-hosted"
_BUNDLE_MEDIA_TYPE = "application/vnd.dev.sigstore.bundle.v0.3+json"
_DSSE_PAYLOAD_TYPE = "application/vnd.in-toto+json"
_VERIFICATION_MEDIA_TYPE = (
    "application/vnd.dev.sigstore.verificationresult+json;version=0.1"
)
_PROVENANCE_PREDICATE = "https://slsa.dev/provenance/v1"
_SBOM_PREDICATE = "https://spdx.dev/Document/v2.3"

_GH_RESULT_KEYS = {"attestation", "verificationResult"}
_GH_ATTESTATION_KEYS = {"bundle", "bundle_url", "initiator"}
_GH_BUNDLE_KEYS = {"mediaType", "verificationMaterial", "dsseEnvelope"}
_GH_VERIFICATION_KEYS = {
    "mediaType",
    "signature",
    "verifiedTimestamps",
    "verifiedIdentity",
    "statement",
}
_GH_CERTIFICATE_KEYS = {
    "certificateIssuer",
    "subjectAlternativeName",
    "issuer",
    "githubWorkflowTrigger",
    "githubWorkflowSHA",
    "githubWorkflowName",
    "githubWorkflowRepository",
    "githubWorkflowRef",
    "buildSignerURI",
    "buildSignerDigest",
    "runnerEnvironment",
    "sourceRepositoryURI",
    "sourceRepositoryDigest",
    "sourceRepositoryRef",
    "sourceRepositoryIdentifier",
    "sourceRepositoryOwnerURI",
    "sourceRepositoryOwnerIdentifier",
    "buildConfigURI",
    "buildConfigDigest",
    "buildTrigger",
    "runInvocationURI",
    "sourceRepositoryVisibilityAtSigning",
}
_GH_VERIFIED_IDENTITY_KEYS = {
    "subjectAlternativeName",
    "issuer",
    "runnerEnvironment",
}


def _error(message: str) -> AttestationValidationError:
    return AttestationValidationError(message)


def _object(
    value: Any,
    field: str,
    required: set[str],
    optional: set[str] | None = None,
) -> dict[str, Any]:
    try:
        return exact_object(value, required=required, optional=optional)
    except ProducerValidationError as exc:
        raise _error(f"{field}: {exc}") from exc


def _string(value: Any, field: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise _error(f"{field} must be a non-empty string")
    return value


def _positive(value: Any, field: str) -> int:
    if type(value) is not int or value <= 0:
        raise _error(f"{field} must be a positive integer")
    return value


def _commit(value: Any, field: str) -> str:
    result = _string(value, field)
    if re.fullmatch(_COMMIT_RE, result) is None:
        raise _error(f"{field} must be a full lowercase commit SHA")
    return result


def _digest(value: Any, field: str) -> str:
    result = _string(value, field)
    if re.fullmatch(_DIGEST_RE, result) is None:
        raise _error(f"{field} must be a lowercase sha256 OCI digest")
    return result


def _subject_digest(value: Any, field: str) -> str:
    result = _string(value, field)
    if re.fullmatch(_SUBJECT_DIGEST_RE, result) is None:
        raise _error(f"{field} must be 64 lowercase hexadecimal characters")
    return result


def _matches(pattern: str, value: str, field: str) -> None:
    try:
        matched = re.match(pattern, value)
    except re.error as exc:
        raise _error(f"{field} is not a valid regular expression") from exc
    if matched is None:
        raise _error(f"{field} does not match the expected identity")


def _read_regular(path: Path, limit: int) -> bytes:
    flags = os.O_RDONLY | getattr(os, "O_CLOEXEC", 0) | getattr(os, "O_NOFOLLOW", 0)
    try:
        descriptor = os.open(path, flags)
    except OSError as exc:
        raise _error(f"attestation file is unavailable: {path}") from exc
    try:
        metadata = os.fstat(descriptor)
        if not stat.S_ISREG(metadata.st_mode):
            raise _error(f"attestation file must be regular: {path}")
        if metadata.st_size > limit:
            raise _error(f"attestation file exceeds byte limit: {path}")
        chunks: list[bytes] = []
        total = 0
        while True:
            chunk = os.read(descriptor, min(64 * 1024, limit + 1 - total))
            if not chunk:
                break
            total += len(chunk)
            if total > limit:
                raise _error(f"attestation file exceeds byte limit: {path}")
            chunks.append(chunk)
        return b"".join(chunks)
    finally:
        os.close(descriptor)


def _parse_gh_results(raw: bytes) -> list[dict[str, Any]]:
    if not isinstance(raw, bytes) or not raw:
        raise _error("gh attestation verifier returned empty output")
    if len(raw) > MAX_RESULT_BYTES:
        raise _error("gh attestation verifier output exceeds byte limit")
    try:
        wrapped = load_json_bytes(
            b'{"results":' + raw + b"}",
            MAX_RESULT_BYTES + 32,
            max_depth=64,
            max_entries=50_000,
        )
    except ProducerValidationError as exc:
        raise _error("gh attestation verifier returned malformed JSON") from exc
    results = wrapped.get("results")
    if not isinstance(results, list):
        raise _error("gh attestation verifier result must be an array")
    return results


def _statement(
    value: Any,
    *,
    expected_subject_name: str,
    expected_subject_digest: str,
    expected_predicate_type: str,
    field: str,
) -> dict[str, Any]:
    statement = _object(value, field, {"_type", "subject", "predicateType", "predicate"})
    if statement["_type"] != "https://in-toto.io/Statement/v1":
        raise _error(f"{field}._type differs")
    subjects = statement["subject"]
    if not isinstance(subjects, list) or len(subjects) != 1:
        raise _error(f"{field}.subject must contain exactly one subject")
    subject = _object(subjects[0], f"{field}.subject[0]", {"name", "digest"})
    if subject["name"] != expected_subject_name:
        raise _error(f"{field}.subject.name differs")
    digest = _object(subject["digest"], f"{field}.subject.digest", {"sha256"})
    if digest["sha256"] != expected_subject_digest.removeprefix("sha256:"):
        raise _error(f"{field}.subject.digest.sha256 differs")
    if statement["predicateType"] != expected_predicate_type:
        raise _error(f"{field}.predicateType differs")
    return statement


def _local_bundle(
    raw: bytes,
    *,
    expected_subject_name: str,
    expected_subject_digest: str,
    expected_predicate_type: str,
) -> dict[str, Any]:
    if not isinstance(raw, bytes) or not raw:
        raise _error("attestation bundle is empty")
    if len(raw) > MAX_BUNDLE_BYTES:
        raise _error("attestation bundle exceeds byte limit")
    try:
        parsed = load_jsonl_bytes(
            raw,
            MAX_BUNDLE_BYTES,
            max_lines=4,
            max_depth=64,
            max_entries=50_000,
        )
    except ProducerValidationError as exc:
        raise _error("attestation bundle is not strict JSONL") from exc
    if len(parsed.envelopes) != 1:
        raise _error("attestation bundle must contain exactly one envelope")
    envelope = _object(parsed.envelopes[0], "attestation bundle", _GH_BUNDLE_KEYS)
    if envelope["mediaType"] != _BUNDLE_MEDIA_TYPE:
        raise _error("attestation bundle mediaType differs")
    material = _object(
        envelope["verificationMaterial"],
        "attestation bundle.verificationMaterial",
        {"certificate", "tlogEntries", "timestampVerificationData"},
    )
    certificate = _object(
        material["certificate"],
        "attestation bundle.verificationMaterial.certificate",
        {"rawBytes"},
    )
    _string(certificate["rawBytes"], "attestation bundle certificate.rawBytes")
    tlog_entries = material["tlogEntries"]
    if not isinstance(tlog_entries, list) or not tlog_entries:
        raise _error("attestation bundle must contain a transparency log entry")
    if any(not isinstance(entry, dict) for entry in tlog_entries):
        raise _error("attestation bundle tlog entries must be objects")
    timestamp_data = _object(
        material["timestampVerificationData"],
        "attestation bundle.timestampVerificationData",
        {"rfc3161Timestamps"},
    )
    timestamps = timestamp_data["rfc3161Timestamps"]
    if not isinstance(timestamps, list):
        raise _error("attestation bundle RFC3161 timestamps must be an array")
    dsse = _object(envelope["dsseEnvelope"], "attestation bundle.dsseEnvelope", {"payload", "payloadType", "signatures"})
    if dsse["payloadType"] != _DSSE_PAYLOAD_TYPE:
        raise _error("attestation DSSE payloadType differs")
    payload_text = _string(dsse["payload"], "attestation DSSE payload")
    try:
        payload = base64.b64decode(payload_text, validate=True)
    except (binascii.Error, ValueError) as exc:
        raise _error("attestation DSSE payload is not valid base64") from exc
    if not payload or len(payload) > MAX_BUNDLE_BYTES:
        raise _error("attestation DSSE payload exceeds byte limit")
    try:
        statement = load_json_bytes(payload, MAX_BUNDLE_BYTES, max_depth=64, max_entries=50_000)
    except ProducerValidationError as exc:
        raise _error("attestation DSSE payload is not strict JSON") from exc
    statement = _statement(
        statement,
        expected_subject_name=expected_subject_name,
        expected_subject_digest=expected_subject_digest,
        expected_predicate_type=expected_predicate_type,
        field="attestation DSSE statement",
    )
    signatures = dsse["signatures"]
    if not isinstance(signatures, list) or len(signatures) != 1:
        raise _error("attestation DSSE must contain exactly one signature")
    signature = _object(signatures[0], "attestation DSSE signature", {"sig"}, {"keyid"})
    _string(signature["sig"], "attestation DSSE signature.sig")
    if "keyid" in signature and not isinstance(signature["keyid"], str):
        raise _error("attestation DSSE signature.keyid must be a string")
    return {
        "raw": raw,
        "envelope": envelope,
        "statement": statement,
        "payload": payload_text,
        "payloadSha256": sha256_hex(payload),
        "payloadType": dsse["payloadType"],
        "signature": signature["sig"],
        "signatureCount": len(signatures),
        "tlogCount": len(tlog_entries),
        "timestampCount": len(timestamps),
    }


def _validate_certificate(
    value: Any,
    *,
    expected_repository: str,
    expected_workflow: str,
    expected_ref: str,
    expected_workflow_sha: str,
    expected_run_id: int,
    expected_run_attempt: int,
) -> dict[str, Any]:
    certificate = _object(value, "verificationResult.signature.certificate", _GH_CERTIFICATE_KEYS)
    for field, child in certificate.items():
        _string(child, f"certificate.{field}")
    workflow_uri = f"https://github.com/{expected_repository}/{expected_workflow}@{expected_ref}"
    repository_uri = f"https://github.com/{expected_repository}"
    invocation = (
        f"{repository_uri}/actions/runs/{expected_run_id}/attempts/{expected_run_attempt}"
    )
    expected = {
        "subjectAlternativeName": workflow_uri,
        "issuer": _ISSUER,
        "githubWorkflowTrigger": "workflow_dispatch",
        "githubWorkflowSHA": expected_workflow_sha,
        "githubWorkflowRepository": expected_repository,
        "githubWorkflowRef": expected_ref,
        "buildSignerURI": workflow_uri,
        "buildSignerDigest": expected_workflow_sha,
        "runnerEnvironment": _RUNNER_ENVIRONMENT,
        "sourceRepositoryURI": repository_uri,
        "sourceRepositoryDigest": expected_workflow_sha,
        "sourceRepositoryRef": expected_ref,
        "buildConfigURI": workflow_uri,
        "buildConfigDigest": expected_workflow_sha,
        "buildTrigger": "workflow_dispatch",
        "runInvocationURI": invocation,
        "sourceRepositoryVisibilityAtSigning": "public",
    }
    for field, expected_value in expected.items():
        if certificate[field] != expected_value:
            raise _error(f"certificate.{field} differs")
    return certificate


def validate_gh_attestation_result(
    value: Any,
    *,
    bundle_raw: bytes,
    expected_subject_name: str,
    expected_subject_digest: str,
    expected_predicate_type: str,
    expected_repository: str = _REPOSITORY,
    expected_workflow: str = _WORKFLOW,
    expected_ref: str = _REF,
    expected_workflow_sha: str,
    expected_run_id: int,
    expected_run_attempt: int,
) -> dict[str, Any]:
    """Validate one parsed ``gh attestation verify --format json`` result."""

    if not isinstance(value, list) or len(value) != 1:
        raise _error("gh attestation verifier must return exactly one result")
    item = _object(value[0], "gh attestation result", _GH_RESULT_KEYS)
    local = _local_bundle(
        bundle_raw,
        expected_subject_name=expected_subject_name,
        expected_subject_digest=expected_subject_digest,
        expected_predicate_type=expected_predicate_type,
    )
    attestation = _object(item["attestation"], "gh attestation result.attestation", _GH_ATTESTATION_KEYS)
    if not isinstance(attestation["bundle_url"], str):
        raise _error("gh attestation result.attestation.bundle_url must be a string")
    if not isinstance(attestation["initiator"], str):
        raise _error("gh attestation result.attestation.initiator must be a string")
    returned_bundle = _object(attestation["bundle"], "gh attestation result.attestation.bundle", _GH_BUNDLE_KEYS)
    if returned_bundle["mediaType"] != local["envelope"]["mediaType"]:
        raise _error("gh attestation bundle mediaType differs from local bundle")
    returned_dsse = _object(returned_bundle["dsseEnvelope"], "gh attestation result.attestation.bundle.dsseEnvelope", {"payload", "payloadType", "signatures"})
    if returned_dsse["payloadType"] != local["payloadType"] or returned_dsse["payload"] != local["payload"]:
        raise _error("gh attestation DSSE payload differs from local bundle")
    returned_signatures = returned_dsse["signatures"]
    if not isinstance(returned_signatures, list) or len(returned_signatures) != 1:
        raise _error("gh attestation DSSE signature count differs")
    returned_signature = _object(returned_signatures[0], "gh attestation result DSSE signature", {"sig"})
    if returned_signature["sig"] != local["signature"]:
        raise _error("gh attestation DSSE signature differs from local bundle")
    returned_material = _object(
        returned_bundle["verificationMaterial"],
        "gh attestation result.attestation.bundle.verificationMaterial",
        {"certificate", "tlogEntries", "timestampVerificationData"},
    )
    returned_certificate = _object(returned_material["certificate"], "gh attestation result bundle certificate", {"rawBytes"})
    local_certificate = local["envelope"]["verificationMaterial"]["certificate"]
    if returned_certificate["rawBytes"] != local_certificate["rawBytes"]:
        raise _error("gh attestation certificate differs from local bundle")

    verification = _object(item["verificationResult"], "gh attestation result.verificationResult", _GH_VERIFICATION_KEYS)
    if verification["mediaType"] != _VERIFICATION_MEDIA_TYPE:
        raise _error("gh attestation verification mediaType differs")
    certificate = _validate_certificate(
        _object(verification["signature"], "verificationResult.signature", {"certificate"})["certificate"],
        expected_repository=expected_repository,
        expected_workflow=expected_workflow,
        expected_ref=expected_ref,
        expected_workflow_sha=expected_workflow_sha,
        expected_run_id=expected_run_id,
        expected_run_attempt=expected_run_attempt,
    )
    timestamps = verification["verifiedTimestamps"]
    if not isinstance(timestamps, list) or not timestamps:
        raise _error("gh attestation result has no verified timestamp")
    for index, timestamp in enumerate(timestamps):
        entry = _object(timestamp, f"verifiedTimestamps[{index}]", {"type", "uri", "timestamp"})
        if entry["type"] != "Tlog" or not _string(entry["uri"], f"verifiedTimestamps[{index}].uri").startswith("https://"):
            raise _error("gh attestation result timestamp is not a verified transparency log")
        _string(entry["timestamp"], f"verifiedTimestamps[{index}].timestamp")
    identity = _object(verification["verifiedIdentity"], "verificationResult.verifiedIdentity", _GH_VERIFIED_IDENTITY_KEYS)
    if identity["runnerEnvironment"] != _RUNNER_ENVIRONMENT:
        raise _error("gh attestation verifiedIdentity runner environment differs")
    identity_san = _object(identity["subjectAlternativeName"], "verifiedIdentity.subjectAlternativeName", {"subjectAlternativeName", "regexp"})
    identity_issuer = _object(identity["issuer"], "verifiedIdentity.issuer", {"issuer", "regexp"})
    _string(identity_san["regexp"], "verifiedIdentity.subjectAlternativeName.regexp")
    _string(identity_issuer["regexp"], "verifiedIdentity.issuer.regexp")
    _matches(
        identity_san["regexp"],
        f"https://github.com/{expected_repository}/{expected_workflow}@{expected_ref}",
        "verifiedIdentity.subjectAlternativeName.regexp",
    )
    _matches(
        identity_issuer["regexp"],
        _ISSUER,
        "verifiedIdentity.issuer.regexp",
    )
    statement = _statement(
        verification["statement"],
        expected_subject_name=expected_subject_name,
        expected_subject_digest=expected_subject_digest,
        expected_predicate_type=expected_predicate_type,
        field="verificationResult.statement",
    )
    if statement != local["statement"]:
        raise _error("gh attestation statement differs from local DSSE payload")
    normalized = {
        "schemaVersion": 1,
        "repository": expected_repository,
        "workflow": expected_workflow,
        "workflowRef": f"{expected_repository}/{expected_workflow}@{expected_ref}",
        "workflowSha": certificate["githubWorkflowSHA"],
        "ref": expected_ref,
        "environment": "paddleocr-producer",
        "runnerEnvironment": certificate["runnerEnvironment"],
        "issuer": certificate["issuer"],
        "sourceRepositoryURI": certificate["sourceRepositoryURI"],
        "sourceRepositoryDigest": certificate["sourceRepositoryDigest"],
        "sourceRepositoryRef": certificate["sourceRepositoryRef"],
        "subjectAlternativeName": certificate["subjectAlternativeName"],
        "signerIdentity": f"{expected_repository}/{expected_workflow}",
        "audience": "sigstore",
        "runId": expected_run_id,
        "runAttempt": expected_run_attempt,
        "headSha": certificate["sourceRepositoryDigest"],
        "subjectName": expected_subject_name,
        "subjectDigest": "sha256:" + statement["subject"][0]["digest"]["sha256"],
        "predicateType": expected_predicate_type,
        "verified": True,
    }
    identity_receipt = {
        field: normalized[field]
        for field in (
            "schemaVersion",
            "repository",
            "workflow",
            "workflowSha",
            "ref",
            "environment",
            "issuer",
            "audience",
            "runId",
            "runAttempt",
            "headSha",
            "subjectName",
            "subjectDigest",
            "predicateType",
            "verified",
        )
    }
    try:
        validate_attestation_identity(
            identity_receipt,
            expected_subject_name=expected_subject_name,
            expected_subject_digest=expected_subject_digest,
            expected_predicate_type=expected_predicate_type,
            expected_run_id=expected_run_id,
            expected_run_attempt=expected_run_attempt,
            expected_head_sha=expected_workflow_sha,
        )
    except ProducerValidationError as exc:
        raise _error(f"normalized attestation identity differs: {exc}") from exc
    return {
        **normalized,
        "bundleBytes": len(bundle_raw),
        "bundleSha256": sha256_hex(bundle_raw),
        "dssePayloadSha256": local["payloadSha256"],
        "dssePayloadType": local["payloadType"],
        "signatureCount": local["signatureCount"],
        "tlogCount": local["tlogCount"],
        "timestampCount": len(timestamps),
        "runInvocationURI": certificate["runInvocationURI"],
    }


def validate_attestation_pair(
    provenance_value: Any,
    sbom_value: Any,
    *,
    provenance_bundle_raw: bytes,
    sbom_bundle_raw: bytes,
    expected_subject_name: str,
    expected_subject_digest: str,
    expected_predicate_type: str = _PROVENANCE_PREDICATE,
    expected_sbom_predicate_type: str = _SBOM_PREDICATE,
    expected_repository: str = _REPOSITORY,
    expected_workflow: str = _WORKFLOW,
    expected_ref: str = _REF,
    expected_workflow_sha: str,
    expected_run_id: int,
    expected_run_attempt: int,
) -> dict[str, Any]:
    """Require provenance and SBOM to share one authenticated subject identity."""

    provenance = validate_gh_attestation_result(
        provenance_value,
        bundle_raw=provenance_bundle_raw,
        expected_subject_name=expected_subject_name,
        expected_subject_digest=expected_subject_digest,
        expected_predicate_type=expected_predicate_type,
        expected_repository=expected_repository,
        expected_workflow=expected_workflow,
        expected_ref=expected_ref,
        expected_workflow_sha=expected_workflow_sha,
        expected_run_id=expected_run_id,
        expected_run_attempt=expected_run_attempt,
    )
    sbom = validate_gh_attestation_result(
        sbom_value,
        bundle_raw=sbom_bundle_raw,
        expected_subject_name=expected_subject_name,
        expected_subject_digest=expected_subject_digest,
        expected_predicate_type=expected_sbom_predicate_type,
        expected_repository=expected_repository,
        expected_workflow=expected_workflow,
        expected_ref=expected_ref,
        expected_workflow_sha=expected_workflow_sha,
        expected_run_id=expected_run_id,
        expected_run_attempt=expected_run_attempt,
    )
    shared = (
        "repository",
        "workflow",
        "workflowSha",
        "ref",
        "environment",
        "runnerEnvironment",
        "issuer",
        "sourceRepositoryURI",
        "sourceRepositoryDigest",
        "sourceRepositoryRef",
        "subjectAlternativeName",
        "signerIdentity",
        "workflowRef",
        "runId",
        "runAttempt",
        "headSha",
        "subjectName",
        "subjectDigest",
        "runInvocationURI",
    )
    if any(provenance[field] != sbom[field] for field in shared):
        raise _error("provenance and SBOM attestation identities differ")
    return {
        "schemaVersion": 1,
        "scope": "609-D",
        "status": "PASS",
        "subjectName": provenance["subjectName"],
        "subjectDigest": provenance["subjectDigest"],
        "producer": {field: provenance[field] for field in shared if field not in {"subjectName", "subjectDigest"}},
        "attestations": [provenance, sbom],
        "sameSubject": True,
        "sameProducerIdentity": True,
        "verifiedTimestamps": True,
        "localBundlesBound": True,
        "predicates": [expected_predicate_type, expected_sbom_predicate_type],
    }


def _ledger_checksum(ledger: Mapping[str, Any]) -> str:
    checksum = _object(ledger.get("checksum"), "ledger.checksum", {"algorithm", "canonicalization", "covers", "sha256"})
    if checksum["algorithm"] != "SHA-256" or checksum["canonicalization"] != "JCS" or checksum["covers"] != "ledger-without-checksum":
        raise _error("#609-C ledger checksum contract differs")
    declared = _string(checksum["sha256"], "ledger.checksum.sha256")
    require_sha256(declared, "ledger.checksum.sha256")
    unsigned = dict(ledger)
    unsigned.pop("checksum", None)
    actual = sha256_hex(jcs_bytes(unsigned))
    if actual != declared:
        raise _error("#609-C ledger checksum differs")
    return declared


def _descriptor(value: Any, field: str) -> dict[str, Any]:
    descriptor = _object(value, field, {"path", "bytes", "sha256"})
    path = _string(descriptor["path"], f"{field}.path")
    parts = path.split("/")
    if (
        path.startswith("/")
        or "\\" in path
        or not parts
        or any(part in {"", ".", ".."} for part in parts)
        or len(parts) > 3
    ):
        raise _error(f"{field}.path is unsafe")
    if type(descriptor["bytes"]) is not int or descriptor["bytes"] <= 0:
        raise _error(f"{field}.bytes must be positive")
    require_sha256(descriptor["sha256"], f"{field}.sha256")
    return descriptor


def _verify_descriptor(root: Path, descriptor: Mapping[str, Any], field: str) -> bytes:
    relative = str(descriptor["path"])
    current = root
    parts = relative.split("/")
    for part in parts[:-1]:
        current = current / part
        if current.is_symlink() or not current.is_dir():
            raise _error(f"{field} path parent is not a regular directory")
    raw = _read_regular(current / parts[-1], MAX_BUNDLE_BYTES)
    if len(raw) != descriptor["bytes"] or sha256_hex(raw) != descriptor["sha256"]:
        raise _error(f"{field} differs from local bytes")
    return raw


def _run_gh(command: Sequence[str], timeout_seconds: int) -> bytes:
    if timeout_seconds <= 0:
        raise _error("attestation verifier timeout must be positive")
    try:
        completed = subprocess.run(
            list(command),
            stdin=subprocess.DEVNULL,
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
            env=dict(os.environ),
            timeout=timeout_seconds,
            check=False,
        )
    except (OSError, subprocess.TimeoutExpired) as exc:
        raise _error("gh attestation verifier did not complete") from exc
    if completed.returncode != 0:
        raise _error("gh attestation verifier rejected the bundle")
    if len(completed.stdout) > MAX_RESULT_BYTES:
        raise _error("gh attestation verifier output exceeds byte limit")
    return completed.stdout


def verify_evidence(
    *,
    evidence_root: Path,
    ledger_path: Path,
    gh_bin: Path,
    output: Path,
    repository: str = _REPOSITORY,
    workflow: str = _WORKFLOW,
    ref: str = _REF,
    subject_name: str = "ghcr.io/bluetape4k/paddleocr-service",
    timeout_seconds: int = 60,
) -> dict[str, Any]:
    """Verify both attestation bundles in a #638 evidence root and write a receipt."""

    if not evidence_root.is_dir() or evidence_root.is_symlink():
        raise _error("evidence root must be a non-symlink directory")
    if gh_bin.is_symlink() or not gh_bin.is_file() or not os.access(gh_bin, os.X_OK):
        raise _error("gh binary must be an executable regular file")
    manifest_raw = _read_regular(evidence_root / "evidence-manifest.json", MAX_BUNDLE_BYTES)
    try:
        manifest = load_json_bytes(manifest_raw, MAX_BUNDLE_BYTES, max_depth=32, max_entries=10_000)
        manifest = validate_evidence_file_manifest(manifest)
    except ProducerValidationError as exc:
        raise _error(f"evidence manifest is invalid: {exc}") from exc
    descriptors = {item["path"]: _descriptor(item, f"evidence-manifest.files[{index}]") for index, item in enumerate(manifest["files"])}
    platform_descriptor = descriptors.get("platform-manifest.json")
    if platform_descriptor is None:
        raise _error("evidence manifest is missing platform-manifest.json")
    platform_raw = _verify_descriptor(evidence_root, platform_descriptor, "platform manifest")
    subject_digest = manifest["subjectDigest"]
    if "sha256:" + sha256_hex(platform_raw) != subject_digest:
        raise _error("platform manifest bytes differ from evidence subject")
    ledger_raw = _read_regular(ledger_path, MAX_BUNDLE_BYTES)
    try:
        ledger = load_json_bytes(ledger_raw, MAX_BUNDLE_BYTES, max_depth=64, max_entries=50_000)
    except ProducerValidationError as exc:
        raise _error("#609-C ledger is malformed") from exc
    ledger_sha = _ledger_checksum(ledger)
    if ledger.get("status") != "PASS" or ledger.get("kind") != "IMAGE_MODEL_LEDGER":
        raise _error("#609-C ledger is not a PASS IMAGE_MODEL_LEDGER")
    scope = _object(ledger.get("scope"), "ledger.scope", {"gate", "deferred"})
    if scope["gate"] != "609-C" or "609-D" not in scope["deferred"]:
        raise _error("#609-C ledger does not preserve the #609-D gate")
    producer = _object(ledger.get("producer"), "ledger.producer", {
        "repository", "workflow", "workflowRef", "workflowSha", "ref", "workflowRunId",
        "sourceRevision", "builderIdentity", "runnerEnvironment", "signerIdentity", "oidcIssuer",
    })
    if producer["repository"] != repository or producer["workflow"] != workflow or producer["ref"] != ref:
        raise _error("ledger producer identity differs from the requested policy")
    if producer["workflowSha"] != producer["sourceRevision"]:
        raise _error("ledger workflow and source revisions differ")
    expected_workflow_sha = _commit(producer["workflowSha"], "ledger.producer.workflowSha")
    attempt_id = _string(manifest["attemptId"], "evidence-manifest.attemptId")
    run_id = _positive(manifest["runId"], "evidence-manifest.runId")
    run_attempt = _positive(manifest["runAttempt"], "evidence-manifest.runAttempt")
    if attempt_id != f"{run_id}.{run_attempt}" or producer["workflowRunId"] != attempt_id:
        raise _error("evidence and ledger run identities differ")
    image = _object(ledger.get("image"), "ledger.image", {
        "imageRef", "imageIndexDigest", "platformManifestDigest", "configDigest", "os", "architecture", "variant", "targetPlatform", "baseImageDigests", "packageLockSha256",
    })
    if image["platformManifestDigest"] != subject_digest:
        raise _error("ledger platform digest differs from evidence subject")
    _digest(subject_digest, "evidence subjectDigest")
    if image["imageIndexDigest"] != subject_digest or image["targetPlatform"] != "linux/amd64":
        raise _error("ledger image platform identity differs")
    for path in ("sbom.spdx.json", "attestations/provenance.bundle.jsonl", "attestations/sbom.bundle.jsonl"):
        if path not in descriptors:
            raise _error(f"evidence manifest is missing {path}")
        _verify_descriptor(evidence_root, descriptors[path], f"evidence {path}")
    provenance_raw = _read_regular(evidence_root / "attestations/provenance.bundle.jsonl", MAX_BUNDLE_BYTES)
    sbom_raw = _read_regular(evidence_root / "attestations/sbom.bundle.jsonl", MAX_BUNDLE_BYTES)
    platform_path = evidence_root / "platform-manifest.json"
    common = {
        "expected_subject_name": subject_name,
        "expected_subject_digest": subject_digest,
        "expected_repository": repository,
        "expected_workflow": workflow,
        "expected_ref": ref,
        "expected_workflow_sha": expected_workflow_sha,
        "expected_run_id": run_id,
        "expected_run_attempt": run_attempt,
    }
    gh_common = [
        str(gh_bin),
        "attestation", "verify", str(platform_path),
        "--repo", repository,
        "--signer-workflow", f"{repository}/{workflow}",
        "--signer-digest", expected_workflow_sha,
        "--source-digest", expected_workflow_sha,
        "--source-ref", ref,
        "--cert-oidc-issuer", _ISSUER,
        "--deny-self-hosted-runners",
        "--format", "json",
    ]
    provenance_raw_result = _run_gh(
        [*gh_common, "--bundle", str(evidence_root / "attestations/provenance.bundle.jsonl"), "--predicate-type", _PROVENANCE_PREDICATE],
        timeout_seconds,
    )
    sbom_raw_result = _run_gh(
        [*gh_common, "--bundle", str(evidence_root / "attestations/sbom.bundle.jsonl"), "--predicate-type", _SBOM_PREDICATE],
        timeout_seconds,
    )
    pair = validate_attestation_pair(
        _parse_gh_results(provenance_raw_result),
        _parse_gh_results(sbom_raw_result),
        provenance_bundle_raw=provenance_raw,
        sbom_bundle_raw=sbom_raw,
        **common,
    )
    evidence = {
        "manifest": {"path": "evidence-manifest.json", "bytes": len(manifest_raw), "sha256": sha256_hex(manifest_raw)},
        "ledger": {"path": ledger_path.name, "bytes": len(ledger_raw), "sha256": sha256_hex(ledger_raw)},
        "platformManifest": platform_descriptor,
        "spdx": descriptors["sbom.spdx.json"],
        "provenanceBundle": descriptors["attestations/provenance.bundle.jsonl"],
        "sbomBundle": descriptors["attestations/sbom.bundle.jsonl"],
    }
    receipt: dict[str, Any] = {
        "schemaVersion": 1,
        "kind": "ATTESTATION_RECEIPT",
        "status": "PASS",
        "scope": {"gate": "609-D", "deferred": ["609-E", "544-B", "547"]},
        "subject": {"name": pair["subjectName"], "digest": pair["subjectDigest"]},
        "producer": pair["producer"],
        "attestations": pair["attestations"],
        "evidence": evidence,
        "verification": {
            "ledgerChecksumMatches": True,
            "evidenceDescriptorsMatch": True,
            "provenanceSubjectMatches": True,
            "sbomSubjectMatches": True,
            "sameSubject": pair["sameSubject"],
            "sameProducerIdentity": pair["sameProducerIdentity"],
            "verifiedTimestamps": pair["verifiedTimestamps"],
            "localBundlesBound": pair["localBundlesBound"],
        },
    }
    receipt["checksum"] = {
        "algorithm": "SHA-256",
        "canonicalization": "JCS",
        "covers": "receipt-without-checksum",
        "sha256": sha256_hex(jcs_bytes(receipt)),
    }
    if output.exists() or output.is_symlink():
        raise _error(f"receipt output already exists: {output}")
    output.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    output.write_bytes(jcs_bytes(receipt))
    return {
        "status": "PASS",
        "kind": "ATTESTATION_RECEIPT",
        "scope": "609-D",
        "subjectDigest": pair["subjectDigest"],
        "ledgerSha256": ledger_sha,
        "receiptSha256": receipt["checksum"]["sha256"],
        "provenanceBundleSha256": pair["attestations"][0]["bundleSha256"],
        "sbomBundleSha256": pair["attestations"][1]["bundleSha256"],
    }


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--evidence-root", type=Path, required=True)
    parser.add_argument("--ledger", type=Path, required=True)
    parser.add_argument("--gh-bin", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--timeout-seconds", type=int, default=60)
    parser.add_argument("--repository", default=_REPOSITORY)
    parser.add_argument("--workflow", default=_WORKFLOW)
    parser.add_argument("--ref", default=_REF)
    parser.add_argument("--subject-name", default="ghcr.io/bluetape4k/paddleocr-service")
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = _parser().parse_args(argv)
    try:
        result = verify_evidence(
            evidence_root=args.evidence_root,
            ledger_path=args.ledger,
            gh_bin=args.gh_bin,
            output=args.output,
            repository=args.repository,
            workflow=args.workflow,
            ref=args.ref,
            subject_name=args.subject_name,
            timeout_seconds=args.timeout_seconds,
        )
    except (AttestationValidationError, OSError, ValueError) as exc:
        print(json.dumps({"status": "REJECTED", "error": str(exc)[:512]}, separators=(",", ":")), file=sys.stderr)
        return 1
    print(json.dumps(result, separators=(",", ":"), sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
