# 한국어 현지화 범위

## 목적

이 문서는 GitHub Epic #302와 child issue #303의 실행 범위를 고정한다. 목표는 README 문서,
LLM-facing 운영 문서, 이미 bilingual pair로 관리되는 manual 본문을 건드리지 않고 단일 언어 문서와
Kotlin/Java 주석을 한국어로 정리하는 것이다.

## 범위 포함

- `CHANGELOG.md`, `WIP.md`처럼 README가 아닌 루트 문서.
- `docs/blog`, `docs/governance`, `docs/lessons`, `docs/review`,
  `docs/superpowers`, `docs/manual/templates` 아래의 단일 언어 Markdown 문서.
- `benchmark/images-benchmark/docs` 아래의 benchmark 결과 문서.
- Kotlin/Java source, test, fixture, benchmark code에 있는 KDoc, block comment, line comment.
- public API 또는 비자명한 내부 계약을 설명하는 KDoc의 `@property`, `@param`, `@return` 설명.

## 범위 제외

- 모든 `README.md`와 `README.ko.md`.
- `AGENTS.md`, `CLAUDE.md` 및 agent/LLM-facing 운영 문서.
- `docs/manual/en`과 `docs/manual/ko`의 primary rewrite. 이 경로는 basename parity와 구조 drift만 검증한다.
- dependency, Gradle topology, public behavior, release, tag, publish action.

## 보존 규칙

- 코드 식별자, Gradle task, CLI command, API name, URL, issue/PR number, exact error text는 원문을 유지한다.
- GitHub issue/PR 제목과 본문은 repository policy에 따라 English로 작성한다.
- 사용자-facing 계획, 검토, lesson, research prose는 한국어로 쓴다.
- README와 bilingual manual의 실제 문체 개선은 별도 issue가 명시적으로 열릴 때만 수행한다.

## Stacked PR Train

| Train | Issue | 대상 |
|---|---:|---|
| 0 | #303 | inventory, 제외 범위 guard, 반복 가능한 audit |
| 1 | #304-#311 | 단일 언어 문서 현지화와 manual parity proof |
| 2 | #312-#314 | core, barcode, captcha, Ktor, OCR KDoc/comment 현지화 |
| 3 | #315-#318 | Spring Boot, libvips, example, benchmark code comment |
| 4 | #319 | final audit, issue reconciliation, Epic DoD |

## Audit 명령

각 train 전후에 다음 명령을 실행한다.

```bash
ruby scripts/audit_localization_scope.rb
```

audit는 다음 항목을 보고한다.

- 제외된 README와 운영 문서.
- 제외된 bilingual manual pair 수와 누락된 basename pair.
- 범위에 포함된 단일 언어 문서 group.
- comment marker가 남아 있는 Kotlin/Java file.

이 명령은 inventory guard이며 자연어 classifier가 아니다. 각 child issue에서는 수정한 prose에 대한 사람 기준의
검토가 별도로 필요하다.
