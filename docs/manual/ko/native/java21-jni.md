---
manualId: "java21-jni"
title: "Java 21 JVips 백엔드"
locale: "ko"
releaseRef: "0.3.0"
---

# Java 21 JVips 백엔드

<code>bluetape4k-images-vips-java21</code>은 JVips/JNI로 공통 Vips 계약을 구현한다. 애플리케이션을 JDK 21에서 실행해야 하고 시스템 libvips를 제공할 수 있을 때 선택한다.

## 런타임 조건

대상 실행 환경에 libvips를 설치하고 JVM에서 공유 라이브러리를 찾을 수 있게 해야 한다. Linux와 macOS의 패키지 구성이 다르므로 운영에 쓸 것과 같은 이미지에서 시작 검사를 실행한다. 모듈은 Java 21 도구 체인으로 컴파일하고 테스트한다.

## 프로그래밍 모델

<code>JVipsRuntime</code>을 초기화하고 이미지를 읽은 뒤 모든 <code>JVipsImage</code>를 닫는다. 작업 실행이 끝난 뒤 런타임을 종료한다. 경로, 바이트와 Okio 소스 읽기, 리사이즈, 썸네일, 자르기와 JPEG/PNG/WebP/AVIF 작성기를 구현하며 실제 포맷 지원은 설치된 네이티브 구성에 달려 있다.

애플리케이션 공개 타입은 공통 API로 유지한다. <code>JVipsImage</code>와 JVips 바인딩 세부 정보는 백엔드 어댑터 안에 둔다.

## 테스트와 실패 처리

0.3.0 빌드는 네이티브 테스트에 <code>forkEvery = 1</code>과 <code>maxParallelForks = 1</code>을 적용한다. JNI와 네이티브 전역 상태가 테스트 사이에 남을 수 있으므로 이 격리를 유지한다. 라이브러리 누락, 지원하지 않는 코덱, 과도한 픽셀 수와 런타임 종료 후 사용은 분명하게 실패해야 한다.

[Java 25 FFM](java25-ffm.md)과 비교할 때는 같은 장비와 테스트 이미지를 사용한다.

## 근거 소스

- [JVipsRuntime](https://github.com/bluetape4k/bluetape4k-image/blob/a571c30004f571fe8cfcddc29670c1404d212ec6/images-vips-java21/src/main/kotlin/io/bluetape4k/images/vips/java21/JVipsRuntime.kt)
- [Java 21 모듈 설정](https://github.com/bluetape4k/bluetape4k-image/blob/a571c30004f571fe8cfcddc29670c1404d212ec6/images-vips-java21/build.gradle.kts)
