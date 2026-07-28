# Projects 1.9.2 BOM 전환

## 배경

`bluetape4k-projects` 1.9.2가 릴리스되었고 Maven Central에서
`bluetape4k-bom:1.9.2`를 확인할 수 있다.

## 결정

이 릴리스 준비 브랜치에서는 대응하는 projects 스냅샷 대신 안정 버전인
`bluetape4k-bom` 1.9.2 계열을 사용한다. 이번 전환은 이미 릴리스된 projects
BOM만 승격하므로 AWS BOM 참조는 현재 계열로 유지한다.

## 결과

버전 카탈로그는 이 저장소의 릴리스 계열을 변경하지 않으면서 안정 버전 1.9.2에서
`io.github.bluetape4k:bluetape4k-bom`을 해석한다.

## 검증

- `bluetape4k-bom:1.9.2`의 Maven Central 응답은 HTTP 200
- `./gradlew help --refresh-dependencies --no-daemon --no-configuration-cache --no-build-cache`
