# 다중 페이지 TIFF OCR Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 기존 `StructuredOcrEngine`을 변경하지 않고 ByteArray TIFF 문서를 순차적으로 structured OCR하여 deterministic aggregate result를 제공한다.

**Architecture:** `TiffMultiPageOcr`가 하나의 ImageIO TIFF reader/session을 소유한다. metadata phase에서는 `MetadataBudgetInputStream`으로 IFD/page budget을 먼저 검증하고, 같은 reader/stream의 payload phase에서 한 page씩 decode·OCR한다. blocking과 suspend entry point는 같은 core session을 공유하되 suspend 경로는 page 작업을 `runInterruptible`로 실행하고 page 경계에서 취소를 확인한다. validation/reason과 OCR failure를 명시적 public exception으로 분리한다.

**Tech Stack:** Kotlin/JVM, Java ImageIO, TwelveMonkeys ImageIO TIFF 3.14.0, Scrimage `ImmutableImage`, Kotlin Coroutines, JUnit 5, Kluent-style bluetape assertions, Testcontainers Tesseract.

---

## 파일 책임과 변경 경계

- Create: `images-ocr/src/main/kotlin/io/bluetape4k/images/ocr/TiffMultiPageOcr.kt` — limits, reason/exception, bounded input, ImageIO reader factory/session, blocking/suspend orchestration, result normalization.
- Create: `images-ocr/src/test/kotlin/io/bluetape4k/images/ocr/TiffMultiPageOcrTest.kt` — pure-JVM fake-engine, TIFF fixture, preflight/budget/error/lifecycle/cancellation 회귀.
- Create: `images-ocr/src/test/kotlin/io/bluetape4k/images/ocr/TiffMultiPageTesseractContainerOcrTest.kt` — CI property가 켜질 때 실제 3-page TIFF를 Tesseract container CLI에 전달하는 smoke.
- Create: `images-ocr/src/test/java/io/bluetape4k/images/ocr/TiffMultiPageOcrJavaApiTest.java` — Java explicit-argument compile smoke for the blocking public surface.
- Modify: no existing OCR production file; `TiffMultiPageOcr.kt` is created once in Task 2. Existing `OcrEngine`, `OcrOptions`, and `TesseractOcrEngine` signatures remain unchanged.
- Modify: `images-ocr/README.md` — English API example, limits, exception/reason, cancellation/dispatcher, GIF exclusion, container/native gate note.
- Modify: `images-ocr/README.ko.md` — README English section과 동일한 heading/order의 자연스러운 Korean 설명.
- Create: `docs/superpowers/checklists/2026-08-15-issue-492-release-gate.md` — exact SHA/workflow/artifact/native command/rollback evidence template.
- Do not modify: root CI workflow; existing `test-images-ocr` job already runs `-Docr.container.enabled=true` and remains the merge gate.

### Task 1: Pure-JVM failing tests for public contract

**Files:**
- Create: `images-ocr/src/test/kotlin/io/bluetape4k/images/ocr/TiffMultiPageOcrTest.kt`

- [ ] **Step 1: Write a 3-page TIFF fixture and fake engine tests before implementation.**

Use the existing `SuspendTiffMultiPageWriter` and `ImmutableImage.fromAwt` helpers. The fake engine must record the input page dimensions and return distinct `OcrStructuredResult` values with page index `0`, nullable metadata, and the same `options` instance:

```kotlin
private class RecordingStructuredEngine : StructuredOcrEngine {
    val calls = mutableListOf<Pair<Int, OcrOptions>>()
    var failAt: Int? = null
    var cancelAt: Int? = null

    override fun recognize(image: ImmutableImage, options: OcrOptions): OcrResult =
        error("plain OCR is not used by the multipage contract")

    override fun recognizeStructured(image: ImmutableImage, options: OcrOptions): OcrStructuredResult {
        val index = calls.size
        calls += image.width to options
        failAt?.takeIf { it == index }?.let { throw OcrException("fake engine failure") }
        cancelAt?.takeIf { it == index }?.let { throw CancellationException("fake cancellation") }
        val text = "page-$index"
        return OcrStructuredResult(
            text = text,
            options = options,
            pages = listOf(OcrPage(pageIndex = 0, text = text)),
            blocks = listOf(OcrTextBlock(pageIndex = 0, text = text, confidence = null, boundingBox = null)),
        )
    }
}
```

