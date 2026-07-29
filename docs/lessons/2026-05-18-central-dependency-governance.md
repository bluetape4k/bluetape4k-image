# 중앙 의존성 거버넌스 동기화

## 배경

하위 저장소의 Dependabot PR이 공유 의존성 버전을 저장소별로 갱신하면서
bluetape4k 조직 전반에 버전 불일치가 생겼다.

## 결정

공유 의존성 버전은 먼저 `bluetape4k-dependencies`에서 변경한 뒤
`sync-shared-versions.py`로 이 저장소에 반영한다. 또한 중앙에서 관리하는
의존성 이름을 이 저장소의 Dependabot 대상에서 제외해 이후 PR이 중앙 원본을
거치도록 한다.

## 결과

로컬 버전 카탈로그와 `.github/dependabot.yml`이 중앙 의존성 거버넌스 정책을
따르게 되었다.

## 검증

- 이 저장소에서 `sync-shared-versions.py --write --check --summary`
- 이 저장소에서 `sync-dependabot-ignores.py --write --check --summary`
- `git diff --check`

## 향후 방지책

중앙에서 관리하는 의존성의 저장소별 Dependabot PR은 병합하지 않는다.
`bluetape4k-dependencies`를 갱신한 뒤 이 저장소를 동기화한다.
