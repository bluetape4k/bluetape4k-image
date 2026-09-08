from __future__ import annotations

import os
import re
import subprocess
import textwrap
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / ".github/workflows/paddleocr-producer.yml"
CI = ROOT / ".github/workflows/ci.yml"
CODEOWNERS = ROOT / ".github/CODEOWNERS"

ALLOWED_ACTIONS = {
    "actions/checkout": "3d3c42e5aac5ba805825da76410c181273ba90b1",
    "actions/setup-python": "5fda3b95a4ea91299a34e894583c3862153e4b97",
    "actions/upload-artifact": "043fb46d1a93c77aae656e7c1c64a875d1fc6a0a",
    "actions/download-artifact": "3e5f45b2cfb9172054b4087a40e8e0b5a5461e7c",
    "actions/attest-build-provenance": "977bb373ede98d70efdf65b84cb5f73e068dcc2a",
    "actions/attest-sbom": "4651f806c01d8637787e274ac3bdf724ef169f34",
}

EXPECTED_TIMEOUTS = {
    "validation": 15,
    "staging": 90,
    "source-repro-check": 90,
    "image-build": 90,
    "staging-push": 15,
    "staging-attest": 15,
    "staging-readback": 15,
    "release-promotion": 15,
    "release-attest": 15,
    "release-readback": 15,
    "release-evidence-push": 15,
    "consumer-verify-private": 15,
    "public-visibility-readback": 15,
    "consumer-verify-public": 15,
    "reconcile-readback": 15,
    "emergency-deny-attest": 15,
    "cleanup-aggregate": 15,
    "release-readback-finalize": 15,
}

READ = {"contents": "read"}
EXPECTED_PERMISSIONS = {
    "validation": READ,
    "staging": READ,
    "source-repro-check": READ,
    "image-build": {"contents": "read", "actions": "read"},
    "staging-push": {"contents": "read", "actions": "read", "packages": "write"},
    "staging-attest": {
        "contents": "read", "actions": "read", "packages": "read",
        "attestations": "write", "id-token": "write",
    },
    "staging-readback": {
        "contents": "read", "packages": "read", "attestations": "read",
    },
    "release-promotion": {"contents": "read", "packages": "write"},
    "release-attest": {
        "contents": "read", "actions": "read", "packages": "read",
        "attestations": "write", "id-token": "write",
    },
    "release-readback": {
        "contents": "read", "packages": "read", "attestations": "read",
    },
    "release-evidence-push": {
        "contents": "read", "actions": "read", "packages": "write",
    },
    "consumer-verify-private": {"contents": "read", "packages": "read"},
    "public-visibility-readback": {"contents": "read", "packages": "read"},
    "consumer-verify-public": READ,
    "reconcile-readback": {
        "contents": "read", "actions": "read", "packages": "read",
        "attestations": "read",
    },
    "emergency-deny-attest": READ,
    "cleanup-aggregate": {"contents": "read", "actions": "read"},
    "release-readback-finalize": {
        "contents": "read", "actions": "read", "packages": "read",
        "attestations": "read",
    },
}

EXPECTED_CODEOWNERS = {
    "/.github/workflows/paddleocr-producer.yml @debop",
    "/.github/workflows/ci.yml @debop",
    "/.github/CODEOWNERS @debop",
    "/.github/scripts/test-paddleocr-producer-workflow.py @debop",
    "/docker/paddleocr/ @debop",
    "/scripts/research/paddle_ocr_producer.py @debop",
    "/scripts/research/paddle_ocr_producer_lib/ @debop",
    "/scripts/research/test_paddle_ocr_producer*.py @debop",
    "/scripts/research/paddle_ocr_smoke.py @debop",
    "/scripts/research/test_paddle_ocr_smoke.py @debop",
    "/docs/superpowers/runbooks/2026-09-07-issue-638-paddleocr-producer.md @debop",
}


