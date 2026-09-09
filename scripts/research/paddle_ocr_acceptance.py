"""Fail-closed no-egress acceptance for the baked PaddleOCR IMAGE mode."""

from __future__ import annotations

import argparse
import base64
import hashlib
import json
import os
import re
import subprocess
import sys
import time
from collections.abc import Callable, Sequence
from pathlib import Path, PurePosixPath
from subprocess import CompletedProcess
from typing import Any

from paddle_ocr_smoke import (
    MAX_FIXTURE_FILE_BYTES,
    SmokeValidationError,
    ValidatedInputs,
    _read_bounded,
    load_fixture_manifest,
    validate_inputs,
)

MAX_COMMAND_OUTPUT_BYTES = 1024 * 1024
MAX_REPORT_BYTES = 1024 * 1024
REQUIRED_PLATFORM = "linux/amd64"
HOST_PLATFORM_ALIASES = frozenset((REQUIRED_PLATFORM, "linux/x86_64"))
CONTAINER_NAME_PATTERN = re.compile(r"\Abluetape4k-paddleocr-[0-9]+\Z")
PROBE_MARKER = "BLUETAPE4K_PROBE:"
FORBIDDEN_LOG_PATTERN = re.compile(
    r"(?i)(?:password|passwd|authorization|cookie|secret|api[_-]?key|token)\s*[:=]"
)
ABSOLUTE_PATH_PATTERN = re.compile(
    r"(?:(?:/Users|/home|/private/tmp|/tmp|/var|/opt/bluetape4k)/[^\s\"']+)"
)

_HEALTH_PROBE = """\
import hashlib, json, urllib.error, urllib.request
request = urllib.request.Request('http://127.0.0.1:8080/health/ready', method='GET')
opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
try:
    response = opener.open(request, timeout=3)
    status = response.status
    body = response.read(65537)
except urllib.error.HTTPError as error:
    status = error.code
    body = error.read(65537)
except Exception:
    status = 599
    body = b''
print('BLUETAPE4K_PROBE:' + json.dumps({'status': status, 'bytes': len(body), 'sha256': hashlib.sha256(body).hexdigest()}, sort_keys=True))
"""

_POST_PROBE = """\
import hashlib, json, sys, urllib.error, urllib.request
body = sys.stdin.buffer.read(16777217)
request = urllib.request.Request('http://127.0.0.1{path}', data=body, method='POST')
request.add_header('Content-Type', 'application/json')
opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
try:
    response = opener.open(request, timeout=30)
    status = response.status
    payload = response.read(16777217)
except urllib.error.HTTPError as error:
    status = error.code
    payload = error.read(16777217)
except Exception:
    status = 599
    payload = b''
print('BLUETAPE4K_PROBE:' + json.dumps({'status': status, 'bytes': len(payload), 'sha256': hashlib.sha256(payload).hexdigest()}, sort_keys=True))
"""


class AcceptanceValidationError(ValueError):
    """Raised when an acceptance command or receipt violates its contract."""


CommandRunner = Callable[..., CompletedProcess[bytes]]


def _default_runner(
    command: Sequence[str], *, input: bytes | None = None, timeout: float | None = None
) -> CompletedProcess[bytes]:
    return subprocess.run(
        tuple(command), input=input, capture_output=True, check=False, timeout=timeout
    )


def _bounded_output(result: CompletedProcess[bytes]) -> bytes:
    stdout = result.stdout or b""
    stderr = result.stderr or b""
    if len(stdout) > MAX_COMMAND_OUTPUT_BYTES or len(stderr) > MAX_COMMAND_OUTPUT_BYTES:
        raise AcceptanceValidationError("command output exceeds the byte limit")
    return stdout


def _bounded_combined_output(result: CompletedProcess[bytes]) -> bytes:
    _bounded_output(result)
    stdout = result.stdout or b""
    stderr = result.stderr or b""
    separator = b"\n" if stdout and stderr else b""
    combined = stdout + separator + stderr
    if len(combined) > MAX_COMMAND_OUTPUT_BYTES:
        raise AcceptanceValidationError("command output exceeds the byte limit")
    return combined


