# Projects 1.10.0 BOM 인계

## 배경

`bluetape4k-projects` 1.10.0이 릴리스되었고 Maven Central에서
`bluetape4k-bom:1.10.0`을 확인할 수 있다.

## 결정

image와 AWS 릴리스 계열은 그대로 두고 로컬 카탈로그의 projects BOM 버전을
1.9.2에서 1.10.0으로 갱신한다.

## 결과

image 빌드는 이제 공통 bluetape4k 모듈 버전에 안정 버전 projects 1.10.0 BOM을
사용한다.

## 검증

- Maven Central의 `bluetape4k-bom:1.10.0` 요청이 HTTP 200을 반환했다.
