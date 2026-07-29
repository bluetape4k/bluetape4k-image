# OCR 아키텍처ure 다이어그램 검토 - 2026-06-06

## 범위

- `docs/images/readme-diagrams/images-ocr-architecture-01.svg`
- `docs/images/readme-diagrams/images-ocr-architecture-01.png`
- Graphviz evidence files for the same architecture asset.

## 발견 사항

| Severity | 발견 사항 | 해결 |
|---|---|---|
| P1 | `OcrResult or OcrException` label exceeded the result card width. | Expanded the result card from 270px to 370px and recentered the label. |
| P1 | `TesseractOcrEngine -> OcrResult or OcrException` route passed through the `Tess4J` card. | Rerouted the connector through the left-side gap between the layer label and Tess4J card. |
| P2 | 첫 reroute는 과보정되어 layer label과 충돌했고 이후 너무 오른쪽으로 우회했다. | 최종 route는 visual inspection 후 왼쪽 `x=370` lane을 사용한다. |

## 게이트 근거

| 게이트 | 결과 | 근거 |
|---|---|---|
| XML parse | PASS | `xmllint --noout docs/images/readme-diagrams/images-ocr-architecture-01.svg docs/images/readme-diagrams/images-ocr-architecture-01-graphviz.svg` |
| PNG pair | PASS | `identify docs/images/readme-diagrams/images-ocr-architecture-01.png docs/images/readme-diagrams/images-ocr-architecture-01-graphviz.png` |
| README embed | PASS | `rg 'images-ocr-architecture-01\.svg' images-ocr/README.md images-ocr/README.ko.md` 일치 항목 없음. |
| font role guard | PASS | `rg 'font-family=.*(Inter|Arial|Helvetica)|font-family:(Inter|Arial|Helvetica)' docs/images/readme-diagrams/images-ocr-architecture-01.svg` 일치 항목 없음. |
| Diff whitespace | PASS | `git diff --check` |
| visual inspection | PASS | 렌더링된 PNG에서 result label overflow, Tess4J 내부 crossing, layer label overlap이 보이지 않는다. |

## 판정

- P0: 0
- P1: 0
- P2/P3: 0 blocking

