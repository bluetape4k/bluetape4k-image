# Issue #166 Large Streaming Benchmark Design

## Context

- Issue: [#166](https://github.com/bluetape4k/bluetape4k-image/issues/166)
- Milestone: `0.3.0`
- Branch baseline: `develop`
- Work type: Type B Fast Track benchmark evidence expansion.

## Develop-Branch Evidence

- `develop` is clean and tracks `origin/develop`.
- CodeGraph lookup against `develop` did not match the current Kotlin symbols,
  so source verification used repository files directly after recording the
  CodeGraph gap.
- `images/src/main/kotlin/io/bluetape4k/images/ImmutableImageSupport.kt`
  already exposes `ByteArray`, `InputStream`, `Path`, Okio `Source`, buffered
  `Source`, suspended source, `Path`, Okio `Sink`, buffered `Sink`, and
  suspended sink boundaries for Scrimage-backed load/write.
- Existing benchmark evidence:
  - `benchmark/images-benchmark/docs/io-boundary-baseline-2026-05-29.md`
  - `benchmark/images-benchmark/docs/file-io-throughput-2026-05-29.md`
  - `benchmark/images-benchmark/docs/memory-profile-2026-05-29.md`

## Decision

Add evidence before changing public APIs. Issue #166 should measure full
large-image load-transform-write pipelines and classify Okio/suspended
boundaries as latency, throughput, or memory/lifecycle features. It should not
add a new production API in this PR.

## Scope

- Add one focused `kotlinx-benchmark` configuration and task:
  `benchmarkLargeStreamingBenchmark`.
- Add one benchmark class under `benchmark/images-benchmark/src/benchmark/kotlin`.
- Generate deterministic large fixtures in JMH setup instead of committing huge
  binary assets.
- Measure two scenarios:
  - `large-photo`: generated 4032x3024 photo-like JPEG.
  - `ocr-document`: generated 2480x3508 document/OCR-like JPEG.
- Measure Scrimage boundary rows:
  - `ByteArray`
  - `Path`
  - `InputStream` / `OutputStream`
  - Okio `Source` / `Sink`
  - suspended file source/sink
- Measure libvips Java 25 FFM rows when local native runtime is available:
  - `ByteArray`
  - `Path`
  - `InputStream` / `OutputStream`
- Record JMH GC-profiler managed heap allocation and GC counters as an
  addendum, because `kotlinx-benchmark` does not expose profiler flags through
  its Gradle DSL.
- Update benchmark README locale set, raw JSON, report, and chart assets.

## Non-Goals

- No new `images` public API in this PR.
- No committed large binary fixture.
- No Java 21 JNI row unless a compatible Linux/Java 21 host is explicitly run.
- No hard performance claim beyond a local comparable snapshot.

## Acceptance Criteria

- `:bluetape4k-images-benchmark:benchmarkBenchmarkCompile` passes.
- `:bluetape4k-images-benchmark:benchmarkLargeStreamingBenchmark -Pvips.impl=java25`
  passes locally and writes raw JSON under `benchmark/images-benchmark/docs/raw/`.
- JMH GC-profiler addendum writes managed-allocation JSON under
  `benchmark/images-benchmark/docs/raw/`.
- README tables and chart state `AverageTime ms/op`; lower is better.
- Report interpretation explicitly says whether Okio/suspended boundaries are
  latency, throughput, or memory/lifecycle features.
- Chart SVG/PNG pair validates and README embeds PNG only.
- P0/P1 review findings are zero before PR creation.

## Follow-Up Guidance

- Use this benchmark as evidence for #165 API positioning.
- Use the OCR-like document row as preprocessing evidence for #1.
- If future work changes public APIs, start from this evidence and write a
  separate API design spec before implementation.
