# bluetape4k BOM 스냅샷 정렬

## 배경

공유 의존성 카탈로그를 갱신한 뒤 `bluetape4k-dependencies`에서 관리하는
`bluetape4k-bom` 별칭을 `1.8.0`에서 `1.8.1-SNAPSHOT`으로 올렸다.

## 결정

하위 저장소 동기화 검증을 다시 실행하기 전에 이 저장소의 로컬 카탈로그를
중앙 BOM 스냅샷과 일치시킨다.

## 결과

카탈로그가 중앙 의존성 제약과 같은 `1.8.1-SNAPSHOT` 계열에서
bluetape4k 모듈을 해석한다.

## 검증

- 브랜치를 병합한 뒤 `bluetape4k-dependencies`에서
  `scripts/sync-shared-versions.py --workspace .. --check --summary`를
  실행하면 이 저장소가 더 이상 보고되지 않아야 한다.

## 향후 참고

중앙 BOM이 새로운 bluetape4k 스냅샷을 가리키면 로컬 `bluetape4k-bom`
별칭을 유지하는 하위 저장소는 중앙 변경이 반영된 뒤 별도의 동기화 PR이 필요하다.
