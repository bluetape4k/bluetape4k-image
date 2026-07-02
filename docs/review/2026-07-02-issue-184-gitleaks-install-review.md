# Issue #184 Gitleaks Install Review

## Scope

- Issue: #184 `ci: harden gitleaks release asset install`
- File reviewed: `.github/workflows/ci.yml`
- Change type: CI maintenance

## Findings

- P0: none.
- P1: none.

## Review Notes

- The install step now resolves the Linux x64 archive from the latest GitHub release asset metadata instead of reconstructing the latest download URL from `tag_name`.
- The fallback remains pinned to the known-good `v8.30.1` archive, so an API outage does not silently skip secret scanning.
- The step prints `gitleaks version` after installation, which gives the GitHub Actions log a direct installation proof before `gitleaks detect` runs.

## Validation

- `actionlint .github/workflows/ci.yml`
- `git diff --check`
- Release API asset selection smoke test for `linux_x64.tar.gz`
- `gitleaks detect --source . --redact --no-git --config .gitleaks.toml`
