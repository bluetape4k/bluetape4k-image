# 2026-05-22 - 0.1.x 안정화 전 Images API 정리

## 배경

Issue #61에서는 `0.1.x` 계열을 안정 버전으로 간주하기 전에 오타 호환 API를
제거해야 했다. 영향받는 심볼은 적었지만 그대로 두면 철자가 틀린 이름이 공개
Kotlin 및 Java ABI에 포함된다.

## 결정

오타만 유지하는 호환 별칭을 제거한다:

- `ImageInputStream.usingSuspend(...)`
- `ImageOutputStream.usingSuspend(...)`
- `SuspendPngWriter.NoComppression`
- 철자가 틀린 Java 파사드 `ImageOuptputStreamSupportKt`

마이그레이션 가치가 있는 안정화 전 폐기 예정 API는 유지하되, 생성한 API 문서와
폐기 메시지에 제거 예정 버전을 기록한다.

## 결과

표준 API 표면은 사용자를 `useSuspending(...)`,
`SuspendPngWriter.NoCompression`, `ImageOutputStreamSupportKt`,
`ImmutableImage.withGraphics(...)`, `HashDistance.hamming(...)`으로 안내한다.

## 검증

- `./gradlew :bluetape4k-images:test --console=plain`
- `./gradlew :bluetape4k-images:build --console=plain`
- `./gradlew detekt --console=plain`
- `git diff --check`

## 향후 지침

안정화 마일스톤 전에 오타 전용 별칭의 폐기 기간을 연장하기보다 삭제한다.
폐기 예정 API를 유지한다면 KDoc과 `@Deprecated` 메시지 모두에 제거 예정 버전을
포함한다.
