# Issue #200 Natural-Photo README 검토

날짜: 2026-07-10
범위: benchmark README table과 resize/encode chart asset.

## 근거 출처

권위 있는 headline source는
`benchmark/images-benchmark/docs/benchmark-results-2026-05-28-natural-photos.md`
및 commit된 raw JSON이다. 이 근거는 macOS Java 25 FFM에서 `cafe`와 `landscape`
natural-photo fixture를 사용한다. 이전 2026-05-25 headline value는 synthetic
fallback fixture를 사용했으므로 더 이상 비교 가능한 current evidence로 제시하지
않는다.

## 검토 결과

**PASS — P0: 0, P1: 0**

English와 Korean headline table은 report/raw value와 일치한다. target chart는
linear 0-to-max axis, exact-scale bar(minimum-width expansion 없음), CairoSVG 2x
PNG output, tofu glyph 없이 렌더링되는 ASCII axis separator를 사용한다.

## 수정한 차단 사항

1. 초기 chart configuration이 log-transformed bar와 linear tick을 함께 사용했다.
   target chart는 이제 `log_scale=False`를 사용한다.
2. shared minimum 16px bar width가 작은 FFM resize value를 과장했다.
   target chart는 `minimum_bar_width=0`을 설정한다.
3. generated target PNG가 `rsvg-convert` 1x output을 사용했다. generator는 이제
   CairoSVG를 요구하고 canonical 2x PNG output을 쓴다.
4. CairoSVG가 `·` axis separator를 tofu glyph로 렌더링했다. axis는 이제 ASCII
   hyphen을 사용한다.

## 검증 근거

- `xmllint --noout`는 두 target SVG에서 모두 통과했다.
- 최종 PNG dimension: resize `2960x1180`; encode `2960x1540`.
- full-size PNG inspection에서 title, legend, axis, value가 읽히고 clipped label이
  없음을 확인했다.
- `python3 -m py_compile docs/scripts/generate-readme-visual-assets.py` 통과.
- `git diff --check` 통과.

## 비차단 후속 작업

generated SVG는 unused marker definition을 보존한다. 이는 target chart의 evidence, rendering, readability에 영향을 주지 않는다.
