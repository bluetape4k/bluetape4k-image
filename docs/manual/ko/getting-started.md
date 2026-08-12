---
manualId: "getting-started"
title: "시작하기"
locale: "ko"
releaseRef: "0.4.0"
---

# 시작하기

이미지 작업 하나를 끝낼 수 있는 가장 작은 의존성과 런타임부터 고른다. Scrimage와 libvips는 겹치는 문제를 풀지만 배포 조건과 자원 소유 방식이 다르다. 처음부터 백엔드를 모두 넣을 이유는 없다.

## 1. 중앙 BOM 가져오기

애플리케이션의 다른 Bluetape 라이브러리와 같은 <code>bluetape4k-dependencies</code> 버전을 쓴다.

    dependencies {
        implementation(platform("io.github.bluetape4k:bluetape4k-dependencies:<version>"))
        implementation("io.github.bluetape4k.image:bluetape4k-images")
    }

저장소에서 <code>bluetape4k-image-bom</code>도 배포하지만, 일반 사용자는 중앙 BOM을 쓰는 편이 맞다. 그래야 Kotlin, Coroutines, 프레임워크와 다른 Bluetape 라이브러리 버전까지 함께 정렬된다. Image 계열만 따로 관리해야 한다면 [Image BOM 문서](modules/bluetape4k-image-bom.md)를 참고한다.

## 2. 실행 경로 하나 고르기

- JVM만으로 로드, 저장, 필터, 변환과 분석을 처리하려면 [불변 이미지 처리](modules/bluetape4k-images.md)를 선택한다.
- 애플리케이션에 필요한 경우에만 [CAPTCHA](modules/bluetape4k-images-captcha.md), [OCR](modules/bluetape4k-images-ocr.md), [Ktor](modules/bluetape4k-images-ktor.md), [Spring Boot](modules/bluetape4k-images-spring-boot.md)를 더한다.
- libvips를 설치할 수 있으면 [JDK 25 JVips JNI](modules/bluetape4k-images-vips-java21.md)를 검토한다. 배포 JNI artifact는 호환성을 위해 `java21` 이름을 유지한다.
- native-access 옵션을 받아들일 수 있으면 [JDK 25 FFM](modules/bluetape4k-images-vips-java25.md)을 검토한다.

비교 기준은 [백엔드 선택](guides/backend-selection.md)에 정리했다.

## 3. 워크숍 실행하기

가장 짧은 JVM 경로는 [기본 이미지 처리 워크숍](modules/basic-processing.md)이다. 프레임워크 애플리케이션은 [Ktor 이미지 API](modules/ktor-image-api.md)나 [Spring Boot 이미지 API](modules/spring-boot-image-api.md)로 이어간다. OCR은 실행 환경에 Tesseract를 설치하는 과정도 학습 범위에 들어가므로 [Ktor](modules/ktor-ocr-api.md)와 [Spring Boot](modules/spring-boot-ocr-api.md) 예제를 따로 제공한다.

## 4. 실제 경계 검증하기

루트 컴파일만 확인하지 말고 사용할 모듈의 테스트를 실행한다.

    ./gradlew :bluetape4k-images:test

네이티브와 OCR 검사는 실행 환경에 별도 소프트웨어가 필요하며 순차로 실행해야 한다. 활성화하기 전에 [OCR 설정](guides/ocr-setup.md)과 [native 자원 수명 주기](guides/native-resource-lifecycle.md)를 읽는다.

## 근거 소스

- [릴리스 빌드 설정](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/build.gradle.kts)
- [릴리스 의존성 예제](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/README.ko.md#의존성-추가)
