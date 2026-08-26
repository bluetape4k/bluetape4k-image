# #544 provider 비교 receipt 계약 lesson

## 맥락

Issue #544의 Tesseract v2 기준선은 이미 `BASELINE_ONLY`로 검증됐지만, PaddleOCR
동일 corpus 결과·공급망 artifact·production adoption은 아직 열려 있었다. 이 train은
실행 provider를 추가하지 않고, 두 provider 결과를 같은 기준으로 기록할 수 있는
benchmark 전용 Jackson3 계약을 고정한다.

## 결정

- `BASELINE_ONLY`와 `COMPARABLE`을 별도 상태로 둔다. 기준선 한 개만 있는 receipt는
  비교·순위·채택 결론을 만들 수 없다.
- top-level과 provider-level에 v2 manifest SHA-256을 함께 기록하고 exact fixture 순서,
  scenario, expected outcome을 검증한다.
- provider identity는 provider, runtime, model, image digest를 포함한다. `COMPARABLE`은
  model과 image를 `sha256:<64 hex>`로 고정하고 mutable tag를 거부한다.
- TEXT/EMPTY/ERROR payload의 일관성을 강제한다. TEXT는 비어 있지 않은 text와 geometry,
  EMPTY는 빈 text와 geometry 없음, ERROR는 bounded error message를 요구한다.
- 모든 fixture에 cold/warm latency, warm iterations, throughput, RSS와 output SHA-256을
  남기며 최소 warm 3회를 요구한다. CER/WER·throughput·RSS delta는 비교 summary에서만 기록한다.
- receipt의 `scenario`는 Kotlin enum 이름이 아니라 canonical manifest wire 값으로 기록하고,
  manifest digest 계산도 bounded resource만 허용한다.
- Jackson3 `JsonMapper`에 unknown-field 거부, trailing-data 거부, document/string/
  nesting/token/name limit을 적용한다. 기존 corpus/protocol의 `kotlinx.serialization`
  사용은 호환성 때문에 그대로 둔다.
- 실제 Paddle 실행은 #545의 trusted image/model digest, SBOM/attestation, security
  receipt와 동일 v2 corpus evidence가 모두 준비된 뒤에만 `COMPARABLE`로 올린다.

## 관찰한 miss와 surprise

1. benchmark 모듈이 이미 `images` production jar를 사용해도 `implementation` dependency는
   consumer compile classpath에 Jackson3를 보장하지 않는다. 새 Jackson3 contract를
   benchmark `implementation`에 중앙 BOM으로 명시해야 compile/runtime 경계가 분명해진다.
2. manifest SHA 하나만 top-level에 두면 provider별 stale report를 감지하기 어렵다. 각
   provider receipt에도 같은 SHA를 요구해야 drift를 fail-closed로 잡을 수 있다.
3. `TEXT`/`EMPTY`/`ERROR`를 enum만 기록하면 text·geometry·error payload가 서로 모순될 수
   있다. actual outcome별 payload invariant와 output SHA를 함께 검증해야 한다.
4. geometry 좌표의 덧셈은 정수 overflow 위험이 있다. image bound는 `width - x` 형태의
   subtraction으로 검증해 overflow를 피한다.
5. JSON unknown-field와 trailing-data를 mapper default에 맡기면 입력 policy가 버전에
   따라 흔들릴 수 있다. strict feature와 bounded parser를 receipt contract 안에 고정한다.
6. Jackson3는 기존 `kotlinx.serialization @SerialName`을 자동으로 적용하지 않으므로
   `LOW_RESOLUTION` 같은 enum 이름을 wire로 내보내면 canonical `low-resolution` manifest와
   교차 provider receipt가 재생되지 않는다. receipt 모델을 manifest wire 문자열로 고정하고
   실제 JSON 값을 회귀 테스트로 확인했다.
7. `warmIterations > 0`만 검사하면 한 번의 warm 실행도 benchmark evidence로 통과한다. Issue
   #544의 최소 3회 반복 조건에 맞춰 validator가 `warmIterations >= 3`을 fail-closed로 요구한다.

## 재사용할 방어선

- 비교 receipt는 provider별 immutable identity와 corpus SHA를 한 envelope로 묶는다.
- baseline-only, comparable, pending, defer 상태를 서로 승격하지 않는다.
- JSON contract는 unknown field·oversized document·oversized string·trailing data를
  테스트로 고정한다.
- scenario wire 값·manifest resource bound·최소 warm 3회도 receipt validator와 regression
  test로 고정한다.
- fixture row는 품질 payload와 resource metric을 함께 보존하되, 실제 실행 수치가 없는
  synthetic test receipt를 production evidence로 사용하지 않는다.
- #545 trusted artifact와 #547 DEFER gate를 통과하기 전에는 Paddle dependency,
  model download, service/container, production OCR API를 추가하지 않는다.
- 독립 reviewer timeout은 PASS가 아니다. timeout과 inline fallback은 별도 artifact와
  문장으로 기록한다.

## 후속 검증

- #545에서 image/model digest, SBOM, NOTICE/license, attestation, no-egress security
  negative와 CI receipt를 먼저 확정한다.
- 같은 v2 manifest로 Tesseract와 Paddle 결과를 각각 기록하고, provider identity·fixture
  order·CER/WER·geometry·empty/error·cold/warm/RSS/throughput을 validator로 재생한다.
- host/runtime/model identity가 다르면 결과를 비교하지 말고 `PENDING`으로 남긴다.
- actual Paddle receipt가 없으면 Issue #544와 Parent #169를 닫지 않는다.

## 범위와 Kotlin pattern 적용

이번 train은 benchmark 전용 Kotlin contract/test와 중앙 Jackson3 dependency edge만
변경했다. `data class` 불변 모델, 명시적 `require` validation, nullable 대신 명시적
outcome/error 필드, JUnit 5 + Bluetape assertions, `assertFailsWith` 패턴을 적용했다.
production OCR API·provider dependency·native lifecycle은 변경하지 않았다.

## Writer DoD

- `SPW-01`: PASS — issue·독자·결정·범위와 후속 gate를 고정했다.
- `SPW-02`: PASS — 결정·miss/surprise·방어선·후속 검증을 연결했다.
- `SPW-03`: PASS — 자연스러운 한국어 technical register와 machine token을 보존했다.
- `SPW-04`: PASS — #544/#545/#547, v2 manifest, PR #605와 correction commit `47659ba3`을 연결했다.
- `SPW-05`: CONDITIONAL — 독립 reviewer는 timeout/NO RESULT이며 inline 대체 검토는 완료했고, hosted CI·최종 read-back이 남아 있다.

## Final Status

`LESSON RECORDED / CONTRACT GREEN / IMPLEMENTATION OF PADDLE BLOCKED BY EVIDENCE`
