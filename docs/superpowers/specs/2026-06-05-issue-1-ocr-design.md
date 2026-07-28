# Issue #1 OCR 설계 스펙

- 이슈: [#1](https://github.com/bluetape4k/bluetape4k-image/issues/1)
  `feat: OCR (Optical Character Recognition) 지원 추가`
- Milestone: `0.3.0`
- Branch/worktree: `feat/issue-1-ocr-support` at `.worktrees/feat-issue-1-ocr-support`
- Research input: `docs/superpowers/research/2026-06-05-issue-1-ocr-research-refresh.md`
- Follow-up scope guard: [#169](https://github.com/bluetape4k/bluetape4k-image/issues/169)

## 문제

`bluetape4k-image`에는 image loading, transformation, analysis, CAPTCHA, Ktor, Spring Boot, libvips module이 있지만 OCR surface는 없다. issue #1은 `ImmutableImage`에서 text extraction, suspend-friendly API, Korean/multilingual support, Docker/Testcontainers 기반 CI evidence를 요구한다.

이 기능은 core `bluetape4k-images` artifact가 native OCR library 또는 bundled model data에 의존하게 만들면 안 된다.

## 현재 evidence

- 기존 image-analysis extension은 `blurScore`, `dominantColors`, suspend variant처럼 `bluetape4k-images`에 있다.
- issue #83의 기존 OCR research는 Tesseract를 Tess4J로 사용하는 별도 `bluetape4k-images-ocr` module을 권장한다.
- Tess4J 5.19.0은 `ITesseract.doOCR(BufferedImage)`, `setDatapath`, `setLanguage`, `setOcrEngineMode`, `setPageSegMode`를 노출한다.
- Tesseract installation docs는 OCR engine과 language `traineddata` package를 분리한다.
- PaddleOCR 3.6.0은 더 큰 Python/model/document-AI stack이며 follow-up issue #169로 추적한다.
- 현재 agent environment에서는 local Docker를 사용할 수 없으므로 local Testcontainers verification은 skip 가능해야 한다. CI는 여전히 Testcontainers lane을 제공할 수 있다.

## 목표

- published `bluetape4k-images-ocr` module을 추가한다.
- `ImmutableImage`용 sync 및 suspend OCR extraction API를 제공한다.
- package/import와 artifact dependency로 OCR opt-in을 유지한다.
- 명시적인 language list와 tessdata path를 지원한다.
- English, Korean, Japanese용 Tesseract engine 및 language data prerequisite을 문서화한다.
- always-on unit test와 gated native OCR/Testcontainers test를 추가한다.
- Gradle, BOM, README locale set, CI, Nightly, coverage artifact, repo-local guidance에 module을 등록한다.
- OCR module이 보이도록 root README diagram/chart를 갱신하고 rendered visual asset이 source-backed 상태를 유지한다.

## 비목표

- Tess4J 또는 Tesseract dependency를 `bluetape4k-images`에 추가하지 않는다.
- published jar에 `*.traineddata` file을 bundle하지 않는다.
- 이 PR에서 PaddleOCR을 구현하지 않는다.
- cloud OCR provider나 credential-driven OCR service를 추가하지 않는다.
- container test가 host JVM native library loading을 증명한다고 주장하지 않는다.

## 설계 대안

### Option A: OCR extension을 `bluetape4k-images`에 직접 배치

제외한다. core image consumer가 resize/filter/encode만 필요하더라도 native OCR dependency, JNA, model/runtime concern을 모두 끌어오게 된다.

### Option B: Tess4J 기반 optional `bluetape4k-images-ocr` module 추가

선택한다. 기존 optional-native module pattern과 맞고 dependency를 격리하면서도, OCR artifact를 추가한 user가 extension-style code를 작성할 수 있다.

### Option C: 지금 PaddleOCR 또는 document-AI backend 구현

#1에서는 제외하고 #169로 이동한다. PaddleOCR에는 별도 runtime/model strategy, service/container boundary, 다른 CI evidence가 필요할 가능성이 크다.

## module boundary

다음을 추가한다:

```text
images-ocr/
  artifact: io.github.bluetape4k.image:bluetape4k-images-ocr
  package: io.bluetape4k.images.ocr
```

Gradle dependencies:

- public API가 `ImmutableImage`를 받으므로 `api(project(":bluetape4k-images"))`.
- `implementation(libs.tess4j)`.
- `implementation(libs.kotlinx.coroutines.core)`.
- `testImplementation(libs.bluetape4k.junit5)`.
- `testImplementation(libs.kotlinx.coroutines.test)`.
- gated container lane용 `testImplementation(libs.testcontainers)`.

Version catalog additions:

```toml
[versions]
tess4j = "5.19.0"

[libraries]
tess4j = { module = "net.sourceforge.tess4j:tess4j", version.ref = "tess4j" }
```

## public API

Package: `io.bluetape4k.images.ocr`

```kotlin
interface OcrEngine {
    fun extractText(image: ImmutableImage, options: OcrOptions = OcrOptions()): OcrResult

    suspend fun suspendExtractText(
        image: ImmutableImage,
        options: OcrOptions = OcrOptions(),
    ): OcrResult
}
```

```kotlin
data class OcrOptions(
    val languages: List<String> = listOf("eng"),
    val tessdataPath: Path? = null,
    val pageSegmentationMode: TesseractPageSegmentationMode? = null,
    val engineMode: TesseractEngineMode? = null,
    val variables: Map<String, String> = emptyMap(),
    val trimResult: Boolean = true,
) : Serializable
```

```kotlin
data class OcrResult(
    val text: String,
    val languages: List<String>,
    val confidence: Int? = null,
) : Serializable
```

```kotlin
enum class TesseractEngineMode(val value: Int)
enum class TesseractPageSegmentationMode(val value: Int)
```

```kotlin
class TesseractOcrEngine : OcrEngine

fun ImmutableImage.extractText(
    engine: OcrEngine = TesseractOcrEngine(),
    options: OcrOptions = OcrOptions(),
): OcrResult

suspend fun ImmutableImage.suspendExtractText(
    engine: OcrEngine = TesseractOcrEngine(),
    options: OcrOptions = OcrOptions(),
): OcrResult
```

API rule:

- 이번 Epic 요구에 따라 public KDoc과 comment는 한국어로 작성한다.
- `OcrOptions`와 `OcrResult`는 `Serializable`을 구현하고 `serialVersionUID`를 정의해야 한다.
- enum value는 Tess4J integer constant를 감싸므로 ordinary use에서 caller가 `ITessAPI` type을 import하지 않아도 된다.
- language code는 non-blank string으로 validate하고 Tesseract용으로 `+`로 join한다.
- option variable key는 non-blank string으로 validate한다.
- Tess4J/Tesseract는 blocking이므로 suspend OCR에는 `Dispatchers.IO`를 사용한다.
- suspend path에서는 broad exception handling 전에 `CancellationException`을 다시 throw한다.
- Tess4J failure는 sanitized message를 가진 OCR-specific runtime exception으로 감싼다.

## runtime behavior

`TesseractOcrEngine`은 extraction call마다 새 Tess4J `Tesseract` instance를 만든다. 이는 caller 간 mutable Tess4J state 공유를 피한다.

Configuration order:

1. `tessdataPath`가 있으면 적용한다.
2. joined `languages`를 적용하고 default는 `eng`로 한다.
3. optional page segmentation mode와 engine mode를 적용한다.
4. Tesseract variable을 적용한다.
5. `doOCR(image.awt())`를 실행한다.
6. `trimResult = true`이면 text를 trim한다.

default path는 `TESSDATA_PREFIX` 또는 platform default 같은 Tess4J/Tesseract resolution에 의존한다. language data가 없으면 명확하게 실패해야 한다.

## testing strategy

Always-on tests:

- `OcrOptionsTest`: language validation, variable validation, language join, serializable model behavior.
- `OcrExtensionsTest`: fake `OcrEngine`으로 sync/suspend extension delegation과 result propagation을 검증한다.
- `TesseractOcrEngineConfigurationTest`: practical한 seam 또는 focused internal adapter로 option-to-Tess4J configuration을 cover한다.

Gated host-native tests:

- `-Docr.enabled=true`로 enable한다.
- Java2D로 high-contrast English text image를 생성한다.
- Tess4J가 `ImmutableImage.awt()`에서 expected English text를 extract하는지 검증한다.
- missing `tessdataPath` 또는 missing language data가 명확한 exception을 만드는지 검증한다.
- 해당 pack이 설치되어 있으면 language list가 `eng`, `kor`, `jpn`을 수용하는지 검증한다. Korean/Japanese exact OCR text matching은 font/rendering reliability가 language-pack resolution보다 낮으면 non-blocking으로 둘 수 있다.

Gated Testcontainers tests:

- `-Docr.container.enabled=true`로 enable한다.
- GitHub Actions Ubuntu LTS image family를 기반으로 하는 test-owned Dockerfile을 선호하고 `tesseract-ocr`, `tesseract-ocr-eng`, `tesseract-ocr-kor`, `tesseract-ocr-jpn`을 설치한다. 검증되지 않은 public OCR image에 의존하지 않는다.
- fixture image를 copy하거나 generate하고 container에서 Tesseract CLI를 실행한다.
- English용 CLI extraction과 multilingual support용 language-pack availability를 검증한다.
- 이는 containerized runtime을 증명하며 host JVM Tess4J native loading을 증명하지 않는다고 명시적으로 기록한다.

## CI 및 Nightly

`ci.yml`:

- `changes.outputs`와 `paths-filter`에 `images-ocr`를 추가한다.
- `test-images-ocr` job을 추가한다.
- OCR job에서 `tesseract-ocr`, `tesseract-ocr-eng`, `tesseract-ocr-kor`, `tesseract-ocr-jpn`, `fonts-noto-cjk`를 설치한다.
- Gradle 전에 `tesseract --list-langs`를 실행하고 `eng`, `kor`, `jpn`이 없으면 early fail한다.
- `./gradlew :bluetape4k-images-ocr:test -Docr.enabled=true -Docr.container.enabled=true --no-daemon`을 실행한다.
- `test-results-images-ocr`를 upload한다.
- OCR job을 `ci-status.needs`에 추가한다.

`nightly-tests.yml`:

- full scope에 `test-images-ocr`를 추가한다.
- `coverage-images-ocr`를 generate/upload한다.
- OCR job과 coverage artifact를 nightly status/coverage aggregation에 추가한다.

workflow edit에는 push 전 `actionlint`와 `rg "\\'" .github/workflows`가 필요하다.

## documentation 및 diagram

다음을 갱신한다:

- root `README.md`와 `README.ko.md`의 module table, requirement, installation, usage, troubleshooting, module README link list.
- 새 `images-ocr/README.md`와 `images-ocr/README.ko.md`.
- repo-local `AGENTS.md` module list와 command section.
- root README visual assets:
  - `docs/images/readme-diagrams/root-readme-overview-01.svg`
  - `docs/images/readme-diagrams/root-readme-overview-01.png`
  - `docs/images/readme-diagrams/root-readme-overview-01.dot`
  - `docs/images/readme-diagrams/root-readme-overview-01.plain`
  - `docs/images/readme-diagrams/root-readme-overview-01-graphviz.svg`
  - `docs/images/readme-diagrams/root-readme-overview-01-graphviz.png`
  - `docs/images/readme-charts/root-readme-module-chart-01.svg`
  - `docs/images/readme-charts/root-readme-module-chart-01.png`
  - `docs/images/readme-diagrams/bluetape4k-image-architecture-01.svg`
  - `docs/images/readme-diagrams/bluetape4k-image-architecture-01.png`

diagram label은 English로 유지하고 README file은 PNG만 embed한다. diagram update는 `$bluetape4k-diagram` validation을 따라야 한다. SVG parse, PNG 존재, connector-heavy diagram의 Graphviz evidence, README link resolution, rendered PNG visual inspection을 모두 확인한다.

## 위험과 대응

| Risk | Severity | Mitigation |
|---|---:|---|
| Tess4J native dependency가 developer machine에서 실패 | P1 | OCR을 optional module에 유지하고 install/traineddata를 문서화하며 clear exception을 제공 |
| Testcontainers가 host JVM native loading을 증명하지 못함 | P1 | 별도 host-native `-Docr.enabled=true` lane을 유지하고 evidence boundary를 명시 |
| font/rasterization 때문에 Korean/Japanese OCR text matching이 flaky함 | P2 | CI에서는 language-pack availability를 검증하고, exact non-Latin text matching이 불안정하면 follow-up으로만 둠 |
| PaddleOCR이 scope를 크게 확장 | P2 | #169로 추적하고 #1 baseline을 집중 유지 |
| module 추가 후 README diagram이 drift | P1 | SVG/PNG/dot/plain asset을 갱신하고 diagram skill로 validate |
| 새 module이 CI/Nightly/BOM registration에서 누락 | P1 | module-registration checklist를 plan과 Step 6/6-R review에 포함 |

## acceptance criteria

- `bluetape4k-images-ocr`가 일반 Gradle/BOM path를 통해 등록되고 publish된다.
- OCR module package에서 `ImmutableImage.extractText()`와 `suspendExtractText()` extension function을 사용할 수 있다.
- `OcrOptions`는 `eng`, `kor`, `jpn`, custom language list, explicit `tessdataPath`, PSM/OEM, variable을 지원한다.
- Tesseract/Tess4J failure는 명확한 OCR exception으로 드러난다.
- unit test는 native OCR dependency 없이 통과한다.
- Tesseract/traineddata가 설치되어 있고 `-Docr.enabled=true`이면 native OCR test가 통과한다.
- Docker를 사용할 수 있고 `-Docr.container.enabled=true`이면 Testcontainers OCR smoke가 통과한다.
- README/README.ko 및 `images-ocr` README/README.ko가 install, usage, multilingual setup, troubleshooting을 문서화한다.
- root README diagram/chart가 OCR을 포함하고 diagram validation을 통과한다.
- CI/Nightly가 `bluetape4k-images-ocr` test와 coverage visibility를 포함한다.
- Step 6-R review가 `P0 = 0`, `P1 = 0`으로 종료된다.

## spec DoD

| Item | Status |
|---|---|
| issue #1 requirement restated | Done |
| prior research 및 current primary source 반영 | Done |
| 최소 세 가지 design approach 검토 | Done |
| selected approach가 OCR dependency를 core module 밖에 유지 | Done |
| PaddleOCR scope expansion을 follow-up으로 등록 | Done (#169) |
| public API shape 지정 | Done |
| test, CI, Nightly, Testcontainers strategy 지정 | Done |
| diagram impact 명시적으로 결정 | Done |
| risk 및 mitigation 문서화 | Done |
