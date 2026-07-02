# bluetape4k-images-barcode-zxing

[English](./README.md) | 한국어

Provider-neutral bluetape4k barcode API를 위한 순수 JVM ZXing provider입니다.

## 기능

- `bluetape4k-images-barcode-api`의 `BarcodeReader` 구현
- ZXing을 통한 QR Code와 Code 128 같은 주요 1D 포맷 디코딩
- ZXing text, backend format, result point, bounding box, raw bytes, metadata를
  `BarcodeResult`로 변환
- Barcode가 없는 이미지는 빈 목록 반환
- Public method signature에 ZXing class를 노출하지 않음

## 의존성 추가

```kotlin
dependencies {
    implementation("io.github.bluetape4k.image:bluetape4k-images-barcode-api:<version>")
    implementation("io.github.bluetape4k.image:bluetape4k-images-barcode-zxing:<version>")
}
```

## 사용 예시

```kotlin
import io.bluetape4k.images.barcode.BarcodeFormat
import io.bluetape4k.images.barcode.BarcodeOptions
import io.bluetape4k.images.barcode.extractBarcodes
import io.bluetape4k.images.barcode.zxing.ZxingBarcodeReader

val reader = ZxingBarcodeReader()
val results = image.extractBarcodes(
    reader = reader,
    options = BarcodeOptions(
        formats = setOf(BarcodeFormat.QR_CODE, BarcodeFormat.CODE_128),
        tryHarder = true,
    ),
)
```

Encoded byte 입력이 malformed image일 때 barcode failure로 정규화해야 한다면 provider
helper를 사용하세요.

```kotlin
val results = ZxingBarcodeReader().readBarcodes(bytes)
```

## Provider Boundary

ZXing은 Apache-2.0 순수 JVM 라이브러리라 native barcode library를 설치할 수 없는
서비스의 기본 OSS provider 경로로 적합합니다. 다만 public barcode API 자체가 아니라
provider로 다뤄야 합니다. 호출자는 `BarcodeReader`와 `BarcodeResult`에 의존하고,
ZXing 전용 class는 이 모듈 안에 둡니다.

단순 ZXing reader 경로는 보통 이미지 하나에서 하나의 barcode를 반환합니다. 더 넓은
multi-barcode detection이 필요하다면 ZXing만 표준화하기 전에 다른 provider도 비교하세요.

## 테스트

```bash
./gradlew :bluetape4k-images-barcode-zxing:test
```

테스트는 ZXing writer로 QR과 Code 128 이미지를 메모리에서 생성합니다. 외부 이미지
fixture가 필요하지 않습니다.
