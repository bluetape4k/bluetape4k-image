# Vips Backend Comparison Benchmark

Issue #104는 두 libvips 백엔드를 비교하기 위한 전용 JMH 클래스를 추가한다.

- `vips-java21`: JVips JNI 백엔드, Java 21 toolchain.
- `vips-java25`: Panama FFM 백엔드, Java 25 toolchain.

벤치마크 클래스는 geometry 작업용 `VipsBackendBenchmark`와 encode 작업용
`VipsBackendEncodeBenchmark`이다. 두 백엔드에서 같은 method 이름을 유지하므로
결과 JSON 파일을 benchmark 이름과 parameter 기준으로 결합할 수 있다.

| Benchmark | Workload | Input |
|-----------|----------|-------|
| `vips_resize` | `1920x1080`, `1280x720` 크기로 resize | 4K JPEG bytes |
| `vips_thumbnail` | max dimension에 맞춘 thumbnail 생성 | 4K JPEG bytes |
| `vips_crop` | 왼쪽 위 영역을 지정 크기로 crop | 4K JPEG bytes |
| `vips_encodeJpeg` | 원본 이미지를 JPEG로 encode | 4K JPEG bytes |

`ms/op` 값이 낮을수록 좋다.

## 현재 비교 가능한 결과

아래 값은 committed natural photo fixture로 2026-05-28에 수집한 Java 25 FFM
전체 실행 결과를 사용한다. 상세 요약은
[`benchmark-results-2026-05-28-natural-photos.md`](benchmark-results-2026-05-28-natural-photos.md).
이 macOS arm64 host에서는 bundled JVips dylib가 x86_64라서 native 측정값을 만들 수
없으므로 Java 21 JNI를 `N/A`로 기록한다.

![Vips backend comparison benchmark chart](../../../docs/images/readme-charts/images-benchmark-vips-backend-comparison-chart-01.png)

| Operation | Image | Parameter | scrimage baseline (ms/op) | Java 21 JNI (ms/op) | Java 25 FFM measured (ms/op) |
|-----------|-------|-----------|---------------------------|---------------------|------------------------------|
| resize | cafe | 1920x1080 | 114.89 ± 3.21 | N/A | 0.247 ± 0.050 |
| resize | landscape | 1920x1080 | 115.64 ± 2.24 | N/A | 0.231 ± 0.025 |
| thumbnail | cafe | 1920 | N/A | N/A | 0.256 ± 0.012 |
| thumbnail | landscape | 1920 | N/A | N/A | 0.262 ± 0.014 |
| crop | cafe | 1920x1080 | N/A | N/A | 0.084 ± 0.003 |
| crop | landscape | 1920x1080 | N/A | N/A | 0.089 ± 0.013 |
| encode JPEG | cafe | original | 137.95 ± 2.42 | N/A | 41.71 ± 0.50 |
| encode JPEG | landscape | original | 144.96 ± 5.51 | N/A | 43.88 ± 1.81 |

원본 Java 25 FFM 결과:
[`raw/benchmark-results-2026-05-28-macos-java25-natural-photos.json`](raw/benchmark-results-2026-05-28-macos-java25-natural-photos.json).

[`benchmark-results-2026-04-29.md`](benchmark-results-2026-04-29.md)의 과거 Linux
CI row는 release archaeology에는 여전히 유용하다. 다만 #104 chart는 오래된 Linux
수치와 현재 macOS 값을 섞지 않기 위해 현재 측정 host의 Java 21 JNI를 의도적으로
`N/A`로 표시한다.

## 전체 실행

두 백엔드는 순차 실행한다. 같은 host에서 병렬 실행하지 않는다. libvips native
초기화와 CPU contention 때문에 비교값이 noisy해진다.

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

위 Gradle task가 일반적인 benchmark 실행 surface이다. `VipsBackend` JMH regex filter와
좁은 raw JSON 출력이 필요한 focused/debug 증거에만 direct JMH jar를 사용한다. 버전이
박힌 artifact 이름을 복사하지 말고 직전 Gradle task가 만든 jar를 찾아 사용한다.

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25) \
  ./gradlew :bluetape4k-images-benchmark:benchmarkBenchmarkJar \
  -Pvips.impl=java25 --console=plain

jmh_jar="$(find benchmark/images-benchmark/build/benchmarks/benchmark/jars \
  -maxdepth 1 -type f \
  -name 'bluetape4k-images-benchmark-benchmark-jmh-*-JMH.jar' \
  -print -quit)"
test -n "$jmh_jar"

JAVA_HOME=$(/usr/libexec/java_home -v 25) \
  java --enable-native-access=ALL-UNNAMED \
  -jar "$jmh_jar" \
  '.*VipsBackend.*' \
  -rf json \
  -rff benchmark/images-benchmark/docs/raw/benchmark-vips-backend-java25.json
```

`-Pvips.impl=java21`과 Java 21로 같은 jar build/run을 반복한다.

## Reporting

raw JMH JSON은 `benchmark/images-benchmark/docs/raw/` 아래에 저장하고, stable full-run
값을 다음 형태로 요약한다.

| Operation | Parameter | Java 21 JNI (ms/op) | Java 25 FFM (ms/op) | Faster backend |
|-----------|-----------|---------------------|---------------------|----------------|
| resize | 1920x1080 | TBD | TBD | TBD |
| thumbnail | 1920x1080 | TBD | TBD | TBD |
| crop | 1920x1080 | TBD | TBD | TBD |
| encode JPEG | original | TBD | TBD | TBD |

## Local Validation Notes

2026-05-28에 `VipsBackendBenchmark`를 macOS arm64에서 검증했다.

- Java 25 / FFM은 `cafe`, `landscape` natural photo fixture와 자동 감지된 Homebrew
  libvips path로 JMH class를 성공적으로 실행했다.
- Java 21 / JVips는 compile과 JMH class launch까지 성공했지만, bundled JVips dylib가
  x86_64이고 JVM은 arm64라서 이 host의 native JNI 실행은 skip되었다. 실제 JNI
  측정은 Linux CI 또는 architecture가 맞는 Java 21 host에서 수행한다.
