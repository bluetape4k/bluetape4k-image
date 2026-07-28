# Issue #208 Codec/Runtime Matrix 구현 계획

> **Agentic worker 필수 지침:** 이 계획은 task 단위로 구현한다. 구현 표면은 superpowers:subagent-driven-development(권장) 또는 superpowers:executing-plans를 사용한다. 진행 추적에는 checkbox(`- [ ]`) 문법을 사용한다.

**목표:** Java 21 JNI와 Java 25 FFM libvips runtime에 대해 fail-closed 방식으로 재현 가능한 PNG/WebP codec benchmark matrix를 추가하고, AVIF/HEIC는 opt-in evidence로 다룬다.

**아키텍처:** Internal main-source harness component는 hash-pinned fixture를 준비하고, 선택된 runtime을 probe하며, eligibility/run manifest를 serialize하고, append-only evidence를 finalize한다. JMH source file은 trial setup과 측정 대상 transcode call만 포함한다. Stable task는 같은 canonical manifest를 소비하고, experimental task는 direction-specific capability와 smoke gate에 의존한다.

**기술 스택:** Repository-selected Kotlin 2.4.0(Kotlin 2.3+ line), Java 21/25 toolchain, Gradle Kotlin DSL, kotlinx-benchmark 0.4.17/JMH, kotlinx-serialization JSON, Scrimage, JVips JNI 또는 vips-ffm을 통한 libvips, JUnit 5, bluetape4k assertion.

---

## 전제조건과 경계

- Worktree: `.worktrees/perf-issue-208-codec-runtime-matrix`
- Branch: `perf/issue-208-codec-runtime-matrix`
- Base: `origin/develop` at `feb75001a35fceb53f976a982e7d44a1eb28e204`
- Approved spec: `docs/superpowers/specs/2026-07-13-issue-208-codec-runtime-matrix-design.md`
- 범위는 `bluetape4k-images-benchmark`, 해당 benchmark evidence, benchmark README 두 locale, 그리고 trigger된 chart asset으로 제한한다.
- `VipsImage`, `VipsRuntime`, JVips, FFM, BOM, catalog alias/version, module registration, CI, Nightly, production API는 변경하지 않는다. Dependency resolution failure가 발생하면 scope approval을 다시 열어야 하며, inline catalog fix 권한으로 해석하지 않는다.
- 기존 `VipsBenchmarkState`와 `VipsBackendEncodeBenchmark.vips_encodeJpeg`는 변경하지 않는다.
- 모든 JNI/FFM/capability/JMH command는 sequential로 실행한다. 실패한 native attempt는 fresh-process rerun 전에 진단한다.
- 모든 harness CLI는 검증된 run ID와 문서화된 scalar flag만 받는다. Gradle은 repository working directory를 pin한다. Kotlin code는 정확한 generated/staging/accepted root를 파생하고 검증하며, absolute path, caller-provided path, `..`, ancestor 또는 tree entry의 symlink, non-regular input을 거부한다.
- `.kt` 편집 후 IDE diagnostic을 사용할 수 있으면 실행하고 import를 정리하며 Gradle compile 전에 모든 error/deprecation을 해결한다. IDE tooling을 사용할 수 없으면 그 제한을 기록하고 focused Kotlin compile/test command를 fallback evidence로 사용한다.
- Implementation review, lesson commit, PR readiness 이후 중단한다. PR 생성과 merge는 명시적 user boundary로 남긴다.

## 파일과 Ownership Map

| File | 책임 |
|---|---|
| `benchmark/images-benchmark/build.gradle.kts` | strict selector, dependency, named config, prepare/capability/finalize task |
| `benchmark/images-benchmark/src/main/kotlin/io/bluetape4k/images/benchmark/CodecMatrixModels.kt` | scenario, status, reason code, manifest |
| `benchmark/images-benchmark/src/main/kotlin/io/bluetape4k/images/benchmark/CodecMatrixJson.kt` | canonical JSON, SHA-256, atomic write, validation |
| `benchmark/images-benchmark/src/main/kotlin/io/bluetape4k/images/benchmark/CodecMatrixFixtures.kt` | fixed-source preparation과 fixture manifest |
| `benchmark/images-benchmark/src/main/kotlin/io/bluetape4k/images/benchmark/CodecMatrixPreflight.kt` | vips-free selector, host/JDK/binary preflight, diagnostic |
| `benchmark/images-benchmark/src/main/kotlin/io/bluetape4k/images/benchmark/CodecMatrixCapability.kt` | vips-free capability/smoke DTO와 operation seam |
| `benchmark/images-benchmark/src/main/kotlin/io/bluetape4k/images/benchmark/CodecMatrixFixtureMain.kt` | stable fixture preparation CLI |
| `benchmark/images-benchmark/src/main/kotlin/io/bluetape4k/images/benchmark/CodecMatrixPreflightMain.kt` | non-native preflight CLI |
| `benchmark/images-benchmark/src/main/kotlin/io/bluetape4k/images/benchmark/CodecMatrixFinalizeMain.kt` | non-native finalization/promotion CLI |
| `benchmark/images-benchmark/src/benchmark/kotlin/io/bluetape4k/images/benchmark/CodecMatrixRuntimeAdapter.kt` | fallback 없는 selected Vips runtime/image adapter |
| `benchmark/images-benchmark/src/benchmark/kotlin/io/bluetape4k/images/benchmark/CodecMatrixCapabilityMain.kt` | selected-backend capability와 directional-smoke CLI |
| `benchmark/images-benchmark/src/benchmark/kotlin/io/bluetape4k/images/benchmark/CodecMatrixExperimentalFixtureMain.kt` | eligible AVIF/HEIC target-input CLI |
| `benchmark/images-benchmark/src/benchmark/kotlin/io/bluetape4k/images/benchmark/VipsCodecMatrixBenchmark.kt` | stable state와 네 measured boundary |
| `benchmark/images-benchmark/src/benchmark/kotlin/io/bluetape4k/images/benchmark/VipsExperimentalCodecMatrixBenchmark.kt` | opt-in AVIF/HEIC state와 method |
| `benchmark/images-benchmark/src/test/kotlin/io/bluetape4k/images/benchmark/CodecMatrixModelsTest.kt` | manifest/status invariant |
| `benchmark/images-benchmark/src/test/kotlin/io/bluetape4k/images/benchmark/CodecMatrixFixturesTest.kt` | fixture determinism/path/magic |
| `benchmark/images-benchmark/src/test/kotlin/io/bluetape4k/images/benchmark/CodecMatrixRuntimeTest.kt` | selector/preflight/sanitizer |
| `benchmark/images-benchmark/src/test/kotlin/io/bluetape4k/images/benchmark/CodecMatrixCapabilityTest.kt` | direction/smoke/close ownership |
| `benchmark/images-benchmark/src/test/kotlin/io/bluetape4k/images/benchmark/CodecMatrixEvidenceFinalizerTest.kt` | hash/cell/no-overwrite |
| `benchmark/images-benchmark/src/test/kotlin/io/bluetape4k/images/benchmark/CodecMatrixBenchmarkContractTest.kt` | JMH/config/task graph contract |
| `benchmark/images-benchmark/src/test/kotlin/io/bluetape4k/images/benchmark/CodecMatrixBenchmarkTaskFunctionalTest.kt` | Gradle TestKit execution-argument/gating contract |
| `benchmark/images-benchmark/docs/raw/<run-id>/` | finalized append-only evidence |
| `benchmark/images-benchmark/docs/codec-runtime-matrix-2026-07-13.md` | 상세 report |
| `benchmark/images-benchmark/README.md` / `README.ko.md` | 동등한 summary와 link |
| `docs/images/readme-charts/images-benchmark-codec-runtime-matrix-chart-01.svg` / `.png` | 조건부 comparable chart |
| `docs/lessons/2026-07-13-issue-208-codec-runtime-matrix.md` | durable lesson |

## Acceptance Traceability

