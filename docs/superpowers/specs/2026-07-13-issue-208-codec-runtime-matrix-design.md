# Issue #208 Codec/Runtime Matrix Benchmark 설계

- 날짜: 2026-07-13
- 이슈: [#208](https://github.com/bluetape4k/bluetape4k-image/issues/208)
- 마일스톤: `0.4.0`
- 작업 유형: Type A - Full Feature
- 범위: `bluetape4k-images-benchmark` harness와 benchmark evidence

## 1. 문제

현재 benchmark module은 Java 21 JVips/JNI와 Java 25 vips-ffm backend에 대해 JPEG 중심 encode evidence와
geometry comparison을 제공한다. 그러나 stable PNG/WebP path에 대한 reproducible codec matrix나 incubating
AVIF/HEIC path에 대한 explicit evidence는 제공하지 않는다.

누락된 evidence 때문에 다음 세 질문에 답하기 어렵다.

1. stable PNG/WebP codec pipeline은 일반적인 web-photo와 profile-image workload에서 어떻게 동작하는가?
2. 어떤 AVIF/HEIC와 runtime 조합이 측정되었고, 어떤 조합이 unsupported였으며, backend가 capability를 증명하지
   못해 skipped된 조합은 무엇인가?
3. local snapshot을 universal ranking처럼 제시하지 않으면서, 측정 host에서 관측한 latency, managed allocation,
   byte-size trade-off는 무엇인가?

## 2. 목표

- 기존 benchmark module에 default reproducible PNG/WebP codec matrix를 추가한다.
- lazy image opening 또는 header parsing을 full decode work로 오인하지 않도록 pixel evaluation을 강제하는
  codec boundary를 측정한다.
- AVIF/HEIC measurement는 opt-in으로 유지하고 default benchmark path에서는 제외한다.
- latency, managed allocation, input/output bytes, fixture dimension, backend, JVM, libvips version,
  capability status를 기록한다.
- Java 21 JNI와 Java 25 FFM measurement는 sequential하게 유지하고, workload semantics와 fixture bytes가
  equivalent할 때만 비교한다.

## 3. 비목표

- published image 또는 Vips API를 변경하지 않는다.
- 새 backend, codec dependency, benchmark module을 추가하지 않는다.
- browser delivery, network transfer, CDN behavior, visual quality, SSIM, PSNR, perceptual quality를
  benchmark하지 않는다.
- local result에서 cross-host 또는 production-wide ranking을 주장하지 않는다.
- AVIF/HEIC를 CI 또는 default benchmark smoke path에 강제하지 않는다.
- historical `vips_encodeJpeg` result를 대체하지 않는다.

## 4. 현재 증거

### 4.1 Repository anchor

- `VipsBackendEncodeBenchmark`는 현재 `vips_encodeJpeg`만 노출한다.
- `VipsBenchmarkState`는 `-Pvips.impl=java21|java25`로 backend를 선택하고 runtime initialization을 소유하며,
  reflection으로 binding-neutral `VipsImage` 값을 만든다.
- `VipsRuntime.codecCapabilityReport()`는 PNG/WebP를 stable로 보고하고, AVIF/HEIC는 backend-specific
  `AVAILABLE`, `UNAVAILABLE`, `UNKNOWN` state로 보고한다.
- Java 21 JVips는 native HEIF operation을 inspect할 수 없고 HEIC를 encode할 수 없다.
- Java 25 FFM은 libvips를 통해 `heifload_buffer`와 `heifsave_buffer`를 probe한다.
- benchmark plugin은 `kotlinx-benchmark` 0.4.17이다. named configuration은 `include(pattern)`과
  `exclude(pattern)`을 모두 지원하므로 experimental JMH class를 default `main` configuration에서 제외할 수 있다.
- 기존 allocation report는 generated JMH jar와 `-prof gc`를 사용하고 `gc.alloc.rate.norm`을 operation당
  managed bytes로 읽는다.

### 4.2 Baseline environment

- worktree base: `feb75001a35fceb53f976a982e7d44a1eb28e204`
- benchmark compilation:
  `./gradlew :bluetape4k-images-benchmark:benchmarkBenchmarkCompile --console=plain`
  이 isolated worktree에서 통과한다.
- 사용 가능한 local JDK: Java 21.0.11, Java 25.0.3.
- local native stack: libvips 8.18.4, WebP 1.6.0, libavif 1.4.2, libheif 1.23.1, aom 3.14.1.
- prior repository evidence는 bundled JVips dylib가 x86_64라서 이 macOS arm64 host에서 Java 21 JNI
  measurement를 만들 수 없다고 기록한다. 이는 environment limitation이지 synthetic benchmark row가 아니다.

### 4.3 Upstream basis

- JMH는 kotlinx-benchmark가 사용하는 JVM microbenchmark execution surface다.
  <https://github.com/openjdk/jmh>
- libvips는 buffer-based WebP와 HEIF load/save operation을 노출한다.
  <https://libvips.github.io/pyvips/vimage.html>

## 5. 검토한 접근법

### 5.1 권장: binding-neutral transcode matrix

public `VipsImage` boundary를 사용하고 common output codec으로 evaluation을 강제한다.

- JPEG input -> PNG output (`encodePngFromJpeg`)
- JPEG input -> WebP output (`encodeWebpFromJpeg`)
- PNG input -> JPEG output (`decodePngToJpeg`)
- WebP input -> JPEG output (`decodeWebpToJpeg`)

이는 순수 codec CPU time을 주장하는 것이 아니라 의도적인 transcode pipeline이다. input side는 decode codec을
식별하고 output side는 libvips가 pixel을 evaluate하도록 강제한다. 같은 binding-neutral operation이 두 backend
모두에서 실행된다.

### 5.2 거부: header/open timing을 decode timing으로 간주

`vipsImageOf(bytes)`만 측정하거나 dimension만 읽으면 모든 pixel을 evaluate하지 않고 lazy open과 header
parsing을 측정할 수 있다. 그 결과를 decode latency로 표시하면 오해를 만든다.

### 5.3 거부: backend-specific raw-pixel hook

JNI/FFM-specific benchmark adapter를 추가하면 lower-level decode boundary를 분리할 수는 있지만, harness가
backend internal에 결합되고 cross-backend semantics를 equivalent하게 유지하기 어려워진다. Issue #208은 새
production SPI나 native adapter API를 정당화하지 않는다.

### 5.4 거부: 모든 codec을 default task에 포함

default task에 AVIF/HEIC method를 넣으면 normal benchmark path가 optional native codec에 의존한다.
unsupported host는 실패하거나 더 나쁘게 no-op measurement를 낼 수 있다. experimental codec은 별도 opt-in lane으로
유지한다.

## 6. Fixture 설계

JMH process가 시작되기 전에 canonical preparation step에서 repository-managed source image 두 개로 realistic
workload shape를 한 번 생성한다.

| Scenario | Source | Derived raster | 목적 |
|---|---|---:|---|
| `web-photo` | `cafe.jpg` (4032x3024) | center-cropped/resized 1920x1080 | 일반적인 대용량 web content |
| `profile` | `homer.jpg` (1248x702) | center-cropped/resized 512x512 | 일반적인 profile/avatar content |

규칙:

- checked-in source는 `benchmark/images-benchmark/src/main/resources/bench/cafe.jpg`와
  `images/src/test/resources/images/homer.jpg`에서 resolve한다. 해당 SHA-256 값을 result report에 기록한다.
- cacheable `syncCodecMatrixSourceFixtures` Gradle `Sync` task는 이 file들을 input으로 선언하고
  `build/generated/codec-matrix-source-fixtures/`로 copy한다. 모든 harness code는 이 generated directory만
  소비한다. process working directory에서 다른 module의 test tree를 resolve하지 않는다.
- transformation recipe는 deterministic하며 measured loop 밖에서 실행된다. 두 target dimension을 모두 덮을 때까지
  uniform scale한 뒤 integer pixel coordinate로 centered target rectangle을 가져온다. source를 stretch하거나 random
  crop을 선택하지 않는다.
- derived raster는 stable matrix에 필요한 JPEG, PNG, WebP input으로 한 번 encode한다.
- canonical preparation command는 derived raster와 JPEG/PNG/WebP input을
  `build/codec-matrix/<run-id>/fixtures/` 아래에 한 번 쓴다. 또한 logical fixture ID, source/derived
  SHA-256 value, dimension, magic-byte result, byte count, transform recipe, codec option을 포함하는
  manifest를 쓴다. backend benchmark JVM은 이 manifest만 읽고 hash, dimension, magic byte가 다르면 실패한다.
- command는 `prepareCodecMatrixFixtures` Gradle task다. validated
  `-Pcodec.matrix.runId=<run-id>`를 받고, 다른 content가 있는 existing run directory overwrite를 거부한다.
  property가 없으면 local smoke run은 generated non-publishable run ID를 사용한다. accepted evidence는
  preparation, backend, profiler, finalization command 전반에서 항상 하나의 explicit run ID를 사용한다.
- JMH trial setup은 manifest를 검증하고 bytes를 load한다. canonical input을 재생성하거나 re-encode하지 않는다.
- stable run은 Java 21과 Java 25에 대해 identical manifest-pinned input bytes를 사용한다.
- missing source fixture는 setup failure다. codec matrix는 synthetic image로 fallback하면 안 된다. 그렇게 하면
  workload가 조용히 바뀌기 때문이다.
- matrix loader는 위 두 fixed repository resource만 받는다. caller path를 받거나 symlink를 따라가거나
  `BenchmarkImageSets`의 synthetic fallback을 재사용하지 않는다.
- result는 scenario별로 분리한다. 서로 다른 dimension의 value를 하나의 ranking으로 평균내지 않는다.
- report는 source identity, derived dimension, encoded input bytes, measured output bytes를 기록한다.

## 7. Benchmark 아키텍처

### 7.1 Stable matrix

`src/benchmark` 아래에 focused `VipsCodecMatrixBenchmark`와 fail-fast `VipsCodecMatrixState`를 추가한다.
state는 현재 binding-neutral runtime selection을 재사용하고 prepared fixture manifest를 읽는다. historical,
skip-capable `VipsBenchmarkState`나 JPEG benchmark behavior는 변경하지 않는다.

deterministic fixture preparation, run-manifest serialization, capability status DTO/mapping,
diagnostic sanitization은 `src/main` 아래 vips-free `internal` component에 둔다. 이 component는 `VipsRuntime`이나
`VipsImage`를 reference하면 안 된다. 따라서 `images-vips-api`는 `benchmarkImplementation`으로 남고 production
dependency change는 없다. `src/benchmark` 아래의 `CodecMatrixRuntimeAdapter`는 선택된 Vips runtime/image
operation을 internal DTO/factory 경계로 mapping한다. unit test는 vips-free fake를 주입한다. JMH annotation,
runtime adapter, measured call만 `src/benchmark` 아래에 둔다. Gradle source-set graph가 behavioral 경계를
제공하지 않는 annotation/configuration test는 focused source contract assertion을 사용할 수 있다.

모든 measured row는 하나의 explicit option profile을 사용한다.

```text
quality=85, effort=4, lossless=false, stripMetadata=true
```

PNG는 `quality`를 무시하고 `effort`를 compression level로 mapping한다. WebP는 common lossy web profile이며
PNG와 lossless-quality peer가 아니다. 따라서 report는 equivalent visual quality나 compression efficiency를
주장하지 않고 latency와 byte-size trade-off를 제시해야 한다. JPEG input과 forcing-output bytes도 metadata를
stripping한 quality 85를 사용한다.

stable class는 각 scenario에 대해 네 method family를 노출한다.

```text
encodePngFromJpeg
encodeWebpFromJpeg
decodePngToJpeg
decodeWebpToJpeg
```

모든 invocation은 자체 `VipsImage`를 만들고 닫는다. output bytes는 `Blackhole`이 소비한다. runtime
initialization failure는 benchmark failure다. class는 `bh.consume(null)`을 사용하거나 no-op timing row를
publish하면 안 된다.

named `codecMatrix` benchmark configuration을 추가한다. expected Gradle task는 다음과 같다.

```text
:bluetape4k-images-benchmark:benchmarkCodecMatrixBenchmark
```

`benchmarkCodecMatrixBenchmark`와 default `benchmarkBenchmark` execution task는 모두
`prepareCodecMatrixFixtures`에 의존하고, 선택된 run manifest path를 JVM system property로 받는다. compile,
generate, jar, `build`, `check`, `test` task는 fixture preparation이나 native capability probe를 실행하지 않는다.

main runtime classpath의 `CodecMatrixPreflightMain`을 사용하는 non-native `codecMatrixPreflight` JavaExec task를
추가한다. 기존 `prepareCodecMatrixFixtures` JavaExec task는 main runtime classpath의
`CodecMatrixFixtureMain`을 사용하고 preflight와 `syncCodecMatrixSourceFixtures`에 의존한다. stable benchmark는
preparation에 의존하며 shared run ID와 fixture manifest를 소비한다. missing/mismatched preflight, selector,
host compatibility, manifest evidence는 native initialization 전에 실패한다.

configuration은 one fork, one benchmark thread, libvips concurrency 4, one one-second warmup iteration,
three one-second measurement iteration을 `AverageTime` mode와 `ms` output으로 사용한다. focused GC-profiler
addendum은 같은 thread count, runtime concurrency, warmup, measurement, fork, fixture, codec option profile을
사용한다.

기존 JPEG benchmark는 historical evidence로 변경하지 않고, focused task 안에 duplicate하지 않으며 new matrix 옆에서
reference한다.

### 7.2 Experimental matrix

AVIF/HEIC용 별도 `VipsExperimentalCodecMatrixBenchmark`를 추가한다. 이 class는 plugin default `main`
configuration에서 제외하고 explicit `codecMatrixAvif`, `codecMatrixHeic` configuration에만 포함한다.
expected Gradle task는 다음과 같다.

```text
:bluetape4k-images-benchmark:benchmarkCodecMatrixAvifBenchmark
:bluetape4k-images-benchmark:benchmarkCodecMatrixHeicBenchmark
```

benchmark runtime classpath의 `CodecMatrixCapabilityMain`을 사용하는 `codecMatrixCapabilityReport` JavaExec task를
추가한다. 이 task는 `codecMatrixPreflight`와 `prepareCodecMatrixFixtures`에 의존한 뒤 선택된
`-Pvips.impl` runtime만 초기화하고 `build/reports/benchmarks/codec-matrix/` 아래에 structured JSON snapshot을
쓴다. 각 entry는 backend, scenario, format, direction, capability, eligibility status, sanitized reason, JVM,
architecture, observed libvips version을 기록한다. `UNAVAILABLE`과 `UNKNOWN`은 이 task의 successful observation이다.
runtime initialization, fixture corruption, malformed output은 task를 실패시킨다.

`vips.impl`은 exact allowlist다. `java21`과 `java25`만 허용한다. input이 없으면 기존 `java25` default를
유지하지만, 다른 값은 Gradle configuration time에 실패한다. evidence는 requested selector와 initialized runtime이
보고한 identity를 모두 기록한다. mismatch는 preflight를 실패시킨다.

shared non-native preflight는 requested backend, actual JDK vendor/version, OS/kernel/architecture,
CPU model, 해당되는 경우 JNI binary architecture, FFM native-access flag, sanitized loader-path availability,
available disk space, git SHA/dirty state, generated run ID를 기록한다. 알려진 JDK/architecture/native-binary
incompatibility는 runtime initialization을 시도하지 않고 structured `N/A` observation이 된다. unexpected
initialization 또는 probe failure는 `ERROR`이며 task를 실패시킨다.

experimental class는 각 scenario에 대해 네 exact method family를 가진다.

```text
encodeAvifFromJpeg
decodeAvifToJpeg
encodeHeicFromJpeg
decodeHeicToJpeg
```

AVIF와 HEIC configuration은 각각 matching two method만 포함하고 stable option/timing profile을 재사용한다.
canonical preparation step은 eligible backend로 manifest-pinned JPEG raster를 target format으로 encode하고,
target magic bytes/dimensions/positive size를 검증하며, 정확한 AVIF/HEIC input과 SHA-256을 fixture manifest에
저장한다. decode row는 pinned target-format bytes를 소비하고 encode row는 pinned JPEG bytes를 소비한다.
target-format manifest는 producer backend, JDK, libvips/codec library version, preparation command,
producer run ID를 기록한다. 서로 다른 host, producer manifest, input hash에서 나온 experimental row는 비교하지 않는다.

experimental run 전에는 다음을 수행한다.

1. 선택된 backend를 initialize한다.
2. `codecCapabilityReport()`를 읽는다.
3. 각 direction에 필요한 capability를 독립적으로 평가한다. encode row는 encode `AVAILABLE`을 요구한다.
   decode row는 decode `AVAILABLE`과 pinned target-format input을 요구한다.
4. exact timed boundary와 option profile로 harness-local directional smoke를 실행한다. encode는 pinned JPEG ->
   target format을 사용하고 target magic/dimensions를 검증한다. decode는 pinned target format -> JPEG를 사용하고
   JPEG magic/dimensions를 검증한다. public round-trip `smokeTestCodec`은 양 direction이 모두 available일 때만
   supplemental evidence로 기록할 수 있으며 single-direction row의 gate가 아니다.
5. capability가 `UNAVAILABLE`이면 `UNSUPPORTED`와 sanitized report reason을 기록한다.
6. capability가 `UNKNOWN`이면 `SKIPPED`와 backend limitation을 기록한다. installed package name에서 support를
   추론하지 않는다.
7. capability는 available이지만 fixture preparation 또는 smoke가 실패하면 failed stage와 함께 `FAILED_SMOKE`를
   기록하고 experimental task를 실패시키며 accepted evidence를 block한다. observed failure를 `SKIPPED`로 낮추지 않는다.

experimental benchmark task는 JMH skip row를 fabricate하지 않는다. 각 task는 `codecMatrixCapabilityReport`에
Gradle dependency를 가지며, 해당 format/direction eligibility와 fixture manifest를 소비한다. ineligible 또는
missing preflight 상태의 direct invocation은 즉시 실패하고 실행할 정확한 capability command를 출력한다. partial 또는
no-op JMH row를 내지 않는다. measurement가 실행되는 동안 capability output은 `build/reports` 아래 ephemeral
eligibility manifest로 남는다. eligible row가 끝나면 finalization step이 eligibility와 numeric
latency/allocation/size artifact 또는 terminal unmeasured status를 결합하고 hash를 검증한 뒤 finalized snapshot을
tracked raw-evidence directory로 atomically promote한다. 어떤 pre-benchmark file도 `MEASURED` 또는 accepted final
status를 주장하면 안 된다.

`codecMatrixCapabilityReport` task는 `prepareCodecMatrixFixtures`에 의존한다. 두 experimental benchmark task는
해당 capability task와 benchmark runtime classpath의 `CodecMatrixExperimentalFixtureMain`을 사용하는 JavaExec task
`prepareExperimentalCodecMatrixFixtures`에 의존한다. 이 task는 eligible encode format에 대해서만
manifest-pinned target input을 생성한다. decode-only row는 명시적으로 제공된 compatible producer manifest를
소비해야 하며, 없으면 timing 전에 실패한다. 모든 task는 같은 explicit run ID를 받는다. non-native
`finalizeCodecMatrixEvidence` JavaExec task는 main runtime classpath의 `CodecMatrixFinalizeMain`을 사용하며,
staged run을 `benchmark/images-benchmark/docs/raw/<run-id>/`로 promote할 수 있는 유일한 task다. atomic
directory move 전에 run manifest, cell coverage, artifact hash, terminal status, blocking state 부재를
검증한다. existing accepted run에 대해 preparation 또는 finalization을 다시 호출해도 tracked evidence를 overwrite하지 않는다.

Gradle task contract는 exact하다.

| Task | Type / entrypoint | Declared inputs | Output / dependency |
|---|---|---|---|
| `syncCodecMatrixSourceFixtures` | `Sync` | the two checked-in source fixtures | `build/generated/codec-matrix-source-fixtures/` |
| `codecMatrixPreflight` | `JavaExec` / `CodecMatrixPreflightMain`, main runtime | selector, explicit run ID, git/host/JDK facts | `build/codec-matrix/<run-id>/preflight-<backend>.json` |
| `prepareCodecMatrixFixtures` | `JavaExec` / `CodecMatrixFixtureMain`, main runtime | synced sources, preflight, transform/options | stable fixtures plus `fixtures/manifest.json` |
| `codecMatrixCapabilityReport` | `JavaExec` / `CodecMatrixCapabilityMain`, benchmark runtime | backend-specific preflight, stable manifest, selected backend | ephemeral `eligibility-<backend>.json` and `sizes-<backend>.json` |
| `prepareExperimentalCodecMatrixFixtures` | `JavaExec` / `CodecMatrixExperimentalFixtureMain`, benchmark runtime | eligibility, stable manifest, producer manifest when supplied | AVIF/HEIC inputs plus updated fixture manifest |
| focused benchmark tasks | generated JMH tasks | preflight, exact fixture/eligibility manifests, run ID | staged latency JSON; direct calls enforce dependencies |
| `finalizeCodecMatrixEvidence` | `JavaExec` / `CodecMatrixFinalizeMain`, main runtime | eligibility, staged latency/GC/size/status artifacts and hashes | atomic tracked `docs/raw/<run-id>/` |

stable `benchmarkBenchmark`와 `benchmarkCodecMatrixBenchmark` task만 non-experimental preparation path에 참여한다.
`build`, `check`, `test`, compile/generate/jar task, default benchmark graph는 capability 또는
experimental-fixture task에 의존하지 않는다. AVIF/HEIC 작업은 explicit focused task name에서만 시작한다.

status semantics는 matrix cell별이며 backend, JVM, architecture, host environment, libvips build, direction,
scenario, input hash scope를 가진다.

- `MEASURED`: numeric latency/allocation/size evidence exists.
- `UNSUPPORTED`: the required operation is explicitly `UNAVAILABLE`.
- `SKIPPED`: capability is `UNKNOWN` or an explicit policy hold prevented a run.
- `N/A`: preflight proves the requested runtime cannot execute on this host.
- `FAILED_SMOKE`: capability said available but preparation/smoke failed; blocks acceptance.
- `ERROR`: unexpected setup/runtime/evidence failure; blocks acceptance.

따라서 이 host에서 Java 21 JVips는 fabricated AVIF/HEIC row를 받지 않는다. Java 25 FFM row는 capability와
smoke gate가 통과한 뒤에만 측정된다.

### 7.3 Measurement와 raw evidence

`ms/op` 단위의 `AverageTime`을 사용하며 낮을수록 좋다. measured backend마다 두 raw evidence file을 만든다.

- latency용 normal kotlinx-benchmark/JMH JSON.
- `gc.alloc.rate.norm` (`B/op`)용 focused JMH `-prof gc` JSON.

managed allocation은 native libvips memory를 나타내지 않는다. report는 allocation table 옆에 이 limitation을 명시한다.

output size는 benchmark가 사용하는 것과 정확히 같은 fixture, codec, option으로 timed loop 밖에서 한 번 수집한다.
decode-to-JPEG row가 encode-from-JPEG row와 혼동되지 않도록 report는 input/output byte count를 모두 포함한다.

각 backend/configuration/profiler command는 fresh JVM에서 실행한다. timed, preparation, smoke, output-size path는
success/failure 모두에서 모든 `VipsImage`를 닫는다. 어떤 lane도 trial 사이에 irreversible
`VipsRuntime.shutdown()`을 호출하지 않는다. failed/interrupted attempt는 sanitized log를 유지하고 partial
measurement를 버리며, failure를 진단한 뒤에만 새 process에서 complete affected lane을 다시 실행한다. failed attempt는
`ERROR` 또는 `FAILED_SMOKE`, diagnosis, mitigation, run ID, replacement attempt link를 유지한다. unexplained retry는
accepted되지 않는다.

## 8. Failure Handling

1. **Missing fixture:** fail setup with the attempted path; never synthesize a
   replacement.
2. **Runtime initialization failure:** fail the selected stable task; do not
   emit a no-op row.
3. **Experimental codec unavailable:** record `UNSUPPORTED` with the sanitized
   capability reason.
4. **Experimental capability unknown:** record `SKIPPED`; do not run by
   assumption.
5. **Capability available but smoke fails:** record blocking `FAILED_SMOKE`
   with decode or encode stage and keep the long benchmark unexecuted.
6. **Java 21 host incompatibility:** record the JVM, architecture, and native
   binding limitation as `N/A`; compilation alone is not a measurement.
7. **Backend runs overlap:** invalidate the evidence and rerun Java 21 and Java
   25 sequentially.
8. **Retry-only success:** investigate native lifecycle or timing behavior
   before accepting the rerun.

## 9. Documentation and Result Artifacts

Add an English detailed report under:

```text
benchmark/images-benchmark/docs/codec-runtime-matrix-2026-07-13.md
```

Store accepted raw evidence under
`benchmark/images-benchmark/docs/raw/<run-id>/`. The directory is append-only:
an interrupted/retried run gets a new ID, accepted evidence is never
overwritten, and replacement runs declare `supersedes` links. The accepted
evidence ledger consists of the run manifest and its hash-linked preflight,
fixture, JMH, size, and capability artifacts. Together with the reproduction
commands in the report, the ledger records git SHA/dirty state, Gradle/JMH
settings, sanitized OS/kernel/architecture and CPU identity, JDK and native
library versions/probes, fixture/input hashes, actual backend identity,
artifact SHA-256 values, terminal cell status, and any superseded run. It omits
hostname, user name, absolute home/worktree/temp paths,
environment values, and secrets. Capability, latency, allocation, and size
artifacts link back to this manifest. The report contains:

- exact commands and metric direction;
- fixture source and derived dimensions;
- runtime and native dependency versions;
- measured, unsupported, skipped, and N/A combinations;
- latency, managed allocation, input bytes, and output bytes;
- interpretation limits and non-comparable rows.

The report and both README locales use the same status legend. Every matrix cell
contains either measured values or one scoped status, sanitized reason, and
rerun guidance. Sanitization maps failures to fixed reason codes/allowlisted
messages, strips control/Markdown metacharacters and absolute paths, and bounds
message length. A pre-commit scan rejects raw exception text, local path
prefixes, or secret-like values in raw JSON, reports, README files, and command
examples.

Update both benchmark README locales with a concise table and a link to the
detailed report. When at least two comparable rows are measured for the same
scenario and host, generate matching latency/output-size SVG and PNG assets
through `bluetape-diagram`, embed the PNG, and validate both formats. If fewer
than two comparable rows exist, keep a table and record the evidence-backed
chart N/A. Do not create a chart that combines non-comparable scenarios or
hosts.

## 10. Test and Validation Strategy

### 10.1 Contract tests

- Verify the stable matrix contains the four named transcode boundaries.
- Verify the default benchmark configuration excludes the experimental class.
- Verify the focused configuration names and one-warmup/three-measurement
  timing profile.
- Verify the codec matrix contains no unavailable-runtime no-op branch.
- Verify `web-photo` derives 1920x1080 and `profile` derives 512x512.
- Verify the derived rasters use deterministic cover-and-center-crop semantics.
- Verify missing fixtures fail instead of using a synthetic fallback.
- Verify prepared PNG/WebP/JPEG inputs have valid magic bytes and positive
  sizes.
- Verify eligibility and finalized cell states map to `MEASURED`,
  `UNSUPPORTED`, `SKIPPED`, or `N/A` without raw native exception leakage.
- Verify `FAILED_SMOKE` and `ERROR` block acceptance and cannot become
  `SKIPPED`.
- Verify exact `vips.impl` validation, requested/actual backend equality, known
  host `N/A`, and unexpected initialization `ERROR` with injected fakes.
- Verify canonical fixture and run manifests, hashes, append-only run IDs,
  supersession links, and atomic promotion into tracked raw evidence.
- Verify stable fixture preparation and benchmark tasks depend on the shared
  preflight/run ID and fail before native initialization when it is missing,
  mismatched, or incompatible.
- Verify eligibility manifests cannot contain `MEASURED`, and finalization
  requires either complete numeric artifacts or one terminal unmeasured status
  for every cell.
- Verify experimental row direction gates, exact-boundary smoke bytes,
  direct-task fail-fast behavior, and close tracking on success and exception
  paths.
- Verify `build`, `check`, `test`, the default `benchmark` task, and CI task
  graphs do not depend on capability or AVIF/HEIC tasks; experimental work runs
  only through explicit focused task names.
- Verify the capability task produces the required JSON fields, treats
  unsupported/unknown as observations, and fails malformed evidence.

### 10.2 Compile and task validation

```text
./gradlew :bluetape4k-images-benchmark:test --console=plain
./gradlew :bluetape4k-images-benchmark:benchmarkBenchmarkCompile -Pvips.impl=java25 --console=plain
./gradlew :bluetape4k-images-benchmark:benchmarkBenchmarkCompile -Pvips.impl=java21 --console=plain
./gradlew :bluetape4k-images-benchmark:tasks --all --console=plain
./gradlew :bluetape4k-images-benchmark:codecMatrixPreflight -Pvips.impl=java25 --console=plain
./gradlew :bluetape4k-images-benchmark:prepareCodecMatrixFixtures -Pvips.impl=java25 --console=plain
./gradlew :bluetape4k-images-benchmark:codecMatrixCapabilityReport -Pvips.impl=java25 --console=plain
./gradlew :bluetape4k-images-benchmark:prepareExperimentalCodecMatrixFixtures -Pvips.impl=java25 --console=plain
```

Dry-run the default and focused tasks to prove experimental isolation before
any native measurement.

### 10.3 Native benchmark validation

- Run the shared non-native preflight and fixture preparation, then the Java 25
  stable codec matrix with the same run ID.
- Run Java 25 capability/smoke checks, followed only by supported experimental
  tasks.
- Build the focused JMH jar and run the matching rows with `-prof gc`.
- Attempt Java 21 native measurement only on a compatible host. On this macOS
  arm64 host, preserve `N/A` rather than inventing a row.
- Run all native/JNI/FFM commands sequentially.
- Compare Java 21 and Java 25 only when commit, dirty state, OS/kernel/CPU/arch,
  libvips and codec-library versions, fixture and producer-manifest hashes,
  option profile, benchmark threads, runtime concurrency, and JMH protocol
  match. Otherwise publish separate non-comparable rows.

### 10.4 Documentation validation

- Parse every raw JSON file with `jq`.
- Cross-check every manifest/artifact hash and reject leakage patterns.
- Verify README English/Korean parity and report links.
- Validate any SVG with `xmllint` and PNG with `identify`.
- Run `git diff --check`.

## 11. Compatibility and Repository Hazards

- No production API or artifact coordinate changes are expected.
- No module registration or BOM change is expected because the harness remains
  in `bluetape4k-images-benchmark`.
- Keep `atomicfu transformJvm = false` in the Java 25 backend unchanged. Also
  disable the unused atomicfu JVM transform in the benchmark module so a Java
  21 verification can follow Java 25 output in the same worktree without the
  Java 21 transformer loading Java 25 class files.
- Keep Java and Kotlin toolchains selected by `-Pvips.impl`.
- Keep `--enable-native-access=ALL-UNNAMED` for the FFM benchmark fork.
- The benchmark source set remains excluded from production coverage.
- README locale parity, raw evidence paths, chart assets, and benchmark task
  names are required hazard checks.
- No API/BOM/artifact-coordinate changes are expected. Until a release-train
  `bluetape4k-dependencies` catalog tag contains `kotlinx-serialization-json`,
  the repo-local catalog carries a temporary issue #208 version pin. Remove the
  pin when the central tagged alias is consumable. The issue #208 operator
  captures the native run; the implementation reviewer validates manifests and
  report interpretation; PR/merge approval remains with `debop`. Report,
  README/chart, and raw evidence roll back together as one change unit.

## 12. Acceptance Criteria Traceability

| Issue criterion | Design proof |
|---|---|
| Distinguish measured, skipped, and unsupported combinations | Experimental capability/smoke gate and explicit status table |
| Include latency, allocation, output bytes, dimensions, backend, JVM, and libvips | Raw latency/GC JSON plus result metadata and byte-size capture |
| Keep experimental codecs from making default paths flaky | Default `main` exclusion plus opt-in experimental configurations |
| Link README to the codec matrix report | English/Korean README updates and detailed report path |
| Compare Java 21 and Java 25 only when semantics match | Binding-neutral transcode boundaries, identical fixture bytes, sequential runs |

## 13. Definition of Done

- The approved stable and experimental task boundaries compile under the
  selected Java 21 and Java 25 toolchains.
- PNG/WebP stable rows run for both approved fixture scenarios on Java 25.
- Experimental rows run only after direction-specific capability and smoke
  gates; every omitted combination has an evidence-backed status and reason,
  and no accepted run contains `FAILED_SMOKE` or `ERROR`.
- Latency, managed allocation, input/output size, dimensions, environment, and
  limitations are committed in raw and human-readable evidence.
- Default benchmark execution excludes experimental codecs.
- Targeted tests, benchmark compile, applicable native runs, JSON validation,
  documentation parity, asset validation when triggered, and
  `git diff --check` pass.
- Spec review and later implementation review converge at P0=0 and P1=0.
- The PR remains unmerged until explicit user approval.
