# Issue #499 filesystem capability 계약 lesson

## 배경

`LocalImageStorage`는 path 기반 fallback 없이 `SecureDirectoryStream`의
descriptor-relative 접근과 existing-target replace를 사용한다. 이 전제는 JDK
filesystem provider마다 달라질 수 있으므로 단일 호스트의 성공 테스트만으로는
계약을 증명할 수 없다.

## 결정

- test source에 provider capability probe를 두고 `SecureDirectoryStream`, existing-target
  atomic replace, POSIX attribute 지원 여부를 함께 기록한다.
- JDK ZipFS를 dependency 없는 unsupported-provider fixture로 사용한다. capability가 없으면
  `LocalImageStorage`가 안전하지 않은 fallback을 선택하지 않고 fail closed하며, parent/file
  side effect가 없어야 한다.
- `LocalImageStorage`는 constructor에서 provider-specific `Path`를 문자열로 round-trip하지
  않는다. 따라서 ZipFS 같은 비기본 provider를 실제로 전달한 상태에서 unsupported 계약을
  검증할 수 있다.
- 지원 provider matrix는 root/nested parent, missing/existing target, overwrite, root/parent
  replacement, symbolic link, permission, 실패 source의 기존 target 보존·staging cleanup,
  cancellation을 검증한다. POSIX 권한은 provider나
  process가 강제할 수 없으면 테스트 출력에서 명시적으로 N/A가 된다.
- Ubuntu와 macOS CI에서 같은 contract test를 실행해 provider 차이를 조기에 드러낸다.

## 검증

- `LocalFileSystemContractTest`: 8개 테스트 통과.
- 기존 `LocalImageStorageTest`: baseline 29개 테스트 통과.
- `.github/workflows/ci.yml`: `actionlint` 통과.

## 놓친 점과 다음 guard

`CancellationException`은 `flowOn` 경계를 지나며 같은 인스턴스 identity가 보장되지 않을 수
있다. public contract는 cancellation type/message 전파로 고정하고 identity를 요구하지 않는다.
새 filesystem provider를 도입할 때는 probe와 unsupported fixture를 함께 추가하며, capability가
없는 provider에 path-based atomic fallback을 추가하지 않는다.
