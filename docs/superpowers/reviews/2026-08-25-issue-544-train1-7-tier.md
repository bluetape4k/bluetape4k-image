# Issue #544 RESEARCH-1 Train 1 7-Tier 독립·inline 검토

## 검토 상태

| 항목 | 값 |
|---|---|
| 대상 | #544 corpus v2 baseline 정합화 문서, 기존 v1 receipt 안내, lesson |
| 기준 base | `develop` @ `c737ed38ac184b1922590ab256c484030f38a9cd` |
| 변경 유형 | Type-E 연구·문서 정합화. production Kotlin/dependency/model/service/API 변경 없음 |
| 독립 reviewer lane | `TIMEOUT` — 90초 이상 응답이 없어 중단; 독립 PASS로 집계하지 않음 |
| inline fallback | P0 0건, P1 0건, P2 0건, P3 0건 — 7-Tier 재검토 PASS |

## 독립 lane timeout 증거

`/root/issue544_train1_reviewer`를 `code-reviewer` role로 read-only 검토에 할당하고
90초 timeout probe를 보냈다. `list_agents`에서 계속 `running`이었고, bounded
`wait_agent` 두 차례와 status probe에도 결과가 없어서 lane을 중단했다. 따라서 이
문서는 독립 reviewer의 품질 판정을 대체하지 않으며, timeout과 inline fallback을
분리해 기록한다.

## 7-Tier inline 결과

| Tier | 검토 범위 | 결과·증거 |
|---|---|---|
| 1. 정확성 | v1/v2 manifest, protocol/model SHA, row·metric 수치 | `sha256sum`과 raw receipt 대조 PASS. v2 manifest `99502a...`, protocol `592806...`, run `ab1f1...`, model `53c4...` 일치 |
| 2. 범위·계약 | #544 완료 조건, `BASELINE_ONLY`/`PENDING`/`DEFER` 경계 | Paddle 비교·provider 순위·SLO·채택을 주장하지 않고 #545/#547 후속 gate로 남김 PASS |
| 3. 추적성 | old v1 receipt 보존, current v2 canonical ledger, source commit | 기존 receipt를 덮어쓰지 않고 v2 문서와 링크를 추가 PASS |
| 4. 재현성 | validator 명령, artifact chain, replay 상태 | 두 validator가 `BUILD SUCCESSFUL`; generator `replayStatus=PENDING`을 PASS로 승격하지 않음 PASS |
| 5. 문서 품질 | 한국어 자연성, 표·링크·EOF·SPW contract | writer SPW-01~05 PASS, terminology audit `findings=0`, relative-link/EOF audit PASS |
| 6. 안전·변경 경계 | dependency/model auto-download, Python/subprocess/HTTP adapter, public API | 금지선을 문서화했고 파일 변경은 docs/research·lesson·review에 한정 PASS |
| 7. 회귀·운영 | 기존 역사 artifact와 후속 train의 연결, CI 해석 | #544를 닫지 않고 `BASELINE_ONLY` 유지; native/provider 비교와 cross-host matrix를 미완료로 명시 PASS |

## 잔여 위험과 disposition

- 독립 reviewer lane은 timeout으로 `PENDING`이다. 후속 train에서 fresh independent
  review를 다시 실행해야 한다.
- generator 전체 byte replay, PaddleOCR 동일 corpus 비교, cross-provider resource
  matrix와 #545 service/security receipt는 이 문서에서 해결하지 않는다.
- 위 잔여 항목은 P0/P1 결함이 아니라 후속 gate이며, 현재 문서의 `BASELINE_ONLY` /
  `PENDING` / `DEFER` 판정을 변경하지 않는다.

## 검증 증거

- `./gradlew :bluetape4k-images-benchmark:validateOcrBenchmarkReceipt --console=plain` — PASS
- `./gradlew :bluetape4k-images-benchmark:validateOcrProtocolReceipt --console=plain` — PASS
- `git diff --check` — PASS
- `audit-korean-terms.mjs` — PASS, 3 files, `findings=0`
- Markdown EOF 및 repository-relative link audit — PASS
- live `gh issue view 544` — OPEN, assignee `debop`, milestone `1.0.0`, labels
  `documentation`, `test` 확인

## 판정

`PASS — inline 7-Tier 문서 gate 충족 / 독립 reviewer timeout 및 Paddle 비교 gate 미완료`

이번 판정은 v1 receipt와 canonical v2 baseline의 문서 정합화만 승인한다. #544
전체 완료, PaddleOCR 채택, production 구현 또는 merge 승인을 의미하지 않는다.

## Writer DoD

- `SPW-01`: `PASS` — 검토 대상·범위·독립 lane 상태를 고정했다.
- `SPW-02`: `PASS` — Tier별 증거·잔여 위험·disposition을 기록했다.
- `SPW-03`: `PASS` — 한국어 기술 문체와 machine-readable token을 보존했다.
- `SPW-04`: `PASS` — raw receipt와 live issue metadata를 재대조했다.
- `SPW-05`: `PASS` — 최종 문서를 다시 읽고 timeout을 독립 PASS로 표현하지 않았다.
