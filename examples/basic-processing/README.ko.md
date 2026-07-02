# 기본 이미지 처리 Quickstart

[English](./README.md) | 한국어

`bluetape4k-images`만 사용하는 순수 JVM 이미지 처리 실행 예제입니다. 자연스러운
사진 fixture인 `cafe.jpg`, `landscape.jpg`와 루트 README 대표 이미지
`docs/images/image-workbench.png`를 사용하고, 기본 출력은 `build/tmp/basic-processing`
아래에 생성합니다.

## 보여주는 내용

- 압축 이미지 파일 전체를 직접 `ByteArray`로 복사하지 않고 file-backed resource에서 로드
- 제한된 크기의 썸네일 생성
- landscape 사진을 정확한 16:9 크기로 smart crop
- JPEG 입력을 PNG 출력으로 변환
- 간단한 텍스트 워터마크 추가
- 루트 README 대표 이미지를 16:9 preview 출력으로 재사용
- suspend-aware `bluetape4k-images` writer로 인코딩 결과 저장
- 무거운 ML runtime이나 model weight 없이 deterministic sensitive-content
  moderation workflow 실행

## 다이어그램

### 예제 시나리오

![Basic Processing Scenario](../../docs/images/readme-diagrams/examples-basic-processing-scenario-01.png)

### Architecture

![Basic Processing Architecture](../../docs/images/readme-diagrams/examples-basic-processing-architecture-01.png)

### Sequence

![Basic Processing Sequence](../../docs/images/readme-diagrams/examples-basic-processing-sequence-01.png)

## 실행

```bash
./gradlew :basic-processing:run
```

출력 디렉터리를 직접 지정할 수도 있습니다.

```bash
./gradlew :basic-processing:run --args="/tmp/bluetape4k-basic-processing"
```

예상 출력:

| 파일 | 원본 | 작업 | 크기 |
| --- | --- | --- | --- |
| `01-cafe-thumbnail.jpg` | `cafe.jpg` | 비율 유지 썸네일 | `320x240` |
| `02-landscape-smart-crop.jpg` | `landscape.jpg` | saliency smart crop | `640x360` |
| `03-cafe-converted.png` | `cafe.jpg` | JPEG to PNG 변환 | `800x600` |
| `04-landscape-watermarked.jpg` | `landscape.jpg` | 비율 유지 리사이즈와 텍스트 워터마크 | `960x540` |
| `05-readme-workbench-preview.jpg` | `image-workbench.png` | 루트 README 대표 이미지 preview | `960x540` |

## Smoke Test

```bash
./gradlew :basic-processing:test
```

테스트는 `run` 태스크가 사용하는 같은 generator를 호출하고, 모든 출력 파일이 존재하며
디코딩 가능하고 기대한 크기인지 검증합니다.

## Sensitive Moderation Workflow

`runSensitiveWorkflow` 태스크는 detector 결과를 policy, action, public-safe
derivative로 이어 붙이는 흐름을 deterministic fake detector 출력으로 보여줍니다.
이 예제는 production moderator가 아닙니다. wiring, audit report, 안전한 derivative
동작을 확인하는 용도이며, 실제 배포에서는 model 검증, false-positive/false-negative
모니터링, threshold review, human escalation rule이 따로 필요합니다.

```bash
./gradlew :basic-processing:runSensitiveWorkflow
```

출력 디렉터리를 직접 지정할 수도 있습니다.

```bash
./gradlew :basic-processing:runSensitiveWorkflow --args="/tmp/bluetape4k-sensitive-workflow"
```

예상 출력:

| 파일 | 용도 |
| --- | --- |
| `06-sensitive-moderation-preview.jpg` | sample policy의 rectangle action으로 만든 public-safe derivative |
| `06-sensitive-moderation-report.txt` | detection, policy decision, action intensity, renderer coverage를 정리한 text audit summary |

예제는 다음 geometry와 action 조합을 다룹니다.

| Detector fixture | Geometry | Policy action | Action parameters |
| --- | --- | --- | --- |
| `suggestive-low` | rectangle | `ALLOW` | no treatment |
| `explicit-medium` | rectangle | `MOSAIC` | `mosaicBlockSize=18` |
| `pii-text-line` | polyline | `BLUR` | `blurRadius=6.0`, `blurSigma=2.0` |
| `minor-face` | rectangle | `SOLID_MASK` | `maskOpacity=0.95` |
| `violence-contour` | polygon | `MANUAL_REVIEW` | `reviewPriority=70` |
| `weapon-silhouette` | rectangle | `DROP` | `rejectReason=weapon policy` |
| `hate-symbol-mask` | raster mask metadata | `REJECT` | `rejectReason=hate-symbol policy` |
| `unknown-sensitive-region` | polygon | `QUARANTINE` | fail-closed fallback |

이 예제에서 core privacy derivative pipeline이 실제로 렌더링하는 것은 rectangle
action뿐입니다. polygon, polyline, raster-mask case는 policy/audit contract를
보여주기 위해 포함했습니다. 이후 renderer나 detector adapter를 붙일 때도
`SensitiveContentDetection`과 `SensitiveModerationPolicy` 모델은 그대로 사용할 수 있고,
model runtime dependency를 `bluetape4k-images` 안으로 밀어 넣을 필요가 없습니다.

Blog seed는
[`docs/blog/sensitive-content-moderation-workflow-outline.md`](../../docs/blog/sensitive-content-moderation-workflow-outline.md)에
정리했습니다.
