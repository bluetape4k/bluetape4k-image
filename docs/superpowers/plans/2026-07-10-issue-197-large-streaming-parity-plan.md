# Issue #197 Large Streaming Benchmark Parity 구현 계획

> **Agentic worker 필수 지침:** 이 계획은 task 단위로 구현한다. 구현 표면은 `subagent-driven-development`(권장) 또는 `executing-plans`를 사용한다. 진행 추적에는 checkbox(`- [ ]`) 문법을 사용한다.

**목표:** Scrimage와 Java 25 FFM libvips 행이 같은 `decode -> resize -> JPEG encode` 작업을 수행하는, 재현 가능하고 색상을 보존하는 large-image benchmark 비교를 게시한다.

**아키텍처:** 기존 `kotlinx.benchmark` module과 deterministic fixture를 유지한다. Benchmark setup은 skipped/null vips 행을 게시하지 않도록 Java 25 FFM backend를 필수로 요구한다. 새 raw JSON만 report, localized README table, root README recommendation, chart의 source of truth로 사용한다.

**기술 스택:** Kotlin 2.3, Java 25, `kotlinx.benchmark`/JMH, Scrimage, libvips FFM, Gradle, bluetape4k assertion을 사용하는 JUnit 5, SVG/PNG chart asset.

---

## 고정 파일 구조

| Path | 책임 |
| --- | --- |
| `benchmark/images-benchmark/src/test/kotlin/io/bluetape4k/images/benchmark/ImageLargeStreamingBenchmarkContractTest.kt` | 색상 보존과 필수 FFM readiness를 test-first로 잠그는 source contract. |
| `benchmark/images-benchmark/src/benchmark/kotlin/io/bluetape4k/images/benchmark/ImageLargeStreamingBenchmark.kt` | Benchmark setup, workload parity, effective warmup setting, fail-fast FFM support. |
| `benchmark/images-benchmark/docs/raw/benchmark-large-streaming-2026-07-10-macos-java25.json` | Metadata scrub 후의 새 primary `kotlinx.benchmark` 결과. |
| `benchmark/images-benchmark/docs/raw/benchmark-large-streaming-jmh-gc-2026-07-10-macos-java25.json` | Metadata scrub 후의 같은 workload JMH GC-profiler addendum. |
| `benchmark/images-benchmark/docs/large-streaming-2026-06-05.md` | Shared active chart 없이 명확히 superseded 표시된 immutable historical report. |
| `benchmark/images-benchmark/docs/large-streaming-2026-07-10.md` | 두 2026-07-10 raw file에서만 파생되는 current report. |
| `benchmark/images-benchmark/README.md`, `benchmark/images-benchmark/README.ko.md` | 각 locale의 large-streaming reference가 current evidence만 가리킨다. |
| `README.md`, `README.ko.md` | Root recommendation은 current evidence만 가리키고 local-snapshot caveat를 유지한다. |
| `docs/scripts/generate-readme-visual-assets.py` | Target chart data, label, scale statement, refreshed report reference. |
| `docs/images/readme-charts/images-benchmark-large-streaming-chart-01.{svg,png}` | All-assets generator에서 유지할 유일한 regenerated chart artifact. |
| `docs/review/2026-07-10-issue-197-plan-3r-review.md` | Step 3-R six-lens plan review와 integration record. |
| `docs/lessons/2026-07-10-issue-197-large-streaming-parity.md` | PR 생성 전 durable benchmark-evidence lifecycle lesson. |

Public library API, dependency catalog, module registration, workflow YAML, Testcontainers configuration은 범위 밖이다. Concurrency tester도 적용하지 않는다. 이 변경은 concurrent contract가 아니라 단일 JMH benchmark state를 측정한다.

## Task 1: 실패하는 source-contract test로 behavior 잠금

**Complexity:** medium

**Files:**
- Create: `benchmark/images-benchmark/src/test/kotlin/io/bluetape4k/images/benchmark/ImageLargeStreamingBenchmarkContractTest.kt`
- Read: `benchmark/images-benchmark/src/benchmark/kotlin/io/bluetape4k/images/benchmark/ImageLargeStreamingBenchmark.kt`

**Pattern rules:** `$bluetape4k-code-patterns`를 적용하고 JUnit 5와 `bluetape4k-assertions`를 사용한다. 이 test는 승인된 design이 요구한 명시적 source-level regression guard이며 synthetic timing test가 아니다.

