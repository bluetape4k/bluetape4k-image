---
manualId: "ktor-ocr-api"
id: "ktor-ocr-api"
title: "Ktor OCR API 워크숍"
locale: "ko"
kind: "example"
gradlePath: ":ktor-ocr-api"
sourceDir: "examples/ktor-ocr-api"
releaseRef: "0.3.0"
artifact: null
---

# Ktor OCR API 워크숍

> 실행 가능한 예제

## 제공하는 기능 {#problem}

멀티파트 이미지 데이터를 스트리밍으로 받고 Tesseract 언어 코드를 해석한 뒤 주입 가능한 `OcrEngine`을 호출하는 Ktor 3 예제입니다. 잘못된 입력과 네이티브 OCR 런타임 오류를 서로 다른 JSON 응답으로 처리하며, 일반 테스트에는 Tesseract가 필요하지 않습니다.

## 사용하기 좋은 경우 {#when-to-use}

Ktor 서비스에 OCR을 넣거나 네이티브 의존성을 인터페이스 뒤로 격리하는 방법을 배울 때 사용하세요. Spring Boot OCR 예제와 비교하면 프레임워크 연결 방식의 차이도 분명해집니다.

## 의존성 좌표 {#coordinates}

예제 자체는 배포되지 않습니다. 애플리케이션에서는 `bluetape4k-dependencies` 버전 하나를 선택하고 `bluetape4k-images-ocr`를 사용하세요. OCR 모듈 버전을 별도로 관리하지 않습니다.

## 핵심 개념 {#concepts}

- 멀티파트 데이터를 스트리밍으로 읽고 디코딩 전에 `maxInputBytes`를 적용합니다.
- `eng+kor`, `eng,kor`, 공백 구분 값을 언어 목록으로 바꿉니다.
- 운영 기본값은 `TesseractOcrEngine`이고 테스트에서는 가짜 엔진을 주입합니다.
- 입력 오류는 `400 bad_request`, `OcrException`은 `503 ocr_unavailable`입니다.

## 빠르게 시작하기 {#quick-start}

테스트는 JDK 21 이상만 있으면 됩니다. 실제 OCR에는 Tesseract와 요청 언어의 traineddata가 필요합니다.

```bash
brew install tesseract tesseract-lang
export EXAMPLE_OCR_TESSDATA_PATH=/opt/homebrew/share/tessdata
./gradlew :ktor-ocr-api:run
```

```bash
curl -F "file=@sample-ko.png;type=image/png" \
  "http://localhost:8080/api/ocr?languages=eng+kor"
```

응답에는 `text`, 정규화된 `languages`, `characterCount`가 들어갑니다.

## 작업별 API {#api-by-task}

| 작업 | 0.3.0 API |
| --- | --- |
| route와 런타임 설정 | `KtorOcrApiConfig` |
| 엔드포인트 설치 | `configureKtorOcrApi(config, ocrEngine)` |
| 디코딩과 인식 | `immutableImageOf(bytes).suspendExtractText(...)` |
| 준비 상태 | `GET /ready` |
| OCR 요청 | `POST /api/ocr?languages=eng+kor` |

## 권장 패턴 {#patterns}

tessdata 경로는 요청값이 아니라 실행 환경의 설정으로 관리하고 OCR 엔진은 주입하세요. 디코딩 전에 바이트 수를 제한하고, 네이티브 런타임 문제를 잘못된 사용자 요청과 다른 오류로 반환하세요. 이 구조라면 일반 CI를 Tesseract에서 분리할 수 있습니다.

## 연동 {#integrations}

[`bluetape4k-images-ocr`](./bluetape4k-images-ocr.md)와 핵심 이미지 디코딩 기능을 사용합니다. [`spring-boot-ocr-api`](./spring-boot-ocr-api.md)와 비교하면 OCR 도메인 경계는 유지한 채 프레임워크 연결 방식만 선택할 수 있습니다.

## 설정 {#configuration}

기본값은 `/api/ocr`, 멀티파트 필드 `file`, 최대 10 MiB, 언어 `eng`, 포트 `8080`입니다. `PORT`로 포트를, `EXAMPLE_OCR_TESSDATA_PATH`로 traineddata 디렉터리를 바꿉니다.

## 실패 유형과 해결 방법 {#failures}

- `400 bad_request`: 필드 누락·오류, 빈 파일, 지원하지 않는 미디어 타입, 잘못된 언어 목록, 10 MiB 초과입니다.
- `503 ocr_unavailable`: Tesseract, traineddata, native bridge 중 하나를 사용할 수 없습니다.
- 인식 결과가 비거나 부정확함: `tesseract --list-langs`, 이미지 해상도·대비·방향, 언어 목록을 확인하세요.

## 운영 {#operations}

