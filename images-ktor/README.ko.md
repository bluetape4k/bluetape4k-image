# bluetape4k-images-ktor

[English](./README.md) | 한국어

bluetape4k 이미지 워크플로우를 위한 Ktor server helper 모듈입니다.

## 기능

- base64 PNG CAPTCHA 챌린지를 발급하는 `Route.bluetape4kCaptchaRoutes()`
- `CaptchaVerificationService` 기반 one-shot CAPTCHA 답변 검증
- 발급, 검증, bad-request 응답을 위한 안정적인 JSON 모델
- 아직 배포되지 않은 bluetape4k 공용 Ktor 모듈에 hard dependency 없음

## 의존성

```kotlin
dependencies {
    implementation("io.github.bluetape4k.image:bluetape4k-images-ktor:<version>")
}
```

애플리케이션에는 Ktor JSON 지원을 설치하세요.

```kotlin
dependencies {
    implementation("io.ktor:ktor-server-content-negotiation:<ktor-version>")
    implementation("io.ktor:ktor-serialization-kotlinx-json:<ktor-version>")
}
```

## 사용 예시

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

라우트:

| Method | Path | 설명 |
| --- | --- | --- |
| `GET` | `/captcha?length=6` | 챌린지를 발급하고 base64 PNG bytes를 반환 |
| `POST` | `/captcha/{id}/verify` | 챌린지를 소비하고 제출된 답변을 검증 |

검증 결과는 `SUCCESS`, `WRONG_ANSWER`, `EXPIRED`, `NOT_FOUND` 중 하나입니다.
`CaptchaVerificationService`는 storage boundary로 유지됩니다. 여러 애플리케이션
인스턴스가 발급된 챌린지를 공유해야 한다면 분산 `CaptchaChallengeStore`를 제공하세요.

## 사용자 정의 설정

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

## 호환성

`bluetape4k-projects` develop에는 이미 공용 Ktor core/testing 모듈이 있습니다.
하지만 해당 artifact는 현재 stable `1.9.2` catalog에 없으므로 이 모듈은 직접 Ktor API를
사용하고, route 계약은 공용 core helper와 함께 쓰기 쉬운 형태로 유지합니다. 공용 Ktor
artifact가 선택한 release train에 배포되면 애플리케이션에서 `bluetape4k-images-ktor`와
함께 설치할 수 있습니다.
