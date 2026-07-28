# Issue #158 Central snapshot 재시도

## 배경
GitHub runner가 Central Portal snapshot 메타데이터에서 일시적인 HTTP 403 응답을
받으면 하위 CI와 Nightly 실행이 실패할 수 있다.

## 결정
Gradle 명령의 의미는 바꾸지 않고 최상위 Gradle 빌드와 Nightly detekt gate를 최대
3회 실행하는 제한된 재시도 loop로 감싼다.

## 검증
- `git diff --check`
- `actionlint .github/workflows/ci.yml .github/workflows/nightly-tests.yml`

## 다음 작업
bluetape4k SNAPSHOT 의존성이 Central 메타데이터 403으로 실패하면 먼저 상위 게시
상태를 확인한다. 의존성이나 카탈로그를 불필요하게 바꾸기보다 제한된 워크플로 재시도를
우선한다.
