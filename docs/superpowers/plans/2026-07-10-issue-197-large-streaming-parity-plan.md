# Issue #197 Large Streaming Benchmark Parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `subagent-driven-development` (recommended) or `executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Publish a reproducible, color-preserving large-image benchmark comparison whose Scrimage and Java 25 FFM libvips rows execute equivalent `decode -> resize -> JPEG encode` work.

**Architecture:** Keep the existing `kotlinx.benchmark` module and deterministic fixtures. Make benchmark setup require the Java 25 FFM backend instead of publishing skipped/null vips rows, then make the fresh raw JSON the only source for the report, localized README tables, root README recommendation, and chart.

**Tech Stack:** Kotlin 2.3, Java 25, `kotlinx.benchmark`/JMH, Scrimage, libvips FFM, Gradle, JUnit 5 with bluetape4k assertions, SVG/PNG chart assets.

---

## Locked File Structure

| Path | Responsibility |
| --- | --- |
| `benchmark/images-benchmark/src/test/kotlin/io/bluetape4k/images/benchmark/ImageLargeStreamingBenchmarkContractTest.kt` | Test-first source contract for color preservation and mandatory FFM readiness. |
| `benchmark/images-benchmark/src/benchmark/kotlin/io/bluetape4k/images/benchmark/ImageLargeStreamingBenchmark.kt` | Benchmark setup, workload parity, effective warmup setting, and fail-fast FFM support. |
| `benchmark/images-benchmark/docs/raw/benchmark-large-streaming-2026-07-10-macos-java25.json` | Fresh primary `kotlinx.benchmark` result after metadata scrubbing. |
| `benchmark/images-benchmark/docs/raw/benchmark-large-streaming-jmh-gc-2026-07-10-macos-java25.json` | Fresh same-workload JMH GC-profiler addendum after metadata scrubbing. |
| `benchmark/images-benchmark/docs/large-streaming-2026-06-05.md` | Immutable historical report marked visibly superseded, without the shared active chart. |
| `benchmark/images-benchmark/docs/large-streaming-2026-07-10.md` | Current report derived only from the two 2026-07-10 raw files. |
| `benchmark/images-benchmark/README.md`, `benchmark/images-benchmark/README.ko.md` | Both large-streaming references in each locale point only to current evidence. |
| `README.md`, `README.ko.md` | Root recommendations point only to current evidence and retain local-snapshot caveats. |
| `docs/scripts/generate-readme-visual-assets.py` | Target chart data, labels, scale statement, and refreshed report reference. |
| `docs/images/readme-charts/images-benchmark-large-streaming-chart-01.{svg,png}` | The only regenerated chart artifacts retained from the all-assets generator. |
| `docs/review/2026-07-10-issue-197-plan-3r-review.md` | Step 3-R six-lens plan review and integration record. |
| `docs/lessons/2026-07-10-issue-197-large-streaming-parity.md` | Durable benchmark-evidence lifecycle lesson before PR creation. |

No public library API, dependency catalog, module registration, workflow YAML, or Testcontainers configuration changes are in scope. Concurrency testers are not applicable: this change measures a single JMH benchmark state rather than a concurrent contract.

### Task 1: Lock the behavior with a failing source-contract test

**Complexity:** medium

**Files:**
- Create: `benchmark/images-benchmark/src/test/kotlin/io/bluetape4k/images/benchmark/ImageLargeStreamingBenchmarkContractTest.kt`
- Read: `benchmark/images-benchmark/src/benchmark/kotlin/io/bluetape4k/images/benchmark/ImageLargeStreamingBenchmark.kt`

**Pattern rules:** Apply `$bluetape4k-code-patterns`; use JUnit 5 plus `bluetape4k-assertions`. This is an explicit source-level regression guard required by the approved design, not a synthetic timing test.

- [ ] **Step 1: Add the RED test for benchmark parity and FFM readiness.**

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

- [ ] **Step 2: Run the test and confirm the expected RED failure.**

Run:

```bash
./gradlew :bluetape4k-images-benchmark:test --tests '*ImageLargeStreamingBenchmarkContractTest' --console=plain
```

