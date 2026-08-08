#!/usr/bin/env python3
"""Regression tests for README visual asset path generation."""

from __future__ import annotations

import importlib.util
import re
import sys
import unittest
from pathlib import Path


SCRIPT = Path(__file__).with_name("generate-readme-visual-assets.py")
MODULE_SPEC = importlib.util.spec_from_file_location("readme_visual_assets", SCRIPT)
if MODULE_SPEC is None or MODULE_SPEC.loader is None:
    raise RuntimeError(f"cannot load generator module: {SCRIPT}")
MODULE = importlib.util.module_from_spec(MODULE_SPEC)
sys.modules[MODULE_SPEC.name] = MODULE
MODULE_SPEC.loader.exec_module(MODULE)


class RoundedPathTest(unittest.TestCase):
    def test_orthogonal_turns_are_emitted_as_quadratic_bends(self) -> None:
        path = MODULE.rounded_orthogonal_path(
            ((0, 0), (100, 0), (100, 100), (20, 100)),
            radius=18,
        )

        self.assertEqual(
            path,
            "M 0 0 L 82 0 Q 100 0 100 18 L 100 82 Q 100 100 82 100 L 20 100",
        )

    def test_terminal_clearance_shortens_only_the_last_bend(self) -> None:
        path = MODULE.rounded_orthogonal_path(
            ((0, 0), (100, 0), (100, 25)),
            radius=18,
            terminal_clearance=15,
        )

        self.assertEqual(path, "M 0 0 L 87.5 0 Q 100 0 100 10 L 100 25")

    def test_target_renderer_keeps_all_bent_edges_rounded(self) -> None:
        svg = MODULE.render_diagram(MODULE.bluetape4k_image_architecture_spec())

        bent_paths = [line for line in svg.splitlines() if 'data-connector="true"' in line and " d=\"" in line]
        self.assertEqual(len(bent_paths), 11)
        self.assertEqual(sum(" Q " in line for line in bent_paths), 6)
        for line in bent_paths:
            commands = re.findall(r"(?:^|\s)([MLQ])\s", line.split(' d="', 1)[1])
            self.assertFalse(any(left == right == "L" for left, right in zip(commands, commands[1:])))


if __name__ == "__main__":
    unittest.main()
