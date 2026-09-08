# 익명 검증에는 토큰 제거와 빈 설정 경로가 함께 필요하다

## 실패와 원인

Issue #638의 PRODUCE `34243680616.1`은 image 게시, release SBOM·provenance,
private evidence 검증까지 통과했다. 공개 전환 후 `verify-public-evidence`는
네트워크 요청 전에 `SCHEMA_INVALID`로 종료됐다.

workflow가 `DOCKER_CONFIG`를 제거했지만 `validate_anonymous_environment`는
절대 경로인 `HOME`과 `DOCKER_CONFIG`를 필수로 요구했다. 기존 `HOME`과
`GH_CONFIG_DIR`도 상속하므로 토큰 환경변수만 제거해서는 저장된 인증 설정과
분리됐음을 보장할 수 없었다.

## 수정과 검증

실행마다 `.producer-state` 아래 새 임시 디렉터리를 만들고 home, docker, gh
설정 경로를 준비한다. 검증 자식 프로세스에만 이 경로들을 지정하며 shell의
기존 환경과 인증 파일은 변경하지 않는다. 검증기가 금지하는 다섯 credential
환경변수를 제거하고 기존 공통 cleanup으로 임시 경로를 정리한다.

회귀 테스트는 실제 workflow shell에 가짜 credential 환경변수와 설정 파일을
주입하고 실제 익명 환경 검증기를 호출한다. 기존 호출부의 거부를 재현했으며,
수정 후에는 격리된 경로만 전달되고 원래 설정 파일은 보존된다. workflow
계약 테스트 14개가 Python 3.9와 3.13에서 통과했다.

실제 공개 evidence는 별도의 익명 HTTP transport와 `gh attestation verify`로
manifest·파일·provenance·SBOM 검증을 통과했다. 이는 증거 자체와 환경 준비
오류를 구분하는 진단이며, 수정된 ORAS workflow의 실제 실행 성공을 대신하지
않는다. 최종 완료에는 새 commit의 PRODUCE 검증이 필요하다.

## 재발 방지

인증 관련 환경변수를 무조건 삭제하기 전에 소비 함수가 빈 값, 미설정 값,
빈 전용 디렉터리 중 무엇을 요구하는지 확인한다. workflow 테스트에는 텍스트
검사뿐 아니라 실제 shell과 소비 함수 사이의 계약을 검증하는 사례를 둔다.