def job_blocks(text: str) -> dict[str, str]:
    if "\njobs:\n" not in text:
        return {}
    jobs = text.split("\njobs:\n", 1)[1]
    matches = list(re.finditer(r"(?m)^  ([a-z][a-z0-9-]*):\n", jobs))
    return {
        match.group(1): jobs[match.start(): matches[index + 1].start() if index + 1 < len(matches) else len(jobs)]
        for index, match in enumerate(matches)
    }


def job_permissions(block: str) -> dict[str, str]:
    match = re.search(r"(?m)^    permissions:\n((?:^      [a-z-]+: (?:read|write)\n)+)", block)
    if match is None:
        return {}
    return dict(re.findall(r"(?m)^      ([a-z-]+): (read|write)$", match.group(1)))


def job_needs(block: str) -> set[str]:
    match = re.search(r"(?m)^    needs: \[([^]]+)]$", block)
    return set() if match is None else {item.strip() for item in match.group(1).split(",")}


def expanded_steps(block: str, shared_steps: str) -> str:
    if "steps: *producer-steps" in block:
        block = shared_steps
    aliases = {
        "- &trust-step\n        name: Validate trust context": "- name: Validate trust context",
        "- &cleanup-step\n        name: Merge cleanup aggregate": "- name: Merge cleanup aggregate",
        "- &summary-step\n        name: Write step summary": "- name: Write step summary",
        "- &terminal-step\n        name: Emit terminal result": "- name: Emit terminal result",
        "- &cleanup-fragment-step\n        name: Write cleanup fragment": "- name: Write cleanup fragment",
        "- &cleanup-upload-step\n        name: Upload cleanup fragment": "- name: Upload cleanup fragment",
        "- *trust-step": "- name: Validate trust context",
        "- *cleanup-step": "- name: Merge cleanup aggregate",
        "- *summary-step": "- name: Write step summary",
        "- *terminal-step": "- name: Emit terminal result",
        "- *cleanup-fragment-step": "- name: Write cleanup fragment",
        "- *cleanup-upload-step": "- name: Upload cleanup fragment\n          cleanup-fragment-${{ env.ATTEMPT_ID }}-${{ env.PRODUCER_JOB }}\n          retention-days: 90",
    }
    for alias, expanded in aliases.items():
        block = block.replace(alias, expanded)
    return block


class ProducerWorkflowContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.workflow = WORKFLOW.read_text(encoding="utf-8")
        cls.blocks = job_blocks(cls.workflow)
        cls.ci = CI.read_text(encoding="utf-8")

    def test_manual_dispatch_inputs_and_serialization_are_exact(self) -> None:
        event_block = self.workflow.split("\npermissions:", 1)[0]
        self.assertIn("on:\n  workflow_dispatch:\n    inputs:", event_block)
        for forbidden in ("pull_request:", "push:", "schedule:", "repository_dispatch:"):
            self.assertNotIn(forbidden, event_block)
        self.assertRegex(event_block, r"(?s)mode:.*type: choice.*options:\n          - PRODUCE\n          - RECONCILE")
        for name in (
            "resumeAttemptId", "expectedPriorStatus", "expectedInputLockSha256",
            "expectedStagingDigest", "expectedReleaseDigest", "expectedEvidenceDigest",
        ):
            self.assertRegex(event_block, rf"(?ms)^      {name}:\n.*?required: true\n.*?default: 'NONE'", name)
        self.assertIn("permissions: {}", self.workflow)
        self.assertIn("group: paddleocr-producer-${{ github.repository }}-linux-amd64", self.workflow)
        self.assertIn("cancel-in-progress: false", self.workflow)

    def test_job_graph_permissions_timeouts_and_runner_are_exact(self) -> None:
        self.assertEqual(set(self.blocks), set(EXPECTED_TIMEOUTS))
        for job, timeout in EXPECTED_TIMEOUTS.items():
            with self.subTest(job=job):
                block = self.blocks[job]
                self.assertIn("runs-on: ubuntu-24.04", block)
                self.assertIn(f"timeout-minutes: {timeout}", block)
                self.assertEqual(job_permissions(block), EXPECTED_PERMISSIONS[job])
                self.assertNotIn("contents: write", block)

    def test_actions_are_verified_full_sha_pins_and_checkout_drops_credentials(self) -> None:
        uses = re.findall(r"(?m)^\s+(?:- )?uses: ([^\s]+)$", self.workflow)
        self.assertTrue(uses)
        for value in uses:
            with self.subTest(uses=value):
                repository, separator, revision = value.partition("@")
                self.assertEqual(separator, "@")
                self.assertEqual(revision, ALLOWED_ACTIONS.get(repository))
                self.assertRegex(revision, r"\A[0-9a-f]{40}\Z")
        for match in re.finditer(r"(?m)^\s+(?:- )?uses: actions/checkout@[0-9a-f]{40}\n", self.workflow):
            following = self.workflow[match.end(): match.end() + 180]
            self.assertRegex(following, r"(?m)^\s+with:\n\s+persist-credentials: false$")

    def test_trust_duplicate_cleanup_summary_and_terminal_order_is_fail_closed(self) -> None:
        privileged = {
            job for job, permissions in EXPECTED_PERMISSIONS.items()
            if permissions.get("packages") == "write" or permissions.get("id-token") == "write"
        }
        shared_steps = self.blocks["validation"]
        for job, block in self.blocks.items():
            with self.subTest(job=job):
                if job in {"cleanup-aggregate", "release-readback-finalize"}:
                    continue
                steps = expanded_steps(block, shared_steps)
                self.assertIn("- name: Validate trust context", steps)
                cleanup = steps.index("- name: Merge cleanup aggregate")
                summary = steps.index("- name: Write step summary")
                terminal = steps.index("- name: Emit terminal result")
                self.assertLess(cleanup, summary)
                self.assertLess(summary, terminal)
                if job in privileged:
                    trust = steps.index("- name: Validate trust context")
                    side_effect = steps.index("- name: Execute producer stage")
                    self.assertLess(trust, side_effect)
        validation = self.blocks["validation"]
        self.assertIn("PRODUCER_JOB_STATUS: ${{ job.status }}", validation)
        self.assertIn(
            '> .producer-state/step-summary-result.json',
            validation,
        )
        terminal = validation.split("name: Emit terminal result", 1)[1]
        self.assertIn(".producer-state/step-summary-result.json", terminal)
        self.assertNotIn("GITHUB_STEP_SUMMARY", terminal)
        self.assertIn("Execute producer stage - validate immutable inputs", validation)
        self.assertIn(" validate-inputs ", validation)
        self.assertIn(" verify-dockerfile ", validation)
        self.assertNotIn("exit 10", validation)
        self.assertNotIn("후속 구현", validation)
        self.assertNotIn("secrets.", self.workflow)

    def test_finalizer_reconcile_and_quarantine_order_is_fail_closed(self) -> None:
        stages = set(EXPECTED_TIMEOUTS) - {"cleanup-aggregate", "release-readback-finalize"}
        shared = self.blocks["validation"]
        self.assertRegex(shared, r"(?s)name: Write cleanup fragment.*?if: \$\{\{ always\(\) \}\}")
        self.assertRegex(shared, r"(?s)name: Upload cleanup fragment.*?if: \$\{\{ always\(\) \}\}")
        for job in stages:
            with self.subTest(job=job):
                steps = expanded_steps(self.blocks[job], shared)
                self.assertIn("cleanup-fragment-${{ env.ATTEMPT_ID }}-${{ env.PRODUCER_JOB }}", steps)
                self.assertIn("retention-days: 90", steps)
        cleanup = self.blocks["cleanup-aggregate"]
        self.assertEqual(job_needs(cleanup), stages)
        self.assertIn("if: ${{ always() }}", cleanup)
        self.assertIn("merge-cleanup", cleanup)
        self.assertIn("pattern: cleanup-fragment-${{ env.ATTEMPT_ID }}-*", cleanup)
        finalizer = self.blocks["release-readback-finalize"]
        self.assertEqual(job_needs(finalizer), {
            "staging-push", "release-promotion", "release-evidence-push",
            "consumer-verify-public", "reconcile-readback",
            "emergency-deny-attest", "cleanup-aggregate",
        })
        self.assertIn("if: ${{ always() }}", finalizer)
        self.assertLess(finalizer.index("download-artifact@"), finalizer.index(" finalize-run "))
        emergency = self.blocks["emergency-deny-attest"]
        self.assertEqual(job_needs(emergency), {"consumer-verify-public"})
        self.assertIn("QUARANTINE_PENDING", emergency)
        self.assertIn("paddleocr-quarantine-pending-${{ env.ATTEMPT_ID }}", emergency)
        self.assertNotIn("verify-emergency-receipt", emergency)
        self.assertNotIn("id-token: write", emergency)
        reconcile = self.blocks["reconcile-readback"]
        self.assertEqual(job_needs(reconcile), {"validation"})
        self.assertIn("inputs.mode == 'RECONCILE'", reconcile)
        for field in (
            "resumeAttemptId", "expectedPriorStatus", "expectedInputLockSha256",
            "expectedStagingDigest", "expectedReleaseDigest", "expectedEvidenceDigest",
        ):
            self.assertIn(f"inputs.{field}", reconcile)
        for command in (
            "readback-workflow-run",
            "readback-packages",
            "validate-reconcile-state",
        ):
            self.assertIn(command, reconcile)
        self.assertIn("-type l -print -quit", reconcile)
        self.assertIn("-le 1048576", reconcile)
        for forbidden in ("docker build", "oras push", "oras cp", "packages: write", "id-token: write"):
            self.assertNotIn(forbidden, reconcile.lower())
        for job in ("staging", "source-repro-check", "image-build", "staging-push", "staging-attest", "release-promotion", "release-attest", "release-evidence-push"):
            self.assertIn("inputs.mode == 'PRODUCE'", self.blocks[job], job)
        self.assertNotIn("issues: write", self.workflow)
        for forbidden in ("visibility public", "visibility private", "delete-package-version", "execute-known-good-rollback"):
            self.assertNotIn(forbidden, self.workflow)
        self.assertIn("paddleocr-reconcile-state-${{ env.ATTEMPT_ID }}", finalizer)
        self.assertIn('= 5', reconcile)
        for path in (
            "producer-result.json",
            "evidence-reference.json",
            "reconciliation-readback.json",
            "cleanup.json",
        ):
            self.assertIn(path, reconcile)
            self.assertIn(path, finalizer)
        self.assertIn('NEEDS_JSON: ${{ toJSON(needs) }}', cleanup)

    def test_unprivileged_build_and_private_push_use_same_run_artifacts(self) -> None:
        self.assertEqual(
            job_needs(self.blocks["image-build"]),
            {"validation", "staging", "source-repro-check"},
        )
        self.assertEqual(
            job_needs(self.blocks["staging-push"]),
            {"validation", "source-repro-check", "image-build"},
        )
        self.assertIn("environment: paddleocr-producer", self.blocks["staging-push"])
        self.assertIn("paddleocr-models-${{ env.ATTEMPT_ID }}", self.workflow)
        self.assertIn("paddleocr-wheelhouse-${{ env.ATTEMPT_ID }}", self.workflow)
        self.assertIn("paddleocr-oci-${{ env.ATTEMPT_ID }}", self.workflow)
        image_build = self.blocks["image-build"]
        self.assertNotRegex(image_build, r"(?i)(docker\s+push|oras\s+(?:push|copy)|packages:\s+write)")
        for forbidden in ("cache-from", "cache-to", "type=gha", "type=registry"):
            self.assertNotIn(forbidden, self.workflow)
        self.assertIn("--network=none", image_build)
        self.assertIn("--no-cache", image_build)
        self.assertIn('--build-arg "SOURCE_DATE_EPOCH=$SOURCE_DATE_EPOCH"', image_build)
        self.assertIn("--build-receipt .producer-state/build-receipt.json", image_build)
        push = self.blocks["staging-push"]
        for output in ("artifact-id", "artifact-digest", "content-sha256"):
            self.assertIn(f"needs.staging.outputs.{output}", image_build)
            self.assertIn(f"needs.source-repro-check.outputs.{output}", image_build)
            self.assertIn(f"needs.image-build.outputs.{output}", push)
        self.assertLess(push.index("validate-oci-artifact"), push.index('"$ORAS_BIN" cp'))
        self.assertLess(push.index('"$ORAS_BIN" manifest fetch'), push.index("staging-digest=%s"))
        self.assertIn("packages/container/paddleocr-service-staging --jq .visibility", push)
        self.assertIn('"private"', push)
        self.assertIn('"registry-config.json"', self.blocks["validation"])

    def test_attestation_promotion_and_public_evidence_are_digest_bound(self) -> None:
        staging_attest = self.blocks["staging-attest"]
        self.assertEqual(job_needs(staging_attest), {"staging-push"})
        self.assertIn("actions/attest-build-provenance@", staging_attest)
        self.assertIn("actions/attest-sbom@", staging_attest)
        self.assertEqual(
            staging_attest.count("          subject-digest: ${{ needs.staging-push.outputs.image-platform-digest }}"),
            2,
        )
        self.assertIn("paddleocr-staging-attestations-${{ env.ATTEMPT_ID }}", staging_attest)

        staging_readback = self.blocks["staging-readback"]
        for required in (
            "gh attestation verify",
            '--signer-digest "$GITHUB_SHA"',
            '--source-digest "$GITHUB_SHA"',
            '--source-ref "$GITHUB_REF"',
            "repos/$GITHUB_REPOSITORY/attestations/sha256:",
            "validate-attestation-identity",
        ):
            self.assertIn(required, staging_readback)

        promotion = self.blocks["release-promotion"]
        self.assertEqual(job_needs(promotion), {"staging-readback"})
        self.assertIn('"$ORAS_BIN" cp', promotion)
        self.assertIn("validate-release-digests", promotion)
        self.assertNotIn("id-token", promotion)

        release_attest = self.blocks["release-attest"]
        self.assertEqual(job_needs(release_attest), {"release-promotion"})
        self.assertEqual(
            release_attest.count("          subject-digest: ${{ needs.release-promotion.outputs.release-digest }}"),
            2,
        )
        self.assertIn("paddleocr-release-attestations-${{ env.ATTEMPT_ID }}", release_attest)

        evidence_push = self.blocks["release-evidence-push"]
        self.assertEqual(job_needs(evidence_push), {"release-readback"})
        self.assertIn("create-evidence-oci", evidence_push)
        for path in (
            "producer-evidence.json",
            "artifact-ledger.fragment.json",
            "inputs/producer-input.lock.json",
            "manifests/package-lock.json",
            "manifests/model-detector.json",
            "manifests/model-recognizer.json",
            "platform-manifest.json",
            "sbom.spdx.json",
            "legal-inventory.json",
            "attestations/provenance.bundle.jsonl",
            "attestations/sbom.bundle.jsonl",
        ):
            self.assertIn(path, evidence_push)

        self.assertEqual(job_needs(self.blocks["consumer-verify-private"]), {"release-evidence-push"})
        self.assertEqual(job_needs(self.blocks["public-visibility-readback"]), {"consumer-verify-private"})
        self.assertIn(
            "environment: paddleocr-producer",
            self.blocks["public-visibility-readback"],
        )
        public = self.blocks["consumer-verify-public"]
        self.assertEqual(job_needs(public), {"public-visibility-readback"})
        self.assertIn("verify-public-evidence", public)
        for forbidden in (
            "GH_TOKEN:", "GITHUB_TOKEN:", "${{ github.token }}", "registry-config", "oras login",
        ):
            self.assertNotIn(forbidden, public)

    def test_ci_routes_every_producer_surface_to_credential_free_matrix(self) -> None:
        self.assertIn("paddleocr-producer: ${{ steps.filter.outputs.paddleocr-producer }}", self.ci)
        for path in (
            "docker/paddleocr/**",
            "scripts/research/paddle_ocr_producer.py",
            "scripts/research/paddle_ocr_producer_lib/**",
            "scripts/research/test_paddle_ocr_producer*.py",
            "scripts/research/paddle_ocr_smoke.py",
            "scripts/research/test_paddle_ocr_smoke.py",
            ".github/scripts/test-paddleocr-producer-workflow.py",
            ".github/workflows/paddleocr-producer.yml",
            ".github/CODEOWNERS",
            ".github/workflows/ci.yml",
        ):
            self.assertIn(f"- '{path}'", self.ci)
        producer = job_blocks("\njobs:\n" + self.ci)["producer-contract"]
        self.assertIn("matrix:\n        python-version: ['3.9', '3.13']", producer)
        self.assertIn("persist-credentials: false", producer)
        self.assertNotIn("secrets.", producer)
        self.assertNotIn("docker build", producer.lower())
        self.assertNotIn("docker push", producer.lower())
        for command in (
            "test_paddle_ocr_producer.py -v",
            "test_paddle_ocr_producer_registry.py -v",
            "test_paddle_ocr_producer_evidence.py -v",
            "test_paddle_ocr_receipt.py",
            "test_paddle_ocr_smoke.py",
            "test-paddleocr-producer-workflow.py",
        ):
            self.assertIn(command, producer)
        self.assertIn("- producer-contract", self.blocks_from_ci_status())
        self.assertIn("PADDLEOCR_PRODUCER_CHANGED:", self.blocks_from_ci_status())
        self.assertIn('require_test "paddleocr-producer"', self.blocks_from_ci_status())

    def blocks_from_ci_status(self) -> str:
        return job_blocks("\njobs:\n" + self.ci)["ci-status"]

    def test_codeowners_exactly_covers_the_producer_control_surface(self) -> None:
        actual = {
            line for line in CODEOWNERS.read_text(encoding="utf-8").splitlines()
            if line and not line.startswith("#")
        }
        self.assertEqual(actual, EXPECTED_CODEOWNERS)

    def test_ci_status_rejects_required_skip_and_matrix_failure_fixtures(self) -> None:
        block = self.blocks_from_ci_status()
        script = textwrap.dedent(block.split("        run: |\n", 1)[1])
        base = {
            "RESULTS": "success,skipped",
            "WORKFLOW_DISPATCH": "false",
            "BUILD_LOGIC_CHANGED": "false",
            "PADDLEOCR_PRODUCER_CHANGED": "false",
            "TEST_PADDLEOCR_PRODUCER_RESULT": "skipped",
        }
        for name in (
            "IMAGES", "IMAGES_BARCODE_API", "IMAGES_BARCODE_ZXING", "IMAGES_CAPTCHA",
            "IMAGES_OCR", "IMAGES_KTOR", "IMAGES_SPRING_BOOT", "IMAGES_VIPS_API",
            "IMAGES_VIPS_JAVA21", "IMAGES_VIPS_JAVA25", "VIPS_VERIFICATION",
        ):
            base[f"{name}_CHANGED"] = "false"
            base[f"TEST_{name}_RESULT" if name != "VIPS_VERIFICATION" else "VIPS_VERIFICATION_RESULT"] = "skipped"
        base["TEST_IMAGES_SPRING_BOOT_FILESYSTEM_RESULT"] = "skipped"

        def run(**overrides: str) -> int:
            environment = {**os.environ, **base, **overrides}
            return subprocess.run(
                ["bash", "-euo", "pipefail", "-c", script],
                env=environment,
                capture_output=True,
                check=False,
            ).returncode

        self.assertEqual(run(), 0)
        self.assertNotEqual(run(PADDLEOCR_PRODUCER_CHANGED="true"), 0)
        self.assertNotEqual(run(BUILD_LOGIC_CHANGED="true"), 0)
        self.assertNotEqual(run(WORKFLOW_DISPATCH="true"), 0)
        self.assertNotEqual(
            run(
                RESULTS="success,failure",
                PADDLEOCR_PRODUCER_CHANGED="true",
                TEST_PADDLEOCR_PRODUCER_RESULT="failure",
            ),
            0,
        )
        self.assertEqual(
            run(PADDLEOCR_PRODUCER_CHANGED="true", TEST_PADDLEOCR_PRODUCER_RESULT="success"),
            0,
        )


if __name__ == "__main__":
    unittest.main()
