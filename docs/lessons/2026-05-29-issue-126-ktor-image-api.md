# 2026-05-29 - Ktor Image API Example

## Context

Issue #126 needed a repository-local Ktor quickstart after `images-ktor` landed,
so users can run the Ktor integration path directly without starting from the
library module tests.

## Decision

Add `examples/ktor-image-api` as a non-published Ktor 3 application. Compose the
existing `bluetape4kCaptchaRoutes` and `bluetape4kImageThumbnailRoutes` helpers
instead of duplicating route logic in the example.

## Outcome

The example exposes a ready endpoint, CAPTCHA issue/verify routes, and a
multipart PNG thumbnail route. README locale files document curl usage for both
CAPTCHA and image processing flows.

## Verification

Run `./gradlew :ktor-image-api:test`, `./gradlew projects`, and `actionlint`
before merging.

## Future Note

Keep runnable examples as composition layers over published module APIs. Route
helper behavior should stay covered in the module tests; examples should prove
first-run wiring and user-facing commands.
