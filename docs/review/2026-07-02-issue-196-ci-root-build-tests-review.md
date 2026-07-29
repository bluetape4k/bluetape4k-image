# Issue #196 CI Root Build Test Coverage 검토

## 범위

- 이슈: #196, `ci: run affected module tests for root Gradle and buildSrc changes`
- 검토 파일: `.github/workflows/ci.yml`
- 검토일: 2026-07-02

## 발견 사항

- P0: 0
- P1: 0
- P2: 0
- P3: 0

## 근거

- `build-logic` path filter now covers root Gradle scripts, `gradle.properties`, `gradle/**`, and `buildSrc/**`.
- 이제 `build-logic`이 바뀌면 모든 module test job도 실행된다.
- `ci-status` fails when a required module test is skipped for workflow dispatch, build-logic changes, or the matching module path.
- `actionlint .github/workflows/ci.yml`: PASS.
- `git diff --check`: PASS.
- `rg -n -F "\\'" .github/workflows/ci.yml`: no escaped expression quotes.
- Shell gate simulation:
  - build-logic changed + skipped module test: FAIL as expected.
  - module path changed + skipped module test: FAIL as expected.
  - unaffected module skipped: PASS as expected.

## 남은 위험

- 이 PR은 workflow-only change에 대해 module test를 강제하지 않는다. workflow syntax와 status logic은 `actionlint`와 shell simulation으로 로컬 검증한다.
- Snapshot publish gate와 release publish gate는 별도 issue(#194, #195)로 남긴다.
