# 2026-06-14 README 다이어그램/차트 재생성 계획

## 목표

`bluetape4k-image`의 README에서 참조하는 모든 다이어그램과 차트를 현재 소스, 현재 README 문장, benchmark report, 승인된 bluetape4k 시각 기준에서 다시 생성한다. README 파일에는 PNG만 삽입하고, 같은 위치에 대응하는 SVG 원본을 함께 둔다.

## 전역 게이트

- `$bluetape4k-diagram`의 모든 필수 게이트를 적용한다.
- 현재 소스, README, 예제, 설정, benchmark report, GNO lesson/PR, wiki best-practice catalog를 source of truth로 사용한다.
- 기존 SVG/PNG asset은 과거 참고 자료로만 다룬다.
- 다이어그램 label은 English로 유지하고, README 주변 본문은 locale에 맞춘다.
- 모든 asset에 대해 SVG와 PNG를 모두 렌더링한다.
- README 시각 asset은 모두 `docs/images/` 아래로 통합한다. 기존 `docs/assets/`와 `images-ocr/docs/assets/` 경로는 과거 입력으로만 사용한다.
- node-and-connector asset에는 Graphviz `.dot`, `.plain`, `-graphviz.svg`, `-graphviz.png` 증거를 남긴다.
- source-derived intent, source evidence, visual model, PNG-only README embed, XML syntax, PNG readability, font role, forbidden UI font absence, connector geometry, text fit, layer containment, visual preview를 검증한다.
- 세 수준에서 top/right/bottom/left 여백을 균일하게 강제한다: outer frame, content body, content-bearing layer/panel body.
- 현재 다이어그램이 생성 및 검증 게이트를 통과하기 전에는 다음 다이어그램으로 진행하지 않는다.
- `fireworks-tech-graph`는 classify/extract/plan/render/export/visual-review helper surface로 사용하고, `architecture-diagram-generator`는 spacing 및 boundary/legend 배치 점검에 사용하며, `excalidraw-diagram-skill`은 source-backed visual-argument test에 사용한다. 최종 asset은 여전히 `$bluetape4k-diagram`의 font, pastel catalog style, SVG+PNG 출력, 검증 게이트를 따른다.

## 카탈로그 기준

- Module/root overview: `module-overview-image-root`
- Layered architecture: `architecture-layered-exposed-mvc-virtualthread`
- Spring/adapter architecture: `architecture-javers-exposed` 또는 `architecture-aws-spring-boot`
- Workflow/scenario: `workflow-image-upload`
- Sequence: `sequence-workflow-sample`
- Class: `class-diagram-style-v3`
- Charts: `chart-benchmark-comparison-sample`; 보조 기준은 `chart-id-generator-comparison`, `chart-id-generator-before-after`

## 실패 처리할 거부 패턴

- relationship-heavy-grid
- unclear-diagram-purpose
- surface-redraw-without-source-model
- card-penetrating-connector
- tangent-or-zero-degree-endpoint
- inheritance-arrow-not-on-parent
- undifferentiated-class-route-overlap
- layer-label-crowding
- text-overflow-or-bad-centering
- sequence-label-path-intersection
- empty-sequence-branch
- chart-note-crowding
- missing-or-subtle-decorator

## 작업 목록