| 요구사항 | Tasks | Proof |
|---|---|---|
| PNG/WebP four-boundary matrix, 두 scenario | 2, 7 | 실행 가능한 backend마다 8개 stable JMH row |
| Backend 간 동일 input | 2, 6 | 하나의 run ID와 manifest hash |
| lazy-open/no-op timing 없음 | 7, 8 | forced output과 `bh.consume(null)` 부재 |
| Direction-specific AVIF/HEIC | 4, 8 | cell eligibility, smoke, focused task graph |
| Terminal status와 blocker | 1, 4, 5 | validator와 negative test |
| Latency/allocation/input/output byte | 5, 9 | JMH, GC profiler, size artifact |
| Runtime/environment evidence | 3, 4, 9 | preflight/capability/run manifest |
| Default experimental isolation | 6, 8 | dry-run과 contract test |
| README/report/locale parity | 10 | paired doc과 link check |
| Comparable chart only | 10 | diagram ledger 또는 evidence-backed N/A |
| Review/lesson | 11 | P0/P1=0과 committed lesson |

## Risk Prediction

| Risk | Signal | Mitigation | Rerun/rollback |
|---|---|---|---|
| Backend input 불일치 | hash mismatch | 명시적 run ID로 한 번만 prepare | run 폐기 후 native evidence 재시작 |
| Java 21 binary incompatible | arm64 host/x86_64 JNI | non-native preflight가 `N_A` emit | JNI JMH 호출 금지 |
| Capability가 operation을 잘못 보고 | malformed/failed transcode | blocking `FAILED_SMOKE` | 진단 후 새 run ID 사용 |
| Native lifecycle leak | retry-only pass/close mismatch | `use`, lane별 fresh process, `shutdown()` 금지 | lane 무효화 후 rerun |
| Experimental graph leakage | default dry-run에 probe/task 포함 | explicit exclusion과 graph test | native work 전에 수리 |
| Evidence가 local data 노출 | path/secret-like token | fixed reason, sanitizer, promotion scan | staging 거부 후 regenerate |
| GC protocol 불일치 | row/iteration/thread mismatch | direct JMH flag pin | profiler lane rerun |
| Promotion이 evidence overwrite | target exists/partial move | atomic append-only promotion | `supersedes`로 새 run |

### Task 1: Manifest와 Status Invariant 정의

**Complexity:** High
**Depends on:** approved spec
**Pattern skills:** `bluetape-kotlin-patterns`, `references/testing.md`
**Files:** `CodecMatrixModels.kt`, `CodecMatrixJson.kt`, `CodecMatrixModelsTest.kt`를 만들고 module `build.gradle.kts`만 수정한다.

- [ ] **Step 1: 실패하는 invariant test를 추가한다.**

`alias(libs.plugins.kotlin.serialization)`을 적용하고 `implementation(libs.kotlinx.serialization.json)`을 사용한다. Governed alias가 release-train catalog tag로 게시되기 전까지는 문서화된 temporary issue #208 version pin을 repo-local alias에 추가한다. Java 25 이후 Java 21 sequential verification에서 Java 21 transformer가 Java 25 class를 load하지 않도록 benchmark module의 사용하지 않는 atomicfu JVM transform을 비활성화한다. 기존 `benchmarkImplementation(project(":bluetape4k-images-vips-api"))`는 보존한다. Main source-set dependency graph에 Vips API type을 추가하지 않는다. Test:

```kotlin
@Test
fun `eligibility manifest cannot claim measured`() {
    assertFailsWith<IllegalArgumentException> {
        eligibilityManifest(CodecMatrixCellStatus.MEASURED).validateEligibility()
    }
}

@Test
fun `accepted manifest rejects blocking states`() {
    assertFailsWith<IllegalArgumentException> {
        finalizedManifest(CodecMatrixCellStatus.FAILED_SMOKE).validateAccepted()
    }
}
```

- [ ] **Step 2: RED를 관찰한다.**

```bash
./gradlew :bluetape4k-images-benchmark:test \
  --tests "io.bluetape4k.images.benchmark.CodecMatrixModelsTest" \
  -Pvips.impl=java25 --console=plain
```

Expected: codec-matrix model type이 아직 없어서 compilation이 실패한다.

- [ ] **Step 3: Model과 canonical JSON을 구현한다.**

Define:

```kotlin
@Serializable internal enum class CodecMatrixScenario { WEB_PHOTO, PROFILE }
@Serializable internal enum class CodecMatrixDirection { ENCODE, DECODE }
@Serializable internal enum class CodecMatrixCellStatus {
    ELIGIBLE, MEASURED, UNSUPPORTED, SKIPPED,
    @SerialName("N/A") N_A,
    FAILED_SMOKE, ERROR,
}
@Serializable internal enum class CodecMatrixReasonCode {
    NONE, CAPABILITY_UNAVAILABLE, CAPABILITY_UNKNOWN, HOST_BINARY_INCOMPATIBLE,
    POLICY_HOLD, RUNTIME_INITIALIZATION_FAILED, BACKEND_IDENTITY_MISMATCH,
    FIXTURE_INVALID, SMOKE_FAILED, EVIDENCE_INVALID,
}
```

`kotlinx.serialization.SerialName`을 import한다. Dimension, hash, input, fixture entry, cell, eligibility, finalized manifest에는 `serialVersionUID`를 가진 `java.io.Serializable` 구현 internal `@Serializable` data class를 사용한다. Positional same-type parameter 대신 run ID, SHA-256, dimension, path, multi-string operation input에 named value/request type을 도입한다. Repository-relative `String` path만 persist하고, serialized model에는 `Path`, `File`, host-local absolute path를 포함하지 않는다. Unique expected cell, `[a-z0-9][a-z0-9._-]{7,79}`에 맞는 run ID, eligibility에 `MEASURED` 없음, accepted evidence에 `ELIGIBLE/FAILED_SMOKE/ERROR` 없음, `MEASURED` cell의 latency/allocation/input/output metric과 hash 완비, unmeasured cell의 fixed reason/rerun guidance를 강제한다. `CodecMatrixJson`은 pretty explicit-default JSON, SHA-256, same-directory temporary plus atomic move를 사용한다. Decode 전에 declared SHA-256과 fixed byte-size ceiling을 확인한다. Strict decoding은 unknown/duplicate key, excess nesting, non-finite number, oversized string/collection, 정확한 matrix cardinality 밖의 cell/artifact count를 거부한다. 모든 bound에 negative test를 추가한다.

- [ ] **Step 4: GREEN을 관찰하고 commit한다.**

Focused test를 다시 실행한다. Native initialization 없이 `PASS`해야 한다.

```bash
git add benchmark/images-benchmark/build.gradle.kts \
  benchmark/images-benchmark/src/main/kotlin/io/bluetape4k/images/benchmark/CodecMatrixModels.kt \
  benchmark/images-benchmark/src/main/kotlin/io/bluetape4k/images/benchmark/CodecMatrixJson.kt \
  benchmark/images-benchmark/src/test/kotlin/io/bluetape4k/images/benchmark/CodecMatrixModelsTest.kt
git commit -m "feat: add codec matrix manifest model"
```

### Task 2: Canonical PNG/WebP Fixture 준비

**Complexity:** High
**Depends on:** Task 1
**Pattern skills:** `bluetape-kotlin-patterns`, `references/testing.md`
**Files:** `CodecMatrixFixtures.kt`, `CodecMatrixFixtureMain.kt`, `CodecMatrixFixturesTest.kt`를 만든다.

- [ ] **Step 1: 실패하는 fixture test를 작성한다.**

정확한 generated-source name/dimension, deterministic hash, symlink/missing rejection, no-overwrite, derived 1920x1080/512x512, positive size, JPEG/PNG/WebP magic을 증명한다. Test는 두 repository fixture만 Gradle `Sync` output 형태의 temporary directory로 복사한다. Harness code는 repository root나 다른 module의 test path를 받지 않는다.

```kotlin
@Test
fun `canonical preparation is deterministic`() {
    val first = prepareFixtures(generatedSources, tempDir.resolve("a"), "fixture-a-0001")
    val second = prepareFixtures(generatedSources, tempDir.resolve("b"), "fixture-b-0001")
    first.fixtures.map { it.inputs.map(CodecMatrixInput::sha256) }
        .shouldBeEqualTo(second.fixtures.map { it.inputs.map(CodecMatrixInput::sha256) })
}
```

- [ ] **Step 2: RED를 관찰한다.**

```bash
./gradlew :bluetape4k-images-benchmark:test \
  --tests "io.bluetape4k.images.benchmark.CodecMatrixFixturesTest" \
  -Pvips.impl=java25 --console=plain
```

Expected: fixture function이 아직 없어 unresolved 상태다.

- [ ] **Step 3: Fixed-source preparation을 구현한다.**

Task 6의 `syncCodecMatrixSourceFixtures`가 채운 `build/generated/codec-matrix-source-fixtures/`만 소비한다. Checked-in source path는 해당 Gradle `Sync` declaration에만 둔다. Use:

