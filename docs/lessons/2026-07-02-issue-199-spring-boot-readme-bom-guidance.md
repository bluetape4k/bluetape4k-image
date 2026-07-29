# Issue #199 Spring Boot README BOM 지침

## 배경

Spring Boot module README pair는 image BOM을 먼저 import하지 않은 상태에서 versionless
dependency declaration을 보여주고 있었다.

## 결정

module README adoption path를 BOM-first로 만들고, BOM을 import하지 않는 consumer를 위해
명시적 `<version>` fallback도 함께 보여준다.

## 결과

이제 Spring Boot module README dependency section만 복사해도 resolve 가능한 Gradle
dependency path가 된다.

## 검증

- `git diff --check`
- `settings.gradle.kts` 대상 artifact-name 검색
- `bom/README.md`, `bom/README.ko.md` 대상 BOM usage 검색

## 향후 방지책

README 파일의 versionless module dependency는 BOM import와 인접해야 한다. README가
의도적으로 BOM을 피한다면 dependency를 명시적 `<version>` placeholder와 함께 보여준다.
