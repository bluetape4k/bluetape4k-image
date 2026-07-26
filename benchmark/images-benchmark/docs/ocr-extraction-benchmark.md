# Tesseract OCR Extraction Benchmark (2026-07-26)

This report records a local Tess4J/Tesseract extraction snapshot for four
hash-pinned document fixtures. It measures the public `ImmutableImage.extractText`
path, which creates and configures a fresh Tesseract engine for every call, and
a separate preprocessing path that applies grayscale plus a safe 90-degree
normalization for the rotated fixture.

## Environment

| Item | Value |
|------|-------|
| Host | Apple M4 Pro, 12 logical processors |
| OS | macOS 26.5.2 (25F84), arm64 |
| JVM | Oracle GraalVM Java 25.0.3 LTS |
| OCR engine | Tesseract 5.5.3 through Tess4J |
| tessdata | `/opt/homebrew/share/tessdata` |
| Installed languages used | `eng`, `kor`, `jpn` |
| Latency command | `./gradlew :bluetape4k-images-benchmark:benchmarkOcrLatencyBenchmark --console=plain` |
| Throughput command | `./gradlew :bluetape4k-images-benchmark:benchmarkOcrThroughputBenchmark --console=plain` |
| Run shape | 1 thread, 1 fork, 3 x 1 s warmups, 5 x 1 s measurements |

The benchmark performs an actionable host preflight: `tesseract --list-langs`
must expose each fixture's languages and a tessdata directory must resolve from
`TESSDATA_PREFIX` or one of the documented local paths. This makes the native
prerequisite explicit instead of silently skipping a missing language package.

## Fixtures

Fixture PNG loading, SHA-256 checking, decoding, and one expected-token OCR
check run in JMH trial setup. They are excluded from the timed methods.

| Scenario | Dimensions | Languages | Timed preprocessing | Manifest SHA-256 |
|----------|------------|-----------|---------------------|-----------------|
| `clean-text` | 1600x1000 | `eng` | grayscale | `eeae6d9dc34fa8281befad9b288196a4fac955ca0b25bda77102b5b1b6079bb0` |
| `noisy-scan` | 1600x1000 | `eng` | grayscale | same manifest |
| `rotated-document` | 1000x1600 | `eng` | right rotation to `TYPE_INT_RGB`, then grayscale | same manifest |
| `multilingual-text` | 1600x1000 | `eng+kor+jpn` | grayscale | same manifest |

The fixture manifest records individual PNG hashes and ImageMagick/font
provenance. The multilingual verification requires stable common tokens while
the full Korean/Japanese glyph recognition remains an engine/model observation.

## Results

`AverageTime` is lower-is-better; throughput is a separate JMH observation and
is higher-is-better. It is not derived from the latency values.

| Scenario | Direct latency (ms/op) | Preprocess + extract (ms/op) | Direct throughput (ops/s) | Preprocess + extract (ops/s) |
|----------|------------------------|------------------------------|---------------------------|--------------------------------|
| `clean-text` | 217.921 +/- 11.427 | 194.128 +/- 2.548 | 4.607 +/- 0.180 | 5.111 +/- 0.116 |
| `noisy-scan` | 367.810 +/- 16.800 | 282.790 +/- 7.498 | 2.727 +/- 0.054 | 3.418 +/- 0.285 |
| `rotated-document` | 168.593 +/- 3.040 | 186.895 +/- 2.912 | 5.875 +/- 0.151 | 5.189 +/- 0.406 |
| `multilingual-text` | 370.003 +/- 2.103 | 394.922 +/- 3.548 | 2.704 +/- 0.043 | 2.518 +/- 0.045 |

![Tesseract OCR extraction benchmark chart](../../../docs/images/readme-charts/images-benchmark-ocr-extraction-chart-01.png)

### Managed Heap Allocation Addendum

The GC-profiler addendum runs the generated JMH jar for direct `clean-text`
extraction only. It reports `214.555 ms/op`, `1,417,421 B/op` from
`gc.alloc.rate.norm`, and one GC count in this local run. This is managed-heap
evidence only; Tesseract native memory and traineddata memory are outside this
number.

```bash
"$(/usr/libexec/java_home -v 25)/bin/java" \
  -jar benchmark/images-benchmark/build/benchmarks/benchmark/jars/bluetape4k-images-benchmark-benchmark-jmh-0.4.0-JMH.jar \
  '.*TesseractOcrExtractionBenchmark.extractText' -p scenario=clean-text \
  -wi 3 -i 5 -f 1 -bm avgt -tu ms -prof gc -rf json \
  -rff benchmark/images-benchmark/docs/raw/issue-203-20260726-macos-java25/ocr-gc-clean-text.json
```

## Raw Evidence

All values above come from immutable raw JSON under
[`raw/issue-203-20260726-macos-java25/`](raw/issue-203-20260726-macos-java25/):

| File | SHA-256 |
|------|---------|
| `ocr-latency.json` | `9b1a9bcbe0a6543b979eda577d74281ef4ada6e4bcc84d9e4db769c248e01151` |
| `ocr-throughput.json` | `fe3f93b8c53f20d5bd7dff6f43995c3c953901e21cb459da4fe951ba62c44137` |
| `ocr-gc-clean-text.json` | `0c644e551d569d29ad4e8df7e0e2c4385caabc15ef0999b6bf6be1f4bd1d3e52` |

## Interpretation and Limits

On this host, noisy and multilingual inputs take roughly twice the direct
latency of the rotated English document. Grayscale preprocessing improved the
clean and noisy rows in this snapshot, while rotation normalization adds cost
to the rotated document and preprocessing worsens the multilingual row. It is
therefore a workload-specific choice, not a universal OCR optimization.

These results include in-process Tess4J/Tesseract initialization and extraction
but exclude upload, image decode, socket, queue, storage, and multi-instance
contention. They are local native-engine observations, not a production
throughput promise. Services should use bounded OCR admission or background
work for expensive/noisy/multilingual documents and calibrate limits on their
deployment hardware.

Tracked by [issue #203](https://github.com/bluetape4k/bluetape4k-image/issues/203).
