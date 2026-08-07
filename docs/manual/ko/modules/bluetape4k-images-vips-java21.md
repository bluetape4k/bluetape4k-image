---
manualId: "bluetape4k-images-vips-java21"
id: "bluetape4k-images-vips-java21"
title: "Java 21 JVips 백엔드"
locale: "ko"
kind: "library"
gradlePath: ":bluetape4k-images-vips-java21"
sourceDir: "images-vips-java21"
releaseRef: "0.4.0"
artifact: io.github.bluetape4k.image:bluetape4k-images-vips-java21
---

# Java 21 JVips 백엔드

> 라이브러리 모듈

## 제공하는 기능 {#problem}

Java 21에서 JVips와 JNI로 공통 libvips API를 구현합니다. Java 21 도구 체계를 유지하는 서비스가 선택할 수 있는 네이티브 백엔드입니다.

## 사용하기 좋은 경우 {#when-to-use}

Java 21 호환성이 필수이고 JVM 아키텍처에 맞는 JVips/libvips 네이티브 라이브러리를 준비할 수 있을 때 선택하세요. 네이티브 배포를 피하려면 핵심 이미지 모듈을 사용하고, Java 25 FFM과의 성능은 실제 환경에서 따로 측정해야 합니다.

## 의존성 좌표 {#coordinates}

Maven 좌표: `io.github.bluetape4k.image:bluetape4k-images-vips-java21`

`bluetape4k-dependencies`를 가져오고 `vips-api`와 이 백엔드를 함께 추가합니다.

## 핵심 개념 {#concepts}

`JVipsRuntime`은 CAS 기반 프로세스 singleton입니다. `vipsImageOf`는 크기, 매직 바이트, 디코딩, 픽셀 수를 차례로 검증합니다. `JVipsImage`는 리사이즈·썸네일·크롭 전에 네이티브 이미지를 복제하므로 결과마다 독립된 `NativeHandle`을 소유합니다.

## 빠르게 시작하기 {#quick-start}

```kotlin
JVipsRuntime.init(concurrency = 4)

vipsImageOf(Path.of("input.jpg")).use { source ->
    source.thumbnail(800).use { thumbnail ->
        thumbnail.writeTo(Path.of("output.webp"), VipsImageFormat.WEBP)
    }
}
```

## 작업별 API {#api-by-task}

- `vipsImageOf`, `suspendVipsImageOf`로 바이트, 파일, 경로, 스트림과 Okio source를 읽습니다.
- 리사이즈, 썸네일, 크롭과 JPEG/PNG/WebP/AVIF 인코딩을 수행합니다.
- 원본과 변환 결과를 각각 닫습니다.
- 런타임은 JVM이 끝날 때만 종료합니다.

## 권장 패턴 {#patterns}

`Path`는 애플리케이션이 허용한 루트 아래인지 먼저 확인하세요. `BufferedSource`는 호출자가 닫고 일반 `Source`는 팩토리가 닫습니다. 이미지 객체를 여러 스레드에서 공유하지 말고 동시성은 상위 파이프라인에서 제한합니다.

## 연동 {#integrations}

`bluetape4k-images-vips-api`를 JVips로 구현하지만 외부 API에는 JVips 타입을 노출하지 않습니다. 실행 시 시스템 libvips와 JVM/네이티브 아키텍처가 맞아야 합니다.

## 설정 {#configuration}

Java 21에서 실행합니다. 입력은 최대 50MiB이고 JPEG/PNG/WebP/AVIF/HEIC 매직 바이트만 허용하며, 디코딩 뒤 `JVipsRuntime.maxPixels`를 검사합니다. `0.4.0`에서는 경로 입력도 제한 확인 후 전체 압축 파일을 바이트 배열로 읽습니다.

## 실패 유형과 해결 방법 {#failures}

