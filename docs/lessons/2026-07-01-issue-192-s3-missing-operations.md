# Issue 192 S3 Missing Operations Fail-Fast

## Context

Issue #192 found that `backend=s3` could silently create local filesystem
storage when no `S3Operations` bean was present. That hides a production
configuration error and can write image data to instance-local temp storage.

## Decision

Explicit S3 backend selection must fail startup unless the application provides
either an `S3Operations` bean or its own `ImageStorage` bean. Local storage
remains the default only for `backend=local` or when the backend property is
omitted.

## Outcome

- Replaced the S3 local fallback configuration with a fail-fast guard bean.
- Preserved user-provided `ImageStorage` backoff for custom storage
  implementations.
- Updated the Spring Boot README pair and KDoc so consumers see that S3 no
  longer falls back to local storage.
- Reworked touched MockK fixtures to class-level fields reset with
  `clearMocks(...)`.

## Verification

- Red test:
  `./gradlew :bluetape4k-images-spring-boot:test --tests 'io.bluetape4k.images.spring.autoconfigure.ImagesStorageAutoConfigurationTest' --no-daemon`
  reported 1 failing test before the production fix.
- Targeted green:
  `./gradlew :bluetape4k-images-spring-boot:test --tests 'io.bluetape4k.images.spring.autoconfigure.ImagesStorageAutoConfigurationTest' --no-daemon`
  reported `9 passing`.
- Module test:
  `./gradlew :bluetape4k-images-spring-boot:test --no-daemon` reported
  `123 passing`.
- `git diff --check`: PASS.

## Future Guard

Do not add implicit backend fallbacks for explicit production backend choices.
If a development fallback is needed later, add an explicit opt-in property and
document it in the README pair before enabling it.
