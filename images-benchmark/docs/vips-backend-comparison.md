# Vips Backend Comparison Benchmark

Issue #104 adds a dedicated JMH class for comparing the two libvips backends:

- `vips-java21`: JVips JNI backend, Java 21 toolchain.
- `vips-java25`: Panama FFM backend, Java 25 toolchain.

The benchmark classes are `VipsBackendBenchmark` for geometry operations and
`VipsBackendEncodeBenchmark` for encode operations. They keep the same method
names across both backends so result JSON files can be joined by benchmark name
and parameter:

| Benchmark | Workload | Input |
|-----------|----------|-------|
| `vips_resize` | resize to `1920x1080` and `1280x720` | 4K JPEG bytes |
| `vips_thumbnail` | thumbnail with matching max dimension | 4K JPEG bytes |
| `vips_crop` | crop top-left region to matching size | 4K JPEG bytes |
| `vips_encodeJpeg` | encode original image to JPEG | 4K JPEG bytes |

Lower `ms/op` is better.

## Current Comparable Results

These values use the Java 25 FFM full run captured for #104 on 2026-05-28 and
the existing macOS Java 25 scrimage baseline from
[`benchmark-results-2026-05-25.md`](benchmark-results-2026-05-25.md). Java 21
JNI is reported as `N/A` on this macOS arm64 host because the bundled JVips
dylib is x86_64 and cannot produce native measurements here.

![Vips backend comparison benchmark chart](../../docs/images/readme-charts/images-benchmark-vips-backend-comparison-chart-01.png)

| Operation | Parameter | scrimage baseline (ms/op) | Java 21 JNI (ms/op) | Java 25 FFM measured (ms/op) |
|-----------|-----------|---------------------------|---------------------|------------------------------|
| resize | 1920x1080 | 65.64 ± 0.76 | N/A | 0.200 ± 0.011 |
| resize | 1280x720 | 44.59 ± 0.50 | N/A | 0.203 ± 0.004 |
| thumbnail | 1920 | N/A | N/A | 0.230 ± 0.002 |
| thumbnail | 1280 | N/A | N/A | 0.240 ± 0.016 |
| crop | 1920x1080 | N/A | N/A | 0.060 ± 0.005 |
| crop | 1280x720 | N/A | N/A | 0.059 ± 0.006 |
| encode JPEG | original | 46.55 ± 0.75 | N/A | 15.55 ± 0.63 |

Raw Java 25 FFM result:
[`raw/benchmark-vips-backend-2026-05-28-macos-java25.json`](raw/benchmark-vips-backend-2026-05-28-macos-java25.json).

Historical Linux CI rows from
[`benchmark-results-2026-04-29.md`](benchmark-results-2026-04-29.md) remain
useful for release archaeology, but the #104 chart intentionally marks Java 21
JNI as `N/A` for the current measured host instead of mixing old Linux numbers
with current macOS values.

## Full Run

Run the two backends sequentially. Do not run them in parallel on the same host;
libvips native initialization and CPU contention make the comparison noisy.

```bash
# Java 21 / JVips JNI
JAVA_HOME=$(/usr/libexec/java_home -v 21) \
  ./gradlew :bluetape4k-images-benchmark:benchmarkBenchmark \
  -Pvips.impl=java21 --console=plain

# Java 25 / FFM
JAVA_HOME=$(/usr/libexec/java_home -v 25) \
  ./gradlew :bluetape4k-images-benchmark:benchmarkBenchmark \
  -Pvips.impl=java25 --console=plain
```

For focused runs, build the JMH jar for each backend and pass a JMH filter:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25) \
  ./gradlew :bluetape4k-images-benchmark:benchmarkBenchmarkJar \
  -Pvips.impl=java25 --console=plain

JAVA_HOME=$(/usr/libexec/java_home -v 25) \
  java --enable-native-access=ALL-UNNAMED \
  -jar images-benchmark/build/benchmarks/benchmark/jars/bluetape4k-images-benchmark-benchmark-jmh-0.1.3-JMH.jar \
  '.*VipsBackend.*' \
  -rf json \
  -rff images-benchmark/docs/raw/benchmark-vips-backend-java25.json
```

Repeat the same jar build/run with `-Pvips.impl=java21` and Java 21.

## Reporting

Store raw JMH JSON under `images-benchmark/docs/raw/` and summarize the stable
full-run values in this shape:

| Operation | Parameter | Java 21 JNI (ms/op) | Java 25 FFM (ms/op) | Faster backend |
|-----------|-----------|---------------------|---------------------|----------------|
| resize | 1920x1080 | TBD | TBD | TBD |
| thumbnail | 1920x1080 | TBD | TBD | TBD |
| crop | 1920x1080 | TBD | TBD | TBD |
| encode JPEG | original | TBD | TBD | TBD |

## Local Validation Notes

On 2026-05-28, `VipsBackendBenchmark` was smoke-tested on macOS arm64:

- Java 25 / FFM ran the focused JMH class successfully with synthetic images and
  Homebrew libvips paths auto-detected.
- Java 21 / JVips compiled and the JMH class launched, but native JNI execution
  skipped on this host because the bundled JVips dylib is x86_64 while the JVM is
  arm64. Use Linux CI or an architecture-compatible Java 21 host for real JNI
  measurements.
