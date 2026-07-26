# Issue #299 - 통합 이미지 인텔리전스 API 예제 설계

**Date**: 2026-07-27
**Issue**: https://github.com/bluetape4k/bluetape4k-image/issues/299
**Transferred from**: https://github.com/bluetape4k/bluetape4k-workshop/issues/578
**Target example**: `examples/spring-boot-image-intelligence-api`
(`:spring-boot-image-intelligence-api`)
**Workflow lane**: Type A Full Feature
**Status**: Approved for implementation planning

## 1. 목적과 독자

하나의 이미지에 다양한 정보가 있을 때 OCR, 이미지 영역 감지, 바코드·QR 판독을
어떻게 한 번의 입력 검증과 디코딩 이후 병렬로 수행하고, 일부 작업이 실패하더라도
성공한 결과를 보존할지를 보여 주는 Spring Boot 실전 예제를 만든다.

대표 시나리오는 방문증 이미지 분석이다.

1. 사용자가 방문증 사진을 업로드한다.
2. 서비스가 파일 크기, 미디어 형식, 실제 이미지 여부, 해상도 한계를 검증한다.
3. 검증을 통과한 이미지를 한 번만 `ImmutableImage`로 디코딩한다.
4. OCR, 얼굴·민감 영역 감지, QR·바코드 판독 작업을 병렬로 실행한다.
5. 각 작업의 완료, 빈 결과, 사용 불가, 실패를 독립적으로 기록한다.
6. 응용 서비스 정책이 전체 결과를 해석해 `ALLOW`, `MANUAL_REVIEW`,
   `REJECT`, `QUARANTINE` 중 하나를 결정한다.

이 예제는 모든 이미지 처리 문제를 해결하는 만능 서비스를 목표로 하지 않는다.
검증, 병렬 처리, 부분 실패, 공급자 교체, 정책 분리의 재사용 가능한 기본 구조를
보여 주는 것이 목표다.

주 독자는 다음과 같다.

- OCR, 이미지 검출, 바코드 기능을 하나의 API로 조합하려는 Kotlin 개발자
- 코루틴 기반 병렬 처리에서 부분 실패와 취소를 구분하려는 백엔드 개발자
- 이미지 분석 사실과 업무 정책을 분리해야 하는 이유를 이해하려는 기획자와 설계자
- 이후 `bluetape4k.github.io#201` 이미지 처리 시리즈를 읽을 독자

## 2. 현재 근거

### 저장소 안의 재사용 대상

| 현재 구현 | 새 예제에서 재사용할 내용 |
|---|---|
| `bluetape4k-images` | `ImmutableImage`, 이미지 디코딩, 크기 탐색, 감지 계약 |
| `bluetape4k-images-ocr` | `OcrEngine`, `StructuredOcrEngine`, 구조화된 OCR 결과 |
| `bluetape4k-images-barcode-api` | 공급자 중립 `BarcodeReader`와 결과 모델 |
| `bluetape4k-images-barcode-zxing` | 실제 QR·바코드 판독을 수행하는 순수 JVM 공급자 |
| `examples/spring-boot-ocr-api` | multipart 입력, OCR 구성, 호스트 의존성 없는 테스트 패턴 |
| `examples/spring-boot-barcode-api` | 업로드 제한, 실제 ZXing 판독, 안정된 오류 응답 패턴 |
| `examples/basic-processing` | 감지 사실과 민감 정보 처리 정책을 분리하는 예제 |
| `bluetape4k-workflow` | `SuspendParallelFlow`, `WorkContext`, `WorkReport` |

현재 `develop`에서 위 4개 라이브러리 모듈과 Spring Boot OCR·바코드 예제 테스트를
한 Gradle 실행으로 검증했으며 53개 task가 성공했다. 새 예제는 이 기준선 위에서
작성한다.

### 공개 자료와의 중복

- 기존 OCR 글은 OCR API, 공급자 fallback, 네이티브 실행 조건을 설명한다.
- 기존 민감 정보 처리 예제와 `bluetape4k-image#219`는 감지 결과에서 처리 정책을
  고르는 흐름에 집중한다.
