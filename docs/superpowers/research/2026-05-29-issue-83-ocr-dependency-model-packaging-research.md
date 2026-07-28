# Issue #83 OCR dependency 및 model packaging 연구

- 이슈: [#83](https://github.com/bluetape4k/bluetape4k-image/issues/83)
- 구현 대상: [#1](https://github.com/bluetape4k/bluetape4k-image/issues/1)
- 날짜: 2026-05-29
- 범위: OCR runtime 선택, traineddata packaging, module boundary, CI 전략.

## 요약

Tesseract를 Tess4J를 통해 사용하는 별도 `bluetape4k-images-ocr` module로 진행한다. `bluetape4k-images`에는 OCR dependency를 추가하지 않고, published artifact에는 기본적으로 traineddata file을 bundle하지 않는다.

Tesseract는 Java server-side 사용에서 가장 위험이 낮은 open-source OCR baseline이다. engine과 official `tessdata` repository는 Apache-2.0 license이고, Tess4J는 검증된 JVM/JNA wrapper이며, Korean/Japanese/multilingual 지원은 숨겨진 runtime download가 아니라 명시적인 traineddata 설치로 처리할 수 있다.

## 현재 repository 적합성

- 현재 core module은 순수 image processing과 optional integration module로 구성되어 있다. 무거운 native/runtime dependency는 이미 `bluetape4k-images-vips-java21`, `bluetape4k-images-vips-java25` 같은 전용 module에 둔다.
- OCR issue는 `ImmutableImage.extractText()`와 `suspendExtractText()`를 요구하지만, OCR을 `bluetape4k-images`에 직접 추가하면 모든 consumer가 native OCR dependency를 강제로 받게 된다. extension은 OCR module package에만 둔다.
- 기존 native-lifecycle guidance를 적용한다. native test는 순차 실행해야 하며, Testcontainers 기반 verification은 다른 container lane과 병렬 실행하지 않는다.

## 후보 평가

| 후보 | 결정 | 근거 |
|---|---|---|
| Tess4J + Tesseract | 권장 | 성숙한 JVM wrapper이고 Apache-2.0과 호환되는 stack이며, local/offline OCR을 지원하고 명시적인 `tessdata` directory와 함께 동작한다. |
| Tesseract CLI process wrapper | 첫 구현에서는 제외 | isolation은 쉽지만 lifecycle/error handling이 나쁘고, pool을 만들지 않으면 call마다 startup 비용이 크며, library API에는 어색하다. |
| Cloud OCR APIs | 제외 | network, credential, billing, privacy, provider lock-in이 reusable local image library에 맞지 않는다. |
| DJL/OCR model wrappers | 보류 | ML pipeline에는 더 적합할 수 있지만 OCR model packaging과 text layout 품질에는 별도 model strategy가 필요하다. |
| Pure Java OCR libraries | 제외 | Tesseract와 비교해 maintenance와 accuracy 측면의 설명력이 약하다. |

## 권장 module boundary

non-core module을 추가한다:

```text
images-ocr/
  artifact: io.github.bluetape4k.image:bluetape4k-images-ocr
  package: io.bluetape4k.images.ocr
```

의존성:

- public API가 `ImmutableImage`를 받으므로 `api(project(":bluetape4k-images"))`를 사용한다.
- version catalog가 관리하는 `implementation(net.sourceforge.tess4j:tess4j)`를 추가한다.
- suspend wrapper용으로 `implementation(libs.kotlinx.coroutines.core)`를 추가한다.
- test dependency는 JUnit 5, bluetape4k assertion, 그리고 native runtime을 container로 검증할 때만 Testcontainers를 포함한다.

Public API 형태:

```kotlin
interface OcrEngine {
    fun extractText(image: ImmutableImage, options: OcrOptions = OcrOptions()): OcrResult
    suspend fun extractTextSuspend(image: ImmutableImage, options: OcrOptions = OcrOptions()): OcrResult
}
```

```kotlin
data class OcrOptions(
    val languages: List<String> = listOf("eng"),
    val tessdataPath: Path? = null,
    val pageSegmentationMode: Int? = null,
    val engineMode: Int? = null,
) : Serializable
```

```kotlin
data class OcrResult(
    val text: String,
    val languageHint: List<String>,
    val confidence: Double? = null,
) : Serializable
```

편의 extension은 이 module에서만 노출한다:

```kotlin
fun ImmutableImage.extractText(
    engine: OcrEngine = TesseractOcrEngine(),
    options: OcrOptions = OcrOptions(),
): OcrResult
```

## model 및 data packaging

첫 release에서는 library artifact에 `*.traineddata` file을 bundle하지 않는다. 대신 다음 규칙을 사용한다:

- 먼저 option에서 `tessdataPath`를 resolve한다.
- Tess4J/Tesseract가 안정적으로 resolve할 수 있을 때만 `TESSDATA_PREFIX` 또는 platform default로 fallback한다.
- `eng`, `kor`, `jpn` 설치 예제를 문서화한다.
- language data가 명시적인 runtime prerequisite임을 README section에 적는다.
- test-only traineddata는 published jar가 아니라 container image 또는 CI setup에 둔다.

이 방식은 artifact를 작게 유지하고 language/model update가 library release cadence에 묶이지 않게 한다.

## CI 및 verification 전략

두 test tier를 사용한다:

1. fake `OcrEngine` 또는 작은 image encoding fixture를 사용하는 JVM unit test.
2. `-Docr.enabled=true`로 gate하고 순차 실행하는 native OCR integration test.

구현 후 권장 validation:

```bash
./gradlew :bluetape4k-images-ocr:test
./gradlew :bluetape4k-images-ocr:test -Docr.enabled=true
```

native lane은 다음을 검증해야 한다:

- generated image 또는 fixture image에서 `eng` text extraction.
- CI image가 traineddata package를 설치한 경우 `kor`, `jpn` language-pack resolution.
- `tessdataPath` 또는 language data가 없을 때 명확한 failure message.
- 넓은 exception handling 전에 suspend API의 cancellation propagation.

## Issue #1 handoff

구현 acceptance criteria:

- core `bluetape4k-images` dependency를 변경하지 않고 `bluetape4k-images-ocr`를 추가한다.
- OCR module package에서 sync/suspend OCR API와 `ImmutableImage` extension function을 제공한다.
- 명시적인 language list와 `tessdataPath`를 지원한다.
- `README.md`와 `README.ko.md`에 native Tesseract 및 traineddata prerequisite을 문서화한다.
- gated native integration test를 추가하고 순차 실행을 유지한다.
- module이 publish 대상이면 BOM/module/README/CI registration을 추가한다.

## 출처

- Tesseract README and license: https://github.com/tesseract-ocr/tesseract
- Tesseract installation and language packages: https://github.com/tesseract-ocr/tessdoc/blob/main/Installation.md
- Tesseract traineddata repository: https://github.com/tesseract-ocr/tessdata
- Tess4J Maven metadata checked on 2026-05-29: latest `5.16.0`
- Apache Tika Tess4J parser notes: https://tika.apache.org/docs/4.0.0-SNAPSHOT/configuration/parsers/tess4j-parser.html
