# 교훈 — Nightly 전체 예약 실행 조건 (2026-06-04)

**관련 이슈**: #146

## 배경

Central snapshot 메타데이터 경합을 줄이기 위해 Nightly cron의 실행 분을 분산했다.
전체 범위 예약 작업은 여전히 `github.event.schedule`을 이전 일요일 cron 문자열과
비교했으므로 주간 전체 작업이 건너뛰어질 수 있었다.

## 결정

분산한 cron은 유지하고 전체 범위 작업 조건이 저장소의 현재 일요일 일정을 비교하도록
갱신한다.

## 검증

- `actionlint .github/workflows/nightly-tests.yml`
- `git diff --check`
- 예약 조건 점검: 이전 `0 19 * * 0` 전체 작업 조건이 남아 있지 않다.

## 이후 규칙

예약 cron 문자열을 바꿀 때는 같은 워크플로의 모든 `github.event.schedule` 비교문을
함께 갱신한다.
