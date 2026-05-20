# README Diagram Image Validation

## Context

README diagrams in bluetape4k-image were refreshed with the shared pastel infographic renderer. The work covers current Mermaid blocks and existing README diagram image links recovered from git history.

## Decision

Use PNG as the README-facing artifact and keep SVG sources beside the PNG files for reuse. Diagram labels are English-only. Generic titles such as `Diagram`, `Architecture`, and `Sequence Diagram` are replaced with module-specific English titles. Sequence labels that lose non-English text fall back to the participating components instead of a meaningless generic label.

## Outcome

- 33 rendered artifacts
- 17 PNG files
- 17 SVG source files
- no missing README image links
- no local SVG image embeds in README files
- no remaining Mermaid code blocks
- no shape-check candidates

## Verification

- `node /Users/debop/work/bluetape4k/.omx/scripts/refine-readme-diagrams.mjs .`
- README image link and Mermaid residue checker
- PNG/SVG shape checker
- Visual contact sheet review: `/tmp/bluetape4k-image-diagram-review-samples.png`
- `git diff --check`

## Future Guidance

Regenerate from the original Mermaid source when available, including git history for previously replaced blocks. Keep image size content-driven, avoid fake filler nodes, preserve SVG sources, and inspect a sample sheet before publishing.

## 2026-05-20 Class Layout Follow-up

`images-class-02` needed manual free placement because the generated class map
had many long curves crossing the center of the diagram. The corrected image
uses local relationship lanes: image operations depend on `ImmutableImage`,
filter classes hang from a local `ImageFilterChain` bus, and coroutine writers
share one implementation lane.

Future class diagrams should reject routes that pass through class card
interiors. Use orthogonal local buses or omit low-value dependencies before
accepting an edge hairball.