OCR은 비용이 큰 네이티브 호출로 취급하세요. 인증, 요청률 제한, 대기열, 타임아웃, 동시 실행 제한은 애플리케이션에서 추가하고, 업로드한 문서나 인식한 전체 문장을 로그에 남기지 않는 편이 안전합니다.

## 테스트 {#testing}

```bash
./gradlew :ktor-ocr-api:test
```

가짜 엔진으로 언어 파싱과 tessdata 전달, 잘못된 필드, 지원하지 않는 콘텐츠 타입, `503` 오류 매핑을 검증합니다.

## 학습 경로와 예제 {#workshops}

1. [`ktor-image-api`](./ktor-image-api.md)에서 기본 multipart route를 익힙니다.
2. 네이티브 구성 요소를 설치하기 전에 가짜 엔진 테스트를 실행합니다.
3. Tesseract와 언어 팩을 설치하고 실제 문서 이미지를 전송합니다.
4. OCR 모듈 매뉴얼과 Spring Boot 예제를 비교합니다.

## 제약 사항 {#limitations}

인증, 영속화, 일괄 OCR, 전처리 정책, 대기열, 분산 유입 제어는 포함하지 않습니다. 지원 언어와 품질은 실행 환경의 설치 상태에 좌우됩니다.

<!-- release-readme-diagrams:start -->
## 배포본 다이어그램 {#release-diagrams}

아래 그림은 `0.3.0` 배포본의 README 자산을 해당 배포 커밋에서 직접 불러옵니다. 이후 SNAPSHOT이 아니라 이 매뉴얼 버전의 구조와 실행 흐름을 보여 줍니다. 미리보기를 누르면 같은 배포 커밋의 SVG 원본이 열립니다.

### Ktor OCR API 아키텍처

[![Ktor OCR API 아키텍처](https://raw.githubusercontent.com/bluetape4k/bluetape4k-image/a571c30004f571fe8cfcddc29670c1404d212ec6/docs/images/readme-diagrams/examples-ktor-ocr-api-architecture-01.png)](https://github.com/bluetape4k/bluetape4k-image/blob/a571c30004f571fe8cfcddc29670c1404d212ec6/docs/images/readme-diagrams/examples-ktor-ocr-api-architecture-01.svg)

_배포본 README: [`examples/ktor-ocr-api/README.ko.md`](https://github.com/bluetape4k/bluetape4k-image/blob/a571c30004f571fe8cfcddc29670c1404d212ec6/examples/ktor-ocr-api/README.ko.md)_

### Ktor OCR API 실행 시나리오

[![Ktor OCR API 실행 시나리오](https://raw.githubusercontent.com/bluetape4k/bluetape4k-image/a571c30004f571fe8cfcddc29670c1404d212ec6/docs/images/readme-diagrams/examples-ktor-ocr-api-scenario-01.png)](https://github.com/bluetape4k/bluetape4k-image/blob/a571c30004f571fe8cfcddc29670c1404d212ec6/docs/images/readme-diagrams/examples-ktor-ocr-api-scenario-01.svg)

_배포본 README: [`examples/ktor-ocr-api/README.ko.md`](https://github.com/bluetape4k/bluetape4k-image/blob/a571c30004f571fe8cfcddc29670c1404d212ec6/examples/ktor-ocr-api/README.ko.md)_

### Ktor OCR API 처리 순서

[![Ktor OCR API 처리 순서](https://raw.githubusercontent.com/bluetape4k/bluetape4k-image/a571c30004f571fe8cfcddc29670c1404d212ec6/docs/images/readme-diagrams/examples-ktor-ocr-api-sequence-01.png)](https://github.com/bluetape4k/bluetape4k-image/blob/a571c30004f571fe8cfcddc29670c1404d212ec6/docs/images/readme-diagrams/examples-ktor-ocr-api-sequence-01.svg)

_배포본 README: [`examples/ktor-ocr-api/README.ko.md`](https://github.com/bluetape4k/bluetape4k-image/blob/a571c30004f571fe8cfcddc29670c1404d212ec6/examples/ktor-ocr-api/README.ko.md)_

<!-- release-readme-diagrams:end -->

## 근거 자료 {#sources}

- [0.3.0 README](https://github.com/bluetape4k/bluetape4k-image/blob/0.3.0/examples/ktor-ocr-api/README.ko.md)
- [애플리케이션 소스](https://github.com/bluetape4k/bluetape4k-image/blob/0.3.0/examples/ktor-ocr-api/src/main/kotlin/io/bluetape4k/images/examples/ktor/ocr/KtorOcrApiApplication.kt)
- [Route 테스트](https://github.com/bluetape4k/bluetape4k-image/blob/0.3.0/examples/ktor-ocr-api/src/test/kotlin/io/bluetape4k/images/examples/ktor/ocr/KtorOcrApiApplicationTest.kt)
- [Gradle 빌드 파일](https://github.com/bluetape4k/bluetape4k-image/blob/0.3.0/examples/ktor-ocr-api/build.gradle.kts)