```kotlin
private const val CAFE_SOURCE = "cafe.jpg"
private const val HOMER_SOURCE = "homer.jpg"
private val JPEG_WRITER = JpegWriter(85, false)
private val PNG_WRITER = PngWriter(4)
private val WEBP_WRITER = WebpWriter(-1, 85, 4, false, false)
```

Generated-source directory 아래로만 resolve하고, symlink를 거부하며, original dimension을 검증하고, stretching 없이 지정된 integer cover-scale plus centered crop을 구현한다. Derived JPEG/PNG/WebP는 `build/codec-matrix/<run-id>/fixtures/<scenario>/` 아래에 쓰고, source/derived/input hash, magic, byte count, dimension, recipe, option을 담은 `fixtures/manifest.json`을 atomic하게 쓴다. Existing run content는 byte-identical하게 validate되거나 fail해야 한다. `CodecMatrixFixtureMain`은 `--run-id`만 받는다. Gradle-pinned repository working directory에서 정확한 generated-source, backend-preflight, run output path를 파생한다. Test는 changed working root, absolute/`..` path, symlink ancestor/target, non-regular generated input을 거부한다.

- [ ] **Step 4: GREEN을 관찰한다.**

```bash
./gradlew :bluetape4k-images-benchmark:test \
  --tests "io.bluetape4k.images.benchmark.CodecMatrixFixturesTest" \
  -Pvips.impl=java25 --console=plain
```

Expected: fixture test가 통과한다. Gradle preparation task는 command implementation이 존재한 뒤 Task 6에서 등록한다.

- [ ] **Step 5: Fixture preparation을 commit한다.**

```bash
git add benchmark/images-benchmark/src/main/kotlin/io/bluetape4k/images/benchmark/CodecMatrixFixtures.kt \
  benchmark/images-benchmark/src/main/kotlin/io/bluetape4k/images/benchmark/CodecMatrixFixtureMain.kt \
  benchmark/images-benchmark/src/test/kotlin/io/bluetape4k/images/benchmark/CodecMatrixFixturesTest.kt
git commit -m "feat: prepare canonical codec fixtures"
```

### Task 3: Strict Runtime Selection과 Host Preflight 추가

**Complexity:** High
**Depends on:** Task 1
**Pattern skills:** `bluetape-kotlin-patterns`, `references/testing.md`
**Files:** `CodecMatrixPreflight.kt`, `CodecMatrixPreflightMain.kt`, `CodecMatrixRuntimeTest.kt`를 만든다.

- [ ] **Step 1: 실패하는 selector/preflight/sanitizer test를 작성한다.**

```kotlin
@Test
fun `unknown selector is rejected`() {
    assertFailsWith<IllegalArgumentException> {
        CodecMatrixBackend.parse("jni")
    }
}

@Test
fun `arm64 host and x86 JNI binary becomes N A without initialization`() {
    val result = preflight(host("arm64"), CodecMatrixBackend.JAVA21, "x86_64")
    result.status.shouldBeEqualTo(CodecMatrixCellStatus.N_A)
    runtimeFactoryCalls.get().shouldBeEqualTo(0)
}
```

Malformed selector/run ID, dirty/git probe error, absolute home path, control/Markdown metacharacter, secret-like key, fixed bound를 넘는 text의 sanitization도 증명한다. 이 task는 non-native이므로 runtime identity는 의도적으로 포함하지 않는다.

- [ ] **Step 2: RED를 관찰한다.**

```bash
./gradlew :bluetape4k-images-benchmark:test \
  --tests "io.bluetape4k.images.benchmark.CodecMatrixRuntimeTest" \
  -Pvips.impl=java25 --console=plain
```

- [ ] **Step 3: Injected non-native preflight를 구현한다.**

`CodecMatrixBackend`는 정확히 `java21|java25` allowlist이며 vips-free identifier와 expected JDK/runtime metadata만 가진다. Host, JDK, JNI binary, git, disk, native-access probe를 주입한다. `CodecMatrixPreflightMain`은 `build/codec-matrix/<run-id>/preflight-<backend>.json`을 쓴다. 알려진 architecture/JDK/binary incompatibility는 Vips class를 load하지 않고 structured `N_A`를 만든다. Allowlisted fact와 fixed reason code만 기록한다. Hostname, user, absolute home/worktree/temp path, environment value, raw tool/native message는 생략한다.

- [ ] **Step 4: GREEN을 관찰하고 commit한다.**

Focused test를 다시 실행한 뒤:

```bash
git add benchmark/images-benchmark/src/main/kotlin/io/bluetape4k/images/benchmark/CodecMatrixPreflight.kt \
  benchmark/images-benchmark/src/main/kotlin/io/bluetape4k/images/benchmark/CodecMatrixPreflightMain.kt \
  benchmark/images-benchmark/src/test/kotlin/io/bluetape4k/images/benchmark/CodecMatrixRuntimeTest.kt
git commit -m "feat: add codec runtime preflight"
```

### Task 4: Directional Capability와 Smoke Evidence 구성

**Complexity:** High
**Depends on:** Tasks 1-3
**Pattern skills:** `bluetape-kotlin-patterns`, `references/testing.md`
**Files:** Main-source `CodecMatrixCapability.kt`를 만들고, benchmark-source `CodecMatrixRuntimeAdapter.kt`, `CodecMatrixCapabilityMain.kt`, `CodecMatrixExperimentalFixtureMain.kt`를 만들며, `CodecMatrixCapabilityTest.kt`를 만든다.

- [ ] **Step 1: 실패하는 capability/smoke test를 작성한다.**

Hand-written vips-free fake로 encode/decode gate가 독립적임을 증명한다. Decode는 pinned target input을 요구한다. `UNAVAILABLE -> UNSUPPORTED`, `UNKNOWN -> SKIPPED`, known incompatibility -> native call 없는 `N_A`, available malformed/failed smoke -> blocking `FAILED_SMOKE`, unexpected failure -> `ERROR`를 검증한다. Operation handle은 success와 exception 모두에서 close되어야 한다. Test에서는 `VipsRuntime`, `VipsImage`, `VipsImageFormat`, backend exception을 import하지 않는다.

```kotlin
@Test
fun `available smoke failure remains blocking`() {
    codecOps.failure = IllegalStateException("native details")
    val cell = evaluator.evaluateEncode(avifAvailable(), fixture, CodecFormat.AVIF)
    cell.status.shouldBeEqualTo(CodecMatrixCellStatus.FAILED_SMOKE)
    cell.reason.shouldNotContain("native details")
}
```

- [ ] **Step 2: RED를 관찰한다.**

```bash
./gradlew :bluetape4k-images-benchmark:test \
  --tests "io.bluetape4k.images.benchmark.CodecMatrixCapabilityTest" \
  -Pvips.impl=java25 --console=plain
```

- [ ] **Step 3: Exact-boundary smoke를 구현한다.**

Main-source evaluator는 vips-free `CodecMatrixCodecOps` interface만 호출한다. `CodecMatrixRuntimeAdapter`는 `src/benchmark` 아래에 두고 exact selector가 지정한 class만 load하며, `init(concurrency = 4)`를 호출하고 requested backend identity와 reported backend identity를 검증한다. Format/option을 mapping하고 image 하나를 열어 `toBytes`를 호출한 뒤 `use`로 닫는다. Fallback은 없다. `CodecMatrixExperimentalFixtureMain`은 eligible target input만 준비하고, magic/dimension/size를 검증하며 hash를 계산하고, producer backend/JDK/libvips/codec-library version, command, run ID를 기록한다. Decode smoke는 그 exact pinned input을 사용하고 JPEG를 강제한다. Decode-only cell은 explicit compatible producer manifest를 요구한다. Supplemental public round-trip smoke는 양방향이 모두 available일 때만 기록한다.

`CodecMatrixCapabilityMain`은 같은 input/option으로 각 stable transcode를 JMH 밖에서 한 번 수행해 `build/reports/benchmarks/codec-matrix/<run-id>/` 아래에 backend-specific `eligibility-<backend>.json`과 stable `sizes-<backend>.json`을 쓴다. `CodecMatrixExperimentalFixtureMain`은 eligible target input을 준비한 뒤, 같은 boundary와 option으로 experimental encode/decode size observation을 staged backend size artifact에 append한다. 두 artifact는 finalization의 immutable input이다. `UNSUPPORTED/SKIPPED/N_A`는 성공 exit이고, `FAILED_SMOKE/ERROR`는 sanitized evidence를 쓴 뒤 nonzero로 exit한다.

