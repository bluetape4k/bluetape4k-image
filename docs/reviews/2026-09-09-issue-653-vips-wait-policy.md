# Issue #653: libvips 동시 초기화 대기 정책 구현 리뷰

## 검토 범위와 기준

- 저장소: `bluetape4k/bluetape4k-image`
- Epic: #656
- 대상 이슈: #653
- 구현 기준: `refactor/issue-652-s3-head` (`0558d3fce5b471a0281f4cc4556a4c1e9c570dd0`)
- 최종 코드 exact head: `fc7ab8626d5391c6e1a86f1b57bce9162b232b38` (fake-clock ordering fix 포함)
- 구현 커밋 이력: `7af92ea` → `8e19f37` → `fc7ab86`
- 대상 모듈: `images-vips-api`, `images-vips-java21`, `images-vips-java25`
- 승인된 범위: 공개 `VipsRuntime` API와 native owner 소유권을 유지하면서 경쟁 `init()`/`shutdown()` waiter에 60초 cap과 interrupt exit를 적용

이번 receipt는 exact head의 소스·호출자·테스트·ABI/API·문서·CI·설계/운영 위험을 7개 관점으로 재검토한 결과다. 구현 PR의 hosted CI가 없는 상태를 성공으로 해석하지 않았다.

## 7-Tier 증거

| Tier | 확인 내용 | 결과 |
| --- | --- | --- |
| T1 정확성 | CAS owner와 waiter를 분리하고, owner 실패 시 `UNINITIALIZED` 재시도 상태를 보존한다. waiter는 `INITIALIZED`/`SHUTDOWN`/`UNINITIALIZED`를 재확인하며 retry owner가 등장해도 한 deadline을 계속 사용한다. | PASS |
| T2 보안·자원 | timeout/interrupt waiter는 state, effective configuration, native adapter, codec probe, native shutdown을 변경하지 않는다. native 자원 해제는 기존 `INITIALIZED → SHUTDOWN` CAS owner만 수행한다. | PASS |
| T3 설계·API | `VipsRuntime.init(Int, Long)` 및 default bridge의 공개 시그니처를 변경하지 않았다. `checkProductionAbi`에서 10개 published JVM module을 검증했다. | PASS |
| T4 성능 | `Thread.onSpinWait()` 후 1ms `LockSupport.parkNanos` backoff을 사용하고, 호출당 absolute `System.nanoTime()` deadline으로 무한 spin/대기와 retry epoch별 cap 재설정을 막는다. | PASS |
| T5 안정성·운영 | interrupt flag를 소비하지 않고 `VipsInitializationException`으로 waiter 자신만 반환한다. `shutdown()`도 동일 cap을 적용하며 owner/native 작업을 중단하지 않는다. | PASS |
| T6 테스트·CI | 공통 vips-api fixture와 두 backend의 adapter/latch/clock seam으로 timeout, interrupt, owner 실패, retry owner, native shutdown 격리를 검증했다. hosted CI는 branch filter상 stacked feature PR에 자동 check가 생성되지 않는다. | LOCAL PASS / REMOTE N/A |
| T7 문서·호출자 | VipsRuntime KDoc과 images-vips-api EN/KR README에 cap, interrupt, timeout, owner 실패 재시도, shutdown 의미를 반영했다. 공개 호출자와 provider 분리는 유지했다. | PASS |

## 구현 및 호출자 근거

- `JVipsRuntime.init()`과 `FfmVipsRuntime.init()`은 최초 CAS 실패 시 deadline을 한 번 계산하고 `awaitInitializationCompletion()`에 전달한다.
- `JVipsRuntime.shutdown()`과 `FfmVipsRuntime.shutdown()`도 호출 시작 시 deadline을 한 번 생성하며, `INITIALIZING` 재확인 루프에서 재계산하지 않는다.
- 운영 clock은 `System::nanoTime`이고 `resetForTest()`에서 복원된다. timeout과 monotonic clock seam은 `@VisibleForTesting internal`이며 public artifact API가 아니다.
- 공통 fixture `VipsInitializationWaitContract`는 60초 메시지와 interrupt 메시지 의미를 두 backend가 동일하게 검사하도록 한다.
- `VipsRuntime`의 직접 호출자는 두 backend runtime과 기존 image consumers이며, 변경된 공개 메서드 시그니처는 없다. 새 dependency나 공용 runtime framework도 추가하지 않았다.

## 결정적 회귀 테스트

각 backend concurrency suite가 다음을 모두 검증한다.

