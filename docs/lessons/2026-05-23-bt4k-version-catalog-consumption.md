# bt4k Version Catalog Consumption

## Context

`bluetape4k-image` duplicated a small set of shared dependency versions locally
instead of reading them from `bluetape4k-dependencies`.

## Decision

Import `io.github.bluetape4k:bluetape4k-version-catalog` as `bt4k` and use
`bt4kVersion(alias)` for shared leaf dependency constraints.

## Outcome

The selected local dependency aliases are versionless, while dependency
management supplies the governed versions from the shared catalog.

## Verification

- `git diff --check`
- `./gradlew help --no-daemon --no-configuration-cache`
- `./gradlew compileKotlin --no-daemon --no-configuration-cache`

## Future Guidance

Keep image-specific coordinates local, but source shared version values from
`bt4k` whenever the alias exists in `bluetape4k-dependencies`.
