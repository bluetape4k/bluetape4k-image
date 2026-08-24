# Issue #545 service receipt contract

## 결정

이번 slice는 PaddleOCR를 실행하거나 배포하지 않는다. 대신 CPU smoke, 공급망
attestation, SBOM, license/NOTICE 결과를 나중에 같은 형식으로 비교할 수 있도록
fail-closed receipt 계약과 독립 validator를 추가한다. #547 adoption gate가 닫히기
전에는 production Paddle dependency, model download, public API, HTTP adapter를
추가하지 않는다.

현재 validator의 `validationScope`는 `CONTRACT_ONLY`로 고정한다. `status=PASS`는
구조·무결성 검사를 통과했다는 뜻일 뿐, 서명·issuer·workflow를 독립 검증한 운영
보안 승인이나 Paddle 채택 판정이 아니다. 실제 acceptance verifier와 #547 gate가
완료되기 전에는 이 receipt를 배포·채택 근거로 사용할 수 없다.

## 추적성

| 항목 | 값 |
| --- | --- |
| Issue | [#545](https://github.com/bluetape4k/bluetape4k-image/issues/545) |
| 선행 baseline | [#544](https://github.com/bluetape4k/bluetape4k-image/issues/544), [PR #559](https://github.com/bluetape4k/bluetape4k-image/pull/559) |
| API 설계 입력 | [#546](https://github.com/bluetape4k/bluetape4k-image/issues/546), [PR #558](https://github.com/bluetape4k/bluetape4k-image/pull/558) |
| 최종 결정 gate | [#547](https://github.com/bluetape4k/bluetape4k-image/issues/547) |
| Main epic | [#513](https://github.com/bluetape4k/bluetape4k-image/issues/513) |
| 기준 commit | `8c3f152cc5b44d3a4007197fa112ffb392340751` |
| 상태 | `CONTRACT_ONLY` — 실제 smoke·SBOM·attestation은 후속 slice |

## Immutable tuple

validator는 다음 식별자를 한 실행의 불변 tuple로 요구한다.

```text
(repository, commitSha, workflowRunId, fixtureManifestSha256,
 modelManifestSha256, containerImageDigest, hostArchitecture, configSha256)
```

commit, fixture/model/config SHA-256, container digest는 lowercase hex를 사용한다.
container digest는 mutable tag가 아니라 `sha256:<64 hex>` 형식이어야 하며, 실행은
`linux/amd64` 또는 `linux/arm64`로 명시한다. software 버전에는 `latest`, `main`,
범위 지정자, floating selector를 기록하지 않는다. receipt 자체도 1 MiB 상한을
먼저 확인한 뒤 bounded read로 JSON을 파싱한다.

## 필수 증거

receipt의 `security` 전 항목이 `true`여야 `status=PASS`를 허용한다.

- offline startup과 model checksum/verified bytes
- first-use download 및 network egress 차단
- auth/TLS, no-log 민감정보 검사, request/resource limits
- non-root, read-only root, capability drop, cleanup

또한 smoke report, redacted log, cleanup report, SPDX SBOM, provenance attestation,
SBOM attestation, license/NOTICE artifact를 각각 relative path와 byte-level SHA-256으로
기록한다. `--artifact-root`를 지정하면 validator가 실제 파일의 크기와 SHA-256까지
재계산하고 bounded UTF-8 `smoke-logs`에서 payload·credential·model-path 패턴을
재검사한다. artifact hash는 64 MiB 상한을 chunk 단위로 읽고, smoke log는 4 MiB
상한을 먼저 확인한다. absolute path, traversal, symlink, receipt metadata의 금지
문자열은 거부한다. attestation artifact의 subject·issuer·signature 검증은 별도의
acceptance verifier slice에서 수행해야 한다.

## 후속 slice 경계

1. digest-pinned tiny CPU image와 pre-baked model을 선택하고, model tree manifest 및
   license/NOTICE를 만든다.
2. external egress가 없는 internal network에서 readiness, OCR smoke, redacted log,
   cleanup을 실행하고 이 contract에 맞는 receipt를 생성한다.
3. 하나의 trusted workflow build digest로 SPDX SBOM과 provenance/SBOM attestation을
   생성·검증한다. local rebuild digest를 signed subject로 대체하지 않는다.
4. #544 동일 corpus 비교와 #546 API 설계 결과를 #547 adoption gate에 연결한다.

현재 문서는 계약 구현만 증명한다. 실제 Paddle 결과나 채택 판단을 `PASS`로
표현하지 않는다.

## 잔여 위험

`artifact_root`가 quiescent한 CI 결과 디렉터리가 아니라면 symlink/stat 확인과
실제 `open()` 사이에 TOCTOU가 생길 수 있다. 운영 acceptance verifier에서는
`O_NOFOLLOW`와 directory-fd 기반 open/fstat 또는 immutable read-only artifact root를
추가해야 한다. 이 P2는 현재 CONTRACT_ONLY slice의 비차단 후속 과제다.

## 검증 DoD

- [x] immutable tuple과 version pin 규칙을 코드로 검증
- [x] security gate가 하나라도 false이면 fail-closed
- [x] 필수 artifact path·size·SHA-256과 symlink/traversal을 검증
- [x] duplicate JSON key와 receipt metadata·bounded smoke-log의 민감 문자열을 거부
- [x] `validationScope=CONTRACT_ONLY`로 운영 acceptance 오용을 차단
- [ ] 실제 Paddle image/model/SBOM/attestation 실행
- [ ] #544 품질·성능 비교와 #547 최종 판정

최종 상태: `CONTRACT_ONLY / RECEIPT EXECUTION PENDING`
