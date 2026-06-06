# Lessons Learned - OCR Architecture Diagram Repair (2026-06-06)

## Context

The `images-ocr` architecture diagram had two visual defects: the result label
overflowed its card, and the engine-to-result connector crossed the Tess4J
card. The first repair also showed that merely avoiding the card interior is not
enough when a route collides with a layer label or takes a visually excessive
detour.

## Decision

Keep the route on the left side, but use the tighter `x=370` lane between the
layer label and the Tess4J card. Widen the result card to fit
`OcrResult or OcrException`.

## Outcome

The rendered PNG now keeps text inside the result card and routes the
engine-to-result connector without crossing the Tess4J card or the layer label.

## Future Guard

For diagram repair, inspect the rendered PNG after every route adjustment and
optimize the route visually, not just for non-overlap. A route that technically
passes geometry but takes a visibly excessive detour is not review-ready.

