# Issue 213 Metadata Report

## Context

#213 needed metadata extraction beyond the existing EXIF-only `readExif()` API while keeping public responses safe by default. The acceptance criteria included XMP, IPTC, ICC, dimensions, orientation, page counts, HDR/gain-map hints, byte/path/stream entry points, size guards, and sensitive-field stripping.

## Decision

Add a pure JVM `ImageMetadataReport` API in `bluetape4k-images`:

- Keep `readExif()` unchanged and reuse its normalized EXIF mapping.
- Make `ImageMetadataReadOptions` strip GPS and omit raw diagnostics by default.
- Expose scalar flags and summaries for XMP, IPTC, ICC, dimensions, orientation, page count, HDR, and gain-map hints.
- Keep `InputStream` caller-owned, with bounded in-memory reading for stream size guards.
- Let optional backend adapters add sanitized scalar header hints through `withBackendHeaderFields()` without adding a native libvips dependency to the core module. Diagnostic header fields still require `includeDiagnosticTags = true`.

## Outcome

Callers can choose a public-safe report for API responses or an explicitly diagnostic report for internal tooling. The report never carries raw metadata blobs, source paths, native pointers, or unbounded backend payloads.

## Verification

- `./gradlew :bluetape4k-images:test --tests 'io.bluetape4k.images.analysis.ImageMetadataReportTest'`
- `./gradlew :bluetape4k-images:test --tests 'io.bluetape4k.images.analysis.*'`
- `./gradlew :bluetape4k-images:test`
- `git diff --check`

## Future Guard

Do not add native backend dependencies or raw metadata payloads to `bluetape4k-images` for metadata enrichment. Add adapter-specific parsing in backend modules and pass only sanitized scalar header facts into `ImageMetadataReport.withBackendHeaderFields()`; keep backend diagnostic fields behind explicit opt-in.
