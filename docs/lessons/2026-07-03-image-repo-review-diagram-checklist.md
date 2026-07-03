# Image Repo Review and Diagram Checklist

Date: 2026-07-03
Scope: `bluetape4k-image`

## Context

The repo-wide review combined Kotlin code quality, README parity, and README
diagram validation. Earlier diagram renders looked acceptable by eye but failed
machine checks for connector metadata, endpoint routing, and sequence style.
A later checklist challenge also showed that "script passed" and contact-sheet
review were not enough evidence: the pass had not explicitly proven marker
color parity, dashed marker isolation, sequence palette parity, zero-connector
exceptions, or full-size inspection of high-risk PNGs.

## Decision

Treat README-facing diagrams as generated assets with both SVG and PNG evidence.
For broad diagram refreshes, validate every SVG under `docs/images/readme-diagrams`
and `docs/images/readme-charts` with the `bluetape4k-diagram` audit scripts,
then render PNGs and inspect a contact sheet plus representative single images.
For connector-heavy or sequence diagrams, add an explicit evidence ledger with
counts for connectors, cards, marker references, dashed marker heads, sequence
labels, and zero-connector exceptions.

## Outcome

The final checklist passed for 52 SVG files:

- `xmllint --noout`
- `diagram-connector-audit.py`
- `diagram-endpoint-audit.py`
- `diagram-geometry-audit.py`
- `diagram-mixed-corner-audit.py`
- `diagram-sequence-style-audit.py` for sequence diagrams
- Additional invariant audit: `connector_marker_refs=310`, `mismatches=0`,
  `context_stroke=0`, `dashed_marker_dash_failures=0`, `sequence_files=6`,
  `saturated_hits=0`, `return_not_teal=0`, `labels=41`,
  `gap_failures=0`, `numbering_failures=0`

The review also fixed the SVG SSRF test so it proves zero outbound HTTP
requests, not just "success or exception".

## Future Guidance

- Keep chart SVGs free of unused marker definitions when they have no connector
  paths.
- Sequence label backgrounds should use `class="pill"`, not `class="label pill"`,
  because the older `.label` fill can cover text.
- Do not rely on contact sheets alone. Open touched or high-risk PNGs full-size
  after the last coordinate or style change, and list the inspected files in the
  PR evidence.
- Sequence return lines must use the muted teal return palette, and normal call
  lines must avoid saturated `#2563eb` when the sequence checklist applies.
- Marker definitions must match connector stroke colors and must set
  `stroke-dasharray="none"` so dashed connector patterns do not bleed into
  arrowheads.
- For SVG resource-security tests, use a local counting HTTP server and assert
  request count rather than relying on network failure.
