from __future__ import annotations

"""Strict lifecycle contracts for the PaddleOCR trusted producer."""

import re
from collections.abc import Mapping, Sequence
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Any

from .contracts import (
    ProducerValidationError,
    exact_object,
    jcs_bytes,
    require_sha256,
    sha256_hex,
)

_ATTEMPT_RE = re.compile(r"\A[1-9][0-9]*\.[1-9][0-9]*\Z")
_COMMIT_RE = re.compile(r"\A[0-9a-f]{40}\Z")
_OCI_DIGEST_RE = re.compile(r"\Asha256:[0-9a-f]{64}\Z")
_TOKEN_RE = re.compile(r"\A[A-Z][A-Z0-9_]*\Z")
_JOB_RE = re.compile(r"\A[a-z][a-z0-9-]*\Z")
_INCIDENT_RE = re.compile(
    r"\Ahttps://github\.com/bluetape4k/bluetape4k-image/(?:issues|pull)/[1-9][0-9]*(?:#issuecomment-[1-9][0-9]*)?\Z"
)

LAST_COMPLETED_STAGES = (
    "NONE",
    "STAGING",
    "EVIDENCE",
    "RELEASE",
    "PUBLIC_EVIDENCE",
)


@dataclass(frozen=True)
class ErrorContract:
    producer_status: str
    error_code: str | None
    exit_code: int | None
    mapped_609_status: str
    allowed_stages: tuple[str, ...]


STATUS_CONTRACT = {
    "VALIDATING": ErrorContract("VALIDATING", None, None, "PENDING", ("NONE",)),
    "BLOCKED_INPUT": ErrorContract("BLOCKED_INPUT", "BLOCKED_INPUT", 10, "BLOCKED", ("NONE",)),
    "BLOCKED_LEGAL_INVENTORY": ErrorContract(
        "BLOCKED_LEGAL_INVENTORY", "BLOCKED_LEGAL_INVENTORY", 11, "BLOCKED", ("NONE",)
    ),
    "BUILDING": ErrorContract("BUILDING", None, None, "PENDING", ("NONE",)),
    "PUBLISHED_UNVERIFIED": ErrorContract(
        "PUBLISHED_UNVERIFIED", "PARTIAL_PUBLICATION", 20, "PENDING", ("STAGING",)
    ),
    "PROMOTING": ErrorContract("PROMOTING", "PARTIAL_PUBLICATION", 20, "PENDING", ("EVIDENCE",)),
    "RELEASE_UNVERIFIED": ErrorContract(
        "RELEASE_UNVERIFIED", "PARTIAL_PUBLICATION", 20, "PENDING", ("RELEASE", "PUBLIC_EVIDENCE")
    ),
    "QUARANTINE_PENDING": ErrorContract(
        "QUARANTINE_PENDING", "PARTIAL_PUBLICATION", 20, "BLOCKED", ("PUBLIC_EVIDENCE",)
    ),
    "QUARANTINED": ErrorContract("QUARANTINED", "QUARANTINED", 21, "BLOCKED", ("PUBLIC_EVIDENCE",)),
    "PRODUCER_PASS": ErrorContract("PRODUCER_PASS", "NONE", 0, "PENDING", ("PUBLIC_EVIDENCE",)),
    "REJECTED": ErrorContract("REJECTED", "REJECTED", 22, "REJECTED", LAST_COMPLETED_STAGES),
    "REVOKED": ErrorContract("REVOKED", "REVOKED", 23, "REJECTED", LAST_COMPLETED_STAGES),
    "FAILED": ErrorContract("FAILED", "FAILED", 30, "BLOCKED", LAST_COMPLETED_STAGES),
    "CANCELLED": ErrorContract("CANCELLED", "CANCELLED", 31, "BLOCKED", LAST_COMPLETED_STAGES),
    "INTERRUPTED": ErrorContract("INTERRUPTED", "INTERRUPTED", 32, "BLOCKED", LAST_COMPLETED_STAGES),
}

SCHEMA_ERROR_CONTRACT = ErrorContract(
    "BLOCKED_INPUT", "SCHEMA_INVALID", 40, "BLOCKED", ("NONE",)
)

CLEANUP_JOB_ORDER = (
    "validation",
    "staging",
    "source-repro-check",
    "image-build",
    "staging-push",
    "staging-attest",
    "staging-readback",
    "release-promotion",
    "release-attest",
    "release-readback",
    "release-evidence-push",
    "consumer-verify-private",
    "public-visibility-readback",
    "consumer-verify-public",
    "emergency-deny-attest",
)

_RECONCILIATION_KEYS = {
    "schemaVersion", "attemptId", "producerStatus", "lastCompletedStage",
    "mapped609Status", "inputLockSha256", "staging", "release", "evidence",
    "replacesAttemptId", "replacedReleaseDigest", "replacedEvidenceDigest",
    "replacedAttemptSha256", "replacedEvidenceSha256",
    "replacedReconciliationSha256", "replacedCleanupSha256",
    "workflowRetryOrdinal", "retryReceipts", "secretScan", "cleanupVerified",
    "denylistVerified", "visibilityReadBack", "downstreamReadBack",
    "revocationsSha256", "revocationsCommitSha", "previousDocumentSha256",
    "statusChangedAt", "incidentUrl", "ownerAcknowledgedAt", "closedAt",
}
_PACKAGE_KEYS = {
    "package", "versionId", "attemptTag", "visibility", "pullMode",
    "pullVerified", "indexDigest", "manifestDigest", "configDigest", "baseDigest",
}
_EVIDENCE_KEYS = {
    "package", "versionId", "attemptTag", "visibility", "pullMode",
    "pullVerified", "artifactType", "manifestDigest", "subjectDigest",
    "fileManifestSha256",
}
_REVOCATION_ENTRY_KEYS = {
    "incidentId", "artifactKind", "digest", "reasonCode", "incidentUrl",
    "issuedAt", "acknowledgedAt", "signer",
}


def _exact(value: Any, keys: set[str], field: str) -> dict[str, Any]:
    try:
        return exact_object(value, required=keys)
    except ProducerValidationError as exc:
        raise ProducerValidationError(f"{field}: {exc}") from exc


def _string(value: Any, field: str) -> str:
    if not isinstance(value, str) or not value or "PENDING" in value.upper():
        raise ProducerValidationError(f"{field} must be a non-empty resolved string")
    return value


def _positive(value: Any, field: str) -> int:
    if type(value) is not int or value <= 0:
        raise ProducerValidationError(f"{field} must be a positive integer")
    return value


