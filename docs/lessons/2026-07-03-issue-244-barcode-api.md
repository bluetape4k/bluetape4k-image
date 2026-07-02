# Issue #244 Barcode API Lessons

## Context

Issue #244 added the provider-neutral barcode API module before the ZXing
provider issue. The work created a new published module, public value models,
sync/suspend entry points, README locale updates, and CI/Nightly registration.

## Decision

Keep `images-barcode-api` dependency-light: it depends on `bluetape4k-images`
and coroutines only. Concrete decoders such as ZXing and BoofCV must live in
separate provider modules.

## Outcome

The module exposes `BarcodeReader`, `BarcodeOptions`, `BarcodeResult`,
geometry/provider models, input helper extensions, and cancellation-safe
suspend extraction. Tests cover validation, serialization, input helpers,
dispatcher delegation, cancellation-before-start, and provider cancellation.

## Future Guard

When a public data class carries `ByteArray`, override equality and hash code
or avoid array properties. Kotlin data class defaults compare arrays by
reference, which is wrong for provider-neutral value models unless explicitly
documented.

For `fun interface` contracts, do not put default parameters on the single
abstract method. Provide extension overloads for default-option ergonomics.
