"""Regression tests for the local stable-manual generator contract."""

import unittest
from importlib.util import module_from_spec, spec_from_file_location
from pathlib import Path

SCRIPT = Path(__file__).with_name("test-manual-provenance-contract.py")
SPEC = spec_from_file_location("manual_provenance_contract", SCRIPT)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError(f"cannot load {SCRIPT}")
CONTRACT = module_from_spec(SPEC)
SPEC.loader.exec_module(CONTRACT)


class ManualProvenanceRegressionTest(unittest.TestCase):
    def test_allows_current_only_projects_outside_the_stable_tree(self) -> None:
        current = {":core": "core", ":next": "next"}
        tagged = {":core": "core"}

        count = CONTRACT.verify_release_topology(
            current,
            tagged,
            {"core/build.gradle.kts"},
        )

        self.assertEqual(1, count)

    def test_rejects_a_missing_stable_project_mapping(self) -> None:
        with self.assertRaisesRegex(SystemExit, r"missing=:core"):
            CONTRACT.verify_release_topology(
                {":renamed": "renamed"},
                {":core": "core"},
                {"core/build.gradle.kts", "renamed/build.gradle.kts"},
            )

    def test_rejects_duplicate_generator_paths(self) -> None:
        settings = (
            'project(":core").projectDir = file("core")\n'
            'project(":core").projectDir = file("core-copy")'
        )

        with self.assertRaisesRegex(SystemExit, r"duplicate Gradle path.*:core"):
            CONTRACT.parse_project_dirs(settings)


if __name__ == "__main__":
    unittest.main()
