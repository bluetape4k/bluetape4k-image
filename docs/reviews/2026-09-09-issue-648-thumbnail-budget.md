# #648 썸네일 픽셀 예산 검토

## 범위와 판정

`ThumbnailPipeline`, 새 `ThumbnailBudgetTest`, 한영 README를 검토했다.
독립 검토자는 소스·호출자·주 실행자가 수집한 로그를 읽었으며 테스트를 중복 실행하지 않았다.

| 역할 | 설치된 모델·추론 수준 | 판정 | 신규 P0/P1 |
|---|---|---|---|
| code-reviewer | gpt-5.6-luna / max | COMMENT | 0 |
| architect | gpt-5.6-sol / high | CLEAR | 0 |

입력과 출력 각각의 한도 검사, 안전한 합계 계산, permit 획득 전 합계 거부,
decode 후 입력 예약량 검증을 확인했다. 기존 permit 반환·취소 전파·원자적 쓰기를
재사용하며 새 의존성이나 공개 시그니처 변경은 없다.

## 검증 근거

- 작은 입력·큰 출력 등 세 회귀는 수정 전 행동 실패를 재현했다.
- 입력만 예약하는 mutation은 동시 writer 2개 진입을 재현했다.
- thumbnail 및 limiter 대상 검증 38개가 통과했다.
- 실제 job 취소와 write 실패 후 다음 크기 처리, 출력 파일과 callback 결과를 확인했다.
- 변경 소스와 새 테스트를 지정한 detekt는 기존 경고 4건으로 실패했다.
  동일 설정의 기준 blob은 5건이며 신규 경고는 없다. 전체 clean으로 판정하지 않는다.

전체 images 708개 통과, 18개 기존 건너뜀, 실패 0건과 production ABI 10개 모듈 통과를 확인했다.
건너뜀은 작은 입력 속성 테스트 10개와 비활성 골든 이미지 생성기 8개이며 통과에 포함하지 않는다.

## 기존 위험의 처리

- P2 Builder의 mutable sizes 별칭: 승인된 후속 #649에서 별도 수정한다.
- P2 Flow 수명: `process()` 호출 때 생성한 `seenOutputs`가 재수집에도 남고,
  별도 호출은 서로 다른 limiter를 사용한다. 기존 코드의 별도 위험으로 남긴다.
- scratch 메모리, 소비자가 보관하는 결과, 파일 변경과 decode 사이 할당은
  픽셀 예약만으로 통제하지 못한다. README와 KDoc에 보장 범위를 한정했다.

## DoD Status

- [x] 결함·RED/GREEN·실제 취소·permit 반환 근거
- [x] 독립 검토 P0/P1=0, 기존 P2 분리
- [x] 한국어 KDoc·한영 README·교훈
- [ ] 정확한 PR head CI와 최신 리뷰 확인
- [ ] 전체 PR 준비 후 새 머지 승인
