"""실제 composite 조건과 output shell의 재시도 계약을 검증한다."""

from __future__ import annotations

import os
import re
import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def condition(
    expression: str, outcomes: dict[str, str], cancelled: bool, failed: bool
) -> bool:
    if expression == "${{ always() }}":
        return True
    if not expression:
        return not failed and not cancelled
    match = re.fullmatch(
        r"\$\{\{ !cancelled\(\) && steps\.(attempt[1-9]).outcome == 'failure' \}\}",
        expression,
    )
    if match is None:
        raise AssertionError(f"Unsupported condition: {expression}")
    return not cancelled and outcomes.get(match[1]) == "failure"


def execute(
    operation: str,
    results: list[bool],
    *,
    cancelled_after_first: bool = False,
    parent_failed: bool = False,
    bad_digest: bool = False,
) -> dict:
    source = (
        ROOT / f".github/actions/paddleocr-{operation}-artifact/action.yml"
    ).read_text()
    blocks = re.split(r"(?m)^    - name: ", source)[1:]
    outcomes: dict[str, str] = {}
    outputs: dict[str, dict[str, str]] = {}
    calls, waits = [], []
    cancelled = False
    failed = parent_failed
    result_code = None
    with tempfile.TemporaryDirectory() as directory:
        root = Path(directory)
        output = root / "output"
        output.touch()
        sleeper = root / "sleep"
        sleeper.write_text('#!/bin/sh\nprintf "%s\\n" "$1" >> "$WAIT_LOG"\n')
        sleeper.chmod(0o755)
        env = dict(os.environ, GITHUB_OUTPUT=str(output), WAIT_LOG=str(root / "waits"))
        env["PATH"] = str(root) + os.pathsep + env["PATH"]
        for block in blocks:
            item = re.search(r"(?m)^      if: (.+)$", block)
            expression = item[1] if item else ""
            if not condition(expression, outcomes, cancelled, failed):
                continue
            step_id = re.search(r"(?m)^      id: (.+)$", block)
            uses = re.search(r"(?m)^      uses: (.+)$", block)
            if uses:
                assert step_id is not None
                step = step_id[1]
                index = len(calls)
                calls.append(block)
                success = results[index]
                outcomes[step] = "success" if success else "failure"
                outputs[step] = {
                    "artifact-id": str(201 + index),
                    "artifact-digest": "bad" if bad_digest else "a" * 64,
                    "artifact-url": f"https://github.com/example/artifacts/{201 + index}",
                    "download-path": str(root / f"download-{index}"),
                }
                if not success and "continue-on-error: true" not in block:
                    failed = True
                if cancelled_after_first:
                    cancelled = True
            elif "      run: sleep 10" in block:
                subprocess.run(
                    ["bash", "-c", "sleep 10"], env=env, check=True, timeout=3
                )
            elif step_id and step_id[1] == "result":
                for key, step, field in re.findall(
                    r"(?m)^        ([A-Z_0-9]+): \$\{\{ steps\.(attempt[1-9])\.(.*?) \}\}$",
                    block,
                ):
                    env[key] = (
                        outcomes.get(step, "skipped")
                        if field == "outcome"
                        else outputs.get(step, {}).get(
                            field.removeprefix("outputs."), ""
                        )
                    )
                shell = textwrap.dedent(block.split("      run: |\n", 1)[1])
                result = subprocess.run(
                    ["bash", "-c", shell],
                    env=env,
                    text=True,
                    capture_output=True,
                    timeout=3,
                    check=False,
                )
                result_code = result.returncode
            else:
                raise AssertionError("Unrecognized composite step")
        if (root / "waits").exists():
            waits = (root / "waits").read_text().splitlines()
        return {
            "calls": calls,
            "waits": waits,
            "exit": result_code,
            "output": output.read_text(),
        }


class ArtifactRetryTest(unittest.TestCase):
    def test_success_on_each_attempt_selects_only_that_attempt(self) -> None:
        for operation in ("upload", "download"):
            for successful_attempt in (1, 2, 3):
                with self.subTest(operation=operation, attempt=successful_attempt):
                    result = execute(
                        operation, [False] * (successful_attempt - 1) + [True]
                    )
                    self.assertEqual(result["exit"], 0)
                    self.assertEqual(len(result["calls"]), successful_attempt)
                    self.assertEqual(result["waits"], ["10"] * (successful_attempt - 1))
                    if operation == "upload":
                        self.assertIn(
                            f"artifact-id={200 + successful_attempt}\n",
                            result["output"],
                        )
                    else:
                        self.assertIn(
                            f"download-{successful_attempt - 1}\n", result["output"]
                        )

    def test_permanent_failure_stops_after_three_attempts_without_outputs(self) -> None:
        for operation in ("upload", "download"):
            result = execute(operation, [False] * 4)
            self.assertEqual(len(result["calls"]), 3)
            self.assertEqual(result["waits"], ["10", "10"])
            self.assertNotEqual(result["exit"], 0)
            self.assertEqual(result["output"], "")

    def test_cancellation_stops_retries_and_never_reports_success(self) -> None:
        for operation in ("upload", "download"):
            result = execute(operation, [False, True], cancelled_after_first=True)
            self.assertEqual(len(result["calls"]), 1)
            self.assertEqual(result["waits"], [])
            self.assertNotEqual(result["exit"], 0)
            self.assertEqual(result["output"], "")

    def test_cleanup_can_transfer_after_parent_job_failure(self) -> None:
        for operation in ("upload", "download"):
            result = execute(operation, [True], parent_failed=True)
            self.assertEqual(len(result["calls"]), 1)
            self.assertEqual(result["exit"], 0)

    def test_success_with_invalid_upload_digest_is_rejected(self) -> None:
        result = execute("upload", [True], bad_digest=True)
        self.assertNotEqual(result["exit"], 0)
        self.assertEqual(result["output"], "")

    def test_retry_preserves_inputs_and_only_replaces_unhanded_upload(self) -> None:
        for operation in ("upload", "download"):
            result = execute(operation, [False, False, True])
            forwarded = [
                dict(
                    re.findall(
                        r"(?m)^        ([a-z-]+): \$\{\{ inputs\.([a-z-]+) \}\}$", block
                    )
                )
                for block in result["calls"]
            ]
            self.assertTrue(forwarded[0])
            self.assertEqual(forwarded, [forwarded[0]] * 3)
            self.assertTrue(all(key == value for key, value in forwarded[0].items()))
            if operation == "upload":
                self.assertIn("overwrite: false", result["calls"][0])
                self.assertTrue(
                    all("overwrite: true" in block for block in result["calls"][1:])
                )
            else:
                self.assertTrue(
                    all("digest-mismatch: error" in block for block in result["calls"])
                )


if __name__ == "__main__":
    unittest.main()
