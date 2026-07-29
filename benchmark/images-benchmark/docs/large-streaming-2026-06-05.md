# 대체됨: 대용량 이미지 스트리밍 파이프라인 벤치마크 (2026-06-05)

> **역사적 근거 전용.** 이 보고서는 비대칭 회색조 변환을 사용했으므로 현재 권장
> 사항의 근거로 사용하면 안 된다. 갱신된
> [2026-07-10 보고서](large-streaming-2026-07-10.md)를 참고한다. 아래 원시 JSON은
> 역사적 재현성을 위해 변경하지 않고 보존한다.

이 보고서는 milestone `0.3.0`의 대용량 파일 및 OCR 전처리 작업을 위해 전체
load-transform-write 파이프라인 근거를 추가한다.

## 실행 환경

| 항목 | 값 |
|------|-------|
| Host | macOS arm64 |
| JVM | GraalVM Java 25.0.3 |
| 주요 명령 | `JAVA_HOME=$(/usr/libexec/java_home -v 25) ./gradlew :bluetape4k-images-benchmark:benchmarkLargeStreamingBenchmark -Pvips.impl=java25 --console=plain` |
| 주요 원시 JSON | [`raw/benchmark-large-streaming-2026-06-05-macos-java25.json`](raw/benchmark-large-streaming-2026-06-05-macos-java25.json) |
| 할당 부록 | [`raw/benchmark-large-streaming-jmh-gc-2026-06-05-macos-java25.json`](raw/benchmark-large-streaming-jmh-gc-2026-06-05-macos-java25.json) |

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25) \
  ./gradlew :bluetape4k-images-benchmark:benchmarkLargeStreamingBenchmark \
  -Pvips.impl=java25 --console=plain

./gradlew :bluetape4k-images-benchmark:benchmarkBenchmarkJar --console=plain

JAVA25=$(/usr/libexec/java_home -v 25)
"$JAVA25/bin/java" --enable-native-access=ALL-UNNAMED \
  -jar benchmark/images-benchmark/build/benchmarks/benchmark/jars/bluetape4k-images-benchmark-benchmark-jmh-0.3.0-JMH.jar \
  '.*ImageLargeStreamingBenchmark.*' -wi 1 -i 3 -f 1 -bm avgt -tu ms \
  -prof gc -rf json \
  -rff benchmark/images-benchmark/docs/raw/benchmark-large-streaming-jmh-gc-2026-06-05-macos-java25.json
