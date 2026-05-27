# Vips Codec and Lifecycle Contracts

## Context

#108 required deterministic close-contract regressions for native-backed
`VipsImage` instances. #100 required AVIF/HEIC support without assuming every
libvips installation has the same HEIF codec surface.

## Decision

Keep stable JPEG/PNG/WebP behavior unconditional, but make HEIF-family formats
capability-gated. Java 25 FFM can inspect native libvips operation availability
with `vips_type_find`, while Java 21 JVips can expose AVIF through the binding
and must report HEIC as a backend limitation.

## Outcome

Close-contract tests now cover double-close, all public operations after close,
and close after failed operations across both JVips and FFM backends. AVIF/HEIC
encoding paths now either produce valid ISO BMFF output or fail with sanitized
codec-support errors.

## Verification

- `./gradlew :bluetape4k-images-vips-java21:test`
- `./gradlew :bluetape4k-images-vips-java25:test`
- `./gradlew detekt`
- `./gradlew build -x test`

## Future Guard

Do not document AVIF/HEIC as universally available. Tie support claims to the
backend and to native libvips codec operations such as `heifload_buffer` and
`heifsave_buffer`.
