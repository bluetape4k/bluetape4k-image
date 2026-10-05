# 작업 조정 도구를 호출하기 전에 명령 계약과 현재 상태를 확인한다

## 실패와 원인

PR #695의 CI 수정 중 작업 조정 도구의 인수를 여러 차례 잘못 가정했다. 현재 worktree와 다른 범위의 상태 저장 위치를 지정하지 않았고, `--expected-head`에 `receipt` 체크섬 대신 Git commit SHA를 넣었으며, 등록된 component ID와 일치하지 않는 값을 사용하고 JSON 입력도 유효한 구조로 전달하지 못했다. 이어서 전역 옵션 `--state-root`를 하위 명령 뒤에 둔 `verify`가 거부됐고, `lane-complete`에는 필수 `--at`을 빠뜨렸다. 이 명령들은 상태를 변경하지 않았다.

첫 reviewer `run`은 별도 기한 오류도 남겼다. `receipt`에는 `startup_ack_deadline=2026-10-05T05:38:45Z`인데 `lane_started=2026-10-05T05:39:38Z`로 기록되어 있다. 시작 기한이 지난 뒤 `lane`을 시작해 해당 `run`을 취소했다. 원인은 도구의 명령 계약과 현재 `receipt`를 함께 확인하지 않은 채 값을 추정해 넣은 데 있었다.

## 결정과 검증

작업 범위가 현재 worktree와 다르면 최상위 `--help`의 위치 규칙에 따라 하위 명령 앞에 절대 경로 `--state-root`를 둔다. 상태 변경 전에는 해당 하위 명령의 `--help`와 읽기 전용 `verify`를 읽고, `receipt` 체크섬은 직전 `verify` 결과에서 가져온다. component ID는 현재 승인된 topology에서 복사한다. 복합 입력은 도구 계약의 최상위 JSON 형식으로 파일에 작성하고, 파서가 성공한 뒤에만 상태 변경 명령에 전달한다. 필수 `--at` 같은 인자도 하위 명령 도움말에서 확인한다.

reviewer의 `startup_ack_deadline`도 실제 호출 직전에 현재 시각을 기준으로 정하고, `lane-start` 직전에 아직 미래인지 확인한다. 새 `run` `20261005T054408Z-cc319a28`의 `pr695-independent-review-r2` lane은 설정 기한보다 3초 늦게 startup ACK를 받았다. 후속 `pr695-final-review-r3`는 시작 후 설정 기한인 `2026-10-05T05:55:34Z`에 ACK했고, commit `0b40cbb5b136f27d11714ee76f4ba627654e3956`의 action pin 수정에 `APPROVE`, P0–P3 0건을 반환했다. 두 `lane`의 시각은 `receipt`에 기록되어 있다.

## 재발 방지

상태 조정 도구는 최상위 및 하위 명령 도움말과 현재 `receipt`를 확인한 뒤 호출한다. 값이 거부되면 형식을 추측해 다시 시도하지 말고, 읽기 전용 상태와 해당 인자 계약을 다시 읽는다. reviewer를 시작할 때는 새 기한을 설정하고 `startup_ack`의 실제 시각을 `receipt`에서 확인한다. 기한을 지난 ACK는 제때 시작한 것으로 보고하지 않는다.
