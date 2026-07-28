# 2026-05-29 - Spring Boot Image API 예제

## 배경

Issue #125에서는 더 큰 워크숍 S3/CDN 흐름을 끌어오지 않고 로컬 스토리지를
보여 주는 간결한 저장소 로컬 Spring Boot Image API 빠른 시작 예제가 필요했다.

## 결정

`examples/spring-boot-image-api`를 게시하지 않는 Spring Boot 4 예제로
추가한다. `bluetape4k-images-spring-boot`의 로컬 스토리지 자동 구성을 사용하고,
S3/CDN 설정은 고급 워크숍 영역으로 문서화한다.

## 결과

이 예제는 멀티파트 업로드를 받고 이미지 콘텐츠 유형을 검증한 뒤 원본을 저장하고
PNG 썸네일을 만든다. 스토리지 키와 로컬 조회 URL을 반환하며, MockMvc와
메모리에서 생성한 JPEG 바이트로 전체 흐름을 테스트한다.

## 검증

병합 전에 `./gradlew :spring-boot-image-api:test`를 실행한다.

## 향후 참고

예제의 첫 실행 경로는 로컬에서 결정적으로 동작하도록 유지한다. 다음 단계에 외부
자격 증명, 공개 URL 정책 또는 다중 서비스 인프라가 필요하면 워크숍
애플리케이션으로 안내한다.
