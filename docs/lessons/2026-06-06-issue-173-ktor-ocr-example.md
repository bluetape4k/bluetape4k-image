# Issue 173 Ktor OCR example 교훈 (2026-06-06)

**관련 이슈**: #173
**영향 모듈**: `examples/ktor-ocr-api`, `bluetape4k-images-ocr`

## L1: Ktor JSON response DTO companion은 serializer 접근 가능해야 한다

### 문제

`@Serializable` DTO에 `private companion object`를 두면 Ktor ContentNegotiation이
companion serializer를 reflection으로 찾는 과정에서 `IllegalAccessException`이 발생할
수 있다. 이번 작업의 happy path route test는 500으로 실패했고, test XML의
`system-err`에서 serializer 접근 실패가 확인됐다.

### 교훈

Ktor response DTO가 `@Serializable`이면 companion object 자체는 serializer 접근이
가능해야 한다. `serialVersionUID`는 `private const val`로 유지하되, companion object를
private으로 숨기지 않는다.

### 검증

- `./gradlew :ktor-ocr-api:test --no-configuration-cache --no-daemon`
- `recognizes uploaded image with parsed languages`가 200과 JSON body를 검증.

## L2: Query string의 `+`는 공백으로 decode될 수 있다

### 문제

Spec과 README는 `languages=eng+kor`를 지원한다고 했지만, Ktor query parameter는 URL
query의 `+`를 공백으로 decode한다. 초기 parser가 comma/plus만 split해서 실제 값이
`["eng kor"]`로 남았다.

### 교훈

HTTP query에서 `+` 기반 구분자를 문서화한다면 parser는 공백 구분도 허용해야 한다.
또는 문서에 `%2B` encode를 요구해야 한다. Quickstart에서는 사용성을 위해 comma, plus,
whitespace를 모두 separator로 지원한다.

### 검증

- Test request `POST /api/ocr?languages=eng+kor`
- Response body와 fake engine options가 모두 `["eng", "kor"]`임을 검증.

## L3: Ktor OCR example test는 fake engine을 주입해야 한다

### 문제

실제 OCR 실행은 host Tesseract와 traineddata 설치 상태에 의존한다. 일반 Examples CI에서
native runtime을 요구하면 빠른 예제 검증이 환경 의존적으로 변한다.

### 교훈

Ktor 예제도 Spring Boot OCR 예제와 동일하게 route wiring은 fake `OcrEngine`으로 검증하고,
실제 native runtime smoke는 `images-ocr`의 opt-in 검증에 둔다. 이를 위해 route 구성 함수는
기본값으로 `TesseractOcrEngine`을 사용하되 테스트에서 engine을 주입할 수 있어야 한다.

### 검증

- `./gradlew :ktor-ocr-api:test --no-configuration-cache --no-daemon`
- 5개 route test가 host Tesseract 없이 통과.

## L4: `suspendExtractText`가 이미 blocking boundary를 소유한다

### 문제

초기 서비스 구현은 `withContext(Dispatchers.IO)` 안에서 다시
`ImmutableImage.suspendExtractText`를 호출했다. `suspendExtractText` 자체가 dispatcher
parameter와 `Dispatchers.IO` 기본값을 갖고 있어 중복 dispatcher hop이었다.

### 교훈

Example layer는 `images-ocr` suspend API의 contract를 신뢰하고, 추가 dispatcher boundary를
겹치지 않는다. 필요한 경우에는 `suspendExtractText(..., dispatcher = ...)`를 직접 넘긴다.

### 검증

- 중복 boundary 제거 후 `./gradlew :ktor-ocr-api:test --no-configuration-cache --no-daemon`
  PASS.

## L5: 새 example module은 불필요한 snapshot 직접 의존을 피한다

### 문제

PR CI의 `Test / ktor-ocr-api`가 compile 단계에서 실패했다. 원인은 test 실패가 아니라
신규 module이 `bluetape4k-ktor-core:1.11.0-SNAPSHOT`을 직접 resolve하면서 Central
snapshot metadata 403을 만난 것이었다.

### 교훈

새 example이 꼭 repo 외부 bluetape4k snapshot artifact를 직접 써야 하는지 먼저 확인한다.
Ktor JSON 설치와 error DTO처럼 example-local로 충분한 부분은 official Ktor dependency와
local DTO로 유지해 CI의 snapshot resolution surface를 줄인다.

### 검증

- `./gradlew :ktor-ocr-api:test --no-configuration-cache --no-daemon` PASS.
- `./gradlew :ktor-ocr-api:dependencies --configuration compileClasspath --no-configuration-cache --no-daemon`
  결과에서 신규 module의 direct `bluetape4k-ktor-core` 의존이 제거됨.
