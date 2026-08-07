---
manualId: "spring-boot-ocr-api"
id: "spring-boot-ocr-api"
title: "Spring Boot OCR API 워크숍"
locale: "ko"
kind: "example"
gradlePath: ":spring-boot-ocr-api"
sourceDir: "examples/spring-boot-ocr-api"
releaseRef: "0.4.0"
artifact: null
---

# Spring Boot OCR API 워크숍

> 실행 가능한 예제

## 제공하는 기능 {#problem}

`bluetape4k-images-ocr`를 멀티파트 엔드포인트로 공개하는 Spring Boot 4 예제입니다. 언어 코드를 해석하고 실행 환경의 tessdata 설정을 주입 가능한 `OcrEngine`에 전달하며, 잘못된 요청과 네이티브 OCR 실행 실패를 서로 다른 응답으로 처리합니다.

## 사용하기 좋은 경우 {#when-to-use}

Spring MVC 애플리케이션에 OCR을 넣거나 Tess4J/Tesseract 경계를 테스트 가능한 구조로 만들 때 사용하세요. 문서 처리 플랫폼 전체가 아니라 OCR 엔드포인트에 집중한 예제입니다.

## 의존성 좌표 {#coordinates}

예제 자체는 배포되지 않습니다. `bluetape4k-dependencies` 버전 하나를 선택하고 `bluetape4k-images-ocr`를 사용하세요. OCR 모듈 버전을 따로 고정하지 않습니다.

## 핵심 개념 {#concepts}

- `OcrEngine`을 주입하며 `TesseractOcrEngine`은 운영 기본 구현일 뿐입니다.
- `eng+kor`, `eng,kor`를 `listOf("eng", "kor")`로 바꿉니다.
- `example.ocr.tessdata-path`는 실행 환경의 설정이며 요청마다 받을 수 없습니다.
- `IllegalArgumentException`은 `400`, `OcrException`은 `503`으로 매핑합니다.

## 빠르게 시작하기 {#quick-start}

테스트는 JDK 21 이상만 필요합니다. 실제 OCR에는 Tesseract와 언어별 traineddata가 필요합니다.

```bash
brew install tesseract tesseract-lang
tesseract --list-langs
./gradlew :spring-boot-ocr-api:bootRun
```

```bash
curl -F "file=@sample-ko.png;type=image/png" \
  "http://localhost:8080/api/ocr?languages=eng+kor"
```

기본 위치가 아닌 traineddata는 `example.ocr.tessdata-path`로 지정합니다.

## 작업별 API {#api-by-task}

| 작업 | 0.4.0 API |
| --- | --- |
| OCR 요청 | `POST /api/ocr`, 멀티파트 필드 `file` |
| 언어 선택 | 쿼리 매개변수 `languages` |
| 네이티브 데이터 설정 | `ExampleOcrProperties.tessdataPath` |
| 인식 실행 | `ImmutableImage.suspendExtractText(options, engine)` |
| 결과 반환 | `OcrTextResponse(text, languages, characterCount)` |

## 권장 패턴 {#patterns}

네이티브 엔진을 주입하고 실행 환경의 경로는 설정으로 관리하세요. 디코딩 전에 콘텐츠 타입을 검사하고, 네이티브 기능을 사용할 수 없는 오류를 잘못된 클라이언트 요청과 구분하세요. 컨트롤러 테스트에서는 가짜 엔진을 사용하고 실제 OCR을 확인하는 최소 테스트는 호환 환경에서 따로 실행하는 편이 안정적입니다.

## 연동 {#integrations}

[`bluetape4k-images-ocr`](./bluetape4k-images-ocr.md)와 핵심 이미지 디코딩 기능을 사용합니다. [`spring-boot-image-api`](./spring-boot-image-api.md)와 합칠 때는 OCR을 영구 저장소에 넣기 전과 후 중 어느 시점에 실행할지 먼저 결정하세요.

## 설정 {#configuration}

JPEG, PNG, WebP, GIF 요청을 받으며 기본 언어는 `eng`입니다. Tesseract가 traineddata를 찾지 못하면 `example.ocr.tessdata-path` 또는 host의 `TESSDATA_PREFIX`를 확인하세요.

## 실패 유형과 해결 방법 {#failures}

- `400 bad_request`: 빈 파일, 콘텐츠 타입 누락, 지원하지 않는 형식, 잘못된 언어 목록입니다.
- `503 ocr_unavailable`: Tesseract/네이티브 브리지 또는 요청 언어의 traineddata가 없습니다.
- 문자가 틀림: 설치 언어, 이미지 품질·방향·전처리를 확인하세요. HTTP 성공은 인식 정확도를 보장하지 않습니다.

