# Issue #245 ZXing Barcode provider

## 배경

#244는 provider-neutral barcode API를 도입했다. #245는 ZXing type을 shared API module로
누수하지 않으면서 첫 concrete OSS provider를 추가했다.

## 결정

ZXing은 dedicated `images-barcode-zxing` module에 두고 `BarcodeReader`, `BarcodeOptions`,
`BarcodeResult`와 related API model만 노출한다. no-code image는 empty list를 반환한다.
malformed encoded byte input은 `BarcodeException(MALFORMED_INPUT)`으로 normalize한다.

## 결과

module은 generated QR과 Code 128 sample을 decode하고, result point를 pixel-space region으로
mapping하며, provider metadata를 기록하고 publishable module로 CI/Nightly validation에
참여한다.

## 향후 지침

`images` 또는 `images-barcode-api`에 ZXing dependency를 추가하지 않는다. barcode breadth가
더 필요하면 이 module을 provider registry로 확장하지 말고 같은 `BarcodeReader` contract로
provider를 비교하고 #247의 fixture/capability matrix를 갱신한다.
