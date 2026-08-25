# Issue #548 이미지 분류 모델 manifest·provenance 연구

- Epic: #513 AI/ML backend 연구 train
- 하위 epic: #3 이미지 분류 ONNX provider
- Train 단계: RESEARCH-2
- 조사일: 2026-08-25
- 변경 유형: Type-E 문서 연구
- 결정 범위: 구체적인 모델 후보와 manifest·provenance 계약을 고정한다. production
  API, runtime dependency, 모델 바이너리, 자동 다운로드는 이번 문서에서 승인하지 않는다.

## 결정 요약

Issue #548의 첫 기준 후보는 ONNX Model Zoo가 문서화한
`resnet50-v1-12.onnx`로 기록한다. 모델 문서의 Git commit은 계약 설명을 고정하는
용도로 사용하고, 실제 artifact는 immutable Hugging Face mirror revision에서
가져온다. 이 분리는 Git LFS 제공 정책이 바뀌어도 문서 ref와 실제 bytes를 혼동하지
않게 한다. 다만 이 후보는 `RESEARCH_ONLY`이며, 모델 weight와 ImageNet 데이터셋의
downstream license·NOTICE·SBOM·attestation을 모두 확인하기 전에는 `ADOPT`로
올리지 않는다.

| 항목 | 연구 결론 |
|---|---|
| 기준 모델 | `resnet50-v1-12.onnx`, fp32, ImageNet 1000-class ResNet-50 후보 |
| 문서 기준 | `onnx/models` commit `4f43949841cb55a0b98dc8fcd045431ccafd9f96` |
| artifact 기준 | `onnxmodelzoo/resnet50-v1-12` revision `1f95315d8bd3b3ca2ceabe54d274e0cdf5a83bbe` |
| 모델 파일 | 102,576,593 bytes, SHA-256 `3f03fdef724b22947eed826f1eef1dc5c34151bb4c37d634f1db89dfa2dd1526` |
| labels | pinned `synset.txt`, 1,000행, SHA-256 `acf75ef0abe89694b19056e0796401068b459c457baa30335f240c7692857355` |
| ONNX 의미 | opset 12, 입력 `N×3×H×W`, 출력 1,000개 score, H/W 최소 224 |
| 배포 정책 | single-file ONNX만 허용; external data·custom op·remote URL·first-use download 거부 |
| 현재 상태 | `RESEARCH_ONLY / ARTIFACT_LICENSE_PENDING / IMPLEMENTATION_BLOCKED` |

## 왜 모델 파일만으로는 충분하지 않은가

ONNX 파일은 계산 그래프를 담지만, 호출자가 기대하는 분류 결과의 의미를 모두
고정하지는 않는다. 다음 정보가 달라지면 같은 이미지와 같은 runtime에서도 class id,
score, top-k가 달라질 수 있다.

- RGB/BGR 순서와 색상 범위
- 짧은 변을 256으로 맞춘 뒤 224 중앙 crop을 적용하는지 여부
- resize 보간법과 crop 좌표의 반올림 규칙
- mean/std와 `0..255` 또는 `0..1` scale
- NCHW/NHWC, batch 차원, tensor 이름과 dtype
- logits를 그대로 정렬하는지, softmax를 먼저 적용하는지
- label 파일의 행 순서와 결과 tie-break

따라서 model identity는 파일 이름이 아니라 **모델 bytes + 입력 전처리 + 출력
후처리 + labels + provenance**의 불변 묶음이어야 한다. #549 API 설계는 이 묶음을
provider-neutral한 identity로 참조하고, ORT의 `OrtSession`이나 native path를 public
signature에 노출하지 않아야 한다.

## 기준 후보의 확인된 계약

ONNX Model Zoo의 ResNet README, immutable mirror artifact, 그리고 연구 reviewer lane의
ONNX parser 확인을 기준으로 다음 값을 기록한다. GitHub ref는 문서·provenance를
고정하고, model bytes는 mirror ref의 SHA-256을 최종 식별자로 사용한다.

