# Issue #247 Barcode Fixtures and Capability Docs

## Context

#247 followed the API, ZXing provider, and BoofCV research issues. The remaining
gap was reusable fixture shape plus user-facing capability documentation.

## Decision or Finding

Use deterministic runtime-generated fixtures instead of committing external
barcode image binaries. Keep provider-neutral helpers in
`images-barcode-api` test fixtures and keep ZXing-specific QR/Code 128 image
generation inside the ZXing provider tests.

## Outcome

`BarcodeTestFixtures` now provides no-code images, rotated images, malformed
bytes, and source-note metadata for provider tests. README capability docs
record API, ZXing, deferred BoofCV, and future commercial/native provider
scope.

## Verification

Targeted API and ZXing module tests plus compile warning checks verify the
fixture helper shape and provider test reuse. Documentation was checked against
actual class names and current #246 research output.

## Future Guidance

When adding another barcode provider, depend on the API module test fixtures for
shared negative/rotation cases. Add provider-specific positive image generation
or license-cleared resources only in the provider module that owns that decoder
dependency.
