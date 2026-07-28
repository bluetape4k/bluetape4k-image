# Issue #208 Codec/Runtime Matrix Spec 검토

- 날짜: 2026-07-13
- Artifact: `docs/superpowers/specs/2026-07-13-issue-208-codec-runtime-matrix-design.md`
- Artifact 종류: spec
- Review mode: 여섯 개의 격리된 main-session pass와 integration

이 세션에서 사용할 수 있는 native collaboration interface는 필수 installed `agent_type`을 받지 않는다. label 없는 agent dispatch는 workspace routing contract를 위반하므로 model-routing fallback을 사용했고, main session이 각 관점을 별도 read-only pass로 수행했다.

## 초기 발견 사항

| Priority | 관점 | 근거 | 필요한 수정 | 재실행 lane |
|---|---|---|---|---|
| P1 | 성능 | Sections 7.1과 7.3이 format과 metric은 명명했지만 quality, effort, lossless, metadata, measurement timing을 고정하지 않았다. | 하나의 option/timing profile을 고정하고 lossy WebP와 PNG 사이의 equivalent-quality claim을 금지한다. | performance |
| P1 | 안정성 | Section 7.2가 skipped/unsupported row를 요구했지만 해당 status를 생성하는 artifact를 정의하지 않았다. | fail-closed capability snapshot task를 추가하고 status/error semantic을 정의한다. | stability |
| P1 | 운영자/Ops | Experimental configuration에 정확한 task name이나 preflight ordering이 없었다. | AVIF/HEIC task, capability task, evidence path, execution hold를 명명한다. | operator/Ops |
| P2 | developer/API | Section 6이 center crop/resize를 언급했지만 deterministic ordering이나 source resolution을 정의하지 않았다. | cover-then-center-crop과 정확한 source path/checksum을 정의한다. | developer/API |
| P2 | user/caller | Optional chart wording은 decision rule 없이 유용한 comparison을 생략할 수 있었다. | 최소 두 comparable row가 있으면 chart를 요구하고, 그렇지 않으면 evidence-backed N/A를 요구한다. | user/caller |
| N/A | 보안 | scope는 checked-in fixture, sanitized capability reason, local benchmark output만 사용하며 external input, credential, network, deserialization, publication boundary를 추가하지 않는다. | 없음. fixture integrity와 sanitized diagnostics test를 유지한다. | security |

## 통합 수정

- `quality=85`, `effort=4`, lossy WebP, metadata stripping, one-warmup/three-measurement timing profile을 고정했다.
- `codecMatrixCapabilityReport`와 그 structured JSON evidence, observation/failure semantic, preflight hold를 정의했다.
- stable, AVIF, HEIC Gradle task를 명명하고 default configuration에서 experimental class를 제외했다.
- 정확한 source file, SHA-256 capture, deterministic cover-then-center-crop fixture preparation을 정의했다.
- measurable chart trigger와 evidence-backed N/A rule을 추가했다.

## 재실행 판정

| 관점 | P0 | P1 | 남은 P2/P3 | 판정 |
|---|---:|---:|---|---|
| Performance | 0 | 0 | 0 | PASS |
| Stability | 0 | 0 | 0 | PASS |
| Security | 0 | 0 | 0 | PASS (scoped N/A risk surface) |
| Operator/Ops | 0 | 0 | 0 | PASS |
| Developer/API | 0 | 0 | 0 | PASS |
| User/caller | 0 | 0 | 0 | PASS |
| Main integration | 0 | 0 | 0 | PASS |

최종 spec review convergence: **P0=0, P1=0**.

## Concurrent Commit 이후 독립 재실행

현재 세션이 artifact를 검토하는 동안 commit `d9560bc`가 위 수정 사항을 추가했다. 현재 세션은 해당 commit을 보존하고 정확한 내용을 기준으로 여섯 필수 관점을 다시 실행했다. 재실행 결과 첫 review가 다루지 못한 추가 blocking ambiguity가 발견됐다.

| Priority | 관점 | 근거 | 수정 |
|---|---|---|---|
| P1 | 성능 | Experimental row에 정확한 direction/input byte가 없었고, fixture equality와 JMH protocol이 manifest로 강제되지 않았다. | 네 exact method family, canonical hash-pinned fixture, 하나의 protocol, host/environment equivalence key를 정의했다. |
| P1 | 안정성 | `smokeTestCodec`이 JPEG를 받으면서 AVIF/HEIC smoke라고 보고할 수 있었고, available-but-failed smoke가 `SKIPPED`였다. | same-codec pinned smoke byte, fresh JVM lifecycle, close tracking, blocking `FAILED_SMOKE`를 요구했다. |
| P1 | 보안 | selector typo가 Java 25를 조용히 선택했고 diagnostic sanitization이 부족했다. | exact selector allowlist, requested/actual identity check, fixed reason code, bounded sanitization, leakage scan을 추가했다. |
| P1 | 운영자/Ops | 유일한 capability snapshot이 ignored `build/` 아래 있었고 run manifest나 retry retention contract가 없었다. | append-only tracked raw evidence로 atomic promotion, hash, run manifest, supersession/rollback rule을 추가했다. |
| P1 | developer/API | Experimental task dependency, source-set test seam, direct invocation behavior가 정의되지 않았다. | Gradle-enforced preflight, internal injected `src/main` component, direct-task fail-fast behavior를 추가했다. |
| P1 | user/caller | directional status와 available-but-smoke-failed behavior가 독자를 오도할 수 있었다. | cell-scoped status semantic, shared bilingual legend, reason, rerun guidance를 추가했다. |

