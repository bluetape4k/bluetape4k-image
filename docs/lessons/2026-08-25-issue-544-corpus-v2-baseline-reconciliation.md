# Issue #544 corpus v2 baseline 정합화 lesson

## 맥락

Issue #544에는 Tesseract v1 baseline receipt가 이미 있었지만, 후속 #565 train에서
corpus v2·CER/WER·cold/warm/RSS protocol이 추가됐다. 두 receipt가 모두 Tesseract
baseline이라고만 쓰이면 v1 raw 수치를 v2 PaddleOCR 비교의 입력으로 잘못 사용할 수
있다.

## 결정

- `bench/ocr-v2/manifest.json`의 SHA-256을 #544의 현재 canonical 입력으로 고정한다.
- 기존 v1 receipt는 삭제하거나 값을 고치지 않고 역사적 artifact로 남긴다.
- #565 full-corpus protocol receipt를 현재 Tesseract 기준선으로 참조한다.
- Tesseract 기준선이 `PASS`여도 PaddleOCR 비교와 adoption은 `PENDING`/`DEFER`로
  유지한다.

## 결과와 검증

- v2 manifest SHA: `99502a59751f68aff19634c33239d0f0e50931a17746621e7384ef169faaebb6`
- protocol receipt: 24개 row, `TEXT` 21개, `EMPTY` 3개, malformed negative 3개
- Tesseract aggregate: `CER=0.04604051565377532`, `WER=0.09897610921501707`
- `validateOcrBenchmarkReceipt`: PASS
- `validateOcrProtocolReceipt`: PASS
- `git diff --check`: PR 전 재실행 대상

## 놓친 점과 다음 guard

처음 #544 receipt는 이후 benchmark corpus가 v2로 전환될 때 자동으로 canonical
상태를 잃지 않았다. 앞으로 benchmark receipt에는 반드시 manifest schema/version,
manifest SHA, coverage와 provider/adoption 상태를 함께 기록하고, corpus가 바뀌면
기존 receipt를 수정하기보다 정합화 문서와 새 run-id를 추가한다.

`replayStatus=PENDING`은 generator 전체 replay가 아직 증명되지 않았음을 뜻한다.
이를 license/provenance PASS나 provider 비교 PASS로 해석하지 않는다.

## Lesson DoD

- context, decision, outcome, verification, miss, future guard를 모두 기록했다.
- source와 raw artifact의 사실·수치·식별자를 보존했다.
- PaddleOCR dependency/model/service/API 범위를 확장하지 않았다.
