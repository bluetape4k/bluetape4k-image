# Issue #1 OCR Step 6-R 검토

- 이슈: #1
- 브랜치: `feat/issue-1-ocr-support`
- 워크플로: `$bluetape4k-workflow` Type A / `$bluetape4k-full-feature`
- 범위: `images-ocr`, 루트 README locale, 루트 다이어그램/차트, 모듈 등록, CI, Nightly, repo-local guidance.
- 검토자:  Codex 로컬 검토. 사용: `bluetape4k-code-patterns`, `bluetape4k-diagram`, 및 Step 6-R 참고 자료.

## 기준 발견 사항

| 우선순위 | 파일 | 영역 | 발견 사항 | 조치 |
|---|---|---|---|---|
| P1 | `images-ocr/src/main/kotlin/io/bluetape4k/images/ocr/TesseractOcrEngine.kt` | 공개 API / 의존성 경계 | 첫 구현은 Tess4J가 `implementation` 의존성인데도 공개 constructor에서 Tess4J `ITesseract` factory를 노출했다. | Replaced it with private primary constructor, public no-arg constructor, internal `TesseractClient` adapter, and `@JvmSynthetic` test factory. |

## 계층별 발견 사항

| 계층 | 영역 | P0 | P1 | P2 | P3 | 결과 |
|---|---|---:|---:|---:|---:|---|
| 1 | 보안 | 0 | 0 | 0 | 0 | secret, credential, unsafe default, injection surface, unsafe deserialization은 발견되지 않았다. 오류 메시지는 native path를 sanitize한다. |
| 2 | 운영/SRE 안정성 | 0 | 0 | 0 | 0 | native runtime/tessdata 실패는 조치 가능한 구성 메시지로 감싼다. 호출별 Tess4J instance는 shared mutable native state를 피한다. |
| 3 | 구조 영향 | 0 | 0 | 0 | 0 | 모듈은 `:bluetape4k-images`에 의존하며 reverse dependency나 cross-module API break는 없다. 공개 constructor surface는 더 이상 Tess4J를 노출하지 않는다. |
| 4 | Kotlin 코드 품질 | 0 | 0 | 0 | 0 | KDoc은 영어이고 model은 serializable이며 validation은 `require*`를 사용한다. suspend API는 `withContext(Dispatchers.IO)`를 사용하고 `!!`나 deprecated Exposed import는 없다. |
| 5 | 테스트/타입/조용한 실패 | 0 | 0 | 0 | 0 | option, enum mapping, serialization, delegation, 호출별 구성, sanitized error, dispatcher dispatch, 시작 전 취소를 테스트한다. native/container test는 gate로 보호된다. |
| 6 | 성능/안정성 | 0 | 0 | 0 | 0 | blocking OCR은 blocking API 또는 `Dispatchers.IO`에 격리된다. unbounded retry/buffer/wait는 추가하지 않았다. container test는 gated이며 always-on이 아니다. |
| 7 | 문서/릴리스/근거 | 0 | 0 | 0 | 0 | README/README.ko, module README, AGENTS, diagram, module registration, CI, Nightly, verification evidence를 갱신했다. |

## 패턴과 영향 점검

- 세션 앞부분에서 CodeGraph를 시도했지만 repository graph가 비어 있었다(`Files: 0`, 갱신된 적 없음). 그래서 source inspection, Gradle module evidence, GNO, targeted grep으로 검토했다.
- GNO docs query는 가장 가까운 모듈 등록 선례를 찾았다: `docs/superpowers/plans/2026-05-24-issue-4-images-captcha-plan.md`.
- GNO GitHub query는 image repo 선례를 찾았다: issue #31 and PR #131.
- production/test concurrency quick scan: `GlobalScope|runBlocking|Thread.sleep|delay|synchronized|@Synchronized|runCatching` `images-ocr`에서 0개 일치.
- Kotlin hazard scan: `!!|SqlExpressionBuilder.eq|assertThrows|kotlin.test.assertFailsWith|invoking .*shouldThrow` `images-ocr`에서 0개 일치.
- public classfile check: `javap ... TesseractOcrEngine | grep tess4j || true` `tess4j` signature가 없음을 반환.

## 다이어그램 검토

