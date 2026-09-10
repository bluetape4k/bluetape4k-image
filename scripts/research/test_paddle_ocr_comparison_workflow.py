import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / ".github/workflows/paddleocr-comparison.yml"


class PaddleOcrComparisonWorkflowContractTest(unittest.TestCase):
    def test_workflow_is_manual_exact_head_and_immutable(self) -> None:
        workflow = WORKFLOW.read_text(encoding="utf-8")
        self.assertIn("workflow_dispatch:", workflow)
        self.assertIn("workflow_call:", workflow)
        self.assertIn("EXPECTED_HEAD", workflow)
        self.assertIn('test "$GITHUB_SHA" = "$EXPECTED_HEAD"', workflow)
        self.assertIn("runs-on: ubuntu-24.04", workflow)
        self.assertIn('test "$DOCKER_PLATFORM" = "linux/amd64"', workflow)
        self.assertIn(
            "ghcr.io/bluetape4k/paddleocr-service@sha256:cc21ee6edc03c672d11cadaadda828ef83e4ecfdee0da8838b402a763fcdec46",
            workflow,
        )
        self.assertIn("PADDLE_MODEL_DIGEST", workflow)

    def test_workflow_runs_full_corpus_and_kotlin_receipt_validator(self) -> None:
        workflow = WORKFLOW.read_text(encoding="utf-8")
        self.assertIn("scripts/research/paddle_ocr_comparison.py", workflow)
        self.assertIn("--repo-root", workflow)
        self.assertIn("--receipt", workflow)
        self.assertIn("--run-manifest", workflow)
        self.assertIn(
            ":bluetape4k-images-benchmark:validateOcrProviderComparisonReceipt",
            workflow,
        )
        self.assertIn("ocr.comparison.input", workflow)
        self.assertIn("actions/upload-artifact", workflow)
        self.assertIn("if: ${{ always() }}", workflow)

    def test_runner_records_exact_source_commit(self) -> None:
        runner = (ROOT / "scripts/research/paddle_ocr_comparison.py").read_text(
            encoding="utf-8"
        )
        self.assertIn('"sourceCommit": _source_commit(repo_root)', runner)


if __name__ == "__main__":
    unittest.main()
