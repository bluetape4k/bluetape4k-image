# Issue #2 Detector 경계

## 배경

0.4.0 image intelligence lane은 production ML runtime을 core `images` artifact로 끌어오지
않는 face/object detector contract가 필요했다.

## 결정

core module에 runtime-free `io.bluetape4k.images.detection` 경계를 정의하고, detector
region에는 기존 sensitive-content region geometry를 재사용한다. runtime-specific adapter,
model file, GPU support, native library 선택은 이 issue 밖에 둔다.

## 결과

caller는 이제 `ImageDetector` 뒤에 deterministic fake detector, native adapter, remote
service adapter, 또는 이후 ML runtime module을 구현할 수 있다. Core entry point는 sync와
suspend `ImmutableImage` 사용, deterministic confidence/category/label filtering을
제공한다.

이 issue에는 exploratory test evidence용 소규모 internet-derived sample corpus도 추가됐다.
corpus는 runtime-free 상태를 유지한다. decoding, dimensions, dominant colors, blur score,
EXIF presence, checksum, manifest-backed detector-boundary category를 검증하지만
semantic ML extraction을 주장하지 않는다. README preview image는 같은 manifest에서
생성하므로 reviewer는 fixture annotation을 production model output으로 오해하지 않고
현재 face/person, object/text, landmark/object, text annotation을 볼 수 있다.

## 검증

- `:bluetape4k-images:compileTestKotlin`
- `:bluetape4k-images:test --tests io.bluetape4k.images.detection.ImageDetectionTest`
- `:bluetape4k-images:test --tests io.bluetape4k.images.detection.ImageDetectionSampleCorpusTest`
- `:bluetape4k-images:test` (610 passing, 18 pending)
- `docs/scripts/generate-detection-sample-overlays.py`
- `CodeGraph detect_changes` (risk 0.00, affected flows 0, test gaps 0)
- `git diff --check`

## 향후 지침

향후 production detector 작업은 이 경계를 먼저 구현하고, model packaging, license review,
native dependency 결정은 별도 module 또는 application-level adapter에 둔다.

external sample fixture를 추가할 때는 source page, license note, attribution, checksum,
deterministic test evidence와 실제 semantic extraction의 경계를 repo manifest와 wiki
research note에 동기화한다.

README detection preview를 갱신할 때는 bounding box를 손으로 수정하지 말고
`docs/scripts/generate-detection-sample-overlays.py`에서 overlay/contact-sheet PNG를
재생성한다.
