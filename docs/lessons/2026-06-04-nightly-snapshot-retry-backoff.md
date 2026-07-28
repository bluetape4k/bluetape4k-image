## 배경

Nightly와 CI matrix 작업이 Central snapshot에서 상위 `1.11.0-SNAPSHOT`
아티팩트를 해석하는 동안 간헐적으로 실패했다. 로컬 Central 메타데이터 확인은 HTTP
200을 반환했지만 GitHub-hosted runner는 간헐적으로 HTTP 403을 받았다.

## 결정

CI, Nightly, example 워크플로의 Gradle 단계에 같은 재시도 정책을 적용한다. 최대 5회
시도하고 각 시도 사이에 30초 기다린다.

## 결과

워크플로는 모듈 테스트를 실패로 처리하기 전에 일시적인 Central snapshot 메타데이터
장애가 복구될 시간을 더 확보한다.

## 검증

- `git diff --check`
- `actionlint .github/workflows/*.yml`

## 이후 지침

하위 bluetape4k 저장소가 아직 릴리스되지 않은 상위 snapshot을 사용한다면 먼저
상위를 안정화한다. 상위 CI와 Nightly gate가 통과한 뒤 하위 Nightly를 다시 실행한다.
