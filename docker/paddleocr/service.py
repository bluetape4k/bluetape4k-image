from __future__ import annotations

"""Fail-closed loopback wrapper for the baked PaddleOCR CPU service image."""

import argparse
import hashlib
import http.client
import json
import os
import re
import signal
import stat
import subprocess
import sys
import time
from collections.abc import Mapping
from dataclasses import dataclass, replace
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path, PurePosixPath
from typing import Any, BinaryIO

RUNTIME_ROOT = Path("/opt/bluetape4k/paddleocr")
UPSTREAM_HOST = "127.0.0.1"
UPSTREAM_PORT = 18080
REQUEST_MAX_BYTES = 16 * 1024 * 1024
RESPONSE_MAX_BYTES = 16 * 1024 * 1024
READINESS_TIMEOUT_SECONDS = 300
MAX_MANIFEST_BYTES = 1024 * 1024
MAX_MODEL_FILE_BYTES = 256 * 1024 * 1024
MAX_MODEL_TREE_BYTES = 512 * 1024 * 1024
READ_CHUNK_BYTES = 1024 * 1024
_SHA256_RE = re.compile(r"\A[0-9a-f]{64}\Z")
_SAFE_PATH_RE = re.compile(r"\A(?!/)(?!.*\\)(?!.*(?:^|/)\.\.(?:/|$))[A-Za-z0-9._/-]+\Z")
_MANIFEST_KEYS = {
    "schemaVersion", "inputLockSha256", "legalInventorySha256",
    "requirementsSha256", "modelTreeDigests", "modelPairSha256",
}
_OVERRIDE_KEYS = {
    "PADDLEOCR_MODEL_ROOT", "PADDLEOCR_MODEL_DIR", "PADDLEX_MODEL_ROOT",
    "PADDLEOCR_HOME", "PADDLEX_HOME", "PADDLE_HOME",
    "PADDLE_PDX_CACHE_HOME", "PADDLE_PDX_MODEL_SOURCE",
}


class ServiceConfigurationError(ValueError):
    """Raised when the immutable image contract cannot be established."""


@dataclass(frozen=True)
class ReadinessState:
    detector_verified: bool = False
    recognizer_verified: bool = False
    legal_verified: bool = False
    pipeline_verified: bool = False
    upstream_ready: bool = False


@dataclass(frozen=True)
class ServiceConfiguration:
    runtime_root: Path
    pipeline: Path
    readiness: ReadinessState
    request_max_bytes: int = REQUEST_MAX_BYTES
    response_max_bytes: int = RESPONSE_MAX_BYTES
    readiness_timeout_seconds: int = READINESS_TIMEOUT_SECONDS


def _reject_duplicate_keys(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise ServiceConfigurationError("duplicate JSON key")
        result[key] = value
    return result


def _read_regular(path: Path, maximum: int, label: str) -> bytes:
    flags = os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0) | getattr(os, "O_CLOEXEC", 0)
    try:
        descriptor = os.open(path, flags)
    except OSError as exc:
        raise ServiceConfigurationError(f"{label} is missing or not a regular file") from exc
    digest = bytearray()
    try:
        if not stat.S_ISREG(os.fstat(descriptor).st_mode):
            raise ServiceConfigurationError(f"{label} is not a regular file")
        while True:
            chunk = os.read(descriptor, min(READ_CHUNK_BYTES, maximum + 1 - len(digest)))
            if not chunk:
                break
            digest.extend(chunk)
            if len(digest) > maximum:
                raise ServiceConfigurationError(f"{label} exceeds its byte limit")
    finally:
        os.close(descriptor)
    if not digest:
        raise ServiceConfigurationError(f"{label} must not be empty")
    return bytes(digest)


def _load_json(path: Path, maximum: int, label: str) -> tuple[dict[str, Any], bytes]:
    raw = _read_regular(path, maximum, label)
    try:
        value = json.loads(raw.decode("utf-8"), object_pairs_hook=_reject_duplicate_keys)
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise ServiceConfigurationError(f"{label} is invalid JSON") from exc
    if not isinstance(value, dict):
        raise ServiceConfigurationError(f"{label} root must be an object")
    return value, raw