## 운영 {#operations}

요청 크기, 타임아웃, 동시 실행 수, 인증, 요청률은 애플리케이션에서 제한하세요. 데이터 정책이 명시적으로 허용하지 않는 한 원본 문서나 인식한 전체 문장을 로그에 남기지 마세요.

## 테스트 {#testing}

```bash
./gradlew :spring-boot-ocr-api:test
```

MockMvc와 가짜 `OcrEngine`으로 멀티파트 요청 성공, `eng+kor` 파싱, tessdata 전달, 지원하지 않는 형식, `503` 매핑을 실제 Tesseract 없이 검증합니다.

## 학습 경로와 예제 {#workshops}

1. OCR 모듈 매뉴얼을 읽고 가짜 엔진 테스트를 실행합니다.
2. Tesseract를 설치하고 `tesseract --list-langs`로 언어 팩을 확인합니다.
3. 글자가 선명한 실제 이미지로 API 계약과 인식 품질을 따로 관찰합니다.
4. [`ktor-ocr-api`](./ktor-ocr-api.md)와 비교해 멀티파트 스트리밍 구현을 살펴봅니다.

## 제약 사항 {#limitations}

인증, 대기열, 영속화, 일괄 OCR, 전처리 정책, 요청률 제한은 포함하지 않습니다. OCR 품질과 지원 언어는 실행 환경에 따라 달라집니다.

<!-- release-readme-diagrams:start -->
## 배포본 다이어그램 {#release-diagrams}

아래 그림은 `0.4.0` 배포본의 README 자산을 해당 배포 커밋에서 직접 불러옵니다. 이후 SNAPSHOT이 아니라 이 매뉴얼 버전의 구조와 실행 흐름을 보여 줍니다. 미리보기를 누르면 같은 배포 커밋의 SVG 원본이 열립니다.

### Spring Boot OCR API 아키텍처

[![Spring Boot OCR API 아키텍처](https://raw.githubusercontent.com/bluetape4k/bluetape4k-image/ea5175b083babf8880f53cf80c9a264a0c61777e/docs/images/readme-diagrams/examples-spring-boot-ocr-api-architecture-01.png)](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/docs/images/readme-diagrams/examples-spring-boot-ocr-api-architecture-01.svg)

_배포본 README: [`examples/spring-boot-ocr-api/README.ko.md`](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/examples/spring-boot-ocr-api/README.ko.md)_

### Spring Boot OCR API 실행 시나리오

[![Spring Boot OCR API 실행 시나리오](https://raw.githubusercontent.com/bluetape4k/bluetape4k-image/ea5175b083babf8880f53cf80c9a264a0c61777e/docs/images/readme-diagrams/examples-spring-boot-ocr-api-scenario-01.png)](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/docs/images/readme-diagrams/examples-spring-boot-ocr-api-scenario-01.svg)

_배포본 README: [`examples/spring-boot-ocr-api/README.ko.md`](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/examples/spring-boot-ocr-api/README.ko.md)_

### Spring Boot OCR API 처리 순서

[![Spring Boot OCR API 처리 순서](https://raw.githubusercontent.com/bluetape4k/bluetape4k-image/ea5175b083babf8880f53cf80c9a264a0c61777e/docs/images/readme-diagrams/examples-spring-boot-ocr-api-sequence-01.png)](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/docs/images/readme-diagrams/examples-spring-boot-ocr-api-sequence-01.svg)

_배포본 README: [`examples/spring-boot-ocr-api/README.ko.md`](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/examples/spring-boot-ocr-api/README.ko.md)_

<!-- release-readme-diagrams:end -->

## 근거 자료 {#sources}

- [0.4.0 README](https://github.com/bluetape4k/bluetape4k-image/blob/0.4.0/examples/spring-boot-ocr-api/README.ko.md)
- [애플리케이션 소스](https://github.com/bluetape4k/bluetape4k-image/blob/0.4.0/examples/spring-boot-ocr-api/src/main/kotlin/io/bluetape4k/images/examples/spring/ocr/SpringBootOcrApiApplication.kt)
- [통합 테스트](https://github.com/bluetape4k/bluetape4k-image/blob/0.4.0/examples/spring-boot-ocr-api/src/test/kotlin/io/bluetape4k/images/examples/spring/ocr/SpringBootOcrApiApplicationTest.kt)
- [Gradle 빌드 파일](https://github.com/bluetape4k/bluetape4k-image/blob/0.4.0/examples/spring-boot-ocr-api/build.gradle.kts)
