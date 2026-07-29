# Image repository review와 diagram checklist

날짜: 2026-07-03
범위: `bluetape4k-image`

## 배경

repo-wide review는 Kotlin code quality, README parity, README diagram validation을 함께
다뤘다. 이전 diagram render는 눈으로 보기에는 괜찮았지만 connector metadata, endpoint
routing, sequence style의 machine check에서 실패했다. 이후 checklist challenge는 "script
passed"와 contact-sheet review만으로 충분한 evidence가 아니라는 점도 보여줬다. 해당 pass는
marker color parity, dashed marker isolation, sequence palette parity, zero-connector
exception, high-risk PNG full-size inspection을 명시적으로 증명하지 않았다. sequence palette
challenge는 generator가 여전히 오래된 Tailwind-like palette를 emit한다면 generated SVG
post-fix만으로는 부족하다는 추가 gap도 드러냈다. generator source 자체가 defect pattern
audit에 포함되어야 한다.

## 결정

README-facing diagram은 SVG와 PNG evidence를 모두 갖는 generated asset으로 다룬다. 넓은
diagram refresh에서는 `docs/images/readme-diagrams`와 `docs/images/readme-charts` 아래의
모든 SVG를 `bluetape4k-diagram` audit script로 검증한 뒤 PNG를 render하고 contact sheet와
대표 single image를 inspect한다. connector-heavy 또는 sequence diagram에는 connector,
card, marker reference, dashed marker head, sequence label, zero-connector exception count를
담은 explicit evidence ledger를 추가한다. sequence diagram은 regenerated SVG/PNG asset을
수용하기 전에 generator 또는 source template 자체를 opened best-practices family와 대조해
검증한다.

## 결과

최종 checklist는 52개 SVG file에서 통과했다.

- `xmllint --noout`
- `diagram-connector-audit.py`
- `diagram-endpoint-audit.py`
- `diagram-geometry-audit.py`
- `diagram-mixed-corner-audit.py`
- `diagram-sequence-style-audit.py` for sequence diagrams
- 추가 invariant audit: `connector_marker_refs=310`, `mismatches=0`,
  `context_stroke=0`, `dashed_marker_dash_failures=0`, `sequence_files=6`
- 추가 sequence palette audit: `sequence_palette_files=6`,
  `connector_paths=41`, `labels=41`,
  `visible_semantic_colors=[#2E8F89,#3E9868,#4F83BF,#B9851B,#C94D68]`,
  `stale_tailwind_palette_hits=0`, `marker_mismatches=0`,
  `label_badge_mismatches=0`

review는 SVG SSRF test도 수정해서 단순 "success or exception"이 아니라 outbound HTTP
request가 0개임을 증명하게 했다.

## 향후 지침

- connector path가 없는 chart SVG에는 unused marker definition을 남기지 않는다.
- sequence label background는 `class="label pill"`이 아니라 `class="pill"`을 사용한다.
  오래된 `.label` fill이 text를 덮을 수 있기 때문이다.
- contact sheet에만 의존하지 않는다. 마지막 coordinate 또는 style 변경 후 touched/high-risk
  PNG를 full-size로 열고, inspected file을 PR evidence에 적는다.
- sequence checklist가 적용될 때 sequence return line은 muted teal return palette를 사용하고,
  normal call line은 saturated `#2563eb`를 피해야 한다.
- sequence frame/background, participant card, lifeline, activation bar, label pill,
  number badge, message line, marker를 모두 opened best-practices PNG와 대조한다. line color
  replacement만으로 palette parity라고 보지 않는다.
- sequence asset이 generated라면 generator를 먼저 갱신한다. 그 뒤 SVG/PNG를 regenerate하고
  generated file에서 `#2563eb`, Tailwind pastel participant fill, 오래된 pale-blue activation
  bar, mismatched marker ID 같은 old palette literal을 audit한다.
- dashed connector pattern이 arrowhead로 번지지 않도록 marker definition은 connector stroke
  color와 일치해야 하고 `stroke-dasharray="none"`을 설정해야 한다.
- SVG resource-security test에서는 network failure에 의존하지 말고 local counting HTTP server를
  사용해 request count를 assert한다.
