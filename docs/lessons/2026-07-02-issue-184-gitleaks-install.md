# Issue #184 Gitleaks Install Hardening

## Context

The CI secret-scan job installed gitleaks by reconstructing the latest release
asset URL from the release tag. That makes the job sensitive to asset naming or
redirect changes and can fail before `gitleaks detect` runs.

## Decision

Resolve the Linux x64 archive through the GitHub Releases API asset metadata and
download the returned `browser_download_url`. Keep a pinned fallback archive for
API outage cases, but use the live asset URL for the normal path.

## Outcome

The workflow no longer parses `tag_name` or guesses the latest asset URL. The
install step now verifies the installed binary with `gitleaks version` before
running the repository scan.

## Verification

- `actionlint .github/workflows/ci.yml`
- `git diff --check`
- Release API asset selection smoke test for `linux_x64.tar.gz`
- `gitleaks detect --source . --redact --no-git --config .gitleaks.toml`

## Future Guidance

For GitHub release tools used in CI, prefer selecting the intended asset from
`.assets[]` and downloading its `browser_download_url`. Do not reconstruct
`latest/download` URLs unless the upstream project documents that path as a
stable contract.