- 기존 바코드 예제는 단일 판독 기능과 업로드 경계를 설명한다.
- OCR·감지·바코드를 하나의 부분 결과 응답으로 조합하는 공개 예제나 글은 없다.

새 예제와 후속 글은 기존 기능 설명을 반복하지 않고, 기능 사이의 조정 경계와
실패 의미를 추가한다.

## 3. 저장소 선택

이 작업은 처음에 `bluetape4k-workshop#578`로 시작했으나
`bluetape4k-image#299`로 이전했다.

`bluetape4k-workshop`에서 구현하면 아직 정식 배포되지 않은 0.4.0 바코드 모듈을
하위 저장소가 snapshot으로 소비해야 한다. 반면 `bluetape4k-image/examples`에서는
같은 source tree의 이미지·OCR·바코드 모듈에 프로젝트 의존성을 걸어, 현재 코드를
직접 통합 검증할 수 있다.

따라서 새 예제는 배포 artifact가 아닌 `bluetape4k-image` 내부 실행 예제로 둔다.
라이브러리 공개 API나 BOM에는 새 artifact를 추가하지 않는다.

## 4. 범위

### 포함

- `POST /api/images/intelligence` multipart API
- 공통 입력 검증과 단일 이미지 디코딩
- OCR, 감지, 바코드 작업의 제한된 병렬 실행
- 작업별 `Completed`, `Empty`, `Unavailable`, `Failed` 결과
- 부분 결과 집계와 방문증 데모 정책
- 기본, `demo`, 선택적 native OCR 구성
- 결정적인 단위·통합 테스트와 실제 ZXing 판독
- 영어·한국어 README와 dark style 기술 다이어그램
- 저장소의 예제 등록·CI·가이드 표면 갱신

### 제외

- 실제 출입 통제, 신원 확인, 얼굴 인식, 생체 정보 매칭
- 운영용 ML 모델의 번들·학습·다운로드
- 인증·인가, 바이러스 검사, 장기 저장소, 메시지 브로커, 감사 이력
- 외부 OCR·비전 API 자격 증명을 요구하는 기본 실행
- 라이브러리 공개 API, BOM, 배포 artifact 추가
- 기존 OCR·바코드·민감 정보 예제 재작성
- 구현 검증 전에 후속 블로그 글 작성
- 아직 0.4.0 release tag가 없는 상태에서 versioned manual을 갱신하는 일

## 5. 검토한 접근

### A. 새 통합 Spring Boot 예제를 `bluetape4k-image/examples`에 추가

채택한다. 이미지 기능의 실제 source tree를 직접 조합하며, 기존 단일 기능 예제는
간결하게 유지할 수 있다. 통합 경계와 실패 정책을 한 모듈에서 읽고 실행할 수 있다.

### B. 기존 `spring-boot-ocr-api`나 `spring-boot-barcode-api`를 확장

채택하지 않는다. 한 예제가 다른 기능과 정책까지 떠안아 단일 기능 학습 경로가
흐려진다. 두 예제의 독립 실행성과 간결한 API 계약도 깨진다.

### C. `bluetape4k-workshop`에 통합 모듈 추가

채택하지 않는다. 정식 배포 전 바코드 모듈을 snapshot으로 소비해야 하고,
생산자 저장소의 현재 코드와 예제 검증 시점이 어긋난다.

### D. `supervisorScope + async`로 직접 병렬 실행

채택하지 않는다. 코루틴만으로도 구현할 수 있지만 `bluetape4k-workflow`가 이미
제공하는 실행 보고서와 병렬 정책을 반복 구현하게 된다. 이 예제에서는 workflow
라이브러리와 업무 결과의 의미를 분리하는 방법 자체가 학습 대상이다.

## 6. 의존성과 모듈 등록

새 모듈은 다음 의존성을 사용한다.

