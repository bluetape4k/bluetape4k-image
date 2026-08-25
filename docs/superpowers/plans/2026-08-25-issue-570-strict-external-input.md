# Issue #570 Strict External Input Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `bluetape4k-image`의 barcode·thumbnail external input이 core strict decode 정책과 provider-neutral `BarcodeException(MALFORMED_INPUT)` 계약을 동일하게 사용하게 한다.

**Architecture:** `bluetape4k-images`에 `immutableExternalImageOf`의 `Path`/`InputStream`/`BufferedSource`/`Source` overload를 추가하고 기존 strict `ByteArray` 구현으로 위임한다. barcode API와 ZXing은 이 helper를 통해 decode 전 byte·metadata·pixel·side 검사를 수행하고, Ktor thumbnail route도 같은 helper와 route별 limits를 사용한다.

**Tech Stack:** Kotlin, Scrimage `ImmutableImage`, ImageIO/metadata probe, Okio `Source`, JUnit 5, `bluetape4k-assertions`, Ktor `testApplication`, Gradle.

---

## 변경 파일 지도

| 책임 | 파일 |
|---|---|
| strict external loader overload와 한국어 KDoc | `images/src/main/kotlin/io/bluetape4k/images/ImmutableImageSupport.kt` |
| core strict path/stream/source 회귀 | `images/src/test/kotlin/io/bluetape4k/images/ImmutableImageSupportTest.kt` |
| provider-neutral external barcode 입력과 오류 정규화 | `images-barcode-api/src/main/kotlin/io/bluetape4k/images/barcode/BarcodeInputExtensions.kt` |
| interface-typed malformed 및 ownership 회귀 | `images-barcode-api/src/test/kotlin/io/bluetape4k/images/barcode/BarcodeReaderExtensionsTest.kt` |
| ZXing concrete strict 입력 | `images-barcode-zxing/src/main/kotlin/io/bluetape4k/images/barcode/zxing/ZxingBarcodeReader.kt` |
| concrete/interface 예외 동등성 회귀 | `images-barcode-zxing/src/test/kotlin/io/bluetape4k/images/barcode/zxing/ZxingBarcodeReaderTest.kt` |
| Ktor strict decode 호출 | `images-ktor/src/main/kotlin/io/bluetape4k/images/ktor/ImageThumbnailKtorRoutes.kt` |
| multipart unknown/malformed/limit/cancellation 회귀 | `images-ktor/src/test/kotlin/io/bluetape4k/images/ktor/ImageThumbnailKtorRoutesTest.kt` |
| 구현 계획과 SPW traceability | `docs/superpowers/plans/2026-08-25-issue-570-strict-external-input.md` |

## Task 1: Type-A receipt와 baseline 고정

**Files:**
- Create: `.bluetape/handles/issue-570-type-a-<thread>.owner`
- Create: `.bluetape/inputs/issue-570-type-a-*.json`
- Modify: `.bluetape/runs/<new-run>/` via `bluetape-flow.py` only

- [ ] **Step 1: Start a fresh Type-A workflow receipt after the approved design.**

Run `bluetape-flow.py init --workflow-type A` with the current worktree, a new owner handle, component `issue-570-strict-input`, and the current session id. Record user approval, approved spec commit `7093e35`, and current `origin/develop` head `c737ed38ac184b1922590ab256c484030f38a9cd` with `run-approve` and `run-start`.

- [ ] **Step 2: Register one implementation lane and the Type-A topology.**

The lane owns only the files in the change map above. The topology must require `tests`, `core-strict-loader`, `barcode-api`, `zxing`, `ktor`, `kotlin-checklist`, and `7-tier-review`; do not include the existing OCR train or unrelated modules.

- [ ] **Step 3: Run the approved mutation check and baseline commands.**