지원하지 않거나 손상·초과한 입력은 `VipsDecodeException`, 기하 연산은 `VipsOperationException`, 인코딩은 `VipsEncodeException`입니다. 초기화 중 `Error`는 상태를 복구한 뒤 그대로 던지고 일반 실패는 재시도할 수 있습니다. 종료 뒤에는 다시 초기화할 수 없습니다.

## 운영 {#operations}

libvips 설치와 JVM/네이티브 아키텍처를 함께 확인하세요. JNI 테스트는 클래스마다 JVM을 새로 띄우고 병렬 fork를 1로 제한합니다. `0.4.0` macOS arm64 벤치마크 호스트에서는 JVips dylib가 x86_64여서 JNI 결과를 측정하지 않았습니다.

## 테스트 {#testing}

`./gradlew :bluetape4k-images-vips-java21:test`를 실행합니다. libvips 초기화 실패 시 자동 skip하며 명시적으로 제외할 때만 `-Dvips.enabled=false`를 사용합니다. runtime 동시성, 이미지 연산, writer, 속성과 골든 출력을 검증합니다.

## 학습 경로와 예제 {#workshops}

배포 아키텍처에서 런타임/이미지 테스트와 작은 인코딩 확인 테스트를 먼저 실행하세요. 그 다음 `-Pvips.impl=java21` 벤치마크를 다른 네이티브 실행과 겹치지 않게 돌립니다.

## 제약 사항 {#limitations}

`0.4.0`의 Java 21 백엔드는 HEIC 인코딩을 지원하지 않습니다. AVIF/HEIF 디코딩과 AVIF 인코딩도 호스트 코덱에 따라 달라집니다. 경로 로딩은 50MiB 제한 안에서 전체 압축 파일을 메모리에 올립니다.

<!-- release-readme-diagrams:start -->
## 배포본 다이어그램 {#release-diagrams}

아래 그림은 `0.4.0` 배포본의 README 자산을 해당 배포 커밋에서 직접 불러옵니다. 이후 SNAPSHOT이 아니라 이 매뉴얼 버전의 구조와 실행 흐름을 보여 줍니다. 미리보기를 누르면 같은 배포 커밋의 SVG 원본이 열립니다.

### JVips 다이어그램

[![JVips 다이어그램](https://raw.githubusercontent.com/bluetape4k/bluetape4k-image/ea5175b083babf8880f53cf80c9a264a0c61777e/docs/images/readme-diagrams/images-vips-java21-architecture-01.png)](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/docs/images/readme-diagrams/images-vips-java21-architecture-01.svg)

_배포본 README: [`images-vips-java21/README.ko.md`](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/images-vips-java21/README.ko.md)_

### images vips java21 클래스 구조도 2

[![images vips java21 클래스 구조도 2](https://raw.githubusercontent.com/bluetape4k/bluetape4k-image/ea5175b083babf8880f53cf80c9a264a0c61777e/docs/images/readme-diagrams/images-vips-java21-class-02.png)](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/docs/images/readme-diagrams/images-vips-java21-class-02.svg)

_배포본 README: [`images-vips-java21/README.ko.md`](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/images-vips-java21/README.ko.md)_

<!-- release-readme-diagrams:end -->

## 근거 자료 {#sources}

- [팩토리와 입력 검증](https://github.com/bluetape4k/bluetape4k-image/blob/0.4.0/images-vips-java21/src/main/kotlin/io/bluetape4k/images/vips/java21/JVipsImageSupport.kt)
- [JNI 이미지 수명](https://github.com/bluetape4k/bluetape4k-image/blob/0.4.0/images-vips-java21/src/main/kotlin/io/bluetape4k/images/vips/java21/JVipsImage.kt)
- [런타임 수명](https://github.com/bluetape4k/bluetape4k-image/blob/0.4.0/images-vips-java21/src/main/kotlin/io/bluetape4k/images/vips/java21/JVipsRuntime.kt)
- [테스트 격리 설정](https://github.com/bluetape4k/bluetape4k-image/blob/0.4.0/images-vips-java21/build.gradle.kts)
