#!/usr/bin/env bash

set -euo pipefail

workflows=(
  .github/workflows/ci.yml
  .github/workflows/nightly-tests.yml
)

for workflow in "${workflows[@]}"; do
  grep -F 'artifact_dir="coverage-artifacts/${artifact#coverage-}"' "$workflow" >/dev/null || {
    echo "Coverage artifact path is not normalized in ${workflow}" >&2
    exit 1
  }

  if grep -F 'artifact_dir="coverage-artifacts/${artifact}"' "$workflow" >/dev/null; then
    echo "Legacy coverage artifact path remains in ${workflow}" >&2
    exit 1
  fi
done

echo "CI and Nightly coverage artifact paths are normalized."