Run `git status --short --branch`, `git diff --check`, `./gradlew :bluetape4k-images:test --tests 'io.bluetape4k.images.ImmutableImageSupportTest'`, `./gradlew :bluetape4k-images-barcode-api:test --tests 'io.bluetape4k.images.barcode.BarcodeReaderExtensionsTest'`, `./gradlew :bluetape4k-images-barcode-zxing:test --tests 'io.bluetape4k.images.barcode.zxing.ZxingBarcodeReaderTest'`, and `./gradlew :bluetape4k-images-ktor:test --tests 'io.bluetape4k.images.ktor.ImageThumbnailKtorRoutesTest'`. Preserve any pre-existing failure as a receipt evidence item before editing.

## Task 2: Add failing core strict-loader tests

**Files:**
- Test: `images/src/test/kotlin/io/bluetape4k/images/ImmutableImageSupportTest.kt`

- [ ] **Step 1: Add path, stream, buffered-source, and source strict tests.**

Use existing `whiteTestImage`, `TempFolder`, `TrackingSource`, `assertFailsWith`, and `shouldBeEqualTo`. Add tests with these exact contracts:

```kotlin
@Test
fun `strict path loader rejects unknown dimensions before decode`(tempFolder: TempFolder) {
    val path = tempFolder.createFile("malformed.bin").toPath()
    Files.write(path, "not an encoded image".toByteArray())

    assertFailsWith<IllegalArgumentException> {
        immutableExternalImageOf(path)
    }.message shouldBeEqualTo "Image input dimensions could not be determined."
}

@Test
fun `strict input stream loader preserves caller ownership`() {
    val input = TrackingInputStream(whiteTestImage(16, 16))

    val image = immutableExternalImageOf(input)

    image.width shouldBeEqualTo 16
    input.closed shouldBeEqualTo false
}

@Test
fun `strict source loader closes owned source`() {
    val source = TrackingSource(whiteTestImage(16, 16))

    val image = immutableExternalImageOf(source)

    image.width shouldBeEqualTo 16
    source.closed shouldBeEqualTo true
}
```

Add `TrackingInputStream` beside the existing `TrackingSource`; its `read` delegates to a `ByteArrayInputStream`, `close()` sets `closed = true`, and it does not expose payload details in assertions. Add equivalent encoded-limit assertions for `Path` and `InputStream`, expecting the existing `Image input encodedBytes=... exceeds maxEncodedBytes=...` message.

- [ ] **Step 2: Run only the new core tests and confirm failure.**

Run `./gradlew :bluetape4k-images:test --tests 'io.bluetape4k.images.ImmutableImageSupportTest.strict*' --no-build-cache`. Expected failure is unresolved `immutableExternalImageOf` overloads for `Path`, `InputStream`, and `Source`, not a fixture or assertion failure.

## Task 3: Implement core strict external overloads

**Files:**
- Modify: `images/src/main/kotlin/io/bluetape4k/images/ImmutableImageSupport.kt`

- [ ] **Step 1: Add Korean-KDoc public overloads after the strict `ByteArray` function.**

Implement these signatures without changing existing compatibility overloads:

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

`Path` must check `Files.size(path)` before opening the file and then use `Files.newInputStream(path).use { immutableExternalImageOf(it, limits) }`. `InputStream` must call the existing bounded reader and then strict bytes. `BufferedSource` must read through `source.inputStream()` without closing the caller-owned source. `Source` must call `source.buffered().use { immutableExternalImageOf(it, limits) }`. Preserve `CancellationException` and existing sanitized `IllegalArgumentException` behavior; do not use `!!`, `runCatching`, or raw logging.

- [ ] **Step 2: Run the core targeted test suite.**

Run `./gradlew :bluetape4k-images:test --tests 'io.bluetape4k.images.ImmutableImageSupportTest' --no-build-cache`. Expected result: all existing tests plus the new strict path/stream/source cases pass.

- [ ] **Step 3: Commit the core boundary as a Lore decision record.**