def _require_success(
    result: CompletedProcess[bytes], command: Sequence[str], *, allow_failure: bool = False
) -> bytes:
    stdout = _bounded_output(result)
    if result.returncode != 0 and not allow_failure:
        detail = (result.stderr or b"")[:4096].decode("utf-8", errors="replace")
        raise AcceptanceValidationError(
            f"command failed ({' '.join(command[:3])}): {detail.strip() or 'unknown error'}"
        )
    return stdout


def validate_container_name(value: str) -> str:
    if CONTAINER_NAME_PATTERN.fullmatch(value) is None:
        raise AcceptanceValidationError("container name is not safe")
    return value


def build_container_command(
    preflight_command: Sequence[str], container_name: str
) -> tuple[str, ...]:
    validate_container_name(container_name)
    command = tuple(preflight_command)
    if len(command) < 3 or command[:2] != ("docker", "run") or "--rm" not in command:
        raise AcceptanceValidationError("preflight command must be docker run with --rm")
    if any(
        token in {"-p", "--publish", "-P", "--publish-all"}
        or token.startswith("--publish=")
        for token in command
    ):
        raise AcceptanceValidationError("published ports are forbidden")
    if "--network" not in command:
        raise AcceptanceValidationError("container network mode is missing")
    network_index = command.index("--network")
    if network_index + 1 >= len(command) or command[network_index + 1] != "none":
        raise AcceptanceValidationError("container network mode must be none")
    if "--name" in command or "--detach" in command:
        raise AcceptanceValidationError("preflight command contains runtime-only flags")
    without_rm = tuple(token for token in command if token != "--rm")
    return without_rm[:2] + ("--detach", "--name", container_name) + without_rm[2:]


def build_exec_command(
    container_name: str, script: str, *, interactive: bool = False
) -> tuple[str, ...]:
    validate_container_name(container_name)
    if "\x00" in script:
        raise AcceptanceValidationError("probe script contains NUL")
    prefix = ("docker", "exec")
    if interactive:
        prefix += ("--interactive",)
    return prefix + (container_name, "python3", "-c", script)


def _parse_json_object(raw: bytes | str, label: str) -> dict[str, Any]:
    try:
        value = json.loads(raw.decode("utf-8") if isinstance(raw, bytes) else raw)
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise AcceptanceValidationError(f"{label} is invalid JSON") from error
    if not isinstance(value, dict):
        raise AcceptanceValidationError(f"{label} must be a JSON object")
    return value


def _decode_utf8(raw: bytes | str, label: str) -> str:
    if isinstance(raw, str):
        return raw
    try:
        return raw.decode("utf-8")
    except UnicodeDecodeError as error:
        raise AcceptanceValidationError(f"{label} is not UTF-8") from error


def parse_probe_output(raw: bytes | str) -> dict[str, Any]:
    text = _decode_utf8(raw, "probe output")
    lines = [line for line in text.splitlines() if line.strip()]
    if len(lines) != 1 or not lines[0].startswith(PROBE_MARKER):
        raise AcceptanceValidationError("probe output must contain one marker line")
    value = _parse_json_object(lines[0][len(PROBE_MARKER) :], "probe receipt")
    if set(value) != {"status", "bytes", "sha256"}:
        raise AcceptanceValidationError("probe receipt fields differ")
    status = value["status"]
    size = value["bytes"]
    digest = value["sha256"]
    if type(status) is not int or not 100 <= status <= 599:
        raise AcceptanceValidationError("probe status is invalid")
    if type(size) is not int or size < 0:
        raise AcceptanceValidationError("probe byte count is invalid")
    if not isinstance(digest, str) or re.fullmatch(r"[0-9a-f]{64}", digest) is None:
        raise AcceptanceValidationError("probe SHA-256 is invalid")
    return {"status": status, "bytes": size, "sha256": digest}


