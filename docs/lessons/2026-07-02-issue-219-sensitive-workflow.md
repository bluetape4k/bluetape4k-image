# Issue 219 Sensitive moderation workflow example

## 배경

#219는 production detector runtime을 선택하지 않으면서 sensitive detection, moderation
policy, action selection, privacy-safe derivative generation을 연결하는 구체적 example이
필요했다.

## 결정

새 module을 만들지 않고 기존 `basic-processing` example에 workflow를 추가한다. 이 example은
deterministic fake detector output, 기존 `SensitiveContentDetection`과
`SensitiveModerationPolicy` model, rectangle redaction 전용 privacy derivative pipeline을
사용한다.

## 결과

example은 rectangle, polygon, polyline, raster-mask metadata region과 allow, mosaic,
blur, solid mask, manual review, drop, reject, quarantine action을 다룬다. README와 blog
seed는 production model selection, renderer adapter, storage side effect, review queue가
core image module 밖에 남는다고 명시한다.

## 검증

- `./gradlew :basic-processing:test --tests 'io.bluetape4k.images.examples.basic.SensitiveContentWorkflowQuickstartTest'`
- `./gradlew :basic-processing:runSensitiveWorkflow --args='build/tmp/sensitive-content-workflow-check'`
- `./gradlew :basic-processing:test`
- `./gradlew detekt`
- `git diff --check`

## 향후 방지책

별도 detector issue가 runtime을 선택하지 않는 한 향후 moderation example은 deterministic하게
유지한다. fixture detection이 semantic model accuracy를 증명한다고 암시하지 않고, storage,
quarantine, rejection, manual-review side effect를 `bluetape4k-images`로 옮기지 않는다.
