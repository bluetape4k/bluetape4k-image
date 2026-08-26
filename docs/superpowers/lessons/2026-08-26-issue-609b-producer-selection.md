# #609-B trusted producer 선택 lesson

## 상황

#609-A 문서 계약 이후 실제 producer를 선택할 수 있는지 확인했다. 현재 개발
환경은 Colima `aarch64`와 Docker `linux/arm64`이고, 이전에 확인한
`paddlepaddle/paddle:3.0.0` 후보는 `linux/amd64` 단일 manifest였다.

## 결정

`linux/amd64` trusted CI runner를 우선 후보로 남기고, 검증된 `linux/arm64`
builder를 대안으로 둔다. 다만 producer workflow, full source commit, builder·signer
identity, OIDC issuer, SBOM/provenance/signature subject를 확보하지 못했으므로
#609-B를 `BLOCKED`로 유지한다. registry token과 manifest metadata는 authority가
아니다.

## 결과와 검증

- `colima status`: healthy `aarch64` Docker runtime.
- `docker info`: server `29.2.1`, `linux/aarch64`; client plugin 목록에 buildx 없음.
- Docker Hub manifest: `sha256:854aa259b6ced0b9c8f2eabb4e0f1314d7dff3e6ddd57cb018035c39eb2c86ce`.
- Docker Hub config: `sha256:a5d1b6184e99f5dbcc93e005fa4fbe78bf918c225777f4f0e9424bdfac1104f2`, `linux/amd64`.
- local digest-pinned image inspect: exit `1`, image 없음.
- model bytes/tree, NOTICE, SPDX SBOM, provenance, signature, offline receipt: 없음.

따라서 #609-C image/model ledger와 #609-D attestation, #609-E offline smoke,
#544 benchmark, #547 adoption을 실행하지 않았다. 이는 harness 실패가 아니라
trusted input이 없을 때의 fail-closed 결과다.

## 놓치기 쉬운 점

manifest를 digest로 조회하면 재현성 문제가 해결된 것처럼 보이지만, digest는
producer identity나 signed subject를 대신하지 않는다. 또한 registry가 anonymous
token을 반환해도 credential scope, build provenance, model tree와 NOTICE가
검증된 것은 아니다. 다음 실행자는 token을 문서·receipt·로그에 남기지 않아야 한다.

## 미래 guard

`#609-C` verifier는 producer identity membership, platform manifest/config/base
binding, detector/recognizer pair, file/tree hash, license/NOTICE, SBOM/provenance/
signature subject를 한 번에 PASS시키지 말고 각각 fail-closed로 판정해야 한다.
`#609-E`는 preloaded image/model과 `network=none` receipt가 준비된 뒤에만 열며,
amd64 emulation 결과를 arm64 native benchmark로 재사용하지 않는다.

## SPW-01~05 writer DoD

| 항목 | 결과 | 근거 |
| --- | --- | --- |
| SPW-01 상황·결정·미확인 항목 | PASS | 환경, 후보, BLOCKED 이유와 후속 gate를 명시 |
| SPW-02 lesson 계약 | PASS | context, decision, outcome, verification, miss, future guard를 포함 |
| SPW-03 한국어 technical register | PASS | 상태·증거·경계를 직접 표현하고 source token을 보존 |
| SPW-04 source·readback | PASS | #609-A/#545 source와 current Docker/registry evidence를 대조 |
| SPW-05 최종 readback | PASS | 표·코드 토큰·PENDING/BLOCKED 의미를 문맥에서 재검토 |
