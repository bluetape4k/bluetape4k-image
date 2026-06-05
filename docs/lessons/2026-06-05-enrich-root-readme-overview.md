# Enrich root README overview

## Context

The root README overview diagram listed modules as independent cards. That proved too simple for a repository that offers pure JVM processing, service integrations, native libvips acceleration, examples, and benchmark evidence.

## Decision

Regenerate the root README overview as a selection-flow diagram:

- entry and version alignment
- pure JVM processing path
- CAPTCHA and service integration paths
- native libvips API and backend choices
- examples and benchmark evidence

The diagram keeps the existing README image path while adding a generator and fixed-position Graphviz evidence.

## Outcome

The root README now explains the three adoption lanes in prose and shows a richer overview diagram with source-backed module relationships.

## Verification

- `python3 docs/scripts/generate-root-readme-overview.py`
- `python3 -m py_compile docs/scripts/generate-root-readme-overview.py`
- `xmllint --noout` for final and Graphviz SVGs
- README PNG link and SVG-embed checks
- rendered PNG inspection
- `git diff --check`

## Future Guidance

Root README overview diagrams should answer "which path should a user choose?" before listing modules. Use module composition charts for inventory and overview diagrams for adoption flow.
For same-layer native backend routes, prefer bottom-to-bottom or top-to-top lanes when they keep the connector outside card interiors and make the layer read as one shared capability band.
