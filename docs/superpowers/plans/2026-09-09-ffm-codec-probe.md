# FFM Codec Probe 상태 분리 구현 계획

> **목표:** vips-ffm native codec operation의 정상적인 부재와 탐색 실패를 capability report에서 구분하고, 내부 probe가 fatal JVM error를 무분별하게 숨기지 않도록 한다.

## 범위와 계약

- `images-vips-java25` 내부 probe만 구조화한다. `VipsRuntime`의 public 메서드와 `images-vips-api` ABI는 변경하지 않는다.
- `vips_type_find`가 `0`을 반환하면 `UNAVAILABLE`, non-zero를 반환하면 `AVAILABLE`로 보고한다.
- native 탐색 중 `Exception`이 발생하면 `UNKNOWN`으로 보고하며, report reason은 고정된 안전 문구만 사용한다.
- `Error`와 같은 fatal JVM 오류는 catch하지 않고 호출자에게 전파한다.
- 실제 decode/encode gating은 probe 실패를 fail-closed(`false`)로 처리하되, capability report에는 `UNKNOWN`을 보존한다.

## 구현 단계

1. `FfmVipsCodecProbeResult` sealed result와 `inspectOperation` seam을 추가하고, 기본 probe의 예외 경계를 `Exception`으로 제한한다.
2. `FfmVipsRuntime.codecCapabilityReport()`가 result를 `AVAILABLE`/`UNAVAILABLE`/`UNKNOWN`으로 매핑하도록 변경한다.
3. true/false/failed probe 대역, 민감한 diagnostic 비노출, fatal error 전파를 테스트한다.
4. API/Java 25 README에 세 상태와 안전한 diagnostic 계약을 반영한다.
5. FFM targeted/full 테스트, API 테스트, production ABI, bytecode, detekt, diff 검증을 순차 실행하고 exact commit을 독립 리뷰한다.

## 검증 명령

```bash
./gradlew :bluetape4k-images-vips-java25:test --tests 'io.bluetape4k.images.vips.java25.FfmVipsCodecCapabilityTest'
./gradlew :bluetape4k-images-vips-java25:test
./gradlew :bluetape4k-images-vips-api:test
./gradlew :bluetape4k-images-vips-api:checkKotlinAbi :bluetape4k-images-vips-java25:checkKotlinAbi
./gradlew checkProductionAbi
./gradlew detekt
git diff --check
```

## 종료 조건

- capability report가 정상 부재와 탐색 실패를 각각 `UNAVAILABLE`/`UNKNOWN`으로 보고한다.
- raw native exception text, 경로, 환경 값이 report reason에 포함되지 않는다.
- public API/ABI가 유지되고 관련 테스트와 독립 리뷰가 exact head에서 `CLEAR`다.
- hosted CI는 branch filter로 자동 실행되지 않으면 `N/A/PENDING`으로 명시하며 green으로 주장하지 않는다.
