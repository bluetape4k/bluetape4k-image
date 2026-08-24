"""Regression tests for the Issue #545 offline preflight boundary."""

from __future__ import annotations

import hashlib
import json
import tempfile
import unittest
from pathlib import Path
from subprocess import CompletedProcess
from unittest.mock import patch

from paddle_ocr_smoke import (
    SmokeValidationError,
    build_docker_command,
    build_plan,
    load_fixture_manifest,
    load_model_manifest,
    load_service_config,
    run_preflight,
    validate_image_reference,
    validate_inputs,
)

IMAGE = "registry.example/paddle-ocr@sha256:" + "a" * 64


def sha256(payload: bytes) -> str:
    return hashlib.sha256(payload).hexdigest()


class PaddleOcrSmokeTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        self.snapshots = []
        self.root = Path(self.temp_dir.name)
        self.model_root = self.root / "model"
        self.model_root.mkdir()
        self.output_root = self.root / "out"
        self.output_root.mkdir()
        (self.model_root / "det.bin").write_bytes(b"detector\n")
        (self.model_root / "NOTICE.txt").write_text("Apache-2.0\n", encoding="utf-8")
        self.model_manifest = self.root / "model-manifest.json"
        self.fixture = self.root / "fixtures" / "clean.png"
        self.fixture.parent.mkdir()
        self.fixture.write_bytes(b"fixture\n")
        self.fixture_manifest = self.root / "fixture-manifest.json"
        self.config = self.root / "config.json"
        self._write_inputs()

    def tearDown(self) -> None:
        for snapshot in self.snapshots:
            snapshot.temp_dir.cleanup()
        self.temp_dir.cleanup()

    def _write_inputs(self) -> None:
        det = b"detector\n"
        notice = b"Apache-2.0\n"
        tree = f"det.bin\0{len(det)}\0{sha256(det)}\n"
        self.model_manifest.write_text(
            json.dumps(
                {
                    "schemaVersion": 1,
                    "modelId": "PP-OCRv6_tiny_det",
                    "revision": "b" * 40,
                    "source": "https://huggingface.co/PaddlePaddle/PP-OCRv6_tiny_det",
                    "licenseSpdx": "Apache-2.0",
                    "noticePath": "NOTICE.txt",
                    "noticeBytes": len(notice),
                    "noticeSha256": sha256(notice),
                    "files": [
                        {"path": "det.bin", "bytes": len(det), "sha256": sha256(det)}
                    ],
                    "treeSha256": sha256(tree.encode("utf-8")),
                    "offline": True,
                }
            ),
            encoding="utf-8",
        )
        self.fixture_manifest.write_text(
            json.dumps(
                {
                    "schemaVersion": 1,
                    "fixtures": [
                        {
                            "id": "clean-001",
                            "path": "fixtures/clean.png",
                            "bytes": len(b"fixture\n"),
                            "sha256": sha256(b"fixture\n"),
                        }
                    ],
                }
            ),
            encoding="utf-8",
        )
        self.config.write_text(
            json.dumps(
                {
                    "schemaVersion": 1,
                    "host": "127.0.0.1",
                    "port": 8080,
                    "network": "none",
                    "command": [
                        "paddlex",
                        "--serve",
                        "--pipeline",
                        "OCR",
                        "--host",
                        "127.0.0.1",
                        "--port",
                        "8080",
                    ],
                    "requestMaxBytes": 1024 * 1024,
                    "responseMaxBytes": 2 * 1024 * 1024,
                    "readinessTimeoutSeconds": 30,
                    "modelMount": "/models",
                    "outputMount": "/out",
                }
            ),
            encoding="utf-8",
        )

    def _inputs(self):
        inputs = validate_inputs(
            image=IMAGE,
            model_manifest=self.model_manifest,
            model_root=self.model_root,
            fixture_manifest=self.fixture_manifest,
            config=self.config,
            output_root=self.output_root,
        )
        self.snapshots.append(inputs.model_snapshot)
        return inputs

    def test_valid_digest_pinned_inputs_build_a_redacted_plan(self) -> None:
        inputs = self._inputs()

        plan = build_plan(inputs, self.output_root)

        self.assertEqual(plan["status"], "PLAN_ONLY")
        self.assertEqual(plan["executionStatus"], "PENDING")
        self.assertEqual(plan["securityPlan"]["networkEgressDenied"], True)
        self.assertEqual(
            plan["runtimeLimits"],
            {
                "requestMaxBytes": 1024 * 1024,
                "responseMaxBytes": 2 * 1024 * 1024,
                "readinessTimeoutSeconds": 30,
            },
        )
        command = " ".join(plan["redactedDockerCommand"])
        self.assertIn("<MODEL_ROOT>:/models:ro", command)
        self.assertNotIn(str(self.model_root), command)
        self.assertIn("--network none", command)
        self.assertNotIn("--publish", inputs.docker_command)
        self.assertNotIn(str(self.model_root), inputs.docker_command)
        self.assertTrue(
            any(
                token.startswith(str(inputs.model_snapshot.root.resolve()))
                for token in inputs.docker_command
            )
        )
        for flag in (
            "--read-only",
            "--cap-drop ALL",
            "--security-opt no-new-privileges:true",
            "--user 65532:65532",
            "--pids-limit 128",
            "--memory 1g",
            "--cpus 2",
            "--tmpfs /tmp:rw,noexec,nosuid,size=64m",
        ):
            self.assertIn(flag, command)

    def test_snapshot_is_verified_mount_source(self) -> None:
        inputs = self._inputs()
        snapshot_file = inputs.model_snapshot.root / "det.bin"
        snapshot_file.chmod(0o644)
        snapshot_file.write_bytes(b"tampered\n")

        with self.assertRaisesRegex(SmokeValidationError, "snapshot file hash"):
            build_plan(inputs, self.output_root)

    def test_raw_model_root_cannot_build_docker_command(self) -> None:
        inputs = self._inputs()

        with self.assertRaisesRegex(SmokeValidationError, "snapshot object"):
            build_docker_command(
                IMAGE, self.model_root, self.output_root, inputs.config
            )

    def test_raw_model_mutation_does_not_change_staged_plan(self) -> None:
        inputs = self._inputs()
        (self.model_root / "det.bin").write_bytes(b"changed after staging\n")

        plan = build_plan(inputs, self.output_root)

        self.assertEqual(plan["status"], "PLAN_ONLY")

    def test_mutable_image_tag_is_rejected(self) -> None:
        with self.assertRaisesRegex(SmokeValidationError, "immutable"):
            validate_image_reference("registry.example/paddle-ocr:3.7.0")

    def test_model_file_hash_tampering_fails_closed(self) -> None:
        self._inputs()
        (self.model_root / "det.bin").write_bytes(b"changed!\n")

        with self.assertRaisesRegex(SmokeValidationError, "model file hash"):
            load_model_manifest(self.model_manifest, self.model_root)

    def test_model_symlink_is_rejected(self) -> None:
        self._inputs()
        target = self.model_root / "det.bin"
        target.unlink()
        target.symlink_to(self.fixture)

        with self.assertRaisesRegex(SmokeValidationError, "contains a symlink"):
            load_model_manifest(self.model_manifest, self.model_root)

    def test_unlisted_model_file_is_rejected(self) -> None:
        self._inputs()
        (self.model_root / "unexpected.bin").write_bytes(b"unexpected\n")

        with self.assertRaisesRegex(SmokeValidationError, "file set differs"):
            load_model_manifest(self.model_manifest, self.model_root)

    def test_schema_version_must_be_a_real_integer(self) -> None:
        manifest = json.loads(self.model_manifest.read_text(encoding="utf-8"))
        manifest["schemaVersion"] = 1.0
        self.model_manifest.write_text(json.dumps(manifest), encoding="utf-8")

        with self.assertRaisesRegex(SmokeValidationError, "integer 1"):
            load_model_manifest(self.model_manifest, self.model_root)

    def test_model_revision_must_be_an_immutable_commit(self) -> None:
        manifest = json.loads(self.model_manifest.read_text(encoding="utf-8"))
        manifest["revision"] = "develop"
        self.model_manifest.write_text(json.dumps(manifest), encoding="utf-8")

        with self.assertRaisesRegex(SmokeValidationError, "40-character"):
            load_model_manifest(self.model_manifest, self.model_root)

    def test_image_control_character_is_rejected(self) -> None:
        with self.assertRaisesRegex(SmokeValidationError, "control characters"):
            validate_image_reference(
                "registry.example/paddle\x00ocr@sha256:" + "a" * 64
            )

    def test_model_metadata_control_character_is_rejected(self) -> None:
        manifest = json.loads(self.model_manifest.read_text(encoding="utf-8"))
        manifest["modelId"] = "PP-OCR\x00v6"
        self.model_manifest.write_text(json.dumps(manifest), encoding="utf-8")

        with self.assertRaisesRegex(SmokeValidationError, "control characters"):
            load_model_manifest(self.model_manifest, self.model_root)

    def test_model_source_control_character_is_rejected(self) -> None:
        manifest = json.loads(self.model_manifest.read_text(encoding="utf-8"))
        manifest["source"] = "https://huggingface.co/PaddlePaddle/PP-OCR\x00v6"
        self.model_manifest.write_text(json.dumps(manifest), encoding="utf-8")

        with self.assertRaisesRegex(SmokeValidationError, "control characters"):
            load_model_manifest(self.model_manifest, self.model_root)

    def test_model_metadata_c1_control_character_is_rejected(self) -> None:
        manifest = json.loads(self.model_manifest.read_text(encoding="utf-8"))
        manifest["modelId"] = "PP-OCR\u0085v6"
        self.model_manifest.write_text(json.dumps(manifest), encoding="utf-8")

        with self.assertRaisesRegex(SmokeValidationError, "control characters"):
            load_model_manifest(self.model_manifest, self.model_root)

    def test_model_aggregate_budget_is_fail_closed(self) -> None:
        with (
            patch("paddle_ocr_smoke.MAX_MODEL_TREE_BYTES", 1),
            self.assertRaisesRegex(SmokeValidationError, "model tree exceeds"),
        ):
            load_model_manifest(self.model_manifest, self.model_root)

    def test_fixture_manifest_traversal_is_rejected(self) -> None:
        manifest = json.loads(self.fixture_manifest.read_text(encoding="utf-8"))
        manifest["fixtures"][0]["path"] = "../model/NOTICE.txt"
        self.fixture_manifest.write_text(json.dumps(manifest), encoding="utf-8")

        with self.assertRaisesRegex(SmokeValidationError, "safe relative path"):
            load_fixture_manifest(self.fixture_manifest)

    def test_network_and_host_must_be_fail_closed(self) -> None:
        config = json.loads(self.config.read_text(encoding="utf-8"))
        config["network"] = "bridge"
        config["host"] = "0.0.0.0"
        self.config.write_text(json.dumps(config), encoding="utf-8")

        with self.assertRaisesRegex(SmokeValidationError, "127.0.0.1"):
            load_service_config(self.config)

    def test_shell_syntax_in_service_argv_is_rejected(self) -> None:
        config = json.loads(self.config.read_text(encoding="utf-8"))
        config["command"][-1] = "8080;curl http://attacker"
        self.config.write_text(json.dumps(config), encoding="utf-8")

        with self.assertRaisesRegex(SmokeValidationError, "shell syntax"):
            load_service_config(self.config)

    def test_unapproved_service_flag_is_rejected(self) -> None:
        config = json.loads(self.config.read_text(encoding="utf-8"))
        config["command"].append("--download-model")
        self.config.write_text(json.dumps(config), encoding="utf-8")

        with self.assertRaisesRegex(SmokeValidationError, "pinned loopback"):
            load_service_config(self.config)

    def test_non_empty_output_root_is_rejected(self) -> None:
        (self.output_root / "stale.log").write_text("stale\n", encoding="utf-8")

        with self.assertRaisesRegex(SmokeValidationError, "output root must be empty"):
            self._inputs()

    def test_preflight_requires_local_digest_match(self) -> None:
        inputs = self._inputs()

        def runner(*args, **kwargs):
            return CompletedProcess(args[0], 0, stdout=IMAGE + "\n", stderr="")

        report = run_preflight(inputs, runner=runner)

        self.assertEqual(report["status"], "PREFLIGHT_PASS")
        self.assertEqual(report["executionStatus"], "PENDING")

    def test_preflight_rejects_missing_offline_image(self) -> None:
        inputs = self._inputs()

        def runner(*args, **kwargs):
            return CompletedProcess(args[0], 1, stdout="", stderr="not found")

        with self.assertRaisesRegex(SmokeValidationError, "not available offline"):
            run_preflight(inputs, runner=runner)

    def test_preflight_rejects_inspect_digest_mismatch(self) -> None:
        inputs = self._inputs()

        def runner(*args, **kwargs):
            return CompletedProcess(
                args[0],
                0,
                stdout="registry.example/paddle-ocr@sha256:" + "b" * 64,
                stderr="",
            )

        with self.assertRaisesRegex(SmokeValidationError, "requested digest"):
            run_preflight(inputs, runner=runner)


if __name__ == "__main__":
    unittest.main()