Expected: FAIL because the current source has two warmups, imports/references `GrayscaleFilter`, retains an optional vips availability contract/JNI fallback, and still consumes null for unavailable vips rows.

- [ ] **Step 3: Commit the test-only RED checkpoint only if the repository convention permits an intermediate local commit; otherwise retain it unstaged until Task 2 is green.**

Do not publish or open a PR at this checkpoint.

### Task 2: Implement color parity and mandatory Java 25 FFM readiness

**Complexity:** high

**Files:**
- Modify: `benchmark/images-benchmark/src/benchmark/kotlin/io/bluetape4k/images/benchmark/ImageLargeStreamingBenchmark.kt:1-420`
- Test: `benchmark/images-benchmark/src/test/kotlin/io/bluetape4k/images/benchmark/ImageLargeStreamingBenchmarkContractTest.kt`

**Pattern rules:** Apply `$bluetape4k-code-patterns`. Reuse the existing benchmark module; do not introduce a raw-JMH harness, fallback benchmark backend, new dependency, or new public API.

- [ ] **Step 1: Make the smallest parity edit.**
  - Remove the `GrayscaleFilter` import and `GRAYSCALE_FILTER` companion value.
  - Change `@Warmup(iterations = 2, ...)` to `@Warmup(iterations = 1, ...)` so direct JMH diagnostics and the authoritative `largeStreaming` Gradle configuration agree.
  - Make `transform(image)` return exactly `image.scaleTo(config.targetWidth, config.targetHeight)`; every existing Scrimage benchmark method keeps its current JPEG writer call.

- [ ] **Step 2: Make setup fail fast and cleanup exception-safe.**
  - Create fixture state in local values after `Files.createTempDirectory("bt4k-image-large-streaming-")`.
  - Initialize `VipsLargePipelineSupport.createRequiredFfm()` before assigning the local values to benchmark fields.
  - If fixture creation or FFM initialization fails, delete only that created run directory, attach any cleanup failure as suppressed to the original failure, and rethrow the original failure.
  - Replace teardown's duplicated traversal with the same private recursive directory-delete helper; normal teardown still removes the owned run directory.

- [ ] **Step 3: Replace optional/fallback vips support with the required FFM contract.**

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

  - Make constructor factory functions non-null and remove `available`.
  - Remove the Java 21 JNI fallback from this benchmark helper and remove every `if (!vipsSupport.available) { bh.consume(null); return }` branch. This does not change Java 21 production support; it only prevents an invalid cross-backend Java 25 FFM comparison.

- [ ] **Step 4: Run the targeted contract test and benchmark-module compilation.**

Run:

```bash
./gradlew :bluetape4k-images-benchmark:test --tests '*ImageLargeStreamingBenchmarkContractTest' --console=plain
./gradlew :bluetape4k-images-benchmark:benchmarkBenchmarkCompile -Pvips.impl=java25 --console=plain
```

Expected: both commands PASS; the source guard proves setup calls the required FFM factory, no optional/fallback/null vips path remains, and the benchmark compiles with the FFM implementation.

- [ ] **Step 5: Exercise the FFM-init failure cleanup path before publishing evidence.**

```zsh
residue_before=$(mktemp)
residue_after=$(mktemp)
find "${TMPDIR:-/tmp}" -maxdepth 1 -type d -name 'bt4k-image-large-streaming-*' -print | sort > "$residue_before"
set +e
JAVA_HOME=$(/usr/libexec/java_home -v 25) \
  JAVA_TOOL_OPTIONS='-Dvipsffm.libpath.vips.override=/definitely-missing/libvips.dylib' \
  ./gradlew --no-daemon :bluetape4k-images-benchmark:benchmarkLargeStreamingBenchmark \
  -Pvips.impl=java25 --console=plain
failure_status=$?
set -e
test "$failure_status" -ne 0
find "${TMPDIR:-/tmp}" -maxdepth 1 -type d -name 'bt4k-image-large-streaming-*' -print | sort > "$residue_after"
diff -u "$residue_before" "$residue_after"
```

