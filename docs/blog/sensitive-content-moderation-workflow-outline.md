# Sensitive Content Moderation Workflow Blog 초안

이 문서는 향후 bluetape4k image moderation 글로 확장할 수 있는 blog-ready
outline이다. 아직 게시용 post가 아니라 의도적으로 outline 상태로 둔다. 별도 detector
issue가 production model, runtime packaging, GPU/native dependency, detector
benchmark를 선택하고 검증하기 전까지는 해당 주장을 글에 넣지 않는다.

## 작업 제목

Detection, policy, rendering을 결합하지 않는 sensitive image moderation

## 독자

Image moderation workflow는 필요하지만 core image library가 model download,
detector runtime 선택, storage side effect, application-specific review queue를
소유하기를 원하지 않는 Kotlin backend engineer.

## Source Anchor

- `io.bluetape4k.images.moderation.SensitiveContentDetection`
- `io.bluetape4k.images.moderation.SensitiveModerationPolicy`
- `io.bluetape4k.images.privacy.PrivacyDerivativeOptions`
- `io.bluetape4k.images.examples.basic.SensitiveContentWorkflowQuickstart`

이 outline을 blog post로 전환할 때는 `develop` branch의 source link를 사용한다.

## 글 구성

1. 문제
   - Detector result는 moderation decision이 아니다.
   - Moderation decision은 pixel renderer가 아니다.
   - Renderer는 storage, quarantine, rejection workflow가 아니다.

2. Boundary model
   - Detection facts: category, severity, confidence, raw backend label, region.
   - Policy decisions: selected action, level, parameters, matched rule, reason.
   - Rendering: public-safe derivative, metadata/GPS stripping, redaction report.
   - Side effects: reject, drop, quarantine, manual review는 caller가 소유한다.

3. Architecture
   - Detector adapter는 backend-neutral fact를 만든다.
   - Policy rule은 fact를 audit 가능한 treatment decision으로 바꾼다.
   - Renderer는 지원하는 geometry/action pair만 소비한다.
   - Application service는 drop, reject, quarantine storage, review queue,
     audit persistence 같은 durable side effect를 소유한다.

4. Workflow states
   - `DETECTED`
   - `CLASSIFIED`
   - `POLICY_EVALUATED`
   - `ACTION_SELECTED`
   - Optional terminal 또는 side-effect state: `RENDERED`, `REJECTED`,
     `QUARANTINED`, `FAILED`

5. Severity와 confidence
   - `LOW`: 보통 audit-only 또는 allow.
   - `MEDIUM`: treatment 가능성이 있지만 threshold review가 필요하다.
   - `HIGH`: automatic treatment나 manual review가 흔한 경로다.
   - `CRITICAL`: drop, reject, quarantine 경로 가능성이 커진다.
   - Severity는 policy-facing risk bucket이다.
   - Confidence는 detector output이며 사실로 취급하면 안 된다.
   - False positive, false negative, model drift, distribution shift는 일반적인
     운영 risk이므로 threshold review가 필요하다.

6. Region geometry
   - Rectangle: 현재 core privacy derivative pipeline에서 바로 render 가능하다.
   - Polygon: contour나 area selection에 유용하지만 pixel treatment 전 renderer
     adapter가 필요하다.
   - Polyline: text line, stroke, path 표현에는 유용하지만 그 자체로 filled area는
     아니다.
   - Raster mask metadata: mask byte나 storage dependency를 core model에 강제하지
     않고 external mask reference를 담는다.

7. Treatment actions
   - `ALLOW`: audit-only path.
   - `MOSAIC`: action intent와 `mosaicBlockSize`; renderer-specific.
   - `BLUR`: action intent와 `blurRadius`, optional sigma; renderer-specific.
   - `SOLID_MASK`: privacy derivative rectangle redaction과 직접 잘 맞는다.
   - `DROP`: 자동 derivative/feed suppression.
   - `REJECT`: request 또는 asset rejection.
   - `QUARANTINE`: unknown 또는 unmatched detection에 대한 fail-closed hold.
   - `MANUAL_REVIEW`: priority가 있는 human decision queue.

8. Example walkthrough
   - `./gradlew :basic-processing:runSensitiveWorkflow`를 실행한다.
   - Deterministic fixture detector를 설명한다.
   - `06-sensitive-moderation-report.txt`의 action matrix를 보여준다.
   - 현재 core derivative가 rectangle action만 render하는 이유를 설명한다.

9. Real detector adapter path
   - Adapter는 기존 detector 또는 sensitive detection contract 뒤에 둔다.
   - Raw backend label과 model metadata를 보존한다.
   - Backend label을 stable category와 severity로 mapping하는 책임은 renderer 밖에
     둔다.
   - Model weight, runtime selection, GPU/native library, licensing review는
     adapter 또는 application module에 둔다.

10. Operational caveats
   - 완벽한 model은 없다.
   - Policy version, detector identity, threshold set, matched rule을 log로 남긴다.
   - False positive와 false negative를 분리해서 추적한다.
   - Quarantine queue와 manual review queue를 관측 가능하게 만든다.
   - Generated derivative를 source image가 안전하다는 증거로 취급하지 않는다.

## 제안 Code Snippet

- `SensitiveContentDetection`을 반환하는 deterministic fake detector output.
- Rule 예제를 포함한 `SensitiveModerationPolicy.failClosed(...)`.
- Rectangle policy decision을 `PrivacyRedaction`으로 mapping하는 예.
- Example task를 실행하고 generated file을 확인하는 흐름.

## 비목표

- Production moderation model 추천.
- ONNX/OpenCV/TensorFlow/PyTorch/runtime asset bundling.
- Deterministic fixture가 semantic moderation accuracy를 증명한다고 주장하기.
- Core image module에 storage, review queue, moderation dashboard 구현하기.
