# Issue #208 Codec/Runtime Matrix 구현 검토

## 범위와 기준선

- 기준선: `origin/develop...HEAD`
- Issue: `#208`, milestone `0.4.0`
- Runtime lane: Java 21 JVips JNI preflight 및 Java 25 FFM measurement
- Fixture: `cafe.jpg` (`1920x1080`, web photo) 및 `homer.jpg` (`512x512`, profile)
- Evidence run: `issue-208-20260713-macos-arm64-09`
- Measurement commit: `999b1e87f764a175d9887af9972ed41644e37f9e`

## 여섯 관점 검토

| 관점 | P0 | P1 | P2 | P3 | 결과 |
|---|---:|---:|---:|---:|---|
| Performance | 0 | 0 | 0 | 0 | 측정된 각 cell은 scenario, format, direction, input hash, warmup/measurement protocol, latency, allocation, encoded size를 고정한다. chart는 accepted Java 25 run의 cell만 비교한다. |
| Stability | 0 | 0 | 0 | 0 | Java 21 host incompatibility는 16개의 explicit `N/A` cell로 확장된다. Experimental AVIF/HEIC execution은 capability-gated이고 default stable task에서 제외된다. |
| Security | 0 | 0 | 0 | 0 | strict bounded JSON parsing, fixed evidence root, traversal/symlink rejection, artifact hash, no-replace atomic promotion guard가 accepted evidence를 보호한다. |
| Operator/Ops | 0 | 0 | 0 | 0 | preflight, immutable run ID, failure ledger, backend identity check, sequential native execution, reproducible command가 failure state 진단과 rerun을 돕는다. |
| Developer/API | 0 | 0 | 0 | 0 | production API와 artifact coordinate는 변경되지 않았다. native runtime adapter는 benchmark source set에 남고 production code에서는 Java 25 internal codec probe만 변경된다. |
| User/caller | 0 | 0 | 0 | 0 | English/Korean README table은 동일하고, `MEASURED`와 `N/A`를 구분하며, cross-runtime ranking을 피하고 report 및 immutable raw evidence에 link한다. |
| Integration | 0 | 0 | 0 | 0 | task graph, manifest finalizer, report, locale pair, chart asset, accepted evidence가 같은 32-cell matrix에 맞춰져 있다. |

## 발견 사항과 수정

| Priority | 발견 사항 | 수정 및 재실행 근거 |
|---|---|---|
| P1 | 문서화된 default `benchmarkBenchmark` command가 mandatory `codec.matrix.runId`를 빠뜨려, 실제 invocation은 stable fixture preparation 시작 시 실패할 수 있었다. | 두 locale command와 documented dry run에 fresh run ID를 추가했다. dry run은 이제 `codecMatrixPreflight`와 `prepareCodecMatrixFixtures`를 포함하고 성공한다. |
| P1 | benchmark build가 external catalog path를 통해 governed serialization alias를 임시로 사용했지만, repository는 released catalog tag만으로 독립 재현 가능해야 한다. | `libs.kotlinx.serialization.json`으로 전환하고 repo-local issue #208 version pin을 문서화했다. removal condition은 release-train central catalog tag가 alias를 publish한 뒤 pin을 삭제하는 것이다. central dependency repository는 수정하지 않았다. |
| P1 | Java 25 직후 Java 21 benchmark compile을 실행하면 unused benchmark-module atomicfu transformer가 Java 21 (`65.0`)에서 stale Java 25 class file (`69.0`)을 load하려다 실패했다. | contract assertion을 추가하고 unused benchmark-module JVM transform을 비활성화했다. repair 전 assertion은 실패했고, 이후 clean 없이 Java 25 다음 Java 21 `benchmarkBenchmarkCompile`이 sequentially 통과했다. |
| P2 | design은 모든 provenance field를 `run-manifest.json`에 귀속했지만, implementation은 이를 hash-linked preflight, fixture, JMH, size, capability, report artifact 전반에 의도적으로 분산한다. | top-level manifest schema를 과장하지 않고 accepted evidence ledger를 설명하도록 design을 수정했다. |

review 중 관측한 current central release-train tag는 `catalog/2026-07-08-00`였고 serialization JSON alias를 포함하지 않는다. 해당 tag로 resolve하면서 local version을 제거하자 empty-version dependency failure가 재현됐다. temporary pin을 복원하자 benchmark-module test 70개가 통과했다.

## Integration 및 Hazard 근거

- accepted run은 정확히 32개 terminal cell을 포함한다. Java 25 `MEASURED` cell 16개와 Java 21 `N/A` cell 16개다.
- manifest-linked artifact 11개는 모두 기록된 SHA-256 및 byte count와 일치하며, JSON file 13개가 parse되고 raw tree에는 symlink가 없다.
- accepted raw evidence는 append-only로 추가됐다. `origin/develop` 대비 accepted file 수정 또는 삭제는 없다.
- 두 codec chart SVG는 `xmllint`를 통과했다. CairoSVG `-s 2` PNG는 `3120x1880` 및 `3120x1720`이고 original size로 inspect했다. latency chart는 두 compared series에 complementary blue/orange pair를 사용하고, four-series output-size chart는 categorical palette를 유지한다.
- settings, BOM, module registration, CI, Nightly, Kover, public API change는 없다. Java 25 backend atomicfu setting은 그대로이며 benchmark module만 unused transformer를 비활성화한다.
- 유일한 catalog delta는 문서화된 temporary serialization version pin이다.

## 검증

| Command 또는 check | 결과 |
|---|---|
| `:bluetape4k-images-benchmark:test --rerun-tasks` on Java 25 | PASS, 70 tests |
| `:bluetape4k-images-benchmark:build` | PASS |
| Java 25 `benchmarkBenchmarkCompile` | PASS |
| Java 21 `benchmarkBenchmarkCompile` immediately after Java 25 | PASS |
| `:bluetape4k-images-benchmark:tasks --all` | PASS, codec task 11개 모두 등록 |
| documented Java 25 `benchmarkBenchmark --dry-run` with run ID | PASS; stable preflight와 fixture preparation 포함 |
| `./gradlew detekt` | PASS (`NO-SOURCE`) |
| Manifest/hash/byte-count/JSON/symlink/append-only audit | PASS |
| README codec-table parity | PASS, value와 link 동일 |
| `git diff --check` | PASS |

## 판정

`PASS` — final integrated count는 `P0=0`, `P1=0`, `P2=0`, `P3=0`이다.
Issue #208은 PR 및 CI validation으로 진행할 준비가 됐다.
