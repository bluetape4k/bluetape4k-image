# Step 2-R 설계 검토 — Issue 172 Spring Boot OCR 예제

Reviewed spec:
`docs/superpowers/specs/2026-06-06-issue-172-spring-boot-ocr-example-design.md`

Reference loaded:
`/Users/debop/.codex/skills/bluetape4k-full-feature/references/step-2r-spec-review.md`

## Multi-Perspective Findings

| 관점 | P0 | P1 | P2 | P3 | 참고 |
|---|---:|---:|---:|---:|---|
| Kotlin 개발자 | 0 | 0 | 0 | 0 | Endpoint/service/test shape follows existing Spring Boot example and `images-ocr` contracts. |
| 보안 | 0 | 0 | 0 | 0 | Initial P1 for caller-controlled `tessdataPath` was fixed by moving it to app configuration. |
| 운영/SRE | 0 | 0 | 0 | 0 | Native OCR failure is separated as 503 and real Tesseract requirements are documented. |
| 사용자/호출자 | 0 | 0 | 0 | 0 | Upload API, language parsing, native requirements, and non-goals are explicit. |

## 로컬 7계층 검토

| 계층 | P0 | P1 | P2 | P3 | 근거 |
|---|---:|---:|---:|---:|---|
| 1 보안 | 0 | 0 | 0 | 0 | file-path endpoint는 없고 caller-controlled tessdata path도 없다. multipart validation이 필요하다. |
| 2 운영/SRE | 0 | 0 | 0 | 0 | 400 vs 503 failure semantics defined; no production lifecycle implied. |
| 3 구조 영향 | 0 | 0 | 0 | 0 | New example module keeps existing examples isolated and uses current registration pattern. |
| 4 Kotlin/API 품질 | 0 | 0 | 0 | 0 | Uses `OcrEngine`, `OcrOptions`, `suspendExtractText`, constructor injection, and fake engine tests. |
| 5 테스트/타입/조용한 실패 | 0 | 0 | 0 | 0 | MockMvc test scope covers success, validation failure, and OCR failure without native OCR. |
| 6 성능/안정성 | 0 | 0 | 0 | 0 | Blocking multipart byte read and OCR call are specified behind coroutine dispatcher boundaries. |
| 7 문서/릴리스/근거 | 0 | 0 | 0 | 0 | README locale set, diagrams, root docs, AGENTS, Gradle, and Examples workflow are acceptance criteria. |

## 수렴 결과

| 발견 사항 | Severity | 해결 |
|---|---|---|
| Request-level `tessdataPath` would teach caller-controlled host path configuration. | P1 | Replaced with `example.ocr.tessdata-path` application property and explicit no request path rule. |

최종 통합 건수: P0 = 0, P1 = 0, P2 = 0, P3 = 0.

게이트 판정: PASS.
