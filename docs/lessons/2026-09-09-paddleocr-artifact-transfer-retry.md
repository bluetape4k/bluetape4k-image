# Artifact 전송 오류는 같은 단계에서 제한적으로 복구한다

## 원인

#638의 hosted 실행은 모델·소스·이미지·서명 검증을 통과한 뒤에도 Actions
artifact 전송에서 간헐적으로 실패했다. `ListArtifacts`와 `FinalizeArtifact`가
intermediary의 403을 반환했다. `34253694284.1`은 다운로드에서,
`34254198170.1`은 staging 서명 후 cleanup fragment 업로드에서 중단됐다.
전체 producer를 다시 실행하면 이미 통과한 빌드와 검증 비용까지 반복된다.

## 수정

producer에 한정된 local composite action으로 기존 full-SHA-pinned upload와
download action을 최대 세 번 실행한다. 실패한 시도 뒤에만 10초 기다린다.
취소하면 재시도를 중단하고, 마지막 실패는 job 실패로 남긴다. job 전체 제한과
기존 artifact ID·digest·run/attempt 검증은 유지한다.

upload의 성공한 시도에서만 artifact ID·digest·URL을 전달한다. 실패 후 재시도는
동일한 고유 run/attempt 이름의 미인계 artifact만 덮어쓸 수 있다. 아직 caller에
성공 output을 반환하지 않았으므로 consumer가 참조 중인 ID를 교체하지 않는다.
첫 시도는 덮어쓰지 않는다. download는 digest 불일치를 오류로 처리한다.

## 검증과 한계

기존 단발 전송 workflow에 대한 회귀 검사는 실패했다. 제한 횟수, 지연 실행,
취소, 영구 실패, 성공한 시도의 output 선택을 검증하고 기존 producer 계약과
같이 Python 3.9·3.13 CI에 포함한다. artifact wrapper는 새 외부 의존성을 추가하지
않으며 기존 Actions SHA를 그대로 사용한다.

실제 output shell 회귀 검사 6개와 workflow 계약 16개가 Python 3.9·3.13에서
통과했다. 잘못된 digest 검사는 macOS Bash 3.2에서 `set -e`만으로 종료되지 않는
문제를 드러냈고, 명시적 실패 종료를 추가해 잠갔다.

재시도는 GitHub 장애의 원인을 해결하지 않는다. 세 번 모두 실패하면 성공으로
취급하지 않는다. 실제 hosted 전체 실행과 cleanup의 PASS가 최종 완료 증거다.
