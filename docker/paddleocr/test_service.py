from __future__ import annotations

import hashlib
import io
import json
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from service import (
    READINESS_TIMEOUT_SECONDS,
    ReadinessState,
    ServiceConfigurationError,
    _read_bounded_response,
    build_upstream_command,
    build_upstream_environment,
    build_upstream_process_kwargs,
    load_service_configuration,
    prepare_upstream_environment,
    read_bounded_request,
    render_readiness,
)


def sha256(payload: bytes) -> str:
    return hashlib.sha256(payload).hexdigest()


class ServiceContractTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        models = self.root / "models"
        models.mkdir()
        tree_digests = {}
        for role in ("detector", "recognizer"):
            role_root = models / role
            role_root.mkdir()
            payload = f"{role}\n".encode()
            (role_root / "inference.pdmodel").write_bytes(payload)
            manifest = f"inference.pdmodel\t{len(payload)}\t{sha256(payload)}\n".encode()
            (models / f"{role}.manifest.txt").write_bytes(manifest)
            tree_digests[role] = sha256(manifest)
        legal = b'{"components":[],"schemaVersion":1}'
        (self.root / "legal-inventory.json").write_bytes(legal)
        (self.root / "ocr-pipeline.yaml").write_text(
            "pipeline_name: OCR\n"
            "text_type: general\n"
            "use_doc_preprocessor: false\n"
            "TextDetection:\n"
            "  model_dir: /opt/bluetape4k/paddleocr/models/detector\n"
            "TextRecognition:\n"
            "  model_dir: /opt/bluetape4k/paddleocr/models/recognizer\n",
            encoding="utf-8",
        )
        pipeline = (self.root / "ocr-pipeline.yaml").read_bytes()
        manifest = {
            "schemaVersion": 1,
            "inputLockSha256": "a" * 64,
            "legalInventorySha256": sha256(legal),
            "pipelineSha256": sha256(pipeline),
            "modelTreeDigests": tree_digests,
            "modelPairSha256": sha256(
                json.dumps(tree_digests, separators=(",", ":"), sort_keys=True).encode()
            ),
        }
        (self.root / "model-manifest.json").write_text(
            json.dumps(manifest, separators=(",", ":"), sort_keys=True),
            encoding="utf-8",
        )

    def tearDown(self) -> None:
        self.temporary.cleanup()

    def test_external_model_root_is_rejected_before_file_access(self) -> None:
        for value in ("/tmp/models", ""):
            with (
                self.subTest(value=value),
                patch.dict(os.environ, {"PADDLEOCR_MODEL_ROOT": value}),
                self.assertRaisesRegex(ServiceConfigurationError, "external model override"),
            ):
                load_service_configuration(runtime_root=self.root / "missing")

    def test_configuration_verifies_both_model_trees_pair_and_legal_inventory(self) -> None:
        config = load_service_configuration(runtime_root=self.root, environment={})
        self.assertTrue(config.readiness.detector_verified)
        self.assertTrue(config.readiness.recognizer_verified)
        self.assertTrue(config.readiness.legal_verified)
        self.assertEqual(config.readiness_timeout_seconds, READINESS_TIMEOUT_SECONDS)
        self.assertEqual(
            build_upstream_command(config),
            (
                "paddlex", "--serve", "--pipeline", str(self.root / "ocr-pipeline.yaml"),
                "--host", "127.0.0.1", "--port", "18080",
            ),
        )

    def test_upstream_process_keeps_stderr_for_failure_diagnostics(self) -> None:
        environment = {"PATH": "/usr/bin"}
        process_kwargs = build_upstream_process_kwargs(environment)
        self.assertIs(process_kwargs["stdin"], subprocess.DEVNULL)
        self.assertIs(process_kwargs["stdout"], subprocess.DEVNULL)
        self.assertIs(process_kwargs["stderr"], sys.stderr)
        self.assertEqual(process_kwargs["env"], environment)

    def test_missing_or_changed_model_never_opens_readiness(self) -> None:
        (self.root / "models/recognizer/inference.pdmodel").write_bytes(b"changed")
        with self.assertRaisesRegex(ServiceConfigurationError, "recognizer"):
            load_service_configuration(runtime_root=self.root, environment={})

    def test_nested_model_symlink_is_rejected_before_model_file_read(self) -> None:
        model = self.root / "models/detector/inference.pdmodel"
        model.unlink()
        outside = self.root / "outside"
        outside.mkdir()
        (outside / "inference.pdmodel").write_bytes(b"detector\n")
        (self.root / "models/detector/nested").symlink_to(outside, target_is_directory=True)
        with self.assertRaisesRegex(ServiceConfigurationError, "symlink"):
            load_service_configuration(runtime_root=self.root, environment={})

    def test_ready_requires_verified_files_and_upstream_health(self) -> None:
        state = ReadinessState(detector_verified=True, recognizer_verified=False)
        self.assertEqual(render_readiness(state), (503, b'{"ready":false}\n'))
        ready = ReadinessState(
            detector_verified=True,
            recognizer_verified=True,
            legal_verified=True,
            pipeline_verified=True,
            upstream_ready=True,
        )
        self.assertEqual(render_readiness(ready), (200, b'{"ready":true}\n'))

    def test_request_body_is_bounded_and_must_match_content_length(self) -> None:
        self.assertEqual(read_bounded_request(io.BytesIO(b"abc"), 3, 3), b"abc")
        for length, maximum, message in ((4, 3, "exceeds"), (3, 3, "truncated")):
            stream = io.BytesIO(b"ab" if message == "truncated" else b"abcd")
            with self.subTest(message=message), self.assertRaisesRegex(
                ServiceConfigurationError, message
            ):
                read_bounded_request(stream, length, maximum)

    def test_upstream_response_is_bounded(self) -> None:
        self.assertEqual(_read_bounded_response(io.BytesIO(b"abc"), 3), b"abc")
        with self.assertRaisesRegex(ServiceConfigurationError, "response exceeds"):
            _read_bounded_response(io.BytesIO(b"abcd"), 3)

    def test_pipeline_rejects_first_use_download_sources(self) -> None:
        pipeline = self.root / "ocr-pipeline.yaml"
        pipeline.write_text(
            pipeline.read_text(encoding="utf-8")
            + "remote_model: https://example.invalid/download\n",
            encoding="utf-8",
        )
        manifest = json.loads((self.root / "model-manifest.json").read_text(encoding="utf-8"))
        manifest["pipelineSha256"] = sha256(pipeline.read_bytes())
        (self.root / "model-manifest.json").write_text(
            json.dumps(manifest, separators=(",", ":"), sort_keys=True), encoding="utf-8"
        )
        with self.assertRaisesRegex(ServiceConfigurationError, "remote model source"):
            load_service_configuration(runtime_root=self.root, environment={})

    def test_pipeline_requires_general_text_type(self) -> None:
        pipeline = self.root / "ocr-pipeline.yaml"
        pipeline.write_text(
            pipeline.read_text(encoding="utf-8").replace("text_type: general", "text_type: table"),
            encoding="utf-8",
        )
        manifest = json.loads((self.root / "model-manifest.json").read_text(encoding="utf-8"))
        manifest["pipelineSha256"] = sha256(pipeline.read_bytes())
        (self.root / "model-manifest.json").write_text(
            json.dumps(manifest, separators=(",", ":"), sort_keys=True), encoding="utf-8"
        )
        with self.assertRaisesRegex(ServiceConfigurationError, "text_type"):
            load_service_configuration(runtime_root=self.root, environment={})

    def test_environment_is_scoped_to_tmp_and_removes_proxies(self) -> None:
        environment = build_upstream_environment({
            "PATH": "/usr/bin",
            "HTTP_PROXY": "http://proxy.invalid",
            "NO_PROXY": "127.0.0.1",
            "HOME": "/unsafe",
        })
        self.assertEqual(environment["HOME"], "/tmp")
        self.assertEqual(environment["PADDLE_PDX_CACHE_HOME"], "/tmp/.paddlex")
        self.assertNotIn("HTTP_PROXY", environment)
        self.assertNotIn("NO_PROXY", environment)

    def test_environment_rejects_runtime_overrides(self) -> None:
        with self.assertRaisesRegex(ServiceConfigurationError, "runtime override"):
            build_upstream_environment({"PYTHONPATH": "/tmp/override"})

    def test_compatibility_shims_are_scoped_and_cleaned(self) -> None:
        with patch("service.ctypes.CDLL", side_effect=OSError("missing")), patch(
            "service._bundled_iomp_library", return_value=None
        ), patch("service._opencv_headless_alias_required", return_value=False):
            environment, temporary = prepare_upstream_environment({"PATH": "/usr/bin"})
        self.assertNotIn("LD_LIBRARY_PATH", environment)
        self.assertIsNone(temporary)

    def test_manifest_does_not_require_legacy_model_pair_file(self) -> None:
        self.assertFalse((self.root / "models/model-pair.json").exists())
        self.assertIsNotNone(load_service_configuration(runtime_root=self.root, environment={}))


if __name__ == "__main__":
    unittest.main()
