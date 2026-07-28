# Issue #273 Spring Boot Barcode Quickstart Plan 검토

## 범위

- Artifact: `docs/superpowers/plans/2026-07-14-issue-273-spring-barcode-quickstart-plan.md`
- Artifact 종류: plan
- Basis: approved issue #273 design, current Spring examples, barcode API/ZXing source와 tests, image dimension/metadata API, Examples workflow, repository module-registration rules
- 관점: performance, stability, security, operator/Ops, developer/API, user/caller, 이후 main-session integration 및 Step 3-R ordering review

이 세션의 native-agent interface는 필수 `agent_type` field를 노출하지 않고 여섯 개의 independent lane도 없다. `model-routing.md`에 따라 여섯 필수 lens는 별도 read-only main-session pass로 실행했다. role label은 지어내지 않았고 main session이 최종 severity normalization을 수행했다.

## 초기 발견 사항과 수정

| Priority | 관점/영역 | 근거 | 필요한 plan 수정 | 해결 |
|---|---|---|---|---|
| P1 | Developer/API | Task 3이 type 생성 전 `BarcodeExtractionService`를 configure해서 Task 3을 독립적으로 compile할 수 없었다. | Task 3은 application/properties/reader/fixture bean으로 유지하고, Task 4가 service를 만든 뒤 service bean을 추가한다. | Tasks 3-4에서 수정. |
| P1 | Security | 초기 properties model은 approved quickstart contract가 PNG/JPEG/WebP 세 type으로 고정되어 있음에도 allowlist를 외부에서 replace 가능하게 만들었다. | resource limit만 configurable하게 두고 internal fixed media-type set을 강제한다. | Tasks 3-4에서 수정. |
| P1 | Stability/Ops | `max-request-size: 5MB`는 valid 5 MiB file에 multipart envelope room을 남기지 않았고, plan은 synthetic MockMvc upload가 servlet resolver byte limit을 exercise한다고 가정했다. | `max-file-size`는 5 MiB로 유지하고 request size를 6 MiB로 설정하며, resolver exception mapping을 직접 test하고 bootRun으로 real resolver를 verify한다. | Tasks 3, 5, 8에서 수정. |
| P1 | User/caller | multipart `file` part 누락이 quickstart의 stable error DTO 대신 Spring default error shape로 빠질 수 있었다. | `MissingServletRequestPartException`을 `400 empty_input`으로 map하고 async controller dispatch를 가정하지 않고 test한다. | Task 5에서 수정. |
| P1 | Stability/coroutines | 첫 dispatcher test requirement가 IO와 CPU execution을 어떻게 구분하거나 test executor를 close할지 정의하지 않았다. | named single-thread executor dispatcher를 사용하고, injected seam에 executing thread를 기록하며, 둘 다 `use`로 close한다. | Task 4에서 수정. |
| P1 | Test evidence | 초기 Task 5 GREEN command는 MockMvc application test만 선택해 새 exception-handler test를 실행하지 않을 수 있었다. | focused GREEN command에서 application 및 handler test class를 모두 선택한다. | Task 5에서 수정. |
| P2 | Developer/API | 초기 controller constructor가 POST-only task에서 fixture를 inject하여 이후 GET task 전까지 unused private dependency를 남겼다. | Task 6이 세 GET method를 추가할 때만 fixture dependency를 추가한다. | Tasks 5-6에서 수정. |
| P2 | Build | plan header는 Kotlin 2.3+라고 했지만 repository와 central catalog는 Kotlin 2.4.0을 resolve한다. | plan의 실제 toolchain description을 Kotlin 2.4로 고정한다. | plan header에서 수정. |

## Lens 재실행

