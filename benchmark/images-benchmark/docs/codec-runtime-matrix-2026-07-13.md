# Codec Runtime Matrix — 2026-07-13

Issue [#208](https://github.com/bluetape4k/bluetape4k-image/issues/208)
records a reproducible codec matrix for the libvips Java 21 JNI and Java 25
FFM backends. The accepted run is
`issue-208-20260713-macos-arm64-09` at Git SHA
`999b1e87f764a175d9887af9972ed41644e37f9e`.

## Status legend

- `MEASURED`: capability checks passed and both latency and allocation evidence
  were accepted.
- `N/A`: the runtime could not be evaluated on this host; this is not a zero or
  a failed benchmark.
- `UNSUPPORTED`: the selected runtime was available, but the requested codec or
  direction was not supported.
- `SKIPPED`: an eligible cell was intentionally not executed and therefore has
  no performance result.

## Result summary

| Backend | Runtime | Host result | Matrix cells |
|---------|---------|-------------|--------------|
| `java25` | FFM | `MEASURED` | 16 of 16 |
| `java21` | JVips JNI | `N/A` — `CAPABILITY_UNKNOWN`: JNI binary architecture is unavailable | 16 of 16 terminal `N/A` cells |

![Codec runtime latency](../../../docs/images/readme-charts/images-benchmark-codec-runtime-latency-chart-01.png)

![Codec encode output size](../../../docs/images/readme-charts/images-benchmark-codec-output-size-chart-01.png)

`encode` measures JPEG input to the named target codec. `decode` measures the
named codec input to JPEG output. AverageTime is in milliseconds per operation
and lower is better. Allocation is JMH `gc.alloc.rate.norm` managed-heap bytes
per operation; it does not include native libvips memory. Output bytes are a
codec/options snapshot, not a visual-quality ranking.

## Java 25 FFM measurements

### Profile image

The profile scenario center-crops `homer.jpg` to `512 x 512`.

| Format | Direction | AverageTime (ms/op) | Managed allocation (B/op) | Input (B) | Output (B) |
|--------|-----------|---------------------|---------------------------|-----------|------------|
| PNG | encode | 5.945 | 456,466 | 32,205 | 225,576 |
| PNG | decode | 2.156 | 69,491 | 257,323 | 32,181 |
| WebP | encode | 10.415 | 47,147 | 32,205 | 19,914 |
| WebP | decode | 2.605 | 68,146 | 19,252 | 31,467 |
| AVIF | encode | 51.134 | 65,065 | 32,205 | 23,605 |
| AVIF | decode | 4.339 | 69,589 | 23,605 | 31,999 |
| HEIC | encode | 60.350 | 132,465 | 32,205 | 55,961 |
| HEIC | decode | 7.681 | 70,063 | 55,961 | 32,200 |

### Web photo

The web-photo scenario center-crops `cafe.jpg` to `1920 x 1080`.

| Format | Direction | AverageTime (ms/op) | Managed allocation (B/op) | Input (B) | Output (B) |
|--------|-----------|---------------------|---------------------------|-----------|------------|
| PNG | encode | 80.132 | 6,984,121 | 429,306 | 3,485,741 |
| PNG | decode | 18.825 | 868,771 | 4,106,689 | 428,521 |
| WebP | encode | 106.405 | 679,169 | 429,306 | 332,028 |
| WebP | decode | 20.020 | 842,109 | 327,690 | 414,997 |
| AVIF | encode | 511.268 | 743,568 | 429,306 | 364,133 |
| AVIF | decode | 38.751 | 870,204 | 364,133 | 428,429 |
| HEIC | encode | 339.555 | 1,521,700 | 429,306 | 754,672 |
| HEIC | decode | 73.038 | 871,753 | 754,672 | 429,295 |

These values describe this host, libvips build, fixture recipe, codec options,
and short JMH protocol. They do not establish a universal codec ranking. In
particular, AVIF and HEIC are incubating APIs and output sizes cannot be
compared as quality-equivalent without a separate visual-quality study.

## Fixture provenance

Both committed source photographs are converted with the deterministic
`cover-center-crop-v1` recipe. Stable fixtures use JPEG quality 85 without
progressive output, PNG compression 4, and lossy WebP quality 85/method 4.

| Scenario | Source | Source shape / bytes | Derived shape | Stable input SHA-256 |
|----------|--------|----------------------|---------------|---------------------|
| `web-photo` | `cafe.jpg` | `4032 x 3024` / 3,061,079 B | `1920 x 1080` | JPEG `5b6e2e599160…`; PNG `51948986bd7a…`; WebP `8d26bd6c6c0c…` |
| `profile` | `homer.jpg` | `1248 x 702` / 83,973 B | `512 x 512` | JPEG `6bca6f3aa1f7…`; PNG `ae31030716d9…`; WebP `36893de3ea32…` |

AVIF and HEIC inputs were produced only after capability checks by Java 25 FFM
with libvips `8.18.4`. Their hashes and magic signatures are recorded in the
[experimental fixture manifest](raw/issue-208-20260713-macos-arm64-09/fixtures/experimental-java25/manifest.json).

## Environment and preflight

| Fact | Java 21 JNI | Java 25 FFM |
|------|-------------|-------------|
| OS / architecture | macOS `26.5.1`, arm64, Apple M5 | same host |
| JDK | Oracle `21.0.11+9-LTS-jvmci-23.1-b92` | Oracle `25.0.3+9-LTS-jvmci-25.1-b19` |
| Native access | disabled | enabled |
| Loader path | available | available |
| Git state | clean, `999b1e87f764…` | clean, `999b1e87f764…` |
| Preflight | `N/A` | `ELIGIBLE` |

The Java 21 preflight stopped before JNI initialization because the JNI binary
architecture could not be established for this arm64 host. Rerun Java 21 on a
host with a compatible JVips JNI binary and keep the same fixture recipe and
JMH protocol; do not reinterpret these `N/A` cells as Java 25 wins.

## Reproduction protocol

Run native/JNI/FFM steps sequentially from a clean checkout. Use a new run ID;
accepted evidence directories are immutable. The external dependency catalog
property below is needed only until the matching catalog release is available.

```bash
RUN_ID=issue-208-YYYYMMDD-host-01
ROOT="$PWD"
CATALOG=/path/to/bluetape4k-dependencies/gradle/libs.versions.toml
export DYLD_LIBRARY_PATH=/opt/homebrew/lib${DYLD_LIBRARY_PATH:+:$DYLD_LIBRARY_PATH}

test -z "$(git status --porcelain)"
command -v jq
command -v vips
test -n "$(/usr/libexec/java_home -v 21)"
test -n "$(/usr/libexec/java_home -v 25)"
test ! -e "benchmark/images-benchmark/build/codec-matrix/$RUN_ID"
test ! -e "benchmark/images-benchmark/docs/raw/$RUN_ID"

JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew \
  :bluetape4k-images-benchmark:codecMatrixPreflight \
  -Pcodec.matrix.runId="$RUN_ID" -Pvips.impl=java21 \
  -Pbluetape4kDependenciesCatalogPath="$CATALOG" --console=plain

JAVA_HOME=$(/usr/libexec/java_home -v 25) ./gradlew \
  :bluetape4k-images-benchmark:codecMatrixCapabilityReport \
  :bluetape4k-images-benchmark:benchmarkCodecMatrixBenchmark \
  -Pcodec.matrix.runId="$RUN_ID" -Pvips.impl=java25 \
  -Pbluetape4kDependenciesCatalogPath="$CATALOG" --console=plain

# Run each experimental task only when the matching capability cells are ELIGIBLE.
JAVA_HOME=$(/usr/libexec/java_home -v 25) ./gradlew \
  :bluetape4k-images-benchmark:benchmarkCodecMatrixAvifBenchmark \
  :bluetape4k-images-benchmark:benchmarkCodecMatrixHeicBenchmark \
  -Pcodec.matrix.runId="$RUN_ID" -Pvips.impl=java25 \
  -Pbluetape4kDependenciesCatalogPath="$CATALOG" --console=plain

JAVA_HOME=$(/usr/libexec/java_home -v 25) ./gradlew \
  :bluetape4k-images-benchmark:stageCodecMatrixProfilerJar \
  -Pcodec.matrix.runId="$RUN_ID" -Pvips.impl=java25 \
  -Pbluetape4kDependenciesCatalogPath="$CATALOG" --console=plain
```

For allocation, launch the staged JMH jar in one fresh JVM each for stable,
AVIF, and HEIC. Absolute manifest paths are required. The accepted run used
both experimental directions because all matching capability cells were
`ELIGIBLE`; omit any pattern that is not eligible on a rerun.

```bash
JAVA25=$(/usr/libexec/java_home -v 25)/bin/java
JMH_JAR="$ROOT/benchmark/images-benchmark/build/codec-matrix/$RUN_ID/staging/codec-matrix-profiler-java25.jar"
PREFLIGHT="$ROOT/benchmark/images-benchmark/build/codec-matrix/$RUN_ID/preflight-java25.json"
FIXTURES="$ROOT/benchmark/images-benchmark/build/codec-matrix/$RUN_ID/fixtures/manifest.json"
ELIGIBILITY="$ROOT/benchmark/images-benchmark/build/reports/benchmarks/codec-matrix/$RUN_ID/eligibility-java25.json"
STAGING="$ROOT/benchmark/images-benchmark/build/codec-matrix/$RUN_ID/staging"

profile_codec_matrix() {
  pattern="$1"
  output="$2"
  "$JAVA25" --enable-native-access=ALL-UNNAMED \
    -Dcodec.matrix.backend=java25 \
    -Dcodec.matrix.runId="$RUN_ID" \
    -Dcodec.matrix.preflight="$PREFLIGHT" \
    -Dcodec.matrix.fixtureManifest="$FIXTURES" \
    -Dcodec.matrix.eligibility="$ELIGIBILITY" \
    -jar "$JMH_JAR" "$pattern" \
    -wi 1 -i 3 -w 1s -r 1s -f 1 -t 1 -bm avgt -tu ms -prof gc \
    -rf json -rff "$STAGING/$output"
}

profile_codec_matrix \
  '.*VipsCodecMatrixBenchmark.*' \
  allocation-java25.json
profile_codec_matrix \
  '.*VipsExperimentalCodecMatrixBenchmark.*(encodeAvifFromJpeg|decodeAvifToJpeg).*' \
  allocation-java25-avif.json
profile_codec_matrix \
  '.*VipsExperimentalCodecMatrixBenchmark.*(encodeHeicFromJpeg|decodeHeicToJpeg).*' \
  allocation-java25-heic.json
```

Finally promote only a complete accepted run:

```bash
./gradlew :bluetape4k-images-benchmark:finalizeCodecMatrixEvidence \
  -Pcodec.matrix.runId="$RUN_ID" -Pvips.impl=java25 \
  -Pbluetape4kDependenciesCatalogPath="$CATALOG" --console=plain
```

## Evidence ledger

The accepted [run manifest](raw/issue-208-20260713-macos-arm64-09/run-manifest.json)
contains 32 terminal cells and SHA-256/byte-count links for 11 artifacts:

- Java 21 and Java 25 preflight reports
- stable and experimental fixture manifests
- stable, AVIF, and HEIC latency JSON
- stable, AVIF, and HEIC GC-profiler JSON
- Java 25 size evidence

The complete immutable directory is
[`docs/raw/issue-208-20260713-macos-arm64-09/`](raw/issue-208-20260713-macos-arm64-09/).
