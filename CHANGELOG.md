# Changelog

All notable changes to `bluetape4k-image` are documented here.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).
This project follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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
- Split Nightly into smoke/full lanes and normalized lessons, Kover, Dependabot, NMCP, and compatibility-guard maintenance ([PR #15](https://github.com/bluetape4k/bluetape4k-image/pull/15), [PR #16](https://github.com/bluetape4k/bluetape4k-image/pull/16), [PR #17](https://github.com/bluetape4k/bluetape4k-image/pull/17), [PR #18](https://github.com/bluetape4k/bluetape4k-image/pull/18), [PR #22](https://github.com/bluetape4k/bluetape4k-image/pull/22), [PR #24](https://github.com/bluetape4k/bluetape4k-image/pull/24), [PR #25](https://github.com/bluetape4k/bluetape4k-image/pull/25), [PR #26](https://github.com/bluetape4k/bluetape4k-image/pull/26)).
- Updated image dependency catalog and related dependency bumps, including JVips and annotations ([PR #14](https://github.com/bluetape4k/bluetape4k-image/pull/14), [PR #20](https://github.com/bluetape4k/bluetape4k-image/pull/20), [PR #21](https://github.com/bluetape4k/bluetape4k-image/pull/21), [PR #23](https://github.com/bluetape4k/bluetape4k-image/pull/23)).
- CI uses path filtering and retry configuration ([PR #10](https://github.com/bluetape4k/bluetape4k-image/pull/10)).
- Test code migrated from Kluent to `bluetape4k-assertions` ([PR #11](https://github.com/bluetape4k/bluetape4k-image/pull/11)).

### Fixed

- Aligned POM license metadata from Apache 2.0 to MIT across all published modules ([PR #38](https://github.com/bluetape4k/bluetape4k-image/pull/38)).
- Aligned repository license text as MIT across all modules ([PR #28](https://github.com/bluetape4k/bluetape4k-image/pull/28)).
