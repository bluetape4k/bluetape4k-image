# Issue #607 receipt artifact TOCTOU 7-Tier 검토

## 검토 상태

| 항목 | 값 |
|---|---|
| 대상 | [Issue #607](https://github.com/bluetape4k/bluetape4k-image/issues/607) receipt artifact validator 수정 |
| 기준 base | `develop` @ `119412889124572f3d762c85787b2953af395c09` |
| 검토한 구현 head | `6e04269` (`descriptor 오류 경로 cleanup 보강`) |
| 변경 범위 | `scripts/research/paddle_ocr_receipt.py`, `scripts/research/test_paddle_ocr_receipt.py` |
| 분류 | Type-B bounded bugfix; production OCR/API·Paddle runtime·model bytes 없음 |
| 독립 reviewer lane | **TIMEOUT / NO RESULT** — bounded wait 후 native interrupt, inline fallback 수행 |

이번 검토는 #545 receipt artifact의 ancestor TOCTOU 제거와 기존 fail-closed
계약 보존만 판단한다. 실제 PaddleOCR service/container, trusted image/model,
SBOM, provenance attestation과 #544/#169 비교·채택 gate는 이 변경으로
충족되지 않는다.

## 7-Tier 체크리스트

| Tier | 검토 범위 | 결과·근거 |
|---|---|---|
| 1. 범위·의존성 | issue boundary, 변경 파일, dependency/API 확장 | **PASS** — Python validator/test 두 파일만 변경했으며 새 dependency, Paddle import, production API와 model artifact는 없다. |
| 2. 정확성·파일 identity | root/ancestor/final component, symlink, inode, byte/hash | **PASS** — `O_DIRECTORY | O_NOFOLLOW` root와 component, final `O_NOFOLLOW`, `fstat()` regular-file 확인, 같은 fd의 `os.read()`·SHA-256으로 검사-사용 경계를 단일 descriptor에 고정했다. |
| 3. 실패·계약 보존 | traversal, symlink, tamper, size, log redaction, required artifact | **PASS** — 기존 symlink/traversal·byte/hash tamper·oversize·UTF-8/redaction 테스트가 유지되고 descriptor 경로 regression이 추가됐다. |
| 4. 자원·성능 | read bound, fd lifecycle, error cleanup | **PASS** — 1 MiB chunk와 expected byte bound를 유지하고 성공·검증 실패·`fstat` 실패 경로에서 file/directory fd를 닫는다. 실제 Paddle latency/RSS는 범위 밖이다. |
| 5. 보안·공급망 | residual P2, fail-closed, runtime evidence | **PASS (validator)** / **PENDING (runtime)** — path TOCTOU window를 제거했지만 trusted image/model/SBOM/attestation은 #545 선행 gate다. |
| 6. API·호환성 | public surface, Kotlin pattern, platform | **PASS/N/A** — public Kotlin/API/dependency 변경이 없고 Python CLI 오류 계약을 유지한다. `$bluetape-kotlin-patterns`는 `N/A (Kotlin 변경 0개)`다. POSIX descriptor API는 CI 대상 Linux/macOS 범위에 맞춘다. |
| 7. 문서·CI·release | lesson/review, metadata, hosted checks, merge boundary | **CONDITIONAL** — 한국어 lesson/review와 inline P0-P2 검토는 준비됐고, 독립 lane은 timeout/NO RESULT로 분리했다. PR exact-head hosted CI와 merge 승인은 아직 남아 있다. |

## 독립 reviewer lifecycle

독립 `code-reviewer` lane에 implementation head `6e04269`를 read-only로
할당했다. 범위는 fd-relative `O_NOFOLLOW`, same-descriptor hash, 기존
receipt contract와 Python 품질이었다. bounded wait 동안 결과가 없어
`TIMEOUT / NO RESULT`로 기록하고 native lane을 회수했다. 이 상태를 독립
PASS로 표현하지 않는다.

### Inline fallback result

parent가 동일 exact head를 다시 read-only로 검토한 결과는 다음과 같다.

| Severity | 결과 | 근거 |
|---|---:|---|
| P0 | 0 | production runtime·credential·model 공급망 경계 침범 없음 |
| P1 | 0 | root부터 final component까지 no-follow descriptor traversal과 동일 fd hash가 구현됨 |
| P2 | 0 | 기존 P2 TOCTOU 원인 제거, symlink/tamper/size/redaction 회귀 유지, descriptor-only 회귀 추가 |
| P3 | 1 관찰 | 실제 concurrent ancestor swap stress fixture는 추가하지 않음. nondeterministic stress 대신 descriptor 구조와 path API 차단 regression으로 방어 불변식을 검증했으며 merge blocker로 보지 않음 |

## 남은 위험과 처분

- 실제 PaddleOCR 실행 결과나 공급망 artifact가 생겼다는 의미가 아니다.
- receipt input 자체의 `_load_json()`은 기존 path-based 검사 범위이며, 이번
  Issue #607은 `artifact_root` 아래 referenced artifact의 TOCTOU만 다룬다.
- POSIX `O_DIRECTORY`·`O_NOFOLLOW`가 없는 환경은 지원 대상이 아니며, 해당
  환경에서 조용히 일반 path fallback을 추가하지 않는다.
- P3 stress 관찰은 현재 PR에서 보류한다. 재현 가능한 race harness가 필요해질
  때 별도 issue로 분리하며, 현재 descriptor 경계와 deterministic regression을
  약화하지 않는다.

## 검증 증거

- RED: `Path.resolve()`를 금지한 regression이 기존 `_validate_artifacts()`의
  `resolve()` 호출에서 실패.
- GREEN receipt: `python3 scripts/research/test_paddle_ocr_receipt.py` — 22 passing.
- 기존 smoke: `python3 scripts/research/test_paddle_ocr_smoke.py` — 23 passing.
- `python3 -m py_compile scripts/research/paddle_ocr_receipt.py scripts/research/test_paddle_ocr_receipt.py` — PASS.
- `ruff check scripts/research/paddle_ocr_receipt.py scripts/research/test_paddle_ocr_receipt.py` — PASS.
- `git diff --check` — PASS.
- live Issue #607 — OPEN, assignee `debop`, milestone `1.0.0`, labels `bug`, `ci`.
- hosted PR/CI/merge — 아직 실행하지 않았으며, fresh merge approval 전에는
  merge하지 않는다.

## Writer DoD

- `SPW-01`: PASS — 대상, 독자, exact base/head, source path, reviewer lane과
  미해결 runtime gate를 고정했다.
- `SPW-02`: PASS — 7-Tier evidence, severity, disposition, P3 관찰과 다음
  gate를 포함했다.
- `SPW-03`: PASS — 한국어 기술 문체를 적용하고 API·command·URL·SHA token을
  보존했다.
- `SPW-04`: PASS — Issue #607/#545의 원인·수정·테스트·scope boundary를
  source와 대조했다.
- `SPW-05`: PASS — 최종 Markdown read-back에서 독립 timeout을 PASS로 둔갑시키지
  않았고, hosted CI와 merge approval을 PENDING으로 남겼다.

## Final Status

`CONDITIONAL / VALIDATOR GREEN / HOSTED CI AND MERGE APPROVAL PENDING`
