# ZXing Barcode Extraction Benchmark — 2026-07-14

This report records one local ZXing 3.5.4 extraction snapshot for immutable QR,
Code 128, and no-result PNG fixtures. It covers extraction from an already
decoded `ImmutableImage`; PNG resource loading and decoding happen once during
JMH trial setup and are excluded from the timed method.

## Environment

| Item | Value |
|------|-------|
| Host | Apple M5, 10 logical processors |
| OS | macOS 26.5.1 (25F80), arm64 |
| JVM | GraalVM Java 25.0.3, Oracle Corporation |
| Gradle | 9.6.0 |
| Kotlin | 2.4.0 project toolchain |
| Provider | ZXing 3.5.4 |
| Run ID | `issue-272-20260714-macos-arm64-01` |

The latency and throughput modes were run sequentially:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25) \
./gradlew :bluetape4k-images-benchmark:benchmarkBarcodeLatencyBenchmark \
  -Pbarcode.benchmark.runId=issue-272-20260714-macos-arm64-01 \
  --console=plain

JAVA_HOME=$(/usr/libexec/java_home -v 25) \
./gradlew :bluetape4k-images-benchmark:benchmarkBarcodeThroughputBenchmark \
  -Pbarcode.benchmark.runId=issue-272-20260714-macos-arm64-01 \
  --console=plain

CPU="$(sysctl -n machdep.cpu.brand_string)"
JAVA_HOME=$(/usr/libexec/java_home -v 25) \
./gradlew :bluetape4k-images-benchmark:finalizeBarcodeBenchmarkEvidence \
  -Pbarcode.benchmark.runId=issue-272-20260714-macos-arm64-01 \
  -Pbarcode.benchmark.cpu="$CPU" \
  --console=plain
```

Both modes use one thread, one fork, three one-second warmups, and five
one-second measurement iterations.

## Immutable Fixtures

| Scenario | Dimensions | Expected result | SHA-256 |
|----------|------------|-----------------|---------|
| QR | 220×220 | `QR_CODE`: `bluetape4k-issue-272-qr` | `4338ae8e47278b7c2816028e7b40ca1466bae06560b02af708d3ef57f6adef62` |
| Code 128 | 360×120 | `CODE_128`: `BLUETAPE4K-272` | `df5da2cd0fb3bf17940a3def3bd1fb54d1f851c8a21e071d0316c0d3ef436782` |
| No result | 220×220 | Empty result | `86aad41769423ad85a979fefe109d00829044a1eba5d891547499413e3d9ff2b` |

The accepted run copies the strict fixture manifest and records its hash with
both raw report hashes. See the [immutable raw evidence](raw/issue-272-20260714-macos-arm64-01/).

## Results

Latency is observed JMH `AverageTime` in `ms/op` (lower is better). Throughput
is a separate observed JMH `Throughput` run in `ops/s` (higher is better); it is
not calculated as the reciprocal of latency.

| Scenario | Latency (ms/op) | Throughput (ops/s) | Expected result |
|----------|-----------------|--------------------|-----------------|
| QR | 0.174126 ± 0.001086 | 5702.142 ± 37.446 | One QR result |
| Code 128 | 0.112914 ± 0.000715 | 8839.015 ± 135.003 | One Code 128 result |
| No result | 0.271397 ± 0.009099 | 3690.012 ± 32.832 | Empty list |

## Interpretation and Limits

On this host and fixture set, the blank image required the most extraction
time, followed by QR and Code 128. These rows describe different workload
shapes for one provider; they do not compare providers, predict application
tail latency, or establish a cross-host production ranking. Result allocation
and ZXing extraction are included, while classpath I/O, PNG decoding, reader
construction, and expectation validation remain setup-only.

A chart is intentionally N/A. One provider, three workload shapes, and two
metrics with incompatible units and opposite directions are clearer in one
table than in a visual comparison. A future two-provider chart should use the
repository's complementary pastel pair rule.

The work is tracked by [issue #272](https://github.com/bluetape4k/bluetape4k-image/issues/272).
