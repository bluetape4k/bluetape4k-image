---
manualId: "basic-processing"
id: "basic-processing"
title: "기본 이미지 처리 워크숍"
locale: "ko"
kind: "example"
gradlePath: ":basic-processing"
sourceDir: "examples/basic-processing"
releaseRef: "0.4.0"
artifact: null
---

# 기본 이미지 처리 워크숍

> 실행 가능한 예제

## 제공하는 기능 {#problem}

웹 프레임워크나 네이티브 런타임 없이 번들 이미지 세 개를 결과물 다섯 개로 바꾸는 예제입니다. 파일에서 이미지 읽기, 크기를 제한한 리사이즈, 중요 영역을 고려한 `smartCropTo`, 포맷 변환, 텍스트 워터마크, `suspendWrite`를 한 흐름에서 익힐 수 있습니다.

## 사용하기 좋은 경우 {#when-to-use}

`bluetape4k-images`를 처음 배우거나 개발 환경을 점검할 때, 또는 복잡한 처리 문제를 순수 JVM 재현 코드로 줄일 때 먼저 사용하세요. 같은 작업을 libvips와 비교하기 전 기준점으로도 좋습니다.

## 의존성 좌표 {#coordinates}

이 예제는 배포되지 않습니다. 애플리케이션에서는 `bluetape4k-dependencies` 버전 하나만 선택하고, 이미지 모듈 버전을 따로 관리하지 않은 채 `bluetape4k-images`를 추가하세요.

## 핵심 개념 {#concepts}

- `suspendLoadImage(Path)`로 애플리케이션이 압축 파일 전체를 `ByteArray`에 복사하지 않고 읽습니다.
- `fit`은 비율을 유지하고, `smartCropTo`는 관심 영역을 고려해 정확한 크기를 채웁니다.
- `withGraphics`로 불변 이미지 처리 흐름 안에서 워터마크를 그립니다.
- 출력 확장자에 맞춰 JPEG 또는 PNG 작성기를 골라 `suspendWrite`로 저장합니다.

## 빠르게 시작하기 {#quick-start}

JDK 21 이상이면 됩니다. 외부 서비스나 네이티브 패키지는 필요하지 않습니다.

```bash
./gradlew :basic-processing:run
./gradlew :basic-processing:run --args="/tmp/bluetape4k-basic-processing"
```

기본 출력 경로는 `build/tmp/basic-processing`입니다. `320x240` 썸네일, `640x360` smart crop, `800x600` PNG와 `960x540` 결과 두 개가 만들어집니다.

## 작업별 API {#api-by-task}

| 작업 | 0.4.0 구현 |
| --- | --- |
| 테스트 이미지 읽기 | `suspendLoadImage(resourcePath(...))` |
| 비율 유지 | `image.fit(width, height)` |
| 정확한 프레임 만들기 | `image.smartCropTo(640, 360)` |
| 워터마크 그리기 | `image.withGraphics { graphics -> ... }` |
| 파일로 인코딩 | `image.suspendWrite(writer, output)` |

## 권장 패턴 {#patterns}

경로 결정, 변환, 인코딩을 분리하세요. 작성기와 최대 크기는 명시하고, 결과 경로·크기·바이트 수를 반환하면 테스트에서 실제 산출물을 검증하기 쉽습니다.

## 연동 {#integrations}

이 예제는 `bluetape4k-images`와 Kotlin Coroutines만 사용합니다. HTTP가 필요하면 Spring Boot나 Ktor 예제로, 측정 결과상 네이티브 가속이 필요하면 vips 모듈로 이어가세요.

## 설정 {#configuration}

인수가 없으면 `build/tmp/basic-processing`에 저장하고, 첫 번째 인수로 출력 디렉터리를 바꿉니다. 빌드가 `images/src/test/resources`와 `docs/images`의 테스트 이미지를 예제 리소스에 포함합니다.

## 실패 유형과 해결 방법 {#failures}

- `Example resource is missing`: 저장소의 Gradle 태스크로 실행해 리소스 소스 세트를 구성하세요.
- 리소스가 `file:` URL이 아님: 이 예제는 `Path` 학습용입니다. JAR 내부 리소스는 스트림/바이트 API를 사용하세요.
- 크기가 예상과 다름: 비율 유지 `fit`과 정확한 크기의 `smartCropTo`를 구분하세요.
- 출력이 비었거나 열리지 않음: 작성기 선택과 디렉터리 권한을 확인한 뒤 테스트를 실행하세요.

