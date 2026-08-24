"""Validate the immutable receipt contract for the Issue #545 service gate."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import re
import sys
from pathlib import Path
from typing import Any

DEFAULT_REPOSITORY = "bluetape4k/bluetape4k-image"
MAX_RECEIPT_BYTES = 1024 * 1024
MAX_ARTIFACT_BYTES = 64 * 1024 * 1024
MAX_SMOKE_LOG_BYTES = 4 * 1024 * 1024
ARTIFACT_READ_CHUNK_BYTES = 1024 * 1024
SHA256_PATTERN = re.compile(r"\A[0-9a-f]{64}\Z")
COMMIT_PATTERN = re.compile(r"\A[0-9a-f]{40}\Z")
VERSION_PATTERN = re.compile(
    r"\A(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)"
    r"(?:-(?:0|[1-9A-Za-z-][0-9A-Za-z-]*)(?:\.(?:0|[1-9A-Za-z-][0-9A-Za-z-]*))*)?"
    r"(?:\+[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?\Z"
)
IMAGE_DIGEST_PATTERN = re.compile(r"\Asha256:[0-9a-f]{64}\Z")
SAFE_ARTIFACT_PATH_PATTERN = re.compile(
    r"\A(?!/)(?!.*\\)(?!.*(?:^|/)\.\.(?:/|$))[A-Za-z0-9._/-]+\Z"
)
FORBIDDEN_TEXT_PATTERN = re.compile(
    r"(?:OCR_TEXT_SENTINEL|known-ocr-sentinel|data:image|file://|"
    r"(?:^|[\\/])(?:models?|model-cache)(?:[\\/]|$)|"
    r"(?:[A-Za-z]:\\|\\\\)|"
    r"(?:authorization|proxy-authorization)\s*[:=]\s*\S+|"
    r"\bbearer\s+[A-Za-z0-9._~-]+|"
    r"(?:password|secret|token|api[_-]?key|model[_-]?(?:path|file|dir))"
    r"\s*[:=]\s*\S+)",
    re.IGNORECASE,
)

TOP_LEVEL_KEYS = {
    "schemaVersion",
    "kind",
    "status",
    "validationScope",
    "run",
    "software",
    "security",
    "artifacts",
    "metrics",
}
RUN_KEYS = {
    "repository",
    "commitSha",
    "workflowRunId",
    "fixtureManifestSha256",
    "modelManifestSha256",
    "containerImageDigest",
    "hostArchitecture",
    "configSha256",
}
SOFTWARE_KEYS = {"python", "paddle", "paddleocr", "paddlex"}
SECURITY_KEYS = {
    "offlineStartup",
    "modelChecksumVerified",
    "noFirstUseDownload",
    "networkEgressDenied",
    "authRequired",
    "tlsVerified",
    "sensitiveLogScanPassed",
    "requestLimitsEnforced",
    "nonRoot",
    "readOnlyRoot",
    "capabilitiesDropped",
    "cleanupVerified",
}
METRIC_KEYS = {
    "readinessSeconds",
    "ocrLatencyMs",
    "rssBytes",
    "requestBytes",
    "responseBytes",
}
ARTIFACT_KEYS = {"name", "path", "bytes", "sha256", "mediaType"}
REQUIRED_ARTIFACTS = {
    "smoke-report",
    "smoke-logs",
    "smoke-cleanup",
    "sbom",
    "provenance-attestation",
    "sbom-attestation",
    "license-notice",
}
SUPPORTED_ARCHITECTURES = {"linux/amd64", "linux/arm64"}


class ReceiptValidationError(ValueError):
    """Raised when a receipt violates the fail-closed contract."""


def _reject_duplicate_keys(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise ReceiptValidationError(f"duplicate JSON key: {key}")
        result[key] = value
    return result


def _load_json(path: Path) -> dict[str, Any]:
    if path.is_symlink() or not path.is_file():
        raise ReceiptValidationError(
            f"receipt must be a regular non-symlink file: {path}"
        )
    if path.stat().st_size > MAX_RECEIPT_BYTES:
        raise ReceiptValidationError(
            f"receipt exceeds {MAX_RECEIPT_BYTES} bytes: {path}"
        )
    with path.open("rb") as stream:
        payload = stream.read(MAX_RECEIPT_BYTES + 1)
    if len(payload) > MAX_RECEIPT_BYTES:
        raise ReceiptValidationError(
            f"receipt exceeds {MAX_RECEIPT_BYTES} bytes: {path}"
        )
    try:
        value = json.loads(
            payload.decode("utf-8"), object_pairs_hook=_reject_duplicate_keys
        )
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise ReceiptValidationError(f"receipt JSON is invalid: {error}") from error
    if not isinstance(value, dict):
        raise ReceiptValidationError("receipt root must be a JSON object")
    return value


def _object(
    value: Any, path: str, required: set[str], allowed: set[str] | None = None
) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise ReceiptValidationError(f"{path} must be an object")
    actual = set(value)
    missing = sorted(required - actual)
    if missing:
        raise ReceiptValidationError(f"{path} is missing: {', '.join(missing)}")
    unexpected = sorted(actual - (allowed or required))
    if unexpected:
        raise ReceiptValidationError(
            f"{path} has unexpected fields: {', '.join(unexpected)}"
        )
    return value


def _string(value: Any, path: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise ReceiptValidationError(f"{path} must be a non-empty string")
    return value


def _boolean(value: Any, path: str) -> bool:
    if not isinstance(value, bool):
        raise ReceiptValidationError(f"{path} must be a boolean")
    return value


def _sha256(value: Any, path: str) -> str:
    value = _string(value, path)
    if not SHA256_PATTERN.fullmatch(value):
        raise ReceiptValidationError(
            f"{path} must be 64 lowercase hexadecimal characters"
        )
    return value


def _finite_number(value: Any, path: str) -> float:
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise ReceiptValidationError(f"{path} must be a non-negative number")
    numeric = float(value)
    if not math.isfinite(numeric) or numeric < 0:
        raise ReceiptValidationError(f"{path} must be a non-negative finite number")
    return numeric


def _walk_strings(value: Any, path: str = "receipt") -> None:
    if isinstance(value, str) and FORBIDDEN_TEXT_PATTERN.search(value):
        raise ReceiptValidationError(
            f"{path} contains a forbidden payload, credential, or model path"
        )
    if isinstance(value, dict):
        for key, child in value.items():
            _walk_strings(child, f"{path}.{key}")
    elif isinstance(value, list):
        for index, child in enumerate(value):
            _walk_strings(child, f"{path}[{index}]")


def _validate_run(value: Any, repository: str) -> None:
    run = _object(value, "run", RUN_KEYS)
    if _string(run["repository"], "run.repository") != repository:
        raise ReceiptValidationError(f"run.repository must be {repository}")
    commit = _string(run["commitSha"], "run.commitSha")
    if not COMMIT_PATTERN.fullmatch(commit):
        raise ReceiptValidationError(
            "run.commitSha must be a 40-character lowercase commit SHA"
        )
    workflow_run_id = run["workflowRunId"]
    if (
        isinstance(workflow_run_id, bool)
        or not isinstance(workflow_run_id, int)
        or workflow_run_id <= 0
    ):
        raise ReceiptValidationError("run.workflowRunId must be a positive integer")
    _sha256(run["fixtureManifestSha256"], "run.fixtureManifestSha256")
    _sha256(run["modelManifestSha256"], "run.modelManifestSha256")
    image_digest = _string(run["containerImageDigest"], "run.containerImageDigest")
    if not IMAGE_DIGEST_PATTERN.fullmatch(image_digest):
        raise ReceiptValidationError(
            "run.containerImageDigest must be sha256:<64 lowercase hexadecimal characters>"
        )
    architecture = _string(run["hostArchitecture"], "run.hostArchitecture")
    if architecture not in SUPPORTED_ARCHITECTURES:
        raise ReceiptValidationError(
            f"run.hostArchitecture must be one of {sorted(SUPPORTED_ARCHITECTURES)}"
        )
    _sha256(run["configSha256"], "run.configSha256")


def _validate_software(value: Any) -> None:
    software = _object(value, "software", SOFTWARE_KEYS)
    for name, version in software.items():
        version = _string(version, f"software.{name}")
        if not VERSION_PATTERN.fullmatch(version):
            raise ReceiptValidationError(
                f"software.{name} must be an exact semantic version, not a floating selector"
            )


def _validate_security(value: Any) -> None:
    security = _object(value, "security", SECURITY_KEYS)
    failed = sorted(
        name
        for name, result in security.items()
        if not _boolean(result, f"security.{name}")
    )
    if failed:
        raise ReceiptValidationError(
            f"security checks must all pass: {', '.join(failed)}"
        )


def _validate_artifacts(value: Any, artifact_root: Path | None) -> None:
    if not isinstance(value, list) or not value:
        raise ReceiptValidationError("artifacts must be a non-empty array")
    names: set[str] = set()
    for index, item in enumerate(value):
        artifact = _object(item, f"artifacts[{index}]", ARTIFACT_KEYS)
        name = _string(artifact["name"], f"artifacts[{index}].name")
        if name in names:
            raise ReceiptValidationError(f"duplicate artifact name: {name}")
        names.add(name)
        relative_path = _string(artifact["path"], f"artifacts[{index}].path")
        if (
            not SAFE_ARTIFACT_PATH_PATTERN.fullmatch(relative_path)
            or relative_path in {".", ".."}
            or relative_path.endswith("/")
            or "//" in relative_path
            or "/./" in f"/{relative_path}/"
        ):
            raise ReceiptValidationError(
                f"artifacts[{index}].path must be a safe relative path"
            )
        bytes_count = artifact["bytes"]
        if (
            isinstance(bytes_count, bool)
            or not isinstance(bytes_count, int)
            or bytes_count < 0
        ):
            raise ReceiptValidationError(
                f"artifacts[{index}].bytes must be a non-negative integer"
            )
        expected_hash = _sha256(artifact["sha256"], f"artifacts[{index}].sha256")
        media_type = _string(artifact["mediaType"], f"artifacts[{index}].mediaType")
        if artifact_root is None:
            continue
        root = artifact_root.resolve()
        lexical_candidate = root / relative_path
        if any(part.is_symlink() for part in _path_parts(root, relative_path)):
            raise ReceiptValidationError(
                f"artifact path contains a symlink: {relative_path}"
            )
        candidate = lexical_candidate.resolve()
        if root not in candidate.parents:
            raise ReceiptValidationError(
                f"artifacts[{index}].path escapes artifact root"
            )
        if not candidate.is_file():
            raise ReceiptValidationError(
                f"artifact file is missing or symlinked: {relative_path}"
            )
        actual_size = candidate.stat().st_size
        if actual_size > MAX_ARTIFACT_BYTES:
            raise ReceiptValidationError(
                f"artifact exceeds {MAX_ARTIFACT_BYTES} bytes: {relative_path}"
            )
        if name == "smoke-logs" and actual_size > MAX_SMOKE_LOG_BYTES:
            raise ReceiptValidationError(
                f"smoke log exceeds {MAX_SMOKE_LOG_BYTES} bytes"
            )
        if actual_size != bytes_count:
            raise ReceiptValidationError(
                f"artifact byte count differs: {relative_path}"
            )
        actual_hash, actual = _hash_artifact(
            candidate,
            bytes_count,
            capture_content=name == "smoke-logs",
        )
        if actual_hash != expected_hash:
            raise ReceiptValidationError(f"artifact SHA-256 differs: {relative_path}")
        if name == "smoke-logs":
            if not media_type.startswith("text/"):
                raise ReceiptValidationError("smoke-logs must use a text media type")
            try:
                log_text = actual.decode("utf-8")
            except UnicodeDecodeError as error:
                raise ReceiptValidationError(
                    "smoke-logs must be valid UTF-8"
                ) from error
            if FORBIDDEN_TEXT_PATTERN.search(log_text):
                raise ReceiptValidationError(
                    "smoke-logs contains a forbidden payload or credential"
                )
    missing = sorted(REQUIRED_ARTIFACTS - names)
    if missing:
        raise ReceiptValidationError(
            f"required artifacts are missing: {', '.join(missing)}"
        )


def _path_parts(root: Path, relative_path: str) -> list[Path]:
    """Return each lexical path component so symlinks cannot hide in a path."""

    current = root
    parts: list[Path] = []
    for part in Path(relative_path).parts:
        current /= part
        parts.append(current)
    return parts


def _hash_artifact(
    path: Path,
    expected_bytes: int,
    *,
    capture_content: bool,
) -> tuple[str, bytes]:
    digest = hashlib.sha256()
    content = bytearray() if capture_content else None
    actual_bytes = 0
    with path.open("rb") as stream:
        while chunk := stream.read(ARTIFACT_READ_CHUNK_BYTES):
            actual_bytes += len(chunk)
            if actual_bytes > expected_bytes:
                raise ReceiptValidationError(f"artifact byte count differs: {path}")
            digest.update(chunk)
            if content is not None:
                content.extend(chunk)
    if actual_bytes != expected_bytes:
        raise ReceiptValidationError(f"artifact byte count differs: {path}")
    return digest.hexdigest(), bytes(content or b"")


def _validate_metrics(value: Any) -> None:
    metrics = _object(value, "metrics", METRIC_KEYS)
    for name, metric in metrics.items():
        _finite_number(metric, f"metrics.{name}")


def validate_receipt(
    receipt: dict[str, Any],
    *,
    repository: str = DEFAULT_REPOSITORY,
    artifact_root: Path | None = None,
) -> None:
    """Validate a decoded receipt and optionally its referenced artifact bytes."""

    _object(receipt, "receipt", TOP_LEVEL_KEYS)
    _walk_strings(receipt)
    if (
        isinstance(receipt["schemaVersion"], bool)
        or not isinstance(receipt["schemaVersion"], int)
        or receipt["schemaVersion"] != 1
    ):
        raise ReceiptValidationError("receipt.schemaVersion must be 1")
    if _string(receipt["kind"], "receipt.kind") != "paddle-ocr-service-receipt":
        raise ReceiptValidationError("receipt.kind is unsupported")
    if (
        _string(receipt["validationScope"], "receipt.validationScope")
        != "CONTRACT_ONLY"
    ):
        raise ReceiptValidationError("receipt.validationScope must be CONTRACT_ONLY")
    if _string(receipt["status"], "receipt.status") != "PASS":
        raise ReceiptValidationError(
            "receipt.status must be PASS for an acceptance receipt"
        )
    _validate_run(receipt["run"], repository)
    _validate_software(receipt["software"])
    _validate_security(receipt["security"])
    _validate_artifacts(receipt["artifacts"], artifact_root)
    _validate_metrics(receipt["metrics"])


def validate_receipt_file(
    path: Path,
    *,
    repository: str = DEFAULT_REPOSITORY,
    artifact_root: Path | None = None,
) -> dict[str, Any]:
    """Load and validate one receipt file, returning its decoded object."""

    receipt = _load_json(path)
    validate_receipt(receipt, repository=repository, artifact_root=artifact_root)
    return receipt


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("receipt", type=Path, help="receipt JSON path")
    parser.add_argument("--repository", default=DEFAULT_REPOSITORY)
    parser.add_argument(
        "--artifact-root",
        type=Path,
        help="verify every artifact path and SHA-256 below this directory",
    )
    args = parser.parse_args(argv)
    try:
        validate_receipt_file(
            args.receipt, repository=args.repository, artifact_root=args.artifact_root
        )
    except (OSError, ReceiptValidationError) as error:
        print(f"FAIL: {error}", file=sys.stderr)
        return 1
    print(f"PASS: {args.receipt}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
