# Issue #487 이미지 probe 예외 경계

**관련 이슈**: #487
**영향 모듈**: `spring-boot-image-intelligence-api`

## 배경

`ImageUploadQualifier`가 dimension·metadata probe의 모든 `Exception`을
`null`로 바꾸고 `image_not_decodable`로 처리했다. malformed 입력과 parser·I/O
내부 결함이 같은 400 응답으로 합쳐져 원인과 운영 관측이 사라지는 경계였다.

## 결정

probe 단계에서는 `CancellationException`을 먼저 재전파한다. 기본 ImageIO
adapter가 입력 형식 오류로 명시적으로 감싼 `MalformedImageProbeException`만
기존 `image_not_decodable` fallback으로 남긴다. 임의의 `IIOException`이나
`IllegalArgumentException`을 probe 경계에서 직접 삼키지 않으며, 그 밖의 예외는
`image_probe_failed`로 감싼다. 내부 원인은 예외 cause와 low-cardinality stage
로그에만 보존하며 HTTP 응답은 고정된 detail과 500 status로 정제한다.

## 결과

dimension probe와 metadata fallback의 실패 의미가 분리됐다. strict metadata
reader는 parse/size-limit을 명시적 malformed 결과로, I/O를 내부 probe 실패로
분류한다. malformed 입력의 기존 계약은 유지되고, 내부 probe 결함은 decode 전에
관측 가능한 실패로 종료된다. 취소는 예외 변환 없이 호출자까지 전달된다.

## 검증

- RED: 예기치 않은 dimension·metadata 예외가 `image_not_decodable`로 축약되고
  handler가 400을 반환하는 회귀를 확인
- `ImageUploadQualifierTest`의 내부 실패·malformed·cancellation·로그 redaction
  회귀 테스트와 기본 adapter truncated-input 회귀 테스트
- `ImageIntelligenceExceptionHandlerTest`의 sanitized 500/problem detail 테스트
- `./gradlew :spring-boot-image-intelligence-api:test --no-daemon`
- `git diff --check`

## 향후 방지책

새로운 이미지 입력 probe는 broad catch로 입력 오류와 내부 결함을 합치지 않는다.
adapter 경계에서만 typed malformed 결과를 만들고, `runProbe`는 그 결과와 임의
예외를 구분해야 한다. 항상 취소를 먼저 재전파하고, 예측 가능한 입력 오류만
명시적으로 분류한다.
내부 오류를 HTTP로 전달할 때는 원문 message·path·payload를 응답에 복사하지 않고
고정 reason code와 운영 로그의 원인 연결을 함께 회귀 테스트로 고정한다.
