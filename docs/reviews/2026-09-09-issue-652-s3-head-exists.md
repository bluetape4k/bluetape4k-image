# #652 S3 `exists` HEAD 경로 검토

## 범위와 판정

`S3ImageStorage.exists`, `S3Operations.headObject` 재사용 경계, 관련 MockK
회귀 테스트와 기존 S3 호출자를 검토한다.

| 역할 | 설치된 모델·추론 수준 | 판정 | P0/P1/P2 |
|---|---|---|---|
| code-reviewer | gpt-5.6-luna / max | CLEAR | 0/0/0 |
| architect | gpt-5.6-sol / high | CLEAR | 0/0/0 |

독립 review lane은 모두 `CLEAR`를 반환했고 P0/P1/P2를 발견하지 않았다. 현재
구현 근거는 다음과 같다.

- 수정 전 23개 targeted 테스트에서 기존 `listPage` 경로의 6개 RED를 확인했다.
- `exists`는 기존 `headObject(key)`만 호출하고, 정규화된 NotFound만 `false`로
  바꾼다. exact key·유사 prefix·LIST 미호출을 MockK로 고정했다.
- 403은 `AccessDeniedException`, 기타 오류는 `TransientException`,
  `CancellationException`은 그대로 전파한다.
- targeted 23 pass, 모듈 222 pass, ABI 10개 모듈 통과, 변경 테스트 detekt
  통과를 확인했다.
- 계측 decorator도 위임된 `headObject` 호출을 기록하므로 별도 LIST metric이나
  resource lifecycle 변경이 없다.

## 잔여 위험

S3 provider가 권한 정책상 HEAD도 차단하면 `exists`는 false로 완화하지 않고
접근 오류를 전파한다. 이는 “존재하지 않음”과 “확인 권한 없음”을 구분하는
fail-closed 정책이다. 실제 AWS IAM 계정 실험은 수행하지 않았으며 MockK 계약과
기존 `S3Operations` 정규화 경로를 근거로 삼는다.

## DoD Status

- [x] 정확한 key HEAD 사용 및 LIST 미호출
- [x] NoSuchKey/404만 false 처리
- [x] 403·전송 오류·취소 전파와 유사 prefix 경계 테스트
- [x] 모듈·ABI·정적분석 검증
- [x] 독립 code-reviewer 및 architect 판정 통합
- [ ] exact-head 원격 CI 확인
- [ ] 전체 PR 준비 후 새 머지 승인
