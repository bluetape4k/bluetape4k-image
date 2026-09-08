#!/usr/bin/env python3
"""Validate the Issue #638 PaddleOCR producer runbook contract."""

from __future__ import annotations

import os
import re
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RUNBOOK = ROOT / "docs/superpowers/runbooks/2026-09-07-issue-638-paddleocr-producer.md"
LOCAL_LINK = re.compile(r"\[[^\]]+\]\(([^)]+)\)")
CODE_BLOCK = re.compile(r"~~~bash\n(.*?)\n~~~", re.DOTALL)
FIXTURE_BLOCK = re.compile(
    r"<!-- RUNBOOK_TEST_FIXTURE_BEGIN -->.*?~~~bash\n(.*?)\n~~~.*?"
    r"<!-- RUNBOOK_TEST_FIXTURE_END -->",
    re.DOTALL,
)


class PaddleOcrRunbookTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.text = RUNBOOK.read_text(encoding="utf-8")

    def test_heading_order_and_contract_markers(self) -> None:
        headings = (
            "# PaddleOCR trusted producer 운영 runbook (#638)",
            "## 목적과 결정 경계",
            "## 지원 범위와 prerequisites",
            "## 0. repo root와 output directory 고정",
            "## 1. PR credential-free validation",
            "## 2. PRODUCE dispatch와 exact run 선택",
            "## 3. staging, release, attestation read-back",
            "## 4. verified ORAS와 anonymous public evidence",
            "## 5. RECONCILE read-back과 새 attempt",
            "## 6. revocation, incident, quarantine와 emergency denial",
            "## 7. retention과 cleanup",
            "## 8. known-good rollback",
            "## STATUS_CONTRACT: status, exit, publish 가능 여부와 다음 명령",
            "## Local source links",
        )
        positions = [self.text.find(heading) for heading in headings]
        self.assertTrue(all(position >= 0 for position in positions))
        self.assertEqual(positions, sorted(positions))

        required = (
            "#638",
            "#609",
            "#611",
            "PRODUCER_PASS",
            "adoption",
            "PENDING",
            "snapshot-workflow-runs",
            "readback-workflow-run",
            "readback-packages",
            "prepare-reconcile-inputs",
            "validate-reconcile-state",
            "open-or-link-incident",
            "readback-incident",
            "readback-retention",
            "execute-known-good-rollback",
            "readback-known-good-rollback",
            "issue-638-incident:",
            "issue-638-rollback:",
            '--oras-bin "$ORAS_BIN"',
            '--registry-config "$REGISTRY_CONFIG"',
            "8388608",
            "16777216",
            "f27adb935022d94df8dc77719c322dda592c78a0d57a6f7dcdd8d900b248c454",
            "--connect-timeout-seconds 10",
            "--read-timeout-seconds 30",
            "--operation-timeout-seconds 60",
            "--materialization-timeout-seconds 600",
        )
        for marker in required:
            self.assertIn(marker, self.text, marker)
        self.assertNotIn("UNSUPPORTED / PENDING", self.text)

    def test_repo_root_preamble_and_mutation_markers(self) -> None:
        for marker in (
            'REPO=bluetape4k/bluetape4k-image',
            'REPO_ROOT="$(git rev-parse --show-toplevel)"',
            'cd "$REPO_ROOT"',
            "mkdir -p build",
            "ORAS_BIN=",
            "EVIDENCE_REF",
            "RECONCILE_APPROVAL",
            "INCIDENT_APPROVAL",
            "APPROVAL_MARKER",
            "fresh owner approval",
            "별도 destructive approval",
        ):
            self.assertIn(marker, self.text, marker)

        for block in CODE_BLOCK.findall(self.text):
            self.assertIsNone(
                re.search(r"(?m)^\s*(?:command\s+)?timeout(?:\s|$)", block),
                block,
            )

    def test_all_local_links_exist(self) -> None:
        for target in LOCAL_LINK.findall(self.text):
            if target.startswith(("http://", "https://", "#")):
                continue
            path = (RUNBOOK.parent / target).resolve()
            self.assertTrue(path.exists(), f"broken local link: {target} -> {path}")

    def _run_fixture(
        self,
        shell_name: str,
        fixture_root: Path,
        payload: str,
    ) -> subprocess.CompletedProcess[str]:
        shell = shutil.which(shell_name)
        self.assertIsNotNone(shell, f"{shell_name} is required for runbook fixture")
        fake_bin = fixture_root / "fake-bin"
        fake_bin.mkdir()
        fake_gh = fake_bin / "gh"
        fake_gh.write_text(
            "#!/bin/sh\n"
            'test "$1" = api || exit 2\n'
            f"printf '%s\\n' '{payload}'\n",
            encoding="utf-8",
        )
        fake_gh.chmod(0o700)
        output_root = fixture_root / "output"
        environment = os.environ.copy()
        environment["PATH"] = f"{fake_bin}:{environment.get('PATH', '/usr/bin:/bin')}"
        environment["RUN_ID"] = "638"
        environment["RUNBOOK_FIXTURE_ROOT"] = str(output_root)
        shell_arguments = [shell, "-f", "-c"] if shell_name == "zsh" else [shell, "-c"]
        return subprocess.run(
            [*shell_arguments, self.fixture],
            cwd=ROOT,
            env=environment,
            text=True,
            capture_output=True,
            check=False,
        )

    def test_nested_run_selection_fixture_works_in_bash_and_zsh(self) -> None:
        match = FIXTURE_BLOCK.search(self.text)
        self.assertIsNotNone(match)
        self.fixture = match.group(1)
        payload = '{"data":{"databaseId":638,"runAttempt":1}}'

        path_fixtures = (Path("/tmp"), Path("/private/tmp"))
        for shell_name in ("bash", "zsh"):
            for base in path_fixtures:
                if not base.is_dir() or not os.access(base, os.W_OK):
                    base = Path(tempfile.gettempdir())
                with tempfile.TemporaryDirectory(
                    prefix=f"issue638-{shell_name}-", dir=base
                ) as temporary:
                    result = self._run_fixture(shell_name, Path(temporary), payload)
                    self.assertEqual(
                        result.returncode,
                        0,
                        f"{shell_name} fixture failed: {result.stderr}",
                    )
                    selected = Path(temporary) / "output/selected-attempt"
                    self.assertEqual(selected.read_text(encoding="utf-8").strip(), "638.1")

    def test_nested_top_level_regression_is_rejected(self) -> None:
        match = FIXTURE_BLOCK.search(self.text)
        self.assertIsNotNone(match)
        self.fixture = match.group(1)
        with tempfile.TemporaryDirectory(prefix="issue638-top-level-") as temporary:
            result = self._run_fixture(
                "bash",
                Path(temporary),
                '{"databaseId":638,"runAttempt":1}',
            )
            self.assertNotEqual(result.returncode, 0)


if __name__ == "__main__":
    unittest.main(verbosity=2)
