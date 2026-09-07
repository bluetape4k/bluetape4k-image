#!/usr/bin/env python3
"""Validate the bilingual Issue #638 README evidence/adoption boundary."""

from __future__ import annotations

import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
README = ROOT / "README.md"
README_KO = ROOT / "README.ko.md"
RUNBOOK_PATH = "docs/superpowers/runbooks/2026-09-07-issue-638-paddleocr-producer.md"
TRUSTED_PRODUCER_BULLET = "**Trusted producer evidence**"


def research_section(text: str, heading: str, next_heading: str) -> str:
    start = text.find(heading)
    end = text.find(next_heading, start + len(heading))
    if start < 0 or end < 0:
        raise AssertionError(f"README section is missing: {heading}")
    return text[start:end]


def bullet_position(section: str, marker: str) -> int:
    bullets = [line for line in section.splitlines() if line.startswith("- **")]
    for position, bullet in enumerate(bullets):
        if marker in bullet:
            return position
    raise AssertionError(f"README bullet is missing: {marker}")


def validate_pair(english: str, korean: str) -> None:
    english_section = research_section(
        english,
        "## AI/ML Backend Research Status",
        "## Manual",
    )
    korean_section = research_section(
        korean,
        "## AI/ML backend 연구 상태",
        "## 매뉴얼",
    )
    for document, section in (("README.md", english_section), ("README.ko.md", korean_section)):
        for marker in (
            "#638",
            "#609",
            "#611",
            "PRODUCER_PASS",
            "PENDING",
            RUNBOOK_PATH,
            TRUSTED_PRODUCER_BULLET,
        ):
            if marker not in section:
                raise AssertionError(f"{document} is missing {marker}")
    if re.search(r"not an adoption\s+approval", english_section) is None:
        raise AssertionError("README.md does not deny adoption approval")
    if "adoption(채택)" not in korean_section or "승인이 아니며" not in korean_section:
        raise AssertionError("README.ko.md does not deny adoption approval")
    if bullet_position(english_section, TRUSTED_PRODUCER_BULLET) != bullet_position(
        korean_section, TRUSTED_PRODUCER_BULLET
    ):
        raise AssertionError("trusted producer bullet moved to a different locale position")


class PaddleOcrReadmeParityTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.english = README.read_text(encoding="utf-8")
        cls.korean = README_KO.read_text(encoding="utf-8")

    def test_positive_pair_and_local_runbook_link(self) -> None:
        validate_pair(self.english, self.korean)
        self.assertTrue((ROOT / RUNBOOK_PATH).is_file())

    def test_removing_each_contract_marker_fails_in_memory(self) -> None:
        mutations = (
            ("bullet", TRUSTED_PRODUCER_BULLET),
            ("link", RUNBOOK_PATH),
            ("status", "PRODUCER_PASS"),
            ("adoption", "not an adoption"),
        )
        for name, marker in mutations:
            with self.subTest(document="README.md", marker=name):
                mutated = self.english.replace(marker, "", 1)
                with self.assertRaises(AssertionError):
                    validate_pair(mutated, self.korean)

        korean_mutations = (
            ("bullet", TRUSTED_PRODUCER_BULLET),
            ("link", RUNBOOK_PATH),
            ("status", "PRODUCER_PASS"),
            ("adoption", "adoption(채택)"),
        )
        for name, marker in korean_mutations:
            with self.subTest(document="README.ko.md", marker=name):
                mutated = self.korean.replace(marker, "", 1)
                with self.assertRaises(AssertionError):
                    validate_pair(self.english, mutated)


if __name__ == "__main__":
    unittest.main(verbosity=2)
