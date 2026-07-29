# bluetape4k-image 7계층 검토

날짜: 2026-07-04
마일스톤: 0.4.0
기준선: develop at `f901b4e`

## 범위

repository를 module과 package 단위로 `bluetape4k-code-patterns` 및 workspace 7-Tier 관점(correctness, security/input, architecture, performance/stability, tests, public API documentation, release readiness)에 따라 검토했다.

## 발견 사항 Filed

| Issue | 모듈 | 우선순위 | 발견 사항 |
|---|---|---:|---|
| #255 | `images` | P2 | Core `immutableImageOf` byte/stream decode helpers needed bounded external-input overloads. |
| #256 | `images-spring-boot` | P2 | `LocalImageStorage.download(key, destination)` missed the documented `maxSizeBytes` precheck. |
| #257 | `images-spring-boot` | P2 | CloudFront key-source configuration used `check` for caller input validation. |
| #258 | `images-spring-boot` | P2 | S3 timeout/retry and upload metadata/cache-control contracts were not aligned across properties, docs, and implementation boundaries. |
| #259 | `images-ktor` | P2 | Malformed thumbnail payload decode failures could escape the route's bad-request mapper. |
| #260 | `images-vips-java21`, `images-vips-java25` | P2 | Path-based Vips loaders split file validation from decode, leaving a replacement race window. |
| #261 | `images-captcha` | P3 | 기본 in-memory CAPTCHA challenge store에는 stale-entry cleanup guidance와 bound가 없었다. |

## 낮은 위험의 후속 작업

- 여러 기존 `images`와 `images-vips-*` API의 public KDoc에는 아직 한국어 text가 있다. 이는 safety fix를 막지는 않지만, future public API touchpoint는 touched KDoc을 English로 전환해야 한다.
- benchmark-native lifecycle cleanup을 검토했다. 현재 benchmark code는 runtime shutdown contract가 terminal이므로 의도적으로 `VipsRuntime.shutdown()`을 피한다. 이 note는 이 stack에서 functional fix가 아니라 documentation risk로 처리했다.
- 기존 Backlog benchmark issue(#197, #200-#208)가 여러 performance-benchmark expansion topic을 이미 다루므로 milestone 0.4.0에 중복 등록하지 않았다.

## 검증 계획

1. Run targeted tests for each touched module after its branch is patched.
2. Run the full repository test suite after the stack is assembled.
3. Verify every PR body, milestone, labels, and assignee with live `gh pr view`
   before requesting merge.
