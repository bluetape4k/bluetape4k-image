# Issue #246 BoofCV Provider Research

## Context

#215 tracks barcode provider architecture. #246 evaluated whether BoofCV should
become a `0.4.0` provider beside the newly added ZXing module.

## Decision or Finding

Defer `images-barcode-boofcv` for `0.4.0`. BoofCV is useful as a future
specialized QR, Micro QR, and Aztec provider when geometry or rejected-marker
diagnostics are needed, but it is not a broad default barcode backend.

## Outcome

The provider set for `0.4.0` stays focused on `images-barcode-api` plus
`images-barcode-zxing`. #247 should include BoofCV in the provider capability
matrix as a deferred specialized 2D provider.

## Verification

Research checked BoofCV GitHub metadata, Apache-2.0 license, Java runtime docs,
Maven Central POM dependency shape, official QR/Micro QR/Aztec examples, a
shallow source grep for barcode detector families, and ZXing public supported
format docs.

## Future Guidance

Do not add a BoofCV module until fixtures prove a concrete QR/Aztec geometry
gap that ZXing cannot cover. If a module is later added, scope it explicitly to
QR, Micro QR, and Aztec unless BoofCV adds broader barcode support.