def _verify_regular_receipt(
    path: Path,
    maximum: int,
    expected_bytes: int,
    expected_sha256: str,
    label: str,
) -> None:
    flags = os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0) | getattr(os, "O_CLOEXEC", 0)
    try:
        descriptor = os.open(path, flags)
    except OSError as exc:
        raise ServiceConfigurationError(f"{label} is missing or not a regular file") from exc
    digest = hashlib.sha256()
    actual_bytes = 0
    try:
        metadata = os.fstat(descriptor)
        if not stat.S_ISREG(metadata.st_mode) or metadata.st_nlink != 1:
            raise ServiceConfigurationError(f"{label} is not a unique regular file")
        while True:
            chunk = os.read(descriptor, READ_CHUNK_BYTES)
            if not chunk:
                break
            actual_bytes += len(chunk)
            if actual_bytes > maximum or actual_bytes > expected_bytes:
                raise ServiceConfigurationError(f"{label} exceeds its byte receipt")
            digest.update(chunk)
    finally:
        os.close(descriptor)
    if actual_bytes != expected_bytes or digest.hexdigest() != expected_sha256:
        raise ServiceConfigurationError(f"{label} receipt differs")


def _exact(value: Any, keys: set[str], label: str) -> dict[str, Any]:
    if not isinstance(value, dict) or set(value) != keys:
        raise ServiceConfigurationError(f"{label} fields differ from the contract")
    return value


def _sha256(value: Any, label: str) -> str:
    if not isinstance(value, str) or _SHA256_RE.fullmatch(value) is None:
        raise ServiceConfigurationError(f"{label} must be lowercase SHA-256")
    return value


def _safe_path(value: str, label: str) -> str:
    if (
        not value or _SAFE_PATH_RE.fullmatch(value) is None
        or value in {".", ".."} or value.endswith("/") or "//" in value
    ):
        raise ServiceConfigurationError(f"{label} is not a safe relative path")
    return value


def _model_files(root: Path, role: str) -> set[str]:
    if root.is_symlink() or not root.is_dir():
        raise ServiceConfigurationError(f"{role} model root is missing")
    result: set[str] = set()
    for current, directories, files in os.walk(root, followlinks=False):
        current_path = Path(current)
        for name in directories:
            if (current_path / name).is_symlink():
                raise ServiceConfigurationError(f"{role} model contains a symlink")
        for name in files:
            path = current_path / name
            if path.is_symlink() or not path.is_file():
                raise ServiceConfigurationError(f"{role} model contains a non-regular file")
            result.add(path.relative_to(root).as_posix())
    return result


def _verify_model_role(runtime_root: Path, role: str, expected_tree: str) -> None:
    manifest_raw = _read_regular(
        runtime_root / "models" / f"{role}.manifest.txt",
        MAX_MANIFEST_BYTES,
        f"{role} model manifest",
    )
    if hashlib.sha256(manifest_raw).hexdigest() != expected_tree:
        raise ServiceConfigurationError(f"{role} model tree manifest SHA-256 differs")
    if b"\r" in manifest_raw or not manifest_raw.endswith(b"\n") or b"\n\n" in manifest_raw:
        raise ServiceConfigurationError(f"{role} model tree manifest encoding differs")
    try:
        lines = manifest_raw.decode("utf-8").splitlines()
    except UnicodeDecodeError as exc:
        raise ServiceConfigurationError(f"{role} model tree manifest is not UTF-8") from exc
    if not lines or len(lines) > 10_000:
        raise ServiceConfigurationError(f"{role} model tree manifest count differs")
    root = runtime_root / "models" / role
    actual_paths = _model_files(root, role)
    expected_paths: set[str] = set()
    total = 0
    previous = ""
    for line in lines:
        parts = line.split("\t")
        if len(parts) != 3:
            raise ServiceConfigurationError(f"{role} model tree manifest row differs")
        relative = _safe_path(parts[0], f"{role} model path")
        if relative <= previous or relative in expected_paths:
            raise ServiceConfigurationError(f"{role} model paths are not strictly ordered")
        previous = relative
        try:
            expected_bytes = int(parts[1])
        except ValueError as exc:
            raise ServiceConfigurationError(f"{role} model byte count is invalid") from exc
        if expected_bytes <= 0 or expected_bytes > MAX_MODEL_FILE_BYTES:
            raise ServiceConfigurationError(f"{role} model byte count exceeds limit")
        expected_sha = _sha256(parts[2], f"{role} model file SHA-256")
        path = root.joinpath(*PurePosixPath(relative).parts)
        _verify_regular_receipt(
            path,
            MAX_MODEL_FILE_BYTES,
            expected_bytes,
            expected_sha,
            f"{role} model file",
        )
        total += expected_bytes
        if total > MAX_MODEL_TREE_BYTES:
            raise ServiceConfigurationError(f"{role} model tree exceeds limit")
        expected_paths.add(relative)
    if actual_paths != expected_paths:
        raise ServiceConfigurationError(f"{role} model file set differs")