Create three `textImage("PAGE N")` images, write them to a `ByteArrayOutputStream` with `SuspendTiffMultiPageWriter().suspendWrite`, and run the blocking API from `runBlocking` in the test.

- [ ] **Step 2: Add explicit red tests and run only the new test class.**

Required test names and assertions:

```kotlin
@Test fun `recognize aggregates pages in TIFF order and remaps structured indices`() { /* text == "page-0\n\npage-1\n\npage-2"; pages/blocks index == 0,1,2; calls == widths; options identity preserved */ }
@Test fun `preflight rejects late total pixel overflow before engine`() { /* maxTotalPixels fits first two pages only; calls.isEmpty() */ }
@Test fun `limits and encoded budget fail before reader or engine`() { /* maxEncodedBytes, maxPages, maxDecodedSide, maxPixelsPerPage */ }
@Test fun `malformed truncated and GIF inputs map to stable validation reasons`() { /* reason and pageIndex assertions */ }
@Test fun `engine failure is fail-fast and never returns partial result`() { /* failAt=1; page 2 not called; reason ENGINE_FAILED; pageIndex == 1 */ }
@Test fun `suspend cancellation between pages propagates`() = runTest { /* cancel after page 0; assertFailsWith<CancellationException>; page 1 absent */ }
@Test fun `suspend cancellation during preflight and page operation keeps cancellation and closes session`() = runTest { /* interrupt-aware fake reader/engine; assert CancellationException, no wrapping, close observed */ }
@Test fun `validation and OCR reason matrix preserves page index`() { /* INPUT_TOO_LARGE, READER_UNAVAILABLE, UNSUPPORTED_FORMAT, PAGE_COUNT_UNKNOWN, DIMENSIONS_UNAVAILABLE, DECODE_FAILED, ENGINE_FAILED */ }
```

Run:

```bash
./gradlew :bluetape4k-images-ocr:test --tests 'io.bluetape4k.images.ocr.TiffMultiPageOcrTest' --no-build-cache
```

Expected: FAIL because `TiffMultiPageOcr` and its public types do not yet exist.

- [ ] **Step 3: Add red tests for bounded metadata, cumulative result budget, cleanup, and redaction.**

Use the internal factory constructor planned in Task 2 with a fake `ImageReader` and a delegating `ImageInputStream` whose `close()`/reader `dispose()` can throw. Add these exact assertions:

```kotlin
@Test fun `metadata budget applies to same reader payload phase`() { /* reader readMetadata attempts > maxMetadataBytes; reason METADATA_LIMIT_EXCEEDED; no second reader */ }
@Test fun `result budget checks cumulative text and entries`() { /* each page is under limit; aggregate crosses max; reason RESULT_LIMIT_EXCEEDED; no partial result */ }
@Test fun `cleanup exception is sanitized and suppressed without replacing primary failure`() { /* primary ENGINE_FAILED; suppressed contains a path-free cleanup marker, not the raw throwable */ }
@Test fun `public errors redact path payload and tessdata`() { /* message and cause are null/sanitized; no /secret or encoded bytes */ }
@Test fun `real TwelveMonkeys reader enforces metadata budget before engine`() { /* use a valid 3-page TIFF with maxMetadataBytes=1; actual reader rejects with METADATA_LIMIT_EXCEEDED and fake engine calls == 0 */ }
```

Run the same targeted command and keep the output as the red evidence checkpoint.

- [ ] **Step 4: Commit the red tests.**

```bash
git add images-ocr/src/test/kotlin/io/bluetape4k/images/ocr/TiffMultiPageOcrTest.kt
git commit -m "다중 페이지 TIFF OCR 계약의 실패 테스트를 고정한다" -m "Constraint: 구현 전 입력·metadata·결과 budget과 취소 계약을 red test로 고정해야 한다.\nRejected: native Tesseract를 단위 테스트 의존성으로 사용 | pure-JVM 실패 경계를 재현할 수 없다.\nConfidence: high\nScope-risk: narrow\nDirective: fake engine과 deterministic TIFF fixture를 유지하고 production engine 계약은 변경하지 않는다.\nTested: targeted test command expected red\nNot-tested: 구현 전이라 compile/test pass는 확인하지 않았다." 
```

### Task 2: Implement the bounded TIFF session and public API

**Files:**
- Create: `images-ocr/src/main/kotlin/io/bluetape4k/images/ocr/TiffMultiPageOcr.kt`

