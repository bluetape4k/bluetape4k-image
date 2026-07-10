# Issue #197 Large Streaming Benchmark Parity Design

## Goal

Make the large-image benchmark a fair comparison of the normal color-preserving
pipeline implemented by Scrimage and libvips:

`decode -> resize -> JPEG encode`

The benchmark must not use a grayscale transform in only one backend. Grayscale
conversion is intentionally out of scope because it is not part of the normal
thumbnail/resize workload being compared.

## Current Problem

`ImageLargeStreamingBenchmark` applies `GRAYSCALE_FILTER` after resize in the
Scrimage path, while the libvips paths resize and encode directly. The benchmark
report and README describe both sides as including grayscale. Consequently, the
published comparison mixes different workloads and its existing measurements,
tables, and chart cannot remain authoritative after the code is corrected.

## Selected Design

Remove the Scrimage-only grayscale filter. Keep the existing deterministic
fixtures, dimensions, boundaries, JPEG options, and libvips behavior unchanged.
All compared rows will perform the color-preserving `decode -> resize -> JPEG
encode` contract.

This is the smallest change that restores workload parity without changing the
purpose of the benchmark or adding a color-conversion feature to either backend.

## Alternatives Considered

1. Add grayscale to libvips.
   - Rejected because it changes a normal color-preserving workload into a
     grayscale workload without a product requirement.
2. Keep the asymmetric implementation and relabel the results.
   - Rejected because the reported backend comparison would still not measure
     equivalent work.
3. Create a dedicated grayscale benchmark.
   - Deferred. It may be valuable only if a real grayscale/OCR preprocessing
     requirement needs its own performance evidence.

## Scope

- Update the shared Scrimage transform in
  `benchmark/images-benchmark/src/benchmark/kotlin/io/bluetape4k/images/benchmark/ImageLargeStreamingBenchmark.kt`.
- Create a new date-stamped detailed report under
  `benchmark/images-benchmark/docs/` and add a visible supersession notice to
  `large-streaming-2026-06-05.md`.
- Update the large-streaming sections in
  `benchmark/images-benchmark/README.md` and
  `benchmark/images-benchmark/README.ko.md` together.
- Update the large-image benchmark links and recommendations in root
  `README.md` and `README.ko.md` together.
- Update the `images-benchmark-large-streaming-chart-01` input in
  `docs/scripts/generate-readme-visual-assets.py` and regenerate its SVG and
  PNG artifacts under `docs/images/readme-charts/`.
- Regenerate the benchmark result evidence and a date-stamped raw JSON copy in
  `benchmark/images-benchmark/docs/raw/` from a fresh supported
  `kotlinx.benchmark` run.
- Regenerate a date-stamped GC-profiler addendum for the same parity workload,
  or remove all managed-allocation recommendations and links derived from the
  old asymmetric addendum.
- Keep recommendation language limited to the refreshed local comparable
  snapshot and retain its measurement caveats.

## Non-Goals

- Changing libvips image-processing behavior.
- Adding grayscale APIs or an OCR preprocessing benchmark.
- Changing fixture generation, target dimensions, output format, or encode
  options unless new root-cause evidence requires it.
- Treating the refreshed local result as a production-wide ranking.

## Measurement and Evidence Policy

The generated benchmark task is
`:bluetape4k-images-benchmark:benchmarkLargeStreamingBenchmark`. The
measurement run must use Java 25 and `-Pvips.impl=java25`, with the current
benchmark configuration of one warmup and three one-second average-time
iterations. The exact command, JVM, host architecture, libvips binding, and
measurement date must be recorded in the report.

The generated Gradle task is the authoritative measurement surface. The source
annotation must be aligned to the same one-warmup contract so a direct JMH
diagnostic run cannot silently select a different workload. The refreshed raw
JSON must prove one fork, one one-second warmup, three one-second measurement
iterations, average-time mode, and milliseconds as the output unit.

The previous 2026-06-05 raw JSON remains immutable as an audit artifact. The
old detailed report, rather than the JSON file itself, must visibly state that
the raw result is superseded by the asymmetric workload. Neither that old raw
JSON nor its old report may power a current README table, recommendation, or
chart. The refreshed raw JSON must use a date-stamped filename and be the sole
source for the report values, README values, and
`images-benchmark-large-streaming-chart-01` input.

The refreshed detailed report will use a new date-stamped filename. The old
report must retain a visible supersession link, and both README locales must
link only to the refreshed report. The old report must not embed the shared
current chart beside its invalid table; it is an archived explanation of the
superseded evidence, not an active benchmark page.

The refreshed GC-profiler addendum must use the same color-preserving workload
and a date-stamped raw filename. A direct JMH jar invocation is allowed only
for this addendum because the repository's Gradle `kotlinx.benchmark` task does
not expose JMH profiler configuration; the main benchmark result remains the
Gradle task.

## Cross-Backend Readiness Contract