def load_service_configuration(
    *,
    runtime_root: Path = RUNTIME_ROOT,
    environment: Mapping[str, str] | None = None,
) -> ServiceConfiguration:
    active_environment = os.environ if environment is None else environment
    if any(key in active_environment for key in _OVERRIDE_KEYS):
        raise ServiceConfigurationError("external model override is forbidden")
    if not runtime_root.is_absolute() or runtime_root.is_symlink() or not runtime_root.is_dir():
        raise ServiceConfigurationError("runtime root must be an absolute non-symlink directory")
    manifest, manifest_raw = _load_json(
        runtime_root / "model-manifest.json", MAX_MANIFEST_BYTES, "model manifest"
    )
    _exact(manifest, _MANIFEST_KEYS, "model manifest")
    if manifest["schemaVersion"] != 1:
        raise ServiceConfigurationError("model manifest schemaVersion must be 1")
    canonical = json.dumps(manifest, separators=(",", ":"), sort_keys=True).encode()
    if canonical != manifest_raw:
        raise ServiceConfigurationError("model manifest must be canonical JSON")
    for field in (
        "inputLockSha256", "legalInventorySha256", "requirementsSha256", "modelPairSha256"
    ):
        _sha256(manifest[field], field)
    trees = _exact(manifest["modelTreeDigests"], {"detector", "recognizer"}, "modelTreeDigests")
    for role in ("detector", "recognizer"):
        _verify_model_role(runtime_root, role, _sha256(trees[role], f"{role} tree SHA-256"))
    pair, pair_raw = _load_json(
        runtime_root / "models/model-pair.json", MAX_MANIFEST_BYTES, "model pair"
    )
    _exact(pair, {"detector", "recognizer"}, "model pair")
    if pair != trees or json.dumps(pair, separators=(",", ":"), sort_keys=True).encode() != pair_raw:
        raise ServiceConfigurationError("model pair differs from model tree bindings")
    if hashlib.sha256(pair_raw).hexdigest() != manifest["modelPairSha256"]:
        raise ServiceConfigurationError("model pair SHA-256 differs")
    legal_raw = _read_regular(
        runtime_root / "legal-inventory.json", MAX_MANIFEST_BYTES, "legal inventory"
    )
    if hashlib.sha256(legal_raw).hexdigest() != manifest["legalInventorySha256"]:
        raise ServiceConfigurationError("legal inventory SHA-256 differs")
    pipeline = runtime_root / "ocr-pipeline.yaml"
    pipeline_raw = _read_regular(pipeline, MAX_MANIFEST_BYTES, "OCR pipeline")
    try:
        text = pipeline_raw.decode("utf-8", errors="strict")
    except UnicodeDecodeError as exc:
        raise ServiceConfigurationError("OCR pipeline is not UTF-8") from exc
    required_roots = (
        "/opt/bluetape4k/paddleocr/models/detector",
        "/opt/bluetape4k/paddleocr/models/recognizer",
    )
    if any(root not in text for root in required_roots):
        raise ServiceConfigurationError("OCR pipeline does not bind both fixed model roots")
    lowered = text.lower()
    if "http://" in lowered or "https://" in lowered or "download" in lowered:
        raise ServiceConfigurationError("OCR pipeline contains a remote model source")
    return ServiceConfiguration(
        runtime_root=runtime_root,
        pipeline=pipeline,
        readiness=ReadinessState(
            detector_verified=True,
            recognizer_verified=True,
            legal_verified=True,
            pipeline_verified=True,
        ),
    )


def build_upstream_command(configuration: ServiceConfiguration) -> tuple[str, ...]:
    return (
        "paddlex", "--serve", "--pipeline", str(configuration.pipeline),
        "--host", UPSTREAM_HOST, "--port", str(UPSTREAM_PORT),
    )


def render_readiness(state: ReadinessState) -> tuple[int, bytes]:
    ready = all((
        state.detector_verified,
        state.recognizer_verified,
        state.legal_verified,
        state.pipeline_verified,
        state.upstream_ready,
    ))
    return (200 if ready else 503, b'{"ready":true}\n' if ready else b'{"ready":false}\n')


def read_bounded_request(stream: BinaryIO, content_length: int, maximum: int) -> bytes:
    if type(content_length) is not int or content_length < 0:
        raise ServiceConfigurationError("request Content-Length is invalid")
    if content_length > maximum:
        raise ServiceConfigurationError("request body exceeds byte limit")
    payload = stream.read(content_length)
    if len(payload) != content_length:
        raise ServiceConfigurationError("request body is truncated")
    return payload


def _read_bounded_response(response: http.client.HTTPResponse, maximum: int) -> bytes:
    chunks: list[bytes] = []
    total = 0
    while True:
        chunk = response.read(min(READ_CHUNK_BYTES, maximum + 1 - total))
        if not chunk:
            break
        total += len(chunk)
        if total > maximum:
            raise ServiceConfigurationError("upstream response exceeds byte limit")
        chunks.append(chunk)
    return b"".join(chunks)


