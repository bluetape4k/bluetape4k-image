# Issue 172 계획 - Spring Boot OCR Example

명세:
`docs/superpowers/specs/2026-06-06-issue-172-spring-boot-ocr-example-design.md`

## 실행 범위

`examples/spring-boot-ocr-api` 아래에 non-published example module
`:spring-boot-ocr-api`를 추가한다. 이 module은 `images-ocr`의 Spring Boot 4 endpoint
wiring을 보여주며, 새 OCR backend나 production security/storage concern을 도입하지 않는다.

## 작업

| Task | Action | Files | DoD |
|---|---|---|---|
| T1 | 새 example module을 등록한다. | `settings.gradle.kts`, `AGENTS.md` | `./gradlew projects`가 `:spring-boot-ocr-api`를 표시하고 module table이 example을 언급한다. |
| T2 | Gradle build와 test resource를 추가한다. | `examples/spring-boot-ocr-api/build.gradle.kts`, `src/test/resources/*` | build가 `images`, `images-ocr`, Spring Boot web, coroutine reactor, Spring Boot test dependency를 사용하고 publication config는 없다. |
| T3 | Spring Boot OCR API를 구현한다. | `examples/spring-boot-ocr-api/src/main/kotlin/...` | `POST /api/ocr`가 multipart image를 받고, `languages`를 parse하며, optional `example.ocr.tessdata-path`를 읽고, `suspendExtractText`를 호출하며, text/languages/character count를 반환하고 validation은 400, OCR failure는 503으로 mapping한다. |
| T4 | deterministic integration test를 추가한다. | `examples/spring-boot-ocr-api/src/test/kotlin/...` | MockMvc test가 success, language parsing, unsupported content type, fake `OcrException`을 다루며 host Tesseract가 필요 없다. |
| T5 | bilingual example README를 추가한다. | `examples/spring-boot-ocr-api/README.md`, `README.ko.md` | 문서는 run command, request/response, `tesseract`/traineddata install, `example.ocr.tessdata-path`, test command를 설명한다. |
| T6 | 기존 generator로 README diagram을 추가한다. | `docs/scripts/generate-example-readme-diagrams.py`, generated assets under `docs/images/readme-diagrams/` | scenario, top-down layered architecture, sequence PNG/SVG와 Graphviz `.dot`, `.plain`, `-graphviz.svg`, `-graphviz.png`가 생성되고 rendered PNG를 시각 검토한다. |
| T7 | root docs와 Examples workflow를 갱신한다. | `README.md`, `README.ko.md`, `.github/workflows/Examples.yml` | root examples list가 새 module을 포함하고, Examples matrix가 `:spring-boot-ocr-api:test`를 실행하며, workflow path filter가 `images-ocr/**`를 포함하고, workflow YAML이 `actionlint`를 통과한다. |
| T8 | local verification을 실행한다. | Commands | `./gradlew projects`, `./gradlew :spring-boot-ocr-api:test`, diagram generator, XML/link checks, `actionlint`, `git diff --check`가 통과하거나 blocker를 기록한다. |
| T9 | Step 5/6/6-R gate를 실행한다. | `docs/review/*` | spec/plan requirement를 검증하고 final checklist를 통과하며, 7-tier code review artifact가 P0=0/P1=0을 보인다. |
| T10 | lesson, commit, PR, CI를 기록한다. | `docs/lessons/*`, PR body | PR 전에 lesson을 commit하고, PR body final section은 `## DoD Status`이며, live PR body를 검증하고 Step 7-R 및 CI gate를 완료한다. |

## 구현 세부 사항

- Package: `io.bluetape4k.images.examples.spring.ocr`.
- Application class: `SpringBootOcrApiApplication`.
- Controller: `OcrApiController`, base path `/api/ocr`.
- Service: `SpringBootOcrService`.
- Configuration:
  - `@ConfigurationProperties(prefix = "example.ocr")` data class `ExampleOcrProperties`.
  - `tessdataPath: String? = null`.
  - `OcrEngine` bean 기본값은 `TesseractOcrEngine()`.
- Response type은 `Serializable`을 구현하고 `serialVersionUID`를 정의한다.
- validation은 가능한 곳에서 bluetape4k helper를 사용한다.
  - `contentType.requireNotBlank("contentType")`.
  - language별 `requireNotBlank("language")`.
- Suspend/blocking boundary:
  - multipart byte read는 `withContext(Dispatchers.IO)`로 수행한다.
  - OCR call은 engine 실행을 이미 `Dispatchers.IO`로 감싸는 `ImmutableImage.suspendExtractText`를 사용한다.

## 테스트 세부 사항

- `@SpringBootTest`와 `@AutoConfigureMockMvc`를 사용한다.
- `@Primary` fake `OcrEngine`을 제공하는 `@TestConfiguration`을 추가한다.
- fake engine은 최신 `OcrOptions`를 `AtomicReference`에 저장해 test가 `languages`와 configured
  `tessdataPath`를 assert할 수 있게 한다.
- Success test:
  - generated PNG 또는 JPEG upload.
  - `languages=eng+kor` 전달.
  - JSON text, language list, character count assert.
- Bad request test:
  - `text/plain` upload.
  - HTTP 400과 `bad_request` assert.
- OCR failure test:
  - fake engine이 `OcrException`을 throw.
  - HTTP 503과 `ocr_unavailable` assert.

## Diagram 계획

- final asset을 손으로 작성하지 말고 `docs/scripts/generate-example-readme-diagrams.py`를 확장한다.
- 다음을 추가한다.
  - `examples-spring-boot-ocr-api-scenario-01`
  - `examples-spring-boot-ocr-api-architecture-01`
  - `examples-spring-boot-ocr-api-sequence-01`
- Architecture diagram은 top-down layered 구조여야 한다.
  client -> Spring Web -> application service -> `images-ocr` -> Tess4J / host Tesseract.
- source-derived label만 사용하고 모든 diagram label은 English로 유지한다.
- 생성 후 새 PNG를 각각 개별 inspect한다.

## 검증 명령

feature worktree에서 실행한다.

```bash
./gradlew projects
./gradlew :spring-boot-ocr-api:test
python3 docs/scripts/generate-example-readme-diagrams.py
find docs/images/readme-diagrams -name 'examples-spring-boot-ocr-api-*.svg' -print0 | xargs -0 -n1 xmllint --noout
find docs/images/readme-diagrams -name 'examples-spring-boot-ocr-api-*.svg' -exec sh -c 'test -f "${1%.svg}.png"' sh {} \;
actionlint .github/workflows/Examples.yml
git diff --check
```

당시 plan 문맥에서는 agent session에서 이 command를 실행할 때 `rtk` wrapper를 사용한다.

## Follow-Up 정책

구현 중 quickstart 범위를 넘는 production concern이 드러나면 이 PR 범위를 넓히지 말고 tracked
follow-up GitHub issue를 만든다. 가능성이 높은 follow-up은 다음과 같다.

- production OCR request hardening 또는 rate limiting.
- file-path 또는 batch OCR workflow.
- Ktor OCR example은 이미 issue #173이다.
