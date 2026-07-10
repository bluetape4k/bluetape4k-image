# Issue #200 Natural-Photo README Review

Date: 2026-07-10
Scope: benchmark README tables and their resize/encode chart assets.

## Evidence source

The authoritative headline source is
`benchmark/images-benchmark/docs/benchmark-results-2026-05-28-natural-photos.md`
and its committed raw JSON. It uses the `cafe` and `landscape` natural-photo
fixtures on macOS Java 25 FFM. The prior 2026-05-25 headline values used a
synthetic fallback fixture and are no longer presented as comparable current
evidence.

## Review result

**PASS — P0: 0, P1: 0**

The English and Korean headline tables match the report/raw values. The target
charts use linear 0-to-max axes, exact-scale bars (no minimum-width expansion),
CairoSVG 2x PNG output, and ASCII axis separators that render without tofu
glyphs.

## Repaired blockers

1. The initial chart configuration used log-transformed bars with linear ticks.
   The target charts now use `log_scale=False`.
2. The shared minimum 16px bar width exaggerated small FFM resize values.
   Target charts set `minimum_bar_width=0`.
3. Generated target PNGs used `rsvg-convert` at 1x. The generator now requires
   CairoSVG and writes canonical 2x PNG output.
4. CairoSVG rendered the `·` axis separator as a tofu glyph. The axis now uses
   an ASCII hyphen.

## Verification evidence

- `xmllint --noout` passed for both target SVGs.
- Final PNG dimensions: resize `2960x1180`; encode `2960x1540`.
- Full-size PNG inspection confirmed readable titles, legend, axis, values, and
  no clipped labels.
- `python3 -m py_compile docs/scripts/generate-readme-visual-assets.py` passed.
- `git diff --check` passed.

## Non-blocking follow-up

The generated SVG retains unused marker definitions. It does not affect the
target chart's evidence, rendering, or readability.
