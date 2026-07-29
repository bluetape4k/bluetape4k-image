# Issue #216 Structured OCR Diagram 검토

## 범위

- Refreshed `images-ocr` README architecture, class, and sequence diagrams so they show the structured OCR API introduced by issue #216.
- Kept the change source-backed in `docs/scripts/generate-readme-visual-assets.py` and regenerated matching SVG/PNG assets.

## 근거

- `python3 docs/scripts/generate-readme-visual-assets.py`: generated diagrams=19 charts=10.
- `xmllint --noout` on the three touched `images-ocr` SVG files: PASS.
- `python3 docs/scripts/validate-readme-visual-assets.py`: PASS, `finalSvg=51`, `png=PASS`, `textFit=1777`, `routeCrossings=0`, `sequenceLabels=41`.
- `diagram-geometry-audit.py`: geometry_failures=0 for all three touched SVG files.
- `diagram-endpoint-audit.py`: PASS for the sequence diagram.
- `diagram-connector-audit.py`: PASS, `markers=2`, `connectors=5`, `intrusions=0`, `crossings=0`.
- `diagram-sequence-style-audit.py`: PASS, `sequence_files=1`.
- `diagram-mixed-corner-audit.py`: PASS, `files=3`, `paths=25`, `q_bends=0`.
- Full-size PNG inspection: no clipped text, incoherent overlap, or connector/label collision found.
- `git diff --check`: PASS.

## 남은 위험

- 문서 전용 변경이며 Kotlin runtime behavior는 바뀌지 않았다.
