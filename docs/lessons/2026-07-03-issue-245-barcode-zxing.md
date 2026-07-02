# Issue #245 ZXing Barcode Provider

## Context

#244 introduced a provider-neutral barcode API. #245 added the first concrete
OSS provider without leaking ZXing types into the shared API module.

## Decision

Keep ZXing in a dedicated `images-barcode-zxing` module and expose only
`BarcodeReader`, `BarcodeOptions`, `BarcodeResult`, and related API models.
No-code images return an empty list. Malformed encoded byte input is normalized
as `BarcodeException(MALFORMED_INPUT)`.

## Outcome

The module decodes generated QR and Code 128 samples, maps result points into a
pixel-space region, records provider metadata, and participates in CI/Nightly
validation as a publishable module.

## Future Guidance

Do not add ZXing dependencies to `images` or `images-barcode-api`. If more
barcode breadth is needed, compare providers through the same `BarcodeReader`
contract and update #247's fixture/capability matrix rather than extending this
module into a provider registry.
