# 교훈 — image Nightly configuration cache (2026-06-04)

**관련 이슈**: #148
**영향 워크플로**: `.github/workflows/nightly-tests.yml`

## 배경

병합 후 Nightly smoke 실행 `26962025345`가 `Test / images-ktor`에서 실패했다.
GitHub runner는 `io.github.bluetape4k:bluetape4k-core:.`,
`org.jetbrains.kotlinx:kotlinx-coroutines-core:.` 등 BOM으로 관리되는 의존성의
버전을 비운 채 해석했다.

## 결정

`--refresh-dependencies`는 유지하고 compile, test, Kover 보고서 생성에 사용하는 모든
Nightly Gradle 명령에서 configuration cache를 비활성화한다. 이 실패는 소스 테스트
문제가 아니라 runner와 cache 상태에 따른 문제다.

## 검증

- `actionlint .github/workflows/nightly-tests.yml`
- `git diff --check`
- Nightly Gradle 점검: 모든 `./gradlew` 실행 블록에 `--refresh-dependencies`와
  `--no-configuration-cache`가 포함되어 있다.

## 이후 규칙

GitHub runner의 Nightly 작업에서 BOM 관리 의존성의 버전이 비어 있다면 해당 저장소의
모든 워크플로 Gradle 호출을 점검한다. 병합 후 Nightly 재실행으로 단일 모듈 문제임을
입증하지 못했다면 수정 범위를 실패한 모듈 하나로 제한하지 않는다.
