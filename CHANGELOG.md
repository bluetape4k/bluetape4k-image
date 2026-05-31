# Changelog

All notable changes to `bluetape4k-image` are documented here.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).
This project follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.2.0] - 2026-06-01

### Added

- `images-captcha` module for CAPTCHA image challenge generation, refreshable challenge services, and verification service contracts ([PR #88](https://github.com/bluetape4k/bluetape4k-image/pull/88), [PR #119](https://github.com/bluetape4k/bluetape4k-image/pull/119), [PR #127](https://github.com/bluetape4k/bluetape4k-image/pull/127), closes [#4](https://github.com/bluetape4k/bluetape4k-image/issues/4), [#101](https://github.com/bluetape4k/bluetape4k-image/issues/101)).
- `images-ktor` route helpers for CAPTCHA issue/verification and multipart thumbnail APIs, aligned with shared `bluetape4k-ktor-*` helpers ([PR #119](https://github.com/bluetape4k/bluetape4k-image/pull/119), [PR #127](https://github.com/bluetape4k/bluetape4k-image/pull/127), [PR #136](https://github.com/bluetape4k/bluetape4k-image/pull/136), closes [#118](https://github.com/bluetape4k/bluetape4k-image/issues/118), [#135](https://github.com/bluetape4k/bluetape4k-image/issues/135)).
- Runnable examples for pure JVM processing, Spring Boot local storage, and Ktor image APIs ([PR #128](https://github.com/bluetape4k/bluetape4k-image/pull/128), [PR #130](https://github.com/bluetape4k/bluetape4k-image/pull/130), [PR #131](https://github.com/bluetape4k/bluetape4k-image/pull/131), closes [#124](https://github.com/bluetape4k/bluetape4k-image/issues/124), [#125](https://github.com/bluetape4k/bluetape4k-image/issues/125), [#126](https://github.com/bluetape4k/bluetape4k-image/issues/126)).
- Research-backed image AI dependency strategy for OCR, face/object detection, and classification follow-up work ([PR #129](https://github.com/bluetape4k/bluetape4k-image/pull/129), closes [#83](https://github.com/bluetape4k/bluetape4k-image/issues/83), [#84](https://github.com/bluetape4k/bluetape4k-image/issues/84), [#85](https://github.com/bluetape4k/bluetape4k-image/issues/85)).

### Changed

- Prepared the release line to consume `io.github.bluetape4k:bluetape4k-bom:1.10.0` and the public stable `io.github.bluetape4k.aws:bluetape4k-aws-bom:0.3.0` ([PR #92](https://github.com/bluetape4k/bluetape4k-image/pull/92), [PR #94](https://github.com/bluetape4k/bluetape4k-image/pull/94), [PR #134](https://github.com/bluetape4k/bluetape4k-image/pull/134)).
- Refreshed vips/java21/java25 benchmark evidence, natural-photo fixtures, charts, and benchmark report regeneration guidance ([PR #89](https://github.com/bluetape4k/bluetape4k-image/pull/89), [PR #117](https://github.com/bluetape4k/bluetape4k-image/pull/117), [PR #121](https://github.com/bluetape4k/bluetape4k-image/pull/121), [PR #122](https://github.com/bluetape4k/bluetape4k-image/pull/122), closes [#86](https://github.com/bluetape4k/bluetape4k-image/issues/86), [#103](https://github.com/bluetape4k/bluetape4k-image/issues/103), [#104](https://github.com/bluetape4k/bluetape4k-image/issues/104), [#105](https://github.com/bluetape4k/bluetape4k-image/issues/105)).
- Documented AVIF/HEIC native codec boundaries, libvips runtime setup, CAPTCHA lifecycle ownership, and completed 0.2.0 roadmap state ([PR #87](https://github.com/bluetape4k/bluetape4k-image/pull/87), [PR #120](https://github.com/bluetape4k/bluetape4k-image/pull/120), [PR #95](https://github.com/bluetape4k/bluetape4k-image/pull/95), closes [#111](https://github.com/bluetape4k/bluetape4k-image/issues/111), [#112](https://github.com/bluetape4k/bluetape4k-image/issues/112), [#113](https://github.com/bluetape4k/bluetape4k-image/issues/113), [#114](https://github.com/bluetape4k/bluetape4k-image/issues/114)).

### Removed

- Removed the `ImmutableImage.useGraphics(...)` and `hammingDistance(...)` compatibility shims that were deprecated for removal in `0.2.0`; use `ImmutableImage.withGraphics { }` and `HashDistance.hamming(a, b)` instead ([#61](https://github.com/bluetape4k/bluetape4k-image/issues/61)).

### Fixed

- Hardened optional S3/CDN auto-configuration fallback behavior and release workflow catalog selection ([PR #97](https://github.com/bluetape4k/bluetape4k-image/pull/97), [PR #119](https://github.com/bluetape4k/bluetape4k-image/pull/119), closes [#109](https://github.com/bluetape4k/bluetape4k-image/issues/109)).
- Guarded native vips lifecycle and FFM arena cleanup contracts, including AVIF/HEIC capability-gated read/write support, double-close, use-after-close, codec capability, and failed creation paths ([PR #115](https://github.com/bluetape4k/bluetape4k-image/pull/115), [PR #116](https://github.com/bluetape4k/bluetape4k-image/pull/116), closes [#100](https://github.com/bluetape4k/bluetape4k-image/issues/100), [#107](https://github.com/bluetape4k/bluetape4k-image/issues/107), [#108](https://github.com/bluetape4k/bluetape4k-image/issues/108)).
- Restricted workflow token permissions for repository automation ([PR #132](https://github.com/bluetape4k/bluetape4k-image/pull/132)).

## [0.1.2] - 2026-05-23

### Changed

- Prepared the release line to consume `io.github.bluetape4k:bluetape4k-bom:1.9.1`, the shared bluetape4k dependency catalog, and `io.github.bluetape4k.aws:bluetape4k-aws-bom:0.2.1` ([PR #77](https://github.com/bluetape4k/bluetape4k-image/pull/77), [PR #78](https://github.com/bluetape4k/bluetape4k-image/pull/78), [PR #80](https://github.com/bluetape4k/bluetape4k-image/pull/80)).
- Parameterized the release workflow catalog ref selection for tag-triggered and manual release dispatches ([PR #79](https://github.com/bluetape4k/bluetape4k-image/pull/79)).

## [0.1.1] - 2026-05-22

### Added

- `images-spring-boot` module: Spring Boot 4 auto-configuration for image storage, CDN signing, reactive health, and Micrometer metrics. Includes `LocalImageStorage`, `S3ImageStorage`, `S3PreSignedUrlSigner`, `CloudFrontUrlSigner`, and five auto-configuration phases ([PR #42](https://github.com/bluetape4k/bluetape4k-image/pull/42), closes [#5](https://github.com/bluetape4k/bluetape4k-image/issues/5)).
- Root README hero image plus refreshed project-purpose and feature entrypoint documentation ([PR #27](https://github.com/bluetape4k/bluetape4k-image/pull/27)).
- `bluetape4k-image-bom` BOM module for image library consumers ([PR #12](https://github.com/bluetape4k/bluetape4k-image/pull/12)).
- English and Korean README files for the image BOM module ([PR #13](https://github.com/bluetape4k/bluetape4k-image/pull/13)).
- GitHub Actions workflows for CI, nightly, snapshot, release, and code-quality checks ([PR #7](https://github.com/bluetape4k/bluetape4k-image/pull/7)).

### Changed

- Extended test coverage for `images` module: batch processing, animated writers, filter DSL, and utility functions ([PR #41](https://github.com/bluetape4k/bluetape4k-image/pull/41)).
- Added unit tests for VipsImage writer classes in `images-vips-java21` and `images-vips-java25` ([PR #40](https://github.com/bluetape4k/bluetape4k-image/pull/40)).
- Added `FfmVipsRuntime` concurrency test for `images-vips-java25` ([PR #39](https://github.com/bluetape4k/bluetape4k-image/pull/39)).
- Prepared the 0.1.1 release line to consume `io.github.bluetape4k:bluetape4k-bom:1.9.0` and `io.github.bluetape4k.aws:bluetape4k-aws-bom:0.2.0`.
- Split Nightly into smoke/full lanes and normalized lessons, Kover, Dependabot, NMCP, and compatibility-guard maintenance ([PR #15](https://github.com/bluetape4k/bluetape4k-image/pull/15), [PR #16](https://github.com/bluetape4k/bluetape4k-image/pull/16), [PR #17](https://github.com/bluetape4k/bluetape4k-image/pull/17), [PR #18](https://github.com/bluetape4k/bluetape4k-image/pull/18), [PR #22](https://github.com/bluetape4k/bluetape4k-image/pull/22), [PR #24](https://github.com/bluetape4k/bluetape4k-image/pull/24), [PR #25](https://github.com/bluetape4k/bluetape4k-image/pull/25), [PR #26](https://github.com/bluetape4k/bluetape4k-image/pull/26)).
- Updated image dependency catalog and related dependency bumps, including JVips and annotations ([PR #14](https://github.com/bluetape4k/bluetape4k-image/pull/14), [PR #20](https://github.com/bluetape4k/bluetape4k-image/pull/20), [PR #21](https://github.com/bluetape4k/bluetape4k-image/pull/21), [PR #23](https://github.com/bluetape4k/bluetape4k-image/pull/23)).
- CI uses path filtering and retry configuration ([PR #10](https://github.com/bluetape4k/bluetape4k-image/pull/10)).
- Test code migrated from Kluent to `bluetape4k-assertions` ([PR #11](https://github.com/bluetape4k/bluetape4k-image/pull/11)).

### Removed

- Pre-stabilization typo compatibility APIs from `images`: `usingSuspend(...)`, `SuspendPngWriter.NoComppression`, and the misspelled `ImageOuptputStreamSupportKt` Java facade. `ImmutableImage.useGraphics(...)` and `hammingDistance(...)` remain deprecated until removal in `0.2.0` ([#61](https://github.com/bluetape4k/bluetape4k-image/issues/61)).

### Fixed

- Aligned POM license metadata from Apache 2.0 to MIT across all published modules ([PR #38](https://github.com/bluetape4k/bluetape4k-image/pull/38)).
- Aligned repository license text as MIT across all modules ([PR #28](https://github.com/bluetape4k/bluetape4k-image/pull/28)).
