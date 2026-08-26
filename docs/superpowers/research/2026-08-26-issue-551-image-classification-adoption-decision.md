# Issue #551 이미지 분류 채택 게이트 결정

## 결정 범위

이 문서는 Main epic #513과 이미지 분류 child epic #3의 마지막 연구 gate다. #543 공통 정책, #548 model manifest, #549 provider-neutral API 경계, #550 ONNX Runtime Java native/platform/BOM/CI 연구를 하나의 adoption decision으로 대조한다.

이번 변경은 문서·검토 artifact만 포함한다. production classifier, ONNX Runtime dependency, model binary, model auto-download, public classification API는 추가하지 않는다.

## 결론 요약

최종 결정은 DEFER다. ONNX Runtime Java direct provider는 서버 JVM에서 재평가할 후보로 남기지만, 현재 증거만으로 library dependency와 public API를 채택하지 않는다.

DEFER는 ORT가 기술적으로 부적합하다는 REJECT가 아니다. 모델·법적 provenance·실제 inference·native/platform·Java 25·BOM·benchmark 증거가 아직 없으므로, 채택 시점과 범위를 뒤로 미루는 결정이다.

기존 images API와 Tesseract/Tess4J OCR baseline에는 영향이 없다. #551 이후에도 classification implementation은 차단 상태로 유지한다.

## 선행 입력과 source-to-decision ledger

| 입력 | 기준 artifact와 commit | 확인한 사실 | 채택 영향 |
|---|---|---|---|
| #543 공통 정책 | PR [#552](https://github.com/bluetape4k/bluetape4k-image/pull/552), docs/superpowers/research/2026-08-19-issue-543-ai-ml-supply-chain-policy.md, merge 83f8a3b888425e4706ab8e0a7d92e4700a6d4868 | model·label·runtime·native·container를 별도 provenance, offline, license/SBOM, 보안, CI gate로 검증한다. | 정책 문서 gate는 PASS이지만 실제 backend 실행 증거는 PENDING이며 adoption을 자동 승인하지 않는다. |
| #548 model manifest | PR [#601](https://github.com/bluetape4k/bluetape4k-image/pull/601), docs/superpowers/research/2026-08-25-issue-548-model-manifest-provenance.md와 schema/receipt JSON, merge 979b45a7865b172c250e199d338e9ad8b1c03732 | ResNet50-v1-12 후보의 bytes·SHA·labels·schema·fail-closed 규칙을 기록했다. model/weight/dataset license, NOTICE, SBOM, attestation과 golden inference는 PENDING이다. | 구체 모델 identity는 후보로 사용할 수 있지만 ADOPT 증거로는 부족하다. |
| #549 API 경계 | PR [#602](https://github.com/bluetape4k/bluetape4k-image/pull/602), docs/superpowers/research/2026-08-25-issue-549-classification-api-boundary.md와 spec/review/plan/lesson, merge 13d3d59c8e68d9a8d63b61ba00e7b0acb07d8e2e | provider-neutral API, 선택 provider module, lifecycle, cancellation, compatibility와 ORT 비노출 경계를 설계했다. 실제 public API/module은 없다. | Type-A 설계 입력은 있으나 implementation authorization은 아니다. |
| #550 runtime 연구 | PR [#603](https://github.com/bluetape4k/bluetape4k-image/pull/603), docs/superpowers/research/2026-08-25-issue-550-ort-native-platform-bom-ci.md와 plan/review/lesson, merge 56967af050630d78d7ac362206052627ae4100e4 | JNI lifecycle, EP, platform matrix, Java 25 consumer, BOM metadata, CI tier, security와 benchmark protocol을 고정했다. 실제 native/runtime/benchmark는 실행하지 않았다. | runtime acceptance가 미완료이므로 ORT dependency를 추가하지 않는다. |

## 채택 기준 평가

| 기준 | 현재 증거 | 판정 |
|---|---|---|
| 모델 identity와 manifest | 단일 ONNX 후보의 source/ref, size, SHA-256, labels, schema와 unknown-field 거부 규칙 | 부분 충족. 실제 license/NOTICE/attestation과 runtime fixture가 없다. |
| preprocessing/postprocessing | input/output shape, labels, logits 의미와 fail-closed 방향을 문서화 | 미충족. crop/interpolation rounding과 golden logits/top-k 재현이 없다. |
| provider-neutral API | ORT/JNI/NDArray를 public API에서 숨기는 설계와 module 경계 | 설계 완료. source/API/ABI/serialization consumer 검증은 구현 후 항목이다. |
| native/runtime | loader, environment/session/result/tensor ownership과 EP 정책 | 설계 완료. exact ORT version native smoke가 없다. |
| platform과 Java 25 | Linux x64 baseline, macOS ARM64·Windows x64 consumer smoke 범위 | 미충족. Java 25 compile/runtime와 platform artifact 증거가 없다. |
| BOM와 dependency metadata | catalog/BOM/generated POM/Gradle metadata/versionless consumer smoke 계약 | 미충족. ORT dependency와 consumer fixture가 없다. |
| 품질·성능 | 동일 corpus, cold/warm p50/p95/p99, RSS, thread/session, concurrency protocol | 미충족. 실제 inference와 비교값이 없다. |
| 공급망·보안 | offline, external-data/custom-op/remote-download/path 정책 | 정책은 PASS. 실제 model·runtime·native SBOM/NOTICE/attestation receipt가 없다. |
| CI 운영 | PR deterministic/no-network, scheduled/native, GPU manual, release tier | CI tier 계획은 PASS. 실제 job·exact-head 결과는 PENDING이다. |

## 선택지와 trade-off

