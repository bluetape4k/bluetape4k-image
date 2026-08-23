# Issue #543 AI/ML 모델 공급망·offline cache·license·CI 공통 정책

- Epic: [#513 AI/ML backend 연구 train](https://github.com/bluetape4k/bluetape4k-image/issues/513)
- 범위 이슈: [#543](https://github.com/bluetape4k/bluetape4k-image/issues/543)
- 후속 train: [#169 PaddleOCR](https://github.com/bluetape4k/bluetape4k-image/issues/169), [#3 이미지 분류](https://github.com/bluetape4k/bluetape4k-image/issues/3)
- 조사일: 2026-08-19
- 결정: **공통 정책 채택, backend 구현은 별도 gate 이후**
- 구현 상태: 이 문서는 정책과 검증 기준만 고정하며 production dependency, 모델 파일, 자동 다운로드, API 구현을 추가하지 않는다.

## 결정 요약

두 backend가 서로 다른 runtime 경계를 갖더라도 모델과 native 자산을 일반 library dependency처럼 취급하지 않는다. 모든 채택 후보는 동일한 provenance manifest, offline cache, license/SBOM, 보안, CI 계층을 통과해야 한다.

| 정책 영역 | 0.5.0 공통 결정 | 금지되는 기본 동작 |
| --- | --- | --- |
| 모델 identity | publisher, release/revision, model/head/labels 조합, source, size, SHA-256을 manifest로 고정 | 이름만 지정한 모델 해석, latest alias, 검증 전 로드 |
| 배포 | 모델·runtime은 optional provider 또는 배포 artifact로 분리 | 핵심 `bluetape4k-images` JAR에 대형 weight/native를 번들링 |
| cache | 명시적으로 준비한 allowlisted local root와 checksum 기반 key를 사용 | first-use network, background download, 임의 경로·URL·symlink |
| license | 모델·dataset·runtime·container별 license와 NOTICE/SBOM을 별도로 보존 | repository license를 모든 모델과 transitive dependency에 전이 |
| 보안 | fail-closed 검증, no-log, no-egress 기본, 안정적인 reason code | raw path/native traceback/이미지/OCR 결과 노출 |
| CI | PR은 network-free fixture, CPU/native와 GPU는 별도 tier | 일반 PR에서 medium model 다운로드, GPU hardware 의존, flaky retry로 성공 위장 |

이 결정은 PaddleOCR 또는 ONNX Runtime을 자동 채택한다는 뜻이 아니다. #169는 Tesseract baseline을 유지하고 PaddleOCR를 self-hosted HTTP service 후보로 보류했으며, #3은 ONNX Runtime Java direct를 조건부 후보로 판단했다. 두 문서의 backend 결론은 유지하되 이 정책을 공통 prerequisite로 참조한다.

## 범위와 비범위

### 범위

- 모델·가중치·label·preprocess/postprocess의 provenance와 무결성
- offline cache 준비·검증·무효화와 network/egress 경계
- model card, dataset, source, runtime, native, container의 license와 SBOM
- CPU, native, scheduled/nightly, GPU 검증의 책임과 비용 상한
- 이미지·모델·OCR/classification 결과, secret, 오류의 신뢰 경계
- #169와 #3의 후속 benchmark·설계·채택 gate가 재사용할 acceptance 항목

### 비범위

- PaddleOCR, ONNX Runtime, LiteRT, DJL 등의 dependency를 `build.gradle.kts`에 추가하는 일
- production `ImageClassifier`, provider-neutral OCR API, HTTP adapter 또는 model resolver 구현
- 특정 ImageNet classifier head/labels, Paddle detector/recognizer 조합의 선정
- 모델 파일, container image, native binary, SBOM을 저장소에 추가하는 일
- 실제 품질 corpus benchmark와 native/GPU 실행 결과의 생성

위 항목은 각각 #544–#551의 research/design/decision train과 이후 승인된 Type-A 구현 issue에서 다룬다.

## 기준 정보와 현재 저장소 경계

선행 연구와 저장소 현재 상태를 기준선으로 삼는다.

| 기준 | 확인된 사실 | 정책 영향 |
| --- | --- | --- |
| OCR | `images-ocr`의 `OcrOptions`가 Tess4J `ITessAPI`, tessdata, Tesseract page/engine mode에 결합되어 있다. | Paddle provider를 추가하기 전에 provider-neutral API 경계를 별도 설계한다. |
| classification | Java/Kotlin 25, optional provider module, detection runtime/model 분리, BOM 자동 constraint 구조를 사용한다. | ORT type은 API module에 노출하지 않고 API/provider artifact를 분리한다. |
| native | OCR와 Vips CI는 host/native ABI 차이와 환경 의존성을 별도 gate로 관리한다. | native smoke를 일반 JVM unit test의 성공으로 간주하지 않는다. |
| 기존 연구 | #169는 PaddleOCR **DEFER**, #3은 ORT Java direct **조건부 ADOPT**이다. | 공통 정책은 backend 결론을 덮어쓰지 않고 재사용 가능한 gate만 제공한다. |

## 1. 모델 provenance manifest 계약

### 필수 identity

모델을 읽기 전에 다음 필드를 가진 versioned manifest를 선택하고 strict parse한다. 필드가 누락되거나 알 수 없는 schema version이면 로드하지 않는다.

```yaml
schemaVersion: 1
model:
  id: repo-owned-stable-id
  version: publisher-release-or-immutable-revision
  publisher: verified-publisher
  sourceUrl: pinned-artifact-url
  mediaType: application/octet-stream
  byteSize: 123456
  sha256: 0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef
  licenses:
    - artifact: model
      spdxExpression: Apache-2.0
      source: model-card-or-license-url
      noticePath: NOTICE-model.txt
    - artifact: labels-or-dataset
      spdxExpression: Apache-2.0
      source: separate-license-or-card-url
      noticePath: NOTICE-labels.txt
runtime:
  provider: onnxruntime-java|paddleocr-http
  version: pinned-runtime-version
  artifactDigest: sha256:...
  containerDigest: sha256:... # service/container를 쓰는 경우만
semantics:
  architecture: classifier-head-or-det-rec-pipeline
  input: {name: input, dtype: float32, layout: NCHW, shape: [1, 3, 224, 224]}
  preprocessing: repo-owned-recipe-id
  output: logits-or-ocr-geometry-contract
  labelsPath: labels.txt
  labelsSha256: 0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef
  postprocessing: repo-owned-recipe-id
```

실제 backend에 따라 `runtime.containerDigest`, `semantics.input/output`,
`labels*`가 선택적으로 적용될 수 있지만, 적용되는 필드는 반드시 명시한다. `licenses`는 compound model의 각 artifact 역할(model, labels, dataset, runtime, native, container)을 별도 항목으로 기록하며, 하나의 repository license로 합치지 않는다. `spdxExpression`은 SPDX expression을 사용하고, `LicenseRef-*` 또는 비표준 license는 원문 URL/텍스트 hash, attribution과 restrictions를 함께 기록한다. 예를 들어 ORT 분류는 tensor name/dtype/layout, opset, preprocessing, label index order와 top-k tie-break를 고정해야 한다. PaddleOCR는 detector·recognizer·orientation/unwarp 조합, 언어, geometry 변환, service API schema와 model directory digest를 고정해야 한다.

### 검증 순서

검증은 다음 순서를 지키며 어느 단계든 실패하면 provider와 모델을 호출하지 않는다.

1. manifest의 UTF-8, schema version, 필드 allowlist, 문자열/collection 길이, SHA-256 형식을 strict parse한다.
2. provider와 모델 identity가 허용된 catalog entry인지 확인한다. `latest`, wildcard, 사용자 입력 URL은 허용하지 않는다.
3. managed root 아래의 regular file인지, 모든 ancestor가 `NOFOLLOW_LINKS` 정책을 만족하는지, 권한과 root ownership이 안전한지 확인한다.
4. 파일의 declared byte size와 실제 크기를 overflow-safe하게 비교한다. 상한을 먼저 적용하고, 상한 초과 파일은 hash 또는 parser를 실행하지 않는다.
5. streaming SHA-256을 계산해 manifest와 비교한다. 불일치·부분 파일·중복 cache entry는 폐기하고 replacement로 승격하지 않는다.
6. model license, NOTICE, runtime/native/container digest와 SBOM receipt가 존재하는지 확인한다.
7. 마지막으로 model parser/provider를 열어 architecture, opset, tensor shape, labels와 preprocessing 계약을 검증한다. parser는 path를 다시 resolve하지 않고, 앞 단계에서 hash한 동일 descriptor/immutable staging snapshot을 소비해야 한다. provider가 path만 받는다면 no-replace descriptor를 유지한 채 즉시 size/hash를 재확인한 read-only snapshot에서만 열며, 검증된 bytes와 다른 파일을 읽었을 가능성이 있으면 fail-closed한다.

모델 parser가 추가 파일을 자동으로 열거나 custom operator를 실행하는 경로는 v1에서 금지한다. 검증 전 parser 호출을 허용하면 external-data traversal이나 악성 native code가 integrity gate를 우회할 수 있다.

### v1 형식 제한

- ORT v1은 **single-file ONNX**만 허용한다. external-data, custom op, remote URL, implicit sidecar와 archive extraction은 거부한다.
- Paddle service v1은 명시된 model directory와 manifest digest를 사용하되, directory 밖의 파일·symlink·startup download를 거부한다.
- label 파일은 model과 별도의 byte size/SHA-256을 가지며, index 순서와 중복 label 정책을 고정한다.
- preprocessing/postprocessing은 vendor default를 암묵적으로 사용하지 않고 repository-owned recipe/version으로 기록한다.
- 모델 업데이트는 코드 dependency 업데이트와 분리된 변경으로 취급하며, 새 manifest·benchmark·rollback receipt가 필요하다.

## 2. offline cache와 provisioning

### 기본 운영 원칙

runtime은 model name이나 source URL을 보고 네트워크를 시작하지 않는다. 배포 단계에서 운영자가 검증된 artifact를 managed cache에 준비하고, 실행 시에는 해당 cache만 읽는다.

- cache root는 애플리케이션 설정으로 allowlist하며, 사용자 입력 path와 분리한다.
- portable model cache key는 `modelSha256 + manifestVersion + artifactRole`의 canonical 조합이다. 파일명이나 URL만으로 key를 만들지 않는다.
- runtime/native cache key는 portable key에 `providerVersion + executionProvider + runtimeArchitecture + nativeAbi`를 추가한다. portable model bytes와 provider별 optimized/native cache를 같은 namespace에 섞지 않는다.
- 준비 작업은 temporary sibling에 다운로드/복사하고, 크기·hash·license/SBOM 검증 후 atomic no-replace 승격한다.
- 실패한 partial file은 유효한 entry로 보이지 않아야 하며, concurrent reader는 old valid entry 또는 명확한 missing 상태만 본다.
- cache read는 read-only 권한을 선호하고, eviction은 현재 사용 중인 identity를 삭제하지 않는 별도 provisioning 정책으로 둔다.
- 캐시가 없거나 manifest와 다르면 fail-closed한다. 자동 재다운로드나 silent fallback은 하지 않는다.

### invalidation과 rollback

다음은 새 cache entry를 만들기 전에 수행한다.

| 상태 | 허용 동작 | 금지 동작 |
| --- | --- | --- |
| manifest/hash 일치 | read-only load | 파일을 다시 쓰거나 hash를 갱신 |
| hash 불일치 | entry quarantine/삭제 후 운영자 조치 | 기존 manifest를 실제 hash로 덮어쓰기 |
| size 초과/파일 누락 | stable `MODEL_ARTIFACT_INVALID` 또는 `MODEL_NOT_PROVISIONED` | network fallback |
| license/SBOM 누락 | load 거부 | warning만 남기고 실행 |
| 새 version 준비 | 새 identity로 atomic promote, old receipt 보존 | in-place overwrite |

rollback은 이전에 승인된 manifest와 receipt를 다시 선택하는 방식으로만 수행한다. cache 디렉터리의 파일명 변경으로 rollback을 암묵화하지 않는다.

### 네트워크 책임

CI PR과 production runtime은 model source connectivity probe를 하지 않는다. 사전 provisioning job이나 별도 artifact registry가 네트워크를 사용할 수 있지만, 그 단계는 checksum·license·SBOM 검증 결과를 남겨야 하며 애플리케이션 startup과 분리한다. PaddleX의 source probe와 model download, 애플리케이션이 제공하는 임의 URL resolver는 이 경계 밖에서 명시적으로 비활성화한다.

## 3. license, NOTICE, SBOM과 변경 gate

모델은 코드 dependency와 다른 공급망 항목이다. 다음 네 계층을 각각 기록한다.

1. **모델/label/dataset**: manifest의 artifact별 model card, source URL, publisher, exact file hash, SPDX 또는 원문 license, 사용 제한과 attribution.
2. **runtime/library**: Paddle/PaddleX, ONNX Runtime, Tess4J 등 package version, repository license, transitive dependency license.
3. **native/driver**: JNI/FFM binary, CUDA/cuDNN 또는 system library version, artifact digest와 플랫폼 matrix.
4. **container/service**: base image digest, package SBOM, NOTICE, non-root/read-only 설정과 취약점 결과.

Apache-2.0 repository가 포함한 모든 model, dataset, third-party extra가 Apache-2.0이라는 뜻은 아니다. catalog 항목별 license가 누락되면 채택 gate는 실패한다. SBOM은 런타임 jar만이 아니라 container/native와 모델 receipt를 함께 가리켜야 한다.

새 모델 또는 runtime 버전은 다음을 요구한다.

- 이전 identity와 새 identity의 manifest diff
- source/release와 file SHA-256 재검증
- license/NOTICE/SBOM 및 취약점 결과 갱신
- 품질·지연·RSS·startup benchmark 비교
- rollback receipt와 지원 플랫폼 변화
- PR에서의 no-network 재현성 확인

## 4. 보안·개인정보 신뢰 경계

| 위협 | 최소 방어 | 외부에 노출할 정보 |
| --- | --- | --- |
| model 교체/악성 artifact | managed root, size/hash, publisher/license, optional signature, SBOM | stable reason code와 model identity만 |
| external-data/path traversal | ORT v1 single-file, no-follow ancestor/entry, archive/custom op 거부 | raw path·parser stack trace 금지 |
| first-use download/telemetry | offline source, egress deny, pre-baked/pinned cache | network URL·credential 금지 |
| 0.0.0.0 service 노출 | loopback/private bind, reverse-proxy auth/mTLS, network policy | health identity/readiness만 |
| image/PDF/model memory bomb | encoded bytes, dimensions/pages/pixels, body/time/concurrency limits | 입력 bytes와 OCR text 금지 |
| retry/cancellation 폭주 | bounded queue, timeout, retry budget, circuit breaker, close guarantee | public exception에는 raw cause를 절대 넣지 않고, 내부에도 path/secret 없는 allowlisted reason만 기록 |
| cache symlink/world-writable | ownership/permission check, `NOFOLLOW_LINKS`, read-only runtime | `MODEL_CACHE_UNSAFE` reason만 |

PaddleHTTP adapter는 HTTP를 신뢰 경계로 간주하지 않는다. 기본 serving bind가 `0.0.0.0:8080`인 경우에도 내부 loopback/private network, authentication/TLS, request body/page/pixel/time/concurrency limits, no-log, secret injection, non-root/read-only container를 별도로 강제한다. URL 입력 모드는 외부 egress와 credential 노출 위험이 있으므로 기본 비활성화한다.

ORT direct provider는 `OrtEnvironment`, `OrtSession`, tensor와 native loader를 명시적으로 소유하고 close한다. session을 inference마다 생성하지 않으며, session/thread 수를 제한한다. coroutine cancellation이 native inference를 항상 즉시 중단한다고 주장하지 않고, cooperative termination 범위와 최악 단일 호출 지연을 문서화한다.

## 5. CI와 증적 계층

CI는 속도보다 재현성의 계층을 분리한다. 한 tier의 성공이 다른 tier의 지원을 의미하지 않는다.

| Tier | 실행 내용 | 네트워크/하드웨어 | 필수 산출물 |
| --- | --- | --- | --- |
| PR required | manifest schema/fixture, stale hash·size·license·path 거부, no-network, fake provider/HTTP contract, deterministic result | network 없음, 일반 JVM | test report, policy receipt, sanitized error assertion |
| scheduled CPU/native | pinned tiny/small model 또는 pre-baked service, session/load/close, 대표 smoke | Ubuntu x64, macOS ARM64 등 명시 matrix | exact runner/JDK/runtime/model/container digest, logs, RSS/latency 요약 |
| nightly benchmark | multilingual/noisy corpus의 품질, cold/warm p50/p95/p99, throughput, RSS, startup | CPU 중심, 긴 timeout | 비교 가능한 baseline과 artifact, regression threshold |
| manual/GPU | CUDA/EP, GPU image, driver와 fallback 경로 | self-hosted GPU | hardware/driver/container evidence, 실패 시 N/A 사유 |
| release | 최종 manifest·SBOM·NOTICE·license·rollback receipt와 selected tier 재실행 | pinned release environment | immutable release checklist |

PR required 경로는 외부 model registry나 first-use Docker pull에 의존하지 않는다. container smoke가 필요한 경우에도 작은 pinned fixture와 사전 준비된 cache를 사용하고, medium model·GPU·full corpus는 scheduled/manual로 분리한다. native 초기화 실패를 retry 횟수로 숨기지 않고 `UNAVAILABLE`, `FAILED_SMOKE`, `ERROR` 같은 고정 상태와 원인을 sanitized evidence로 남긴다.

공통 PR fixture 최소 집합은 다음과 같다.

1. 정상 manifest와 정상 cache load
2. 오래된 SHA-256, declared size 불일치, size 상한 초과
3. 누락/불일치 license·NOTICE·SBOM
4. missing cache와 offline 환경에서의 fail-closed
5. absolute path, `..`, symlink ancestor/entry, world-writable root
6. unsupported schema, external-data/custom-op/remote URL
7. native/runtime/container digest mismatch와 sanitized error
8. verified replacement와 rollback에서 old receipt 보존

## 6. backend별 적용과 train 순서

### #169 PaddleOCR

선행 연구의 결론은 Tesseract/Tess4J baseline 유지와 PaddleOCR **DEFER**다. benchmark와 공급망 gate가 통과하더라도 Python/Paddle runtime을 JVM in-process나 호출별 CLI로 넣지 않고, 별도 self-hosted HTTP/container adapter만 재평가한다. #545는 service/container provenance·보안·CI를, #544는 Tesseract 대비 corpus 품질·성능을, #546은 provider-neutral OCR API 경계를, #547은 최종 채택 여부와 Type-A 범위를 다룬다.

### #3 이미지 분류

선행 연구의 결론은 ONNX Runtime Java direct **조건부 ADOPT**, LiteRT/DJL/TensorFlow Java/OpenCV DNN 첫 provider는 보류 또는 거부다. CPU artifact를 기본으로 하고 GPU execution provider는 opt-in/nightly로 둔다. DINOv2 backbone만으로 ImageNet classifier 의미가 생기지 않으므로 #548이 구체적인 head/labels/provenance/manifest를 먼저 고정하고, #550이 native/platform/BOM/CI 증거를 만든다. #549는 provider-neutral API와 ORT artifact 경계를, #551은 Type-A 구현 gate를 결정한다.

### stacked train dependency

```text
#543 공통 정책
  ├─> #544 Paddle 품질/성능 benchmark ─┐
  ├─> #545 Paddle service/container gate ─┴─> #546 OCR API plan ─> #547 Paddle 채택 gate
  ├─> #548 분류 model/head/labels manifest ─┐
  └─> #550 ORT native/platform/BOM/CI ─────┴─> #549 classification API plan ─> #551 분류 채택 gate
```

#543은 #169와 #3의 compile dependency가 아니다. 공통 정책을 먼저 merge한 뒤 #544/#545와 #548/#550은 독립 research PR로 병렬 진행할 수 있다. 각 research가 끝난 뒤 해당 backend의 design(#546 또는 #549), 이어서 decision gate(#547 또는 #551)를 진행한다. 후속 문서는 이 정책의 manifest·cache·license·CI gate를 인용해야 하며, backend 하나의 결론이 다른 backend를 자동 채택하지 않는다.

## 장점·단점·대안

| 대안 | 장점 | 단점·위험 | 결정 |
| --- | --- | --- | --- |
| caller-managed verified local model | offline·재현성·공급망 경계가 가장 단순하고 library artifact가 작다 | 사용자 provisioning과 receipt 관리가 필요하다 | 0.5.0 기본 |
| verified resolver/cache | setup 편의성과 공통 cache/eviction 제공 | download 권한, mirror, locking, egress와 poisoning 방어가 추가된다 | 후속 별도 설계 |
| JAR에 model bundle | quickstart가 간단하다 | artifact 크기·license/update·platform과 공급망 재현성이 악화된다 | 거부 |
| first-use remote download | 초기 설정이 작다 | offline 위반, latency, outage, credential/egress·hash race | 거부 |
| Python/JNI를 core에 직접 결합 | 호출 경계가 짧아 보인다 | ABI/GIL/native allocator/lifecycle와 optional dependency 누출 | 거부 |
| self-hosted HTTP service | runtime과 model lifecycle을 격리하고 JVM provider를 얇게 유지한다 | auth/TLS, version skew, payload/backpressure와 운영 비용 | Paddle 조건부 후보 |
| ORT Java direct provider | JVM session lifecycle과 CPU baseline이 명확하다 | native matrix, model semantics, session/RSS 관리가 필요하다 | classification 조건부 후보 |

## 채택 gate와 acceptance checklist

다음 항목을 모두 충족하기 전에는 production dependency나 provider 구현을 시작하지 않는다.

- [ ] manifest schema와 canonical serialization, unknown-field/version 정책이 고정되었다.
- [ ] 모델·label·runtime·native·container 각각의 source, version/revision, size, SHA-256, license, NOTICE/SBOM receipt가 있다.
- [ ] managed cache, no-follow, permission, atomic promote, invalidation, rollback 정책과 negative fixture가 있다.
- [ ] PR no-network fixture가 정상, stale hash, size 초과, missing license, offline, 임의 경로, external-data/custom-op를 검증한다.
- [ ] 외부 응답에 path, URL credential, native/parser cause, image bytes, OCR text가 노출되지 않는다.
- [ ] CPU required, native scheduled, GPU manual/nightly의 지원 범위와 N/A/실패 분류가 문서화되었다.
- [ ] backend별 품질 corpus와 cold/warm latency, p95/p99, RSS, startup, concurrency 기준이 있다.
- [ ] 선택 model update의 license diff, SBOM diff, benchmark diff, rollback receipt가 보존된다.
- [ ] #169/#3 후속 문서가 이 정책을 링크하고, 공통 gate를 다시 약화하지 않는다.

### 이번 issue에서 남기는 검증 목록

이 연구 문서는 실제 model/corpus/native 실행을 주장하지 않는다. 다음 결과가 후속 issue의 증적이 되어야 한다.

| 결과 | 담당 issue | 현재 상태 |
| --- | --- | --- |
| PaddleOCR·Tesseract 동일 corpus CER/WER·geometry·latency/RSS | #544 | 미수행 |
| Paddle service/container digest, offline startup, auth/TLS·limits·no-log | #545 | 미수행 |
| provider-neutral OCR API와 adapter migration plan | #546 | 미작성 |
| Paddle 채택/보류 및 Type-A 범위 | #547 | 미결정 |
| classifier head/labels/preprocess/ONNX manifest와 golden output | #548 | 미수행 |
| ORT CPU native matrix, BOM/consumer, platform·CI evidence | #550 | 미수행 |
| provider-neutral classification API와 ORT module plan | #549 | 미작성 |
| classification 채택/보류 및 Type-A 범위 | #551 | 미결정 |

## 위험과 재평가 조건

- upstream release, model source, license, native ABI가 바뀔 수 있으므로 구현 직전에 exact version과 URL을 다시 조회한다.
- model card의 license가 dataset·extra dependency의 권리를 보장하지 않으므로 catalog entry별 legal review가 필요하다.
- CPU와 GPU의 수치, x64와 ARM64의 native 지원은 서로 대체할 수 없으며 각 platform evidence를 따로 보존한다.
- offline cache는 무결성을 보장하지만 모델의 품질이나 악성 training data를 보장하지 않으므로 corpus·publisher·rollback 검토가 별도로 필요하다.
- native provider가 interrupt를 무시할 수 있으므로 timeout은 hard kill이 아니라 caller 책임 경계로 문서화해야 한다.
- `model name`과 `latest`를 허용하는 편의 계층은 정책을 약화시키므로 별도 resolver 설계와 security review 없이는 추가하지 않는다.

다음 중 하나라도 충족되지 않으면 해당 backend는 0.5.0 이후로 보류한다.

1. exact model/head/labels와 provenance/license/SHA-256/size가 고정되지 않음
2. no-network startup과 verified cache를 재현하지 못함
3. 대표 corpus에서 Tesseract 또는 기존 baseline 대비 품질·운영 이점이 입증되지 않음
4. native/container/platform matrix와 CI 비용·실패 분류가 불명확함
5. public API가 provider/native/path/remote-download 세부사항을 노출함

## 조사 근거와 source-to-claim ledger

### 저장소 기록

- [#169 PaddleOCR backend 평가 연구](2026-08-18-issue-169-paddleocr-backend-evaluation.md)
- [#3 이미지 분류 ML backend 평가 연구](2026-08-18-issue-3-image-classification-ml-backend-evaluation.md)
- [#85 기존 image classification packaging 연구](2026-05-29-issue-85-image-classification-dependency-model-packaging-research.md)
- [이미지 AI 조사 gate lesson](../../lessons/2026-05-29-image-ai-research-gates.md)
- [#543 공통 정책 issue](https://github.com/bluetape4k/bluetape4k-image/issues/543)
- [#513 main epic](https://github.com/bluetape4k/bluetape4k-image/issues/513)

### 공식 primary source

| 주장 | 출처 |
| --- | --- |
| PaddleOCR release, install/runtime, serving, model source/cache | [PaddleOCR releases](https://github.com/PaddlePaddle/PaddleOCR/releases/tag/v3.7.0), [pyproject](https://github.com/PaddlePaddle/PaddleOCR/blob/v3.7.0/pyproject.toml), [installation](https://www.paddleocr.ai/main/en/version3.x/installation.html), [serving](https://www.paddleocr.ai/main/en/version3.x/inference_deployment/serving/serving.html), [model source update](https://www.paddleocr.ai/latest/en/update/update.html), [PaddleX FAQ](https://paddlepaddle.github.io/PaddleX/3.7/FAQ.html) |
| PaddleOCR model size·평가셋 caveat·license | [OCR pipeline](https://www.paddleocr.ai/main/en/version3.x/pipeline_usage/OCR.html), [PaddleOCR license](https://github.com/PaddlePaddle/PaddleOCR/blob/v3.7.0/LICENSE), [PP-OCRv6 detector model card](https://huggingface.co/PaddlePaddle/PP-OCRv6_medium_det_safetensors), [PP-OCRv6 recognizer model card](https://huggingface.co/PaddlePaddle/PP-OCRv6_medium_rec_safetensors) |
| ONNX Runtime Java/session/threading/memory | [ORT v1.29.0 release](https://github.com/microsoft/onnxruntime/releases/tag/v1.29.0), [Java guide](https://onnxruntime.ai/docs/get-started/with-java.html), [Java API](https://onnxruntime.ai/docs/api/java/), [OrtSession](https://onnxruntime.ai/docs/api/java/ai/onnxruntime/OrtSession.html), [RunOptions](https://onnxruntime.ai/docs/api/java/ai/onnxruntime/OrtSession.RunOptions.html), [threading](https://onnxruntime.ai/docs/performance/tune-performance/threading.html), [memory](https://onnxruntime.ai/docs/performance/tune-performance/memory.html) |
| ONNX external-data risk·execution providers | [External data security](https://onnx.ai/onnx/repo-docs/ExternalDataSecurity.html), [execution providers](https://onnxruntime.ai/docs/execution-providers/), [CUDA provider](https://onnxruntime.ai/docs/execution-providers/CUDA-ExecutionProvider.html) |
| LiteRT/DJL 대안 범위 | [LiteRT release](https://github.com/google-ai-edge/LiteRT/releases/tag/v2.2.0), [LiteRT inference](https://ai.google.dev/edge/litert/inference), [LiteRT Android](https://developers.google.com/edge/litert/android), [DJL engines](https://djl.ai/docs/engine.html), [DJL cache](https://djl.ai/docs/development/cache_management.html) |

공식 자료의 version·platform·license는 구현 직전에 다시 검증한다. 이 문서의 판정은 해당 조사일의 evidence에 근거하며, upstream 변경을 자동으로 승인하지 않는다.

## Research DoD

- [x] Issue #543, parent #513, 후속 #169/#3와 현재 범위를 live 상태로 확인했다.
- [x] 두 선행 research와 기존 model packaging lesson을 GNO·로컬 문서에서 재검토했다.
- [x] provenance manifest, offline cache, license/SBOM, 보안 경계, CI tier를 공통 계약으로 고정했다.
- [x] 정상·stale hash·size 초과·offline·license 누락·임의 경로·native 실패 fixture를 acceptance 목록에 포함했다.
- [x] PaddleOCR와 ONNX Runtime의 장점·단점·대안·platform/native 위험을 backend별로 분리했다.
- [x] production code, dependency, model binary, remote download를 변경하지 않았다.
- [ ] 실제 대표 corpus benchmark와 품질 비교
- [ ] 선택 model/runtime/container의 final digest·SBOM receipt
- [ ] native CPU/GPU smoke와 release 환경 증적

최종 상태: **공통 정책 DONE / #169 RESEARCH-1·#3 RESEARCH-2 후속 gate PENDING / 구현 PENDING**