- [ ] **Step 4: GREEN을 관찰하고 commit한다.**

```bash
./gradlew :bluetape4k-images-benchmark:test \
  --tests "io.bluetape4k.images.benchmark.CodecMatrixCapabilityTest" \
  -Pvips.impl=java25 --console=plain
git add benchmark/images-benchmark/src/main/kotlin/io/bluetape4k/images/benchmark/CodecMatrixCapability.kt \
  benchmark/images-benchmark/src/benchmark/kotlin/io/bluetape4k/images/benchmark/CodecMatrixRuntimeAdapter.kt \
  benchmark/images-benchmark/src/benchmark/kotlin/io/bluetape4k/images/benchmark/CodecMatrixCapabilityMain.kt \
  benchmark/images-benchmark/src/benchmark/kotlin/io/bluetape4k/images/benchmark/CodecMatrixExperimentalFixtureMain.kt \
  benchmark/images-benchmark/src/test/kotlin/io/bluetape4k/images/benchmark/CodecMatrixCapabilityTest.kt
git commit -m "feat: gate experimental codec benchmarks"
```

### Task 5: Append-Only Evidence Finalize

**Complexity:** High
**Depends on:** Tasks 1 and 4
**Pattern skills:** `bluetape-kotlin-patterns`, `references/testing.md`
**Files:** `CodecMatrixFinalizeMain.kt`와 `CodecMatrixEvidenceFinalizerTest.kt`를 만든다.

- [ ] **Step 1: 실패하는 finalizer test를 작성한다.**

Latency/allocation/size artifact가 없는 measured cell, pre-benchmark `MEASURED`, `FAILED_SMOKE/ERROR`, missing/duplicate cell, hash mismatch, local-path/secret leakage, overwrite를 거부하는지 증명한다. Complete atomic promotion과 valid `supersedes` run ID가 accepted directory를 교체하지 않고 lineage를 기록하는지도 증명한다. 한 run에 대한 두 finalizer race, symlink ancestor/tree entry, atomic-move-unavailable, oversized/strict-JSON failure, forbidden numeric/native artifact가 붙은 Java 21 `N_A` expansion을 추가한다. 또한 `FAILED_SMOKE/ERROR`가 nonzero exit 전에 sanitized bounded failure ledger를 atomic하게 만드는지 증명한다. 같은 failed run ID는 rewrite/delete/mutate할 수 없다. Nonexistent/mismatched `--replaces-failed-attempt`는 거부된다. Valid replacement의 새 accepted manifest는 immutable failed ledger를 run ID와 manifest hash로 참조한다. Old ledger는 forward pointer로 업데이트하지 않는다.

- [ ] **Step 2: RED를 관찰한다.**

```bash
./gradlew :bluetape4k-images-benchmark:test \
  --tests "io.bluetape4k.images.benchmark.CodecMatrixEvidenceFinalizerTest" \
  -Pvips.impl=java25 --console=plain
```

- [ ] **Step 3: Validation과 promotion을 구현한다.**

Command는 `--run-id`와 optional `--supersedes`, `--replaces-failed-attempt` run ID만 받는다. Pinned repository working directory에서 정확한 staging root와 accepted root를 파생한다. 모든 ancestor를 `NOFOLLOW_LINKS`로 resolve하고, symlink/non-regular tree entry를 거부하며, run에 대한 exclusive sibling lock을 잡고, lock 아래에서 target absence를 다시 확인한다. No-replace atomic directory move를 요구하고, filesystem이 atomic guarantee를 제공하지 않으면 copy fallback 없이 실패한다. Latency와 GC-profiler JMH JSON을 parse하고 exact benchmark/scenario/backend key로 cell을 join하며, timing 밖에서 수집한 output size를 붙인다. Protocol, hash, terminal coverage, leakage, comparability metadata를 검증하고 `run-manifest.json`을 쓴 뒤 complete staged directory를 atomic하게 move한다. `supersedes` 값은 run을 연결할 뿐 replacement를 허용하지 않는다. Accepted evidence는 delete/rewrite/replace하지 않는다. Backend가 preflight에서 `N_A`이면 모든 expected cell을 `N_A`로 확장하고, 해당 backend의 latency/allocation/size/capability/native-init artifact를 거부한다.

Capability, smoke, measurement가 `FAILED_SMOKE`/`ERROR`로 끝나면 같은 task는 nonzero exit 전에 `benchmark/images-benchmark/docs/raw/failed/<run-id>/attempt-manifest.json`에 bounded sanitized failure ledger를 atomic하게 기록한다. 해당 attempt를 accepted run에 넣지 않는다. Operator는 새 run ID로 retry하기 전에 immutable failure ledger를 commit한다. Replacement run은 `--replaces-failed-attempt`로 해당 ledger를 명명해야 한다. Finalizer는 새 accepted manifest에 one-way link를 쓰기 전에 참조된 ledger의 run ID, terminal status, manifest hash를 검증한다.

- [ ] **Step 4: GREEN을 관찰하고 commit한다.**

```bash
./gradlew :bluetape4k-images-benchmark:test \
  --tests "io.bluetape4k.images.benchmark.CodecMatrixEvidenceFinalizerTest" \
  -Pvips.impl=java25 --console=plain
git add benchmark/images-benchmark/src/main/kotlin/io/bluetape4k/images/benchmark/CodecMatrixFinalizeMain.kt \
  benchmark/images-benchmark/src/test/kotlin/io/bluetape4k/images/benchmark/CodecMatrixEvidenceFinalizerTest.kt
git commit -m "feat: finalize codec benchmark evidence"
```

### Task 6: Gradle Task Graph 연결

**Complexity:** Medium
**Depends on:** Tasks 1-5
**Pattern skills:** `bluetape-kotlin-patterns`, `references/module-setup.md`, workflow `repository-hazards.md`
**Files:** Module `build.gradle.kts`를 수정하고 `CodecMatrixBenchmarkContractTest.kt`, `CodecMatrixBenchmarkTaskFunctionalTest.kt`를 만든다.

- [ ] **Step 1: 실패하는 Gradle source-contract test를 작성한다.**

Selector validation, exact task/config/entrypoint name, timing, include/exclude, task dependency, Java launcher/classpath, run-ID/manifest propagation, compile/generate/jar/build/check/test에 대한 non-dependency를 assert한다. `testImplementation(gradleTestKit())`는 functional task test에만 추가한다. Catalog alias/version은 추가하거나 바꾸지 않는다.

- [ ] **Step 2: RED를 관찰한다.**

```bash
./gradlew :bluetape4k-images-benchmark:test \
  --tests "io.bluetape4k.images.benchmark.CodecMatrixBenchmarkContractTest" \
  --tests "io.bluetape4k.images.benchmark.CodecMatrixBenchmarkTaskFunctionalTest" \
  -Pvips.impl=java25 --console=plain
```

Expected: Gradle 구현 전에는 selector/task/parameter-file/gating behavior가 없으므로 source-contract test와 functional task test가 모두 실패한다.

- [ ] **Step 3: 정확한 Gradle graph를 구현한다.**

Validate:

```kotlin
val vipsImpl = providers.gradleProperty("vips.impl").orElse("java25").get()
require(vipsImpl == "java21" || vipsImpl == "java25") {
    "vips.impl must be java21 or java25: $vipsImpl"
}
```

`images-vips-api`는 `benchmarkImplementation`으로 유지하고, 선택된 backend implementation만 `benchmarkRuntimeOnly`로 이동한다. Plugin의 `main` benchmark configuration은 experimental class를 제외한다. `codecMatrix`는 `VipsCodecMatrixBenchmark`만 포함하고, `codecMatrixAvif`와 `codecMatrixHeic`는 각각 정확한 두 method만 포함한다. 모든 focused config는 1 warmup, 3 measurement, 1 second, average time, ms, JSON, 1 fork, 1 thread를 사용한다. Default `benchmarkBenchmark`, focused task, direct GC profiler가 drift되지 않도록 두 matrix class에 같은 timing/mode/unit/fork/thread annotation을 적용한다. Contract test는 세 launch path를 모두 비교한다. Benchmark working directory는 repository root로 설정하고 native access는 benchmark-runtime launch에만 추가한다.

정확한 contract를 등록한다.

