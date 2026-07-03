# bluetape4k-image 7-Tier Code Review

Date: 2026-07-03
Scope: repository-wide Kotlin code, README/README.ko parity, README diagram assets
Branch: `review/image-repo-quality-docs-diagrams`

## Executive Summary

7-Tier review found no release-blocking P0 issue. The pass closed two scoped
quality defects in this branch:

- SVG rasterization now validates positive dimensions, DPI, timeout, and maximum
  bounds at `SvgRasterizeOptions` construction time.
- The SSRF regression test now proves `allowExternalResources=false` performs
  zero loopback HTTP requests instead of accepting either success or exception.

The repository still has broad cleanup debt that should be handled in separate,
small PRs rather than hidden inside this review PR.

## Tier 1: API Boundary

Status: PASS with follow-up.

- Fixed: `SvgRasterizeOptions` now rejects zero or negative `width`, `height`,
  `dpi`, `timeoutMillis`, `maxWidthPx`, and `maxHeightPx`.
- Fixed: `SvgRasterizeOptions` now implements `Serializable` with
  `serialVersionUID`, matching bluetape4k value-object rules for public data
  classes.
- Follow-up: `allowedSchemes` is part of the public options model, but the Batik
  path currently only applies `KEY_ALLOW_EXTERNAL_RESOURCES`. A future API PR
  should either wire scheme-level filtering explicitly or deprecate the option.

## Tier 2: Security

Status: PASS for touched security surface.

- Fixed: `BatikSvgRasterizerSecurityTest` now starts a local loopback server,
  embeds its URL in the SVG, rasterizes with `allowExternalResources=false`, and
  asserts request count is exactly zero.
- Existing XXE test still verifies that DOCTYPE/file entity input is rejected or
  does not leak `/etc/passwd` markers.

## Tier 3: Correctness

Status: PASS for changed code.

- `SvgRasterizeOptions` now fails before invalid values reach `withTimeout` or
  Batik DPI conversion.
- `maxWidthPx` overflow behavior remains covered by the existing rasterizer
  test.

## Tier 4: Concurrency and Resource Lifecycle

Status: PASS for changed code.

- SVG rasterization continues to use `withTimeout` and `runInterruptible` on
  `Dispatchers.IO`.
- The loopback server in the SSRF test is stopped in `finally`.

## Tier 5: Tests

Status: PASS with repository-level cleanup debt.

- Targeted verification: `./gradlew :bluetape4k-images:test --tests '*BatikSvgRasterizer*' --warning-mode all`
  passed with 10 tests.
- Follow-up: older tests still contain mixed assertion idioms such as
  `kotlin.test.assertFailsWith` and JUnit assertions. Convert them gradually
  when touching those files; do not mix this broad migration into unrelated
  feature PRs.

## Tier 6: Documentation

Status: PASS for README parity.

- Root `README.md` and `README.ko.md` now explain the root overview diagram
  color semantics.
- Barcode provider capability matrix now separates `Commercial SDK` and
  `Native/JNI SDK` concerns instead of combining license and runtime policy in
  one row.
- `images/README.ko.md` now includes the image-analysis diagram that already
  existed in `images/README.md`.
- AVIF/HEIC KDoc examples no longer reference the nonexistent
  `bluetape4k-images-vips`, `VipsAvifWriter`, or `VipsHeicReader` names.

## Tier 7: Diagram and Visual Assets

Status: PASS.

Evidence ledger:

- Best-practice references opened before final edits:
  `sequence-workflow-sample.png`, `bluetape4k-coroutines-sequence-01.png`, and
  `leader-redis-lettuce-sequence-02.png` from `bluetape4k-wiki`.
- Sequence generator source was updated, not only the generated SVG files:
  `docs/scripts/generate-example-readme-diagrams.py` and
  `docs/scripts/generate-readme-visual-assets.py` now emit the muted
  best-practices frame, participant card, lifeline, activation, label, line,
  badge, and marker palette for sequence assets.
- Scope: `svg_files=52`, `png_files=52`, `connector_files=41`,
  `connectors=310`, `cards=335`, `zero_connector_files=11`.
- Zero-connector exceptions: the 10 README chart SVGs and
  `images-captcha-example-01.svg`, which is a static decorative sample image.
- `xmllint --noout`: `files=52`, `errors=0`.
- CairoSVG render: 52 SVG files rendered to PNG with `-s 2`.
- `diagram-connector-audit.py`: all 52 SVGs PASS; connector-bearing files
  report `intrusions=0` and `crossings=0`; `root-readme-overview-01.svg` now
  reports `connectors=13`.
- `diagram-endpoint-audit.py`: `PASS files=52`.
- `diagram-geometry-audit.py`: `geometry_failures=0` for every SVG.
- `diagram-mixed-corner-audit.py`: `PASS files=52`, `paths=300`,
  `q_bends=120`, `failures=0`.
- `diagram-sequence-style-audit.py`: `PASS sequence_files=6`.
- Additional marker parity audit: `connector_marker_refs=310`, `mismatches=0`,
  `context_stroke=0`, `dashed_marker_dash_failures=0`.
- Additional sequence palette audit: `sequence_palette_files=6`,
  `connector_paths=41`, `labels=41`,
  `visible_semantic_colors=[#2E8F89,#3E9868,#4F83BF,#B9851B,#C94D68]`,
  `stale_tailwind_palette_hits=0`, `marker_mismatches=0`,
  `label_badge_mismatches=0`.
- Full-size PNG inspection covered all 6 sequence diagrams plus representative
  high-risk root, barcode, architecture, class, and chart assets:
  `examples-basic-processing-sequence-01.png`,
  `examples-ktor-image-api-sequence-01.png`,
  `examples-ktor-ocr-api-sequence-01.png`,
  `examples-spring-boot-image-api-sequence-01.png`,
  `examples-spring-boot-ocr-api-sequence-01.png`,
  `images-ocr-sequence-diagram-01.png`, `root-readme-overview-01.png`,
  `bluetape4k-image-architecture-01.png`,
  `images-barcode-api-architecture-01.png`, `images-ktor-architecture-01.png`,
  `images-spring-boot-architecture-01.png`, `images-class-core-01.png`,
  `images-class-filters-01.png`, `images-class-writers-01.png`,
  `images-ocr-class-diagram-01.png`, `images-vips-api-class-01.png`,
  `examples-ktor-image-api-architecture-01.png`,
  `examples-spring-boot-image-api-architecture-01.png`,
  `root-readme-module-chart-01.png`, and
  `images-benchmark-vips-backend-comparison-chart-01.png`.

## Remaining Watch Items

- Public KDoc language is still mixed across the repository. New and touched
  public APIs should use English KDoc, but a repo-wide conversion should be a
  dedicated documentation PR.
- Public data classes outside the touched SVG options model still need a
  separate Serializable audit.
- `allowedSchemes` should be reconciled with Batik's resource-loading behavior
  before any feature enables external SVG resources for untrusted input.
