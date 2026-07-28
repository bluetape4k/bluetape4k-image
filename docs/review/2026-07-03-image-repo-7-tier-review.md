# bluetape4k-image 7계층 코드 검토

날짜: 2026-07-03
범위: repository-wide Kotlin code, README/README.ko parity, README diagram assets
브랜치: `review/image-repo-quality-docs-diagrams`

## 요약

7계층 검토에서 release-blocking P0 issue는 발견되지 않았다. 이 pass는 이 branch 안의 좁은 quality defect 두 개를 닫았다:

- SVG rasterization now validates positive dimensions, DPI, timeout, and maximum
  bounds at `SvgRasterizeOptions` construction time.
- SSRF regression test는 이제 success나 exception을 모두 허용하지 않고 `allowExternalResources=false`가 loopback HTTP request 0회를 수행함을 증명한다.

repository에는 여전히 넓은 cleanup debt가 있다. 이는 이 review PR 안에 숨기지 말고 별도의 작은 PR로 처리해야 한다.

## Tier 1: API Boundary

상태: 후속 작업 포함 PASS.

- 수정: `SvgRasterizeOptions` now rejects zero or negative `width`, `height`,
  `dpi`, `timeoutMillis`, `maxWidthPx`, and `maxHeightPx`.
- 수정: `SvgRasterizeOptions` now implements `Serializable` with
  `serialVersionUID`, matching bluetape4k value-object rules for public data
  classes.
- 후속: `allowedSchemes` is part of the public options model, but the Batik
  path currently only applies `KEY_ALLOW_EXTERNAL_RESOURCES`. A future API PR
  should either wire scheme-level filtering explicitly or deprecate the option.

## Tier 2: Security

상태: 변경된 security surface에 대해 PASS.

- 수정: `BatikSvgRasterizerSecurityTest` now starts a local loopback server,
  embeds its URL in the SVG, rasterizes with `allowExternalResources=false`, and
  asserts request count is exactly zero.
- 기존 XXE test는 DOCTYPE/file entity input이 거부되거나 `/etc/passwd` marker를 leak하지 않음을 계속 검증한다.

## Tier 3: Correctness

상태: 변경 코드 기준 PASS.

- `SvgRasterizeOptions` now fails before invalid values reach `withTimeout` or
  Batik DPI conversion.
- `maxWidthPx` overflow behavior remains covered by the existing rasterizer
  test.

## Tier 4: Concurrency and Resource Lifecycle

상태: 변경 코드 기준 PASS.

- SVG rasterization continues to use `withTimeout` and `runInterruptible` on
  `Dispatchers.IO`.
- SSRF test의 loopback server는 `finally`에서 중지된다.

## Tier 5: Tests

상태: repository-level cleanup debt가 있지만 PASS.

- 대상 검증: `./gradlew :bluetape4k-images:test --tests '*BatikSvgRasterizer*' --warning-mode all`
  passed with 10 tests.
- 후속: older tests still contain mixed assertion idioms such as
  `kotlin.test.assertFailsWith` and JUnit assertions. Convert them gradually
  when touching those files; do not mix this broad migration into unrelated
  feature PRs.

## Tier 6: Documentation

상태: README parity 기준 PASS.

- Root `README.md`와 `README.ko.md`는 이제 root overview diagram을 설명한다
  color semantics.
- Barcode provider capability matrix now separates `Commercial SDK` and
  `Native/JNI SDK` concerns instead of combining license and runtime policy in
  one row.
- `images/README.ko.md` now includes the image-analysis diagram that already
  existed in `images/README.md`.
- AVIF/HEIC KDoc examples no longer reference the nonexistent
  `bluetape4k-images-vips`, `VipsAvifWriter`, or `VipsHeicReader` names.

## Tier 7: Diagram and Visual Assets

상태: PASS.

Evidence ledger:

- Best-practice references opened before final edits:
  `sequence-workflow-sample.png`, `bluetape4k-coroutines-sequence-01.png`, and
  `leader-redis-lettuce-sequence-02.png` from `bluetape4k-wiki`.
- Sequence generator source was updated, not only the generated SVG files:
  `docs/scripts/generate-example-readme-diagrams.py` and
  `docs/scripts/generate-readme-visual-assets.py` now emit the muted
  best-practices frame, participant card, lifeline, activation, label, line,
  badge, and marker palette for sequence assets.
- 범위: `svg_files=52`, `png_files=52`, `connector_files=41`,
  `connectors=310`, `cards=335`, `zero_connector_files=11`.
- Zero-connector exceptions: the 10 README chart SVGs and
  `images-captcha-example-01.svg`, which is a static decorative sample image.
- `xmllint --noout`: `files=52`, `errors=0`.
- CairoSVG render: 52 SVG files rendered to PNG with `-s 2`.
- `diagram-connector-audit.py`: all 52 SVGs PASS; connector-bearing files
  report `intrusions=0` and `crossings=0`; `root-readme-overview-01.svg` now
  reports `connectors=13`.
- `diagram-endpoint-audit.py`: `PASS files=52`.
- `diagram-geometry-audit.py`: `geometry_failures=0` for every SVG.
- `diagram-mixed-corner-audit.py`: `PASS files=52`, `paths=300`,
  `q_bends=120`, `failures=0`.
- `diagram-sequence-style-audit.py`: `PASS sequence_files=6`.
- Additional marker parity audit: `connector_marker_refs=310`, `mismatches=0`,
  `context_stroke=0`, `dashed_marker_dash_failures=0`.
- Additional sequence palette audit: `sequence_palette_files=6`,
  `connector_paths=41`, `labels=41`,
  `visible_semantic_colors=[#2E8F89,#3E9868,#4F83BF,#B9851B,#C94D68]`,
  `stale_tailwind_palette_hits=0`, `marker_mismatches=0`,
  `label_badge_mismatches=0`.
- Full-size PNG inspection covered all 6 sequence diagrams plus representative
  high-risk root, barcode, architecture, class, and chart assets:
  `examples-basic-processing-sequence-01.png`,
  `examples-ktor-image-api-sequence-01.png`,
  `examples-ktor-ocr-api-sequence-01.png`,
  `examples-spring-boot-image-api-sequence-01.png`,
  `examples-spring-boot-ocr-api-sequence-01.png`,
  `images-ocr-sequence-diagram-01.png`, `root-readme-overview-01.png`,
  `bluetape4k-image-architecture-01.png`,
  `images-barcode-api-architecture-01.png`, `images-ktor-architecture-01.png`,
  `images-spring-boot-architecture-01.png`, `images-class-core-01.png`,
  `images-class-filters-01.png`, `images-class-writers-01.png`,
  `images-ocr-class-diagram-01.png`, `images-vips-api-class-01.png`,
  `examples-ktor-image-api-architecture-01.png`,
  `examples-spring-boot-image-api-architecture-01.png`,
  `root-readme-module-chart-01.png`, and
  `images-benchmark-vips-backend-comparison-chart-01.png`.

## 남은 관찰 항목

- Public KDoc language는 repository 전체에서 아직 섞여 있다. 새 public API와 수정되는
  public API는 English KDoc을 사용해야 하지만, repo-wide conversion은 dedicated
  documentation PR로 처리해야 한다.
- 수정된 SVG options model 밖의 public data class는 별도 Serializable audit이 아직
  필요하다.
- untrusted input에 대해 external SVG resource를 활성화하는 기능을 추가하기 전에
  `allowedSchemes`를 Batik resource-loading behavior와 조정해야 한다.
