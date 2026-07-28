# Issue 213 Metadata Report 검토

## 범위

- Added `ImageMetadataReport.kt` as a privacy-aware metadata report over `metadata-extractor`.
- Reused `ExifData` mapping while adding XMP, IPTC, ICC, dimensions, orientation, page count, HDR/gain-map hints, safe diagnostics, and backend scalar hint enrichment.
- Added tests for malformed input, byte/path/stream entry points, stream ownership, XMP/IPTC/ICC mapping, diagnostic bounds, GPS stripping, and backend field filtering.
- Updated `images/README.md` and `images/README.ko.md` with public-safe and internal diagnostic examples.

## 7계층 검토

| 계층 | 판정 | 근거 |
|---|---|---|
| API 경계 | PASS | The core API stays pure JVM and backend-neutral; optional backend facts enter through sanitized scalar header hints. |
| 정확성 | PASS | Metadata mapping covers EXIF, XMP, IPTC, ICC, dimensions, orientation, multi-frame GIF page count, HDR, and gain-map hints. |
| 개인정보 보호 | PASS | GPS is stripped by default, diagnostics are opt-in, and backend field filtering removes path, URI, native pointer, memory, raw blob, byte, and GPS/location keys before any diagnostic field is added. |
| 리소스 안전성 | PASS | Byte arrays, files, paths, and streams enforce `maxBytes`; caller-owned `InputStream` instances are not closed. |
| 테스트 범위 | PASS | `ImageMetadataReportTest` covers the issue acceptance paths and regression-prone privacy/resource behavior. |
| 문서 | PASS | English and Korean module README files describe safe defaults, diagnostics, and backend enrichment parity. |
| 생태계 재사용 | PASS | Reuses `ImageDimensions`, `ExifData`, `metadata-extractor`, bluetape validation helpers, and bluetape assertion helpers; no new dependency was introduced. |

## 검증

- `./gradlew :bluetape4k-images:test --tests 'io.bluetape4k.images.analysis.ImageMetadataReportTest'`
- `./gradlew :bluetape4k-images:test --tests 'io.bluetape4k.images.analysis.*'`
- `./gradlew :bluetape4k-images:test`
- `git diff --check`

## 남은 위험

HDR and gain-map detection is intentionally best-effort because common metadata containers expose these fields inconsistently. Backend-specific adapters should map known header fields into sanitized scalar hints and expose diagnostic fields only through explicit opt-in.
