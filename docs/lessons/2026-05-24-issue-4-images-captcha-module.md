# Issue 4 images-captcha module

## Context

Issue #4 requested a CAPTCHA module, but the issue body pointed at stale
`bluetape4k-projects/x-obsoleted/captcha` paths. The old module was removed in
`bluetape4k-projects` commit `494d95ee1`, so the useful source of truth was git
history, not the current issue text.

## Decision

Add `bluetape4k-images-captcha` as a pure JVM Java2D module on top of
`bluetape4k-images`, using `ImmutableImage.withGraphics` instead of the removed
legacy mutation helper. Keep the public API small through `captchaGenerator { }`
and serializable option value types, while keeping `CaptchaChallenge`
non-serializable because `ImmutableImage` is not a storage payload.

## Outcome

The module registers in `settings.gradle.kts`, is covered by CI and Nightly
module jobs, and is documented in English/Korean root and module READMEs. BOM
coverage is automatic through `bom/build.gradle.kts` because it constrains all
published root subprojects.

## Verification

- `./gradlew projects`
- `./gradlew :bluetape4k-images-captcha:test :bluetape4k-images-captcha:koverXmlReport`
- `./gradlew build -x test`
- `actionlint .github/workflows/ci.yml .github/workflows/nightly-tests.yml`
- `git diff --check`
- Claude code review artifact:
  `.omx/artifacts/claude-issue-4-images-captcha-code-review-rerun-20260524172738.md`
  with P0=0 and P1=0.

## Future Guard

When old bluetape4k issues reference `x-obsoleted/*`, inspect the deletion commit
and deleted files first. For new modules, remember that this repository's BOM is
auto-generated from included published subprojects; verify `settings.gradle.kts`
and `./gradlew projects` before adding manual BOM constraints.
