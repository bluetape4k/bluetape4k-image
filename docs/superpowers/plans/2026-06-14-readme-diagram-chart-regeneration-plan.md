# 2026-06-14 README diagram/chart regeneration plan

## Goal

Regenerate every README-referenced diagram and chart in `bluetape4k-image` from current source, current README text, benchmark reports, and approved bluetape4k visual baselines. README files must embed PNG files only, with matching SVG sources beside them.

## Global gates

- Apply every mandatory gate in `$bluetape4k-diagram`.
- Use current source, README, examples, configuration, benchmark reports, GNO lessons/PRs, and wiki best-practice catalog as source truth.
- Treat existing SVG/PNG assets as historical hints only.
- Keep diagram labels in English and surrounding README prose localized.
- Render SVG and PNG for every asset.
- Consolidate all README visual assets under `docs/images/`; legacy `docs/assets/` and `images-ocr/docs/assets/` paths are only historical inputs.
- For node-and-connector assets, keep Graphviz `.dot`, `.plain`, `-graphviz.svg`, and `-graphviz.png` evidence.
- Validate source-derived intent, source evidence, visual model, PNG-only README embeds, XML syntax, PNG readability, font roles, forbidden UI font absence, connector geometry, text fit, layer containment, and visual preview.
- Enforce uniform top/right/bottom/left whitespace at three levels: outer frame, content body, and every content-bearing layer/panel body.
- Do not proceed to the next diagram until the current diagram passes its generation and validation gates.
- Use `fireworks-tech-graph` as the classify/extract/plan/render/export/visual-review helper surface, `architecture-diagram-generator` for spacing and boundary/legend placement checks, and `excalidraw-diagram-skill` for the source-backed visual-argument test. Final assets still obey `$bluetape4k-diagram` fonts, pastel catalog style, SVG+PNG output, and validation gates.

## Catalog baselines

- Module/root overview: `module-overview-image-root`
- Layered architecture: `architecture-layered-exposed-mvc-virtualthread`
- Spring/adapter architecture: `architecture-javers-exposed` or `architecture-aws-spring-boot`
- Workflow/scenario: `workflow-image-upload`
- Sequence: `sequence-workflow-sample`
- Class: `class-diagram-style-v3`
- Charts: `chart-benchmark-comparison-sample`, with `chart-id-generator-comparison` and `chart-id-generator-before-after` as secondary references

## Rejected patterns to fail

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

## Work list