1. `CountDownLatch`로 첫 native owner를 `INITIALIZING`에 고정한다.
2. 경쟁 waiter가 첫 대기에 들어간 뒤 첫 owner를 실패시킨다.
3. `AtomicLong` test clock을 첫 deadline 끝으로 이동하고 retry owner를 `INITIALIZING`에 고정한다.
4. 단일-deadline 구현은 즉시 timeout으로 이탈한다. 두 번째 helper가 새 deadline을 만드는 구현은 clock이 고정된 상태에서 waiter가 계속 살아 있어 `join(2_000)`/`isAlive` 검증에 실패한다.
5. waiter 이탈 중 `isInitialized`, `isShutdown`, native shutdown count를 확인하고, retry owner 완료 후 명시적 shutdown이 정확히 한 번만 native release를 수행하는지 확인한다.

최신 순차 실행 결과:

- JVips targeted: 19/19 PASS를 3회 연속 실행
- FFM targeted: 22/22 PASS를 3회 연속 실행
- JVips 전체: 65 실행, 23 PASS, 42 native-gated SKIPPED
- FFM 전체: 90 PASS
- vips-api 전체: 23 PASS

## ABI·정적·운영 검증

- `./gradlew checkProductionAbi --rerun-tasks`: `Production ABI validated: 10 published JVM modules`
- `./gradlew :bluetape4k-images-vips-java21:verifyVipsJava21Bytecode --rerun-tasks`: 68 production class files, major `<= 69`
- `./gradlew detekt --rerun-tasks`: `BUILD SUCCESSFUL`, root aggregate `NO-SOURCE` (변경 소스에 대한 hosted detekt 결과는 별도 확인 필요)
- `git diff --check`: PASS
- bluetape-workflow mutation check: `ok=true`, run `20260908T145834Z-ed223197`, sequence 67
- PR hosted CI: 아직 PR 생성 전이며, workflow의 push/pull_request branch filter가 `develop/main`만 대상으로 하므로 stacked feature branch의 자동 check는 생성되지 않는 범위다. workflow dispatch는 별도 승인 없이는 실행하지 않았다.

## 독립 리뷰 결과

| reviewer | 범위 | 실행 모델/노력 | 결과 |
| --- | --- | --- | --- |
| code-reviewer | 최종 코드 exact head의 두 runtime, API, tests, docs | `gpt-5.6-luna / max` | P0=0, P1=0, P2=0, `CLEAR` |
| architect | lifecycle, owner/native 경계, ABI, cumulative deadline 증거 | `gpt-5.6-sol / high` | P0=0, P1=0, P2=0, `CLEAR` |

이전 review에서 발견된 두 가지 결함도 exact head에 반영됐다.

- owner 실패 직후 retry owner가 `INITIALIZING`을 재획득할 때 waiter가 조기 성공할 수 있던 race를 `INITIALIZING -> continue` 재확인 루프와 결정적 테스트로 수정했다.
- retry loop마다 새 60초 deadline을 만들 수 있던 누적 대기 결함을 호출 단위 absolute deadline과 fake monotonic clock 회귀 테스트로 수정했다.
- fake clock을 main thread에서 전진시키던 ordering race를 completion hook 내부의 `clock.set()` → `firstWaitCompleted.countDown()` 순서로 고정했다. 이 보강 뒤 두 backend targeted suite를 각각 3회 연속 재실행했다.

## 잔여 위험과 범위 밖 검증

- JVips 전체 suite의 42개 image/native 테스트는 host native gate로 조건부 제외됐다. adapter seam concurrency 검증은 통과했지만, 실제 JNI 초기화가 정체되는 production 장애 주입은 실행하지 않았다.
- FFM 전체 suite는 90개가 host libvips에서 통과했지만, 모든 배포 환경의 native ABI를 대체하지 않는다.
- hosted CI, workflow dispatch, release/tag/publication, merge는 이 receipt의 실행 범위가 아니다. PR 생성 후 remote check가 비어 있는 것은 branch filter의 scope evidence이지 PASS가 아니다.

## DoD Status

- [x] 경쟁 `init()`/`shutdown()` waiter에 60초 cap과 interrupt exit를 정의하고 public API 호환성을 유지했다.
- [x] owner failure/retry, timeout, interrupt, native ownership isolation을 두 backend에서 결정적으로 검증했다.
- [x] cumulative deadline reset 결함을 구별하는 injectable monotonic clock 회귀 테스트를 추가했다.
- [x] API 문서와 EN/KR README를 동기화했다.
- [x] exact head에서 API, backend suites, ABI, bytecode, diff, workflow mutation 검증을 수행했다.
- [x] 독립 code-reviewer와 architect가 P0/P1/P2=0, `CLEAR`를 확인했다.
- [ ] stacked PR hosted CI 결과 확보 — branch filter상 자동 check가 생성되지 않는 범위이며 workflow dispatch 권한이 별도로 필요하다.

상태: **DONE (로컬 검증·독립 리뷰 완료; remote CI는 범위 밖/PENDING)**
