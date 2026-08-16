---
manualId: "spring-boot-image-intelligence-api"
id: "spring-boot-image-intelligence-api"
title: "Spring Boot 이미지 인텔리전스 API 워크숍"
locale: "ko"
kind: "example"
gradlePath: ":spring-boot-image-intelligence-api"
sourceDir: "examples/spring-boot-image-intelligence-api"
releaseRef: "0.4.0"
artifact: null
---

# Spring Boot 이미지 인텔리전스 API 워크숍

> 실행 가능한 예제

## 제공하는 기능 {#problem}

이 Spring Boot 4 예제는 업로드한 이미지 하나를 먼저 검증하고 한 번만 디코딩한
뒤, OCR·객체 검출·바코드/QR 판독으로 불변 이미지를 전달합니다. 특정 ML 모델을
선택하는 대신 공통 입력 검증, 공급자별 격리 실행, 부분 결과, 교체 가능한 업무
정책이라는 경계를 보여 줍니다. 기준 시나리오는 방문증입니다. 텍스트, 얼굴 영역,
`visitor:` QR 값이라는 사실을 모아 정책으로 판단합니다.

## 사용하기 좋은 경우 {#when-to-use}

여러 이미지 분석이 하나의 제한된 디코드를 공유하면서도 각 경로를 독립적으로
관측해야 할 때 사용합니다. 방문증뿐 아니라 배송 라벨, 상품 라벨, 접수 문서에도
같은 구조를 적용할 수 있습니다. 인증, 보존, 악성 파일 검사, 운영 모델 관리는
예제 범위를 벗어나며 애플리케이션이 맡아야 합니다.

## 의존성 좌표 {#coordinates}

이 워크숍은 배포하지 않으므로 Maven 좌표가 없습니다. 배포 라이브러리는 하나의
`bluetape4k-dependencies` 버전을 선택하고, 이 저장소에서는
`:spring-boot-image-intelligence-api` 프로젝트를 실행합니다.

## 핵심 개념 {#concepts}

- `ImageUploadQualifier`가 미디어 타입, 압축 크기, 시그니처, 크기와 픽셀 예산을
  확인한 뒤 `ImmutableImage`을 한 번만 디코딩합니다.
- `ImageIntelligenceWorkflow`가 `suspendParallelFlow`로 OCR·검출·ZXing 경로를
  실행하고 서로 다른 `WorkContext` 키에 결과를 기록합니다.
- `AnalysisResult`는 `Completed`, `Empty`, `Unavailable`, `Failed`를 구분합니다.
  공급자 하나가 실패해도 다른 경로의 성공 결과는 보존합니다.
- 모든 경로가 사용 가능하거나 비어 있으면 `COMPLETED`, 사용 가능한 결과와
  unavailable/failed가 섞이면 `PARTIAL`, 사용 가능한 결과가 하나도 없으면
  `FAILED`입니다.
- `VisitorPassPolicy`는 공급자 중립적인 사실을 업무 결정으로 바꿉니다. 다른
  업무에 적용할 때 검증·오케스트레이션은 유지하고 정책만 교체합니다.

## 빠르게 시작하기 {#quick-start}

기본 프로필은 외부 OCR이나 ML 서비스가 필요 없습니다.

```bash
./gradlew :spring-boot-image-intelligence-api:bootRun
```

OCR과 객체 검출에는 결정적인 fixture를 사용하고 QR은 실제 ZXing으로 판독하려면
`demo` 프로필을 사용합니다.

```bash
./gradlew :spring-boot-image-intelligence-api:bootRun \
  --args='--spring.profiles.active=demo'
```

PNG·JPEG·WebP 이미지를 다음처럼 보냅니다.

```bash
curl -X POST \
  -F "file=@visitor-pass.png;type=image/png" \
  http://localhost:8080/api/images/intelligence
```

`native-ocr` 프로필은 호스트 Tesseract와 traineddata가 필요합니다. `demo`와
동시에 켜지 않도록 하십시오. 공급자 소유권이 모호해지는 조합은 의도적으로
거부합니다.

## 작업별 API {#api-by-task}

| 작업 | 예제 경계 |
| --- | --- |
| 이미지 제출 | multipart `file`을 받는 `POST /api/images/intelligence` |
| 입력 보호 | `ImageUploadQualifier`가 타입·바이트·시그니처·크기·픽셀을 검사 |
| 분석 실행 | `ImageIntelligenceWorkflow`가 OCR·검출·ZXing을 조정 |
| 부분 결과 보존 | `AnalysisResult`와 `ImageIntelligenceAggregator` |
| 업무 규칙 적용 | `VisitorPassPolicy`가 사실을 `ALLOW` 또는 `MANUAL_REVIEW`로 변환 |

## 권장 패턴 {#patterns}

불변 이미지를 한 번만 디코딩해 여러 공급자에 공유하고, 각 공급자가 업로드를
다시 열지 않게 합니다. 공급자별 제한 시간과 Semaphore를 분리하고 `Empty`(실행은
했지만 찾지 못함)와 `Failed`(확인할 수 없음)를 같은 의미로 취급하지 않습니다.
외부 `CancellationException`은 업무 실패로 바꾸지 않고 전달합니다. 인터럽트를
무시하는 네이티브 호출은 반환할 때까지 스레드를 점유할 수 있으므로, 강한 실행
시간 제한이 필요하면 프로세스 격리와 프로세스 수준 timeout을 추가합니다.

## 연동 {#integrations}

