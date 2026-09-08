from __future__ import annotations

import hashlib
import os
import stat
import tempfile
import unittest
from pathlib import Path

from paddle_ocr_producer_lib.contracts import ProducerValidationError, jcs_bytes
from paddle_ocr_producer_lib.evidence import (
    EVIDENCE_FILES,
    MaterializationLimits,
    create_evidence_oci_layout,
    materialize_evidence_files,
    validate_attestation_identity,
    validate_evidence_file_manifest,
    validate_producer_evidence,
    validate_release_digests,
    validate_remote_evidence_manifest,
    validate_same_run_artifact,
    verify_public_evidence,
)


def descriptor(title: str, payload: bytes, *, manifest: bool = False) -> dict[str, object]:
    return {
        "mediaType": (
            "application/vnd.bluetape4k.paddleocr.evidence.manifest.v1+json"
            if manifest
            else "application/vnd.bluetape4k.paddleocr.evidence.file.v1"
        ),
        "digest": "sha256:" + hashlib.sha256(payload).hexdigest(),
        "size": len(payload),
        "annotations": {"org.opencontainers.image.title": title},
    }


def remote_manifest() -> tuple[dict[str, object], dict[str, bytes]]:
    payloads = {path: path.encode() for path in EVIDENCE_FILES}
    payloads["evidence-manifest.json"] = b"manifest"
    layers = [descriptor("evidence-manifest.json", payloads["evidence-manifest.json"], manifest=True)]
    layers.extend(descriptor(path, payloads[path]) for path in EVIDENCE_FILES)
    return {
        "schemaVersion": 2,
        "mediaType": "application/vnd.oci.image.manifest.v1+json",
        "artifactType": "application/vnd.bluetape4k.paddleocr.producer-evidence.v1",
        "config": {
            "mediaType": "application/vnd.bluetape4k.paddleocr.evidence.config.v1+json",
            "digest": "sha256:" + "c" * 64,
            "size": 2,
        },
        "layers": layers,
        "subject": {
            "mediaType": "application/vnd.oci.image.manifest.v1+json",
            "digest": "sha256:" + "a" * 64,
            "size": len(payloads["platform-manifest.json"]),
        },
    }, payloads


def file_manifest(payloads: dict[str, bytes]) -> dict[str, object]:
    return {
        "schemaVersion": 1,
        "artifactType": "application/vnd.bluetape4k.paddleocr.producer-evidence.v1",
        "imageIndexDigest": "sha256:" + "b" * 64,
        "imagePlatformDigest": "sha256:" + "a" * 64,
        "imageConfigDigest": "sha256:" + "c" * 64,
        "baseDigest": "sha256:" + "d" * 64,
        "subjectDigest": "sha256:" + "a" * 64,
        "runId": 44,
        "runAttempt": 2,
        "attemptId": "44.2",
        "inputLockSha256": "e" * 64,
        "revocationsSha256": "f" * 64,
        "revocationsCommitSha": "1" * 40,
        "files": [
            {"path": path, "bytes": len(payloads[path]), "sha256": hashlib.sha256(payloads[path]).hexdigest()}
            for path in EVIDENCE_FILES
        ],
    }


def complete_producer_evidence() -> dict[str, object]:
    subject = "sha256:" + "a" * 64

    def artifact(path: str) -> dict[str, object]:
        return {"path": path, "bytes": 1, "sha256": "b" * 64}

    def attestation(path: str, predicate: str) -> dict[str, object]:
        return {
            **artifact(path),
            "predicateType": predicate,
            "subjectDigest": subject,
            "signer": "bluetape4k/bluetape4k-image/.github/workflows/paddleocr-producer.yml",
            "issuer": "https://token.actions.githubusercontent.com",
            "verified": True,
        }

    return {
        "schemaVersion": 1,
        "attemptId": "44.2",
        "producerStatus": "PRODUCER_PASS",
        "lastCompletedStage": "PUBLIC_EVIDENCE",
        "inputLockSha256": "e" * 64,
        "staging": artifact("staging.json"),
        "release": artifact("release.json"),
        "payload": {
            "packageLock": artifact("manifests/package-lock.json"),
            "detectorModel": artifact("manifests/model-detector.json"),
            "recognizerModel": artifact("manifests/model-recognizer.json"),
            "legalInventory": artifact("legal-inventory.json"),
            "spdx": artifact("sbom.spdx.json"),
            "provenanceAttestation": attestation(
                "attestations/provenance.bundle.jsonl", "https://slsa.dev/provenance/v1"
            ),
            "sbomAttestation": attestation(
                "attestations/sbom.bundle.jsonl", "https://spdx.dev/Document/v2.3"
            ),
        },
        "evidenceSubjectDigest": subject,
    }