def _probe_upstream(timeout_seconds: float = 2.0) -> bool:
    connection = http.client.HTTPConnection(UPSTREAM_HOST, UPSTREAM_PORT, timeout=timeout_seconds)
    try:
        connection.request("GET", "/health")
        response = connection.getresponse()
        response.read(64 * 1024)
        return response.status == 200
    except OSError:
        return False
    finally:
        connection.close()


class _ServiceHandler(BaseHTTPRequestHandler):
    server: _ServiceServer

    def log_message(self, format: str, *args: object) -> None:
        return

    def _send(self, status_code: int, payload: bytes, content_type: str) -> None:
        self.send_response(status_code)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(payload)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(payload)

    def _handle(self) -> None:
        configuration = self.server.configuration
        if self.command == "GET" and self.path == "/health/ready":
            state = replace(configuration.readiness, upstream_ready=_probe_upstream())
            status_code, payload = render_readiness(state)
            self._send(status_code, payload, "application/json")
            return
        if len(self.path) > 2048 or any(ord(character) < 0x20 for character in self.path):
            self._send(400, b'{"error":"invalid request target"}\n', "application/json")
            return
        if self.headers.get("Transfer-Encoding") is not None:
            self._send(400, b'{"error":"transfer encoding is forbidden"}\n', "application/json")
            return
        raw_length = self.headers.get("Content-Length", "0")
        try:
            content_length = int(raw_length)
            body = read_bounded_request(
                self.rfile, content_length, configuration.request_max_bytes
            )
        except (ValueError, ServiceConfigurationError):
            self._send(413, b'{"error":"request body rejected"}\n', "application/json")
            return
        connection = http.client.HTTPConnection(UPSTREAM_HOST, UPSTREAM_PORT, timeout=30)
        headers = {
            name: value for name, value in (
                ("Content-Type", self.headers.get("Content-Type")),
                ("Accept", self.headers.get("Accept")),
            ) if value is not None
        }
        try:
            connection.request(self.command, self.path, body=body, headers=headers)
            response = connection.getresponse()
            payload = _read_bounded_response(response, configuration.response_max_bytes)
            content_type = response.getheader("Content-Type", "application/octet-stream")
            self._send(response.status, payload, content_type)
        except (OSError, ServiceConfigurationError):
            self._send(502, b'{"error":"upstream unavailable"}\n', "application/json")
        finally:
            connection.close()

    def do_GET(self) -> None:
        self._handle()

    def do_POST(self) -> None:
        self._handle()


class _ServiceServer(ThreadingHTTPServer):
    daemon_threads = True

    def __init__(self, address: tuple[str, int], configuration: ServiceConfiguration):
        super().__init__(address, _ServiceHandler)
        self.configuration = configuration


def _parse_args(argv: list[str] | None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--host", required=True)
    parser.add_argument("--port", required=True, type=int)
    args = parser.parse_args(argv)
    if args.host != "127.0.0.1" or not 1 <= args.port <= 65535:
        parser.error("service must bind a valid loopback port")
    return args


def main(argv: list[str] | None = None) -> int:
    args = _parse_args(argv)
    process: subprocess.Popen[bytes] | None = None
    server: _ServiceServer | None = None
    try:
        configuration = load_service_configuration()
        process = subprocess.Popen(
            build_upstream_command(configuration),
            stdin=subprocess.DEVNULL,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            env={
                key: value for key, value in os.environ.items()
                if key not in _OVERRIDE_KEYS and not key.lower().endswith("_proxy")
            },
        )
        deadline = time.monotonic() + configuration.readiness_timeout_seconds
        while not _probe_upstream():
            if process.poll() is not None:
                raise ServiceConfigurationError("PaddleX service exited before readiness")
            if time.monotonic() >= deadline:
                raise ServiceConfigurationError("PaddleX readiness deadline exceeded")
            time.sleep(0.25)
        server = _ServiceServer((args.host, args.port), configuration)
        def stop_service(_signum: int, _frame: object) -> None:
            raise KeyboardInterrupt

        previous = signal.signal(signal.SIGTERM, stop_service)
        try:
            server.serve_forever(poll_interval=0.25)
        finally:
            signal.signal(signal.SIGTERM, previous)
        return 0
    except KeyboardInterrupt:
        return 0
    except (OSError, ServiceConfigurationError) as exc:
        print(f"service startup failed: {exc}", file=sys.stderr)
        return 1
    finally:
        if server is not None:
            server.server_close()
        if process is not None and process.poll() is None:
            process.terminate()
            try:
                process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()


if __name__ == "__main__":
    raise SystemExit(main())
