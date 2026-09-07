from __future__ import annotations

"""OCI evidence descriptor validation and root-pinned materialization."""

import hashlib
import os
import re
import shutil
import stat
import time
from collections.abc import Callable, Iterable, Mapping, Sequence
from dataclasses import dataclass
from pathlib import Path, PurePosixPath
from typing import Any

from .contracts import (
    ProducerValidationError,
    exact_object,
    jcs_bytes,
    load_json_bytes,
    load_jsonl_bytes,
    require_sha256,
    sha256_hex,
)
from .registry import validate_anonymous_environment

EVIDENCE_FILES = (
    "producer-evidence.json",
    "artifact-ledger.fragment.json",
    "inputs/producer-input.lock.json",
    "manifests/package-lock.json",
    "manifests/model-detector.json",
    "manifests/model-recognizer.json",
    "platform-manifest.json",
    "sbom.spdx.json",
    "legal-inventory.json",
    "attestations/provenance.bundle.jsonl",
    "attestations/sbom.bundle.jsonl",
)

ARTIFACT_TYPE = "application/vnd.bluetape4k.paddleocr.producer-evidence.v1"
OCI_MANIFEST_MEDIA_TYPE = "application/vnd.oci.image.manifest.v1+json"
EVIDENCE_CONFIG_MEDIA_TYPE = "application/vnd.bluetape4k.paddleocr.evidence.config.v1+json"
EVIDENCE_MANIFEST_MEDIA_TYPE = "application/vnd.bluetape4k.paddleocr.evidence.manifest.v1+json"
EVIDENCE_FILE_MEDIA_TYPE = "application/vnd.bluetape4k.paddleocr.evidence.file.v1"

_OCI_DIGEST_RE = re.compile(r"\Asha256:[0-9a-f]{64}\Z")
_COMMIT_RE = re.compile(r"\A[0-9a-f]{40}\Z")
_ATTEMPT_RE = re.compile(r"\A[1-9][0-9]*\.[1-9][0-9]*\Z")
_PARENT_DIRECTORIES = frozenset({"inputs", "manifests", "attestations"})
_STATUS_VALUES = frozenset({
    "VALIDATING", "BLOCKED_INPUT", "BLOCKED_LEGAL_INVENTORY", "BUILDING",
    "PUBLISHED_UNVERIFIED", "PROMOTING", "RELEASE_UNVERIFIED",
    "QUARANTINE_PENDING", "QUARANTINED", "PRODUCER_PASS", "REJECTED",
    "REVOKED", "FAILED", "CANCELLED", "INTERRUPTED",
})
_STAGES = ("NONE", "STAGING", "EVIDENCE", "RELEASE", "PUBLIC_EVIDENCE")
_SIGNER_WORKFLOW = "bluetape4k/bluetape4k-image/.github/workflows/paddleocr-producer.yml"
_OIDC_ISSUER = "https://token.actions.githubusercontent.com"
_ATTESTATION_CONTRACT = {
    "provenanceAttestation": (
        "attestations/provenance.bundle.jsonl",
        "https://slsa.dev/provenance/v1",
    ),
    "sbomAttestation": (
        "attestations/sbom.bundle.jsonl",
        "https://spdx.dev/Document/v2.3",
    ),
}
_STATUS_STAGES = {
    "VALIDATING": frozenset({"NONE"}),
    "BUILDING": frozenset({"NONE"}),
    "BLOCKED_INPUT": frozenset({"NONE"}),
    "BLOCKED_LEGAL_INVENTORY": frozenset({"NONE"}),
    "PUBLISHED_UNVERIFIED": frozenset({"STAGING"}),
    "PROMOTING": frozenset({"EVIDENCE"}),
    "RELEASE_UNVERIFIED": frozenset({"RELEASE", "PUBLIC_EVIDENCE"}),
    "PRODUCER_PASS": frozenset({"PUBLIC_EVIDENCE"}),
    "QUARANTINE_PENDING": frozenset({"PUBLIC_EVIDENCE"}),
    "QUARANTINED": frozenset({"PUBLIC_EVIDENCE"}),
}


@dataclass(frozen=True)
class MaterializationLimits:
    max_manifest_bytes: int = 1024 * 1024
    max_file_bytes: int = 128 * 1024 * 1024
    max_files_bytes: int = 512 * 1024 * 1024
    max_descriptor_bytes: int = 513 * 1024 * 1024
    max_layers: int = 16
    max_path_depth: int = 3
    max_path_bytes: int = 240
    manifest_timeout_seconds: int = 60
    blob_connect_timeout_seconds: int = 10
    blob_read_timeout_seconds: int = 30
    total_timeout_seconds: int = 600

    def __post_init__(self) -> None:
        for name, value in vars(self).items():
            if type(value) is not int or value <= 0:
                raise ProducerValidationError(f"{name} must be a positive integer")


def _exact(value: Any, keys: set[str], field: str) -> dict[str, Any]:
    try:
        return exact_object(value, required=keys)
    except ProducerValidationError as exc:
        raise ProducerValidationError(f"{field}: {exc}") from exc


