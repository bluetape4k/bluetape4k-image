# Lessons Learned - Issue 191 S3 Download Limit (2026-07-01)

**Related issue**: #191
**Affected module**: `bluetape4k-images-spring-boot`

## Context

`S3ImageStorage.download()` used `S3Operations.listPage()` as a best-effort
size pre-check before calling `downloadBytes()`. If the size lookup failed or
did not return the exact object, the implementation continued to download the
object without a second size check.

## Decision

Keep S3 downloads fail-closed while `S3Operations` lacks a HEAD/metadata API.
The storage now refuses to start a byte-array download unless the object size is
verified through the list-based pre-check. It also validates the returned byte
array size after download to catch races or inconsistent S3 metadata.

## Outcome

An unavailable or inconsistent S3 size pre-check no longer disables
`maxSizeBytes`. Destination downloads inherit the same guard because they call
`download(key)` before writing bytes.

## Verification

- Red test: `S3ImageStorageTest` reported 4 failing tests and 1 passing test
  before the fix.
- Green targeted test:
  `./gradlew :bluetape4k-images-spring-boot:test --tests 'io.bluetape4k.images.spring.storage.s3.S3ImageStorageTest' --no-daemon`
  reported `5 passing`.
- Module test:
  `./gradlew :bluetape4k-images-spring-boot:test --no-daemon` reported
  `123 passing`.

## Future Guard

Do not treat object-size metadata lookup as optional for download safety limits.
If a future `S3Operations` HEAD API becomes available, prefer it over list-based
metadata, but keep the post-download byte-count guard because S3 metadata can
race with object replacement.
