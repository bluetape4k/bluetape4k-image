# 이미지 I/O 경계 기준선 (2026-05-29)

이 보고서는 `bluetape4k-okio` `Source`/`Sink`와
`SuspendedSource`/`SuspendedSink` 오버로드를 추가한 뒤 이미지 로드/쓰기 경계
선택지를 비교한다.

## 실행 환경

| 항목 | 값 |
|------|-------|
| 호스트 | macOS arm64 |
| JVM | GraalVM Java 25.0.3 |
| 기본 실행 명령 | `JAVA_HOME=$(/usr/libexec/java_home -v 25) ./gradlew :bluetape4k-images-benchmark:benchmarkIoBoundaryBenchmark --console=plain` |
| 기본 원본 JSON | [`raw/benchmark-io-boundary-2026-05-29-macos-java25.json`](raw/benchmark-io-boundary-2026-05-29-macos-java25.json) |
| 할당량 부록 | [`raw/benchmark-io-boundary-jmh-gc-2026-05-29-macos-java25.json`](raw/benchmark-io-boundary-jmh-gc-2026-05-29-macos-java25.json) |

> 벤치마크 소스와 기본 실행 경로는 `kotlinx-benchmark`를 사용한다. JVM에서
> `kotlinx-benchmark`는 JMH를 백엔드로 사용한다. Gradle DSL이 프로파일러 인자를
> 제공하지 않으므로 GC 프로파일러 파일은 JMH를 직접 실행한 부록이다.

## 픽스처

| 픽스처 | 원본 | 크기 | 역할 |
|---------|--------|------------|------|
| `homer.jpg` | `images/src/test/resources/images/homer.jpg` | 1248x702 | 작은 일러스트 형식의 로드/쓰기 경계 |
| `landscape.jpg` | `images/src/test/resources/images/landscape.jpg` | 4032x3024 | 자연 사진 로드 경계 |

## 결과

AverageTime은 낮을수록 좋다. 할당량은 JMH GC 프로파일러 부록의
`gc.alloc.rate.norm` 값이다.

| 벤치마크 | 처리 경계 | AverageTime | 할당량 |
|-----------|----------|-------------|------------|
| `load_homer_byteArray` | `ByteArray` baseline | 7.70 ms/op | 5.42 MB/op |
| `load_homer_inputStream` | `InputStream` baseline | 7.81 ms/op | 5.62 MB/op |
| `load_homer_path` | `Path` baseline | 7.78 ms/op | 5.50 MB/op |
| `load_homer_okioSource` | Okio `Source` | 8.23 ms/op | 5.65 MB/op |
| `load_homer_suspendedFileSource` | `AsynchronousFileChannel` `SuspendedSource` | 10.81 ms/op | 5.76 MB/op |
| `load_landscape_path` | `Path` baseline | 152.22 ms/op | 76.88 MB/op |
| `load_landscape_suspendedFileSource` | `AsynchronousFileChannel` `SuspendedSource` | 216.62 ms/op | 86.34 MB/op |
| `write_homer_byteArray` | `ByteArray` baseline | 6.90 ms/op | 2.89 MB/op |
| `write_homer_outputStream` | `OutputStream` baseline | 7.35 ms/op | 2.72 MB/op |
| `write_homer_path` | `Path` baseline | 7.35 ms/op | 2.72 MB/op |
| `write_homer_okioSink` | Okio `Sink` | 7.40 ms/op | 2.73 MB/op |
| `write_homer_suspendedFileSink` | `AsynchronousFileChannel` `SuspendedSink` | 14.03 ms/op | 2.82 MB/op |

![이미지 I/O 경계 벤치마크 차트](../../../docs/images/readme-charts/images-benchmark-io-boundary-chart-01.png)

## 해석

이 기준선에서 중요한 점은 `SuspendedSource`와 `SuspendedSink`가 이 Scrimage
경계 벤치마크에서 더 빠르지 않다는 것이다. Scrimage 디코딩/인코딩에는 여전히
블로킹 `InputStream`/`OutputStream` 어댑터가 필요하다. 따라서 일시 중단 파일
채널 경로는 Scrimage의 지배적인 디코딩/인코딩 작업을 제거하지 못하고 코루틴에서
블로킹으로 이어지는 브리지 비용을 추가한다.

실무적으로는 코루틴 파일 I/O 편의와 생명주기 일관성을 위해 새 일시 중단
오버로드를 유지하되, Scrimage 기반 로드/쓰기 경로의 지연 시간 최적화로 홍보하지
않아야 한다. 호출자가 이미 `SuspendedSource`나 `SuspendedSink`를 소유한 순수
스트리밍 파이프라인에서는 이 오버로드가 임시 바이트 배열 준비 단계를 거치지
않도록 해 준다.

별도의 [`file-io-throughput-2026-05-29.md`](file-io-throughput-2026-05-29.md)
보고서는 다수 파일 동시 처리량 가설을 직접 검증한다. 이 macOS Java 25
실행에서도 일시 중단 파일 채널은 압축 이미지 파일 I/O 처리량이 더 높지 않았다.