```kotlin
dependencies {
    implementation(project(":bluetape4k-images"))
    implementation(project(":bluetape4k-images-ocr"))
    implementation(project(":bluetape4k-images-barcode-zxing"))
    implementation(bt4k.bluetape4k.workflow)

    implementation(libs.spring.boot.starter.web)
    implementation(libs.kotlinx.coroutines.reactor)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.webmvc.test)
    testImplementation(bt4k.bluetape4k.junit5)
    testImplementation(libs.kotlinx.coroutines.test)
}
```

- 이미지 기능은 local project dependency로 재사용한다.
- `bluetape4k-workflow`는 중앙 catalog의 versionless alias를 사용한다.
- 별도 version catalog 항목이나 직접 버전은 추가하지 않는다.
- 예제이므로 publishing 설정과 BOM 등록은 하지 않는다.

등록 대상은 다음과 같다.

- `settings.gradle.kts`
- 루트 `AGENTS.md`의 예제 표와 검증 명령
- 루트 `README.md`, `README.ko.md`의 예제 학습 경로
- `.github/workflows/Examples.yml`의 단일 matrix 항목
- 예제의 `README.md`, `README.ko.md`
- 예제의 `src/test/resources/junit-platform.properties`, `logback-test.xml`

`Examples.yml`은 이미 `examples/**`를 감시하므로 path filter를 넓힐 필요가 없다.
새 matrix 항목 `:spring-boot-image-intelligence-api:test`만 추가한다.

`docs/manual`은 안정 release tag를 기준으로 작성되는 versioned manual이다.
이번 변경은 0.4.0 개발 예제이므로 manual manifest와 release-pinned 페이지는
갱신하지 않는다. 0.4.0 manual 작성 시 별도 등록한다.

## 7. 아키텍처

```text
Client
  -> ImageIntelligenceController
      -> ImageUploadQualifier
          -> QualifiedImage(mediaType, ImmutableImage, metadata)
      -> ImageIntelligenceWorkflow
          -> SuspendParallelFlow(ALL)
              -> OcrAnalysis
              -> DetectionAnalysis
              -> BarcodeAnalysis
      -> ImageIntelligenceAggregator
      -> VisitorPassPolicy
      -> ImageIntelligenceResponse
```

| 컴포넌트 | 책임 |
|---|---|
| `ImageIntelligenceController` | multipart 입력과 HTTP 응답 경계 |
| `ImageUploadQualifier` | 바이트·형식·디코딩·픽셀 한계를 검증하고 `ImmutableImage` 생성 |
| `QualifiedImage` | 한 번 디코딩한 이미지와 정제된 메타데이터 |
| `ImageIntelligenceWorkflow` | 세 분석 작업을 `SuspendParallelFlow`로 조정 |
| `OcrAnalysis` | 구조화 OCR 공급자를 호출하고 OCR 업무 결과 생성 |
| `DetectionAnalysis` | 이미지 감지 공급자를 호출하고 감지 업무 결과 생성 |
| `BarcodeAnalysis` | ZXing 기반 바코드·QR 결과 생성 |
| `ImageIntelligenceAggregator` | 작업별 결과를 전체 응답 상태로 집계 |
| `VisitorPassPolicy` | 분석 사실을 방문증 데모 결정으로 해석 |
| `ImageIntelligenceExceptionHandler` | 입력 오류와 오케스트레이터 오류를 정제된 `ProblemDetail`로 변환 |

각 컴포넌트는 HTTP DTO, 분석 업무 결과, 공급자 API, 정책 결정을 섞지 않는다.
분석 공급자를 바꿔도 컨트롤러와 정책 계약은 유지되고, 방문증 정책을 다른 업무
정책으로 바꿔도 OCR·감지·바코드 실행기는 유지된다.

## 8. 한 번의 입력 검증과 디코딩

`ImageUploadQualifier`는 분석 작업을 시작하기 전에 다음을 한 번만 수행한다.