- [ ] **Step 1: Benchmark parity와 FFM readiness에 대한 RED test를 추가한다.**

```kotlin
package io.bluetape4k.images.benchmark

import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.assertions.shouldNotContain
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Test

class ImageLargeStreamingBenchmarkContractTest {

    private val benchmarkSource: String = sequenceOf(
        Path.of("src/benchmark/kotlin/io/bluetape4k/images/benchmark/ImageLargeStreamingBenchmark.kt"),
        Path.of("benchmark/images-benchmark/src/benchmark/kotlin/io/bluetape4k/images/benchmark/ImageLargeStreamingBenchmark.kt"),
    ).first(Files::isRegularFile).toFile().readText()

    @Test
    fun `large streaming benchmark preserves color and requires FFM vips`() {
        benchmarkSource shouldContain "@Warmup(iterations = 1"
        benchmarkSource shouldContain "vipsSupport = VipsLargePipelineSupport.createRequiredFfm()"
        benchmarkSource shouldContain "private fun transform(image: ImmutableImage): ImmutableImage ="
        benchmarkSource shouldContain "image.scaleTo(config.targetWidth, config.targetHeight)"
        benchmarkSource shouldNotContain "GrayscaleFilter"
        benchmarkSource shouldNotContain "GRAYSCALE_FILTER"
        benchmarkSource shouldNotContain ".filter("
        benchmarkSource shouldNotContain "JNI_RUNTIME_CLASS"
        benchmarkSource shouldNotContain "available: Boolean"
        benchmarkSource shouldNotContain "bh.consume(null)"
    }
}
```

- [ ] **Step 2: Test를 실행하고 의도한 RED failure를 확인한다.**

Run:

```bash
./gradlew :bluetape4k-images-benchmark:test --tests '*ImageLargeStreamingBenchmarkContractTest' --console=plain
```

Expected: 현재 source가 warmup 2회, `GrayscaleFilter` import/reference, optional vips availability contract/JNI fallback, unavailable vips 행에서 null consume을 유지하므로 `FAIL`이어야 한다.

- [ ] **Step 3: Repository convention이 intermediate local commit을 허용할 때만 test-only RED checkpoint를 commit한다.**

이 checkpoint에서는 publish하거나 PR을 열지 않는다. Convention이 맞지 않으면 Task 2가 green이 될 때까지 unstaged 상태로 유지한다.

## Task 2: Color parity와 필수 Java 25 FFM readiness 구현

**Complexity:** high

**Files:**
- Modify: `benchmark/images-benchmark/src/benchmark/kotlin/io/bluetape4k/images/benchmark/ImageLargeStreamingBenchmark.kt:1-420`
- Test: `benchmark/images-benchmark/src/test/kotlin/io/bluetape4k/images/benchmark/ImageLargeStreamingBenchmarkContractTest.kt`

**Pattern rules:** `$bluetape4k-code-patterns`를 적용한다. 기존 benchmark module을 재사용한다. Raw-JMH harness, fallback benchmark backend, 새 dependency, 새 public API는 도입하지 않는다.

- [ ] **Step 1: 가장 작은 parity edit를 적용한다.**
  - `GrayscaleFilter` import와 `GRAYSCALE_FILTER` companion value를 제거한다.
  - Direct JMH diagnostic과 authoritative `largeStreaming` Gradle configuration이 일치하도록 `@Warmup(iterations = 2, ...)`를 `@Warmup(iterations = 1, ...)`로 변경한다.
  - `transform(image)`가 정확히 `image.scaleTo(config.targetWidth, config.targetHeight)`를 반환하게 한다. 기존 Scrimage benchmark method는 현재 JPEG writer call을 유지한다.

- [ ] **Step 2: Setup을 fail-fast로 만들고 cleanup을 exception-safe로 만든다.**
  - `Files.createTempDirectory("bt4k-image-large-streaming-")` 이후 fixture state를 local value로 생성한다.
  - Local value를 benchmark field에 대입하기 전에 `VipsLargePipelineSupport.createRequiredFfm()`을 초기화한다.
  - Fixture creation 또는 FFM initialization이 실패하면 생성된 run directory만 삭제하고, cleanup failure는 original failure에 suppressed로 붙인 뒤 original failure를 다시 던진다.
  - Teardown의 중복 traversal을 같은 private recursive directory-delete helper로 대체한다. 정상 teardown은 owned run directory를 계속 제거한다.

