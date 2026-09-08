# #609-D attestation 검증 lesson

## 결정

- `gh attestation verify`를 서명·certificate·투명성 로그의 암호 검증 권위로 두고,
  Python 코드는 그 JSON 결과를 다시 검증하는 경계로 한정했다.
- attestation bundle을 성공 결과와 별도로 신뢰하지 않는다. local JSONL의 media type,
  DSSE payload/signature, certificate raw bytes를 `gh` 결과의 bundle과 대조해 같은
  입력이 검증됐는지 확인한다.
- provenance와 SPDX SBOM은 각각 predicate를 검사한 뒤 repository/workflow/ref/commit,
  OIDC issuer, runner, invocation, subject name/digest가 모두 같은지 pair-level에서
  확인한다.
- 기존 `validate_attestation_identity` 계약의 `environment=paddleocr-producer`와
  Sigstore certificate의 `runnerEnvironment=github-hosted`를 별도 필드로 보존한다.
  둘을 하나의 값으로 덮으면 producer environment와 hosted runner 정책을 혼동한다.
- local bundle에는 RFC3161 배열이 비어 있을 수 있으므로 bundle의 tlog material 존재와
  `gh` read-back의 verified `Tlog` timestamp를 분리해 검증한다. 비어 있는 optional
  RFC3161 field를 서명 성공으로 오해하지 않는다.

## 관찰한 miss와 수정

1. 기존 public evidence callback은 `gh` JSON이 비어 있지 않은지만 확인했다. 결과가
   다른 subject를 포함하거나 signer certificate가 바뀌어도 통과할 여지가 있어 exact
   one-result와 identity/predicate 검사를 추가했다.
2. workflow의 `jq` 검사는 CI 단계에만 있었고, 저장된 final evidence를 다시 읽는
   재현 가능한 receipt가 없었다. 저장소 Python verifier와 canonical receipt를 추가했다.
3. `gh` 출력의 `bundle_url`, `initiator`, timestamp verification material은 빈 문자열
   또는 정규화된 형태로 반환될 수 있었다. 보안 판단에 필요한 certificate raw bytes,
   DSSE payload/signature, verified timestamp만 필수로 두고 출력 정규화 차이를 허용했다.
4. `/opt/homebrew/bin/gh`가 symlink인 macOS 환경이므로 CLI에는 `realpath`한 executable
   regular file을 넘겨야 한다. symlink 자체를 허용해 실행 경계를 흐리지 않는다.

## 재사용할 방어선

1. `gh` 같은 외부 verifier의 성공 exit code만으로 acceptance를 만들지 말고, 출력의
   subject·predicate·certificate identity·timestamp를 기대 tuple과 대조한다.
2. 서명된 DSSE payload를 base64 decode해 statement를 확인하고, verifier output의
   statement가 local bundle payload와 같은지 비교한다. 별도 JSON 파일만 읽으면
   bundle swap을 놓칠 수 있다.
3. provenance/SBOM pair는 각자의 predicate를 유지하면서 shared identity tuple을
   비교한다. 한 attestation의 signer를 다른 attestation에 전이하지 않는다.
4. receipt는 raw evidence 전체를 복사하지 않고 path/bytes/SHA-256과 ledger checksum을
   보존한다. 모델 bytes와 registry side effect는 후속 train의 경계로 남긴다.
5. `PASS` receipt는 attestation gate만 닫는다. no-egress runtime, 동일 corpus
   benchmark, #547 adoption을 자동으로 완료시키지 않는다.

## 검증 결과

- strict contract test: `python3 scripts/research/test_paddle_ocr_attestation.py` — 10개 통과
- syntax/static: `py_compile`, `ruff check`, `git diff --check` — 통과
- schema/receipt: `python3 -m json.tool` — 통과
- 실제 evidence: `gh attestation verify` provenance/SBOM 2회, same-subject receipt — 통과
- receipt: `cd9532b7506dc0ca78456d0376a9125d91e6044b8fdee4aec367ed550f430f3c`

Kotlin/API/dependency와 Dockerfile/workflow는 변경하지 않았다. 따라서
`$bluetape-kotlin-patterns` production 검토는 `N/A (Kotlin 변경 0개)`이며, 이 결과는
PaddleOCR runtime 또는 채택을 증명하지 않는다.

## Writer DoD

- `SPW-01`: PASS — issue, 선행 ledger, 독립 verifier, 후속 runtime/benchmark 경계를 기록했다.
- `SPW-02`: PASS — 결정, 관찰한 miss, 재현 명령과 fail-closed negative를 연결했다.
- `SPW-03`: PASS — 한국어 prose와 machine token/URL/digest를 구분했다.
- `SPW-04`: PASS — 실제 #638 run, bundle SHA, ledger checksum, receipt SHA를 대조했다.
- `SPW-05`: PASS — attestation PASS와 runtime/adoption PENDING을 분리했다.

최종 상태: `LESSON RECORDED / 609-D RECEIPT GREEN / 609-E·544-B·547 PENDING`
