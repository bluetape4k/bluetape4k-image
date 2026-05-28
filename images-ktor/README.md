# bluetape4k-images-ktor

English | [한국어](./README.ko.md)

Ktor server helpers for bluetape4k image workflows.

## Features

- `Route.bluetape4kCaptchaRoutes()` for issuing base64 PNG CAPTCHA challenges
- One-shot CAPTCHA answer verification backed by `CaptchaVerificationService`
- Stable JSON response models for issue, verify, and bad-request responses
- No hard dependency on the unreleased shared bluetape4k Ktor modules; applications
  can still install those modules when their release train provides them

## Dependency

```kotlin
dependencies {
    implementation("io.github.bluetape4k.image:bluetape4k-images-ktor:<version>")
}
```

Install Ktor JSON support in the application:

```kotlin
dependencies {
    implementation("io.ktor:ktor-server-content-negotiation:<ktor-version>")
    implementation("io.ktor:ktor-serialization-kotlinx-json:<ktor-version>")
}
```

## Usage

```kotlin
import io.bluetape4k.images.ktor.bluetape4kCaptchaRoutes
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing

fun Application.module() {
    install(ContentNegotiation) {
        json()
    }

    routing {
        bluetape4kCaptchaRoutes()
    }
}
```

Routes:

| Method | Path | Description |
| --- | --- | --- |
| `GET` | `/captcha?length=6` | Issues a challenge and returns base64 PNG bytes |
| `POST` | `/captcha/{id}/verify` | Consumes the challenge and verifies the submitted answer |

Verification returns `SUCCESS`, `WRONG_ANSWER`, `EXPIRED`, or `NOT_FOUND`.
`CaptchaVerificationService` remains the storage boundary. Use a distributed
`CaptchaChallengeStore` when multiple application instances must share issued
challenges.

## Custom Configuration

```kotlin
import io.bluetape4k.images.captcha.CaptchaChallengeId
import io.bluetape4k.images.captcha.CaptchaVerificationService
import io.bluetape4k.images.captcha.captchaGenerator
import io.bluetape4k.images.ktor.CaptchaKtorRoutesConfig
import io.bluetape4k.images.ktor.bluetape4kCaptchaRoutes
import io.bluetape4k.codec.Base58

val verifier = CaptchaVerificationService()

routing {
    bluetape4kCaptchaRoutes(
        CaptchaKtorRoutesConfig(
            routePath = "/security/captcha",
            generator = captchaGenerator { length(6) },
            verificationService = verifier,
            idFactory = { CaptchaChallengeId("login-${Base58.randomString(12)}") },
        )
    )
}
```

## Compatibility

`bluetape4k-projects` develop already contains shared Ktor core/testing modules.
Those artifacts are not present in the current stable `1.9.2` catalog, so this
module uses direct Ktor APIs and keeps the route contract compatible with the
shared core helpers. Once the shared Ktor artifacts are published on the selected
release train, applications may install them alongside `bluetape4k-images-ktor`.
