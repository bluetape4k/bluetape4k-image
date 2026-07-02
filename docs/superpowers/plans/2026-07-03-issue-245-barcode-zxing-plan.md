# Issue #245 ZXing Barcode Provider Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use
> checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the first concrete barcode provider module,
`bluetape4k-images-barcode-zxing`, on top of the provider-neutral barcode API.

**Architecture:** The ZXing module depends on `bluetape4k-images-barcode-api`
and ZXing only. Public API returns `BarcodeResult` and related provider-neutral
models. No ZXing type is exposed from public method signatures.

**Tech Stack:** Kotlin/JVM, Gradle Kotlin DSL, ZXing `core`/`javase`, scrimage
`ImmutableImage`, bluetape4k assertions, JUnit 5.

---

## Task 1: Register Module Skeleton

**complexity:** medium

**Files:**
- Modify: `settings.gradle.kts`
- Modify: `gradle/libs.versions.toml`
- Modify: `AGENTS.md`
- Create: `images-barcode-zxing/build.gradle.kts`
- Create: `images-barcode-zxing/src/test/resources/junit-platform.properties`
- Create: `images-barcode-zxing/src/test/resources/logback-test.xml`

- [ ] Add `bluetape4k-images-barcode-zxing` to `settings.gradle.kts`.
- [ ] Add local ZXing version and dependency aliases if the central catalog has
  no governed alias.
- [ ] Add module ownership notes to `AGENTS.md`.
- [ ] Create module build file with API, ZXing, and test dependencies.
- [ ] Add test resources following API module conventions.
- [ ] Verify registration with `./gradlew projects --console=plain`.

## Task 2: Write RED Provider Tests

**complexity:** high

**Files:**
- Create: `images-barcode-zxing/src/test/kotlin/io/bluetape4k/images/barcode/zxing/ZxingBarcodeReaderTest.kt`

- [ ] Generate QR and Code 128 sample images in memory with ZXing writers.
- [ ] Test QR decode and provider metadata.
- [ ] Test Code 128 decode.
- [ ] Test requested format mismatch behavior.
- [ ] Test no-code image returns an empty list.
- [ ] Test rotated QR decode with `tryHarder = true`.
- [ ] Test malformed encoded byte helper maps to `MALFORMED_INPUT`.
- [ ] Test unsupported requested format maps to `UNSUPPORTED_FORMAT`.
- [ ] Verify RED with targeted Gradle test.

## Task 3: Implement ZXing Reader

**complexity:** high

**Files:**
- Create: `images-barcode-zxing/src/main/kotlin/io/bluetape4k/images/barcode/zxing/ZxingBarcodeReader.kt`

- [ ] Implement `ZxingBarcodeReader : BarcodeReader`.
- [ ] Map bluetape4k formats to ZXing hints and ZXing formats back to
  `BarcodeFormat`.
- [ ] Build `BinaryBitmap` from `ImmutableImage.awt()` using
  `BufferedImageLuminanceSource` and `HybridBinarizer`.
- [ ] Use `MultiFormatReader` with `DecodeHintType.POSSIBLE_FORMATS` and
  `DecodeHintType.TRY_HARDER`.
- [ ] Normalize no-code, unsupported format, malformed input, and decode
  failures to #244 API behavior.
- [ ] Map result text, raw backend format, raw bytes, metadata, result points,
  and bounding box.
- [ ] Add English KDoc to public APIs.
- [ ] Verify GREEN with provider tests.

## Task 4: Documentation and Workflow Registration

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

- [ ] Document explicit provider construction and `ImmutableImage.extractBarcodes`
  examples in English and Korean.
- [ ] Document ZXing as pure JVM Apache-2.0 and note maintenance/capability
  boundaries.
- [ ] Add module rows to root README and requirements tables.
- [ ] Add CI path filter, test job, status needs/env, and summary requirement.
- [ ] Add Nightly test/coverage job and summary requirement.
- [ ] Add release and publish-snapshot required job labels.
- [ ] Add Examples path filters for module path awareness.

## Task 5: Verification, Review, and PR

**complexity:** medium

**Files:**
- Create: `docs/review/2026-07-03-issue-245-barcode-zxing-review.md`
- Create: `docs/lessons/2026-07-03-issue-245-barcode-zxing.md`

- [ ] Run `./gradlew :bluetape4k-images-barcode-zxing:test --configuration-cache --build-cache`.
- [ ] Run `./gradlew :bluetape4k-images-barcode-zxing:compileTestKotlin --warning-mode all --configuration-cache --build-cache`.
- [ ] Run `./gradlew :bluetape4k-images-barcode-api:test --configuration-cache --build-cache`.
- [ ] Run `./gradlew projects --console=plain`.
- [ ] Run `actionlint`.
- [ ] Run `git diff --check`.
- [ ] Perform local workflow/code-pattern review and record P0/P1 = 0.
- [ ] Commit with Lore protocol.
- [ ] Push branch and create PR closing #245 with final `## DoD Status`.
- [ ] Verify PR body, labels, assignee, milestone, and CI.
