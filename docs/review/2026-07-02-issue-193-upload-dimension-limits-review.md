# Review - Issue 193 Upload Dimension Limits

Date: 2026-07-02
Scope: issue #193, `fix(upload): enforce decoded image dimension limits before thumbnail and OCR work`

## Review Scope

- Public helper: `io.bluetape4k.images.ImageDimensionProbe`
- Upload routes/examples:
  - `images-ktor`
  - `examples/ktor-ocr-api`
  - `examples/spring-boot-image-api`
  - `examples/spring-boot-ocr-api`
- README locale pairs for the touched modules/examples

## Evidence

- Red regression: `ImageThumbnailKtorRoutesTest` failed before the fix because oversized PNG headers reached scrimage decode and returned a parsing error instead of a decoded-pixel validation message.
- CodeGraph: review context for changed files reported low risk and no impacted graph nodes; graph node coverage was weak, so local diff review was used as fallback.
- IntelliJ diagnostics: MCP surface was unavailable in this session; fallback was Gradle compile/test, source grep, CodeGraph review context, and `git diff --check`.
- Validation:
  - `./gradlew :bluetape4k-images:test --tests 'io.bluetape4k.images.ImageDimensionProbeTest' --tests 'io.bluetape4k.images.batch.ImageDimensionProbeTest' :bluetape4k-images-ktor:test --tests 'io.bluetape4k.images.ktor.ImageThumbnailKtorRoutesTest' :ktor-ocr-api:test --tests 'io.bluetape4k.images.examples.ktor.ocr.KtorOcrApiApplicationTest' :spring-boot-image-api:test --tests 'io.bluetape4k.images.examples.spring.SpringBootImageApiApplicationTest' :spring-boot-ocr-api:test --tests 'io.bluetape4k.images.examples.spring.ocr.SpringBootOcrApiApplicationTest' --no-daemon`: PASS, 24 targeted tests.
  - `./gradlew :bluetape4k-images:test :bluetape4k-images-ktor:test :ktor-ocr-api:test :spring-boot-image-api:test :spring-boot-ocr-api:test --no-daemon`: PASS.
  - `git diff --check`: PASS.

## Findings

- P0: 0
- P1: 0
- P2/P3: 0

## Notes

- The fix validates encoded upload bytes with ImageIO header probes before creating `ImmutableImage`, thumbnail bytes, or OCR calls.
- The tests use generated PNG headers so large dimensions are covered without allocating a large pixel buffer.
- No MockK mocks were introduced; the existing fake OCR engines remain class-level fields and are reset in `@BeforeEach`.
- Concurrency helper gate: N/A. The bug is a request validation boundary, not a race, cancellation, thread-safety, virtual-thread, or coroutine stress scenario.
