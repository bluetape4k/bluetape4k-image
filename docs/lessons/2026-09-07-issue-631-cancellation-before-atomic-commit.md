# Issue #631 원자적 파일 교체의 coroutine 취소 경계

## 배경

`S3ImageStorage.download(key, destination)`은 공용 `Path.writeAtomically`를
사용해 임시 파일을 destination으로 교체한다. 이 공용 API는 blocking callback이
정상 반환되면 commit을 수행하므로, suspend adapter가 coroutine 취소를 callback
반환 전에 확인해야 한다.

## 원인과 수정

기존 구현은 `withContext`가 반환될 때 취소를 관찰했다. 다운로드가 끝난 직후 Job이
취소되면 atomic commit이 먼저 완료되어 기존 destination이 교체될 수 있었다.

adapter 진입 시 `currentCoroutineContext()`를 캡처하고, 다운로드 snapshot 검증이
끝난 뒤 callback 반환 직전에 `ensureActive()`를 호출하도록 수정했다. 취소되면
callback이 예외로 끝나므로 provider가 임시 파일을 정리하고 기존 destination을
보존한다.

실제 `Deferred`를 EOF 직후 취소하는 회귀 테스트로 RED를 재현한 뒤 GREEN을
확인했다. 임시 파일 검증은 provider 규칙인 `.${target.fileName}.*.tmp`만 검사해
공유 system temporary directory의 다른 파일과 간섭하지 않게 했다.

## 의존성 결정

- catalog commit은 immutable SHA로 고정한다.
- 현재 catalog의 `bluetape4k-io`는 `2.1.0-SNAPSHOT`이며, 검증 시 선택된 실제
  artifact는 `2.1.0-20260906.132244-2`였다.
- `images`가 `bluetape4k-io`를 `api`로 공개하므로 `images-spring-boot`와
  `images-benchmark`에는 같은 의존성을 중복 선언하지 않는다. 모듈 경계를 분리해
  transitive 노출이 사라질 때만 직접 의존성을 추가한다.
- stable `2.1.0` artifact로의 전환은 provider release 완료 후 consumer release
  gate에서 다시 검증한다. timestamped SNAPSHOT은 현재 통합 검증 근거이지 장기
  재현성을 보장하는 release 입력이 아니다.

## 재사용 지침

blocking atomic writer를 coroutine adapter에서 사용할 때는 다음 순서를 유지한다.

1. suspend 경계에서 coroutine context를 캡처한다.
2. blocking write와 결과 검증을 끝낸다.
3. provider callback 반환 직전에 취소를 확인한다.
4. destination 보존과 provider 규칙 기반 임시 파일 정리를 함께 회귀 검증한다.
