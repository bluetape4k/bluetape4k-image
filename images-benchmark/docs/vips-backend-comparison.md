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
