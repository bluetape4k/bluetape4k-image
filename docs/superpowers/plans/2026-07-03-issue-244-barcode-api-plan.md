# Issue #244 Barcode API 구현 계획

> **Agentic worker 필수 지침:** 이 계획은 task 단위로 구현한다. 구현 표면은 superpowers:subagent-driven-development(권장) 또는 superpowers:executing-plans를 사용한다. 진행 추적에는 checkbox(`- [ ]`) 문법을 사용한다.

**목표:** barcode 추출을 위한 provider-neutral `bluetape4k-images-barcode-api` module을 추가한다.

**아키텍처:** API module은 `ImmutableImage`를 사용하기 위해 `bluetape4k-images`에 의존하고, 순수 contract, model, exception, sync/suspend helper만 노출한다. ZXing, BoofCV 같은 concrete provider는 별도 module에 둔다.

**기술 스택:** Kotlin/JVM, Gradle Kotlin DSL, `ImmutableImage`, Okio `Source`, Kotlin coroutine, bluetape4k validation helper, bluetape4k assertion, JUnit 5.

---

## Task 1: Module Skeleton 등록

**complexity:** medium

**Files:**
- Modify: `settings.gradle.kts`
- Modify: `AGENTS.md`
- Create: `images-barcode-api/build.gradle.kts`
- Create: `images-barcode-api/src/test/resources/junit-platform.properties`
- Create: `images-barcode-api/src/test/resources/logback-test.xml`

- [ ] `settings.gradle.kts`의 `images-ocr` 주변에 `bluetape4k-images-barcode-api` include/projectDir를 추가한다.
- [ ] `AGENTS.md`의 module list와 command list에 module을 추가한다.
- [ ] `api(project(":bluetape4k-images"))`, coroutine implementation, test dependency를 포함한 `images-barcode-api/build.gradle.kts`를 만든다.
- [ ] 기존 module convention에서 test resource를 복사해 추가한다.
- [ ] `./gradlew projects --console=plain`으로 등록 상태를 검증한다.

## Task 2: API Model RED Test 작성

**complexity:** medium

**Files:**
- Create: `images-barcode-api/src/test/kotlin/io/bluetape4k/images/barcode/BarcodeModelsTest.kt`

- [ ] `BarcodeProviderIdentity`, `BarcodePoint`, `BarcodeBoundingBox`, `BarcodeRegion`, `BarcodeOptions`, `BarcodeResult` test를 추가한다.
- [ ] assertion은 `io.bluetape4k.assertions`만 사용한다.
- [ ] `./gradlew :bluetape4k-images-barcode-api:test --tests 'io.bluetape4k.images.barcode.BarcodeModelsTest'`로 RED 상태를 검증한다.

## Task 3: API Model 구현

**complexity:** high

**Files:**
- Create: `images-barcode-api/src/main/kotlin/io/bluetape4k/images/barcode/BarcodeModels.kt`
- Create: `images-barcode-api/src/main/kotlin/io/bluetape4k/images/barcode/BarcodeExceptions.kt`

- [ ] enum, serializable value model, validation helper, sanitized exception type을 구현한다.
- [ ] validation이 필요한 type은 private constructor와 companion `invoke`를 사용한다.
- [ ] 모든 public type에 English KDoc을 추가한다.
- [ ] Task 2 targeted test로 GREEN 상태를 검증한다.

## Task 4: Reader Extension RED Test 작성

**complexity:** medium

**Files:**
- Create: `images-barcode-api/src/test/kotlin/io/bluetape4k/images/barcode/BarcodeReaderExtensionsTest.kt`

- [ ] mock-like seam이 필요하면 test class에 fake `BarcodeReader` field variable을 두고 `@BeforeEach`에서 상태를 초기화한다.
- [ ] `ImmutableImage.extractBarcodes`를 test한다.
- [ ] `ImmutableImage.suspendExtractBarcodes`를 test한다.
- [ ] provider가 던진 `CancellationException`을 suspend helper가 전파하는지 test한다.
- [ ] `ByteArray`, `Path`, `InputStream`, Okio `Source` reader helper를 test한다.
- [ ] targeted Gradle test로 RED 상태를 검증한다.

## Task 5: Reader Extension 구현

**complexity:** high

**Files:**
- Create: `images-barcode-api/src/main/kotlin/io/bluetape4k/images/barcode/BarcodeReader.kt`
- Create: `images-barcode-api/src/main/kotlin/io/bluetape4k/images/barcode/ImmutableImageBarcodeExtensions.kt`
- Create: `images-barcode-api/src/main/kotlin/io/bluetape4k/images/barcode/BarcodeInputExtensions.kt`

- [ ] `BarcodeReader`를 구현한다.
- [ ] dispatcher parameter와 cancellation-safe behavior를 갖춘 `ImmutableImage` sync/suspend helper를 구현한다.
- [ ] 기존 `immutableImageOf(...)` factory를 통해 byte/path/input-stream/source helper를 구현한다.
- [ ] Task 4 test로 GREEN 상태를 검증한다.

## Task 6: Documentation과 Workflow 등록

**complexity:** medium

**Files:**
- Create: `images-barcode-api/README.md`
- Create: `images-barcode-api/README.ko.md`
- Modify: `README.md`
- Modify: `README.ko.md`
- Modify: `.github/workflows/ci.yml`
- Modify: `.github/workflows/nightly-tests.yml`
- Modify: `.github/workflows/release.yml`
- Modify: `.github/workflows/publish-snapshot.yml`
- Modify: `.github/workflows/Examples.yml`

- [ ] API/provider 분리와 dependency snippet을 English와 Korean으로 문서화한다.
- [ ] root README module table, requirements, installation, usage, module link에 module을 추가한다.
- [ ] CI path filter, test job, status needs/env, summary requirement를 추가한다.
- [ ] Nightly test/coverage job과 summary needs/artifact를 추가한다.
- [ ] release와 publish-snapshot validation label을 추가한다.
- [ ] module path 인지가 필요하면 Examples path filter를 추가한다.
- [ ] `actionlint`를 실행한다.

## Task 7: Verification, Review, PR

**complexity:** medium

**Files:**
- Create: `docs/review/2026-07-03-issue-244-barcode-api-review.md`
- Create: `docs/lessons/2026-07-03-issue-244-barcode-api.md`

- [ ] `./gradlew :bluetape4k-images-barcode-api:test --configuration-cache --build-cache`를 실행한다.
- [ ] `./gradlew :bluetape4k-images-barcode-api:compileTestKotlin --warning-mode all --configuration-cache --build-cache`를 실행한다.
- [ ] `./gradlew projects --console=plain`을 실행한다.
- [ ] `actionlint`를 실행한다.
- [ ] `git diff --check`를 실행한다.
- [ ] 이 tool surface에서 native subagent를 사용할 수 없으면 구현 전 local-equivalent Step 2-R/3-R review를 수행하고 P0/P1 = 0을 기록한다.
- [ ] local 7-Tier implementation review를 수행하고 P0/P1 = 0을 기록한다.
- [ ] Lore protocol로 commit한다.
- [ ] branch를 push하고 final `## DoD Status`가 있는 PR을 만들어 #244를 닫는다.
- [ ] PR body, label, assignee, milestone, CI를 검증한다.
