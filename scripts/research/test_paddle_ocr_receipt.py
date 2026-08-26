"""Tests for the fail-closed Issue #545 receipt contract."""

from __future__ import annotations

import hashlib
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from paddle_ocr_receipt import (
    MAX_RECEIPT_BYTES,
    MAX_SMOKE_LOG_BYTES,
    ReceiptValidationError,
    main,
    validate_receipt,
    validate_receipt_file,
)

RECEIPT_ARTIFACTS = {
    "smoke-report": ("receipts/smoke-report.json", "application/json", b"smoke-pass\n"),
    "smoke-logs": ("receipts/smoke-redacted.log", "text/plain", b"redacted\n"),
    "smoke-cleanup": ("receipts/smoke-cleanup.txt", "text/plain", b"cleanup=pass\n"),
    "sbom": (
        "receipts/sbom.spdx.json",
        "application/json",
        b'{"spdxVersion":"SPDX-2.3"}\n',
    ),
    "provenance-attestation": (
        "receipts/provenance.json",
        "application/json",
        b"provenance\n",
    ),
    "sbom-attestation": (
        "receipts/sbom-attestation.json",
        "application/json",
        b"sbom-attestation\n",
    ),
    "license-notice": ("receipts/NOTICE.txt", "text/plain", b"Apache-2.0\n"),
}


def valid_receipt() -> dict:
    return {
        "schemaVersion": 1,
        "kind": "paddle-ocr-service-receipt",
        "status": "PASS",
        "validationScope": "CONTRACT_ONLY",
        "run": {
            "repository": "bluetape4k/bluetape4k-image",
            "commitSha": "8c3f152cc5b44d3a4007197fa112ffb392340751",
            "workflowRunId": 32649379038,
            "fixtureManifestSha256": "a" * 64,
            "modelManifestSha256": "b" * 64,
            "containerImageDigest": "sha256:" + "c" * 64,
            "hostArchitecture": "linux/amd64",
            "configSha256": "d" * 64,
        },
        "software": {
            "python": "3.12.4",
            "paddle": "3.0.0",
            "paddleocr": "3.7.0",
            "paddlex": "3.7.0",
        },
        "security": {
            "offlineStartup": True,
            "modelChecksumVerified": True,
            "noFirstUseDownload": True,
            "networkEgressDenied": True,
            "authRequired": True,
            "tlsVerified": True,
            "sensitiveLogScanPassed": True,
            "requestLimitsEnforced": True,
            "nonRoot": True,
            "readOnlyRoot": True,
            "capabilitiesDropped": True,
            "cleanupVerified": True,
        },
        "artifacts": [],
        "metrics": {
            "readinessSeconds": 12.5,
            "ocrLatencyMs": 84.0,
            "rssBytes": 123456789,
            "requestBytes": 1024,
            "responseBytes": 512,
        },
    }


def add_artifacts(receipt: dict, root: Path) -> None:
    artifacts = []
    for name, (relative_path, media_type, payload) in RECEIPT_ARTIFACTS.items():
        target = root / relative_path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(payload)
        artifacts.append(
            {
                "name": name,
                "path": relative_path,
                "bytes": len(payload),
                "sha256": hashlib.sha256(payload).hexdigest(),
                "mediaType": media_type,
            },
        )
    receipt["artifacts"] = artifacts