def _boolean(value: Any, field: str) -> bool:
    if type(value) is not bool:
        raise ProducerValidationError(f"{field} must be a JSON boolean")
    return value


def _attempt_id(value: Any, field: str = "attemptId") -> str:
    if not isinstance(value, str) or _ATTEMPT_RE.fullmatch(value) is None:
        raise ProducerValidationError(f"{field} must be <runId>.<runAttempt>")
    return value


def _commit(value: Any, field: str) -> str:
    if not isinstance(value, str) or _COMMIT_RE.fullmatch(value) is None:
        raise ProducerValidationError(f"{field} must be 40 lowercase hexadecimal characters")
    return value


def _oci_digest(value: Any, field: str) -> str:
    if not isinstance(value, str) or _OCI_DIGEST_RE.fullmatch(value) is None:
        raise ProducerValidationError(f"{field} must be a sha256 OCI digest")
    return value


def _timestamp(value: Any, field: str, *, nullable: bool = False) -> datetime | None:
    if value is None and nullable:
        return None
    if not isinstance(value, str) or not value.endswith("Z"):
        raise ProducerValidationError(f"{field} must be an RFC3339 UTC timestamp")
    try:
        parsed = datetime.fromisoformat(value[:-1] + "+00:00")
    except ValueError as exc:
        raise ProducerValidationError(f"{field} must be an RFC3339 UTC timestamp") from exc
    if parsed.tzinfo != timezone.utc:
        raise ProducerValidationError(f"{field} must use UTC")
    return parsed


def _incident(value: Any, *, required: bool) -> str | None:
    if value is None and not required:
        return None
    if not isinstance(value, str) or _INCIDENT_RE.fullmatch(value) is None:
        raise ProducerValidationError("incidentUrl must identify this repository")
    return value


def _validate_retry_receipts(value: Any) -> list[dict[str, Any]]:
    if not isinstance(value, list):
        raise ProducerValidationError("retryReceipts must be an array")
    seen: set[str] = set()
    result = []
    for index, raw in enumerate(value):
        receipt = _exact(
            raw,
            {"operationId", "kind", "attempts", "delaysSeconds", "terminal"},
            f"retryReceipts[{index}]",
        )
        operation = _string(receipt["operationId"], "operationId")
        if operation in seen:
            raise ProducerValidationError("duplicate retryReceipts operationId")
        seen.add(operation)
        if receipt["kind"] not in {"HTTP_DOWNLOAD", "GITHUB_API", "REGISTRY"}:
            raise ProducerValidationError("retryReceipts kind is invalid")
        attempts = _positive(receipt["attempts"], "retryReceipts attempts")
        if attempts > 3:
            raise ProducerValidationError("retryReceipts attempts exceeds 3")
        delays = receipt["delaysSeconds"]
        if not isinstance(delays, list) or delays != [2, 4][: max(0, attempts - 1)]:
            raise ProducerValidationError("retryReceipts delaysSeconds must be the [2,4] prefix")
        if receipt["terminal"] not in {"SUCCESS", "TRANSIENT_EXHAUSTED", "PERMANENT_FAILURE"}:
            raise ProducerValidationError("retryReceipts terminal is invalid")
        result.append(receipt)
    return result


def _validate_package(value: Any, attempt_id: str, field: str) -> dict[str, Any]:
    package = _exact(value, _PACKAGE_KEYS, field)
    _string(package["package"], f"{field}.package")
    _positive(package["versionId"], f"{field}.versionId")
    if package["attemptTag"] != "image-" + attempt_id:
        raise ProducerValidationError(f"{field}.attemptTag differs from attemptId")
    if package["visibility"] not in {"private", "public"}:
        raise ProducerValidationError(f"{field}.visibility is invalid")
    if package["pullMode"] not in {"PACKAGES_READ", "ANONYMOUS"}:
        raise ProducerValidationError(f"{field}.pullMode is invalid")
    _boolean(package["pullVerified"], f"{field}.pullVerified")
    for digest_field in ("indexDigest", "manifestDigest", "configDigest", "baseDigest"):
        _oci_digest(package[digest_field], f"{field}.{digest_field}")
    return package


def _validate_evidence(value: Any, attempt_id: str) -> dict[str, Any]:
    evidence = _exact(value, _EVIDENCE_KEYS, "evidence")
    _string(evidence["package"], "evidence.package")
    _positive(evidence["versionId"], "evidence.versionId")
    if evidence["attemptTag"] != "evidence-" + attempt_id:
        raise ProducerValidationError("evidence.attemptTag differs from attemptId")
    if evidence["visibility"] not in {"private", "public"}:
        raise ProducerValidationError("evidence.visibility is invalid")
    if evidence["pullMode"] not in {"PACKAGES_READ", "ANONYMOUS"}:
        raise ProducerValidationError("evidence.pullMode is invalid")
    _boolean(evidence["pullVerified"], "evidence.pullVerified")
    _string(evidence["artifactType"], "evidence.artifactType")
    _oci_digest(evidence["manifestDigest"], "evidence.manifestDigest")
    _oci_digest(evidence["subjectDigest"], "evidence.subjectDigest")
    require_sha256(evidence["fileManifestSha256"], "evidence.fileManifestSha256")
    return evidence