| 관점 | 판정 | 근거 |
|---|---|---|
| Performance | PASS | Tasks 3-4는 I/O/decode 전 encoded byte를 제한하고, full decode 전 dimension을 제한하며, fixture를 한 번 load하고, bounded byte만 clone하며, blocking multipart read와 CPU probe/decode/provider work를 분리한다. performance acceptance target이 없는 non-production quickstart라 benchmark는 필요 없다. |
| Stability | PASS | Tasks 2-6은 missing fixture, immutable copy, malformed input, 두 dimension-limit form, provider failure, cancellation, dispatcher ownership, missing part, servlet/application size boundary를 cover한다. Task 8은 clean-start test와 live server smoke path를 추가한다. |
| Security | PASS | Tasks 3-5는 media allowlist를 고정하고, byte/pixel/side를 bound하며, bounded WebP fallback으로 decode 전에 probe하고, message를 sanitize하며, raw byte/backend metadata를 생략하고 unauthenticated local-only boundary를 문서화한다. |
| Operator/Ops | PASS | Tasks 5와 8은 stable status/error mapping, real resolver verification, missing resource startup failure, exact registration/CI ownership, stateless rollback, exact-head review, post-merge cleanup을 정의한다. Actuator/metrics는 approved example scope에 따라 의도적으로 제외된다. |
| Developer/API | PASS | 수정된 task order는 consumer 전에 producer type을 compile한다. ZXing construction은 configuration에 confined되고 service/controller type은 `BarcodeReader`를 사용한다. 모든 behavior에는 focused RED/GREEN command와 commit boundary가 있다. |
| User/caller | PASS | Tasks 5-7은 real multipart path, missing/empty/unsupported/oversized/malformed input, 세 deterministic GET scenario, exact JSON/curl example, English/Korean parity, diagram, capability limit, production warning을 cover한다. |

## Step 3-R 통합

| Check | 결과 | 근거 |
|---|---|---|
| Spec and DoD mapping | PASS | Acceptance Traceability가 approved endpoint, format, limit, fixture, documentation, workflow requirement를 Tasks 1-8에 모두 map한다. |
| Implementable ordering | PASS | Registration -> fixtures -> configuration -> service -> POST -> GET -> docs -> delivery 순서다. Task 3은 더 이상 later service type을 소비하지 않는다. |
| Success/failure/edge paths | PASS | QR, no-result, JPEG, WebP fallback, empty/missing part, unsupported type, encoded size, side, pixels, malformed bytes, provider reason이 할당됐다. |
| Coroutine/concurrency/lifecycle | PASS | Task 4는 cancellation을 rethrow하고 dispatcher/probe seam을 inject하며 IO/CPU work를 분리하고 named executor dispatcher를 close한다. fixture array는 copy-on-read다. |
| Commands and evidence | PASS | 모든 code task에는 focused RED/GREEN command가 있다. final verification은 clean rerun, build, detekt, projects, actionlint, diff check, bootRun, workflow verification을 포함한다. |
| Documentation/KDoc | PASS | Task 3은 public example type의 English KDoc을 할당한다. Task 7은 equivalent English/Korean README, exact curl/JSON, 세 validated SVG/PNG pair를 할당한다. |
| New-module chain | PASS | Task 1과 Task 8은 settings, AGENTS, Examples PR/push/daily matrix, required test resource, project listing, publication surface, root/provider locale link를 cover한다. |
| Compatibility/rollback | PASS | production API/artifact/state/migration change가 없다. Rollback section은 example과 모든 registration을 하나의 unit으로 제거한다. |
| Duplication decision | PASS | Task 2는 runtime source set을 benchmark module에 coupling하지 않고 reviewed fixture byte를 module ownership으로 copy한다. provider logic은 API/provider module을 통해 reuse된다. |

## Conditional N/A 근거

- Spring Boot auto-configuration condition과 `AutoConfiguration.imports`: 이는 published auto-configuration artifact가 아니라 normal internal `@Configuration`을 가진 runnable application이므로 N/A다.
- Exposed/deprecated SQL operator와 receiver shadowing: database나 Exposed code가 scope에 없으므로 N/A다.
- Streaming EOF/truncation/reuse: bounded endpoint가 multipart file 하나를 최대 5 MiB byte array로 읽고 streaming API를 노출하지 않으므로 N/A다.
- JDK preview/FFM migration: module은 Java 21 pure JVM Scrimage/ZXing에서 실행되며 Java 25나 native/FFM API를 건드리지 않으므로 N/A다.
- Testcontainers, OCR, libvips, JNI, 기타 heavyweight lifecycle check는 pure-JVM Spring/ZXing dependency graph에서 N/A다. final diff가 예기치 않게 이를 도입할 때만 plan이 sequential check를 실행한다.
- BOM, Maven publication, Kover/Codecov aggregation, benchmark evidence, production `ci.yml`/nightly job은 `examples/**`가 non-published이고 Examples workflow가 PR 및 daily coverage를 소유하므로 N/A다.

## 최종 판정

- 모든 초기 P0/P1 finding에는 exact plan repair와 affected-lens rerun이 있다.
- P2 toolchain 및 unused-dependency finding은 defer하지 않고 수정했다.
- unresolved user decision, placeholder hash, later-produced type, merge-authorization ambiguity는 남아 있지 않다.
- Latest convergence: **P0=0, P1=0**.

Required checks: 14/14; N/A: 6; Blocked: 0.
