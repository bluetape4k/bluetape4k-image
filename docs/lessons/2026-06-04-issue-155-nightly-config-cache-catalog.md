# 2026-06-04 Issue 155 Nightly configuration cache와 카탈로그

## 배경

Nightly 워크플로는 snapshot과 BOM 관리 의존성을 사용하므로 오래된 Gradle 또는
configuration 상태가 버전 없는 의존성 좌표를 만들 수 있다.

## 결정

Nightly Gradle 명령에 `--no-configuration-cache`를 유지하고 로컬 bluetape4k 별칭의
버전은 BOM 참조를 통해 지정한다.

## 결과

Nightly 명령은 의존성을 새로 고칠 때 configuration cache에 의존하지 않으며 저장소
로컬 카탈로그 별칭은 `group:artifact:.` 좌표를 만들지 않는다.

## 검증

- 예정된 검증: `actionlint`, `git diff --check`, 명령 점검, 카탈로그 별칭 점검.

## 이후 규칙

snapshot을 새로 고치는 Nightly 작업에서는 저장소별 근거로 안전함을 입증하지 않는 한
Gradle action cache와 configuration cache를 모두 비활성화한다.
