from __future__ import annotations

import hashlib
import json
import os
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))

from paddle_ocr_ledger import (
    LedgerValidationError,
    build_ledger,
    pair_binding_sha256,
    validate_ledger,
)
from paddle_ocr_producer_lib.contracts import jcs_bytes
from paddle_ocr_producer_lib.filesystem import canonical_tree_manifest

SHA_A = "a" * 64
SHA_B = "b" * 64
SHA_C = "c" * 64
SHA_D = "d" * 64
SHA_E = "e" * 64
COMMIT = "1" * 40
PLATFORM_DIGEST = "sha256:" + "f" * 64
CONFIG_DIGEST = "sha256:" + "0" * 64
BASE_DIGEST = "sha256:" + "1" * 64


def _write_json(path: Path, value: object) -> str:
    raw = jcs_bytes(value)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(raw)
    return hashlib.sha256(raw).hexdigest()


class LedgerFixture:
    def __init__(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.evidence = self.root / "evidence"
        self.models = self.root / "models"
        self.legal = self.root / "legal"
        self.policy = self.root / "trust-policy.json"
        self.ledger_path = self.root / "image-model-ledger.json"
        self.evidence.mkdir()
        self.models.mkdir()
        self.legal.mkdir()

        (self.models / "models/detector").mkdir(parents=True)
        (self.models / "models/recognizer").mkdir(parents=True)
        (self.models / "models/detector/inference.json").write_bytes(b"detector")
        (self.models / "models/detector/inference.yml").write_bytes(b"detector-yaml")
        (self.models / "models/recognizer/inference.json").write_bytes(b"recognizer")
        (self.models / "models/recognizer/inference.yml").write_bytes(b"recognizer-yaml")

        (self.legal / "legal/models").mkdir(parents=True)
        (self.legal / "legal/models/det.card").write_bytes(b"detector-license")
        (self.legal / "legal/models/det.notice").write_bytes(b"detector-notice")
        (self.legal / "legal/models/rec.card").write_bytes(b"recognizer-license")
        (self.legal / "legal/models/rec.notice").write_bytes(b"recognizer-notice")

        self.models_info = [
            self._model("detector", "detector-model", "det.card", "det.notice"),
            self._model("recognizer", "recognizer-model", "rec.card", "rec.notice"),
        ]
        self.legal_inventory = {
            "schemaVersion": 1,
            "components": [
                self._legal_component("model-detector", self.models_info[0]),
                self._legal_component("model-recognizer", self.models_info[1]),
            ],
        }
        legal_sha = _write_json(self.evidence / "legal-inventory.json", self.legal_inventory)

        self.lock = {
            "schemaVersion": 1,
            "targetPlatform": "linux/amd64",
            "baseImage": {
                "reference": "docker.io/library/python@" + BASE_DIGEST,
                "indexDigest": BASE_DIGEST,
                "platformDigest": BASE_DIGEST,
                "configDigest": CONFIG_DIGEST,
                "os": "linux",
                "architecture": "amd64",
                "variant": None,
            },
            "sources": [
                {
                    "id": "paddlex",
                    "url": "https://github.com/PaddlePaddle/PaddleX/archive/" + COMMIT + ".tar.gz",
                    "bytes": 100,
                    "sha256": SHA_A,
                    "mediaType": "application/gzip",
                    "revision": COMMIT,
                },
                {
                    "id": "paddleocr",
                    "url": "https://github.com/PaddlePaddle/PaddleOCR/archive/" + "2" * 40 + ".tar.gz",
                    "bytes": 100,
                    "sha256": SHA_C,
                    "mediaType": "application/gzip",
                    "revision": "2" * 40,
                }
            ],
            "packages": [
                {
                    "id": "paddleocr-wheel",
                    "name": "paddleocr",
                    "version": "3.7.0",
                    "filename": "paddleocr-3.7.0.whl",
                    "wheelTags": ["py3-none-any"],
                    "url": "https://pypi.org/project/paddleocr/3.7.0/",
                    "bytes": 100,
                    "sha256": SHA_B,
                    "mediaType": "application/zip",
                }
            ],
            "models": self.models_info,
            "legalInventorySha256": legal_sha,
        }
        input_lock_sha = _write_json(self.evidence / "inputs/producer-input.lock.json", self.lock)

        self.package_lock = {"schemaVersion": 1, "packages": self.lock["packages"]}
        _write_json(self.evidence / "manifests/package-lock.json", self.package_lock)

        self.platform = {
            "schemaVersion": 2,
            "mediaType": "application/vnd.oci.image.manifest.v1+json",
            "config": {
                "mediaType": "application/vnd.oci.image.config.v1+json",
                "digest": CONFIG_DIGEST,
                "size": 1,
            },
            "layers": [
                {
                    "mediaType": "application/vnd.oci.image.layer.v1.tar+gzip",
                    "digest": "sha256:" + "2" * 64,
                    "size": 1,
                }
            ],
        }
        platform_raw = jcs_bytes(self.platform)
        platform_digest = "sha256:" + hashlib.sha256(platform_raw).hexdigest()
        self.platform_digest = platform_digest
        (self.evidence / "platform-manifest.json").write_bytes(platform_raw)

        self.evidence_files = [
            "inputs/producer-input.lock.json",
            "manifests/package-lock.json",
            "manifests/model-detector.json",
            "manifests/model-recognizer.json",
            "platform-manifest.json",
            "legal-inventory.json",
        ]
        for role, model in zip(("detector", "recognizer"), self.models_info):
            _write_json(self.evidence / f"manifests/model-{role}.json", model)
        descriptors = []
        for relative in self.evidence_files:
            raw = (self.evidence / relative).read_bytes()
            descriptors.append({"path": relative, "bytes": len(raw), "sha256": hashlib.sha256(raw).hexdigest()})
        self.evidence_manifest = {
            "schemaVersion": 1,
            "artifactType": "application/vnd.bluetape4k.paddleocr.producer-evidence.v1",
            "imageIndexDigest": platform_digest,
            "imagePlatformDigest": platform_digest,
            "imageConfigDigest": CONFIG_DIGEST,
            "baseDigest": BASE_DIGEST,
            "subjectDigest": platform_digest,
            "attemptId": "42.1",
            "runId": 42,
            "runAttempt": 1,
            "inputLockSha256": input_lock_sha,
            "revocationsSha256": SHA_C,
            "revocationsCommitSha": COMMIT,
            "files": descriptors,
        }
        _write_json(self.evidence / "evidence-manifest.json", self.evidence_manifest)

        self.policy_doc = {
            "actors": ["debop"],
            "audiences": ["sigstore"],
            "hosts": ["github.com"],
            "oidcIssuers": ["https://token.actions.githubusercontent.com"],
            "refs": ["refs/heads/develop"],
            "repositories": ["bluetape4k/bluetape4k-image"],
            "runnerEnvironments": ["github-hosted"],
            "schemaVersion": 1,
            "workflows": [".github/workflows/paddleocr-producer.yml"],
        }
        _write_json(self.policy, self.policy_doc)

    def _model(self, role: str, model_id: str, license_name: str, notice_name: str) -> dict[str, object]:
        license_raw = (self.legal / "legal/models" / license_name).read_bytes()
        notice_raw = (self.legal / "legal/models" / notice_name).read_bytes()
        tree_sha = hashlib.sha256(
            canonical_tree_manifest(self.models / "models" / role)
        ).hexdigest()
        return {
            "role": role,
            "modelId": model_id,
            "modelRevision": COMMIT,
            "sourceId": "paddlex",
            "url": "https://paddle-model-ecology.bj.bcebos.com/model.tar",
            "bytes": 100,
            "sha256": SHA_D,
            "mediaType": "application/x-tar",
            "archiveType": "tar",
            "treeSha256": tree_sha,
            "licenseExpression": "Apache-2.0",
            "licenseSourcePath": "legal/models/" + license_name,
            "licenseSha256": hashlib.sha256(license_raw).hexdigest(),
            "noticePath": "legal/models/" + notice_name,
            "noticeSha256": hashlib.sha256(notice_raw).hexdigest(),
        }

    def _legal_component(self, identifier: str, model: dict[str, object]) -> dict[str, object]:
        notice = (self.legal / model["noticePath"]).read_bytes()
        return {
            "id": identifier,
            "licenseExpression": model["licenseExpression"],
            "licenseSourceUrl": "https://example.com/license",
            "licenseSha256": model["licenseSha256"],
            "notice": notice.decode(),
            "noticeSha256": hashlib.sha256(notice).hexdigest(),
        }

    def close(self) -> None:
        self.temp.cleanup()

    def build(self) -> dict[str, object]:
        return build_ledger(
            evidence_root=self.evidence,
            model_root=self.models,
            legal_root=self.legal,
            policy_path=self.policy,
            repository="bluetape4k/bluetape4k-image",
            workflow=".github/workflows/paddleocr-producer.yml",
            workflow_ref="bluetape4k/bluetape4k-image/.github/workflows/paddleocr-producer.yml@refs/heads/develop",
            workflow_run_id="42.1",
            source_revision=COMMIT,
            builder_identity="github-hosted/ubuntu-24.04",
            signer_identity="bluetape4k/bluetape4k-image/.github/workflows/paddleocr-producer.yml",
            oidc_issuer="https://token.actions.githubusercontent.com",
            image_ref="ghcr.io/bluetape4k/paddleocr-service@" + self.platform_digest,
        )

    def validate(self, ledger: dict[str, object]) -> dict[str, object]:
        return validate_ledger(
            ledger,
            evidence_root=self.evidence,
            model_root=self.models,
            legal_root=self.legal,
            policy_path=self.policy,
        )

    @staticmethod
    def refresh_checksum(ledger: dict[str, object]) -> None:
        unsigned = dict(ledger)
        unsigned.pop("checksum", None)
        ledger["checksum"] = {
            "algorithm": "SHA-256",
            "canonicalization": "JCS",
            "covers": "ledger-without-checksum",
            "sha256": hashlib.sha256(jcs_bytes(unsigned)).hexdigest(),
        }


class PaddleOcrLedgerTest(unittest.TestCase):
    def setUp(self) -> None:
        self.fixture = LedgerFixture()

    def tearDown(self) -> None:
        self.fixture.close()

    def test_build_and_validate_recomputes_model_tree_and_checksum(self) -> None:
        ledger = self.fixture.build()
        result = self.fixture.validate(ledger)
        self.assertEqual(result["status"], "PASS")
        self.assertEqual(ledger["status"], "PASS")
        self.assertTrue(ledger["verification"]["modelTreeRecomputed"])
        self.assertTrue(ledger["verification"]["pairBindingMatches"])
        self.assertEqual(ledger["scope"]["gate"], "609-C")
        self.assertEqual(ledger["model"]["pairBindingSha256"], pair_binding_sha256(ledger["model"]["models"]))

    def test_model_file_tamper_fails_closed(self) -> None:
        ledger = self.fixture.build()
        (self.fixture.models / "models/detector/inference.json").write_bytes(b"tampered")
        with self.assertRaisesRegex(LedgerValidationError, "model tree|model file"):
            self.fixture.validate(ledger)

    def test_platform_manifest_tamper_fails_closed(self) -> None:
        ledger = self.fixture.build()
        platform = json.loads((self.fixture.evidence / "platform-manifest.json").read_text())
        platform["layers"][0]["size"] = 2
        (self.fixture.evidence / "platform-manifest.json").write_bytes(jcs_bytes(platform))
        with self.assertRaisesRegex(LedgerValidationError, "platform manifest|evidence file"):
            self.fixture.validate(ledger)

    def test_symlink_model_file_fails_closed(self) -> None:
        ledger = self.fixture.build()
        path = self.fixture.models / "models/recognizer/inference.yml"
        path.unlink()
        os.symlink("inference.json", path)
        with self.assertRaisesRegex(LedgerValidationError, "symlink|regular"):
            self.fixture.validate(ledger)

    def test_trust_membership_fails_closed(self) -> None:
        ledger = self.fixture.build()
        ledger["producer"]["workflowRef"] = ledger["producer"]["workflowRef"].replace("develop", "main")
        self.fixture.refresh_checksum(ledger)
        with self.assertRaisesRegex(LedgerValidationError, "workflowRef|trust"):
            self.fixture.validate(ledger)

    def test_checksum_and_unknown_key_fail_closed(self) -> None:
        ledger = self.fixture.build()
        ledger["unexpected"] = True
        with self.assertRaisesRegex(LedgerValidationError, "unexpected|checksum"):
            self.fixture.validate(ledger)

    def test_pair_binding_role_order_is_fixed(self) -> None:
        models = [
            {"role": "recognizer", "treeSha256": SHA_B},
            {"role": "detector", "treeSha256": SHA_A},
        ]
        expected = hashlib.sha256(
            f"detector\n{SHA_A}\nrecognizer\n{SHA_B}\n".encode()
        ).hexdigest()
        self.assertEqual(pair_binding_sha256(models), expected)


if __name__ == "__main__":
    unittest.main()
