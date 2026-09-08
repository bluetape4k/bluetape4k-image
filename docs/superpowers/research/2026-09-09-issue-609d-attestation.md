# #609-D attestation same-subject 검증 결과

## 조사 범위

이번 검증은 [#638 final evidence](https://github.com/bluetape4k/bluetape4k-image/issues/638)의
release evidence와 [#609-C ledger](2026-09-09-issue-609c-image-model-ledger.json)를
입력으로 사용했다. 외부 evidence root와 모델 원본 bytes는 저장소에 복사하지 않고,
attestation bundle·DSSE payload·SPDX 파일의 byte/sha만 receipt에 보존했다.

`#609-E` no-egress 실행, `#544-B` 성능 비교, `#547` adoption decision은 이 결과의
입력이 아니며 계속 후속 범위다.

## 고정 tuple

| 항목 | 검증값 |
|---|---|
| workflow | `.github/workflows/paddleocr-producer.yml` |
| workflow ref | `bluetape4k/bluetape4k-image/.github/workflows/paddleocr-producer.yml@refs/heads/develop` |
| source/workflow SHA | `367275a58681e7a7f04fc5f078d70e694a13951a` |
| workflow run | `34258892748.1` |
| runner | `github-hosted` / `ubuntu-24.04` |
| OIDC issuer | `https://token.actions.githubusercontent.com` |
| subject | `ghcr.io/bluetape4k/paddleocr-service` |
| platform subject digest | `sha256:d08a5967dc6d95de2f6d6ed744d0b91376b1e2032e3511e596f9784fbf271810` |
| #609-C ledger checksum | `92338285b720869f76b69e3114c30af61b7a600a37fdbbf2ffe6f95ea6df3be4` |

## Bundle과 receipt

| 종류 | predicate | bundle bytes | bundle SHA-256 | DSSE payload SHA-256 |
|---|---|---:|---|---|
| provenance | `https://slsa.dev/provenance/v1` | 11534 | `e559893736aa8dfb30e502b5f182e72ad20ca561ab3e1c534b9996b038569d27` | `1eb462aba5f4e73045d9654109b2a18b2c5684ea0b6548921de3537eb0efff89` |
| SBOM | `https://spdx.dev/Document/v2.3` | 59002 | `6717d84042385338bb28b9a3a061252c144503aea8d90786889223b517c85b5f` | `8b2641b47d486eb341ff87dd4aa97a12e368094b6621a1c266f5c2e916b80224` |

두 결과 모두 `gh attestation verify --format json`에서 정확히 1개 결과를 반환했다.
각 결과의 certificate는 repository/workflow/ref, `buildSignerDigest`,
`sourceRepositoryDigest`, `buildConfigDigest`, signer URI, invocation URI, OIDC issuer,
`runnerEnvironment=github-hosted`를 고정 tuple과 일치시켰다. 각 결과에는 하나의
`Tlog` HTTPS `verifiedTimestamp`가 있었고, statement의 유일한 subject name/digest와
predicate가 local DSSE payload를 base64 decode한 statement와 byte 의미상 동일했다.

생성된 canonical receipt는
[`2026-09-09-issue-609d-attestation.json`](2026-09-09-issue-609d-attestation.json)이며
receipt SHA-256은
`cd9532b7506dc0ca78456d0376a9125d91e6044b8fdee4aec367ed550f430f3c`이다. Receipt의
`verification`은 ledger checksum, evidence descriptor, provenance/SBOM subject,
producer identity, verified timestamp, local bundle binding을 모두 `true`로 기록한다.

## 재현 명령

```bash
E=<issue-638-final-evidence-root>
GH="$(realpath "$(command -v gh)")"
python3 scripts/research/paddle_ocr_attestation.py \
  --evidence-root "$E" \
  --ledger docs/superpowers/research/2026-09-09-issue-609c-image-model-ledger.json \
  --gh-bin "$GH" \
  --output docs/superpowers/research/2026-09-09-issue-609d-attestation.json
```

검증기는 local bundle을 먼저 bounded strict JSONL로 읽고, `gh` 결과의 DSSE
payload/signature/certificate raw bytes와 대조한다. `gh` 명령은 읽기 전용
attestation 검증이며 registry push, credential 저장, model download, service 실행을
수행하지 않는다.

## negative contract

다음 입력은 모두 receipt 생성 전에 거부한다.

- 결과 배열이 0개 또는 2개 이상인 경우
- statement subject digest/name 또는 predicate가 기대값과 다른 경우
- signer/source/build digest, workflow/ref, invocation, issuer, runner가 다른 경우
- verified timestamp가 없거나 `Tlog` HTTPS entry가 아닌 경우
- local bundle의 media type, DSSE payload/signature, certificate raw bytes가 `gh` 결과와 다른 경우
- duplicate/unknown JSON key, malformed JSON, bundle size 초과, symlink/unsafe path

## Writer SPW-01~05

| 항목 | 판정 | 근거 |
|---|---|---|
| SPW-01 대상·독자·범위 | PASS | #609-D와 #638/#609-C 선행, #609-E/#544-B/#547 후속을 고정했다. |
| SPW-02 실행·실패·재개 | PASS | 명령, bounded read, 실패 조건, receipt 출력 경계를 기록했다. |
| SPW-03 한국어·machine token | PASS | 설명은 한국어로 쓰고 API·URL·digest·status token은 보존했다. |
| SPW-04 source·readback | PASS | 실제 run `34258892748.1`, bundle SHA, ledger checksum, receipt SHA를 read-back했다. |
| SPW-05 사실·불확실성 | PASS | `gh` 암호 검증과 Python identity/binding 검증을 분리하고 runtime/performance 미실행을 명시했다. |

## DoD Status

- [x] SPDX SBOM과 SLSA provenance를 동일 image platform digest에 연결했다.
- [x] signer/OIDC/workflow/ref/source digest/runner/invocation을 독립 read-back했다.
- [x] local DSSE bundle payload/signature와 `gh` verification result를 대조했다.
- [x] canonical same-subject receipt와 strict schema를 생성했다.
- [ ] `network=none` runtime 및 no-egress receipt (`#609-E`)
- [ ] 동일 corpus 품질·성능 비교 (`#544-B`)와 adoption decision (`#547`)

최종 판정: `609-D PASS / PRODUCER ARTIFACT ATTESTATIONS VERIFIED / RUNTIME ADOPTION PENDING`
