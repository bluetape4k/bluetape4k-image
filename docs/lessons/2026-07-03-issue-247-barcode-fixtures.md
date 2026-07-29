# Issue #247 Barcode fixture와 capability 문서

## 배경

#247은 API, ZXing provider, BoofCV research issue 뒤를 이었다. 남은 gap은 reusable fixture
shape와 user-facing capability documentation이었다.

## 결정 또는 확인 사항

external barcode image binary를 commit하는 대신 deterministic runtime-generated fixture를
사용한다. provider-neutral helper는 `images-barcode-api` test fixture에 두고, ZXing-specific
QR/Code 128 image generation은 ZXing provider test 안에 둔다.

## 결과

`BarcodeTestFixtures`는 이제 provider test용 no-code image, rotated image, malformed byte,
source-note metadata를 제공한다. README capability docs는 API, ZXing, deferred BoofCV, future
commercial/native provider scope를 기록한다.

## 검증

targeted API와 ZXing module test, compile warning check가 fixture helper shape와 provider
test reuse를 검증한다. Documentation은 actual class name과 현재 #246 research output에
대조해 확인했다.

## 향후 지침

다른 barcode provider를 추가할 때 shared negative/rotation case에는 API module test fixture를
사용한다. provider-specific positive image generation 또는 license-cleared resource는 해당
decoder dependency를 소유한 provider module에만 추가한다.
