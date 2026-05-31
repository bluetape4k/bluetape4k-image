# WIP - bluetape4k-image

Snapshot: 2026-06-01 KST
Scope: open GitHub issues assigned to `debop`.
Open count: 3 issues.

## Current Development State - 2026-06-01

`0.2.0` is released and available from GitHub Releases and Maven Central. The
`develop` branch is reopened on `baseVersion=0.2.1` for the next patch line.
Remaining assigned issues are backlog model-heavy features:

- [#1](https://github.com/bluetape4k/bluetape4k-image/issues/1) OCR
- [#2](https://github.com/bluetape4k/bluetape4k-image/issues/2) face/object detection
- [#3](https://github.com/bluetape4k/bluetape4k-image/issues/3) image classification

Release prep for future stable tags must verify the `CHANGELOG.md` sections,
remove any internal `*-SNAPSHOT` bluetape4k dependencies, rerun CI/Nightly on
the release-prep commit, and reopen `develop` on the next patch line after the
tag is published.

## 2026-05-29 Roadmap Refresh

Current evidence: milestone `0.2.0` now contains the active feature,
performance, bug-hardening, documentation, and research work for the next image
line. The previous starter items are no longer the next work queue:

- [#4](https://github.com/bluetape4k/bluetape4k-image/issues/4)
  `images-captcha` shipped.
- [#82](https://github.com/bluetape4k/bluetape4k-image/issues/82)
  libvips prerequisite and native-access README guidance shipped.
- [#86](https://github.com/bluetape4k/bluetape4k-image/issues/86)
  benchmark report and README chart refresh shipped.
- [#83](https://github.com/bluetape4k/bluetape4k-image/issues/83),
  [#84](https://github.com/bluetape4k/bluetape4k-image/issues/84), and
  [#85](https://github.com/bluetape4k/bluetape4k-image/issues/85) remain active
  0.2.0 research gates before OCR, face/object detection, or classification
  implementation work.

| Lane | Candidate milestone | Current candidates | Decision |
|---|---|---|---|
| Patch | `0.2.1` | none yet | Keep empty unless image-core, docs, dependency, or CI regressions appear. |
| Minor | future milestone | #1/#2/#3 after dependency/model packaging is proven | Keep native/model-heavy work isolated from the core `images` module. |

Recommended order: finish the draft PR branch for the Ktor/CAPTCHA integration
lane, keep #118 unmerged until the BOM blocker is gone, then execute #83/#84/#85
as dependency and packaging research before starting #1/#2/#3 implementation.

## Milestone Queue - 2026-05-29

### Completed since the previous snapshot

1. [#4](https://github.com/bluetape4k/bluetape4k-image/issues/4)
   `feat: CAPTCHA 이미지 생성 모듈 추가 (images-captcha)`
2. [#82](https://github.com/bluetape4k/bluetape4k-image/issues/82)
   `docs: refresh libvips prerequisite and native-access troubleshooting`
3. [#86](https://github.com/bluetape4k/bluetape4k-image/issues/86)
   `perf: refresh scrimage vs libvips benchmark report and README charts`

### Active minor milestone `0.2.0`

1. [#118](https://github.com/bluetape4k/bluetape4k-image/issues/118)
   `feat(ktor): add image Ktor integration module` — implemented in draft
   [PR #119](https://github.com/bluetape4k/bluetape4k-image/pull/119), blocked
   until `bluetape4k-bom 1.10.0`.
2. [#109](https://github.com/bluetape4k/bluetape4k-image/issues/109)
   `fix(spring-boot): verify optional S3/CDN auto-configuration backoff paths`
   — carried by draft PR #119.
3. [#103](https://github.com/bluetape4k/bluetape4k-image/issues/103) and
   [#105](https://github.com/bluetape4k/bluetape4k-image/issues/105)
   — allocation and memory profiling benchmark evidence carried by draft PR
   #119.
4. [#111](https://github.com/bluetape4k/bluetape4k-image/issues/111) and
   [#114](https://github.com/bluetape4k/bluetape4k-image/issues/114)
   — documentation refresh items for roadmap and CAPTCHA lifecycle guidance.
5. [#83](https://github.com/bluetape4k/bluetape4k-image/issues/83)
   `research: OCR dependency and model packaging strategy`
6. [#84](https://github.com/bluetape4k/bluetape4k-image/issues/84)
   `research: face and object detection dependency and model packaging strategy`
7. [#85](https://github.com/bluetape4k/bluetape4k-image/issues/85)
   `research: image classification dependency and model packaging strategy`

### Backlog reference

- [#1](https://github.com/bluetape4k/bluetape4k-image/issues/1),
  [#2](https://github.com/bluetape4k/bluetape4k-image/issues/2), and
  [#3](https://github.com/bluetape4k/bluetape4k-image/issues/3) remain backlog
  implementation ideas until the 0.2.0 research issues decide dependency and
  model packaging.

## Issue Discovery - 2026-05-29

Patch candidates:

- none currently selected.
  - Reopen this lane only if image-core, docs, dependency, or CI regressions
    appear outside the 0.2.0 milestone.

Minor candidates:

- `feat(ktor): add image Ktor integration module` (#118)
- `fix(spring-boot): verify optional S3/CDN auto-configuration backoff paths` (#109)
- `docs(captcha): add challenge storage and verification lifecycle guidance` (#114)
- `docs(roadmap): refresh WIP and README status for completed 0.2.0 work` (#111)
- `research: OCR dependency and model packaging strategy` (#83)
- `research: face and object detection dependency and model packaging strategy` (#84)
- `research: image classification dependency and model packaging strategy` (#85)

## Refresh Notes

Verified with `gh` on 2026-05-29 KST.

- GNO was queried first for prior image/vips plans, specs, and follow-ups.
- All current open issues remain assigned to `debop`.
- GNO was queried first for prior image/captcha roadmap and documentation
  context.

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
merged. The next step is release-prep validation, not new feature work.

Native/model-heavy work (#1 OCR, #2 face/object detection, #3 classification) remains isolated
from the core `images` module. Do not start implementation for these until
#83/#84/#85 settle dependency, packaging, and CI strategy.

## Priority Queue

| Priority | Issue | Difficulty | Notes |
|---|---|---:|---|
| P1 | 0.2.0 release prep | S | Validate release metadata, dependency stability, CHANGELOG, CI, and Nightly before tagging. |
| P2 | [#1](https://github.com/bluetape4k/bluetape4k-image/issues/1) OCR | L | Start only after release prep; reuse the completed dependency research. |
| P2 | [#2](https://github.com/bluetape4k/bluetape4k-image/issues/2) face/object detection | L | Start only after release prep; reuse the completed dependency research. |
| P3 | [#3](https://github.com/bluetape4k/bluetape4k-image/issues/3) classification | L | Defer until model/runtime packaging is proven by the earlier model-heavy lanes. |

## Dependency Map

```text
#118 Ktor integration
  -> merge after bluetape4k-bom 1.10.0

#83 OCR research -> #1 OCR
#84 face/object detection research -> #2 face/object detection
  -> #3 classification, if model/runtime packaging is settled
```

## WIP Limits

| Lane | Limit | Current next |
|---|---:|---|
| Release prep | 1 | 0.2.0 validation and tag readiness |
| Native/model feature | 1 | #1 or #2 after release prep, not both |
| Cross-repo integration | 1 | keep stable releases free of internal `*-SNAPSHOT` dependencies |
