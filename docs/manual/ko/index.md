---
manualId: "repository-overview"
title: "Bluetape4k Image 매뉴얼"
locale: "ko"
releaseRef: "0.4.0"
---

# Bluetape4k Image 매뉴얼

<code>bluetape4k-image</code>에는 Kotlin/JVM 애플리케이션에서 사용할 수 있는 이미지 처리 계열이 두 가지 있다. <code>bluetape4k-images</code> 계열은 Scrimage와 Java2D를 바탕으로 불변 이미지 연산, 필터, 변환, 분석, CAPTCHA, OCR과 프레임워크 연동을 제공한다. libvips 계열은 바인딩과 분리된 공통 API, Java 21 JNI 백엔드, Java 25 FFM 백엔드를 제공한다.

이 매뉴얼은 패키지 이름을 나열하지 않는다. 런타임과 백엔드를 먼저 고르고, 실행 예제 하나를 끝까지 따라간 뒤, 세부 API나 설정이 필요할 때 모듈 문서를 찾아가는 방식으로 구성했다.

## 핵심 기능

- **불변 JVM 이미지 처리:** [이미지 모델](core/immutable-image-model.md), [로딩과 쓰기](core/loading-and-writing.md), [변환과 필터](core/transforms-and-filters.md)에서 Scrimage와 Java2D를 안전하게 조합하는 흐름을 설명합니다.
- **분석과 이미지 형식:** [분석과 유사도](core/analysis-and-similarity.md), [codec 선택 가이드](guides/codec-and-format-selection.md)를 이용해 메타데이터, 비교, 인코딩, 이미지 형식을 선택할 수 있습니다.
- **바코드·CAPTCHA·OCR:** [CAPTCHA](integrations/captcha.md), [OCR](integrations/ocr.md), 바코드 모듈이 웹 애플리케이션에서 자주 쓰는 추출과 인증 흐름을 제공합니다.
- **네이티브 libvips 백엔드:** 공통 [Vips API](native/vips-api.md)에 [Java 21 JNI](native/java21-jni.md) 또는 [Java 25 FFM](native/java25-ffm.md) 백엔드를 연결하며, 네이티브 자원 소유권을 명시적으로 관리합니다.
- **웹 프레임워크 연동:** [Ktor](integrations/ktor.md)와 [Spring Boot](integrations/spring-boot.md)가 이미지 처리, 업로드, CAPTCHA, OCR, 상태 점검, 메트릭을 애플리케이션 생명 주기에 연결합니다.
- **스토리지와 운영 선택:** [스토리지와 CDN](integrations/storage-and-cdn.md), [성능 선택](guides/performance-selection.md), [테스트와 운영](guides/testing-and-operations.md) 문서에서 애플리케이션이 직접 책임질 경계를 정합니다.

## 버전 기준

애플리케이션은 <code>io.github.bluetape4k:bluetape4k-dependencies:&lt;version&gt;</code> 하나만 선택하면 된다. Image BOM, Scrimage, Ktor, Spring Boot, 네이티브 바인딩 버전을 따로 맞출 필요는 없다.

이 매뉴얼의 기술 기준은 변경되지 않는 <code>0.4.0</code> 릴리스다. 이 태그에는 배포 라이브러리 8개, 배포 BOM 1개, 실행 예제 5개, 배포하지 않는 벤치마크 프로젝트 1개가 들어 있다.

- [0.4.0 릴리스 태그](https://github.com/bluetape4k/bluetape4k-image/tree/0.4.0)
- [릴리스 커밋 a571c300](https://github.com/bluetape4k/bluetape4k-image/commit/ea5175b083babf8880f53cf80c9a264a0c61777e)
- 런타임 기준: Scrimage, OCR, 프레임워크와 JNI 계열은 JDK 21, FFM 백엔드는 JDK 25

이 태그 이후에 추가된 기능은 다루지 않는다. 매뉴얼의 소스 링크는 `0.4.0` 릴리스 태그나 해당 태그의 고정 커밋을 가리킨다.

## 어디서 시작할까

- [시작하기](getting-started.md)에서 의존성과 런타임 기준을 정한다.
- Scrimage, JVips, Java 25 FFM 중 무엇을 쓸지 정하기 전에 [백엔드 선택](guides/backend-selection.md)을 읽는다.
- [학습 경로](guides/learning-path.md)를 따라 기본 처리, 프레임워크 연동, OCR, 네이티브 실행 순으로 익힌다.
- [저장소 지도](architecture/repository-map.md)에서 릴리스 프로젝트 15개의 관계를 확인한다.
- 네이티브 라이브러리, OCR 언어 데이터, 스토리지와 CDN 설정을 운영에 넣기 전에 [테스트와 운영](guides/testing-and-operations.md)을 읽는다.

## 책임 경계

라이브러리는 이미지 어댑터와 공통 연산, 프레임워크 연결을 맡는다. 업로드 제한, 허용 포맷, 파일과 객체 스토리지 정책, 네이티브 패키지 설치, OCR 언어 데이터, 종료 순서와 운영 관측은 애플리케이션이 정해야 한다.

## 근거 소스

- [릴리스 프로젝트 등록부](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/settings.gradle.kts)
- [릴리스 저장소 안내서](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/README.ko.md)
