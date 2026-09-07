from __future__ import annotations

import hashlib
import io
import json
import os
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
    load_service_configuration,
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
        pair = json.dumps(tree_digests, separators=(",", ":"), sort_keys=True).encode()
        (models / "model-pair.json").write_bytes(pair)
        legal = b'{"components":[],"schemaVersion":1}'
        (self.root / "legal-inventory.json").write_bytes(legal)
        (self.root / "ocr-pipeline.yaml").write_text(
            "pipeline_name: OCR\n"
            "text_det_model_dir: /opt/bluetape4k/paddleocr/models/detector\n"
            "text_rec_model_dir: /opt/bluetape4k/paddleocr/models/recognizer\n",
            encoding="utf-8",
        )
        manifest = {
            "schemaVersion": 1,
            "inputLockSha256": "a" * 64,
            "legalInventorySha256": sha256(legal),
            "requirementsSha256": "b" * 64,
            "modelTreeDigests": tree_digests,
            "modelPairSha256": sha256(pair),
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
        with self.assertRaisesRegex(ServiceConfigurationError, "remote model source"):
            load_service_configuration(runtime_root=self.root, environment={})


if __name__ == "__main__":
    unittest.main()