- [ ] **Step 1: Add limits, reason enum, and serializable-safe exceptions.**

Implement the exact public types from the spec. Every limit is strictly positive; `maxMetadataBytes`, `maxResultTextChars`, and `maxResultEntries` are validated alongside the existing encoded/page/pixel/side limits. `TiffMultiPageOcrValidationException(reason, pageIndex, message)` extends `IllegalArgumentException` and `TiffMultiPageOcrException(reason, pageIndex, message)` extends `OcrException`; both declare an explicit `serialVersionUID`, Korean KDoc, and never attach raw provider causes. Add `@JvmOverloads` to the public class/limits constructor where it creates a useful Java overload; the Java fixture in Task 4 must compile against the explicit three-argument blocking method.

- [ ] **Step 2: Implement `MetadataBudgetInputStream` and internal factories.**

`MetadataBudgetInputStream` wraps a `ByteArrayInputStream`, counts bytes in metadata phase, throws an internal `MetadataLimitExceededException` once `maxMetadataBytes` would be exceeded, and exposes `allowPayloadReads()` to switch the same stream to full encoded-byte reads. Add internal `TiffImageInput`/`TiffImageInputFactory` and `TiffImageReaderFactory` seams so tests can inject tracking streams/readers without publishing new ABI. The default input factory calls `ImageIO.createImageInputStream`; null maps to `READER_UNAVAILABLE`. The default reader factory registers classpath SPIs, collects ImageIO readers, and the session distinguishes no reader (`READER_UNAVAILABLE`) from a non-TIFF reader (`UNSUPPORTED_FORMAT`). Dispose unselected candidates immediately.

- [ ] **Step 3: Implement metadata preflight on one reader/session.**

Open one input/reader, call `getNumImages(false)`, reject unknown/zero/page overflow with the exact validation reason, then read every `getWidth(index)`/`getHeight(index)` before the first `read` or engine call. Require positive dimensions, use `Math.multiplyExact`, check side/page/total limits with subtraction (`next > max - total`), and store immutable `PageMetadata(index, width, height, pixels)`. Catch `MetadataLimitExceededException` as `METADATA_LIMIT_EXCEEDED`; preserve nullable page index (`null` for document-level, `index` for page-level) in every validation error.

- [ ] **Step 4: Implement blocking decode/OCR and deterministic result normalization.**

After preflight, call `allowPayloadReads()` on the same input and decode exactly one page at a time. Post-check decoded dimensions, call `engine.recognizeStructured(image, options)`, and normalize all returned `pages`, `blocks`, `lines`, and `words` to the TIFF index with `copy(pageIndex = index)`. Before appending, compare cumulative text (including `\n\n` separators) and cumulative entry count against result budgets using subtraction; map overflow to `RESULT_LIMIT_EXCEEDED`. Wrap decode/engine errors in sanitized `TiffMultiPageOcrException` with `DECODE_FAILED`/`ENGINE_FAILED`, rethrow `CancellationException`, and return no partial aggregate.

- [ ] **Step 5: Implement resource ownership and suspend cancellation.**

Use a session helper that records the primary throwable and adds only a path-free sanitized cleanup marker as suppressed; send the raw cleanup throwable to an internal redacted log and never expose it publicly. If cleanup is the only failure, propagate the same sanitized marker. The blocking `recognize` uses the helper directly. The suspend `suspendRecognize` runs preflight and each page blocking operation with `runInterruptible(dispatcher)`, calls `currentCoroutineContext().ensureActive()` before each page, and closes in `withContext(NonCancellable + dispatcher)`; document that native providers may ignore interrupts and callers must set a timeout. Keep `CancellationException` untouched.

- [ ] **Step 6: Run the targeted tests to green.**

```bash
./gradlew :bluetape4k-images-ocr:test --tests 'io.bluetape4k.images.ocr.TiffMultiPageOcrTest' --no-build-cache
```

Expected: all new pure-JVM tests PASS; no native/container property is required for this checkpoint.

- [ ] **Step 7: Commit the implementation.**