- [ ] **Step 3: Optional/fallback vips support를 required FFM contract로 교체한다.**

```kotlin
fun createRequiredFfm(): VipsLargePipelineSupport {
    applyMacOsVipsLibraryPaths()
    return try {
        create(FFM_RUNTIME_CLASS, FFM_IMAGE_SUPPORT_CLASS, "ffmVipsImageOf")
    } catch (cause: Throwable) {
        throw IllegalStateException(
            "ImageLargeStreamingBenchmark requires Java 25 FFM libvips; " +
                "run with -Pvips.impl=java25 and a working libvips installation.",
            cause,
        )
    }
}
```

  - Constructor factory function은 non-null로 만들고 `available`을 제거한다.
  - 이 benchmark helper에서 Java 21 JNI fallback을 제거하고 모든 `if (!vipsSupport.available) { bh.consume(null); return }` branch를 제거한다. 이는 Java 21 production support를 변경하지 않는다. 잘못된 cross-backend Java 25 FFM 비교를 막을 뿐이다.

- [ ] **Step 4: Targeted contract test와 benchmark-module compilation을 실행한다.**

Run:

```bash
./gradlew :bluetape4k-images-benchmark:test --tests '*ImageLargeStreamingBenchmarkContractTest' --console=plain
./gradlew :bluetape4k-images-benchmark:benchmarkBenchmarkCompile -Pvips.impl=java25 --console=plain
```

Expected: 두 command 모두 `PASS`한다. Source guard는 setup이 required FFM factory를 호출하고 optional/fallback/null vips path가 남아 있지 않으며 benchmark가 FFM implementation으로 compile되는 것을 증명한다.

- [ ] **Step 5: Evidence 게시 전 FFM-init failure cleanup path를 실행한다.**

```zsh
residue_before=$(mktemp)
residue_after=$(mktemp)
find "${TMPDIR:-/tmp}" -maxdepth 1 -type d -name 'bt4k-image-large-streaming-*' -print | sort > "$residue_before"
set +e
JAVA_HOME=$(/usr/libexec/java_home -v 25) \
  JAVA_TOOL_OPTIONS='-Dvipsffm.libpath.vips.override=/definitely-missing/libvips.dylib' \
  ./gradlew --no-daemon :bluetape4k-images-benchmark:benchmarkLargeStreamingBenchmark \
  -Pvips.impl=java25 --console=plain > /tmp/issue-197-ffm-failure.log 2>&1
failure_status=$?
set -e
if [[ "$failure_status" -eq 0 ]]; then
  rg -q 'EXCEPTION: <ERROR>|Java 25 FFM libvips backend is required' /tmp/issue-197-ffm-failure.log
else
  rg -q 'Java 25 FFM libvips backend is required' /tmp/issue-197-ffm-failure.log
fi
find "${TMPDIR:-/tmp}" -maxdepth 1 -type d -name 'bt4k-image-large-streaming-*' -print | sort > "$residue_after"
diff -u "$residue_before" "$residue_after"
```

Expected: controlled invalid FFM path는 실행 가능한 Java 25 FFM requirement와 함께 실패하고, `diff`는 run-owned temp directory가 남지 않았음을 증명한다. Cleanup error가 original setup error를 대체하면 original diagnostic을 보존하고 cleanup failure를 suppressed로 붙인 뒤 이 step을 다시 실행한다.

## Task 3: Fresh benchmark evidence 생성 및 검증

**Complexity:** high

**Files:**
- Create: `benchmark/images-benchmark/docs/raw/benchmark-large-streaming-2026-07-10-macos-java25.json`
- Create: `benchmark/images-benchmark/docs/raw/benchmark-large-streaming-jmh-gc-2026-07-10-macos-java25.json`
- Read: `benchmark/images-benchmark/build.gradle.kts:98-107`

**Pattern rules:** `$bluetape4k-code-patterns` benchmark-module rule을 적용한다. Gradle `kotlinx.benchmark` task가 authoritative source이다. Direct JMH jar는 Gradle이 profiler를 노출하지 않는 별도 GC profiler addendum에만 허용한다.

- [ ] **Step 1: 실행 전 generated task를 다시 확인한다.**

Run:

```bash
./gradlew :bluetape4k-images-benchmark:tasks --all --console=plain | rg 'benchmarkLargeStreamingBenchmark'
```