class PaddleOcrReceiptTest(unittest.TestCase):
    def test_valid_receipt_and_artifact_hashes_pass(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            receipt = valid_receipt()
            add_artifacts(receipt, root)
            receipt_path = root / "receipt.json"
            receipt_path.write_text(json.dumps(receipt), encoding="utf-8")

            loaded = validate_receipt_file(receipt_path, artifact_root=root)

            self.assertEqual(loaded["status"], "PASS")

    def test_artifact_tampering_fails_closed(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            receipt = valid_receipt()
            add_artifacts(receipt, root)
            (root / "receipts/smoke-report.json").write_bytes(b"tampered\n")

            with self.assertRaisesRegex(
                ReceiptValidationError, "artifact byte count differs"
            ):
                validate_receipt(receipt, artifact_root=root)

    def test_same_size_artifact_tampering_fails_hash_check(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            receipt = valid_receipt()
            add_artifacts(receipt, root)
            (root / "receipts/smoke-report.json").write_bytes(b"smoke-fail\n")

            with self.assertRaisesRegex(
                ReceiptValidationError, "artifact SHA-256 differs"
            ):
                validate_receipt(receipt, artifact_root=root)

    def test_missing_security_gate_is_rejected(self) -> None:
        receipt = valid_receipt()
        receipt["security"]["networkEgressDenied"] = False

        with self.assertRaisesRegex(ReceiptValidationError, "networkEgressDenied"):
            validate_receipt(receipt)

    def test_unpinned_software_version_is_rejected(self) -> None:
        receipt = valid_receipt()
        receipt["software"]["paddleocr"] = "latest"

        with self.assertRaisesRegex(ReceiptValidationError, "floating selector"):
            validate_receipt(receipt)

    def test_non_canonical_semantic_version_is_rejected(self) -> None:
        receipt = valid_receipt()
        receipt["software"]["paddleocr"] = "01.2.3"

        with self.assertRaisesRegex(ReceiptValidationError, "floating selector"):
            validate_receipt(receipt)

    def test_non_integer_schema_version_is_rejected(self) -> None:
        receipt = valid_receipt()
        receipt["schemaVersion"] = 1.0

        with self.assertRaisesRegex(ReceiptValidationError, "schemaVersion must be 1"):
            validate_receipt(receipt)

    def test_malformed_model_path_and_credential_assignment_are_rejected(self) -> None:
        for value in (
            "models/secret.onnx",
            "C:\\models\\secret.onnx",
            "password=secret",
            "authorization=secret",
            "authorization : secret",
            "model_path=weights.onnx",
        ):
            receipt = valid_receipt()
            receipt["run"]["configSha256"] = value

            with (
                self.subTest(value=value),
                self.assertRaisesRegex(ReceiptValidationError, "forbidden payload"),
            ):
                validate_receipt(receipt)

    def test_unsafe_artifact_path_is_rejected(self) -> None:
        receipt = valid_receipt()
        receipt["artifacts"] = [
            {
                "name": "smoke-report",
                "path": "../smoke-report.json",
                "bytes": 0,
                "sha256": "a" * 64,
                "mediaType": "application/json",
            },
        ]

        with self.assertRaisesRegex(ReceiptValidationError, "safe relative path"):
            validate_receipt(receipt)

    def test_non_pass_status_is_not_an_acceptance_receipt(self) -> None:
        receipt = valid_receipt()
        receipt["status"] = "PENDING"

        with self.assertRaisesRegex(ReceiptValidationError, "must be PASS"):
            validate_receipt(receipt)

    def test_acceptance_scope_is_rejected_until_adoption_gate(self) -> None:
        receipt = valid_receipt()
        receipt["validationScope"] = "ACCEPTANCE"

        with self.assertRaisesRegex(
            ReceiptValidationError, "validationScope must be CONTRACT_ONLY"
        ):
            validate_receipt(receipt)

    def test_boolean_schema_version_is_rejected(self) -> None:
        receipt = valid_receipt()
        receipt["schemaVersion"] = True

        with self.assertRaisesRegex(ReceiptValidationError, "schemaVersion must be 1"):
            validate_receipt(receipt)

    def test_forbidden_payload_or_log_content_is_rejected(self) -> None:
        receipt = valid_receipt()
        receipt["run"]["configSha256"] = "file:///secret"

        with self.assertRaisesRegex(ReceiptValidationError, "forbidden payload"):
            validate_receipt(receipt)

    def test_duplicate_json_key_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "receipt.json"
            path.write_text('{"schemaVersion":1,"schemaVersion":1}', encoding="utf-8")

            with self.assertRaisesRegex(ReceiptValidationError, "duplicate JSON key"):
                validate_receipt_file(path)

    def test_file_validation_does_not_bypass_contract_checks(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "receipt.json"
            receipt = valid_receipt()
            receipt["status"] = "PENDING"
            path.write_text(json.dumps(receipt), encoding="utf-8")

            with self.assertRaisesRegex(ReceiptValidationError, "must be PASS"):
                validate_receipt_file(path)

            self.assertEqual(main([str(path)]), 1)

    def test_smoke_log_content_is_scanned_when_artifacts_are_bound(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            receipt = valid_receipt()
            add_artifacts(receipt, root)
            malicious = b"authorization: Bearer secret\n"
            log_path = root / "receipts/smoke-redacted.log"
            log_path.write_bytes(malicious)
            log_artifact = next(
                item for item in receipt["artifacts"] if item["name"] == "smoke-logs"
            )
            log_artifact["bytes"] = len(malicious)
            log_artifact["sha256"] = hashlib.sha256(malicious).hexdigest()

            with self.assertRaisesRegex(ReceiptValidationError, "smoke-logs contains"):
                validate_receipt(receipt, artifact_root=root)

    def test_oversized_smoke_log_is_rejected_before_reading_content(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            receipt = valid_receipt()
            add_artifacts(receipt, root)
            oversized = b"x" * (MAX_SMOKE_LOG_BYTES + 1)
            log_path = root / "receipts/smoke-redacted.log"
            log_path.write_bytes(oversized)
            log_artifact = next(
                item for item in receipt["artifacts"] if item["name"] == "smoke-logs"
            )
            log_artifact["bytes"] = len(oversized)
            log_artifact["sha256"] = hashlib.sha256(oversized).hexdigest()

            with self.assertRaisesRegex(ReceiptValidationError, "smoke log exceeds"):
                validate_receipt(receipt, artifact_root=root)

    def test_parent_directory_symlink_is_rejected(self) -> None:
        with (
            tempfile.TemporaryDirectory() as directory,
            tempfile.TemporaryDirectory() as outside_directory,
        ):
            root = Path(directory)
            outside = Path(outside_directory) / "receipts"
            receipt = valid_receipt()
            add_artifacts(receipt, root)
            (root / "receipts").rename(outside)
            (root / "receipts").symlink_to(outside, target_is_directory=True)

            with self.assertRaisesRegex(ReceiptValidationError, "contains a symlink"):
                validate_receipt(receipt, artifact_root=root)

    def test_receipt_input_symlink_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            target = root / "target.json"
            target.write_text(json.dumps(valid_receipt()), encoding="utf-8")
            link = root / "receipt.json"
            link.symlink_to(target)

            with self.assertRaisesRegex(ReceiptValidationError, "regular non-symlink"):
                validate_receipt_file(link)

    def test_oversized_receipt_is_rejected_before_full_read(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "receipt.json"
            with path.open("wb") as stream:
                stream.truncate(MAX_RECEIPT_BYTES + 1)

            with self.assertRaisesRegex(ReceiptValidationError, "receipt exceeds"):
                validate_receipt_file(path)

    def test_symlinked_artifact_path_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            receipt = valid_receipt()
            add_artifacts(receipt, root)
            target = root / "receipts/smoke-report.json"
            target.unlink()
            target.symlink_to(root / "receipts/smoke-logs")

            with self.assertRaisesRegex(ReceiptValidationError, "contains a symlink"):
                validate_receipt(receipt, artifact_root=root)

    def test_artifact_validation_uses_descriptors_not_path_rechecks(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            receipt = valid_receipt()
            add_artifacts(receipt, root)

            with (
                patch.object(
                    Path,
                    "resolve",
                    side_effect=AssertionError("artifact validation resolved a path"),
                ),
                patch.object(
                    Path,
                    "stat",
                    side_effect=AssertionError("artifact validation stat'ed a path"),
                ),
                patch.object(
                    Path,
                    "open",
                    side_effect=AssertionError("artifact validation opened a path"),
                ),
            ):
                validate_receipt(receipt, artifact_root=root)


if __name__ == "__main__":
    unittest.main()
