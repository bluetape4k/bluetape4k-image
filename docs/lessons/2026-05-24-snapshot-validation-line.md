# 스냅샷 검증 계열

## 배경

이전 릴리스 이후 스냅샷 검증을 위해 저장소를 다음 개발 계열로 열고, 이에 맞는
상위 bluetape4k 및 AWS 스냅샷을 사용해야 했다.

## 결정

`baseVersion=0.1.3`으로 설정하고 `snapshotVersion=`은 비워 두며
`bluetape4k-bom:1.9.2-SNAPSHOT` plus
`bluetape4k-aws-bom:0.2.2-SNAPSHOT`을 함께 사용한다.

## 결과

`gradle.properties`에 스냅샷 접미사를 커밋하지 않고도
`publish-snapshot.yml`로 `0.1.3-SNAPSHOT`을 게시할 수 있다.

## 검증

스냅샷 검증 트레인에서 진행 예정이다.