class EvidenceContractTest(unittest.TestCase):
    def test_attestation_identity_and_release_digests_are_exact(self) -> None:
        subject = "sha256:" + "a" * 64
        identity = {
            "schemaVersion": 1,
            "repository": "bluetape4k/bluetape4k-image",
            "workflow": ".github/workflows/paddleocr-producer.yml",
            "workflowSha": "1" * 40,
            "ref": "refs/heads/develop",
            "environment": "paddleocr-producer",
            "issuer": "https://token.actions.githubusercontent.com",
            "audience": "sigstore",
            "runId": 44,
            "runAttempt": 2,
            "headSha": "1" * 40,
            "subjectName": "ghcr.io/bluetape4k/paddleocr-service-staging",
            "subjectDigest": subject,
            "predicateType": "https://slsa.dev/provenance/v1",
            "verified": True,
        }
        self.assertEqual(
            validate_attestation_identity(
                identity,
                expected_subject_name=identity["subjectName"],
                expected_subject_digest=subject,
                expected_predicate_type=identity["predicateType"],
                expected_run_id=44,
                expected_run_attempt=2,
                expected_head_sha="1" * 40,
            ),
            identity,
        )
        for field, value in (
            ("repository", "other/repository"),
            ("workflowSha", "2" * 40),
            ("environment", "other"),
            ("audience", "other"),
            ("runAttempt", 3),
            ("subjectDigest", "sha256:" + "b" * 64),
            ("verified", False),
        ):
            changed = dict(identity)
            changed[field] = value
            with self.subTest(field=field), self.assertRaises(ProducerValidationError):
                validate_attestation_identity(
                    changed,
                    expected_subject_name=identity["subjectName"],
                    expected_subject_digest=subject,
                    expected_predicate_type=identity["predicateType"],
                    expected_run_id=44,
                    expected_run_attempt=2,
                    expected_head_sha="1" * 40,
                )

        chain = {
            "schemaVersion": 1,
            "stagingDigest": subject,
            "releaseDigest": subject,
            "handoffDigest": subject,
            "attestationSubjectDigest": subject,
        }
        self.assertEqual(validate_release_digests(chain), chain)
        changed = dict(chain, releaseDigest="sha256:" + "b" * 64)
        with self.assertRaisesRegex(ProducerValidationError, "four-digest"):
            validate_release_digests(changed)

    def test_evidence_oci_layout_is_canonical_and_subject_bound(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            workspace = Path(temporary)
            source = workspace / "source"
            source.mkdir()
            payloads = {path: (path + "\n").encode() for path in EVIDENCE_FILES}
            for path, raw in payloads.items():
                target = source / path
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(raw)
            platform_digest = "sha256:" + hashlib.sha256(payloads["platform-manifest.json"]).hexdigest()
            output = workspace / "layout"
            receipt = create_evidence_oci_layout(
                source,
                output,
                image_index_digest="sha256:" + "b" * 64,
                image_platform_digest=platform_digest,
                image_config_digest="sha256:" + "c" * 64,
                base_digest="sha256:" + "d" * 64,
                run_id=44,
                run_attempt=2,
                input_lock_sha256="e" * 64,
                revocations_sha256="f" * 64,
                revocations_commit_sha="1" * 40,
            )
            self.assertEqual(receipt["subjectDigest"], platform_digest)
            index = __import__("json").loads((output / "index.json").read_bytes())
            root_digest = index["manifests"][0]["digest"]
            outer = __import__("json").loads(
                (output / "blobs" / "sha256" / root_digest.split(":", 1)[1]).read_bytes()
            )
            self.assertEqual(set(validate_remote_evidence_manifest(outer)), {"evidence-manifest.json", *EVIDENCE_FILES})
            self.assertEqual(outer["subject"]["digest"], platform_digest)

    def test_same_run_artifact_binding_rejects_cross_run_swap_and_rename(self) -> None:
        receipt = {
            "schemaVersion": 1,
            "kind": "MODELS",
            "artifactName": "paddleocr-models-44.2",
            "artifactId": 123,
            "artifactDigest": "sha256:" + "a" * 64,
            "runId": 44,
            "runAttempt": 2,
            "attemptId": "44.2",
            "inputLockSha256": "b" * 64,
            "contentSha256": "c" * 64,
        }
        validated = validate_same_run_artifact(
            receipt,
            expected_kind="MODELS",
            expected_artifact_name="paddleocr-models-44.2",
            expected_artifact_id=123,
            expected_artifact_digest="sha256:" + "a" * 64,
            expected_run_id=44,
            expected_run_attempt=2,
            expected_input_lock_sha256="b" * 64,
            expected_content_sha256="c" * 64,
        )
        self.assertEqual(validated, receipt)
        mutations = (
            ("schemaVersion", 2),
            ("kind", "WHEELHOUSE"),
            ("artifactName", "paddleocr-models-renamed"),
            ("artifactId", 124),
            ("artifactDigest", "sha256:" + "d" * 64),
            ("runId", 45),
            ("runAttempt", 3),
            ("attemptId", "45.2"),
            ("inputLockSha256", "e" * 64),
            ("contentSha256", "f" * 64),
        )
        for field, value in mutations:
            changed = dict(receipt)
            changed[field] = value
            with self.subTest(field=field), self.assertRaises(ProducerValidationError):
                validate_same_run_artifact(
                    changed,
                    expected_kind="MODELS",
                    expected_artifact_name="paddleocr-models-44.2",
                    expected_artifact_id=123,
                    expected_artifact_digest="sha256:" + "a" * 64,
                    expected_run_id=44,
                    expected_run_attempt=2,
                    expected_input_lock_sha256="b" * 64,
                    expected_content_sha256="c" * 64,
                )

    def test_producer_evidence_rejects_status_stage_and_attestation_drift(self) -> None:
        for mutate, message in (
            (
                lambda value: value.update({"lastCompletedStage": "RELEASE"}),
                "producerStatus",
            ),
            (
                lambda value: value["payload"]["provenanceAttestation"].update({"verified": False}),
                "verified",
            ),
            (
                lambda value: value["payload"]["sbomAttestation"].update({"subjectDigest": "sha256:" + "c" * 64}),
                "subjectDigest",
            ),
        ):
            value = complete_producer_evidence()
            mutate(value)
            with self.subTest(message=message), self.assertRaisesRegex(ProducerValidationError, message):
                validate_producer_evidence(value)

    def test_manifest_urls_foreign_layers_and_duplicate_titles_are_rejected(self) -> None:
        for mutate, message in (
            (lambda value: value["layers"][0].update({"urls": ["https://example.invalid/blob"]}), "urls"),
            (lambda value: value["layers"][1].update({"mediaType": "application/vnd.oci.image.layer.nondistributable.v1.tar"}), "mediaType"),
            (lambda value: value["layers"][1].update({
                "mediaType": "application/vnd.bluetape4k.paddleocr.evidence.manifest.v1+json",
                "annotations": {"org.opencontainers.image.title": "evidence-manifest.json"},
            }), "duplicate"),
        ):
            manifest, _ = remote_manifest()
            mutate(manifest)
            with self.subTest(message=message), self.assertRaisesRegex(ProducerValidationError, message):
                validate_remote_evidence_manifest(manifest)

    def test_exact_remote_manifest_and_file_allowlist_are_accepted(self) -> None:
        manifest, payloads = remote_manifest()
        layers = validate_remote_evidence_manifest(manifest)
        self.assertEqual(set(layers), {"evidence-manifest.json", *EVIDENCE_FILES})
        validated = validate_evidence_file_manifest(file_manifest(payloads))
        self.assertEqual(validated["attemptId"], "44.2")

    def test_file_manifest_rejects_path_escape_missing_and_unallowlisted(self) -> None:
        _, payloads = remote_manifest()
        for replacement, message in (("", "path"), ("../secret", "path"), ("other.json", "allowlist")):
            value = file_manifest(payloads)
            value["files"][0]["path"] = replacement
            with self.subTest(replacement=replacement), self.assertRaisesRegex(ProducerValidationError, message):
                validate_evidence_file_manifest(value)
        value = file_manifest(payloads)
        value["files"].pop()
        with self.assertRaisesRegex(ProducerValidationError, "exactly"):
            validate_evidence_file_manifest(value)

    def test_materializer_writes_exact_modes_and_rejects_preexisting_root(self) -> None:
        _, payloads = remote_manifest()
        manifest = file_manifest(payloads)
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary) / "evidence"
            materialize_evidence_files(
                manifest["files"], root,
                lambda digest: [next(payload for payload in payloads.values() if hashlib.sha256(payload).hexdigest() == digest)],
            )
            self.assertEqual(stat.S_IMODE(root.stat().st_mode), 0o700)
            for path in EVIDENCE_FILES:
                target = root.joinpath(*path.split("/"))
                self.assertEqual(target.read_bytes(), payloads[path])
                self.assertEqual(stat.S_IMODE(target.stat().st_mode), 0o600)
            with self.assertRaisesRegex(ProducerValidationError, "pre-existing"):
                materialize_evidence_files(manifest["files"], root, lambda _: [])

    def test_materializer_removes_partial_root_on_size_or_digest_failure(self) -> None:
        _, payloads = remote_manifest()
        manifest = file_manifest(payloads)
        with tempfile.TemporaryDirectory() as temporary:
            for suffix, reader in (
                ("size", lambda _: [b"too-long"]),
                ("digest", lambda _: [b"wrong"]),
            ):
                root = Path(temporary) / suffix
                with self.subTest(suffix=suffix), self.assertRaises(ProducerValidationError):
                    materialize_evidence_files(
                        manifest["files"], root, reader,
                        limits=MaterializationLimits(max_file_bytes=128 * 1024 * 1024),
                    )
                self.assertFalse(root.exists())

    def test_materializer_rejects_symlink_and_special_targets_before_commit(self) -> None:
        _, payloads = remote_manifest()
        manifest = file_manifest(payloads)
        with tempfile.TemporaryDirectory() as temporary:
            workspace = Path(temporary)
            root = workspace / "evidence"
            outside = workspace / "outside"
            outside.mkdir()
            reads = [0]

            def swap_parent(digest: str) -> list[bytes]:
                reads[0] += 1
                if reads[0] == 3:
                    (root / "inputs").rename(root / "inputs-moved")
                    (root / "inputs").symlink_to(outside)
                return [next(payload for payload in payloads.values() if hashlib.sha256(payload).hexdigest() == digest)]

            with self.assertRaisesRegex(ProducerValidationError, "materialized"):
                materialize_evidence_files(manifest["files"], root, swap_parent)
            self.assertFalse(root.exists())
            self.assertEqual(list(outside.iterdir()), [])

    def test_materializer_rejects_hardlink_injection(self) -> None:
        _, payloads = remote_manifest()
        manifest = file_manifest(payloads)
        with tempfile.TemporaryDirectory() as temporary:
            workspace = Path(temporary)
            root = workspace / "evidence"
            outside = workspace / "outside-link"
            first = [True]

            def hardlink_temporary(digest: str) -> list[bytes]:
                if first[0]:
                    first[0] = False
                    os.link(root / ".producer-evidence.json.partial", outside)
                return [next(payload for payload in payloads.values() if hashlib.sha256(payload).hexdigest() == digest)]

            with self.assertRaisesRegex(ProducerValidationError, "owned regular file"):
                materialize_evidence_files(manifest["files"], root, hardlink_temporary)
            self.assertFalse(root.exists())
            self.assertTrue(outside.is_file())

    def test_public_verifier_preflights_then_materializes_and_verifies_bundles(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            workspace = Path(temporary)
            oras = workspace / "oras"
            gh = workspace / "gh"
            for binary in (oras, gh):
                binary.write_bytes(b"#!/bin/sh\nexit 0\n")
                binary.chmod(0o700)
            payloads = {path: path.encode() for path in EVIDENCE_FILES}
            payloads["platform-manifest.json"] = b'{"platform":"linux/amd64"}'
            payloads["attestations/provenance.bundle.jsonl"] = b'{"bundle":"provenance"}\n'
            payloads["attestations/sbom.bundle.jsonl"] = b'{"bundle":"sbom"}\n'
            subject = "sha256:" + hashlib.sha256(payloads["platform-manifest.json"]).hexdigest()

            def file_artifact(path: str) -> dict[str, object]:
                raw = payloads[path]
                return {"path": path, "bytes": len(raw), "sha256": hashlib.sha256(raw).hexdigest()}

            artifact = file_artifact("manifests/package-lock.json")
            payloads["producer-evidence.json"] = jcs_bytes({
                "schemaVersion": 1,
                "attemptId": "44.2",
                "producerStatus": "PRODUCER_PASS",
                "lastCompletedStage": "PUBLIC_EVIDENCE",
                "inputLockSha256": "e" * 64,
                "staging": artifact,
                "release": artifact,
                "payload": {
                    "packageLock": file_artifact("manifests/package-lock.json"),
                    "detectorModel": file_artifact("manifests/model-detector.json"),
                    "recognizerModel": file_artifact("manifests/model-recognizer.json"),
                    "legalInventory": file_artifact("legal-inventory.json"),
                    "spdx": file_artifact("sbom.spdx.json"),
                    "provenanceAttestation": {
                        "path": "attestations/provenance.bundle.jsonl",
                        "bytes": len(payloads["attestations/provenance.bundle.jsonl"]),
                        "sha256": hashlib.sha256(payloads["attestations/provenance.bundle.jsonl"]).hexdigest(),
                        "predicateType": "https://slsa.dev/provenance/v1",
                        "subjectDigest": subject,
                        "signer": "bluetape4k/bluetape4k-image/.github/workflows/paddleocr-producer.yml",
                        "issuer": "https://token.actions.githubusercontent.com",
                        "verified": True,
                    },
                    "sbomAttestation": {
                        "path": "attestations/sbom.bundle.jsonl",
                        "bytes": len(payloads["attestations/sbom.bundle.jsonl"]),
                        "sha256": hashlib.sha256(payloads["attestations/sbom.bundle.jsonl"]).hexdigest(),
                        "predicateType": "https://spdx.dev/Document/v2.3",
                        "subjectDigest": subject,
                        "signer": "bluetape4k/bluetape4k-image/.github/workflows/paddleocr-producer.yml",
                        "issuer": "https://token.actions.githubusercontent.com",
                        "verified": True,
                    },
                },
                "evidenceSubjectDigest": subject,
            })
            payloads["artifact-ledger.fragment.json"] = jcs_bytes({
                "schemaVersion": 1,
                "attemptId": "44.2",
                "producerStatus": "PRODUCER_PASS",
                "lastCompletedStage": "PUBLIC_EVIDENCE",
                "mapped609Status": "PENDING",
                "inputLockSha256": "e" * 64,
                "imagePlatformDigest": subject,
                "evidenceSha256": hashlib.sha256(payloads["producer-evidence.json"]).hexdigest(),
                "reconciliationSha256": "b" * 64,
                "cleanupSha256": "c" * 64,
                "revocationsSha256": "f" * 64,
                "revocationsCommitSha": "1" * 40,
            })
            internal = file_manifest(payloads)
            internal["imagePlatformDigest"] = subject
            internal["subjectDigest"] = subject
            internal_raw = jcs_bytes(internal)
            config_raw = jcs_bytes({
                "schemaVersion": 1,
                "artifactType": "application/vnd.bluetape4k.paddleocr.producer-evidence.v1",
                "attemptId": "44.2",
                "subjectDigest": subject,
            })
            layers = [descriptor("evidence-manifest.json", internal_raw, manifest=True)]
            layers.extend(descriptor(path, payloads[path]) for path in EVIDENCE_FILES)
            outer = {
                "schemaVersion": 2,
                "mediaType": "application/vnd.oci.image.manifest.v1+json",
                "artifactType": "application/vnd.bluetape4k.paddleocr.producer-evidence.v1",
                "config": {
                    "mediaType": "application/vnd.bluetape4k.paddleocr.evidence.config.v1+json",
                    "digest": "sha256:" + hashlib.sha256(config_raw).hexdigest(),
                    "size": len(config_raw),
                },
                "layers": layers,
                "subject": {
                    "mediaType": "application/vnd.oci.image.manifest.v1+json",
                    "digest": subject,
                    "size": len(payloads["platform-manifest.json"]),
                },
            }
            outer_raw = jcs_bytes(outer)
            reference = "ghcr.io/bluetape4k/paddleocr-service@sha256:" + hashlib.sha256(outer_raw).hexdigest()
            blobs = {
                outer["config"]["digest"]: config_raw,
                **{layer["digest"]: (internal_raw if layer["annotations"]["org.opencontainers.image.title"] == "evidence-manifest.json" else payloads[layer["annotations"]["org.opencontainers.image.title"]]) for layer in layers},
            }

            def run(command: list[str], _: int, __: int) -> bytes:
                if command[1:] == ["version"]:
                    return b"Version: 1.3.4\n"
                if command[1:3] == ["manifest", "fetch"]:
                    return outer_raw
                return blobs[command[-1].split("@", 1)[1]]

            verified = []
            root = workspace / "materialized"
            result = verify_public_evidence(
                oras_bin=oras,
                gh_bin=gh,
                reference=reference,
                root=root,
                environment={"HOME": str(workspace / "home"), "DOCKER_CONFIG": str(workspace / "docker")},
                run_command=run,
                verify_attestation=lambda artifact, bundle, predicate: verified.append((artifact, bundle, predicate)),
            )
            self.assertEqual(result["manifestDigest"], reference.split("@", 1)[1])
            self.assertEqual(len(verified), 2)
            self.assertTrue((root / "evidence-manifest.json").is_file())

    def test_public_verifier_rejects_credentials_before_transport(self) -> None:
        calls = []
        with tempfile.TemporaryDirectory() as temporary:
            workspace = Path(temporary)
            oras = workspace / "oras"
            gh = workspace / "gh"
            for binary in (oras, gh):
                binary.write_bytes(b"#!/bin/sh\nexit 0\n")
                binary.chmod(0o700)
            with self.assertRaisesRegex(ProducerValidationError, "credential"):
                verify_public_evidence(
                    oras_bin=oras,
                    gh_bin=gh,
                    reference="ghcr.io/bluetape4k/paddleocr-service@sha256:" + "a" * 64,
                    root=workspace / "materialized",
                    environment={
                        "HOME": str(workspace / "home"),
                        "DOCKER_CONFIG": str(workspace / "docker"),
                        "GH_TOKEN": "secret",
                    },
                    run_command=lambda *args: calls.append(args) or b"",
                    verify_attestation=lambda *_: None,
                )
        self.assertEqual(calls, [])


if __name__ == "__main__":
    unittest.main()