def _positive(value: Any, field: str) -> int:
    if type(value) is not int or value <= 0:
        raise ProducerValidationError(f"{field} must be a positive integer")
    return value


def _oci_digest(value: Any, field: str) -> str:
    if not isinstance(value, str) or _OCI_DIGEST_RE.fullmatch(value) is None:
        raise ProducerValidationError(f"{field} must be a lowercase sha256 OCI digest")
    return value


def _validate_descriptor(
    value: Any,
    *,
    field: str,
    expected_media_type: str | None = None,
    max_bytes: int,
    annotations: bool,
) -> dict[str, Any]:
    keys = {"mediaType", "digest", "size"}
    if annotations:
        keys.add("annotations")
    descriptor = _exact(value, keys, field)
    media_type = descriptor["mediaType"]
    if not isinstance(media_type, str) or not media_type:
        raise ProducerValidationError(f"{field}.mediaType is invalid")
    if expected_media_type is not None and media_type != expected_media_type:
        raise ProducerValidationError(f"{field}.mediaType differs")
    _oci_digest(descriptor["digest"], f"{field}.digest")
    size = _positive(descriptor["size"], f"{field}.size")
    if size > max_bytes:
        raise ProducerValidationError(f"{field}.size exceeds limit")
    if annotations:
        annotation = _exact(
            descriptor["annotations"],
            {"org.opencontainers.image.title"},
            f"{field}.annotations",
        )
        title = annotation["org.opencontainers.image.title"]
        if not isinstance(title, str) or not title:
            raise ProducerValidationError(f"{field} title is invalid")
    return descriptor


def validate_remote_evidence_manifest(
    value: Mapping[str, Any],
    *,
    limits: MaterializationLimits | None = None,
) -> dict[str, dict[str, Any]]:
    """Preflight all remote OCI descriptors before any blob is requested."""

    active = limits or MaterializationLimits()
    manifest = _exact(
        value,
        {"schemaVersion", "mediaType", "artifactType", "config", "layers", "subject"},
        "OCI evidence manifest",
    )
    if manifest["schemaVersion"] != 2:
        raise ProducerValidationError("OCI manifest schemaVersion must be 2")
    if manifest["mediaType"] != OCI_MANIFEST_MEDIA_TYPE:
        raise ProducerValidationError("OCI manifest mediaType differs")
    if manifest["artifactType"] != ARTIFACT_TYPE:
        raise ProducerValidationError("OCI manifest artifactType differs")
    _validate_descriptor(
        manifest["config"],
        field="config",
        expected_media_type=EVIDENCE_CONFIG_MEDIA_TYPE,
        max_bytes=64 * 1024,
        annotations=False,
    )
    subject = _validate_descriptor(
        manifest["subject"],
        field="subject",
        expected_media_type=OCI_MANIFEST_MEDIA_TYPE,
        max_bytes=active.max_descriptor_bytes,
        annotations=False,
    )
    _oci_digest(subject["digest"], "subject.digest")
    layers = manifest["layers"]
    if not isinstance(layers, list) or not layers or len(layers) > active.max_layers:
        raise ProducerValidationError("OCI layers count is invalid")
    by_title: dict[str, dict[str, Any]] = {}
    seen_digests: set[str] = set()
    total = 0
    allowed_titles = {"evidence-manifest.json", *EVIDENCE_FILES}
    for index, raw in enumerate(layers):
        title_hint = None
        if isinstance(raw, dict) and isinstance(raw.get("annotations"), dict):
            title_hint = raw["annotations"].get("org.opencontainers.image.title")
        expected_type = (
            EVIDENCE_MANIFEST_MEDIA_TYPE
            if title_hint == "evidence-manifest.json"
            else EVIDENCE_FILE_MEDIA_TYPE
        )
        descriptor = _validate_descriptor(
            raw,
            field=f"layers[{index}]",
            expected_media_type=expected_type,
            max_bytes=active.max_file_bytes,
            annotations=True,
        )
        title = descriptor["annotations"]["org.opencontainers.image.title"]
        if title not in allowed_titles:
            raise ProducerValidationError("layer title is outside the evidence allowlist")
        if title in by_title:
            raise ProducerValidationError("duplicate layer title")
        if descriptor["digest"] in seen_digests:
            raise ProducerValidationError("duplicate layer digest")
        by_title[title] = descriptor
        seen_digests.add(descriptor["digest"])
        total += descriptor["size"]
        if total > active.max_descriptor_bytes:
            raise ProducerValidationError("OCI layer descriptor bytes exceed limit")
    if set(by_title) != allowed_titles:
        raise ProducerValidationError("OCI layers must contain the exact evidence allowlist")
    if by_title["evidence-manifest.json"]["size"] > active.max_manifest_bytes:
        raise ProducerValidationError("evidence manifest layer exceeds limit")
    if by_title["platform-manifest.json"]["size"] != subject["size"]:
        raise ProducerValidationError("OCI subject size differs from platform manifest layer")
    return by_title