Expected: generated `benchmarkLargeStreamingBenchmark` task가 정확히 하나 listing된다.

- [ ] **Step 2: Primary cross-backend measurement를 serial로 실행한다.**

Run:

```zsh
find benchmark/images-benchmark/build -type f -name '*.json' -delete
run_marker=$(mktemp)
JAVA_HOME=$(/usr/libexec/java_home -v 25) \
  ./gradlew :bluetape4k-images-benchmark:benchmarkLargeStreamingBenchmark \
  -Pvips.impl=java25 --console=plain
```

Expected: Java 25 FFM libvips가 초기화된 뒤에만 성공한다. Backend를 사용할 수 없으면 actionable failure이며 null result row가 아니다. Task 3 Step 3에서 새로 생성된 JSON을 발견하고 atomic하게 게시한다.

- [ ] **Step 3: Primary raw JSON을 값 사용 전에 검증한다.**

성공 후 새 generated JSON이 정확히 하나인지 확인하고 SHA-256을 보존한다. Metadata를 temporary file로 sanitize하고, sanitized file을 검증한 뒤 성공할 때만 date-stamped destination으로 atomic move한다. 후보가 0개이거나 여러 개이면 수용하지 않는다.

```zsh
raw_dest=benchmark/images-benchmark/docs/raw/benchmark-large-streaming-2026-07-10-macos-java25.json
raw_candidates=("${(@f)$(find benchmark/images-benchmark/build -type f -name '*.json' -newer "$run_marker" -print)}")
(( ${#raw_candidates[@]} == 1 ))
raw_source=$raw_candidates[1]
shasum -a 256 "$raw_source"
jq 'map({
  benchmark,
  params: {scenario: .params.scenario},
  forks, warmupIterations, warmupTime, measurementIterations, measurementTime, mode,
  primaryMetric: {score: .primaryMetric.score, scoreUnit: .primaryMetric.scoreUnit},
  jvm: "Java 25 runtime (sanitized)",
  jvmArgs: ([.jvmArgs[]? | select(. == "--enable-native-access=ALL-UNNAMED")])
})' \
  "$raw_source" > "$raw_dest.tmp"
```

Temporary result에 아래 fail-closed gate를 적용한 뒤, 성공할 때만 `mv "$raw_dest.tmp" "$raw_dest"`를 실행한다.

```bash
expected_methods='["scrimage_byteArray_pipeline","scrimage_path_pipeline","scrimage_inputStream_pipeline","scrimage_okioSourceSink_pipeline","scrimage_suspendedSourceSink_pipeline","vips_byteArray_pipeline","vips_path_pipeline","vips_inputStream_pipeline"]'
jq -e --argjson expected_methods "$expected_methods" '
  length == 16 and
  ([.[].params.scenario] | unique) == ["large-photo", "ocr-document"] and
  ([.[].benchmark | split(".")[-1]] | unique | sort) == ($expected_methods | sort) and
  all(.[];
    .forks == 1 and .warmupIterations == 1 and .warmupTime == "1 s" and
    .measurementIterations == 3 and .measurementTime == "1 s" and .mode == "avgt" and
    .primaryMetric.scoreUnit == "ms/op" and (.primaryMetric.score | type == "number") and
    .jvm == "Java 25 runtime (sanitized)" and
    ((.jvmArgs // []) | any(. == "--enable-native-access=ALL-UNNAMED")) and
    ((.jvmArgs // []) | all(. == "--enable-native-access=ALL-UNNAMED"))
  ) and
  all($expected_methods[] as $method;
    all(["large-photo", "ocr-document"][] as $scenario;
      any(.[]; (.benchmark | endswith("." + $method)) and .params.scenario == $scenario)
    )
  )
' "$raw_dest.tmp"
all_strings_clean=$(jq -e 'all(.. | strings; test("(?i)(^|[^[:alnum:]])/(Users|home|Library|private|var|tmp|opt|System|Volumes|Applications|usr|etc|dev|bin|sbin)(/|$)|[A-Za-z]:\\\\|token=|secret=|password=|authorization:|api[_-]?key=|proxy=|vipsffm\\.libpath|java\\.library\\.path|hostname=|file://" ) | not)' "$raw_dest.tmp")
test "$all_strings_clean" = true
mv "$raw_dest.tmp" "$raw_dest"
```

Native initialization cause, stack trace, library property, raw JVM property value는 report나 README에 복사하지 않는다. Gate가 실패하면 docs나 chart value를 만들지 말고 실패한 전제조건을 고친 뒤 이 task를 다시 실행한다.

