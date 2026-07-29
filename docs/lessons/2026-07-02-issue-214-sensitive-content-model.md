# Issue #214 Sensitive content detection model 교훈

날짜: 2026-07-02
관련 이슈: #214

## 배경

0.4.0 line은 detector runtime을 선택하거나 `bluetape4k-images`에 model dependency를
추가하지 않는 첫 sensitive-content boundary가 필요했다.

## 결정

core image module에는 backend-neutral result model만 추가한다.

- stable category와 severity enum
- raw backend label 보존
- rectangle, polygon, polyline, raster-mask region geometry
- coordinate와 confidence validation

Detector runtime, policy action, redaction rendering은 별도 issue/module로 유지한다.

## 결과

public model은 이제 향후 detector adapter가 OpenCV, ONNX, model weight, policy engine을 core
module에 강제하지 않고 consistent result를 publish할 수 있게 한다.

## 검증

- model 구현 전 red compile failure.
- targeted model test 통과.
- 전체 `:bluetape4k-images:test` 통과.
- README/README.ko가 non-goal과 caller policy risk를 문서화.

## 향후 방지책

향후 sensitive-content 작업은 이 model을 먼저 소비해야 한다. 별도 issue와 module-boundary
review 없이 runtime detector dependency, bundled model asset, treatment action을
`bluetape4k-images`에 추가하지 않는다.