예제는 `bluetape4k-images` 디코드, OCR contract, provider-neutral barcode API,
ZXing provider, Spring Boot 4, `bluetape4k-workflow`를 함께 사용합니다. 검출
어댑터는 의도적으로 로컬에 두어 실제 모델을 도입해도 HTTP와 집계 계약을 바꾸지
않게 했습니다.

## 설정 {#configuration}

릴리스 기본값은 압축 입력 5 MiB, 한 변 8,192픽셀, 디코드 면적 16,777,216픽셀과
공급자별 제한 시간·동시성입니다.

```yaml
example:
  image-intelligence:
    max-input-bytes: 5242880
    max-input-pixels: 16777216
    max-input-side: 8192
    ocr-timeout: 3s
    detection-timeout: 2s
    barcode-timeout: 2s
    ocr-concurrency: 1
    detection-concurrency: 2
    barcode-concurrency: 4
    tessdata-path: null
```

`tessdata-path` 같은 호스트 경로는 애플리케이션 설정에만 두고 업로드 요청에서
받지 않습니다.

## 실패 유형과 해결 방법 {#failures}

- 빈 업로드, 미지원 미디어 타입, 시그니처 불일치, 잘못된 이미지, 크기·픽셀
  제한 초과는 `400`으로 처리합니다.
- 압축 입력 제한 초과는 `413`입니다.
- 공급자 timeout이나 네이티브 의존성 부재는 `Failed` 또는 `Unavailable`로
  응답하며 성공한 다른 경로는 유지합니다.
- WorkContext 키 누락과 예상하지 못한 프로그래밍 오류는 정상 공급자 결과가
  아니라 워크플로 실패입니다.

응답에는 안정된 상태와 reason code만 노출하고 업로드 내용은 반환하거나 로그에
남기지 않습니다. 경로와 네이티브 예외를 외부 호출자에게 전달하지 말고, 공급자
설정과 정제된 애플리케이션 로그를 확인합니다.

## 운영 {#operations}

신뢰하지 않는 요청을 받기 전에 인증·인가, 테넌트별 quota, rate limit, 요청 timeout,
바이러스/콘텐츠 무해화 검사, 보존·삭제, 암호화와 감사 정책을 추가합니다. 크기와
픽셀 제한은 디코드 메모리를 보호하고, 공급자별 Semaphore는 CPU와 네이티브 자원을
보호합니다. `PARTIAL`을 인프라 장애로만 보지 말고 공급자 지연과 부분 결과 비율을
측정합니다.

## 테스트 {#testing}

```bash
./gradlew :spring-boot-image-intelligence-api:test
```

릴리스 테스트는 생성한 QR의 실제 ZXing 추출, 입력 검증 경계, 프로필 소유권,
병렬 겹침, 부분 실패, 워크플로 키, 정책 결정표, 외부 취소, permit 복구, payload
없는 로그, HTTP 오류 계약을 확인합니다. 네이티브 OCR smoke test는 호스트 환경에
따라 달라집니다.

## 학습 경로와 예제 {#workshops}

1. 불변 이미지와 OCR 모듈 문서를 읽고 fake 공급자 테스트를 실행합니다.
2. `demo` 프로필로 네이티브 OCR 없이 완료 집계를 관찰합니다.
3. Ktor와 Spring OCR 워크숍을 비교해 transport와 공급자 오케스트레이션의 경계를
   구분합니다.
4. 같은 입력 검증·부분 결과 경계를 유지한 채 방문증 정책을 배송 라벨이나 상품
   라벨 정책으로 교체합니다.

## 제약 사항 {#limitations}

인증, 영속화, queue, batch 처리, 전처리 정책, 악성 파일 검사, 테넌트 격리, retry·
circuit breaker, 모델 선택, 품질 측정과 drift monitoring은 제공하지 않습니다.
비협조적인 네이티브 함수는 coroutine 취소만으로 강제 종료되지 않을 수 있습니다.
네이티브 실행에 hard bound가 필요하면 프로세스 격리를 사용해야 합니다.

## 근거 자료 {#sources}

- [릴리스 README](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/examples/spring-boot-image-intelligence-api/README.ko.md)
- [애플리케이션 설정](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/examples/spring-boot-image-intelligence-api/src/main/kotlin/io/bluetape4k/images/examples/spring/intelligence/config/ImageIntelligenceConfiguration.kt)
- [업로드 검증](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/examples/spring-boot-image-intelligence-api/src/main/kotlin/io/bluetape4k/images/examples/spring/intelligence/service/ImageUploadQualifier.kt)
- [워크플로 오케스트레이션](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/examples/spring-boot-image-intelligence-api/src/main/kotlin/io/bluetape4k/images/examples/spring/intelligence/service/ImageIntelligenceWorkflow.kt)
- [공급자 어댑터](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/examples/spring-boot-image-intelligence-api/src/main/kotlin/io/bluetape4k/images/examples/spring/intelligence/service/ImageAnalysisProviders.kt)
- [정책](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/examples/spring-boot-image-intelligence-api/src/main/kotlin/io/bluetape4k/images/examples/spring/intelligence/service/VisitorPassPolicy.kt)
- [HTTP 연동 테스트](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/examples/spring-boot-image-intelligence-api/src/test/kotlin/io/bluetape4k/images/examples/spring/intelligence/web/ImageIntelligenceControllerTest.kt)
- [Gradle 빌드 파일](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/examples/spring-boot-image-intelligence-api/build.gradle.kts)
