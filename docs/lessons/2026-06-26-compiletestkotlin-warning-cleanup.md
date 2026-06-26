# compileTestKotlin warning cleanup

## Context

`compileTestKotlin --warning-mode all --rerun-tasks` exposed Kotlin compiler and
test-source warning noise in the image repository. The cleanup touched image
core, Spring storage, and JVips resize code without changing the public API
surface.

## Decision

- Keep the scope on source/test warnings that the repository owns directly.
- Use Kotlin 2.3 private data-class constructor guidance by adding
  `@ConsistentCopyVisibility` and moving validation into the companion factory.
- Replace deprecated or noisy API usage instead of suppressing warnings when the
  replacement is direct, such as JVips `thumbnailImage`.
- Use bluetape4k assertion helpers in touched tests instead of JUnit boolean
  type checks.

## Outcome

`./gradlew compileTestKotlin --warning-mode all --rerun-tasks` passes. The
remaining warning-mode output is Gradle 10 deprecation noise from build logic:
`ReportingExtension.file`, project dependency notation, and Kotlin DSL delegate
syntax. Treat those as a separate build-logic follow-up unless the task scope is
explicitly broadened.

## Future Guard

For warning cleanup PRs, split evidence into:

1. Kotlin/compiler warnings fixed in source or tests.
2. Deprecated API replacements made in owned code.
3. Residual Gradle/plugin/build-logic warnings documented separately.

Do not claim the entire `--warning-mode all` output is clean while Gradle
deprecation warnings still appear.
