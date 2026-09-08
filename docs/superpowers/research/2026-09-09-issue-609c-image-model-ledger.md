# #609-C image·model ledger 연구 결과

## 조사 범위

이번 기록은 [#611의 producer 선택](https://github.com/bluetape4k/bluetape4k-image/issues/611)과
[#638의 최종 trusted producer evidence](https://github.com/bluetape4k/bluetape4k-image/issues/638)를
입력으로 삼아 #609-C에 필요한 immutable identity만 재검증한다. #609-D attestation,
#609-E no-egress 실행, #544 성능 비교, #547 adoption은 이 기록의 입력이 아니다.

증거 root는 #638이 생성한
`issue638-transfer-review/final-anonymous-9469zzv0/evidence`이고, 모델 root는 같은
review의 `accepted-image`이다. 원본 모델 bytes는 저장소에 복사하지 않고 파일 크기와
SHA-256만 ledger에 보존했다.

## producer와 image identity

| 항목 | 검증값 |
|---|---|
| workflow | `.github/workflows/paddleocr-producer.yml` |
| workflow ref | `bluetape4k/bluetape4k-image/.github/workflows/paddleocr-producer.yml@refs/heads/develop` |
| source revision / workflow SHA | `367275a58681e7a7f04fc5f078d70e694a13951a` |
| workflow run | `34258892748.1` |
| builder | `github-hosted/ubuntu-24.04` (`runnerEnvironment=github-hosted`) |
| signer | `bluetape4k/bluetape4k-image/.github/workflows/paddleocr-producer.yml` |
| OIDC issuer | `https://token.actions.githubusercontent.com` |
| target | `linux/amd64` |
| image index/platform | `sha256:d08a5967dc6d95de2f6d6ed744d0b91376b1e2032e3511e596f9784fbf271810` |
| image config | `sha256:24dd4b1fd42006649f730c45cbbc88b37ef0291bbb749c18ea921323195db312` |
| base platform | `sha256:8c97ebedc32fd60935cdf5992e935753e2a0f98231830028050e1e04bd3c13c2` |
| package lock | `27a8f7fa52cb48b0bbc623ad80a6e2621cbf4476134d1c9210090c9b8fec9f09` |
| input lock | `5db831e044429eccea59d088673aed1beb5dfd21768a982b7c8ab91b11f86779` |
| trust policy | `8d9dce8676b72582b614badc0bcff83f2f495e6411d370d34a4e5e5076274768` |

index와 단일 platform manifest가 같은 digest이고, platform manifest raw bytes의
SHA-256이 그 digest와 일치한다. config descriptor도 evidence의 config digest와
일치한다. trust policy의 repository, workflow path, `refs/heads/develop`, runner
environment, OIDC issuer allowlist에 producer claim을 대조했고 allowlist는 비어 있지
않다.

## model과 legal inventory

| role | archive bytes / SHA-256 | tree SHA-256 | model revision |
|---|---:|---|---|
| detector | `4935680` / `50446e5d01ac2a73d5319c89513281f6578414c888c602f9af13f93feefffc58` | `9263307ff8b2863cc3764f63e16d4d5317ec7d0012627382d41a67411af5844a` | `ffb64904d23708863ff5b8da312a5cbd52a7f462` |
| recognizer | `16834560` / `566b9512b34e34a9f0db54d87b51fa5a0b9ed2cf1ab7e49728cc0b8b5a64f414` | `76ad80b3557fc2a145ea5c2e65746d387860498eed93f337f2b675477a7d185c` | `ffb64904d23708863ff5b8da312a5cbd52a7f462` |

각 role에서 `inference.json`, `inference.pdiparams`, `inference.yml` 세 파일을
실제 staged root에서 읽고 bytes·SHA-256을 비교했다. 고정 role 순서로 계산한
`pairBindingSha256`은
`d2f455ea0e0a34cfa8fac7bba38887cf136d62525e69cf9f433eac8009d9dd8f`이다.

두 모델 모두 `Apache-2.0`이며 legal inventory와 다음 file hash가 일치한다.

| role | license file | notice file |
|---|---|---|
| detector | `legal/models/PP-OCRv5_mobile_det.MODEL_CARD.md` · `4cc20ad6d41af86b3ce9885ffb0956e152574a2eb14179aeb07fd2d3956161ca` | `legal/models/PP-OCRv5_mobile_det.NOTICE.txt` · `638a54c6cfb0fc69f7a7aca2d375b35f6f39b6b99a6988a34abd2891751f2531` |
| recognizer | `legal/models/PP-OCRv5_mobile_rec.MODEL_CARD.md` · `b02727443cef4904a9ee12accdfaf66fcbada7b93a1a05c40dde7582291ba28c` | `legal/models/PP-OCRv5_mobile_rec.NOTICE.txt` · `fcb2ceaf16590e0029a443a4daf698257d7978b05d7e3948c334608157268ac1` |

legal inventory raw SHA-256은 `af0e2f2aff80643cd548497efb833deb542c4514efdd73b7e461d38586896120`이다.

## validator와 ledger 결과

`scripts/research/paddle_ocr_ledger.py`는 stdlib와 기존 strict JSON/JCS·filesystem
primitive만 사용한다. 입력 문서는 duplicate key, float, BOM, 크기·깊이 초과,
unknown field를 거부한다. evidence/model/legal root는 symlink가 아닌 regular file로
열고, 상대 경로 탈출과 symlink tree를 차단한다. ledger는 top-level 및 nested key를
정확히 비교하고 checksum을 제거한 JCS object의 SHA-256을 다시 계산한다.

검증 결과는 다음과 같다.

```text
status=PASS
kind=IMAGE_MODEL_LEDGER
scope=609-C
ledgerSha256=92338285b720869f76b69e3114c30af61b7a600a37fdbbf2ffe6f95ea6df3be4
verification=true (10/10)
```

재현 가능한 canonical ledger는
[`2026-09-09-issue-609c-image-model-ledger.json`](./2026-09-09-issue-609c-image-model-ledger.json)에
있다. fixture test는 정상 build/validate, 모델 파일 변조, platform manifest 변조,
symlink 모델 파일, workflow ref 변조, unknown top-level key, pair role 순서 고정을
검증하며 7개가 모두 통과했다.

## 범위 판정

이 결과로 #609-C image/model ledger gate는 `DONE`으로 기록할 수 있다. 부모 #609의
전체 상태는 `PENDING`이다. 현재 ledger의 `scope.deferred`는 `609-D`, `609-E`,
`544-B`, `547`이며, 실제 no-egress 실행과 성능 수치는 아직 측정하지 않았다.
