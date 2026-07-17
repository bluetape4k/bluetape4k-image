---
manualId: "bluetape4k-images-benchmark"
id: "bluetape4k-images-benchmark"
title: "이미지 처리 벤치마크"
locale: "ko"
kind: "benchmark"
gradlePath: ":bluetape4k-images-benchmark"
sourceDir: "benchmark/images-benchmark"
releaseRef: "0.3.0"
artifact: null
---

# 이미지 처리 벤치마크

> 성능 벤치마크

## 제공하는 기능 {#problem}

Scrimage와 libvips 가운데 무엇을 선택할지 재현 가능한 근거로 판단하기 위한 비배포 모듈입니다. JVM JMH 백엔드 위에서 `kotlinx-benchmark`를 사용해 기하 연산, 인코딩, 필터 체인, I/O 경계, 압축 파일 동시 I/O, 관리 힙 할당량, 대용량 이미지 읽기-변환-쓰기 전체 과정을 측정합니다.

## 사용하기 좋은 경우 {#when-to-use}

백엔드나 I/O 경계가 더 빠르다고 주장하기 전, 성능 회귀를 확인할 때, 실제 작업에 Scrimage와 libvips 중 무엇이 맞는지 결정할 때 사용하세요. 눈에 띄는 성능 배수 하나만 보고 운영 처리 용량을 판단하면 안 됩니다.

## 의존성 좌표 {#coordinates}

저장소 전용 벤치마크라 배포 좌표가 없습니다. 프로젝트 의존성은 0.3.0 소스와 같은 `bluetape4k-dependencies` 릴리스 카탈로그로 맞춥니다.

## 핵심 개념 {#concepts}

- `AverageTime`은 낮을수록, throughput은 높을수록 좋습니다.
- 테스트 이미지, 실행 장비, JVM, 백엔드, 워밍업, 반복 횟수, 포크, 명령, 원본 JSON을 함께 기록해야 합니다.
- Java 25는 FFM, `-Pvips.impl=java21`은 Java 21 JNI 백엔드를 선택합니다.
- GC 프로파일러는 관리 힙만 측정하며 libvips 네이티브 메모리는 포함하지 않습니다.
- 지연 평가되는 기하 연산보다 전체 처리 결과가 애플리케이션 판단에 더 강한 근거입니다.

## 빠르게 시작하기 {#quick-start}

Java 25 FFM 경로는 JDK 25와 system libvips가 필요합니다. macOS에서는 `brew install vips`를 사용합니다.

```bash
./gradlew :bluetape4k-images-benchmark:benchmarkBenchmark \
  -Pvips.impl=java25 --console=plain
```

대용량 전체 처리 과정만 측정하려면 다음 태스크를 사용합니다.

```bash
./gradlew :bluetape4k-images-benchmark:benchmarkLargeStreamingBenchmark \
  -Pvips.impl=java25 --console=plain
```

Java 21 JNI와 Java 25 FFM은 두 환경을 모두 지원하는 같은 장비에서 차례로 실행하세요.

## 작업별 API {#api-by-task}

| 확인할 질문 | Benchmark |
| --- | --- |
| 리사이즈/인코딩 백엔드 지연 시간 | `ImageResizeBenchmark`, `ImageEncodeBenchmark` |
| Scrimage filter 비용 | `ImageFilterBenchmark` |
| 변환 체인의 할당량 | `ImagePipelineBenchmark` |
| `Path`/스트림/Okio/suspend 경계 | `ImageIoBoundaryBenchmark` |
| 압축 파일 동시 처리량 | `ImageFileIoThroughputBenchmark` |
| 대용량 전체 처리 | `ImageLargeStreamingBenchmark` |
| JNI와 FFM 래퍼 비교 | `VipsBackendBenchmark`, `VipsBackendEncodeBenchmark` |

## 권장 패턴 {#patterns}

운영 환경과 비슷한 시나리오부터 측정한 뒤 의심되는 경계를 분리하세요. 원본 JSON과 환경 정보를 보존하고 두 후보를 같은 장비·JVM 정책·테스트 이미지로 다시 실행하세요. libvips 자원 수명은 JMH GC 결과와 별도로 네이티브 프로파일러에서 확인해야 합니다.

## 연동 {#integrations}

[`bluetape4k-images`](./bluetape4k-images.md)를 특정 바인딩에 종속되지 않는 [`vips API`](./bluetape4k-images-vips-api.md), [Java 21 JNI](./bluetape4k-images-vips-java21.md), [Java 25 FFM](./bluetape4k-images-vips-java25.md) 런타임과 비교합니다.

## 설정 {#configuration}

전체 벤치마크는 워밍업 3회, 측정 5회, 포크 1회, `AverageTime` ms/op를 사용합니다. 집중 측정 설정은 워밍업 1회와 1초 측정 3회를 사용합니다. `vips.impl`에 따라 JDK 21/25 도구 체인을 고르고 포크에 `--enable-native-access=ALL-UNNAMED`를 넣습니다.

## 실패 유형과 해결 방법 {#failures}