- [ ] **Step 4: 같은 workload GC addendum을 생성, sanitize, 검증한다.**

```zsh
find benchmark/images-benchmark/build/benchmarks -type f -name '*-JMH.jar' -delete
JAVA_HOME=$(/usr/libexec/java_home -v 25) \
  ./gradlew :bluetape4k-images-benchmark:benchmarkBenchmarkJar \
  -Pvips.impl=java25 --console=plain
jmh_jars=("${(@f)$(find benchmark/images-benchmark/build/benchmarks -type f -name '*-JMH.jar' -print)}")
(( ${#jmh_jars[@]} == 1 ))
JAVA25=$(/usr/libexec/java_home -v 25)
gc_dest=benchmark/images-benchmark/docs/raw/benchmark-large-streaming-jmh-gc-2026-07-10-macos-java25.json
"$JAVA25/bin/java" --enable-native-access=ALL-UNNAMED \
  -jar "$jmh_jars[1]" '.*ImageLargeStreamingBenchmark.*' \
  -wi 1 -i 3 -f 1 -bm avgt -tu ms -prof gc -rf json -rff "$gc_dest.tmp"
jq 'map({
  benchmark,
  params: {scenario: .params.scenario},
  forks, warmupIterations, warmupTime, measurementIterations, measurementTime, mode,
  primaryMetric: {score: .primaryMetric.score, scoreUnit: .primaryMetric.scoreUnit},
  secondaryMetrics: {"gc.alloc.rate.norm": {score: .secondaryMetrics["gc.alloc.rate.norm"].score, scoreUnit: .secondaryMetrics["gc.alloc.rate.norm"].scoreUnit}},
  jvm: "Java 25 runtime (sanitized)",
  jvmArgs: ([.jvmArgs[]? | select(. == "--enable-native-access=ALL-UNNAMED")])
})' \
  "$gc_dest.tmp" > "$gc_dest.sanitized"
mv "$gc_dest.sanitized" "$gc_dest.tmp"
```

`$gc_dest.tmp`에 Step 3 primary validation expression을 재사용하고, atomic move 전에 아래 GC metric predicate를 추가한다.

```jq
all(.[]; .secondaryMetrics["gc.alloc.rate.norm"].scoreUnit == "B/op" and (.secondaryMetrics["gc.alloc.rate.norm"].score | type == "number"))
```

같은 post-scrub sensitive-pattern scan을 실행한 뒤 검증된 file을 `$gc_dest`로 atomic move한다. Expected: addendum은 같은 16 method/scenario row를 담고 managed-heap allocation claim만 뒷받침한다. Profiler를 실행할 수 없거나 structure/metadata gate가 실패하면 stale value를 유지하지 말고 active report/README에서 allocation table, recommendation, link를 제거한다.

GC post-scrub scan은 primary file과 동일한 fail-closed projection/string predicate를 사용한다.

```zsh
gc_strings_clean=$(jq -e 'all(.. | strings; test("(?i)(^|[^[:alnum:]])/(Users|home|Library|private|var|tmp|opt|System|Volumes|Applications|usr|etc|dev|bin|sbin)(/|$)|[A-Za-z]:\\\\|token=|secret=|password=|authorization:|api[_-]?key=|proxy=|vipsffm\\.libpath|java\\.library\\.path|hostname=|file://" ) | not)' "$gc_dest.tmp")
test "$gc_strings_clean" = true
mv "$gc_dest.tmp" "$gc_dest"
```

## Step 3-P handoff: 구현 리스크 통제

Pre-implementation architect, critic, verifier scan에서는 P0/P1 readiness blocker가 없었다. Step 4로 이동할 때 아래 통제를 유지한다.

- Controlled `vipsffm.libpath.vips.override` failure가 required FFM factory에 도달하고, actionable cause와 함께 실패하며, run-owned temp directory를 남기지 않는지 확인한다. JNI fallback이나 null-result row를 다시 도입하지 않는다.
- Setup exception contract를 보존한다. Run-owned directory만 정리하고 original throwable을 유지하며 cleanup failure는 suppressed로 붙인다.
- 16-row method/scenario gate, non-null numeric score, native-access metadata, invalid-library failure를 execution evidence로 취급한다. Structural JSON shape만으로는 충분하지 않다.
- Report를 파생하기 전에 Java/vendor/architecture와 raw SHA-256을 기록한다. Report, README, chart 간 rounded value를 게시 전에 비교한다.
- Source-test path resolution은 module project directory 아래에서 deterministic하게 유지하고, temporary manifest path는 고유하게 사용한다.

