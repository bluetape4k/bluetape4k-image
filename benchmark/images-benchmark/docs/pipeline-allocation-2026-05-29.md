# 이미지 파이프라인 할당량 기준선 (2026-05-29)

이 보고서는 `ImagePipelineBenchmark`의 첫 할당량 중심 기준선을 기록한다.

## 실행 환경

| 항목 | 값 |
|------|-------|
| 호스트 | macOS arm64 |
| JVM | GraalVM Java 25.0.3 |
| 기본 실행 명령 | `JAVA_HOME=$(/usr/libexec/java_home -v 25) ./gradlew :bluetape4k-images-benchmark:benchmarkPipelineAllocationBenchmark --console=plain` |
| 기본 원본 JSON | [`raw/benchmark-pipeline-allocation-2026-05-29-macos-java25.json`](raw/benchmark-pipeline-allocation-2026-05-29-macos-java25.json) |
| 할당량 부록 | [`raw/benchmark-pipeline-allocation-jmh-gc-2026-05-29-macos-java25.json`](raw/benchmark-pipeline-allocation-jmh-gc-2026-05-29-macos-java25.json) |

> 벤치마크 소스와 기본 실행 경로는 `kotlinx-benchmark`를 사용한다. JVM에서
> `kotlinx-benchmark`는 JMH를 백엔드로 사용한다. Gradle DSL이 JMH 프로파일러를
> 제공하지 않으므로 할당량 값은 별도의 JMH GC 프로파일러 부록에 기록한다.

## 픽스처

| 픽스처 | 원본 | 크기 | 역할 |
|---------|--------|------------|------|
| `landscape.jpg` | `images/src/test/resources/images/landscape.jpg` | 4032x3024 | 자연 사진 미리 보기 |
| `homer.png` | `images/src/test/resources/images/homer.png` | 1248x702 | 일러스트/문서 형식 PNG 경로 |

## 결과

AverageTime은 낮을수록 좋다. `gc.alloc.rate.norm`은 JMH GC 프로파일러가 추정한
작업당 할당량이다.

| 벤치마크 | 파이프라인 | AverageTime | 할당량 |
|-----------|----------|-------------|------------|
| `scrimage_photoPreviewJpeg` | 4032x3024 landscape -> 1280x720 리사이즈 -> 회색조 -> JPEG | 113.82 ms/op | 53,217,235 B/op (50.75 MB/op) |
| `scrimage_documentPreviewPng` | 1248x702 homer -> 640x905 리사이즈 -> 흐림 -> 세피아 -> PNG | 57.86 ms/op | 63,850,031 B/op (60.89 MB/op) |

![이미지 파이프라인 할당량 벤치마크 차트](../../../docs/images/readme-charts/images-benchmark-pipeline-allocation-chart-01.png)

## 해석

두 파이프라인 모두 작업당 수십 MB를 할당한다. 따라서 파이프라인 결합이나 API
지침을 검토하기 전에 고수준 scrimage 연쇄 변환을 회귀 검사 대상으로 삼을
가치가 있다.

관련 운영 코드 변경에서는 코루틴 `Path` 로드/쓰기 도우미의 불필요한 전체 파일
`ByteArray` 복사도 제거하고, 호출자가 소유한 스트리밍 경계를 위해
`bluetape4k-okio` `BufferedSource`/`BufferedSink` 오버로드를 제공한다. 이
측정에서 지배적인 중간 이미지 할당은 여전히 Scrimage 디코딩/인코딩 자체가
차지한다.

별도의 [`io-boundary-baseline-2026-05-29.md`](io-boundary-baseline-2026-05-29.md)
보고서는 `Path`, Okio, `SuspendedSource`/`SuspendedSink` 파일 채널 경계의 기준
비교를 추가한다. 해당 Scrimage 브리지 벤치마크에서 일시 중단 파일 채널 경로는
코루틴 파일 I/O에 의미가 있지만, Scrimage가 여전히 블로킹 스트림으로 디코딩과
인코딩을 수행하므로 지연 시간 최적화는 아니다.
