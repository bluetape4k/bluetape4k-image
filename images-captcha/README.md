# bluetape4k-images-captcha

English | [한국어](./README.ko.md)

Java2D CAPTCHA image challenge generation for Kotlin/JVM services.

## Features

- Pure JVM rendering through Java2D and scrimage `ImmutableImage`
- Bounded challenge length, image size, noise, and distortion options
- Logical JVM fonts only; no bundled font files or native runtime dependency
- Synchronous and suspend-friendly generation entrypoints
- One-shot verification service contract with pluggable challenge storage

## Dependency

```kotlin
dependencies {
    implementation("io.github.bluetape4k.image:bluetape4k-images-captcha:<version>")
}
```

## Usage

```kotlin
import com.sksamuel.scrimage.nio.PngWriter
import io.bluetape4k.images.captcha.CaptchaDistortion
import io.bluetape4k.images.captcha.CaptchaNoise
import io.bluetape4k.images.captcha.captchaGenerator

val generator = captchaGenerator {
    length(6)
    imageSize(width = 200, height = 80)
    noise(CaptchaNoise.Medium)
    distortion(CaptchaDistortion.Wave(0.2f))
}

val challenge = generator.generate()
val pngBytes = challenge.image.forWriter(PngWriter.MaxCompression).bytes()
```

Use `CaptchaVerificationService` to persist answer metadata and consume each
challenge on the first verification attempt:

```kotlin
import io.bluetape4k.images.captcha.CaptchaChallengeId
import io.bluetape4k.images.captcha.CaptchaVerificationResult
import io.bluetape4k.images.captcha.CaptchaVerificationService

val verifier = CaptchaVerificationService()
val issued = verifier.issue(CaptchaChallengeId("login-form:request-123"), challenge)

when (val result = verifier.verify(issued.id, userAnswer)) {
    is CaptchaVerificationResult.Success -> Unit
    is CaptchaVerificationResult.WrongAnswer -> Unit
    is CaptchaVerificationResult.Expired -> Unit
    is CaptchaVerificationResult.NotFound -> Unit
}
```

## Verification Lifecycle

CAPTCHA verification is intentionally split between generated image bytes and
application-owned metadata:

1. Generate a `CaptchaChallenge` for the request.
2. Encode and return the image bytes to the client.
3. Store `IssuedCaptchaChallenge` metadata under an application-visible
   `CaptchaChallengeId`.
4. Verify the user answer with `CaptchaVerificationService.verify`.
5. Treat every verification result as terminal for that challenge id.

`CaptchaVerificationService.verify` calls `CaptchaChallengeStore.consume` before
checking expiration or comparing the answer. Store implementations must make
`consume` atomic so a successful answer, wrong answer, or expired challenge all
remove the stored metadata. This one-shot contract prevents replay and repeated
guessing against the same challenge id.

`InMemoryCaptchaChallengeStore` is useful for tests, demos, and single-node
applications. Implement `CaptchaChallengeStore` for Redis, database, or session
storage when challenge metadata must be shared across application instances.
Production stores should preserve the `expiresAt` value from
`IssuedCaptchaChallenge`, apply a backend TTL at least as long as that instant,
and clean up stale records even if the user never submits an answer.

Persist encoded image bytes and `IssuedCaptchaChallenge`-style metadata, not
`CaptchaChallenge`, because `ImmutableImage` is not treated as a stable Java
serialization payload. Applications still own rate limiting policy, id
generation, tenant scoping, and abuse controls around issue and verify routes.

## Options

| Option | Default | Notes |
| --- | --- | --- |
| `length` | `6` | Per-call override is supported; valid range is `1..32` |
| `charSet` | Uppercase letters and digits without `I`, `O`, `0`, `1` | Printable non-whitespace BMP characters only |
| `imageSize` | `200 x 80` | Width and height are bounded to `1..2000` |
| `fontSize` | `36` | Positive Java2D font size |
| `noise` | `CaptchaNoise.Medium` | `None`, `Low`, `Medium`, `High`, or bounded `Custom` |
| `distortion` | `CaptchaDistortion.None` | Optional bounded horizontal wave distortion |
| `expiresAfter` | `5.minutes` | Advisory timestamp only |

## Coroutine Entry

```kotlin
val challenge = generator.generateSuspend()
```

`generateSuspend` checks cancellation before CPU-bound Java2D rendering starts.
Mid-render cancellation is not guaranteed because Java2D drawing is
non-suspending.
