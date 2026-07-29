# 이슈 191 S3 다운로드 제한에서 얻은 교훈 (2026-07-01)

**관련 이슈**: #191
**영향받는 모듈**: `bluetape4k-images-spring-boot`

## 배경

`S3ImageStorage.download()`는 `downloadBytes()`를 호출하기 전에
`S3Operations.listPage()`로 가능한 범위에서 크기를 미리 확인했다. 크기
조회에 실패하거나 정확한 객체를 찾지 못해도 추가 확인 없이 객체
다운로드를 계속했다.

## 결정

`S3Operations`에 HEAD/metadata API가 없는 동안 S3 다운로드는 크기를
확인하지 못하면 안전하게 거부한다. 이제 스토리지는 목록 기반 사전 검사로
객체 크기를 검증한 경우에만 바이트 배열 다운로드를 시작한다. 객체 교체로
인한 경합이나 S3 metadata 불일치도 감지할 수 있도록 다운로드 후 반환된
바이트 배열의 크기를 다시 검증한다.

## 결과

S3 크기 사전 검사를 사용할 수 없거나 결과가 일관되지 않아도
`maxSizeBytes`가 무력화되지 않는다. 대상 경로로 다운로드할 때도 바이트를
쓰기 전에 `download(key)`를 호출하므로 같은 보호 장치가 적용된다.

## 검증

- 수정 전 실패 테스트: 문제를 해결하기 전 `S3ImageStorageTest`에서 테스트
  4개가 실패하고 1개가 통과했다.
- 수정 후 대상 테스트:
  `./gradlew :bluetape4k-images-spring-boot:test --tests 'io.bluetape4k.images.spring.storage.s3.S3ImageStorageTest' --no-daemon`
  실행 결과는 `5 passing`이었다.
- 모듈 테스트:
  `./gradlew :bluetape4k-images-spring-boot:test --no-daemon` 실행 결과는
  `123 passing`이었다.

## 향후 지침

안전한 다운로드 제한을 적용할 때 객체 크기 metadata 조회를 선택 사항으로
취급하지 않는다. 향후 `S3Operations` HEAD API를 사용할 수 있게 되면 목록
기반 metadata보다 우선해서 사용한다. 다만 S3 metadata 조회와 객체 교체가
경합할 수 있으므로 다운로드 후 바이트 수 검사는 유지해야 한다.