```text
syncCodecMatrixSourceFixtures          Sync / two checked-in inputs
codecMatrixPreflight                   JavaExec / CodecMatrixPreflightMain / main runtime
prepareCodecMatrixFixtures             JavaExec / CodecMatrixFixtureMain / main runtime
codecMatrixCapabilityReport            JavaExec / CodecMatrixCapabilityMain / benchmark runtime
prepareExperimentalCodecMatrixFixtures JavaExec / CodecMatrixExperimentalFixtureMain / benchmark runtime
finalizeCodecMatrixEvidence             JavaExec / CodecMatrixFinalizeMain / main runtime
stageCodecMatrixProfilerJar             non-native helper / exact Jar.archiveFile provider
benchmarkCodecMatrixBenchmark           generated stable JMH task
benchmarkCodecMatrixAvifBenchmark       generated AVIF JMH task
benchmarkCodecMatrixHeicBenchmark       generated HEIC JMH task
```

`prepareCodecMatrixFixtures`는 `codecMatrixPreflight`와 `syncCodecMatrixSourceFixtures`에 의존한다. 두 stable execution task(`benchmarkBenchmark`, `benchmarkCodecMatrixBenchmark`)는 stable preparation에만 의존한다. `codecMatrixCapabilityReport`는 preflight와 stable preparation에 의존한다. `prepareExperimentalCodecMatrixFixtures`는 output provider를 통해 `codecMatrixCapabilityReport`에 명시적으로 의존하고, eligibility와 stable fixture를 소비한다. 각 experimental JMH task는 `prepareExperimentalCodecMatrixFixtures`에 의존하므로 강제 순서는 preflight -> stable preparation -> capability -> experimental preparation -> JMH다. 모두 검증된 run ID와 exact manifest path를 공유한다. Compile/generate/jar/build/check/test는 fixture preparation, native capability probe, experimental preparation을 실행하지 않는다. Default benchmark graph는 capability나 experimental preparation에 도달하지 않는다.

`finalizeCodecMatrixEvidence`는 항상 검증된 run ID를 넘긴다. Optional Gradle provider `codec.matrix.supersedes`와 `codec.matrix.replacesFailedAttempt`는 각각 CLI argument `--supersedes`, `--replaces-failed-attempt`로 mapping한다. Blank, malformed, self-referential, nonexistent reference는 finalizer 실행 전에 실패한다. Contract/functional test는 documented rerun command가 실행 가능하도록 정확한 provider-to-argument mapping을 assert한다.

각 focused benchmark execution task는 `doFirst`에서 start instant를 캡처한다. `doLast`에서는 matching `build/reports/benchmarks/<configuration>/` directory 아래에 그 instant 이후 수정된 JSON report가 정확히 하나인지 요구하고, JMH configuration/row set을 검증한 뒤 selected run 아래에 `latency-<backend>-<configuration>.json`으로 atomic stage한다. Matching report가 0개 또는 여러 개이면 추측하지 않고 task를 실패시킨다.

kotlinx-benchmark 0.4.17은 모든 execution task를 generated JMH parameter file 하나만 argument로 받는 `JavaExec`로 만든다. 각 experimental format에 대해 `CodecMatrixExperimentalFixtureMain`은 eligibility가 확인된 뒤 동일한 protocol/report setting과 eligible direction의 정확한 `include:` line을 가진 validated replacement parameter file을 쓴다. Generated `JavaExec` task는 `onlyIf`로 zero-eligible format을 skip하고, `doFirst`에서 blocking status면 실패하며, `setArgs(listOf(exactParameterFile))`를 호출하고 run ID/backend/preflight/fixture/eligibility JVM property를 설정한다. Configuration time 이후 plugin의 static `BenchmarkConfiguration`을 mutate하지 않는다. Zero-eligible skip, one-direction exact parameter file, blocking failure, JVM-property propagation에 대한 Gradle TestKit functional test와 pure parameter-renderer test를 추가한다. 이는 가정한 task API가 아니라 설치된 0.4.17 `createJvmBenchmarkExecTask` source에 근거한다.

Plugin은 benchmark configuration별이 아니라 target별 JMH jar 하나를 만든다. 이 jar에는 experimental class를 포함한 compiled benchmark source set이 들어 있다. `stageCodecMatrixProfilerJar`는 `benchmarkBenchmarkJar`에 의존하고, 정확한 `Jar.archiveFile` provider를 읽으며, current commit/build freshness와 stable/experimental generated class를 검증하고, SHA-256을 기록한 뒤 run의 fixed staging path로 복사한다. Missing/stale/unexpected jar에서는 실패하며 `find -print -quit`를 사용하지 않는다.

- [ ] **Step 4: Name, invalid input, dry-run isolation을 검증한다.**

```bash
./gradlew :bluetape4k-images-benchmark:test \
  --tests "io.bluetape4k.images.benchmark.CodecMatrixBenchmarkContractTest" \
  --tests "io.bluetape4k.images.benchmark.CodecMatrixBenchmarkTaskFunctionalTest" \
  -Pvips.impl=java25 --console=plain
./gradlew :bluetape4k-images-benchmark:tasks --all --console=plain
./gradlew :bluetape4k-images-benchmark:tasks -Pvips.impl=invalid --console=plain
./gradlew :bluetape4k-images-benchmark:prepareCodecMatrixFixtures \
  -Pcodec.matrix.runId=issue-208-local-fixtures \
  -Pvips.impl=java25 --console=plain
./gradlew :bluetape4k-images-benchmark:codecMatrixPreflight --dry-run \
  -Pcodec.matrix.runId=issue-208-dry-run -Pvips.impl=java25 --console=plain
./gradlew :bluetape4k-images-benchmark:benchmarkBenchmark --dry-run \
  -Pcodec.matrix.runId=issue-208-dry-run -Pvips.impl=java25 --console=plain
./gradlew :bluetape4k-images-benchmark:benchmarkCodecMatrixAvifBenchmark --dry-run \
  -Pcodec.matrix.runId=issue-208-dry-run -Pvips.impl=java25 --console=plain
```

Expected: exact task와 entrypoint가 존재한다. Invalid input은 configuration time에 실패한다. `codecMatrixPreflight --dry-run`은 main-runtime/non-native로 남는다. Default execution은 sync/preflight/stable preparation을 가지지만 capability/experimental execution은 없다. AVIF는 preflight, preparation, capability, experimental preparation을 가진다. 또한 `build`, `check`, `test`, benchmark compile/generate/jar task를 dry-run해 여섯 execution/preparation task 중 어느 것도 도달하지 않음을 증명한다. Contract assertion은 sibling dependency만이 아니라 ordered capability output-provider edge를 검증한다.

- [ ] **Step 5: Existing-module registration이 변하지 않았는지 검증한다.**

```bash
./gradlew projects --console=plain
git diff -- settings.gradle.kts bom gradle/libs.versions.toml \
  .github/workflows/ci.yml .github/workflows/nightly.yml
```

Expected: benchmark module은 계속 listing되고 scoped branch diff는 비어 있다. Commit 후에는 같은 path에 대해 `git diff --exit-code origin/develop...HEAD --`를 사용한다. Working-tree-only diff만으로는 부족하다. 이는 new-module/BOM/catalog/CI/Nightly registration chain에 대한 concrete N/A evidence이지, 해당 surface를 편집할 권한이 아니다.

- [ ] **Step 6: Gradle wiring을 commit한다.**

```bash
git add benchmark/images-benchmark/build.gradle.kts \
  benchmark/images-benchmark/src/test/kotlin/io/bluetape4k/images/benchmark/CodecMatrixBenchmarkContractTest.kt \
  benchmark/images-benchmark/src/test/kotlin/io/bluetape4k/images/benchmark/CodecMatrixBenchmarkTaskFunctionalTest.kt
git commit -m "build: wire codec matrix benchmark tasks"
```

### Task 7: Stable PNG/WebP Matrix 추가

**Complexity:** Medium
**Depends on:** Tasks 2, 3, 6
**Pattern skills:** `bluetape-kotlin-patterns`, `references/testing.md`
**Files:** `VipsCodecMatrixBenchmark.kt`를 만들고 `CodecMatrixBenchmarkContractTest.kt`를 확장한다.

- [ ] **Step 1: 실패하는 source-contract assertion을 추가한다.**

