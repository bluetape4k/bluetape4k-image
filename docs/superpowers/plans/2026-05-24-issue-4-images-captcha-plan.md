# Issue #4 images-captcha Implementation Plan

- Issue: [#4](https://github.com/bluetape4k/bluetape4k-image/issues/4)
- Spec: `docs/superpowers/specs/2026-05-24-issue-4-images-captcha-design.md`
- Workflow: `bluetape4k-workflow` Type A Full Design
- Branch/worktree: `feat/issue-4-images-captcha`

## 1. Pre-Implementation State

- Worktree exists at `.worktrees/feat-issue-4-images-captcha`.
- Legacy source was recovered from `bluetape4k-projects` git history at
  `494d95ee1^:x-obsoleted/captcha`.
- Spec Step 2-R Claude advisor gate passed on rerun:
  `.omx/artifacts/claude-issue-4-images-captcha-spec-rerun-20260524165543.md`.

## 2. Implementation Tasks

### Task 1: Register the module

- Add `bluetape4k-images-captcha` to `settings.gradle.kts`.
- Set `project(":bluetape4k-images-captcha").projectDir = file("images-captcha")`.
- Create `images-captcha/build.gradle.kts`.
- Apply sibling Kotlin/JVM module conventions:
  - `alias(libs.plugins.kotlin.jvm)`
  - `alias(libs.plugins.dependency.management)`
  - Java/Kotlin toolchain 21
  - compiler flags matching existing modules (`-Xjsr305=strict`,
    `-jvm-default=enable`)
- Depend on:
  - `api(project(":bluetape4k-images"))`
  - `implementation(libs.kotlinx.coroutines.core)`
  - `testImplementation(libs.bluetape4k.junit5)`
  - `testImplementation(libs.kotlinx.coroutines.test)`
- Configure tests with `systemProperty("java.awt.headless", "true")`.
- Run `./gradlew projects` after registration.

### Task 2: Add public model and options API

Create package `io.bluetape4k.images.captcha`:

- `CaptchaChallenge` regular class, not data class and not Serializable.
- `CaptchaGenerator` interface with sync and suspend methods.
- `CaptchaOptions` data class with validation-compatible immutable fields.
- `CaptchaImageSize` data class.
- `CaptchaNoise` sealed interface with `None`, `Low`, `Medium`, `High`, and
  `Custom(lines, dots)` exactly as specified.
- `CaptchaDistortion` sealed interface with `None` and `Wave(strength)`.
- `CaptchaFont` data class plus `CaptchaFontStyle` enum exactly as specified,
  with no bundled binaries.
- `captchaGenerator { ... }` factory and builder.
- Every module `data class` must implement `Serializable` and define
  `serialVersionUID`.
- Verify whether detekt/Kover are inherited from root conventions or require
  explicit module wiring before relying on `:detekt` and coverage aggregation.

Public KDoc must be English and state:

- CAPTCHA is lightweight friction, not a complete bot-defense system.
- `expiresAt` is advisory.
- `CaptchaChallenge` is not serializable because it carries `ImmutableImage`.
- Default charset is uppercase-only and excludes ambiguous `I`, `O`, `0`, `1`.

### Task 3: Implement rendering

- Use Java2D/scrimage through `ImmutableImage`.
- Generate text with secure default randomness.
- Validate per-call `length` in both sync and suspend APIs.
- Render background, characters with bounded rotation/jitter, optional line/dot
  noise, and optional bounded wave distortion.
- Use `Clock.systemUTC()` by default and support fixed `Clock` in tests.
- Ensure `generateSuspend()` uses `withContext(Dispatchers.Default)` and honors
  cancellation before render starts.

### Task 4: Add tests and resources

Add:

- `images-captcha/src/test/resources/junit-platform.properties`
- `images-captcha/src/test/resources/logback-test.xml`

Test cases:

- option validation rejects invalid length, charset, image size, font size,
  expiration, empty text colors, and invalid distortion strength.
- default challenge text length equals options length.
- per-call `generate(length)` validates and uses requested length.
- generated text uses only configured charset.
- generated image dimensions match options.
- fixed clock produces deterministic `expiresAt`.
- default generator creates varied text across 100 generations.
- generated image encodes to bytes with existing writer APIs.
- suspend generation returns a valid challenge.
- pre-cancelled coroutine propagates `CancellationException` before rendering
  starts. Use a test seam/counter to prove no image artifact was captured; do
  not claim mid-render Java2D cancellation.
- headless test JVM can generate an image.

Use bluetape4k assertions only.

### Task 5: Documentation

- Add `images-captcha/README.md`.
- Add `images-captcha/README.ko.md`.
- Update root `README.md` and `README.ko.md`:
  - module table
  - dependency snippet
  - module README links
- If root diagrams/charts include module inventory, update them only if the
  existing README semantics require it. Otherwise avoid visual churn.

### Task 6: CI and release metadata

- Inspect `.github/workflows/ci.yml` and nightly workflows; update them unless
  inspection proves the new module is auto-covered.
- When explicit module patterns are present, update `.github/workflows/ci.yml`
  with `images-captcha` change output, `dorny/paths-filter` entry,
  `test-images-captcha` job, and Kover XML upload.
- Update nightly workflow module blocks for `bluetape4k-images-captcha` when
  explicit module blocks are present.
- Record the workflow coverage evidence either way.
- Run `actionlint` after workflow YAML changes.
- BOM inclusion should be automatic through `bom/build.gradle.kts`; verify by
  reading the constraint rule and `./gradlew projects`.

### Task 7: Verification

Run in order:

1. `./gradlew projects`
2. IDE diagnostics when available; otherwise record fallback.
3. `./gradlew :bluetape4k-images-captcha:test`
4. `./gradlew :bluetape4k-images-captcha:build`
5. `./gradlew :bluetape4k-images-captcha:detekt`
6. Verify Kover aggregation includes the new module and record the command used.
7. `git diff --check`
8. `actionlint` if workflow files changed

Then run Step 6-R code review:

- Current Codex review over changed diff.
- Claude Code CLI code review artifact over the same diff.
- Fix all P0/P1 findings before PR creation.

## 3. Commit and PR

- Commit spec and plan before implementation if review gate passes.
- Commit implementation, docs, tests, and lessons separately if useful.
- Add/update `docs/lessons/YYYY-MM-DD-issue-4-images-captcha.md`.
- Create PR against `develop`.
- PR body must include `Fixes #4`, work done, validation, and not-run notes.
- Do not merge automatically after PR creation.

## 4. Rollback

The module is additive. Rollback is:

- remove `images-captcha/`
- remove `settings.gradle.kts` registration
- revert README/workflow updates

No existing runtime behavior should change.

## 5. Risks

- AWT rendering can vary slightly by font/platform. Tests must assert structural
  properties rather than exact pixels.
- `ImmutableImage` is not treated as serializable; applications must encode
  images before persistence.
- Overly broad CAPTCHA security claims would mislead users. Docs must keep the
  scope to challenge image generation.
- Wave distortion can introduce edge artifacts; keep strength bounded and test
  output dimensions/encodability.
