# Issue #245 ZXing Barcode Provider 설계 명세

- 이슈: [#245](https://github.com/bluetape4k/bluetape4k-image/issues/245)
- 상위 epic: [#215](https://github.com/bluetape4k/bluetape4k-image/issues/215)
- 마일스톤: `0.4.0`
- branch/worktree: `.worktrees/feat-issue-245-barcode-zxing`의 `feat/issue-245-barcode-zxing`

## 문제

#244의 provider-neutral barcode API에는 첫 OSS 구현이 필요하다. 그래야 caller가 future native 또는
commercial provider에 의존하지 않고 QR과 일반적인 1D barcode를 decode할 수 있다. ZXing은 Apache-2.0,
pure JVM이며 QR과 주요 retail/industrial 1D symbology를 다루므로 첫 provider로 도입 비용이 가장 낮다.

## 현재 증거

- `bluetape4k-images-barcode-api`는 이미 `BarcodeReader`, `BarcodeOptions`, `BarcodeResult`,
  `BarcodeRegion`, provider identity, sanitized `BarcodeException` failure reason을 정의한다.
- #215는 provider dependency가 `bluetape4k-images`와 API module 밖에 머물도록 요구한다.
- #245는 QR과 최소 하나의 1D family, provider metadata, result points/bounding polygon mapping,
  no-code handling, ZXing maintenance/capability boundary 문서화를 요구한다.
- `com.google.zxing:core`, `com.google.zxing:javase`에 대해 관측한 Maven Central metadata는
  latest/release `3.5.4`를 보고한다.

## 목표

- published module `bluetape4k-images-barcode-zxing`을 추가한다.
- 모든 ZXing dependency와 import를 ZXing provider module 내부에 유지한다.
- `ZxingBarcodeReader`를 `BarcodeReader`로 구현한다.
- deterministic test에서 QR Code와 Code 128을 decode하고, 지원되는 ZXing symbology에 대해 더 넓은
  API format mapping을 둔다.
- 요청 시 ZXing text, backend format, result points, bounding box, raw bytes, provider identity,
  metadata를 #244 model로 mapping한다.
- barcode가 없는 image에는 empty list를 반환한다.
- unsupported requested format, malformed encoded input, decode failure에는 provider-neutral failure reason을
  가진 `BarcodeException`을 던진다.
- English와 Korean README file에 explicit provider construction과 `ImmutableImage.extractBarcodes` 사용법을
  문서화한다.

## 비목표

- ZXing을 `images-barcode-api`의 default provider로 만들지 않는다.
- `bluetape4k-images`나 `images-barcode-api`에 ZXing을 추가하지 않는다.
- 이 issue에서는 BoofCV, commercial SDK, OpenCV, ZBar를 구현하지 않는다.
- full cross-provider fixture/capability matrix를 만들지 않는다. 최소 하나의 concrete provider가 들어온 뒤 #247이
  이를 소유한다.
- 여러 shipped provider가 생기기 전에는 service loader registry를 도입하지 않는다.

## Module 경계

다음을 추가한다.

```text
images-barcode-zxing/
  artifact: io.github.bluetape4k.image:bluetape4k-images-barcode-zxing
  package: io.bluetape4k.images.barcode.zxing
```

Gradle dependency:

- `api(project(":bluetape4k-images-barcode-api"))`
- `implementation(libs.zxing.core)`
- `implementation(libs.zxing.javase)`
- `testImplementation(libs.bluetape4k.junit5)`
- suspend entry point에 direct provider test가 필요하면 `testImplementation(libs.kotlinx.coroutines.test)`.

## Public API

primary provider:

```kotlin
class ZxingBarcodeReader(
    private val provider: BarcodeProviderIdentity = zxingProviderIdentity(),
) : BarcodeReader {
    override fun readBarcodes(image: ImmutableImage, options: BarcodeOptions): List<BarcodeResult>
}
```

generic API extension이 볼 수 없는 provider-specific failure normalization을 추가할 때만 convenience
entry point를 추가할 수 있다. malformed encoded input handling이 그 예다.

```kotlin
fun ZxingBarcodeReader.readBarcodes(
    bytes: ByteArray,
    options: BarcodeOptions = BarcodeOptions(),
): List<BarcodeResult>
```

## Mapping 규칙

- empty `BarcodeOptions.formats`는 모든 ZXing-supported format을 의미한다.
- `BarcodeFormat.UNKNOWN`은 ZXing hint로 전달하지 않는다.
- caller가 ZXing이 지원할 수 없는 format만 요청하면 `BarcodeFailureReason.UNSUPPORTED_FORMAT`으로 실패한다.
- `NotFoundException`은 `emptyList()`를 반환한다. no-code image는 expected negative result이기 때문이다.
- `FormatException`, `ChecksumException`, unexpected runtime decode error는
  `BarcodeException(DECODE_FAILED, ...)`가 된다.
- malformed encoded bytes는 provider-specific byte helper에서 `BarcodeException(MALFORMED_INPUT, ...)`가 된다.
- `Result.resultPoints`는 pixel-space `BarcodePoint` 값이 된다. point가 positive width와 height를 만들 때
  bounding box를 포함한다.
- `Result.rawBytes`는 `BarcodeOptions.includeRawBytes`가 true이고 ZXing이 non-empty bytes를 제공할 때만 노출한다.
- ZXing result metadata는 string-only metadata value로 변환한다.

## 테스트 전략

always-on test:

- QR image가 `BarcodeFormat.QR_CODE`로 decode된다.
- Code 128 image가 `BarcodeFormat.CODE_128`로 decode된다.
- requested format filtering이 mismatch barcode를 반환하지 않는다.
- no-code image가 empty list를 반환한다.
- `tryHarder = true`일 때 rotated QR image가 decode된다.
- malformed encoded byte helper가 `MALFORMED_INPUT`을 가진 `BarcodeException`을 던진다.
- ZXing mapping이 없을 때 unsupported requested format set이 `UNSUPPORTED_FORMAT`을 던진다.
- ZXing이 result point를 노출할 때 result metadata가 provider identity, backend format, pixel region을 포함한다.

test는 ZXing writer로 in-memory barcode image를 생성한다. 이 issue에서는 network-fetched fixture를
추가하지 않는다. shared fixture 범위는 #247이 소유한다.

## 문서화와 등록

다음을 업데이트한다.

- `settings.gradle.kts`
- `gradle/libs.versions.toml`
- `AGENTS.md`
- `README.md`
- `README.ko.md`
- `images-barcode-zxing/README.md`
- `images-barcode-zxing/README.ko.md`
- `.github/workflows/ci.yml`
- `.github/workflows/nightly-tests.yml`
- `.github/workflows/release.yml`
- `.github/workflows/publish-snapshot.yml`
- `.github/workflows/Examples.yml`

`bom/build.gradle.kts`가 published subproject를 순회하므로 published subproject에 대한 BOM constraint는 자동이다.

## 위험과 완화

- **ZXing maintenance-mode risk**: ZXing을 long-term exclusive dependency가 아니라 첫 OSS provider로 문서화한다.
- **API leakage**: ZXing class를 provider package 내부에 유지하고 #244 API model만 노출한다.
- **multi-barcode limitation**: ZXing의 simple reader path는 많은 format에서 image당 barcode 하나만 decode할 수 있다.
  이 provider boundary를 문서화하고 필요하면 multi-provider/multi-barcode 범위를 future issue work로 남긴다.
- **workflow drift**: CI/Nightly/release validation을 같은 PR에 추가한다.
- **test fragility**: external image file에 의존하지 않고 ZXing writer로 deterministic in-memory fixture를 생성한다.

## 인수 기준

- `:bluetape4k-images-barcode-zxing`이 존재하고 build된다.
- ZXing dependency는 ZXing provider module에서만 보인다.
- unit test가 QR, Code 128, no-code, rotated QR, malformed input, unsupported format behavior를 다룬다.
- root/module README locale set이 construction과 extension-style 사용을 문서화한다.
- CI/Nightly/release/publish workflow registration이 새 module을 포함한다.
- `./gradlew :bluetape4k-images-barcode-zxing:test`,
  `./gradlew :bluetape4k-images-barcode-zxing:compileTestKotlin --warning-mode all`,
  `./gradlew projects`, `actionlint`, `git diff --check`가 통과한다.
