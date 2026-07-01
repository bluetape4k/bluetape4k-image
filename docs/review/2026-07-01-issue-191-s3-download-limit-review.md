# Issue 191 S3 Download Limit Review

## Scope

- Issue: #191 `fix(storage): enforce S3 image download size limits fail-closed`
- Module: `bluetape4k-images-spring-boot`
- Files reviewed:
  - `images-spring-boot/src/main/kotlin/io/bluetape4k/images/spring/storage/s3/S3ImageStorage.kt`
  - `images-spring-boot/src/test/kotlin/io/bluetape4k/images/spring/storage/s3/S3ImageStorageTest.kt`
  - `docs/lessons/2026-07-01-issue-191-s3-download-limit.md`

## Findings

- P0: 0
- P1: 0
- P2: 0
- P3: 0

Rereview note: the first review missed a test-fixture lifecycle violation.
`S3ImageStorageTest` recreated the `S3Operations` mock in `@BeforeEach` instead
of keeping it as a class-level field and resetting it with `clearMocks(...)`.
The test now follows the bluetape4k mock lifecycle rule and verifies strict
interactions with `confirmVerified(...)`.

## 7-Tier Review

| Tier | P0 | P1 | Notes |
|---|---:|---:|---|
| 1 Security | 0 | 0 | Oversized or unverifiable S3 downloads no longer bypass `maxSizeBytes`. |
| 2 Reliability | 0 | 0 | Cancellation is rethrown before broad exception handling; destination writes happen only after validated download. |
| 3 Structural impact | 0 | 0 | Change is scoped to S3 storage; no public API or dependency changes. |
| 4 Kotlin/API quality | 0 | 0 | Helper methods keep validation logic local and reuse existing `ImageStorageException` types. |
| 5 Tests/types/silent failure | 0 | 0 | Regression tests cover pre-check failure, missing exact key, post-download oversize, destination path, and cancellation. Mocks are class-level fields reset with `clearMocks(...)`, and strict interaction scopes call `confirmVerified(...)`. |
| 6 Performance/stability | 0 | 0 | Fail-closed pre-check avoids byte-array materialization when size cannot be verified; post-check catches races. |
| 7 Docs/release/evidence | 0 | 0 | KDoc contract and lesson note were updated; release note is not required for unreleased 0.4.0 work. |

## Validation

- Red test:
  `./gradlew :bluetape4k-images-spring-boot:test --tests 'io.bluetape4k.images.spring.storage.s3.S3ImageStorageTest' --no-daemon`
  reported 4 failing tests and 1 passing test before the fix.
- Targeted green:
  `./gradlew :bluetape4k-images-spring-boot:test --tests 'io.bluetape4k.images.spring.storage.s3.S3ImageStorageTest' --no-daemon`
  reported `5 passing`.
- Module test:
  `./gradlew :bluetape4k-images-spring-boot:test --no-daemon` reported
  `123 passing`.
- `git diff --check`: PASS.

## Verdict

Gate verdict: PASS. P0 = 0, P1 = 0.
