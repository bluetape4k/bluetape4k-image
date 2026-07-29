# Issue #272 ZXing Barcode Extraction Benchmark 설계

## 1. 배경

- 이슈: [#272](https://github.com/bluetape4k/bluetape4k-image/issues/272)
- 마일스톤: `0.4.0`
- 작업 유형: Type A - Full Feature
- Repository: `bluetape4k/bluetape4k-image`
- Base: `origin/develop`
- Branch: `perf/issue-272-zxing-barcode-benchmark`

`bluetape4k-images-barcode-zxing`은 provider-neutral barcode API의 pure-JVM ZXing 구현을 제공한다.
test는 QR, Code 128, no-result behavior를 증명하지만, repository에는 반복 가능한 extraction latency 또는
throughput evidence가 없다. Issue #272는 production barcode contract를 변경하지 않고 이 evidence를 추가한다.

## 2. 목표

1. ZXing extraction latency와 throughput을 독립적으로 측정한다.
2. 대표적인 QR, Code 128, no-result shape를 다룬다.
3. timed extraction operation에서 fixture generation, resource loading, PNG decoding을 제외한다.
4. immutable fixture bytes, hash, command, environment metadata, raw JSON, result table, interpretation limit를
   commit한다.
5. English/Korean benchmark README section을 equivalent하게 유지한다.

## 3. 비목표

- 다른 barcode provider를 추가하거나 비교하지 않는다.
- `images-barcode-api` 또는 `images-barcode-zxing` production API를 변경하지 않는다.
- module, artifact, public API, BOM/catalog entry, dependency version을 추가하지 않는다.
- CI, Nightly, Kover, native/JNI, OCR, Testcontainers surface를 변경하지 않는다.
- PNG loading, fixture generation, web delivery, end-to-end request handling을 측정하지 않는다.
- 하나의 local run을 production-wide provider ranking으로 제시하지 않는다.

## 4. 현재 증거

- `BarcodeReader.readBarcodes`는 provider-neutral synchronous extraction boundary다.
- `ZxingBarcodeReader`는 각 call 내부에서 ZXing `MultiFormatReader`를 만들고 실행하며 provider-neutral result를
  반환한다.
- 기존 provider test는 QR과 Code 128 image를 생성하고 blank image가 empty result를 반환함을 검증한다.
- `benchmark/images-benchmark`는 이미 `kotlinx-benchmark`, JMH JVM target, named configuration,
  JSON report, Gradle TestKit contract, bilingual result documentation을 사용한다.
- approved baseline은 14개 barcode API test와 8개 ZXing provider test를 성공적으로 실행했다. benchmark module
  test와 `tasks --all`도 통과했다.

## 5. 검토한 접근법

### 5.1 scenario parameter를 가진 단일 benchmark class - 선택

하나의 `ZxingBarcodeExtractionBenchmark`와 fixed scenario parameter를 사용한다. 같은 class를 별도 latency/throughput
configuration으로 실행한다.

장점:

- 두 result set이 정확히 같은 operation과 fixture setup을 공유한다.
- scenario coverage를 비교하고 검증하기 쉽다.
- implementation duplication이 최소다.

trade-off: raw row는 distinct method name 대신 scenario parameter를 포함한다.

### 5.2 scenario별 별도 method - 거부

이 방식은 explicit method name을 만들지만 setup을 중복하고 한 scenario가 다른 scenario에서 drift하기 쉬워진다.

### 5.3 latency와 throughput class 분리 - 거부

mode를 분리할 수 있지만 state와 extraction code를 모두 중복한다. 두 measurement path가 user value 없이 diverge할 수 있다.

## 6. Fixture contract

다음 immutable PNG input을 `benchmark/images-benchmark/src/main/resources/bench/barcode/` 아래에 commit한다.

| Scenario | Shape | Expected result |
|---|---:|---|
| `qr` | square QR image | one `QR_CODE` with the pinned payload |
| `code-128` | wide linear image | one `CODE_128` with the pinned payload |
| `no-result` | square blank image | empty result list |

같은 directory의 `manifest.json`은 다음을 포함한다.

- schema version과 SHA-256 algorithm.
- fixture id와 classpath resource.
- width와 height.
- exact SHA-256.
- successful case의 expected payload와 provider-neutral format.
- blank case의 explicit empty-result expectation.
- 해당되는 경우 ZXing writer version과 parameter를 포함한 generation provenance.

runtime benchmark setup은 이 input을 재생성하거나 overwrite하지 않는다. selected bytes를 load하고 hash/dimension을
검증하며 PNG를 한 번 decode하고 measurement 시작 전에 expected barcode result를 확인한다.

loader는 선언된 세 scenario id만 받으며 resource path를 fixed `bench/barcode/` classpath prefix로 제한하고
absolute path와 `..` segment를 거부하며 encoded fixture 하나를 1 MiB로 제한한다. 이 check는 malformed manifest가
benchmark run을 unbounded 또는 unrelated resource read로 바꾸는 일을 막는다.

## 7. Benchmark 아키텍처

### 7.1 Fixture loader 구성

internal main-source fixture component는 manifest를 parse하고 selected resource를 검증한 뒤 immutable image와
provider-neutral expectation을 반환한다. 이 component는 기존 image/serialization surface에만 의존하며 ZXing
provider에 의존하지 않는다. test는 public API를 노출하지 않고 success와 corrupted/missing/hash-mismatch/path/size
failure path를 exercise한다.

### 7.2 Benchmark state 구성

`ZxingBarcodeExtractionBenchmark`는 fixed `scenario` parameter를 가진 하나의 trial-scoped state를 사용한다.
`@Setup`은 fixture를 load/validate하고 `ZxingBarcodeReader`를 만든다. timed benchmark method는 다음만 수행한다.

```kotlin
reader.readBarcodes(image, options)
```

method는 JMH backend가 value를 소비하도록 result list를 반환한다. result allocation과 ZXing decode work는
extraction의 일부로 남는다. PNG I/O와 `ImmutableImage` construction은 포함하지 않는다.

### 7.3 Measurement configuration

두 configuration 모두 같은 benchmark class와 scenario를 사용한다.

| Configuration | Mode | Unit | Direction |
|---|---|---|---|
| `barcodeLatency` | `AverageTime` | `ms/op` | lower is better |
| `barcodeThroughput` | `Throughput` | `ops/s` | higher is better |

각 configuration은 one thread, one fork, three one-second warmup, five one-second measurement iteration을
사용한다. generated Gradle task는 documentation 또는 evidence collection에 사용하기 전에 `tasks --all`에서 검증한다.

## 8. Dependency boundary

benchmark source set은 `:bluetape4k-images-barcode-zxing`에 대한 `benchmarkImplementation`을 추가하고,
test는 대응 `testImplementation`을 추가한다. module의 기존 main `implementation`과 published dependency surface는
provider를 얻지 않는다. benchmark setup과 expectation test는 `ZxingBarcodeReader`와 provider-neutral API model을
사용한다. `com.google.zxing`을 import하거나 새 external coordinate/version을 추가하지 않는다. ZXing dependency는
provider module이 계속 소유한다.

## 9. 실패 처리

| Failure | Required behavior |
|---|---|
| Fixture is missing or unreadable | fail setup with the scenario/resource name |
| SHA-256 or dimensions differ | fail setup before any measurement |
| QR/Code 128 payload or format differs | fail setup before any measurement |
| Blank fixture returns a result | fail setup before any measurement |
| Manifest path escapes the fixed prefix or fixture exceeds 1 MiB | reject it before image decoding |
| Configuration scenario or timing contracts diverge | fail the Gradle contract test |
| Raw output is missing, partial, or would overwrite accepted evidence | reject it and use a new run id |
| Documentation overstates local evidence | block review until caveats are restored |

benchmark는 extraction exception을 empty result로 변환하지 않는다. provider behavior가 authoritative하게 남는다.

## 10. 테스트 전략

각 behavior에 RED/GREEN cycle을 사용한다.

1. fixture manifest parsing과 complete three-scenario coverage.
2. resource hash, dimension, expected success result, expected no-result.
3. missing resource, changed bytes, expectation mismatch failure.
4. benchmark configuration name, mode, unit, timing, fork/thread count, scenario coverage, target include pattern.
5. benchmark source-set compilation과 focused latency/throughput smoke run.

targeted validation은 benchmark module과 barcode provider에서 시작한 뒤 repository static check로 비례 확장한다.
benchmark와 모든 native check는 sequential하게 유지한다. 이 benchmark 자체는 pure JVM이며 libvips backend를 load하면
안 된다.

## 11. Evidence와 문서화

각 attempt는 validated run id를 사용하고 fresh build directory 아래에만 쓴다. accepted evidence는
`benchmark/images-benchmark/docs/raw/issue-272-<run-id>/`로 한 번 promote된다. existing target은 overwrite하지
않는다. accepted run은 다음을 기록한다.

- exact Gradle command.
- macOS/architecture/CPU, JVM vendor/version, ZXing provider version.
- fixture id, dimension, payload class, SHA-256 value.
- latency와 throughput raw JSON path.
- command, environment, fixture hash, 두 raw file을 같은 attempt로 묶는 run manifest.
- 여섯 result row. 각 mode당 세 scenario다.
- score error와 interpretation caveat.

`benchmark/images-benchmark/docs/` 아래에 detailed English report를 쓰고, `README.md`와 `README.ko.md`에는
equivalent concise section을 추가한다.

이 issue에서 chart는 N/A다. provider가 하나이고 workload shape는 세 개뿐이며 두 metric의 unit/direction이
호환되지 않기 때문이다. table이 여섯 value를 더 정확히 제시한다. 나중에 provider comparison이 chart를 도입하면
series가 정확히 두 개일 때 complementary pastel pair를 사용하고, 세 개 이상이면 categorical palette를 사용한다.

## 12. 호환성과 repository hazard

- 기존 barcode API/provider behavior와 artifact coordinate는 변경하지 않는다.
- 기존 benchmark module과 kotlinx-benchmark plugin을 재사용한다. module registration 변경은 없다.
- provider는 benchmark/test configuration에 제한되므로 published benchmark artifact dependency surface는 변경되지 않는다.
- benchmark source는 production coverage에서 계속 제외한다.
- README locale parity가 필요하다.
- CHANGELOG와 WIP update는 issue #270과 #271이 계속 소유하므로 여기서 premature하게 업데이트하지 않는다.
- 측정되는 pure-JVM path는 libvips, OCR, Docker, network access를 요구하지 않는다.

## 13. 인수 기준

- `barcodeLatency`와 `barcodeThroughput`이 모두 repository-supported Gradle benchmark task를 통해 실행된다.
- QR, Code 128, no-result scenario는 immutable, hash-pinned local PNG를 사용한다.
- timed operation은 fixture generation, loading, PNG decoding을 제외한다.
- accepted raw JSON은 configured mode, unit, thread, fork, warmup, iteration contract와 함께 정확히 세 expected
  scenario row를 포함한다.
- documentation은 command, environment, raw path, result table, metric direction, caveat를 포함한다.
- English/Korean README section은 source-equivalent 상태를 유지한다.
- review가 P0=0, P1=0으로 수렴한다.

## 14. 완료 정의

- spec과 implementation plan이 approved/reviewed/committed 상태다.
- 모든 test-first fixture와 benchmark contract가 통과한다.
- 두 benchmark mode가 accepted raw evidence와 여섯 documented row를 만든다.
- targeted compile/test, relevant static check, `git diff --check`가 통과한다.
- durable lesson과 final review evidence가 commit된다.
- issue-linked PR은 올바른 milestone, label, assignee, final DoD body, green CI를 가지며 unresolved P0/P1 finding이 없다.
- fresh merge-ready approval 전까지 merge는 block된다. merge 후 local `develop` sync와 merged worktree/branch cleanup은
  automatic이다.
