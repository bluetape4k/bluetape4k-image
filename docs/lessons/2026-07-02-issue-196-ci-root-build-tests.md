# 이슈 #196 CI 루트 빌드 테스트 범위

## 배경

CI workflow는 모듈 경로 변경을 감지했지만 루트 Gradle, version catalog,
Gradle wrapper 또는 `buildSrc` 변경 시 모듈 테스트가 필요하다고 판단하지
않았다. 최종 상태에서도 건너뛴 모듈 job을 성공으로 처리했다.

## 결정

`build-logic` 경로 필터를 추가하고 모든 모듈 테스트 job에 적용한다.
모듈이 실제로 영향을 받지 않을 때만 건너뛴 job을 허용한다.

## 결과

이제 루트 빌드 로직을 변경하면 모든 이미지 모듈 테스트 job을 실행해야
한다. workflow dispatch, build-logic 변경 또는 일치하는 모듈 경로로 인해
필요해진 모듈 테스트를 건너뛰면 최종 상태 단계가 실패한다.

## 검증

- `actionlint .github/workflows/ci.yml`
- `git diff --check`
- `rg -n -F "\\'" .github/workflows/ci.yml`
- 필수 테스트를 건너뛴 경우와 영향받지 않은 테스트를 건너뛴 경우에 대한
  로컬 셸 시뮬레이션

## 향후 지침

CI가 경로 필터를 적용한 모듈 job을 사용한다면 루트
Gradle/catalog/buildSrc 변경을 포괄하는 build-logic 필터를 추가한다.
집계 상태에서는 필수 job을 건너뛴 경우와 영향받지 않은 job을 건너뛴 경우를
구분해야 한다.