정확한 네 method, `@Threads(1)`, `@Fork(1)`, `@Warmup(iterations = 1, time = 1, timeUnit = TimeUnit.SECONDS)`, `@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)`, `@BenchmarkMode(Mode.AverageTime)`, `@OutputTimeUnit(TimeUnit.MILLISECONDS)`, 두 scenario parameter, 명시적 `quality=85, effort=4, lossless=false, stripMetadata=true` profile, manifest/preflight loading, strict adapter use, `vipsAvailable`/`bh.consume(null)` 부재를 assert한다.

- [ ] **Step 2: RED를 관찰한다.**

Task 6 focused test를 실행한다. Stable JMH source assertion이 아직 없어서 실패해야 한다.

- [ ] **Step 3: Fail-fast state와 method를 구현한다.**

`@Param("web-photo", "profile")`를 가진 thread-scoped `VipsCodecMatrixState`를 사용한다. Trial setup은 matching run ID, preflight, selector, backend identity, manifest hash/dimension/magic/option을 검증하고, pinned JPEG/PNG/WebP를 load하며, concurrency 4에서 `CodecMatrixRuntimeAdapter`만 연다. 각 method는 image 하나를 열고 닫으며 forced output을 consume한다.

```kotlin
@Benchmark
fun encodeWebpFromJpeg(state: VipsCodecMatrixState, bh: Blackhole) {
    state.open(state.jpegBytes).use { image ->
        bh.consume(image.toBytes(VipsImageFormat.WEBP, state.encodeOptions))
    }
}

@Benchmark
fun decodeWebpToJpeg(state: VipsCodecMatrixState, bh: Blackhole) {
    state.open(state.webpBytes).use { image ->
        bh.consume(image.toBytes(VipsImageFormat.JPEG, state.encodeOptions))
    }
}
```

Matching PNG method도 구현한다. Runtime/transcode failure는 catch하지 않는다. Teardown은 reference를 release하지만 `shutdown()`은 호출하지 않는다.

- [ ] **Step 4: Test, 두 toolchain compile, commit을 수행한다.**

```bash
./gradlew :bluetape4k-images-benchmark:test \
  --tests "io.bluetape4k.images.benchmark.CodecMatrixBenchmarkContractTest" \
  -Pvips.impl=java25 --console=plain
./gradlew :bluetape4k-images-benchmark:benchmarkBenchmarkCompile \
  -Pvips.impl=java25 --console=plain
./gradlew :bluetape4k-images-benchmark:benchmarkBenchmarkCompile \
  -Pvips.impl=java21 --console=plain
git add benchmark/images-benchmark/src/benchmark/kotlin/io/bluetape4k/images/benchmark/VipsCodecMatrixBenchmark.kt \
  benchmark/images-benchmark/src/test/kotlin/io/bluetape4k/images/benchmark/CodecMatrixBenchmarkContractTest.kt
git commit -m "perf: add stable codec benchmark matrix"
```

### Task 8: Opt-In AVIF/HEIC Method 추가

**Complexity:** Medium
**Depends on:** Tasks 4, 6, 7
**Pattern skills:** `bluetape-kotlin-patterns`, `references/testing.md`
**Files:** `VipsExperimentalCodecMatrixBenchmark.kt`를 만들고 `CodecMatrixBenchmarkContractTest.kt`를 확장한다.

- [ ] **Step 1: 실패하는 experimental assertion을 추가한다.**

네 method name, `@OptIn(VipsIncubatingApi::class)`, distinct direction state, pinned target decode input, JPEG encode input, eligibility check, no no-op/fallback을 assert한다.

- [ ] **Step 2: RED를 관찰한다.**

Task 6 focused test를 실행한다. Experimental source assertion이 아직 없어서 실패해야 한다.

- [ ] **Step 3: Direction-specific state와 method를 구현한다.**

Common internal state에 delegate하는 AVIF/HEIC state를 만든다. Setup은 invoked direction이 matching `ELIGIBLE`인지 요구하고, pinned input hash/magic/dimension/producer manifest를 검증하며, evidence가 없으면 정확한 capability command와 함께 실패한다. Eligibility 생성 후 experimental fixture command는 Task 6에 지정된 generated `JavaExec` task가 소비할 정확한 kotlinx-benchmark parameter file을 쓰며, eligible direction include만 포함한다. `UNSUPPORTED`, `SKIPPED`, `N_A` direction은 manifest status로 남고 JMH row를 emit하지 않는다. Eligible direction이 0개이면 JMH launch 없이 완료하고, `FAILED_SMOKE`/`ERROR`는 task를 실패시킨다. 구현:

```kotlin
@Benchmark fun encodeAvifFromJpeg(state: VipsAvifCodecMatrixState, bh: Blackhole)
@Benchmark fun decodeAvifToJpeg(state: VipsAvifCodecMatrixState, bh: Blackhole)
@Benchmark fun encodeHeicFromJpeg(state: VipsHeicCodecMatrixState, bh: Blackhole)
@Benchmark fun decodeHeicToJpeg(state: VipsHeicCodecMatrixState, bh: Blackhole)
```

모든 invocation은 image 하나를 열고 닫으며 output byte를 consume한다.

- [ ] **Step 4: Compile, isolation 증명, commit을 수행한다.**

```bash
./gradlew :bluetape4k-images-benchmark:test \
  --tests "io.bluetape4k.images.benchmark.CodecMatrixBenchmarkContractTest" \
  -Pvips.impl=java25 --console=plain
./gradlew :bluetape4k-images-benchmark:benchmarkBenchmarkCompile \
  -Pvips.impl=java25 --console=plain
./gradlew :bluetape4k-images-benchmark:benchmarkBenchmark --dry-run \
  -Pcodec.matrix.runId=issue-208-default-isolation \
  -Pvips.impl=java25 --console=plain
git add benchmark/images-benchmark/src/benchmark/kotlin/io/bluetape4k/images/benchmark/VipsExperimentalCodecMatrixBenchmark.kt \
  benchmark/images-benchmark/src/test/kotlin/io/bluetape4k/images/benchmark/CodecMatrixBenchmarkContractTest.kt
git commit -m "perf: add experimental codec benchmark lanes"
```

### Task 9: Sequential Native Evidence 실행

**Complexity:** High
**Depends on:** Tasks 1-8 committed and clean
**Pattern skills:** `bluetape-kotlin-patterns`, benchmark hazards
**Files:** Finalization을 통해서만 `benchmark/images-benchmark/docs/raw/<run-id>/`를 생성한다.

- [ ] **Step 1: 깨끗한 accepted run 하나를 준비한다.**

Fail-closed workspace와 host prerequisite check 후에만 `issue-208-20260713-macos-arm64`를 사용한다. Command/JDK/libvips prerequisite가 없으면 native work 전에 중단한다. Architecture incompatibility는 structured preflight `N_A` path로 남긴다.

```bash
test -z "$(git status --porcelain)"
command -v jq
command -v vips
command -v java
test -n "$(/usr/libexec/java_home -v 21)"
test -n "$(/usr/libexec/java_home -v 25)"
jq --version
vips --version
uname -m
test ! -e benchmark/images-benchmark/build/codec-matrix/issue-208-20260713-macos-arm64
test ! -e benchmark/images-benchmark/docs/raw/issue-208-20260713-macos-arm64
test ! -e benchmark/images-benchmark/docs/raw/failed/issue-208-20260713-macos-arm64
```

- [ ] **Step 2: Java 21 preflight를 먼저 실행한다.**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) \
  ./gradlew :bluetape4k-images-benchmark:codecMatrixPreflight \
  -Pcodec.matrix.runId=issue-208-20260713-macos-arm64 \
  -Pvips.impl=java21 --console=plain
```

Expected on current macOS arm64: initialization 없이 x86_64 JNI에 대해 structured `N_A`가 나온다. Preflight가 compatibility를 증명하기 전에는 Java 21 capability나 native JMH를 호출하지 않는다.

- [ ] **Step 3: Fixture를 준비한 뒤 Java 25 capability와 stable latency를 실행한다.**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25) \
  ./gradlew :bluetape4k-images-benchmark:prepareCodecMatrixFixtures \
  -Pcodec.matrix.runId=issue-208-20260713-macos-arm64 \
  -Pvips.impl=java25 --console=plain
JAVA_HOME=$(/usr/libexec/java_home -v 25) \
  ./gradlew :bluetape4k-images-benchmark:codecMatrixCapabilityReport \
  -Pcodec.matrix.runId=issue-208-20260713-macos-arm64 \
  -Pvips.impl=java25 --console=plain
JAVA_HOME=$(/usr/libexec/java_home -v 25) \
  ./gradlew :bluetape4k-images-benchmark:benchmarkCodecMatrixBenchmark \
  -Pcodec.matrix.runId=issue-208-20260713-macos-arm64 \
  -Pvips.impl=java25 --console=plain
```

