---
manualId: "spring-boot-integration"
title: "Spring Boot 연동"
locale: "ko"
releaseRef: "0.3.0"
---

# Spring Boot 연동

<code>bluetape4k-images-spring-boot</code>는 이미지 저장소, 선택적 CDN URL 서명, 상태 점검과 메트릭을 위한 Spring Boot 4 자동 구성을 제공한다. 이미지 자체는 핵심 라이브러리에서 처리한다.

## 자동 구성 묶음

- 저장소 구성은 로컬 또는 S3 기반 <code>ImageStorage</code>를 선택한다.
- CDN 구성은 필요할 때 CloudFront URL 서명 기능을 만든다.
- 상태 점검 구성은 Actuator 계약으로 저장소 가용성을 보여 준다.
- 메트릭 구성은 Micrometer가 있을 때 저장소 연산을 계측한다.
- 처리 구성은 관련 속성만 바인딩한다. 실제 처리기나 파이프라인 빈은 애플리케이션이 만들고 수명도 직접 관리해야 한다.

선택적인 AWS와 관측 의존성은 라이브러리에서 <code>compileOnly</code>로 선언된다. 애플리케이션은 실제로 사용할 기능의 의존성을 직접 추가해야 한다.

## 설정 소유권

저장소 루트, 버킷과 리전, CDN 키, 업로드 정책과 기능 스위치는 애플리케이션의 안전한 설정에서 읽는다. 로컬 저장소 루트를 검증하고 적절한 권한으로 디렉터리를 만든다. AWS 인증에는 표준 공급자 체인이나 서비스의 자격 증명 정책을 사용한다. 비밀값을 매뉴얼 예제나 저장소 파일에 넣으면 안 된다.

## 워크숍으로 시작하기

[Spring Boot 이미지 API 워크숍](../modules/spring-boot-image-api.md)은 멀티파트 업로드, 로컬 저장소, 다운로드, 썸네일 처리, 설정과 테스트를 보여 준다. 운영에 적용하기 전에는 예제의 로컬 환경 가정을 실제 배포 환경에 맞춰야 한다. OCR은 별도 [Spring Boot OCR 워크숍](../modules/spring-boot-ocr-api.md)을 사용한다.

운영 백엔드를 고르기 전에 [저장소와 CDN](storage-and-cdn.md)을 읽는다.

## 근거 소스

- [자동 구성 소스](https://github.com/bluetape4k/bluetape4k-image/tree/a571c30004f571fe8cfcddc29670c1404d212ec6/images-spring-boot/src/main/kotlin/io/bluetape4k/images/spring/autoconfigure)
- [Spring Boot 모듈 문서](../modules/bluetape4k-images-spring-boot.md)