- multipart part 존재 여부와 빈 파일 검사
- 허용된 선언 미디어 형식 검사
- 최대 업로드 바이트 검사
- magic byte와 선언 미디어 형식의 일관성 검사
- 전체 디코딩 전에 이미지 크기를 탐색해 너비, 높이, 전체 픽셀 수 검사
- 크기 제한을 통과한 이미지의 실제 디코딩
- `ImmutableImage`와 정제된 메타데이터 생성

OCR 품질, 감지 confidence, 바코드 존재 여부는 공통 입력 검증이 아니다.
입력 이미지가 분석 가능한 형식인지를 통과한 뒤 각 분석 작업의 업무 결과로 판단한다.

파일명은 식별자나 보안 판단에 사용하지 않는다. 업로드 원본, OCR 본문, 바코드 원문,
감지된 민감 정보는 기본 로그에 기록하지 않는다.

`QualifiedImage`는 분석에 필요하지 않은 원본 `ByteArray`를 보관하지 않는다. 요청
바이트는 검증과 디코딩이 끝난 뒤 참조를 해제해, 압축 바이트와 디코딩 이미지가 전체
분석 시간 동안 함께 유지되는 것을 피한다.

## 9. 두 개의 상태 축

### 워크플로 실행 상태

`WorkReport.Success`는 **workflow step이 끝까지 실행되어 자신의 업무 결과를
`WorkContext`에 기록했다**는 뜻이다.

```text
WorkReport.Success
  = workflow step completed and result recorded
  != OCR, detection, or barcode business success
```

공급자가 없거나 분석이 실패해도 해당 사실을 `AnalysisResult`로 기록했다면
workflow step은 `Success`다. 결과를 기록할 수 없는 프로그래밍 오류나 외부 요청
취소만 workflow 실행 실패 또는 취소로 전파한다.

### 분석 업무 결과

```kotlin
sealed interface AnalysisResult<out T> {
    val provider: String
    val elapsedMillis: Long

    data class Completed<T>(
        override val provider: String,
        override val elapsedMillis: Long,
        val value: T,
    ) : AnalysisResult<T>

    data class Empty(
        override val provider: String,
        override val elapsedMillis: Long,
    ) : AnalysisResult<Nothing>

    data class Unavailable(
        override val provider: String,
        override val elapsedMillis: Long,
        val reasonCode: String,
    ) : AnalysisResult<Nothing>

    data class Failed(
        override val provider: String,
        override val elapsedMillis: Long,
        val reasonCode: String,
    ) : AnalysisResult<Nothing>
}
```

- `COMPLETED`: 공급자가 유효한 하나 이상의 결과를 반환했다.
- `EMPTY`: 공급자 실행은 정상 종료했지만 검출된 정보가 없다.
- `UNAVAILABLE`: 공급자가 구성되지 않았거나 현재 실행 환경에서 사용할 수 없다.
- `FAILED`: 제한 시간 초과 또는 정규화할 수 있는 공급자 오류가 발생했다.

원본 예외 메시지, stack trace, native 경로는 API 응답에 넣지 않는다.

## 10. 병렬 실행, 부분 실패, 취소

`SuspendParallelFlow(ParallelPolicy.ALL)`의 각 작업은 서로 다른 `WorkContext` 키에
분석 결과를 한 번 기록한 뒤 `WorkReport.success(context)`를 반환한다.

```kotlin
val flow = suspendParallelFlow("image-intelligence") {
    execute("ocr") { context ->
        context[OCR_RESULT] = ocrAnalysis.analyze(qualifiedImage)
        WorkReport.success(context)
    }
    execute("detection") { context ->
        context[DETECTION_RESULT] = detectionAnalysis.analyze(qualifiedImage)
        WorkReport.success(context)
    }
    execute("barcode") { context ->
        context[BARCODE_RESULT] = barcodeAnalysis.analyze(qualifiedImage)
        WorkReport.success(context)
    }
    all()
}
```

각 작업은 공유 객체를 변경하거나 read-modify-write하지 않는다. Workflow 종료 뒤에만
세 키를 읽어 응답을 집계한다.