def validate_reconciliation(value: Mapping[str, Any]) -> dict[str, Any]:
    """Validate exact lifecycle state, stage, incident, and read-back invariants."""

    document = _exact(value, _RECONCILIATION_KEYS, "reconciliation")
    if document["schemaVersion"] != 1:
        raise ProducerValidationError("schemaVersion must be 1")
    attempt_id = _attempt_id(document["attemptId"])
    status = document["producerStatus"]
    if status not in STATUS_CONTRACT:
        raise ProducerValidationError("producerStatus is invalid")
    contract = STATUS_CONTRACT[status]
    stage = document["lastCompletedStage"]
    if stage not in contract.allowed_stages:
        raise ProducerValidationError("lastCompletedStage is invalid for producerStatus")
    if document["mapped609Status"] != contract.mapped_609_status:
        raise ProducerValidationError("mapped609Status differs from status contract")
    require_sha256(document["inputLockSha256"], "inputLockSha256")
    require_sha256(document["revocationsSha256"], "revocationsSha256")
    _commit(document["revocationsCommitSha"], "revocationsCommitSha")
    if document["previousDocumentSha256"] is not None:
        require_sha256(document["previousDocumentSha256"], "previousDocumentSha256")

    expected_presence = {
        "NONE": (False, False, False),
        "STAGING": (True, False, False),
        "EVIDENCE": (True, False, False),
        "RELEASE": (True, True, False),
        "PUBLIC_EVIDENCE": (True, True, True),
    }[stage]
    raw_artifacts = (document["staging"], document["release"], document["evidence"])
    for field, raw, expected in zip(("staging", "release", "evidence"), raw_artifacts, expected_presence):
        if (raw is not None) != expected:
            raise ProducerValidationError(f"{field} nullability differs from lastCompletedStage")
    staging = _validate_package(document["staging"], attempt_id, "staging") if expected_presence[0] else None
    release = _validate_package(document["release"], attempt_id, "release") if expected_presence[1] else None
    evidence = _validate_evidence(document["evidence"], attempt_id) if expected_presence[2] else None

    replacement_hashes = (
        "replacedAttemptSha256", "replacedEvidenceSha256",
        "replacedReconciliationSha256", "replacedCleanupSha256",
    )
    if document["replacesAttemptId"] is None:
        for field in ("replacedReleaseDigest", "replacedEvidenceDigest", *replacement_hashes):
            if document[field] is not None:
                raise ProducerValidationError(f"{field} requires replacesAttemptId")
    else:
        _attempt_id(document["replacesAttemptId"], "replacesAttemptId")
        for field in replacement_hashes:
            require_sha256(document[field], field)
        for field in ("replacedReleaseDigest", "replacedEvidenceDigest"):
            if document[field] is not None:
                _oci_digest(document[field], field)

    _positive(document["workflowRetryOrdinal"], "workflowRetryOrdinal")
    _validate_retry_receipts(document["retryReceipts"])
    if document["secretScan"] not in {"NOT_RUN", "PASS", "FAIL"}:
        raise ProducerValidationError("secretScan is invalid")
    for field in ("cleanupVerified", "denylistVerified", "visibilityReadBack", "downstreamReadBack"):
        _boolean(document[field], field)

    changed = _timestamp(document["statusChangedAt"], "statusChangedAt")
    terminal_incident = status in {
        "QUARANTINE_PENDING", "QUARANTINED", "REJECTED", "REVOKED",
        "FAILED", "CANCELLED", "INTERRUPTED",
    }
    _incident(document["incidentUrl"], required=terminal_incident)
    acknowledged = _timestamp(document["ownerAcknowledgedAt"], "ownerAcknowledgedAt", nullable=True)
    closed = _timestamp(document["closedAt"], "closedAt", nullable=True)
    if terminal_incident and acknowledged is None:
        raise ProducerValidationError("ownerAcknowledgedAt is required for terminal incident status")
    if (
        not terminal_incident
        and status not in {"PUBLISHED_UNVERIFIED", "PROMOTING", "RELEASE_UNVERIFIED"}
        and (document["incidentUrl"] is not None or acknowledged is not None)
    ):
        raise ProducerValidationError("incident fields must be null for this status")
    if acknowledged is not None and acknowledged < changed:  # type: ignore[operator]
        raise ProducerValidationError("incident timestamp order is invalid")
    if closed is not None and (acknowledged is None or closed < acknowledged):
        raise ProducerValidationError("incident timestamp order is invalid")

    if status == "PRODUCER_PASS":
        required_true = (
            "cleanupVerified", "denylistVerified", "visibilityReadBack", "downstreamReadBack"
        )
        for field in required_true:
            if document[field] is not True:
                raise ProducerValidationError(f"{field} must be true for PRODUCER_PASS")
        if document["secretScan"] != "PASS":
            raise ProducerValidationError("secretScan must be PASS for PRODUCER_PASS")
        assert staging is not None and release is not None and evidence is not None
        for field in ("indexDigest", "manifestDigest", "configDigest", "baseDigest"):
            if staging[field] != release[field]:
                raise ProducerValidationError(f"{field} differs between staging and release")
        if staging["visibility"] != "private" or staging["pullMode"] != "PACKAGES_READ":
            raise ProducerValidationError("staging access contract is invalid")
        if release["visibility"] != "public" or release["pullMode"] != "ANONYMOUS":
            raise ProducerValidationError("release access contract is invalid")
        if evidence["visibility"] != "public" or evidence["pullMode"] != "ANONYMOUS":
            raise ProducerValidationError("evidence access contract is invalid")
        if not staging["pullVerified"] or not release["pullVerified"] or not evidence["pullVerified"]:
            raise ProducerValidationError("pullVerified must be true for PRODUCER_PASS")
        if evidence["subjectDigest"] != release["manifestDigest"]:
            raise ProducerValidationError("evidence.subjectDigest differs from release.manifestDigest")
        if document["incidentUrl"] is not None or acknowledged is not None or closed is not None:
            raise ProducerValidationError("PRODUCER_PASS incident fields must be null")

    if closed is not None:
        for field in ("cleanupVerified", "denylistVerified", "visibilityReadBack", "downstreamReadBack"):
            if document[field] is not True:
                raise ProducerValidationError(f"{field} must be true before closedAt")
    return document


def validate_cleanup_fragment(value: Mapping[str, Any]) -> dict[str, Any]:
    fragment = _exact(
        value,
        {"schemaVersion", "attemptId", "jobId", "originalProducerStatus", "startedAt", "finishedAt", "targets"},
        "cleanup fragment",
    )
    if fragment["schemaVersion"] != 1:
        raise ProducerValidationError("schemaVersion must be 1")
    _attempt_id(fragment["attemptId"])
    job = fragment["jobId"]
    if not isinstance(job, str) or job not in CLEANUP_JOB_ORDER or _JOB_RE.fullmatch(job) is None:
        raise ProducerValidationError("jobId is invalid")
    if fragment["originalProducerStatus"] not in STATUS_CONTRACT:
        raise ProducerValidationError("originalProducerStatus is invalid")
    started = _timestamp(fragment["startedAt"], "startedAt")
    finished = _timestamp(fragment["finishedAt"], "finishedAt")
    if finished < started:  # type: ignore[operator]
        raise ProducerValidationError("cleanup timestamp order is invalid")
    if not isinstance(fragment["targets"], list):
        raise ProducerValidationError("targets must be an array")
    seen: set[tuple[str, str]] = set()
    for index, raw in enumerate(fragment["targets"]):
        target = _exact(raw, {"kind", "idSha256", "result"}, f"targets[{index}]")
        kind = target["kind"]
        if not isinstance(kind, str) or _TOKEN_RE.fullmatch(kind) is None:
            raise ProducerValidationError("cleanup target kind is invalid")
        identity = require_sha256(target["idSha256"], "cleanup target idSha256")
        if target["result"] not in {"REMOVED", "ABSENT", "FAILED"}:
            raise ProducerValidationError("cleanup target result is invalid")
        key = (kind, identity)
        if key in seen:
            raise ProducerValidationError("duplicate cleanup target")
        seen.add(key)
    return fragment


