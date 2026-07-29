# 코드 스캔 워크플로 권한

## 배경

GitHub CodeQL이 릴리스, 스냅샷 게시, Nightly 워크플로 작업에서
`actions/missing-workflow-permissions` 경고를 보고했다.

## 결정

소스를 체크아웃하는 작업에는 워크플로 수준의 `contents: read` 기준을 선언하고,
토큰이 필요 없는 집계 또는 판정 작업은 `permissions: {}`로 재정의한다. 릴리스
생성은 `gh release create`를 호출하므로 작업 수준의 `contents: write` 권한을
유지한다.

## 결과

Gradle, 게시, 릴리스 동작을 변경하지 않으면서 워크플로 토큰 경계를 명시하고
최소 권한 원칙을 적용했다.

## 검증

PR 전에 변경한 워크플로에서 `actionlint`를 실행하고 YAML 차이를 검사한다.

## 향후 방지책

워크플로나 작업을 추가할 때 워크플로 수준 권한 또는 작업 수준 재정의를 함께
선언한다.