def _validate_relative_path(
    raw: Any, *, field: str, limits: MaterializationLimits
) -> str:
    if not isinstance(raw, str) or not raw or raw in {".", ".."}:
        raise ProducerValidationError(f"{field} path is invalid")
    if len(raw.encode("utf-8")) > limits.max_path_bytes:
        raise ProducerValidationError(f"{field} path bytes exceed limit")
    path = PurePosixPath(raw)
    if path.is_absolute() or any(part in {"", ".", ".."} for part in path.parts):
        raise ProducerValidationError(f"{field} path escapes root")
    if len(path.parts) > limits.max_path_depth:
        raise ProducerValidationError(f"{field} path depth exceeds limit")
    if len(path.parts) == 2 and path.parts[0] not in _PARENT_DIRECTORIES:
        raise ProducerValidationError(f"{field} path parent is invalid")
    if len(path.parts) > 2:
        raise ProducerValidationError(f"{field} path nesting is invalid")
    return raw


def _validate_file_descriptors(
    value: Any, limits: MaterializationLimits
) -> list[dict[str, Any]]:
    if not isinstance(value, list):
        raise ProducerValidationError("files must be an array")
    if len(value) != len(EVIDENCE_FILES):
        raise ProducerValidationError("files must contain exactly the evidence allowlist")
    result = []
    paths: set[str] = set()
    digests: set[str] = set()
    total = 0
    for index, raw in enumerate(value):
        descriptor = _exact(raw, {"path", "bytes", "sha256"}, f"files[{index}]")
        path = _validate_relative_path(descriptor["path"], field=f"files[{index}]", limits=limits)
        if path not in EVIDENCE_FILES:
            raise ProducerValidationError("file path is outside the evidence allowlist")
        if path in paths:
            raise ProducerValidationError("duplicate evidence file path")
        size = _positive(descriptor["bytes"], f"files[{index}].bytes")
        if size > limits.max_file_bytes:
            raise ProducerValidationError("evidence file bytes exceed limit")
        digest = require_sha256(descriptor["sha256"], f"files[{index}].sha256")
        if digest in digests:
            raise ProducerValidationError("duplicate evidence file digest")
        paths.add(path)
        digests.add(digest)
        total += size
        if total > limits.max_files_bytes:
            raise ProducerValidationError("evidence file bytes exceed total limit")
        result.append(descriptor)
    if paths != set(EVIDENCE_FILES):
        raise ProducerValidationError("files must contain exactly the evidence allowlist")
    return result


def validate_evidence_file_manifest(
    value: Mapping[str, Any],
    *,
    limits: MaterializationLimits | None = None,
) -> dict[str, Any]:
    """Validate the non-self-referential internal file manifest."""

    active = limits or MaterializationLimits()
    document = _exact(
        value,
        {
            "schemaVersion", "artifactType", "imageIndexDigest", "imagePlatformDigest",
            "imageConfigDigest", "baseDigest", "subjectDigest", "runId", "runAttempt",
            "attemptId", "inputLockSha256", "revocationsSha256", "revocationsCommitSha", "files",
        },
        "evidence file manifest",
    )
    if document["schemaVersion"] != 1:
        raise ProducerValidationError("schemaVersion must be 1")
    if document["artifactType"] != ARTIFACT_TYPE:
        raise ProducerValidationError("artifactType differs")
    for field in (
        "imageIndexDigest", "imagePlatformDigest", "imageConfigDigest", "baseDigest", "subjectDigest"
    ):
        _oci_digest(document[field], field)
    if document["subjectDigest"] != document["imagePlatformDigest"]:
        raise ProducerValidationError("subjectDigest differs from imagePlatformDigest")
    run_id = _positive(document["runId"], "runId")
    run_attempt = _positive(document["runAttempt"], "runAttempt")
    if not isinstance(document["attemptId"], str) or _ATTEMPT_RE.fullmatch(document["attemptId"]) is None:
        raise ProducerValidationError("attemptId is invalid")
    if document["attemptId"] != f"{run_id}.{run_attempt}":
        raise ProducerValidationError("attemptId differs from run identity")
    require_sha256(document["inputLockSha256"], "inputLockSha256")
    require_sha256(document["revocationsSha256"], "revocationsSha256")
    if not isinstance(document["revocationsCommitSha"], str) or _COMMIT_RE.fullmatch(document["revocationsCommitSha"]) is None:
        raise ProducerValidationError("revocationsCommitSha is invalid")
    document["files"] = _validate_file_descriptors(document["files"], active)
    return document


def _validate_artifact_file(value: Any, field: str) -> dict[str, Any]:
    descriptor = _exact(value, {"path", "bytes", "sha256"}, field)
    if not isinstance(descriptor["path"], str) or not descriptor["path"]:
        raise ProducerValidationError(f"{field}.path is invalid")
    _positive(descriptor["bytes"], f"{field}.bytes")
    require_sha256(descriptor["sha256"], f"{field}.sha256")
    return descriptor


