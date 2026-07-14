---
manualId: "repository-map"
title: "저장소 지도"
locale: "ko"
releaseRef: "0.3.0"
---

# 저장소 지도

0.3.0 릴리스에는 Gradle 프로젝트 15개가 있다. 9개는 Maven 좌표로 배포하고, 5개는 실행 예제이며, 1개는 벤치마크 프로젝트다. 프로젝트 디렉터리와 Gradle 경로, 아티팩트 이름이 항상 같지는 않으므로 릴리스의 <code>settings.gradle.kts</code>를 정확한 목록으로 삼는다.

## 플랫폼과 기반

- [Image BOM](../modules/bluetape4k-image-bom.md)은 이미지 라이브러리 아티팩트 8개의 버전을 맞춘다.
- [불변 이미지 처리](../modules/bluetape4k-images.md)는 Scrimage와 Java2D를 쓰는 JVM 기반 모듈이다. CAPTCHA, OCR, Ktor, Spring Boot와 libvips API의 테스트 이미지가 이 모듈을 사용한다.

## 기능과 프레임워크

- [CAPTCHA](../modules/bluetape4k-images-captcha.md)는 챌린지 생성과 검증 규칙을 맡는다.
- [OCR](../modules/bluetape4k-images-ocr.md)은 Tess4J/Tesseract를 <code>ImmutableImage</code>에 연결한다.
- [Ktor 라우트](../modules/bluetape4k-images-ktor.md)는 썸네일과 CAPTCHA 엔드포인트를 제공한다.
- [Spring Boot](../modules/bluetape4k-images-spring-boot.md)는 저장소, CDN, 상태 점검과 메트릭을 구성한다.

## Native 처리

- [Vips API](../modules/bluetape4k-images-vips-api.md)는 <code>VipsImage</code>, <code>VipsRuntime</code>, 포맷, 작성기와 수명 주기 규칙을 정의한다.
- [Java 21 JVips](../modules/bluetape4k-images-vips-java21.md)는 JNI로 공통 API를 구현한다.
- [Java 25 FFM](../modules/bluetape4k-images-vips-java25.md)은 Foreign Function and Memory API로 구현한다.

공통 API가 애플리케이션 대신 백엔드를 고르거나 초기화하지는 않는다. 측정된 마이그레이션 요구가 없다면 런타임 구현은 하나만 배포한다.

## 학습하고 측정하기

워크숍 5개는 JVM 기본 처리, Ktor 이미지/CAPTCHA, Ktor OCR, Spring Boot 스토리지, Spring Boot OCR을 다룬다. [벤치마크 프로젝트](../modules/bluetape4k-images-benchmark.md)는 처리와 I/O 경로를 비교하지만 라이브러리로 배포하지 않는다.

프로젝트를 이름순으로 읽기보다 [학습 경로](../guides/learning-path.md)에서 목적에 맞는 순서를 고르는 편이 빠르다.

## 근거 소스

- [0.3.0 프로젝트 등록부](https://github.com/bluetape4k/bluetape4k-image/blob/a571c30004f571fe8cfcddc29670c1404d212ec6/settings.gradle.kts#L84-L123)
- [배포 대상 판정 규칙](https://github.com/bluetape4k/bluetape4k-image/blob/a571c30004f571fe8cfcddc29670c1404d212ec6/build.gradle.kts#L46-L58)
