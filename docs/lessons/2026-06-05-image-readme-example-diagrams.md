# Image README Example Diagrams

## Context

The examples README files described runnable image workflows but did not show
the scenario, architecture, or sequence visually. Existing module README
diagrams also needed source-backed cleanup: `images-spring-boot` used a
lowercase title and non-layered architecture layout, `images-ktor` lacked an
architecture diagram, and `images-captcha` lacked a visible example output.

## Decision

Generate English-label SVG/PNG pairs under `docs/images/readme-diagrams` and
embed PNG files only from README files. Keep Graphviz `.dot`, `.plain`, sketch
SVG, and sketch PNG evidence beside connector-heavy final assets.

## Outcome

- Added scenario, architecture, and sequence diagrams for all three examples.
- Rebuilt `Images Spring Boot Architecture` with javers-style horizontal layer
  bands, wider cards, and Graphviz-informed non-grid placement.
- Added `Images Ktor Architecture` using the same javers-style layer language.
- Added a static CAPTCHA challenge preview for `images-captcha`.
- Removed cramped flow edge labels from README-facing diagrams so line text
  cannot be hidden by cards.
- Added route and card-overlap validation for javers-style architecture assets.
- Added a generator guard for accidental string `Node.details`, which had
  rendered one detail line as vertical per-character text.
- Rebuilt the example scenario and architecture diagrams with wider
  Graphviz-like layouts instead of compact grid placement.
- Extended route, crowding, and text-fit validation to all flow diagrams, not
  only javers-style module architecture diagrams.

## Verification

- Generator gate output reported node/edge/message counts with
  `manual_exceptions=0` and sequence `label_intersections=0`.
- Rendered final PNGs with `rsvg-convert`.
- Inspected a contact sheet of all touched final PNG assets.
- Re-inspected the Spring Boot and Ktor Architecture PNGs after widening the
  canvas, lowering the body start, and breaking uniform table-like placement.
- Re-inspected the examples sequence PNGs after centering participants with
  symmetric left/right margins and moving message labels below participant
  headers.
- Re-inspected the six example scenario/architecture PNGs after widening
  canvases, reserving layer-label space, and simplifying connector lanes.
- Tried the local Claude advisor path for layout guidance, but the CLI failed
  with the disabled-organization API error; Graphviz evidence and rendered PNG
  inspection were used instead.
- Parsed touched SVG files with `xmllint --noout`.
- Confirmed local README image links resolve and README files embed PNG, not SVG.
- Confirmed diagram SVGs use `Architects Daughter` and `Comic Mono` without
  UI font families.
- `git diff --check` passed.

## Future Guidance

When a README module diagram is intended to explain Spring, Ktor, or storage
layers, prefer the javers-style horizontal layer bands with left-side layer
labels, wide cards, and a Graphviz evidence footer. Do not place cards as a
uniform table of equal cells when Graphviz evidence suggests staggered flow.
If text approaches the card edge, widen the canvas and card before shortening
labels. Hide edge labels in README flow diagrams unless the label has proven
clearance from every card. Keep title/subtitle spacing and bottom whitespace
visually balanced, and center sequence lifelines with symmetric left/right
margins.

For example diagrams, do not preserve a compact grid merely because it fits the
first canvas size. Use the Graphviz `.plain` direction as the final-layout
baseline: wide left-to-right pipelines for architecture, central fan-out for
scenarios, and column or band layers when the example has application/runtime,
route/helper, library, or storage responsibilities. Reserve visible space for
layer labels before placing cards. If a route needs a cramped bus or a label
would collide with a card, enlarge the canvas and move cards first.
