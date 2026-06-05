# Issue #166 Large Streaming Benchmark

## Context

Issue #166 needed benchmark evidence for milestone `0.3.0` before deciding how
to position large-file Okio APIs and OCR preprocessing work.

## Decision

Analyze `develop` first, then write a spec before treating the worktree
implementation as authoritative. CodeGraph did not resolve the current symbols,
so the durable evidence came from direct source inspection plus the spec.

The benchmark uses `kotlinx-benchmark` as the primary execution path and a
separate JMH GC-profiler addendum for managed heap allocation because the
Gradle DSL does not expose profiler flags.

## Outcome

Added `ImageLargeStreamingBenchmark`, a focused `benchmarkLargeStreamingBenchmark`
task, raw latency JSON, raw GC-profiler JSON, README/README.ko summaries, a
detailed report, and a rendered chart.

Scrimage `Path`, Okio, and stream boundaries are similar for managed heap:
about 216-218 MiB/op for the large photo and 164-166 MiB/op for the OCR-like
document. vips `Path` stays under 1 MiB/op managed heap and is also the fastest
large-file row in the Java 25 FFM run.

## Verification

- `./gradlew :bluetape4k-images-benchmark:benchmarkBenchmarkCompile --console=plain`
- `./gradlew :bluetape4k-images-benchmark:benchmarkLargeStreamingBenchmark -Pvips.impl=java25 --console=plain`
- JMH jar GC profiler addendum with `-prof gc`
- `xmllint --noout docs/images/readme-charts/images-benchmark-large-streaming-chart-01.svg`
- `identify docs/images/readme-charts/images-benchmark-large-streaming-chart-01.png`
- `jq empty` on both raw JSON files
- `git diff --check`

## Future Guidance

For benchmark-backed API work, keep develop-branch analysis and spec approval
ahead of worktree implementation. Treat Scrimage Okio/suspended boundaries as
lifecycle/integration boundaries unless a future workload shows a measured
latency, throughput, or managed-allocation win.