def merge_cleanup_fragments(
    attempt_id: str,
    original_status: str,
    started_job_ids: Sequence[str],
    fragments: Sequence[Mapping[str, Any]],
    *,
    merged_at: str,
) -> dict[str, Any]:
    """Build a deterministic aggregate without inventing missing cleanup receipts."""

    _attempt_id(attempt_id)
    if original_status not in STATUS_CONTRACT:
        raise ProducerValidationError("originalProducerStatus is invalid")
    _timestamp(merged_at, "mergedAt")
    if len(set(started_job_ids)) != len(started_job_ids):
        raise ProducerValidationError("startedJobIds must be unique")
    unknown = set(started_job_ids) - set(CLEANUP_JOB_ORDER)
    if unknown:
        raise ProducerValidationError("startedJobIds contains an unknown job")
    ordered_jobs = [job for job in CLEANUP_JOB_ORDER if job in started_job_ids]
    by_job: dict[str, dict[str, Any]] = {}
    seen_targets: set[tuple[str, str]] = set()
    all_targets_clean = True
    for raw in fragments:
        fragment = validate_cleanup_fragment(raw)
        if fragment["attemptId"] != attempt_id:
            raise ProducerValidationError("cleanup fragment attemptId differs")
        if fragment["originalProducerStatus"] != original_status:
            raise ProducerValidationError("cleanup fragment originalProducerStatus differs")
        job = fragment["jobId"]
        if job not in ordered_jobs:
            raise ProducerValidationError("cleanup fragment does not belong to a started job")
        if job in by_job:
            raise ProducerValidationError("duplicate cleanup fragment jobId")
        by_job[job] = fragment
        for target in fragment["targets"]:
            key = (target["kind"], target["idSha256"])
            if key in seen_targets:
                raise ProducerValidationError("duplicate cleanup target across fragments")
            seen_targets.add(key)
            all_targets_clean = all_targets_clean and target["result"] in {"REMOVED", "ABSENT"}
    descriptors = []
    for job in ordered_jobs:
        if job not in by_job:
            continue
        raw = jcs_bytes(by_job[job])
        descriptors.append(
            {
                "jobId": job,
                "path": f"cleanup-fragments/{job}.json",
                "bytes": len(raw),
                "sha256": sha256_hex(raw),
            }
        )
    return {
        "schemaVersion": 1,
        "attemptId": attempt_id,
        "originalProducerStatus": original_status,
        "startedJobIds": ordered_jobs,
        "fragments": descriptors,
        "cleanupVerified": len(descriptors) == len(ordered_jobs) and all_targets_clean,
        "mergedAt": merged_at,
    }


def apply_cleanup_outcome(original_status: str, cleanup: Mapping[str, Any]) -> str:
    """Preserve an original terminal failure and prevent a false pass."""

    if original_status not in STATUS_CONTRACT:
        raise ProducerValidationError("original producer status is invalid")
    verified = cleanup.get("cleanupVerified")
    _boolean(verified, "cleanupVerified")
    if verified:
        return original_status
    if original_status in {"BLOCKED_INPUT", "BLOCKED_LEGAL_INVENTORY", "QUARANTINED", "REJECTED", "REVOKED", "FAILED", "CANCELLED", "INTERRUPTED"}:
        return original_status
    return "INTERRUPTED"


def _terminal_summary(
    status: str, stage: str, *, cleanup_verified: bool
) -> dict[str, Any]:
    contract = STATUS_CONTRACT[status]
    if contract.exit_code is None:
        raise ProducerValidationError("final producerStatus must be terminal")
    if stage not in contract.allowed_stages:
        raise ProducerValidationError(
            "lastCompletedStage is invalid for producerStatus"
        )
    return {
        "producerStatus": status,
        "lastCompletedStage": stage,
        "mapped609Status": contract.mapped_609_status,
        "errorCode": contract.error_code,
        "exitCode": contract.exit_code,
        "cleanupVerified": cleanup_verified,
    }


def finalize_attempt(
    attempt: Mapping[str, Any], readbacks: Mapping[str, Any]
) -> dict[str, Any]:
    """Resolve a terminal status without allowing cleanup or cancellation to become PASS."""

    status = attempt.get("producerStatus")
    stage = attempt.get("lastCompletedStage")
    conclusion = attempt.get("runConclusion")
    if status not in STATUS_CONTRACT:
        raise ProducerValidationError("producerStatus is invalid")
    if stage not in LAST_COMPLETED_STAGES:
        raise ProducerValidationError("lastCompletedStage is invalid")
    if conclusion not in {
        "success",
        "failure",
        "cancelled",
        "timed_out",
        "stale",
        "action_required",
    }:
        raise ProducerValidationError("runConclusion is invalid")
    cleanup_verified = readbacks.get("cleanupVerified")
    fragments_complete = readbacks.get("fragmentsComplete")
    _boolean(cleanup_verified, "cleanupVerified")
    _boolean(fragments_complete, "fragmentsComplete")
    if readbacks.get("malformedFragments") or readbacks.get("duplicateFragments"):
        return _terminal_summary("REJECTED", stage, cleanup_verified=False)
    if conclusion == "cancelled":
        return _terminal_summary("CANCELLED", stage, cleanup_verified=cleanup_verified)
    if (
        conclusion in {"timed_out", "stale", "action_required"}
        or not fragments_complete
    ):
        return _terminal_summary("INTERRUPTED", stage, cleanup_verified=False)
    if conclusion == "failure" and status not in {
        "BLOCKED_INPUT",
        "BLOCKED_LEGAL_INVENTORY",
        "QUARANTINED",
        "REJECTED",
        "REVOKED",
        "FAILED",
        "CANCELLED",
        "INTERRUPTED",
    }:
        status = "FAILED"
    if not cleanup_verified:
        status = apply_cleanup_outcome(status, {"cleanupVerified": False})
    if STATUS_CONTRACT[status].exit_code is None:
        raise ProducerValidationError(
            "successful run did not reach a terminal producerStatus"
        )
    return _terminal_summary(status, stage, cleanup_verified=cleanup_verified)


