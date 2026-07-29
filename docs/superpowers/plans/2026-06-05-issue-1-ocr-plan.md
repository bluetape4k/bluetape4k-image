# Issue #1 OCR 구현 계획

- 이슈: [#1](https://github.com/bluetape4k/bluetape4k-image/issues/1)
- 명세:
  `docs/superpowers/specs/2026-06-05-issue-1-ocr-design.md`
- 조사:
  `docs/superpowers/research/2026-06-05-issue-1-ocr-research-refresh.md`
- 명세 리뷰:
  `docs/review/2026-06-05-issue-1-ocr-spec-review.md`
- 후속:
  [#169](https://github.com/bluetape4k/bluetape4k-image/issues/169)

## 실행 규칙

- 모든 source 변경은 feature worktree 안에 유지한다.
- Step 3-R이 통과하고 spec/plan artifact가 commit되기 전에는 Step 4 구현을 시작하지 않는다.
- `bluetape4k-images`에는 Tess4J/Tesseract dependency를 추가하지 않는다.
- 당시 계획 기준으로 public KDoc, README English text, GitHub artifact, commit message는
  English로 유지한다.
- user-facing Korean README와 최종 chat report는 Korean으로 작성한다.
- Step 4 구현과 Step 6-R code review 전에 `$bluetape4k-code-patterns`를 load/apply하고,
  관련 check를 Step DoD report에 기록한다.
- root README visual asset이 바뀌면 `$bluetape4k-diagram` gate를 사용한다.
- PaddleOCR 또는 더 복잡한 OCR runtime이 필요해지면 #1 범위를 넓히지 말고 멈춘 뒤
  follow-up issue #169를 사용한다.

## 작업 계획

| Task | Scope | Files |
|---|---|---|
| T1 Module registration | OCR published module을 Gradle settings와 version catalog에 추가 | `settings.gradle.kts`, `gradle/libs.versions.toml`, `images-ocr/build.gradle.kts` |
| T2 API models | OCR options/result/enums/exceptions를 English KDoc과 serializable data class로 추가 | `images-ocr/src/main/kotlin/io/bluetape4k/images/ocr/*.kt` |
| T3 Engine and extensions | Tess4J-backed engine과 `ImmutableImage` sync/suspend extension 구현 | `images-ocr/src/main/kotlin/io/bluetape4k/images/ocr/*.kt` |
| T4 Unit tests | native OCR 없이 통과하는 fake-engine/model test 추가 | `images-ocr/src/test/kotlin/...` |
| T5 Native tests | `ocr.enabled`로 gate되는 host-native Tess4J test 추가 | `images-ocr/src/test/kotlin/...` |
| T6 Testcontainers tests | `ocr.container.enabled`로 gate되는 containerized Tesseract CLI smoke 추가 | `images-ocr/src/test/kotlin/...` |
| T7 Module docs | OCR README locale set과 test resource 추가 | `images-ocr/README.md`, `images-ocr/README.ko.md`, `src/test/resources/*` |
| T8 Root docs/guidance | root README locale set과 repo-local AGENTS에 OCR 등록 | `README.md`, `README.ko.md`, `AGENTS.md` |
| T9 Root diagrams/charts | OCR을 포함하도록 root README visual asset 갱신 | `docs/images/readme-diagrams/*`, `docs/images/readme-charts/*` |
| T10 CI/Nightly | OCR path filter, job, coverage artifact, status need 추가 | `.github/workflows/ci.yml`, `.github/workflows/nightly-tests.yml` |
| T11 Verification | targeted Gradle, docs, diagram, workflow check 실행 | 아래 command |
| T12 Review prep | code review artifact, lesson, PR body DoD 저장 | `docs/review/*`, `docs/lessons/*`, PR body |

## 구현 세부 사항

### T1 Module registration

- `bluetape4k-images-ocr`를 include하고 `images-ocr`에 mapping한다.
- `tess4j = "5.19.0"`과 `libs.tess4j`를 추가한다.
- 새 published module은 기존 `rootProject.subprojects` logic을 통해 BOM constraint에 포함되게 한다.
- `./gradlew -q projects`로 확인한다.

### T2/T3 API and engine

- `io.bluetape4k.images.ocr` package를 생성한다.
- 다음을 구현한다.
  - `OcrEngine`
  - `OcrOptions`
  - `OcrResult`
  - `TesseractEngineMode`
  - `TesseractPageSegmentationMode`
  - `OcrException` and `OcrConfigurationException`
  - `TesseractOcrEngine`
  - `ImmutableImage.extractText`
  - `ImmutableImage.suspendExtractText`
- `TesseractOcrEngine`은 call마다 fresh `Tesseract`를 생성한다.
- `TesseractOcrEngine`은 mutable Tess4J client state를 call 사이에 공유하지 않는다. 각 call은
  OCR duration 동안 자신이 configure한 `Tesseract` instance를 소유한다.
- `suspendExtractText`는 blocking OCR을 `Dispatchers.IO`로 감싼다.
- exception은 명시적으로 configure된 tessdata path error context를 넘어서 secret이나 전체 local
  path를 노출하지 않아야 한다.

### T4/T5/T6 tests

- `junit-platform.properties`와 `logback-test.xml`을 추가한다.
- Unit test:
  - option validation
  - enum value mapping
  - fake engine delegation
  - `runTest` 기반 suspend delegation
  - blocking boundary 전후의 suspend cancellation propagation
  - serializable model
  - call별 engine lifecycle/configuration isolation
- Native test:
  - `@EnabledIfSystemProperty(named = "ocr.enabled", matches = "true")`
  - generated English fixture OCR
  - missing language/datapath failure message
  - `eng`, `kor`, `jpn` language-pack availability
- Container test:
  - `@EnabledIfSystemProperty(named = "ocr.container.enabled", matches = "true")`
  - test-owned Dockerfile, unverified OCR image 없음
  - CLI OCR English smoke
  - `tesseract --list-langs`에 `eng`, `kor`, `jpn` 포함

local Docker를 계속 사용할 수 없으면 local container verification은 skipped로 기록하고
`ocr.container.enabled=true`는 GitHub CI evidence에 의존한다.

### T7/T8 docs

- Root README/README.ko:
  - OCR adoption lane 추가
  - module row 추가
  - Tesseract/traineddata requirements row 추가
  - install dependency 추가
  - usage example 추가
  - `TESSDATA_PREFIX`, missing languages, native library loading troubleshooting 추가
  - module README link 추가
- Module README/README.ko:
  - installation과 language data setup 설명
  - sync/suspend example 제시
  - `OcrOptions` 설명
  - native/container test gate 설명
- Repo-local `AGENTS.md`:
  - module row와 command example 추가
  - OCR native test는 gated이며 sequential하게 실행한다고 기록

### T9 diagram work

- 기존 root README diagram/chart asset을 제자리에서 갱신한다.
- 모든 README image는 PNG로 유지하고 모든 PNG는 SVG pair를 가진다.
- connector-heavy root overview에는 Graphviz `.dot`, `.plain`, `-graphviz.svg/png`를
  유지하거나 regenerate한다.
- label은 English를 사용한다.
- 다음을 검증한다.
  - SVG XML parsing.
  - 변경된 SVG마다 PNG 존재.
  - README가 local SVG asset을 embed하지 않음.
  - README image link resolution.
  - rendered PNG 직접 inspection.
  - `$bluetape4k-diagram`의 geometry/source drift check 기록.

### T10 CI/Nightly

- CI:
  - `images-ocr` output/filter/job 추가
  - Tesseract package와 Noto CJK font 설치
  - `tesseract --list-langs` preflight 실행
  - `:bluetape4k-images-ocr:test -Docr.enabled=true -Docr.container.enabled=true` 실행
  - test results artifact와 status need 추가
- Nightly full:
  - OCR job과 `coverage-images-ocr` 추가
  - status와 coverage aggregation need에 OCR job 포함
- workflow 수정 후 `actionlint`를 실행한다.

## 검증 명령

Step 3-R이 계획을 바꾸지 않는 한 다음 순서로 실행한다.

1. `./gradlew -q projects`
2. `./gradlew :bluetape4k-images-ocr:compileKotlin :bluetape4k-images-ocr:compileTestKotlin --console=plain`
3. `./gradlew :bluetape4k-images-ocr:test --console=plain`
4. `./gradlew :bluetape4k-images-ocr:detekt --console=plain`
5. local Tesseract가 있으면:
   `./gradlew :bluetape4k-images-ocr:test -Docr.enabled=true --console=plain`
6. local Docker가 있으면:
   `./gradlew :bluetape4k-images-ocr:test -Docr.container.enabled=true --console=plain`
7. `./gradlew :bluetape4k-images-ocr:build --console=plain`
8. `./gradlew :bluetape4k-images-ocr:koverXmlReport --console=plain`
9. `xmllint --noout docs/images/readme-diagrams/*.svg docs/images/readme-charts/*.svg`
10. `find docs/images/readme-diagrams docs/images/readme-charts -name '*.svg' -exec sh -c 'test -f "${1%.svg}.png"' sh {} \\;`
11. `rg 'docs/images/(readme-diagrams|readme-charts)/.*\\.svg' README*.md` must return no hits.
12. README image-link resolution check.
13. `actionlint`
14. `rg "\\\\'" .github/workflows` must return no hits.
15. `git diff --check`
16. `$bluetape4k-code-patterns`를 다시 적용한 Step 6-R 7-tier review, `P0 = 0`, `P1 = 0`.

## 롤백 계획

- `images-ocr/` 제거.
- `settings.gradle.kts`, README locale set, AGENTS, CI, Nightly, diagram에서
  `bluetape4k-images-ocr` 제거.
- `gradle/libs.versions.toml`에서 `tess4j` 제거.
- `./gradlew -q projects`, `actionlint`, diagram validation, `git diff --check`를 다시 실행한다.

## Step 3 DoD

| Item | Status |
|---|---|
| Spec inputs mapped to implementation tasks | Done |
| Files and modules identified | Done |
| Test strategy sequenced | Done |
| CI/Nightly changes planned | Done |
| Diagram validation planned | Done |
| Local Docker/Tesseract skip conditions explicit | Done |
| Rollback plan documented | Done |
