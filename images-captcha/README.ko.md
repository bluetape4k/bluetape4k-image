# bluetape4k-images-captcha

[English](./README.md) | 한국어

Kotlin/JVM 서비스용 Java2D CAPTCHA 이미지 챌린지 생성 모듈입니다.

## 기능

- Java2D와 scrimage `ImmutableImage` 기반 순수 JVM 렌더링
- 챌린지 길이, 이미지 크기, 노이즈, 왜곡 옵션의 bounded validation
- JVM logical font만 사용하며 font 파일이나 native runtime 의존성 없음
- 동기 및 suspend-friendly 생성 entrypoint
- 애플리케이션 저장소에서 사용할 advisory expiration timestamp 제공

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

정답 비교, 저장, 만료 처리, replay 방지, rate limiting은 애플리케이션 책임입니다.
`ImmutableImage`를 안정적인 Java serialization payload로 취급하지 않으므로,
`CaptchaChallenge` 자체가 아니라 인코딩된 이미지 바이트와 애플리케이션 메타데이터를
저장하세요.

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
