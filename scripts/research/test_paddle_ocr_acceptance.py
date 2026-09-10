"""Regression tests for the Issue #609-E no-egress acceptance gate."""

from __future__ import annotations

import hashlib
import json
import tempfile
import unittest
from dataclasses import replace
from pathlib import Path
from subprocess import CompletedProcess

from paddle_ocr_acceptance import (
    AcceptanceValidationError,
    _LIMIT_PROBE,
    _POST_PROBE,
    _docker_inspect,
    _write_report,
    build_container_command,
    parse_host_platform,
    parse_image_inspect,
    parse_probe_output,
    run_acceptance,
    sanitize_logs,
    validate_container_inspect,
    validate_container_name,
)
from paddle_ocr_smoke import validate_inputs

IMAGE = "registry.example/paddle-ocr@sha256:" + "a" * 64


def _completed(returncode: int = 0, stdout: bytes = b"", stderr: bytes = b"") -> CompletedProcess[bytes]:
    return CompletedProcess((), returncode, stdout, stderr)


class PaddleOcrAcceptanceTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        self.output = self.root / "output"
        self.output.mkdir()
        fixture = self.root / "clean.png"
        fixture.write_bytes(b"fixture\n")
        self.fixture_manifest = self.root / "fixture-manifest.json"
        self.fixture_manifest.write_text(
            json.dumps({
                "schemaVersion": 1,
                "fixtures": [{
                    "id": "clean",
                    "path": "clean.png",
                    "bytes": fixture.stat().st_size,
                    "sha256": hashlib.sha256(fixture.read_bytes()).hexdigest(),
                }],
            }),
            encoding="utf-8",
        )
        self.config = self.root / "config.json"
        self.config.write_text(
            json.dumps({
                "schemaVersion": 1,
                "modelSource": "IMAGE",
                "host": "127.0.0.1",
                "port": 8080,
                "network": "none",
                "command": ["--host", "127.0.0.1", "--port", "8080"],
                "requestMaxBytes": 1024,
                "responseMaxBytes": 2048,
                "readinessTimeoutSeconds": 1,
                "modelMount": None,
                "outputMount": "/out",
            }),
            encoding="utf-8",
        )

    def tearDown(self) -> None:
        self.temporary.cleanup()

    def _inputs(self):
        return validate_inputs(
            image=IMAGE,
            model_manifest=None,
            model_root=None,
            fixture_manifest=self.fixture_manifest,
            config=self.config,
            output_root=self.output,
        )

    def test_container_name_is_safe(self) -> None:
        self.assertEqual(validate_container_name("bluetape4k-paddleocr-12"), "bluetape4k-paddleocr-12")
        with self.assertRaises(AcceptanceValidationError):
            validate_container_name("unsafe/name")

    def test_container_command_is_detached_and_unpublished(self) -> None:
        command = (
            "docker", "run", "--rm", "--network", "none", IMAGE, "--host", "127.0.0.1",
        )
        rendered = build_container_command(command, "bluetape4k-paddleocr-12")
        self.assertIn("--detach", rendered)
        self.assertIn("--name", rendered)
        self.assertNotIn("--rm", rendered)
        with self.assertRaisesRegex(AcceptanceValidationError, "published"):
            build_container_command(command + ("--publish=8080:8080",), "bluetape4k-paddleocr-12")

    def test_image_acceptance_rejects_legacy_model_inputs(self) -> None:
        inputs = replace(self._inputs(), config=replace(self._inputs().config, model_source="LEGACY_MOUNT"))
        with self.assertRaisesRegex(AcceptanceValidationError, "IMAGE"):
            run_acceptance(inputs, self.fixture_manifest, self.output, "bluetape4k-paddleocr-12", runner=lambda *args, **kwargs: _completed())

    def test_probe_and_platform_parsers_are_strict(self) -> None:
        receipt = {"status": 200, "bytes": 2, "sha256": "b" * 64}
        self.assertEqual(parse_probe_output("BLUETAPE4K_PROBE:" + json.dumps(receipt)), receipt)
        with self.assertRaises(AcceptanceValidationError):
            parse_probe_output("noise\nBLUETAPE4K_PROBE:" + json.dumps(receipt))
        self.assertEqual(parse_host_platform("linux/amd64\n"), "linux/amd64")
        self.assertEqual(parse_host_platform("linux/x86_64\n"), "linux/amd64")
        with self.assertRaises(AcceptanceValidationError):
            parse_host_platform("linux/aarch64")
        self.assertEqual(parse_image_inspect("linux/amd64\n" + IMAGE + "\n", IMAGE)[0], "linux/amd64")
        with self.assertRaisesRegex(AcceptanceValidationError, "UTF-8"):
            parse_probe_output(b"\xff")
        self.assertIn("http://127.0.0.1:8080{path}", _POST_PROBE)
        self.assertIn("Content-Length", _LIMIT_PROBE)
        self.assertIn("{content_length}", _LIMIT_PROBE)

    def test_docker_inspect_rejects_invalid_json(self) -> None:
        with self.assertRaisesRegex(AcceptanceValidationError, "invalid JSON"):
            _docker_inspect(
                lambda *args, **kwargs: _completed(stdout=b"not-json"),
                "bluetape4k-paddleocr-12",
            )

    def test_security_contract_rejects_bridge_and_published_ports(self) -> None:
        value = {
            "Config": {"User": "65532:65532"},
            "HostConfig": {
                "ReadonlyRootfs": True,
                "CapDrop": ["ALL"],
                "SecurityOpt": ["no-new-privileges:true"],
                "NetworkMode": "none",
                "PortBindings": {},
                "PublishAllPorts": False,
                "PidsLimit": 128,
                "Memory": 1024 * 1024 * 1024,
                "NanoCpus": 2_000_000_000,
            },
            "NetworkSettings": {"Ports": {}},
            "State": {"Running": True},
        }
        checks = validate_container_inspect(value)
        self.assertTrue(checks["networkEgressDenied"])
        value["HostConfig"]["NetworkMode"] = "bridge"
        with self.assertRaisesRegex(AcceptanceValidationError, "networkEgressDenied"):
            validate_container_inspect(value)

    def test_logs_are_redacted_and_credentials_rejected(self) -> None:
        self.assertNotIn("/tmp/run", sanitize_logs("path /tmp/run/output"))
        with self.assertRaises(AcceptanceValidationError):
            sanitize_logs("token=secret")

    def test_full_fake_acceptance_passes_and_cleans_container(self) -> None:
        inputs = self._inputs()
        calls: list[tuple[tuple[str, ...], bytes | None]] = []
        inspect_value = {
            "Config": {"User": "65532:65532"},
            "HostConfig": {
                "ReadonlyRootfs": True,
                "CapDrop": ["ALL"],
                "SecurityOpt": ["no-new-privileges:true"],
                "NetworkMode": "none",
                "PortBindings": {},
                "PublishAllPorts": False,
                "PidsLimit": 128,
                "Memory": 1024 * 1024 * 1024,
                "NanoCpus": 2_000_000_000,
            },
            "NetworkSettings": {"Ports": {}},
            "State": {"Running": True},
        }

        def runner(command, *, input=None, timeout=None):
            del timeout
            command = tuple(command)
            calls.append((command, input))
            if command[:3] == ("docker", "image", "inspect"):
                return _completed(stdout=("linux/amd64\n" + IMAGE + "\n").encode())
            if command[:2] == ("docker", "info"):
                return _completed(stdout=b"linux/amd64\n")
            if command[:2] == ("docker", "run"):
                return _completed(stdout=b"abcdef123456\n")
            if command[:3] == ("docker", "inspect", "bluetape4k-paddleocr-12"):
                if len([item for item, _ in calls if item[:3] == ("docker", "inspect", "bluetape4k-paddleocr-12")]) == 1:
                    return _completed(stdout=json.dumps([inspect_value]).encode())
                return _completed(returncode=1)
            if command[:2] == ("docker", "exec"):
                if "--interactive" in command:
                    status = 413 if (
                        "Content-Length" in command[-1]
                        or input is not None and len(input) > 1024
                    ) else 200
                else:
                    status = 200
                body = b"ok"
                receipt = {"status": status, "bytes": len(body), "sha256": hashlib.sha256(body).hexdigest()}
                return _completed(stdout=("BLUETAPE4K_PROBE:" + json.dumps(receipt, sort_keys=True)).encode())
            if command[:3] == ("docker", "logs", "--tail"):
                return _completed(stdout=b"service ready\n")
            if command[:3] == ("docker", "stats", "--no-stream"):
                return _completed(stdout=b'{"MemUsage":"1MiB"}\n')
            if command[:3] == ("docker", "stop", "--time"):
                return _completed()
            if command[:3] == ("docker", "rm", "--force"):
                return _completed()
            raise AssertionError(command)

        report = run_acceptance(
            inputs, self.fixture_manifest, self.output, "bluetape4k-paddleocr-12", runner=runner
        )
        self.assertEqual(report["status"], "PASS")
        self.assertEqual(report["executionStatus"], "PASS")
        self.assertTrue(report["observed"]["cleanup"]["verified"])
        self.assertIn("--network", next(command for command, _ in calls if command[:2] == ("docker", "run")))
        limit_command, limit_input = next(
            (command, value)
            for command, value in calls
            if command[:2] == ("docker", "exec") and "Content-Length" in command[-1]
        )
        self.assertIn("Content-Length', '1025", limit_command[-1])
        self.assertIsNone(limit_input)

    def test_readiness_failure_records_sanitized_diagnostics(self) -> None:
        inputs = self._inputs()
        inspect_value = {
            "Config": {"User": "65532:65532"},
            "HostConfig": {
                "ReadonlyRootfs": True,
                "CapDrop": ["ALL"],
                "SecurityOpt": ["no-new-privileges:true"],
                "NetworkMode": "none",
                "PortBindings": {},
                "PublishAllPorts": False,
                "PidsLimit": 128,
                "Memory": 1024 * 1024 * 1024,
                "NanoCpus": 2_000_000_000,
            },
            "NetworkSettings": {"Ports": {}},
            "State": {
                "Running": True,
                "Status": "running",
                "ExitCode": 0,
                "Error": "",
                "OOMKilled": False,
            },
        }
        inspect_calls = 0

        def runner(command, *, input=None, timeout=None):
            del input, timeout
            nonlocal inspect_calls
            command = tuple(command)
            if command[:3] == ("docker", "image", "inspect"):
                return _completed(stdout=("linux/amd64\n" + IMAGE + "\n").encode())
            if command[:2] == ("docker", "info"):
                return _completed(stdout=b"linux/amd64\n")
            if command[:2] == ("docker", "run"):
                return _completed(stdout=b"abcdef123456\n")
            if command[:3] == ("docker", "inspect", "bluetape4k-paddleocr-15"):
                inspect_calls += 1
                if inspect_calls < 3:
                    return _completed(stdout=json.dumps([inspect_value]).encode())
                return _completed(returncode=1)
            if command[:2] == ("docker", "exec"):
                return _completed(returncode=1, stderr=b"upstream is not ready")
            if command[:3] == ("docker", "logs", "--tail"):
                return _completed(stderr=b"startup failed at /tmp/paddleocr\n")
            if command[:3] == ("docker", "stop", "--time"):
                return _completed()
            if command[:3] == ("docker", "rm", "--force"):
                return _completed()
            raise AssertionError(command)

        report = run_acceptance(
            inputs,
            self.fixture_manifest,
            self.output,
            "bluetape4k-paddleocr-15",
            runner=runner,
        )
        self.assertEqual((report["status"], report["executionStatus"]), ("FAIL", "FAILED"))
        diagnostics = report["observed"]["failureDiagnostics"]
        self.assertEqual(diagnostics["state"], {
            "running": True,
            "status": "running",
            "exitCode": 0,
            "error": "",
            "oomKilled": False,
        })
        self.assertEqual(diagnostics["logs"], "startup failed at <PATH>\n")
        self.assertTrue(report["observed"]["cleanup"]["verified"])

    def test_ocr_failure_records_probe_receipt(self) -> None:
        inputs = self._inputs()
        inspect_value = {
            "Config": {"User": "65532:65532"},
            "HostConfig": {
                "ReadonlyRootfs": True,
                "CapDrop": ["ALL"],
                "SecurityOpt": ["no-new-privileges:true"],
                "NetworkMode": "none",
                "PortBindings": {},
                "PublishAllPorts": False,
                "PidsLimit": 128,
                "Memory": 1024 * 1024 * 1024,
                "NanoCpus": 2_000_000_000,
            },
            "NetworkSettings": {"Ports": {}},
            "State": {"Running": True},
        }
        inspect_calls = 0
        probe_body = b'{"detail":"invalid input"}'
        probe_receipt = {
            "status": 422,
            "bytes": len(probe_body),
            "sha256": hashlib.sha256(probe_body).hexdigest(),
        }

        def runner(command, *, input=None, timeout=None):
            del input, timeout
            nonlocal inspect_calls
            command = tuple(command)
            if command[:3] == ("docker", "image", "inspect"):
                return _completed(stdout=("linux/amd64\n" + IMAGE + "\n").encode())
            if command[:2] == ("docker", "info"):
                return _completed(stdout=b"linux/amd64\n")
            if command[:2] == ("docker", "run"):
                return _completed(stdout=b"abcdef123456\n")
            if command[:3] == ("docker", "inspect", "bluetape4k-paddleocr-16"):
                inspect_calls += 1
                if inspect_calls == 1:
                    return _completed(stdout=json.dumps([inspect_value]).encode())
                return _completed(returncode=1)
            if command[:2] == ("docker", "exec"):
                if "--interactive" in command:
                    return _completed(
                        stdout=(
                            "BLUETAPE4K_PROBE:" + json.dumps(probe_receipt, sort_keys=True)
                        ).encode()
                    )
                return _completed(
                    stdout=(
                        "BLUETAPE4K_PROBE:" + json.dumps(
                            {"status": 200, "bytes": 2, "sha256": "b" * 64},
                            sort_keys=True,
                        )
                    ).encode()
                )
            if command[:3] == ("docker", "logs", "--tail"):
                return _completed(stdout=b"service ready\n")
            if command[:3] == ("docker", "stop", "--time"):
                return _completed()
            if command[:3] == ("docker", "rm", "--force"):
                return _completed()
            raise AssertionError(command)

        report = run_acceptance(
            inputs,
            self.fixture_manifest,
            self.output,
            "bluetape4k-paddleocr-16",
            runner=runner,
        )
        self.assertEqual((report["status"], report["executionStatus"]), ("FAIL", "FAILED"))
        self.assertEqual(report["failure"], "OCR probe did not return a 2xx status (422)")
        self.assertEqual(report["observed"]["ocr"], probe_receipt)
        self.assertEqual(report["observed"]["failureDiagnostics"]["upstreamHealth"], {
            "status": 200,
            "bytes": 2,
            "sha256": "b" * 64,
        })
        self.assertTrue(report["observed"]["cleanup"]["verified"])

    def test_cleanup_failure_forces_failed_report(self) -> None:
        inputs = self._inputs()

        def runner(command, *, input=None, timeout=None):
            del input, timeout
            command = tuple(command)
            if command[:3] == ("docker", "image", "inspect"):
                return _completed(stdout=("linux/amd64\n" + IMAGE + "\n").encode())
            if command[:2] == ("docker", "info"):
                return _completed(stdout=b"linux/amd64")
            if command[:2] == ("docker", "run"):
                return _completed(stdout=b"abcdef123456")
            if command[:3] == ("docker", "inspect", "bluetape4k-paddleocr-13"):
                return _completed(stdout=json.dumps({
                    "Config": {"User": "65532:65532"},
                    "HostConfig": {"ReadonlyRootfs": True, "CapDrop": ["ALL"], "SecurityOpt": ["no-new-privileges:true"], "NetworkMode": "none", "PortBindings": {}, "PublishAllPorts": False, "PidsLimit": 128, "Memory": 1024 * 1024 * 1024, "NanoCpus": 2_000_000_000},
                    "NetworkSettings": {"Ports": {}}, "State": {"Running": True},
                }).encode())
            if command[:2] == ("docker", "exec"):
                status = 413 if command.count("--interactive") else 200
                body = b"ok"
                return _completed(stdout=("BLUETAPE4K_PROBE:" + json.dumps({"status": status, "bytes": len(body), "sha256": hashlib.sha256(body).hexdigest()})).encode())
            if command[:3] == ("docker", "logs", "--tail"):
                return _completed(stdout=b"ok")
            if command[:3] == ("docker", "stats", "--no-stream"):
                return _completed(stdout=b"{}")
            if command[:3] == ("docker", "stop", "--time"):
                raise OSError("stop failed")
            if command[:3] == ("docker", "rm", "--force"):
                return _completed()
            if command[:3] == ("docker", "inspect", "bluetape4k-paddleocr-13"):
                return _completed(returncode=1)
            raise AssertionError(command)

        report = run_acceptance(inputs, self.fixture_manifest, self.output, "bluetape4k-paddleocr-13", runner=runner)
        self.assertEqual((report["status"], report["executionStatus"]), ("FAIL", "FAILED"))
        self.assertFalse(report["observed"]["cleanup"]["verified"])

    def test_invalid_container_id_still_attempts_cleanup(self) -> None:
        inputs = self._inputs()
        commands: list[tuple[str, ...]] = []

        def runner(command, *, input=None, timeout=None):
            del input, timeout
            command = tuple(command)
            commands.append(command)
            if command[:3] == ("docker", "image", "inspect"):
                return _completed(stdout=("linux/amd64\n" + IMAGE + "\n").encode())
            if command[:2] == ("docker", "info"):
                return _completed(stdout=b"linux/amd64")
            if command[:2] == ("docker", "run"):
                return _completed(stdout=b"invalid-container-id")
            if command[:3] == ("docker", "stop", "--time"):
                return _completed()
            if command[:3] == ("docker", "rm", "--force"):
                return _completed()
            if command[:3] == ("docker", "inspect", "bluetape4k-paddleocr-14"):
                return _completed(returncode=1)
            raise AssertionError(command)

        report = run_acceptance(
            inputs,
            self.fixture_manifest,
            self.output,
            "bluetape4k-paddleocr-14",
            runner=runner,
        )
        self.assertEqual((report["status"], report["executionStatus"]), ("FAIL", "FAILED"))
        self.assertIn(("docker", "stop", "--time", "10", "bluetape4k-paddleocr-14"), commands)

    def test_write_report_is_exclusive_and_private(self) -> None:
        report_path = self.root / "report.json"
        _write_report(report_path, {"status": "FAIL"})
        self.assertEqual(report_path.stat().st_mode & 0o777, 0o600)
        with self.assertRaises(AcceptanceValidationError):
            _write_report(report_path, {"status": "FAIL"})


if __name__ == "__main__":
    unittest.main()
