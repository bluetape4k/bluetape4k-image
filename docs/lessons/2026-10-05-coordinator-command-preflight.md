# 작업 조정 도구를 호출하기 전에 명령 계약과 현재 상태를 확인한다

## 실패와 원인

PR #695의 CI 수정 중 작업 조정 도구의 인수를 여러 차례 잘못 가정했다. 현재 worktree와 다른 범위의 상태 저장 위치를 지정하지 않았고, `--expected-head`에 `receipt` 체크섬 대신 Git commit SHA를 넣었으며, 등록된 component ID와 일치하지 않는 값을 사용하고 JSON 입력도 유효한 구조로 전달하지 못했다. 이어서 전역 옵션 `--state-root`를 하위 명령 뒤에 둔 `verify`가 거부됐고, `lane-complete`에는 필수 `--at`을 빠뜨렸다. 이 명령들은 상태를 변경하지 않았다.

첫 reviewer `run`은 별도 기한 오류도 남겼다. `receipt`에는 `startup_ack_deadline=2026-10-05T05:38:45Z`인데 `lane_started=2026-10-05T05:39:38Z`로 기록되어 있다. 시작 기한이 지난 뒤 `lane`을 시작해 해당 `run`을 취소했다. 원인은 도구의 명령 계약과 현재 `receipt`를 함께 확인하지 않은 채 값을 추정해 넣은 데 있었다.

마지막 exact-head 검토도 `lane-create`와 `lane-start`를 `receipt`에 기록하기 전에 subagent 요청을 보냈다. 시작 절차를 생략한 실행은 중단하고 검토 근거로 사용하지 않기로 했다. 교훈 문서를 보완할 때도 파일을 고치기 전에 `mutation-check`를 실행하지 않았다. 뒤늦은 읽기 전용 확인은 단일 실행과 대상 경로를 확인했지만, 이를 사전 gate 통과로 간주하지 않았다. 두 경우 모두 도구의 사전 기록 순서를 실행에 적용하지 않은 누락이었다.

교훈 문서를 다시 검토하려고 만든 `pr695-lesson-writer-r3` lane은 `2026-10-05T06:38:33Z`에 시작했을 때 ACK 기한 `2026-10-05T06:38:35Z`까지 2초만 남아 있었다. 입력 파일을 만들고 receipt를 갱신하는 동안 `observed_at=2026-10-05T06:38:05Z`부터 28초가 지나 subagent 호출 전에 ACK 기한이 만료됐다. 에이전트는 부르지 않고 `2026-10-05T06:39:27Z`에 lane을 취소했다. 기한을 잡을 때 후속 도구 호출에 필요한 시간을 반영하지 않은 사례다.

## 결정과 검증

작업 범위가 현재 worktree와 다르면 최상위 `--help`의 위치 규칙에 따라 하위 명령 앞에 절대 경로 `--state-root`를 둔다. 상태 변경 전에는 해당 하위 명령의 `--help`와 읽기 전용 `verify`를 읽고, `receipt` 체크섬은 직전 `verify` 결과에서 가져온다. component ID는 현재 승인된 topology에서 복사한다. 복합 입력은 도구 계약의 최상위 JSON 형식으로 파일에 작성하고, 파서가 성공한 뒤에만 상태 변경 명령에 전달한다. 필수 `--at` 같은 인자도 하위 명령 도움말에서 확인한다.

reviewer의 `startup_ack_deadline`은 `lane-create` 등록 직전에 현재 시각으로 정하고, `lane-start` 뒤 subagent 호출과 ACK까지 필요한 시간이 남았는지 확인한다. 새 `run` `20261005T054408Z-cc319a28`의 `pr695-independent-review-r2` lane은 설정 기한보다 3초 늦게 startup ACK를 받았다. 후속 `pr695-final-review-r3`는 시작 후 설정 기한인 `2026-10-05T05:55:34Z`에 ACK했고, commit `0b40cbb5b136f27d11714ee76f4ba627654e3956`의 action pin 수정에 `APPROVE`, P0–P3 0건을 반환했다. 두 `lane`의 시각은 `receipt`에 기록되어 있다.

## 재발 방지

상태 조정 도구는 최상위 및 하위 명령 도움말과 현재 `receipt`를 확인한 뒤 호출한다. 저장소 파일을 쓰기 전에는 각 절대 경로에 대해 `mutation-check`를 실행하고 결과를 확인한다. 값이 거부되면 형식을 추측해 다시 시도하지 말고, 읽기 전용 상태와 해당 인자 계약을 다시 읽는다. 에이전트 입력과 증거 파일은 먼저 준비한다. `lane-create` 입력의 `observed_at`과 ACK 기한은 등록 직전에 정하고, `lane-start`부터 subagent 호출과 ACK까지 필요한 시간이 남았는지 확인한다. 부족하거나 기한이 지났으면 에이전트를 부르지 말고 lane을 취소한 뒤 새 시각으로 다시 시작한다. 독립 reviewer를 부르기 전에는 `lane-create`, `lane-start`를 `receipt`에 차례로 기록하고, 응답을 관찰한 뒤에만 `startup-ack`를 기록한다. `startup_ack`의 실제 시각은 `receipt`에서 확인하고, 기한을 지난 ACK는 제때 시작한 것으로 보고하지 않는다.
