---
manualId: "java25-ffm"
title: "Java 25 FFM 백엔드"
locale: "ko"
releaseRef: "0.4.0"
---

# Java 25 FFM 백엔드

<code>bluetape4k-images-vips-java25</code>는 vips-ffm 바인딩과 Java Foreign Function and Memory API로 공통 Vips API를 구현한다.

## 런타임 조건

Java 25 도구 체인, 시스템 libvips와 다음 옵션이 필요하다.

    --enable-native-access=ALL-UNNAMED

macOS Homebrew 환경에서 0.4.0 Gradle 테스트는 <code>/opt/homebrew/lib</code>가 있으면 <code>DYLD_LIBRARY_PATH</code>로 노출한다. 운영 시작 설정은 Gradle 테스트 환경에 기대지 말고 같은 역할의 라이브러리 경로를 제공해야 한다.

## 프로그래밍 모델

공통 API 뒤에서 <code>FfmVipsRuntime</code>과 <code>FfmVipsImage</code>를 쓴다. 원본과 연산 결과를 <code>use</code>로 닫는다. 이미지 읽기, 리사이즈, 썸네일, 자르기, 코루틴 도우미, Okio 소스와 JPEG/PNG/WebP/HEIF 계열 작성기를 제공하며 실제 지원 범위는 네이티브 설치에 달려 있다.

FFM 의존성은 Java 25 클래스 파일을 사용한다. Java 21 빌드 JVM의 AtomicFU 변환기가 이를 안전하게 처리할 수 없으므로 이 모듈은 AtomicFU JVM 변환을 끈다. 이 빌드 제약을 유지해야 한다.

## 배포 조건으로 선택하기

JDK 25와 native-access 옵션을 배포 요구사항으로 받아들일 때만 이 백엔드를 쓴다. 저장소 벤치마크 숫자만 보고 고르지 말고 대상 컨테이너나 실행 환경에서 시작 동작, 코덱, 메모리와 처리량을 검증한다.

## 근거 소스

- [FfmVipsRuntime](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/images-vips-java25/src/main/kotlin/io/bluetape4k/images/vips/java25/FfmVipsRuntime.kt)
- [Java 25 모듈 설정](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/images-vips-java25/build.gradle.kts)
