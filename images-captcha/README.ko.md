# bluetape4k-images-captcha

[English](./README.md) | 한국어

Kotlin/JVM 서비스용 Java2D CAPTCHA 이미지 챌린지 생성 모듈입니다.

## 기능

- Java2D와 scrimage `ImmutableImage` 기반 순수 JVM 렌더링
- 챌린지 길이, 이미지 크기, 노이즈, 왜곡 옵션의 bounded validation
- JVM logical font만 사용하며 font 파일이나 native runtime 의존성 없음
- 동기 및 suspend-friendly 생성 entrypoint
- pluggable challenge storage 를 지원하는 one-shot verification service contract 제공

## 의존성

```kotlin
dependencies {
    implementation("io.github.bluetape4k.image:bluetape4k-images-captcha:<version>")
}
```

## 사용 예시

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

`CaptchaVerificationService`로 정답 메타데이터를 저장하고 첫 검증 시도에서 challenge를
소비할 수 있습니다.

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

CAPTCHA 검증은 생성된 이미지 바이트와 애플리케이션 소유 메타데이터를 분리해서 다룹니다.

1. 요청 단위로 `CaptchaChallenge`를 생성합니다.
2. 이미지 바이트를 인코딩해서 클라이언트에 반환합니다.
3. 애플리케이션에서 볼 수 있는 `CaptchaChallengeId` 아래에 `IssuedCaptchaChallenge`
   메타데이터를 저장합니다.
4. 사용자가 제출한 답을 `CaptchaVerificationService.verify`로 검증합니다.
5. 어떤 검증 결과가 나오더라도 해당 challenge id는 종료된 것으로 취급합니다.

`CaptchaVerificationService.verify`는 만료 여부나 정답 비교 전에
`CaptchaChallengeStore.consume`을 호출합니다. Store 구현은 `consume`을 atomic 하게 만들어야
하며, 성공, 오답, 만료 결과 모두 저장된 메타데이터를 제거해야 합니다. 이 one-shot contract는
같은 challenge id에 대한 replay와 반복 추측을 막기 위한 경계입니다.

`InMemoryCaptchaChallengeStore`는 테스트, 데모, single-node 애플리케이션에 적합합니다.
여러 애플리케이션 인스턴스가 challenge 메타데이터를 공유해야 한다면 Redis, database,
session storage 용 `CaptchaChallengeStore` 구현을 제공하세요. 운영 store는
`IssuedCaptchaChallenge`의 `expiresAt` 값을 보존하고, 그 시점 이상 유지되는 backend TTL을
적용하며, 사용자가 답을 제출하지 않아도 오래된 레코드를 정리해야 합니다.

`ImmutableImage`를 안정적인 Java serialization payload로 취급하지 않으므로
`CaptchaChallenge` 자체가 아니라 인코딩된 이미지 바이트와 `IssuedCaptchaChallenge` 형태의
메타데이터를 저장하세요. Rate limiting 정책, id 생성, tenant scoping, issue/verify route의
abuse control은 여전히 애플리케이션 책임입니다.

## 옵션

| 옵션 | 기본값 | 설명 |
| --- | --- | --- |
| `length` | `6` | 호출별 override 지원, 유효 범위는 `1..32` |
| `charSet` | `I`, `O`, `0`, `1`을 제외한 대문자와 숫자 | 출력 가능한 non-whitespace BMP 문자만 허용 |
| `imageSize` | `200 x 80` | 가로/세로 `1..2000` bounded |
| `fontSize` | `36` | 양수 Java2D font size |
| `noise` | `CaptchaNoise.Medium` | `None`, `Low`, `Medium`, `High`, bounded `Custom` |
| `distortion` | `CaptchaDistortion.None` | 선택적 bounded horizontal wave distortion |
| `expiresAfter` | `5.minutes` | advisory timestamp 전용 |

## Coroutine Entry

```kotlin
val challenge = generator.generateSuspend()
```

`generateSuspend`는 CPU-bound Java2D 렌더링 시작 전 cancellation을 확인합니다.
Java2D drawing은 non-suspending 작업이므로 렌더링 중간 cancellation은 보장하지 않습니다.
