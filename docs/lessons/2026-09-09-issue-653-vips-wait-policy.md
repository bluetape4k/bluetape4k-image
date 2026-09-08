# Issue #653: libvips 동시 초기화 대기 정책 교훈

## 배경

JVips JNI와 vips-ffm FFM runtime은 `UNINITIALIZED → INITIALIZING` CAS owner가 native 초기화를 수행하고, 경쟁 호출자가 완료까지 spin/park하는 구조였다. owner가 정체되거나 waiter가 취소되어도 반환 경계가 없었으므로, 공개 `VipsRuntime` 계약과 두 backend의 lifecycle 경계를 함께 보강해야 했다.

## 결정

- `VipsRuntime.init(Int, Long)`와 default bridge는 변경하지 않는다.
- 경쟁 `init`/`shutdown` waiter만 monotonic `System.nanoTime()` deadline을 사용한다.
- 운영 cap은 60초이며, 테스트에서는 `@VisibleForTesting internal` nanosecond seam으로 짧은 cap을 주입한다.
- 한 호출의 deadline은 첫 경쟁 대기 진입 시 한 번만 계산하고, owner 실패 뒤 retry owner가 등장해도 같은 absolute deadline을 재사용한다. 반복 대기마다 cap을 다시 계산하면 공개된 60초 상한이 사실상 무한 대기로 변한다.
- 누적 deadline 회귀는 실제 sleep 시간에 의존하지 않고 테스트용 monotonic clock과 owner-failure latch로 예산을 정확히 소진시킨다. 그래야 deadline을 두 번째 대기에서 재설정하는 구현이 확실히 실패한다.
- waiter는 `Thread.isInterrupted`를 소비하지 않고 `VipsInitializationException`으로 자신만 반환한다.
- timeout/interrupt 경로는 `AtomicReference` state, effective configuration, native adapter, codec probe를 변경하지 않는다.
- owner가 `Exception`으로 실패하면 기존처럼 `UNINITIALIZED`로 복구하여 다음 호출의 재시도를 허용한다.
- `images-vips-api`에는 공용 runtime framework를 추가하지 않고, 두 backend가 공유하는 메시지/시간 계약만 test fixture로 둔다.

## 검증에서 재사용할 패턴

1. native adapter seam에서 owner를 `CountDownLatch`로 `INITIALIZING`에 고정한다.
2. 경쟁 `init`과 `shutdown` waiter를 각각 interrupt하거나 짧은 cap으로 종료시킨다.
3. waiter의 예외·interrupt flag·`isInitialized`/`isShutdown`·native shutdown count를 확인한다.
4. owner latch를 해제한 뒤 owner가 정상 완료하고, 이후 명시적 shutdown만 native release를 수행하는지 확인한다.
5. shared test fixture가 두 backend의 timeout/interrupt 메시지 의미를 같은 방식으로 검사한다.
6. 첫 owner 실패 직후 retry owner를 다시 `INITIALIZING`에 고정하고, `init()` 및 `shutdown()` waiter가 두 번째 대기에서 deadline을 갱신하지 않고 timeout으로 이탈하는지 검증한다.

## 남은 운영 경계

실제 hosted CI는 현재 workflow의 push/pull_request branch filter가 `develop/main`만 대상으로 하므로 stacked feature PR에서는 자동 check가 생성되지 않는다. native/JNI gated test와 hosted CI dispatch는 별도 권한·환경 게이트로 기록해야 하며, local GREEN을 hosted CI PASS로 해석하지 않는다.

## 최종 검증 메모

- JVips targeted concurrency: 19개 통과; 전체 모듈: 65개 실행, 42개 native-gated skip.
- FFM targeted concurrency: 22개 통과; 전체 모듈: 90개 통과.
- Vips API: 23개 통과; production ABI: 10개 published JVM module 검증; Java 21 bytecode: 66개 class 검증.
- `git diff --check`와 fresh `detekt` 실행은 성공했다. `detekt` aggregate task는 현재 root에 분석 소스가 없어 `NO-SOURCE`로 종료됐으며, 별도 hosted CI 결과는 stacked branch filter 때문에 아직 없다.
