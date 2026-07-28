# 2026-05-29 - Ktor Image API 예제

## 배경

`images-ktor`가 반영된 뒤 Issue #126에서는 사용자가 라이브러리 모듈 테스트에서
시작하지 않고 Ktor 통합 경로를 직접 실행할 수 있는 저장소 로컬 빠른 시작
예제가 필요했다.

## 결정

`examples/ktor-image-api`를 게시하지 않는 Ktor 3 애플리케이션으로 추가한다.
예제에서 경로 로직을 중복하지 않고 기존 `bluetape4kCaptchaRoutes`와
`bluetape4kImageThumbnailRoutes` 도우미를 조합한다.

## 결과

이 예제는 준비 상태 엔드포인트, CAPTCHA 발급/검증 경로, 멀티파트 PNG 썸네일
경로를 제공한다. 각 언어 README 파일은 CAPTCHA와 이미지 처리 흐름의 curl
사용법을 설명한다.

## 검증

병합 전에 `./gradlew :ktor-image-api:test`, `./gradlew projects`,
`actionlint`를 실행한다.

## 향후 참고

실행 가능한 예제는 게시된 모듈 API 위의 조합 계층으로 유지한다. 경로 도우미
동작은 모듈 테스트에서 계속 검증하고, 예제는 최초 실행 연결과 사용자 명령을
증명해야 한다.
