---
manualId: "bluetape4k-images-ocr"
id: "bluetape4k-images-ocr"
title: "Tesseract OCR 연동"
locale: "ko"
kind: "library"
gradlePath: ":bluetape4k-images-ocr"
sourceDir: "images-ocr"
releaseRef: "0.4.0"
artifact: io.github.bluetape4k.image:bluetape4k-images-ocr
---

# Tesseract OCR 연동

> 라이브러리 모듈

## 제공하는 기능 {#problem}

Scrimage `ImmutableImage`에서 Tess4J와 호스트 Tesseract를 이용해 글자를 추출합니다. Kotlin에 맞는 옵션과 예외 타입을 제공하면서도 OCR이 네이티브 실행 환경에 의존한다는 사실을 숨기지 않습니다.

## 사용하기 좋은 경우 {#when-to-use}

이미 `bluetape4k-images`로 디코딩한 이미지에서 영문 또는 다국어 텍스트를 읽어야 할 때 사용하세요. 바코드를 읽거나 운영형 클라우드 OCR 서비스를 쓰려는 경우에는 맞지 않습니다.

## 의존성 좌표 {#coordinates}

Maven 좌표: `io.github.bluetape4k.image:bluetape4k-images-ocr`

```kotlin
dependencies {
    implementation(platform("io.github.bluetape4k:bluetape4k-dependencies:<version>"))
    implementation("io.github.bluetape4k.image:bluetape4k-images-ocr")
}
```

## 핵심 개념 {#concepts}

`OcrEngine`이 구현체 경계입니다. `TesseractOcrEngine`은 인식 요청마다 Tess4J 클라이언트를 새로 만들어 변경 가능한 네이티브 상태를 공유하지 않습니다. `languages`는 `eng+kor` 같은 Tesseract 표현식으로 바뀌며 `OcrResult`에는 실제 적용한 옵션이 함께 담깁니다.

## 빠르게 시작하기 {#quick-start}

```kotlin
val image = immutableImageOf(Path.of("receipt.png"))
val text = image.suspendExtractText(
    OcrOptions(
        languages = listOf("eng", "kor"),
        pageSegmentationMode = TesseractPageSegmentationMode.AUTO,
    ),
)
```

## 작업별 API {#api-by-task}

- 동기 호출은 `extractText`, 코루틴 경계에서는 `suspendExtractText`를 사용합니다.
- 언어 팩은 `languages`, 필요하면 `tessdataPath`로 지정합니다.
- 엔진 모드, 페이지 분할 모드, config와 variable을 조절할 수 있습니다.
- 다른 OCR 공급자를 쓰려면 `OcrEngine`을 구현해 extension에 전달합니다.

## 권장 패턴 {#patterns}

운영에서 쓸 언어 팩을 배포 이미지에 명시적으로 설치하고 시작 단계에서 확인하세요. 문서 형태를 알고 있다면 페이지 분할 모드를 구체적으로 선택하는 편이 좋습니다. OCR 작업은 별도의 크기가 제한된 작업자 풀에서 실행하세요.

## 연동 {#integrations}

입력은 `bluetape4k-images`의 `ImmutableImage`입니다. Ktor와 Spring 예제로 서비스 API를 만들 수 있지만 이 모듈 자체는 라우트나 Spring Bean을 등록하지 않습니다.

## 설정 {#configuration}

호스트에 Tesseract와 요청 언어의 traineddata를 설치해야 합니다. 기본 경로가 맞지 않으면 `TESSDATA_PREFIX` 또는 `OcrOptions.tessdataPath`를 지정합니다. 기본 언어는 `eng`, 기본 엔진 모드와 자동 페이지 분할, 결과 문자열 trim입니다.

## 실패 유형과 해결 방법 {#failures}

네이티브 라이브러리, Tess4J 클래스, 언어 데이터 설정 문제는 `OcrConfigurationException`으로 구분합니다. 실제 인식 실패는 `OcrException`입니다. 사용자 응답에는 내부 원인을 노출하지 말고 서버 로그의 cause로 보존하세요.

