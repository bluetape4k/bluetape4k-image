#!/usr/bin/env python3
"""Guard the release workflow's read-only Nightly run lookup contract."""

from pathlib import Path
import re


workflow = Path(".github/workflows/release.yml").read_text(encoding="utf-8")
nightly_lookup = re.compile(
    r'gh api "repos/\$\{GITHUB_REPOSITORY\}/actions/workflows/nightly-tests\.yml/runs" \\\n'
    r"\s+--method GET \\\n"
    r"\s+-f status=completed \\\n"
)

if nightly_lookup.search(workflow) is None:
    raise SystemExit(
        "release workflow must query completed Nightly runs with gh api --method GET"
    )

