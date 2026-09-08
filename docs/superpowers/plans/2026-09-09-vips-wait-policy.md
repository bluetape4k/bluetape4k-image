# Vips Runtime Wait Policy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: follow `$executing-plans` while implementing this plan and keep every validation claim tied to fresh command output.

**Goal:** Make concurrent libvips initialization and shutdown waiters terminate deterministically without changing the public `VipsRuntime` API or the initialization owner’s native work/state.

**Architecture:** Keep the lifecycle state machine and native-resource ownership inside `JVipsRuntime` and `FfmVipsRuntime`. Replace their unbounded INITIALIZING spin loops with the same backend-local bounded wait policy: a monotonic 60-second production cap, prompt interruption exit that preserves the interrupt flag, and an exception-based timeout/interrupt result. Add a reusable `images-vips-api` test fixture that asserts the shared failure-message contract, while backend tests inject a short wait cap, a monotonic test clock, and native latches to prove owner/waiter isolation and one deadline across retry owners. Do not add a public runtime framework or alter `VipsRuntime.init(Int, Long)`/default bridges.

**Tech Stack:** Kotlin/JVM 25, `AtomicReference`, `LockSupport`, JUnit 5, bluetape4k assertions, existing JVips/FFM native-runtime seams, Gradle ABI and detekt checks.

---

## Task 1: Record the shared contract and RED scenarios

**Files:**
- Add `images-vips-api/src/testFixtures/kotlin/io/bluetape4k/images/vips/testfixtures/VipsInitializationWaitContract.kt`.
- Update `images-vips-api/src/main/kotlin/io/bluetape4k/images/vips/VipsRuntime.kt`.
- Extend `images-vips-java21/src/test/kotlin/io/bluetape4k/images/vips/java21/JVipsRuntimeConcurrencyTest.kt`.
- Extend `images-vips-java25/src/test/kotlin/io/bluetape4k/images/vips/java25/FfmVipsRuntimeConcurrencyTest.kt`.

**Steps:**
1. Define the test-fixture contract for the 60-second maximum, timeout wording, and interruption wording so both backend suites consume one assertion surface.
2. Update `VipsRuntime.init` KDoc from unbounded “spin wait” to bounded/interruptible wait semantics; document that a waiting caller exits without changing the owner’s state or native resources.
3. Add deterministic tests for each backend that hold the owner in `nativeInit`, interrupt a competing `init` waiter, and assert `VipsInitializationException`, preserved interrupt status, unchanged `isInitialized`/native counters while the owner is blocked, and successful owner completion afterward.
4. Add deterministic short-cap timeout tests for competing `init` waiters with the same owner-isolation assertions.
5. Add shutdown-waiter interruption and short-cap timeout tests; assert an abandoned waiter does not call `nativeShutdown`, while a later explicit shutdown still does.
6. Run the two targeted test classes before implementation and record the expected RED failures for the new behavior.

**Commands:**
```bash
./gradlew :bluetape4k-images-vips-java21:test --tests 'io.bluetape4k.images.vips.java21.JVipsRuntimeConcurrencyTest'
./gradlew :bluetape4k-images-vips-java25:test --tests 'io.bluetape4k.images.vips.java25.FfmVipsRuntimeConcurrencyTest'
```

## Task 2: Implement the JVips bounded wait policy

**Files:**
- Modify `images-vips-java21/src/main/kotlin/io/bluetape4k/images/vips/java21/JVipsRuntime.kt`.

**Steps:**
1. Add a private 60-second nanosecond cap and an internal `@VisibleForTesting` timeout override restored by `resetForTest`.
2. Replace the competing-init spin loop with a monotonic deadline loop that checks `Thread.isInterrupted` without consuming the flag, uses bounded `LockSupport.parkNanos` backoff, and throws the contract exception on interruption or timeout.
3. Apply the same helper to `shutdown` while the owner is INITIALIZING; after a waiter exits, re-read state and let only the normal INITIALIZED→SHUTDOWN CAS owner release native resources.
4. Keep the existing owner `CAS`, native init/shutdown calls, failure reset behavior, effective configuration, and public signatures unchanged.
5. Run the targeted JVips test class and the JVips module test suite.

**Commands:**
```bash
./gradlew :bluetape4k-images-vips-java21:test --tests 'io.bluetape4k.images.vips.java21.JVipsRuntimeConcurrencyTest'
./gradlew :bluetape4k-images-vips-java21:test
```

## Task 3: Implement the FFM bounded wait policy

**Files:**
- Modify `images-vips-java25/src/main/kotlin/io/bluetape4k/images/vips/java25/FfmVipsRuntime.kt`.

**Steps:**
1. Mirror the JVips policy and test-only timeout seam without introducing a public API or changing Java 25/native-access configuration.
2. Replace both unbounded INITIALIZING loops with the monotonic bounded/interruptible helper and preserve FFM codec probe, native runtime, and terminal shutdown ownership.
3. Run the targeted FFM test class and the FFM module suite with the repository’s Java 25/native-access settings.

**Commands:**
```bash
./gradlew :bluetape4k-images-vips-java25:test --tests 'io.bluetape4k.images.vips.java25.FfmVipsRuntimeConcurrencyTest'
./gradlew :bluetape4k-images-vips-java25:test
```

## Task 4: Contract, ABI, static, and review evidence

**Files:**
- Add `docs/reviews/2026-09-09-issue-653-vips-wait-policy.md`.
- Add `docs/lessons/2026-09-09-issue-653-vips-wait-policy.md`.
- Keep the PR body and issue linkage synchronized with the approved #653 scope.

**Steps:**
1. Run the API tests and both backend targeted/full tests sequentially; native/JNI checks remain serialized.
2. Run production ABI checks and `detekt`, comparing changed-source warnings against a clean base when needed; verify `VipsRuntime` public descriptors remain unchanged.
3. Run the shared test-fixture contract assertions through both backend suites and capture exact pass/skip counts, timeout/interrupt evidence, native counter evidence, ABI evidence, and any gated native/CI gaps.
4. Perform independent code and architecture reviews of the exact commit, record P0/P1/P2 findings, and resolve all actionable findings before PR creation.
5. Create the PR from `feat/issue-653-vips-wait-policy` against `refactor/issue-652-s3-head`, assign `debop`, mirror #653 metadata, and end the body with `## DoD Status` stating remote CI scope honestly.

**Commands:**
```bash
./gradlew :bluetape4k-images-vips-api:test
./gradlew :bluetape4k-images-vips-java21:test
./gradlew :bluetape4k-images-vips-java25:test
./gradlew :bluetape4k-images-vips-java21:verifyVipsJava21Bytecode
./gradlew checkProductionAbi
./gradlew detekt
git diff --check
```

## Self-review checklist

- Spec coverage: timeout cap, interruption, retry/failure state, public ABI, owner/native-resource isolation, shared fixture, and independent evidence are all represented.
- Placeholder scan: no `TBD`, `TODO`, fake command, or unspecified file remains in the execution steps.
- Type consistency: both backends retain their private enum state and native adapters; only wait-policy control flow is shared by contract.
- Verification order: RED precedes implementation; API, backend, ABI, static, and review gates precede PR creation; merge remains a separate fresh exact-head approval gate.
