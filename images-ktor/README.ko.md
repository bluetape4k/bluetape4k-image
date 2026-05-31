# bluetape4k-images-ktor

[English](./README.md) | 한국어

bluetape4k 이미지 워크플로우를 위한 Ktor server helper 모듈입니다.

## 기능

- multipart 이미지 업로드를 받아 썸네일 bytes를 반환하는 `Route.bluetape4kImageThumbnailRoutes()`
- base64 PNG CAPTCHA 챌린지를 발급하는 `Route.bluetape4kCaptchaRoutes()`
- `CaptchaVerificationService` 기반 one-shot CAPTCHA 답변 검증
- 발급과 검증을 위한 안정적인 JSON 모델
- bad-request 응답에는 공용 `bluetape4k-ktor-core`의 request parameter helper와
  `ApiErrorResponse` 사용

## 의존성

```kotlin
dependencies {
    implementation("io.github.bluetape4k.image:bluetape4k-images-ktor:<version>")
    implementation("io.github.bluetape4k:bluetape4k-ktor-core")
}
```

공용 bluetape4k Ktor core baseline을 설치하거나, 호환되는 Ktor JSON 지원을 직접
설치하세요.

```kotlin
dependencies {
    implementation("io.ktor:ktor-server-content-negotiation:<ktor-version>")
    implementation("io.ktor:ktor-serialization-kotlinx-json:<ktor-version>")
}
```

## 사용 예시

```kotlin
import io.bluetape4k.images.ktor.bluetape4kCaptchaRoutes
import io.bluetape4k.images.ktor.bluetape4kImageThumbnailRoutes
import io.bluetape4k.ktor.core.Bluetape4kKtorCoreConfig
import io.bluetape4k.ktor.core.installBluetape4kKtorCore
import io.ktor.server.application.Application
import io.ktor.server.routing.routing

fun Application.module() {
    installBluetape4kKtorCore(
        Bluetape4kKtorCoreConfig(installHealthRoutes = false)
    )

    routing {
        bluetape4kImageThumbnailRoutes()
        bluetape4kCaptchaRoutes()
    }
}
```

라우트:

| Method | Path | 설명 |
| --- | --- | --- |
| `POST` | `/images/thumbnail?maxSide=320` | multipart field `file`을 읽어 PNG 썸네일 bytes 반환 |
| `GET` | `/captcha?length=6` | 챌린지를 발급하고 base64 PNG bytes를 반환 |
| `POST` | `/captcha/{id}/verify` | 챌린지를 소비하고 제출된 답변을 검증 |

썸네일 업로드 예시:

```bash
curl -F "file=@photo.jpg;type=image/jpeg" \
  "http://localhost:8080/images/thumbnail?maxSide=320" \
  --output thumbnail.png
```

검증 결과는 `SUCCESS`, `WRONG_ANSWER`, `EXPIRED`, `NOT_FOUND` 중 하나입니다.
`CaptchaVerificationService`는 storage boundary로 유지됩니다. 여러 애플리케이션
인스턴스가 발급된 챌린지를 공유해야 한다면 분산 `CaptchaChallengeStore`를 제공하세요.

## 사용자 정의 설정

```kotlin
import io.bluetape4k.images.captcha.CaptchaChallengeId
import io.bluetape4k.images.captcha.CaptchaVerificationService
import io.bluetape4k.images.captcha.captchaGenerator
import io.bluetape4k.images.ktor.CaptchaKtorRoutesConfig
import io.bluetape4k.images.ktor.ImageThumbnailKtorRoutesConfig
import io.bluetape4k.images.ktor.bluetape4kCaptchaRoutes
import io.bluetape4k.images.ktor.bluetape4kImageThumbnailRoutes
import io.bluetape4k.codec.Base58

val verifier = CaptchaVerificationService()

routing {
    bluetape4kImageThumbnailRoutes(
        ImageThumbnailKtorRoutesConfig(
            routePath = "/media",
            multipartFieldName = "upload",
            maxInputBytes = 5 * 1024 * 1024,
            defaultMaxSide = 256,
            maxAllowedSide = 1024,
        )
    )

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

썸네일 helper는 순수 JVM 기반의 로컬 처리 경계만 제공합니다. persistence, S3/CDN URL,
authorization, native libvips 가속이 필요하면 애플리케이션 레이어에서 조합하세요.
일반 JSON 기본값, error payload, path/query parameter parsing, test-client helper는
공용 `bluetape4k-ktor-*` 모듈을 사용합니다.
