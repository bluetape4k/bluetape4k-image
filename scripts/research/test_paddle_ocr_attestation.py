from __future__ import annotations

import base64
import copy
import json
import unittest

from paddle_ocr_attestation import (
    AttestationValidationError,
    _descriptor,
    validate_attestation_pair,
    validate_gh_attestation_result,
)

REPOSITORY = "bluetape4k/bluetape4k-image"
WORKFLOW = ".github/workflows/paddleocr-producer.yml"
REF = "refs/heads/develop"
WORKFLOW_SHA = "3" * 40
RUN_ID = 34258892748
RUN_ATTEMPT = 1
SUBJECT_NAME = "ghcr.io/bluetape4k/paddleocr-service"
SUBJECT_DIGEST = "sha256:" + "d" * 64
ISSUER = "https://token.actions.githubusercontent.com"
PROVENANCE = "https://slsa.dev/provenance/v1"
SBOM = "https://spdx.dev/Document/v2.3"


def _statement(predicate_type: str) -> dict[str, object]:
    return {
        "_type": "https://in-toto.io/Statement/v1",
        "subject": [
            {"name": SUBJECT_NAME, "digest": {"sha256": SUBJECT_DIGEST[7:]}}
        ],
        "predicateType": predicate_type,
        "predicate": {},
    }


def _bundle(predicate_type: str) -> bytes:
    payload = json.dumps(
        _statement(predicate_type), separators=(",", ":"), sort_keys=True
    ).encode()
    document = {
        "mediaType": "application/vnd.dev.sigstore.bundle.v0.3+json",
        "verificationMaterial": {
            "certificate": {"rawBytes": "certificate"},
            "tlogEntries": [{"logIndex": "1"}],
            "timestampVerificationData": {
                "rfc3161Timestamps": [{"signedTimestamp": "timestamp"}]
            },
        },
        "dsseEnvelope": {
            "payload": base64.b64encode(payload).decode(),
            "payloadType": "application/vnd.in-toto+json",
            "signatures": [{"sig": "signature", "keyid": ""}],
        },
    }
    return (json.dumps(document, separators=(",", ":")) + "\n").encode()


def _certificate() -> dict[str, str]:
    workflow_uri = f"https://github.com/{REPOSITORY}/{WORKFLOW}@{REF}"
    repository_uri = f"https://github.com/{REPOSITORY}"
    invocation = f"{repository_uri}/actions/runs/{RUN_ID}/attempts/{RUN_ATTEMPT}"
    return {
        "certificateIssuer": "CN=sigstore-intermediate,O=sigstore.dev",
        "subjectAlternativeName": workflow_uri,
        "issuer": ISSUER,
        "githubWorkflowTrigger": "workflow_dispatch",
        "githubWorkflowSHA": WORKFLOW_SHA,
        "githubWorkflowName": "PaddleOCR trusted producer",
        "githubWorkflowRepository": REPOSITORY,
        "githubWorkflowRef": REF,
        "buildSignerURI": workflow_uri,
        "buildSignerDigest": WORKFLOW_SHA,
        "runnerEnvironment": "github-hosted",
        "sourceRepositoryURI": repository_uri,
        "sourceRepositoryDigest": WORKFLOW_SHA,
        "sourceRepositoryRef": REF,
        "sourceRepositoryIdentifier": "123",
        "sourceRepositoryOwnerURI": "https://github.com/bluetape4k",
        "sourceRepositoryOwnerIdentifier": "456",
        "buildConfigURI": workflow_uri,
        "buildConfigDigest": WORKFLOW_SHA,
        "buildTrigger": "workflow_dispatch",
        "runInvocationURI": invocation,
        "sourceRepositoryVisibilityAtSigning": "public",
    }


def _gh_result(predicate_type: str, *, bundle: bytes | None = None) -> tuple[list[object], bytes]:
    local_bundle = bundle or _bundle(predicate_type)
    local = json.loads(local_bundle.decode().splitlines()[0])
    statement = json.loads(
        base64.b64decode(local["dsseEnvelope"]["payload"], validate=True)
    )
    result = [
        {
            "attestation": {
                "bundle": {
                    "mediaType": local["mediaType"],
                    "verificationMaterial": local["verificationMaterial"],
                    "dsseEnvelope": {
                        "payload": local["dsseEnvelope"]["payload"],
                        "payloadType": local["dsseEnvelope"]["payloadType"],
                        "signatures": [
                            {"sig": local["dsseEnvelope"]["signatures"][0]["sig"]}
                        ],
                    },
                },
                "bundle_url": "https://github.com/bundle",
                "initiator": "github-actions",
            },
            "verificationResult": {
                "mediaType": "application/vnd.dev.sigstore.verificationresult+json;version=0.1",
                "signature": {"certificate": _certificate()},
                "verifiedTimestamps": [
                    {
                        "type": "Tlog",
                        "uri": "https://rekor.sigstore.dev",
                        "timestamp": "2026-09-09T02:51:09+09:00",
                    }
                ],
                "verifiedIdentity": {
                    "subjectAlternativeName": {
                        "subjectAlternativeName": "",
                        "regexp": ".*",
                    },
                    "issuer": {"issuer": "", "regexp": ".*"},
                    "runnerEnvironment": "github-hosted",
                },
                "statement": statement,
            },
        }
    ]
    return result, local_bundle


