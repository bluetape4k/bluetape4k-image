#!/usr/bin/env python3
"""Regression tests for static detection sample asset validation."""

from __future__ import annotations

import importlib.util
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch


SCRIPT = Path(__file__).with_name("validate-readme-visual-assets.py")
MODULE_SPEC = importlib.util.spec_from_file_location("readme_visual_asset_validator", SCRIPT)
if MODULE_SPEC is None or MODULE_SPEC.loader is None:
    raise RuntimeError(f"cannot load validator module: {SCRIPT}")
MODULE = importlib.util.module_from_spec(MODULE_SPEC)
MODULE_SPEC.loader.exec_module(MODULE)


ROOT = Path(__file__).resolve().parents[2]


class DetectionSampleAssetTest(unittest.TestCase):
    def test_checked_in_detection_samples_have_static_assets(self) -> None:
        lines: list[str] = []

        MODULE.validate_detection_sample_assets(lines)

        self.assertIn("manifestSamples=4", lines[0])
        self.assertIn("decode=delegated-to-Kotlin", lines[0])

    def test_missing_preview_is_reported_without_decoding_images(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            manifest = root / "images/src/test/resources/detection/samples/metadata.json"
            manifest.parent.mkdir(parents=True)
            manifest.write_text(
                json.dumps([{"id": "sample", "resourcePath": "detection/samples/sample.png"}]),
                encoding="utf-8",
            )
            resource = root / "images/src/test/resources/detection/samples/sample.png"
            resource.write_bytes(b"not decoded by this validator")

            with patch.object(MODULE, "ROOT", root), patch.object(MODULE, "SAMPLE_MANIFEST", manifest), patch.object(
                MODULE, "SAMPLE_PREVIEW_DIR", root / "docs/images/detection-samples"
            ):
                with self.assertRaisesRegex(AssertionError, "missing detection sample preview"):
                    MODULE.validate_detection_sample_assets([])


if __name__ == "__main__":
    unittest.main()
