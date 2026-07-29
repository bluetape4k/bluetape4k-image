# Issue #272 ZXing Barcode Benchmark 구현 검토

## 범위와 기준선

- 기준선: `origin/develop...4abcd2cd20f23838d1b74765d471035384754b05`
- Issue: `#272`, milestone `0.4.0`
- Module slice: `benchmark/images-benchmark`
- Provider: ZXing `3.5.4`
- Evidence run: `issue-272-20260714-macos-arm64-01`
- Review input: approved design, approved implementation plan, current diff, accepted raw JSON, locale documentation, fresh tests, dependency reports, CodeGraph change 및 impact analysis

active collaboration interface는 필수 native-agent `agent_type` field를 노출하지 않는다. `model-routing.md`에 따라 여섯 perspective는 별도 read-only main-session pass로 실행했다. 이후 main session이 finding을 통합하고 normalize했다.

## Step 5 Verifier

| Accepted requirement | 현재 proof | 결과 |
|---|---|---|
| Supported latency and throughput tasks | `barcodeLatency`, `barcodeThroughput`, task listing, contract tests, 두 번의 성공한 real execution | PASS |
| Immutable QR, Code 128, and no-result PNGs | commit된 PNG 세 개, strict manifest, SHA-256/dimension/provider tests | PASS |
| Timed extraction excludes setup | `ZxingBarcodeExtractionBenchmark.setup`이 `extractBarcodes` 전에 load, decode, validate, reader construct를 수행한다. | PASS |
| Exact three-row protocol per mode | Finalizer validation, accepted `latency.json` 및 `throughput.json` | PASS |
| Reproducible evidence and interpretation | Run manifest, detailed report, English/Korean README table, raw link, metric direction, caveat | PASS |
| Dependency boundary | Main `runtimeClasspath`에는 provider-neutral API가 있으나 ZXing은 없고, `benchmarkRuntimeClasspath`에는 ZXing provider 및 core `3.5.4`가 있다. | PASS |
| Append-only accepted run | Fresh-report timestamp check, collision rejection, atomic directory promotion, duplicate-finalization proof | PASS |
| Repository scope | production barcode API/provider, settings, BOM, catalog, workflow, Nightly, Kover, native/JNI, OCR, Testcontainers change 없음 | PASS |

여섯 plan task가 모두 완료됐다. fixture expectation test가 Task 2 전에 provider를 필요로 했기 때문에 provider test dependency는 Task 1에서 도입됐다. strict fixture loader가 provider-neutral expectation type을 소유하므로 provider-neutral API는 main에 추가됐다. 어느 변경도 main runtime classpath로 ZXing을 leak하지 않는다.

Verifier verdict: `PASS`. hidden gap이나 deferred acceptance row는 남아 있지 않다.

## 여섯 관점 검토

| 관점 | P0 | P1 | P2 | P3 | 최종 결과 |
|---|---:|---:|---:|---:|---|
| Performance | 0 | 0 | 0 | 0 | 하나의 parameterized timed call이 pinned `avgt` 및 `thrpt` protocol 아래 preloaded image 세 개를 동일하게 측정한다. latency와 throughput은 독립적으로 관측된다. |
| Stability | 0 | 0 | 0 | 0 | strict fixture validation은 timing 전에 실행된다. report는 fresh, complete, finite 상태여야 하며 한 번만 staged된다. accepted evidence는 atomically 및 append-only로 promote된다. |
| Security | 0 | 0 | 0 | 0 | JSON은 strict하고 bounded하다. resource path는 absolute, traversal, dot, empty-segment, backslash form을 거부한다. fixture byte는 bounded 및 hash-pinned 상태다. |
| Operator/Ops | 0 | 0 | 0 | 0 | validated run ID, sequential mode, host/JVM/provider metadata, exact command, useful failure message, immutable raw artifact가 diagnosis와 rerun을 지원한다. |
| Developer/API | 0 | 0 | 0 | 0 | 새 Kotlin type은 internal이고 public barcode contract는 unchanged다. ZXing import는 benchmark/test code에 남으며 configuration-specific test가 cross-benchmark coupling을 피한다. |
| User/caller | 0 | 0 | 0 | 0 | English/Korean summary는 같은 여섯 value, link, direction, local-snapshot caveat를 담는다. one-provider mixed-unit result에는 chart보다 table이 더 명확하다. |
| Integration | 0 | 0 | 0 | 0 | spec, plan, task, fixture, raw evidence, hash, report, locale pair, repository hazard가 하나의 bounded benchmark feature에 동의한다. |

