# #609-B trusted producer 선택 실행 계획

## 계획 메타데이터

| 항목 | 내용 |
| --- | --- |
| 대상 | [#611 #609-B](https://github.com/bluetape4k/bluetape4k-image/issues/611) |
| Parent | [#609](https://github.com/bluetape4k/bluetape4k-image/issues/609) |
| 선행 | #609-A 문서 계약, PR #610 merge |
| 후속 | #609-C image/model ledger, #609-D attestation, #609-E offline smoke |
| 실행 유형 | Type-E 연구·문서·검증 |
| write scope | research 1개, plan 1개, lesson 1개; production code·dependency·model·Dockerfile 0개 |
| stop condition | producer authority 또는 immutable artifact 증거가 없으면 `BLOCKED`로 종료 |

## 의존 순서

| 단계 | Action | Expected DoD | 실패 시 |
| --- | --- | --- | --- |
| 1 | #609-A ledger/plan과 #545 availability evidence를 읽고 live #609/#547/#169/#513 metadata를 재확인 | 선행 issue·milestone·label·state가 현재 값과 일치 | read-only 보류 |
| 2 | Colima/Docker platform, buildx availability, local digest-pinned image를 확인 | host target, daemon/client version, local inspect exit가 기록됨 | local artifact 없음이면 `BLOCKED` |
| 3 | Docker Hub/Baidu manifest metadata를 인증 경계 안에서 읽고 digest/config/platform을 비교 | candidate digest와 source URL이 기록되고 token/credential은 저장되지 않음 | 401/400/subject 불일치는 trusted evidence로 승격하지 않음 |
| 4 | amd64 trusted CI runner, arm64 builder, authenticated mirror를 비교 | 우선 후보·대안·거부 선택지와 위험을 문서화 | producer authority 미확인이면 `BLOCKED` |
| 5 | #609-C 입력 목록과 no-go를 기록하고 research/plan/lesson SPW-01~05를 완료 | 세 문서의 source·scope·readback·불확실성 표가 PASS | writer 또는 traceability 미완료 시 PR 보류 |
| 6 | 독립 reviewer가 exact final docs를 read-only로 검토 | P0/P1=0, artifact acceptance와 문서 계약을 분리한 verdict | P0/P1이면 수정 후 재검토 |

## 불변 규칙

- `producer.repository`, `workflow`, `workflowRef`, `sourceRevision`,
  `builderIdentity`, `signerIdentity`, `oidcIssuer`는 각 non-empty allowlist와
  policy SHA-256으로 함께 검증한다.
- `linux/amd64` emulation은 `linux/arm64` native acceptance로 기록하지 않는다.
- tag, registry metadata, model card revision만으로 image/model bytes,
  SBOM/provenance/signature/NOTICE를 채우지 않는다.
- credential과 token은 output·문서·commit·issue에 저장하지 않는다.
- #609-C 이후에는 image pull/build와 model download를 별도 승인·receipt gate로
  분리한다.

## 검증 명령

```bash
colima status
docker context show
docker info --format 'Server={{.ServerVersion}} OSType={{.OSType}} Architecture={{.Architecture}}'
docker buildx version
docker image inspect 'paddlepaddle/paddle@sha256:854aa259b6ced0b9c8f2eabb4e0f1314d7dff3e6ddd57cb018035c39eb2c86ce'
```

registry 조회는 access token을 화면과 artifact에 남기지 않는 bounded Python
readback으로 수행하고, manifest/config summary만 문서에 기록한다. 이 명령은
image layer나 model bytes를 pull하지 않는다.

## 롤백과 재실행

- 문서 오류는 branch에서 수정하고 같은 readback·writer audit을 다시 실행한다.
- registry 권한이 없으면 현재 digest를 지우지 않고 `BLOCKED` evidence로 남긴다.
- 잘못된 producer를 선택한 경우 ledger를 채우지 말고 trustPolicy를 초기화한 뒤
  독립 reviewer를 다시 요청한다.
- 이 단계에서 production code나 dependency가 필요해지면 Type-A/B 별도 workflow로
  재분류하고 현재 PR train을 중단한다.

## SPW-01~05 writer DoD

| 항목 | 결과 | 근거 |
| --- | --- | --- |
| SPW-01 대상·승인·경계 | PASS | #611/#609와 exact write scope, stop condition을 고정 |
| SPW-02 plan 계약 | PASS | dependency order, action, evidence, failure, rollback, review gate를 표로 정의 |
| SPW-03 한국어 technical register | PASS | 명령·식별자·상태 코드를 보존하고 불확실성을 완화하지 않음 |
| SPW-04 source·readback | PASS | #609-A/#545 문서, live GitHub, local Docker/registry commands와 단계별 결과를 연결 |
| SPW-05 최종 readback | PASS | 단계 순서, code fence, 표, N/A/Blocked 경계를 재확인 |