def parse_image_inspect(raw: bytes | str, image: str) -> tuple[str, str]:
    text = _decode_utf8(raw, "image inspect output")
    lines = text.splitlines()
    if len(lines) != 2 or lines[0] != REQUIRED_PLATFORM or lines[1] != image:
        raise AcceptanceValidationError("image platform or digest differs")
    return lines[0], lines[1]


def parse_host_platform(raw: bytes | str) -> str:
    text = _decode_utf8(raw, "docker host platform")
    if text.rstrip("\n") not in HOST_PLATFORM_ALIASES:
        raise AcceptanceValidationError("docker host platform must be linux/amd64")
    return REQUIRED_PLATFORM


def validate_container_inspect(value: dict[str, Any]) -> dict[str, bool]:
    if not isinstance(value, dict):
        raise AcceptanceValidationError("container inspect must be an object")
    config = value.get("Config")
    host = value.get("HostConfig")
    network = value.get("NetworkSettings")
    state = value.get("State")
    if not all(isinstance(item, dict) for item in (config, host, network, state)):
        raise AcceptanceValidationError("container inspect sections are incomplete")
    checks = {
        "nonRoot": config.get("User") == "65532:65532",
        "readOnlyRoot": host.get("ReadonlyRootfs") is True,
        "capabilitiesDropped": isinstance(host.get("CapDrop"), list)
        and "ALL" in host["CapDrop"],
        "noNewPrivileges": isinstance(host.get("SecurityOpt"), list)
        and "no-new-privileges:true" in host["SecurityOpt"],
        "networkEgressDenied": host.get("NetworkMode") == "none",
        "portsUnpublished": network.get("Ports") in (None, {})
        and host.get("PublishAllPorts") is not True
        and not host.get("PortBindings"),
        "pidsLimit": host.get("PidsLimit") == 128,
        "memoryLimit": host.get("Memory") == 1024 * 1024 * 1024,
        "cpuLimit": host.get("NanoCpus") == 2_000_000_000,
        "stateRunning": state.get("Running") is True,
    }
    failed = [name for name, passed in checks.items() if not passed]
    if failed:
        raise AcceptanceValidationError("container security contract failed: " + ", ".join(failed))
    return checks


def sanitize_logs(raw: bytes | str) -> str:
    text = raw.decode("utf-8", errors="replace") if isinstance(raw, bytes) else raw
    if len(text.encode("utf-8")) > MAX_COMMAND_OUTPUT_BYTES:
        raise AcceptanceValidationError("container logs exceed the byte limit")
    if FORBIDDEN_LOG_PATTERN.search(text):
        raise AcceptanceValidationError("container logs contain credential material")
    return ABSOLUTE_PATH_PATTERN.sub("<PATH>", text)


def _safe_failure(error: BaseException) -> str:
    text = str(error).replace("\x00", "")
    text = ABSOLUTE_PATH_PATTERN.sub("<PATH>", text)
    text = text[:4096]
    return text or type(error).__name__


def _capture_failure_diagnostics(
    runner: CommandRunner, container_name: str
) -> dict[str, Any]:
    diagnostics: dict[str, Any] = {}
    try:
        inspected = _docker_inspect(runner, container_name)
        state = inspected.get("State")
        if not isinstance(state, dict):
            raise AcceptanceValidationError("container inspect state is incomplete")
        diagnostics["state"] = {
            "running": state.get("Running") is True,
            "status": state.get("Status") if isinstance(state.get("Status"), str) else None,
            "exitCode": state.get("ExitCode") if type(state.get("ExitCode")) is int else None,
            "error": sanitize_logs(state.get("Error", ""))
            if isinstance(state.get("Error", ""), str)
            else None,
            "oomKilled": state.get("OOMKilled") is True,
        }
    except (AcceptanceValidationError, OSError, subprocess.TimeoutExpired) as error:
        diagnostics["inspectError"] = _safe_failure(error)

    logs_command = ("docker", "logs", "--tail", "200", container_name)
    try:
        logs_result = runner(logs_command, timeout=30)
        if logs_result.returncode == 0:
            diagnostics["logs"] = sanitize_logs(_bounded_combined_output(logs_result))
        else:
            diagnostics["logsError"] = f"docker logs exited with code {logs_result.returncode}"
    except (AcceptanceValidationError, OSError, subprocess.TimeoutExpired) as error:
        diagnostics["logsError"] = _safe_failure(error)
    return diagnostics


