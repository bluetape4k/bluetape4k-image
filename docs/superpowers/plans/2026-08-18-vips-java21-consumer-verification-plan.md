# VIPS Java21 소비자 검증 구현 계획

## 계획 상태

- 기준: Issue #486, Epic #509, 부모 PR #538 head `7726530975e664330e247d48089b06d55e9fbd2d`
- 작업 worktree: `.worktrees/feat-issue-486-vips-consumer-verification`
- 구현 원칙: `$bluetape-workflow` Type A, `$bluetape-kotlin-patterns`, TDD RED→GREEN, native/JNI 작업 순차 실행
- 선행 scope gate: merged PR #502의 Java25 baseline과 충돌하는 Issue의 JDK21/major65 acceptance를 JDK25/major69로 live issue에 정렬한 뒤 구현한다.
- PR base: `fix/issue-485-vips-contract`; PR head: `feat/issue-486-vips-consumer-verification`

## 선행 게이트

1. 설계 문서와 이 계획을 먼저 commit한다.
2. `git diff --check`, writer SPW-01..05, independent architecture/build review에서 P0/P1이 없어야 구현한다.
3. 각 구현 단위는 실패하는 테스트 또는 contract assertion을 먼저 추가하고 RED 결과를 기록한다.

## 작업 순서와 traceability

### 1. Java25 production target과 consumer-only smoke

**대상 파일**

- `images-vips-api/build.gradle.kts`
- `images-vips-java21/build.gradle.kts`
- `images-vips-java21/src/consumerTest/kotlin/io/bluetape4k/images/vips/java21/VipsJava21ConsumerSmokeTest.kt`
- 필요 시 `images-vips-java21/README.md`, `README.ko.md`, `images-vips-api/README.md`, `README.ko.md`

**구현**

- API/JNI main compile은 현재 Java25 contract를 유지하고, test fixture를 소비하지 않는 consumer smoke source set을 추가한다.
- `consumerTest` source set/configuration/task를 생성한다. classpath는 해당 module main output과 runtime dependency만 포함하고 `testFixtures`/`src/test` output은 포함하지 않는다.
- Java 25 launcher와 Kotlin JVM target 25로 consumer smoke를 실행한다. 명시적 `-Pvips.consumer.enabled=true`가 없으면 capability gate로 skip하고, 활성화했는데 JNI init이 실패하면 실패한다.
- smoke는 public `vipsImageOf`와 `VipsImage`만 사용해 embedded PNG를 decode하고 dimensions를 검증한다.
- `verifyVipsJava21Bytecode`는 API/JNI production class directories/jar의 class file major를 69 이하로 검사한다.

**RED**

- 먼저 production-only consumer task와 major 69 ceiling task를 추가하고, fixture classpath가 섞이면 실패하는 contract를 관찰한다.

**검증**

```bash
./gradlew :bluetape4k-images-vips-java21:consumerTest -Pvips.consumer.enabled=true -Dvips.enabled=true --no-configuration-cache --no-daemon
./gradlew :bluetape4k-images-vips-java21:verifyVipsJava21Bytecode --no-configuration-cache --no-daemon
```

### 2. Golden fail-closed와 fixture 복구

**대상 파일**

- `images-vips-api/src/testFixtures/kotlin/io/bluetape4k/images/vips/testfixtures/VipsGoldenAssert.kt`
- `images-vips-api/src/test/kotlin/io/bluetape4k/images/vips/testfixtures/VipsGoldenAssertTest.kt`
- `images-vips-api/src/testFixtures/resources/golden/vips/*.png`

**구현**

- missing resource의 assumption skip를 assertion failure로 바꾼다.
- update mode/CI guard와 native capability gate는 유지한다.
- Java25 FFM authoritative operation으로 six PNG를 생성하고 tracked fixture로 추가한다.
- API unit test는 native 없이 missing key가 실패함을 검증한다.

**RED/GREEN 순서**

1. missing-key test를 먼저 추가하고 현재 `TestAbortedException`으로 RED를 확인한다.
2. helper를 fail-closed로 고쳐 GREEN을 확인한다.
3. Java25 FFM에서 fixture를 생성한 뒤 Java25/Java21 golden test를 순차 실행한다.

**검증**

```bash
./gradlew :bluetape4k-images-vips-api:test --tests '*VipsGoldenAssertTest' --no-configuration-cache --no-daemon
./gradlew :bluetape4k-images-vips-java25:test --tests '*golden.*' -Dvips.enabled=true --no-configuration-cache --no-daemon
./gradlew :bluetape4k-images-vips-java21:test --tests '*golden.*' -Dvips.enabled=true --no-configuration-cache --no-daemon
```

native/JNI 테스트는 Java25 FFM fixture 생성과 겹치지 않도록 순차 실행한다.

### 3. Java21 benchmark 실행과 report contract