| # | Asset | Kind | README refs | Primary source evidence | Baseline | Stop gate |
|---:|---|---|---|---|---|---|
| 1 | `docs/images/readme-diagrams/root-readme-overview-01` | module-overview / selection flow | root EN/KO | `settings.gradle.kts`, root README EN/KO, module READMEs | `module-overview-image-root` | source intent, Graphviz evidence, uniform L/R/T/B margins |
| 2 | `docs/images/readme-charts/root-readme-module-chart-01` | module composition chart | root EN/KO | `settings.gradle.kts`, Gradle project names, root module table | `module-overview-image-root` | chart source table, PNG/SVG, balanced frame/content margins |
| 3 | `docs/images/readme-diagrams/bluetape4k-image-architecture-01` | layered architecture | root EN/KO | root README, module READMEs, Gradle module graph | `architecture-layered-exposed-mvc-virtualthread` | layered architecture gates and uniform margins |
| 4 | `docs/images/readme-diagrams/images-ocr-architecture-01` | layered architecture | images-ocr EN/KO | OCR source, OCR README, Tess4J/Tesseract integration | `architecture-layered-exposed-mvc-virtualthread` | OCR route no label/card/layer collision |
| 5 | `docs/images/readme-diagrams/images-ocr-class-diagram-01` | class | images-ocr EN/KO | OCR classes and public APIs | `class-diagram-style-v3` | class endpoint/text/route gates |
| 6 | `docs/images/readme-diagrams/images-ocr-sequence-diagram-01` | sequence | images-ocr EN/KO | OCR recognition flow source and README | `sequence-workflow-sample` | sequence label/path and participant margin gates |
| 7 | `docs/images/readme-diagrams/bom-architecture-01` | architecture | bom EN/KO | BOM build file, README dependency usage | `architecture-kafka4-module-boundary` | source-derived module boundary and PNG/SVG gates |
| 8 | `docs/images/readme-diagrams/images-architecture-01` | data-flow architecture | images EN/KO | `images` source loading/processing APIs, README usage | `architecture-layered-exposed-mvc-virtualthread` | no grid, route/body margin gates |
| 9 | `docs/images/readme-diagrams/images-class-core-01` | class | images EN/KO | core image APIs | `class-diagram-style-v3` | class graph gates |
| 10 | `docs/images/readme-diagrams/images-class-filters-01` | class | images EN/KO | filters and filter DSL source | `class-diagram-style-v3` | class graph gates |
| 11 | `docs/images/readme-diagrams/images-class-writers-01` | class | images EN/KO | coroutine writer source | `class-diagram-style-v3` | class graph gates |
| 12 | `docs/images/readme-diagrams/images-architecture-03` | transform/data-flow architecture | images EN/KO | transform/filter/analysis README sections and source | `flow-retry-workflow` | source intent and route clearance |
| 13 | `docs/images/readme-diagrams/images-class-04` | class / analysis map | images EN only | analysis API source and README section | `class-diagram-style-v3` | class graph gates |
| 14 | `docs/images/readme-diagrams/images-captcha-example-01` | workflow/static example | images-captcha EN/KO | captcha source and README | `workflow-image-upload` | PNG/SVG pair and visual margins |
| 15 | `docs/images/readme-diagrams/images-ktor-architecture-01` | layered architecture | images-ktor EN/KO | Ktor source and README | `architecture-javers-exposed` | architecture gates |
| 16 | `docs/images/readme-diagrams/images-spring-boot-architecture-01` | layered architecture | images-spring-boot EN/KO | Spring Boot auto-config source and README | `architecture-aws-spring-boot` | layer label and route clearance |
| 17 | `docs/images/readme-diagrams/images-vips-api-class-01` | class | images-vips-api EN/KO | Vips API source and README | `class-diagram-style-v3` | class graph gates |
| 18 | `docs/images/readme-diagrams/images-vips-api-architecture-02` | architecture/data-flow | images-vips-api EN/KO | Vips API source and README | `architecture-javers-exposed` | architecture gates |
| 19 | `docs/images/readme-diagrams/images-vips-java21-architecture-01` | architecture/data-flow | images-vips-java21 EN/KO | JVips source, README, native requirements | `architecture-javers-exposed` | native boundary/source gates |
| 20 | `docs/images/readme-diagrams/images-vips-java21-class-02` | class | images-vips-java21 EN/KO | JVips classes/source | `class-diagram-style-v3` | class graph gates |
| 21 | `docs/images/readme-diagrams/images-vips-java25-class-01` | class | images-vips-java25 EN/KO | FFM classes/source | `class-diagram-style-v3` | class graph gates |
| 22 | `docs/images/readme-diagrams/images-vips-java25-architecture-02` | architecture/chart-like comparison | images-vips-java25 EN/KO | FFM README and source | `architecture-javers-exposed` | source-derived comparison gates |
| 23 | `docs/images/readme-diagrams/images-benchmark-architecture-01` | benchmark architecture | images-benchmark EN/KO | benchmark source, reports, README | `architecture-layered-exposed-mvc-virtualthread` | benchmark-source architecture gates |
| 24 | `docs/images/readme-charts/images-benchmark-resize-latency-chart-01` | chart | images-benchmark EN/KO | README table and benchmark report | `chart-benchmark-comparison-sample` | chart value/source/margin gates |
| 25 | `docs/images/readme-charts/images-benchmark-encode-latency-chart-01` | chart | images-benchmark EN/KO | README table and benchmark report | `chart-benchmark-comparison-sample` | chart value/source/margin gates |
| 26 | `docs/images/readme-charts/images-benchmark-vips-backend-comparison-chart-01` | chart | images-benchmark EN/KO | `docs/vips-backend-comparison.md`, source classes | `chart-benchmark-comparison-sample` | chart source/margin gates |
| 27 | `docs/images/readme-charts/images-benchmark-filter-latency-chart-01` | chart | images-benchmark EN/KO | README table and benchmark report | `chart-benchmark-comparison-sample` | chart value/source/margin gates |
| 28 | `docs/images/readme-charts/images-benchmark-pipeline-allocation-chart-01` | chart | images-benchmark EN/KO | README table and raw report | `chart-benchmark-comparison-sample` | chart value/source/margin gates |
| 29 | `docs/images/readme-charts/images-benchmark-io-boundary-chart-01` | chart | images-benchmark EN/KO | README table and raw report | `chart-benchmark-comparison-sample` | chart value/source/margin gates |
| 30 | `docs/images/readme-charts/images-benchmark-file-io-throughput-chart-01` | chart | images-benchmark EN/KO | README table and raw report | `chart-id-generator-comparison` | chart value/source/margin gates |
| 31 | `docs/images/readme-charts/images-benchmark-large-streaming-chart-01` | chart | images-benchmark EN/KO | README table and raw report | `chart-benchmark-comparison-sample` | chart value/source/margin gates |
| 32 | `docs/images/readme-charts/images-benchmark-memory-profile-chart-01` | chart | images-benchmark EN/KO | README table and raw report | `chart-benchmark-comparison-sample` | chart value/source/margin gates |
| 33 | `docs/images/readme-diagrams/examples-basic-processing-scenario-01` | scenario workflow | basic-processing EN/KO | example source and README | `workflow-image-upload` | scenario/source gates |
| 34 | `docs/images/readme-diagrams/examples-basic-processing-architecture-01` | architecture | basic-processing EN/KO | example source and README | `architecture-layered-exposed-mvc-virtualthread` | architecture gates |
| 35 | `docs/images/readme-diagrams/examples-basic-processing-sequence-01` | sequence | basic-processing EN/KO | example source and README | `sequence-workflow-sample` | sequence gates |
| 36 | `docs/images/readme-diagrams/examples-spring-boot-image-api-scenario-01` | scenario workflow | spring-boot-image-api EN/KO | example source and README | `workflow-image-upload` | scenario/source gates |
| 37 | `docs/images/readme-diagrams/examples-spring-boot-image-api-architecture-01` | architecture | spring-boot-image-api EN/KO | example source and README | `architecture-aws-spring-boot` | architecture gates |
| 38 | `docs/images/readme-diagrams/examples-spring-boot-image-api-sequence-01` | sequence | spring-boot-image-api EN/KO | example source and README | `sequence-workflow-sample` | sequence gates |
| 39 | `docs/images/readme-diagrams/examples-spring-boot-ocr-api-scenario-01` | scenario workflow | spring-boot-ocr-api EN/KO | example source and README | `workflow-image-upload` | scenario/source gates |
| 40 | `docs/images/readme-diagrams/examples-spring-boot-ocr-api-architecture-01` | architecture | spring-boot-ocr-api EN/KO | example source and README | `architecture-aws-spring-boot` | architecture gates |
| 41 | `docs/images/readme-diagrams/examples-spring-boot-ocr-api-sequence-01` | sequence | spring-boot-ocr-api EN/KO | example source and README | `sequence-workflow-sample` | sequence gates |
| 42 | `docs/images/readme-diagrams/examples-ktor-image-api-scenario-01` | scenario workflow | ktor-image-api EN/KO | example source and README | `workflow-image-upload` | scenario/source gates |
| 43 | `docs/images/readme-diagrams/examples-ktor-image-api-architecture-01` | architecture | ktor-image-api EN/KO | example source and README | `architecture-javers-exposed` | architecture gates |
| 44 | `docs/images/readme-diagrams/examples-ktor-image-api-sequence-01` | sequence | ktor-image-api EN/KO | example source and README | `sequence-workflow-sample` | sequence gates |
| 45 | `docs/images/readme-diagrams/examples-ktor-ocr-api-scenario-01` | scenario workflow | ktor-ocr-api EN/KO | example source and README | `workflow-image-upload` | scenario/source gates |
| 46 | `docs/images/readme-diagrams/examples-ktor-ocr-api-architecture-01` | architecture | ktor-ocr-api EN/KO | example source and README | `architecture-javers-exposed` | architecture gates |
| 47 | `docs/images/readme-diagrams/examples-ktor-ocr-api-sequence-01` | sequence | ktor-ocr-api EN/KO | example source and README | `sequence-workflow-sample` | sequence gates |
| 48 | `docs/images/image-workbench.png` | raster hero illustration | root EN/KO | root README only | n/a | not a diagram/chart; link/readability check only unless replaced by explicit request |

## Execution order

1. Add or patch generators/auditors so they emit source-intent evidence, Graphviz evidence, SVG, PNG, and uniform-margin summaries.
2. Regenerate root overview/chart/architecture assets and validate them.
3. Regenerate `images-ocr` local assets and validate them.
4. Regenerate module architecture/class assets and validate one by one.
5. Regenerate benchmark architecture and chart assets from README/report tables and validate one by one.
6. Regenerate example scenario/architecture/sequence assets and validate one by one.
7. Run repository-wide validation: PNG link scan, no README SVG embeds, XML parse, PNG identify, forbidden font scan, Graphviz evidence presence, margin report, contact sheet plus individual PNG inspection.
8. Only after all gates pass, report final DoD.

## Stop condition

Stop only when all 48 referenced assets either pass the full applicable gate or are explicitly classified as not a diagram/chart (`image-workbench.png`) and pass link/readability checks. Any visible defect, missing evidence artifact, non-uniform margin, or skipped mandatory gate keeps the task open.
