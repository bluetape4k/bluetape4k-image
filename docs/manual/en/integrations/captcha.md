---
manualId: "captcha"
title: "CAPTCHA Generation and Verification"
locale: "en"
releaseRef: "0.3.0"
---

# CAPTCHA Generation and Verification

<code>bluetape4k-images-captcha</code> separates image generation from challenge storage and one-shot verification. This boundary matters: drawing text is only one part of a usable CAPTCHA flow.

## Generation

<code>CaptchaGenerator</code> produces a <code>CaptchaChallenge</code> from <code>CaptchaOptions</code>. Options cover image size, fonts, colors, noise, distortion, and answer characteristics. Validate ranges before accepting product or tenant configuration; extreme dimensions, font sizes, or noise counts can create unusable images or expensive work.

Java2D generation runs headless in tests and servers. The result uses the core immutable image model, so normal image writers can encode the challenge.

## Verification lifecycle

<code>CaptchaChallengeStore</code> owns challenge state. <code>CaptchaVerificationService</code> distinguishes success from wrong answer, expiry, and missing challenge. Verification is one-shot: design the store operation so two concurrent requests cannot both consume the same challenge.

Do not store or log the plaintext answer longer than required. Bind a challenge to the relevant session or transaction, expire it promptly, rate-limit issue and verify routes, and return a generic client failure that does not reveal whether an identifier exists.

## Framework path

[Ktor integration](ktor.md) supplies issue and verify route helpers. The [Ktor image API workshop](../modules/ktor-image-api.md) shows the full lifecycle. Other frameworks can use the generator, store, and service directly.

## Sources

- [CAPTCHA generator contract](https://github.com/bluetape4k/bluetape4k-image/blob/a571c30004f571fe8cfcddc29670c1404d212ec6/images-captcha/src/main/kotlin/io/bluetape4k/images/captcha/CaptchaGenerator.kt)
- [Verification lifecycle](https://github.com/bluetape4k/bluetape4k-image/blob/a571c30004f571fe8cfcddc29670c1404d212ec6/images-captcha/src/main/kotlin/io/bluetape4k/images/captcha/CaptchaVerification.kt)
