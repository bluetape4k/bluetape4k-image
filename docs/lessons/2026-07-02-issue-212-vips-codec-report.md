# Issue 212: VIPS Codec Capability Reports

## Context

#212 needed AVIF/HEIC support guidance that does not pretend every libvips host
has the same HEIF-family codec set. The Java 25 backend can inspect native
operation availability, while the Java 21 JVips binding cannot prove the native
codec surface directly.

## Decision

Expose `VipsRuntime.codecCapabilityReport()` and `VipsRuntime.smokeTestCodec(...)`.
Reports keep JPEG/PNG/WebP stable and unconditional, report Java 25 AVIF/HEIC
support from `heifload_buffer` and `heifsave_buffer`, and report Java 21
JVips uncertainty as `UNKNOWN` except for HEIC encode, which is a backend
limitation and therefore `UNAVAILABLE`.

## Outcome

Services can make deployment decisions from structured support states instead
of parsing native errors. Smoke helpers return sanitized result objects, so raw
native paths or internal libvips messages remain outside public responses.

## Verification

- `./gradlew :bluetape4k-images-vips-api:test :bluetape4k-images-vips-java21:test :bluetape4k-images-vips-java25:test --configuration-cache --build-cache`
- `git diff --check`

## Future Guard

Do not document AVIF/HEIC as universally available. Java 21 JVips reports
unknown support where it cannot inspect native operations; Java 25 FFM reports
operation availability but still needs caller-provided sample smoke tests on the
deployment host.
