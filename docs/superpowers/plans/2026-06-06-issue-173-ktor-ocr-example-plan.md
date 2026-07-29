# Issue 173 Ktor OCR Example 계획

## 범위

Issue #173을 새 non-published example module로 구현한다.

- `examples/ktor-ocr-api`
- root README locale update
- example README locale set
- generated README diagram
- Gradle/settings/AGENTS registration
- Examples workflow matrix coverage
- review, verification, lessons, PR artifact

## 작업 계획

| Task | Description | DoD |
|---|---|---|
| T1 | Gradle module registration과 build file을 추가한다. | `settings.gradle.kts`가 `ktor-ocr-api`를 include하고 `./gradlew projects`가 이를 표시한다. |
| T2 | Ktor OCR app을 구현한다. | `GET /ready`; `POST /api/ocr`; injectable `OcrEngine`; tessdata path env config; multipart validation; 400/503 mapping. |
| T3 | Ktor route test를 추가한다. | `testApplication` test가 native Tesseract 없이 통과하고, fake engine이 languages와 tessdata path를 검증하며, invalid multipart case가 400을 반환한다. |
| T4 | README locale set을 추가한다. | `README.md`와 `README.ko.md`가 purpose, diagrams, native install, run, curl, test, local-only limit를 설명한다. |
| T5 | root docs와 repo guidance를 갱신한다. | Root README/README.ko examples가 Ktor OCR을 언급하고 repo `AGENTS.md` module list가 새 example을 포함한다. |
| T6 | diagram을 추가한다. | generator가 scenario, top-down layered architecture, sequence asset을 PNG/SVG/DOT/plain/Graphviz evidence와 함께 생성한다. |
| T7 | Examples workflow를 갱신한다. | matrix가 `:ktor-ocr-api:test`를 포함하고, path trigger는 이미 `examples/**`와 `images-ocr/**`를 포함하며, `actionlint`가 통과한다. |
| T8 | verification과 review를 실행한다. | target tests, projects, diagram generation/XML/visual checks, workflow checks, `git diff --check`, Step 6-R P0=0/P1=0. |
| T9 | lesson, commit, PR을 기록한다. | PR 전에 lesson을 commit하고, PR body는 `## DoD Status`로 끝나며, post-PR review와 CI gate가 PR body를 갱신한다. |

## 구현 세부 사항

### Module

- Directory: `examples/ktor-ocr-api`
- Package: `io.bluetape4k.images.examples.ktor.ocr`
- Main class: `KtorOcrApiApplicationKt`
- Dependencies:
  - `project(":bluetape4k-images")`
  - `project(":bluetape4k-images-ocr")`
  - `libs.ktor.server.content.negotiation`
  - `libs.ktor.server.core`
  - `libs.ktor.server.netty`
  - `libs.ktor.serialization.kotlinx.json`
  - `runtimeOnly(libs.logback)`
  - test는 `bluetape4k-junit5`, Ktor client content negotiation, `ktor-server-test-host` 사용

### Route

- `fun Application.configureKtorOcrApi(...)`
- 기본값:
  - engine: `TesseractOcrEngine()`
  - tessdata path: `System.getenv("EXAMPLE_OCR_TESSDATA_PATH")`
  - max input bytes: 10 MiB
- `POST /api/ocr`:
  - multipart field `file`을 읽는다.
  - `PartData.FileItem`과 `PartData.BinaryChannelItem`을 지원한다.
  - 각 part를 `finally`에서 release한다.
  - non-empty upload와 content type allowlist를 검증한다.
  - `languages`를 `[,+\s]+`로 parse한다.
  - `immutableImageOf(uploadBytes).suspendExtractText(OcrOptions(...), engine)`을 호출한다.
  - `OcrTextResponse`로 응답한다.
- Errors:
  - `IllegalArgumentException` and `IOException` -> `400 bad_request`
  - `OcrException` -> `503 ocr_unavailable`

### Tests

- `ready endpoint responds with plain text`
- `recognizes uploaded image with parsed languages`
- `rejects request without expected file field`
- `rejects unsupported content type`
- `maps OCR failures to service unavailable`

`configureKtorOcrApi(...)`를 통해 주입한 class-level fake `OcrEngine`을 사용한다.

### Diagrams

`docs/scripts/generate-example-readme-diagrams.py`를 통해 asset 3개를 추가한다.

- `examples-ktor-ocr-api-scenario-01`
- `examples-ktor-ocr-api-architecture-01`
- `examples-ktor-ocr-api-sequence-01`

Architecture는 top-down layered 구조여야 한다.

1. Client Layer
2. Ktor Routing Layer
3. Application Layer
4. OCR Library Layer
5. Native Runtime Layer

## 검증 명령

다음 순서로 실행한다.

```bash
./gradlew :ktor-ocr-api:test --no-configuration-cache --no-daemon
./gradlew projects --no-configuration-cache --no-daemon
python3 docs/scripts/generate-example-readme-diagrams.py
find docs/images/readme-diagrams -name 'examples-ktor-ocr-api-*.svg' -print0 | xargs -0 -n1 xmllint --noout
actionlint .github/workflows/Examples.yml
rg -n "\\\\'" .github/workflows
git diff --check
```

시각 검토:

- 새 PNG 각각을 열어 text fit, layer containment, connector routing, sequence label placement,
  PNG embed policy, top-down architecture shape를 확인한다.

## Follow-Up Issue 정책

구현 결과 reusable `images-ktor` OCR route helper, production auth/rate-limit policy, batch
OCR, persistence, native Ktor OCR smoke gate가 필요하다고 증명될 때만 follow-up issue를 만든다.
#173 밖의 rejected option에 대해 speculative follow-up을 만들지 않는다.
