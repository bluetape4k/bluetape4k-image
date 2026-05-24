# Issue #4 images-captcha Design Spec

- Issue: [#4](https://github.com/bluetape4k/bluetape4k-image/issues/4) `feat: CAPTCHA 이미지 생성 모듈 추가 (images-captcha)`
- Milestone: `0.2.0`
- Workflow: `bluetape4k-workflow` Type A Full Design
- Target module: `bluetape4k-images-captcha`

## 1. Context

`bluetape4k-image` needs a first-party CAPTCHA image module for internal admin
tools, B2B screens, and offline deployments where reCAPTCHA/hCaptcha is not
appropriate. The issue body points to `x-obsoleted/captcha` in
`bluetape4k-projects`; that path is stale in the current tree.

Git history confirms the legacy implementation was deleted by
`494d95ee1 chore: x-obsoleted 레거시 모듈 5개 삭제 (#331)` on 2026-05-07. The
pre-delete tree contained:

- `Captcha.kt`, `CaptchaGenerator.kt`, `CaptchaCodeGenerator.kt`
- `config/CaptchaConfig.kt`, `config/CaptchaTheme.kt`
- `image/ImageCaptcha.kt`, `image/ImageCaptchaGenerator.kt`
- `utils/FontProvider.kt`, embedded fonts, README files, and tests

The new module should reuse the proven shape, but not copy legacy contracts
blindly. This repository already has `bluetape4k-images`, `ImmutableImage`
helpers, multilingual README policy, BOM aggregation, and Java 21 baseline.

## 2. Goals

- Add a published `bluetape4k-images-captcha` module under `images-captcha/`.
- Generate CAPTCHA challenges as text plus `ImmutableImage`.
- Provide a configurable Java2D/scrimage implementation with no native
  dependency.
- Support synchronous and suspend generation APIs.
- Include deterministic enough tests for dimensions, length, allowed charset,
  writer output, validation, and uniqueness behavior.
- Register the module in Gradle settings, BOM, README files, CI/Nightly
  workflow scope if the repository requires explicit module lists.

## 3. Non-Goals

- No CAPTCHA validation/store/session lifecycle. This module creates challenges;
  applications own persistence, rate limiting, replay protection, and answer
  comparison.
- No audio CAPTCHA.
- No external CAPTCHA service integration.
- No ML or OCR-resistance guarantee. The module is a utility for lightweight
  friction, not a high-security bot-defense system.
- No new third-party dependencies unless current `bluetape4k-images` and
  shared bluetape4k libraries are insufficient.

## 4. Legacy Evidence and Reuse

### 4.1 Reusable Legacy Ideas

- `CaptchaCodeGenerator` validated non-empty symbols and positive length with
  bluetape4k validation helpers.
- Default symbols were uppercase letters plus digits.
- `CaptchaConfig` exposed width, height, length, noise count, theme, palette,
  font paths/styles, and font size.
- `ImageCaptchaGenerator` created an `ImmutableImage`, filled the background,
  drew rotated characters, and optionally drew random line noise.
- Font loading supported classpath bundled fonts and custom file paths.
- Tests covered code generation, config behavior, image generation, output
  bytes/files, and font provider behavior.

### 4.2 Legacy Issues to Fix

- Legacy `CaptchaConfig` used `MutableList` defaults in a `data class`; the new
  public config should expose immutable `List` values.
- Legacy API returned generic `Captcha<T>` and `ImageCaptcha`; the new API should
  use domain names aligned with the issue: `CaptchaChallenge` and
  `CaptchaGenerator`.
- Legacy `generate()` silently coerced length to at least four in the image
  generator. The new design should validate options up front and keep generated
  text length equal to requested/configured length.
- Legacy code used deprecated `useGraphics`; new code must use current
  `withGraphics`/current image helpers where applicable.
- Legacy KDoc was Korean; new public KDoc must be English.

## 5. Public API

Package: `io.bluetape4k.images.captcha`

```kotlin
interface CaptchaGenerator {
    val options: CaptchaOptions

    fun generate(length: Int = options.length): CaptchaChallenge

    suspend fun generateSuspend(length: Int = options.length): CaptchaChallenge
}
```

```kotlin
class CaptchaChallenge(
    val text: String,
    val image: ImmutableImage,
    val expiresAt: Instant,
)
```

`CaptchaChallenge` is intentionally **not** a `data class` and does not
implement `Serializable`. The repository requires every `data class` to be
`Serializable`, but `ImmutableImage` is not a safe Java-serialization payload.
Applications that need persistence should encode `image` to bytes with the
existing image writer APIs and store their own DTO.

```kotlin
data class CaptchaOptions(
    val length: Int = 6,
    val charSet: String = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789",
    val imageSize: CaptchaImageSize = CaptchaImageSize(width = 200, height = 80),
    val fontSize: Int = 36,
    val noise: CaptchaNoise = CaptchaNoise.Medium,
    val distortion: CaptchaDistortion = CaptchaDistortion.None,
    val backgroundColor: Color = Color.WHITE,
    val textColors: List<Color> = listOf(Color.DARK_GRAY),
    val expiresAfter: Duration = 5.minutes,
    val fonts: List<CaptchaFont> = CaptchaFont.defaults(),
) : Serializable
```

Every `data class` in the new module must implement `Serializable` and define a
`serialVersionUID`. This includes `CaptchaOptions`, `CaptchaImageSize`, and any
data-class implementations of `CaptchaNoise`, `CaptchaDistortion`, or
`CaptchaFont`.

Supporting value types:

- `CaptchaImageSize(width: Int, height: Int)` to avoid same-type positional
  width/height mistakes.
- `CaptchaNoise` is a sealed interface with concrete values:
  - `None`
  - `Low` (`lines=2`, `dots=20`)
  - `Medium` (`lines=4`, `dots=40`)
  - `High` (`lines=8`, `dots=80`)
  - `Custom(lines: Int, dots: Int)` data class; both counts must be in `0..500`
- `CaptchaDistortion` supports `None` and `Wave(strength: Float)`. `strength`
  must be in `0.0f..1.0f`; tests verify dimensions and non-empty encoded output,
  not exact pixel layout.
- Font customization is intentionally limited to logical JVM font family names
  in the first release. Use this public ABI:

```kotlin
data class CaptchaFont(
    val family: String = Font.SANS_SERIF,
    val style: CaptchaFontStyle = CaptchaFontStyle.BOLD,
) : Serializable

enum class CaptchaFontStyle {
    PLAIN,
    BOLD,
    ITALIC,
    BOLD_ITALIC,
}
```

  `CaptchaFontStyle` maps to AWT `Font` constants internally. Do not add bundled
  font binary assets in issue #4; that would require separate license review.
- Public colors use `java.awt.Color`. This is acceptable because the module is
  explicitly Java2D/JVM-only; future non-AWT backends should introduce a
  separate color abstraction instead of changing this API silently.

Factory DSL:

```kotlin
fun captchaGenerator(block: CaptchaOptionsBuilder.() -> Unit = {}): CaptchaGenerator
```

The builder should map issue examples while preserving Kotlin named-value
clarity:

```kotlin
val generator = captchaGenerator {
    length(6)
    charSet("ABCDEFGHJKLMNPQRSTUVWXYZ23456789")
    imageSize(width = 200, height = 80)
    fontSize(36)
    fonts(CaptchaFont(Font.SANS_SERIF, CaptchaFontStyle.BOLD))
    noise(CaptchaNoise.Medium)
    backgroundColor(Color.WHITE)
    textColors(Color.DARK_GRAY)
}
```

## 6. Implementation Design

- Module directory: `images-captcha/`
- Artifact: `io.github.bluetape4k.image:bluetape4k-images-captcha`
- Dependencies:
  - `api(project(":bluetape4k-images"))` because public API exposes
    `ImmutableImage`.
  - `implementation(libs.bluetape4k.core)` if validation helpers are not already
    transitively available through `bluetape4k-images`.
  - `implementation(libs.kotlinx.coroutines.core)` for suspend API if the module
    wraps generation in `Dispatchers.Default` or `Dispatchers.IO`.
  - `testImplementation(libs.bluetape4k.junit5)`.

Renderer:

- Use Java2D through scrimage/`ImmutableImage` creation.
- Fill background, render each character with controlled random rotation and
  jitter, then draw noise.
- Default `charSet` excludes ambiguous characters (`I`, `O`, `0`, `1`) to match
  the issue body. It is uppercase-only by design.
- Use `java.security.SecureRandom` by default for CAPTCHA text generation. The
  concrete generator may expose an internal constructor with a deterministic
  random source for tests; the public factory keeps the secure default.
- Accept a `Clock` in the concrete generator/factory with
  `Clock.systemUTC()` as the default. `expiresAt` is an advisory convenience
  value (`clock.instant() + options.expiresAfter`); applications still own
  challenge storage, replay protection, and validation policy.
- Headless operation must work with `-Djava.awt.headless=true`.

Suspend API:

- `generateSuspend()` should wrap CPU/rendering work in
  `withContext(Dispatchers.Default)` unless tests show Java2D blocks on IO.
- Rethrow `CancellationException` before broad exception handling if any error
  wrapping is added.

## 7. Validation

Validate options at construction/build time:

- `length in 1..32`
- `charSet` not blank, has at least two distinct characters, and contains only
  printable BMP non-control characters
- `imageSize.width in 1..2000`, `imageSize.height in 1..2000`
- `fontSize > 0`
- `expiresAfter > Duration.ZERO`
- `textColors` not empty
- `textColors` alpha values are visible (`alpha > 0`) and not all text colors
  equal `backgroundColor`
- noise/distortion strengths/counts are non-negative and bounded

Use bluetape4k `require*` validation helpers where available. Do not silently
coerce invalid caller input. Because `generate(length: Int)` allows per-call
overrides, both `generate()` and `generateSuspend()` must validate the effective
length before generating text.

## 8. Tests

Add focused tests under `images-captcha/src/test/kotlin`:

- options validation throws `IllegalArgumentException`
- generated `CaptchaChallenge.text.length == requested length`
- generated text uses only configured charset
- generated image width/height match options
- generated `expiresAt` is after generation time and roughly `expiresAfter`
  using an injected fixed `Clock`
- 100 generated challenges from default generator are not all identical
- generated image encodes to bytes through existing image writer APIs
- suspend generation returns equivalent contract and responds to cancellation
  before rendering starts. Mid-render cancellation is not guaranteed because
  Java2D rendering is CPU-bound and non-suspending.
- headless property does not prevent generation

Use `bluetape4k-assertions`, `runTest` or existing coroutine test pattern for
suspend-only tests, and include module test resources:

- `src/test/resources/junit-platform.properties`
- `src/test/resources/logback-test.xml`

## 9. Documentation

- Add `images-captcha/README.md` and `images-captcha/README.ko.md`.
- Update root `README.md` and `README.ko.md` module tables, dependency snippets,
  and module README links.
- Public KDoc must be English.
- Internal spec/plan/lesson may be Korean or English; this spec is English to
  preserve future cross-tool reuse.

## 10. Build and CI

Required checks during implementation:

- `./gradlew projects`
- `./gradlew :bluetape4k-images-captcha:test`
- `./gradlew :bluetape4k-images-captcha:build`
- `git diff --check`

If `.github/workflows/*.yml` contains explicit path filters or module lists,
update CI and Nightly so the new module is covered, then run `actionlint`.
For this repository, treat CI/Nightly registration as mandatory unless verified
otherwise against current workflow files.

The new module test task must set:

```kotlin
tasks.withType<Test>().configureEach {
    systemProperty("java.awt.headless", "true")
}
```

## 11. Acceptance Criteria

- `bluetape4k-images-captcha` is registered and published through normal project
  conventions.
- Public API can generate CAPTCHA text plus `ImmutableImage` synchronously and
  through suspend API.
- Default generator works in headless local/CI JVM.
- README English/Korean docs show dependency and usage.
- Tests pass for the new module.
- PR closes issue #4.
- `.github/workflows/ci.yml` and nightly workflows are checked for explicit
  module lists/path filters and updated if needed.

## 12. Open Decisions

- None. Distortion is included only as `None` plus bounded `Wave`; font bundling
  is deferred to avoid unreviewed binary assets and license drift.

## 13. Step 2-R Review Notes

### Claude Code Opus Advisor

- Artifact: `.omx/artifacts/claude-issue-4-images-captcha-spec-20260524165243.md`
- Rerun artifact: `.omx/artifacts/claude-issue-4-images-captcha-spec-rerun-20260524165543.md`
- Initial verdict: FAIL (`P0=0`, `P1=3`)
- Rerun verdict: PASS (`P0=0`, `P1=0`, `P2=3`, `P3=2`)

| Severity | Finding | Decision |
|---|---|---|
| P1 | `generate(length)` bypassed construction-time validation | Accepted. Spec now requires effective length validation in both sync and suspend generation paths. |
| P1 | `CaptchaChallenge : Serializable` conflicts with non-serializable `ImmutableImage` | Accepted. Spec now uses a regular non-serializable class and documents persistence guidance. |
| P1 | `CaptchaDistortion` public API was unresolved | Accepted. Spec now commits to `None` plus bounded `Wave(strength)`. |
| P2 | CI/Nightly registration was conditional | Accepted. Spec now makes workflow inspection/updates mandatory unless verified unnecessary. |
| P2 | `expiresAt` tests needed a clock seam | Accepted. Spec now requires injectable `Clock`. |
| P2 | `expiresAt` lifecycle scope ambiguous | Accepted. Spec now documents it as advisory only. |
| P2 | Headless property not pinned in build config | Accepted. Spec now requires test JVM headless property. |
| P2 | Default charset exclusion policy could drift | Accepted. Spec now states uppercase-only and ambiguous-excluded. |
| P2 | Font assets need license clearance | Accepted. Spec now defers bundled binary fonts and uses logical JVM font families. |
| P2 | Serializable completeness for options/member data classes | Accepted. Spec now requires `Serializable` and `serialVersionUID` for all module data classes. |
| P2 | AWT `Color` locks API to JVM/AWT | Accepted with rationale. This module is Java2D/JVM-only; future non-AWT backend requires a new abstraction. |
| P2 | Suspend cancellation test expectation too loose | Accepted. Spec now states cancellation is guaranteed before render starts, not mid-render. |
| P1 | Plan review: `CaptchaFont` public type undefined | Accepted. Spec now pins `CaptchaFont` and `CaptchaFontStyle` ABI. |
| P1 | Plan review: `CaptchaNoise` public type undefined | Accepted. Spec now pins sealed values and custom bounds. |
| P2 | Raw `fontStyle: Int` leaks AWT constants | Accepted. Spec now uses `CaptchaFontStyle`. |
| P3 | Missing upper bounds/printable charset/color visibility | Accepted. Spec now sets practical bounds and validation rules. |
