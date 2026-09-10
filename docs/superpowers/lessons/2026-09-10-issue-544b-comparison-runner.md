# #544-B 비교 runner lesson

## 결정

- canonical v2 manifest의 원문 SHA-256을 runner 시작 시 다시 계산하고, 24개
  positive와 3개 malformed negative의 순서·resource·ground-truth hash·PNG
  dimension을 모두 검증한다.
- Tesseract TSV와 PaddleX `result.ocrResults[0].prunedResult`를 같은 line geometry
  형태로 정규화한다. Paddle raw response와 Tesseract TSV는 fixture별 bounded artifact로
  남기고 receipt에는 text SHA-256만 기록한다.
- 두 provider 모두 cold 1회, warm-up 3회, warm 3회를 실제 수행한다. 반복 결과가
  달라지면 비교 receipt를 발행하지 않는다.
- Paddle 실행은 #609-E의 immutable image/model digest와 #545의 `--network none`,
  non-root/read-only/cap-drop/no-new-privileges/resource limit을 재사용한다. Python
  runner는 production provider adapter가 아니다.
- Kotlin Jackson3 receipt model은 baseline/candidate CER·WER, geometry/outcome
  accuracy, cold/warm latency delta를 optional default field로 확장해 기존 synthetic
  contract test와 실제 비교 artifact를 함께 읽는다.

## 관찰과 재사용

1. empty ground-truth text는 매니페스트에서 bytes `0`으로 합법적이다. 일반 fixture
   reader의 non-empty guard와 분리해 hash만 검증해야 한다.
2. macOS Homebrew Tesseract의 traineddata는 Cellar를 가리키는 symlink일 수 있다.
   모델 digest를 만들 때 symlink target을 허용하되 Homebrew prefix 밖으로 탈출하는
   경로는 거부한다.
3. Docker `exec` stdout에 OCR body와 stderr에 별도 marker를 두면 16 MiB response
   bound를 지키면서 response hash를 확인할 수 있다. marker와 body가 다르면 즉시
   실패한다.
4. malformed negative도 동일 반복 envelope를 거쳐야 오류 latency가 synthetic 값으로
   채워지지 않는다. 모든 반복의 outcome이 같을 때만 `ERROR` row를 남긴다.
5. hosted workflow가 끝나기 전에는 #544 또는 #609를 완료로 닫지 않는다. 실제
   Linux/amd64 receipt와 Kotlin validator 출력이 다음 외부 상태 증거다.

## 후속 gate

- workflow dispatch 후 artifact의 exact head, receipt SHA-256, cleanup/security,
  provider metrics를 Issue #544와 #609에 댓글로 기록한다.
- 비교 결과는 #547 `DEFER`를 자동 변경하지 않는다. 운영 채택은 재현성·SLO·공급망·
  production boundary 검토 후 별도 결정한다.

## 상태

`RUNNER_READY / HOSTED_COMPARISON_PENDING` — local parser, contract test, actionlint,
Kotlin validator는 통과했으며 actual Paddle result는 hosted Linux/amd64 실행에서만
확정한다.