def _expected() -> dict[str, object]:
    return {
        "expected_subject_name": SUBJECT_NAME,
        "expected_subject_digest": SUBJECT_DIGEST,
        "expected_predicate_type": PROVENANCE,
        "expected_repository": REPOSITORY,
        "expected_workflow": WORKFLOW,
        "expected_ref": REF,
        "expected_workflow_sha": WORKFLOW_SHA,
        "expected_run_id": RUN_ID,
        "expected_run_attempt": RUN_ATTEMPT,
    }


class AttestationContractTest(unittest.TestCase):
    def test_valid_result_is_bound_to_the_local_bundle_and_normalized(self) -> None:
        value, bundle = _gh_result(PROVENANCE)
        normalized = validate_gh_attestation_result(
            value, bundle_raw=bundle, **_expected()
        )
        self.assertEqual(normalized["subjectDigest"], SUBJECT_DIGEST)
        self.assertEqual(normalized["predicateType"], PROVENANCE)
        self.assertEqual(normalized["workflowSha"], WORKFLOW_SHA)
        self.assertEqual(normalized["runId"], RUN_ID)
        self.assertEqual(normalized["runAttempt"], RUN_ATTEMPT)
        self.assertEqual(normalized["timestampCount"], 1)
        self.assertEqual(normalized["signatureCount"], 1)
        self.assertEqual(normalized["bundleBytes"], len(bundle))

    def test_pair_requires_two_distinct_predicates_with_one_subject(self) -> None:
        provenance, provenance_bundle = _gh_result(PROVENANCE)
        sbom, sbom_bundle = _gh_result(SBOM)
        result = validate_attestation_pair(
            provenance,
            sbom,
            provenance_bundle_raw=provenance_bundle,
            sbom_bundle_raw=sbom_bundle,
            **{**_expected(), "expected_sbom_predicate_type": SBOM},
        )
        self.assertTrue(result["sameSubject"])
        self.assertEqual(result["subjectDigest"], SUBJECT_DIGEST)
        self.assertEqual(
            [item["predicateType"] for item in result["attestations"]],
            [PROVENANCE, SBOM],
        )

    def test_rejects_multiple_verification_results(self) -> None:
        value, bundle = _gh_result(PROVENANCE)
        with self.assertRaises(AttestationValidationError):
            validate_gh_attestation_result(
                value + copy.deepcopy(value), bundle_raw=bundle, **_expected()
            )

    def test_rejects_subject_digest_mismatch(self) -> None:
        value, bundle = _gh_result(PROVENANCE)
        value[0]["verificationResult"]["statement"]["subject"][0]["digest"][
            "sha256"
        ] = "e" * 64
        with self.assertRaises(AttestationValidationError):
            validate_gh_attestation_result(
                value, bundle_raw=bundle, **_expected()
            )

    def test_rejects_signer_source_ref_or_runner_drift(self) -> None:
        for field, replacement in (
            ("buildSignerDigest", "4" * 40),
            ("sourceRepositoryRef", "refs/heads/feature"),
            ("runnerEnvironment", "self-hosted"),
            ("issuer", "https://issuer.invalid"),
            ("runInvocationURI", "https://github.com/run/other"),
        ):
            value, bundle = _gh_result(PROVENANCE)
            value[0]["verificationResult"]["signature"]["certificate"][field] = replacement
            with self.subTest(field=field), self.assertRaises(AttestationValidationError):
                validate_gh_attestation_result(
                    value, bundle_raw=bundle, **_expected()
                )

    def test_rejects_missing_verified_timestamp(self) -> None:
        value, bundle = _gh_result(PROVENANCE)
        value[0]["verificationResult"]["verifiedTimestamps"] = []
        with self.assertRaises(AttestationValidationError):
            validate_gh_attestation_result(
                value, bundle_raw=bundle, **_expected()
            )

    def test_rejects_identity_policy_regex_that_excludes_expected_workflow(self) -> None:
        value, bundle = _gh_result(PROVENANCE)
        value[0]["verificationResult"]["verifiedIdentity"][
            "subjectAlternativeName"
        ]["regexp"] = r"^https://github.com/other/repository/"
        with self.assertRaises(AttestationValidationError):
            validate_gh_attestation_result(
                value, bundle_raw=bundle, **_expected()
            )

    def test_rejects_bundle_payload_swap_even_when_statement_is_valid(self) -> None:
        value, bundle = _gh_result(PROVENANCE)
        swapped, _ = _gh_result(PROVENANCE, bundle=_bundle(SBOM))
        value[0]["attestation"]["bundle"]["dsseEnvelope"]["payload"] = swapped[0][
            "attestation"
        ]["bundle"]["dsseEnvelope"]["payload"]
        with self.assertRaises(AttestationValidationError):
            validate_gh_attestation_result(
                value, bundle_raw=bundle, **_expected()
            )

    def test_rejects_unknown_top_level_field(self) -> None:
        value, bundle = _gh_result(PROVENANCE)
        value[0]["unexpected"] = True
        with self.assertRaises(AttestationValidationError):
            validate_gh_attestation_result(
                value, bundle_raw=bundle, **_expected()
            )

    def test_rejects_unsafe_evidence_descriptor_paths(self) -> None:
        for path in ("../outside", "/absolute", "nested//file", "nested/./file", "nested\\file"):
            with self.subTest(path=path), self.assertRaises(AttestationValidationError):
                _descriptor(
                    {"path": path, "bytes": 1, "sha256": "a" * 64},
                    "evidence descriptor",
                )


if __name__ == "__main__":
    unittest.main()
