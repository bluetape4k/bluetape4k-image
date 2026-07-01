# Issue 193 Upload Dimension Limits Lesson

Date: 2026-07-02
Issue: #193

## Context

Upload routes and examples already limited compressed request bytes, but they decoded images before checking decoded dimensions. A small compressed image with very large dimensions could still force expensive decode, thumbnail, or OCR work.

## Decision

Add a shared first-frame dimension probe in `bluetape4k-images` and validate `maxInputPixels` plus `maxInputSide` before thumbnail or OCR work. Keep byte-size limits separate from decoded-dimension limits because compressed size does not bound decoded memory or CPU cost.

## Outcome

Ktor thumbnail routes, Ktor OCR example, Spring Boot image example, and Spring Boot OCR example now reject oversized decoded image headers before expensive processing. README locale pairs document the separate byte, pixel, and side limits.

## Future Guard

For any new upload or image-processing example, validate both compressed bytes and decoded dimensions before creating `ImmutableImage`, invoking OCR, or calling native/VIPS processing.