def reconcile_remote_state(remote: Mapping[str, Any]) -> dict[str, Any]:
    """Classify an observed registry topology without creating or mutating artifacts."""

    if set(remote) - {"staging", "release", "evidence", "ambiguous"}:
        raise ProducerValidationError("remote state contains unknown fields")
    staging = remote.get("staging")
    release = remote.get("release")
    evidence = remote.get("evidence")
    if remote.get("ambiguous") is True or (
        staging is None and (release is not None or evidence is not None)
    ):
        return _terminal_summary(
            "REJECTED",
            "PUBLIC_EVIDENCE" if evidence is not None else "RELEASE",
            cleanup_verified=False,
        )
    if release is None and evidence is not None:
        return _terminal_summary("REJECTED", "PUBLIC_EVIDENCE", cleanup_verified=False)
    if staging is None:
        return _terminal_summary("INTERRUPTED", "NONE", cleanup_verified=False)
    if release is None:
        status, stage = "PUBLISHED_UNVERIFIED", "STAGING"
    elif evidence is None:
        status, stage = "RELEASE_UNVERIFIED", "RELEASE"
    else:
        status, stage = "RELEASE_UNVERIFIED", "PUBLIC_EVIDENCE"
    contract = STATUS_CONTRACT[status]
    return {
        "producerStatus": status,
        "lastCompletedStage": stage,
        "mapped609Status": contract.mapped_609_status,
        "errorCode": contract.error_code,
        "exitCode": contract.exit_code,
        "cleanupVerified": False,
    }


def validate_reconcile_expectations(
    expected: Mapping[str, Any],
    prior: Mapping[str, Any],
    remote: Mapping[str, Any],
) -> dict[str, Any]:
    """Bind a RECONCILE request to the exact prior attempt and current remote state."""

    expected_keys = {
        "resumeAttemptId",
        "expectedPriorStatus",
        "expectedInputLockSha256",
        "expectedStagingDigest",
        "expectedReleaseDigest",
        "expectedEvidenceDigest",
    }
    if set(expected) != expected_keys:
        raise ProducerValidationError(
            "reconcile expectations must contain all six exact fields"
        )
    if set(prior) != {
        "attemptId",
        "producerStatus",
        "inputLockSha256",
        "staging",
        "release",
        "evidence",
    }:
        raise ProducerValidationError("prior reconcile state has an invalid shape")
    if set(remote) != set(prior):
        raise ProducerValidationError("remote reconcile state has an invalid shape")
    _attempt_id(expected["resumeAttemptId"], "resumeAttemptId")
    require_sha256(expected["expectedInputLockSha256"], "expectedInputLockSha256")
    if (
        prior["attemptId"] != expected["resumeAttemptId"]
        or remote["attemptId"] != prior["attemptId"]
    ):
        raise ProducerValidationError(
            "resumeAttemptId differs from prior or remote state"
        )
    if (
        prior["producerStatus"] != expected["expectedPriorStatus"]
        or remote["producerStatus"] != prior["producerStatus"]
    ):
        raise ProducerValidationError(
            "expected prior status differs from prior or remote state"
        )
    if (
        prior["inputLockSha256"] != expected["expectedInputLockSha256"]
        or remote["inputLockSha256"] != prior["inputLockSha256"]
    ):
        raise ProducerValidationError(
            "expected input lock differs from prior or remote state"
        )

    for artifact, field in (
        ("staging", "expectedStagingDigest"),
        ("release", "expectedReleaseDigest"),
        ("evidence", "expectedEvidenceDigest"),
    ):
        expected_digest = expected[field]
        prior_object = prior[artifact]
        remote_object = remote[artifact]
        prior_digest = (
            None if prior_object is None else prior_object.get("manifestDigest")
        )
        remote_digest = (
            None if remote_object is None else remote_object.get("manifestDigest")
        )
        if expected_digest is not None:
            _oci_digest(expected_digest, field)
        if expected_digest != prior_digest or remote_digest != prior_digest:
            raise ProducerValidationError(
                f"{artifact} digest differs from reconcile expectation"
            )
    return dict(prior)


def validate_quarantine_recovery(observed: Mapping[str, Any]) -> dict[str, Any]:
    """Allow QUARANTINED only after the protected revocation and denial receipt read back."""

    keys = {
        "publicVerificationFailed",
        "revocationMerged",
        "emergencyReceiptVerified",
        "revocationsCommitSha",
        "developHeadSha",
        "cleanupVerified",
        "downstreamReadBack",
        "visibilityReadBack",
    }
    if set(observed) != keys:
        raise ProducerValidationError("quarantine observation has an invalid shape")
    for field in (
        "publicVerificationFailed",
        "revocationMerged",
        "emergencyReceiptVerified",
        "cleanupVerified",
        "downstreamReadBack",
        "visibilityReadBack",
    ):
        _boolean(observed[field], field)
    for field in ("revocationsCommitSha", "developHeadSha"):
        _commit(observed[field], field)
    if observed["publicVerificationFailed"] is not True:
        raise ProducerValidationError(
            "quarantine requires a public verification failure"
        )
    complete = (
        observed["revocationMerged"] is True
        and observed["emergencyReceiptVerified"] is True
        and observed["revocationsCommitSha"] == observed["developHeadSha"]
        and observed["cleanupVerified"] is True
        and observed["downstreamReadBack"] is True
        and observed["visibilityReadBack"] is True
    )
    status = "QUARANTINED" if complete else "QUARANTINE_PENDING"
    contract = STATUS_CONTRACT[status]
    return {
        "producerStatus": status,
        "exitCode": contract.exit_code,
        "mapped609Status": contract.mapped_609_status,
        "observed": dict(observed),
    }


_FAILURE_ORDER = (
    "cleanup-aggregate",
    "result",
    "quarantine-pending",
    "revocation-merge",
    "emergency-attestation",
    "quarantined",
    "known-good-selection",
    "downstream-notification",
    "owner-acknowledgement",
    "incident-closure",
)


def validate_failure_order(events: Sequence[str]) -> list[str]:
    """Validate the append-only failure recovery order and reject inline rollback."""

    if not isinstance(events, Sequence) or isinstance(events, (str, bytes)):
        raise ProducerValidationError("failure events must be a sequence")
    if len(events) != len(set(events)) or any(
        not isinstance(event, str) for event in events
    ):
        raise ProducerValidationError(
            "failure ordering contains duplicate or invalid events"
        )
    if any(
        event in {"visibility-rollback", "delete-package", "stable-tag-mutation"}
        for event in events
    ):
        raise ProducerValidationError(
            "destructive rollback is outside the producer workflow"
        )
    positions = {event: index for index, event in enumerate(_FAILURE_ORDER)}
    selected = [event for event in events if event in positions]
    if selected != sorted(selected, key=positions.__getitem__):
        raise ProducerValidationError("failure ordering is invalid")
    return list(events)


