#!/usr/bin/env bash

set -euo pipefail

workflows=(
  .github/workflows/ci.yml
  .github/workflows/nightly-tests.yml
)

for workflow in "${workflows[@]}"; do
  required_lines=(
    'artifact_dir="coverage-artifacts/${artifact}"'
    'normalized_artifact_dir="coverage-artifacts/${artifact#coverage-}"'
    'if [[ ! -d "${artifact_dir}" && -d "${normalized_artifact_dir}" ]]; then'
    'artifact_dir="${normalized_artifact_dir}"'
  )

  for required_line in "${required_lines[@]}"; do
    grep -F "$required_line" "$workflow" >/dev/null || {
      echo "Missing single/multi artifact path fallback in ${workflow}: ${required_line}" >&2
      exit 1
    }
  done
done

echo "CI and Nightly support single and multiple coverage artifact paths."
