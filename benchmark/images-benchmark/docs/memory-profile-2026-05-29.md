# 이미지 워크로드 메모리 프로필 (2026-05-29)

이 보고서는 대표적인 리사이즈, 자르기, 인코딩, 썸네일 워크로드의 할당량 근거를
추가한다.

## 실행 환경

| 항목 | 값 |
|------|-------|
| 호스트 | macOS arm64 |
| JVM | GraalVM Java 25.0.3 |
| 기본 실행 명령 | `JAVA_HOME=$(/usr/libexec/java_home -v 25) ./gradlew :bluetape4k-images-benchmark:benchmarkMemoryProfileBenchmark --console=plain` |
| 기본 원본 JSON | [`raw/benchmark-memory-profile-2026-05-29-macos-java25.json`](raw/benchmark-memory-profile-2026-05-29-macos-java25.json) |
| 할당량 부록 | [`raw/benchmark-memory-profile-jmh-gc-2026-05-29-macos-java25.json`](raw/benchmark-memory-profile-jmh-gc-2026-05-29-macos-java25.json) |

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25) \
  ./gradlew :bluetape4k-images-benchmark:benchmarkMemoryProfileBenchmark --console=plain
```

> 벤치마크 소스와 기본 실행 경로는 `kotlinx-benchmark`를 사용한다. JVM에서
> `kotlinx-benchmark`는 JMH를 백엔드로 사용한다. Gradle DSL이 JMH 프로파일러를
> 제공하지 않으므로 할당량 값은 별도의 JMH GC 프로파일러 부록에 기록한다.

## 픽스처

| 픽스처 | 원본 | 크기 | 역할 |
|---------|--------|------------|------|
| `landscape.jpg` | `images/src/test/resources/images/landscape.jpg` | 4032x3024 | 사진 리사이즈/인코딩/vips 입력 |
| `homer.jpg` | `images/src/test/resources/images/homer.jpg` | 1248x702 | 썸네일 픽스처 |

## 결과

AverageTime은 낮을수록 좋다. 할당량은 `gc.alloc.rate.norm`이다.

| 벤치마크 | 해상도 | AverageTime | 할당량 |
|-----------|------------|-------------|------------|
| `scrimage_encodeJpeg` | N/A | 146.09 ms/op | 101,017,430 B/op (96.34 MB/op) |
| `scrimage_encodePng` | N/A | 832.79 ms/op | 268,386 B/op (0.26 MB/op) |
| `scrimage_scaleTo` | 1920x1080 | 115.34 ms/op | 25,206,811 B/op (24.04 MB/op) |
| `scrimage_scaleTo` | 1280x720 | 93.66 ms/op | 14,127,469 B/op (13.47 MB/op) |
| `vips_crop` | 1920x1080 | 0.085 ms/op | 4,744 B/op (4.63 KB/op) |
| `vips_crop` | 1280x720 | 0.085 ms/op | 4,744 B/op (4.63 KB/op) |
| `vips_resize` | 1920x1080 | 0.246 ms/op | 4,242 B/op (4.14 KB/op) |
| `vips_resize` | 1280x720 | 0.271 ms/op | 4,246 B/op (4.15 KB/op) |
| `vips_thumbnail` | 1920x1080 | 0.266 ms/op | 4,043 B/op (3.95 KB/op) |
| `vips_thumbnail` | 1280x720 | 0.274 ms/op | 4,052 B/op (3.96 KB/op) |
| `vips_encodeJpeg` | N/A | 44.16 ms/op | 271,075 B/op (0.26 MB/op) |

![이미지 워크로드 메모리 프로필 차트](../../../docs/images/readme-charts/images-benchmark-memory-profile-chart-01.png)

## 참고 사항

- JMH GC 프로파일러는 작업 중 libvips가 유지하는 전체 네이티브 메모리가 아니라
  관리 힙 할당량을 보고한다.
- vips 변환 행은 래퍼 객체와 Java 측 생명주기 코드가 한 자릿수 KB/op 범위를
  유지하는지 확인하므로 여전히 의미가 있다.
- 네이티브 생명주기 회귀는 이 관리 힙 할당 프로필과 함께 OS/네이티브 메모리
  도구로 조사해야 한다.
- 코루틴 `Path` 로드/쓰기 도우미는 압축 파일 전체를 중간 `ByteArray` 값으로
  구체화하지 않고 Scrimage를 통해 직접 스트리밍한다. `bluetape4k-okio`
  `BufferedSource`/`BufferedSink` 오버로드는 호출자가 소유한 Okio 스트리밍
  경계를 지원한다.
