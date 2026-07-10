#!/usr/bin/env bash

set -euo pipefail

workflow="${1:-.github/workflows/ci.yml}"

require_literal() {
  local literal="$1"
  grep -Fqx -- "$literal" "$workflow" >/dev/null || {
    echo "Missing CI coverage contract: $literal" >&2
    exit 1
  }
}

# Coverage must run only when at least one upstream module test completed.
require_literal "    if: always() && !contains(needs.*.result, 'failure') && !contains(needs.*.result, 'cancelled') && (needs.test-images.result == 'success' || needs.test-images-barcode-api.result == 'success' || needs.test-images-barcode-zxing.result == 'success' || needs.test-images-captcha.result == 'success' || needs.test-images-ocr.result == 'success' || needs.test-images-ktor.result == 'success' || needs.test-images-spring-boot.result == 'success' || needs.test-images-vips-api.result == 'success' || needs.test-images-vips-java21.result == 'success' || needs.test-images-vips-java25.result == 'success')"

# A successful module test is the only source of a required coverage artifact.
require_literal '            "coverage-images:${TEST_IMAGES_RESULT}"'
require_literal '            "coverage-images-vips-java25:${TEST_IMAGES_VIPS_JAVA25_RESULT}"'
require_literal '            if [[ "$result" == "success" ]]; then'

echo "CI coverage selection contract is present."