def validate_oci_handoff(value: Mapping[str, Any]) -> dict[str, Any]:
    document = _exact(
        value,
        {
            "schemaVersion", "attemptId", "runId", "runAttempt", "inputLockSha256",
            "stagingArtifactSha256", "imageTarSha256", "imageIndexDigest",
            "imagePlatformDigest", "imageConfigDigest", "baseDigest", "targetPlatform",
            "sourceDateEpoch", "modelTreeDigests", "modelPairSha256", "createdAt",
        },
        "OCI handoff",
    )
    if document["schemaVersion"] != 1:
        raise ProducerValidationError("schemaVersion must be 1")
    run_id = _positive(document["runId"], "runId")
    run_attempt = _positive(document["runAttempt"], "runAttempt")
    if document["attemptId"] != f"{run_id}.{run_attempt}":
        raise ProducerValidationError("attemptId differs from run identity")
    for field in ("inputLockSha256", "stagingArtifactSha256", "imageTarSha256", "modelPairSha256"):
        require_sha256(document[field], field)
    for field in ("imageIndexDigest", "imagePlatformDigest", "imageConfigDigest", "baseDigest"):
        _oci_digest(document[field], field)
    if document["targetPlatform"] != "linux/amd64":
        raise ProducerValidationError("targetPlatform must be linux/amd64")
    _positive(document["sourceDateEpoch"], "sourceDateEpoch")
    models = _exact(document["modelTreeDigests"], {"detector", "recognizer"}, "modelTreeDigests")
    require_sha256(models["detector"], "modelTreeDigests.detector")
    require_sha256(models["recognizer"], "modelTreeDigests.recognizer")
    if not isinstance(document["createdAt"], str) or not document["createdAt"].endswith("Z"):
        raise ProducerValidationError("createdAt must be an RFC3339 UTC timestamp")
    return document


def validate_producer_evidence(value: Mapping[str, Any]) -> dict[str, Any]:
    document = _exact(
        value,
        {
            "schemaVersion", "attemptId", "producerStatus", "lastCompletedStage",
            "inputLockSha256", "staging", "release", "payload", "evidenceSubjectDigest",
        },
        "producer evidence",
    )
    if document["schemaVersion"] != 1:
        raise ProducerValidationError("schemaVersion must be 1")
    if not isinstance(document["attemptId"], str) or _ATTEMPT_RE.fullmatch(document["attemptId"]) is None:
        raise ProducerValidationError("attemptId is invalid")
    if document["producerStatus"] not in _STATUS_VALUES:
        raise ProducerValidationError("producerStatus is invalid")
    stage = document["lastCompletedStage"]
    if stage not in _STAGES:
        raise ProducerValidationError("lastCompletedStage is invalid")
    allowed_stages = _STATUS_STAGES.get(document["producerStatus"])
    if allowed_stages is not None and stage not in allowed_stages:
        raise ProducerValidationError("lastCompletedStage differs from producerStatus")
    require_sha256(document["inputLockSha256"], "inputLockSha256")
    rank = _STAGES.index(stage)
    expected = {
        "staging": rank >= 1,
        "payload": rank >= 2,
        "release": rank >= 3,
        "evidenceSubjectDigest": rank >= 3,
    }
    for field, present in expected.items():
        if (document[field] is not None) != present:
            raise ProducerValidationError(f"{field} nullability differs from lastCompletedStage")
    if document["staging"] is not None:
        _validate_artifact_file(document["staging"], "staging")
    if document["release"] is not None:
        _validate_artifact_file(document["release"], "release")
    if document["evidenceSubjectDigest"] is not None:
        _oci_digest(document["evidenceSubjectDigest"], "evidenceSubjectDigest")
    if document["payload"] is not None:
        payload = _exact(
            document["payload"],
            {
                "packageLock", "detectorModel", "recognizerModel", "legalInventory",
                "spdx", "provenanceAttestation", "sbomAttestation",
            },
            "payload",
        )
        for field in ("packageLock", "detectorModel", "recognizerModel", "legalInventory", "spdx"):
            _validate_artifact_file(payload[field], f"payload.{field}")
        for field, (expected_path, expected_predicate) in _ATTESTATION_CONTRACT.items():
            descriptor = _exact(
                payload[field],
                {"path", "bytes", "sha256", "predicateType", "subjectDigest", "signer", "issuer", "verified"},
                f"payload.{field}",
            )
            if descriptor["path"] != expected_path:
                raise ProducerValidationError(f"payload.{field}.path differs")
            _positive(descriptor["bytes"], f"payload.{field}.bytes")
            require_sha256(descriptor["sha256"], f"payload.{field}.sha256")
            _oci_digest(descriptor["subjectDigest"], f"payload.{field}.subjectDigest")
            for text_field in ("predicateType", "signer", "issuer"):
                if not isinstance(descriptor[text_field], str) or not descriptor[text_field]:
                    raise ProducerValidationError(f"payload.{field}.{text_field} is invalid")
            if descriptor["predicateType"] != expected_predicate:
                raise ProducerValidationError(f"payload.{field}.predicateType differs")
            if descriptor["subjectDigest"] != document["evidenceSubjectDigest"]:
                raise ProducerValidationError(f"payload.{field}.subjectDigest differs")
            if descriptor["signer"] != _SIGNER_WORKFLOW:
                raise ProducerValidationError(f"payload.{field}.signer differs")
            if descriptor["issuer"] != _OIDC_ISSUER:
                raise ProducerValidationError(f"payload.{field}.issuer differs")
            if descriptor["verified"] is not True:
                raise ProducerValidationError(f"payload.{field}.verified must be true")
    return document


