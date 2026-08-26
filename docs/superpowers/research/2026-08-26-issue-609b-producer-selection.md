# #609-B trusted producer·target platform 선택 연구

## 연구 메타데이터

| 항목 | 내용 |
| --- | --- |
| Issue | [#611 #609-B trusted producer·target platform·registry 권한 선택 gate](https://github.com/bluetape4k/bluetape4k-image/issues/611) |
| Parent | [#609 trusted artifact producer·SBOM·attestation 재개 gate](https://github.com/bluetape4k/bluetape4k-image/issues/609) |
| 선행 | [PR #610](https://github.com/bluetape4k/bluetape4k-image/pull/610), #609-A 문서 계약 |
| 후속 | #609-C image/model ledger → #609-D attestation → #609-E offline smoke |
| 유형 | Type-E 연구·검증 |
| 독자 | trusted artifact를 준비하는 build/release 담당자와 #609 후속 실행자 |
| 기준 시각 | 2026-08-26 |
| 판정 | `BLOCKED` — producer authority와 실행 artifact 증거가 없음 |

## 질문과 범위

이 단계의 질문은 “어떤 tag를 실행할까?”가 아니라 “어떤 producer가 full commit,
target platform, image subject, model pair, SBOM·provenance·signature를 함께
증명할 수 있는가?”이다. 따라서 registry metadata를 읽는 것과 trusted producer를
선택하는 것을 분리한다.

다음 항목은 이 단계에서 실행하지 않는다.

- image pull/build, model download, PaddleOCR service와 external egress smoke
- private credential 발급·저장, mutable tag 실행, amd64 emulation의 native 승격
- #544 corpus benchmark와 #547 adoption decision

## 기준 정보와 source ledger

| source | 확인한 내용 | 이번 단계의 한계 |
| --- | --- | --- |
| `docs/superpowers/research/2026-08-25-issue-545-train2-artifact-availability.md` | Docker Hub 후보 digest, Baidu `401`, Colima arm64와 amd64 후보의 차이 | 전 단계 기록이므로 현재 registry readback으로 갱신해야 함 |
| `docs/superpowers/research/2026-08-26-issue-609-trusted-artifact-producer.md` | producer 선택지, platform 경계, attestation 계약 | 선택지 문서가 실제 producer authority를 만들지는 않음 |
| `docs/superpowers/research/2026-08-26-issue-609-artifact-ledger-contract.json` | producer/trustPolicy/image/model/SBOM/provenance/signature 필수 경로 | 모든 값이 `PENDING`이고 verification flag가 `false`임 |
| [PaddleOCR serving guide](https://github.com/PaddlePaddle/PaddleOCR/blob/b03f46425e8ff4442b268ce449e3eef758146cd4/docs/version3.x/inference_deployment/serving/serving.en.md) | serving 경로 | producer 서명·SBOM·model bytes를 증명하지 않음 |
| [Docker image digest](https://docs.docker.com/dhi/explore/security-concepts/digests/) | tag와 digest의 구분 | digest만으로 builder·signer·NOTICE를 증명하지 않음 |

## 현재 환경과 registry readback

| 검사 | 결과 | 해석 |
| --- | --- | --- |
| `colima status` | running, macOS Virtualization.Framework, `aarch64`, Docker | local target은 `linux/arm64` |
| `docker info` | server `29.2.1`, `linux/aarch64` | 현재 daemon은 amd64 후보의 native target이 아님 |
| Docker CLI plugins | client `29.7.2`, `docker buildx` unknown command | local trusted multi-platform build 경로가 준비되지 않음 |
| Docker Hub manifest | `sha256:854aa259b6ced0b9c8f2eabb4e0f1314d7dff3e6ddd57cb018035c39eb2c86ce` | `paddlepaddle/paddle:3.0.0`의 immutable 후보 digest |
| Docker Hub config | `sha256:a5d1b6184e99f5dbcc93e005fa4fbe78bf918c225777f4f0e9424bdfac1104f2` | config가 `linux/amd64`임을 확인 |
| local image inspect | digest-pinned image 없음, exit `1` | offline 실행 입력이 없음 |
| Baidu registry | manifest metadata는 anonymous token으로 조회 가능 | token/metadata는 trusted producer authority·attestation이 아님 |

Docker Hub와 Baidu가 같은 manifest/config digest를 반환하더라도 mirror가
원 producer의 builder identity, signer, OIDC issuer, SBOM subject를 보존했다는
증거는 아니다. 그러므로 위 결과는 “후보 digest를 발견했다”까지만 증명한다.

## 선택지와 판정

| 선택지 | 장점 | 위험 | 판정 |
| --- | --- | --- | --- |
| `linux/amd64` trusted CI runner에서 고정 build | 확인된 후보 platform과 일치하고 emulation 없이 smoke 가능 | runner 비용, artifact producer와 attestation을 새로 고정해야 함 | **우선 재개 후보** |
| 검증된 `linux/arm64` 자체 build | 현재 Colima와 native 일치 | Paddle/PaddleX wheel·base image, SBOM·provenance·signature를 새로 증명해야 함 | 조건부 대안 |
| 인증된 Baidu registry artifact | upstream serving 경로를 따를 수 있음 | credential scope와 mirror subject 재검증 필요 | 권한·attestation 전까지 보류 |
| mutable tag 또는 first-use download | 탐색은 빠름 | offline·재현성·egress 계약 위반 | **거부** |
| amd64 emulation을 native 결과로 사용 | local에서 실행 가능 | native platform·성능·운영 증거가 아님 | **거부** |

현재는 첫 번째 선택지를 “선택 결정”으로 승격할 수 없다. trusted CI repository,
workflow path/ref, full source commit, builder identity, signer identity, OIDC
issuer와 allowlist가 비어 있기 때문이다.

## #609-C 재개 입력

#609-B가 `PASS`가 되려면 다음 값을 비밀값 없이 기록해야 한다.

1. producer repository/workflow/workflowRef와 full immutable source commit
2. target platform과 image index/platform/config/base digest
3. builder·signer identity, OIDC issuer, non-empty allowlist와 policy SHA-256
4. credential은 저장소 밖의 승인된 secret store에서만 사용한다는 권한 경계
5. detector/recognizer pre-baked model revision, file/tree hash와 `NOTICE`
6. 동일 platform manifest subject의 SPDX SBOM, in-toto/SLSA provenance, signature

하나라도 빠지면 ledger는 `PENDING` 또는 `BLOCKED`로 남기고 #609-C verifier를
실행하지 않는다.

## 결론

현재 가장 낮은 위험의 target은 `linux/amd64` trusted CI runner지만, producer
authority가 확인되지 않아 #609-B는 `BLOCKED`이다. manifest digest를 읽은 것,
anonymous registry token을 받은 것, Docker daemon이 healthy인 것은 trusted
artifact acceptance가 아니다. #609-C/D/E와 #544 benchmark, #547 adoption은
그 증거가 확보될 때까지 열지 않는다.

## SPW-01~05 writer DoD

| 항목 | 결과 | 근거 |
| --- | --- | --- |
| SPW-01 대상·근거·불확실성 | PASS | #611, #609, #610, local/registry readback과 미확인 producer evidence를 명시 |
| SPW-02 연구 artifact 계약 | PASS | 질문, 범위, source ledger, 환경, 선택지, 재개 입력, 결론을 포함 |
| SPW-03 한국어 technical register | PASS | 사실·상태·거부 경계를 직접 서술하고 식별자·명령·URL을 보존 |
| SPW-04 source·readback | PASS | 기존 연구 문서와 official URL, `colima status`, `docker info`, manifest/config/inspect 결과를 대조 |
| SPW-05 최종 readback | PASS | 표·링크·코드 토큰·PENDING/BLOCKED 표현을 문맥에서 재검토 |
