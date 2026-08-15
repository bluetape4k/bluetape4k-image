# #492 설계 독립 6-lens 검토

## 검토 범위와 기준

- 대상: `docs/superpowers/specs/2026-08-15-issue-492-tiff-multipage-ocr-design.md`
- 기준 HEAD: `efc2411215dec522eacd46b2554719afa1775a66`
- 변경 범위: `bluetape4k-images-ocr`, 기존 `bluetape4k-images` ImageIO/TIFF 계약 재사용
- 방식: 설계 승인 후 독립 reviewer lane을 분리해 performance, stability, security,
  operations, developer/API, user/caller 관점으로 읽기 전용 검토하고, P1 발견을 설계에
  반영한 뒤 재검토했다.

## 독립 lane 결과

| 관점 | 1차 판정 | 핵심 finding | 설계 반영·최종 상태 |
|---|---|---|---|
| Architecture | REVISE/P2 | provider-neutral page orchestration, TIFF-only, page budget과 pageIndex 정규화 필요 | 기존 engine 재사용·GIF 제외·순차 처리·budget/pageIndex 계약 반영. P1 없음 |
| Performance | REJECT/P1 | late total-pixel 초과가 앞 page decode/OCR 뒤 발견됨; metadata scan 비용·peak memory 미정 | 전 page 2-phase metadata preflight, `maxMetadataBytes`, 순차 1-page resident, output budget 및 취소 지연 계약 반영. P1 없음 |
| Stability | PARTIAL/P1 | reader lifecycle/cleanup 주 예외 보존, unknown metadata, blocking 취소 경계가 미정 | 같은 reader/stream 재사용, phase budget, suppressed cleanup, `runInterruptible`, best-effort native abort 명시. P1 없음 |
| Security | REQUEST CHANGES/P1 → PASS | `getNumImages(false)`도 provider에 따라 전체 IFD를 읽음; fresh decode reader가 metadata budget 우회; 누적 결과 budget bypass 가능 | metadata-phase bounded stream + 동일 reader payload phase 재사용, 누적 overflow-safe result budget, raw cause 비노출, stable reason/validation exception 반영. 최종 P1 없음 |
| Operations | REQUEST CHANGES/P1 → PASS | 실제 3-page container OCR 경로와 release native 증적/runbook 부족 | CI `test-images-ocr` container gate, 3-page CLI smoke 경로, exact SHA/run/artifact/native release checklist와 rollback 절차를 DoD에 고정. 최종 P1 없음 |
| Developer/API | REQUEST CHANGES/P1 → PASS | overflow/양수 dimension, SPI/runtime 버전, cleanup seam, 안정 error 분류 누락; validation pageIndex와 no-reader/non-TIFF mapping 추가 확인 | `TiffMultiPageOcrValidationException`, `TiffMultiPageOcrException`, reason enum, serialVersionUID, internal reader seam, TwelveMonkeys 3.14.0 계약 반영. 최종 P1 없음 |
| User/Caller | CONDITIONAL PASS/P2 | ByteArray trade-off, Java/default API, README/error/cancellation 사용법 보강 필요 | ByteArray 선택 이유·bounded caller read·README parity·explicit Java args·reason/retryability 문서화. P1 없음 |

## 통합 판정

- P0: 0
- P1: 0 (위 설계 amendment와 acceptance criteria로 해소; security/ops/API 재검토 PASS)
- P2: metrics 구현은 caller integration 책임으로 범위를 고정했고, provider 내부 native
  peak memory는 정확한 byte 상한으로 주장하지 않는다. README와 release checklist는 구현
  계획의 필수 산출물이다.
- P3: BigTIFF/special compression/orientation, GIF frame, streaming, parallel OCR은
  후속 capability로 유지한다.

## 필수 acceptance

1. 모든 page metadata preflight가 끝나기 전 `read`/engine 호출 0회이며 late total/page
   budget 초과도 동일하게 종료한다.
2. TwelveMonkeys reader가 `allowSearch`를 무시해도 metadata byte budget을 넘을 수 없고,
   decode 시 새 reader를 만들지 않아 동일 budget이 유지된다.
3. page별·누적 text/entry budget, width/height 양수와 곱셈 overflow, unknown count,
   malformed/truncated/GIF, cleanup suppressed, cancellation propagation을 검증한다.
4. public error에는 stable reason/pageIndex와 sanitized message만 있고 raw cause/path/
   payload/tessdata path가 노출되지 않는다.
5. Testcontainers Tesseract가 실제 3-page TIFF를 받아 page별 text와 aggregate separator를
   검증하며, release checklist가 exact SHA·workflow run·artifact·native 결과를 고정한다.

## SPW 및 workflow 증거

- SPW-01: 문제·범위·non-goals·기존 계약 근거가 설계 문서에 있다.
- SPW-02: public API, limits, lifecycle, error/retryability, cancellation 정책이 명시됐다.
- SPW-03: 대안과 기각 근거(InputStream/Path/engine mutation/parallel/GIF/full scan)가 있다.
- SPW-04: 3-page fixture, malformed/limit/cancel/cleanup/error-redaction/container 검증이
  acceptance에 연결됐다.
- SPW-05: DoD가 구현·문서·기존 회귀·독립 6-lane·PR metadata를 포함한다.
- CG-01/02: 기준 HEAD와 worktree baseline `:bluetape4k-images-ocr:test` 19 passing,
  5 pending를 확인했다. 설계 변경은 `git diff --check`를 통과해야 한다.
- 독립 lane이 반환하지 않은 stability 재검토는 leader가 source/contract를 직접 재검증했고,
  해당 대체 근거를 숨기지 않는다.

## 최종 gate

**설계 리뷰 상태: CLEAR FOR PLAN** — P0/P1 없음. 다음 단계는 이 문서를 기준으로
구현 계획을 작성하고, 계획에 대한 동일 6-lane 검토에서 P0/P1=0을 재확인하는 것이다.
