# Split images class diagrams by responsibility

## Context

`images-class-02` combined core APIs, filter implementations, writer types, and helpers into one class diagram. The diagram became too dense for README-scale review, and connector routing defects were hard to inspect visually.

## Decision

Split the `images` module class diagram into three responsibility-focused diagrams:

- Core API classes
- Filter classes
- Writer classes

Each diagram now has a generated SVG/PNG pair plus fixed-position Graphviz evidence (`.dot`, `.plain`, `-graphviz.svg`, `-graphviz.png`). The generator validates node overlap, text overflow, source/target boundary attachment, and connector clearance from unrelated boxes.

## Outcome

README files now embed the three PNG diagrams instead of the old single crowded image. The diagrams keep English labels for shared assets and localized surrounding README prose.

## Verification

- `python3 docs/scripts/generate-images-class-diagrams.py`
- `python3 -m py_compile docs/scripts/generate-images-class-diagrams.py`
- `xmllint --noout` for new final and Graphviz SVGs
- README PNG link existence check
- rendered PNG inspection for Core, Filters, Writers, and Graphviz evidence
- `git diff --check`

## Future Guidance

For dense class diagrams, split by class responsibility before adding more routing complexity. If a connector crosses or crowds a non-endpoint class box, fix placement or remove low-value relationships instead of accepting a long detour.
