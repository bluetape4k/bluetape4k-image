# Issue #550 후속 실행 계획: ORT Java native·platform·BOM·CI

- 대상: [#550](https://github.com/bluetape4k/bluetape4k-image/issues/550)
- 선행: [#548 model manifest](https://github.com/bluetape4k/bluetape4k-image/issues/548), [#549 provider-neutral API](https://github.com/bluetape4k/bluetape4k-image/issues/549)
- 채택 gate: [#551](https://github.com/bluetape4k/bluetape4k-image/issues/551)
- 기준: `develop` @ `13d3d59c8e68d9a8d63b61ba00e7b0acb07d8e2e`
- 계획 유형: Type-E research/design → 후속 Type-A stacked train
- 현재 실행: 이 문서 train에서는 아래 PR을 만들지 않고 설계·검증 순서만 고정한다.

## 실행 규칙

1. #551이 `ADOPT`를 기록하기 전에는 ORT dependency, module, model bytes, public
   classifier API, native CI job을 변경하지 않는다.
2. 각 PR은 이전 PR의 exact head를 base로 삼고, 한 PR의 실패가 앞선 API 문서나
   provider-neutral contract를 오염시키지 않도록 되돌릴 수 있게 유지한다.
3. native/JNI/host check는 순차 실행한다. skipped, unavailable, old-SHA CI는
   exact-head PASS로 해석하지 않는다.
4. dependency/BOM metadata와 public API leakage는 native benchmark보다 먼저
   확인한다. consumer가 잘못된 graph를 resolve한 채 native 결과만 green이 되는
   상황을 막는다.
5. 모든 PR body는 한국어, linked issue·milestone·assignee·labels를 live state에서
   확인하고 `## DoD Status`로 끝낸다.

## Stacked PR train

### PR-A — exact dependency·model compatibility receipt

- **Base:** `develop`
- **목적:** 선택한 ORT CPU version과 #548 manifest의 model IR/opset/labels/
  preprocessing/postprocessing/provenance를 한 compatibility receipt로 고정한다.
- **변경 후보:** version catalog 초안, checked-in tiny fixture, provenance/sha
  receipt, 문서와 테스트 helper. public API는 아직 추가하지 않는다.
- **필수 검증:** Maven POM/Gradle metadata, license(MIT 여부 포함), artifact
  SHA-256, Java 25 dependency resolution, model/manifest digest와 corrupted-model
  negative fixture.
- **중단 조건:** license/SBOM/provenance가 없거나 Java 25 resolve가 불안정하거나
  model opset이 runtime과 맞지 않으면 PR-A를 `DEFER`하고 후속 PR을 만들지 않는다.
- **rollback:** catalog/fixture만 revert하며 기존 image artifact에는 dependency를
  전파하지 않는다.

### PR-B — provider module native lifecycle

- **Base:** PR-A exact head
- **목적:** #549에서 승인한 provider-neutral API 뒤에 ORT provider를 붙이고 CPU
  native loader/session lifecycle을 구현한다.
- **변경 후보:** 별도 `bluetape4k-images-classification-onnxruntime` module,
  `OrtEnvironment`/`SessionOptions`/session/result/tensor ownership, sanitized
  error mapping, single-file model guard.
- **필수 검증:** Linux x64 CPU load/run/close, session reuse, close race,
  cancellation observation, `NATIVE_RUNTIME_UNAVAILABLE`, `MODEL_CORRUPT`,
  external-data/custom-op rejection.
- **중단 조건:** native library path가 host에 의존하거나 session/result/tensor
  cleanup이 모든 exception path에서 증명되지 않으면 provider를 publish graph에
  넣지 않는다.
- **rollback:** provider module만 revert하고 API/fake fixture는 유지한다.

### PR-C — catalog/BOM/generated metadata/consumer smoke

- **Base:** PR-B exact head
- **목적:** catalog alias, image BOM constraint, published metadata와 versionless
  consumer resolution을 일치시킨다.
- **변경 후보:** `gradle/libs.versions.toml`, `bom/build.gradle.kts`, publication
  inventory, Java 25 consumer smoke, APIElements/runtimeClasspath checks.
- **필수 검증:** BOM이 exact version을 resolve하고 ORT가 API artifact로 누출되지
  않으며 Jackson 3가 implementation-only인지 generated POM와 Gradle metadata로
  확인한다.
- **중단 조건:** API consumer가 ORT/JNI/native type을 transitively 받거나 BOM과
  catalog version이 다르면 PR-C를 merge하지 않는다.
- **rollback:** BOM/catalog/metadata 변경을 함께 revert한다.

### PR-D — CI matrix와 native smoke

- **Base:** PR-C exact head
- **목적:** PR deterministic lane, Linux x64 CPU native lane, macOS ARM64/Windows
  x64 consumer smoke, GPU manual/nightly lane을 분리한다.
- **변경 후보:** path filter, required checks, scheduled workflow, native flags,
  runner matrix와 sanitized receipt artifact.
- **필수 검증:** changed path가 job을 놓치지 않음, CPU fallback이 GPU PASS로
  합쳐지지 않음, unavailable/skipped가 명시됨, native checks sequential.
- **중단 조건:** docs-only/old-SHA/skip을 exact-head PASS로 보고하거나 GPU
  environment가 CPU fallback을 숨기면 CI 변경을 차단한다.
- **rollback:** workflow-only 변경을 provider code와 분리해 revert한다.

### PR-E — example·benchmark·adoption follow-up

- **Base:** PR-D exact head와 #551 `ADOPT`
- **목적:** 명시적 provider injection, 동일 corpus benchmark, reader-facing manual
  문서를 별도 train으로 추가한다.
- **변경 후보:** Spring/Ktor example, cold/warm latency/RSS/native memory/
  concurrency benchmark, README/manual의 versionless consumer setup.
- **필수 검증:** provider silent fallback 없음, manifest/license/SBOM/NOTICE와
  benchmark receipt identity 일치, quality와 performance를 별도 보고.
- **중단 조건:** #551이 `DEFER`/`REJECT`이면 integration을 추가하지 않고 research
  상태를 유지한다.
- **rollback:** example/benchmark를 revert해도 API/provider artifact는 독립적으로
  되돌릴 수 있어야 한다.

## 의존성 그래프와 gate

```text
#548 manifest/provenance
          ↓
#549 API/provider boundary
          ↓
#550 native/platform/BOM/CI research (현재 문서)
          ↓
#551 ADOPT / DEFER / REJECT
          ↓ ADOPT only
PR-A → PR-B → PR-C → PR-D → PR-E
```

- `DEFER`: native/platform/BOM/benchmark gap issue를 열고 구현을 멈춘다.
- `REJECT`: ORT provider와 관련 dependency를 추가하지 않고 대안을 별도 research로
  보낸다.
- `ADOPT`: PR-A부터 순서대로 exact head와 fresh CI/review evidence를 만든다.

## 검증 체크리스트

### 문서 단계 (#550 현재)

- [x] official ORT Java/API/EP/security/Maven source ledger
- [x] repository source ledger와 변경 범위 증명
- [x] Linux x64/macOS ARM64/Windows x64/GPU matrix
- [x] Java 25, BOM, license/SHA/provenance, consumer smoke acceptance
- [x] external-data/custom-op/path traversal/error sanitization policy
- [x] cold/warm/RSS/native memory/concurrency benchmark protocol
- [ ] actual native runtime and benchmark — #551 뒤 후속

### Type-A 실행 단계

- [ ] exact ORT version/manifest receipt and license/SBOM
- [ ] CPU native session lifecycle and negative fixtures
- [ ] APIElements/POM/BOM/versionless Java 25 consumer
- [ ] required PR and scheduled/native/GPU CI matrix
- [ ] independent reviewer per PR and exact-head merge gate
- [ ] #551 adoption decision and release-facing documentation

## 위험과 완화

| 위험 | 조기 신호 | 완화 |
|---|---|---|
| Java 25와 jar/native ABI 불일치 | compile은 되지만 class load/native load 실패 | Java 25 consumer compile/runtime를 PR-C 이전 required evidence로 둔다. |
| macOS ARM64 artifact 부재 | Maven resolution 또는 architecture mismatch | 지원을 선언하지 않고 consumer smoke gap을 기록하거나 DEFER한다. |
| EP fallback 은닉 | GPU 요청이 CPU로 실행되고 green | active EP와 fallback을 receipt에 기록하고 provider mismatch를 실패시킨다. |
| native memory leak | heap은 안정적이나 RSS 증가 | Result/tensor/pinned output close와 RSS/session bound를 benchmark에 포함한다. |
| BOM/API leakage | API consumer가 ORT를 transitive resolve | APIElements, runtimeClasspath, generated POM을 같이 검사한다. |
| 악성 model sidecar/custom op | external path 또는 native library가 읽힘 | external data/custom op/remote download를 기본 거부한다. |
| CI coverage 착시 | docs-only/old SHA/skip이 green | path/filter/sha/scope를 receipt에 기록하고 skipped를 PASS로 합치지 않는다. |

## DoD

- `SPW-01`: PASS — PR-A~E의 목적, base, 산출물, 중단 조건, rollback을 고정했다.
- `SPW-02`: PASS — dependency, native lifecycle, BOM, CI, benchmark와 adoption gate를
  ordered train으로 연결했다.
- `SPW-03`: PASS — 한국어 계획 문체와 exact project/module/command/token을 보존했다.
- `SPW-04`: PASS — #548/#549/#551 및 repository rules와 충돌하지 않음을 확인했다.
- `SPW-05`: PASS — 실제 implementation/native/benchmark는 아직 실행하지 않는다는
  경계를 명시했다.

최종 상태: `PLAN READY / TYPE-A BLOCKED BY #551 / NATIVE EVIDENCE PENDING`