| # | Asset | 종류 | README 참조 | 주 source evidence | 기준 | 중단 게이트 |
|---:|---|---|---|---|---|---|
| 1 | `docs/images/readme-diagrams/root-readme-overview-01` | module-overview / selection flow | root EN/KO | `settings.gradle.kts`, root README EN/KO, module README | `module-overview-image-root` | source intent, Graphviz evidence, 균일한 L/R/T/B margin |
| 2 | `docs/images/readme-charts/root-readme-module-chart-01` | module composition chart | root EN/KO | `settings.gradle.kts`, Gradle project names, root module table | `module-overview-image-root` | chart source table, PNG/SVG, 균형 잡힌 frame/content margin |
| 3 | `docs/images/readme-diagrams/bluetape4k-image-architecture-01` | layered architecture | root EN/KO | root README, module README, Gradle module graph | `architecture-layered-exposed-mvc-virtualthread` | layered architecture gate와 균일 margin |
| 4 | `docs/images/readme-diagrams/images-ocr-architecture-01` | layered architecture | images-ocr EN/KO | OCR source, OCR README, Tess4J/Tesseract integration | `architecture-layered-exposed-mvc-virtualthread` | OCR route에 label/card/layer collision 없음 |
| 5 | `docs/images/readme-diagrams/images-ocr-class-diagram-01` | class | images-ocr EN/KO | OCR class와 public API | `class-diagram-style-v3` | class endpoint/text/route gate |
| 6 | `docs/images/readme-diagrams/images-ocr-sequence-diagram-01` | sequence | images-ocr EN/KO | OCR recognition flow source와 README | `sequence-workflow-sample` | sequence label/path와 participant margin gate |
| 7 | `docs/images/readme-diagrams/bom-architecture-01` | architecture | bom EN/KO | BOM build file, README dependency usage | `architecture-kafka4-module-boundary` | source-derived module boundary와 PNG/SVG gate |
| 8 | `docs/images/readme-diagrams/images-architecture-01` | data-flow architecture | images EN/KO | `images` source loading/processing API, README usage | `architecture-layered-exposed-mvc-virtualthread` | grid 없음, route/body margin gate |
| 9 | `docs/images/readme-diagrams/images-class-core-01` | class | images EN/KO | core image API | `class-diagram-style-v3` | class graph gate |
| 10 | `docs/images/readme-diagrams/images-class-filters-01` | class | images EN/KO | filter와 filter DSL source | `class-diagram-style-v3` | class graph gate |
| 11 | `docs/images/readme-diagrams/images-class-writers-01` | class | images EN/KO | coroutine writer source | `class-diagram-style-v3` | class graph gate |
| 12 | `docs/images/readme-diagrams/images-architecture-03` | transform/data-flow architecture | images EN/KO | transform/filter/analysis README section과 source | `flow-retry-workflow` | source intent와 route clearance |
| 13 | `docs/images/readme-diagrams/images-class-04` | class / analysis map | images EN only | analysis API source와 README section | `class-diagram-style-v3` | class graph gate |
| 14 | `docs/images/readme-diagrams/images-captcha-example-01` | workflow/static example | images-captcha EN/KO | captcha source와 README | `workflow-image-upload` | PNG/SVG pair와 visual margin |
| 15 | `docs/images/readme-diagrams/images-ktor-architecture-01` | layered architecture | images-ktor EN/KO | Ktor source와 README | `architecture-javers-exposed` | architecture gate |
| 16 | `docs/images/readme-diagrams/images-spring-boot-architecture-01` | layered architecture | images-spring-boot EN/KO | Spring Boot auto-config source와 README | `architecture-aws-spring-boot` | layer label과 route clearance |
| 17 | `docs/images/readme-diagrams/images-vips-api-class-01` | class | images-vips-api EN/KO | Vips API source와 README | `class-diagram-style-v3` | class graph gate |
| 18 | `docs/images/readme-diagrams/images-vips-api-architecture-02` | architecture/data-flow | images-vips-api EN/KO | Vips API source와 README | `architecture-javers-exposed` | architecture gate |
| 19 | `docs/images/readme-diagrams/images-vips-java21-architecture-01` | architecture/data-flow | images-vips-java21 EN/KO | JVips source, README, native requirements | `architecture-javers-exposed` | native boundary/source gate |
| 20 | `docs/images/readme-diagrams/images-vips-java21-class-02` | class | images-vips-java21 EN/KO | JVips class/source | `class-diagram-style-v3` | class graph gate |
| 21 | `docs/images/readme-diagrams/images-vips-java25-class-01` | class | images-vips-java25 EN/KO | FFM class/source | `class-diagram-style-v3` | class graph gate |
| 22 | `docs/images/readme-diagrams/images-vips-java25-architecture-02` | architecture/chart-like comparison | images-vips-java25 EN/KO | FFM README와 source | `architecture-javers-exposed` | source-derived comparison gate |
| 23 | `docs/images/readme-diagrams/images-benchmark-architecture-01` | benchmark architecture | images-benchmark EN/KO | benchmark source, report, README | `architecture-layered-exposed-mvc-virtualthread` | benchmark-source architecture gate |
| 24 | `docs/images/readme-charts/images-benchmark-resize-latency-chart-01` | chart | images-benchmark EN/KO | README table과 benchmark report | `chart-benchmark-comparison-sample` | chart value/source/margin gate |
| 25 | `docs/images/readme-charts/images-benchmark-encode-latency-chart-01` | chart | images-benchmark EN/KO | README table과 benchmark report | `chart-benchmark-comparison-sample` | chart value/source/margin gate |
| 26 | `docs/images/readme-charts/images-benchmark-vips-backend-comparison-chart-01` | chart | images-benchmark EN/KO | `docs/vips-backend-comparison.md`, source class | `chart-benchmark-comparison-sample` | chart source/margin gate |
| 27 | `docs/images/readme-charts/images-benchmark-filter-latency-chart-01` | chart | images-benchmark EN/KO | README table과 benchmark report | `chart-benchmark-comparison-sample` | chart value/source/margin gate |
| 28 | `docs/images/readme-charts/images-benchmark-pipeline-allocation-chart-01` | chart | images-benchmark EN/KO | README table과 raw report | `chart-benchmark-comparison-sample` | chart value/source/margin gate |
| 29 | `docs/images/readme-charts/images-benchmark-io-boundary-chart-01` | chart | images-benchmark EN/KO | README table과 raw report | `chart-benchmark-comparison-sample` | chart value/source/margin gate |
| 30 | `docs/images/readme-charts/images-benchmark-file-io-throughput-chart-01` | chart | images-benchmark EN/KO | README table과 raw report | `chart-id-generator-comparison` | chart value/source/margin gate |
| 31 | `docs/images/readme-charts/images-benchmark-large-streaming-chart-01` | chart | images-benchmark EN/KO | README table과 raw report | `chart-benchmark-comparison-sample` | chart value/source/margin gate |
| 32 | `docs/images/readme-charts/images-benchmark-memory-profile-chart-01` | chart | images-benchmark EN/KO | README table과 raw report | `chart-benchmark-comparison-sample` | chart value/source/margin gate |
| 33 | `docs/images/readme-diagrams/examples-basic-processing-scenario-01` | scenario workflow | basic-processing EN/KO | example source와 README | `workflow-image-upload` | scenario/source gate |
| 34 | `docs/images/readme-diagrams/examples-basic-processing-architecture-01` | architecture | basic-processing EN/KO | example source와 README | `architecture-layered-exposed-mvc-virtualthread` | architecture gate |
| 35 | `docs/images/readme-diagrams/examples-basic-processing-sequence-01` | sequence | basic-processing EN/KO | example source와 README | `sequence-workflow-sample` | sequence gate |
| 36 | `docs/images/readme-diagrams/examples-spring-boot-image-api-scenario-01` | scenario workflow | spring-boot-image-api EN/KO | example source와 README | `workflow-image-upload` | scenario/source gate |
| 37 | `docs/images/readme-diagrams/examples-spring-boot-image-api-architecture-01` | architecture | spring-boot-image-api EN/KO | example source와 README | `architecture-aws-spring-boot` | architecture gate |
| 38 | `docs/images/readme-diagrams/examples-spring-boot-image-api-sequence-01` | sequence | spring-boot-image-api EN/KO | example source와 README | `sequence-workflow-sample` | sequence gate |
| 39 | `docs/images/readme-diagrams/examples-spring-boot-ocr-api-scenario-01` | scenario workflow | spring-boot-ocr-api EN/KO | example source와 README | `workflow-image-upload` | scenario/source gate |
| 40 | `docs/images/readme-diagrams/examples-spring-boot-ocr-api-architecture-01` | architecture | spring-boot-ocr-api EN/KO | example source와 README | `architecture-aws-spring-boot` | architecture gate |
| 41 | `docs/images/readme-diagrams/examples-spring-boot-ocr-api-sequence-01` | sequence | spring-boot-ocr-api EN/KO | example source와 README | `sequence-workflow-sample` | sequence gate |
| 42 | `docs/images/readme-diagrams/examples-ktor-image-api-scenario-01` | scenario workflow | ktor-image-api EN/KO | example source와 README | `workflow-image-upload` | scenario/source gate |
| 43 | `docs/images/readme-diagrams/examples-ktor-image-api-architecture-01` | architecture | ktor-image-api EN/KO | example source와 README | `architecture-javers-exposed` | architecture gate |
| 44 | `docs/images/readme-diagrams/examples-ktor-image-api-sequence-01` | sequence | ktor-image-api EN/KO | example source와 README | `sequence-workflow-sample` | sequence gate |
| 45 | `docs/images/readme-diagrams/examples-ktor-ocr-api-scenario-01` | scenario workflow | ktor-ocr-api EN/KO | example source와 README | `workflow-image-upload` | scenario/source gate |
| 46 | `docs/images/readme-diagrams/examples-ktor-ocr-api-architecture-01` | architecture | ktor-ocr-api EN/KO | example source와 README | `architecture-javers-exposed` | architecture gate |
| 47 | `docs/images/readme-diagrams/examples-ktor-ocr-api-sequence-01` | sequence | ktor-ocr-api EN/KO | example source와 README | `sequence-workflow-sample` | sequence gate |
| 48 | `docs/images/image-workbench.png` | raster hero illustration | root EN/KO | root README only | n/a | diagram/chart가 아니므로 명시 요청으로 교체하지 않는 한 link/readability check만 수행 |

