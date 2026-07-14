---
manualId: "bluetape4k-images-vips-java25"
id: "bluetape4k-images-vips-java25"
title: "Java 25 FFM 백엔드"
locale: "ko"
kind: "library"
gradlePath: ":bluetape4k-images-vips-java25"
sourceDir: "images-vips-java25"
releaseRef: "0.3.0"
artifact: io.github.bluetape4k.image:bluetape4k-images-vips-java25
---

# Java 25 FFM 백엔드

> 라이브러리 모듈

## 제공하는 기능 {#problem}

`vips-ffm`과 Java Foreign Function & Memory API로 공통 libvips API를 구현합니다. Java 25 전용 백엔드이며 호스트 libvips가 지원하면 HEIF 계열 인코딩도 사용할 수 있습니다.

## 사용하기 좋은 경우 {#when-to-use}

Java 25 서비스에서 libvips와 네이티브 접근을 설정할 수 있을 때 선택하세요. 특히 로컬 대용량 파일은 `Path` 팩토리가 전체 압축 파일을 JVM 바이트 배열로 만들지 않고 libvips에 직접 경로를 넘깁니다.

## 의존성 좌표 {#coordinates}

Maven 좌표: `io.github.bluetape4k.image:bluetape4k-images-vips-java25`

중앙 BOM, `vips-api`, 이 백엔드를 함께 사용합니다.

## 핵심 개념 {#concepts}

`FfmVipsRuntime`은 프로세스 전체에서 하나이며 종료는 되돌릴 수 없습니다. 루트 `FfmVipsImage`는 `Arena.ofShared`를 소유하지만 리사이즈·썸네일·크롭 결과는 같은 arena를 공유하고 소유하지 않습니다. 따라서 자식 이미지는 부모 arena보다 오래 살 수 없습니다.

## 빠르게 시작하기 {#quick-start}

```kotlin
FfmVipsRuntime.init(concurrency = 4)

ffmVipsImageOf(Path.of("input.jpg")).use { source ->
    val thumbnail = source.thumbnail(800)
    try {
        thumbnail.writeTo(Path.of("output.avif"), VipsImageFormat.AVIF)
    } finally {
        thumbnail.close()
    }
}
```

JVM은 `--enable-native-access=ALL-UNNAMED` 옵션으로 시작합니다.

## 작업별 API {#api-by-task}

- `ffmVipsImageOf`, `suspendFfmVipsImageOf`로 입력을 읽습니다.
- 로컬 파일은 `Path` 오버로드를 우선하고 다른 스트림은 제한 안에서 버퍼링합니다.
- 리사이즈, 썸네일, 크롭과 JPEG/PNG/WebP/AVIF/HEIC 인코딩을 수행합니다.
- 모든 파생 이미지를 루트 이미지 수명 안에서 사용합니다.

## 권장 패턴 {#patterns}

독립된 파이프라인마다 루트 이미지를 하나 만들고 모든 파생 작업이 끝난 뒤 닫으세요. 루트의 `use` 블록 안에서 만든 자식 이미지를 바깥으로 반환하면 안 됩니다. 파일 경로는 저장소 경계에서 검증하고 네이티브 작업 동시성을 제한합니다.

## 연동 {#integrations}

`bluetape4k-images-vips-api`를 `vips-ffm`으로 구현합니다. Java 25와 시스템 libvips가 필요하고 AVIF/HEIC는 libheif 및 해당 encoder를 포함한 빌드가 필요합니다.

## 설정 {#configuration}

Java 25와 `--enable-native-access=ALL-UNNAMED`를 사용합니다. Homebrew macOS에서 필요하면 `DYLD_LIBRARY_PATH=/opt/homebrew/lib`를 설정하세요. 입력 50MiB, 픽셀 150,000,000 기본 제한을 적용하며 `Path`는 네이티브 로딩 전에 파일 크기를 확인합니다.

## 실패 유형과 해결 방법 {#failures}

디코딩, 연산, 인코딩, 초기화 실패는 공통 예외 계층으로 구분합니다. 네이티브 접근 권한이 없으면 초기화할 때 경고하고 실제 FFM 호출이 실패할 수 있습니다. Arena 정리 실패는 억제된 예외로 보존합니다. 일반 초기화 실패는 재시도할 수 있지만 종료 뒤에는 불가능합니다.

## 운영 {#operations}

Java 힙뿐 아니라 네이티브 메모리를 함께 관찰하세요. `0.3.0` 벤치마크에서 기하 연산의 Java 할당량은 작았지만 libvips 네이티브 메모리는 측정하지 않았습니다. Java 21과 Java 25 측정은 CPU와 네이티브 런타임 간섭을 피하도록 순차 실행합니다.

## 테스트 {#testing}

Java 25에서 `./gradlew :bluetape4k-images-vips-java25:test`를 실행합니다. Gradle은 native access, 네이티브 테스트 격리, Homebrew 라이브러리 경로를 설정합니다. runtime 동시성, arena 기반 연산, writer, 속성과 골든 이미지를 검증합니다.

## 학습 경로와 예제 {#workshops}

먼저 네이티브 시작과 부모·자식 수명을 테스트하고, 운영 이미지에서 필요한 코덱을 하나씩 스모크 테스트하세요. 마지막으로 실제 입력을 사용해 `-Pvips.impl=java25` 집중 벤치마크를 실행합니다.

## 제약 사항 {#limitations}

파생 이미지는 루트 arena를 공유하므로 루트보다 오래 살 수 없습니다. `Path`가 아닌 입력은 50MiB 제한 안에서 버퍼링합니다. AVIF/HEIC enum이 있다는 사실만으로 호스트 코덱 지원을 보장하지 않으며 Java 21 JVM에서는 실행할 수 없습니다.

## 근거 자료 {#sources}

- [팩토리와 직접 경로 로딩](https://github.com/bluetape4k/bluetape4k-image/blob/0.3.0/images-vips-java25/src/main/kotlin/io/bluetape4k/images/vips/java25/FfmVipsImageSupport.kt)
- [Arena 소유권](https://github.com/bluetape4k/bluetape4k-image/blob/0.3.0/images-vips-java25/src/main/kotlin/io/bluetape4k/images/vips/java25/FfmVipsImage.kt)
- [런타임과 native access 검사](https://github.com/bluetape4k/bluetape4k-image/blob/0.3.0/images-vips-java25/src/main/kotlin/io/bluetape4k/images/vips/java25/FfmVipsRuntime.kt)
- [Java 25 빌드 설정](https://github.com/bluetape4k/bluetape4k-image/blob/0.3.0/images-vips-java25/build.gradle.kts)
