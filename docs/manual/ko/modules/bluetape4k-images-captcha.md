---
manualId: "bluetape4k-images-captcha"
id: "bluetape4k-images-captcha"
title: "CAPTCHA 생성과 검증"
locale: "ko"
kind: "library"
gradlePath: ":bluetape4k-images-captcha"
sourceDir: "images-captcha"
releaseRef: "0.3.0"
artifact: io.github.bluetape4k.image:bluetape4k-images-captcha
---

# CAPTCHA 생성과 검증

> 라이브러리 모듈

## 제공하는 기능 {#problem}

Java2D로 CAPTCHA 이미지를 만들고 한 번만 사용할 수 있는 검증 계약을 제공합니다. 렌더링, 발급 메타데이터, 답 비교, 저장소 경계가 분리되어 있어 이미지 생성기는 그대로 두고 저장 방식을 바꿀 수 있습니다.

## 사용하기 좋은 경우 {#when-to-use}

네이티브 이미지 런타임 없이 JVM 서비스에 간단한 사람 확인 절차를 넣을 때 적합합니다. HTTP 엔드포인트가 필요하면 Ktor 어댑터를 사용하세요. 분산 저장소, 요청 제한, 봇 탐지와 감사 정책은 애플리케이션이 맡아야 합니다.

## 의존성 좌표 {#coordinates}

Maven 좌표: `io.github.bluetape4k.image:bluetape4k-images-captcha`

```kotlin
dependencies {
    implementation(platform("io.github.bluetape4k:bluetape4k-dependencies:<version>"))
    implementation("io.github.bluetape4k.image:bluetape4k-images-captcha")
}
```

## 핵심 개념 {#concepts}

`CaptchaGenerator`가 `CaptchaChallenge`를 만듭니다. 저장할 때는 Scrimage 객체가 든 challenge 전체가 아니라 직렬화 가능한 `IssuedCaptchaChallenge`와 인코딩한 이미지 바이트를 사용합니다. `CaptchaChallengeStore.consume`은 한 번만 성공해야 하며, `CaptchaVerificationService`는 답을 비교하기 전에 challenge를 소비합니다.

## 빠르게 시작하기 {#quick-start}

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
```

## 작업별 API {#api-by-task}

- `generate`, `generateSuspend`로 challenge를 만듭니다.
- 크기, 폰트, 색상, 노이즈, 왜곡, 문자 집합과 만료 시간을 설정합니다.
- 운영 환경에서는 원자적 `consume`을 제공하는 분산 저장소를 구현합니다.
- 답 비교는 정확 비교와 공백 제거 후 대소문자 무시 비교 중에서 고릅니다.

## 권장 패턴 {#patterns}

외부에 노출하는 id는 예측하기 어렵게 만들고, id당 검증 시도는 한 번으로 제한하세요. 정답은 로그, 응답, 메트릭에 남기지 않습니다. 저장 TTL과 challenge 만료 시간을 같은 정책으로 맞추고 요청 제한은 API 경계에 둡니다.

## 연동 {#integrations}

`bluetape4k-images-ktor`가 발급·검증 라우트를 제공합니다. 생성된 이미지는 `ImmutableImage`이므로 검증 메타데이터와 별개로 core writer를 이용해 인코딩할 수 있습니다.

## 설정 {#configuration}

기본값은 혼동하기 쉬운 `I`, `O`, `0`, `1`을 뺀 대문자 6자, 200×80, 글꼴 크기 36, 중간 노이즈, 왜곡 없음, 5분 만료입니다. 길이는 1..32, 크기는 1..2000, 글꼴은 1..512 범위이며 글자색에는 배경과 구분되는 보이는 색이 있어야 합니다.

## 실패 유형과 해결 방법 {#failures}

검증 결과는 `Success`, `WrongAnswer`, `Expired`, `NotFound`입니다. 검증 전에 데이터를 소비하므로 어떤 결과가 나오든 같은 id로 다시 시도하면 `NotFound`가 됩니다. 잘못된 옵션은 생성 시점에 거부되고 Java2D 렌더링 오류는 호출자에게 전파됩니다.

## 운영 {#operations}

Java2D 렌더링은 CPU 작업입니다. `generateSuspend`는 렌더링 시작 전 취소를 확인하지만, 그리기 도중 취소까지 보장하지는 않습니다. 서버에서는 headless 모드로 실행하고 답을 남기지 않은 채 생성 지연만 관찰하세요.

## 테스트 {#testing}

`CaptchaOptionsTest`, `Java2dCaptchaGeneratorTest`, `CaptchaVerificationServiceTest`가 옵션, 렌더링 범위, 만료, 재사용 방지와 matcher를 검증합니다. 테스트에서는 `Clock`과 id 생성을 고정하세요.

## 학습 경로와 예제 {#workshops}

먼저 로컬 one-shot 검증을 완성하고, 저장소를 공유 인프라로 옮긴 뒤 Ktor 라우트를 붙이세요. 시각 효과를 조정하기 전에 만료와 중복 제출 동작부터 테스트하는 편이 좋습니다.

## 제약 사항 {#limitations}

인메모리 저장소는 분산·영속 저장소가 아니며 만료 데이터를 백그라운드에서 정리하지 않습니다. 요청 제한, 사용자 연결, 접근성 대안, 최신 이미지 인식 모델에 대한 보안 보장을 제공하지 않습니다.

<!-- release-readme-diagrams:start -->
## 배포본 다이어그램 {#release-diagrams}

아래 그림은 `0.3.0` 배포본의 README 자산을 해당 배포 커밋에서 직접 불러옵니다. 이후 SNAPSHOT이 아니라 이 매뉴얼 버전의 구조와 실행 흐름을 보여 줍니다. 미리보기를 누르면 같은 배포 커밋의 SVG 원본이 열립니다.

### CAPTCHA challenge preview

[![CAPTCHA challenge preview](https://raw.githubusercontent.com/bluetape4k/bluetape4k-image/a571c30004f571fe8cfcddc29670c1404d212ec6/docs/images/readme-diagrams/images-captcha-example-01.png)](https://github.com/bluetape4k/bluetape4k-image/blob/a571c30004f571fe8cfcddc29670c1404d212ec6/docs/images/readme-diagrams/images-captcha-example-01.svg)

_배포본 README: [`images-captcha/README.ko.md`](https://github.com/bluetape4k/bluetape4k-image/blob/a571c30004f571fe8cfcddc29670c1404d212ec6/images-captcha/README.ko.md)_

<!-- release-readme-diagrams:end -->

## 근거 자료 {#sources}

- [생성기 계약](https://github.com/bluetape4k/bluetape4k-image/blob/0.3.0/images-captcha/src/main/kotlin/io/bluetape4k/images/captcha/CaptchaGenerator.kt)
- [옵션과 범위](https://github.com/bluetape4k/bluetape4k-image/blob/0.3.0/images-captcha/src/main/kotlin/io/bluetape4k/images/captcha/CaptchaOptions.kt)
- [한 번만 사용하는 검증](https://github.com/bluetape4k/bluetape4k-image/blob/0.3.0/images-captcha/src/main/kotlin/io/bluetape4k/images/captcha/CaptchaVerification.kt)
