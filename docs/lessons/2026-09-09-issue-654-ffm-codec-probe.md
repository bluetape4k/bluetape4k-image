# Issue #654: FFM codec probe 교훈

## 배경

Boolean probe는 `vips_type_find`의 정상적인 `0` 반환과 native 예외를 모두 `false`로 축약해 capability report에서 실제 미지원과 환경·linkage 실패를 구분하지 못하게 했다.

## 결정

- 내부 `FfmVipsCodecProbeResult`를 `Available`/`Unavailable`/`Failed`로 분리한다.
- `vips_type_find`의 `0`은 정상적인 `Unavailable`이며, lookup 중 `Exception`은 `Failed`다.
- `classifyFfmVipsCodecProbe` 같은 순수 seam으로 `true`/`false`/`Exception`/`Error` 경계를 직접 테스트한다.
- `Error`는 fatal JVM/linkage 신호일 수 있으므로 catch하지 않는다.
- report는 `Failed`를 backend-neutral `VipsCodecOperationCapability.UNKNOWN`과 고정된 safe reason으로 변환한다. 내부 diagnostic은 공개하지 않는다.
- 실제 format gate는 `Available`만 true로 해석해 fail-closed를 유지한다.
- public `VipsCodecSupport.UNKNOWN` 계약을 재사용하고 FFM 타입을 public API로 올리지 않는다.

## 재사용할 패턴

1. native Boolean 반환값을 즉시 public capability로 매핑하지 말고 정상 부재·탐색 실패·fatal을 먼저 분리한다.
2. native 호출은 confined resource scope 안에 두고 순수 classifier seam으로 예외 경계를 검증한다.
3. 공개 report reason은 고정된 안전 문구를 사용하고 raw exception/path/env를 버린다.
4. report의 진단 상태와 실행 gate의 fail-closed Boolean을 분리한다.
5. 내부 API 변경 뒤에도 production ABI와 public backend-neutral model을 별도로 검증한다.

## 운영 경계

배포 환경의 실제 codec 지원은 capability report만으로 충분하지 않다. report가 `UNKNOWN`이면 caller-provided sample을 같은 호스트에서 `smokeTestCodec(...)`으로 확인해야 한다. hosted CI가 stacked branch에서 생성되지 않는 경우 local GREEN을 remote PASS로 해석하지 않는다.
