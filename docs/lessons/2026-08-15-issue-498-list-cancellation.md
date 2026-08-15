# Issue #498 LocalImageStorage 목록 순회 취소 경계

**관련 이슈**: #498
**영향 모듈**: `images-spring-boot`

## 배경

`LocalImageStorage.list`는 파일을 모두 `MutableList`에 모은 뒤에야 `Flow`로
방출했다. 호출자가 `take(1)`처럼 일부 결과만 소비하거나 취소해도 디렉터리 전체를
순회하고 결과를 메모리에 보관하므로, 큰 prefix에서 불필요한 I/O와 메모리 사용이
발생했다.

## 결정

디렉터리 순회 callback을 suspend 경계로 바꾸고 각 iterator·attribute 조회·재귀 진입·
방출 직전에 `currentCoroutineContext().ensureActive()`를 확인한다. 결과는 순회 중
즉시 `Flow`로 방출하며 별도의 전체 결과 materialization을 두지 않는다. 기존
`SecureDirectoryStream`과 `NOFOLLOW_LINKS` 경로를 유지해 symbolic-link 우회 방어와
descriptor 수명 규칙을 변경하지 않는다.

`CancellationException`은 기존처럼 다시 던지고, permission 오류와 일반 I/O 오류의
`ImageStorageException` 매핑도 유지한다. `flowOn(Dispatchers.IO)` 경계는 그대로 두어
blocking filesystem 호출이 caller dispatcher를 점유하지 않게 한다.

## 결과

collector가 첫 항목에서 취소하면 나머지 파일을 수집하지 않고 순회를 종료한다. prefix,
nested key, 빈 디렉터리, missing prefix, symbolic-link 미추적 동작은 기존 계약을
그대로 유지한다. 내부 helper의 suspend 전환은 public ABI를 변경하지 않는다.

## 검증

- `LocalImageStorageTest`에 50,000개 파일 fixture와 `take(1)` bounded cancellation 회귀를 추가
- streaming 구현 targeted test 통과
- 이전 전체 materialization 구현은 동일 fixture에서 500ms bounded test를 통과하지 못함
- `git diff --check`

## 향후 방지책

새 filesystem `Flow`는 먼저 결과를 수집하는 collection API로 구현하지 않는다. 순회
경계마다 cooperative cancellation을 검사하고, 부분 소비(`take`, `first`, 취소된
collector)와 대형 디렉터리 fixture를 함께 검증한다. descriptor-relative/no-follow
보호를 유지해야 하므로 순회 helper를 단순한 `Files.walk`로 대체하지 않는다.
