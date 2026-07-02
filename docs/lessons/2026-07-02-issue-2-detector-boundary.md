# Issue #2 Detector Boundary

## Context

The 0.4.0 image intelligence lane needed face/object detector contracts without
pulling production ML runtimes into the core `images` artifact.

## Decision

Define a runtime-free `io.bluetape4k.images.detection` boundary in the core
module and reuse the existing sensitive-content region geometry for detector
regions. Keep runtime-specific adapters, model files, GPU support, and native
library choices outside this issue.

## Outcome

Callers can now implement deterministic fake detectors, native adapters, remote
service adapters, or later ML runtime modules behind `ImageDetector`. Core
entry points provide sync and suspend `ImmutableImage` usage plus deterministic
confidence/category/label filtering.

The issue also gained a small internet-derived sample corpus for exploratory
test evidence. The corpus stays runtime-free: it validates decoding, dimensions,
dominant colors, blur score, EXIF presence, checksums, and manifest-backed
detector-boundary categories, but it does not claim semantic ML extraction.
The README preview image is generated from the same manifest so reviewers can
see the current face/person, object/text, landmark/object, and text annotations
without confusing the fixture annotations for production model output.

## Verification

- `:bluetape4k-images:compileTestKotlin`
- `:bluetape4k-images:test --tests io.bluetape4k.images.detection.ImageDetectionTest`
- `:bluetape4k-images:test --tests io.bluetape4k.images.detection.ImageDetectionSampleCorpusTest`
- `:bluetape4k-images:test` (610 passing, 18 pending)
- `docs/scripts/generate-detection-sample-overlays.py`
- `CodeGraph detect_changes` (risk 0.00, affected flows 0, test gaps 0)
- `git diff --check`

## Future Guidance

Future production detector work should implement this boundary first and keep
model packaging, license review, and native dependency decisions in a separate
module or application-level adapter.

When external sample fixtures are added, keep the repo manifest and wiki
research note in sync with source pages, license notes, attribution, checksums,
and the boundary between deterministic test evidence and real semantic
extraction.

When README detection previews are updated, regenerate the overlay/contact-sheet
PNG from `docs/scripts/generate-detection-sample-overlays.py` instead of editing
bounding boxes by hand.
