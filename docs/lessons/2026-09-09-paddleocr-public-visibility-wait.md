# 외부 공개 전환은 단발 조회와 경합하지 않아야 한다

## 원인

Issue #638의 실행 `34249339498.1`은 release 서명과 private consumer 검증까지
통과했다. 그러나 다음 job의 단발 visibility 조회가 관리 UI의 공개 전환보다
먼저 끝나면서 public readback이 실패했다. 뒤이은 익명 검증도 아직 private인
package 접근을 거부했다. 검증을 느슨하게 하는 대신 외부 상태 전환을 기다리는
경계를 명시해야 했다.

## 수정

visibility 조회는 최대 30회이며 각 API 요청을 5초로 제한한다. private일 때만
다음 조회까지 5초 기다린다. 정상 public이면 성공하고, API 오류나 예상 밖 상태는
즉시 실패한다. 모든 조회가 private이면 실패한다. 요청과 대기를 합쳐 최대
295초이며 step에는 별도의 6분 제한을 둔다. 이 단계는 visibility를 변경하지
않으며 실제 공개 전환은 private 검증 후 관리 UI에서 수행한다.

## 검증

실제 workflow shell을 실행하는 테스트에서 지연된 private→public 전환,
계속 private인 상태, internal 응답, API 실패를 확인했다. 기존 단발 조회는
지연 전환과 제한 횟수 검증에 실패했고 수정 후 통과했다. workflow 테스트 15개가
Python 3.9·3.13에서 통과했으며 actionlint와 Python lint도 통과했다.
공개 전환 후 같은 실행의 증거를 실제 ORAS 1.3.4와 `gh`로 익명 검증해
파일 무결성, provenance, SBOM 서명 검증이 모두 PASS임을 확인했다.

## 재발 방지

외부 사람이 수행하는 상태 변경과 자동 검증 사이에는 충분한 관찰 시간을 둔다.
권한 오류까지 반복하거나 대기 시간 만료를 성공으로 취급하지 않는다. 최종 증거는
새 commit의 실제 PRODUCE와 익명 검증 결과로 판정한다.
