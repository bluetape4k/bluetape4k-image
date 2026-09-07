# Issue #638 PaddleOCR trusted producer 검증 기록

## 검증 기준

- 기준 브랜치: `origin/develop`
- 기준 base: `24a34f6563a0ad596a4110e0a944a61f15ff783d`
- 구현 exact HEAD: `14448ad501c6924e7754445d861ff2b37d23ce43`
- 이슈: `#638 ci: PaddleOCR trusted image/model producer pipeline과 registry 권한 구축`
- milestone: `1.1.0`

문서 커밋은 검증 대상 구현 HEAD 뒤에 추가한다. 아래 결과는 구현 HEAD에서 새로
실행했으며, 이전 커밋이나 다른 workflow run의 결과를 대체 증거로 사용하지 않았다.

## 로컬 검증 결과

| 검증 | Python 3.9 | Python 3.13 | 결과 |
| --- | ---: | ---: | --- |
| producer contract | 70 | 70 | PASS |
| registry/retry contract | 10 | 10 | PASS |
| evidence/materialization contract | 13 | 13 | PASS |
| service boundary | 8 | 8 | PASS |
| acceptance receipt | 22 | 22 | PASS |
| offline smoke | 25 | 25 | PASS |

각 Python 버전에서 총 148개 테스트가 통과했다. receipt suite가 negative fixture에서
출력하는 `FAIL: receipt.status must be PASS for an acceptance receipt` 한 줄은 의도한
거부 경로이며 suite exit code는 0이다.

추가 검증 결과:

- workflow contract: 10 tests PASS
- runbook contract: 5 tests PASS
- README parity: 2 tests PASS
- `actionlint`: PASS
- `ruff check`: PASS
- `gitleaks detect --redact --no-git`: 약 5.91 MB 검사, leak 0
- `validate-inputs`: `status=PASS`
- Docker/producer JSON parse: PASS
- Python 3.9/3.13 import smoke: PASS
- `git diff --check`: PASS
- 최신 `origin/develop` 통합: 0 behind / 14 ahead

## 검증된 핵심 계약

- input lock, legal inventory, trust policy는 canonical bytes와 SHA-256으로 결합된다.
- source/model/archive는 크기, 경로, link, host allowlist, digest 경계를 벗어나면
  fail closed한다.
- build는 `linux/amd64`, `--network=none`, `--no-cache`, same-run artifact binding을
  요구한다.
- staging, release, evidence, attestation, public read-back은 같은 digest chain을
  사용한다.
- RECONCILE은 prior state가 기록한 네 문서 hash를 실제 artifact bytes와 다시
  비교하고 현재 package read-back과 일치해야 진행한다.
- incident는 reconciliation marker로 한 번만 연결되며 live GitHub comment를 다시
  읽는다.
- rollback은 mutation 직전에 package ID, private config mode, 현재 `stable` digest를
  재검증한다.
- 각 job summary는 GitHub `job.status`와 단계별 producer status를 기록하고,
  terminal 판정은 finalizer artifact가 담당한다.

## 외부 검증 상태

다음 항목은 로컬 PASS로 대체하지 않았다.

| 항목 | 상태 | 필요한 증거 |
| --- | --- | --- |
| PR exact-head CI | PENDING | remote branch와 PR 생성 후 terminal checks |
| GitHub-hosted producer dispatch | PENDING | 별도 dispatch 승인과 exact run ID |
| GHCR staging/release/evidence | PENDING | exact digest, visibility, anonymous pull read-back |
| GitHub Artifact Attestations | PENDING | exact workflow/head/run identity receipt |
| protected environment | PENDING | live environment/ruleset read-back |
| retention 최소값 | PENDING | GitHub 설정 화면 또는 지원 API의 read-back |
| 실제 model download 및 OCI build | PENDING | 승인된 producer run의 build/evidence receipt |

이 외부 항목들은 PR 생성이나 producer dispatch 권한을 뜻하지 않는다. 현재 stop
condition은 로컬 구현 검증과 P0/P1 해소까지이며, 외부 mutation은 별도 gate에서
수행한다.
