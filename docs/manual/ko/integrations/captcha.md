---
manualId: "captcha"
title: "CAPTCHA 생성과 검증"
locale: "ko"
releaseRef: "0.3.0"
---

# CAPTCHA 생성과 검증

<code>bluetape4k-images-captcha</code>는 이미지 생성과 챌린지 저장, 한 번만 허용하는 검증을 분리한다. 문자를 그리는 일만으로 CAPTCHA 흐름이 완성되는 것은 아니다.

## 생성

<code>CaptchaGenerator</code>는 <code>CaptchaOptions</code>를 받아 <code>CaptchaChallenge</code>를 만든다. 이미지 크기, 글꼴, 색상, 잡음, 왜곡과 답안 특성을 설정할 수 있다. 제품이나 테넌트 설정을 받을 때 범위를 검증한다. 지나치게 큰 이미지, 글꼴, 잡음 개수는 읽기 어려운 이미지나 과도한 연산을 만든다.

Java2D 생성은 테스트와 서버에서 헤드리스로 실행된다. 결과는 핵심 모듈의 불변 이미지 모델을 사용하므로 일반 이미지 작성기로 인코딩할 수 있다.

## 검증 수명 주기

<code>CaptchaChallengeStore</code>가 챌린지 상태를 관리한다. <code>CaptchaVerificationService</code>는 성공, 오답, 만료와 챌린지 없음 결과를 구분한다. 검증은 한 번만 성공해야 한다. 동시에 들어온 요청 두 개가 같은 챌린지를 모두 소비하지 못하도록 저장소 연산을 원자적으로 설계한다.

평문 답안은 필요한 시간보다 오래 저장하거나 로그에 남기지 않는다. 챌린지를 세션이나 트랜잭션에 묶고 빠르게 만료한다. 발급과 검증 라우트에는 요청률 제한을 적용한다. 클라이언트 오류는 식별자 존재 여부를 드러내지 않게 단순화한다.

## 프레임워크 경로

[Ktor 연동](ktor.md)은 발급과 검증 라우트 도우미를 제공한다. [Ktor 이미지 API 워크숍](../modules/ktor-image-api.md)에서 전체 흐름을 확인할 수 있다. 다른 프레임워크는 생성기, 저장소와 서비스를 직접 쓰면 된다.

## 근거 소스

- [CAPTCHA generator 계약](https://github.com/bluetape4k/bluetape4k-image/blob/a571c30004f571fe8cfcddc29670c1404d212ec6/images-captcha/src/main/kotlin/io/bluetape4k/images/captcha/CaptchaGenerator.kt)
- [검증 수명 주기](https://github.com/bluetape4k/bluetape4k-image/blob/a571c30004f571fe8cfcddc29670c1404d212ec6/images-captcha/src/main/kotlin/io/bluetape4k/images/captcha/CaptchaVerification.kt)
