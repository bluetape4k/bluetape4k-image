# Issue #654: FFM codec 탐색 실패 상태 분리 리뷰

## 검토 범위와 기준

- 저장소: `bluetape4k/bluetape4k-image`
- Epic: #656
- 대상 이슈: #654
- 기준 base: `feat/issue-653-vips-wait-policy` (`97fe5a308c2eefa399bbb303893f47b45b5a8df6`)
- 최종 구현 exact head: `ba3ce3a7a00ee38293ce86bae44afd6ee014775d`
- 대상 모듈: `images-vips-java25`, `images-vips-api`
- 승인된 범위: native codec operation의 정상 부재와 탐색 실패를 구분하되 public backend-neutral API/ABI를 유지하고, fatal JVM error는 숨기지 않음

이번 receipt는 exact head의 source, caller, tests, API/ABI, docs, CI, design-risk를 7개 관점으로 재검토한다. `UNKNOWN`은 probe 실패를 의미하며 실제 decode/encode 경로는 fail-closed로 유지한다.

## 7-Tier 증거

| Tier | 확인 내용 | 결과 |
| --- | --- | --- |
| T1 정확성 | `vips_type_find != 0`은 `Available`, `0`은 `Unavailable`, `Exception`은 `Failed`로 분류하고 report에서 각각 `AVAILABLE`, `UNAVAILABLE`, `UNKNOWN`으로 매핑한다. | PASS |
| T2 보안·자원 | report는 내부 diagnostic을 버리고 고정된 안전 문구만 사용한다. native `Arena`는 호출마다 `use`로 닫으며 raw 경로·환경 값·exception text를 노출하지 않는다. | PASS |
| T3 설계·API | 새 probe/result/classifier는 `internal`이다. 기존 `VipsCodecSupport.UNKNOWN`과 `VipsCodecOperationCapability.unknown()`을 재사용했으며 public source/API/ABI를 변경하지 않았다. | PASS |
| T4 성능 | capability report는 load/save operation을 각각 한 번만 inspect하고, 실행 gating은 기존 Boolean 경로를 통해 필요한 시점에 fail-closed한다. 새 dependency나 native global state를 추가하지 않았다. | PASS |
| T5 안정성·운영 | `Available`만 실제 operation 지원으로 허용한다. `Exception`은 진단 가능한 `UNKNOWN`으로 보존하고 `Error`는 전파하여 fatal linkage 문제를 숨기지 않는다. | PASS |
| T6 테스트·CI | true/false/Exception/Error classifier seam, report mapping, 민감 정보 비노출, malformed smoke 결과를 검증했다. FFM 전체와 API 테스트는 통과했으며 stacked branch hosted CI는 branch filter상 자동 생성 범위가 아니다. | LOCAL PASS / REMOTE N/A |
| T7 문서·호출자 | `images-vips-api` 및 `images-vips-java25` EN/KR README에 `UNKNOWN` 의미, 안전한 reason, fail-closed와 fatal error 경계를 반영했다. `FfmVipsFormatSupport` 호출자는 기존 Boolean gate를 유지한다. | PASS |

## 구현 및 호출자 근거

- `FfmVipsCodecProbeResult`는 `Available`, `Unavailable`, `Failed` 세 상태를 내부 sealed result로 표현한다.
- `classifyFfmVipsCodecProbe`는 native lookup 결과를 분류하며 `Exception`만 `Failed`로 바꾸고 `Error`는 그대로 전파한다.
- `DefaultFfmVipsCodecProbe`는 confined `Arena`에서 `vips_type_find`를 수행하고 lookup 결과를 classifier에 위임한다.
- `FfmVipsRuntime.codecCapabilityReport()`는 load/save result를 두 AVIF/HEIC capability에 재사용하며 `Failed`를 고정된 safe reason의 `UNKNOWN`으로 보고한다.
- `FfmVipsFormatSupport`는 `supportsOperation()`의 `Available` 전용 Boolean 결과를 사용하므로 probe 실패 시 실제 decode/encode가 계속 fail-closed다.
- `images-vips-api` public model의 `UNKNOWN` enum/constructor는 변경하지 않았다.

## 결정적 회귀 테스트

최신 exact head에서 다음을 확인했다.

- `FfmVipsCodecCapabilityTest`: 6/6 PASS를 `--rerun-tasks`로 3회 연속 실행
  - `true` → `Available`
  - `false` → `Unavailable`
  - `Exception` → `Failed(SAFE_FAILURE_REASON)`
  - `Error` → 호출자 전파
  - report `UNKNOWN` 매핑 및 secret path 비노출
  - malformed smoke 결과의 sanitized reason
- FFM 전체 모듈: 93 PASS
- `images-vips-api` 전체: 23 PASS

## ABI·정적·운영 검증

- `./gradlew checkProductionAbi`: `Production ABI validated: 10 published JVM modules`
- Java 25 production class 확인: 최대 class major `69`
- `./gradlew detekt`: `BUILD SUCCESSFUL`, root aggregate `NO-SOURCE`
- `git diff --check`: PASS
- bluetape-workflow mutation check: `ok=true`, run `20260908T145834Z-ed223197`, sequence 67
- hosted PR CI: workflow push/pull_request branch filter가 `develop/main`만 대상으로 하므로 stacked feature branch 자동 check는 생성되지 않는 scope이다. workflow dispatch는 별도 승인 없이는 실행하지 않는다.

## 독립 리뷰 결과

| reviewer | 범위 | 실행 모델/노력 | 결과 |
| --- | --- | --- | --- |
| code-reviewer | probe result, runtime mapping, fail-closed gate, diagnostics, tests | `gpt-5.6-luna / max` | P0=0, P1=0, P2=0, `CLEAR` (최종 exact head 재검토 진행) |
| architect | API/ABI 경계, Arena lifecycle, Exception/Error 분류, seam adequacy | `gpt-5.6-sol / high` | P0=0, P1=0, P2=0, `CLEAR` |

초기 `84499a0` 리뷰에서 지적된 P1 테스트 공백은 `ba3ce3a`의 pure classifier seam과 직접적인 네 상태 회귀 테스트로 해소했다.

## 잔여 위험과 범위 밖 검증

- 실제 배포 호스트별 libvips plugin/linkage 조합은 local host 검사만으로 모두 대체하지 않는다. `UNKNOWN`과 smoke test를 배포 진단 경계로 유지한다.
- FFM hosted CI, workflow dispatch, release/tag/publication, merge는 이 receipt의 실행 범위가 아니다.
- PR이 생성된 뒤에도 remote check가 비어 있으면 branch filter scope evidence이지 PASS가 아니다.

## DoD Status

- [x] 정상 부재와 native 탐색 실패를 `UNAVAILABLE`/`UNKNOWN`으로 구분했다.
- [x] `true`/`false`/`Exception`/`Error` seam을 직접 테스트하고 fatal error를 숨기지 않았다.
- [x] backend-neutral public API/ABI를 유지하고 민감한 diagnostic 노출을 제한했다.
- [x] FFM capability/전체 모듈 및 vips-api 테스트, ABI, bytecode, detekt, diff, workflow mutation 검증을 수행했다.
- [x] 독립 리뷰에서 발견된 P1 테스트 공백을 exact final head에 반영했다.
- [ ] PR hosted CI 결과 확보 — stacked branch filter상 자동 check가 생성되지 않는 범위이며 별도 dispatch 승인이 필요하다.

상태: **DONE (로컬 검증·리뷰 게이트 완료; remote CI는 N/A/PENDING)**