Use a Korean commit whose intent explains that external adapters now share one bounded decode policy, with `Constraint`, `Rejected`, `Confidence`, `Scope-risk`, `Directive`, `Tested`, and `Not-tested` trailers.

## Task 4: Add failing provider-neutral barcode tests

**Files:**
- Test: `images-barcode-api/src/test/kotlin/io/bluetape4k/images/barcode/BarcodeReaderExtensionsTest.kt`

- [ ] **Step 1: Add malformed tests for every interface-typed external overload.**

Use `BarcodeTestFixtures.malformedImageBytes`, a temporary malformed `Path`, `ByteArrayInputStream`, and `okio.Buffer().write(...).asSource()`; assert `BarcodeException.reason shouldBeEqualTo BarcodeFailureReason.MALFORMED_INPUT` for bytes, path, input stream, and source. Use `assertFailsWith` and the existing bluetape4k matchers only.

- [ ] **Step 2: Add cancellation and provider-error propagation tests.**

Keep the existing `CancellationException` tests and add a reader that throws `BarcodeException(DECODE_FAILED, "decode failed")`; the external helper must rethrow the same domain exception rather than remapping it to `MALFORMED_INPUT`.

- [ ] **Step 3: Run the new API tests and confirm failure.**

Run `./gradlew :bluetape4k-images-barcode-api:test --tests 'io.bluetape4k.images.barcode.BarcodeReaderExtensionsTest' --no-build-cache`. Expected failure is the current `IllegalArgumentException` from the compatibility loader for malformed interface-typed input.

## Task 5: Implement provider-neutral strict input normalization

**Files:**
- Modify: `images-barcode-api/src/main/kotlin/io/bluetape4k/images/barcode/BarcodeInputExtensions.kt`

- [ ] **Step 1: Replace compatibility loaders with strict loaders.**

Each public bytes/path/input/source overload must call the matching `immutableExternalImageOf(..., ImageDecodeLimits.ExternalInput)` overload and delegate to the image-based `BarcodeReader` method.

- [ ] **Step 2: Add one private normalization boundary.**

Use this catch order so cancellation and domain errors survive:

```kotlin
private inline fun BarcodeReader.readExternalBarcodes(
    options: BarcodeOptions,
    load: () -> ImmutableImage,
): List<BarcodeResult> =
    try {
        readBarcodes(load(), options)
    } catch (e: CancellationException) {
        throw e
    } catch (e: BarcodeException) {
        throw e
    } catch (e: Exception) {
        throw BarcodeException(
            reason = BarcodeFailureReason.MALFORMED_INPUT,
            message = "Barcode input could not be decoded as an image.",
            cause = e,
        )
    }
```

Add Korean KDoc describing the trust boundary and sanitized message. Do not expose provider dependencies or payload/path details.

- [ ] **Step 3: Run API tests and commit.**

Run the targeted API test, then `./gradlew :bluetape4k-images-barcode-api:test --no-build-cache`. Commit the implementation and tests with a Korean Lore message.

## Task 6: Add failing ZXing concrete/interface parity test

**Files:**
- Test: `images-barcode-zxing/src/test/kotlin/io/bluetape4k/images/barcode/zxing/ZxingBarcodeReaderTest.kt`

- [ ] **Step 1: Compare receiver types on malformed bytes.**

Call the concrete `ZxingBarcodeReader` overload and the same object referenced as `BarcodeReader`; assert both exceptions have `BarcodeFailureReason.MALFORMED_INPUT` and the fixed sanitized message. Keep the existing valid QR, empty result, and unsupported-format tests unchanged.

- [ ] **Step 2: Run the provider test and confirm the concrete path still passes through the old compatibility loader.**

Run `./gradlew :bluetape4k-images-barcode-zxing:test --tests 'io.bluetape4k.images.barcode.zxing.ZxingBarcodeReaderTest' --no-build-cache`; the new parity assertion must fail before implementation.

