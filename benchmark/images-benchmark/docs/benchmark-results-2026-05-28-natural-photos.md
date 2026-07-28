# 이미지 처리 JMH 벤치마크 결과 - 2026-05-28 자연 사진

## 요약

이번 실행은 이전의 합성 대체 이미지 대신 실제 자연 사진 픽스처를 사용해
`images-benchmark` 비교 결과를 갱신한다.

`ms/op`는 낮을수록 좋다. 점수는 워밍업 반복 3회, 측정 반복 5회, fork 1회로
실행한 JMH `AverageTime` 결과다.

## 실행 환경

| 항목 | 값 |
|------|-------|
| 날짜 | 2026-05-28 |
| 호스트 | macOS Darwin arm64 |
| JVM | Oracle GraalVM Java 25.0.3 |
| vips 구현체 | `-Pvips.impl=java25` / vips-ffm |
| 실행 명령 | `./gradlew :bluetape4k-images-benchmark:benchmarkBenchmark -Pvips.impl=java25 --console=plain` |
| 원본 JSON | [`raw/benchmark-results-2026-05-28-macos-java25-natural-photos.json`](raw/benchmark-results-2026-05-28-macos-java25-natural-photos.json) |

## 입력

리사이즈와 인코딩 비교는 저장소에 커밋된 자연 사진 픽스처 두 개를 사용한다.

| 이미지 | 원본 경로 | 크기 | 원본 크기 |
|-------|-------------|------------|---------------|
| `cafe` | `benchmark/images-benchmark/src/main/resources/bench/cafe.jpg` | 4032x3024 | 2.9 MiB |
| `landscape` | `benchmark/images-benchmark/src/main/resources/bench/landscape.jpg` | 4032x3024 | 3.4 MiB |

`BenchmarkImageSets`는 선택 사항인 문서와 썸네일 리소스를 위한 합성 대체 이미지
생성 기능을 계속 유지한다. 다만 아래의 핵심 리사이즈와 인코딩 행은 실제 JPEG
사진을 입력으로 사용한다.

## 자연 사진 결과

### 1920x1080으로 리사이즈

| 이미지 | scrimage `scaleTo` (ms/op) | libvips Java 25 FFM `resize` (ms/op) | 속도 향상 |
|-------|----------------------------|--------------------------------------|---------|
| `cafe` | 114.885 ± 3.207 | 0.257 ± 0.083 | 446x |
| `landscape` | 115.641 ± 2.242 | 0.244 ± 0.028 | 473x |

### 인코딩

| 이미지 | 형식 | scrimage (ms/op) | libvips Java 25 FFM (ms/op) | 속도 향상 |
|-------|--------|------------------|------------------------------|---------|
| `cafe` | JPEG | 137.947 ± 2.417 | 58.351 ± 23.828 | 2.4x |
| `landscape` | JPEG | 144.961 ± 5.511 | 46.749 ± 6.066 | 3.1x |
| `cafe` | PNG | 884.105 ± 156.993 | 585.288 ± 186.247 | 1.5x |
| `landscape` | PNG | 989.370 ± 346.605 | 546.388 ± 25.444 | 1.8x |

## 주의 사항

- 이 행은 자연 사진 결과이며 문서, 평면 그래픽, 애니메이션 이미지 결과가 아니다.
  이미지 내용에 따라 인코딩 속도는 크게 달라질 수 있다.
- 사용할 수 있는 JVips dylib가 x86_64이므로 이 macOS arm64 호스트에서 Java 21
  JNI는 여전히 `N/A`다.
- 벤치마크 출력에는 libvips 백엔드 전용 행과 scrimage 필터 행도 포함된다. 이
  보고서는 블로그와 README 비교에 사용한 행을 중심으로 정리한다.
