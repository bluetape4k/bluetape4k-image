# OCR Architecture Diagram Review - 2026-06-06

## Scope

- `images-ocr/docs/assets/readme-diagrams/images-ocr-architecture-01.svg`
- `images-ocr/docs/assets/readme-diagrams/images-ocr-architecture-01.png`
- Graphviz evidence files for the same architecture asset.

## Findings

| Severity | Finding | Resolution |
|---|---|---|
| P1 | `OcrResult or OcrException` label exceeded the result card width. | Expanded the result card from 270px to 370px and recentered the label. |
| P1 | `TesseractOcrEngine -> OcrResult or OcrException` route passed through the `Tess4J` card. | Rerouted the connector through the left-side gap between the layer label and Tess4J card. |
| P2 | First reroute over-corrected and collided with the layer label / then detoured too far right. | Final route uses the left-side `x=370` lane after visual inspection. |

## Gate Evidence

| Gate | Result | Evidence |
|---|---|---|
| XML parse | PASS | `xmllint --noout images-ocr/docs/assets/readme-diagrams/images-ocr-architecture-01.svg images-ocr/docs/assets/readme-diagrams/images-ocr-architecture-01-graphviz.svg` |
| PNG pair | PASS | `identify images-ocr/docs/assets/readme-diagrams/images-ocr-architecture-01.png images-ocr/docs/assets/readme-diagrams/images-ocr-architecture-01-graphviz.png` |
| README embed | PASS | `rg 'images-ocr-architecture-01\.svg' images-ocr/README.md images-ocr/README.ko.md` returned no matches. |
| Font role guard | PASS | `rg 'font-family=.*(Inter|Arial|Helvetica)|font-family:(Inter|Arial|Helvetica)' images-ocr/docs/assets/readme-diagrams/images-ocr-architecture-01.svg` returned no matches. |
| Diff whitespace | PASS | `git diff --check` |
| Visual inspection | PASS | Rendered PNG shows no result-label overflow, no Tess4J interior crossing, and no layer-label overlap. |

## Verdict

- P0: 0
- P1: 0
- P2/P3: 0 blocking

