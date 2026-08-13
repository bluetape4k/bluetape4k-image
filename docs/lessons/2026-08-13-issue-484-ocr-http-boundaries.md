# Issue #484 OCR HTTP 경계와 bounded input

**관련 이슈**: #484
**영향 모듈**: `images-ktor`, `ktor-ocr-api`, `spring-boot-ocr-api`

## 배경

OCR native runtime과 image writer가 만든 예외 message에는 `tessdataPath`, host
경로, native codec 진단 문자열이 포함될 수 있었다. Ktor thumbnail route는
`maxInputBytes + 1`을 그대로 계산해 `Long.MAX_VALUE`에서 `Long.MIN_VALUE`로
wrap되므로, 정상적인 작은 upload도 음수 read limit으로 거부될 수 있었다.

## 결정

HTTP 경계에서는 validation detail과 내부 runtime failure를 구분한다. 요청 형식과
limit 위반은 기존 4xx detail을 유지하지만 `IOException`과 `OcrException`은 고정된
`Invalid image payload.` 또는 `OCR runtime is unavailable.`만 반환한다. 원본 예외와
cause는 `WARN` log에 연결해 운영 진단을 보존하고, response에는 exception message를
복사하지 않는다. Spring OCR에는 `IOException` 전용 400 handler도 추가해 framework
기본 오류 응답으로 빠지지 않게 했다.

bounded read-ahead는 `maxInputBytes`가 `Long.MAX_VALUE`여도 덧셈이 wrap되지 않도록
`Long.MAX_VALUE - 1`로 먼저 제한한 뒤 1을 더한다. 실제 payload 크기 검사는 기존
`ByteArray` 경계와 `maxInputBytes` 검사를 그대로 사용한다.

## 결과

세 OCR HTTP 경로에서 tessdata/host/native 내부 문자열이 public JSON에 노출되지
않는다. thumbnail route는 `Long.MAX_VALUE` 설정으로 작은 정상 upload를 처리하고,
0 설정은 construction 단계에서 거부한다. 기존 malformed image와 OCR runtime의
HTTP status 계약(400/503)은 유지한다.

## 검증

- `ImageThumbnailKtorRoutesTest`: 8개 통과
- `KtorOcrApiApplicationTest`: 11개 통과
- `SpringBootOcrApiApplicationTest`: 9개 통과
- path/native message redaction, `Long.MAX_VALUE`, 0 boundary, malformed payload 회귀 테스트
- RED 단계에서 overflow와 원문 노출을 확인한 뒤 GREEN 단계에서 고정 문구와 safe read를 확인
- `git diff --check`

## 놓친 점과 향후 방지책

초기 테스트는 generic OCR message만 비교해 경로 노출을 검증하지 못했다. HTTP
handler 테스트는 반드시 path/native 문자열을 포함한 fake exception을 사용하고, 응답에
그 문자열이 없는지 확인한다. `Long` bounded read는 `+ 1` 전에 `Long.MAX_VALUE`를
명시적으로 다루고, 최대값과 정확한 초과 경계를 함께 고정한다.

독립 reviewer가 확인한 `TesseractOcrEngine` 내부 broad `RuntimeException`의
`CancellationException` 재전파 보강은 이 HTTP 경계 변경의 write scope 밖에 있으므로
별도 후속 작업으로 남긴다. 현재 세 HTTP wrapper는 `CancellationException`을 잡지 않아
요청 취소를 변환하지 않는다.