Expected: the controlled invalid FFM path fails with the actionable Java 25 FFM requirement while `diff` proves no run-owned temp directory remains. If the original setup error is replaced by a cleanup error, preserve the original diagnostic and attach cleanup failure as suppressed before rerunning this step.

### Task 3: Produce and validate fresh benchmark evidence

**Complexity:** high

**Files:**
- Create: `benchmark/images-benchmark/docs/raw/benchmark-large-streaming-2026-07-10-macos-java25.json`
- Create: `benchmark/images-benchmark/docs/raw/benchmark-large-streaming-jmh-gc-2026-07-10-macos-java25.json`
- Read: `benchmark/images-benchmark/build.gradle.kts:98-107`

**Pattern rules:** Apply `$bluetape4k-code-patterns` benchmark-module rules. The Gradle `kotlinx.benchmark` task is authoritative; a direct JMH jar is permitted only for the separate GC profiler because Gradle does not expose that profiler.

- [ ] **Step 1: Reconfirm the generated task before executing it.**

Run:

```bash
./gradlew :bluetape4k-images-benchmark:tasks --all --console=plain | rg 'benchmarkLargeStreamingBenchmark'
```

Expected: exactly the generated `benchmarkLargeStreamingBenchmark` task is listed.

- [ ] **Step 2: Run the primary cross-backend measurement serially.**

Run:

```zsh
find benchmark/images-benchmark/build -type f -name '*.json' -delete
run_marker=$(mktemp)
JAVA_HOME=$(/usr/libexec/java_home -v 25) \
  ./gradlew :bluetape4k-images-benchmark:benchmarkLargeStreamingBenchmark \
  -Pvips.impl=java25 --console=plain
```

Expected: success only after Java 25 FFM libvips initializes; an unavailable backend is an actionable failure, not a null result row. Task 3 Step 3 discovers and atomically publishes the newly produced JSON.

- [ ] **Step 3: Validate primary raw JSON before using its values.**

After the successful task, discover exactly one fresh generated JSON, preserve its SHA-256, sanitize metadata into a temporary file, validate the sanitized file, then atomically move it to the date-stamped destination. Do not accept zero or multiple candidates.

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

Validate the temporary result with this exact failing gate, then `mv "$raw_dest.tmp" "$raw_dest"` only on success:

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

Do not copy native initialization causes, stack traces, library properties, or raw JVM property values into reports or READMEs. If any gate fails, do not create docs or chart values; fix the failed precondition and rerun this task.

- [ ] **Step 4: Generate, sanitize, and validate the same-workload GC addendum.**

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

Reuse the Step 3 primary validation expression against `$gc_dest.tmp`, adding this required GC metric predicate before the atomic move:

```jq
all(.[]; .secondaryMetrics["gc.alloc.rate.norm"].scoreUnit == "B/op" and (.secondaryMetrics["gc.alloc.rate.norm"].score | type == "number"))
```

Run the same post-scrub sensitive-pattern scan, then atomically move the validated file to `$gc_dest`. Expected: the addendum contains the same 16 method/scenario rows and only supports managed-heap allocation claims. If the profiler cannot run or the structure/metadata gate fails, remove allocation tables, recommendations, and links from every active report/README instead of retaining stale values.

The GC post-scrub scan is the same fail-closed projection and string predicate as the primary file:

```zsh
gc_strings_clean=$(jq -e 'all(.. | strings; test("(?i)(^|[^[:alnum:]])/(Users|home|Library|private|var|tmp|opt|System|Volumes|Applications|usr|etc|dev|bin|sbin)(/|$)|[A-Za-z]:\\\\|token=|secret=|password=|authorization:|api[_-]?key=|proxy=|vipsffm\\.libpath|java\\.library\\.path|hostname=|file://" ) | not)' "$gc_dest.tmp")
test "$gc_strings_clean" = true
mv "$gc_dest.tmp" "$gc_dest"
```

### Task 4: Replace stale derived evidence and regenerate the target chart

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

