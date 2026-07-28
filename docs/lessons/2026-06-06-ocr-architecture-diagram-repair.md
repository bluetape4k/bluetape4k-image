# OCR architecture diagram 수리 교훈 (2026-06-06)

## 배경

`images-ocr` architecture diagram에는 두 가지 시각적 결함이 있었다. result label이
card 밖으로 넘쳤고, engine-to-result connector가 Tess4J card를 가로질렀다. 첫 번째
수리는 route가 layer label과 충돌하거나 시각적으로 과도하게 우회하면 card 내부만
피하는 것으로는 충분하지 않다는 점도 보여줬다.

## 결정

route는 왼쪽에 유지하되 layer label과 Tess4J card 사이의 더 좁은 `x=370` lane을
사용한다. `OcrResult or OcrException`이 들어가도록 result card를 넓힌다.

## 결과

렌더링된 PNG는 이제 text를 result card 안에 유지하고, engine-to-result connector를
Tess4J card나 layer label과 교차하지 않게 배치한다.

## 향후 방지책

diagram repair에서는 route 조정 후마다 렌더링된 PNG를 확인하고, 단순 non-overlap만이
아니라 시각적으로 route를 최적화한다. geometry상 통과하더라도 눈에 띄게 과도한
우회를 하는 route는 review-ready가 아니다.
