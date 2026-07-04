# bluetape4k-image 7-Tier Review

Date: 2026-07-04
Milestone: 0.4.0
Baseline: develop at `f901b4e`

## Scope

Reviewed the repository by module and package against `bluetape4k-code-patterns`
and the workspace 7-Tier lenses: correctness, security/input, architecture,
performance/stability, tests, public API documentation, and release readiness.

## Findings Filed

| Issue | Module | Priority | Finding |
|---|---|---:|---|
| #255 | `images` | P2 | Core `immutableImageOf` byte/stream decode helpers needed bounded external-input overloads. |
| #256 | `images-spring-boot` | P2 | `LocalImageStorage.download(key, destination)` missed the documented `maxSizeBytes` precheck. |
| #257 | `images-spring-boot` | P2 | CloudFront key-source configuration used `check` for caller input validation. |
| #258 | `images-spring-boot` | P2 | S3 timeout/retry and upload metadata/cache-control contracts were not aligned across properties, docs, and implementation boundaries. |
| #259 | `images-ktor` | P2 | Malformed thumbnail payload decode failures could escape the route's bad-request mapper. |
| #260 | `images-vips-java21`, `images-vips-java25` | P2 | Path-based Vips loaders split file validation from decode, leaving a replacement race window. |
| #261 | `images-captcha` | P3 | The default in-memory CAPTCHA challenge store lacked stale-entry cleanup guidance and bounds. |

## Lower-Risk Follow-Ups

- Public KDoc still contains Korean text in several existing `images` and
  `images-vips-*` APIs. This does not block the safety fixes, but future public
  API touchpoints should convert touched KDoc to English.
- Benchmark-native lifecycle cleanup was reviewed. Current benchmark code
  intentionally avoids `VipsRuntime.shutdown()` because the runtime shutdown
  contract is terminal; the note was treated as documentation risk rather than
  a functional fix in this stack.
- Existing Backlog benchmark issues (#197, #200-#208) already cover several
  performance-benchmark expansion topics and were not duplicated in milestone
  0.4.0.

## Verification Plan

1. Run targeted tests for each touched module after its branch is patched.
2. Run the full repository test suite after the stack is assembled.
3. Verify every PR body, milestone, labels, and assignee with live `gh pr view`
   before requesting merge.
