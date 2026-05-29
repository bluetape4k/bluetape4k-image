# 2026-05-29 - Spring Boot Image API Example

## Context

Issue #125 needed a compact repository-local Spring Boot image API quickstart
that demonstrates local storage without pulling in the larger workshop S3/CDN
flow.

## Decision

Add `examples/spring-boot-image-api` as a non-published Spring Boot 4 example.
Use `bluetape4k-images-spring-boot` local storage auto-configuration and keep
S3/CDN setup documented as an advanced-workshop concern.

## Outcome

The example accepts multipart uploads, validates image content type, stores the
original, creates a PNG thumbnail, returns storage keys and local read URLs, and
tests the flow with MockMvc and generated in-memory JPEG bytes.

## Verification

Run `./gradlew :spring-boot-image-api:test` before merging.

## Future Note

For examples, keep the first-run path local and deterministic. Link to workshop
apps when the next step requires external credentials, public URL policy, or
multi-service infrastructure.
