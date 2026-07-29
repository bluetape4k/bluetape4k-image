# ZXing 바코드 추출 벤치마크 — 2026-07-14

이 보고서는 변경할 수 없는 QR, Code 128, 결과 없음 PNG 픽스처를 사용한 로컬
ZXing 3.5.4 추출 스냅샷을 기록한다. 이미 디코딩된 `ImmutableImage`에서 추출하는
경로를 측정하며, PNG 리소스 로딩과 디코딩은 JMH trial 준비 단계에서 한 번만
수행하므로 측정 대상 메서드에서 제외된다.

## 실행 환경

| 항목 | 값 |
|------|-------|
| 호스트 | Apple M5, 논리 프로세서 10개 |
| OS | macOS 26.5.1 (25F80), arm64 |
| JVM | GraalVM Java 25.0.3, Oracle Corporation |
| Gradle | 9.6.0 |
| Kotlin | 2.4.0 프로젝트 툴체인 |
| 프로바이더 | ZXing 3.5.4 |
| 실행 ID | `issue-272-20260714-macos-arm64-01` |

지연 시간 모드와 처리량 모드는 순차 실행했다.

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25) \
./gradlew :bluetape4k-images-benchmark:benchmarkBarcodeLatencyBenchmark \
  -Pbarcode.benchmark.runId=issue-272-20260714-macos-arm64-01 \
  --console=plain

JAVA_HOME=$(/usr/libexec/java_home -v 25) \
./gradlew :bluetape4k-images-benchmark:benchmarkBarcodeThroughputBenchmark \
  -Pbarcode.benchmark.runId=issue-272-20260714-macos-arm64-01 \
  --console=plain

CPU="$(sysctl -n machdep.cpu.brand_string)"
JAVA_HOME=$(/usr/libexec/java_home -v 25) \
./gradlew :bluetape4k-images-benchmark:finalizeBarcodeBenchmarkEvidence \
  -Pbarcode.benchmark.runId=issue-272-20260714-macos-arm64-01 \
  -Pbarcode.benchmark.cpu="$CPU" \
  --console=plain
```

두 모드 모두 스레드 1개, fork 1회, 1초 워밍업 3회, 1초 측정 반복 5회를 사용한다.

## 변경 불가 픽스처

| 시나리오 | 크기 | 예상 결과 | SHA-256 |
|----------|------------|-----------------|---------|
| QR | 220×220 | `QR_CODE`: `bluetape4k-issue-272-qr` | `4338ae8e47278b7c2816028e7b40ca1466bae06560b02af708d3ef57f6adef62` |
| Code 128 | 360×120 | `CODE_128`: `BLUETAPE4K-272` | `df5da2cd0fb3bf17940a3def3bd1fb54d1f851c8a21e071d0316c0d3ef436782` |
| 결과 없음 | 220×220 | 빈 결과 | `86aad41769423ad85a979fefe109d00829044a1eba5d891547499413e3d9ff2b` |

승인된 실행은 엄격한 픽스처 매니페스트를 복사하고 두 원본 보고서 해시와 함께
그 해시를 기록한다. [변경 불가 원본 근거](raw/issue-272-20260714-macos-arm64-01/)에서
확인할 수 있다.

## 결과

지연 시간은 JMH `AverageTime`을 `ms/op` 단위로 측정하며 낮을수록 좋다. 처리량은
별도의 JMH `Throughput` 실행에서 `ops/s` 단위로 측정하며 높을수록 좋다. 지연
시간의 역수로 계산한 값이 아니다.

| 시나리오 | 지연 시간 (ms/op) | 처리량 (ops/s) | 예상 결과 |
|----------|-----------------|--------------------|-----------------|
| QR | 0.174126 ± 0.001086 | 5702.142 ± 37.446 | QR 결과 1개 |
| Code 128 | 0.112914 ± 0.000715 | 8839.015 ± 135.003 | Code 128 결과 1개 |
| 결과 없음 | 0.271397 ± 0.009099 | 3690.012 ± 32.832 | 빈 목록 |

## 해석과 한계

이 호스트와 픽스처 세트에서는 빈 이미지의 추출 시간이 가장 길었고 QR과
Code 128이 뒤를 이었다. 이 행들은 한 프로바이더에서 서로 다른 워크로드 형태를
보여 줄 뿐이다. 프로바이더 간 비교, 애플리케이션 꼬리 지연 시간 예측, 호스트 간
운영 성능 순위를 제시하지 않는다. 결과 객체 할당과 ZXing 추출은 포함하지만
클래스패스 I/O, PNG 디코딩, 리더 생성, 예상 결과 검증은 준비 단계에서만
수행한다.

차트는 의도적으로 N/A 처리했다. 프로바이더 하나, 워크로드 형태 세 가지, 단위와
좋은 방향이 서로 다른 메트릭 두 개는 시각적 비교보다 하나의 표에서 더 명확하다.
향후 두 프로바이더를 비교하는 차트를 만들 때는 저장소의 보색 파스텔 조합 규칙을
따라야 한다.

이 작업은 [issue #272](https://github.com/bluetape4k/bluetape4k-image/issues/272)에서
추적한다.
