# 이미지 처리 JMH 벤치마크 결과 - 2026-05-25

## 요약

이번 실행은 `images-benchmark` 모듈의 macOS Java 25 행을 갱신한다. 과거 CI
Linux 행은 다음 CI 벤치마크를 갱신할 때까지
[`benchmark-results-2026-04-29.md`](benchmark-results-2026-04-29.md)에 유지한다.

`ms/op`는 낮을수록 좋다. 점수는 워밍업 반복 3회, 측정 반복 5회, fork 1회로
실행한 JMH `AverageTime` 결과다.

## 실행 환경

| 항목 | 값 |
|------|-------|
| 날짜 | 2026-05-25 |
| 호스트 | macOS Darwin 25.5.0 arm64 |
| JVM | Oracle GraalVM Java 25.0.3+9.1 |
| vips 구현체 | `-Pvips.impl=java25` / vips-ffm |
| 실행 명령 | `./gradlew :bluetape4k-images-benchmark:benchmarkBenchmark -Pvips.impl=java25 --console=plain` |
| 원본 JSON | [`raw/benchmark-results-2026-05-25-macos-java25.json`](raw/benchmark-results-2026-05-25-macos-java25.json) |

## 주의 사항

- 벤치마크 로그에서 `/bench/photo-4k.jpg`, `/bench/thumbnail.jpg`, 문서 샘플
  리소스가 없다고 보고되어 `BenchmarkImageSets`가 생성한 합성 이미지를 사용했다.
- `VipsBenchmarkState`가 macOS Homebrew libvips 경로를 자동으로 감지했다.
- 이번 실행은 Java 21/JNI 또는 Linux CI 행을 갱신하지 않는다.

## macOS Java 25 결과

### 리사이즈

| 벤치마크 | 해상도 | 점수 (ms/op) | 오차 |
|-----------|------------|---------------|-------|
| scrimage `scaleTo` | 1920x1080 | 65.639 | ± 0.762 |
| scrimage `scaleTo` | 1280x720 | 44.590 | ± 0.503 |
| vips `resize` | 1920x1080 | 0.170 | ± 0.006 |
| vips `resize` | 1280x720 | 0.170 | ± 0.015 |

README의 4K → 1920x1080 행에서 이 호스트의 libvips는 scrimage보다 약 386배
빠르다.

### 인코딩

| 벤치마크 | 점수 (ms/op) | 오차 |
|-----------|---------------|-------|
| scrimage JPEG | 46.548 | ± 0.748 |
| vips JPEG | 15.181 | ± 0.546 |
| scrimage PNG | 84.910 | ± 4.206 |
| vips PNG | 46.912 | ± 0.521 |

libvips의 JPEG 인코딩은 약 3.1배, PNG 인코딩은 약 1.8배 빠르다.

### 필터

| 벤치마크 | 점수 (ms/op) | 오차 |
|-----------|---------------|-------|
| scrimage grayscale | 6.258 | ± 0.121 |
| scrimage blur | 27.755 | ± 0.152 |
| scrimage sepia | 14.506 | ± 8.446 |

필터는 scrimage 전용 벤치마크 행이다. README 비교표에는 갱신된 macOS 행과 함께
과거 CI Linux 값을 유지한다.
