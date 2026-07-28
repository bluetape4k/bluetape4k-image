# AWS 0.3.1 카탈로그 동기화

## 배경

`bluetape4k-dependencies` 1.2.0 최종 BOM을 준비하면서 AWS 릴리스가 Maven
Central에 공개된 것을 확인한 뒤 `bluetape4k-aws-bom`을 `0.3.0`에서 `0.3.1`로
올렸다.

## 결정

image 저장소의 로컬 공유 카탈로그를 dependencies 원본과 일치시키고
`bluetape4k-aws-bom`을 `0.3.1`로 변경한다.

## 결과

image 카탈로그는 이제 최종 dependencies 1.2.0 BOM이 게시할 공개 안정 버전의 AWS
계열을 사용한다.

## 검증

- `sync-shared-versions.py --workspace .. --write --check --summary`
  명령으로 image 카탈로그를 `0.3.0`에서 `0.3.1`로 갱신했다.
- Maven Central에서
  `io.github.bluetape4k.aws:bluetape4k-aws-bom:0.3.1`.
  요청이 HTTP 200을 반환했다.
