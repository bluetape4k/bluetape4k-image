# Issue #245 ZXing Barcode Provider Review

## Scope

- Added `bluetape4k-images-barcode-zxing` as the first concrete provider for
  `bluetape4k-images-barcode-api`.
- Registered the module in Gradle, README locale set, AGENTS, CI, Nightly,
  release, snapshot publish, and Examples path filters.

## Review Findings

- P0: none.
- P1: none.
- P2: none.

## Evidence

- `:bluetape4k-images-barcode-zxing:test` covers QR, Code 128, no-code,
  rotated QR, malformed input, unsupported formats, raw bytes, and region
  mapping.
- `rg` check confirms `com.google.zxing` imports are confined to
  `images-barcode-zxing`.
- `rg` check confirms no `!!` or MockK setup lifecycle issues in the new
  provider module.

## Residual Risk

- The provider uses ZXing's simple `MultiFormatReader` path, which commonly
  returns one decoded barcode per image. Broader multi-barcode/capability matrix
  work remains in #247 and future provider comparison issues.
