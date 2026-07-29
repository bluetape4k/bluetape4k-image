# 교훈 — Issue 172 Spring Boot OCR example (2026-06-06)

**관련 이슈**: #172
**영향 모듈**: `examples/spring-boot-ocr-api`, `bluetape4k-images-ocr`

## L1: Native OCR 빠른 시작 테스트는 engine을 주입해야 한다

### 문제

Spring Boot OCR 예제는 실제 실행 시 host Tesseract와 traineddata가 필요하지만,
일반 Examples CI가 이 native runtime을 항상 제공한다고 가정하면 예제가
불안정해진다.

### 교훈

HTTP 연결 예제는 `OcrEngine`을 주입 가능하게 두고 기본 runtime은
`TesseractOcrEngine`으로 사용하되 테스트에서는 가짜 engine을 `@Primary`로 주입한다.
이렇게 하면 controller/service 연결과 오류 변환을 검증하면서 native OCR smoke
테스트는 `images-ocr`의 opt-in gate에 남길 수 있다.

### 검증

- `./gradlew :spring-boot-ocr-api:test --no-configuration-cache --no-daemon`
- 테스트 XML: `tests=3 skipped=0 failures=0 errors=0`

## L2: Host 경로는 요청 매개변수가 아니라 application 설정에 둔다

### 문제

초기 spec은 `tessdataPath`를 요청 매개변수로 받을 수 있게 열어 두었다. 빠른 시작
예제라도 호출자가 host 경로를 제어하는 패턴을 문서화하면 보안상 나쁜 예제가 된다.

### 교훈

OCR traineddata 위치는 `example.ocr.tessdata-path` 같은 application 설정으로
제한하고 endpoint는 언어 선택과 image upload만 받게 한다.

### 검증

- Step 2-R spec review에서 P1로 분류한 뒤 spec/plan에 반영했다.
- README에 endpoint가 요청별 tessdata 경로를 받지 않는다고 명시했다.

## L3: Example 모듈 워크플로 범위는 의존성 trigger를 따라야 한다

### 문제

새 예제가 `images-ocr`에 의존하면 `examples/**` 변경뿐 아니라 `images-ocr/**` 변경도
Examples 워크플로를 다시 실행해야 한다.

### 교훈

새 example 모듈을 추가할 때 matrix 항목만 넣지 말고 예제가 의존하는 library 모듈의
경로 filter도 함께 확인한다.

### 검증

- `.github/workflows/Examples.yml`의 push/PR 경로 filter에 `images-ocr/**`를 추가했다.
- `actionlint .github/workflows/Examples.yml` 통과.

## L4: CI installer 실패는 원인 근거를 별도로 확인해야 한다

### 문제

PR CI의 `Secret Scan (gitleaks)`가 실패했지만 원인은 secret 탐지가 아니라 인증하지
않은 GitHub release 조회가 403 또는 빈 태그로 깨진 설치 단계였다.

### 교훈

보안 scan 도구 설치에는 인증된 API 조회, 재시도, 검증된 대체 버전이 있어야 한다.
CI gate 실패를 보고할 때도 scan 결과 실패와 installer 실패를 나눠 판단해야 한다.

### 검증

- `actionlint .github/workflows/ci.yml` 통과.
- 인증된 gitleaks release/download smoke: `v8.30.1` tarball을 내려받고 `gitleaks`
  항목을 확인해 통과.
