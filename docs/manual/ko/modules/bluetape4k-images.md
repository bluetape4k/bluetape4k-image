---
manualId: "bluetape4k-images"
id: "bluetape4k-images"
title: "불변 이미지 처리"
locale: "ko"
kind: "library"
gradlePath: ":bluetape4k-images"
sourceDir: "images"
releaseRef: "0.3.0"
artifact: io.github.bluetape4k.image:bluetape4k-images
---

# 불변 이미지 처리

> 라이브러리 모듈

## 제공하는 기능 {#problem}

순수 JVM 이미지 처리의 중심 모듈입니다. Scrimage와 Java2D를 바탕으로 이미지 로딩, 불변 그리기, 필터·변환 DSL, 코루틴 I/O, 배치 처리, 썸네일, 타일, 분석과 유사도 계산을 제공합니다.

네이티브 환경 없이 다양한 이미지 작업을 수행하거나 Java2D/Scrimage의 풍부한 기능이 필요할 때 가장 먼저 선택할 라이브러리입니다.

## 사용하기 좋은 경우 {#when-to-use}

일반 웹 이미지 로딩과 변환, 워터마크, 썸네일, 제한된 배치 작업, 유사도 분석, SVG 래스터화에 적합합니다. 대량 리사이즈 처리량이나 네이티브 코덱이 핵심이라면 libvips 백엔드를 함께 비교하세요.

## 의존성 좌표 {#coordinates}

Maven 좌표: `io.github.bluetape4k.image:bluetape4k-images`

```kotlin
dependencies {
    implementation(platform("io.github.bluetape4k:bluetape4k-dependencies:<version>"))
    implementation("io.github.bluetape4k.image:bluetape4k-images")
}
```

## 핵심 개념 {#concepts}

- `ImmutableImage` 변환은 새 값을 반환합니다. `withGraphics`는 복사본에 그리고 `Graphics2D`를 항상 해제합니다.
- `BufferedImage.useGraphics`는 그래픽 객체를 해제하지만 수신 이미지는 직접 바꿉니다.
- Scrimage 코덱은 블로킹입니다. suspend 함수는 `Dispatchers.IO`로 옮겨 실행할 뿐 코덱 자체를 논블로킹으로 만들지는 않습니다.
- `BufferedSource`와 `InputStream`은 호출자가 닫고, `Source`와 `SuspendedSource` 오버로드는 함수가 버퍼링한 뒤 닫습니다.
- 배치 처리는 이미지별 픽셀, 처리 중인 전체 픽셀, 병렬도 제한을 함께 적용합니다.

## 빠르게 시작하기 {#quick-start}

```kotlin
val image = immutableImageOf(Path.of("photo.jpg"))
val output = image.applyFilters {
    brightness(1.08f)
    gaussianBlur(radius = 2)
    watermark("© bluetape4k")
}

output.suspendWrite(
    SuspendJpegWriter.Default.withCompression(85),
    Path.of("photo-ready.jpg"),
)
```

## 작업별 API {#api-by-task}

- 로딩: `immutableImageOf`, `suspendImmutableImageOf`가 바이트, 스트림, Okio, 파일과 경로를 받습니다.
- 필터: `applyFilters`로 블러, 색상, 효과, 스타일, 테두리, 캡션과 워터마크를 조합합니다.
- 변환: `autoCrop`, `smartCropTo`, `clahe`, 원근 변환, 회전과 뒤집기를 제공합니다.
- 비교: 지각 해시, 히스토그램, MSE, PSNR, SSIM/MSSIM 등을 선택할 수 있습니다.
- 배치: `processImages`와 `ImageProcessingDsl`, `writeImagesTo`를 연결합니다.
- 파생 이미지: `ThumbnailPipeline`과 `TileProcessor`로 여러 출력물을 만듭니다.

## 권장 패턴 {#patterns}

애플리케이션 경계에서는 `ImmutableImage`를 우선 사용하세요. 배치 작업은 전체 디코딩 전에 크기를 탐색하며, 기본값인 `skipFailures=false`로 먼저 검증하는 편이 좋습니다. 실패를 건너뛸 때는 `onFailure`로 반드시 관측하세요.