**Pattern rules:** Apply `$bluetape4k-code-patterns` documentation rules and `$bluetape4k-diagram` with `references/common.md` and `references/chart.md`. Public README content remains English/Korean by locale; generated chart labels are English.

- [ ] **Step 1: Write the new current report from validated raw data.**
  - Include command, Java/vendor/architecture, generic Java 25 FFM libvips binding, date, generated-result source path/SHA-256, sanitized raw JSON links, effective settings, fixture dimensions, `decode -> resize -> JPEG encode`, lower-is-better units, all 16 primary values, and GC allocation caveats.
  - State that this is a local comparable snapshot, not a production ranking, and exclude native-memory conclusions from the GC addendum.
  - Link to the historical report as superseded evidence only.

- [ ] **Step 2: Archive the invalid report without mutating historical raw JSON.**
  - Add a visible top-level supersession notice and link to `large-streaming-2026-07-10.md`.
  - Retain the original table as historical asymmetric evidence, remove the shared active chart image, and state that its raw JSON must not support current recommendations.

- [ ] **Step 3: Synchronize every active README surface.**
  - Update both large-streaming locations in each benchmark README locale, including class-reference guidance, to color-preserving wording and refreshed report/raw links.
  - Update each locale's `Running Benchmarks` GC-addendum command to the 2026-07-10 raw path, Java 25, and Java 25 FFM requirement.
  - Update root `README.md` and `README.ko.md` recommendations to use only the refreshed report and preserve local-snapshot caveats.
  - Trace every displayed latency/allocation value to the 2026-07-10 raw JSON; if no GC addendum is available, remove allocation claims from active README surfaces.

