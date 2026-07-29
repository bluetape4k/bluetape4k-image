# Issue #244 Barcode API 교훈

## 배경

Issue #244는 ZXing provider issue 전에 provider-neutral barcode API module을 추가했다. 이
작업은 새 published module, public value model, sync/suspend entry point, README locale
update, CI/Nightly registration을 만들었다.

## 결정

`images-barcode-api`는 dependency-light로 유지한다. 이 module은 `bluetape4k-images`와
coroutines에만 의존한다. ZXing, BoofCV 같은 concrete decoder는 별도 provider module에
있어야 한다.

## 결과

module은 `BarcodeReader`, `BarcodeOptions`, `BarcodeResult`, geometry/provider model, input
helper extension, cancellation-safe suspend extraction을 노출한다. test는 validation,
serialization, input helper, dispatcher delegation, cancellation-before-start, provider
cancellation을 다룬다.

## 향후 방지책

public data class가 `ByteArray`를 담는다면 equality와 hash code를 override하거나 array
property를 피한다. Kotlin data class 기본값은 array를 reference로 비교하므로 명시적으로
문서화하지 않는 한 provider-neutral value model에는 맞지 않는다.

`fun interface` contract에서는 single abstract method에 default parameter를 두지 않는다.
default-option ergonomics는 extension overload로 제공한다.
