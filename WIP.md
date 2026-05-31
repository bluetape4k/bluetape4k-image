# WIP - bluetape4k-image

Snapshot: 2026-06-01 KST
Scope: open GitHub issues assigned to `debop`.
Open count: 3 issues.

## Current Development State - 2026-06-01

`0.2.0` is released and available from GitHub Releases and Maven Central. The
`develop` branch is reopened on `baseVersion=0.3.0` for the next feature line.
Patch versions are reserved for bug fixes only.
Remaining assigned issues are backlog model-heavy features:

- [#1](https://github.com/bluetape4k/bluetape4k-image/issues/1) OCR
- [#2](https://github.com/bluetape4k/bluetape4k-image/issues/2) face/object detection
- [#3](https://github.com/bluetape4k/bluetape4k-image/issues/3) image classification

Release prep for future stable tags must verify the `CHANGELOG.md` sections,
remove any internal `*-SNAPSHOT` bluetape4k dependencies, rerun CI/Nightly on
the release-prep commit, and reopen `develop` on the next minor line after a
feature release. Use a patch line only for bug fixes.

## Roadmap Refresh - 2026-06-01

Current evidence: milestone `0.2.0` is released. The next normal development
line is `0.3.0`; do not open `0.2.1` unless a bug fix must ship before the next
feature release.

| Lane | Candidate milestone | Current candidates | Decision |
|---|---|---|---|
| Patch | `0.2.1` | bug fixes only | Open this only for image-core, docs, dependency, or CI regressions that must ship before the next feature line. |
| Minor | `0.3.0` | #1/#2/#3 after dependency/model packaging is proven | Keep native/model-heavy work isolated from the core `images` module. |

Recommended order: plan the `0.3.0` native/model lane from the completed
dependency research, then choose one of #1 or #2 as the first implementation
slice. Keep #3 behind the model/runtime packaging proof.

## Milestone Queue - 2026-05-29

### Completed since the previous snapshot

1. [#4](https://github.com/bluetape4k/bluetape4k-image/issues/4)
   `feat: CAPTCHA 이미지 생성 모듈 추가 (images-captcha)`
2. [#82](https://github.com/bluetape4k/bluetape4k-image/issues/82)
   `docs: refresh libvips prerequisite and native-access troubleshooting`
3. [#86](https://github.com/bluetape4k/bluetape4k-image/issues/86)
   `perf: refresh scrimage vs libvips benchmark report and README charts`

### Active minor milestone `0.3.0`

1. [#1](https://github.com/bluetape4k/bluetape4k-image/issues/1)
   `feat: OCR (Optical Character Recognition) 지원 추가`
2. [#2](https://github.com/bluetape4k/bluetape4k-image/issues/2)
   `feat: 얼굴/객체 탐지 (Face/Object Detection) 지원`
3. [#3](https://github.com/bluetape4k/bluetape4k-image/issues/3)
   `feat: 이미지 분류 (Image Classification) ML 모델 통합`

### Backlog reference

- [#1](https://github.com/bluetape4k/bluetape4k-image/issues/1),
  [#2](https://github.com/bluetape4k/bluetape4k-image/issues/2), and
  [#3](https://github.com/bluetape4k/bluetape4k-image/issues/3) are the next
  feature-line candidates after the completed 0.2.0 research issues.

## Issue Discovery - 2026-05-29

Patch candidates:

- none currently selected. Patch versions are bug-fix only.

Minor candidates:

- `feat: OCR (Optical Character Recognition) 지원 추가` (#1)
- `feat: 얼굴/객체 탐지 (Face/Object Detection) 지원` (#2)
- `feat: 이미지 분류 (Image Classification) ML 모델 통합` (#3)

## Refresh Notes

Verified with `gh` on 2026-06-01 KST.

- `0.2.0` GitHub Release and Maven Central publication are complete.
- All current open issues remain assigned to `debop`.
- `0.3.0` is the next feature line; `0.2.1` is reserved for bug fixes only.

## Recently Completed

- **images-spring-boot** module (Spring Boot 4 auto-configuration — S3/CDN/health/metrics) merged via [PR #42](https://github.com/bluetape4k/bluetape4k-image/pull/42), closes [#5](https://github.com/bluetape4k/bluetape4k-image/issues/5).
- **images-captcha** module merged via [PR #88](https://github.com/bluetape4k/bluetape4k-image/pull/88), closes [#4](https://github.com/bluetape4k/bluetape4k-image/issues/4).
- libvips prerequisite and native-access README guidance completed via [#82](https://github.com/bluetape4k/bluetape4k-image/issues/82).
- scrimage vs libvips benchmark report and chart refresh completed via [#86](https://github.com/bluetape4k/bluetape4k-image/issues/86).
- Pre-stabilization typo compatibility APIs were removed via [#61](https://github.com/bluetape4k/bluetape4k-image/issues/61).
- Extended test coverage for `images`, `images-vips-java21`, `images-vips-java25` via [PR #39](https://github.com/bluetape4k/bluetape4k-image/pull/39), [PR #40](https://github.com/bluetape4k/bluetape4k-image/pull/40), [PR #41](https://github.com/bluetape4k/bluetape4k-image/pull/41).
- POM license metadata corrected (Apache 2.0 → MIT) via [PR #38](https://github.com/bluetape4k/bluetape4k-image/pull/38).
- BOM module and localized BOM README files merged via [PR #12](https://github.com/bluetape4k/bluetape4k-image/pull/12) and [PR #13](https://github.com/bluetape4k/bluetape4k-image/pull/13).
- CI/Nightly, dependency governance, Kover policy, NMCP version, and dependency catalog maintenance merged through [PR #15](https://github.com/bluetape4k/bluetape4k-image/pull/15)–[PR #26](https://github.com/bluetape4k/bluetape4k-image/pull/26).
- Test code migrated from Kluent to `bluetape4k-assertions`.

## Current Direction

Active 0.2.0 work is complete. `images-captcha`, `images-ktor`, examples,
benchmark evidence, native lifecycle hardening, and documentation refreshes are
released.

Native/model-heavy work (#1 OCR, #2 face/object detection, #3 classification) remains isolated
from the core `images` module. Use the completed #83/#84/#85 research before
implementation.

## Priority Queue

| Priority | Issue | Difficulty | Notes |
|---|---|---:|---|
| P1 | [#1](https://github.com/bluetape4k/bluetape4k-image/issues/1) OCR | L | Reuse the completed dependency research. |
| P1 | [#2](https://github.com/bluetape4k/bluetape4k-image/issues/2) face/object detection | L | Reuse the completed dependency research. |
| P3 | [#3](https://github.com/bluetape4k/bluetape4k-image/issues/3) classification | L | Defer until model/runtime packaging is proven by the earlier model-heavy lanes. |

## Dependency Map

```text
#83 OCR research -> #1 OCR
#84 face/object detection research -> #2 face/object detection
  -> #3 classification, if model/runtime packaging is settled
```

## WIP Limits

| Lane | Limit | Current next |
|---|---:|---|
| Patch release | 1 | bug fixes only |
| Native/model feature | 1 | #1 or #2 first, not both |
| Cross-repo integration | 1 | keep stable releases free of internal `*-SNAPSHOT` dependencies |
