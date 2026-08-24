# Issue #570 외부 이미지 입력 strict decode 설계

- 이슈: [#570](https://github.com/bluetape4k/bluetape4k-image/issues/570)
- 상위 Epic: [#585](https://github.com/bluetape4k/bluetape4k-image/issues/585)
- 마일스톤: `1.0.0`
- train: 입력·barcode `#570 → #577 → #581`
- branch/worktree: `fix/image-barcode-strict-input` / `.worktrees/fix/image-barcode-strict-input`
- 기준 ref: `origin/develop` (`c737ed38ac184b1922590ab256c484030f38a9cd`)

## 문제와 현재 증거

외부에서 받은 encoded image를 barcode와 thumbnail adapter가 서로 다른 경계로
처리합니다. core에는 이미 `immutableExternalImageOf(ByteArray, ImageDecodeLimits)`가
있지만, 대상 adapter는 이를 사용하지 않습니다.

- `images-barcode-api/src/main/kotlin/io/bluetape4k/images/barcode/BarcodeInputExtensions.kt`의
  bytes/path/stream/source 확장은 compatibility `immutableImageOf(...)`를 직접 호출합니다.
- `images-barcode-zxing/src/main/kotlin/io/bluetape4k/images/barcode/zxing/ZxingBarcodeReader.kt`의
  concrete `ByteArray` overload만 malformed 예외를 `BarcodeException(MALFORMED_INPUT)`으로
  정규화합니다. `BarcodeReader` 정적 타입 호출은 loader 예외가 그대로 노출됩니다.
- `images-ktor/src/main/kotlin/io/bluetape4k/images/ktor/ImageThumbnailKtorRoutes.kt`는
  byte·pixel·side 한계를 적용하지만 dimension probe가 `null`이면 strict helper가 아니라
  bounded compatibility loader로 진행합니다.
- `images/src/main/kotlin/io/bluetape4k/images/ImmutableImageSupport.kt`의 strict bytes
  overload는 ImageIO probe와 bounded metadata reader를 모두 실패한 입력을 decode 전에
  거부할 수 있습니다. path/stream/source strict overload는 아직 없습니다.
- 관련 테스트는 `bluetape4k-assertions`를 이미 사용하지만, interface-typed malformed
  입력과 모든 external adapter의 동일한 failure contract를 직접 고정하지 않습니다.

## 목표

1. 신뢰하지 않는 barcode·thumbnail 입력이 encoded bytes, decoded pixels, maximum side를
   동일 정책으로 decode 전에 검사하도록 합니다.
2. core의 strict helper를 `Path`, caller-owned `InputStream`/`BufferedSource`,
   source-owned `Source`까지 확장해 bounded read와 metadata fallback을 한 곳에서
   관리합니다.
3. `BarcodeReader` interface-typed 입력과 ZXing concrete 입력의 malformed·unsupported
   예외 계약을 동일하게 유지합니다.
4. Ktor thumbnail route가 `immutableExternalImageOf(bytes, config.toDecodeLimits())`를
   사용하도록 하고, `CancellationException`은 기존처럼 응답 오류로 축약하지 않습니다.
5. public API와 새 KDoc는 provider-neutral 타입과 한국어 계약을 유지합니다.

## 비목표

- ZXing dependency를 `images-barcode-api` 또는 core로 이동하지 않습니다.
- barcode provider capability, barcode result model, OCR, CAPTCHA, Spring Boot,
  VIPS, benchmark 구조를 변경하지 않습니다.
- 새로운 decode limit 설정 키나 HTTP status mapping을 추가하지 않습니다.
- `#577`의 ZXing provider 진단·성능 개선과 `#581`의 전체 통합 회귀를 이 PR에서
  선행 구현하지 않습니다.

## 선택지와 결정

### 선택지 A — core strict helper overload 확장 (선택)

core에 `immutableExternalImageOf(Path/InputStream/BufferedSource/Source, limits)`를
추가합니다. path와 stream은 encoded byte 한계를 먼저 적용하고, 모든 overload가 strict
bytes 구현으로 모여 ImageIO probe → bounded metadata reader → decode 후 dimension
재검증 순서를 공유합니다. barcode API와 Ktor는 이 helper만 호출합니다.

이 선택은 입력 경계를 한 구현으로 단일화하고, stream 소유권·`Source` close 정책을
현재 `immutableImageOf` 계약과 맞출 수 있습니다. core public API 확장이지만 기존
overload를 제거하지 않으므로 source compatibility를 유지합니다.

### 선택지 B — 각 adapter에서 bounded read와 metadata fallback 복제

barcode API와 Ktor에 별도 byte reader와 metadata 처리를 둡니다. 변경 범위는 처음에는
작아 보이지만 limit 기본값, unknown dimensions, stream close, 오류 문구가 다시
분기되어 다음 provider와 adapter에서 재발합니다. 정책 단일화 목표와 맞지 않아
거부합니다.

### 선택지 C — `BarcodeReader`에 encoded input을 직접 추가

provider가 byte/path/stream 디코딩을 직접 소유하도록 interface를 확장합니다. 이는
provider-neutral API가 decoder lifecycle과 dependency를 떠안게 만들고, Ktor와 다른
provider가 같은 strict 정책을 공유할 수 없으므로 거부합니다.

## 설계

### 1. core strict loader

다음 overload를 추가합니다.

```kotlin
fun immutableExternalImageOf(
    path: Path,
    limits: ImageDecodeLimits = ImageDecodeLimits.ExternalInput,
): ImmutableImage

fun immutableExternalImageOf(
    inputStream: InputStream,
    limits: ImageDecodeLimits = ImageDecodeLimits.ExternalInput,
): ImmutableImage

fun immutableExternalImageOf(
    source: BufferedSource,
    limits: ImageDecodeLimits = ImageDecodeLimits.ExternalInput,
): ImmutableImage

fun immutableExternalImageOf(
    source: Source,
    limits: ImageDecodeLimits = ImageDecodeLimits.ExternalInput,
): ImmutableImage
```

- `Path`는 `Files.size`로 encoded limit을 확인한 뒤 bounded stream으로 읽습니다.
- `InputStream`과 `BufferedSource`는 caller가 소유하므로 닫지 않습니다.
- `Source`는 기존 `immutableImageOf(Source)`와 같은 정책으로 buffer하고 닫습니다.
- 각 overload는 strict bytes overload에 위임해 unknown dimensions를 제한 없는
  decoder로 넘기지 않습니다.
- `CancellationException`은 재전파하고, 기존 `IllegalArgumentException` 계약과
  sanitized decode message를 유지합니다.

### 2. provider-neutral barcode input boundary

`BarcodeInputExtensions.kt`의 네 overload는 strict loader를 사용합니다. loader 또는
provider가 `BarcodeException`을 이미 만든 경우 그대로 재전파하고, `CancellationException`
도 그대로 재전파합니다. 그 밖의 malformed/unsupported external input은 다음 고정
계약으로 변환합니다.

```kotlin
BarcodeException(
    reason = BarcodeFailureReason.MALFORMED_INPUT,
    message = "Barcode input could not be decoded as an image.",
    cause = cause,
)
```

message에는 path, payload, credential을 포함하지 않습니다. `options`와 provider-neutral
`BarcodeResult` public 타입은 변경하지 않습니다.

### 3. ZXing concrete boundary

`ZxingBarcodeReader.readBarcodes(ByteArray, ...)`도 strict bytes helper를 사용하고
`CancellationException`을 먼저 재전파합니다. concrete receiver와 interface receiver가
동일한 malformed reason을 반환하는 회귀 테스트를 추가합니다.

### 4. Ktor thumbnail boundary

multipart 수신 단계에서는 현재의 streamed file, non-empty, encoded byte 검사를 유지합니다.
선택적인 `probeImageDimensions` 호출과 compatibility decode를 제거하고,
`immutableExternalImageOf(uploadBytes, config.toDecodeLimits())`를 한 번 호출합니다.
따라서 ImageIO와 bounded metadata reader가 모두 dimensions를 확인하지 못하면 decode
전에 `IllegalArgumentException`이 발생하고, route는 기존 fixed `Invalid image payload.`
응답을 반환합니다. `CancellationException`은 `respondImageRoute`가 잡지 않습니다.

## 실패·소유권 계약

| 입력 | byte limit | unknown dimensions | close 책임 | 외부 예외 |
|---|---|---|---|---|
| `ByteArray` | 즉시 검사 | decode 전 거부 | 없음 | barcode API는 `MALFORMED_INPUT`으로 정규화 |
| `Path` | `Files.size` 후 bounded read | decode 전 거부 | helper가 연 stream | 같은 계약 |
| `InputStream` | bounded read | decode 전 거부 | caller | 같은 계약, cancellation 재전파 |
| `BufferedSource` | bounded read | decode 전 거부 | caller | 같은 계약 |
| `Source` | bounded read | decode 전 거부 | helper가 buffer를 닫음 | 같은 계약 |

내부 invariant에는 `check`를 사용하지 않고, caller 입력 검증에는 기존
`requirePositiveNumber`와 `require*` helper를 유지합니다. production code에 `!!`,
`println`, suspend `runCatching`을 추가하지 않습니다.

## 테스트 전략

모든 새 테스트는 JUnit 5, descriptive backtick 이름, Given/When/Then 구조와
`io.bluetape4k.assertions`를 사용합니다. 예외 검증은
`io.bluetape4k.assertions.assertFailsWith`만 사용합니다.

- `images`: strict `Path`/`InputStream`/`BufferedSource`/`Source` overload의 valid,
  encoded-limit, unknown-dimensions, caller/helper close ownership을 검증합니다.
- `images-barcode-api`: malformed bytes를 `BarcodeReader` interface static type으로
  bytes/path/stream/source 각각 호출해 `MALFORMED_INPUT`을 확인하고,
  `CancellationException`과 provider `BarcodeException` 재전파를 확인합니다.
- `images-barcode-zxing`: concrete receiver와 interface receiver의 malformed reason
  equality를 확인하고 valid QR regression을 유지합니다.
- `images-ktor`: unknown-format/malformed payload, encoded·pixel·side limit, valid
  thumbnail regression을 유지하고 writer/provider cancellation이 client error로
  축약되지 않는지 검증합니다.
- Testcontainers와 native backend는 이 slice의 동작 경계가 아니므로 실행하지
  않습니다. 해당 검증은 Epic의 OCR/VIPS train에서 순차 수행합니다.

최소 검증 순서는 core targeted test → barcode API/Zxing/Ktor targeted test → 세 모듈
전체 test → `detekt`/compile 및 `git diff --check`입니다. Kover는 전체 모듈 테스트가
통과한 뒤 report-only로 실행합니다.

## 호환성·롤백

- 기존 `immutableImageOf(...)` overload와 기존 `BarcodeReader`/`BarcodeResult` API는
  제거하지 않습니다. external adapter의 malformed 입력 예외만 provider-neutral
  계약으로 정규화합니다.
- rollback은 이 branch의 core overload와 세 adapter 호출을 함께 되돌리는 방식이며,
  `#577` 또는 `#581`의 변경을 포함하지 않습니다.
- merge 전에는 exact head CI와 7-Tier review를 다시 읽고, 다음 train head로 전진하지
  않습니다. merge는 별도 fresh approval이 필요합니다.

## 수용 기준

- [ ] core의 네 external overload가 동일한 strict decode 순서와 stream 소유권을 지킵니다.
- [ ] interface-typed와 concrete ZXing malformed 입력이 모두
      `BarcodeException(MALFORMED_INPUT)`을 반환합니다.
- [ ] encoded-size, pixel-count, side-limit 초과 입력이 decoder 호출 전에 거부됩니다.
- [ ] unknown dimensions 입력이 compatibility decoder로 우회되지 않습니다.
- [ ] Ktor multipart 회귀가 `bluetape4k-assertions` matcher로 검증됩니다.
- [ ] provider dependency가 API/core 밖으로 새지 않고 public API는 provider-neutral 타입만
      노출합니다.
- [ ] fresh targeted/module tests, compile/detekt, `git diff --check`가 통과합니다.
- [ ] PR body가 한국어이고 마지막에 `## DoD Status`를 가지며, #585와 milestone
      `1.0.0`을 연결합니다.

## Superpowers writer DoD

- SPW-01 — PASS: issue #570/#585, 기준 ref, 대상 source path, API·오류·소유권 증거와
  비목표를 고정했습니다.
- SPW-02 — PASS: 문제, 목표, 선택지, module/API 경계, 실패·호환성·테스트·수용 기준을
  포함했습니다.
- SPW-03 — PASS: 한국어 기술 문체와 기존 API/명령/식별자 보존을 검토했습니다.
- SPW-04 — PASS: 현재 `origin/develop` source와 live issue 내용을 대조했습니다.
- SPW-05 — PASS: Markdown read-back, placeholder/모순/범위 검사를 완료했습니다.

## DoD Status

상태: **SPEC READY — 구현 전 사용자 문서 검토 대기**

