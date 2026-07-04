# bluetape4k-image 7-Tier Review Stack

Date: 2026-07-04

## Context

The image repository review covered core image decoding, Ktor routes, Spring
storage/CDN integration, Vips native backends, and CAPTCHA verification.

## Decisions

- Keep existing one-argument image decode helpers source-compatible, and add
  bounded overloads for external input boundaries.
- Treat malformed Ktor thumbnail payloads as caller input errors while
  preserving coroutine cancellation propagation.
- Keep S3 timeout/header behavior documented at the `S3Operations` boundary
  instead of adding a parallel client construction path in this module.
- For Vips path inputs, prefer a bounded byte snapshot over native path decode
  so validation and decode operate on the same bytes.
- Bound the default CAPTCHA in-memory store on save without changing the
  one-shot verification semantics.

## Verification

- Targeted tests were run for each touched module before committing each stack
  layer.
- Full repository tests are still required after the stack is assembled.

## Future Guidance

- Public KDoc touched by future work should be converted to English when it is
  not already.
- Native-backed tests should isolate pre-native input-boundary checks in small
  unit tests so they do not become pending when native libraries are missing.
