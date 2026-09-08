# Issue #655 benchmark CI 연결 7-Tier 리뷰

## 검토 범위와 기준

- 저장소: `bluetape4k/bluetape4k-image`
- Epic: #656
- 대상 이슈: #655
- 대상: `benchmark/images-benchmark`, `.github/workflows/ci.yml`, `.github/workflows/nightly-tests.yml`
- 기준 base: `e574435e8f7917e99a21df9d78401bb6119e4823` (`fix/issue-654-codec-probe`)
- 최종 구현 exact head: `6fecae1` (`ci/issue-655-benchmark-checks`)
- 승인된 범위: benchmark compile·native 불필요 unit/receipt 검증을 일반 CI와 nightly에 연결하고 required-check와 artifact 근거를 보존한다.
- 제외 범위: JMH 장시간 성능 측정, libvips/JNI native smoke, host Tesseract OCR 측정, workflow dispatch와 merge.

이번 receipt는 benchmark module의 source·caller·test·CI aggregation·artifact·운영 위험을 7개 관점으로 확인한다.

## 7-Tier 증거

| Tier | 확인 내용 | 결과 |
| --- | --- | --- |
| T1 정확성 | `benchmarkClasses`·`testClasses` compile 후 `test`와 세 receipt validator를 별도 호출한다. | PASS |
| T2 보안·자원 | hosted lane은 native/performance task를 호출하지 않고 `--max-workers=1`로 실행한다. 기존 receipt 내용만 읽고 외부 credential이나 artifact publish를 사용하지 않는다. | PASS |
| T3 설계·재사용 | 기존 `paths-filter`, module test job, `require_test`, JUnit upload 관례를 재사용하고 benchmark를 Kover aggregate에 억지로 포함하지 않는다. | PASS |
| T4 성능 | benchmark-only PR은 전용 lane만 활성화하며 JMH 측정과 native/OCR 측정은 명시적 별도 실행으로 남긴다. | PASS |
| T5 안정성·운영 | `ci-status`가 benchmark path/build-logic/workflow dispatch에서 skipped job을 required failure로 판정한다. nightly status도 benchmark job을 기다린다. | PASS |
| T6 테스트·CI | benchmark filter output, compile, 124개 unit test, OCR corpus/protocol·VIPS transform receipt, JUnit·receipt artifact 경로를 연결했다. YAML/actionlint와 local execution을 확인했다. | LOCAL PASS / REMOTE N/A |
| T7 문서·근거 | step summary에 수행 범위와 native/performance 제외를 기록하고 계획·lesson·PR `Closes #655`로 연결한다. | PASS |

## 구현 근거

- `ci.yml`은 `images-benchmark` path output과 `test-images-benchmark` job을 추가한다.
- `test-images-benchmark`는 Java 25에서 `benchmarkClasses`, `testClasses`, `test`, 세 receipt validator를 순차 실행한다.
- test/receipt 출력은 `test-results-images-benchmark` artifact와 `$GITHUB_STEP_SUMMARY`에 남긴다.
- `ci-status`의 기존 `require_test` 함수가 benchmark 변경·build-logic 변경·dispatch에서 결과를 required로 판정한다.
- `nightly-tests.yml`은 전체 nightly에서 동일한 검증 lane을 실행하고 `nightly-status` needs에 포함한다.
- 일반 build와 nightly build의 `-x :bluetape4k-images-benchmark:build` 제외는 유지하되 dedicated lane이 compile 공백을 닫는다.

## 결정적 로컬 검증

- `actionlint .github/workflows/ci.yml .github/workflows/nightly-tests.yml`: PASS
- Ruby YAML parse: 두 workflow PASS
- benchmark compile: `benchmarkClasses` + `testClasses` BUILD SUCCESSFUL
- benchmark unit test: `SUCCESS: Executed 124 tests`, BUILD SUCCESSFUL
- receipt: OCR benchmark, OCR protocol(24 fixtures), VIPS transform 모두 `Validated`, BUILD SUCCESSFUL
- `git diff --check`: PASS
- bluetape-workflow mutation-check: `ok=true`, sequence 67

첫 전체 테스트 시 중첩 Gradle TestKit의 Maven Central `bad_record_mac` 일시 오류로 1건이 실패했으나, 동일 테스트 재실행 1/1 PASS 후 전체 124건을 fresh 옵션으로 재실행해 PASS했다. 이는 코드 실패가 아닌 네트워크 재시도 근거이며 hosted CI에서는 별도 관찰한다.

## 독립 리뷰 및 잔여 위험

- exact implementation head `6fecae1`에서 workflow 조건, task 격리, required aggregation, artifact 보존을 독립 확인한다.
- architect: `P0=0, P1=0, P2=0`, `CLEAR`. benchmark filter/output, `ci-status` needs와 skip 판정, nightly graph, hosted task 격리를 확인했다.
- leader exact-diff review: `P0=0, P1=0, P2=0`; actionlint·dry-run·local task evidence와 workflow diff를 대조했다.
- code-reviewer dedicated lane은 응답 시간 제한으로 회수했으며 별도 판정 근거로 사용하지 않는다.
- stacked feature branch는 저장소 `ci.yml` push/pull_request filter(`develop`, `main`) 밖이므로 hosted check가 자동 생성되지 않을 수 있다. 이를 green으로 주장하지 않고 `N/A/PENDING`으로 기록한다.
- nightly lane은 일반 unit/receipt만 수행한다. native library, Tesseract, JMH 호스트별 결과는 이 PR의 성공 조건이 아니다.

## DoD Status

- [x] benchmark path filter와 dedicated compile·unit·receipt lane을 일반 CI에 연결했다.
- [x] required-check 집계와 JUnit·receipt artifact를 연결했다.
- [x] nightly lane과 status needs에 포함했으며 performance/native/OCR 측정을 실행하지 않는다.
- [x] local workflow/test/receipt evidence를 확보했다.
- [x] exact final head leader/architect review에서 P0/P1/P2를 확인했다.
- [ ] PR hosted CI 결과 — stacked branch filter상 자동 check가 생성되지 않으면 `N/A/PENDING`으로 기록한다.

상태: **IN PROGRESS (구현·로컬 검증 완료; exact-head review와 PR remote evidence 대기)**