```

> 벤치마크 소스와 주요 실행 경로는 `kotlinx-benchmark`를 사용한다. JVM에서
> `kotlinx-benchmark`는 JMH를 백엔드로 사용한다. Gradle DSL은 JMH profiler를
> 노출하지 않으므로 관리 힙 할당과 GC counter는 별도 JMH GC profiler 부록에
> 기록한다.

## 픽스처

벤치마크는 JMH 설정 중 결정적인 JPEG 픽스처를 생성한다. 큰 바이너리 파일은
저장소에 커밋하지 않는다.

| 시나리오 | 생성 크기 | 변환 | 역할 |
|----------|----------------------|-----------|------|
| `large-photo` | 4032x3024 | resize to 1920x1440, grayscale, JPEG encode | 큰 자연 사진형 파이프라인 |
| `ocr-document` | 2480x3508 | resize to 1240x1754, grayscale, JPEG encode | 문서/OCR 전처리형 파이프라인 |

## 결과

AverageTime은 낮을수록 좋다. 이는 로컬 비교 스냅숏이지 운영 순위가 아니다.

### Scrimage 행

| 경계 | `large-photo` | `ocr-document` | 해석 |
|----------|---------------|----------------|----------------|
| `ByteArray` | 224.04 ms/op | 143.64 ms/op | 인메모리 기준선이다. 편리하지만 압축 입력 byte를 staging한다. |
| `Path` | 223.19 ms/op | 145.13 ms/op | 이 실행의 다른 블로킹 Scrimage 경계와 비슷하다. |
| `InputStream` / `OutputStream` | 221.64 ms/op | 148.39 ms/op | 가장 빠른 `large-photo` Scrimage 행이며, 호출자 소유 stream 경계에 유용하다. |
| Okio `Source` / `Sink` | 222.00 ms/op | 145.59 ms/op | stream/path와 비슷하며 지연 시간 이점은 아니다. |
| Suspended file source/sink | 254.95 ms/op | 170.69 ms/op | Scrimage가 여전히 블로킹 stream으로 bridge하므로 더 느리다. |

### libvips Java 25 FFM 행

| 경계 | `large-photo` | `ocr-document` | 해석 |
|----------|---------------|----------------|----------------|
| `ByteArray` | 23.65 ms/op | 15.38 ms/op | Scrimage보다 훨씬 빠르지만 여전히 압축 입력 byte를 staging한다. |
| `Path` | 7.13 ms/op | 5.47 ms/op | 이 실행에서 가장 좋은 행이다. vips가 파일 경로에서 직접 디코딩할 수 있다. |
| `InputStream` / `OutputStream` | 23.99 ms/op | 15.59 ms/op | vips stream 경로가 제한된 byte를 읽기 때문에 `ByteArray`와 비슷하다. |

### 관리 힙 할당 부록

할당량은 JMH GC profiler의 `gc.alloc.rate.norm`이다. 값은 관리 힙 할당만 나타낸다.
네이티브 생명주기가 관심사라면 libvips 네이티브 메모리는 별도 native profiling
도구로 확인해야 한다.

| 경계 | `large-photo` 할당 | `ocr-document` 할당 | GC 관찰 |
|----------|--------------------------|---------------------------|----------------|
| Scrimage `ByteArray` | 226,613,134 B/op (216.12 MiB/op) | 172,450,359 B/op (164.46 MiB/op) | 실행당 young GC 5-6회 |
| Scrimage `Path` | 226,896,636 B/op (216.39 MiB/op) | 172,320,109 B/op (164.34 MiB/op) | `ByteArray`와 비슷함 |
| Scrimage `InputStream` / `OutputStream` | 227,932,451 B/op (217.37 MiB/op) | 173,361,261 B/op (165.33 MiB/op) | `Path` 및 Okio와 비슷함 |
| Scrimage Okio `Source` / `Sink` | 227,950,815 B/op (217.39 MiB/op) | 173,368,561 B/op (165.34 MiB/op) | 관리 할당 이점 없음 |
| Scrimage suspended source/sink | 229,457,449 B/op (218.83 MiB/op) | 174,171,066 B/op (166.10 MiB/op) | 할당이 약간 더 많고 bridge overhead가 있음 |
| vips `ByteArray` | 576,111 B/op (0.55 MiB/op) | 361,436 B/op (0.34 MiB/op) | 거의 0에 가까운 GC |
| vips `Path` | 569,114 B/op (0.54 MiB/op) | 359,858 B/op (0.34 MiB/op) | 거의 0에 가까운 GC |
| vips `InputStream` / `OutputStream` | 2,601,939 B/op (2.48 MiB/op) | 1,467,317 B/op (1.40 MiB/op) | 낮은 할당, 이 실행에서 GC count 1회 |

## 권장 사항

#165에서는 Okio와 suspended 경계를 Scrimage의 지연 시간, 처리량, 관리 할당 최적화가
아니라 생명주기와 통합 기능으로 설명한다. 대용량 파일 API는 `Path`, `InputStream`,
`Source`, 호출자 소유 sink 경계가 이미 있는 경우 불필요한 압축 `ByteArray` staging을
피해야 한다. 다만 README/API 문구에는 Scrimage 디코딩/인코딩이 내부적으로 여전히
블로킹이며, 디코딩된 이미지의 힙 할당을 계속 지배한다는 점을 명시해야 한다.

#1에서는 문서/OCR형 행을 전처리 근거로 사용한다. 큰 문서의 resize/encode는
벤치마크 lane에서 가능하지만, OCR 구현은 선택적 OCR 모듈을 선호하고 네이티브/모델
의존성을 격리해야 한다.

성능에 민감한 대용량 이미지 변환에서는 libvips가 여전히 주요 권장 경로다. 이 Java
25 FFM 실행에서 `Path` 파이프라인은 지연 시간과 관리 힙 할당 모두에서 가장 강한
대용량 파일 행이었다. Java wrapper 작업은 1 MiB/op 아래에 머물고 변환은 네이티브
백엔드가 맡기 때문이다.