def validate_ledger_fragment(value: Mapping[str, Any]) -> dict[str, Any]:
    document = _exact(
        value,
        {
            "schemaVersion", "attemptId", "producerStatus", "lastCompletedStage",
            "mapped609Status", "inputLockSha256", "imagePlatformDigest",
            "evidenceSha256",
            "reconciliationSha256", "cleanupSha256", "revocationsSha256",
            "revocationsCommitSha",
        },
        "artifact ledger fragment",
    )
    if document["schemaVersion"] != 1:
        raise ProducerValidationError("schemaVersion must be 1")
    if not isinstance(document["attemptId"], str) or _ATTEMPT_RE.fullmatch(document["attemptId"]) is None:
        raise ProducerValidationError("attemptId is invalid")
    if document["producerStatus"] not in _STATUS_VALUES or document["lastCompletedStage"] not in _STAGES:
        raise ProducerValidationError("ledger status or stage is invalid")
    if document["mapped609Status"] not in {"PENDING", "BLOCKED", "REJECTED"}:
        raise ProducerValidationError("mapped609Status is invalid")
    for field in (
        "inputLockSha256", "evidenceSha256", "reconciliationSha256",
        "cleanupSha256", "revocationsSha256",
    ):
        require_sha256(document[field], field)
    if document["imagePlatformDigest"] is not None:
        _oci_digest(document["imagePlatformDigest"], "imagePlatformDigest")
    if not isinstance(document["revocationsCommitSha"], str) or _COMMIT_RE.fullmatch(document["revocationsCommitSha"]) is None:
        raise ProducerValidationError("revocationsCommitSha is invalid")
    return document


def _write_all(descriptor: int, chunk: bytes) -> None:
    offset = 0
    while offset < len(chunk):
        written = os.write(descriptor, chunk[offset:])
        if written <= 0:
            raise OSError("short write")
        offset += written


def _open_directory(parent_fd: int, name: str) -> int:
    flags = os.O_RDONLY | getattr(os, "O_DIRECTORY", 0) | getattr(os, "O_NOFOLLOW", 0) | getattr(os, "O_CLOEXEC", 0)
    try:
        descriptor = os.open(name, flags, dir_fd=parent_fd)
    except OSError as exc:
        raise ProducerValidationError("materialization directory was replaced") from exc
    metadata = os.fstat(descriptor)
    if not stat.S_ISDIR(metadata.st_mode):
        os.close(descriptor)
        raise ProducerValidationError("materialization parent is not a directory")
    return descriptor


def _verify_materialized_tree(
    root_fd: int, descriptors: Sequence[Mapping[str, Any]]
) -> None:
    expected: dict[str | None, set[str]] = {None: set()}
    for descriptor in descriptors:
        parts = PurePosixPath(descriptor["path"]).parts
        parent = parts[0] if len(parts) == 2 else None
        if parent is not None:
            expected[None].add(parent)
            expected.setdefault(parent, set()).add(parts[1])
        else:
            expected[None].add(parts[0])
    if set(os.listdir(root_fd)) != expected[None]:
        raise ProducerValidationError("materialized root entries differ from allowlist")

    def verify_files(directory_fd: int, names: set[str]) -> None:
        if set(os.listdir(directory_fd)) != names:
            raise ProducerValidationError("materialized directory entries differ from allowlist")
        for name in names:
            metadata = os.stat(name, dir_fd=directory_fd, follow_symlinks=False)
            if (
                not stat.S_ISREG(metadata.st_mode)
                or metadata.st_nlink != 1
                or stat.S_IMODE(metadata.st_mode) != 0o600
            ):
                raise ProducerValidationError("materialized evidence file contract differs")

    top_files = {name for name in expected[None] if name not in _PARENT_DIRECTORIES}
    for name in top_files:
        metadata = os.stat(name, dir_fd=root_fd, follow_symlinks=False)
        if (
            not stat.S_ISREG(metadata.st_mode)
            or metadata.st_nlink != 1
            or stat.S_IMODE(metadata.st_mode) != 0o600
        ):
            raise ProducerValidationError("materialized evidence file contract differs")
    for parent in _PARENT_DIRECTORIES:
        directory_fd = _open_directory(root_fd, parent)
        try:
            metadata = os.fstat(directory_fd)
            if stat.S_IMODE(metadata.st_mode) != 0o700:
                raise ProducerValidationError("materialized directory mode differs")
            verify_files(directory_fd, expected[parent])
        finally:
            os.close(directory_fd)


