# 검토 - Issue 193 Upload Dimension Limits

날짜: 2026-07-02
범위: issue #193, `fix(upload): enforce decoded image dimension limits before thumbnail and OCR work`

## 검토 범위

- public helper: `io.bluetape4k.images.ImageDimensionProbe`
- Upload routes/examples:
  - `images-ktor`
  - `examples/ktor-ocr-api`
  - `examples/spring-boot-image-api`
  - `examples/spring-boot-ocr-api`
- README locale pairs for the touched modules/examples

## 근거

- Red regression: `ImageThumbnailKtorRoutesTest` failed before the fix because oversized PNG headers reached scrimage decode and returned a parsing error instead of a decoded-pixel validation message.
- CodeGraph review context는 changed file의 low risk와 no impacted graph node를 보고했다. graph node coverage가 약해 local diff review를 fallback으로 사용했다.
- IntelliJ diagnostic은 이 세션에서 MCP surface를 사용할 수 없었다. Gradle compile/test, source grep, CodeGraph review context, `git diff --check`를 fallback으로 사용했다.
- 검증:
  - `./gradlew :bluetape4k-images:test --tests 'io.bluetape4k.images.ImageDimensionProbeTest' --tests 'io.bluetape4k.images.batch.ImageDimensionProbeTest' :bluetape4k-images-ktor:test --tests 'io.bluetape4k.images.ktor.ImageThumbnailKtorRoutesTest' :ktor-ocr-api:test --tests 'io.bluetape4k.images.examples.ktor.ocr.KtorOcrApiApplicationTest' :spring-boot-image-api:test --tests 'io.bluetape4k.images.examples.spring.SpringBootImageApiApplicationTest' :spring-boot-ocr-api:test --tests 'io.bluetape4k.images.examples.spring.ocr.SpringBootOcrApiApplicationTest' --no-daemon`: PASS, 24 targeted tests.
  - `./gradlew :bluetape4k-images:test :bluetape4k-images-ktor:test :ktor-ocr-api:test :spring-boot-image-api:test :spring-boot-ocr-api:test --no-daemon`: PASS.
  - `git diff --check`: PASS.

## 발견 사항

- P0: 0
- P1: 0
- P2/P3: 0

## Notes

- 수정은 `ImmutableImage`, thumbnail byte, OCR call을 만들기 전에 ImageIO header probe로 encoded upload byte를 검증한다.
- 테스트는 generated PNG header를 사용하므로 큰 pixel buffer를 할당하지 않고 large dimension을 다룬다.
- MockK mock은 도입하지 않았다. 기존 fake OCR engine은 class-level field로 남고 `@BeforeEach`에서 reset된다.
- Concurrency helper gate: N/A. 이 bug는 request validation boundary 문제이며 race, cancellation, thread-safety, virtual-thread, coroutine stress scenario가 아니다.
