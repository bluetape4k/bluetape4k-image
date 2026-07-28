# Issue 218 Moderation policy

## 배경

#214는 backend-neutral sensitive-content detection fact를 추가했고, #2는 detector boundary를
추가했다. #218에는 detector inference나 pixel rendering 없이 treatment action을 선택할 수
있는 다음 layer, 즉 policy decision이 필요했다.

## 결정

moderation policy는 `bluetape4k-images` 안의 작은 renderer-neutral model로 유지한다.

- `SensitiveModerationRule` matches category, minimum severity, and minimum confidence.
- `SensitiveTreatmentParameters` carries optional renderer hints such as blur radius, mosaic block size, mask opacity, review priority, and reject reason.
- `SensitiveModerationPolicy.failClosed(...)` quarantines unmatched detections by default.
- Empty detection lists return `ALLOW`; unmatched detections fail closed.

## 결과

policy layer는 auditable report를 만들 수 있고, detector runtime selection, rendering,
persistence, service-side workflow side effect는 follow-up issue 또는 application에 남긴다.

## 검증

- `./gradlew :bluetape4k-images:test --tests 'io.bluetape4k.images.moderation.SensitiveContentPolicyTest'`
- `./gradlew :bluetape4k-images:test`
- `git diff --check`

## 향후 방지책

moderation policy를 확장할 때 `bluetape4k-images`에 ML runtime, model download, pixel
rendering을 추가하지 않는다. follow-up issue가 그 경계를 명시적으로 선택할 때만 adapter
또는 renderer module을 추가한다.
