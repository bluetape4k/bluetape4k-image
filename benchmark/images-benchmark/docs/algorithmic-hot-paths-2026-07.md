# 알고리즘 핵심 경로 벤치마크 (Issue #207)

`ImageAlgorithmBenchmark`는 핵심 리사이즈/인코딩 표에 포함되지 않은 유틸리티
API를 측정한다. 저장소에 커밋된 자연 사진과 문서 픽스처, 그리고 작은 결정적
SVG 문자열을 사용한다.

## 실행 명령

```bash
./gradlew :bluetape4k-images-benchmark:benchmarkAlgorithmicHotPathsBenchmark
```

## 운영 API 대응표

| 벤치마크 | 운영 API | 픽스처와 매개변수 |
|-----------|----------------|------------------------|
| `crop` | `ImmutableImage.subimage` | 왼쪽 위 `min(1024,w) x min(768,h)` |
| `tileSplit` | `TileProcessor.split` | `TileSize(512,512)`, 최대 256개 타일 |
| `dominantColors` | `ImmutableImage.dominantColors` | median-cut 색상 5개 |
| `histogramSimilarity` | `histogramSimilarityTo` | 원본 픽스처와 512x512 비교 이미지, 기본 메트릭 |
| `phashDistance` | `phashDistanceTo` | 원본 픽스처와 512x512 비교 이미지 |
| `svgRasterize` | `BatikSvgRasterizer.rasterize` | 1024x1024 SVG, 512x512 출력 |

이 스위트는 `AverageTime ms/op`를 보고하며 낮을수록 좋다. 자르기, 타일 분할,
색상 분석, 유사도 계산은 CPU와 할당량에 민감하다. SVG 항목에는 일시 중단
방식의 래스터라이저 API에 필요한 코루틴 브리지가 포함된다. 이 결과는 특정
경로를 대상으로 한 로컬 측정값이며, 호스트 간 운영 성능 순위가 아니다.

![알고리즘 핵심 경로 벤치마크 차트](../../../docs/images/readme-charts/images-benchmark-algorithmic-hot-paths-chart-01.png)

Java 25/macOS 원본 출력: [`algorithmic-hot-paths.json`](raw/issue-207-20260726-macos-java25/algorithmic-hot-paths.json).