일반적인 공급자 실패와 작업별 제한 시간은 `AnalysisResult.Failed`로 정규화하므로
다른 작업을 취소하지 않는다. 다음 경우에는 workflow 수준에서 fail-fast한다.

- 결과 기록 전의 예상하지 못한 프로그래밍 오류
- 작업이 완료 불가 `WorkReport`를 반환한 경우
- 외부 요청 취소

공급자별 보호 장치는 응용 서비스가 소유한다.

- OCR, 감지, 바코드에 서로 다른 제한 시간을 둔다.
- 네이티브 또는 비용이 큰 공급자에는 공급자별 `Semaphore`를 둔다.
- 각 공급자 동시 실행 수는 양의 정수 설정으로 제한한다.
- 분석 adapter는 `suspend` 계약을 제공하고, blocking JVM 공급자는 기존
  `suspendExtractOcr`, `suspendDetectRegions`, `suspendExtractBarcodes` 확장을 통해
  지정 dispatcher에서 실행한다.
- 내부 `withTimeout`의 `TimeoutCancellationException`만 해당 작업의
  `Failed(TIMEOUT)`으로 변환한다.
- 그 밖의 `CancellationException`은 잡아 두지 않고 다시 던진다.
- 전체 HTTP 요청 제한 시간은 서버 운영 설정의 책임이며 작업별 제한 시간과 구분한다.

in-process native 호출이 thread interruption에 반응하지 않으면 `withTimeout`만으로
실행을 강제 종료할 수 없다. 선택적 Tesseract 실행은 이 한계를 README에 명시한다.
엄격한 종료 시간이 필요한 운영 공급자는 별도 process 또는 원격 worker로 격리해야
한다. 기본·demo 테스트는 cooperative adapter의 timeout과 cancellation만 보장한다.

## 11. 공급자 구성

### 기본 환경

별도 native 설정 없이 안전하고 결정적으로 실행된다.

| 작업 | 기본 공급자 | 결과 |
|---|---|---|
| OCR | `disabled-ocr` | `UNAVAILABLE` |
| 이미지 감지 | `disabled-detector` | `UNAVAILABLE` |
| 바코드·QR | `zxing` | 실제 판독 |

### `demo` 프로필

통합 흐름과 정책을 로컬에서 재현하기 위한 결정적 공급자를 사용한다.

| 작업 | demo 공급자 |
|---|---|
| OCR | `fixture-ocr` |
| 이미지 감지 | `fixture-detector` |
| 바코드·QR | 실제 `zxing` |

fixture OCR과 감지는 해당 모듈의 계약을 구현하되 운영 공급자로 오해되지 않도록
package, bean 이름, 응답의 provider 식별자에 `fixture`를 명시한다.

### 선택적 native OCR

명시적 `native-ocr` 프로필에서만 `TesseractOcrEngine`을 구성한다. 기본 테스트와
CI는 Tesseract, tessdata, 운영체제 native library를 요구하지 않는다.

운영 ML 감지기는 애플리케이션이 `ImageDetector` adapter로 제공해야 한다.
이 예제는 모델을 선택하거나 자동 다운로드하지 않는다.

## 12. HTTP 계약

### Endpoint

`POST /api/images/intelligence`

- consumes: `multipart/form-data`
- required part: `file`
- 유효한 이미지가 분석 경계에 들어간 뒤의 일부·전체 분석 실패: HTTP `200`
- 빈 파일, 형식 불일치, 디코딩 불가: HTTP `400` `ProblemDetail`
- 압축 바이트, 한 변 길이, 픽셀 한계 초과: HTTP `413` `ProblemDetail`
- 요청 취소: 코루틴 취소 전파
- 예상하지 못한 workflow 결함: HTTP `500` `ProblemDetail`

여기서 HTTP `200`은 입력을 받아 분석 envelope를 정상적으로 만들었다는 transport
결과다. 업무적으로 사용할 수 있는 분석 결과가 없을 수 있으므로 호출자는 반드시
응답의 전체 상태와 정책 결정을 확인해야 한다.

