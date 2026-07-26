# Issue #299 통합 이미지 인텔리전스 API 설계 검토

**Artifact kind**: spec
**Reviewed spec**:
`docs/superpowers/specs/2026-07-27-issue-299-image-intelligence-api-design.md`
**Research basis**: issue #299, transferred workshop issue #578, current
`develop`, existing Spring Boot OCR·바코드 examples, image detection contracts,
and `bluetape4k-workflow` source
**Review method**: 현재 작업의 inline 실행 원칙에 따라 main session에서 여섯 관점을
서로 섞지 않은 순차 pass로 검토하고 마지막에 통합했다.

## 발견 사항과 수정

| Priority | Lens | Evidence | Required edit | Result |
|---|---|---|---|---|
| P1 | Performance | 초기 `QualifiedImage`가 압축 `ByteArray`와 디코딩 이미지를 분석 종료까지 함께 보관했다. | 검증·디코딩 뒤 원본 바이트 참조를 해제하고 `QualifiedImage`에는 이미지와 정제된 metadata만 둔다. | 수정 완료 |
| P1 | Security | 초기 입력 순서가 전체 디코딩 뒤 pixel budget을 검사해 큰 압축 이미지가 먼저 메모리를 사용할 수 있었다. | 전체 디코딩 전에 dimension probe와 pixel budget을 검사하도록 순서를 고정한다. | 수정 완료 |
| P1 | Stability | 초기 timeout 설명이 interruption에 반응하지 않는 in-process native 호출도 강제 종료할 수 있는 것처럼 읽혔다. | cooperative adapter만 timeout 보장 대상으로 한정하고 `runInterruptible`, native 한계, 운영 process 격리를 명시한다. | 수정 완료 |
| P2 | User/caller | 분석 전체 실패도 HTTP 200을 반환하는 이유가 transport 성공과 업무 성공의 차이를 충분히 설명하지 않았다. | 호출자가 전체 상태와 정책 결정을 반드시 확인해야 한다는 문장을 추가한다. | 수정 완료 |
| P2 | Developer/API | 응답 예시가 빈 감지 목록을 `COMPLETED`로 표시해 `Empty` 계약과 충돌했다. | 감지 예시 상태를 `EMPTY`로 바꾼다. | 수정 완료 |

## 최종 관점별 판정

| Lens | P0 | P1 | P2 | P3 | 최종 근거 |
|---|---:|---:|---:|---:|---|
| Performance | 0 | 0 | 0 | 0 | 단일 디코딩, 조기 dimension 검사, 원본 바이트 해제, 공급자별 동시 실행 제한을 명시했다. |
| Stability | 0 | 0 | 0 | 0 | 부분 실패, 외부 취소, 내부 timeout, 비협조 native 호출의 한계와 격리 대안을 구분했다. |
| Security | 0 | 0 | 0 | 0 | MIME·magic byte·byte·dimension·pixel 경계를 분석 전에 적용하고 민감 원문 로그를 금지했다. |
| Operator/Ops | 0 | 0 | 0 | 0 | 구조화 로그와 request context를 정의하고 health, retry, circuit breaker가 운영 adapter의 별도 범위임을 명시했다. |
| Developer/API | 0 | 0 | 0 | 0 | local project dependency, 관리된 workflow alias, 상태 두 축, 전용 HTTP DTO, 비배포 예제 경계가 일치한다. |
| User/caller | 0 | 0 | 0 | 0 | 방문증 시나리오, 200 응답의 의미, 정책 교체 지점, 비운영·비인증 한계를 명시했다. |

## 통합 검토

- 설계의 목표, 아키텍처, 오류 처리, 테스트와 수용 기준 사이에 남은 모순이 없다.
- `TBD`, `TODO`, 이전 workshop module path, 구현 준비 완료를 선행 주장하는 문구가 없다.
- 새 예제는 한 implementation plan으로 나눌 수 있는 범위이며, 기존 단일 기능 예제를
  변경하지 않는다.
- `docs/manual` 제외는 안정 release tag 기반 manual 소유권 규칙과 일치한다.
- `settings.gradle.kts`, `AGENTS.md`, root README locale set,
  `.github/workflows/Examples.yml`을 registration chain으로 명시했다.
- 최종 판정은 `P0=0`, `P1=0`이다.
