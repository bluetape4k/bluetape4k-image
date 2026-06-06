# Lessons Learned — Issue 172 Spring Boot OCR Example (2026-06-06)

**관련 이슈**: #172
**영향 모듈**: `examples/spring-boot-ocr-api`, `bluetape4k-images-ocr`

## L1: Native OCR quickstart tests should inject the engine

### 문제

Spring Boot OCR 예제는 실제 실행 시 host Tesseract와 traineddata가 필요하지만,
일반 Examples CI가 이 native runtime을 항상 갖고 있다고 가정하면 예제가
불안정해진다.

### 교훈

HTTP wiring 예제는 `OcrEngine`을 주입 가능하게 두고, 기본 runtime은
`TesseractOcrEngine`으로 두되 테스트에서는 fake engine을 `@Primary`로 주입한다.
이렇게 하면 controller/service wiring과 error mapping은 검증하면서 native OCR smoke
test는 `images-ocr`의 opt-in gate에 남길 수 있다.

### 검증

- `./gradlew :spring-boot-ocr-api:test --no-configuration-cache --no-daemon`
- Test XML: `tests=3 skipped=0 failures=0 errors=0`

## L2: Host paths belong in app configuration, not request parameters

### 문제

초기 spec은 `tessdataPath`를 request parameter로 받을 수 있게 열어 두었다. Quickstart라
하더라도 caller-controlled host path 패턴을 문서화하면 보안적으로 나쁜 예제가 된다.

### 교훈

OCR traineddata 위치는 `example.ocr.tessdata-path` 같은 application configuration으로
제한하고, endpoint는 language selection과 image upload만 받게 한다.

### 검증

- Step 2-R spec review에서 P1로 분류 후 spec/plan에 반영.
- README는 endpoint가 request-level tessdata path를 받지 않는다고 명시.

## L3: Example module workflow coverage must follow dependency triggers

### 문제

새 예제가 `images-ocr`에 의존하면 `examples/**` 변경뿐 아니라 `images-ocr/**` 변경도
Examples workflow를 다시 실행해야 한다.

### 교훈

새 example module을 추가할 때 matrix entry만 넣지 말고, 예제가 의존하는 library module
path filter도 함께 확인한다.

### 검증

- `.github/workflows/Examples.yml`에 `images-ocr/**` push/PR path filter 추가.
- `actionlint .github/workflows/Examples.yml` PASS.