- [ ] **Step 4: Update only the target chart input.**
  - Use exactly this ordered raw-method mapping: `scrimage_path_pipeline`, `scrimage_okioSourceSink_pipeline`, `scrimage_suspendedSourceSink_pipeline`, `vips_path_pipeline`, and `vips_inputStream_pipeline`; each chart row's two values are respectively `large-photo`, then `ocr-document`.
  - Extract each score from the validated raw JSON, round only for display to two decimal places using conventional half-up formatting, and use the same numeric rounded values in the report/README table/chart data and labels; retain the generator's existing `g` label formatting (so insignificant trailing zeroes may be omitted consistently).
  - Set series names to exactly `large-photo` and `ocr-document`, retain `ms/op`, set `log_scale=False` (the generator's linear-scale path), and verify the rendered SVG/PNG uses that linear scale without requiring a new axis-label convention. Change the source reference to `large-streaming-2026-07-10.md`.
  - Before chart generation, compare the five raw method/scenario scores against the report/README data table; a mismatch blocks generation.

- [ ] **Step 5: Run the generator with an explicit asset allowlist.**

The generator rewrites both asset roots and a geometry summary. Its preflight must fail rather than risk overwriting user work:

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

Keep only:

```text
docs/images/readme-charts/images-benchmark-large-streaming-chart-01.svg
docs/images/readme-charts/images-benchmark-large-streaming-chart-01.png
```

After the clean-baseline preflight, every change in these roots is generator-owned. Restore tracked unrelated paths to `HEAD`; remove only unrelated untracked paths created after the manifest; then require the final changed-path set to equal the two-file allowlist. Include `geometry-summary-generated-missing.txt` in this comparison. If the preflight fails, do not run the generator and resolve user edits first.

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

Then validate the target asset:

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

Open the PNG at full size and reject clipped labels, incorrect `JPEG`/`PNG` legend text, inconsistent scale/ticks, or values that disagree with the README table.

### Task 5: Integrate verification, review evidence, lesson, and commits

**Complexity:** high

**Files:**
- Create: `docs/review/2026-07-10-issue-197-plan-3r-review.md`
- Create: `docs/review/2026-07-10-issue-197-code-6r-review.md`
- Create: `docs/lessons/2026-07-10-issue-197-large-streaming-parity.md`
- Modify: `docs/superpowers/plans/2026-07-10-issue-197-large-streaming-parity-plan.md` only to check completed steps or record reviewer repairs.

**Pattern rules:** Apply `$bluetape4k-workflow`, `$bluetape4k-code-patterns`, `$verification-before-completion`, and `$bluetape4k-diagram` evidence-ledger rules.

- [ ] **Step 1: Execute the final local validation sequence serially.**

```bash
./gradlew :bluetape4k-images-benchmark:test --console=plain
./gradlew :bluetape4k-images-benchmark:benchmarkBenchmarkCompile -Pvips.impl=java25 --console=plain
JAVA_HOME=$(/usr/libexec/java_home -v 25) \
  ./gradlew :bluetape4k-images-benchmark:benchmarkLargeStreamingBenchmark \
  -Pvips.impl=java25 --console=plain
git diff --check
```

Expected: no source-contract failure, no unavailable-vips success path, valid primary raw evidence, and no whitespace errors. The benchmark run remains serial; no Testcontainers or concurrency test applies.

- [ ] **Step 2: Perform Step 6-R code review.**
  - Run six independent read-only lenses: performance, stability, security, operator/Ops, developer/API, and library user.
  - Integrate findings in `docs/review/2026-07-10-issue-197-code-6r-review.md`; P0/P1 must be zero before PR creation.
  - Treat raw-data lineage, supersession, localized README parity, and chart visual QA as Tier 7 evidence-integrity checks.

- [ ] **Step 3: Capture the lesson and commit intentional checkpoints.**
  - Record that cross-backend benchmark rows need mandatory backend readiness and that derived documents/charts must retire asymmetric raw evidence together.
  - Commit the plan before implementation, then make separately reviewable implementation/evidence commits using Lore trailers. Keep all commit messages English.

- [ ] **Step 4: PR and CI boundary.**
  - Push/create a PR only after the user explicitly requests it.
  - If requested, assign `debop`, set milestone `0.4.0`, link `Fixes #197`, use the workflow PR template with final `## DoD Status`, verify the live PR body, run Step 7-R, and wait for required CI before asking the user to merge.

## Acceptance-Criteria Mapping

| Approved design criterion | Plan task and evidence |
| --- | --- |
| No asymmetric grayscale | Task 1 RED/GREEN contract test; Task 2 source edit; Task 5 test/benchmark. |
| vips rows prove executed FFM work | Task 2 required FFM setup; Task 3 fresh raw JSON method/scenario checks. |
| Effective measurement settings are reproducible | Task 2 warmup alignment; Task 3 task discovery, command, and `jq` proof. |
| Old evidence cannot appear current | Task 4 dated report, visible supersession, README/root link replacement, no active chart in archive. |
| README/report/chart share one source | Task 3 validated raw data; Task 4 derived-artifact update and target chart QA. |
| GC claims are same-workload or absent | Task 3 GC addendum fallback; Task 4 removes claims if the addendum fails. |
| Chart is semantically and visually valid | Task 4 exact series labels, SVG XML, CairoSVG render, full-size inspection, PNG file proof. |
| Evidence has no sensitive host metadata | Task 3 scrub check before raw JSON commit. |
| Temporary output cleanup is safe | Task 2 exception-safe run-owned cleanup and controlled FFM-init residue inspection. |

## Risks, Re-run Points, and Stop Conditions

| Risk | Re-run / rollback point | Stop condition |
| --- | --- | --- |
| Java 25 FFM libvips cannot initialize | Keep code/test changes; do not create current raw report/chart or publish a comparison. Record the actionable environment blocker. | No refreshed cross-backend claim is committed. |
| Fresh values differ from June 2026 | Replace invalid asymmetric values rather than comparing/ranking the two runs. | All active surfaces derive from one fresh raw file. |
| GC profiler fails | Remove allocation claims and links from current report/READMEs. | No stale managed-allocation conclusion remains. |
| Full generator changes unrelated assets | Restore only generator-owned paths outside the two-file allowlist to the pre-run baseline. | Target SVG/PNG remain and all unrelated user changes remain untouched. |
| Visual QA fails | Adjust only target chart data/labels/scale, regenerate, and repeat XML/render/full-size inspection. | PNG is legible and semantically consistent with the README table. |