def materialize_evidence_files(
    descriptors: Sequence[Mapping[str, Any]],
    root: Path,
    read_blob: Callable[[str], Iterable[bytes]],
    *,
    limits: MaterializationLimits | None = None,
    monotonic: Callable[[], float] = time.monotonic,
) -> None:
    """Materialize exact blobs below a fresh directory using directory-relative FDs."""

    active = limits or MaterializationLimits()
    validated = _validate_file_descriptors(list(descriptors), active)
    if os.path.lexists(root):
        raise ProducerValidationError("materialization root is pre-existing")
    deadline = monotonic() + active.total_timeout_seconds
    root_fd: int | None = None
    created = False
    total = 0
    try:
        os.mkdir(root, mode=0o700)
        created = True
        root_fd = os.open(
            root,
            os.O_RDONLY | getattr(os, "O_DIRECTORY", 0) | getattr(os, "O_NOFOLLOW", 0) | getattr(os, "O_CLOEXEC", 0),
        )
        os.fchmod(root_fd, 0o700)
        for descriptor in validated:
            if monotonic() > deadline:
                raise ProducerValidationError("materialization deadline exceeded")
            relative = PurePosixPath(descriptor["path"])
            parent_fd = root_fd
            owned_parent_fd: int | None = None
            if len(relative.parts) == 2:
                parent = relative.parts[0]
                try:
                    os.mkdir(parent, mode=0o700, dir_fd=root_fd)
                except FileExistsError:
                    pass
                owned_parent_fd = _open_directory(root_fd, parent)
                os.fchmod(owned_parent_fd, 0o700)
                parent_fd = owned_parent_fd
            filename = relative.parts[-1]
            temporary = f".{filename}.partial"
            file_fd: int | None = None
            try:
                flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL | getattr(os, "O_NOFOLLOW", 0) | getattr(os, "O_CLOEXEC", 0)
                file_fd = os.open(temporary, flags, 0o600, dir_fd=parent_fd)
                digest = hashlib.sha256()
                count = 0
                for chunk in read_blob(descriptor["sha256"]):
                    if not isinstance(chunk, bytes) or not chunk:
                        raise ProducerValidationError("blob stream yielded an invalid chunk")
                    if monotonic() > deadline:
                        raise ProducerValidationError("materialization deadline exceeded")
                    count += len(chunk)
                    total += len(chunk)
                    if count > descriptor["bytes"] or count > active.max_file_bytes:
                        raise ProducerValidationError("blob size exceeds descriptor")
                    if total > active.max_files_bytes:
                        raise ProducerValidationError("materialization bytes exceed total limit")
                    digest.update(chunk)
                    _write_all(file_fd, chunk)
                os.fsync(file_fd)
                metadata = os.fstat(file_fd)
                if not stat.S_ISREG(metadata.st_mode) or metadata.st_nlink != 1:
                    raise ProducerValidationError("materialized temporary is not an owned regular file")
                if count != descriptor["bytes"]:
                    raise ProducerValidationError("blob size differs from descriptor")
                if digest.hexdigest() != descriptor["sha256"]:
                    raise ProducerValidationError("blob sha256 differs from descriptor")
                os.close(file_fd)
                file_fd = None
                os.link(
                    temporary,
                    filename,
                    src_dir_fd=parent_fd,
                    dst_dir_fd=parent_fd,
                    follow_symlinks=False,
                )
                os.unlink(temporary, dir_fd=parent_fd)
                final_fd = os.open(
                    filename,
                    os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0) | getattr(os, "O_CLOEXEC", 0),
                    dir_fd=parent_fd,
                )
                try:
                    final = os.fstat(final_fd)
                    if not stat.S_ISREG(final.st_mode) or final.st_nlink != 1:
                        raise ProducerValidationError("materialized target is not a regular file")
                    if stat.S_IMODE(final.st_mode) != 0o600:
                        raise ProducerValidationError("materialized target mode differs")
                finally:
                    os.close(final_fd)
            finally:
                if file_fd is not None:
                    os.close(file_fd)
                try:
                    os.unlink(temporary, dir_fd=parent_fd)
                except FileNotFoundError:
                    pass
                if owned_parent_fd is not None:
                    os.close(owned_parent_fd)
        _verify_materialized_tree(root_fd, validated)
    except BaseException:
        if root_fd is not None:
            os.close(root_fd)
            root_fd = None
        if created:
            shutil.rmtree(root, ignore_errors=True)
        raise
    finally:
        if root_fd is not None:
            os.close(root_fd)


def _validate_raw_blob(raw: bytes, descriptor: Mapping[str, Any], field: str) -> None:
    if len(raw) != descriptor["size"]:
        raise ProducerValidationError(f"{field} size differs from descriptor")
    if "sha256:" + sha256_hex(raw) != descriptor["digest"]:
        raise ProducerValidationError(f"{field} digest differs from descriptor")