def acknowledge_incident(
    reconciliation: Mapping[str, Any], incident_url: str, acknowledged_at: str
) -> dict[str, Any]:
    document = dict(reconciliation)
    _incident(incident_url, required=True)
    acknowledged = _timestamp(acknowledged_at, "ownerAcknowledgedAt")
    changed = _timestamp(document.get("statusChangedAt"), "statusChangedAt")
    if acknowledged < changed:  # type: ignore[operator]
        raise ProducerValidationError("incident timestamp order is invalid")
    if document.get("incidentUrl") not in {None, incident_url}:
        raise ProducerValidationError("incidentUrl differs from reconciliation")
    if document.get("ownerAcknowledgedAt") is not None:
        raise ProducerValidationError("incident is already acknowledged")
    document["incidentUrl"] = incident_url
    document["ownerAcknowledgedAt"] = acknowledged_at
    validate_reconciliation(document)
    return document


def close_incident(reconciliation: Mapping[str, Any], closed_at: str) -> dict[str, Any]:
    document = dict(reconciliation)
    if document.get("ownerAcknowledgedAt") is None:
        raise ProducerValidationError("ownerAcknowledgedAt is required before closure")
    if document.get("closedAt") is not None:
        raise ProducerValidationError("incident is already closed")
    for field in (
        "cleanupVerified",
        "denylistVerified",
        "visibilityReadBack",
        "downstreamReadBack",
    ):
        if document.get(field) is not True:
            raise ProducerValidationError(f"{field} must be true before closure")
    _timestamp(closed_at, "closedAt")
    document["closedAt"] = closed_at
    validate_reconciliation(document)
    return document


def plan_known_good_rollback(
    *,
    current_digest: str,
    candidates: Sequence[Mapping[str, Any]],
    revoked_digests: set[str],
    downstream_notified: bool,
) -> dict[str, Any]:
    """Select one immutable accepted non-revoked digest without mutating registry state."""

    _oci_digest(current_digest, "currentDigest")
    _boolean(downstream_notified, "downstreamNotified")
    if not downstream_notified:
        raise ProducerValidationError("downstream notification is required")
    for digest in revoked_digests:
        _oci_digest(digest, "revokedDigest")
    eligible = []
    for raw in candidates:
        if set(raw) != {"digest", "stableTag", "accepted"}:
            raise ProducerValidationError("rollback candidate has an invalid shape")
        digest = raw["digest"]
        if not isinstance(digest, str):
            raise ProducerValidationError(
                "rollback candidate requires an immutable digest"
            )
        _oci_digest(digest, "knownGoodDigest")
        if raw["stableTag"] != "stable" or raw["accepted"] is not True:
            continue
        if digest not in revoked_digests and digest != current_digest:
            eligible.append(digest)
    if len(eligible) != 1:
        raise ProducerValidationError(
            "known-good rollback candidate is absent or ambiguous"
        )
    return {
        "schemaVersion": 1,
        "dryRun": True,
        "currentDigest": current_digest,
        "knownGoodDigest": eligible[0],
        "mutationRequired": "visibility-and-stable-tag",
    }


_ATTEMPT_KEYS = {
    "schemaVersion", "attemptId", "producerStatus", "lastCompletedStage",
    "inputLockSha256", "policySha256", "repository", "workflowPath",
    "workflowRef", "workflowSha", "ref", "headSha", "runId", "runAttempt",
    "actor", "runner", "environment", "replacesAttemptId",
    "replacedReleaseDigest", "replacedEvidenceDigest", "replacedAttemptSha256",
    "replacedEvidenceSha256", "replacedReconciliationSha256", "replacedCleanupSha256",
    "timestamps", "evidenceSha256", "reconciliationSha256", "cleanupSha256",
    "ledgerFragmentSha256", "revocationsSha256", "revocationsCommitSha",
    "previousDocumentSha256",
}


def validate_attempt(value: Mapping[str, Any]) -> dict[str, Any]:
    attempt = _exact(value, _ATTEMPT_KEYS, "attempt")
    if attempt["schemaVersion"] != 1:
        raise ProducerValidationError("schemaVersion must be 1")
    identity = _attempt_id(attempt["attemptId"])
    status = attempt["producerStatus"]
    if status not in STATUS_CONTRACT:
        raise ProducerValidationError("producerStatus is invalid")
    if attempt["lastCompletedStage"] not in STATUS_CONTRACT[status].allowed_stages:
        raise ProducerValidationError("lastCompletedStage is invalid for producerStatus")
    _positive(attempt["runId"], "runId")
    _positive(attempt["runAttempt"], "runAttempt")
    if identity != f'{attempt["runId"]}.{attempt["runAttempt"]}':
        raise ProducerValidationError("attemptId differs from run identity")
    require_sha256(attempt["inputLockSha256"], "inputLockSha256")
    require_sha256(attempt["policySha256"], "policySha256")
    for field in (
        "evidenceSha256", "reconciliationSha256", "cleanupSha256",
        "ledgerFragmentSha256", "revocationsSha256",
    ):
        require_sha256(attempt[field], field)
    for field in ("workflowSha", "headSha", "revocationsCommitSha"):
        _commit(attempt[field], field)
    if attempt["previousDocumentSha256"] is not None:
        require_sha256(attempt["previousDocumentSha256"], "previousDocumentSha256")
    for field in (
        "repository", "workflowPath", "workflowRef", "ref", "actor", "runner", "environment"
    ):
        _string(attempt[field], field)
    replacement_hashes = (
        "replacedAttemptSha256", "replacedEvidenceSha256",
        "replacedReconciliationSha256", "replacedCleanupSha256",
    )
    if attempt["replacesAttemptId"] is None:
        for field in ("replacedReleaseDigest", "replacedEvidenceDigest", *replacement_hashes):
            if attempt[field] is not None:
                raise ProducerValidationError(f"{field} requires replacesAttemptId")
    else:
        _attempt_id(attempt["replacesAttemptId"], "replacesAttemptId")
        for field in replacement_hashes:
            require_sha256(attempt[field], field)
        for field in ("replacedReleaseDigest", "replacedEvidenceDigest"):
            if attempt[field] is not None:
                _oci_digest(attempt[field], field)
    timestamps = _exact(
        attempt["timestamps"],
        {"VALIDATING", "BUILDING", "STAGING", "EVIDENCE", "RELEASE", "PUBLIC_EVIDENCE", "TERMINAL"},
        "timestamps",
    )
    observed: list[datetime] = []
    for field in ("VALIDATING", "BUILDING", "STAGING", "EVIDENCE", "RELEASE", "PUBLIC_EVIDENCE", "TERMINAL"):
        parsed = _timestamp(timestamps[field], f"timestamps.{field}", nullable=True)
        if parsed is not None:
            observed.append(parsed)
    if observed != sorted(observed):
        raise ProducerValidationError("attempt timestamps are out of order")
    if timestamps["VALIDATING"] is None:
        raise ProducerValidationError("timestamps.VALIDATING is required")
    stage_timestamp_fields = {
        "NONE": (),
        "STAGING": ("BUILDING", "STAGING"),
        "EVIDENCE": ("BUILDING", "STAGING", "EVIDENCE"),
        "RELEASE": ("BUILDING", "STAGING", "EVIDENCE", "RELEASE"),
        "PUBLIC_EVIDENCE": (
            "BUILDING", "STAGING", "EVIDENCE", "RELEASE", "PUBLIC_EVIDENCE"
        ),
    }[attempt["lastCompletedStage"]]
    for field in stage_timestamp_fields:
        if timestamps[field] is None:
            raise ProducerValidationError(f"timestamps.{field} is required for lastCompletedStage")
    if STATUS_CONTRACT[status].exit_code is None and timestamps["TERMINAL"] is not None:
        raise ProducerValidationError("timestamps.TERMINAL must be null for a non-terminal status")
    if STATUS_CONTRACT[status].exit_code is not None and timestamps["TERMINAL"] is None:
        raise ProducerValidationError("timestamps.TERMINAL is required for a terminal status")
    return attempt


