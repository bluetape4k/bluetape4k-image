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
  bands.
- Added `Images Ktor Architecture` using the same javers-style layer language.
- Added a static CAPTCHA challenge preview for `images-captcha`.

## Verification

- Generator gate output reported node/edge/message counts with
  `manual_exceptions=0` and sequence `label_intersections=0`.
- Rendered final PNGs with `rsvg-convert`.
- Inspected a contact sheet of all touched final PNG assets.
- Parsed touched SVG files with `xmllint --noout`.
- Confirmed local README image links resolve and README files embed PNG, not SVG.
- Confirmed diagram SVGs use `Architects Daughter` and `Comic Mono` without
  UI font families.
- `git diff --check` passed.

## Future Guidance

When a README module diagram is intended to explain Spring, Ktor, or storage
layers, prefer the javers-style horizontal layer bands with left-side layer
labels, centered cards, and a Graphviz evidence footer instead of arranging
cards in a generic flow.
