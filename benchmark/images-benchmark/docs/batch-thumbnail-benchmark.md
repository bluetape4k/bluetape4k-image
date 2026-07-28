# 배치 및 썸네일 벤치마크 (Issue #206)

`ImageBatchBenchmark`는 여러 입력에 대한 썸네일 분기 처리와 썸네일 생성 후
JPEG로 인코딩하는 연쇄 작업을 측정한다. 생성된 출력을 커밋하지 않고도 확장
양상을 관찰할 수 있도록 픽스처 개수를 매개변수화했다.

## 실행 명령

```bash
./gradlew :bluetape4k-images-benchmark:benchmarkBatchPipelineBenchmark
```

전체 대상을 실행할 때는 생성된 JMH 출력에서 `ImageBatchBenchmark`만 필터링한다.
이 클래스는 자연 사진 픽스처 1개, 4개, 8개와 세 가지 썸네일 크기(`320`,
`640`, `1280`), `640x480` JPEG 출력 경로를 사용한다.

## 워크로드 대응표

| 메서드 | 처리 경계 | 동시성 | 백엔드 |
|--------|----------|-------------|---------|
| `scrimage_thumbnailFanout` | 리사이즈만 수행, 입력당 출력 3개 | 순차 | Scrimage |
| `scrimage_batchSequential` | 리사이즈 + JPEG 인코딩 | 순차 | Scrimage |
| `scrimage_batchBoundedConcurrency` | 리사이즈 + JPEG 인코딩 | 코루틴 디스패처를 2로 제한 | Scrimage |
| `vips_thumbnailFanout` | 썸네일만 생성, 입력당 출력 3개 | 순차 | libvips |

Scrimage와 libvips 행은 하나의 순위로 비교하기 위한 값이 아니다. Scrimage 연쇄
작업에는 JPEG 인코딩이 포함되지만 libvips 행은 썸네일 변환만 분리해서 측정한다.
동등한 행끼리만 비교하거나 애플리케이션 처리 경계에 맞는 파이프라인 형태를
고를 때 이 표를 사용한다.

## 메트릭과 한계

JMH는 `AverageTime ms/op`를 보고하며 낮을수록 좋다. 픽스처 수와 출력 수는
벤치마크 매개변수로 보고하고, 할당량은 별도의 GC 프로파일러 실행으로 측정해야
한다. 동시성을 제한한 코루틴 행은 CPU 중심 병렬 처리를 측정하며 원격 스토리지
I/O는 모델링하지 않는다.

로컬 Java 25/macOS 스모크 실행에서 Scrimage 연쇄 배치는 입력 1개일 때 약
`77 ms/op`, 입력 8개일 때 약 `625 ms/op`였다. 반면 동시성을 제한한 행은 입력
8개에서 약 `94 ms/op`를 유지했다. 따라서 여러 입력을 처리하는 CPU 작업에는
제한된 코루틴 동시성을 사용하고, 이미지 하나에는 순차 경로를 유지하는 편이
적합하다. 이 값은 해당 픽스처와 호스트에만 해당하며 보편적인 처리량을 보장하지
않는다.

![배치 및 썸네일 확장 벤치마크 차트](../../../docs/images/readme-charts/images-benchmark-batch-pipeline-chart-01.png)

원본 출력: [`batch-pipeline.json`](raw/issue-206-20260726-macos-java25/batch-pipeline.json).
