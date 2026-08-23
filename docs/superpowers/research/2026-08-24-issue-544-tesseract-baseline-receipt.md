# Issue #544 Tesseract baseline 실행 receipt

## 상태

`BASELINE_ONLY` — #544의 동일 corpus 비교를 위한 Tesseract baseline은 재현 가능한 raw
JSON으로 보존했지만, PaddleOCR service 결과·CER/WER/geometry 비교·adoption 판단은 아직
수행하지 않았다. #545 service/security receipt와 #547 gate가 완료되기 전에는 이 결과를
PaddleOCR 채택 근거로 해석하지 않는다.

## 추적성

| 항목 | 값 |
|---|---|
| Issue | [#544](https://github.com/bluetape4k/bluetape4k-image/issues/544) |
| Parent | [#169](https://github.com/bluetape4k/bluetape4k-image/issues/169), Epic [#513](https://github.com/bluetape4k/bluetape4k-image/issues/513) |
| 기준 commit | `83f8a3b888425e4706ab8e0a7d92e4700a6d4868` |
| run ID | `issue-544-20260824-macos-arm64-java25-baseline` |
| raw manifest | [`run-manifest.json`](../../../benchmark/images-benchmark/docs/raw/issue-544-20260824-macos-arm64-java25-baseline/run-manifest.json) |
| contract | [`2026-08-19-issue-544-ocr-benchmark-corpus.md`](2026-08-19-issue-544-ocr-benchmark-corpus.md) |

## 실행 환경과 사전 조건

| 항목 | 값 |
|---|---|
| Host | Mac16,11, macOS 26.6.2, arm64, 12 logical CPUs |
| JVM | Oracle GraalVM 25.0.4 (25.2.4+7.1 LTS) |
| Gradle | 9.7.0 |
| OCR engine | Tesseract 5.5.3 / Leptonica 1.87.0 |
| tessdata | `/opt/homebrew/share/tessdata` |
| languages | `eng`, `kor`, `jpn` 모두 `tesseract --list-langs`에서 확인 |
| network/model download | 실행하지 않음; host-installed traineddata만 사용 |

Tesseract host prerequisite와 언어 확인은 benchmark `@Setup(Level.Trial)`에서 수행했다.
누락된 traineddata나 fixture token이 있으면 측정 전에 실패하며, 결과를 조용히 생략하지
않는다.

## Fixture 계약

매니페스트 자체의 SHA-256은
`eeae6d9dc34fa8281befad9b288196a4fac955ca0b25bda77102b5b1b6079bb0`이다. 모든 fixture는
resource 경로·크기·SHA-256·언어·expected token·provenance를 검증한 뒤 사용했다.

| scenario | 크기 | language | fixture SHA-256 |
|---|---:|---|---|
| `clean-text` | 1600x1000 | `eng` | `f036a0ec994554fa6c214fe883603bea79c399c934b4674d84f77737ea0322b8` |
| `noisy-scan` | 1600x1000 | `eng` | `caa9520bb2e01711a87463a69f4267e6f329c42018793f13322b801e988d177f` |
| `rotated-document` | 1000x1600 | `eng` | `1b7865690f6882782593d0be46ff7564fe6d91b444797f665ac5a2fa9f1fcc76` |
| `multilingual-text` | 1600x1000 | `eng`, `kor`, `jpn` | `a3b5cba05b847e8ab996edf7df820e13013850f9e2089c3e898e1cfacfe238f2` |

## Protocol과 검증

동일한 JMH protocol로 직접 `extractText`와 grayscale/회전 정규화를 포함한
`preprocessAndExtract`를 각각 측정했다.

- threads: 1
- forks: 1
- warmups: 3 x 1 s
- measurements: 5 x 1 s
- latency: `avgt`, `ms/op`
- throughput: `thrpt`, `ops/s`
- 실행 순서: latency 완료 후 throughput 실행

실행 명령:

```bash
./gradlew :bluetape4k-images-benchmark:benchmarkOcrLatencyBenchmark --console=plain
./gradlew :bluetape4k-images-benchmark:benchmarkOcrThroughputBenchmark --console=plain
```

두 task 모두 `BUILD SUCCESSFUL`로 종료했고, build script의 fresh report coverage·mode·unit·
scenario validator가 8개 row를 확인했다.

## 결과 요약

| scenario | direct latency (ms/op) | preprocess latency (ms/op) | direct throughput (ops/s) | preprocess throughput (ops/s) |
|---|---:|---:|---:|---:|
| `clean-text` | 218.456 ± 5.369 | 360.076 ± 861.162 | 4.657 ± 0.197 | 4.751 ± 2.783 |
| `noisy-scan` | 369.915 ± 11.358 | 290.184 ± 17.242 | 2.749 ± 0.078 | 3.342 ± 0.447 |
| `rotated-document` | 170.684 ± 5.206 | 189.036 ± 5.192 | 5.842 ± 0.269 | 5.105 ± 0.671 |
| `multilingual-text` | 612.407 ± 1391.148 | 400.330 ± 21.158 | 2.692 ± 0.134 | 2.475 ± 0.178 |

오차는 JMH score error이며, 원시 값과 모든 benchmark metadata는 raw JSON을 기준으로
한다. 이 한 호스트의 실행 기준 데이터만으로 품질 우위나 운영 처리량을 일반화하지 않는다.

## 해석과 미완료 gate

- `noisy-scan`에서는 전처리 경로가 이번 실행 기준 데이터에서 더 낮은 latency와 높은 throughput을
  보였지만, `clean-text`·`rotated-document`에서는 비용이 증가했다. 전처리는 보편 최적화가
  아니라 workload별 선택이다.
- `multilingual-text` direct latency의 오차가 크게 나타났으므로 반복 확대·CI host matrix와
  품질 metric을 추가하기 전에는 안정된 성능 결론을 내리지 않는다.
- 현재 receipt는 Tesseract token smoke와 latency/throughput baseline만 증명한다. CER/WER,
  geometry F1, empty/error taxonomy, RSS/peak native memory, cold-start model load는 아직
  이 receipt의 evidence가 아니다.
- PaddleOCR 동일 입력 결과는 #545의 pinned service/offline/security receipt 이후 별도 run으로
  생성하며, 두 provider의 비교와 `ADOPT`/`DEFER`/`REJECT`는 #547에서 결정한다.

| #544 완료 조건 | 상태 | 다음 evidence |
|---|---|---|
| corpus provenance/license/hash·정답 고정 | PASS | manifest와 fixture SHA/read-back |
| 최소 반복·warm-up·artifact 형식 | PASS | run manifest와 2개 raw JSON |
| Tesseract baseline 재현 | PASS | latency/throughput task 성공, 8+8 rows |
| PaddleOCR 동일 corpus 비교 | PENDING | #545 service receipt 후 provider run |
| 품질 CER/WER·geometry·empty/error 비교 | PENDING | schema에 맞춘 provider별 result artifact |
| RSS/peak memory·cold/warm·concurrency | PENDING | bounded resource profile과 host matrix |
| 최종 채택 판단 | PENDING | #547 decision issue |

## Raw evidence

| artifact | SHA-256 |
|---|---|
| [`ocr-latency.json`](../../../benchmark/images-benchmark/docs/raw/issue-544-20260824-macos-arm64-java25-baseline/ocr-latency.json) | `603f8fa2134b812185ed622d56620f843cc45e41d8a85e547064dfc0b1504580` |
| [`ocr-throughput.json`](../../../benchmark/images-benchmark/docs/raw/issue-544-20260824-macos-arm64-java25-baseline/ocr-throughput.json) | `a750ef4030810b2411af4367985eacb594d4c5f41dfc54dccbeda28eceba2a66` |
| [`run-manifest.json`](../../../benchmark/images-benchmark/docs/raw/issue-544-20260824-macos-arm64-java25-baseline/run-manifest.json) | see file content and report hashes |

이 receipt은 synthetic fixture와 host-installed Tesseract만 사용하며, 개인정보·외부
dataset·model binary를 저장소에 추가하지 않는다.
