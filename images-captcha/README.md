# bluetape4k-images-captcha

English | [한국어](./README.ko.md)

Java2D CAPTCHA image challenge generation for Kotlin/JVM services.

## Features

- Pure JVM rendering through Java2D and scrimage `ImmutableImage`
- Bounded challenge length, image size, noise, and distortion options
- Logical JVM fonts only; no bundled font files or native runtime dependency
- Synchronous and suspend-friendly generation entrypoints
- Advisory expiration timestamp for application-owned storage

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

Applications own answer comparison, storage, expiration enforcement, replay
protection, and rate limiting. Persist encoded image bytes and application
metadata, not `CaptchaChallenge`, because `ImmutableImage` is not treated as a
stable Java serialization payload.

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
