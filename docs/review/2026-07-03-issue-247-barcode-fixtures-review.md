# Issue #247 Barcode Fixtures and Capability Docs Review

## Scope

Review deterministic barcode fixture helpers, ZXing provider test reuse, and
README capability documentation for #247.

## Findings

No P0/P1 findings.

## Checks

| Lens | Result | Evidence |
| --- | --- | --- |
| Fixture source safety | PASS | Fixtures are generated at test runtime. No external barcode image binaries are committed. `BarcodeTestFixtures.GENERATED_SOURCE_NOTE` records the source note. |
| Dependency boundary | PASS | `images-barcode-api` production dependencies remain provider-neutral. Test fixtures do not add ZXing, BoofCV, native, or commercial decoder dependencies to the API module. |
| Provider tests | PASS | ZXing tests reuse shared no-code, rotation, and malformed-byte helpers while keeping QR/Code 128 generation in ZXing test code. |
| Docs parity | PASS | `README.md`, `README.ko.md`, `images-barcode-api/README.*`, and `images-barcode-zxing/README.*` document fixtures and provider capability scope. |
| BoofCV outcome | PASS | Root README matrix records BoofCV as deferred specialized QR/Micro QR/Aztec provider, consistent with #246. |

## P0/P1 Gate

- P0 (CRITICAL): 0
- P1 (HIGH): 0
- P2/P3: none

## Verification Plan

- `./gradlew :bluetape4k-images-barcode-api:test :bluetape4k-images-barcode-zxing:test --configuration-cache --build-cache`
- `./gradlew :bluetape4k-images-barcode-api:compileTestFixturesKotlin :bluetape4k-images-barcode-zxing:compileTestKotlin --warning-mode all --configuration-cache --build-cache`
- `git diff --check`
- Targeted `rg` checks for fixture class names and provider matrix rows