### 전체 상태

| 상태 | 조건 |
|---|---|
| `COMPLETED` | 모든 분석이 `COMPLETED` 또는 정책상 허용된 `EMPTY` |
| `PARTIAL` | 하나 이상의 사용 가능한 결과와 하나 이상의 `FAILED` 또는 `UNAVAILABLE` |
| `FAILED` | 정책 판단에 필요한 결과를 하나도 얻지 못했거나 모든 필수 분석이 실패 |

`EMPTY`가 성공인지 실패인지는 분석기 자체가 아니라 `VisitorPassPolicy`가 판단한다.
바코드가 없는 일반 사진은 정상적인 빈 결과일 수 있지만, 방문증에서는 수동 검토
사유가 될 수 있다.

응답은 `WorkContext`나 `WorkReport`를 노출하지 않는 전용 DTO다.

```json
{
  "requestId": "01J...",
  "status": "PARTIAL",
  "decision": "MANUAL_REVIEW",
  "reasons": ["OCR_UNAVAILABLE"],
  "image": {
    "mediaType": "image/png",
    "width": 1200,
    "height": 800
  },
  "ocr": {
    "status": "UNAVAILABLE",
    "provider": "disabled-ocr",
    "reasonCode": "PROVIDER_NOT_CONFIGURED"
  },
  "detection": {
    "status": "EMPTY",
    "provider": "fixture-detector",
    "regions": []
  },
  "barcodes": {
    "status": "COMPLETED",
    "provider": "zxing",
    "items": []
  }
}
```

## 13. 방문증 정책

`VisitorPassPolicy`는 분석기가 관찰한 사실을 소비하며, 분석 수행 책임은 갖지 않는다.

```text
민감하거나 금지된 영역 감지
  -> QUARANTINE

필수 QR 형식이 잘못됨
  -> REJECT

필수 작업이 FAILED 또는 UNAVAILABLE
  -> MANUAL_REVIEW

얼굴 또는 QR이 없거나 여러 개라 규칙을 확정할 수 없음
  -> MANUAL_REVIEW

모든 필수 조건 충족
  -> ALLOW
```

감지 결과가 비어 있는 경우와 감지 공급자가 실패한 경우를 합치지 않는다. 실패를 빈
목록으로 바꾸면 자동 허용으로 잘못 판정할 수 있다.

정책 결과는 학습용 설명이며 실제 출입 권한을 부여하지 않는다. 배송 라벨이나 상품
라벨에 같은 처리 구조를 재사용할 때는 `VisitorPassPolicy`를 해당 업무 정책으로
교체해야 한다.

## 14. 오류와 관측성

- 예상 가능한 입력 오류는 안정된 reason code를 가진 `400 ProblemDetail`로 반환한다.
- 분석 공급자 오류는 작업별 `FAILED`로 정규화하고 원본 예외를 응답에 노출하지 않는다.
- 예상하지 못한 workflow 결함만 정제된 `500 ProblemDetail`로 반환한다.
- lifecycle, 작업별 provider, 상태, 제한 시간, 경과 시간은 구조화 로그로 남긴다.
- 업로드 원본, OCR 본문, 바코드 원문, 감지된 민감 정보는 로그에 남기지 않는다.
- request id와 작업 이름은 저카디널리티 운영 문맥으로 사용하되 개인정보를 넣지 않는다.
- health endpoint, 영속화, 재시도, 외부 공급자 circuit breaker는 이 예제 범위 밖이며
  운영 adapter를 추가할 때 별도로 설계한다.

## 15. 주요 실패 모드

