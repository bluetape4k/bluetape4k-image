# #652 S3 존재 확인을 HEAD capability로 통일

## 원인과 결정

`S3ImageStorage.exists`가 정확한 object key를 확인하면서도
`listPage(prefix = fullKey, maxKeys = 1)`를 호출해 LIST 권한과 prefix 열거
결과에 의존했다. 유사 prefix 객체가 존재해도 정확한 key가 아니면 별도 필터가
필요하고, LIST가 차단된 환경에서는 존재 확인 자체가 실패한다.

같은 클래스의 download·metadata 경로가 이미 사용하는 `headObject` private
정규화 경로를 재사용했다. HEAD가 성공하면 `true`, `NoSuchKey` 또는 HTTP 404가
정규화된 `ImageStorageException.NotFoundException`이면 `false`를 반환한다.
403·전송 오류·취소는 기존 `AccessDeniedException`·`TransientException`·
`CancellationException` 계약을 그대로 전파하며, waiter나 fallback이 LIST를
호출하지 않는다.

## 검증과 재발 방지

수정 전 exact-key HEAD 성공/404/403/전송 오류/취소 테스트가 모두 LIST 호출로
실패하는 RED(23개 중 17 pass/6 fail)를 확인했다. 수정 후 exact-key 성공,
NoSuchKey·404 false, 403·전송 오류·취소 전파와 LIST 미호출을 검증했다.

- targeted `S3ImageStorageTest`: 23 pass
- Spring Boot 이미지 모듈: 222 pass
- `checkProductionAbi`: 10 published JVM modules 통과
- 변경 테스트 파일 detekt: PASS
- 기존 main 정적 경고 baseline과 비교해 새 warning 없음(기존
  `TooManyFunctions`, `TooGenericExceptionCaught`, `LoopWithTooManyJumpStatements`만
  남고 기존 `MaxLineLength` 1건은 줄바꿈으로 제거)

정확한 단일 객체 확인은 prefix LIST보다 provider의 HEAD capability를 먼저
찾는다. `S3Operations`에 이미 있는 정규화 경로를 재사용하면 SDK 예외 매핑과
취소 우선순위를 중복 구현하지 않아도 된다.

독립 code-reviewer와 architect는 모두 `CLEAR`를 판정했고 P0/P1/P2는 없었다.
실제 AWS IAM 계정 실험은 하지 않았으므로 HEAD 권한까지 차단된 경우의 외부
응답은 기존 domain mapping과 MockK 계약을 근거로 기록한다.