## 운영 {#operations}

인식 지연, 빈 결과 비율, 사용 언어, 대기열 포화를 관찰하되 인식한 원문은 기본적으로 기록하지 않습니다. 운영과 같은 불변 컨테이너 이미지에 Tesseract와 traineddata를 포함하세요.

## 테스트 {#testing}

일반 단위 테스트는 가짜 Tess4J 클라이언트를 사용합니다. 호스트 네이티브 테스트는 `-Docr.enabled=true`, 컨테이너 테스트는 Docker와 `-Docr.container.enabled=true`가 필요합니다. `0.4.0` 기준선은 `eng`, `kor`, `jpn`을 확인하며 두 네이티브 경로는 순차 실행합니다.

## 학습 경로와 예제 {#workshops}

`OcrQuickstartExampleTest`로 시작한 뒤 실제 문서로 페이지 분할을 조정하고, 마지막에 배포 파이프라인의 네이티브·컨테이너 검증을 추가하세요.

## 제약 사항 {#limitations}

Tesseract와 언어 데이터는 라이브러리에 포함되지 않습니다. dispatch 전에 취소되면 OCR을 시작하지 않지만 이미 실행 중인 네이티브 호출은 블로킹입니다. 인식 품질은 입력 이미지와 언어 팩에 크게 좌우됩니다.

<!-- release-readme-diagrams:start -->
## 배포본 다이어그램 {#release-diagrams}

아래 그림은 `0.4.0` 배포본의 README 자산을 해당 배포 커밋에서 직접 불러옵니다. 이후 SNAPSHOT이 아니라 이 매뉴얼 버전의 구조와 실행 흐름을 보여 줍니다. 미리보기를 누르면 같은 배포 커밋의 SVG 원본이 열립니다.

### images-ocr 아키텍처

[![images-ocr 아키텍처](https://raw.githubusercontent.com/bluetape4k/bluetape4k-image/ea5175b083babf8880f53cf80c9a264a0c61777e/docs/images/readme-diagrams/images-ocr-architecture-01.png)](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/docs/images/readme-diagrams/images-ocr-architecture-01.svg)

_배포본 README: [`images-ocr/README.ko.md`](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/images-ocr/README.ko.md)_

### images-ocr 클래스 다이어그램

[![images-ocr 클래스 다이어그램](https://raw.githubusercontent.com/bluetape4k/bluetape4k-image/ea5175b083babf8880f53cf80c9a264a0c61777e/docs/images/readme-diagrams/images-ocr-class-diagram-01.png)](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/docs/images/readme-diagrams/images-ocr-class-diagram-01.svg)

_배포본 README: [`images-ocr/README.ko.md`](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/images-ocr/README.ko.md)_

### images-ocr Recognition 처리 순서

[![images-ocr Recognition 처리 순서](https://raw.githubusercontent.com/bluetape4k/bluetape4k-image/ea5175b083babf8880f53cf80c9a264a0c61777e/docs/images/readme-diagrams/images-ocr-sequence-diagram-01.png)](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/docs/images/readme-diagrams/images-ocr-sequence-diagram-01.svg)

_배포본 README: [`images-ocr/README.ko.md`](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/images-ocr/README.ko.md)_

<!-- release-readme-diagrams:end -->

## 근거 자료 {#sources}

- [OCR 계약과 예외](https://github.com/bluetape4k/bluetape4k-image/blob/0.4.0/images-ocr/src/main/kotlin/io/bluetape4k/images/ocr/OcrEngine.kt)
- [OCR 옵션](https://github.com/bluetape4k/bluetape4k-image/blob/0.4.0/images-ocr/src/main/kotlin/io/bluetape4k/images/ocr/OcrOptions.kt)
- [Tesseract 구현](https://github.com/bluetape4k/bluetape4k-image/blob/0.4.0/images-ocr/src/main/kotlin/io/bluetape4k/images/ocr/TesseractOcrEngine.kt)
- [테스트 게이트](https://github.com/bluetape4k/bluetape4k-image/blob/0.4.0/images-ocr/build.gradle.kts)
