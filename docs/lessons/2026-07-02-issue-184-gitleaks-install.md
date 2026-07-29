# 이슈 #184 Gitleaks 설치 과정 강화

## 배경

CI secret-scan job은 release tag로 최신 release asset URL을 재구성해
gitleaks를 설치했다. 이 방식은 asset 이름이나 redirect 변경에 취약하며,
`gitleaks detect`를 실행하기 전에 실패할 수 있다.

## 결정

GitHub Releases API의 asset metadata에서 Linux x64 아카이브를 찾고, 반환된
`browser_download_url`로 다운로드한다. API 장애에 대비해 버전을 고정한
대체 아카이브는 유지하되, 정상 경로에서는 현재 asset URL을 사용한다.

## 결과

이제 workflow는 `tag_name`을 파싱하거나 최신 asset URL을 추측하지 않는다.
설치 단계에서는 저장소 검사를 실행하기 전에 `gitleaks version`으로 설치된
바이너리를 검증한다.

## 검증

- `actionlint .github/workflows/ci.yml`
- `git diff --check`
- `linux_x64.tar.gz` Release API asset 선택 스모크 테스트
- `gitleaks detect --source . --redact --no-git --config .gitleaks.toml`

## 향후 지침

CI에서 사용하는 GitHub release 도구는 `.assets[]`에서 필요한 asset을
선택하고 해당 `browser_download_url`로 다운로드한다. 상위 프로젝트가
안정적인 계약으로 명시한 경우가 아니라면 `latest/download` URL을
재구성하지 않는다.
