# Nightly snapshot 새로 고침

## 배경

Nightly는 Gradle cache를 복원하고 변경 가능한 bluetape4k Central snapshot
아티팩트를 사용한다. 오래된 snapshot 메타데이터나 동시에 발생한 Central snapshot
메타데이터 요청 때문에 테스트 실행 전에 모듈 작업이 실패할 수 있다.

## 결정

Nightly Gradle 호출에 `--refresh-dependencies`를 전달하고 예약 cron의 실행 분을
분산한다. 모든 하위 저장소를 동시에 시작하지 않으면서 snapshot 메타데이터를 다시
확인하기 위한 조치다.

## 결과

Nightly는 빌드 상태 cache를 재사용하면서 변경 가능한 메타데이터를 새로 고치고 예약
실행에서 여러 저장소가 만드는 Central snapshot 경합을 줄인다.

## 검증

- `actionlint .github/workflows/nightly-tests.yml`
- `git diff --check`