## Task 4: Stale derived evidence 교체와 target chart 재생성

**Complexity:** high

**Files:**
- Create: `benchmark/images-benchmark/docs/large-streaming-2026-07-10.md`
- Modify: `benchmark/images-benchmark/docs/large-streaming-2026-06-05.md`
- Modify: `benchmark/images-benchmark/README.md`
- Modify: `benchmark/images-benchmark/README.ko.md`
- Modify: `README.md`
- Modify: `README.ko.md`
- Modify: `docs/scripts/generate-readme-visual-assets.py:850`
- Modify: `docs/images/readme-charts/images-benchmark-large-streaming-chart-01.svg`
- Modify: `docs/images/readme-charts/images-benchmark-large-streaming-chart-01.png`

**Pattern rules:** `$bluetape4k-code-patterns` documentation rule과 `$bluetape4k-diagram`의 `references/common.md`, `references/chart.md`를 적용한다. Public README content는 locale별 English/Korean을 유지하고 generated chart label은 English로 둔다.

- [ ] **Step 1: Validated raw data에서 새 current report를 작성한다.**
  - Command, Java/vendor/architecture, generic Java 25 FFM libvips binding, date, generated-result source path/SHA-256, sanitized raw JSON link, effective setting, fixture dimension, `decode -> resize -> JPEG encode`, lower-is-better unit, 16개 primary value, GC allocation caveat를 포함한다.
  - 이 결과가 production ranking이 아니라 local comparable snapshot임을 명시하고, GC addendum에서 native-memory conclusion을 제외한다.
  - Historical report는 superseded evidence로만 연결한다.

- [ ] **Step 2: Historical raw JSON을 변경하지 않고 invalid report를 archive한다.**
  - 눈에 띄는 top-level supersession notice와 `large-streaming-2026-07-10.md` link를 추가한다.
  - 원래 table은 historical asymmetric evidence로 유지하되 shared active chart image를 제거하고, 해당 raw JSON이 current recommendation을 뒷받침하면 안 된다고 명시한다.

- [ ] **Step 3: 모든 active README surface를 동기화한다.**
  - 각 benchmark README locale에서 large-streaming 위치 두 곳과 class-reference guidance를 color-preserving wording과 refreshed report/raw link로 업데이트한다.
  - 각 locale의 `Running Benchmarks` GC-addendum command를 2026-07-10 raw path, Java 25, Java 25 FFM requirement로 업데이트한다.
  - Root `README.md`와 `README.ko.md` recommendation을 refreshed report만 사용하도록 변경하고 local-snapshot caveat를 유지한다.
  - 표시되는 모든 latency/allocation value를 2026-07-10 raw JSON까지 추적한다. GC addendum이 없으면 active README surface에서 allocation claim을 제거한다.

- [ ] **Step 4: Target chart input만 업데이트한다.**
  - Ordered raw-method mapping은 정확히 `scrimage_path_pipeline`, `scrimage_okioSourceSink_pipeline`, `scrimage_suspendedSourceSink_pipeline`, `vips_path_pipeline`, `vips_inputStream_pipeline`을 사용한다. 각 chart row의 두 값은 각각 `large-photo`, `ocr-document`이다.
  - 각 score는 validated raw JSON에서 추출하고 display에는 conventional half-up formatting으로 소수점 둘째 자리까지만 반올림한다. 같은 rounded numeric value를 report/README table/chart data/label에 사용한다. Generator의 기존 `g` label formatting은 유지한다.
  - Series name은 정확히 `large-photo`, `ocr-document`로 설정하고 `ms/op`를 유지하며 `log_scale=False`를 설정한다. Generator의 linear-scale path를 사용하고, 새 axis-label convention 없이 rendered SVG/PNG가 linear scale을 쓰는지 확인한다. Source reference는 `large-streaming-2026-07-10.md`로 변경한다.
  - Chart generation 전에 다섯 raw method/scenario score를 report/README data table과 비교한다. 불일치하면 generation을 막는다.

- [ ] **Step 5: Explicit asset allowlist로 generator를 실행한다.**

