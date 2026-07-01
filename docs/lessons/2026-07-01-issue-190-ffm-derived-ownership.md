# Lessons Learned - Issue 190 FFM Derived Ownership (2026-07-01)

**Related issue**: #190
**Affected module**: `bluetape4k-images-vips-java25`

## Context

`FfmVipsImage` returned `resize`, `thumbnail`, and `crop` results backed by the
source image arena. Closing the source first left a derived image marked open
while its native arena was already closed.

## Decision

Keep the binding-neutral `VipsImage` contract simple: every returned
`VipsImage` owns native resources and must be closed independently. For vips-ffm
derived operations, copy raw pixels into a new arena and wrap that copy with
`VImage.newFromMemory`.

## Outcome

Derived Java 25 FFM images can outlive their source image, and closing a derived
image does not close the source image.

## Verification

- Red test: `derived image remains usable after source closes` failed with
  `IllegalStateException: Already closed`.
- Green test: same test passed after the ownership fix.
- `./gradlew :bluetape4k-images-vips-api:compileKotlin :bluetape4k-images-vips-java25:test --no-daemon`
  reported `53 passing` and `BUILD SUCCESSFUL`.

## Future Guard

Do not return vips-ffm operation results directly from the source arena when the
public API returns a new `VipsImage`. Either create an independently owned image
or document and test an explicit shared-lifetime contract.