## Task 7: Implement ZXing strict concrete input

**Files:**
- Modify: `images-barcode-zxing/src/main/kotlin/io/bluetape4k/images/barcode/zxing/ZxingBarcodeReader.kt`

- [ ] **Step 1: Use the core strict bytes helper.**

Replace the concrete overload's `immutableImageOf(bytes)` call with `immutableExternalImageOf(bytes)`. Catch `CancellationException` first, preserve `BarcodeException`, and map other exceptions to the existing sanitized `MALFORMED_INPUT` contract. Keep all ZXing imports and provider types inside this module.

- [ ] **Step 2: Run provider tests and compile.**

Run `./gradlew :bluetape4k-images-barcode-zxing:test --tests 'io.bluetape4k.images.barcode.zxing.ZxingBarcodeReaderTest' --no-build-cache` and `./gradlew :bluetape4k-images-barcode-zxing:compileKotlin`. Expected result: concrete/interface parity and all existing provider tests pass.

## Task 8: Add failing Ktor strict-boundary tests

**Files:**
- Test: `images-ktor/src/test/kotlin/io/bluetape4k/images/ktor/ImageThumbnailKtorRoutesTest.kt`

- [ ] **Step 1: Add unknown-dimensions and encoded-limit coverage.**

Keep the existing malformed and decoded-pixel tests, add a route configured with `maxInputBytes` just below a valid PNG, and assert `HttpStatusCode.BadRequest` with the fixed `Invalid image payload.` response. Add a malformed payload whose `probeImageDimensions` returns unavailable and assert no decoder success is possible.

- [ ] **Step 2: Add cancellation coverage with a cancellable writer.**

Define `CancellingImageWriter` beside `FailingImageWriter` that throws `CancellationException("cancelled")`; wrap `client.post` in `assertFailsWith<CancellationException>` so route error handling does not convert cancellation to HTTP 400.

- [ ] **Step 3: Run the route test and confirm the implementation still uses the compatibility bounded loader.**

Run `./gradlew :bluetape4k-images-ktor:test --tests 'io.bluetape4k.images.ktor.ImageThumbnailKtorRoutesTest' --no-build-cache`. The new unknown-dimension/strict assertion must fail or expose the old route path before changing production code.

## Task 9: Implement Ktor strict decode reuse

**Files:**
- Modify: `images-ktor/src/main/kotlin/io/bluetape4k/images/ktor/ImageThumbnailKtorRoutes.kt`

- [ ] **Step 1: Replace the route loader and remove the duplicate optional probe.**

Import `immutableExternalImageOf`, remove `probeImageDimensions`, and keep multipart byte-count, non-empty, and streaming checks. In the IO block call:

```kotlin
immutableExternalImageOf(uploadBytes, config.toDecodeLimits())
    .max(maxSide, maxSide)
    .forWriter(config.writer)
    .toByteArray()
```

Do not catch `CancellationException`; preserve the existing fixed response for `IllegalArgumentException` and `IOException`.

- [ ] **Step 2: Run Ktor tests and commit the adapter slice.**

Run the targeted route test, then `./gradlew :bluetape4k-images-ktor:test --no-build-cache`. Commit with a Korean Lore message that records strict policy reuse and cancellation preservation.

## Task 10: Kotlin and public-contract review pass

**Files:**
- Review: all files in the change map; no unrelated files

- [ ] **Step 1: Apply the Kotlin checklist and triggered testing reference.**

Confirm caller validation, exception compatibility, no production `!!`, no suspend `runCatching`, no swallowed cancellation, bounded I/O, stream ownership, Korean KDoc, and `bluetape4k-assertions` usage. Record KT-TEST-01..05 and KT-FIN-01..11 evidence in the workflow receipt.

- [ ] **Step 2: Run static checks.**