| 선택지 | 장점 | 위험·비용 | 결정 |
|---|---|---|---|
| ADOPT | 다음 Type-A train을 바로 시작하고 ORT Java direct의 JVM 통합을 추진할 수 있다. | 법적 provenance, 실제 정확도, native/platform 호환성, Java 25와 BOM leakage를 증거 없이 승인한다. | 거부 |
| DEFER | 현재 images API와 OCR baseline을 보호하면서 필요한 evidence를 명시적으로 수집한다. | 구현 시점이 늦고 별도 benchmark·native CI 비용이 남는다. | 선택 |
| REJECT | 현재 dependency와 운영 위험을 즉시 제거한다. | 실제 runtime/quality evidence 없이 ORT 자체를 부적합하다고 단정하며 후보 재평가 비용이 커진다. | 선택하지 않음 |

## 품질·성능·운영·보안 판단

### 품질

ResNet50-v1-12의 label count와 tensor shape는 문서 receipt에 있지만, 동일 corpus의 top-k 결과와 preprocessing rounding을 실행으로 확인하지 않았다. 문서 metadata만으로 실제 분류 품질을 주장하지 않는다.

### 성능

약 103MB FP32 후보와 대안의 artifact 크기는 비교했지만, cold/warm latency, p50/p95/p99, RSS, native memory, thread/session 수, 동시성 결과는 없다. benchmark protocol을 결과로 오인하지 않는다.

### 운영

ORT Java는 JNI native resource와 session/result/tensor ownership을 관리해야 한다. Java 25 consumer compile/runtime, Linux x64·macOS ARM64·Windows x64 smoke, CPU/GPU EP 선택과 fallback을 실제로 확인하기 전에는 운영 지원 범위를 선언하지 않는다.

### 보안·공급망

external data, custom op, remote download, arbitrary path, symlink/hardlink와 model replacement를 fail-closed로 제한하는 설계는 유효하다. 그러나 model/weight/dataset/license, NOTICE, SBOM, attestation과 exact runtime/native artifact receipt가 없으면 채택 gate를 통과할 수 없다.

## 재평가 조건

다음 항목을 모두 실제 receipt와 재현 가능한 명령으로 증명할 때만 #551을 다시 연다.

1. model·label·dataset·runtime·native 각각의 source/ref, version, byte size, SHA-256, SPDX/license, NOTICE, SBOM, attestation.
2. canonical manifest schema와 schema digest, unknown-field/duplicate/trailing rejection, stale hash/size/offline/path negative fixture.
3. repo-owned resize/crop/color/normalization과 postprocess/top-k tie-break의 golden logits/top-k fixture. 동일 fixture에서 top-1/top-5 index가 reference와 100% 일치하고, FP32 score 차이는 절대 오차 1e-5 이내여야 한다.
4. exact ORT version의 Linux x64 CPU native smoke와 Java 25 compile/runtime; macOS ARM64와 Windows x64 consumer smoke.
5. catalog/BOM/generated POM/Gradle metadata에서 ORT가 intended provider scope로만 노출되는지와 versionless consumer smoke.
6. no-network PR fixture, scheduled/native matrix, GPU manual/nightly, skipped/old-SHA 의미를 포함한 hosted CI 결과.
7. 동일 corpus의 quality, cold/warm p50/p95/p99, RSS/native memory, thread/session, concurrency와 rollback receipt. 첫 승인 receipt가 host/JVM/model 기준 baseline을 고정하며, 후속 변경은 warm p95 20% 초과 또는 RSS 10% 초과 회귀, error/timeout, top-k 불일치가 발생하면 자동 DEFER와 rollback 대상이다.

## 후속 범위와 기존 API 영향

- 이번 결정은 기존 images API, barcode/OCR, Tesseract/Tess4J baseline을 변경하지 않는다.
- ORT dependency, classification module, public ImageClassifier, model distribution, auto-download와 migration 문서는 만들지 않는다.
- ADOPT가 될 때만 별도 Type-A implementation issue와 stacked PR train을 생성한다.
- DEFER 기간에는 #3의 implementation checkbox를 열지 않고, Main epic #513은 PaddleOCR child epic #169의 남은 gate 때문에 열린 상태로 둔다.

## 근거 링크

- [Issue #551](https://github.com/bluetape4k/bluetape4k-image/issues/551)
- [Issue #543](https://github.com/bluetape4k/bluetape4k-image/issues/543)
- [Issue #548](https://github.com/bluetape4k/bluetape4k-image/issues/548)
- [Issue #549](https://github.com/bluetape4k/bluetape4k-image/issues/549)
- [Issue #550](https://github.com/bluetape4k/bluetape4k-image/issues/550)
- [Main epic #513](https://github.com/bluetape4k/bluetape4k-image/issues/513)

## Research DoD

- SPW-01: PASS — #543/#548/#549/#550의 commit, artifact, 독자, 결정 질문과 미확정 claim을 고정했다.
- SPW-02: PASS — source ledger, 기준 평가, trade-off, 재평가 조건, 후속 범위와 API 영향 경계를 포함했다.
- SPW-03: PASS — 한국어 기술 문체로 작성하고 issue·PR·SHA·URL·API token을 보존했다.
- SPW-04: PASS — 네 선행 artifact와 live merge commit을 결론에 연결했으며 PENDING을 채택 PASS로 승격하지 않았다.
- SPW-05: PASS — 독립 reviewer 결과를 반영한 7-Tier review·lesson을 작성하고 최종 Markdown/read-back을 완료했다.

최종 결정: DEFER / IMPLEMENTATION_BLOCKED / REASSESSMENT_REQUIRED
