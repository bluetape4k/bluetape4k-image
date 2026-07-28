# 이슈 192 S3 연산 누락 시 빠른 실패

## 배경

이슈 #192에서 `S3Operations` bean이 없을 때 `backend=s3`가 아무런 경고
없이 로컬 파일 시스템 스토리지를 생성하는 문제가 확인되었다. 이 동작은
프로덕션 구성 오류를 숨기며, 이미지 데이터를 인스턴스의 로컬 임시
스토리지에 쓸 수 있다.

## 결정

S3 백엔드를 명시적으로 선택했다면 애플리케이션이 `S3Operations` bean이나
자체 `ImageStorage` bean을 제공하지 않는 한 시작 단계에서 실패해야 한다.
로컬 스토리지는 `backend=local`이거나 backend property를 생략한 경우에만
기본값으로 사용한다.

## 결과

- S3 로컬 대체 구성을 시작 단계에서 실패시키는 검증 bean으로 교체했다.
- 사용자 정의 스토리지 구현을 위해 사용자 제공 `ImageStorage`가 있으면
  자동 구성을 적용하지 않는 동작을 유지했다.
- S3가 더 이상 로컬 스토리지로 대체되지 않는다는 점을 사용자가 확인할 수
  있도록 Spring Boot README 쌍과 KDoc을 수정했다.
- 변경한 MockK 픽스처를 클래스 수준 필드로 옮기고
  `clearMocks(...)`.

## 검증

- 수정 전 실패 테스트:
  `./gradlew :bluetape4k-images-spring-boot:test --tests 'io.bluetape4k.images.spring.autoconfigure.ImagesStorageAutoConfigurationTest' --no-daemon`
  실행 결과는 프로덕션 코드를 수정하기 전에 테스트 1개 실패였다.
- 수정 후 대상 테스트:
  `./gradlew :bluetape4k-images-spring-boot:test --tests 'io.bluetape4k.images.spring.autoconfigure.ImagesStorageAutoConfigurationTest' --no-daemon`
  실행 결과는 `9 passing`이었다.
- 모듈 테스트:
  `./gradlew :bluetape4k-images-spring-boot:test --no-daemon` 실행 결과는
  `123 passing`이었다.
- `git diff --check`: `PASS`.

## 향후 지침

프로덕션 백엔드를 명시적으로 선택한 경우 암묵적인 대체 백엔드를 추가하지
않는다. 향후 개발 환경용 대체 동작이 필요하다면 이를 활성화하기 전에
명시적인 opt-in property를 추가하고 README 쌍에 문서화해야 한다.
