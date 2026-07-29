# Step 2-R 설계 검토 — Issue 173 Ktor OCR 예제

범위:

- 설계: `docs/superpowers/specs/2026-06-06-issue-173-ktor-ocr-example-design.md`
- 이슈: #173 `feat: add Ktor OCR example`
- 대상 모듈: `examples/ktor-ocr-api`

읽은 참고 자료:

- `/Users/debop/.codex/skills/bluetape4k-full-feature/references/step-2r-spec-review.md`
- `/Users/debop/.codex/skills/bluetape4k-code-patterns/SKILL.md`
- `/Users/debop/.codex/skills/bluetape4k-diagram/SKILL.md`
- Ktor official docs via Context7: multipart `receiveMultipart()` / `PartData.FileItem.provider()` and `testApplication`
- Current repo evidence: `examples/ktor-image-api`, `images-ktor/ImageThumbnailKtorRoutes.kt`, `examples/spring-boot-ocr-api`, issue #173

## 관점별 발견 사항

| 관점 | P0 | P1 | P2 | P3 | 근거 |
|---|---:|---:|---:|---:|---|
| 개발자 | 0 | 0 | 0 | 0 | Module, route, DTO, fake-engine test, and byte-reading contracts map to existing Ktor and Spring OCR patterns. |
| 보안 | 0 | 0 | 0 | 0 | Spec rejects request-level `tessdataPath`, limits upload bytes, validates content type, and documents local-only auth/rate-limit boundary. |
| 운영/SRE | 0 | 0 | 0 | 0 | Native OCR runtime failure maps to 503; CI avoids native Tesseract by injected fake engine; `/ready` is scoped as a local quickstart readiness route. |
| 사용자/호출자 | 0 | 0 | 0 | 0 | README requirements include run/test commands, native installation, language examples, unsupported production concerns, and bilingual locale set. |

## 7계층 검토

| 계층 | P0 | P1 | P2 | P3 | 근거 |
|---|---:|---:|---:|---:|---|
| 1 보안 | 0 | 0 | 0 | 0 | Caller-controlled host paths are excluded; invalid multipart inputs are rejected as 400. |
| 2 운영/SRE 안정성 | 0 | 0 | 0 | 0 | OCR native failures are explicit 503 and real native smoke remains outside normal example CI. |
| 3 구조 영향 | 0 | 0 | 0 | 0 | New non-published example module only; no library public API or OCR backend change. |
| 4 Kotlin/API 품질 | 0 | 0 | 0 | 0 | Ktor 3 multipart and coroutine IO boundary align with `images-ktor` implementation patterns. |
| 5 테스트/타입/조용한 실패 | 0 | 0 | 0 | 0 | Acceptance requires success, languages, tessdata config propagation, unsupported content type, and OCR failure mapping tests. |
| 6 성능/안정성 | 0 | 0 | 0 | 0 | Upload byte limit, part release, and fake native dependency in CI are required. |
| 7 문서/릴리스/근거 | 0 | 0 | 0 | 0 | README locale set, root README, Examples workflow matrix, diagrams, PR body final DoD section all required. |

## Integration

이 설계는 내부적으로 일관되며 의도한 예제 범위 안에서 issue를 구현한다. 더 큰 reusable Ktor OCR route helper는 public API 확장으로 명시적으로 기각했으므로, 구현 중 실제 중복이나 user-facing need가 드러나지 않는 한 후속 issue는 필요하지 않다.

Consolidated counts: P0 = 0, P1 = 0, P2 = 0, P3 = 0.

게이트 판정: PASS.
