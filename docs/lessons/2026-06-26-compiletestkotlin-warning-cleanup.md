# compileTestKotlin warning 정리

## 배경

`compileTestKotlin --warning-mode all --rerun-tasks`가 image repository의 Kotlin
compiler와 test source warning noise를 드러냈다. 이번 정리는 public API surface를
바꾸지 않고 image core, Spring storage, JVips resize code를 다뤘다.

## 결정

- 범위는 repository가 직접 소유한 source/test warning으로 제한한다.
- Kotlin 2.3 private data-class constructor 지침에 맞춰 `@ConsistentCopyVisibility`를
  추가하고 validation을 companion factory로 옮긴다.
- JVips `thumbnailImage`처럼 직접 대체 가능한 deprecated/noisy API는 warning suppress가
  아니라 replacement로 정리한다.
- 수정한 test에서는 JUnit boolean type check 대신 bluetape4k assertion helper를 사용한다.

## 결과

`./gradlew compileTestKotlin --warning-mode all --rerun-tasks`가 통과한다. 남은
warning-mode 출력은 build logic에서 오는 Gradle 10 deprecation noise이다:
`ReportingExtension.file`, project dependency notation, Kotlin DSL delegate syntax.
작업 범위를 명시적으로 넓히지 않는 한 이 항목은 별도 build-logic follow-up으로 다룬다.

## 향후 방지책

warning cleanup PR에서는 evidence를 다음처럼 나눈다.

1. source 또는 test에서 수정한 Kotlin/compiler warning.
2. owned code에서 수행한 deprecated API replacement.
3. 별도로 문서화한 잔여 Gradle/plugin/build-logic warning.

Gradle deprecation warning이 남아 있는 상태에서 전체 `--warning-mode all` 출력이
깨끗하다고 주장하지 않는다.