def validate_cleanup_aggregate(value: Mapping[str, Any]) -> dict[str, Any]:
    cleanup = _exact(
        value,
        {"schemaVersion", "attemptId", "originalProducerStatus", "startedJobIds", "fragments", "cleanupVerified", "mergedAt"},
        "cleanup",
    )
    if cleanup["schemaVersion"] != 1:
        raise ProducerValidationError("schemaVersion must be 1")
    _attempt_id(cleanup["attemptId"])
    if cleanup["originalProducerStatus"] not in STATUS_CONTRACT:
        raise ProducerValidationError("originalProducerStatus is invalid")
    _timestamp(cleanup["mergedAt"], "mergedAt")
    _boolean(cleanup["cleanupVerified"], "cleanupVerified")
    jobs = cleanup["startedJobIds"]
    if not isinstance(jobs, list) or any(job not in CLEANUP_JOB_ORDER for job in jobs):
        raise ProducerValidationError("startedJobIds is invalid")
    expected_jobs = [job for job in CLEANUP_JOB_ORDER if job in jobs]
    if jobs != expected_jobs or len(jobs) != len(set(jobs)):
        raise ProducerValidationError("startedJobIds order or uniqueness is invalid")
    fragments = cleanup["fragments"]
    if not isinstance(fragments, list):
        raise ProducerValidationError("fragments must be an array")
    fragment_jobs = []
    for index, raw in enumerate(fragments):
        descriptor = _exact(raw, {"jobId", "path", "bytes", "sha256"}, f"fragments[{index}]")
        job = descriptor["jobId"]
        if job not in jobs or job in fragment_jobs:
            raise ProducerValidationError("fragment jobId is invalid or duplicate")
        if descriptor["path"] != f"cleanup-fragments/{job}.json":
            raise ProducerValidationError("fragment path is invalid")
        _positive(descriptor["bytes"], "fragment bytes")
        require_sha256(descriptor["sha256"], "fragment sha256")
        fragment_jobs.append(job)
    if fragment_jobs != [job for job in expected_jobs if job in fragment_jobs]:
        raise ProducerValidationError("fragments are out of order")
    if cleanup["cleanupVerified"] is True and fragment_jobs != expected_jobs:
        raise ProducerValidationError("cleanupVerified requires every started job fragment")
    return cleanup


def build_producer_result(
    reconciliation: Mapping[str, Any],
    *,
    reconciliation_sha256: str,
    ledger_fragment_sha256: str,
    revocations_sha256: str,
) -> dict[str, Any]:
    document = validate_reconciliation(reconciliation)
    for value, field in (
        (reconciliation_sha256, "reconciliationSha256"),
        (ledger_fragment_sha256, "ledgerFragmentSha256"),
        (revocations_sha256, "revocationsSha256"),
    ):
        require_sha256(value, field)
    if document["revocationsSha256"] != revocations_sha256:
        raise ProducerValidationError("revocationsSha256 differs from reconciliation")
    status = document["producerStatus"]
    contract = STATUS_CONTRACT[status]
    if contract.exit_code is None:
        raise ProducerValidationError("non-terminal producerStatus cannot emit a result")
    release = document["release"]
    evidence = document["evidence"]
    return {
        "schemaVersion": 1,
        "attemptId": document["attemptId"],
        "producerStatus": status,
        "lastCompletedStage": document["lastCompletedStage"],
        "mapped609Status": document["mapped609Status"],
        "imagePlatformDigest": release["manifestDigest"] if release is not None else None,
        "evidenceManifestDigest": evidence["manifestDigest"] if evidence is not None else None,
        "reconciliationSha256": reconciliation_sha256,
        "ledgerFragmentSha256": ledger_fragment_sha256,
        "revocationsSha256": revocations_sha256,
        "errorCode": contract.error_code,
        "errorMessage": None if status == "PRODUCER_PASS" else f"producer ended with {status}",
    }


def initial_revocations() -> dict[str, Any]:
    return {"schemaVersion": 1, "previousDocumentSha256": None, "digests": []}


def _validate_revocation_entry(value: Any, field: str) -> dict[str, Any]:
    entry = _exact(value, _REVOCATION_ENTRY_KEYS, field)
    _string(entry["incidentId"], f"{field}.incidentId")
    if entry["artifactKind"] not in {"IMAGE", "EVIDENCE"}:
        raise ProducerValidationError(f"{field}.artifactKind is invalid")
    _oci_digest(entry["digest"], f"{field}.digest")
    if not isinstance(entry["reasonCode"], str) or _TOKEN_RE.fullmatch(entry["reasonCode"]) is None:
        raise ProducerValidationError(f"{field}.reasonCode is invalid")
    _incident(entry["incidentUrl"], required=True)
    issued = _timestamp(entry["issuedAt"], f"{field}.issuedAt")
    acknowledged = _timestamp(entry["acknowledgedAt"], f"{field}.acknowledgedAt")
    if acknowledged < issued:  # type: ignore[operator]
        raise ProducerValidationError(f"{field} timestamp order is invalid")
    _string(entry["signer"], f"{field}.signer")
    return entry


