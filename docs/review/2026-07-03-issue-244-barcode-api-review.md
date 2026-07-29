# Issue #244 Barcode API 검토

## Step 2-R / 3-R 설계와 계획 검토

- 범위: `docs/superpowers/specs/2026-07-03-issue-244-barcode-api-design.md`,
  `docs/superpowers/plans/2026-07-03-issue-244-barcode-api-plan.md`
- Workflow: Type A Full Feature, new published API module.
- 검토 방식: 이 session surface에서는 native subagent tool을 사용할 수 없어 local-equivalent review로 진행했다. architect, security, SRE, planner, test, build, API/user, integration lens로 검토했다.
- Repo evidence:
  - CodeGraph stats for this worktree reported `Files: 0`; symbol lookup
    fallback used direct source inspection.
  - `images-ocr` keeps Tess4J/Tesseract behind an opt-in module.
  - `images` exposes `immutableImageOf(...)` overloads for `ByteArray`,
    `InputStream`, Okio `Source`, and `Path`.
  - `images-ocr` has suspend dispatcher and cancellation-before-start tests.

## 발견 사항

- P0: 0
- P1: 0
- P2: 1 fixed before implementation

### 수정한 P2

- 첫 plan version은 barcode API의 suspend cancellation propagation coverage를 명시적으로 요구하지 않았다. 이제 spec과 plan은 `CancellationException` propagation test를 요구한다.

## 게이트 판정

PASS. API/provider split, no-decoder-dependency boundary, module registration scope, README locale parity, workflow validation, TDD plan은 구현 가능하다. module skeleton과 RED test로 진행한다.

## Step 6-R 구현 검토

- 범위: `images-barcode-api`, root/module README locale set, `settings.gradle.kts`,
  repo-local `AGENTS.md`, and GitHub workflow registration.
- 검토 방식: performance, stability, security, operator/Ops, developer/API, user/caller, main integration lens를 사용한 local 7-Tier review.
- Native subagent: 이 session surface에서 사용할 수 없어 main session이 local-equivalent review를 수행하고 fallback을 기록한다.

### 발견 사항

- P0: 0
- P1: 1 fixed
- P2/P3: 0

### 수정한 P1

- `BarcodeResult.rawBytes` is a `ByteArray?`. Kotlin data class equality would
  compare arrays by reference and surprise API callers. `BarcodeResult` now
  implements content-based `equals`/`hashCode`, and
  `BarcodeModelsTest.result validates text and raw metadata` verifies equal
  byte content across distinct arrays.

### 관점 메모

- 성능: API module에는 decoder, background work, cache, shared mutable provider state가 없다. concurrency stress helper는 필요하지 않다.
- 안정성: suspend wrapper는 `withContext(dispatcher)`를 사용하고 cancellation-before-start와 provider-thrown `CancellationException`을 모두 테스트한다.
- 보안: exception은 sanitized caller-supplied message만 담고, metadata는 string-only이며 non-blank key/value를 검증한다.
- Operator/Ops: CI와 Nightly는 dedicated module job을 갖고, Nightly는
  `coverage-images-barcode-api`를 업로드한다.
- 개발자/API: public API는 provider-neutral이고 English KDoc을 가진다.
  `fun interface`는 SAM ergonomics를 유지하며, Kotlin이 `fun interface` abstract
  method의 default parameter를 금지하므로 default-options ergonomics는 extension
  overload로 제공한다.
- 사용자/호출자: README locale set은 concrete ZXing factory가 이미 존재한다고 주장하지
  않으면서 API/provider split을 문서화한다.

## 최종 검토 판정

PASS. `rawBytes` equality fix 이후 P0/P1 = 0이다.
