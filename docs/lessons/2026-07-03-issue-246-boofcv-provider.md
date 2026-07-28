# Issue #246 BoofCV provider research

## 배경

#215는 barcode provider architecture를 추적한다. #246은 BoofCV가 새로 추가된 ZXing module
옆의 `0.4.0` provider가 되어야 하는지 평가했다.

## 결정 또는 확인 사항

`0.4.0`에서는 `images-barcode-boofcv`를 연기한다. BoofCV는 geometry 또는 rejected-marker
diagnostic이 필요할 때 향후 specialized QR, Micro QR, Aztec provider로 유용하지만 broad
default barcode backend는 아니다.

## 결과

`0.4.0`의 provider set은 `images-barcode-api`와 `images-barcode-zxing`에 집중한다. #247은
BoofCV를 deferred specialized 2D provider로 provider capability matrix에 포함해야 한다.

## 검증

research는 BoofCV GitHub metadata, Apache-2.0 license, Java runtime docs, Maven Central
POM dependency shape, official QR/Micro QR/Aztec example, barcode detector family에
대한 shallow source grep, ZXing public supported format docs를 확인했다.

## 향후 지침

ZXing이 처리할 수 없는 구체적 QR/Aztec geometry gap을 fixture가 증명하기 전에는 BoofCV
module을 추가하지 않는다. 나중에 module을 추가하더라도 BoofCV가 더 넓은 barcode support를
추가하지 않는 한 QR, Micro QR, Aztec로 범위를 명시한다.