suspend Okio 오버로드는 기존 코루틴 I/O 경계와 수명 관리를 자연스럽게 연결하기 위한 API입니다. `0.3.0` 벤치마크에서는 로컬 `Path`보다 빠르지 않았으므로 성능 기능처럼 소개하면 안 됩니다.

## 연동 {#integrations}

CAPTCHA, OCR, Ktor 모듈이 이 라이브러리를 사용합니다. Spring 저장소 모듈은 처리 DSL과 별개이며, `vips-api`도 Scrimage 구현체가 아니라 독립된 네이티브 API입니다.

## 설정 {#configuration}

`ImageProcessingOptions` 기본값은 CPU 수에 맞춘 병렬도, 이미지당 16,777,216픽셀, 처리 중인 전체 33,554,432픽셀입니다. 타일 기본 최대 개수는 65,536개입니다.

신뢰할 수 없는 SVG에는 `BatikSvgRasterizer` 기본 보안을 유지하세요. 외부 엔티티와 DTD를 차단하고 외부 리소스를 기본으로 금지하며 크기와 시간 제한을 적용합니다.

## 실패 유형과 해결 방법 {#failures}

배치 실패는 `VALIDATION`, `LOAD`, `TRANSFORM`, `WRITE` 단계로 구분됩니다. `skipFailures=true`면 `ImageBatchResult.Failure`로 흘려보내고, 아니면 `ImageBatchException`을 던집니다. 코루틴 취소는 실패 항목으로 바꾸지 않고 그대로 전파합니다.

## 운영 {#operations}

압축 파일 크기뿐 아니라 실제 픽셀 수와 단계별 실패율을 관찰하세요. 골든 이미지 갱신은 명시적으로 켜는 기능이므로 일반 CI에서 `bluetape4k.images.golden.update`를 활성화하지 않습니다.

## 테스트 {#testing}

`./gradlew :bluetape4k-images:test`로 순수 JVM 영역을 검증합니다. 팩토리와 수명, 필터, 속성 테스트, 골든 결과, 배치 제한, 썸네일·타일, 유사도, SVG 보안, suspend writer를 폭넓게 다룹니다.

## 학습 경로와 예제 {#workshops}

`examples/basic-processing`으로 시작한 뒤 `ImageProcessingDsl`, `ThumbnailPipeline`, `TileProcessor` 순서로 살펴보세요. 처리량이 중요하면 저장소 벤치마크의 입력 이미지와 실행 환경까지 확인한 뒤 libvips를 비교합니다.

## 제약 사항 {#limitations}

suspend 함수도 내부 블로킹 코덱을 사용합니다. 이 모듈의 AVIF/HEIC 타입은 `0.3.0`에서 구현체가 없는 실험적 계약입니다. ImageIO 코덱 지원 범위는 런타임 클래스패스에 따라 달라질 수 있습니다.

<!-- release-readme-diagrams:start -->
## 배포본 다이어그램 {#release-diagrams}

아래 그림은 `0.3.0` 배포본의 README 자산을 해당 배포 커밋에서 직접 불러옵니다. 이후 SNAPSHOT이 아니라 이 매뉴얼 버전의 구조와 실행 흐름을 보여 줍니다. 미리보기를 누르면 같은 배포 커밋의 SVG 원본이 열립니다.

### images 아키텍처

