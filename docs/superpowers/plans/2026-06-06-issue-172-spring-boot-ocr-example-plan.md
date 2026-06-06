# Issue 172 Plan — Spring Boot OCR Example

Spec:
`docs/superpowers/specs/2026-06-06-issue-172-spring-boot-ocr-example-design.md`

## Execution Scope

Add a non-published example module `:spring-boot-ocr-api` under
`examples/spring-boot-ocr-api`. The module demonstrates Spring Boot 4 endpoint
wiring for `images-ocr`; it does not introduce a new OCR backend or production
security/storage concerns.

## Tasks

| Task | Action | Files | DoD |
|---|---|---|---|
| T1 | Register the new example module. | `settings.gradle.kts`, `AGENTS.md` | `./gradlew projects` lists `:spring-boot-ocr-api`; module table mentions the example. |
| T2 | Add Gradle build and test resources. | `examples/spring-boot-ocr-api/build.gradle.kts`, `src/test/resources/*` | Build uses `images`, `images-ocr`, Spring Boot web, coroutine reactor, and Spring Boot test dependencies; no publication config. |
| T3 | Implement Spring Boot OCR API. | `examples/spring-boot-ocr-api/src/main/kotlin/...` | `POST /api/ocr` accepts multipart image, parses `languages`, reads optional `example.ocr.tessdata-path`, calls `suspendExtractText`, returns text/languages/character count, maps validation to 400 and OCR failures to 503. |
| T4 | Add deterministic integration tests. | `examples/spring-boot-ocr-api/src/test/kotlin/...` | MockMvc tests cover success, language parsing, unsupported content type, and fake `OcrException`; no host Tesseract required. |
| T5 | Add bilingual example README. | `examples/spring-boot-ocr-api/README.md`, `README.ko.md` | Docs explain run commands, request/response, `tesseract`/traineddata install, `example.ocr.tessdata-path`, and test command. |
| T6 | Add README diagrams through existing generator. | `docs/scripts/generate-example-readme-diagrams.py`, generated assets under `docs/images/readme-diagrams/` | Scenario, top-down layered architecture, and sequence PNG/SVG plus Graphviz `.dot`, `.plain`, `-graphviz.svg`, `-graphviz.png` are generated; rendered PNGs are visually inspected. |
| T7 | Update root docs and Examples workflow. | `README.md`, `README.ko.md`, `.github/workflows/Examples.yml` | Root examples list includes the new module; Examples matrix runs `:spring-boot-ocr-api:test`; workflow path filters include `images-ocr/**`; workflow YAML passes `actionlint`. |
| T8 | Run local verification. | Commands | `./gradlew projects`, `./gradlew :spring-boot-ocr-api:test`, diagram generator, XML/link checks, `actionlint`, and `git diff --check` pass or have recorded blockers. |
| T9 | Run Step 5/6/6-R gates. | `docs/review/*` | Spec/plan requirements verified, final checklist passed, 7-tier code review artifact shows P0=0/P1=0. |
| T10 | Capture lessons, commit, PR, and CI. | `docs/lessons/*`, PR body | Lessons committed before PR; PR body final section is `## DoD Status`; live PR body verified; Step 7-R and CI gate completed. |

## Implementation Details

- Package: `io.bluetape4k.images.examples.spring.ocr`.
- Application class: `SpringBootOcrApiApplication`.
- Controller: `OcrApiController`, base path `/api/ocr`.
- Service: `SpringBootOcrService`.
- Configuration:
  - `@ConfigurationProperties(prefix = "example.ocr")` data class
    `ExampleOcrProperties`.
  - `tessdataPath: String? = null`.
  - `OcrEngine` bean defaults to `TesseractOcrEngine()`.
- Response types implement `Serializable` and define `serialVersionUID`.
- Validation uses bluetape4k helpers where available:
  - `contentType.requireNotBlank("contentType")`.
  - per-language `requireNotBlank("language")`.
- Suspend/blocking boundaries:
  - multipart bytes read with `withContext(Dispatchers.IO)`.
  - OCR call uses `ImmutableImage.suspendExtractText`, which already wraps
    engine execution on `Dispatchers.IO`.

## Test Details

- Use `@SpringBootTest` and `@AutoConfigureMockMvc`.
- Add `@TestConfiguration` with `@Primary` fake `OcrEngine`.
- The fake engine stores the latest `OcrOptions` in an `AtomicReference` so
  tests can assert `languages` and configured `tessdataPath`.
- Success test:
  - upload generated PNG or JPEG;
  - pass `languages=eng+kor`;
  - assert JSON text, language list, and character count.
- Bad request test:
  - upload `text/plain`;
  - assert HTTP 400 and `bad_request`.
- OCR failure test:
  - fake engine throws `OcrException`;
  - assert HTTP 503 and `ocr_unavailable`.

## Diagram Plan

- Extend `docs/scripts/generate-example-readme-diagrams.py` instead of hand
  authoring final assets.
- Add:
  - `examples-spring-boot-ocr-api-scenario-01`
  - `examples-spring-boot-ocr-api-architecture-01`
  - `examples-spring-boot-ocr-api-sequence-01`
- Architecture diagram must be top-down layered:
  client -> Spring Web -> application service -> `images-ocr` -> Tess4J /
  host Tesseract.
- Use source-derived labels only and keep all diagram labels in English.
- After generation, inspect each new PNG individually.

## Verification Commands

Run from the feature worktree:

```bash
./gradlew projects
./gradlew :spring-boot-ocr-api:test
python3 docs/scripts/generate-example-readme-diagrams.py
find docs/images/readme-diagrams -name 'examples-spring-boot-ocr-api-*.svg' -print0 | xargs -0 -n1 xmllint --noout
find docs/images/readme-diagrams -name 'examples-spring-boot-ocr-api-*.svg' -exec sh -c 'test -f "${1%.svg}.png"' sh {} \;
actionlint .github/workflows/Examples.yml
git diff --check
```

Use `rtk` wrapper when executing these commands in the agent session.

## Follow-Up Policy

If implementation reveals production concerns beyond quickstart scope, create
tracked follow-up GitHub issues instead of expanding this PR. Likely follow-ups:

- production OCR request hardening or rate limiting;
- file-path or batch OCR workflow;
- Ktor OCR example is already issue #173.