| 실패 모드 | 처리 | 검증 |
|---|---|---|
| 미디어 형식을 위장한 업로드 | 분석 전 `400`; 공급자 미호출 | HTTP 통합 테스트 |
| 디코딩 폭탄에 가까운 픽셀 수 | 전체 디코딩 전 dimension probe와 pixel budget으로 `400` | 경계값 테스트 |
| OCR 공급자 미설정 | OCR만 `UNAVAILABLE`; 다른 결과 보존 | 기본 profile 테스트 |
| 한 공급자 예외 | 해당 작업만 `FAILED`; 형제 결과 보존 | workflow 테스트 |
| 작업별 제한 시간 초과 | 해당 작업만 `FAILED(TIMEOUT)` | virtual time 또는 제어된 지연 테스트 |
| 외부 요청 취소 | 모든 하위 작업에 취소 전파 | 실제 coroutine cancellation 테스트 |
| interruption에 반응하지 않는 native 호출 | 강제 종료를 보장하지 않음을 문서화하고 운영에서는 process 격리 | adapter 계약·README 검증 |
| workflow가 결과 키를 기록하지 않음 | 오케스트레이터 결함으로 `500` | 실패 주입 테스트 |
| 감지 실패를 빈 목록으로 오판 | 정책이 자동 허용하지 않고 `MANUAL_REVIEW` | 정책 결정표 테스트 |

## 16. 테스트 전략

### 입력 경계

- multipart part 누락과 빈 파일
- 지원하지 않는 선언 형식
- magic byte 불일치
- 실제 업로드 바이트 초과
- 디코딩 불가
- 너비, 높이, 전체 픽셀 수의 경계값
- 분석 공급자가 호출되기 전에 거부되는지 검증

### 분석과 workflow

- `Completed`, `Empty`, `Unavailable`, `Failed` 매핑
- 세 작업이 순차 합보다 짧은 시간 안에 겹쳐 실행됨
- OCR 실패 뒤에도 감지와 바코드 결과가 보존됨
- 한 작업의 `Empty`가 다른 결과를 지우지 않음
- 작업별 제한 시간은 해당 작업의 `Failed`만 생성
- 외부 취소가 모든 하위 작업에 전달됨
- 각 작업이 서로 다른 `WorkContext` 키에 한 번만 기록
- 예외 메시지와 stack trace 비노출

### 정책

- 금지 영역의 `QUARANTINE`
- 잘못된 필수 QR의 `REJECT`
- 공급자 실패·사용 불가의 `MANUAL_REVIEW`
- 필수 사실 누락·중복의 `MANUAL_REVIEW`
- 모든 필수 조건 충족의 `ALLOW`
- 감지 `Empty`와 감지 `Failed`가 다른 결정을 만듦

### HTTP 통합

- `demo` profile의 실제 ZXing + fixture OCR·감지 `COMPLETED`
- 한 공급자 실패의 `PARTIAL`
- 기본 profile의 OCR·감지 `UNAVAILABLE`
- 모든 필수 결과를 얻지 못한 `FAILED`
- 입력 오류의 정제된 `400`
- 예상하지 못한 workflow 결함의 정제된 `500`

테스트 fixture는 방문증 시나리오를 재현하는 고정 이미지와 pinned hash를 가진다.
QR 판독은 실제 `ZxingBarcodeReader`를 호출한다. OCR·감지는 호스트 의존성을 없애기
위해 결정적인 adapter를 사용한다. native OCR 테스트는 opt-in이며 다른 native·container
검증과 병렬 실행하지 않는다.

## 17. 문서와 다이어그램

`README.md`와 `README.ko.md`를 동등한 구조로 작성한다.

- 시나리오와 이 예제가 해결하지 않는 범위
- 전체 구조와 한 번 디코딩한 뒤 세 작업으로 분기하는 처리 흐름
- `WorkReport.Success`와 분석 `COMPLETED`의 차이
- 기본, `demo`, 선택적 `native-ocr` 실행 방법
- `COMPLETED`, `PARTIAL`, `FAILED` 응답 예시
- 공급자별 제한 시간과 동시 실행 수 설정
- 방문증 이외 업무에서 정책만 교체하는 방법
- 실서비스에서 추가해야 할 인증, 저장, 악성 파일 검사, 개인정보 보호
- 관련 OCR·바코드·민감 정보 자료 링크