[![images 아키텍처](https://raw.githubusercontent.com/bluetape4k/bluetape4k-image/a571c30004f571fe8cfcddc29670c1404d212ec6/docs/images/readme-diagrams/images-architecture-01.png)](https://github.com/bluetape4k/bluetape4k-image/blob/a571c30004f571fe8cfcddc29670c1404d212ec6/docs/images/readme-diagrams/images-architecture-01.svg)

_배포본 README: [`images/README.ko.md`](https://github.com/bluetape4k/bluetape4k-image/blob/a571c30004f571fe8cfcddc29670c1404d212ec6/images/README.ko.md)_

### images Transform 아키텍처

[![images Transform 아키텍처](https://raw.githubusercontent.com/bluetape4k/bluetape4k-image/a571c30004f571fe8cfcddc29670c1404d212ec6/docs/images/readme-diagrams/images-architecture-03.png)](https://github.com/bluetape4k/bluetape4k-image/blob/a571c30004f571fe8cfcddc29670c1404d212ec6/docs/images/readme-diagrams/images-architecture-03.svg)

_배포본 README: [`images/README.ko.md`](https://github.com/bluetape4k/bluetape4k-image/blob/a571c30004f571fe8cfcddc29670c1404d212ec6/images/README.ko.md)_

### Bluetape4k Image Analysis 다이어그램

[![Bluetape4k Image Analysis 다이어그램](https://raw.githubusercontent.com/bluetape4k/bluetape4k-image/a571c30004f571fe8cfcddc29670c1404d212ec6/docs/images/readme-diagrams/images-class-04.png)](https://github.com/bluetape4k/bluetape4k-image/blob/a571c30004f571fe8cfcddc29670c1404d212ec6/docs/images/readme-diagrams/images-class-04.svg)

_배포본 README: [`images/README.md`](https://github.com/bluetape4k/bluetape4k-image/blob/a571c30004f571fe8cfcddc29670c1404d212ec6/images/README.md)_

### Images Core API 클래스 구성도

[![Images Core API 클래스 구성도](https://raw.githubusercontent.com/bluetape4k/bluetape4k-image/a571c30004f571fe8cfcddc29670c1404d212ec6/docs/images/readme-diagrams/images-class-core-01.png)](https://github.com/bluetape4k/bluetape4k-image/blob/a571c30004f571fe8cfcddc29670c1404d212ec6/docs/images/readme-diagrams/images-class-core-01.svg)

_배포본 README: [`images/README.ko.md`](https://github.com/bluetape4k/bluetape4k-image/blob/a571c30004f571fe8cfcddc29670c1404d212ec6/images/README.ko.md)_

### Images Filter 클래스 구성도

[![Images Filter 클래스 구성도](https://raw.githubusercontent.com/bluetape4k/bluetape4k-image/a571c30004f571fe8cfcddc29670c1404d212ec6/docs/images/readme-diagrams/images-class-filters-01.png)](https://github.com/bluetape4k/bluetape4k-image/blob/a571c30004f571fe8cfcddc29670c1404d212ec6/docs/images/readme-diagrams/images-class-filters-01.svg)

_배포본 README: [`images/README.ko.md`](https://github.com/bluetape4k/bluetape4k-image/blob/a571c30004f571fe8cfcddc29670c1404d212ec6/images/README.ko.md)_

### Images Writer 클래스 구성도

[![Images Writer 클래스 구성도](https://raw.githubusercontent.com/bluetape4k/bluetape4k-image/a571c30004f571fe8cfcddc29670c1404d212ec6/docs/images/readme-diagrams/images-class-writers-01.png)](https://github.com/bluetape4k/bluetape4k-image/blob/a571c30004f571fe8cfcddc29670c1404d212ec6/docs/images/readme-diagrams/images-class-writers-01.svg)

_배포본 README: [`images/README.ko.md`](https://github.com/bluetape4k/bluetape4k-image/blob/a571c30004f571fe8cfcddc29670c1404d212ec6/images/README.ko.md)_

<!-- release-readme-diagrams:end -->

## 근거 자료 {#sources}

- [이미지 팩토리와 자원 소유권](https://github.com/bluetape4k/bluetape4k-image/blob/0.3.0/images/src/main/kotlin/io/bluetape4k/images/ImmutableImageSupport.kt)
- [배치 결과와 옵션](https://github.com/bluetape4k/bluetape4k-image/blob/0.3.0/images/src/main/kotlin/io/bluetape4k/images/batch/ImageBatchModels.kt)
- [배치 Flow 구현](https://github.com/bluetape4k/bluetape4k-image/blob/0.3.0/images/src/main/kotlin/io/bluetape4k/images/batch/ImageBatchFlow.kt)
- [처리 DSL](https://github.com/bluetape4k/bluetape4k-image/blob/0.3.0/images/src/main/kotlin/io/bluetape4k/images/batch/ImageProcessingDsl.kt)