| 계약 | 값 | 근거 |
|---|---|---|
| 모델 파일 | `resnet50-v1-12.onnx` | [pinned model pointer](https://github.com/onnx/models/blob/4f43949841cb55a0b98dc8fcd045431ccafd9f96/validated/vision/classification/resnet/model/resnet50-v1-12.onnx) |
| artifact mirror | `onnxmodelzoo/resnet50-v1-12` @ `1f95315d8bd3b3ca2ceabe54d274e0cdf5a83bbe` | [pinned Hugging Face artifact](https://huggingface.co/onnxmodelzoo/resnet50-v1-12/blob/1f95315d8bd3b3ca2ceabe54d274e0cdf5a83bbe/resnet50-v1-12.onnx) |
| 파일 형식 | `format=onnx`, `onnxVersion=1.7.0` | [ResNet README](https://github.com/onnx/models/blob/4f43949841cb55a0b98dc8fcd045431ccafd9f96/validated/vision/classification/resnet/README.md) |
| IR version | 4 | pinned artifact를 `onnx==1.20.0` parser로 확인한 연구 lane 결과 |
| opset | 12 | 같은 README의 model information |
| 입력 | `data: float32[N,3,224,224]` | parser 확인; README는 H/W 최소 224로 설명 |
| resize/crop | shortest side 256, center crop 224 | 같은 README의 pre-processing |
| 정규화 | mean `255×[0.485, 0.456, 0.406]`, std `255×[0.229, 0.224, 0.225]` | 같은 README의 pre-processing |
| head | `GlobalAveragePool → Flatten → Gemm` | pinned artifact를 parser로 확인한 연구 lane 결과 |
| 출력 | `resnetv17_dense0_fwd: float32[N,1000]`, Softmax node 없음 | parser 확인; 확률 변환은 repo-owned postprocess 책임 |
| labels | 1,000행, zero-based output index 순서 후보 | [pinned ORT example labels](https://github.com/microsoft/onnxruntime-inference-examples/blob/978efc89bdfb43aec001677d9344355e896c9ca0/c_sharp/image_classification/model/synset.txt) |
| postprocess | logits에 softmax를 적용한 뒤 deterministic top-k | README 설명과 parser의 Softmax node 부재를 결합한 설계 |
| 모델 크기 | README 표기 97.8MB; 실제 object 102,576,593 bytes | README 및 LFS object 확인 |

README는 저장소와 예제의 Apache-2.0 표기를 제공하지만, 그 사실만으로 학습 weight,
ImageNet 데이터셋, 변환 과정, 제3자 NOTICE의 downstream 사용권을 보증하지 않는다.
따라서 이 문서에서는 license를 `candidate metadata`로 기록하고, `ADOPT` gate의
license·NOTICE·SBOM·attestation은 미완료로 유지한다.

## 제안 manifest 계약

manifest는 애플리케이션이 준비한 로컬 artifact를 검증할 때 사용한다. 구현 시
canonical JSON codec은 bluetape4k 의존성 catalog가 제공하는 Jackson 3를
implementation-only/private boundary에서 사용한다. 이 연구는 dependency를
추가하지 않으며, public API에 Jackson DTO나 `kotlinx.serialization` 의존성을
노출하지 않는다. Jackson 3의 default typing은 사용하지 않고, 알 수 없는 필드와
버전은 fail-closed 정책으로 처리한다.

```json
{
  "manifestVersion": 1,
  "schemaRef": "docs/superpowers/research/2026-08-25-issue-548-model-manifest-schema.json",
  "schemaDigest": "5481f4d8a1238e58a3d07d55c0b88c06d171bc41ba71084b9a1a8ae86dae7889",
  "modelId": "onnx-model-zoo/resnet50-v1-12",
  "modelVersion": "resnet50-v1-12",
  "source": {
    "repository": "https://huggingface.co/onnxmodelzoo/resnet50-v1-12",
    "ref": "1f95315d8bd3b3ca2ceabe54d274e0cdf5a83bbe",
    "path": "resnet50-v1-12.onnx",
    "documentationRef": "https://github.com/onnx/models/tree/4f43949841cb55a0b98dc8fcd045431ccafd9f96/validated/vision/classification/resnet",
    "retrievedAt": "2026-08-25"
  },
  "artifact": {
    "format": "onnx",
    "bytes": 102576593,
    "sha256": "3f03fdef724b22947eed826f1eef1dc5c34151bb4c37d634f1db89dfa2dd1526",
    "externalData": false,
    "customOperators": false
  },
  "onnx": {
    "onnxVersion": "1.7.0",
    "irVersion": 4,
    "opset": 12,
    "inputs": [{"name": "data", "dtype": "float32", "shape": ["N", 3, 224, 224]}],
    "outputs": [{"name": "resnetv17_dense0_fwd", "dtype": "float32", "shape": ["N", 1000], "kind": "logits"}],
    "head": ["GlobalAveragePool", "Flatten", "Gemm"]
  },
  "preprocess": {
    "color": "RGB",
    "resizeShortSide": 256,
    "crop": {"kind": "center", "height": 224, "width": 224},
    "layout": "NCHW",
    "scale": 1.0,
    "mean": [123.675, 116.28, 103.53],
    "std": [58.395, 57.12, 57.375],
    "interpolation": "UNSPECIFIED_PENDING_IMPLEMENTATION_FIXTURE"
  },
  "labels": {
    "source": "https://github.com/microsoft/onnxruntime-inference-examples/blob/978efc89bdfb43aec001677d9344355e896c9ca0/c_sharp/image_classification/model/synset.txt",
    "ref": "978efc89bdfb43aec001677d9344355e896c9ca0",
    "bytes": 31675,
    "count": 1000,
    "sha256": "acf75ef0abe89694b19056e0796401068b459c457baa30335f240c7692857355"
  },
  "output": {
    "kind": "logits",
    "topK": 5,
    "tieBreak": "ascending_class_index"
  },
  "license": {
    "repository": "Apache-2.0",
    "modelWeights": "PENDING",
    "dataset": "PENDING",
    "notice": "PENDING"
  }
}
```

위 JSON은 **계약 예시**이지 최종 release receipt가 아니다. canonical field와
fail-closed unknown-field 정책은 [v1 JSON Schema](2026-08-25-issue-548-model-manifest-schema.json)에
고정하고 schema bytes의 SHA-256을 `schemaDigest`로 함께 기록한다. 실제 artifact
동일성은 [machine-readable receipt](2026-08-25-issue-548-model-manifest-receipt.json)에
남긴다. 연구 reviewer lane은 `onnx==1.20.0`으로 IR·tensor·head·custom domain·Softmax node를 확인했지만,
실제 runtime의 golden logits와 top-k fixture를 실행하지 않았다. README의 전처리
표기에는 보간법과 crop 반올림이 명시되지 않았으므로 `interpolation`은 여전히
placeholder다. mean/std는 README의 0..255 scale을 수치로 펼친 값이며, 구현에서
0..1 scale을 사용할 경우 동일한 변환을 수식과 fixture로 다시 고정해야 한다.

### 필수 검증 규칙

1. `manifestVersion`과 schema digest를 먼저 확인한다. 지원하지 않는 버전이나
   unknown field는 무시하지 말고 거부한다.
2. 경로는 manifest가 지정한 managed model root 아래의 regular file만 허용한다.
   symlink, `..`, 외부 mount, world-writable 임시 경로는 거부한다.
3. 파일 byte size와 SHA-256을 모두 계산해 manifest와 일치시킨다. URL, 파일 이름,
   Git LFS pointer의 hash만으로는 실제 model bytes를 승인하지 않는다.
4. single-file ONNX만 허용한다. external-data tensor, custom operator, embedded
   native code, remote URL, mutable tag, first-use/background download를 거부한다.
5. labels는 UTF-8 텍스트의 줄 수·순서·SHA-256을 함께 확인하고 output class 수와
   정확히 일치시킨다. 중복·빈 label·후행 줄바꿈 정책도 fixture로 고정한다.
6. input/output 이름·dtype·rank·batch·shape·opset은 parser 결과와 manifest를
   대조한다. README의 요약만으로 parser 검증을 대신하지 않는다.
7. 전처리와 후처리는 repository-owned 코드와 golden fixture로 고정한다. 최소한
   이미지 bytes hash, crop 결과 hash, top-k class/rank/score tolerance를 기록한다.
8. cache key는 URL이나 파일 이름이 아니라 model SHA-256, manifest version, ORT
   version, execution provider와 architecture의 조합으로 만든다.
9. license·NOTICE·SBOM·provenance/attestation receipt가 없으면 `ADOPT`가 아니라
   `PENDING`으로 종료한다. 사람이 내용을 추정해 보완하지 않는다.
10. model bytes, 원본 이미지, 전체 labels를 일반 log/metric label에 넣지 않는다.
    검증 실패에는 model id와 짧은 digest prefix만 남기고 경로·비밀·이미지 내용을
    노출하지 않는다.

## 후보와 대안 비교

| 선택지 | 장점 | 단점·위험 | 이번 판정 |
|---|---|---|---|
| ResNet50-v1-12 FP32 단일 ONNX | 공개된 1000-class head와 preprocessing 설명, parser로 표준 node·logit output 확인, 재현 가능한 mirror bytes | 약 103MB artifact, weight/dataset license 미확정, 실제 inference fixture 미완료 | 기준 후보, `RESEARCH_ONLY` |
| ResNet50-v1-12 QDQ INT8 | 약 25.8MB, custom node 없이 표준 node 후보 | 추가 opset import와 ORT provider matrix가 필요 | 조건부 benchmark 대안 |
| ResNet50-v1-12 operator INT8 | 작은 파일과 hardware-specific 성능 후보 | `com.microsoft` custom op가 있어 native/runtime coupling이 생김 | 기본 정책상 거부 |
| ResNet18 또는 MobileNet | 작은 CPU baseline, native CI와 consumer smoke 비용 감소 | 정확도·preprocess·head가 후보마다 달라 별도 receipt 필요 | benchmark 대안 |
| EfficientNet 계열 | 정확도/효율 trade-off 후보 | export 버전·opset·preprocess와 license를 다시 고정해야 함 | 후속 조사 |
| Torchvision multi-weight | weight metadata와 preprocessing API를 함께 제공 | PyTorch export와 ONNX artifact provenance를 별도로 연결해야 함 | 대안 source |
| library JAR에 weight 번들 | 호출자 설정이 단순 | artifact 크기, license, 업데이트, 취약 model 교체가 library release에 결합 | 거부 |
| caller-managed local path | offline·재현성·공급망 경계가 명확 | 사용자가 model receipt와 배포를 책임짐 | v1 권장 |
| verified resolver | cache와 mirror 정책을 공통화 | 다운로드 권한, locking, eviction, egress와 mirror 신뢰가 추가됨 | 후속 Type-A |

### 모델 선택에 대한 결론

ResNet50-v1-12는 “좋은 모델”이라는 품질 판정이 아니라 **계약을 검증할 수 있는
첫 기준 자료**다. #551 adoption gate에서 실제 license/NOTICE, 동일 corpus 품질,
CPU latency/RSS, native CI와 SBOM/attestation을 비교한 뒤 ResNet50을 채택하거나
더 작은 모델로 교체한다. DINOv2 같은 backbone-only checkpoint를 ImageNet
classifier로 부르는 것은 head와 labels가 별도로 정해지기 전까지 허용하지 않는다.

## 보안·운영 위험과 완화

| 위험 | 중단 조건 | 완화 |
|---|---|---|
| mutable URL/tag가 다른 bytes를 반환 | source ref 또는 SHA 불일치 | pinned commit, byte SHA-256, size를 동시에 확인 |
| Git LFS pointer를 model로 저장 | 실제 bytes 크기와 pointer가 불일치 | LFS pointer의 OID와 다운로드한 object hash를 별도 기록 |
| external-data path traversal | model이 둘 이상의 파일을 요구 | v1 single-file 거부, managed root realpath 검사 |
| custom op가 native code를 실행 | parser/session이 허용 목록 밖 op를 발견 | v1 거부; allowlist는 별도 threat review 후 도입 |
| license·NOTICE 누락 | 모델/weight/dataset/변환 provenance 중 하나가 미확정 | `PENDING` 유지, release receipt와 SBOM/attestation 확보 전 adoption 금지 |
| label 순서 drift | label count/hash 또는 class id 불일치 | labels 자체를 hash하고 output dimension과 exact equality 검사 |
| preprocessing drift | crop/normalization/golden top-k 불일치 | repo-owned fixture와 tolerance, schema version으로 차단 |
| first-use network와 캐시 오염 | runtime이 원격 URL을 요청하거나 unmanaged path를 사용 | URL을 public API에서 제거하고 caller-managed offline path만 허용 |
| native memory/thread 폭주 | RSS/thread/session limit 초과 | bounded session, Java 25 CPU smoke, p50/p95/p99 및 RSS 측정 |

## 후속 train과 acceptance gate

이번 문서는 #548의 연구 산출물이다. 다음 순서를 바꾸지 않는다.

1. #548 문서에서 model/head/labels/preprocess/manifest 경계를 확정한다.
2. #549에서 provider-neutral API와 ONNX module의 public/private 경계를 설계한다.
3. #550에서 ONNX Runtime Java native artifact, Java 25, CPU matrix, BOM/CI를
   검증한다. #549와 #550은 #548의 manifest 계약을 입력으로 삼는다.
4. #551에서 license·SBOM·attestation·동일 corpus·성능·운영 acceptance를 판정한다.
5. #551이 `ADOPT`를 반환하기 전에는 model file, ORT dependency, public API,
   auto-download를 production branch에 추가하지 않는다.

필수 후속 산출물은 다음과 같다.

- pinned model을 parser로 읽은 immutable manifest receipt
- 실제 output tensor name/shape/dtype와 logits/probability 판정
- interpolation, crop rounding, label newline 정책을 포함한 golden fixture
- model/weight/dataset license와 NOTICE의 명시적 근거
- SPDX SBOM 및 provenance/attestation의 subject가 model SHA-256을 포함하는 receipt
- #544/#563 corpus와 동일한 classifier 품질·latency·RSS benchmark

## 조사 원장

| 근거 | 확인 내용 |
|---|---|
| [ONNX Model Zoo ResNet README](https://github.com/onnx/models/blob/4f43949841cb55a0b98dc8fcd045431ccafd9f96/validated/vision/classification/resnet/README.md) | `onnxVersion=1.7.0`, opset 12, 입력·전처리·출력·1000 labels 요약 |
| [pinned model pointer](https://github.com/onnx/models/blob/4f43949841cb55a0b98dc8fcd045431ccafd9f96/validated/vision/classification/resnet/model/resnet50-v1-12.onnx) | Git LFS OID `3f03fdef…`, size 102,576,593 bytes |
| [pinned mirror artifact](https://huggingface.co/onnxmodelzoo/resnet50-v1-12/blob/1f95315d8bd3b3ca2ceabe54d274e0cdf5a83bbe/resnet50-v1-12.onnx) | mirror revision과 실제 bytes SHA/size equality receipt. moving `main`을 사용하지 않음 |
| [artifact receipt](2026-08-25-issue-548-model-manifest-receipt.json) | retrieval 시각, 도구, bytes/SHA, LFS pointer equality, labels와 license 상태를 기계 판독 형식으로 보존 |
| [manifest schema](2026-08-25-issue-548-model-manifest-schema.json) | `additionalProperties=false`, version/schemaDigest, required fields와 fail-closed shape |
| [pinned synset](https://github.com/microsoft/onnxruntime-inference-examples/blob/978efc89bdfb43aec001677d9344355e896c9ca0/c_sharp/image_classification/model/synset.txt) | 31,675 bytes, 1,000 lines, SHA-256 `acf75ef0…`; label source license는 별도 확인 필요 |
| [ONNX Model Zoo root](https://github.com/onnx/models/tree/4f43949841cb55a0b98dc8fcd045431ccafd9f96) | repository license와 문서 source ref. 모델 weight·dataset license의 충분조건은 아님 |
| [ONNX Model Zoo mirror](https://huggingface.co/onnxmodelzoo/models) | GitHub LFS 정책 변화에 대비한 immutable artifact mirror 선택지 |
| [ONNX Runtime image-classification example](https://github.com/microsoft/onnxruntime-inference-examples/blob/978efc89bdfb43aec001677d9344355e896c9ca0/mobile/examples/image_classification/android/app/src/main/java/ai/onnxruntime/example/imageclassifier/MainActivity.kt#L131-L162) | output index를 label list index로 매핑하는 예시 |
| [ONNX external data security](https://onnx.ai/onnx/repo-docs/ExternalDataSecurity.html) | external-data를 별도 보안 경계로 취급해야 하는 근거 |
| [#3 image classification research](2026-08-18-issue-3-image-classification-ml-backend-evaluation.md) | ORT direct 조건부 채택, API/provider 및 native CI 선행 gate |
| [#543 supply-chain policy](2026-08-19-issue-543-ai-ml-supply-chain-policy.md) | fail-closed cache, license, SBOM, attestation 공통 정책 |

### 조사 한계

leader 환경에는 Python `onnx` parser가 없었지만, 독립 research lane이 pinned
artifact를 임시 경로에서 받아 `onnx==1.20.0`으로 구조 검사를 수행했다. 세 후보의
`check_model(full_check=True)`와 external-data 부재, FP32/QDQ의 custom-domain 검사,
INT8의 `com.microsoft:QLinearAdd` 거부를 확인했으며, 실제 bytes·labels는
repository에 복사하지 않았다. 이 결과는 lane 보고서에 남은 연구 증거이지 CI
receipt가 아니다. runtime inference, golden logits/top-k, interpolation·crop
rounding, model/weight/dataset license와 NOTICE/SBOM/attestation은 여전히
`PENDING`이다. 이 한계를 숨기지 않는 것이 이번 연구의 공급망 경계다.

## Issue #548 acceptance mapping

| Issue 요구 | 결과 |
|---|---|
| 구체 model/head/input normalization/resize/crop/labels/output ordering | 기준 후보와 manifest/schema 필드로 고정; runtime fixture 세부는 PENDING |
| source URL/version/license/size/SHA/opset/shape/label count/provenance | pinned source·size·SHA·README 계약을 기록; model/weight/dataset license와 attestation은 PENDING |
| single-file ONNX v1, external-data/custom-op/remote download 거부 | 계약과 fail-closed 규칙으로 고정 |
| repo-owned preprocess/postprocess와 golden fixture | 필요 산출물과 acceptance gate로 고정; 이번 변경에서 fixture를 만들지 않음 |
| implementation/dependency/model 금지선 | production 변경 0개, Jackson3 권고만 문서화 |

최종 판정은 `PASS — #548 research artifact complete / model adoption and Type-A
implementation pending`이다. 이는 모델을 채택했다는 뜻이 아니며, #549·#550 설계와
#551 adoption gate가 이 문서의 미완료 receipt를 해결해야 한다.

## Writer DoD

- `SPW-01`: PASS — #548·#3·#513의 독자, 연구 목적, 기준 후보, 비범위와 한계를 고정했다.
- `SPW-02`: PASS — manifest schema, fail-closed 검증, 대안, 후속 acceptance를 연결했다.
- `SPW-03`: PASS — 한국어 기술 문체를 사용하고 code/API/URL/hash는 그대로 보존했다.
- `SPW-04`: PASS — 문서 ref, mirror revision, LFS object, parser lane 결과, labels hash와 공식 URL을 조사 원장에 남겼다.
- `SPW-05`: PASS — 문서 read-back에서 placeholder·PENDING을 채택 PASS로 승격하지 않았다.

최종 상태: `RESEARCH_ONLY / ARTIFACT_LICENSE_PENDING / IMPLEMENTATION_BLOCKED`
