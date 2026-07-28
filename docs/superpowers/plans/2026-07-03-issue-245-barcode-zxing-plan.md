# Issue #245 ZXing Barcode Provider 구현 계획

> **Agentic worker 필수 지침:** 이 계획은 task 단위로 구현한다. 구현 표면은 superpowers:subagent-driven-development(권장) 또는 superpowers:executing-plans를 사용한다. 진행 추적에는 checkbox(`- [ ]`) 문법을 사용한다.

**목표:** provider-neutral barcode API 위에 첫 concrete barcode provider module인 `bluetape4k-images-barcode-zxing`을 추가한다.

**아키텍처:** ZXing module은 `bluetape4k-images-barcode-api`와 ZXing에만 의존한다. Public API는 `BarcodeResult`와 관련 provider-neutral model을 반환한다. Public method signature에서 ZXing type을 노출하지 않는다.

**기술 스택:** Kotlin/JVM, Gradle Kotlin DSL, ZXing `core`/`javase`, scrimage `ImmutableImage`, bluetape4k assertion, JUnit 5.

---

## Task 1: Module Skeleton 등록

**complexity:** medium

**Files:**
- Modify: `settings.gradle.kts`
- Modify: `gradle/libs.versions.toml`
- Modify: `AGENTS.md`
- Create: `images-barcode-zxing/build.gradle.kts`
- Create: `images-barcode-zxing/src/test/resources/junit-platform.properties`
- Create: `images-barcode-zxing/src/test/resources/logback-test.xml`

- [ ] `settings.gradle.kts`에 `bluetape4k-images-barcode-zxing`을 추가한다.
- [ ] central catalog에 관리되는 alias가 없으면 local ZXing version과 dependency alias를 추가한다.
- [ ] `AGENTS.md`에 module ownership note를 추가한다.
- [ ] API, ZXing, test dependency를 포함한 module build file을 만든다.
- [ ] API module convention을 따라 test resource를 추가한다.
- [ ] `./gradlew projects --console=plain`으로 등록 상태를 검증한다.

## Task 2: Provider RED Test 작성

**complexity:** high

**Files:**
- Create: `images-barcode-zxing/src/test/kotlin/io/bluetape4k/images/barcode/zxing/ZxingBarcodeReaderTest.kt`

- [ ] ZXing writer로 QR과 Code 128 sample image를 memory에서 생성한다.
- [ ] QR decode와 provider metadata를 test한다.
- [ ] Code 128 decode를 test한다.
- [ ] 요청 format mismatch behavior를 test한다.
- [ ] code가 없는 image는 empty list를 반환하는지 test한다.
- [ ] `tryHarder = true`에서 rotated QR decode를 test한다.
- [ ] malformed encoded byte helper가 `MALFORMED_INPUT`으로 mapping되는지 test한다.
- [ ] unsupported requested format이 `UNSUPPORTED_FORMAT`으로 mapping되는지 test한다.
- [ ] targeted Gradle test로 RED 상태를 검증한다.

## Task 3: ZXing Reader 구현

**complexity:** high

**Files:**
- Create: `images-barcode-zxing/src/main/kotlin/io/bluetape4k/images/barcode/zxing/ZxingBarcodeReader.kt`

- [ ] `ZxingBarcodeReader : BarcodeReader`를 구현한다.
- [ ] bluetape4k format을 ZXing hint 및 ZXing format으로 mapping하고, ZXing format을 다시 `BarcodeFormat`으로 mapping한다.
- [ ] `ImmutableImage.awt()`에서 `BufferedImageLuminanceSource`와 `HybridBinarizer`를 사용해 `BinaryBitmap`을 만든다.
- [ ] `DecodeHintType.POSSIBLE_FORMATS`와 `DecodeHintType.TRY_HARDER`를 설정한 `MultiFormatReader`를 사용한다.
- [ ] no-code, unsupported format, malformed input, decode failure를 #244 API behavior에 맞게 normalize한다.
- [ ] result text, raw backend format, raw bytes, metadata, result point, bounding box를 mapping한다.
- [ ] Public API에 English KDoc을 추가한다.
- [ ] provider test로 GREEN 상태를 검증한다.

## Task 4: Documentation과 Workflow 등록

**complexity:** medium

**Files:**
- Create: `images-barcode-zxing/README.md`
- Create: `images-barcode-zxing/README.ko.md`
- Modify: `README.md`
- Modify: `README.ko.md`
- Modify: `.github/workflows/ci.yml`
- Modify: `.github/workflows/nightly-tests.yml`
- Modify: `.github/workflows/release.yml`
- Modify: `.github/workflows/publish-snapshot.yml`
- Modify: `.github/workflows/Examples.yml`

- [ ] explicit provider construction과 `ImmutableImage.extractBarcodes` 예제를 English와 Korean으로 문서화한다.
- [ ] ZXing이 pure JVM Apache-2.0 dependency임을 문서화하고 maintenance/capability boundary를 남긴다.
- [ ] root README와 requirements table에 module row를 추가한다.
- [ ] CI path filter, test job, status needs/env, summary requirement를 추가한다.
- [ ] Nightly test/coverage job과 summary requirement를 추가한다.
- [ ] release와 publish-snapshot required job label을 추가한다.
- [ ] module path 인지를 위해 Examples path filter를 추가한다.

## Task 5: Verification, Review, PR

**complexity:** medium

**Files:**
- Create: `docs/review/2026-07-03-issue-245-barcode-zxing-review.md`
- Create: `docs/lessons/2026-07-03-issue-245-barcode-zxing.md`

- [ ] `./gradlew :bluetape4k-images-barcode-zxing:test --configuration-cache --build-cache`를 실행한다.
- [ ] `./gradlew :bluetape4k-images-barcode-zxing:compileTestKotlin --warning-mode all --configuration-cache --build-cache`를 실행한다.
- [ ] `./gradlew :bluetape4k-images-barcode-api:test --configuration-cache --build-cache`를 실행한다.
- [ ] `./gradlew projects --console=plain`을 실행한다.
- [ ] `actionlint`를 실행한다.
- [ ] `git diff --check`를 실행한다.
- [ ] local workflow/code-pattern review를 수행하고 P0/P1 = 0을 기록한다.
- [ ] Lore protocol로 commit한다.
- [ ] branch를 push하고 final `## DoD Status`가 있는 PR을 만들어 #245를 닫는다.
- [ ] PR body, label, assignee, milestone, CI를 검증한다.
