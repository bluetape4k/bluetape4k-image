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
- Update the `images-benchmark-large-streaming-chart-01` input in
  `docs/scripts/generate-readme-visual-assets.py` and regenerate its SVG and
  PNG artifacts under `docs/images/readme-charts/`.
- Regenerate the benchmark result evidence and a date-stamped raw JSON copy in
  `benchmark/images-benchmark/docs/raw/` from a fresh supported
  `kotlinx.benchmark` run.
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

The previous 2026-06-05 raw JSON remains immutable as an audit artifact. The
old detailed report, rather than the JSON file itself, must visibly state that
the raw result is superseded by the asymmetric workload. Neither that old raw
JSON nor its old report may power a current README table, recommendation, or
chart. The refreshed raw JSON must use a date-stamped filename and be the sole
source for the report values, README values, and
`images-benchmark-large-streaming-chart-01` input.

The refreshed detailed report will use a new date-stamped filename. The old
report must retain a visible supersession link, and both README locales must
link only to the refreshed report.

## Regression Guard

No synthetic timing unit test will be introduced for this benchmark-only
change. The module currently has no benchmark test sources, and a timing test
would not establish workload parity. Instead, validation must include both:

1. A source-level guard showing that the large-streaming Scrimage transform
   contains resize and JPEG encode only, with no `GRAYSCALE_FILTER` reference.
2. A successful focused `kotlinx.benchmark` execution whose raw output contains
   all expected Scrimage and libvips rows for both scenarios.

This guard is intentionally narrow: grayscale remains a valid operation in
other benchmark classes and must not be removed elsewhere.

## Derived Artifact Contract

The report and both benchmark README locales must show identical refreshed
values and the same color-preserving workload description. The chart generator
must run after its `ChartSpec` values are updated, producing:

- `docs/images/readme-charts/images-benchmark-large-streaming-chart-01.svg`
- `docs/images/readme-charts/images-benchmark-large-streaming-chart-01.png`

The regenerated SVG must be XML-valid, and its displayed series/values must
match the refreshed README table. The chart source reference must name the
refreshed report rather than the superseded asymmetric result.

## Verification Contract

1. Inspect the generated benchmark task name with Gradle before running a
   filter or measurement.
2. Run the focused `kotlinx.benchmark` task for
   `ImageLargeStreamingBenchmark` using the recorded Java 25/libvips command.
3. Confirm raw output contains each expected Scrimage and libvips row and copy
   it to the documented date-stamped audit path.
4. Run the source-level `GRAYSCALE_FILTER` guard only against the
   large-streaming benchmark file.
5. Update the selected report lifecycle, both benchmark README locales, chart
   input, SVG, and PNG from that raw evidence.
6. Validate SVG XML, run focused benchmark module validation, and run
   `git diff --check`.
7. Review the implementation and documentation diff for workload parity and
   evidence integrity before a PR is opened.

## Risks and Mitigations

| Risk | Mitigation |
| --- | --- |
| Fresh numbers differ materially from the previous report | Replace, rather than compare against, the invalid asymmetric snapshot; retain environment and command metadata. |
| Native libvips is unavailable locally | Report the environment blocker and do not publish a refreshed cross-backend claim. |
| README/chart drift from raw JSON | Treat the raw benchmark output as the source and regenerate all derived values from that run. |

## Acceptance Criteria

- No Scrimage-only grayscale operation remains in the compared benchmark path.
- Benchmark text describes `resize -> JPEG encode`, preserving color.
- The raw JSON, report table, both benchmark README tables, and chart agree on
  every displayed result.
- The superseded asymmetric result cannot be mistaken for current evidence.
- `git diff --check`, SVG XML validation, the focused Gradle validation, and
  the targeted source-level parity guard pass.
