# Issue #244 Barcode API 설계 명세

- 이슈: [#244](https://github.com/bluetape4k/bluetape4k-image/issues/244)
- 상위 epic: [#215](https://github.com/bluetape4k/bluetape4k-image/issues/215)
- 마일스톤: `0.4.0`
- branch/worktree: `.worktrees/feat-issue-244-barcode-api`의 `feat/issue-244-barcode-api`

## 문제

repository가 ZXing, BoofCV, commercial SDK, native adapter를 추가하기 전에 barcode와 QR 추출에는
안정적인 bluetape4k API가 필요하다. 이 API는 concrete decoder class를 caller나 core
`bluetape4k-images` artifact로 누수하지 않으면서 provider module이 image를 decode할 수 있게 해야 한다.

## 현재 증거

- `images-ocr`는 Tess4J/Tesseract를 `bluetape4k-images` 밖에 유지하고 opt-in module에서
  `ImmutableImage` sync/suspend extension을 노출한다.
- `images`에는 이미 serializable value object, provider identity metadata, validation helper,
  suspend wrapper를 가진 backend-neutral detection model이 있다.
- #215는 처음부터 API/provider 분리를 요구한다. 대상은 `images-barcode-api`,
  `images-barcode-zxing`, optional future provider다.
- #245는 ZXing 구현을 소유한다. #246은 BoofCV research를 소유한다. #247은 shared fixture와
  provider capability documentation을 소유한다.

## 목표

- published `bluetape4k-images-barcode-api` module을 추가한다.
- provider-neutral result model, provider metadata, option, failure reason, sync/suspend entry point를 정의한다.
- concrete barcode decoder dependency를 추가하지 않고 `ImmutableImage`, `ByteArray`, `Path`,
  `InputStream`, `Source` input entry point를 지원한다.
- 필요에 따라 Gradle, BOM, root/module README locale set, repo-local `AGENTS.md`, CI, Nightly,
  release/publish validation list, example path filter에 module을 등록한다.
- API test는 always-on, deterministic, pure JVM으로 유지한다.

## 비목표

- 이 issue에서는 ZXing, BoofCV, OpenCV, ZBar, Dynamsoft, Aspose를 구현하지 않는다.
- `bluetape4k-images`나 API module에 barcode dependency를 추가하지 않는다.
- API test에 필요하지 않으면 이 issue에서 real barcode fixture를 추가하지 않는다. shared fixture 범위는 #247에 속한다.
- OCR API를 변경하지 않는다.

## 설계 선택지

### 선택지 A: API와 ZXing을 하나의 `images-barcode` module에 포함

거부한다. 이 방식은 ZXing이 public API의 형태를 결정하게 만들고, future provider가 bluetape4k contract가
아니라 ZXing 개념에 맞추게 한다.

### 선택지 B: API module을 먼저 두고 provider를 나중에 추가

선택한다. dependency boundary를 깨끗하게 유지하고 #245, #246, future provider module이 하나의 contract를
공유할 수 있게 한다.

### 선택지 C: generic `images.detection` model을 직접 재사용

primary API로는 거부한다. barcode extraction에는 decoded payload, symbology, checksum, raw bytes,
failure semantics가 있으며 이는 일반 object detection fact가 아니다. geometry style은 detection model과
맞출 수 있지만 barcode result는 domain-specific하게 유지해야 한다.

## Module 경계

다음을 추가한다.

```text
images-barcode-api/
  artifact: io.github.bluetape4k.image:bluetape4k-images-barcode-api
  package: io.bluetape4k.images.barcode
```

Gradle dependency:

- public API가 `ImmutableImage`를 받으므로 `api(project(":bluetape4k-images"))`.
- suspend wrapper를 위한 `implementation(libs.kotlinx.coroutines.core)`.
- API test를 위한 `testImplementation(libs.bluetape4k.junit5)`와
  `testImplementation(libs.kotlinx.coroutines.test)`.

## Public API

Package: `io.bluetape4k.images.barcode`

core model:

- `BarcodeFormat`: `QR_CODE`, `CODE_128`, `EAN_13`, `EAN_8`, `UPC_A`, `UPC_E`,
  `DATA_MATRIX`, `AZTEC`, `PDF_417`, `CODABAR`, `ITF`, `UNKNOWN` 같은 common value를
  가진 stable symbology enum.
- `BarcodeProviderIdentity`: provider name, optional version/backend, metadata.
- `BarcodePoint`, `BarcodeBoundingBox`, `BarcodeRegion`: pixel 또는 normalized localization data.
- `BarcodeResult`: decoded text, format, provider, optional region, confidence, quality, raw bytes,
  failure-free metadata, optional raw backend format.
- `BarcodeFailureReason`: `NO_BARCODE`, `UNSUPPORTED_FORMAT`, `MALFORMED_INPUT`, `DECODE_FAILED`,
  `PROVIDER_UNAVAILABLE`, `CANCELLED`, `UNKNOWN`.
- `BarcodeException`: `BarcodeFailureReason`을 담는 sanitized failure exception.
- `BarcodeOptions`: requested formats, `tryHarder`, `includeRawBytes`, `metadata`,
  optional minimum confidence filter.

engine과 entry point:

```kotlin
fun interface BarcodeReader {
    fun readBarcodes(image: ImmutableImage, options: BarcodeOptions): List<BarcodeResult>
}

fun BarcodeReader.readBarcodes(image: ImmutableImage): List<BarcodeResult>

fun ImmutableImage.extractBarcodes(
    reader: BarcodeReader,
    options: BarcodeOptions = BarcodeOptions(),
): List<BarcodeResult>

suspend fun ImmutableImage.suspendExtractBarcodes(
    reader: BarcodeReader,
    options: BarcodeOptions = BarcodeOptions(),
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
): List<BarcodeResult>
```

input helper:

```kotlin
fun BarcodeReader.readBarcodes(bytes: ByteArray, options: BarcodeOptions = BarcodeOptions()): List<BarcodeResult>
fun BarcodeReader.readBarcodes(path: Path, options: BarcodeOptions = BarcodeOptions()): List<BarcodeResult>
fun BarcodeReader.readBarcodes(input: InputStream, options: BarcodeOptions = BarcodeOptions()): List<BarcodeResult>
fun BarcodeReader.readBarcodes(source: Source, options: BarcodeOptions = BarcodeOptions()): List<BarcodeResult>
```

## 검증 규칙

- 모든 value object는 `Serializable`이며 `serialVersionUID`를 정의한다.
- public value model은 validation이 필요할 때 private constructor와 companion `invoke`를 사용한다.
- caller input validation은 사용할 수 있는 경우 bluetape4k `require*` helper를 사용한다.
- metadata map은 blank가 아닌 string key와 value를 사용한다.
- confidence와 quality 값은 nullable이다. 값이 있으면 finite여야 하고 `0.0..1.0` 범위 안에 있어야 한다.
- raw bytes는 optional이며 provider가 제어한다. API는 모든 provider가 raw bytes를 노출하도록 요구하지 않는다.
- suspend wrapper는 `withContext(dispatcher)`를 사용하고 `CancellationException`을 삼키지 않는다.

## 테스트 전략

always-on test:

- option validation과 format filtering.
- provider identity validation.
- region과 bounding-box validation.
- fake reader를 사용하는 sync/suspend extraction extension delegation.
- provider가 `CancellationException`을 보고할 때 suspend extraction cancellation propagation.
- byte/path/input-stream/source helper가 `immutableImageOf(...)`를 통해 load하고 reader로 delegate한다.
- failure exception이 sanitized message와 reason을 유지한다.
- serializable model smoke test.

#244에서는 API module이 shared mutable state, background job, cache, provider lifecycle을
도입하지 않으므로 concurrency stress helper가 필요하지 않다.

## 문서화와 등록

다음을 업데이트한다.

- `settings.gradle.kts`
- `AGENTS.md`
- `README.md`
- `README.ko.md`
- `images-barcode-api/README.md`
- `images-barcode-api/README.ko.md`
- `.github/workflows/ci.yml`
- `.github/workflows/nightly-tests.yml`
- `.github/workflows/release.yml`
- `.github/workflows/publish-snapshot.yml`
- `.github/workflows/Examples.yml` path filters if examples watch all modules

`bom/build.gradle.kts`가 published subproject를 순회하므로 published subproject에 대한 BOM constraint는
자동이다. 다만 이 module을 example이나 benchmark로 취급하면 안 된다.

## 위험과 완화

- **API가 ZXing에 과적합됨**: ZXing-specific name을 API 밖에 유지하고 provider metadata를 string-only로 문서화한다.
- **geometry 모호성**: coordinate space를 명시적으로 model하고 image dimension을 사용할 수 있을 때만 pixel bounds를 검증한다.
- **suspend dispatcher 불일치**: local CPU-bound decoder는 기본값을 `Dispatchers.Default`로 두고,
  service/native provider가 `Dispatchers.IO`를 전달하게 한다.
- **workflow drift**: CI, Nightly job과 path filter를 같은 PR에 추가한다.
- **docs drift**: README example은 구현 후 실제 source name을 사용해야 한다.

## 인수 기준

- `:bluetape4k-images-barcode-api`가 존재하고 ZXing/BoofCV 없이 build된다.
- public model과 entry point가 자세한 한국어 KDoc을 가진다.
- unit test가 validation, delegation, input helper, exception을 다룬다.
- root/module README locale set이 API/provider split을 설명한다.
- CI/Nightly/release validation이 새 module을 포함한다.
- `./gradlew projects`, targeted tests, compile, `actionlint`, `git diff --check`가 통과한다.
- 구현 전 local-equivalent Step 2-R/3-R review가 P0/P1 = 0을 보고한다.