- 사용자 수정 이후 최신 `-diagram` skill을 다시 읽고 적용했다.
- font discovery: `fc-match "아키텍처s Daughter"` resolved `아키텍처sDaughter-Regular.ttf`; `fc-match "Comic Mono"` resolved `ComicMono-Bold.ttf`.
- XML gate: `xmllint --noout` 변경된 SVG asset에서 통과.
- README image links: `missing=0`.
- SVG/PNG pairs: `missing_png=0`.
- 금지 font/SVG embed scan: 0 matches for README SVG embeds, `Inter`, `Arial`, `Helvetica`, old `13x13`, and tiny `3.9x3.9` arrowheads.
- 변경된 root Image 아키텍처ure, `images-ocr` 아키텍처ure, `images-ocr` Class Diagram에 대한 Graphviz evidence가 있다: `.dot`, `.plain`, `*-graphviz.svg`, `*-graphviz.png`.
- 렌더링된 PNG를 개별 검수했다:
  - `docs/images/readme-diagrams/root-readme-overview-01.png`
  - `docs/images/readme-charts/root-readme-module-chart-01.png`
  - `docs/images/readme-diagrams/bluetape4k-image-architecture-01.png`
  - `docs/images/readme-diagrams/images-ocr-architecture-01.png`
  - `docs/images/readme-diagrams/images-ocr-class-diagram-01.png`
  - `docs/images/readme-diagrams/images-ocr-sequence-diagram-01.png`
- overview geometry gate:

```text
geometryGate file=docs/images/readme-diagrams/root-readme-overview-01.svg
nodes=12 routes=15 segments=36 badEndpointAngle=0 badBends=0 interiorCrossings=0 marginImbalance=source-layer-balanced titleGap=24 labelsOk=True

geometryGate file=docs/images/readme-diagrams/bluetape4k-image-architecture-01.svg
nodes=10 routes=10 segments=28 badEndpointAngle=0 badBends=0 interiorCrossings=0 marginImbalance=balanced titleGap=24 labelsOk=True

geometryGate file=docs/images/readme-diagrams/images-ocr-architecture-01.svg
nodes=10 routes=9 segments=19 badEndpointAngle=0 badBends=0 interiorCrossings=0 marginImbalance=balanced titleGap=24 labelsOk=True

geometryGate file=docs/images/readme-diagrams/images-ocr-class-diagram-01.svg
nodes=11 routes=9 segments=17 badEndpointAngle=0 badBends=0 interiorCrossings=0 marginImbalance=balanced titleGap=24 labelsOk=True

geometryGate file=docs/images/readme-diagrams/images-ocr-sequence-diagram-01.svg
nodes=5 routes=7 segments=8 badEndpointAngle=0 badBends=0 interiorCrossings=0 marginImbalance=balanced titleGap=24 labelsOk=True
```

## 검증 근거

| 명령 | 결과 |
|---|---|
| `./gradlew -q projects --console=plain` | Passed; `:bluetape4k-images-ocr` is registered. |
| `./gradlew :bluetape4k-images-ocr:compileKotlin :bluetape4k-images-ocr:compileTestKotlin :bluetape4k-images-ocr:test --console=plain` | Passed; 13 tests, 10 executed, 3 skipped. |
| `./gradlew :bluetape4k-images-ocr:build --console=plain` | PASS; Kover verify 포함. |
| `./gradlew :bluetape4k-images-ocr:koverXmlReport --console=plain` | Passed; `images-ocr/build/reports/kover/report.xml` exists. |
| `actionlint` | Passed. |
| `git diff --check` | Passed. |
| 다이어그램 asset 검증 | PASS; XML, README link, PNG pair, font discovery, forbidden font/embed grep, geometry gate 통과. |
| `:bluetape4k-images-ocr:detekt` | Not available in this project; task lookup failed with `task 'detekt' not found`. |
| `command -v tesseract` | `tesseract_not_found`; native OCR tests skipped locally. |
| `command -v docker` | `docker_not_found`; container OCR tests skipped locally. |

## 수렴 결과

- 최종 게이트: `P0 = 0`, `P1 = 0`.
- 남은 위험: native/container OCR 테스트는 CI/Nightly에 구성돼 있지만, 이 머신에 Tesseract와 Docker가 없어 로컬에서는 실행하지 않았다.
