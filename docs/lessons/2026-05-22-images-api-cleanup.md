# 2026-05-22 - Images API cleanup before 0.1.x stabilization

## Context

Issue #61 required removing typo compatibility APIs before the `0.1.x` line is
treated as stable. The affected symbols were small, but keeping them would make
misspelled names part of the public Kotlin and Java ABI.

## Decision

Remove compatibility aliases that only preserved mistakes:

- `ImageInputStream.usingSuspend(...)`
- `ImageOutputStream.usingSuspend(...)`
- `SuspendPngWriter.NoComppression`
- misspelled Java facade `ImageOuptputStreamSupportKt`

Keep intentional pre-stabilization deprecations when they have migration value,
but document a removal target in generated API docs and deprecation messages.

## Outcome

The canonical API surface now points users to `useSuspending(...)`,
`SuspendPngWriter.NoCompression`, `ImageOutputStreamSupportKt`,
`ImmutableImage.withGraphics(...)`, and `HashDistance.hamming(...)`.

## Verification

- `./gradlew :bluetape4k-images:test --console=plain`
- `./gradlew :bluetape4k-images:build --console=plain`
- `./gradlew detekt --console=plain`
- `git diff --check`

## Future Guidance

Before stabilization milestones, prefer deleting typo-only aliases instead of
extending their deprecation window. If a deprecated API remains, include the
planned removal version in both KDoc and `@Deprecated` messages.
