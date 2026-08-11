# Issue #480 ImageStorage 저장 계약

**관련 이슈**: #480
**영향 모듈**: `images-spring-boot`

## 배경

공개 `ImageStorage` 계약은 upload가 atomic하고 `Path` overload가 대용량 입력을
stream한다고 명시했지만, Local 구현은 최종 파일을 직접 truncate/write했고 실패 시
기존 object까지 삭제할 수 있었다. S3 구현은 `Path`를 `ByteArray`로 먼저 읽었고,
Local의 lexical path 검사만으로는 root 내부 symbolic link 우회를 막을 수 없었다.

## 결정

Local은 source와 같은 디렉터리에 임시 파일을 만들고 내용을 기록한 뒤 file channel을
flush하고 `ATOMIC_MOVE`로 교체한다. 실패나 cancellation 시에는 임시 파일만 정리하며,
기존 object는 건드리지 않는다. download destination도 같은 staged replace 규칙을
사용한다.

S3 `Path` upload는 source를 bounded streaming 임시 snapshot으로 고정한 뒤 optional
`S3TransferOperations`의 file transfer를 사용하고, transfer capability가 없으면 source를
`ByteArray`로 적재하지 않고 fail closed한다.
`Path` download는 `S3Resource` input stream을 임시 destination으로 복사한 뒤 atomic
replace한다. Local key는 real root와 `NOFOLLOW_LINKS` attribute를 기준으로 검사하고
symbolic link와 permission 오류를 각각 validation/access-denied 계약으로 보존한다. Local의
각 연산마다 root descriptor를 열어 file-key를 재검증하고, 모든 exists/read/list/delete 경로를
descriptor-relative `NOFOLLOW_LINKS` 경로로 통일해 검사와 사용 사이의 symbolic-link 교체
경합을 차단한다. 연산별 descriptor는 블록이 끝나면 닫히며, 직렬화 상태에는
provider-specific `Path`/file-key 객체를 저장하지 않는다. Local path download가 root 내부를
root 내부 destination으로 복사할 때는 source를 먼저 bounded temporary snapshot으로
스트리밍해 source descriptor를 닫은 뒤 target을 atomic replace한다. 일부 Linux provider는
target/source를 동시에 descriptor-relative로 열 때 유효한 경로도 `NoSuchFileException`으로
거부할 수 있으므로, 두 descriptor를 중첩하지 않는다. JDK의 `SecureDirectoryStream`에
mkdirat가 없으므로 parent directory 생성 뒤 root와 모든 segment를 다시 검증하고, 검증을
통과한 경우에만 descriptor-relative object operation을 시작한다.

## 결과

provider 구현 모두 partial object 노출과 unbounded `Path` buffering 경계를 제거했다.
기존 S3 2-argument 생성자의 Java 호출 호환성을 위해 transfer capability를 추가한
constructor에는 `@JvmOverloads`를 유지한다.

## 검증

- Local storage 회귀 테스트 25개 통과
- S3 storage 회귀 테스트 9개 통과
- storage auto-configuration 테스트 9개 통과
- `images-spring-boot` 전체 테스트 137개 통과
- 실패 overwrite 보존, symbolic-link 거부, descriptor-relative atomic write, transfer fail-closed, resource streaming,
  bounded ByteArray read, root 교체 fail-closed, serialization round-trip, oversized precheck,
  cancellation destination 보존을 고정
- `git diff --check`

## 향후 방지책

새로운 filesystem storage는 최종 경로에 직접 쓰지 말고 staged file과 atomic replace를
먼저 설계한다. `Files.exists`는 permission failure를 숨길 수 있으므로 no-follow
attribute 조회 결과를 예외 계약으로 매핑한다. `Path` API를 구현할 때는 메모리 사용량을
검증하는 테스트를 함께 두고, optional transfer capability가 없을 때 조용히
`ByteArray` fallback을 추가하지 않는다.
