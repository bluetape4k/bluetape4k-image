# Issue #550 ONNX Runtime Java native·platform·BOM·CI 검증 연구

- Epic: [#513 AI/ML backend 연구 train](https://github.com/bluetape4k/bluetape4k-image/issues/513)
- 하위 epic: [#3 이미지 분류 ONNX backend 도입 검증 train](https://github.com/bluetape4k/bluetape4k-image/issues/3)
- 선행 문서: [#543 공통 공급망 정책](../research/2026-08-19-issue-543-ai-ml-supply-chain-policy.md), [#548 model manifest·provenance](2026-08-25-issue-548-model-manifest-provenance.md), [#549 provider-neutral API 경계](2026-08-25-issue-549-classification-api-boundary.md)
- 대상 issue: [#550 ONNX Runtime Java native/platform·BOM·CI 검증](https://github.com/bluetape4k/bluetape4k-image/issues/550)
- 조사일: 2026-08-25
- 변경 유형: Type-E research/design
- 상태: `RESEARCH_ONLY / NATIVE_VALIDATION_PENDING / #551 ADOPTION_GATE_BLOCKED`

## 결정 요약

이번 issue는 ONNX Runtime dependency나 native backend를 추가하는 issue가 아니다. Java
binding의 native loader, execution provider(EP), 지원 플랫폼, Maven/BOM metadata, CI
matrix, 보안·성능 acceptance 기준을 고정해서 후속 Type-A가 재현 가능한 검증을 수행할
수 있게 하는 문서 issue다.

| 질문 | 결론 | 이번 train의 상태 |
|---|---|---|
| 기본 runtime | CPU `onnxruntime`을 기본 후보로 둔다. GPU는 `onnxruntime_gpu`와 CUDA/cuDNN을 별도 opt-in으로 둔다. | 설계만 확정 |
| 플랫폼 | Linux x64를 required CPU baseline으로 둔다. macOS ARM64와 Windows x64는 소비자 smoke matrix로 명시하되 현재 artifact 지원을 가정하지 않는다. | 실제 smoke 미실행 |
| Java 25 | 공식 Java binding의 일반 실행 보장(Java 8+)과 이 저장소의 Java 25 consumer 호환성은 별도 문제다. Java 25 compile/runtime smoke를 채택 gate로 둔다. | PENDING |
| dependency/BOM | 정확한 ORT version, license, artifact byte size, SHA-256, provenance, catalog alias, BOM constraint를 한 receipt에 묶는다. API module로 ORT를 누출하지 않는다. | dependency 추가 없음 |
| CI | PR에는 checked-in tiny/no-network fixture와 metadata 검증, scheduled/nightly에는 Linux native CPU, 별도 consumer smoke에는 macOS ARM64/Windows x64, GPU는 manual/nightly를 사용한다. | CI 변경 없음 |
| security | remote auto-download와 untrusted custom op를 금지한다. external data는 처음에는 거부하거나 managed root containment를 통과한 경우만 허용한다. | 정책·fixture 설계만 |
| benchmark | cold/warm latency, p50/p95/p99, RSS/native memory, thread/session 수, concurrency와 session reuse를 고정한다. | 수치 없음 |

`#551`이 `ADOPT`를 기록하기 전에는 ORT dependency, public classifier API, native
library loading, model binary, CI native job을 추가하지 않는다. 이번 문서에서
`PASS`는 설계와 근거가 충분하다는 뜻이며 실제 native runtime이 동작한다는 뜻이
아니다.

## 저장소 source ledger

| 저장소 근거 | 현재 관찰 | #550 영향 |
|---|---|---|
| `settings.gradle.kts` | image repository에는 scrimage/Java2D, barcode, OCR, Spring/Ktor, vips 계열이 있고 classification module은 없다. | 새 module·project registration은 #551 이후 Type-A로 분리한다. |
| `images/build.gradle.kts` | Jackson 3 BOM은 implementation dependency로만 사용한다. | ORT 연구가 public JSON/serialization contract를 만들지 않으며, 후속 manifest codec도 Jackson 3 implementation-only를 유지한다. |
| `buildSrc/PublicationInventory.kt`, `bom/build.gradle.kts` | published project inventory와 consumer BOM constraint 수집이 분리돼 있다. | catalog alias, published artifact, BOM constraint, generated POM을 함께 검증해야 한다. |
| `benchmark/` 및 Java 25 module | benchmark와 vips Java 25 module은 toolchain·native flags를 명시한다. | ORT Java 25 smoke는 별도 consumer fixture로 두고, vips 설정을 그대로 복사하지 않는다. |
| `images-ocr` | host Tesseract/native 검증은 property gate로 분리하고 container/native check를 순차 실행한다. | ORT native check도 PR deterministic fixture와 scheduled native fixture를 분리하고 순차 실행한다. |
| `docs/superpowers/research/2026-08-25-issue-548-model-manifest-provenance.md` | model bytes, labels, preprocessing, postprocessing, license/provenance가 함께 있어야 identity가 안정된다. | ORT version만 고정해서는 충분하지 않으며 manifest digest와 fixture digest를 같은 receipt에 둔다. |
| `docs/superpowers/research/2026-08-25-issue-549-classification-api-boundary.md` | provider-neutral API와 ORT provider artifact를 분리하고 ORT/JNI/native type을 public surface에서 배제한다. | #550은 provider 내부의 native/BOM/CI 조건만 다루고 API 경계를 다시 넓히지 않는다. |
| `AGENTS.md` image rules | native/JNI/FFM/OCR/Testcontainers check는 순차 실행하고, BOM consumer setup은 dependencies version만 사용한다. | 후속 train의 실행 순서와 versionless consumer smoke acceptance에 반영한다. |

현재 develop에는 ORT dependency, classification module, model fixture, ORT-specific
CI job이 없다. 따라서 이 문서는 현재 구현을 설명하는 문서가 아니라 후속 변경의
범위와 검증 증거를 고정하는 문서다.

## 공식 source와 확인한 사실

조사일은 2026-08-25다. 아래 사실은 공식 문서와 Maven Central metadata를 읽어
확인했으며, 버전·플랫폼·native 동작은 후속 exact version receipt에서 다시 확인해야
한다.

| 공식 근거 | 확인한 사실 | 설계 제한 |
|---|---|---|
| [ONNX Runtime Java guide](https://onnxruntime.ai/docs/get-started/with-java.html) | Java binding은 Maven Central artifact로 배포되고 `OrtEnvironment`에서 model path 또는 bytes로 `OrtSession`을 만든다. CPU `com.microsoft.onnxruntime:onnxruntime`와 GPU `com.microsoft.onnxruntime:onnxruntime_gpu`가 분리돼 있다. 문서의 CPU package 표에는 Linux x64, Windows x64, macOS x64가 보이며 macOS ARM64는 확인되지 않는다. | CPU artifact를 기본 후보로 두고 GPU dependency를 기본 graph에 넣지 않는다. macOS ARM64는 별도 consumer smoke로 검증하고 지원을 선언하지 않는다. |
| [Java binding README](https://raw.githubusercontent.com/microsoft/onnxruntime/main/java/README.md) | Java API는 JNI를 사용하며 jar에는 플랫폼 native resource가 포함된다. loader는 classpath resource를 기본으로 사용하고 명시적 native path/skip property도 제공한다. build는 Java 11+이고 compiled jar의 일반 실행 기준은 Java 8+다. | Java 25는 “공식 일반 실행 보장”으로 간주하지 않고 consumer compile/runtime smoke로 검증한다. native path override는 숨은 host dependency가 되므로 receipt에 남긴다. |
| [Java package summary](https://onnxruntime.ai/docs/api/java/ai/onnxruntime/package-summary.html) | Java binding은 `onnxruntime`와 `onnxruntime4j_jni` native library를 사용하며 loader property로 명시 경로와 기본 resource loading을 조정할 수 있다. | 두 library의 resolved path·byte size·SHA-256·architecture를 같은 receipt에 남긴다. |
| [OrtEnvironment API](https://onnxruntime.ai/docs/api/java/ai/onnxruntime/OrtEnvironment.html) | `OrtEnvironment`는 `AutoCloseable`이지만 1.11 이후 `close()`가 no-op이고 JVM lifetime에 하나인 host object로 설명된다. session 생성과 available provider 조회를 제공한다. | environment를 request마다 만들고 닫는 public API를 만들지 않는다. provider 내부 singleton/lifecycle 책임을 문서화한다. |
| [OrtSession.SessionOptions API](https://onnxruntime.ai/docs/api/java/ai/onnxruntime/OrtSession.SessionOptions.html) | `SessionOptions`는 `AutoCloseable`이며 이를 사용하는 모든 session이 닫힌 뒤에 닫아야 한다. EP 순서와 custom-op library registration surface가 있다. | `session 종료 → SessionOptions 종료` 순서를 검증하고 custom op 등록은 기본 차단한다. |
| [OrtSession.Result API](https://onnxruntime.ai/docs/api/java/ai/onnxruntime/OrtSession.Result.html) | `Result`는 소유한 `OnnxValue`를 닫을 수 있다. pinned output ownership은 별도 규칙이므로 `Result.close()`가 모든 output을 닫는다고 가정하면 안 된다. | result, tensor, pinned output의 소유자를 각각 정하고 정상·예외·취소 경로를 fixture로 확인한다. |
| [OnnxTensor API](https://onnxruntime.ai/docs/api/java/ai/onnxruntime/OnnxTensor.html) | tensor는 native memory를 소유하고 명시적인 close가 필요한 `AutoCloseable` object다. | per-call tensor와 temporary buffer를 `use` 또는 명시적 close로 정리하고 native memory를 heap-only로 추정하지 않는다. |
| [Execution Providers](https://onnxruntime.ai/docs/execution-providers/) | CPU와 GPU를 포함한 EP가 있고 provider 순서와 fallback이 결과에 영향을 준다. | CPU/GPU 결과를 같은 PASS로 합치지 않고 active EP와 fallback을 receipt에 기록한다. |
| [EP build requirements](https://onnxruntime.ai/docs/build/eps.html) | provider shared library와 non-shared dependency가 모두 있어야 하며 native loader는 같은 위치/검색 경로 규칙을 따른다. | missing library, wrong search path, ABI mismatch를 별도 failure reason으로 분리한다. |
| [Installation guide](https://onnxruntime.ai/docs/install/) | GPU에는 CUDA/cuDNN과 OS별 library search path가 필요하고 Windows에는 VC++ runtime 조건이 있다. | GPU는 manual/nightly 환경으로 제한하고 PR required check로 승격하지 않는다. |
| [CUDA Execution Provider](https://onnxruntime.ai/docs/execution-providers/CUDA-ExecutionProvider.html) | Java GPU provider options와 CUDA/cuDNN compatibility 조건이 별도 문서화돼 있다. | GPU artifact/version/toolkit matrix를 CPU baseline과 독립적으로 pin하고 skip을 성공으로 합치지 않는다. |
| [Maven Central ORT metadata](https://central.sonatype.com/artifact/com.microsoft.onnxruntime/onnxruntime) | 2026-08-25 조회 시 `com.microsoft.onnxruntime:onnxruntime`의 관찰 version은 `1.29.0`이며 POM license는 MIT다. | `1.29.0`은 조사일 기준 관찰값이지 무기한 latest 선언이 아니다. 실제 채택 version의 POM, Gradle metadata, artifact byte size, SHA-256, license와 함께 다시 고정한다. |
| [ONNX Runtime versioning](https://github.com/microsoft/onnxruntime/blob/main/docs/Versioning.md) | ORT는 semantic versioning과 ONNX opset compatibility를 별도로 다룬다. | runtime version, model IR/opset, manifest schema를 같은 compatibility matrix에 기록한다. |
| [External data security](https://onnx.ai/onnx/repo-docs/ExternalDataSecurity.html) | external data는 symlink, hardlink, path traversal을 포함한 파일시스템 위협을 만든다. | untrusted external data를 기본 거부하고 허용 시 managed root·canonical path·link 정책을 검증한다. |
| [External data](https://onnx.ai/onnx/repo-docs/ExternalData.html) | external-data location은 상대 경로이며 `..` 같은 상위 탈출을 허용하면 안 된다. | model package의 모든 referenced file을 manifest에 열거하고 root 밖 접근은 fail closed 한다. |
| [ONNX Runtime Extensions](https://onnxruntime.ai/docs/extensions/) | custom-op library를 등록할 수 있고 Java에서도 registration path가 제공된다. | untrusted model이 custom native code를 로드하지 못하도록 custom op는 첫 adoption 범위에서 금지한다. |
| [ORT format models](https://onnxruntime.ai/docs/performance/model-optimizations/ort-format-models.html) | ORT format은 runtime/model compatibility와 in-memory model bytes lifecycle을 별도로 갖는다. | ONNX와 ORT format을 같은 fixture로 취급하지 않고 format, runtime, opset을 명시한다. |

## Native loader와 lifecycle 검증 계약

후속 provider 구현은 아래 경계를 코드와 fixture로 증명해야 한다.

### Loader와 EP

1. 기본 classpath loading만으로 Linux x64 CPU fixture를 실행한다.
2. explicit native path override를 사용하는 경우 path, loader property, resolved
   library와 SHA-256을 receipt에 기록한다.
3. resolved EP 목록과 실제 선택된 EP를 기록한다. CPU fallback이 발생하면 GPU
   smoke의 성공으로 표시하지 않는다.
4. missing `onnxruntime`/`onnxruntime4j_jni` 또는 provider dependency, wrong
   architecture, incompatible CUDA/cuDNN, Windows VC++ runtime을 각각
   `NATIVE_RUNTIME_UNAVAILABLE` 하위 원인으로 구분한다.
5. native library를 원격에서 runtime auto-download하지 않는다. CI fixture는
   checkout과 dependency cache에서만 읽는다.

### Ownership과 close order

| 자원 | owner | 닫는 순서 | 필수 검증 |
|---|---|---|---|
| `OrtEnvironment` | provider process/JVM lifecycle | JVM lifecycle; per-call close하지 않음 | singleton 재사용과 no-op close 설명이 API contract와 일치하는지 확인 |
| `OrtSession.SessionOptions` | provider | 모든 session 종료 뒤 | session이 살아 있을 때 먼저 close하지 않는 race fixture |
| `OrtSession` | model/session pool | in-flight run 완료 또는 bounded cleanup 뒤 | close 이후 새 호출 거부, reuse/eviction 경계 |
| `RunOptions` | inference call | `run`/취소 처리 직후 | terminate 요청과 coroutine cancellation의 차이를 기록 |
| `OrtSession.Result` | inference call | output mapping 및 owned values 소비 뒤 | exception/cancellation에서도 close |
| `OnnxTensor`·pinned output | call/output mapper | consumer가 더 이상 native memory를 읽지 않을 때 | `Result.close()`만으로 pinned output이 정리된다고 가정하지 않음 |

`CancellationException`은 일반 `RuntimeException` catch보다 먼저 재전파한다. native
inference가 즉시 중단되지 않을 수 있으므로 acceptance는 “호출자 관찰 중단”과 “native
작업 종료 및 resource cleanup”을 별도 시각으로 기록한다. unbounded thread 생성,
request마다 environment/session 생성, close 중 in-flight use-after-free는 실패다.

## Platform·artifact matrix

아래 matrix는 “검증할 대상”이며 현재 지원을 선언하는 표가 아니다.

| Tier | OS/arch | package | 실행 시점 | required evidence | 현재 상태 |
|---|---|---|---|---|---|
| P0 deterministic | repository runner JVM | CPU artifact, checked-in tiny model | PR | no-network load/metadata/fixture, checksum, sanitized error | 후속 구현 |
| P1 native baseline | Linux x64 | `onnxruntime` | PR 또는 required scheduled | native load, CPU EP, session/result/tensor close, Java 25 smoke | 미실행 |
| P1 consumer | macOS ARM64 | CPU candidate selected by exact version | scheduled/nightly | dependency resolution, native load, architecture, Java 25 runtime | artifact support 확인 필요 |
| P1 consumer | Windows x64 | CPU candidate selected by exact version | scheduled/nightly | native load, PATH/VC++ runtime, Java 25 runtime | 미실행 |
| P2 GPU | Linux x64/Windows x64 | `onnxruntime_gpu` + pinned CUDA/cuDNN | manual/nightly | active CUDA EP, toolkit matrix, memory/timeout, no CPU fallback | adoption 이후 |
| P2 alternate | other OS/arch | none by default | issue-driven | explicit support decision and rollback | 범위 밖 |

CPU artifact의 Maven 문서 표기와 실제 macOS ARM64/Windows x64 resolution 결과가
다르면, 표의 “consumer smoke”는 지원 선언이 아니라 gap 증거가 된다. 해당 OS에서
host-installed native library를 우연히 찾았다는 이유로 green으로 판정하지 않는다.

## BOM·metadata·consumer smoke 계약

후속 Type-A catalog/BOM PR은 다음을 한 번에 검증해야 한다.

- version catalog에 exact ORT CPU alias와 필요 시 별도 GPU alias를 둔다. `latest`,
  dynamic version, transitive version override를 사용하지 않는다.
- `bom/build.gradle.kts` constraint와 published module inventory가 같은 version을
  가리키고, generated POM/Gradle metadata에 license와 dependency scope가 맞는지
  확인한다.
- API artifact의 `api`/`compileOnly` graph에 ORT/JNI/native type이 없고 provider
  artifact만 ORT를 소유하는지 `apiElements`, `runtimeClasspath`, generated POM으로
  확인한다.
- consumer example은 개별 Image artifact version을 적지 않고
  `platform(libs.bluetape4k.dependencies)`와 unversioned alias 정책을 따른다.
- Java 25 consumer가 versionless BOM resolve, compile, native smoke를 순서대로
  수행한다. compile 성공만 runtime/native 성공으로 해석하지 않는다.
- ORT exact version, Maven POM, Gradle module metadata, artifact byte size, artifact
  SHA-256, license, source/ref, retrieval date, SBOM/NOTICE 위치를 receipt에 남긴다.

Jackson 3는 #549에서 정한 대로 private implementation-only codec의 기본값이다.
이 issue는 ORT Java API의 serialization을 public contract로 만들지 않으며,
`kotlinx.serialization`, mapper, default typing, class-name polymorphism을
추가하지 않는다.

## CI·fixture·failure contract

### PR required lane

- checked-in tiny model 또는 fake provider만 사용한다.
- network, remote model URL, runtime auto-download, custom op, external data를
  사용하지 않는다.
- ORT dependency가 승인된 뒤에만 CPU native load/run/close를 required check로
  추가한다. 그 전에는 문서/metadata validation만 실행한다.
- native check는 `forkEvery = 1`, `maxParallelForks = 1`에 준하는 격리 정책을
  적용하고, macOS Colima/Testcontainers 정책과 섞지 않는다.

### Scheduled/nightly lane

- Linux x64 CPU를 baseline으로 매일 또는 nightly 실행하고 exact ORT/Java/OS/arch,
  active EP, native library checksum, model/manifest digest, elapsed time을 기록한다.
- macOS ARM64와 Windows x64 consumer smoke는 해당 runner가 실제로 제공될 때만
  실행하며 unavailable/skipped를 CPU baseline PASS에 합치지 않는다.
- GPU/CUDA는 manual/nightly 별도 job으로 두고 CUDA/cuDNN/driver/VC++ matrix와
  provider fallback을 기록한다.
- path filter는 API/provider/catalog/BOM/example/CI 변경을 놓치지 않아야 하며,
  docs-only 변경에서 native job이 실행되지 않은 사실을 성공 증거로 포장하지 않는다.

### Sanitized failure reasons

public error와 CI summary에는 아래 bounded reason만 사용한다.

`MODEL_UNAVAILABLE`, `MODEL_CORRUPT`, `MODEL_EXTERNAL_DATA_REJECTED`,
`MODEL_CUSTOM_OP_REJECTED`, `NATIVE_RUNTIME_UNAVAILABLE`, `EP_UNAVAILABLE`,
`UNSUPPORTED_OPSET`, `INFERENCE_TIMEOUT`, `INFERENCE_CANCELLED`, `OUTPUT_INVALID`,
`INFERENCE_FAILED`.

원본 model path, URL, credential, host absolute path, native stack trace와 input bytes를
public message나 metric label에 넣지 않는다. 내부 receipt에는 sanitized reason과
재현 가능한 artifact/model/manifest checksum만 기록한다.

## 보안 acceptance

| 위험 | 기본 정책 | 후속 fixture |
|---|---|---|
| remote model/weights download | 금지 | URL/redirect/network 호출이 없음을 확인 |
| external data traversal/symlink/hardlink | 거부. 허용 시 managed root와 canonical path를 모두 검사 | `..`, symlink, hardlink, missing sidecar, root 밖 file |
| custom op/native code | 금지 | registration path 입력이 `MODEL_CUSTOM_OP_REJECTED`가 되는지 |
| corrupted model/unknown opset | fail closed | truncated bytes, invalid header, unsupported opset |
| provider fallback | 명시적 기록 없이는 성공 금지 | GPU 요청이 CPU로 조용히 바뀌지 않는지 |
| resource exhaustion | image/model/threads/memory/time bound | large tensor, concurrent close, timeout, cancellation |
| path/diagnostic leakage | sanitized public error | absolute path, native error, credential redaction |

model bytes와 sidecar labels/preprocess/postprocess는 #548 manifest digest와 일치해야
한다. 동일 파일 이름이나 동일 ORT version만으로 identity를 인정하지 않는다.

## Benchmark acceptance

실제 benchmark 결과는 #551 adoption evidence다. #550에서는 protocol만 고정한다.

- 고정된 JVM/Java/ORT/model/manifest/OS/arch/EP와 warm-up 횟수를 기록한다.
- cold session creation과 warm session reuse를 분리해 p50/p95/p99 latency를
  기록한다.
- RSS와 native memory, thread 수, session 수, tensor allocation/release를 같은
  workload에서 기록한다.
- single-thread, bounded concurrency, close 중 concurrency, session reuse와
  per-request session 생성의 결과를 비교한다.
- quality가 포함되면 동일 model/corpus/labels/provenance를 사용하고 latency와
  quality를 한 숫자로 합치지 않는다.
- benchmark가 실행되지 않았거나 native provider가 unavailable이면 `PENDING`으로
  남긴다. skipped는 baseline PASS가 아니다.

## 대안과 위험

| 대안 | 장점 | 위험·비용 | 판정 |
|---|---|---|---|
| ORT Java CPU direct | JVM API, CPU artifact, provider 선택과 session lifecycle을 직접 제어 | JNI/native matrix, model preprocessing과 memory ownership을 직접 관리 | 조건부 후보 |
| ORT Java GPU opt-in | CUDA EP로 GPU inference 가능 | CUDA/cuDNN/driver/VC++ matrix와 native memory가 크고 CI 재현성이 낮음 | CPU adoption 이후 별도 |
| ORT Server/remote inference | application JVM에서 native loader를 제거 | network/SLO/credential/data residency와 운영 component 추가 | 현재 범위 밖 |
| DJL 또는 다른 model runtime | model zoo와 고수준 abstraction 가능 | provider/native artifact가 한 단계 더 간접화되고 BOM·license surface가 커짐 | 별도 research issue |
| LiteRT/TensorFlow 계열 | 일부 mobile/edge workload에 적합 | ONNX manifest/opset과 tooling이 달라 migration cost 발생 | 현재 범위 밖 |
| fake provider만 유지 | deterministic PR test와 API 검증이 쉬움 | 실제 native compatibility·performance 증명이 없음 | API 선행 fixture로 채택 |

권고는 “CPU direct를 조건부 후보로 검증하고, GPU는 opt-in으로 격리하며, #551
ADOPT 전에는 아무 dependency도 추가하지 않는다”이다. native matrix나 model
provenance가 실패하면 API 설계를 되돌리지 않고 provider/BOM/CI train만 `DEFER`한다.

## 미해결 질문과 후속 issue 입력

1. exact ORT version이 Java 25와 Linux x64/macOS ARM64/Windows x64에서 같은
   artifact/native loader를 제공하는가?
2. chosen version의 `onnxruntime`/`onnxruntime_gpu` POM, Gradle metadata, license,
   SBOM, artifact SHA-256, NOTICE를 어떤 release/ref로 보존할 것인가?
3. macOS ARM64 CPU artifact가 없거나 host library가 필요하면 지원을 `DEFER`할지,
   별도 packaging을 연구할지?
4. Java binding의 JNI loader가 classpath resource와 explicit native path에서
   동일 checksum·EP를 선택하는가?
5. external-data를 완전히 금지할지 managed root subset을 허용할지?
6. session pool의 최대 크기, idle eviction, close bounded wait와 native memory
   budget을 어떤 workload로 정할지?
7. GPU manual/nightly 결과를 #551 quality/latency decision에 어떻게 반영할지?

이 질문은 후속 Type-A 구현과 별도 benchmark/compatibility issue로 분해한다.

## 검증 범위와 DoD

- `SPW-01`: PASS — issue scope, 선행 #548/#549, 후속 #551, Type-E 경계를 고정했다.
- `SPW-02`: PASS — ORT official source, Maven metadata, repo source ledger, matrix,
  security, CI, benchmark, alternatives와 gaps를 기록했다.
- `SPW-03`: PASS — 한국어 기술 문체를 사용하고 version, coordinates, API names,
  commands, URLs를 그대로 보존했다.
- `SPW-04`: PASS — 현재 repository에는 classification/ORT implementation이 없다는
  사실과 설계 제안을 분리하고, official claim을 source URL에 연결했다.
- `SPW-05`: PASS — native test, benchmark, Java 25 compatibility, platform support는
  모두 `PENDING`이며 문서 연구를 실행 증거로 승격하지 않았다.

`#550`의 문서 결과는 `RESEARCH_COMPLETE / IMPLEMENTATION_BLOCKED / ADOPTION_PENDING`이다.
