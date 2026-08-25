# #545 Train 2 trusted artifact 가용성 lesson

## 배경

Issue #545는 PaddleOCR service/container의 실행·공급망·보안·CI acceptance를
연구한다. 앞선 train에서 receipt contract와 offline smoke preflight를 만들었지만,
실제 service를 시작하려면 trusted image, pre-baked model, SBOM, provenance와
attestation이 모두 필요하다. 이번 Train 2는 그 입력이 현재 환경에서 실제로
가용한지 확인하는 좁은 Type-E slice였다.

## 결정

- 공식 install/serving 문서와 digest-pinned service image를 같은 것으로 취급하지
  않는다. serving 문서의 `paddlex --serve --pipeline OCR`는 실행 명령을 설명할
  뿐, producer digest·architecture·SBOM subject·서명을 제공하지 않는다.
- 모델 API의 revision과 `apache-2.0` metadata는 model identity의 일부일 뿐이다.
  실제 model bytes, file/tree hash, NOTICE, recognizer pair가 없으면 acceptance
  입력으로 승격하지 않는다.
- Docker Hub `paddlepaddle/paddle:3.0.0`의 확인된 digest/config가
  `linux/amd64`이고 local Colima가 `linux/arm64`이므로, tag를 pull하거나
  cross-architecture 실행을 가장하지 않는다.
- 인증되지 않은 Baidu registry `401` 응답은 “공식 artifact가 없다”는 증거가
  아니라, 현재 세션이 trusted manifest를 확인할 수 없다는 `BLOCKED` 증거다.
- 결과는 `ARTIFACT_AVAILABILITY_PENDING`, PaddleOCR provider 판정은 기존
  `DEFER`로 유지한다. 실제 artifact가 생길 때만 새 immutable receipt를 만든다.

## 관찰된 결과

기존 `paddle_ocr_smoke.py`에 immutable candidate를 입력한 preflight는 Docker local
inspect exit `1`과 `digest-pinned image is not available offline`로 fail-closed했다.
이는 harness가 정상적으로 보류 경계를 지킨 결과이며, OCR 품질·startup·license·
SBOM·attestation PASS가 아니다. 실제 model/service를 다운로드하거나 실행하지
않았으므로 산출물에는 바이너리와 민감정보가 없다.

## 검증에서 얻은 교훈

1. **Artifact availability는 연구 시작 gate다.** 공식 문서 링크가 살아 있고 모델
   registry가 revision을 반환해도, 현재 architecture에서 실행 가능한 digest-pinned
   image가 없으면 service acceptance를 시작할 수 없다.
2. **Identity와 bytes를 분리한다.** revision/license metadata를 file/tree digest와
   혼동하면 model drift와 NOTICE 누락을 감지할 수 없다.
3. **Architecture를 먼저 고정한다.** `amd64` 후보를 `arm64` Colima에서 “아마
   된다”고 실행하는 대신, trusted CI runner 또는 ARM build 중 하나를 선택하고
   producer digest와 attestation을 같이 확보해야 한다.
4. **권한 실패는 부재와 다르다.** registry `401`은 접근 권한·scope 문제이므로
   공개 artifact 부재나 보안 PASS로 재분류하지 않는다.
5. **Fail-closed receipt가 빠른 탐색보다 안전하다.** mutable tag/model
   auto-download를 허용하면 나중에 같은 OCR 결과와 공급망 상태를 재현할 수 없다.

## 재사용할 방어선

- 실행 전 체크리스트에 `target architecture → image digest/config → local offline
  inspect → model tree/NOTICE → SBOM subject → attestation` 순서를 고정한다.
- synthetic availability-only receipt에는 `PENDING/BLOCKED` 의미를 함께 기록하고,
  `PASS`가 실제 서비스 승인처럼 보이지 않도록 범위를 명시한다.
- trusted artifact가 준비되면 clean/offline service smoke, redacted log, cleanup,
  SPDX SBOM, provenance/SBOM attestation을 같은 immutable tuple에 연결한다.
- #544 동일 corpus 비교가 끝나기 전에는 #547 adoption decision이나 Paddle
  production dependency/API를 열지 않는다.

## 범위와 미해결 사항

이번 변경은 연구 문서·lesson·review에만 한정한다. Kotlin source/test, Spring,
Exposed, coroutine, native binding, dependency catalog, model file, Dockerfile,
production API를 변경하지 않았다. 따라서 `$bluetape-kotlin-patterns`는
`N/A (Kotlin 변경 0개)`이며, 해당 skill의 코드 품질 검증을 수행했다는 의미가
아니다.

남은 작업은 trusted architecture 선택, image/model/SBOM/attestation receipt 수집,
실제 offline service smoke, #544 비교, #547 결정이다. 이는 이번 문서 slice의
실패가 아니라 명시적으로 열린 후속 gate다.

## Writer DoD

- `SPW-01`: PASS — 대상 issue, 독자, source, 결정과 비범위를 고정했다.
- `SPW-02`: PASS — 관찰 결과, 실패 모드, 재개 gate와 운영 경계를 기록했다.
- `SPW-03`: PASS — 한국어 기술 문체를 사용하고 command/API/URL/token은 보존했다.
- `SPW-04`: PASS — 공식 source와 live preflight 결과를 receipt·wiki 링크로 대조했다.
- `SPW-05`: PASS — 문서를 다시 읽어 `PENDING/BLOCKED/DEFER`를 `PASS`로 승격하지
  않았고, 미해결 사항을 후속 gate로 남겼다.

최종 상태: `LESSON RECORDED / ARTIFACT AVAILABILITY PENDING`