def _cleanup_container(
    runner: CommandRunner, container_name: str
) -> dict[str, Any]:
    result: dict[str, Any] = {
        "stopExitCode": None,
        "removeExitCode": None,
        "inspectExitCode": None,
        "errors": [],
        "verified": False,
    }
    errors: list[str] = result["errors"]
    for key, command in (
        ("stopExitCode", ("docker", "stop", "--time", "10", container_name)),
        ("removeExitCode", ("docker", "rm", "--force", container_name)),
        ("inspectExitCode", ("docker", "inspect", container_name)),
    ):
        try:
            completed = runner(command, timeout=30)
            _bounded_output(completed)
            result[key] = completed.returncode
            if key == "inspectExitCode" and completed.returncode == 0:
                errors.append("inspect-after-cleanup-succeeded")
        except (AcceptanceValidationError, OSError, subprocess.TimeoutExpired) as error:
            errors.append(f"{key}:{type(error).__name__}")
            result[key] = None
    result["verified"] = (
        result["stopExitCode"] == 0
        and result["removeExitCode"] == 0
        and result["inspectExitCode"] != 0
        and not errors
    )
    return result


def _fixture_request(fixture_manifest: Path, config: Any) -> tuple[bytes, str]:
    manifest_sha = load_fixture_manifest(fixture_manifest)
    value = _parse_json_object(
        _read_bounded(fixture_manifest, "fixture manifest", 1024 * 1024),
        "fixture manifest",
    )
    fixtures = value.get("fixtures")
    if not isinstance(fixtures, list) or not fixtures:
        raise AcceptanceValidationError("fixture manifest has no fixtures")
    entry = fixtures[0]
    relative = entry.get("path") if isinstance(entry, dict) else None
    if not isinstance(relative, str):
        raise AcceptanceValidationError("fixture path is invalid")
    path = PurePosixPath(relative)
    if path.is_absolute() or any(part in {"", ".", ".."} for part in path.parts):
        raise AcceptanceValidationError("fixture path escapes its root")
    payload = _read_bounded(
        fixture_manifest.parent.joinpath(*path.parts), "fixture", MAX_FIXTURE_FILE_BYTES
    )
    if len(payload) != entry.get("bytes") or hashlib.sha256(payload).hexdigest() != entry.get("sha256"):
        raise AcceptanceValidationError("fixture receipt differs")
    request = json.dumps(
        {"file": base64.b64encode(payload).decode("ascii"), "fileType": 1},
        separators=(",", ":"),
        sort_keys=True,
    ).encode("utf-8")
    if len(request) > config.request_max_bytes:
        raise AcceptanceValidationError("fixture request exceeds request limit")
    return request, manifest_sha