## 운영 {#operations}

실행 결과에는 파일명, 최종 크기, 바이트 수, 정규화된 출력 경로가 표시됩니다. 압축된 바이트 수는 환경에 따라 달라질 수 있으므로 파일 존재 여부와 디코딩한 크기를 최소 검증 기준으로 삼으세요.

## 테스트 {#testing}

```bash
./gradlew :basic-processing:test
```

실행 코드와 같은 generator를 호출해 파일 다섯 개가 비어 있지 않은지, 다시 디코딩되는지, 크기가 정확한지 확인합니다.

## 학습 경로와 예제 {#workshops}

1. 이 예제를 실행해 다섯 결과물을 직접 비교합니다.
2. [`bluetape4k-images`](./bluetape4k-images.md)에서 이미지 읽기, 변환, 작성 API를 익힙니다.
3. [`spring-boot-image-api`](./spring-boot-image-api.md) 또는 [`ktor-image-api`](./ktor-image-api.md)로 HTTP 연동을 배웁니다.
4. 운영 작업을 libvips로 옮기기 전 [이미지 벤치마크](./bluetape4k-images-benchmark.md)를 확인합니다.

## 제약 사항 {#limitations}

단일 프로세스에서 파일을 생성하는 예제입니다. 업로드 검증, 저장 정책, 네이티브 코덱, 역압, 인증, 공개 URL은 다루지 않습니다.

<!-- release-readme-diagrams:start -->
## 배포본 다이어그램 {#release-diagrams}

아래 그림은 `0.4.0` 배포본의 README 자산을 해당 배포 커밋에서 직접 불러옵니다. 이후 SNAPSHOT이 아니라 이 매뉴얼 버전의 구조와 실행 흐름을 보여 줍니다. 미리보기를 누르면 같은 배포 커밋의 SVG 원본이 열립니다.

### 기본 처리 아키텍처

[![기본 처리 아키텍처](https://raw.githubusercontent.com/bluetape4k/bluetape4k-image/ea5175b083babf8880f53cf80c9a264a0c61777e/docs/images/readme-diagrams/examples-basic-processing-architecture-01.png)](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/docs/images/readme-diagrams/examples-basic-processing-architecture-01.svg)

_배포본 README: [`examples/basic-processing/README.ko.md`](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/examples/basic-processing/README.ko.md)_

### 기본 처리 실행 시나리오

[![기본 처리 실행 시나리오](https://raw.githubusercontent.com/bluetape4k/bluetape4k-image/ea5175b083babf8880f53cf80c9a264a0c61777e/docs/images/readme-diagrams/examples-basic-processing-scenario-01.png)](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/docs/images/readme-diagrams/examples-basic-processing-scenario-01.svg)

_배포본 README: [`examples/basic-processing/README.ko.md`](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/examples/basic-processing/README.ko.md)_

### 기본 처리 순서

[![기본 처리 순서](https://raw.githubusercontent.com/bluetape4k/bluetape4k-image/ea5175b083babf8880f53cf80c9a264a0c61777e/docs/images/readme-diagrams/examples-basic-processing-sequence-01.png)](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/docs/images/readme-diagrams/examples-basic-processing-sequence-01.svg)

_배포본 README: [`examples/basic-processing/README.ko.md`](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/examples/basic-processing/README.ko.md)_

<!-- release-readme-diagrams:end -->

## 근거 자료 {#sources}

- [0.4.0 README](https://github.com/bluetape4k/bluetape4k-image/blob/0.4.0/examples/basic-processing/README.ko.md)
- [Quickstart 소스](https://github.com/bluetape4k/bluetape4k-image/blob/0.4.0/examples/basic-processing/src/main/kotlin/io/bluetape4k/images/examples/basic/BasicImageProcessingQuickstart.kt)
- [결과 검증 테스트](https://github.com/bluetape4k/bluetape4k-image/blob/0.4.0/examples/basic-processing/src/test/kotlin/io/bluetape4k/images/examples/basic/BasicImageProcessingQuickstartTest.kt)
- [Gradle 빌드 파일](https://github.com/bluetape4k/bluetape4k-image/blob/0.4.0/examples/basic-processing/build.gradle.kts)
