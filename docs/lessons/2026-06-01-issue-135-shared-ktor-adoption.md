# 2026-06-01 - Issue 135 공통 Ktor 모듈 도입

## 배경

`bluetape4k-projects` 1.10.0에서 공통 `bluetape4k-ktor-*` 모듈을 게시했지만
`bluetape4k-images-ktor`와 `examples/ktor-image-api`에는 로컬 JSON/테스트
클라이언트 설정과 라우트 매개변수·오류 helper가 남아 있었다.

## 결정

공통 JSON 기본값, 요청 매개변수 파싱, `ApiErrorResponse` 잘못된 요청 payload에는
`bluetape4k-ktor-core`를 사용한다. JSON을 처리하는 테스트 클라이언트와 상태
검증에는 `bluetape4k-ktor-testing`을 사용한다. image 전용 CAPTCHA와 thumbnail
라우트 동작은 이 저장소에 유지한다.

## 결과

image Ktor 모듈은 이제 공통 Ktor core 모듈에 의존하며 quickstart는
`installBluetape4kKtorCore`를 설치하고 테스트는 공통 Ktor 테스트 helper를 사용한다.
공통 모듈이 이미 노출하는 Ktor JSON·테스트 의존성은 중복 직접 선언에서 제거했다.

## 검증

- `./gradlew :bluetape4k-images-ktor:test :ktor-image-api:test --no-daemon --no-configuration-cache --no-build-cache`

## 이후 주의 사항

하위 저장소에 공통 bluetape4k 모듈을 도입할 때는 중복 helper 코드를 먼저 교체한다.
대상 테스트로 공통 모듈이 필요한 API를 노출한다는 사실을 확인한 뒤에만 직접 의존성
선언을 줄인다.
