# 수동 문서 언어 쌍 검증 기록

## 목적

`docs/manual/en`과 `docs/manual/ko`는 이미 영어와 한국어 쌍으로 관리되는 수동 문서다.
이번 한국어 재작성 train의 primary scope는 단일 언어 문서와 코드 주석이므로, 이 수동 문서
쌍은 본문 재작성 대상에서 제외하고 구조 동등성만 검증한다.

## 검증 명령

```bash
ruby scripts/audit_localization_scope.rb
```

## 검증 결과

| 항목 | 값 |
|------|----|
| `docs/manual/en` 문서 수 | 41 |
| `docs/manual/ko` 문서 수 | 41 |
| 누락된 한국어 쌍 | 0 |
| 누락된 영어 쌍 | 0 |
| manual pair mismatch | 0 |

## 판정

수동 문서의 영어/한국어 basename parity는 맞다. 따라서 manual pair 본문은 이번 단일 언어
한국어 재작성 범위에서 제외하고, 이후 문서 train에서는 parity 검증 대상으로만 유지한다.

README, `AGENTS.md`, `CLAUDE.md`도 동일하게 이번 rewrite scope에서 제외한다. 이 경계는
GitHub issue/PR metadata와 LLM-facing operating docs를 영어로 유지해야 한다는 저장소 규칙을
보존하기 위한 것이다.
