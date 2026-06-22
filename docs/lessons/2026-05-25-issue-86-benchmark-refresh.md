# Issue 86 Benchmark Refresh

## Context

#86 needed to be narrowed from already-completed chart creation to a current
`images-benchmark` rerun.

## Decision

Update the issue title/body to focus on rerunning the benchmark, then refresh
only the macOS Java 25 rows because Linux CI benchmarks were not rerun in this
work item.

## Outcome

`images-benchmark` completed successfully on GraalVM Java 25.0.3 with
`-Pvips.impl=java25`. README tables and chart assets now reflect the refreshed
macOS values while preserving historical CI Linux rows.

## Verification

- `./gradlew :bluetape4k-images-benchmark:benchmarkBenchmark -Pvips.impl=java25 --console=plain`
- Raw JSON copied to `benchmark/images-benchmark/docs/raw/benchmark-results-2026-05-25-macos-java25.json`

## Future Guard

When refreshing benchmark charts, state which environment rows were actually
rerun. Do not imply CI Linux rows are current unless they were rerun.
