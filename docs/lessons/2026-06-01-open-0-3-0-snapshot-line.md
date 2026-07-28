# 2026-06-01 0.3.0 Snapshot 계열 시작

## 배경

이전 dependencies 릴리스 train을 위해 `bluetape4k-image` `0.2.0`을 유지했다.
다음 카탈로그 train snapshot은 새로운 projects와 AWS snapshot 계열을 사용해야 한다.

## 결정

커밋된 `baseVersion=0.3.0`을 유지하고 `snapshotVersion=`은 비워 둔다. 직접 BOM
참조는 `bluetape4k-bom:1.11.0-SNAPSHOT`과
`bluetape4k-aws-bom:0.4.0-SNAPSHOT`.
에 맞춘다.

## 결과

저장소가 다음 내부 snapshot train을 기준으로 의존성을 해석한다.

## 검증

- `./gradlew help --no-daemon --console=plain`으로 갱신된 카탈로그의 의존성 해석을
  확인했다.