Expected: blocking stable status가 없고 latency row가 정확히 8개다.

- [ ] **Step 4: Eligible experimental task만 실행한다.**

Capability JSON을 `jq`로 점검한다. Matching cell이 `ELIGIBLE`일 때만 각 command를 실행한다. 그렇지 않으면 terminal status를 유지한다.

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25) \
  ./gradlew :bluetape4k-images-benchmark:benchmarkCodecMatrixAvifBenchmark \
  -Pcodec.matrix.runId=issue-208-20260713-macos-arm64 \
  -Pvips.impl=java25 --console=plain
JAVA_HOME=$(/usr/libexec/java_home -v 25) \
  ./gradlew :bluetape4k-images-benchmark:benchmarkCodecMatrixHeicBenchmark \
  -Pcodec.matrix.runId=issue-208-20260713-macos-arm64 \
  -Pvips.impl=java25 --console=plain
```

- [ ] **Step 5: Focused GC profiler를 실행한다.**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25) \
  ./gradlew :bluetape4k-images-benchmark:stageCodecMatrixProfilerJar \
  -Pcodec.matrix.runId=issue-208-20260713-macos-arm64 \
  -Pvips.impl=java25 --console=plain
jmh_jar=benchmark/images-benchmark/build/codec-matrix/issue-208-20260713-macos-arm64/staging/codec-matrix-profiler-java25.jar
root="$PWD"
java25=$(/usr/libexec/java_home -v 25)/bin/java
export DYLD_LIBRARY_PATH=/opt/homebrew/lib${DYLD_LIBRARY_PATH:+:$DYLD_LIBRARY_PATH}
test -f "$jmh_jar"
jar tf "$jmh_jar" | rg 'VipsCodecMatrixBenchmark|VipsExperimentalCodecMatrixBenchmark'
"$java25" --enable-native-access=ALL-UNNAMED \
  -Dcodec.matrix.backend=java25 \
  -Dcodec.matrix.runId=issue-208-20260713-macos-arm64 \
  -Dcodec.matrix.preflight="$root/benchmark/images-benchmark/build/codec-matrix/issue-208-20260713-macos-arm64/preflight-java25.json" \
  -Dcodec.matrix.fixtureManifest="$root/benchmark/images-benchmark/build/codec-matrix/issue-208-20260713-macos-arm64/fixtures/manifest.json" \
  -jar "$jmh_jar" '.*VipsCodecMatrixBenchmark.*' \
  -wi 1 -i 3 -w 1s -r 1s -f 1 -t 1 -bm avgt -tu ms -prof gc \
  -rf json \
  -rff benchmark/images-benchmark/build/codec-matrix/issue-208-20260713-macos-arm64/staging/allocation-java25.json
```

Expected: 같은 8개 stable row가 `gc.alloc.rate.norm`을 포함한다.

- [ ] **Step 6: Eligible experimental format의 profiler addendum을 실행한다.**

Timed task가 실행된 각 format에 대해 해당 format의 `ELIGIBLE` cell에서 method alternation을 파생한다. 두 direction을 hard-code하지 않는다. Empty alternation은 skip하고, fresh JVM에서 exact eligible regex를 실행하며, 별도 staging artifact를 쓴다. Finalizer는 profiler cell key가 대응하는 latency cell key와 정확히 같아야 한다고 요구한다.

```bash
eligibility=benchmark/images-benchmark/build/reports/benchmarks/codec-matrix/issue-208-20260713-macos-arm64/eligibility-java25.json
eligible_methods() {
  jq -r --arg format "$1" '
    .cells[] | select(.key.format == $format and .status == "ELIGIBLE") |
    if .key.direction == "encode" then
      (if $format == "AVIF" then "encodeAvifFromJpeg" else "encodeHeicFromJpeg" end)
    else
      (if $format == "AVIF" then "decodeAvifToJpeg" else "decodeHeicToJpeg" end)
    end' "$eligibility" | sort -u | paste -sd'|' -
}
avif_methods="$(eligible_methods AVIF)"
heic_methods="$(eligible_methods HEIC)"
test -z "$avif_methods" || \
"$java25" --enable-native-access=ALL-UNNAMED \
  -Dcodec.matrix.backend=java25 \
  -Dcodec.matrix.runId=issue-208-20260713-macos-arm64 \
  -Dcodec.matrix.preflight="$root/benchmark/images-benchmark/build/codec-matrix/issue-208-20260713-macos-arm64/preflight-java25.json" \
  -Dcodec.matrix.fixtureManifest="$root/benchmark/images-benchmark/build/codec-matrix/issue-208-20260713-macos-arm64/fixtures/manifest.json" \
  -Dcodec.matrix.eligibility="$root/benchmark/images-benchmark/build/reports/benchmarks/codec-matrix/issue-208-20260713-macos-arm64/eligibility-java25.json" \
  -jar "$jmh_jar" \
  ".*VipsExperimentalCodecMatrixBenchmark.*(${avif_methods}).*" \
  -wi 1 -i 3 -w 1s -r 1s -f 1 -t 1 -bm avgt -tu ms -prof gc -rf json \
  -rff benchmark/images-benchmark/build/codec-matrix/issue-208-20260713-macos-arm64/staging/allocation-java25-avif.json
test -z "$heic_methods" || \
"$java25" --enable-native-access=ALL-UNNAMED \
  -Dcodec.matrix.backend=java25 \
  -Dcodec.matrix.runId=issue-208-20260713-macos-arm64 \
  -Dcodec.matrix.preflight="$root/benchmark/images-benchmark/build/codec-matrix/issue-208-20260713-macos-arm64/preflight-java25.json" \
  -Dcodec.matrix.fixtureManifest="$root/benchmark/images-benchmark/build/codec-matrix/issue-208-20260713-macos-arm64/fixtures/manifest.json" \
  -Dcodec.matrix.eligibility="$root/benchmark/images-benchmark/build/reports/benchmarks/codec-matrix/issue-208-20260713-macos-arm64/eligibility-java25.json" \
  -jar "$jmh_jar" \
  ".*VipsExperimentalCodecMatrixBenchmark.*(${heic_methods}).*" \
  -wi 1 -i 3 -w 1s -r 1s -f 1 -t 1 -bm avgt -tu ms -prof gc -rf json \
  -rff benchmark/images-benchmark/build/codec-matrix/issue-208-20260713-macos-arm64/staging/allocation-java25-heic.json
```

- [ ] **Step 7: Complete evidence만 finalize하고 commit한다.**

```bash
./gradlew :bluetape4k-images-benchmark:finalizeCodecMatrixEvidence \
  -Pcodec.matrix.runId=issue-208-20260713-macos-arm64 \
  -Pvips.impl=java25 --console=plain
find benchmark/images-benchmark/docs/raw/issue-208-20260713-macos-arm64 \
  -type f -name '*.json' -exec jq empty {} +
git add benchmark/images-benchmark/docs/raw/issue-208-20260713-macos-arm64
git commit -m "perf: record codec runtime benchmark evidence"
```

`FAILED_SMOKE/ERROR`가 있으면 finalization은 `docs/raw/failed/<run-id>/attempt-manifest.json`을 기록한 뒤 실패한다. Ledger가 fixed reason code, bounded sanitized diagnostic, command/protocol fact, hash, original run ID만 포함하는지 검증한다. 진단 전에 이 ledger를 commit한다. Mitigation을 기록하고 새 run ID로 재시작하며, replacement lineage가 검증되도록 `-Pcodec.matrix.replacesFailedAttempt=<old-run-id>`를 넘긴다. Failed run ID는 재사용하거나 삭제하지 않는다.

### Task 10: Evidence를 Docs와 Chart에 게시

**Complexity:** Medium
**Depends on:** Task 9
**Pattern skills:** `bluetape-writer`, `bluetape-diagram` with `common.md` and `chart.md`
**Files:** Detailed report를 만들고 두 README locale을 수정하며, 조건이 충족되면 canonical SVG/PNG chart를 만든다.

- [ ] **Step 1: English report를 작성한다.**

