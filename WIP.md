# WIP - bluetape4k-image

Snapshot: 2026-07-14 KST

This roadmap tracks open GitHub issues assigned to `debop`. It separates the
current release-line closeout from work deferred to the Backlog milestone.

## Current Development State

- [`0.3.0`](https://github.com/bluetape4k/bluetape4k-image/releases/tag/0.3.0)
  is the latest stable release, published on 2026-06-27.
- `develop` uses `baseVersion=0.4.0`; the `0.4.0` milestone is still in
  development and has not been released.
- This roadmap refresh is tracked by
  [#271](https://github.com/bluetape4k/bluetape4k-image/issues/271). After it
  merges, the changelog backfill below is the remaining `0.4.0` milestone
  closeout item.

## 0.4.0 Closeout

- [#270](https://github.com/bluetape4k/bluetape4k-image/issues/270)
  backfills `CHANGELOG.md` from completed, traceable `0.4.0` work. It does not
  publish the release or change artifact versions.

## Deferred Backlog

These issues have no fixed release target and are not part of the `0.4.0`
closeout:

- [#3](https://github.com/bluetape4k/bluetape4k-image/issues/3) — integrate an
  image-classification ML model.
- [#169](https://github.com/bluetape4k/bluetape4k-image/issues/169) — evaluate
  PaddleOCR for advanced document OCR.
- [#203](https://github.com/bluetape4k/bluetape4k-image/issues/203) — add OCR
  extraction benchmarks.
- [#204](https://github.com/bluetape4k/bluetape4k-image/issues/204) — benchmark
  storage upload and download paths.
- [#205](https://github.com/bluetape4k/bluetape4k-image/issues/205) — benchmark
  the Ktor multipart thumbnail route.
- [#206](https://github.com/bluetape4k/bluetape4k-image/issues/206) — benchmark
  batch and thumbnail pipeline throughput.
- [#207](https://github.com/bluetape4k/bluetape4k-image/issues/207) — benchmark
  algorithmic hot paths.

## Release Boundary

The `0.4.0` release date remains unset. Release publication, version changes,
and milestone closure require their own verified release workflow after the
changelog reflects the completed milestone work.