def _docker_inspect(
    runner: CommandRunner, container_name: str, *, timeout: float = 30
) -> dict[str, Any]:
    command = ("docker", "inspect", container_name)
    completed = runner(command, timeout=timeout)
    raw = _require_success(completed, command)
    try:
        value = json.loads(raw.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise AcceptanceValidationError("docker inspect returned invalid JSON") from error
    if isinstance(value, list) and len(value) == 1 and isinstance(value[0], dict):
        return value[0]
    if isinstance(value, dict):
        return value
    raise AcceptanceValidationError("docker inspect must return one object")


def run_acceptance(
    inputs: ValidatedInputs,
    fixture_manifest: Path,
    output_root: Path,
    container_name: str,
    *,
    runner: CommandRunner = _default_runner,
) -> dict[str, Any]:
    del output_root
    validate_container_name(container_name)
    if inputs.config.model_source != "IMAGE" or inputs.model_snapshot is not None:
        raise AcceptanceValidationError("acceptance requires IMAGE mode")
    request, fixture_sha = _fixture_request(fixture_manifest, inputs.config)
    if fixture_sha != inputs.fixture_manifest_sha256:
        raise AcceptanceValidationError("fixture manifest digest differs")
    oversized = b"x" * (inputs.config.request_max_bytes + 1)
    report: dict[str, Any] = {
        "schemaVersion": 1,
        "kind": "paddle-ocr-acceptance",
        "status": "FAIL",
        "executionStatus": "NOT_STARTED",
        "image": inputs.image,
        "fixtureManifestSha256": inputs.fixture_manifest_sha256,
        "configSha256": inputs.config_sha256,
        "runtimeLimits": {
            "requestMaxBytes": inputs.config.request_max_bytes,
            "responseMaxBytes": inputs.config.response_max_bytes,
            "readinessTimeoutSeconds": inputs.config.readiness_timeout_seconds,
        },
    }
    started = False
    container_id_valid = False
    cleanup: dict[str, Any] | None = None
    try:
        image_command = (
            "docker",
            "image",
            "inspect",
            inputs.image,
            "--format",
            '{{.Os}}/{{.Architecture}}{{printf "\\n"}}{{index .RepoDigests 0}}',
        )
        image_raw = _require_success(runner(image_command, timeout=30), image_command)
        image_platform, image_ref = parse_image_inspect(image_raw, inputs.image)
        host_command = ("docker", "info", "--format", "{{.OSType}}/{{.Architecture}}")
        host_raw = _require_success(runner(host_command, timeout=30), host_command)
        host_platform = parse_host_platform(host_raw)
        report["platform"] = {
            "required": REQUIRED_PLATFORM,
            "image": image_platform,
            "host": host_platform,
            "imageReference": image_ref,
        }
        start_command = build_container_command(inputs.docker_command, container_name)
        started_result = runner(start_command, timeout=30)
        started_output = _require_success(started_result, start_command)
        started = True
        container_id = _decode_utf8(started_output, "docker run output").strip()
        if re.fullmatch(r"[0-9a-f]{12,128}", container_id) is None:
            raise AcceptanceValidationError("docker run did not return a container id")
        container_id_valid = True
        inspected = _docker_inspect(runner, container_name)
        inspect_checks = validate_container_inspect(inspected)
        deadline = time.monotonic() + inputs.config.readiness_timeout_seconds
        readiness: dict[str, Any] | None = None
        while readiness is None:
            health_command = build_exec_command(container_name, _HEALTH_PROBE)
            health_result = runner(health_command, timeout=10)
            if health_result.returncode == 0:
                readiness = parse_probe_output(_bounded_output(health_result))
                if readiness["status"] != 200:
                    readiness = None
            if readiness is not None:
                break
            if time.monotonic() >= deadline:
                raise AcceptanceValidationError("readiness deadline exceeded")
            time.sleep(1)
        ocr_command = build_exec_command(
            container_name, _POST_PROBE.replace("{path}", "/ocr"), interactive=True
        )
        ocr_result = runner(ocr_command, input=request, timeout=40)
        ocr_probe = parse_probe_output(_bounded_output(ocr_result))
        report.setdefault("observed", {})["ocr"] = ocr_probe
        if not 200 <= ocr_probe["status"] <= 299:
            raise AcceptanceValidationError(
                f"OCR probe did not return a 2xx status ({ocr_probe['status']})"
            )
        limit_result = runner(ocr_command, input=oversized, timeout=40)
        limit_probe = parse_probe_output(_bounded_output(limit_result))
        if limit_probe["status"] != 413:
            raise AcceptanceValidationError("request limit probe did not return 413")
        logs_command = ("docker", "logs", "--tail", "1000", container_name)
        logs = sanitize_logs(_require_success(runner(logs_command, timeout=30), logs_command))
        stats_command = ("docker", "stats", "--no-stream", "--format", "{{json .}}", container_name)
        stats = sanitize_logs(_require_success(runner(stats_command, timeout=30), stats_command))
        security = {
            "offlineStartup": True,
            "networkEgressDenied": inspect_checks["networkEgressDenied"],
            "portsUnpublished": inspect_checks["portsUnpublished"],
            "requestLimitsEnforced": True,
            "nonRoot": inspect_checks["nonRoot"],
            "readOnlyRoot": inspect_checks["readOnlyRoot"],
            "capabilitiesDropped": inspect_checks["capabilitiesDropped"],
            "noNewPrivileges": inspect_checks["noNewPrivileges"],
            "sensitiveLogScanPassed": True,
            "cleanupVerified": False,
        }
        report["observed"] = {
            "containerId": container_id,
            "readiness": readiness,
            "ocr": ocr_probe,
            "requestLimit": limit_probe,
            "security": inspect_checks,
            "logs": {"tail": logs},
            "resources": {"stats": stats},
        }
        report["security"] = security
        report["executionStatus"] = "PASS"
    except (AcceptanceValidationError, OSError, subprocess.TimeoutExpired) as error:
        report["failure"] = _safe_failure(error)
        report["executionStatus"] = "FAILED"
        if started and container_id_valid:
            report.setdefault("observed", {})["failureDiagnostics"] = _capture_failure_diagnostics(
                runner, container_name
            )
    finally:
        if started:
            cleanup = _cleanup_container(runner, container_name)
            report.setdefault("observed", {})["cleanup"] = cleanup
            report.setdefault("security", {})["cleanupVerified"] = cleanup["verified"]
            if not cleanup["verified"]:
                report["failure"] = "container cleanup could not be verified"
                report["executionStatus"] = "FAILED"
        if report["executionStatus"] == "PASS":
            report["status"] = "PASS"
            report["security"]["cleanupVerified"] = bool(cleanup and cleanup["verified"])
            if not report["security"]["cleanupVerified"]:
                report["status"] = "FAIL"
                report["executionStatus"] = "FAILED"
                report["failure"] = "container cleanup could not be verified"
    return report


def _write_report(path: Path, report: dict[str, Any]) -> None:
    parent = path.parent
    if parent.is_symlink() or not parent.is_dir() or path.is_symlink():
        raise AcceptanceValidationError("report path must be inside a regular directory")
    raw = json.dumps(report, ensure_ascii=False, separators=(",", ":"), sort_keys=True).encode()
    if len(raw) > MAX_REPORT_BYTES:
        raise AcceptanceValidationError("acceptance report exceeds the byte limit")
    flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL | getattr(os, "O_NOFOLLOW", 0)
    descriptor: int | None = None
    try:
        descriptor = os.open(path, flags, 0o600)
        written = 0
        while written < len(raw):
            written += os.write(descriptor, raw[written:])
        os.fsync(descriptor)
    except OSError as error:
        try:
            path.unlink()
        except OSError:
            pass
        raise AcceptanceValidationError("acceptance report could not be written") from error
    finally:
        if descriptor is not None:
            os.close(descriptor)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--image", required=True)
    parser.add_argument("--fixture-manifest", required=True, type=Path)
    parser.add_argument("--config", required=True, type=Path)
    parser.add_argument("--output-root", required=True, type=Path)
    parser.add_argument("--report", required=True, type=Path)
    parser.add_argument("--container-name", default=f"bluetape4k-paddleocr-{os.getpid()}")
    args = parser.parse_args(argv)
    try:
        inputs = validate_inputs(
            image=args.image,
            model_manifest=None,
            model_root=None,
            fixture_manifest=args.fixture_manifest,
            config=args.config,
            output_root=args.output_root,
        )
        report = run_acceptance(
            inputs,
            args.fixture_manifest,
            args.output_root,
            args.container_name,
        )
        _write_report(args.report, report)
        print(json.dumps(report, ensure_ascii=False, separators=(",", ":"), sort_keys=True))
        return 0 if report.get("status") == "PASS" else 1
    except (AcceptanceValidationError, OSError, SmokeValidationError, subprocess.TimeoutExpired) as error:
        print(f"acceptance failed: {_safe_failure(error)}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
