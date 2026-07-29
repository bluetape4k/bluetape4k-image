# Issue #4 images-captcha 설계 스펙

- 이슈: [#4](https://github.com/bluetape4k/bluetape4k-image/issues/4) `feat: CAPTCHA 이미지 생성 모듈 추가 (images-captcha)`
- Milestone: `0.2.0`
- Workflow: `bluetape4k-workflow` Type A Full Design
- 대상 module: `bluetape4k-images-captcha`

## 1. context

`bluetape4k-image`에는 internal admin tool, B2B screen, offline deployment에서 사용할 first-party CAPTCHA image module이 필요하다. 이런 환경에서는 reCAPTCHA/hCaptcha가 적절하지 않다. issue body는 `bluetape4k-projects`의 `x-obsoleted/captcha`를 가리키지만, 현재 tree에서는 해당 path가 stale이다.

Git history에 따르면 legacy implementation은 2026-05-07의 `494d95ee1 chore: x-obsoleted 레거시 모듈 5개 삭제 (#331)`에서 삭제됐다. 삭제 전 tree에는 다음 요소가 있었다:

- `Captcha.kt`, `CaptchaGenerator.kt`, `CaptchaCodeGenerator.kt`
- `config/CaptchaConfig.kt`, `config/CaptchaTheme.kt`
- `image/ImageCaptcha.kt`, `image/ImageCaptchaGenerator.kt`
- `utils/FontProvider.kt`, embedded fonts, README files, tests

새 module은 검증된 형태를 재사용하되 legacy contract를 맹목적으로 복사하지 않는다. 이 repository에는 이미 `bluetape4k-images`, `ImmutableImage` helper, multilingual README policy, BOM aggregation, Java 21 baseline이 있다.

## 2. 목표

- `images-captcha/` 아래에 published `bluetape4k-images-captcha` module을 추가한다.
- CAPTCHA challenge를 text와 `ImmutableImage`로 생성한다.
- native dependency 없는 configurable Java2D/scrimage implementation을 제공한다.
- synchronous 및 suspend generation API를 지원한다.
- dimension, length, allowed charset, writer output, validation, uniqueness behavior에 대해 충분히 deterministic한 test를 포함한다.
- repository가 explicit module list를 요구하면 Gradle settings, BOM, README files, CI/Nightly workflow scope에 module을 등록한다.

## 3. 비목표

- CAPTCHA validation/store/session lifecycle은 제공하지 않는다. 이 module은 challenge를 생성하며, persistence, rate limiting, replay protection, answer comparison은 application 책임이다.
- audio CAPTCHA는 제공하지 않는다.
- external CAPTCHA service integration은 제공하지 않는다.
- ML 또는 OCR-resistance guarantee는 제공하지 않는다. 이 module은 lightweight friction용 utility이며 high-security bot-defense system이 아니다.
- 현재 `bluetape4k-images`와 shared bluetape4k library로 충분하다면 새 third-party dependency를 추가하지 않는다.

## 4. legacy evidence와 reuse

### 4.1 재사용 가능한 legacy idea

- `CaptchaCodeGenerator`는 bluetape4k validation helper로 non-empty symbol과 positive length를 검증했다.
- default symbol은 uppercase letter와 digit이었다.
- `CaptchaConfig`는 width, height, length, noise count, theme, palette, font path/style, font size를 노출했다.
- `ImageCaptchaGenerator`는 `ImmutableImage`를 만들고 background를 채운 뒤 rotated character를 그렸으며, optional random line noise를 그렸다.
- font loading은 classpath bundled font와 custom file path를 지원했다.
- test는 code generation, config behavior, image generation, output bytes/files, font provider behavior를 cover했다.

### 4.2 수정할 legacy issue

- legacy `CaptchaConfig`는 `data class`에서 `MutableList` default를 사용했다. 새 public config는 immutable `List` 값을 노출해야 한다.
- legacy API는 generic `Captcha<T>`와 `ImageCaptcha`를 반환했다. 새 API는 issue와 맞는 domain name인 `CaptchaChallenge`, `CaptchaGenerator`를 사용한다.
- legacy `generate()`는 image generator에서 length를 조용히 최소 4로 coerce했다. 새 design은 option을 upfront validation하고 generated text length를 requested/configured length와 같게 유지한다.
- legacy code는 deprecated `useGraphics`를 사용했다. 새 code는 적용 가능한 곳에서 current `withGraphics`/current image helper를 사용해야 한다.
- legacy KDoc은 한국어였다. 이번 Epic 요구에 따라 새 public KDoc과 comment도 한국어로 작성한다.

## 5. public API

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

`CaptchaChallenge`는 의도적으로 `data class`가 아니며 `Serializable`을 구현하지 않는다. repository는 모든 `data class`가 `Serializable`이기를 요구하지만, `ImmutableImage`는 안전한 Java-serialization payload가 아니다. persistence가 필요한 application은 기존 image writer API로 `image`를 byte로 encode하고 자체 DTO를 저장해야 한다.

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

새 module의 모든 `data class`는 `Serializable`을 구현하고 `serialVersionUID`를 정의해야 한다. 여기에는 `CaptchaOptions`, `CaptchaImageSize`, 그리고 `CaptchaNoise`, `CaptchaDistortion`, `CaptchaFont`의 data-class implementation이 포함된다.

지원 value type:

- `CaptchaImageSize(width: Int, height: Int)`는 same-type positional width/height 실수를 피하기 위해 사용한다.
- `CaptchaNoise`는 concrete value를 가진 sealed interface다:
  - `None`
  - `Low` (`lines=2`, `dots=20`)
  - `Medium` (`lines=4`, `dots=40`)
  - `High` (`lines=8`, `dots=80`)
  - `Custom(lines: Int, dots: Int)` data class; 두 count는 모두 `0..500`이어야 한다.
- `CaptchaDistortion`은 `None`과 `Wave(strength: Float)`를 지원한다. `strength`는 `0.0f..1.0f` 범위여야 하며, test는 정확한 pixel layout이 아니라 dimension과 non-empty encoded output을 검증한다.
- 첫 release의 font customization은 logical JVM font family name으로 의도적으로 제한한다. public ABI는 다음과 같다:

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

`CaptchaFontStyle`은 내부에서 AWT `Font` constant로 mapping된다. issue #4에서는 bundled font binary asset을 추가하지 않는다. 이는 별도 license review가 필요하기 때문이다.
- public color는 `java.awt.Color`를 사용한다. 이 module은 명시적으로 Java2D/JVM-only이므로 허용된다. future non-AWT backend는 이 API를 조용히 바꾸지 말고 별도 color abstraction을 도입해야 한다.

Factory DSL:

```kotlin
fun captchaGenerator(block: CaptchaOptionsBuilder.() -> Unit = {}): CaptchaGenerator
```

builder는 Kotlin named-value clarity를 유지하면서 issue example을 mapping해야 한다:

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

## 6. implementation design

- Module directory: `images-captcha/`
- Artifact: `io.github.bluetape4k.image:bluetape4k-images-captcha`
- Dependencies:
  - public API가 `ImmutableImage`를 노출하므로 `api(project(":bluetape4k-images"))`.
  - validation helper가 `bluetape4k-images`를 통해 transitively 제공되지 않으면 `implementation(libs.bluetape4k.core)`.
  - module이 generation을 `Dispatchers.Default` 또는 `Dispatchers.IO`로 감싸면 suspend API용 `implementation(libs.kotlinx.coroutines.core)`.
  - `testImplementation(libs.bluetape4k.junit5)`.

Renderer:

- scrimage/`ImmutableImage` creation을 통해 Java2D를 사용한다.
- background를 채우고, controlled random rotation과 jitter로 각 character를 render한 뒤 noise를 그린다.
- default `charSet`은 issue body와 맞게 ambiguous character(`I`, `O`, `0`, `1`)를 제외한다. 설계상 uppercase-only다.
- CAPTCHA text generation에는 기본적으로 `java.security.SecureRandom`을 사용한다. concrete generator는 test용 deterministic random source를 받는 internal constructor를 노출할 수 있고, public factory는 secure default를 유지한다.
- concrete generator/factory는 default `Clock.systemUTC()`와 함께 `Clock`을 받을 수 있어야 한다. `expiresAt`은 advisory convenience value(`clock.instant() + options.expiresAfter`)이며, challenge storage, replay protection, validation policy는 여전히 application 책임이다.
- `-Djava.awt.headless=true`에서 headless operation이 동작해야 한다.

Suspend API:

- test가 Java2D가 IO에서 block됨을 보여주지 않는 한 `generateSuspend()`는 CPU/rendering work를 `withContext(Dispatchers.Default)`로 감싼다.
- error wrapping이 추가되면 broad exception handling 전에 `CancellationException`을 다시 throw한다.

## 7. validation

option은 construction/build time에 검증한다:

- `length in 1..32`
- `charSet`은 blank가 아니고, 최소 두 개의 distinct character를 가지며, printable BMP non-control character만 포함한다.
- `imageSize.width in 1..2000`, `imageSize.height in 1..2000`
- `fontSize > 0`
- `expiresAfter > Duration.ZERO`
- `textColors`는 비어 있지 않다.
- `textColors` alpha 값은 보이는 값(`alpha > 0`)이어야 하며, 모든 text color가 `backgroundColor`와 같으면 안 된다.
- noise/distortion strength/count는 non-negative이고 bounded여야 한다.

사용 가능한 경우 bluetape4k `require*` validation helper를 사용한다. invalid caller input을 조용히 coerce하지 않는다. `generate(length: Int)`가 per-call override를 허용하므로 `generate()`와 `generateSuspend()`는 text 생성 전에 effective length를 모두 validate해야 한다.

## 8. tests

`images-captcha/src/test/kotlin` 아래에 focused test를 추가한다:

- options validation이 `IllegalArgumentException`을 throw한다.
- generated `CaptchaChallenge.text.length == requested length`
- generated text는 configured charset만 사용한다.
- generated image width/height는 options와 일치한다.
- injected fixed `Clock`을 사용해 generated `expiresAt`이 generation time 이후이고 대략 `expiresAfter`와 일치한다.
- default generator에서 생성한 100개 challenge가 모두 동일하지 않다.
- generated image는 기존 image writer API로 byte encode된다.
- suspend generation은 동등한 contract를 반환하고 rendering 시작 전 cancellation에 반응한다. Java2D rendering은 CPU-bound이고 non-suspending이므로 mid-render cancellation은 보장하지 않는다.
- headless property가 generation을 막지 않는다.

`bluetape4k-assertions`, suspend-only test용 `runTest` 또는 기존 coroutine test pattern을 사용하고 module test resource를 포함한다:

- `src/test/resources/junit-platform.properties`
- `src/test/resources/logback-test.xml`

## 9. documentation

- `images-captcha/README.md`와 `images-captcha/README.ko.md`를 추가한다.
- root `README.md`와 `README.ko.md`의 module table, dependency snippet, module README link를 갱신한다.
- 이번 Epic 요구에 따라 public KDoc/comment는 한국어로 작성한다.
- internal spec/plan/lesson은 user-collaboration document이므로 한국어를 사용한다.

## 10. build 및 CI

구현 중 필요한 check:

- `./gradlew projects`
- `./gradlew :bluetape4k-images-captcha:test`
- `./gradlew :bluetape4k-images-captcha:build`
- `git diff --check`

`.github/workflows/*.yml`에 explicit path filter 또는 module list가 있으면 새 module을 cover하도록 CI와 Nightly를 갱신한 뒤 `actionlint`를 실행한다. 이 repository에서는 current workflow file로 불필요함이 검증되지 않는 한 CI/Nightly registration을 mandatory로 취급한다.

새 module test task는 다음을 설정해야 한다:

```kotlin
tasks.withType<Test>().configureEach {
    systemProperty("java.awt.headless", "true")
}
```

## 11. acceptance criteria

- `bluetape4k-images-captcha`가 일반 project convention을 통해 등록되고 publish된다.
- public API는 sync 및 suspend API로 CAPTCHA text와 `ImmutableImage`를 생성할 수 있다.
- default generator는 headless local/CI JVM에서 동작한다.
- README English/Korean docs는 dependency와 usage를 보여준다.
- 새 module test가 통과한다.
- PR이 issue #4를 닫는다.
- `.github/workflows/ci.yml`와 nightly workflow의 explicit module list/path filter를 확인하고 필요하면 갱신한다.

## 12. open decisions

- 없음. Distortion은 `None`과 bounded `Wave`만 포함한다. font bundling은 검토되지 않은 binary asset과 license drift를 피하기 위해 보류한다.

## 13. Step 2-R review notes

### Claude Code Opus advisor

- Artifact: `.omx/artifacts/claude-issue-4-images-captcha-spec-20260524165243.md`
- Rerun artifact: `.omx/artifacts/claude-issue-4-images-captcha-spec-rerun-20260524165543.md`
- Initial verdict: FAIL (`P0=0`, `P1=3`)
- Rerun verdict: PASS (`P0=0`, `P1=0`, `P2=3`, `P3=2`)

| Severity | Finding | Decision |
|---|---|---|
| P1 | `generate(length)`가 construction-time validation을 우회했다. | 수용. spec은 이제 sync 및 suspend generation path 모두에서 effective length validation을 요구한다. |
| P1 | `CaptchaChallenge : Serializable`이 non-serializable `ImmutableImage`와 충돌했다. | 수용. spec은 이제 regular non-serializable class를 사용하고 persistence guidance를 문서화한다. |
| P1 | `CaptchaDistortion` public API가 미해결이었다. | 수용. spec은 이제 `None`과 bounded `Wave(strength)`로 확정한다. |
| P2 | CI/Nightly registration이 conditional이었다. | 수용. spec은 이제 불필요함이 검증되지 않는 한 workflow inspection/update를 mandatory로 만든다. |
| P2 | `expiresAt` test에는 clock seam이 필요했다. | 수용. spec은 injectable `Clock`을 요구한다. |
| P2 | `expiresAt` lifecycle scope가 모호했다. | 수용. spec은 이를 advisory로만 문서화한다. |
| P2 | headless property가 build config에 고정되지 않았다. | 수용. spec은 test JVM headless property를 요구한다. |
| P2 | default charset exclusion policy가 drift될 수 있었다. | 수용. spec은 uppercase-only와 ambiguous-excluded를 명시한다. |
| P2 | font asset에는 license clearance가 필요하다. | 수용. spec은 bundled binary font를 보류하고 logical JVM font family를 사용한다. |
| P2 | option/member data class의 Serializable completeness가 필요했다. | 수용. spec은 모든 module data class에 `Serializable`과 `serialVersionUID`를 요구한다. |
| P2 | AWT `Color`가 API를 JVM/AWT에 고정한다. | 근거와 함께 수용. 이 module은 Java2D/JVM-only이며 future non-AWT backend에는 새 abstraction이 필요하다. |
| P2 | suspend cancellation test expectation이 너무 느슨했다. | 수용. spec은 cancellation이 render 시작 전에는 보장되지만 mid-render에는 보장되지 않는다고 명시한다. |
| P1 | Plan review: `CaptchaFont` public type이 정의되지 않았다. | 수용. spec은 이제 `CaptchaFont`와 `CaptchaFontStyle` ABI를 고정한다. |
| P1 | Plan review: `CaptchaNoise` public type이 정의되지 않았다. | 수용. spec은 sealed value와 custom bound를 고정한다. |
| P2 | raw `fontStyle: Int`가 AWT constant를 노출했다. | 수용. spec은 이제 `CaptchaFontStyle`을 사용한다. |
| P3 | upper bound/printable charset/color visibility가 누락됐다. | 수용. spec은 practical bound와 validation rule을 설정한다. |
