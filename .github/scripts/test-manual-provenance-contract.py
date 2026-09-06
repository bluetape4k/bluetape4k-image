"""Guard the Image CI boundary for central stable-manual provenance."""

import os
import re
import subprocess
from pathlib import Path, PurePosixPath

WORKFLOW = Path(".github/workflows/ci.yml")
SCRIPT = ".github/scripts/test-manual-provenance-contract.py"
REGRESSION_SCRIPT = ".github/scripts/test-manual-provenance-regressions.py"
STABLE_RELEASE = re.compile(r"^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$")
CANONICAL_SHA = re.compile(r"^[0-9a-f]{40}$")
PROJECT_DIR = re.compile(
    r'^[ \t]*project\("(:[^"]+)"\)\.projectDir[ \t]*=[ \t]*'
    r'(?:\n[ \t]*)?file\("([^"]+)"\)[ \t]*(?=\n|\Z)',
    re.MULTILINE,
)
REQUIRED_TAG_INPUTS = ("settings.gradle.kts", "build.gradle.kts")
FORBIDDEN_REMOTE_MANUAL_CONTRACTS = (
    "Checkout central manual site",
    "repository: bluetape4k/bluetape4k.github.io",
    ".manual-site",
    "export_settings_inventory.rb",
    "release_inventory.rb",
    "validate_release_drift.rb",
    "release_inventory_test.rb",
    "release_drift_test.rb",
    "export_manifest.rb",
    "release_diagram_contract_test.rb",
)


def git(*arguments: str) -> str:
    result = subprocess.run(
        ("git", *arguments),
        check=False,
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        raise SystemExit(f"git {' '.join(arguments)} failed")
    return result.stdout.strip()


def verify_workflow_contract(workflow: str) -> None:
    required = (
        "name: Verify stable manual generator inputs",
        "MANUAL_TAG: 1.0.0",
        f"python3 {REGRESSION_SCRIPT}",
        f"python3 {SCRIPT}",
    )
    missing = [value for value in required if value not in workflow]
    forbidden = [value for value in FORBIDDEN_REMOTE_MANUAL_CONTRACTS if value in workflow]
    if missing or forbidden:
        details = [
            *(f"missing workflow contract: {value}" for value in missing),
            *(f"remote manual contract must stay in Pages: {value}" for value in forbidden),
        ]
        raise SystemExit("\n".join(details))


def verify_tag_inputs(tag: str) -> str:
    if STABLE_RELEASE.fullmatch(tag) is None:
        raise SystemExit("MANUAL_TAG must be a stable semantic release")
    commit = git("rev-parse", "--verify", f"refs/tags/{tag}^{{commit}}")
    if CANONICAL_SHA.fullmatch(commit) is None:
        raise SystemExit("MANUAL_TAG must resolve to a canonical commit")
    for relative in REQUIRED_TAG_INPUTS:
        git("cat-file", "-e", f"{commit}:{relative}")
    return commit


def parse_project_dirs(settings: str) -> dict[str, str]:
    rows = PROJECT_DIR.findall(settings.replace("\r\n", "\n"))
    if not rows:
        raise SystemExit("no Gradle project directories found")

    projects: dict[str, str] = {}
    sources: set[str] = set()
    for gradle_path, source_dir in rows:
        source_path = PurePosixPath(source_dir)
        if source_path.is_absolute() or not source_dir or ".." in source_path.parts:
            raise SystemExit(f"unsafe sourceDir in generator input: {source_dir}")
        if gradle_path in projects:
            raise SystemExit(f"duplicate Gradle path in generator input: {gradle_path}")
        if source_dir in sources:
            raise SystemExit(f"duplicate sourceDir in generator input: {source_dir}")
        projects[gradle_path] = source_dir
        sources.add(source_dir)
    return projects


def verify_generator_topology(commit: str) -> int:
    current = parse_project_dirs(Path("settings.gradle.kts").read_text(encoding="utf-8"))
    tagged = parse_project_dirs(git("show", f"{commit}:settings.gradle.kts"))
    release_paths = set(git("ls-tree", "-r", "--name-only", commit).splitlines())

    return verify_release_topology(current, tagged, release_paths)


def verify_release_topology(
    current: dict[str, str],
    tagged: dict[str, str],
    release_paths: set[str],
) -> int:
    missing_builds = sorted(
        gradle_path
        for gradle_path, source_dir in tagged.items()
        if f"{source_dir}/build.gradle.kts" not in release_paths
    )
    if missing_builds:
        raise SystemExit(
            "tagged generator inputs have no build file: " + ", ".join(missing_builds)
        )

    current_release = {
        gradle_path: source_dir
        for gradle_path, source_dir in current.items()
        if f"{source_dir}/build.gradle.kts" in release_paths
    }
    if current_release != tagged:
        missing = sorted(set(tagged) - set(current_release))
        unexpected = sorted(set(current_release) - set(tagged))
        changed = sorted(
            gradle_path
            for gradle_path in set(current_release) & set(tagged)
            if current_release[gradle_path] != tagged[gradle_path]
        )
        details = []
        if missing:
            details.append("missing=" + ",".join(missing))
        if unexpected:
            details.append("unexpected=" + ",".join(unexpected))
        if changed:
            details.append("changed=" + ",".join(changed))
        raise SystemExit("generator/tag topology mismatch: " + " ".join(details))
    return len(tagged)


def main() -> None:
    verify_workflow_contract(WORKFLOW.read_text(encoding="utf-8"))
    tag = os.environ.get("MANUAL_TAG", "")
    commit = verify_tag_inputs(tag)
    project_count = verify_generator_topology(commit)
    print(
        f"Manual generator inputs valid: tag={tag} commit={commit} "
        f"files={len(REQUIRED_TAG_INPUTS)} projects={project_count}"
    )


if __name__ == "__main__":
    main()