```bash
git add images-ocr/src/main/kotlin/io/bluetape4k/images/ocr/TiffMultiPageOcr.kt images-ocr/src/test/kotlin/io/bluetape4k/images/ocr/TiffMultiPageOcrTest.kt
git commit -m "다중 페이지 TIFF structured OCR을 구현한다" -m "Constraint: 기존 StructuredOcrEngine source/binary 계약과 외부 입력 resource budget을 유지해야 한다.\nRejected: page별 새 reader와 병렬 OCR | metadata budget 우회와 native 메모리 배수를 만든다.\nConfidence: high\nScope-risk: moderate\nDirective: 동일 reader/stream phase와 누적 result budget을 변경하지 않는다.\nTested: targeted TiffMultiPageOcrTest\nNot-tested: module/native/container/documentation gates는 후속 단계다." 
```

### Task 3: Add the real container smoke and caller documentation

**Files:**
- Create: `images-ocr/src/test/kotlin/io/bluetape4k/images/ocr/TiffMultiPageTesseractContainerOcrTest.kt`
- Modify: `images-ocr/README.md`
- Modify: `images-ocr/README.ko.md`

- [ ] **Step 1: Add the gated Testcontainers three-page path.**

Annotate the test with `@EnabledIfSystemProperty(named = "ocr.container.enabled", matches = "true")`. Build three deterministic text images and a TIFF with `SuspendTiffMultiPageWriter`; inject a test `StructuredOcrEngine` that writes each received page to a temporary PNG, copies it to `TesseractContainerLauncher.container`, executes `tesseract <path> stdout -l eng --psm 7`, and returns the trimmed stdout as an `OcrStructuredResult`. Assert page order, non-empty page text, and aggregate `\n\n`; delete host temp files in `finally`. This test is executed by the existing CI `test-images-ocr` job with `-Docr.container.enabled=true`.

- [ ] **Step 2: Update both README locales in identical structure.**

Add sections for the ByteArray API, `TiffMultiPageOcrLimits` defaults and result budgets, `TiffMultiPageOcrValidationException`/`TiffMultiPageOcrException` reason handling, suspend dispatcher and best-effort cancellation, deterministic page index/aggregate separator, GIF exclusion, and the container/native test commands. Add a migration note that existing single-image callers remain unchanged; Path/InputStream callers must perform a bounded read before invoking the ByteArray API, and integrations should map stable reasons to retry/HTTP policy. Keep headings and code blocks structurally equivalent; Korean prose must be natural, while API names and commands stay exact.

- [ ] **Step 3: Run documentation and container-aware tests.**

```bash
./gradlew :bluetape4k-images-ocr:test --tests 'io.bluetape4k.images.ocr.TiffMultiPageTesseractContainerOcrTest' -Docr.container.enabled=true --no-build-cache
git diff --check
```

Expected: the container test PASS when Docker is available; otherwise record the environment-gated result and rely on CI for the merge gate.

- [ ] **Step 4: Commit the smoke/docs slice.**

```bash
git add images-ocr/src/test/kotlin/io/bluetape4k/images/ocr/TiffMultiPageTesseractContainerOcrTest.kt images-ocr/README.md images-ocr/README.ko.md
git commit -m "다중 페이지 TIFF OCR 운영 smoke와 문서를 추가한다" -m "Constraint: CI container gate는 실제 TIFF 전달과 aggregate 결과를 검증해야 한다.\nRejected: 기존 language-list smoke만 유지 | 새 orchestration 경계를 증명하지 못한다.\nConfidence: high\nScope-risk: moderate\nDirective: native host gate는 release checklist와 exact artifact 증적으로 별도 관리한다.\nTested: container-targeted test when Docker is available; git diff --check\nNot-tested: full module and release workflow gates are later." 
```

### Task 4: Release checklist, full verification, and independent plan/code review

**Files:**
- Create: `docs/superpowers/checklists/2026-08-15-issue-492-release-gate.md`
- Test: `images-ocr/src/test/java/io/bluetape4k/images/ocr/TiffMultiPageOcrJavaApiTest.java`

- [ ] **Step 1: Write the release gate checklist.**

Pin fields for feature branch HEAD, merged commit, CI workflow URL, exact `test-images-ocr` run, uploaded XML/artifact URL, native command result, failure-rate/ABI rollback trigger, previous artifact/catalog pin, and caller fallback to the unchanged single-image API. Do not invent run IDs before CI exists.

The Java compile fixture must contain a real invocation shape, not reflection-only coverage:

