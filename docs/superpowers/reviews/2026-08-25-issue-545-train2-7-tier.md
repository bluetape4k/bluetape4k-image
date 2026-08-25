# Issue #545 Train 2 7-Tier trusted artifact availability 검토

## 검토 상태

| 항목 | 값 |
|---|---|
| 대상 | Train 2 연구 문서, lesson, 위키 보존 note, 기존 receipt/preflight 계약 |
| 기준 base | `develop` @ `5add7facf71cea0b1c0e2bfbbfdb4b29be16a998` |
| 변경 유형 | Type-E 연구·문서·검증. production Kotlin/dependency/model/service/API 변경 없음 |
| `$bluetape-kotlin-patterns` | `N/A` — Kotlin 파일 변경 0개 |
| 결론 범위 | 문서/availability gate만 검토. 실제 Paddle acceptance·adoption은 미완료 |
| 독립 reviewer lane | `CONDITIONAL` — P0 0건, P1 0건, P2 3건, P3 3건. traceability 보완을 요구했고 후속 수정으로 반영 중 |
| leader follow-up | `PASS` — 수정된 문서에 대해 source·receipt·writer·CI 경계를 다시 읽고 F-01~F-06을 `CLOSED`로 확인 |

## 검토 기준과 금지선

이번 검토는 #545의 receipt/preflight contract와 #544 v2 baseline을 기준으로 한다.
검토 대상은 source ledger의 추적성, architecture·offline 경계, 보안·공급망
중단 조건, 한국어 문서 품질이다. 다음 사항은 의도적으로 변경하지 않았다.

- Paddle/PaddleX dependency, model bytes, Dockerfile, service/API 또는 HTTP adapter
- mutable tag pull, first-use model download, external network egress
- SBOM/provenance/attestation 서명 검증을 하지 않은 상태의 acceptance 승격
- #544 비교 결과를 만들거나 #547 `DEFER` 판정을 바꾸는 행위

## 7-Tier matrix (leader self-review)

| Tier | 검토 범위 | 결과·증거 |
|---|---|---|
| 1. 계약·범위 | Issue/epic/train 연결, Type-E 한계, non-goal | PASS — #545·#169·#513·#544·#547와 선행 receipt를 연결하고 production 변경을 제외했다. |
| 2. 보안·공급망 | digest, model identity/bytes 분리, registry auth, egress | PASS — mutable tag와 model auto-download를 거부하고 Baidu `401`을 `BLOCKED`로 유지했다. image/model/SBOM/attestation 부재를 `PASS`로 표현하지 않았다. |
| 3. 정확성·추적성 | 공식 source freshness, hash, manifest architecture, receipt | PASS — serving `1b30c3…`, pyproject `63e8bf…`, registry manifest/config digest·architecture, model revision, 입력 manifest와 raw preflight stderr 지문을 기록했다. |
| 4. 운영·아키텍처 | Colima arm64, offline inspect, 재개 선택지, fail-closed | PASS — local image inspect exit `1`을 재현했고 `amd64` runner/`arm64` build/authenticated registry의 장단점과 중단 조건을 문서화했다. |
| 5. 성능·benchmark | #544 동일 corpus, CPU/GPU, resource/SLO | N/A/PENDING — 실행 가능한 Paddle artifact가 없으므로 benchmark 숫자를 만들지 않고 #544 후속 gate로 남겼다. |
| 6. API·호환성 | public API, Kotlin, dependency, runtime compatibility | N/A — Kotlin/API/dependency 변경 0개. `$bluetape-kotlin-patterns`는 적용 대상이 아니며, 향후 Type-A implementation에서 별도 gate가 필요하다. |
| 7. 문서·CI·release | Korean writer, links, wiki preservation, CI scope | 문서 범위 PASS / PR live CI PENDING — SPW-01~05 경계와 commit-pinned wiki note를 기록했다. docs-only PR checks는 PR live read-back에서 확정한다. |

## 독립 reviewer 초기 findings와 수정 disposition