Generator는 두 asset root와 geometry summary를 다시 쓴다. Preflight는 user work overwrite 위험이 있으면 실패해야 한다.

```zsh
asset_roots=(
  docs/images/readme-charts
  docs/images/readme-diagrams
  docs/images/readme-diagrams/geometry-summary-generated-missing.txt
)
if [[ -n $(git status --porcelain -- $asset_roots) ]]; then
  print -u2 'STOP: generator-owned assets are already dirty or untracked'
  exit 1
fi
pre_generator_manifest=$(mktemp)
find docs/images/readme-charts docs/images/readme-diagrams -type f -print0 | sort -z | xargs -0 shasum -a 256 > "$pre_generator_manifest"
python3 docs/scripts/generate-readme-visual-assets.py
git status --short -- $asset_roots
```

아래 두 path만 유지한다.

```text
docs/images/readme-charts/images-benchmark-large-streaming-chart-01.svg
docs/images/readme-charts/images-benchmark-large-streaming-chart-01.png
```

Clean-baseline preflight 이후 이 root의 모든 change는 generator-owned이다. Tracked unrelated path는 `HEAD`로 복원하고, untracked unrelated path는 manifest 이후 생성된 것만 제거한다. 최종 changed-path set은 반드시 두-file allowlist와 같아야 한다. `geometry-summary-generated-missing.txt`도 비교에 포함한다. Preflight가 실패하면 generator를 실행하지 말고 user edit를 먼저 해결한다.

```zsh
allowed_paths=(
  docs/images/readme-charts/images-benchmark-large-streaming-chart-01.svg
  docs/images/readme-charts/images-benchmark-large-streaming-chart-01.png
)
all_changed=($(git status --porcelain -- $asset_roots | cut -c4-))
for asset_path in $all_changed; do
  [[ ${allowed_paths[(Ie)$asset_path]} -ne 0 ]] && continue
  if git ls-files --error-unmatch -- "$asset_path" >/dev/null 2>&1; then
    git restore --source=HEAD --worktree -- "$asset_path"
  else
    rm -f -- "$asset_path"
  fi
done
printf '%s\n' $allowed_paths | sort > /tmp/issue-197-chart-allowlist
git diff --name-only -- $asset_roots | sort > /tmp/issue-197-chart-after
diff -u /tmp/issue-197-chart-allowlist /tmp/issue-197-chart-after
test -z "$(git ls-files --others --exclude-standard -- $asset_roots)"
```

이후 target asset을 검증한다.

```bash
xmllint --noout docs/images/readme-charts/images-benchmark-large-streaming-chart-01.svg
CAIROSVG="$(command -v cairosvg)" \
"$CAIROSVG" \
  docs/images/readme-charts/images-benchmark-large-streaming-chart-01.svg \
  -o docs/images/readme-charts/images-benchmark-large-streaming-chart-01.png -s 2
file docs/images/readme-charts/images-benchmark-large-streaming-chart-01.png
python3 - <<'PY'
from pathlib import Path
import struct

png = Path('docs/images/readme-charts/images-benchmark-large-streaming-chart-01.png').read_bytes()
assert png[:8] == b'\x89PNG\r\n\x1a\n'
width, height = struct.unpack('>II', png[16:24])
assert width > 0 and height > 0
print(f'png={width}x{height}')
PY
```

PNG를 full size로 열고 label clipping, 잘못된 `JPEG`/`PNG` legend text, inconsistent scale/tick, README table과 다른 value를 발견하면 거부한다.

## Task 5: Verification, review evidence, lesson, commit 통합

**Complexity:** high

**Files:**
- Create: `docs/review/2026-07-10-issue-197-plan-3r-review.md`
- Create: `docs/review/2026-07-10-issue-197-code-6r-review.md`
- Create: `docs/lessons/2026-07-10-issue-197-large-streaming-parity.md`
- Modify: `docs/superpowers/plans/2026-07-10-issue-197-large-streaming-parity-plan.md` only to check completed steps or record reviewer repairs.

**Pattern rules:** `$bluetape4k-workflow`, `$bluetape4k-code-patterns`, `$verification-before-completion`, `$bluetape4k-diagram` evidence-ledger rule을 적용한다.

- [ ] **Step 1: 최종 local validation sequence를 serial로 실행한다.**