```java
package io.bluetape4k.images.ocr;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class TiffMultiPageOcrJavaApiTest {
    @Test
    void blockingSurfaceIsCallableWithExplicitArguments() {
        TiffMultiPageOcrLimits limits = new TiffMultiPageOcrLimits(
            1_024L, 1, 1_024L, 1_024L, 1_024, 1_024L, 10_000, 100
        );
        TiffMultiPageOcr ocr = new TiffMultiPageOcr();
        assertNotNull(ocr);
        assertNotNull(limits);
        try {
            ocr.recognize(new byte[0], new OcrOptions(), limits);
        } catch (TiffMultiPageOcrValidationException expected) {
            // The call is intentionally invalid; compiling this invocation is the ABI smoke.
        }
    }
}
```

- [ ] **Step 2: Run ordered verification.**

```bash
./gradlew :bluetape4k-images-ocr:test --no-build-cache
./gradlew :bluetape4k-images:test --no-build-cache
./gradlew detekt
git diff --check
```

Compile and inspect the Java surface as a separate ABI gate:

```bash
./gradlew :bluetape4k-images-ocr:compileKotlin :bluetape4k-images-ocr:compileTestKotlin :bluetape4k-images-ocr:compileTestJava --no-build-cache
javap -classpath images-ocr/build/classes/kotlin/main -public io.bluetape4k.images.ocr.TiffMultiPageOcr io.bluetape4k.images.ocr.TiffMultiPageOcrLimits io.bluetape4k.images.ocr.TiffMultiPageOcrValidationException io.bluetape4k.images.ocr.TiffMultiPageOcrException > build/issue-492-public-api.javap
git diff --exit-code -- images-ocr/src/main/kotlin/io/bluetape4k/images/ocr/OcrEngine.kt images-ocr/src/main/kotlin/io/bluetape4k/images/ocr/OcrOptions.kt
```

Expected: Java fixture compiles, `javap` lists the documented blocking overloads and reason/pageIndex properties, and existing OCR contract files have no diff.

Run native `./gradlew :bluetape4k-images-ocr:test -Docr.enabled=true --no-build-cache` only when host Tesseract/traineddata is available; run container/native checks sequentially and record exact outcomes. Inspect `git diff`, `git status`, and test reports before claiming completion.

- [ ] **Step 3: Run verifier checklist and six code-review lanes.**

Verify public API/KDoc/README parity, same-reader metadata budget, page/result limits, cleanup suppression, cancellation, sanitization, container smoke, and unchanged existing API tests. Obtain independent performance, stability, security, operations, developer/API, and user/caller findings against the final diff; P0/P1 must be zero. Fix any finding and rerun targeted/full tests before proceeding.

- [ ] **Step 4: Commit verification artifacts.**

```bash
git add docs/superpowers/checklists/2026-08-15-issue-492-release-gate.md images-ocr/src/test/java/io/bluetape4k/images/ocr/TiffMultiPageOcrJavaApiTest.java
git commit -m "다중 페이지 TIFF OCR 검증과 릴리스 gate를 기록한다" -m "Constraint: release 증적은 exact SHA와 실제 workflow artifact를 참조해야 한다.\nRejected: 환경 gate를 성공으로 추정 | native/container 상태를 왜곡한다.\nConfidence: high\nScope-risk: narrow\nDirective: run ID와 artifact URL은 live CI 확인 후에만 채운다.\nTested: module tests, detekt, diff check, independent code review\nNot-tested: live CI/merge는 PR 이후다." 
```

### Plan self-review

- Spec coverage: input/format/page/metadata/pixel/result limits, same-reader lifecycle,
  pageIndex normalization, fail-fast/no-partial, cancellation, reason mapping, container
  smoke, README parity, release rollback, and existing API compatibility each map to Tasks 1–4.
- Completeness scan: no unfinished marker or unspecified implementation step is used; every command
  has an expected outcome and every changed file has an owner.
- Type consistency: `TiffMultiPageOcrLimits`, `TiffMultiPageOcrFailureReason`, both exception
  types, factories, `PageMetadata`, and session phase names are used consistently across tests,
  implementation, docs, and checklist.

## DoD

- [ ] Plan reviewed independently with P0/P1 = 0.
- [ ] Red tests fail before implementation and pass after implementation.
- [ ] Pure-JVM module tests, existing images tests, detekt, diff check, and applicable container/native gates have fresh evidence.
- [ ] Six-lens final code review, release checklist, Korean README parity, and PR metadata are complete.