| ID | 등급 | 관찰 | disposition |
|---|---|---|---|
| F-01 | P2 | Docker 후보가 PaddleOCR service image가 아닌 base-image 후보이며 tag 페이지 정보만으로는 registry manifest/config 추적성이 부족했다. | CLOSED — registry `GET` endpoint, Accept header, HTTP status, manifest digest, config digest/size/architecture와 base-image 한계를 연구 문서에 기록했다. |
| F-02 | P2 | Baidu registry `401` 요청의 endpoint·시각·응답 정보가 부족했다. | CLOSED — redacted `GET` endpoint, 시각, HTTP `401`, `WWW-Authenticate` realm을 기록하고 권한 실패를 artifact 부재로 해석하지 않았다. |
| F-03 | P2 | synthetic preflight receipt에 정확한 실행 명령, 입력 manifest/config hash, raw output 지문이 없었다. | CLOSED — 실행 명령, 3개 입력 SHA-256, stderr/stdout SHA-256, exit code와 hostArchitecture의 수동 환경 필드를 명시했다. |
| F-04 | P3 | 영문 기준 데이터 표현이 terminology audit에서 loanword로 남았다. | CLOSED — `source 기준 데이터`로 교체하고 문서 read-back에 반영했다. |
| F-05 | P3 | wiki 링크가 mutable branch를 가리켰다. | CLOSED — 위키 최신 commit `ea70b8b7374c8cc1aa70e82fe0129f1c128ed1e6`에 고정했다. |
| F-06 | P3 | PR live CI 확인 전 Tier 7을 전체 PASS로 표현했고 untracked 문서에 일반 diff check만 사용했다. | CLOSED — 문서 범위 PASS와 PR live CI PENDING을 분리하고 staged/no-index 검증을 PR gate에 추가했다. |

위 6건은 독립 reviewer의 초기 조건부 판정에서 나온 traceability·writer 보완이다.
문서 수정 후 leader가 같은 source와 receipt를 재검토했으며, 실제 image/model/
SBOM/attestation이 없는 상태라는 후속 운영 gate 자체는 여전히 `PENDING/BLOCKED`
이다. 이는 review 결함을 닫았다는 뜻이지 Paddle adoption을 승인했다는 뜻이 아니다.

## 독립 reviewer lane

`code-reviewer` role의 별도 read-only lane을 issue-545-train2 worktree의 세 문서와
공식 source ledger에 배정한다. reviewer는 다음을 확인하고 write scope 없이 결과만
반환한다.

1. source hash·architecture·offline receipt가 실제 주장과 일치하는가
2. P0/P1 보안·공급망·범위 누락이 없는가
3. P2/P3 후속 항목이 `PENDING/BLOCKED/DEFER` 경계를 침범하지 않는가
4. 한국어 문서·링크·SPW-01~05와 7-Tier matrix가 재현 가능한가

초기 lane은 bounded window 안에 결과를 반환했으며, `CONDITIONAL` 결과와 6개
finding을 수신했다. 수정 후 재검토 요청은 bounded observation window 안에 새
결과를 내지 않아 중단했으며, reviewer의 initial result를 변경하지 않았다. 대신
leader가 각 수정 disposition과 source/receipt를 inline으로 다시 읽어 `PASS`를
별도로 기록했다. 독립 결과를 leader follow-up PASS로 덮어쓰지 않는다.

## 검증 계획

- `git diff --check` 및 markdown EOF/fence/relative-link 검사
- receipt 21개, smoke preflight 23개 단위 테스트 재실행
- Korean terminology audit와 문서 read-back
- 변경 경로가 연구/lesson/review 세 파일뿐인지 NUL-safe 목록으로 확인
- live issue #545 metadata, PR metadata/checks/review threads를 PR 단계에서 확인

## 잠정 판정

`PASS (문서 gate) — 독립 초기 P0=0/P1=0/P2=3/P3=3; leader inline follow-up에서 6건 CLOSED, PR live gate 대기`

이 문서는 trusted artifact availability를 기록하는 문서 gate만 승인한다. 실제
Paddle service 실행, model quality, performance, SBOM/attestation 검증,
production adoption 또는 #547 종결을 의미하지 않는다.

## Writer DoD

- `SPW-01`: PASS — 대상·독자·source·범위를 검토 상태에 고정했다.
- `SPW-02`: PASS — 7-Tier matrix와 P2/P3 disposition을 연결했다.
- `SPW-03`: PASS — 한국어 기술 문체와 machine token을 보존했다.
- `SPW-04`: PASS — 독립 reviewer의 6개 traceability finding과 수정 후 source/receipt read-back을 연결했다.
- `SPW-05`: PENDING — PR live metadata/checks와 최종 `Required checks`를 DoD에 연결한다.
