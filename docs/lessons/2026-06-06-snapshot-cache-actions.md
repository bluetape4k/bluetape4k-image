# Snapshot cache action

## 배경

Nightly는 이미 changing module cache TTL을 하루로 사용하고 있었지만, workflow는
여전히 job의 Gradle dependency cache를 비활성화하고 있었다.

## 결정

Nightly Gradle setup step에서 `cache-disabled: true`를 제거한다.

## 결과

Nightly는 기존 task 구조를 유지하지만, workflow에서 Gradle cache read/write 동작을
더 이상 명시적으로 비활성화하지 않는다.

## 검증

- `actionlint .github/workflows/*.yml`
- `rg -n -- '--refresh-dependencies|cache-disabled: true' .github/workflows` -> no matches
- `./gradlew help --no-daemon`
- `git diff --check`

## 향후 지침

명시적 dependency refresh는 게시 후 freshness 전용 검증에서만 사용한다. 일반 CI,
Nightly, Examples workflow는 cached changing-module metadata에 의존하고, test-only
SNAPSHOT dependency가 필요할 때만 targeted warm-up을 추가한다.
