# 의존성 카탈로그 2026-05-26-01

## 배경

`bluetape4k-dependencies`가 중앙에서 관리하는 보안 의존성 계열을 포함한
`catalog/2026-05-26-01`을 게시했다.

## 결정

공유 외부 라이브러리 버전을 로컬에 고정하지 않고 하위 저장소의 기본
`bluetape4kDependenciesCatalogRef`를 새 카탈로그 태그로 갱신한다.

## 결과

저장소는 기본적으로 `catalog/2026-05-26-01`에서 공유 의존성 버전을 해석한다.

## 검증

`settings.gradle.kts`의 카탈로그 참조를 확인했다.

## 향후 참고

공유 외부 라이브러리는 먼저 `bluetape4k-dependencies`를 갱신하고 카탈로그에
태그를 붙인 뒤 하위 저장소를 해당 태그로 옮긴다.
