# Issue #638 trusted producer 구현 계획 검토

## 검토 범위와 근거

| 항목 | 값 |
|---|---|
| 대상 plan | [`2026-09-07-issue-638-trusted-producer-implementation.md`](../plans/2026-09-07-issue-638-trusted-producer-implementation.md) |
| 대상 spec | [`2026-09-07-issue-638-trusted-producer-design.md`](../specs/2026-09-07-issue-638-trusted-producer-design.md) |
| 대상 issue | [#638 PaddleOCR trusted image/model producer pipeline과 registry 권한 구축](https://github.com/bluetape4k/bluetape4k-image/issues/638) |
| 기준 branch/commit | `develop` / `d6d6c2f30ed18e666090e5e39164b890e1fea4df` |
| 독립 검토 snapshot SHA-256 | `7ede9d5ba906f77b4db1a0f7328d0b1439ec757594edaffeb9a1155d829e8c19` |
| 통합 완료 plan SHA-256 | `4593b9ea718137af57cccddc06ba7454dc1a376c02b893070e375130e9bd7672` |
| 변경 범위 | 구현 계획과 검토 문서만 포함한다. producer 구현, PR, workflow dispatch, GHCR push와 visibility 변경은 포함하지 않는다. |

성능, 안정성, 보안, 운영, developer/API, 사용자/caller의 여섯 관점이 같은 계획을
독립적으로 검토했다. finding을 계획에 반영한 뒤 영향받은 관점을 다시 검토했으며,
최종 판정은 P0/P1/P2/P3 finding이 모두 0건일 때만 PASS로 처리했다.

## 독립 관점별 최종 판정

| 관점 | 최종 판정 | P0 | P1 | P2 | P3 | 주요 확인 사항 |
|---|---:|---:|---:|---:|---:|---|
| 성능 | PASS | 0 | 0 | 0 | 0 | file/page/aggregate 크기와 operation/run deadline, polling, cache-disabled build receipt가 bounded contract에 포함됐다. |
| 안정성 | PASS | 0 | 0 | 0 | 0 | attempt lifecycle, exactly-once cleanup, terminal result, interruption과 `RECONCILE` 경계가 task와 test에 연결됐다. |
| 보안 | PASS | 0 | 0 | 0 | 0 | SSRF, archive/path traversal, no-follow materialization, token redaction, 최소 job permission, action SHA와 legal hash를 fail closed한다. |
| 운영 | PASS | 0 | 0 | 0 | 0 | step summary, incident 후보, retention, quarantine, known-good rollback rehearsal과 destructive gate가 runbook 및 workflow task에 포함됐다. |
| developer/API | PASS | 0 | 0 | 0 | 0 | canonical result, named error contract, Python 3.9/3.13 import, same-run OCI handoff와 IMAGE/LEGACY API 확장을 exact test로 고정했다. |
| 사용자/caller | PASS | 0 | 0 | 0 | 0 | `verify-attempt` 12-key result, nested `sourceDateEpoch`, portable verifier, README parity와 상태별 후속 조치가 명시됐다. |

## 주요 finding과 disposition

| 관점 | finding | 반영 결과 |
|---|---|---|
| 성능 | registry/API와 artifact materialization이 무제한으로 커지거나 build cache가 재현성 판단을 흐릴 수 있었다. | 공통 bounded HTTP client, page/item/byte/deadline 상한, streaming hash/materialization, cache-disabled duration receipt와 negative fixture를 각 구현 task에 넣었다. |
| 안정성 | executor 제출 전후 중단, partial staging과 rerun이 cleanup/result를 중복 또는 누락할 수 있었다. | attempt identity, cleanup fragment merge, terminal finalizer, same-head run selection과 six-field `RECONCILE` 입력을 순서가 있는 lifecycle test로 고정했다. |
| 보안 | remote redirect, archive entry, OCI foreign layer, raw error와 workflow 권한이 trust boundary를 넓힐 수 있었다. | HTTPS host/redirect allowlist, root-pinned no-follow materializer, foreign layer 거부, redacted typed error, job별 최소 permission과 full action SHA 검증을 추가했다. |
| 운영 | incident, evidence 보존, rollback과 package visibility 변경의 책임 및 승인 경계가 불충분했다. | workflow는 incident 후보와 dry-run만 만들고 `issues: write`를 갖지 않는다. 90일/3년/30일 retention, owner acknowledgement, rollback read-back과 외부 mutation별 fresh approval을 runbook에 고정했다. |
| developer/API | Python 3.9 annotation, retry stage, OCI artifact identity, legal inventory, CI path, 기존 smoke API 통합이 불완전했다. | 모든 새 Python module의 future import, `error.stage`, `paddleocr-oci-<attemptId>`, legal COPY/startup hash, smoke/CODEOWNERS path fixture, 기존 API의 optional snapshot branch를 추가했다. |
| developer/API | IMAGE positive test가 `<host>:/models:ro`를 놓치고 import smoke가 CLI/service를 실제로 읽지 않았다. | Docker argv 전체 exact tuple과 모든 `:/models:ro` suffix를 검사하고, Python 3.9/3.13에서 contracts, smoke, CLI, service를 모두 import하도록 수정한 뒤 재검토 PASS를 받았다. |
| 사용자/caller | generic envelope와 producer result, root/nested source epoch, 실패 상태 후속 조치가 혼동될 수 있었다. | `verify-attempt`만 exact 12-key schema를 쓰고 다른 command는 generic JCS envelope를 쓴다. `packages[].buildToolchain.sourceDateEpoch`와 상태/action 표를 명시했다. |

## Main-session integration 판정

- 12개 task와 62개 step이 strict primitive, immutable input, lifecycle, OCI/evidence,
  image, workflow, staging, attestation, recovery, docs, exact-head 검증, 승인된 외부 실행
  순서로 연결된다.
- AC-01~08은 구현 task와 검증 명령 또는 evidence에 모두 연결된다.
- 각 task는 `Files`, RED, GREEN, 실행 명령, 예상 결과와 Lore commit 경계를 포함한다.
- Python package 추가 없이 stdlib-only 도구로 제한하고 Python 3.9/3.13 import/test matrix를
  둔다.
- PR, merge, workflow dispatch, 최초 GHCR push, package visibility, rollback과 issue/comment
  mutation은 구현과 분리된 gate로 남는다.
- #609 offline acceptance와 #611 consumer 검증은 #638 producer 성공만으로 완료 처리하지
  않는다.

## 검증 증거

- 34개 `bash` code block을 `bash -n`과 `zsh -n`으로 검사했다.
- 14개 `python` code block을 `compile(..., "exec")`로 검사했다.
- plan과 review의 local Markdown link 대상이 존재하는지 검사했다.
- `TODO`, `TBD`, `FIXME`, `VALIDATE_ONLY`, `args.stage`,
  `load_service_config_bytes`, GNU `timeout` 실행과 hard-coded `python3.9` 명령이 없는지
  검사했다.
- 12개 task, 62개 step, 8개 AC row를 다시 세고 `git diff --check`를 실행했다.
- #638이 OPEN, assignee `debop`, milestone `1.1.0`인지 live GitHub에서 다시 읽어 확인했다.

## SPW-01~05

| 항목 | 결과와 근거 |
|---|---|
| SPW-01 대상·목적·근거 | PASS — zero-context 구현자에게 issue, 승인 spec, base SHA, 파일 경계와 stop condition을 제공한다. |
| SPW-02 plan contract | PASS — 12개 task에 files, RED/GREEN, code, command, expected result와 commit을 포함한다. |
| SPW-03 한국어 technical register | PASS — 사용자용 설명은 한국어로 작성하고 path, API, digest, status와 command token은 보존했다. |
| SPW-04 추적성 | PASS — AC-01~08, lifecycle, permission, CI, 운영과 외부 gate를 task/검증 증거에 연결했다. |
| SPW-05 최종 read-back | PASS — 여섯 관점 P0/P1/P2/P3=0, 구조, local link, code block syntax, 표식과 whitespace를 확인했다. |

## DoD Status

- [x] 여섯 관점 독립 plan review를 수행했다.
- [x] 모든 P0/P1/P2/P3 finding을 해소하고 영향 관점을 재검토했다.
- [x] 12개 task, 62개 step과 AC-01~08 traceability를 확인했다.
- [x] shell/Python code block, local link, 미해결 표식과 whitespace를 검사했다.
- [x] 구현과 외부 side effect의 별도 gate를 유지했다.
- [ ] 사용자가 구현 실행 방식을 선택한다.

최종 상태: `PLAN REVIEWED / USER EXECUTION CHOICE PENDING`
