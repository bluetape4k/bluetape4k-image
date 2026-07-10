# Issue #200 natural-photo benchmark headline

## Context

The image benchmark README headline tables still cited a synthetic-fallback run even though comparable natural-photo JMH evidence was committed.

## Decision

Use the committed 2026-05-28 natural-photo report and raw JSON as the README headline source. Keep English and Korean tables identical, and show chart bars on a linear axis for these small comparison sets.

## Outcome

The README now states the measurement source and date. The generator renders target PNGs with CairoSVG at 2x resolution; an ASCII separator is used in chart axis labels because the previous middle-dot glyph rendered as tofu in CairoSVG output.

## Verification

- Matched every displayed value against the report and raw JSON.
- Regenerated SVG and PNG charts, checked SVG XML, and inspected full-size PNGs.
- Completed final 7-tier review with P0=0 and P1=0.

## Future guidance

For documentation charts, validate the rendered PNG rather than only the SVG source; verify axis semantics and font glyphs at the published resolution.
