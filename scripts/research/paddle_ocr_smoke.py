"""Prepare the fail-closed offline preflight for the Issue #545 service gate."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import stat
import subprocess
import sys
import tempfile
from collections.abc import Callable, Sequence
from dataclasses import dataclass
from pathlib import Path
from typing import Any
from urllib.parse import urlparse

MAX_JSON_BYTES = 1024 * 1024
MAX_MODEL_FILE_BYTES = 256 * 1024 * 1024
MAX_MODEL_TREE_BYTES = 512 * 1024 * 1024
MAX_FIXTURE_FILE_BYTES = 64 * 1024 * 1024
READ_CHUNK_BYTES = 1024 * 1024
SHA256_PATTERN = re.compile(r"\A[0-9a-f]{64}\Z")
IMAGE_REF_PATTERN = re.compile(r"\A[^\x00-\x1f\x7f\s@]+@sha256:[0-9a-f]{64}\Z")
REVISION_PATTERN = re.compile(r"\A[0-9a-f]{40}\Z")
SAFE_RELATIVE_PATH_PATTERN = re.compile(
    r"\A(?!/)(?!.*\\)(?!.*(?:^|/)\.\.(?:/|$))[A-Za-z0-9._/-]+\Z"
)
FORBIDDEN_COMMAND_PATTERN = re.compile(r"[;&|$`()<>\n\r]")
SNAPSHOT_MARKER = ".bluetape4k-model-snapshot"
MODEL_MANIFEST_KEYS = {
    "schemaVersion",
    "modelId",
    "revision",
    "source",
    "licenseSpdx",
    "noticePath",
    "noticeBytes",
    "noticeSha256",
    "files",
    "treeSha256",
    "offline",
}
FIXTURE_MANIFEST_KEYS = {"schemaVersion", "fixtures"}
FIXTURE_KEYS = {"id", "path", "bytes", "sha256"}
MODEL_FILE_KEYS = {"path", "bytes", "sha256"}
CONFIG_KEYS = {
    "schemaVersion",
    "host",
    "port",
    "network",
    "command",
    "requestMaxBytes",
    "responseMaxBytes",
    "readinessTimeoutSeconds",
    "modelMount",
    "outputMount",
}
SECURITY_PLAN = {
    "networkEgressDenied": True,
    "nonRoot": True,
    "daemonRootless": "PENDING_DAEMON_CAPABILITY_CHECK",
    "readOnlyRoot": True,
    "capabilitiesDropped": True,
    "noNewPrivileges": True,
    "modelMountReadOnly": True,
    "modelAggregateLimitBytes": MAX_MODEL_TREE_BYTES,
    "requestLimitsConfigured": True,
    "requestLimitsEnforced": "PENDING_UNTIL_SERVICE_EXECUTION",
    "modelSnapshot": "CREATED_AND_VERIFIED_BEFORE_MOUNT",
    "parentPathTrust": "TRUSTED_TEMPORARY_DIRECTORY",
    "cleanup": "PENDING_UNTIL_SERVICE_EXECUTION",
}


class SmokeValidationError(ValueError):
    """Raised when an offline smoke input violates the execution contract."""


@dataclass(frozen=True)
class ServiceConfig:
    host: str
    port: int
    network: str
    command: tuple[str, ...]
    request_max_bytes: int
    response_max_bytes: int
    readiness_timeout_seconds: int
    model_mount: str
    output_mount: str


@dataclass(frozen=True)
class ModelSnapshot:
    root: Path
    tree_sha256: str
    temp_dir: Any


@dataclass(frozen=True)
class ValidatedInputs:
    image: str
    model_manifest_sha256: str
    model_tree_sha256: str
    fixture_manifest_sha256: str
    config_sha256: str
    config: ServiceConfig
    model_snapshot: ModelSnapshot
    docker_command: tuple[str, ...]


def _reject_duplicate_keys(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise SmokeValidationError(f"duplicate JSON key: {key}")
        result[key] = value
    return result


def _regular_file(path: Path, label: str) -> Path:
    if path.is_symlink() or not path.is_file():
        raise SmokeValidationError(f"{label} must be a regular non-symlink file")
    return path


def _regular_directory(path: Path, label: str) -> Path:
    if path.is_symlink() or not path.is_dir():
        raise SmokeValidationError(f"{label} must be a regular non-symlink directory")
    return path


def _empty_directory(path: Path, label: str) -> Path:
    path = _regular_directory(path, label)
    try:
        children = tuple(path.iterdir())
    except OSError as error:
        raise SmokeValidationError(f"{label} cannot be inspected") from error
    if any(child.is_symlink() for child in children):
        raise SmokeValidationError(f"{label} contains a symlink")
    if children:
        raise SmokeValidationError(f"{label} must be empty before execution")
    return path


def _load_json(path: Path, label: str) -> tuple[dict[str, Any], bytes]:
    path = _regular_file(path, label)
    payload = _read_bounded(path, label, MAX_JSON_BYTES)
    try:
        value = json.loads(
            payload.decode("utf-8"), object_pairs_hook=_reject_duplicate_keys
        )
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise SmokeValidationError(f"{label} JSON is invalid: {error}") from error
    if not isinstance(value, dict):
        raise SmokeValidationError(f"{label} root must be an object")
    return value, payload


def _object(
    value: Any, label: str, required: set[str], allowed: set[str]
) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise SmokeValidationError(f"{label} must be an object")
    actual = set(value)
    missing = sorted(required - actual)
    unexpected = sorted(actual - allowed)
    if missing:
        raise SmokeValidationError(f"{label} is missing: {', '.join(missing)}")
    if unexpected:
        raise SmokeValidationError(
            f"{label} has unexpected fields: {', '.join(unexpected)}"
        )
    return value


def _string(value: Any, label: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise SmokeValidationError(f"{label} must be a non-empty string")
    if any(
        ord(character) < 0x20 or 0x7F <= ord(character) <= 0x9F for character in value
    ):
        raise SmokeValidationError(f"{label} must not contain control characters")
    return value


def _positive_int(value: Any, label: str, *, maximum: int | None = None) -> int:
    if isinstance(value, bool) or not isinstance(value, int) or value <= 0:
        raise SmokeValidationError(f"{label} must be a positive integer")
    if maximum is not None and value > maximum:
        raise SmokeValidationError(f"{label} exceeds {maximum}")
    return value


def _sha256(value: Any, label: str) -> str:
    value = _string(value, label)
    if not SHA256_PATTERN.fullmatch(value):
        raise SmokeValidationError(
            f"{label} must be 64 lowercase hexadecimal characters"
        )
    return value


def _schema_version(value: Any, label: str) -> None:
    if type(value) is not int or value != 1:
        raise SmokeValidationError(f"{label} must be integer 1")


def _safe_relative_path(value: Any, label: str) -> str:
    path = _string(value, label)
    if (
        not SAFE_RELATIVE_PATH_PATTERN.fullmatch(path)
        or path in {".", ".."}
        or path.endswith("/")
        or "//" in path
        or "/./" in f"/{path}/"
    ):
        raise SmokeValidationError(f"{label} must be a safe relative path")
    return path


def _read_bounded(path: Path, label: str, maximum: int) -> bytes:
    fd = _open_regular_file(path, label)
    try:
        return _read_fd_bounded(fd, label, maximum)
    finally:
        os.close(fd)


def _open_regular_file(path: Path, label: str) -> int:
    try:
        fd = os.open(os.fspath(path), os.O_RDONLY | os.O_NOFOLLOW)
    except OSError as error:
        raise SmokeValidationError(
            f"{label} must be a readable regular file"
        ) from error
    try:
        if not stat.S_ISREG(os.fstat(fd).st_mode):
            raise SmokeValidationError(f"{label} must be a regular non-symlink file")
    except BaseException:
        os.close(fd)
        raise
    return fd


def _read_fd_bounded(fd: int, label: str, maximum: int) -> bytes:
    digest = bytearray()
    while chunk := os.read(fd, min(READ_CHUNK_BYTES, maximum + 1 - len(digest))):
        digest.extend(chunk)
        if len(digest) > maximum:
            raise SmokeValidationError(f"{label} exceeds {maximum} bytes")
    return bytes(digest)


def _open_relative_file(root: Path, relative_path: str, label: str) -> int:
    root = _regular_directory(root, f"{label} root")
    try:
        directory_fd = os.open(
            os.fspath(root), os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW
        )
    except OSError as error:
        raise SmokeValidationError(f"{label} root cannot be opened") from error
    current_fd = directory_fd
    try:
        parts = Path(relative_path).parts
        for part in parts[:-1]:
            next_fd = os.open(
                part,
                os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW,
                dir_fd=current_fd,
            )
            os.close(current_fd)
            current_fd = next_fd
        file_fd = os.open(parts[-1], os.O_RDONLY | os.O_NOFOLLOW, dir_fd=current_fd)
        if not stat.S_ISREG(os.fstat(file_fd).st_mode):
            os.close(file_fd)
            raise SmokeValidationError(
                f"{label} must be a regular non-symlink file: {relative_path}"
            )
        return file_fd
    except OSError as error:
        raise SmokeValidationError(
            f"{label} contains a symlink or missing path: {relative_path}"
        ) from error
    finally:
        os.close(current_fd)


def _hash_relative_file(
    root: Path, relative_path: str, label: str, *, maximum: int
) -> tuple[int, str]:
    fd = _open_relative_file(root, relative_path, label)
    digest = hashlib.sha256()
    actual = 0
    try:
        while chunk := os.read(fd, READ_CHUNK_BYTES):
            actual += len(chunk)
            if actual > maximum:
                raise SmokeValidationError(f"{label} exceeds {maximum} bytes")
            digest.update(chunk)
    finally:
        os.close(fd)
    return actual, digest.hexdigest()


def _collect_relative_files(root: Path, label: str) -> set[str]:
    root = _regular_directory(root, f"{label} root")
    result: set[str] = set()
    pending: list[tuple[Path, str]] = [(root, "")]
    while pending:
        current, prefix = pending.pop()
        try:
            entries = tuple(os.scandir(current))
        except OSError as error:
            raise SmokeValidationError(f"{label} cannot be enumerated") from error
        for entry in entries:
            relative = f"{prefix}/{entry.name}" if prefix else entry.name
            if entry.is_symlink():
                raise SmokeValidationError(f"{label} contains a symlink: {relative}")
            if entry.is_dir(follow_symlinks=False):
                pending.append((Path(entry.path), relative))
            elif entry.is_file(follow_symlinks=False):
                result.add(relative)
            else:
                raise SmokeValidationError(
                    f"{label} contains a non-regular entry: {relative}"
                )
    return result


def _tree_digest(entries: Sequence[tuple[str, int, str]]) -> str:
    payload = "".join(f"{path}\0{size}\0{digest}\n" for path, size, digest in entries)
    return hashlib.sha256(payload.encode("utf-8")).hexdigest()


def validate_image_reference(image: Any) -> str:
    image = _string(image, "image")
    if not IMAGE_REF_PATTERN.fullmatch(image):
        raise SmokeValidationError(
            "image must include an immutable @sha256:<64 lowercase hex> digest"
        )
    return image


def load_model_manifest(path: Path, model_root: Path) -> tuple[str, dict[str, Any]]:
    value, payload = _load_json(path, "model manifest")
    manifest = _object(
        value,
        "model manifest",
        MODEL_MANIFEST_KEYS,
        MODEL_MANIFEST_KEYS,
    )
    _schema_version(manifest["schemaVersion"], "model manifest.schemaVersion")
    model_id = _string(manifest["modelId"], "model manifest.modelId")
    revision = _string(manifest["revision"], "model manifest.revision")
    if not REVISION_PATTERN.fullmatch(revision):
        raise SmokeValidationError(
            "model manifest.revision must be a 40-character immutable commit SHA"
        )
    source = _string(manifest["source"], "model manifest.source")
    parsed_source = urlparse(source)
    if (
        parsed_source.scheme != "https"
        or parsed_source.query
        or parsed_source.fragment
        or not any(
            parsed_source.netloc == host and parsed_source.path.startswith(path_prefix)
            for host, path_prefix in (
                ("huggingface.co", "/PaddlePaddle/"),
                ("github.com", "/PaddlePaddle/"),
            )
        )
    ):
        raise SmokeValidationError(
            "model manifest.source is not an allowlisted upstream"
        )
    if _string(manifest["licenseSpdx"], "model manifest.licenseSpdx") != "Apache-2.0":
        raise SmokeValidationError("model manifest.licenseSpdx must be Apache-2.0")
    if manifest["offline"] is not True:
        raise SmokeValidationError("model manifest.offline must be true")

    files = manifest["files"]
    if not isinstance(files, list) or not files or len(files) > 256:
        raise SmokeValidationError("model manifest.files must contain 1..256 entries")
    entries: list[tuple[str, int, str]] = []
    model_total_bytes = 0
    seen: set[str] = set()
    for index, item in enumerate(files):
        file_entry = _object(
            item,
            f"model manifest.files[{index}]",
            MODEL_FILE_KEYS,
            MODEL_FILE_KEYS,
        )
        relative_path = _safe_relative_path(
            file_entry["path"], f"model manifest.files[{index}].path"
        )
        if relative_path == SNAPSHOT_MARKER:
            raise SmokeValidationError("model manifest uses a reserved snapshot path")
        if relative_path in seen:
            raise SmokeValidationError(f"duplicate model file path: {relative_path}")
        seen.add(relative_path)
        expected_bytes = _positive_int(
            file_entry["bytes"],
            f"model manifest.files[{index}].bytes",
            maximum=MAX_MODEL_FILE_BYTES,
        )
        expected_sha = _sha256(
            file_entry["sha256"], f"model manifest.files[{index}].sha256"
        )
        actual_bytes, actual_sha = _hash_relative_file(
            model_root,
            relative_path,
            "model file",
            maximum=MAX_MODEL_FILE_BYTES,
        )
        if actual_bytes != expected_bytes or actual_sha != expected_sha:
            raise SmokeValidationError(
                f"model file hash or size differs: {relative_path}"
            )
        model_total_bytes += actual_bytes
        if model_total_bytes > MAX_MODEL_TREE_BYTES:
            raise SmokeValidationError(
                f"model tree exceeds {MAX_MODEL_TREE_BYTES} bytes"
            )
        entries.append((relative_path, actual_bytes, actual_sha))

    notice_path = _safe_relative_path(
        manifest["noticePath"], "model manifest.noticePath"
    )
    if notice_path in seen:
        raise SmokeValidationError("model manifest.noticePath must be distinct")
    if notice_path == SNAPSHOT_MARKER:
        raise SmokeValidationError("model manifest uses a reserved snapshot path")
    notice_bytes, notice_sha = _hash_relative_file(
        model_root,
        notice_path,
        "model notice",
        maximum=MAX_JSON_BYTES,
    )
    if notice_bytes != _positive_int(
        manifest["noticeBytes"], "model manifest.noticeBytes"
    ):
        raise SmokeValidationError("model notice byte count differs")
    if notice_sha != _sha256(manifest["noticeSha256"], "model manifest.noticeSha256"):
        raise SmokeValidationError("model notice SHA-256 differs")
    model_total_bytes += notice_bytes
    if model_total_bytes > MAX_MODEL_TREE_BYTES:
        raise SmokeValidationError(f"model tree exceeds {MAX_MODEL_TREE_BYTES} bytes")
    actual_files = _collect_relative_files(model_root, "model")
    expected_files = seen | {notice_path}
    if actual_files != expected_files:
        raise SmokeValidationError("model root file set differs from manifest")
    expected_tree = _sha256(manifest["treeSha256"], "model manifest.treeSha256")
    if _tree_digest(sorted(entries)) != expected_tree:
        raise SmokeValidationError("model tree SHA-256 differs")
    return hashlib.sha256(payload).hexdigest(), {
        "modelId": model_id,
        "revision": revision,
        "source": source,
        "licenseSpdx": "Apache-2.0",
        "fileCount": len(entries),
        "files": [
            {"path": path, "bytes": size, "sha256": digest}
            for path, size, digest in entries
        ],
        "noticePath": notice_path,
        "noticeBytes": notice_bytes,
        "noticeSha256": notice_sha,
        "treeSha256": expected_tree,
    }


def load_fixture_manifest(path: Path) -> str:
    value, payload = _load_json(path, "fixture manifest")
    manifest = _object(
        value, "fixture manifest", FIXTURE_MANIFEST_KEYS, FIXTURE_MANIFEST_KEYS
    )
    _schema_version(manifest["schemaVersion"], "fixture manifest.schemaVersion")
    fixtures = manifest["fixtures"]
    if not isinstance(fixtures, list) or not fixtures or len(fixtures) > 256:
        raise SmokeValidationError(
            "fixture manifest.fixtures must contain 1..256 entries"
        )
    seen: set[str] = set()
    root = path.parent
    for index, item in enumerate(fixtures):
        fixture = _object(
            item, f"fixture manifest.fixtures[{index}]", FIXTURE_KEYS, FIXTURE_KEYS
        )
        fixture_id = _string(fixture["id"], f"fixture manifest.fixtures[{index}].id")
        if fixture_id in seen:
            raise SmokeValidationError(f"duplicate fixture id: {fixture_id}")
        seen.add(fixture_id)
        relative_path = _safe_relative_path(
            fixture["path"], f"fixture manifest.fixtures[{index}].path"
        )
        expected_bytes = _positive_int(
            fixture["bytes"],
            f"fixture manifest.fixtures[{index}].bytes",
            maximum=MAX_FIXTURE_FILE_BYTES,
        )
        expected_sha = _sha256(
            fixture["sha256"], f"fixture manifest.fixtures[{index}].sha256"
        )
        actual_bytes, actual_sha = _hash_relative_file(
            root,
            relative_path,
            "fixture",
            maximum=MAX_FIXTURE_FILE_BYTES,
        )
        if actual_bytes != expected_bytes or actual_sha != expected_sha:
            raise SmokeValidationError(f"fixture hash or size differs: {relative_path}")
    return hashlib.sha256(payload).hexdigest()


def load_service_config(path: Path) -> tuple[str, ServiceConfig]:
    value, payload = _load_json(path, "service config")
    config = _object(value, "service config", CONFIG_KEYS, CONFIG_KEYS)
    _schema_version(config["schemaVersion"], "service config.schemaVersion")
    host = _string(config["host"], "service config.host")
    if host != "127.0.0.1":
        raise SmokeValidationError("service config.host must be 127.0.0.1")
    port = _positive_int(config["port"], "service config.port", maximum=65535)
    if config["network"] != "none":
        raise SmokeValidationError("service config.network must be none")
    model_mount = _string(config["modelMount"], "service config.modelMount")
    output_mount = _string(config["outputMount"], "service config.outputMount")
    if (model_mount, output_mount) != ("/models", "/out"):
        raise SmokeValidationError("service config mounts must be /models and /out")
    command = config["command"]
    if (
        not isinstance(command, list)
        or not command
        or not all(isinstance(token, str) for token in command)
    ):
        raise SmokeValidationError(
            "service config.command must be a non-empty argv array"
        )
    if any(FORBIDDEN_COMMAND_PATTERN.search(token) for token in command):
        raise SmokeValidationError("service config.command contains shell syntax")
    expected_command = [
        "paddlex",
        "--serve",
        "--pipeline",
        "OCR",
        "--host",
        host,
        "--port",
        str(port),
    ]
    if command != expected_command:
        raise SmokeValidationError(
            "service config.command must be the pinned loopback OCR serving argv"
        )
    return hashlib.sha256(payload).hexdigest(), ServiceConfig(
        host=host,
        port=port,
        network="none",
        command=tuple(command),
        request_max_bytes=_positive_int(
            config["requestMaxBytes"],
            "service config.requestMaxBytes",
            maximum=16 * 1024 * 1024,
        ),
        response_max_bytes=_positive_int(
            config["responseMaxBytes"],
            "service config.responseMaxBytes",
            maximum=16 * 1024 * 1024,
        ),
        readiness_timeout_seconds=_positive_int(
            config["readinessTimeoutSeconds"],
            "service config.readinessTimeoutSeconds",
            maximum=300,
        ),
        model_mount=model_mount,
        output_mount=output_mount,
    )


def _copy_snapshot_file(
    source_root: Path,
    relative_path: str,
    destination: Path,
    expected_bytes: int,
    expected_sha: str,
) -> None:
    fd = _open_relative_file(source_root, relative_path, "model snapshot source")
    digest = hashlib.sha256()
    actual_bytes = 0
    destination.parent.mkdir(parents=True, exist_ok=True)
    try:
        with destination.open("xb") as stream:
            while chunk := os.read(fd, READ_CHUNK_BYTES):
                actual_bytes += len(chunk)
                if actual_bytes > MAX_MODEL_FILE_BYTES:
                    raise SmokeValidationError(
                        f"model snapshot file exceeds {MAX_MODEL_FILE_BYTES} bytes"
                    )
                digest.update(chunk)
                stream.write(chunk)
    finally:
        os.close(fd)
    if actual_bytes != expected_bytes or digest.hexdigest() != expected_sha:
        raise SmokeValidationError(
            f"model snapshot source changed during staging: {relative_path}"
        )
    destination.chmod(0o444)


def _create_model_snapshot(
    model_root: Path, model_info: dict[str, Any]
) -> ModelSnapshot:
    temp_dir = tempfile.TemporaryDirectory(prefix="bluetape4k-paddle-model-")
    snapshot_root = Path(temp_dir.name) / "model"
    try:
        snapshot_root.mkdir(mode=0o700)
        for entry in model_info["files"]:
            _copy_snapshot_file(
                model_root,
                entry["path"],
                snapshot_root / entry["path"],
                entry["bytes"],
                entry["sha256"],
            )
        _copy_snapshot_file(
            model_root,
            model_info["noticePath"],
            snapshot_root / model_info["noticePath"],
            model_info["noticeBytes"],
            model_info["noticeSha256"],
        )
        marker = {
            "schemaVersion": 1,
            "treeSha256": model_info["treeSha256"],
            "files": model_info["files"],
            "notice": {
                "path": model_info["noticePath"],
                "bytes": model_info["noticeBytes"],
                "sha256": model_info["noticeSha256"],
            },
        }
        marker_path = snapshot_root / SNAPSHOT_MARKER
        marker_path.write_text(json.dumps(marker, sort_keys=True), encoding="utf-8")
        marker_path.chmod(0o444)
        for directory in sorted(
            (path for path in snapshot_root.rglob("*") if path.is_dir()),
            key=lambda path: len(path.parts),
            reverse=True,
        ):
            directory.chmod(0o555)
        snapshot_root.chmod(0o555)
        return ModelSnapshot(
            root=snapshot_root,
            tree_sha256=model_info["treeSha256"],
            temp_dir=temp_dir,
        )
    except BaseException:
        temp_dir.cleanup()
        raise


def _verify_model_snapshot(snapshot: ModelSnapshot) -> Path:
    if not isinstance(snapshot, ModelSnapshot):
        raise SmokeValidationError("model snapshot object is required")
    root = _regular_directory(snapshot.root, "model snapshot")
    marker_path = root / SNAPSHOT_MARKER
    marker_value, _ = _load_json(marker_path, "model snapshot marker")
    marker = _object(
        marker_value,
        "model snapshot marker",
        {"schemaVersion", "treeSha256", "files", "notice"},
        {"schemaVersion", "treeSha256", "files", "notice"},
    )
    _schema_version(marker["schemaVersion"], "model snapshot marker.schemaVersion")
    tree_sha = _sha256(marker["treeSha256"], "model snapshot marker.treeSha256")
    if tree_sha != snapshot.tree_sha256:
        raise SmokeValidationError("model snapshot tree digest differs")
    files = marker["files"]
    if not isinstance(files, list) or not files or len(files) > 256:
        raise SmokeValidationError("model snapshot marker.files must be non-empty")
    expected_entries: list[tuple[str, int, str]] = []
    total_bytes = 0
    seen: set[str] = set()
    for index, item in enumerate(files):
        entry = _object(
            item,
            f"model snapshot marker.files[{index}]",
            MODEL_FILE_KEYS,
            MODEL_FILE_KEYS,
        )
        relative_path = _safe_relative_path(
            entry["path"], f"model snapshot marker.files[{index}].path"
        )
        if relative_path in seen:
            raise SmokeValidationError("duplicate model snapshot file path")
        seen.add(relative_path)
        expected_bytes = _positive_int(
            entry["bytes"],
            f"model snapshot marker.files[{index}].bytes",
            maximum=MAX_MODEL_FILE_BYTES,
        )
        expected_sha = _sha256(
            entry["sha256"], f"model snapshot marker.files[{index}].sha256"
        )
        actual_bytes, actual_sha = _hash_relative_file(
            root, relative_path, "model snapshot file", maximum=MAX_MODEL_FILE_BYTES
        )
        if (actual_bytes, actual_sha) != (expected_bytes, expected_sha):
            raise SmokeValidationError("model snapshot file hash differs")
        total_bytes += actual_bytes
        if total_bytes > MAX_MODEL_TREE_BYTES:
            raise SmokeValidationError(
                f"model snapshot exceeds {MAX_MODEL_TREE_BYTES} bytes"
            )
        expected_entries.append((relative_path, actual_bytes, actual_sha))
    notice = _object(
        marker["notice"],
        "model snapshot marker.notice",
        MODEL_FILE_KEYS,
        MODEL_FILE_KEYS,
    )
    notice_path = _safe_relative_path(
        notice["path"], "model snapshot marker.notice.path"
    )
    if notice_path in seen:
        raise SmokeValidationError("model snapshot notice must be distinct")
    notice_bytes = _positive_int(
        notice["bytes"],
        "model snapshot marker.notice.bytes",
        maximum=MAX_JSON_BYTES,
    )
    notice_sha = _sha256(notice["sha256"], "model snapshot marker.notice.sha256")
    actual_notice_bytes, actual_notice_sha = _hash_relative_file(
        root, notice_path, "model snapshot notice", maximum=MAX_JSON_BYTES
    )
    if (actual_notice_bytes, actual_notice_sha) != (notice_bytes, notice_sha):
        raise SmokeValidationError("model snapshot notice hash differs")
    total_bytes += actual_notice_bytes
    if total_bytes > MAX_MODEL_TREE_BYTES:
        raise SmokeValidationError(
            f"model snapshot exceeds {MAX_MODEL_TREE_BYTES} bytes"
        )
    expected_files = seen | {notice_path, SNAPSHOT_MARKER}
    if _collect_relative_files(root, "model snapshot") != expected_files:
        raise SmokeValidationError("model snapshot file set differs from marker")
    if _tree_digest(sorted(expected_entries)) != tree_sha:
        raise SmokeValidationError("model snapshot tree digest differs")
    return root


def build_docker_command(
    image: str,
    model_snapshot: ModelSnapshot,
    output_root: Path,
    config: ServiceConfig,
) -> tuple[str, ...]:
    image = validate_image_reference(image)
    model_root = _verify_model_snapshot(model_snapshot).resolve()
    output_root = _empty_directory(output_root, "output root").resolve()
    return (
        "docker",
        "run",
        "--rm",
        "--network",
        "none",
        "--read-only",
        "--cap-drop",
        "ALL",
        "--security-opt",
        "no-new-privileges:true",
        "--user",
        "65532:65532",
        "--pids-limit",
        "128",
        "--memory",
        "1g",
        "--cpus",
        "2",
        "--tmpfs",
        "/tmp:rw,noexec,nosuid,size=64m",
        "--volume",
        f"{model_root}:{config.model_mount}:ro",
        "--volume",
        f"{output_root}:{config.output_mount}:rw",
        image,
        *config.command,
    )


def _redact_command(
    command: Sequence[str], model_root: Path, output_root: Path
) -> list[str]:
    model_prefix = str(model_root.resolve()) + ":"
    output_prefix = str(output_root.resolve()) + ":"
    redacted: list[str] = []
    for token in command:
        if token.startswith(model_prefix):
            redacted.append("<MODEL_ROOT>:" + token[len(model_prefix) :])
        elif token.startswith(output_prefix):
            redacted.append("<OUTPUT_ROOT>:" + token[len(output_prefix) :])
        else:
            redacted.append(token)
    return redacted


def validate_inputs(
    *,
    image: str,
    model_manifest: Path,
    model_root: Path,
    fixture_manifest: Path,
    config: Path,
    output_root: Path,
) -> ValidatedInputs:
    image = validate_image_reference(image)
    model_manifest_sha, model_info = load_model_manifest(model_manifest, model_root)
    model_snapshot = _create_model_snapshot(model_root, model_info)
    try:
        fixture_manifest_sha = load_fixture_manifest(fixture_manifest)
        config_sha, service_config = load_service_config(config)
        docker_command = build_docker_command(
            image, model_snapshot, output_root, service_config
        )
        return ValidatedInputs(
            image=image,
            model_manifest_sha256=model_manifest_sha,
            model_tree_sha256=model_info["treeSha256"],
            fixture_manifest_sha256=fixture_manifest_sha,
            config_sha256=config_sha,
            config=service_config,
            model_snapshot=model_snapshot,
            docker_command=docker_command,
        )
    except BaseException:
        model_snapshot.temp_dir.cleanup()
        raise


def build_plan(inputs: ValidatedInputs, output_root: Path) -> dict[str, Any]:
    _verify_model_snapshot(inputs.model_snapshot)
    runtime_limits = {
        "requestMaxBytes": inputs.config.request_max_bytes,
        "responseMaxBytes": inputs.config.response_max_bytes,
        "readinessTimeoutSeconds": inputs.config.readiness_timeout_seconds,
    }
    return {
        "schemaVersion": 1,
        "kind": "paddle-ocr-preflight",
        "status": "PLAN_ONLY",
        "executionStatus": "PENDING",
        "image": inputs.image,
        "fixtureManifestSha256": inputs.fixture_manifest_sha256,
        "modelManifestSha256": inputs.model_manifest_sha256,
        "modelTreeSha256": inputs.model_tree_sha256,
        "configSha256": inputs.config_sha256,
        "runtimeLimits": runtime_limits,
        "redactedDockerCommand": _redact_command(
            inputs.docker_command, inputs.model_snapshot.root, output_root
        ),
        "securityPlan": SECURITY_PLAN,
    }


def run_preflight(
    inputs: ValidatedInputs,
    *,
    runner: Callable[..., subprocess.CompletedProcess[str]] = subprocess.run,
) -> dict[str, Any]:
    _verify_model_snapshot(inputs.model_snapshot)
    inspect_command = (
        "docker",
        "image",
        "inspect",
        inputs.image,
        "--format",
        "{{index .RepoDigests 0}}",
    )
    try:
        result = runner(
            inspect_command,
            check=False,
            capture_output=True,
            text=True,
            timeout=inputs.config.readiness_timeout_seconds,
        )
    except (OSError, subprocess.TimeoutExpired) as error:
        raise SmokeValidationError(
            "offline image inspection could not complete"
        ) from error
    if result.returncode != 0:
        raise SmokeValidationError(
            f"digest-pinned image is not available offline (docker exit {result.returncode})"
        )
    inspected = (result.stdout or "").strip()
    if inspected != inputs.image:
        raise SmokeValidationError(
            "docker image inspect did not return the requested digest"
        )
    return {
        "schemaVersion": 1,
        "kind": "paddle-ocr-preflight",
        "status": "PREFLIGHT_PASS",
        "executionStatus": "PENDING",
        "image": inputs.image,
        "fixtureManifestSha256": inputs.fixture_manifest_sha256,
        "modelManifestSha256": inputs.model_manifest_sha256,
        "modelTreeSha256": inputs.model_tree_sha256,
        "configSha256": inputs.config_sha256,
        "runtimeLimits": {
            "requestMaxBytes": inputs.config.request_max_bytes,
            "responseMaxBytes": inputs.config.response_max_bytes,
            "readinessTimeoutSeconds": inputs.config.readiness_timeout_seconds,
        },
        "securityPlan": SECURITY_PLAN,
        "nextGate": "offline service execution and receipt generation",
    }


def _write_report(path: Path, report: dict[str, Any]) -> None:
    if not path.parent.is_dir() or path.parent.is_symlink():
        raise SmokeValidationError(
            "report parent must be an existing non-symlink directory"
        )
    try:
        fd = os.open(
            os.fspath(path),
            os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW,
            0o600,
        )
    except FileExistsError as error:
        raise SmokeValidationError("report path already exists") from error
    except OSError as error:
        raise SmokeValidationError("report path cannot be created safely") from error
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as stream:
            stream.write(json.dumps(report, indent=2, sort_keys=True) + "\n")
    except BaseException:
        try:
            path.unlink()
        except OSError:
            pass
        raise


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--image", required=True)
    parser.add_argument("--model-manifest", type=Path, required=True)
    parser.add_argument("--model-root", type=Path, required=True)
    parser.add_argument("--fixture-manifest", type=Path, required=True)
    parser.add_argument("--config", type=Path, required=True)
    parser.add_argument("--output-root", type=Path, required=True)
    parser.add_argument("--report", type=Path)
    parser.add_argument(
        "--execute", action="store_true", help="inspect the digest-pinned image locally"
    )
    args = parser.parse_args(argv)
    inputs: ValidatedInputs | None = None
    try:
        inputs = validate_inputs(
            image=args.image,
            model_manifest=args.model_manifest,
            model_root=args.model_root,
            fixture_manifest=args.fixture_manifest,
            config=args.config,
            output_root=args.output_root,
        )
        if args.execute:
            report = run_preflight(inputs)
        else:
            report = build_plan(inputs, args.output_root)
        if args.report:
            _write_report(args.report, report)
        print(json.dumps(report, indent=2, sort_keys=True))
        return 0
    except (OSError, SmokeValidationError) as error:
        print(f"FAIL: {error}", file=sys.stderr)
        return 1
    finally:
        if inputs is not None:
            inputs.model_snapshot.temp_dir.cleanup()


if __name__ == "__main__":
    raise SystemExit(main())
