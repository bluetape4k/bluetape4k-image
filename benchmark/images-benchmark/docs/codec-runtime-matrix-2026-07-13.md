# 코덱 런타임 매트릭스 — 2026-07-13

Issue [#208](https://github.com/bluetape4k/bluetape4k-image/issues/208)은
libvips Java 21 JNI와 Java 25 FFM 백엔드의 재현 가능한 코덱 매트릭스를 기록한다.
승인된 실행은 Git SHA `999b1e87f764a175d9887af9972ed41644e37f9e`의
`issue-208-20260713-macos-arm64-09`다.

## 상태 범례

- `MEASURED`: 기능 검사를 통과했으며 지연 시간과 할당량 근거가 모두 승인되었다.
- `N/A`: 이 호스트에서 런타임을 평가할 수 없었다. 0이나 벤치마크 실패를 뜻하지
  않는다.
- `UNSUPPORTED`: 선택한 런타임은 사용할 수 있지만 요청한 코덱이나 방향을
  지원하지 않는다.
- `SKIPPED`: 실행 가능한 셀을 의도적으로 실행하지 않아 성능 결과가 없다.

## 결과 요약

| 백엔드 | 런타임 | 호스트 결과 | 매트릭스 셀 |
|---------|---------|-------------|--------------|
| `java25` | FFM | `MEASURED` | 16개 중 16개 |
| `java21` | JVips JNI | `N/A` — `CAPABILITY_UNKNOWN`: JNI 바이너리 아키텍처를 확인할 수 없음 | 16개 모두 최종 `N/A` 셀 |

![코덱 런타임 지연 시간](../../../docs/images/readme-charts/images-benchmark-codec-runtime-latency-chart-01.png)

![코덱 인코딩 출력 크기](../../../docs/images/readme-charts/images-benchmark-codec-output-size-chart-01.png)

`encode`는 JPEG 입력을 지정한 대상 코덱으로 변환하는 작업을 측정한다. `decode`는
지정한 코덱 입력을 JPEG로 출력하는 작업을 측정한다. AverageTime은 작업당
밀리초이며 낮을수록 좋다. 할당량은 JMH `gc.alloc.rate.norm`으로 측정한 작업당
관리 힙 바이트 수이며 libvips 네이티브 메모리는 포함하지 않는다. 출력 바이트
수는 특정 코덱과 옵션의 스냅샷이며 시각적 품질 순위가 아니다.

## Java 25 FFM 측정값

### 프로필 이미지

프로필 시나리오는 `homer.jpg` 중앙을 `512 x 512`로 자른다.

| 형식 | 방향 | AverageTime (ms/op) | 관리 힙 할당량 (B/op) | 입력 (B) | 출력 (B) |
|--------|-----------|---------------------|---------------------------|-----------|------------|
| PNG | encode | 5.945 | 456,466 | 32,205 | 225,576 |
| PNG | decode | 2.156 | 69,491 | 257,323 | 32,181 |
| WebP | encode | 10.415 | 47,147 | 32,205 | 19,914 |
| WebP | decode | 2.605 | 68,146 | 19,252 | 31,467 |
| AVIF | encode | 51.134 | 65,065 | 32,205 | 23,605 |
| AVIF | decode | 4.339 | 69,589 | 23,605 | 31,999 |
| HEIC | encode | 60.350 | 132,465 | 32,205 | 55,961 |
| HEIC | decode | 7.681 | 70,063 | 55,961 | 32,200 |

### 웹 사진

웹 사진 시나리오는 `cafe.jpg` 중앙을 `1920 x 1080`으로 자른다.

| 형식 | 방향 | AverageTime (ms/op) | 관리 힙 할당량 (B/op) | 입력 (B) | 출력 (B) |
|--------|-----------|---------------------|---------------------------|-----------|------------|
| PNG | encode | 80.132 | 6,984,121 | 429,306 | 3,485,741 |
| PNG | decode | 18.825 | 868,771 | 4,106,689 | 428,521 |
| WebP | encode | 106.405 | 679,169 | 429,306 | 332,028 |
| WebP | decode | 20.020 | 842,109 | 327,690 | 414,997 |
| AVIF | encode | 511.268 | 743,568 | 429,306 | 364,133 |
| AVIF | decode | 38.751 | 870,204 | 364,133 | 428,429 |
| HEIC | encode | 339.555 | 1,521,700 | 429,306 | 754,672 |
| HEIC | decode | 73.038 | 871,753 | 754,672 | 429,295 |

이 값은 해당 호스트, libvips 빌드, 픽스처 생성법, 코덱 옵션, 짧은 JMH 실행
절차에만 해당한다. 보편적인 코덱 순위를 뜻하지 않는다. 특히 AVIF와 HEIC는
실험 단계 API이며, 별도의 시각 품질 연구 없이 출력 크기를 동일 품질 기준으로
비교할 수 없다.

## 픽스처 출처

커밋된 원본 사진 두 장은 결정적 `cover-center-crop-v1` 방식으로 변환한다. 안정
픽스처에는 progressive 출력 없는 JPEG 품질 85, PNG 압축 4, 손실 WebP 품질
85/method 4를 사용한다.

| 시나리오 | 원본 | 원본 크기 / 바이트 | 파생 크기 | 안정 입력 SHA-256 |
|----------|--------|----------------------|---------------|---------------------|
| `web-photo` | `cafe.jpg` | `4032 x 3024` / 3,061,079 B | `1920 x 1080` | JPEG `5b6e2e599160…`; PNG `51948986bd7a…`; WebP `8d26bd6c6c0c…` |
| `profile` | `homer.jpg` | `1248 x 702` / 83,973 B | `512 x 512` | JPEG `6bca6f3aa1f7…`; PNG `ae31030716d9…`; WebP `36893de3ea32…` |

AVIF와 HEIC 입력은 libvips `8.18.4`를 사용하는 Java 25 FFM 기능 검사를 통과한
뒤에만 생성했다. 해시와 매직 시그니처는
[실험 픽스처 매니페스트](raw/issue-208-20260713-macos-arm64-09/fixtures/experimental-java25/manifest.json)에
기록되어 있다.

## 실행 환경과 사전 검사

| 항목 | Java 21 JNI | Java 25 FFM |
|------|-------------|-------------|
| OS / 아키텍처 | macOS `26.5.1`, arm64, Apple M5 | 동일 호스트 |
| JDK | Oracle `21.0.11+9-LTS-jvmci-23.1-b92` | Oracle `25.0.3+9-LTS-jvmci-25.1-b19` |
| 네이티브 접근 | 비활성화 | 활성화 |
| 로더 경로 | 사용 가능 | 사용 가능 |
| Git 상태 | clean, `999b1e87f764…` | clean, `999b1e87f764…` |
| 사전 검사 | `N/A` | `ELIGIBLE` |

이 arm64 호스트에서 JNI 바이너리 아키텍처를 확인할 수 없어 Java 21 사전 검사는
JNI 초기화 전에 중단되었다. 호환되는 JVips JNI 바이너리가 있는 호스트에서 같은
픽스처 생성법과 JMH 실행 절차로 Java 21을 다시 실행해야 한다. 이 `N/A` 셀을
Java 25의 우위로 해석해서는 안 된다.

## 재현 절차

깨끗한 checkout에서 native/JNI/FFM 단계를 순차 실행한다. 새 실행 ID를 사용해야
하며 승인된 근거 디렉터리는 변경할 수 없다. 저장소 로컬 직렬화 버전 고정은
관리되는 별칭을 릴리스 트레인 카탈로그 태그에서 사용할 수 있을 때까지 적용하는
임시 issue #208 예외다.

```bash
RUN_ID=issue-208-YYYYMMDD-host-01
ROOT="$PWD"
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
  --console=plain

JAVA_HOME=$(/usr/libexec/java_home -v 25) ./gradlew \
  :bluetape4k-images-benchmark:codecMatrixCapabilityReport \
  :bluetape4k-images-benchmark:benchmarkCodecMatrixBenchmark \
  -Pcodec.matrix.runId="$RUN_ID" -Pvips.impl=java25 \
  --console=plain

# 대응하는 기능 셀이 ELIGIBLE일 때만 각 실험 작업을 실행한다.
JAVA_HOME=$(/usr/libexec/java_home -v 25) ./gradlew \
  :bluetape4k-images-benchmark:benchmarkCodecMatrixAvifBenchmark \
  :bluetape4k-images-benchmark:benchmarkCodecMatrixHeicBenchmark \
  -Pcodec.matrix.runId="$RUN_ID" -Pvips.impl=java25 \
  --console=plain

JAVA_HOME=$(/usr/libexec/java_home -v 25) ./gradlew \
  :bluetape4k-images-benchmark:stageCodecMatrixProfilerJar \
  -Pcodec.matrix.runId="$RUN_ID" -Pvips.impl=java25 \
  --console=plain
```

할당량을 측정할 때는 안정 코덱, AVIF, HEIC마다 준비된 JMH jar를 새 JVM 하나에서
실행한다. 매니페스트는 절대 경로로 지정해야 한다. 승인된 실행에서는 대응하는
모든 기능 셀이 `ELIGIBLE`이어서 두 실험 방향을 모두 사용했다. 다시 실행할 때
적합하지 않은 패턴은 제외한다.

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

마지막으로 완전하게 승인된 실행만 승격한다.

```bash
./gradlew :bluetape4k-images-benchmark:finalizeCodecMatrixEvidence \
  -Pcodec.matrix.runId="$RUN_ID" -Pvips.impl=java25 \
  --console=plain
```

## 근거 원장

승인된 [실행 매니페스트](raw/issue-208-20260713-macos-arm64-09/run-manifest.json)에는
최종 상태 셀 32개와 아티팩트 11개의 SHA-256/바이트 수 링크가 포함되어 있다.

- Java 21과 Java 25 사전 검사 보고서
- 안정 및 실험 픽스처 매니페스트
- 안정, AVIF, HEIC 지연 시간 JSON
- 안정, AVIF, HEIC GC 프로파일러 JSON
- Java 25 크기 근거

전체 변경 불가 디렉터리는
[`docs/raw/issue-208-20260713-macos-arm64-09/`](raw/issue-208-20260713-macos-arm64-09/)다.
