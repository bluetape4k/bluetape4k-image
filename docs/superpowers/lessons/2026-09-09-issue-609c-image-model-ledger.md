# #609-C image·model ledger lesson

## 결정

- 기존 `artifact-ledger.fragment.json`을 확장하지 않고 `IMAGE_MODEL_LEDGER`를 별도
  stage-specific 계약으로 만들었다. 기존 fragment는 producer lifecycle 상태를
  표현하고, 이번 ledger는 image/model bytes identity를 표현한다.
- model pair hash는 JSON object의 key 순서에 의존하지 않고
  `detector\n<tree>\nrecognizer\n<tree>\n` 형식을 사용했다. role 순서를 바꾸면 다른
  pair가 되도록 negative test를 고정했다.
- evidence JSON은 #638 producer가 만든 pretty JSON을 raw byte digest와 함께 읽는다.
  처음 validator를 모든 입력에 canonical JCS bytes로 제한했을 때 실제
  `platform-manifest.json`이 유효한 pretty JSON인데도 거부됐다. 입력은 strict parse와
  declared raw SHA를 유지하고, ledger 출력만 JCS canonical form으로 제한하도록
  경계를 수정했다.
- trust policy 파일의 기존 allowlist key를 변경하지 않고 ledger에 producer와 policy의
  관계를 복사했다. repository/workflow/ref/builder/signer/OIDC membership과 policy
  SHA-256을 검증해 선언만으로 `trustPolicyMatched`를 세우지 않는다.
- validator는 model bytes를 다운로드하지 않는다. `O_NOFOLLOW`, regular-file 확인,
  relative path 검사, canonical tree 재계산으로 staged artifact만 검증한다.

## 검증에서 얻은 것

- #638 최종 evidence와 accepted model root에서 같은 image platform digest,
  package/input lock, model tree/file hash, legal inventory를 재현했다.
- ledger checksum은
  `92338285b720869f76b69e3114c30af61b7a600a37fdbbf2ffe6f95ea6df3be4`이고, 10개
  #609-C verification flag가 모두 `true`이다.
- 정상 fixture 1건과 tamper/symlink/unknown-key/pair-order negative를 포함한 7개
  Python test가 통과했다.

## 남은 경계

- attestation subject와 signer 검증은 #609-D에서 같은 platform digest를 기준으로
  별도 receipt를 만든다.
- no-egress, preload runtime, resource limit, log redaction, cleanup은 #609-E에서
  실제 실행으로 증명한다. 이번 ledger의 `status=PASS`를 runtime PASS로 해석하지 않는다.
- #544-B 성능·정확도 비교와 #547 adoption 결정은 이 ledger의 deferred scope를
  소비하는 후속 작업이다.

## 재사용 규칙

후속 단계가 ledger를 합칠 때 `image.platformManifestDigest`, 두 `treeSha256`,
`pairBindingSha256`, `licenseNotice.inventorySha256`, `trustPolicy.policySha256`를
그대로 유지한다. 하나라도 변경되면 새 evidence attempt와 checksum을 만들고, 이전
ledger를 덮어쓰지 않는다.
