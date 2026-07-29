# 릴리스 워크플로 표준화

배경: Central Portal 릴리스 작업에서는 `bluetape4k-projects`의 릴리스 워크플로
구조를 표준으로 사용한다.

결정: 워크플로 표시 이름은 유지하면서 릴리스 준비 워크플로 파일명을
`nightly-tests.yml`과 `publish-snapshot.yml`로 변경한다.

결과: 릴리스 준비 스크립트가 bluetape4k 저장소 전반에서 같은 워크플로 파일명을
사용할 수 있다.

검증: `actionlint .github/workflows/nightly-tests.yml .github/workflows/publish-snapshot.yml .github/workflows/release.yml`.

향후 방지책: 저장소별 예외를 `AGENTS.md`에 기록하지 않는 한 릴리스 워크플로
파일명을 `bluetape4k-projects`와 일치시킨다.
