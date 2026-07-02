# Issue #244 Barcode API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a provider-neutral `bluetape4k-images-barcode-api` module for barcode extraction.

**Architecture:** The API module depends on `bluetape4k-images` for `ImmutableImage` and exposes pure contracts, models, exceptions, and sync/suspend helpers. Concrete providers such as ZXing and BoofCV remain separate modules.

**Tech Stack:** Kotlin/JVM, Gradle Kotlin DSL, `ImmutableImage`, Okio `Source`, Kotlin coroutines, bluetape4k validation helpers, bluetape4k assertions, JUnit 5.

---

## Task 1: Register Module Skeleton

**complexity:** medium

**Files:**
- Modify: `settings.gradle.kts`
- Modify: `AGENTS.md`
- Create: `images-barcode-api/build.gradle.kts`
- Create: `images-barcode-api/src/test/resources/junit-platform.properties`
- Create: `images-barcode-api/src/test/resources/logback-test.xml`

- [ ] Add `bluetape4k-images-barcode-api` to `settings.gradle.kts` include/projectDir near `images-ocr`.
- [ ] Add the module to `AGENTS.md` module list and command list.
- [ ] Create `images-barcode-api/build.gradle.kts` with `api(project(":bluetape4k-images"))`, coroutine implementation, and test dependencies.
- [ ] Add test resources copied from existing module conventions.
- [ ] Verify with `./gradlew projects --console=plain`.

## Task 2: Write RED Tests for API Models

**complexity:** medium

**Files:**
- Create: `images-barcode-api/src/test/kotlin/io/bluetape4k/images/barcode/BarcodeModelsTest.kt`

- [ ] Add tests for `BarcodeProviderIdentity`, `BarcodePoint`, `BarcodeBoundingBox`, `BarcodeRegion`, `BarcodeOptions`, and `BarcodeResult`.
- [ ] Use `io.bluetape4k.assertions` only.
- [ ] Verify RED with `./gradlew :bluetape4k-images-barcode-api:test --tests 'io.bluetape4k.images.barcode.BarcodeModelsTest'`.

## Task 3: Implement API Models

**complexity:** high

**Files:**
- Create: `images-barcode-api/src/main/kotlin/io/bluetape4k/images/barcode/BarcodeModels.kt`
- Create: `images-barcode-api/src/main/kotlin/io/bluetape4k/images/barcode/BarcodeExceptions.kt`

- [ ] Implement enums, serializable value models, validation helpers, and sanitized exception types.
- [ ] Use private constructors plus companion `invoke` when validation is needed.
- [ ] Add English KDoc to all public types.
- [ ] Verify GREEN with the Task 2 targeted test.

## Task 4: Write RED Tests for Reader Extensions

**complexity:** medium

**Files:**
- Create: `images-barcode-api/src/test/kotlin/io/bluetape4k/images/barcode/BarcodeReaderExtensionsTest.kt`

- [ ] Add fake `BarcodeReader` field variables in the test class if a mock-like seam is needed; reset field state in `@BeforeEach`.
- [ ] Test `ImmutableImage.extractBarcodes`.
- [ ] Test `ImmutableImage.suspendExtractBarcodes`.
- [ ] Test that `CancellationException` from a provider is propagated by the suspend helper.
- [ ] Test `ByteArray`, `Path`, `InputStream`, and Okio `Source` reader helpers.
- [ ] Verify RED with targeted Gradle test.

## Task 5: Implement Reader Extensions

**complexity:** high

**Files:**
- Create: `images-barcode-api/src/main/kotlin/io/bluetape4k/images/barcode/BarcodeReader.kt`
- Create: `images-barcode-api/src/main/kotlin/io/bluetape4k/images/barcode/ImmutableImageBarcodeExtensions.kt`
- Create: `images-barcode-api/src/main/kotlin/io/bluetape4k/images/barcode/BarcodeInputExtensions.kt`

- [ ] Implement `BarcodeReader`.
- [ ] Implement `ImmutableImage` sync and suspend helpers with dispatcher parameter and cancellation-safe behavior.
- [ ] Implement byte/path/input-stream/source helpers through existing `immutableImageOf(...)` factories.
- [ ] Verify GREEN with Task 4 tests.

## Task 6: Documentation and Workflow Registration

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

- [ ] Document API/provider split and dependency snippet in English and Korean.
- [ ] Add module to root README module table, requirements, installation, usage, and module links.
- [ ] Add CI path filter, test job, status needs/env, and summary requirement.
- [ ] Add Nightly test/coverage job and summary needs/artifacts.
- [ ] Add release and publish-snapshot validation labels.
- [ ] Add Examples path filters if needed for module path awareness.
- [ ] Run `actionlint`.

## Task 7: Verification, Review, and PR

**complexity:** medium

**Files:**
- Create: `docs/review/2026-07-03-issue-244-barcode-api-review.md`
- Create: `docs/lessons/2026-07-03-issue-244-barcode-api.md`

- [ ] Run `./gradlew :bluetape4k-images-barcode-api:test --configuration-cache --build-cache`.
- [ ] Run `./gradlew :bluetape4k-images-barcode-api:compileTestKotlin --warning-mode all --configuration-cache --build-cache`.
- [ ] Run `./gradlew projects --console=plain`.
- [ ] Run `actionlint`.
- [ ] Run `git diff --check`.
- [ ] Perform local-equivalent Step 2-R/3-R review before implementation because native subagents are unavailable in this tool surface; record P0/P1 = 0.
- [ ] Perform local 7-Tier implementation review and record P0/P1 = 0.
- [ ] Commit with Lore protocol.
- [ ] Push branch and create PR closing #244 with final `## DoD Status`.
- [ ] Verify PR body, labels, assignee, milestone, and CI.
