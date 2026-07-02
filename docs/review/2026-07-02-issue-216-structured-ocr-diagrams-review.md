# Issue #216 Structured OCR Diagram Review

## Scope

- Refreshed `images-ocr` README architecture, class, and sequence diagrams so they show the structured OCR API introduced by issue #216.
- Kept the change source-backed in `docs/scripts/generate-readme-visual-assets.py` and regenerated matching SVG/PNG assets.

## Evidence

- `python3 docs/scripts/generate-readme-visual-assets.py`: generated diagrams=19 charts=10.
- `xmllint --noout` on the three touched `images-ocr` SVG files: pass.
- `python3 docs/scripts/validate-readme-visual-assets.py`: pass, `finalSvg=51`, `png=pass`, `textFit=1777`, `routeCrossings=0`, `sequenceLabels=41`.
- `diagram-geometry-audit.py`: geometry_failures=0 for all three touched SVG files.
- `diagram-endpoint-audit.py`: pass for the sequence diagram.
- `diagram-connector-audit.py`: pass, `markers=2`, `connectors=5`, `intrusions=0`, `crossings=0`.
- `diagram-sequence-style-audit.py`: pass, `sequence_files=1`.
- `diagram-mixed-corner-audit.py`: pass, `files=3`, `paths=25`, `q_bends=0`.
- Full-size PNG inspection: no clipped text, incoherent overlap, or connector/label collision found.
- `git diff --check`: pass.

## Residual Risk

- Documentation-only change; no Kotlin runtime behavior changed.
