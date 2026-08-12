---
manualId: "native-resource-lifecycle"
title: "Native 자원 수명 주기"
locale: "ko"
releaseRef: "0.4.0"
---

# Native 자원 수명 주기

libvips 이미지는 GC에만 맡길 수 있는 일반 값이 아니다. <code>VipsImage</code>마다 네이티브 상태를 소유하며 <code>AutoCloseable</code>을 구현한다.

## 연산 결과까지 범위를 묶기

이미지 읽기, 리사이즈, 자르기 등의 연산은 새 네이티브 이미지를 만들 수 있다. 원본과 결과를 모두 닫는다.

    runtime.load(path).use { source ->
        source.resize(640, 480).use { resized ->
            resized.writeTo(output)
        }
    }

이미 닫힌 범위에서 <code>VipsImage</code>를 반환하면 안 된다. 애플리케이션 API는 인코딩한 바이트나 파일 결과처럼 소유권이 끝난 값을 반환하는 편이 안전하다. `Closeable`을 반환해야 한다면 호출자의 책임을 API 계약에 적는다.

## 한 번 초기화하고 마지막에 종료하기

애플리케이션 시작 시 <code>JVipsRuntime</code> 또는 <code>FfmVipsRuntime</code> 하나를 초기화한다. 종료는 프로세스 전체에 영향을 준다. 종료한 뒤에는 요청 처리, 작업 실행기, 작성기가 libvips를 사용하면 안 된다. 프레임워크를 종료할 때도 트래픽과 작업 실행기를 먼저 멈추고 런타임을 마지막에 닫아야 한다.

## Native 테스트 격리하기

0.4.0 빌드는 JNI와 FFM 테스트를 클래스마다 새 포크에서 실행하고 병렬 포크를 1개로 제한한다. CI 작업을 나눌 때도 이 격리를 유지한다. 네이티브 실패는 프로세스 상태를 망가뜨릴 수 있고 두 백엔드는 전역 라이브러리 상태를 두고 충돌할 수 있다.

## 취소와 부분 출력

코루틴이 취소되어도 <code>use</code>는 필요하다. 구조화된 범위에서 자원을 닫고, 부분 파일이 위험하면 임시 파일에 쓴 뒤 원자적으로 교체한다. 네이티브 디코딩에 들어가기 전에 입력 바이트와 픽셀 수를 제한한다.

## 근거 소스

- [VipsImage 계약](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/images-vips-api/src/main/kotlin/io/bluetape4k/images/vips/VipsImage.kt)
- [JDK 25 JVips 테스트 격리(legacy java21 모듈)](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/images-vips-java21/build.gradle.kts)
- [JDK 25 FFM 테스트 격리](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/images-vips-java25/build.gradle.kts)
