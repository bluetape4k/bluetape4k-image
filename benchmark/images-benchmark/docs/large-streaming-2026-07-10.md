# 대용량 이미지 스트리밍 파이프라인 벤치마크 (2026-07-10)

이 보고서는 Issue #197을 위해 갱신한, 색상 보존 대용량 스트리밍 스냅숏이다. 모든
행에서 Java 25 FFM libvips 백엔드와 동일한 결정적 `large-photo`, `ocr-document`
워크로드를 사용한다.

## 실행 환경

| 항목 | 값 |
|------|-------|
| Host | macOS arm64 |
| JVM | Java 25 (local GraalVM distribution) |
| 백엔드 | Java 25 FFM binding을 통한 libvips; JNI fallback 없음 |
| 명령 | `JAVA_HOME=$(/usr/libexec/java_home -v 25) ./gradlew :bluetape4k-images-benchmark:benchmarkLargeStreamingBenchmark -Pvips.impl=java25 --console=plain` |
| 주요 원시 JSON | [`raw/benchmark-large-streaming-2026-07-10-macos-java25.json`](raw/benchmark-large-streaming-2026-07-10-macos-java25.json) |
| 주요 SHA-256 | `b82f80dd530c586b3827e1af7750e479ec2dec8d5e6795effe7f0f34f501962f` |
| GC 부록 | [`raw/benchmark-large-streaming-jmh-gc-2026-07-10-macos-java25.json`](raw/benchmark-large-streaming-jmh-gc-2026-07-10-macos-java25.json) |
| GC SHA-256 | `fccccba2c5fb4cd8e4604fb44a208d5ef560fb6dd1b9bf74bea0904b6c9c7df6` |

원시 파일은 정제된 JMH JSON 스냅숏이다. 유효 설정은 fork 1회, warmup iteration 1회,
1초 measurement iteration 3회이며, `AverageTime` 단위는 `ms/op`이다. 낮을수록
좋다. GC 부록은 네이티브 메모리 생명주기가 아니라 관리 힙 `gc.alloc.rate.norm`만
`B/op` 단위로 보고한다.

## 픽스처와 변환

| 시나리오 | 크기 | 변환 |
|----------|------------|-----------|
| `large-photo` | 4032x3024 | decode -> resize to 1920x1440 -> JPEG encode |
| `ocr-document` | 2480x3508 | decode -> resize to 1240x1754 -> JPEG encode |

이 비교에는 회색조나 다른 색상 변경 필터가 포함되지 않는다.

## AverageTime 결과

| 경계 | `large-photo` | `ocr-document` |
|----------|---------------|----------------|
| Scrimage `ByteArray` | 186.14 ms/op | 117.41 ms/op |
| Scrimage `Path` | 187.44 ms/op | 114.77 ms/op |
| Scrimage `InputStream` / `OutputStream` | 183.65 ms/op | 114.88 ms/op |
| Scrimage Okio `Source` / `Sink` | 183.37 ms/op | 115.41 ms/op |
| Scrimage suspended source/sink | 215.61 ms/op | 136.77 ms/op |
| vips `ByteArray` | 25.13 ms/op | 16.30 ms/op |
| vips `Path` | 27.34 ms/op | 16.76 ms/op |
| vips `InputStream` / `OutputStream` | 25.76 ms/op | 16.61 ms/op |

![대용량 스트리밍 파이프라인 벤치마크 차트](../../../docs/images/readme-charts/images-benchmark-large-streaming-chart-01.png)

## 관리 힙 할당 부록

| 경계 | `large-photo` | `ocr-document` |
|----------|---------------|----------------|
| Scrimage `ByteArray` | 121,127,659 B/op | 89,281,836 B/op |
| Scrimage `Path` | 119,104,337 B/op | 87,489,727 B/op |
| Scrimage `InputStream` / `OutputStream` | 120,145,564 B/op | 88,532,271 B/op |
| Scrimage Okio `Source` / `Sink` | 120,158,675 B/op | 88,539,439 B/op |
| Scrimage suspended source/sink | 121,678,506 B/op | 89,306,881 B/op |
| vips `ByteArray` | 577,814 B/op | 363,620 B/op |
| vips `Path` | 2,602,946 B/op | 1,469,544 B/op |
| vips `InputStream` / `OutputStream` | 2,603,465 B/op | 1,469,863 B/op |

이는 관리 힙 관찰값일 뿐이다. 네이티브 libvips 메모리가 없거나 Java 할당 숫자로
제한된다는 뜻이 아니다.

## 해석

이는 로컬 비교 스냅숏이지 운영 순위가 아니다. 이 워크로드에서 Java 25 FFM vips
행은 Scrimage 행보다 뚜렷하게 빠르다. 반면 Scrimage는 색상을 보존하는 블로킹
구현으로 남는다. vips를 사용할 수 있는 로컬 파일에는 `Path` 경계를 선택한다.
stream/Okio 경계는 지연 시간 개선 약속이 아니라 생명주기 또는 호출자 소유 I/O
통합을 위해 선택한다.

이전 비대칭 보고서는 역사적 근거로만 보존한다.
[`large-streaming-2026-06-05.md`](large-streaming-2026-06-05.md). Its raw data
의 원시 데이터는 현재 권장 사항의 근거로 사용하면 안 된다.
