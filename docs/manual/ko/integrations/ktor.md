---
manualId: "ktor-integration"
title: "Ktor 연동"
locale: "ko"
releaseRef: "0.4.0"
---

# Ktor 연동

<code>bluetape4k-images-ktor</code>는 썸네일과 CAPTCHA에 필요한 작은 라우트 도우미를 제공한다. 핵심 이미지, CAPTCHA와 Bluetape Ktor 기반 모듈을 사용한다.

## Thumbnail route

<code>ImageThumbnailKtorRoutes</code>는 이미지 입력을 리사이즈와 인코딩 출력으로 연결한다. 미디어 위치, 허용할 크기와 포맷, 캐시 동작, 인증과 요청 제한은 애플리케이션이 정한다. 요청값으로 임의의 파일 경로를 해석하게 만들면 안 된다.

## CAPTCHA route

<code>CaptchaKtorRoutes</code>는 CAPTCHA 서비스로 챌린지 이미지를 발급하고 답을 검증한다. 라우트 설정은 API 형태를 정하고 챌린지 저장소는 한 번만 쓰는 상태와 만료를 관리한다. JSON 직렬화와 오류 처리는 애플리케이션에 설치한다. 도우미가 전역 Ktor 파이프라인을 몰래 바꾸지는 않는다.

## 애플리케이션 조립

[Ktor 이미지 API 워크숍](../modules/ktor-image-api.md)은 두 라우트 계열과 테스트를 함께 보여 준다. 가장 작은 전체 구조로 실행한 뒤 예제 구성 요소를 애플리케이션 저장소, 인증, 요청률 제한과 관측 코드로 바꾼다.

OCR은 라우트 모듈에 숨겨져 있지 않다. [Ktor OCR 워크숍](../modules/ktor-ocr-api.md)에서 멀티파트 입력, 프레임워크와 무관한 OCR 엔진, 응답 모델과 실패 처리를 명시적으로 조합한다.

## 근거 소스

- [CAPTCHA route](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/images-ktor/src/main/kotlin/io/bluetape4k/images/ktor/CaptchaKtorRoutes.kt)
- [Thumbnail route](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/images-ktor/src/main/kotlin/io/bluetape4k/images/ktor/ImageThumbnailKtorRoutes.kt)