- vips 결과가 거의 0임: 네이티브 백엔드를 사용할 수 있는지 확인하세요. 사용할 수 없으면 메서드가 `null`을 소비하고 바로 끝납니다.
- macOS arm64에서 Java 21 JNI가 건너뛰어짐: 0.3.0 기록의 JVips dylib는 x86_64이므로 이를 지원하는 장비가 필요했습니다.
- 분산이 큼: 벤치마크를 병렬 실행하지 말고 장비 부하·온도와 동일한 테스트 이미지/포크 설정을 확인하세요.
- 네이티브 로드 실패: 숫자를 해석하기 전에 시스템 libvips와 FFM/JNI 라이브러리 경로를 고치세요.

## 운영 {#operations}

0.3.0의 macOS arm64, GraalVM Java 25.0.3 실행에서 libvips `Path` 전체 처리 시간은 `large-photo` 7.13 ms/op, `ocr-document` 5.47 ms/op였고 Scrimage `Path`는 각각 223.19, 145.13 ms/op였습니다. 이 값은 해당 환경에서 얻은 비교 결과일 뿐 보편적인 순위가 아닙니다. suspend 경계는 지연 시간과 여러 파일의 처리량 모두 더 느렸으므로 성능 최적화가 아니라 자원 수명 관리와 연동을 위한 API로 설명해야 합니다.

## 테스트 {#testing}

전체 벤치마크를 실행하지 않고도 태스크 연결을 확인할 수 있습니다.

```bash
./gradlew :bluetape4k-images-benchmark:benchmarkBenchmark \
  -Pvips.impl=java25 --dry-run --console=plain
```

공개할 근거를 만들 때는 JSON을 보존하고 명령과 환경을 기록한 뒤 날짜가 붙은 보고서와 SVG/PNG 차트를 함께 갱신하세요.

## 학습 경로와 예제 {#workshops}

1. [`basic-processing`](./basic-processing.md)으로 사용자가 보는 이미지 작업을 익힙니다.
2. 자연 사진 결과와 벤치마크 소스를 함께 읽습니다.
3. 코루틴 API를 해석하기 전 I/O 경계와 할당량 보고서를 살펴봅니다.
4. 전체 과정을 측정한 대용량 처리 보고서를 확인합니다.
5. 실제 배포 환경과 비슷한 장비에서 집중 측정 시나리오를 다시 실행한 뒤 결정합니다.

## 제약 사항 {#limitations}

- 0.3.0 보고서에는 새 macOS Java 25 결과와 명시적으로 남겨 둔 과거 Linux 행이 섞여 있으며 하나의 실험이 아닙니다.
- macOS arm64 실행에서는 호환되는 Java 21 JNI 값을 얻지 못했습니다.
- `vips_resize`는 결과를 인코딩하지 않습니다. libvips가 지연 평가하므로 기하 연산만 측정한 배수를 전체 픽셀 처리 배수로 읽으면 안 됩니다.
- GC 할당량에는 네이티브 메모리가 포함되지 않습니다.
- I/O API의 편의성, 지연 시간, 처리량, 백엔드 선택은 서로 다른 질문입니다.
- 다른 데이터·동시성·하드웨어·JVM·코덱·저장소의 운영 성능을 보장하지 않습니다.

<!-- release-readme-diagrams:start -->
## 배포본 다이어그램 {#release-diagrams}

아래 그림은 현재 개발 브랜치가 아니라 `0.3.0` 배포 태그의 README 자산을 바이트 단위로 그대로 옮긴 것입니다. 따라서 이후 SNAPSHOT 변경이 아니라 이 매뉴얼 버전의 구조와 실행 흐름을 보여 줍니다. 미리보기를 누르면 SVG 원본이 열립니다.

### images benchmark 아키텍처

[![images benchmark 아키텍처](../../assets/readme-diagrams/images-benchmark-architecture-01.png)](../../assets/readme-diagrams/images-benchmark-architecture-01.svg)

_배포본 README: [`benchmark/images-benchmark/README.ko.md`](https://github.com/bluetape4k/bluetape4k-image/blob/a571c30004f571fe8cfcddc29670c1404d212ec6/benchmark/images-benchmark/README.ko.md)_

<!-- release-readme-diagrams:end -->

## 근거 자료 {#sources}

- [0.3.0 benchmark README](https://github.com/bluetape4k/bluetape4k-image/blob/0.3.0/benchmark/images-benchmark/README.ko.md)
- [자연 사진 결과](https://github.com/bluetape4k/bluetape4k-image/blob/0.3.0/benchmark/images-benchmark/docs/benchmark-results-2026-05-28-natural-photos.md)
- [IO boundary baseline](https://github.com/bluetape4k/bluetape4k-image/blob/0.3.0/benchmark/images-benchmark/docs/io-boundary-baseline-2026-05-29.md)
- [파일 IO throughput](https://github.com/bluetape4k/bluetape4k-image/blob/0.3.0/benchmark/images-benchmark/docs/file-io-throughput-2026-05-29.md)
- [Memory profile](https://github.com/bluetape4k/bluetape4k-image/blob/0.3.0/benchmark/images-benchmark/docs/memory-profile-2026-05-29.md)
- [Large streaming pipeline](https://github.com/bluetape4k/bluetape4k-image/blob/0.3.0/benchmark/images-benchmark/docs/large-streaming-2026-06-05.md)
- [Gradle 빌드 파일](https://github.com/bluetape4k/bluetape4k-image/blob/0.3.0/benchmark/images-benchmark/build.gradle.kts)
