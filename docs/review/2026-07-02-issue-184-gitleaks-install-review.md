# Issue #184 Gitleaks Install 검토

## 범위

- 이슈: #184 `ci: harden gitleaks release asset install`
- 검토 파일: `.github/workflows/ci.yml`
- 변경 유형: CI maintenance

## 발견 사항

- P0: 없음.
- P1: 없음.

## 검토 메모

- install step은 이제 `tag_name`에서 latest download URL을 재구성하지 않고 latest GitHub release asset metadata에서 Linux x64 archive를 찾는다.
- fallback은 known-good `v8.30.1` archive에 고정되어 있어 API outage가 secret scanning을 조용히 건너뛰게 만들지 않는다.
- 해당 step은 설치 후 `gitleaks version`을 출력해 `gitleaks detect` 실행 전에 GitHub Actions log에 직접 설치 증거를 남긴다.

## 검증

- `actionlint .github/workflows/ci.yml`
- `git diff --check`
- Release API asset selection smoke test for `linux_x64.tar.gz`
- `gitleaks detect --source . --redact --no-git --config .gitleaks.toml`
