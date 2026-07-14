# Issue #273 Spring Boot Barcode Quickstart Design

## 1. 배경

- Issue: [#273](https://github.com/bluetape4k/bluetape4k-image/issues/273)
- Milestone: `0.4.0`
- 작업 유형: Type A - Full Feature
- Repository: `bluetape4k/bluetape4k-image`
- Base: `origin/develop`
- Branch: `feat/issue-273-barcode-quickstart`

`bluetape4k-images-barcode-api`와
`bluetape4k-images-barcode-zxing`은 provider-neutral barcode contract와 순수 JVM
ZXing 구현을 제공한다. 하지만 사용자가 실제 파일을 업로드하거나, 정상·빈 결과·손상
입력 contract를 HTTP로 재현할 수 있는 runnable example은 없다. 이 설계는 Spring Boot
4 기반의 독립 example module로 그 간극을 메운다.

## 2. 목표

1. 사용자가 multipart image를 업로드하고 barcode 결과를 JSON으로 받을 수 있게 한다.
2. 고정 local fixture로 성공, no-result, malformed-input 동작을 각각 재현한다.
3. Spring Bean은 provider-neutral `BarcodeReader`로 노출하고 ZXing 구현을 등록한다.
4. 업로드 byte, decoded dimension, content type을 제한하고 오류 응답에서 입력 내용을
   노출하지 않는다.
5. 실행, curl 호출, 예상 응답, provider capability boundary를 영어·한국어 문서로
   제공한다.
6. 신규 example의 settings, repo module map, Examples workflow, README locale 등록을
   함께 완료한다.

## 3. 비목표

- Ktor endpoint를 추가하지 않는다.
- 두 번째 barcode provider를 추가하거나 비교하지 않는다.
- 업로드 이미지를 저장하거나 S3, CDN, database와 연결하지 않는다.
- 인증·인가를 갖춘 production API를 설계하지 않는다.
- 기존 `spring-boot-image-api`의 storage/upload contract를 변경하지 않는다.
- barcode API/provider production public API를 변경하지 않는다.
- BOM, Maven publication, catalog version, benchmark 결과를 변경하지 않는다.

## 4. 검토한 접근법

### 4.1 독립 Spring Boot barcode module - 선택

`examples/spring-boot-barcode-api`를 만들고 upload endpoint와 세 fixture endpoint를
같은 extraction service에 연결한다.

장점:

- barcode extraction만 다루므로 새 사용자가 최소 dependency와 흐름을 이해하기 쉽다.
- 기존 image storage example과 lifecycle, validation, 문서가 섞이지 않는다.
- fixture와 MockMvc test가 module 내부에서 독립적으로 실행된다.

대가: 신규 Gradle module 등록과 Examples workflow matrix 갱신이 필요하다.

### 4.2 기존 `spring-boot-image-api` 확장 - 제외

기존 app에 barcode endpoint를 추가하면 module 수는 늘지 않지만 local storage,
thumbnail, upload validation과 barcode extraction이 한 quickstart에 섞인다. Issue #273의
focused extraction 목표와 맞지 않는다.

### 4.3 CLI 또는 fixture GET endpoint만 제공 - 제외

구현은 가장 작지만 실제 사용자가 자신의 이미지를 보내는 흐름을 보여주지 못한다.
사용자 결정에 따라 multipart POST를 핵심 경로로 포함한다.

### 4.4 upload endpoint만 제공 - 제외

실제 API 모양은 단순하지만 no-result와 malformed contract를 재현하려면 호출자가 별도
파일을 준비해야 한다. 고정 GET scenario는 문서와 진단에서 세 contract를 즉시 확인하게
해 준다.

## 5. 모듈과 의존성 경계

신규 non-published module의 Gradle project name은
`:spring-boot-barcode-api`, directory는 `examples/spring-boot-barcode-api`로 한다.

필수 dependency는 다음과 같다.

- `:bluetape4k-images-barcode-zxing`: ZXing provider와 transitive provider-neutral API
- Spring Boot web starter
- Kotlin Spring plugin과 Spring Boot plugin
- Spring Boot test, WebMvc test, `bluetape4k-junit5`

Application code는 `BarcodeReader`, `BarcodeOptions`, `BarcodeResult`,
`BarcodeException` 같은 API type을 사용한다. Configuration만
`ZxingBarcodeReader`를 생성한다. `com.google.zxing` type은 example source와 public
response에 나타나지 않는다. 외부 사용자를 위한 README dependency 예시는 API와 ZXing
artifact를 모두 명시하여 contract/provider 경계를 드러낸다.

## 6. 구성 요소

### 6.1 Application과 configuration

`SpringBootBarcodeApiApplication`이 app entrypoint다. 별도 configuration은 다음 Bean을
등록한다.

- `BarcodeExampleProperties`: 최대 encoded byte, decoded pixel, decoded side 제한
- `BarcodeReader`: `ZxingBarcodeReader` 구현
- `BarcodeExtractionService`: provider-neutral reader와 properties 사용
- `BarcodeExampleFixtures`: 고정 classpath fixture만 읽는 component

Properties 기본값은 encoded input 5 MiB, decoded pixels 16,777,216, decoded side
8,192다. Spring multipart limit도 이 경계를 강제하며, application service는
`MultipartFile.size`를 다시 검증한다.

`BarcodeExampleFixtures`는 세 고정 resource를 startup에 한 번 읽고 누락된 resource가
있으면 app startup을 실패시킨다. Request마다 classpath I/O를 반복하지 않으며 mutable
`ByteArray`를 외부에 공유하지 않는다.

### 6.2 Controller

`BarcodeApiController`는 request parsing과 HTTP response mapping만 담당한다. 실제
image validation과 extraction은 service에 위임한다. Endpoint는 모두
`/api/barcodes` 아래에 둔다.

| Method | Path | 목적 | 정상 status |
|---|---|---|---|
| `POST` | `/extract` | 사용자가 올린 multipart `file` 추출 | `200 OK` |
| `GET` | `/sample` | 고정 QR 성공 fixture 추출 | `200 OK` |
| `GET` | `/no-result` | 고정 blank PNG의 빈 결과 재현 | `200 OK` |
| `GET` | `/malformed` | 고정 invalid bytes의 오류 정규화 재현 | `400 Bad Request` |

GET endpoint는 production data API가 아니라 deterministic contract demonstration임을
README와 KDoc에 명시한다.

### 6.3 Extraction service

Service 흐름은 다음과 같다.

1. upload인 경우 non-empty, encoded size, allowlisted content type을 확인한다.
2. `probeImageDimensions`로 decode 전에 width, height, total pixels를 제한한다. ImageIO가
   WebP reader를 제공하지 않는 runtime에서는 bounded `readImageMetadataReport`의 WebP
   dimension을 fallback으로 사용한다.
3. dimension을 읽을 수 없거나 image decode가 실패하면
   `BarcodeException(MALFORMED_INPUT)`으로 정규화한다.
4. `immutableImageOf(bytes)`로 image를 만들고 provider-neutral `BarcodeReader`와
   default `BarcodeOptions`로 추출한다.
5. Provider-neutral `BarcodeResult`를 bounded HTTP DTO로 변환해
   `BarcodeExtractionResponse(count, results)`로 반환한다.

`MultipartFile.bytes` 읽기는 `Dispatchers.IO`, dimension probe와 image decode 및 ZXing
호출은 `Dispatchers.Default`에서 수행한다. `ZxingBarcodeReader`는 호출마다 ZXing
reader state를 만들기 때문에 singleton Spring Bean이 request 간 mutable decoder state를
공유하지 않는다. Coroutine cancellation은 broad catch로 변환하지 않고 그대로 전파한다.

### 6.4 Response와 오류 handler

성공과 no-result는 같은 response shape를 사용한다.

```json
{
  "count": 1,
  "results": [
    {
      "text": "bluetape4k-barcode-quickstart",
      "format": "QR_CODE",
      "provider": "ZXing"
    }
  ]
}
```

Barcode가 없으면 `200 OK`, `count: 0`, `results: []`를 반환한다. No-result는 오류로
승격하지 않는다. HTTP result DTO는 `text`, provider-neutral `format`, provider name만
노출한다. Library `BarcodeResult`를 Jackson에 직접 넘기지 않으며 `includeRawBytes`도
활성화하지 않아 uploaded payload byte, backend metadata, result point가 JSON에 포함되지
않는다.

오류 response는 안정된 `error`, `reason`, `message` field를 가진다.

```json
{
  "error": "malformed_input",
  "reason": "MALFORMED_INPUT",
  "message": "The uploaded file is not a decodable image."
}
```

HTTP mapping은 다음과 같다.

| 조건 | Status | Error code |
|---|---:|---|
| 빈 file | `400` | `empty_input` |
| 허용하지 않은 media type | `415` | `unsupported_media_type` |
| encoded byte 또는 multipart limit 초과 | `413` | `payload_too_large` |
| 손상되었거나 dimension을 읽을 수 없는 image | `400` | `malformed_input` |
| 요청한 format을 provider가 지원하지 않음 | `400` | `unsupported_format` |
| provider unavailable | `503` | `provider_unavailable` |
| 그 밖의 barcode decode failure | `500` | provider-neutral reason의 lowercase |

오류 message는 file bytes, local path, stack trace를 반환하지 않는다.

## 7. Upload contract

`POST /api/barcodes/extract`는 `multipart/form-data`의 `file` part 하나를 받는다.
허용 media type은 `image/png`, `image/jpeg`, `image/webp`다. File extension은 신뢰하지
않으며 media type allowlist 뒤에 실제 encoded header/dimension과 decode 가능성을 다시
검증한다.

이 quickstart는 format filter나 `tryHarder` query parameter를 노출하지 않는다. Empty
`BarcodeOptions.formats`를 사용하여 ZXing이 지원하는 format을 요청하고, README에서
검증된 QR Code/Code 128 범위와 broader ZXing capability가 동일하지 않을 수 있음을
명시한다. 옵션 API는 향후 별도 issue가 없으면 추가하지 않는다.

Endpoint는 인증 없이 로컬에서 실행되는 example이다. README는 internet-facing 배포 전
인증·인가, rate limiting, request logging 정책, malware scanning, 운영용 resource limit이
추가로 필요하다고 경고한다.

## 8. Fixture contract

Module은 benchmark directory를 runtime sourceSet으로 참조하지 않고 다음 resource를
자체 소유한다.

| Resource | Shape/content | Determinism guard | 기대 동작 |
|---|---|---|---|
| `barcodes/qr.png` | 220x220 QR PNG | ZXing 3.5.4 `QR_CODE` writer로 한 번 생성한 byte와 SHA-256을 test에 고정 | payload `bluetape4k-barcode-quickstart`, `QR_CODE` 한 건 |
| `barcodes/no-result.png` | 220x220 white RGB PNG | `86aad41769423ad85a979fefe109d00829044a1eba5d891547499413e3d9ff2b` | 빈 목록 |
| `barcodes/malformed.bin` | exact ASCII `not-an-image` | `f2e2c6db1745cc40df646dc40c385487c36e4ceb3f1d5c8d6ad1f7620af1ebae` | `MALFORMED_INPUT` |

No-result PNG는 issue #272에서 hash와 generation provenance가 검증된 fixture를
복제한다. QR PNG는 quickstart 전용 payload로 생성한다. 두 PNG 모두 복제 또는 생성
후에는 example module resource가 runtime owner다. Test는 module-local resource의 hash,
dimensions, payload 또는 empty result를 고정한다. Fixture loader는 enum에 선언된 세
classpath path만 허용하며 사용자 입력으로 resource path를 조합하지 않는다.

## 9. Test 전략

모든 behavior는 RED/GREEN 순서로 구현한다.

### 9.1 Service test

- QR fixture가 정확한 payload, format, ZXing provider를 반환한다.
- blank fixture가 empty result를 반환한다.
- invalid bytes와 dimension probe failure가 `MALFORMED_INPUT`이 된다.
- encoded size, decoded side, decoded pixels 제한을 각각 거부한다.
- PNG, JPEG, WebP input의 dimension guard와 decode 경로를 각각 검증한다.
- cancellation을 일반 barcode failure로 바꾸지 않는다.

### 9.2 MockMvc integration test

- multipart QR upload는 `200`, `count: 1`, expected provider-neutral JSON이다.
- multipart no-result PNG는 `200`, `count: 0`, empty results다.
- 유효한 JPEG와 WebP multipart는 허용되고 동일 extraction response contract를 사용한다.
- malformed upload는 sanitized `400 malformed_input`이다.
- empty upload, unsupported media type, oversized upload의 status/code를 검증한다.
- 세 GET fixture endpoint가 upload와 같은 service contract를 재현한다.

Spring endpoint가 `suspend`이면 기존 Spring Boot example pattern처럼 async request를
dispatch하여 최종 response를 검증한다. 신규 module 규칙에 따라
`junit-platform.properties`와 `logback-test.xml`을 포함한다.

## 10. 문서와 시각 자료

신규 module에 영어 `README.md`와 자연스러운 한국어 `README.ko.md`를 함께 만들고 다음을
source-equivalent하게 유지한다.

- API/provider dependency와 Spring Bean registration
- `bootRun` command
- 네 endpoint의 curl command와 예상 status/JSON
- PNG/JPEG/WebP upload allowlist와 size/dimension limit
- ZXing의 검증된 QR/Code 128 범위, no-result contract, production deployment 경고
- scenario, architecture, sequence diagram

새 diagram source와 PNG는 `docs/images/readme-diagrams/`에 두고 두 README가 같은
English-label asset을 사용한다. Root `README.md`/`README.ko.md`의 Examples 및 barcode
section과 `images-barcode-zxing/README.md`/`README.ko.md`에서 새 quickstart를 link한다.
Chart는 측정값이나 series가 없는 작업이므로 N/A다.

## 11. Module registration과 repository hazard

Module 추가 시 다음 chain을 함께 검증한다.

- `settings.gradle.kts` include와 project directory mapping
- repo-local `AGENTS.md` module table과 command
- root README locale의 Examples link
- provider README locale의 runnable quickstart link
- `.github/workflows/Examples.yml` PR/daily matrix의
  `:spring-boot-barcode-api:test`
- `./gradlew projects`

`Examples.yml`은 `examples/**`와 barcode module path를 PR에서 감시하고 daily schedule도
실행하므로 이 non-container example의 CI와 nightly 역할을 함께 담당한다. Main
`ci.yml`과 `nightly-tests.yml`에 production-module job을 추가하지 않는다.

`examples/**`는 root build에서 non-published module로 분류된다. 따라서 Maven
publication, BOM/catalog coordinate, Kover artifact와 Codecov aggregation은 N/A다.
Production barcode artifact나 dependency version이 바뀌지 않으므로 benchmark evidence도
N/A다. 이를 `./gradlew projects`, publication task inspection, workflow diff로 증명한다.

Native/JNI, libvips, OCR, Docker, Testcontainers는 이 pure-JVM Spring/ZXing example의
실행 경로에 없으므로 heavyweight verification 대상이 아니다.

## 12. Compatibility, 운영, rollback

- 기존 public API와 artifact coordinate는 바뀌지 않는다.
- 신규 app은 state와 persistence가 없으며 restart 후 복구할 data가 없다.
- Error response와 startup log만 example-level diagnostics로 제공한다. Actuator, metrics,
  distributed tracing은 production 운영 기능이므로 포함하지 않는다.
- 배포 migration은 없다. 문제가 생기면 settings, Examples matrix, docs link와 신규
  directory를 함께 되돌리면 된다.
- Default port는 Spring Boot 기본 `8080`을 사용하고 README에서 override 방법을
  안내한다.

## 13. Acceptance criteria

- `./gradlew :spring-boot-barcode-api:bootRun`으로 app이 시작된다.
- `POST /api/barcodes/extract`가 PNG/JPEG/WebP multipart upload를 제한된 크기로 받는다.
- QR upload와 `/sample`은 expected QR payload와 provider-neutral ZXing result를 반환한다.
- Valid blank image upload와 `/no-result`는 `200`, count 0, empty results를 반환한다.
- Malformed upload와 `/malformed`는 sanitized `400 MALFORMED_INPUT`을 반환한다.
- Size, dimensions, media type, empty input negative contract가 test로 고정된다.
- 네 endpoint가 동일 `BarcodeExtractionService`를 사용한다.
- Settings, AGENTS, Examples workflow, root/provider/example README locale가 등록된다.
- Diagram asset이 render 검증되고 영어·한국어 README가 같은 asset을 사용한다.
- Targeted tests, example workflow-equivalent test, repository static checks와
  `git diff --check`가 통과한다.
- 최신 spec review가 P0=0, P1=0으로 수렴한다.

## 14. Definition of Done

- 승인된 spec과 implementation plan이 review 후 commit되어 있다.
- TDD evidence와 module registration evidence가 남아 있다.
- 새 module test와 기존 barcode API/provider test가 통과한다.
- English/Korean README parity와 diagram visual QA가 완료된다.
- Type A lesson과 final code review evidence가 commit된다.
- Issue-linked PR의 assignee, milestone, labels, final DoD body가 live 상태와 일치한다.
- Exact PR head CI와 review가 green이고 P0=0, P1=0이다.
- Merge-ready 보고 뒤 fresh 승인을 받기 전에는 merge하지 않는다.
- Merge 후 local `develop` sync와 merged worktree/branch cleanup을 자동 수행한다.
