# Issue 191 S3 Download Limit 검토

## 범위

- 이슈: #191 `fix(storage): enforce S3 image download size limits fail-closed`
- 모듈: `bluetape4k-images-spring-boot`
- 검토 파일:
  - `images-spring-boot/src/main/kotlin/io/bluetape4k/images/spring/storage/s3/S3ImageStorage.kt`
  - `images-spring-boot/src/test/kotlin/io/bluetape4k/images/spring/storage/s3/S3ImageStorageTest.kt`
  - `docs/lessons/2026-07-01-issue-191-s3-download-limit.md`

## 발견 사항

- P0: 0
- P1: 0
- P2: 0
- P3: 0

Rereview note: the first review missed a test-fixture lifecycle violation.
`S3ImageStorageTest` recreated the `S3Operations` mock in `@BeforeEach` instead
of keeping it as a class-level field and resetting it with `clearMocks(...)`.
The test now follows the bluetape4k mock lifecycle rule and verifies strict
interactions with `confirmVerified(...)`.

## 7계층 검토

| 계층 | P0 | P1 | 참고 |
|---|---:|---:|---|
| 1 보안 | 0 | 0 | 크기가 너무 크거나 검증할 수 없는 S3 download는 더 이상 `maxSizeBytes`를 우회하지 않는다. |
| 2 신뢰성 | 0 | 0 | Cancellation is rethrown before broad exception handling; destination writes happen only after validated download. |
| 3 구조 영향 | 0 | 0 | Change is scoped to S3 storage; no public API or dependency changes. |
| 4 Kotlin/API 품질 | 0 | 0 | Helper methods keep validation logic local and reuse existing `ImageStorageException` types. |
| 5 테스트/타입/조용한 실패 | 0 | 0 | Regression tests cover pre-check failure, missing exact key, post-download oversize, destination path, and cancellation. Mocks are class-level fields reset with `clearMocks(...)`, and strict interaction scopes call `confirmVerified(...)`. |
| 6 성능/안정성 | 0 | 0 | Fail-closed pre-check avoids byte-array materialization when size cannot be verified; post-check catches races. |
| 7 문서/릴리스/근거 | 0 | 0 | KDoc contract and lesson note were updated; unreleased 0.4.0 작업이므로 release note는 필요하지 않다. |

## 검증

- Red test:
  `./gradlew :bluetape4k-images-spring-boot:test --tests 'io.bluetape4k.images.spring.storage.s3.S3ImageStorageTest' --no-daemon`
  reported 4 failing tests and 1 PASSing test before the fix.
- Targeted green:
  `./gradlew :bluetape4k-images-spring-boot:test --tests 'io.bluetape4k.images.spring.storage.s3.S3ImageStorageTest' --no-daemon`
  reported `5 PASSing`.
- Module test:
  `./gradlew :bluetape4k-images-spring-boot:test --no-daemon` reported
  `123 PASSing`.
- `git diff --check`: PASS.

## 판정

게이트 판정: PASS. P0 = 0, P1 = 0.
