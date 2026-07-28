# 2026-06-04 Issue 152 Nightly Gradle cache

## 배경

여러 bluetape4k 저장소의 Nightly 빌드에서 GitHub runner가 관리 대상 의존성을
간헐적으로 `group:artifact:.` 형식으로 해석했다.

## 결정

예약 실행이 오래된 의존성 관리 상태를 재사용하지 않도록 Nightly 작업에서
`gradle/actions/setup-gradle` cache 복원과 쓰기를 비활성화한다.

## 결과

모든 Nightly `setup-gradle` 블록은 명시적인 Gradle 의존성 새로 고침을 유지하면서
`cache-disabled: true`를 설정한다.

## 검증

- `.github/workflows/nightly-tests.yml`을 점검해 `setup-gradle` 블록과
  `cache-disabled` 블록의 수가 일치함을 확인했다.
- 예정된 검증: `actionlint`, `git diff --check`.

## 이후 규칙

Nightly 워크플로에서 snapshot 또는 BOM 관리 bluetape4k 의존성을 사용한다면 cache
복원이 오래된 메타데이터를 재사용하지 않음을 새 CI 결과로 입증할 때까지 Gradle
action cache를 비활성화한다.
