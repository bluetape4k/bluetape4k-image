# Issue #638 PaddleOCR trusted producer 코드 리뷰

## 범위와 provenance

- base: `24a34f6563a0ad596a4110e0a944a61f15ff783d`
- exact implementation HEAD: `14448ad501c6924e7754445d861ff2b37d23ce43`
- 대상: `git diff 24a34f6563a0ad596a4110e0a944a61f15ff783d..14448ad501c6924e7754445d861ff2b37d23ce43`
- 방식: primary agent inline exact-diff review

별도 reviewer provenance는 만들지 않았다. 현재 실행 lane에서 독립 reviewer를
실행하지 않았으므로 이 문서는 독립 리뷰로 표기하지 않는다. 대신 performance,
stability, security, operator/Ops, developer/API, user/caller의 여섯 관점을 각각
검토하고 모든 P0/P1 finding을 구현 HEAD에 반영한 뒤 전체 테스트를 다시 실행했다.

## 최종 판정

- P0: 0
- P1: 0
- P2: 0 open
- P3: 0 open
- 판정: 로컬 기준 PR-ready

## 수정한 finding

| ID | 등급 | 관점 | 발견 | 수정 및 검증 |
| --- | --- | --- | --- | --- |
| R1 | P1 | stability/security | RECONCILE state의 hash 값만 읽고 companion artifact bytes를 다시 확인하지 않았다. | `validate-reconcile-state`가 result/evidence/reconciliation/cleanup 네 파일의 canonical bytes를 hash와 비교한다. workflow artifact도 다섯 파일을 함께 보존한다. tamper test 추가. |
| R2 | P1 | operator/security | incident command가 매번 새 comment를 만들고 read-back은 local reconciliation만 읽었다. | exact reconciliation marker 검색, 20페이지 제한, duplicate reject, existing comment reuse, live comment URL/body/attempt read-back을 추가했다. |
| R3 | P1 | stability/Ops | rollback 승인 뒤 현재 `stable` digest가 바뀌어도 visibility/tag mutation을 시작할 수 있었다. | mutation 직전에 package ID와 live `stable` digest를 승인값과 비교한다. drift 시 mutation 0회 회귀 테스트를 추가했다. |
| R4 | P1 | stability | GitHub API retry policy가 timeout에만 연결되고 실제 408/429/5xx `gh` 실패는 즉시 거부됐다. | bounded stderr에서 HTTP status만 분류하고 2초/4초 재시도 계약에 연결했다. 500 두 번 뒤 성공 및 404 non-retry test를 추가했다. |
| R5 | P1 | operator | 모든 job summary가 성공 여부와 무관하게 `FAILED/NONE`을 기록했다. | `job.status`를 별도 기록하고 성공 단계별 producer status/stage, 실패, 취소를 구분했다. finalizer를 terminal truth로 유지했다. |

## 여섯 관점 검토

### Performance

HTTP page, total bytes, document depth/entry, archive file/tree, request/response,
model tree 크기가 모두 상한을 가진다. registry/API pagination은 최대 20페이지이며
evidence materialization도 전체 deadline과 file allowlist를 적용한다. 모델 자체와
법적 inventory가 큰 점은 의도한 offline image 비용이며 unbounded read 경로는 찾지
못했다.

### Stability

API transient retry, operation deadline, exact run/attempt selection, same-run artifact
binding, cleanup aggregate, terminal finalizer, RECONCILE read-back이 서로 분리돼 있다.
runner loss나 malformed/duplicate fragment는 PASS로 승격되지 않는다. 새 base
`24a34f65`는 build signing 코드만 변경해 producer surface와 충돌하지 않았다.

### Security

workflow 기본 permission은 `{}`이고 job별 최소 permission을 사용한다. action은 full
SHA로 고정되며 checkout credential을 보존하지 않는다. public verification은
credential 환경을 제거한다. archive traversal/link, path escape, private/rebound IP,
secret echo, digest swap, duplicate JSON key를 거부한다. incident/rollback mutation은
exact approval marker 뒤에 있고 live state drift를 다시 검사한다.

### Operator/Ops

runbook은 dispatch 전 snapshot, exact run selection, stage wait, final read-back,
RECONCILE, incident, retention, rollback을 명령 단위로 제공한다. job summary와 terminal
artifact의 역할을 분리했고 quarantine은 signed revocation/evidence 없이 종결되지
않는다. retention 설정은 현재 GitHub REST 응답만으로 증명할 수 없어 명시적으로
PENDING이다.

### Developer/API

CLI는 Python 3.9/3.13 stdlib 호환이며 machine output은 canonical one-line JSON이다.
provider 기능은 `paddle_ocr_producer_lib`의 contracts/filesystem/registry/evidence/
lifecycle 경계로 분리돼 있다. entrypoint 파일이 큰 점은 현재 command surface를 한
곳에서 검색할 수 있게 유지한 선택이다. 첫 live run 전에 추가 분할하면 parser와
운영 명령의 변경 범위가 커지므로 이번 issue에서는 기존 모듈 경계를 유지했다.

### User/Caller

README와 runbook은 trusted producer 구축과 PaddleOCR adoption 승인을 구분한다.
`PRODUCER_PASS`도 승인된 public read-back 전에는 issue #609 채택 근거가 되지 않는다.
caller가 소유하는 fresh approval, downstream notification, dispatch, visibility,
incident, rollback gate를 문서에 명시했다.

## 잔여 위험

- 실제 GitHub-hosted runner, protected environment, GHCR, attestation service의 동작은
  로컬 fixture로 증명할 수 없다.
- native model download와 OCI build 시간/용량은 첫 승인된 producer run에서만 확인할
  수 있다.
- 별도 독립 reviewer provenance는 없다. PR exact-head CI와 reviewer read-back은 PR
  gate에서 새로 확인해야 한다.

이 위험은 로컬 구현 finding이 아니며 외부 검증 전에는 PASS로 승격하지 않는다.
