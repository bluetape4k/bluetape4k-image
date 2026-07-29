# bt4k 버전 카탈로그 사용

## 배경

`bluetape4k-image`는 일부 공유 의존성 버전을 `bluetape4k-dependencies`에서
읽지 않고 로컬에 중복 정의했다.

## 결정

`io.github.bluetape4k:bluetape4k-version-catalog`를 `bt4k`로 가져오고,
공유 말단 의존성 제약에 `bt4kVersion(alias)`를 사용한다.

## 결과

선택한 로컬 의존성 별칭에서 버전을 제거하고, 의존성 관리가 공유 카탈로그에서
관리 버전을 제공하도록 했다.

## 검증

- `git diff --check`
- `./gradlew help --no-daemon --no-configuration-cache`
- `./gradlew compileKotlin --no-daemon --no-configuration-cache`

## 향후 지침

이미지 전용 좌표는 로컬에서 유지하되, `bluetape4k-dependencies`에 별칭이 있으면
공유 버전 값은 `bt4k`에서 가져온다.