def validate_revocations(value: Mapping[str, Any]) -> dict[str, Any]:
    document = _exact(value, {"schemaVersion", "previousDocumentSha256", "digests"}, "revocations")
    if document["schemaVersion"] != 1:
        raise ProducerValidationError("schemaVersion must be 1")
    if document["previousDocumentSha256"] is not None:
        require_sha256(document["previousDocumentSha256"], "previousDocumentSha256")
    if not isinstance(document["digests"], list):
        raise ProducerValidationError("digests must be an array")
    if not document["digests"] and document["previousDocumentSha256"] is not None:
        raise ProducerValidationError("only the initial revocations document may be empty")
    if document["digests"] and document["previousDocumentSha256"] is None:
        raise ProducerValidationError("non-initial revocations require previousDocumentSha256")
    seen_digest: set[tuple[str, str]] = set()
    seen_incident_kind: set[tuple[str, str]] = set()
    incident_kinds: dict[str, set[str]] = {}
    for index, raw in enumerate(document["digests"]):
        entry = _validate_revocation_entry(raw, f"digests[{index}]")
        digest_key = (entry["artifactKind"], entry["digest"])
        incident_key = (entry["incidentId"], entry["artifactKind"])
        if digest_key in seen_digest or incident_key in seen_incident_kind:
            raise ProducerValidationError("duplicate revocation entry")
        seen_digest.add(digest_key)
        seen_incident_kind.add(incident_key)
        incident_kinds.setdefault(entry["incidentId"], set()).add(entry["artifactKind"])
    for incident_id, kinds in incident_kinds.items():
        if kinds != {"IMAGE", "EVIDENCE"}:
            raise ProducerValidationError(f"incident {incident_id} must revoke IMAGE and EVIDENCE together")
    for index in range(0, len(document["digests"]), 2):
        pair = document["digests"][index : index + 2]
        if (
            len(pair) != 2
            or pair[0]["incidentId"] != pair[1]["incidentId"]
            or [entry["artifactKind"] for entry in pair] != ["IMAGE", "EVIDENCE"]
        ):
            raise ProducerValidationError("revocation entries must use adjacent IMAGE, EVIDENCE order")
    return document


def append_revocations(
    previous: Mapping[str, Any], entries: Sequence[Mapping[str, Any]]
) -> dict[str, Any]:
    validated = validate_revocations(previous)
    if not entries:
        raise ProducerValidationError("at least one revocation entry is required")
    candidate = {
        "schemaVersion": 1,
        "previousDocumentSha256": sha256_hex(jcs_bytes(validated)),
        "digests": [*validated["digests"], *[dict(entry) for entry in entries]],
    }
    validate_revocations(candidate)
    return candidate


def validate_revocation_successor(
    previous: Mapping[str, Any], candidate: Mapping[str, Any]
) -> dict[str, Any]:
    old = validate_revocations(previous)
    new = validate_revocations(candidate)
    expected_parent = sha256_hex(jcs_bytes(old))
    if new["previousDocumentSha256"] != expected_parent:
        raise ProducerValidationError("previousDocumentSha256 differs from previous document")
    old_entries = old["digests"]
    if len(new["digests"]) <= len(old_entries) or new["digests"][: len(old_entries)] != old_entries:
        raise ProducerValidationError("revocations successor is not append-only")
    return new


def validate_emergency_receipt(
    value: Mapping[str, Any], revocations: Mapping[str, Any]
) -> dict[str, Any]:
    keys = {
        "schemaVersion", "imageDigest", "evidenceDigest", "incidentUrl", "reasonCode",
        "repository", "workflowPath", "workflowRef", "workflowSha", "runId", "runAttempt",
        "signer", "oidcIssuer", "oidcAudience", "developHeadSha",
        "revocationsCommitSha", "revocationsSha256", "issuedAt", "revocationEntries",
    }
    receipt = _exact(value, keys, "emergency receipt")
    current = validate_revocations(revocations)
    if receipt["schemaVersion"] != 1:
        raise ProducerValidationError("schemaVersion must be 1")
    image = _oci_digest(receipt["imageDigest"], "imageDigest")
    evidence = _oci_digest(receipt["evidenceDigest"], "evidenceDigest")
    _incident(receipt["incidentUrl"], required=True)
    if not isinstance(receipt["reasonCode"], str) or _TOKEN_RE.fullmatch(receipt["reasonCode"]) is None:
        raise ProducerValidationError("reasonCode is invalid")
    if receipt["repository"] != "bluetape4k/bluetape4k-image":
        raise ProducerValidationError("repository is invalid")
    if receipt["workflowPath"] != ".github/workflows/paddleocr-producer.yml":
        raise ProducerValidationError("workflowPath is invalid")
    expected_ref = "bluetape4k/bluetape4k-image/.github/workflows/paddleocr-producer.yml@refs/heads/develop"
    if receipt["workflowRef"] != expected_ref:
        raise ProducerValidationError("workflowRef is invalid")
    for field in ("workflowSha", "developHeadSha", "revocationsCommitSha"):
        _commit(receipt[field], field)
    _positive(receipt["runId"], "runId")
    _positive(receipt["runAttempt"], "runAttempt")
    for field in ("signer", "oidcIssuer", "oidcAudience"):
        _string(receipt[field], field)
    _timestamp(receipt["issuedAt"], "issuedAt")
    expected_revocations_sha = sha256_hex(jcs_bytes(current))
    if receipt["revocationsSha256"] != expected_revocations_sha:
        raise ProducerValidationError("revocationsSha256 differs from current revocations")
    if not isinstance(receipt["revocationEntries"], list) or len(receipt["revocationEntries"]) != 2:
        raise ProducerValidationError("revocationEntries must contain IMAGE and EVIDENCE")
    expected_entries = {
        (entry["artifactKind"], entry["digest"]): entry
        for entry in current["digests"]
        if entry["incidentUrl"] == receipt["incidentUrl"]
        and entry["reasonCode"] == receipt["reasonCode"]
    }
    wanted = {("IMAGE", image), ("EVIDENCE", evidence)}
    if set(expected_entries) != wanted:
        raise ProducerValidationError("current revocations do not bind both receipt artifacts")
    seen = set()
    for index, raw in enumerate(receipt["revocationEntries"]):
        descriptor = _exact(raw, {"artifactKind", "digest", "entrySha256"}, f"revocationEntries[{index}]")
        key = (descriptor["artifactKind"], descriptor["digest"])
        if key not in wanted or key in seen:
            raise ProducerValidationError("revocationEntries artifact binding is invalid")
        seen.add(key)
        expected_sha = sha256_hex(jcs_bytes(expected_entries[key]))
        if descriptor["entrySha256"] != expected_sha:
            raise ProducerValidationError("revocationEntries entrySha256 differs")
    return receipt
