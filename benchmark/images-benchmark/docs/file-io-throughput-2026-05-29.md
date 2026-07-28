# 동시 이미지 파일 I/O 처리량 기준선 (2026-05-29)

이 보고서는 많은 압축 이미지 파일을 동시에 처리할 때 `SuspendedSource`와
`SuspendedSink`의 처리량이 더 높을 수 있다는 후속 가설을 검증한다.

## 실행 환경

| 항목 | 값 |
|------|-------|
| 호스트 | macOS arm64 |
| JVM | GraalVM Java 25.0.3 |
| 기본 실행 명령 | `JAVA_HOME=$(/usr/libexec/java_home -v 25) ./gradlew :bluetape4k-images-benchmark:benchmarkIoThroughputBenchmark --console=plain` |
| 기본 원본 JSON | [`raw/benchmark-file-io-throughput-2026-05-29-macos-java25.json`](raw/benchmark-file-io-throughput-2026-05-29-macos-java25.json) |

## 픽스처

| 픽스처 | 원본 | 크기 | 읽기 배치 | 쓰기 배치 | 워크로드 |
|---------|--------|------|------------|-------------|----------|
| `cafe.jpg` | `images/src/test/resources/images/cafe.jpg` | 2.9 MB | 하드 링크 경로 6,400개 | 출력 파일 256개 | 압축 파일 동시 읽기/쓰기 |
| `landscape.jpg` | `images/src/test/resources/images/landscape.jpg` | 3.4 MB | 하드 링크 경로 6,400개 | 출력 파일 256개 | 압축 파일 동시 읽기/쓰기 |

벤치마크는 의도적으로 Scrimage 디코딩/인코딩을 제외하고 고정된 128 KiB 버퍼로
바이트를 스트리밍한다. 읽기 입력에는 하드 링크를 사용해 시나리오마다 18~22 GB의
준비용 복사본이 생기지 않도록 한다. 쓰기 벤치마크는 실제 출력 파일을 생성한다.

## 결과

처리량은 높을수록 좋다. 원본 `ops/s` 점수는 초당 배치 작업 수이며, 아래 표에는
이를 환산한 초당 파일 작업 수를 표시한다.

| 시나리오 | 처리 경계 | 읽기 처리량 | 쓰기 처리량 |
|----------|----------|-----------------|------------------|
| `cafe-6400` | `Path` | 16,904 files/s | 1,507 files/s |
| `cafe-6400` | Okio `Source`/`Sink` | 2,513 files/s | 767 files/s |
| `cafe-6400` | `AsynchronousFileChannel` `SuspendedSource`/`SuspendedSink` | 74 files/s | 147 files/s |
| `landscape-6400` | `Path` | 15,981 files/s | 1,280 files/s |
| `landscape-6400` | Okio `Source`/`Sink` | 2,072 files/s | 778 files/s |
| `landscape-6400` | `AsynchronousFileChannel` `SuspendedSource`/`SuspendedSink` | 70 files/s | 154 files/s |

![동시 이미지 파일 I/O 처리량 차트](../../../docs/images/readme-charts/images-benchmark-file-io-throughput-chart-01.png)

## 해석

이전의 파일 64개 `homer.jpg` 벤치마크는 작은 파일의 API 오버헤드에 치우쳐
있었다. 이번 실행은 더 큰 사진 픽스처, 읽기 경로 6,400개, `readByteArray` 대신
스트리밍, 실제 대용량 파일 쓰기를 사용한다. 하지만 로컬 macOS Java 25 실행에서도
처리량 가설은 성립하지 않았다. `Path`가 가장 빨랐고 Okio가 그보다 느렸으며,
`AsynchronousFileChannel` 일시 중단 경계는 읽기와 쓰기 모두 훨씬 느렸다.

실무 지침은 보수적으로 유지한다. 코루틴 통합과 생명주기 편의를 위해 일시 중단
파일 채널 오버로드를 제공하되, 워크로드별 근거 없이 이미지 파일 I/O 처리량을
높인다고 주장하지 않는다.
