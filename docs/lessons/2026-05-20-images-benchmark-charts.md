# 2026-05-20 — Images benchmark charts

## Context

The images benchmark README used diagram-style visuals for benchmark results.
Those visuals made performance data harder to interpret than charts.

## Decision

Add static benchmark charts under `docs/images/readme-charts/` and link them from
the English/Korean README files and benchmark result note. Preserve SVG sources
next to PNG files for later reuse.

## Outcome

Resize, encode, and filter benchmark results are now shown as chart images. The
encode benchmark description was also corrected to state that it uses a 4K photo
image.

## Verification

- `xmllint --noout docs/images/readme-charts/*.svg`
- `identify docs/images/readme-charts/*.png`
- Manual visual spot-check of all three image benchmark charts.

## Future

Use chart visuals for numeric benchmark results. Reserve architecture diagrams
for structural relationships.
