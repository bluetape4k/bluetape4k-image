---
manualId: "spring-vs-ktor"
title: "Spring Boot와 Ktor"
locale: "ko"
releaseRef: "0.4.0"
---

# Spring Boot와 Ktor

애플리케이션 설정과 수명 주기를 이미 맡고 있는 프레임워크를 고른다. 이미지 도우미를 쓰려고 두 번째 프레임워크를 들일 필요는 없다.

## Ktor가 맞는 경우

[Ktor 연동](../integrations/ktor.md)은 범위가 작다. Image와 CAPTCHA 모듈 위에 썸네일과 CAPTCHA 라우트 도우미를 제공한다. JSON 직렬화, 인증, 오류 처리, 요청 크기 정책과 저장 방식은 애플리케이션이 설치한다. [Ktor 이미지 API 워크숍](../modules/ktor-image-api.md)에서 챌린지 발급과 검증, 썸네일을 함께 확인할 수 있다.

Ktor OCR은 [Ktor OCR 워크숍](../modules/ktor-ocr-api.md)에서 직접 조립한다. OCR 라이브러리 자체는 프레임워크에 기대지 않는다.

## Spring Boot가 맞는 경우

[Spring Boot 연동](../integrations/spring-boot.md)은 범위가 더 넓다. 로컬 또는 S3 저장소, 선택적 CloudFront URL 서명, 상태 점검과 메트릭을 자동 구성한다. Boot가 이미 구성 속성, 빈 수명 주기, Actuator와 Micrometer를 관리한다면 이 경로가 자연스럽다. [Spring Boot 이미지 API 워크숍](../modules/spring-boot-image-api.md)을 따라가면 된다.

[Spring Boot OCR 워크숍](../modules/spring-boot-ocr-api.md)은 프레임워크와 무관한 OCR 엔진을 웹 컨트롤러에 연결한다. 실행 환경의 Tesseract는 여전히 명시적인 런타임 선행 조건이다.

## 라이브러리를 직접 쓰는 경우

일괄 작업, CLI 도구나 다른 프레임워크에서는 <code>bluetape4k-images</code>, CAPTCHA, OCR, Vips를 직접 쓴다. 프레임워크 모듈은 어댑터일 뿐 핵심 이미지 처리의 선행 조건이 아니다.

어느 경로를 선택해도 업로드 제한, 인증, 저장소 자격 증명, 응답 캐시와 종료 동작은 애플리케이션이 정한다.

## 근거 소스

- [Ktor 릴리스 모듈](https://github.com/bluetape4k/bluetape4k-image/tree/ea5175b083babf8880f53cf80c9a264a0c61777e/images-ktor)
- [Spring Boot 릴리스 모듈](https://github.com/bluetape4k/bluetape4k-image/tree/ea5175b083babf8880f53cf80c9a264a0c61777e/images-spring-boot)