선택한 binding-neutral design, module boundary, public API scope, issue acceptance criteria는 바뀌지 않았다. 이 수정은 선택한 design을 executable하고 fail closed하게 만든다.

이 수정 뒤 affected-lens rerun이 필요하다. 해당 rerun이 끝날 때까지 effective convergence state는 **P0=0, P1=PENDING**이다.

## 최종 Affected-Lens 재실행

수정된 artifact를 disk에서 다시 열고 모든 affected perspective를 read-only로 재실행했다. 여전히 blocking인 finding은 수정했고 affected lane을 다시 실행했다. 현재 diff를 확인하지 않은 agent assertion은 수용하지 않았다.

| 관점 | P0 | P1 | 남은 P2/P3 | 판정 |
|---|---:|---:|---:|---|
| Performance | 0 | 0 | 0 | PASS |
| Stability | 0 | 0 | 0 | PASS |
| Security | 0 | 0 | 0 | PASS |
| Operator/Ops | 0 | 0 | 0 | PASS |
| Developer/API | 0 | 0 | 0 | PASS |
| User/caller | 0 | 0 | 0 | PASS |
| Main integration | 0 | 0 | 0 | PASS |

Main integration은 task name 일관성, preflight와 fixture dependency의 fail-closed 성격, eligibility와 finalized evidence의 분리, `src/main` seam의 vips-free 유지, default task graph에 experimental codec dependency가 추가되지 않음을 확인했다.

Effective final spec review convergence: **P0=0, P1=0**.

## Affected-Lens 재실행 판정

수정된 artifact를 repository benchmark task graph, kotlinx-benchmark 0.4.17 task implementation, issue acceptance criteria에 맞춰 다시 열었다. 남은 integration blocker는 하나였다. fixture preparation, cross-process run identity, task dependency, evidence promotion은 설명됐지만 executable Gradle task boundary가 없었다. spec은 이제 `prepareCodecMatrixFixtures`와 `finalizeCodecMatrixEvidence`를 명명하고, run-ID/no-overwrite contract를 정의하며, native probe를 compile/build/check/test task로 끌어오지 않으면서 dependency를 할당한다.

| 관점 | P0 | P1 | 남은 P2/P3 | 판정 |
|---|---:|---:|---:|---|
| Performance | 0 | 0 | 0 | PASS — timed/profiler run 전반에 하나의 protocol과 manifest가 고정됐다. |
| Stability | 0 | 0 | 0 | PASS — preparation, smoke, timed work, retry, finalization이 fail closed한다. |
| Security | 0 | 0 | 0 | PASS — selector, path, diagnostic, promoted evidence가 constrained 및 sanitized 상태다. |
| Operator/Ops | 0 | 0 | 0 | PASS — exact task, run identity, append-only promotion, rerun point가 할당됐다. |
| Developer/API | 0 | 0 | 0 | PASS — source-set seam, task ordering, direct invocation behavior가 executable하다. |
| User/caller | 0 | 0 | 0 | PASS — 모든 cell에 하나의 scoped status, reason, rerun guidance가 있다. |
| Main integration | 0 | 0 | 0 | PASS — acceptance criteria, hazard, ownership, rollback, proof가 정렬됐다. |

Latest effective spec review convergence: **P0=0, P1=0**.

## Plan-Integration Addendum

Implementation-plan review는 승인된 task contract에서 하나의 cross-backend evidence collision을 발견했다. Java 21과 Java 25 command가 하나의 run ID를 공유하므로 단일 `preflight.json` 또는 `eligibility.json` path가 finalization 전에 다른 backend fact를 overwrite할 수 있었다. spec은 이제 `preflight-<backend>.json`, `eligibility-<backend>.json`, `sizes-<backend>.json`을 할당한다. 이는 storage-key clarification일 뿐이며 selected design, benchmark boundary, acceptance scope를 바꾸지 않는다.

stability, operator/Ops, developer/API, integration lens를 backend-keyed contract에 대해 재실행했다. 각 backend는 immutable evidence slot을 갖고, stable fixture는 hash로 shared 상태를 유지하며, finalization은 last-writer-wins 없이 두 runtime outcome을 증명할 수 있다.

Latest post-plan-integration spec convergence: **P0=0, P1=0**.
