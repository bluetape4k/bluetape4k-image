# Issue 82 libvips README Prerequisites

## Context

Issue #82 asked for user-facing README setup to match the current libvips,
JVips, and Java 25 FFM runtime requirements.

## Decision

Keep root README setup concise, but make native boundaries explicit: the pure JVM
`images` module needs no native library, `images-vips-*` modules need libvips,
and `images-vips-java25` consumers must provide `--enable-native-access=ALL-UNNAMED`
as a JVM startup option. Correct examples must place that flag before `-jar`.

## Outcome

Updated English and Korean root README setup/troubleshooting and aligned the
Java 25 module README examples for command-line, Spring Boot/container launch,
IDE VM options, and Homebrew macOS library lookup.

## Verification

- Compared README claims with `images-vips-java21/build.gradle.kts` and
  `images-vips-java25/build.gradle.kts`.
- `git diff --check` passed.
- `rg` found no remaining `java -jar ... --enable-native-access` examples in
  the touched README files.

## Future Guidance

Document FFM native access as a JVM launch concern, not an application property.
For macOS Homebrew libvips failures, mention `DYLD_LIBRARY_PATH=/opt/homebrew/lib`
alongside `vips --version`.
