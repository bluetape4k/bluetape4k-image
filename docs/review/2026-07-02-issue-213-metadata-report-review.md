# Issue 213 Metadata Report Review

## Scope

- Added `ImageMetadataReport.kt` as a privacy-aware metadata report over `metadata-extractor`.
- Reused `ExifData` mapping while adding XMP, IPTC, ICC, dimensions, orientation, page count, HDR/gain-map hints, safe diagnostics, and backend scalar hint enrichment.
- Added tests for malformed input, byte/path/stream entry points, stream ownership, XMP/IPTC/ICC mapping, diagnostic bounds, GPS stripping, and backend field filtering.
- Updated `images/README.md` and `images/README.ko.md` with public-safe and internal diagnostic examples.

## 7-Tier Review

| Tier | Verdict | Evidence |
|---|---|---|
| API boundary | PASS | The core API stays pure JVM and backend-neutral; optional backend facts enter through sanitized scalar header hints. |
| Correctness | PASS | Metadata mapping covers EXIF, XMP, IPTC, ICC, dimensions, orientation, multi-frame GIF page count, HDR, and gain-map hints. |
| Privacy | PASS | GPS is stripped by default, diagnostics are opt-in, and backend field filtering removes path, URI, native pointer, memory, raw blob, byte, and GPS/location keys before any diagnostic field is added. |
| Resource safety | PASS | Byte arrays, files, paths, and streams enforce `maxBytes`; caller-owned `InputStream` instances are not closed. |
| Test coverage | PASS | `ImageMetadataReportTest` covers the issue acceptance paths and regression-prone privacy/resource behavior. |
| Documentation | PASS | English and Korean module README files describe safe defaults, diagnostics, and backend enrichment parity. |
| Ecosystem reuse | PASS | Reuses `ImageDimensions`, `ExifData`, `metadata-extractor`, bluetape validation helpers, and bluetape assertion helpers; no new dependency was introduced. |

## Validation

- `./gradlew :bluetape4k-images:test --tests 'io.bluetape4k.images.analysis.ImageMetadataReportTest'`
- `./gradlew :bluetape4k-images:test --tests 'io.bluetape4k.images.analysis.*'`
- `./gradlew :bluetape4k-images:test`
- `git diff --check`

## Residual Risk

HDR and gain-map detection is intentionally best-effort because common metadata containers expose these fields inconsistently. Backend-specific adapters should map known header fields into sanitized scalar hints and expose diagnostic fields only through explicit opt-in.