**대상 파일**

- `benchmark/images-benchmark/build.gradle.kts`
- `benchmark/images-benchmark/src/test/kotlin/io/bluetape4k/images/benchmark/VipsJava21BenchmarkContractTest.kt`
- 필요 시 `benchmark/images-benchmark/README.md`, `README.ko.md`

**구현**

- `vipsJava21Smoke` configuration을 등록하고 `VipsBackendBenchmark`만 포함한다.
- warmup/measurement/fork를 짧고 deterministic하게 고정하며 `vips.impl=java21`을 필수화한다.
- `verifyVipsJava21BenchmarkReport` task가 JSON report 존재, backend/configuration, finite positive score를 검사한다.
- 기존 장시간 benchmark configuration은 변경하지 않는다.

**RED/GREEN 순서**

1. task listing/report contract test를 먼저 추가한다.
2. configuration/task를 구현하고 dry-run 및 실제 native 실행을 통과시킨다.

**검증**

```bash
./gradlew :bluetape4k-images-benchmark:test --tests '*VipsJava21BenchmarkContractTest' -Pvips.impl=java21 --no-configuration-cache --no-daemon
./gradlew :bluetape4k-images-benchmark:verifyVipsJava21BenchmarkReport -Pvips.impl=java21 --no-configuration-cache --no-daemon
```

### 4. Published BOM consumer smoke

**대상 파일**

- `bom/build.gradle.kts`
- `bom/src/test/kotlin/io/bluetape4k/images/bom/VipsBomConsumerResolutionTest.kt` 또는 build task 전용 Kotlin helper
- 필요 시 `bom/README.md`, `README.ko.md`

**구현**

- 임시 file Maven repository publication을 선행하고, Bluetape 공통 runtime snapshot repository와 Maven Central을 보조 repository로 사용하는 임시 외부 Gradle consumer를 생성하는 `verifyVipsBomConsumer` task/test를 추가한다. image BOM/API/JNI 좌표는 file repository에서 해석되는지 검증하고, Maven Local과 project substitution은 사용하지 않는다.
- consumer는 platform BOM과 versionless legacy `java21` module coordinate만 선언하고 Java25 toolchain compile/smoke를 수행한다.
- temp project cleanup, repository allow-list, dependency resolution failure를 명시한다.

**RED/GREEN 순서**

1. BOM constraint가 없는/fixture-only consumer와 Maven Local/project substitution을 거부하는 functional assertion을 먼저 추가한다.
2. publish + temp consumer task를 구현하고 exact coordinates/Java25 compile을 통과시킨다.

**검증**

```bash
./gradlew :bluetape4k-image-bom:verifyVipsBomConsumer --no-configuration-cache --no-daemon
```

### 5. CI/nightly 증적

**대상 파일**

- `.github/workflows/ci.yml`
- `.github/workflows/nightly-tests.yml`
- 관련 contract test/README

**구현**

- CI에서 Java25 consumer/bytecode와 golden contract를 실행한다. legacy `java21`은 artifact/backend 식별자에만 남긴다.
- libvips가 준비된 native benchmark lane을 Java21로 순차 실행한다.
- benchmark report directory를 artifact로 업로드하고 report path/backend/score를 `$GITHUB_STEP_SUMMARY`에 기록한다.
- nightly에 동일한 benchmark/BOM consumer 검증을 추가하고, 의도된 capability gate와 실패를 구분한다.
- actionlint로 workflow 문법과 job dependency를 검증한다.

## 독립 리뷰와 통합

- 설계/계획: architect, test-engineer, code-reviewer, verifier 관점에서 read-only 검토한다.
- 구현 후: performance, stability/Ops, security, developer/API, user/caller, build/CI 관점 여섯 lane을 순차 wave로 검토하고 main integration에서 P0/P1을 정규화한다.
- native/JNI와 benchmark는 동시 실행하지 않는다.
- review artifact에는 기준 base/head, changed files, commands, P0-P3 counts, disposition, residual gaps를 기록한다.

## 롤백과 정리

- target override가 test fixture variant를 깨뜨리면 module target 변경을 되돌리고 source set 경계를 먼저 복원한다.
- golden 생성 실패 시 generated files를 commit하지 않고 authoritative Java25 command를 재실행한다.
- benchmark/report 또는 temp consumer 실패는 CI job을 실패시키며 기존 benchmark configurations는 유지한다.
- PR 생성 전 local branch/worktree만 정리하고, 부모 PR #538 merge 전에는 stacked base를 변경하지 않는다.

## Writer gate

- SPW-01: audience/purpose/evidence — PASS
- SPW-02: ordered tasks/files/tests/rollback — PASS
- SPW-03: Korean technical register — PASS
- SPW-04: spec-to-plan traceability — PASS
- SPW-05: final read-back — PASS