Run `./gradlew :bluetape4k-images:detekt :bluetape4k-images-barcode-api:detekt :bluetape4k-images-barcode-zxing:detekt :bluetape4k-images-ktor:detekt` if supported, otherwise run root `./gradlew detekt`; run `git diff --check` and `rg -n '!!|runCatching|println\('` over only changed Kotlin files.

- [ ] **Step 3: Read back public documentation and issue metadata.**

Verify changed KDoc is Korean, issue #570 remains assigned to `debop` with milestone `1.0.0`, and the PR body prepared later will end with `## DoD Status`.

## Task 11: Full affected-module validation and 7-Tier review

**Files:**
- Modify: workflow receipt evidence only
- Create: `docs/superpowers/reviews/2026-08-25-issue-570-7-tier.md` after review findings converge

- [ ] **Step 1: Run full affected-module tests sequentially.**

Run `./gradlew :bluetape4k-images:test --no-build-cache`, then `:bluetape4k-images-barcode-api:test`, `:bluetape4k-images-barcode-zxing:test`, and `:bluetape4k-images-ktor:test` one at a time. Run Kover only after all four module tests pass and treat coverage as report-only.

- [ ] **Step 2: Perform independent 7-Tier review.**

Review correctness, security/input, architecture, performance/stability, tests, public API documentation, and release readiness. Reopen the current diff and classify P0/P1/P2/P3 with file/line evidence. P0/P1 must be zero; unresolved P2/P3 must be explicitly documented in #570 or a follow-up issue.

- [ ] **Step 3: Record workflow checks and exact head.**

Attach test results, Kotlin checklist, 7-Tier review, `git diff --check`, and local scope evidence. Run `completion-check`, `verify`, and `receipt-diagnose`; do not advance to #577 until the first PR has exact-head CI and review evidence.

## Task 12: Create the first stacked PR without merging

- [ ] **Step 1: Commit final implementation with Lore trailers and verify the exact head.**

Ensure only the approved change map and review artifact are committed. Capture `git rev-parse HEAD`, `git diff --check`, and clean worktree status except intentional workflow inputs.

- [ ] **Step 2: Create PR #570 train-1 from `fix/image-barcode-strict-input` into `develop`.**

Use Korean title/body, assign `debop`, mirror milestone `1.0.0` and applicable labels, link `#570` and `#585`, state base/head commit IDs and validation results, and end the body with `## DoD Status`. Do not enable auto-merge or merge the PR.

- [ ] **Step 3: Read back PR metadata, checks, review threads, and mergeability.**

Confirm live base/head, body tail, assignee, labels, milestone, linked issues, exact-head CI, and review state. Stop at merge-ready `PENDING` until the user gives a separate merge approval. The next train PR `#577` must use this PR's verified head as its base only after the exact-head gate passes.

## Plan self-review

- Spec coverage: all goals, non-goals, strict overload contracts, receiver parity, Ktor behavior, ownership, cancellation, assertion usage, compatibility, rollback, and acceptance criteria map to Tasks 2–12.
- Placeholder scan: no unresolved marker or unspecified implementation step is present.
- Type consistency: all overload names use `immutableExternalImageOf`; all external barcode helpers call the image-based `BarcodeReader.readBarcodes(image, options)` method; all module project paths match `settings.gradle.kts`.
- Scope boundary: no OCR, CAPTCHA, Spring Boot, VIPS, benchmark, merge, release, or dependency mutation is included.

## Plan DoD

- SPW-01 — PASS: issue #570/#585, current ref, exact files, audience, and evidence sources are fixed.
- SPW-02 — PASS: dependency order, file ownership, test-first actions, commands, rollback, and approval gates are concrete.
- SPW-03 — PASS: Korean technical register and exact API/command token preservation reviewed.
- SPW-04 — PASS: plan is traced to the approved spec and current source symbols.
- SPW-05 — PASS: Markdown read-back and placeholder/type/scope self-review completed.

## DoD Status

상태: **PLAN READY — Type-A implementation starts after workflow receipt bootstrap**