dark style 기술 다이어그램은 다음 두 개를 만든다.

1. 입력 검증 → 단일 디코딩 → 세 분석 작업 → 집계 → 정책의 아키텍처
2. 정상, 부분 실패, 외부 취소의 상호 작용 흐름

SVG를 원본으로 유지하고 같은 basename의 PNG를 생성한다. README에는 PNG를
표시하고 SVG를 크게 볼 수 있는 링크를 제공한다. PNG 변환 뒤 텍스트 잘림, 화살촉,
call line, 카드 간격을 실제 크기로 검수한다.

## 18. 호환성과 운영 경계

- 기존 라이브러리와 예제의 공개 계약은 바꾸지 않는다.
- 새 예제는 배포되지 않으므로 소비자 BOM과 artifact 좌표에 영향이 없다.
- 기본 profile은 외부 서비스, native OCR, Docker 없이 실행된다.
- 실제 OCR·감지 공급자는 adapter bean을 교체하는 확장점으로만 제공한다.
- API 응답의 상태와 reason code는 예제 내부의 안정된 학습 계약으로 테스트한다.
- 운영에 적용할 때 인증·인가, 저장·삭제 정책, 악성 파일 검사, 개인정보 보호,
  외부 공급자 재시도와 circuit breaker를 별도 설계해야 한다.

## 19. 수용 기준

- [ ] `examples/spring-boot-image-intelligence-api`가 실행 가능한 Spring Boot 예제로 등록된다.
- [ ] 이미지·OCR·바코드 local project와 관리된 `bluetape4k-workflow` 의존성을 재사용한다.
- [ ] 입력을 한 번 검증하고 `ImmutableImage`를 한 번 디코딩한 뒤 세 작업이 공유한다.
- [ ] OCR·감지·바코드를 제한된 병렬 실행으로 처리한다.
- [ ] 각 작업은 `Completed`, `Empty`, `Unavailable`, `Failed`를 독립적으로 반환한다.
- [ ] `WorkReport.Success`와 분석 업무 성공을 코드와 문서에서 분리한다.
- [ ] 한 작업의 실패 후에도 다른 성공 결과가 응답에 남는다.
- [ ] 작업별 timeout과 외부 cancellation이 구분된다.
- [ ] 감지 사실과 `VisitorPassPolicy` 결정이 분리된다.
- [ ] 기본 테스트는 native OCR이나 운영 ML 모델을 요구하지 않는다.
- [ ] 실제 ZXing 판독을 포함한 성공·빈 결과·부분 실패·사용 불가·입력 오류·취소 테스트가 통과한다.
- [ ] 영어·한국어 README와 dark style SVG·PNG 다이어그램이 동등하게 제공된다.
- [ ] settings, AGENTS, root README, Examples workflow 등록이 완료된다.
- [ ] `./gradlew projects`에 새 모듈이 표시되고 targeted/full example tests가 통과한다.
- [ ] `actionlint .github/workflows/Examples.yml`, diagram 검증, `git diff --check`가 통과한다.
- [ ] versioned manual과 배포 BOM은 변경하지 않는다.

## 20. 완료 조건

- Type A 설계·계획·구현·검토·lesson·PR 게이트가 순서대로 완료된다.
- 기본 환경에서 외부 자격 증명, Docker, native OCR 없이 예제 테스트가 통과한다.
- `demo` profile에서 fixture OCR·감지와 실제 ZXing을 조합한 결과를 재현할 수 있다.
- 부분 실패에서도 성공한 결과가 보존되고, 외부 취소는 결과로 위장되지 않는다.
- 상태 두 축과 정책 분리가 코드, 테스트, README, 다이어그램에서 같은 의미로 표현된다.
- 블로그 시리즈가 재사용할 수 있는 안정적인 예제 코드와 설명 경계가 마련된다.
- PR 생성 후 CI와 리뷰가 통과하면 merge-ready 상태에서 멈추고, 별도의 최신 승인 없이는
  merge하지 않는다.
