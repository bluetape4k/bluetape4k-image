# Issue #607 receipt artifact TOCTOU lesson

## 배경

Issue [#607](https://github.com/bluetape4k/bluetape4k-image/issues/607)은
Issue [#545](https://github.com/bluetape4k/bluetape4k-image/issues/545)의
receipt artifact 검증에 남아 있던 P2 보안 결함을 다룬다. 기존
`scripts/research/paddle_ocr_receipt.py`는 artifact root와 경로를
`resolve()`한 뒤 `stat()`하고 다시 `Path.open()`하는 순차 검증을 사용했다.
검증 중 ancestor directory가 교체되면 검사한 inode와 실제로 해시한 파일이
달라질 수 있었다.

## 결정

- artifact root를 `O_DIRECTORY | O_NOFOLLOW` descriptor로 먼저 연다.
- 각 relative path component를 이전 directory descriptor에 대해
  `openat` 방식으로 열고, directory와 최종 파일 모두 `O_NOFOLLOW`를
  적용한다.
- 최종 파일은 `fstat()`로 regular file과 크기를 확인한 뒤 같은 file
  descriptor에서 `os.read()`와 SHA-256 계산을 수행한다.
- 기존 safe-relative-path, 최대 크기, smoke log UTF-8·민감정보 검사,
  오류 메시지와 required artifact 계약은 유지한다.
- 실제 PaddleOCR service/container, model/image digest, SBOM, attestation은
  이번 수정 범위가 아니며 #545 선행 gate로 남긴다.

## 결과와 검증

- 구현 commits: `df9aae0f20b84cdfb93748682e5c833abd9410dd`,
  `6e04269` (descriptor 오류 경로 cleanup 보강)
- RED 회귀는 artifact 검증 중 `Path.resolve()` 호출을 금지해 기존 구현의
  실패를 재현했다.
- GREEN 회귀는 `resolve/stat/open` path 재검증을 모두 금지하고 descriptor
  경로만 사용하도록 고정했다.
- receipt contract test: `python3 scripts/research/test_paddle_ocr_receipt.py`
  — 22 passing.
- 기존 smoke test: `python3 scripts/research/test_paddle_ocr_smoke.py`
  — 23 passing.
- `python3 -m py_compile scripts/research/paddle_ocr_receipt.py scripts/research/test_paddle_ocr_receipt.py`
  — PASS.
- `ruff check scripts/research/paddle_ocr_receipt.py scripts/research/test_paddle_ocr_receipt.py`
  — PASS.
- `git diff --check` — PASS.

## 놓친 점과 수정

기존 테스트는 symlink와 byte/hash tampering을 확인했지만, path-based
`resolve/stat/open` 재검증 자체가 다시 들어오는 것을 막지는 못했다. 따라서
정상 artifact fixture에서 세 `Path` 접근을 모두 차단하는 회귀를 추가했다.
이 테스트는 구현 세부의 함수명을 고정하는 것이 아니라, artifact validation이
경로를 재해석하지 않고 열린 descriptor를 소비해야 한다는 보안 불변식을
검증한다.

## 재사용할 방어선

1. 경로 containment 검사만으로 파일 identity를 고정했다고 보지 않는다.
   native filesystem 경계에서는 directory descriptor, `O_NOFOLLOW`, `fstat`,
   동일 descriptor read/hash를 하나의 acceptance 단위로 검증한다.
2. `resolve() → stat() → open()`처럼 같은 pathname을 여러 번 해석하는
   코드는 ancestor 교체 공격의 검사-사용 창을 남기므로 새 artifact validator에
   재사용하지 않는다.
3. symlink 거부 테스트와 함께 path API를 차단한 정상 fixture regression을
   유지해 방어 경계의 회귀를 조기에 검출한다.
4. receipt contract PASS는 실제 Paddle runtime·공급망 artifact PASS와
   동일하지 않다. trusted image/model/SBOM/attestation이 준비되기 전에는
   #545와 #544/#169의 adoption·benchmark 결론을 닫지 않는다.

## 범위와 Kotlin pattern 적용

이번 변경은 Python receipt validator와 회귀 테스트만 수정했다. Kotlin
source/API/dependency는 변경하지 않았으므로 `$bluetape-kotlin-patterns`의
production Kotlin 검증은 `N/A (Kotlin 변경 0개)`다. 다만 descriptor ownership,
명시적 fail-closed validation, 예외 경계와 동일한 자원 수명 원칙은 후속 Kotlin
service gate 구현에도 적용할 기준으로 기록한다.

## Writer DoD

- `SPW-01`: PASS — issue, 독자, 원인, 범위, source path와 미해결 gate를
  고정했다.
- `SPW-02`: PASS — context, decision, outcome, verification, miss와 future
  guard를 포함했다.
- `SPW-03`: PASS — 한국어 기술 문체를 사용하고 Python API·command·URL·SHA를
  보존했다.
- `SPW-04`: PASS — Issue #545 residual P2, 구현 commit, source와 test 결과를
  대조했다.
- `SPW-05`: PASS — 최종 Markdown을 다시 읽고 PASS와 PENDING 경계를 분리했다.

## Final Status

`LESSON RECORDED / TOCTOU FIX GREEN / PADDLE ADOPTION PENDING`
