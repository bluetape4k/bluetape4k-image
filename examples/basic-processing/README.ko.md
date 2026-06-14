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
