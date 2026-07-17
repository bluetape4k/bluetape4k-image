---
manualId: "bluetape4k-images-captcha"
id: "bluetape4k-images-captcha"
title: "CAPTCHA generation and verification"
locale: "en"
kind: "library"
gradlePath: ":bluetape4k-images-captcha"
sourceDir: "images-captcha"
releaseRef: "0.3.0"
artifact: io.github.bluetape4k.image:bluetape4k-images-captcha
---

# CAPTCHA generation and verification

> Library module

## Problem {#problem}

This module generates Java2D CAPTCHA images and supplies a one-shot verification contract. It keeps rendering, challenge metadata, answer matching, and storage boundaries explicit so applications can replace the in-memory pieces without replacing the image generator.

## When to use it {#when-to-use}

Use it for bounded human-verification challenges in JVM services that do not require a native image runtime. Use the Ktor adapter for ready-made HTTP routes. It is not a complete anti-abuse system: durable distributed storage, rate limiting, bot detection, and audit policy remain application concerns.

## Coordinates {#coordinates}

Maven coordinate: `io.github.bluetape4k.image:bluetape4k-images-captcha`

```kotlin
dependencies {
    implementation(platform("io.github.bluetape4k:bluetape4k-dependencies:<version>"))
    implementation("io.github.bluetape4k.image:bluetape4k-images-captcha")
}
```

## Core concepts {#concepts}

`CaptchaGenerator` renders a `CaptchaChallenge`. `IssuedCaptchaChallenge` is the serializable metadata; do not serialize the Scrimage-bearing challenge. `CaptchaChallengeStore.consume` is the one-shot boundary, and `CaptchaVerificationService` consumes before comparing to prevent replay.

`InMemoryCaptchaChallengeStore` is JVM-local and intended for tests, demos, or a single-node process. Production clusters need an atomic distributed `consume` implementation.

## Quick start {#quick-start}

```kotlin
val generator = captchaGenerator {
    length(6)
    expiresAfter(5.minutes)
}
val verifier = CaptchaVerificationService(
    answerMatcher = CaptchaAnswerMatcher.caseInsensitive(),
)

val challenge = generator.generate()
val id = CaptchaChallengeId("login-42")
verifier.issue(id, challenge)

val result = verifier.verify(id, submittedAnswer)
check(result.verified)
```

## API by task {#api-by-task}

- Generate synchronously with `generate` or from a coroutine with `generateSuspend`.
- Configure dimensions, fonts, colors, noise, distortion, charset, length, and expiry through `CaptchaOptionsBuilder`.
- Persist only `IssuedCaptchaChallenge` plus encoded image bytes.
- Implement `CaptchaChallengeStore` for Redis or another atomic shared store.
- Select exact or trimmed case-insensitive matching through `CaptchaAnswerMatcher`.

## Recommended patterns {#patterns}

Use a cryptographically unpredictable public id and enforce one verification attempt per id. Keep the answer out of logs, client payloads, and metrics. Treat expiry and store TTL as the same policy and apply request-level rate limits outside this library.

## Integrations {#integrations}

`bluetape4k-images-ktor` exposes issue/verify routes over these contracts. The generated image is an `ImmutableImage`, so the core image writers can encode it independently of verification metadata.

## Configuration {#configuration}

Defaults are six uppercase characters from `ABCDEFGHJKLMNPQRSTUVWXYZ23456789`, 200×80 pixels, font size 36, medium noise, no distortion, and five-minute expiry. Length is limited to 1..32; dimensions to 1..2000; font size to 1..512. Text must include a visible color distinct from the background.

## Failure modes {#failures}

Verification returns `Success`, `WrongAnswer`, `Expired`, or `NotFound`. Because verification consumes first, a retry after any result becomes `NotFound`. Invalid option ranges fail at construction. Java2D rendering errors propagate to the caller.

## Operations {#operations}

Java2D rendering is CPU-bound. `generateSuspend` honors cancellation before rendering begins, but cancellation is not guaranteed in the middle of the non-suspending draw. Run headless in servers and observe generation latency without recording answers.

## Testing {#testing}

Use `Java2dCaptchaGeneratorTest` for rendering bounds, `CaptchaOptionsTest` for validation, and `CaptchaVerificationServiceTest` for expiry, replay, and matcher behavior. Inject a `Clock` and deterministic ids in tests.

## Workshops and learning path {#workshops}

Start with generation and local one-shot verification, then move the store to shared infrastructure, and finally add the Ktor route adapter. Test expiry and duplicate submissions before tuning visual distortion.

## Limitations {#limitations}

The in-memory store is not distributed or durable and performs no background expiry cleanup. The module does not provide throttling, identity binding, accessibility alternatives, or a security guarantee against modern vision models.

<!-- release-readme-diagrams:start -->
## Release diagrams {#release-diagrams}

These diagrams are copied byte-for-byte from README assets in the `0.3.0` release tag. They describe this manual's released structure and runtime flows, not later Snapshot changes. Select a preview to open the SVG source.

### CAPTCHA challenge preview

[![CAPTCHA challenge preview](../../assets/readme-diagrams/images-captcha-example-01.png)](../../assets/readme-diagrams/images-captcha-example-01.svg)

_Release README: [`images-captcha/README.md`](https://github.com/bluetape4k/bluetape4k-image/blob/a571c30004f571fe8cfcddc29670c1404d212ec6/images-captcha/README.md)_

<!-- release-readme-diagrams:end -->

## Sources {#sources}

- [Generator contract](https://github.com/bluetape4k/bluetape4k-image/blob/0.3.0/images-captcha/src/main/kotlin/io/bluetape4k/images/captcha/CaptchaGenerator.kt)
- [Options and bounds](https://github.com/bluetape4k/bluetape4k-image/blob/0.3.0/images-captcha/src/main/kotlin/io/bluetape4k/images/captcha/CaptchaOptions.kt)
- [One-shot verification](https://github.com/bluetape4k/bluetape4k-image/blob/0.3.0/images-captcha/src/main/kotlin/io/bluetape4k/images/captcha/CaptchaVerification.kt)
- [Release build](https://github.com/bluetape4k/bluetape4k-image/blob/0.3.0/images-captcha/build.gradle.kts)
