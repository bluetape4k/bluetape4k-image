# Step 5 검증 — Issue 173 Ktor OCR 예제

범위:

- 이슈: #173 `feat: add Ktor OCR example`
- 모듈: `examples/ktor-ocr-api`
- 브랜치: `feat/issue-173-ktor-ocr-example`

## 구현 DoD

| DoD | 상태 | 근거 |
|---|---|---|
| 새 Ktor OCR 예제 모듈이 존재한다. | PASS | `examples/ktor-ocr-api` added and registered in `settings.gradle.kts`. |
| 최소 OCR route가 존재한다. | PASS | `GET /ready` and `POST /api/ocr` implemented in `KtorOcrApiApplication.kt`. |
| 기존 `images-ocr` API를 재사용한다. | PASS | Route calls `ImmutableImage.suspendExtractText` with injected `OcrEngine`; no new backend was added. |
| native runtime path는 request config가 아니라 host config다. | PASS | `EXAMPLE_OCR_TESSDATA_PATH` maps to `OcrOptions.tessdataPath`; request-level tessdata path is not accepted. |
| Ktor route test는 host Tesseract를 피한다. | PASS | fake `OcrEngine`을 주입한다. |
| Multipart validation은 field, type, OCR failure mapping을 다룬다. | PASS | expected field, unsupported content type, `OcrException` -> 503을 테스트한다. |
| README locale set은 setup과 run command를 문서화한다. | PASS | `examples/ktor-ocr-api/README.md` and `README.ko.md` added. |
| 다이어그램은 repo script로 생성된다. | PASS | Scenario, top-down layered architecture, and sequence assets were generated. |

## 대상 테스트 근거

Command:

```bash
./gradlew :ktor-ocr-api:test --no-configuration-cache --no-daemon
```

결과:

- PASS
- 5 tests passing:
  - `ready endpoint responds with plain text`
  - `recognizes uploaded image with parsed languages`
  - `rejects request without expected file field`
  - `rejects unsupported content type`
  - `maps OCR failures to service unavailable`

## 검증 중 수정 사항

| 발견 사항 | 수정 | 근거 |
|---|---|---|
| Ktor response serialization returned 500 for `OcrTextResponse` because the `@Serializable` DTO had a private companion object. | Made the companion object serializer-accessible while keeping `serialVersionUID` private. | Happy path route test now returns 200 and deserializes `OcrTextResponse`. |
| `languages=eng+kor` is decoded by HTTP query parsing as `eng kor`. | Language parser now accepts comma, plus, and whitespace separators. | Test request `languages=eng+kor` resolves to `["eng", "kor"]`. |

## Step 6 점검

| 점검 | 상태 | 근거 |
|---|---|---|
| `./gradlew :ktor-ocr-api:test --no-configuration-cache --no-daemon` | PASS | 5 route tests; rerun after CI dependency repair. |
| `./gradlew :ktor-ocr-api:dependencies --configuration compileClasspath --no-configuration-cache --no-daemon` | PASS | New module no longer directly depends on `bluetape4k-ktor-core`. |
| `./gradlew projects --no-configuration-cache --no-daemon` | PASS | `:ktor-ocr-api` is listed under examples. |
| `python3 docs/scripts/generate-example-readme-diagrams.py` | PASS | New scenario, architecture, and sequence families report `manual_exceptions=0`. |
| `xmllint --noout` for generated `examples-ktor-ocr-api-*.svg` | PASS | XML error 없음. |
| README SVG-reference guard | PASS | README가 생성된 SVG asset을 참조하지 않는다. |
| `actionlint .github/workflows/Examples.yml` | PASS | workflow lint error 없음. |
| Workflow escaped single-quote guard | PASS | fixed-string `\'` match 없음. |
| `git diff --check` | PASS | whitespace error 없음. |
| visual inspection of generated PNG assets | PASS | Scenario, architecture, and sequence text fit; architecture is top-down layered. |
| Step 6-R code review | PASS | `docs/review/2026-06-06-issue-173-ktor-ocr-example-code-review.md`가 P0=0/P1=0을 기록한다. |