Command, manifest link, fixture hash/dimension, runtime version, common status legend, full cell matrix, latency, `gc.alloc.rate.norm`, input/output byte, metric direction, native-allocation limitation, comparability key, reason, rerun guidance, supersession을 포함한다. “local evidence only; no cross-host or production-wide ranking.”이라고 명시한다. PNG와 lossy WebP가 동등한 visual quality를 가진다고 claim하지 않는다.

- [ ] **Step 2: 동등한 README summary를 추가한다.**

English summary와 자연스러운 Korean summary가 number, status, command, report link, chart link에서 일치하게 한다. Recorded-run command와 fresh run ID를 요구하는 rerun template을 분리한다. Accepted evidence를 수정할 때는 `-Pcodec.matrix.supersedes=<accepted-run-id>`를 보여 준다. Accepted 또는 failed run ID 재사용을 명시적으로 금지한다.

- [ ] **Step 3: Chart trigger를 적용한다.**

Finalizer와 같은 canonical comparability-key implementation을 사용한다. Commit/dirty state, OS/kernel/CPU/arch, JDK, libvips 및 codec-library version, scenario, source/derived/input hash, experimental producer manifest, codec option, thread count, libvips concurrency, fork, warmup/measurement count/duration, benchmark mode, time unit, profiler protocol, metric이 모두 일치할 때만 group은 comparable이다. Exact group 하나에 최소 2 row가 있으면 English label, unit, legend, truthful scale, separate scenario panel을 가진 grouped latency/output-size SVG와 PNG를 만든다. 그렇지 않으면 모든 differing key, exact comparable-row count, chart N/A를 기록한다.

- [ ] **Step 4: Asset과 docs를 검증한다.**

```bash
xmllint --noout docs/images/readme-charts/images-benchmark-codec-runtime-matrix-chart-01.svg
cairosvg docs/images/readme-charts/images-benchmark-codec-runtime-matrix-chart-01.svg \
  -o docs/images/readme-charts/images-benchmark-codec-runtime-matrix-chart-01.png -s 2
identify docs/images/readme-charts/images-benchmark-codec-runtime-matrix-chart-01.png
rg -n "codec-runtime-matrix-2026-07-13|MEASURED|UNSUPPORTED|SKIPPED|N/A" \
  benchmark/images-benchmark/README.md benchmark/images-benchmark/README.ko.md
rg -ni '/Users/|/home/|[A-Z]:\\|file:/{2,}|https?://[^/@[:space:]]+:[^/@[:space:]]+@|authorization|bearer[[:space:]]|password|passwd|api[_-]?key|secret|private[_-]?key|token[=:]|gh[pousr]_[A-Za-z0-9_]+|AKIA[0-9A-Z]{16}' \
  benchmark/images-benchmark/docs/raw \
  benchmark/images-benchmark/docs/codec-runtime-matrix-2026-07-13.md \
  benchmark/images-benchmark/README.md benchmark/images-benchmark/README.ko.md
git diff --check
```

각 README에서 모든 result number/unit, status, recorded/rerun command, report link, chart link를 추출해 parity ledger를 만든다. 두 set을 비교하고 unexplained difference가 0임을 review artifact에 기록한다. Token 존재만으로는 부족하다. Chart가 있으면 final coordinate change 후 chart audit을 실행하고 full-size PNG를 inspection한다. Leakage scan은 match가 없어야 한다.

- [ ] **Step 5: Docs와 triggered asset을 commit한다.**

```bash
git add benchmark/images-benchmark/docs/codec-runtime-matrix-2026-07-13.md \
  benchmark/images-benchmark/README.md benchmark/images-benchmark/README.ko.md
git add docs/images/readme-charts/images-benchmark-codec-runtime-matrix-chart-01.svg \
  docs/images/readme-charts/images-benchmark-codec-runtime-matrix-chart-01.png
git commit -m "docs: publish codec runtime benchmark matrix"
```

Chart trigger가 N/A이면 두 asset path는 생략한다.

### Task 11: Final Verification, Review, Lesson

**Complexity:** High
**Depends on:** Tasks 1-10
**Pattern skills:** `verification-before-completion`, Kotlin final checklist, full-feature verifier/performance/review references
**Files:** Code-review artifact와 `docs/lessons/2026-07-13-issue-208-codec-runtime-matrix.md`를 만든다.

- [ ] **Step 1: Fresh validation을 실행한다.**

```bash
./gradlew :bluetape4k-images-benchmark:test -Pvips.impl=java25 --console=plain
./gradlew :bluetape4k-images-benchmark:build -Pvips.impl=java25 --console=plain
./gradlew :bluetape4k-images-benchmark:benchmarkBenchmarkCompile -Pvips.impl=java25 --console=plain
./gradlew :bluetape4k-images-benchmark:benchmarkBenchmarkCompile -Pvips.impl=java21 --console=plain
./gradlew :bluetape4k-images-benchmark:tasks --all --console=plain
./gradlew detekt --console=plain
git diff --check
```

- [ ] **Step 2: Hazard와 exact spec/plan을 검증한다.**

Module/BOM/API/CI/Nightly/Kover registration change가 없고, default task isolation, append-only raw evidence, locale parity, triggered diagram ledger, 모든 acceptance row가 source/test/evidence/docs에 mapping됨을 증명한다. 유일한 catalog delta는 문서화된 temporary issue #208 serialization pin이다. 이 branch에서 `bluetape4k-dependencies`에도 추가되지 않았고 explicit removal condition이 있는지 검증한다. `NEEDS FIX`면 owning task로 돌아가고, `NEEDS REVIEW SCOPE`면 approval을 다시 연다.

- [ ] **Step 3: 여섯 code-review lens와 integration을 실행한다.**

Exact branch diff에 대해 performance, stability, security, operator/Ops, developer/API, user/caller를 review한다. `docs/review/2026-07-13-issue-208-codec-runtime-matrix-code-review.md`에 기록한다. 모든 P0/P1을 fix/revalidate하고 `P0=0, P1=0`일 때만 닫는다.

- [ ] **Step 4: Lesson을 작성하고 commit한다.**

Context, canonical manifest decision, directional smoke result, runtime N/A handling, measurement outcome, verification, review miss, future no-op/lazy-row guard를 기록한다.

```bash
git add docs/review/2026-07-13-issue-208-codec-runtime-matrix-code-review.md \
  docs/lessons/2026-07-13-issue-208-codec-runtime-matrix.md
git commit -m "docs: record codec benchmark lessons"
```

- [ ] **Step 5: PR boundary에서 중단한다.**

Clean branch와 full `origin/develop...HEAD` diff를 확인한다. Issue #208 milestone/label/assignee와 DoD를 보고한다. 명시적 authorization 없이 PR을 만들거나 merge하지 않는다.

## Documentation, Compatibility, Hazard 결정

- Public KDoc: N/A. 새 Kotlin type은 모두 internal benchmark harness component다.
- Production API: N/A. Published API/backend implementation 변경이 없다.
- README locales: 필요하며 동기화한다.
- CHANGELOG/release notes: 여기서는 N/A. Report와 later PR이 evidence를 담는다.
- Chart: 조건부다. Triggered output은 SVG, PNG, audit, full-size inspection을 요구한다.
- Module/BOM/settings/CI/Nightly/Kover: N/A. Module/coordinate/workflow/coverage surface 변경이 없다.
- Catalog: temporary issue #208 local serialization pin만 허용한다. Release-train central catalog tag가 governed alias를 게시하면 제거한다.
- Atomicfu: Java 25 backend setting은 변경하지 않는다. Benchmark module은 sequential toolchain을 지원하기 위해 사용하지 않는 JVM transform을 비활성화한다.
- Coroutines/Testcontainers/network: N/A. Coroutine API, container, external fixture fetch가 없다.
- Native concurrency: JMH thread 1개, libvips concurrency 4, sequential fresh process.
- Rollback: Code와 report/README/chart reference는 하나의 issue unit으로 revert할 수 있다. 그러나 accepted raw directory는 delete/rewrite하지 않는다. Evidence correction은 prior accepted run을 `supersedes`로 명명하는 fresh run을 추가한 뒤 documentation reference를 업데이트한다.

## Plan Completion Gate

- 모든 spec acceptance item을 mapping한다.
- Placeholder/type/path/task/command consistency scan을 통과한다.
- 여섯 plan-review perspective와 integration을 `P0=0, P1=0`으로 수렴한다.
- 명시적 user approval을 받는다.
- Implementation 전에 reviewed spec, plan, review artifact를 commit한다.
