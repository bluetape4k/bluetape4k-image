# #649 Builder 크기 목록 스냅샷 검토

## 독립 검토와 판정

`ThumbnailPipeline.Builder.build()`, 새 회귀 테스트, KDoc와 한영 README를 검토했다.
두 검토자는 읽기 전용으로 소스·diff·실행 로그를 확인했고 테스트를 중복 실행하지 않았다.

| 역할 | 설치된 모델·추론 수준 | 판정 | P0/P1 |
|---|---|---|---|
| code-reviewer | gpt-5.6-luna / max | APPROVE | 0 |
| architect | gpt-5.6-sol / high | CLEAR | 0 |

## 원인과 검증

`ifEmpty`는 non-empty mutable list를 복사하지 않는다.
build 시 `toList()`로 복사하면 이후 Builder의 size 추가가 기존 pipeline과
미수집 Flow에 전파되지 않는다. 요소 `ThumbnailSize`는 읽기 전용 값이므로
이 크기 목록 계약에는 얕은 복사로 충분하다.

- 수정 전 실제 출력 크기 목록 `[5, 6, 7]`이 기대 `[5, 6]`과 달라 회귀가 실패했다.
- 수정 후 thumbnail 32개, 전체 images 710개 및 production ABI 10개 모듈이 통과했다.
- 기존 건너뜀 18개는 작은 입력 속성 테스트 10개·비활성 골든 생성기 8개로, 통과 수에서 제외했다.
- 기본 320×240 크기, 새 pipeline의 세 크기, 출력 디렉터리와 PNG 실제 크기도 검증했다.
- 변경 소스·새 테스트 detekt는 부모 소스와 동일한 경고 4건으로 실패했다.
  신규 경고는 없으며 전체 clean으로 판정하지 않는다.

## 범위와 잔여 한계

순차 Builder 재사용을 지원하며 Builder 자체의 동시 수정은 지원하지 않는다.
사용자가 주입한 writer·callback 내부 상태까지 복사하는 전체 deep immutability는 보장하지 않는다.
공개 API/ABI는 유지하며, 기존 pipeline이 나중의 size 추가를 관측하던 동작만 수정했다.
#648에서 기록한 기존 Flow 재수집 수명 문제는 이번 수정과 별개다.

## DoD Status

- [x] 실제 process RED/GREEN·기본값·파일 출력·ABI 검증
- [x] 독립 코드 리뷰 APPROVE / 아키텍처 CLEAR
- [x] 한국어 문서와 한영 계약 동등성 확인
- [ ] exact-head 원격 CI 확인
- [ ] 전체 PR 준비 후 새 머지 승인