## 발견 사항과 수정

| Priority | 발견 사항 | 수정 및 재실행 근거 |
|---|---|---|
| P1 | 기존 codec-matrix contract가 전체 Gradle file에서 protocol literal을 count해서 barcode configuration 두 개 추가가 unrelated test를 깨뜨렸다. | 모든 assertion을 named codec-matrix configuration으로 scope했다. focused regression test와 전체 84-test benchmark suite가 통과한다. |
| P1 | Resource validation은 `..` 및 absolute path는 거부했지만 dot segment, duplicate separator, backslash는 이후 hash failure 전까지 허용했다. | error-specific RED test와 exact normalized-segment validation을 추가했다. focused test와 full suite가 통과한다. |
| P2 | Report validation이 `score`는 확인했지만 문서화된 `scoreError`는 확인하지 않았다. | TestKit failure case와 finite, non-negative `scoreError` validation을 추가했다. test는 repair 전 unexpected finalization에서 실패했고 이후 통과한다. |

## Performance, Stability, Hazard 근거

- CodeGraph는 commit된 diff를 medium change risk로 분석했다. wide generic symbol impact는 실제 file scope와 대조해 검토했으며, executable change가 모두 benchmark module에 있으므로 production execution flow 변경은 없다.
- timed method에는 harness가 도입한 loading, decoding, generation, validation, reflection, blocking I/O, retry, lock, coroutine, native work가 없다. provider result를 반환해 extraction allocation이 measurement에 포함된다.
- Kotlin diff에는 새 `!!`, `GlobalScope`, `runBlocking`, sleep, monitor, broad `runCatching`, cancellation boundary가 없다.
- accepted raw evidence에는 regular file 네 개가 있다. canonical fixture manifest는 byte-for-byte로 일치하고, 기록된 artifact hash 세 개가 모두 recompute되며, tree에는 local absolute path나 secret-like value가 없다.
- 두 번째 finalization attempt는 accepted hash를 변경하지 않고 실패했다.
- chart asset은 바뀌지 않았다. report가 provider 하나와 incompatible unit/direction을 가진 metric 두 개를 담으므로 Chart N/A가 적절하다. 향후 two-provider comparison은 complementary pastel pair rule을 사용해야 한다.
- CHANGELOG와 WIP는 release issue `#270` 및 `#271`에 계속 할당된다.

## 검증

| Command 또는 check | 결과 |
|---|---|
| `:bluetape4k-images-benchmark:cleanTest :bluetape4k-images-benchmark:test :bluetape4k-images-benchmark:benchmarkClasses --no-build-cache` | PASS, 84 tests |
| Fresh barcode API and ZXing provider tests | PASS, 14 + 8 tests |
| `:bluetape4k-images-benchmark:build` | PASS |
| `:bluetape4k-images-benchmark:tasks --all` and `projects` | PASS; both modes and finalizer registered |
| `detekt` | PASS (`NO-SOURCE`) |
| Main vs benchmark runtime dependency reports | PASS; ZXing absent from main and present only in benchmark runtime |
| Six-row mode/unit/protocol audit | PASS, 3 latency + 3 throughput rows |
| Artifact hash, secret/path, locale value/link, and `git diff --check` audits | PASS |

## 판정

`PASS` — final integrated count는 `P0=0`, `P1=0`, `P2=0`, `P3=0`이다.
Issue #272는 PR 및 exact-head CI validation으로 진행할 준비가 됐다.
