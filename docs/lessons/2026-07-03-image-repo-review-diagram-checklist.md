# Image Repo Review and Diagram Checklist

Date: 2026-07-03
Scope: `bluetape4k-image`

## Context

The repo-wide review combined Kotlin code quality, README parity, and README
diagram validation. Earlier diagram renders looked acceptable by eye but failed
machine checks for connector metadata, endpoint routing, and sequence style.

## Decision

Treat README-facing diagrams as generated assets with both SVG and PNG evidence.
For broad diagram refreshes, validate every SVG under `docs/images/readme-diagrams`
and `docs/images/readme-charts` with the `bluetape4k-diagram` audit scripts,
then render PNGs and inspect a contact sheet plus representative single images.

## Outcome

The final checklist passed for 52 SVG files:

- `xmllint --noout`
- `diagram-connector-audit.py`
- `diagram-endpoint-audit.py`
- `diagram-geometry-audit.py`
- `diagram-mixed-corner-audit.py`
- `diagram-sequence-style-audit.py` for sequence diagrams

The review also fixed the SVG SSRF test so it proves zero outbound HTTP
requests, not just "success or exception".

## Future Guidance

- Keep chart SVGs free of unused marker definitions when they have no connector
  paths.
- Sequence label backgrounds should use `class="pill"`, not `class="label pill"`,
  because the older `.label` fill can cover text.
- For SVG resource-security tests, use a local counting HTTP server and assert
  request count rather than relying on network failure.