## 실행 순서

1. generator/auditor를 추가하거나 패치해 source-intent evidence, Graphviz evidence, SVG, PNG, uniform-margin summary를 생성하게 한다.
2. root overview/chart/architecture asset을 다시 생성하고 검증한다.
3. `images-ocr` local asset을 다시 생성하고 검증한다.
4. module architecture/class asset을 하나씩 다시 생성하고 검증한다.
5. benchmark architecture와 chart asset을 README/report table에서 다시 생성하고 하나씩 검증한다.
6. example scenario/architecture/sequence asset을 다시 생성하고 하나씩 검증한다.
7. repository-wide validation을 실행한다: PNG link scan, README SVG embed 부재, XML parse, PNG identify, forbidden font scan, Graphviz evidence presence, margin report, contact sheet와 개별 PNG inspection.
8. 모든 게이트가 통과한 뒤에만 final DoD를 보고한다.

## 중단 조건

48개 참조 asset이 모두 해당 full gate를 통과하거나, `image-workbench.png`처럼 diagram/chart가 아님을 명시적으로 분류하고 link/readability check를 통과한 경우에만 중단한다. 시각 결함, 누락된 evidence artifact, 균일하지 않은 margin, 건너뛴 mandatory gate가 하나라도 있으면 작업은 계속 열린 상태로 둔다.
