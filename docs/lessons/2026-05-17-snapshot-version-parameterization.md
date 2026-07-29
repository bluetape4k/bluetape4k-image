# 스냅샷 버전 매개변수화

배경: Central Portal 릴리스에서 `-SNAPSHOT`을 제거하려고 `gradle.properties`를
수정할 필요가 없어야 한다.

결정: 기본 `snapshotVersion=` 값을 비워 두고 `publish-snapshot.yml`이
`-PsnapshotVersion=-SNAPSHOT`을 전달하도록 한다.

결과: `develop`은 릴리스 가능한 상태를 유지하고, 스냅샷 게시는 워크플로 명령에서
명시적으로 수행한다.

릴리스 준비 결과: Central Portal 배포 전에 `bluetape4k-*` 의존성이
`-SNAPSHOT`이 아닌 정식 릴리스 버전을 사용한다.
버전 별칭은 저장소 이름만 쓰지 말고 `bluetape4k-bom`,
`bluetape4k-aws-bom`처럼 BOM 아티팩트 이름을 따른다.

검증: `actionlint .github/workflows/publish-snapshot.yml`.

향후 방지책: `gradle.properties`의 기본값으로
`snapshotVersion=-SNAPSHOT`을 다시 도입하지 않는다.