def _write_manifest_below_root(root: Path, raw: bytes) -> None:
    root_fd = os.open(
        root,
        os.O_RDONLY | getattr(os, "O_DIRECTORY", 0) | getattr(os, "O_NOFOLLOW", 0) | getattr(os, "O_CLOEXEC", 0),
    )
    descriptor: int | None = None
    try:
        descriptor = os.open(
            "evidence-manifest.json",
            os.O_WRONLY | os.O_CREAT | os.O_EXCL | getattr(os, "O_NOFOLLOW", 0) | getattr(os, "O_CLOEXEC", 0),
            0o600,
            dir_fd=root_fd,
        )
        _write_all(descriptor, raw)
        os.fsync(descriptor)
        metadata = os.fstat(descriptor)
        if not stat.S_ISREG(metadata.st_mode) or metadata.st_nlink != 1:
            raise ProducerValidationError("evidence manifest target is not a regular file")
    finally:
        if descriptor is not None:
            os.close(descriptor)
        os.close(root_fd)


def verify_public_evidence(
    *,
    oras_bin: Path,
    gh_bin: Path,
    reference: str,
    root: Path,
    environment: Mapping[str, str],
    run_command: Callable[[Sequence[str], int, int], bytes],
    verify_attestation: Callable[[Path, Path, str], None],
    limits: MaterializationLimits | None = None,
) -> dict[str, Any]:
    """Verify and materialize one anonymous digest-pinned public evidence artifact."""

    active = limits or MaterializationLimits()
    validate_anonymous_environment(environment)
    if not oras_bin.is_absolute() or not gh_bin.is_absolute():
        raise ProducerValidationError("verifier binary paths must be absolute")
    if not os.path.isfile(oras_bin) or os.path.islink(oras_bin) or not os.access(oras_bin, os.X_OK):
        raise ProducerValidationError("ORAS binary must be an executable regular file")
    if not os.path.isfile(gh_bin) or os.path.islink(gh_bin) or not os.access(gh_bin, os.X_OK):
        raise ProducerValidationError("gh binary must be an executable regular file")
    prefix = "ghcr.io/bluetape4k/paddleocr-service@sha256:"
    if not reference.startswith(prefix) or _OCI_DIGEST_RE.fullmatch(reference.split("@", 1)[1]) is None:
        raise ProducerValidationError("public evidence ref must be the digest-pinned producer repository")
    version = run_command([str(oras_bin), "version"], 64 * 1024, 10).decode("utf-8", errors="strict")
    if not re.search(r"(?m)^Version:\s*1\.3\.4\s*$", version):
        raise ProducerValidationError("ORAS version must be 1.3.4")
    manifest_raw = run_command(
        [str(oras_bin), "manifest", "fetch", "--output", "-", reference],
        active.max_manifest_bytes,
        active.manifest_timeout_seconds,
    )
    expected_manifest_digest = reference.split("@", 1)[1]
    if "sha256:" + sha256_hex(manifest_raw) != expected_manifest_digest:
        raise ProducerValidationError("public manifest bytes differ from digest-pinned ref")
    manifest = load_json_bytes(manifest_raw, active.max_manifest_bytes, max_depth=16, max_entries=1000)
    layers = validate_remote_evidence_manifest(manifest, limits=active)
    repository = reference.split("@", 1)[0]

    config_descriptor = manifest["config"]
    config_raw = run_command(
        [str(oras_bin), "blob", "fetch", "--output", "-", repository + "@" + config_descriptor["digest"]],
        64 * 1024,
        active.blob_read_timeout_seconds,
    )
    _validate_raw_blob(config_raw, config_descriptor, "config")
    config = load_json_bytes(config_raw, 64 * 1024, max_depth=8, max_entries=100)
    config = _exact(config, {"schemaVersion", "artifactType", "attemptId", "subjectDigest"}, "evidence config")
    if config["schemaVersion"] != 1 or config["artifactType"] != ARTIFACT_TYPE:
        raise ProducerValidationError("evidence config contract differs")
    if not isinstance(config["attemptId"], str) or _ATTEMPT_RE.fullmatch(config["attemptId"]) is None:
        raise ProducerValidationError("evidence config attemptId is invalid")
    if config["subjectDigest"] != manifest["subject"]["digest"]:
        raise ProducerValidationError("evidence config subjectDigest differs")
    if jcs_bytes(config) != config_raw:
        raise ProducerValidationError("evidence config must use canonical JSON")

    internal_descriptor = layers["evidence-manifest.json"]
    internal_raw = run_command(
        [str(oras_bin), "blob", "fetch", "--output", "-", repository + "@" + internal_descriptor["digest"]],
        active.max_manifest_bytes,
        active.blob_read_timeout_seconds,
    )
    _validate_raw_blob(internal_raw, internal_descriptor, "evidence manifest")
    internal = load_json_bytes(internal_raw, active.max_manifest_bytes, max_depth=16, max_entries=1000)
    validated_internal = validate_evidence_file_manifest(internal, limits=active)
    if jcs_bytes(validated_internal) != internal_raw:
        raise ProducerValidationError("evidence manifest must use canonical JSON")
    if validated_internal["subjectDigest"] != manifest["subject"]["digest"]:
        raise ProducerValidationError("evidence manifest subjectDigest differs from OCI subject")
    descriptors_by_hash: dict[str, dict[str, Any]] = {}
    for descriptor in validated_internal["files"]:
        remote = layers[descriptor["path"]]
        if remote["size"] != descriptor["bytes"] or remote["digest"] != "sha256:" + descriptor["sha256"]:
            raise ProducerValidationError("evidence file descriptor differs from OCI layer")
        descriptors_by_hash[descriptor["sha256"]] = remote

    def read_blob(digest: str) -> Iterable[bytes]:
        descriptor = descriptors_by_hash[digest]
        raw = run_command(
            [str(oras_bin), "blob", "fetch", "--output", "-", repository + "@" + descriptor["digest"]],
            min(active.max_file_bytes, descriptor["size"]),
            active.blob_read_timeout_seconds,
        )
        yield raw

    materialize_evidence_files(validated_internal["files"], root, read_blob, limits=active)
    try:
        _write_manifest_below_root(root, internal_raw)
        platform_raw = (root / "platform-manifest.json").read_bytes()
        if "sha256:" + sha256_hex(platform_raw) != manifest["subject"]["digest"]:
            raise ProducerValidationError("platform manifest bytes differ from OCI subject")
        producer_evidence_raw = (root / "producer-evidence.json").read_bytes()
        producer_evidence = validate_producer_evidence(
            load_json_bytes(producer_evidence_raw, active.max_file_bytes, max_depth=24, max_entries=5000)
        )
        if jcs_bytes(producer_evidence) != producer_evidence_raw:
            raise ProducerValidationError("producer evidence must use canonical JSON")
        ledger_raw = (root / "artifact-ledger.fragment.json").read_bytes()
        ledger = validate_ledger_fragment(
            load_json_bytes(ledger_raw, active.max_file_bytes, max_depth=16, max_entries=1000)
        )
        if jcs_bytes(ledger) != ledger_raw:
            raise ProducerValidationError("ledger fragment must use canonical JSON")
        if producer_evidence["attemptId"] != validated_internal["attemptId"] or ledger["attemptId"] != validated_internal["attemptId"]:
            raise ProducerValidationError("evidence attemptId differs across documents")
        if producer_evidence["inputLockSha256"] != validated_internal["inputLockSha256"] or ledger["inputLockSha256"] != validated_internal["inputLockSha256"]:
            raise ProducerValidationError("evidence inputLockSha256 differs across documents")
        if producer_evidence["evidenceSubjectDigest"] != validated_internal["subjectDigest"]:
            raise ProducerValidationError("producer evidence subject differs from file manifest")
        if ledger["imagePlatformDigest"] != validated_internal["subjectDigest"]:
            raise ProducerValidationError("ledger imagePlatformDigest differs from file manifest")
        if ledger["evidenceSha256"] != sha256_hex(producer_evidence_raw):
            raise ProducerValidationError("ledger evidenceSha256 differs from producer evidence")
        if ledger["revocationsSha256"] != validated_internal["revocationsSha256"]:
            raise ProducerValidationError("ledger revocationsSha256 differs from file manifest")
        if ledger["revocationsCommitSha"] != validated_internal["revocationsCommitSha"]:
            raise ProducerValidationError("ledger revocationsCommitSha differs from file manifest")
        if producer_evidence["producerStatus"] != ledger["producerStatus"] or producer_evidence["lastCompletedStage"] != ledger["lastCompletedStage"]:
            raise ProducerValidationError("evidence lifecycle differs from ledger fragment")
        files_by_path = {item["path"]: item for item in validated_internal["files"]}
        payload_paths = {
            "packageLock": "manifests/package-lock.json",
            "detectorModel": "manifests/model-detector.json",
            "recognizerModel": "manifests/model-recognizer.json",
            "legalInventory": "legal-inventory.json",
            "spdx": "sbom.spdx.json",
            "provenanceAttestation": "attestations/provenance.bundle.jsonl",
            "sbomAttestation": "attestations/sbom.bundle.jsonl",
        }
        for field, path in payload_paths.items():
            evidence_descriptor = producer_evidence["payload"][field]
            file_descriptor = files_by_path[path]
            if (
                evidence_descriptor["path"] != path
                or evidence_descriptor["bytes"] != file_descriptor["bytes"]
                or evidence_descriptor["sha256"] != file_descriptor["sha256"]
            ):
                raise ProducerValidationError(f"producer evidence {field} differs from file manifest")
        provenance = root / "attestations/provenance.bundle.jsonl"
        sbom = root / "attestations/sbom.bundle.jsonl"
        for path in (provenance, sbom):
            parsed = load_jsonl_bytes(path.read_bytes(), 4 * 1024 * 1024, max_lines=30)
            if not parsed.envelopes:
                raise ProducerValidationError("attestation bundle must not be empty")
        verify_attestation(root / "platform-manifest.json", provenance, "https://slsa.dev/provenance/v1")
        verify_attestation(root / "platform-manifest.json", sbom, "https://spdx.dev/Document/v2.3")
    except BaseException:
        shutil.rmtree(root, ignore_errors=True)
        raise
    return {
        "manifestDigest": expected_manifest_digest,
        "fileManifestSha256": sha256_hex(internal_raw),
        "provenanceVerified": True,
        "sbomVerified": True,
        "attemptId": validated_internal["attemptId"],
    }