```bash
./gradlew :bluetape4k-images-benchmark:test --console=plain
./gradlew :bluetape4k-images-benchmark:benchmarkBenchmarkCompile -Pvips.impl=java25 --console=plain
JAVA_HOME=$(/usr/libexec/java_home -v 25) \
  ./gradlew :bluetape4k-images-benchmark:benchmarkLargeStreamingBenchmark \
  -Pvips.impl=java25 --console=plain
git diff --check
```

Expected: source-contract failure가 없고, unavailable-vips success path가 없으며, primary raw evidence가 valid이고, whitespace error가 없다. Benchmark run은 serial이다. Testcontainers나 concurrency test는 적용하지 않는다.

- [ ] **Step 2: Step 6-R code review를 수행한다.**
  - Performance, stability, security, operator/Ops, developer/API, library user의 여섯 independent read-only lens를 실행한다.
  - Finding을 `docs/review/2026-07-10-issue-197-code-6r-review.md`에 통합한다. PR 생성 전 P0/P1은 0이어야 한다.
  - Raw-data lineage, supersession, localized README parity, chart visual QA를 Tier 7 evidence-integrity check로 다룬다.

- [ ] **Step 3: Lesson을 기록하고 의도적 checkpoint를 commit한다.**
  - Cross-backend benchmark row는 mandatory backend readiness가 필요하고 derived document/chart는 asymmetric raw evidence를 함께 retire해야 한다는 점을 기록한다.
  - 구현 전 plan을 commit한 뒤, review 가능한 implementation/evidence commit을 Lore trailer와 함께 나눈다. Commit message는 모두 English로 유지한다.

- [ ] **Step 4: PR과 CI boundary를 지킨다.**
  - User가 명시적으로 요청한 뒤에만 push/create PR을 수행한다.
  - 요청되면 `debop`을 assign하고 milestone `0.4.0`을 설정하며 `Fixes #197`을 연결한다. Workflow PR template과 final `## DoD Status`를 사용하고, live PR body를 검증하며, Step 7-R을 실행하고 required CI를 기다린 뒤 user에게 merge를 요청한다.

## Acceptance-Criteria Mapping

| Approved design criterion | Plan task와 evidence |
| --- | --- |
| Asymmetric grayscale 없음 | Task 1 RED/GREEN contract test; Task 2 source edit; Task 5 test/benchmark. |
| vips 행이 실제 FFM work 실행을 증명 | Task 2 required FFM setup; Task 3 fresh raw JSON method/scenario check. |
| Effective measurement setting 재현 가능 | Task 2 warmup alignment; Task 3 task discovery, command, `jq` proof. |
| Old evidence가 current로 보이지 않음 | Task 4 dated report, visible supersession, README/root link replacement, active chart archive 제거. |
| README/report/chart가 단일 source 공유 | Task 3 validated raw data; Task 4 derived-artifact update와 target chart QA. |
| GC claim은 same-workload이거나 없음 | Task 3 GC addendum fallback; Task 4 addendum 실패 시 claim 제거. |
| Chart가 의미와 시각 모두 valid | Task 4 exact series label, SVG XML, CairoSVG render, full-size inspection, PNG file proof. |
| Evidence에 민감한 host metadata 없음 | Task 3 scrub check before raw JSON commit. |
| Temporary output cleanup 안전 | Task 2 exception-safe run-owned cleanup과 controlled FFM-init residue inspection. |

## Risk, Re-run Point, Stop Condition

| Risk | Re-run / rollback point | Stop condition |
| --- | --- | --- |
| Java 25 FFM libvips 초기화 실패 | Code/test change는 유지하되 current raw report/chart나 comparison을 만들지 않는다. Actionable environment blocker를 기록한다. | Refreshed cross-backend claim을 commit하지 않는다. |
| Fresh value가 2026년 6월 값과 다름 | 두 run을 비교/ranking하지 말고 invalid asymmetric value를 교체한다. | 모든 active surface가 하나의 fresh raw file에서 파생된다. |
| GC profiler 실패 | Current report/README에서 allocation claim과 link를 제거한다. | Stale managed-allocation conclusion이 남지 않는다. |
| Full generator가 unrelated asset을 변경 | Two-file allowlist 밖의 generator-owned path만 pre-run baseline으로 복원한다. | Target SVG/PNG는 남고 unrelated user change는 모두 보존된다. |
| Visual QA 실패 | Target chart data/label/scale만 조정하고 regenerate 후 XML/render/full-size inspection을 반복한다. | PNG가 읽기 쉽고 README table과 의미상 일치한다. |
