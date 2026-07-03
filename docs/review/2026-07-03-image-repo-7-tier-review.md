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

- Rendered 52 README-facing SVG files to PNG.
- Connector audit passed for all 52 SVGs.
- Endpoint audit passed for all 52 SVGs.
- Geometry audit passed for all 52 SVGs.
- Mixed-corner audit passed: `files=52`, `paths=300`, `q_bends=120`,
  `failures=0`.
- Sequence-style audit passed: `sequence_files=6`.
- Contact sheets were regenerated under `.omx/artifacts/diagram-contact-sheets/`
  for local visual review.

## Remaining Watch Items

- Public KDoc language is still mixed across the repository. New and touched
  public APIs should use English KDoc, but a repo-wide conversion should be a
  dedicated documentation PR.
- Public data classes outside the touched SVG options model still need a
  separate Serializable audit.
- `allowedSchemes` should be reconciled with Batik's resource-loading behavior
  before any feature enables external SVG resources for untrusted input.