This issue publishes a cross-backend comparison, so libvips unavailability is
not an optional skipped row. Before the benchmark result is accepted, the Java
25 FFM vips implementation must initialize successfully and every expected
vips row must execute the image pipeline rather than consume an unavailable
sentinel value. The benchmark must fail fast with an actionable error when that
precondition is not met. A successful Gradle exit code or a vips-named raw row
alone is not sufficient evidence.

## Regression Guard

No synthetic timing unit test will be introduced for this benchmark-only
change. The module currently has no benchmark test sources, and a timing test
would not establish workload parity. Instead, validation must include both:

1. A source-level guard showing that the large-streaming Scrimage transform
   contains resize and JPEG encode only, with no `GRAYSCALE_FILTER` reference.
2. A source-level readiness guard showing that a cross-backend run fails rather
   than publishes unavailable libvips rows.
3. A successful focused `kotlinx.benchmark` execution whose raw output proves
   the configured measurement settings and contains executed Scrimage and vips
   rows for both `large-photo` and `ocr-document` scenarios.

This guard is intentionally narrow: grayscale remains a valid operation in
other benchmark classes and must not be removed elsewhere.

## Derived Artifact Contract

The report, both large-streaming locations in each benchmark README locale,
and root README locale pair must show the same color-preserving workload
description and trace every displayed value to the refreshed raw data. The
chart generator must use backend/boundary categories and the two scenario
series `large-photo` and `ocr-document`; it must not label those series as
JPEG and PNG. Its scale label, tick spacing, and displayed values must express
one consistent scale. It produces:

- `docs/images/readme-charts/images-benchmark-large-streaming-chart-01.svg`
- `docs/images/readme-charts/images-benchmark-large-streaming-chart-01.png`

The regenerated SVG must be XML-valid, and its displayed series/values must
match the refreshed README table. The chart source reference must name the
refreshed report rather than the superseded asymmetric result.

The generator currently rewrites the complete visual set. The execution must
record an allowlist containing only the large-streaming SVG and PNG, inspect
the generated diff immediately, and restore every unrelated generated asset
before continuing. The target SVG must then be rendered with CairoSVG to the
target PNG and that PNG must be inspected at full size for legend, scale,
label, and clipping correctness. Verify the PNG signature and dimensions after
rendering.

## Verification Contract

1. Inspect the generated benchmark task name with Gradle before running a
   filter or measurement.
2. Validate Java 25 FFM/libvips readiness before the measurement and fail the
   cross-backend run if it is unavailable.
3. Run the focused Gradle `kotlinx.benchmark` task for
   `ImageLargeStreamingBenchmark` using the recorded Java 25/libvips command.
4. Confirm the raw output proves the effective fork, warmup, iteration,
   duration, mode, unit, and all executed backend/scenario rows; copy it to the
   documented date-stamped audit path.
5. Run the equivalent GC-profiler addendum with the same workload, or remove
   all allocation claims from active reports and README files.
6. Run the source-level `GRAYSCALE_FILTER` and libvips-readiness guards only
   against the
   large-streaming benchmark file.
7. Update the report lifecycle, both locations in both benchmark README
   locales, root README locales, GC evidence, chart input, SVG, and PNG from
   that raw evidence.
8. Validate SVG XML, render and inspect the target PNG, check PNG signature and
   dimensions, verify the generated-file allowlist, run focused benchmark module
   validation, and run `git diff --check`.
9. Scrub raw JSON and documented commands for absolute home paths, host/user
   identifiers, and token-like JVM properties before committing evidence.
10. Review the implementation and documentation diff for workload parity and
   evidence integrity before a PR is opened.

## Risks and Mitigations

| Risk | Mitigation |
| --- | --- |
| Fresh numbers differ materially from the previous report | Replace, rather than compare against, the invalid asymmetric snapshot; retain environment and command metadata. |
| Native libvips is unavailable locally | Report the environment blocker and do not publish a refreshed cross-backend claim. |
| README/chart drift from raw JSON | Treat the raw benchmark output as the source and regenerate all derived values from that run. |
| Benchmark setup leaves temporary files after a failure | Check for `bt4k-image-large-streaming-*` residue after failed setup or execution and remove only the run-owned directory. |

## Acceptance Criteria

- No Scrimage-only grayscale operation remains in the compared benchmark path.
- A cross-backend run fails when Java 25 FFM/libvips is not ready; published
  vips rows therefore prove executed image work.
- Benchmark text describes `resize -> JPEG encode`, preserving color.
- The primary and GC raw JSON, report, both locations in both benchmark README
  locales, root README locales, and chart agree on every displayed result.
- The superseded asymmetric result cannot be mistaken for current evidence.
- The chart legend uses `large-photo` and `ocr-document`, and its scale/ticks
  are consistent and legible in the full-size rendered PNG.
- `git diff --check`, SVG XML validation, PNG signature/dimension inspection,
  generated-file allowlist verification, focused Gradle validation, and the
  targeted source-level parity/readiness guards pass.
