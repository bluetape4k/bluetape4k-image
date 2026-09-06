# Issue #638 trusted producer 설계 검토

## 검토 범위와 근거

| 항목 | 값 |
|---|---|
| 대상 spec | [`2026-09-07-issue-638-trusted-producer-design.md`](../specs/2026-09-07-issue-638-trusted-producer-design.md) |
| 대상 issue | [#638 PaddleOCR trusted image/model producer pipeline과 registry 권한 구축](https://github.com/bluetape4k/bluetape4k-image/issues/638) |
| live milestone | `1.1.0` |
| 기준 branch/commit | `develop` / `d6d6c2f30ed18e666090e5e39164b890e1fea4df` |
| 변경 범위 | 설계와 검토 문서만 포함한다. workflow 구현, dispatch, GHCR push, visibility 변경은 포함하지 않는다. |

검토는 성능, 안정성, 보안, 운영, developer/API, 사용자/caller의 여섯 관점을
분리해 수행했다. 각 관점은 같은 spec을 독립적으로 읽고 P0/P1/P2/P3 finding을
제출했으며, finding을 반영한 최신 문서를 다시 읽어 최종 판정을 냈다.

## 독립 관점별 최종 판정

| 관점 | 최종 판정 | P0 | P1 | P2 | P3 | 주요 확인 사항 |
|---|---:|---:|---:|---:|---:|---|
| 성능 | PASS | 0 | 0 | 0 | 0 | evidence 파일 수·개별/총 크기·OCI descriptor·API page/body·시간 budget을 bounded contract로 고정했다. |
| 안정성 | PASS | 0 | 0 | 0 | 0 | cleanup fragment, terminal state, `RECONCILE` remote staging 조건과 중단 후 재개 계약을 고정했다. |
| 보안 | PASS | 0 | 0 | 0 | 0 | revocation ancestry, emergency receipt, #611 quarantine, foreign layer 거부와 root-pinned no-follow materialization을 고정했다. |
| 운영 | PASS | 0 | 0 | 0 | 0 | artifact 소비 job의 `actions: read`와 exact image/evidence attempt tag 규칙을 고정했다. |
| developer/API | PASS | 0 | 0 | 0 | 0 | JCS와 opaque bytes 경계, exhaustive lifecycle stage, finalizer hash와 attestation path 계약을 고정했다. |
| 사용자/caller | PASS | 0 | 0 | 0 | 0 | 재현 가능한 ORAS 명령, nested evidence path 생성, mode와 atomic rename 계약을 고정했다. |

## 주요 finding과 disposition

| 분야 | finding | 반영 결과 |
|---|---|---|
| 성능 | evidence 크기와 GitHub/GHCR API 탐색이 무제한으로 커질 수 있었다. | 정확한 11개 evidence file, 파일당 128 MiB, 총 512 MiB, OCI 총 513 MiB, manifest 1 MiB, layer 16개, API 10/30초·2/4 MiB·20 page 상한을 추가했다. |
| 안정성 | 중단 시 local cleanup과 remote staging 유무가 terminal/reconciliation 계약에서 불충분했다. | started job별 cleanup fragment와 final cleanup 검증을 요구하고, release/evidence가 있는데 staging이 없으면 `REJECTED`로 고정했다. |
| 보안 | 현재 revocation ancestry, emergency denial, path traversal와 foreign layer 방어가 충분히 좁지 않았다. | public HTTPS git ancestry, incident-grouped revocation, signed emergency receipt, `urls`/foreign media type 거부, `openat` 기반 root-pinned materializer를 추가했다. |
| 운영 | artifact download job의 repository permission과 attempt tag 계산이 암시적이었다. | 소비 job에만 `actions: read`를 부여하고 cross-run 제한을 추가했다. tag는 run ID/attempt에서 `image-<attemptId>`와 `evidence-<attemptId>`로 유도하도록 고정했다. |
| developer/API | canonical JSON과 raw platform output의 hash 의미, lifecycle finalizer 값이 모호했다. | JCS 대상과 opaque raw bytes를 분리하고 `lastCompletedStage`, non-null finalizer hash, attestation exact path와 `fileManifestSha256` 의미를 고정했다. |
| 사용자/caller | public verifier 명령과 nested evidence materialization이 구현자가 그대로 재현하기 어려웠다. | ORAS `v1.3.4` URL/SHA와 manifest/blob 절차를 고정하고 component별 `mkdirat`/`openat`, directory `0700`, file `0600` 계약을 추가했다. |

## 통합 판정

최신 spec에서 P0/P1/P2/P3 finding은 모두 0건이다. 설계는 구현 계획을 작성할 수 있는
수준으로 승인한다. 다만 이 판정은 문서 계약에 대한 판정이다. AC-07의 실제 producer
runtime 성능/no-egress 검증은 구현 후 승인된 dispatch에서 수행해야 한다. model archive
SHA-256과 model별 재배포 legal inventory도 source lock을 생성하기 전에 채우지 못하면
workflow가 fail closed해야 하는 의도적 사전 조건이다.

## 검증 증거

- spec의 4개 shell block을 `bash -n`과 `zsh -n`으로 검사했다.
- spec과 review의 Markdown 링크를 요청해 HTTP status를 확인했다.
- 미해결 작성 표식이 남지 않았는지 검사했다.
- `test_paddle_ocr_receipt.py` 22개와 `test_paddle_ocr_smoke.py` 23개 test를 실행했다.
- `actionlint`로 현재 workflow syntax를 검사했다.
- `git diff --check`로 whitespace 오류가 없는지 검사했다.
- `gh issue view 638`로 OPEN, assignee `debop`, milestone `1.1.0`을 다시 확인했다.

## SPW-01~05

| 항목 | 결과와 근거 |
|---|---|
| SPW-01 대상·목적·근거 | PASS — 구현자, #611 consumer와 운영자를 독자로 두고 live issue와 upstream/공식 문서를 근거로 삼았다. |
| SPW-02 spec 계약 | PASS — scope, 대안, architecture, data flow, permission, failure state, rollout, AC와 stop condition을 포함했다. |
| SPW-03 한국어 technical register | PASS — 독자용 설명은 한국어로 작성하고 identifier, digest, status, 명령 token은 보존했다. |
| SPW-04 의미·추적성 | PASS — finding을 spec의 계약과 AC-01~08, #609 ledger, #611 consumer boundary에 연결했다. |
| SPW-05 최종 read-back | PASS — 미해결 작성 표식, 모순, scope, Markdown 구조·링크, 명령 syntax와 최신 issue metadata를 다시 확인했다. |

## DoD Status

- [x] 여섯 관점의 독립 spec review를 수행했다.
- [x] P0/P1/P2/P3 finding을 모두 해소했다.
- [x] spec 구조, 링크, shell, receipt/smoke validator와 actionlint를 검증했다.
- [x] #638의 live milestone이 `1.1.0`인지 확인했다.
- [ ] 커밋된 spec에 대한 사용자 검토

최종 상태: `SPEC REVIEWED / USER REVIEW PENDING`
