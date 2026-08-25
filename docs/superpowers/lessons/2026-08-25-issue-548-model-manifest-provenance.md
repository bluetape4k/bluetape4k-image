# #548 이미지 분류 model manifest·provenance lesson

## 배경

Issue #548은 이미지 분류 ONNX 모델의 파일만 고르는 일이 아니라, classifier head,
입력 전처리, 출력 의미, label 순서와 공급망 provenance를 하나의 재현 가능한
계약으로 묶는 연구다. #3의 ORT direct 조건부 채택은 이 계약과 native/CI 검증을
전제로 한다. 이번 Train은 문서와 후보 receipt만 만들며 Kotlin source, dependency,
model bytes, remote downloader를 추가하지 않았다.

## 결정

- `onnx/models`가 문서화한 `resnet50-v1-12.onnx` FP32를 첫 golden 후보로 둔다.
  실제 artifact는 moving branch가 아닌 Hugging Face mirror의 full revision과
  SHA-256으로 식별한다.
- pinned artifact를 research lane에서 ONNX parser로 읽어 IR 4, opset 12,
  `data: float32[N,3,224,224]`, `resnetv17_dense0_fwd: float32[N,1000]`,
  `GlobalAveragePool → Flatten → Gemm`, Softmax node 부재를 확인했다. Softmax와
  deterministic top-k는 repository-owned postprocess 계약으로 남긴다.
- label 파일은 model bytes와 별도 artifact다. 1,000행·byte size·SHA-256·source
  revision을 고정하고, output dimension과 zero-based 순서를 golden fixture로
  다시 확인하기 전에는 완전한 계약으로 승격하지 않는다.
- 저장소/모델 카드의 `Apache-2.0` 표기는 model weight, ImageNet dataset, 변환
  과정과 제3자 NOTICE의 downstream 권리를 대신하지 않는다. license·NOTICE·SBOM·
  provenance/attestation receipt가 없으면 `ADOPT`가 아니라 `PENDING`으로 끝낸다.
- v1은 single-file ONNX만 허용한다. external data, unknown/custom operator,
  remote URL, first-use/background download, mutable cache miss를 fail-closed로
  거부한다.
- 향후 private manifest codec은 중앙 catalog의 Jackson 3를 사용한다. public API에
  Jackson DTO나 `kotlinx.serialization`을 노출하지 않으며, default typing을 켜지
  않는다. 이번 문서에는 dependency 변경이 없다.

## 관찰에서 얻은 교훈

1. **모델 identity는 파일 이름이 아니다.** bytes hash만 고정하면 전처리, output
   logits, labels drift를 감지하지 못한다. model id/version, graph shape, preprocess,
   postprocess, labels와 legal provenance를 함께 version해야 한다.
2. **문서 source와 artifact source를 분리한다.** ONNX Model Zoo Git ref는 계약 설명을
   고정하고, mirror revision은 실제 bytes를 고정한다. Git LFS 제공 정책이 바뀌어도
   moving `main`이나 단순 URL을 receipt로 사용하지 않는다.
3. **repository license와 weight license를 분리한다.** upstream 저장소가
   Apache-2.0이어도 ImageNet terms나 학습 weight redistribution 조건이 자동으로
   해결되지 않는다. 법적 근거가 없는 license를 `PASS`로 추정하지 않는다.
4. **정량 결과보다 그래프 의미가 먼저다.** ResNet FP32에는 Softmax node가 없으므로
   score를 probability로 오해하면 API 결과가 달라진다. output index와 label line,
   tie-break를 fixture로 고정해야 한다.
5. **작은 INT8 파일이 곧 안전한 기본값은 아니다.** QDQ INT8은 별도 runtime matrix가
   필요하고, operator-oriented INT8의 `com.microsoft` node는 custom-op/native
   coupling을 만든다. 크기만으로 FP32 기준 후보를 바꾸지 않는다.

## 재사용할 방어선

- manifest 입력은 full revision, path, byte size, SHA-256, format, opset, tensor
  contract, labels hash, license/NOTICE 상태를 모두 요구한다.
- parser가 확인한 실제 tensor name/shape/domain을 README 요약과 구분하고,
  확인하지 못한 interpolation·crop rounding·golden top-k는 `PENDING`으로 표시한다.
- mirror bytes와 공식 LFS pointer의 size/SHA equality는 retrieval 시각·도구와 함께
  machine-readable receipt로 보존하고, manifest 예시는 canonical JSON Schema ref와
  schemaDigest를 반드시 포함한다.
- managed model root 아래 regular file만 읽고 symlink, `..`, external mount,
  world-writable temp와 external-data를 거부한다.
- cache key를 URL/filename이 아니라 model digest, manifest schema, ORT version,
  execution provider와 architecture의 조합으로 만든다.
- log와 metric에는 model id와 짧은 digest prefix만 남기고 원본 이미지, 전체 labels,
  local path와 bytes를 노출하지 않는다.
- #549 API 설계와 #550 native/CI 연구는 이 manifest를 입력으로 받아야 하며,
  #551 `ADOPT` 전에는 production API·dependency·model 파일을 추가하지 않는다.

## 범위와 미해결 사항

이번 변경은 `research/`, `lessons/`, `reviews/`의 문서와 연구용 schema/receipt
JSON 다섯 파일에 한정한다. Kotlin
source/test, Exposed, coroutine, native binding, dependency catalog, BOM, model
binary, downloader와 public API는 변경하지 않았다. 따라서 `$bluetape-kotlin-patterns`
는 `N/A (Kotlin 변경 0개)`이며, Kotlin 코드 품질을 검증했다는 뜻이 아니다.

후속 작업은 pinned artifact parser receipt, 실제 runtime inference와 golden
logits/top-k, interpolation·crop rounding, label license/NOTICE, model/weight/
dataset provenance, SPDX SBOM·attestation, #544/#563 동일 corpus quality·latency·
RSS benchmark다. 이 항목들이 채워질 때까지 상태는
`RESEARCH_ONLY / ARTIFACT_LICENSE_PENDING / IMPLEMENTATION_BLOCKED`다.

## Writer DoD

- `SPW-01`: PASS — issue·독자·결정·비범위·미해결 gate를 고정했다.
- `SPW-02`: PASS — 모델 identity, parser 결과, label·license·cache 방어선을 재사용 가능한 lesson으로 정리했다.
- `SPW-03`: PASS — 한국어 문장으로 작성하고 API·hash·URL·machine token은 보존했다.
- `SPW-04`: PASS — research 문서의 pinned ref, parser lane, bytes receipt/schema, license 미확정 근거를 연결했다.
- `SPW-05`: PASS — read-back에서 `PENDING`을 adoption PASS로 승격하지 않았다.

최종 상태: `LESSON RECORDED / ADOPTION PENDING`
