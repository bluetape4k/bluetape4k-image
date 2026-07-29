# 교훈 - Issue 165 대용량 파일 Okio IO (2026-06-05)

관련 이슈: #165
영향 모듈: `images`, `images-vips-api`, `images-vips-java21`, `images-vips-java25`

## L1: Okio는 대용량 이미지의 성능 만능 경로가 아니라 ownership 경계다

### 문제

대용량 파일 요구는 쉽게 "streaming이면 메모리가 줄어든다"로 오해될 수 있다. 하지만
기존 benchmark 근거는 scrimage에서 Okio `Source`/`Sink`가 Path/InputStream보다
latency나 managed allocation 이점이 없고, libvips Java 25 FFM에서는 로컬 파일 `Path`
경로가 가장 강하다는 점을 보여준다.

### 결정

Okio API를 추가하되, README와 KDoc에서는 다음처럼 분리했다.

- 로컬 대용량 파일: libvips backend의 `Path` 진입점 우선
- upload/object storage/pipe/asynchronous channel: `bluetape4k-okio` `Source`/`Sink`
  또는 `SuspendedSource`/`SuspendedSink` 사용
- scrimage: decoded pixel allocation은 여전히 JVM image memory가 지배하므로
  Okio를 성능 최적화가 아니라 lifecycle/integration 기능으로 설명

### 결과

새 vips Okio overload는 기존 `InputStream` 경로로 위임해 50 MB 제한, 허용 형식 목록,
maxPixels 검증을 우회하지 않는다. Sink write API는 호출자가 소유한 `BufferedSink`는
flush만 하고 raw `Sink`/`SuspendedSink`는 helper가 소유해 닫도록 테스트했다.

### 검증

- `:bluetape4k-images:test --tests "io.bluetape4k.images.ImmutableImageSupportTest"` PASS, 16 tests
- `:bluetape4k-images-vips-api:test --tests "io.bluetape4k.images.vips.VipsImageOkioSupportTest"` PASS, 4 tests
- `:bluetape4k-images-vips-java25:test --tests "io.bluetape4k.images.vips.java25.FfmVipsImageTest"` PASS, 23 tests
- `git diff --check` PASS

### 다음 작업자 주의

대용량 이미지 문서나 API를 추가할 때는 benchmark 근거 없이 "streaming으로 메모리 절감"을
단정하지 말고, 압축 입력 staging과 decoded pixel/native memory를 분리해서 설명한다.

## L2: 워크플로 gate는 단계별 Action/DoD를 먼저 고정해야 한다

### 문제

Step 6-R에 들어가면서 실제 review는 진행했지만, 워크플로 지침의 "Tier 1-7 plus
integration plan items"를 처음부터 명시적으로 세우지 않아 gatekeeper 진행 증거가 흐려졌다.

### 결정

Step 6-R을 다시 정렬하고 각 tier마다 Action, DoD, 근거, P0/P1/P2/P3를 보고했다.
통합 아티팩트도 `.omx/artifacts/current-session-code-review-issue-165-large-file-okio-20260605.md`
로 남겼다.

### 다음 작업자 주의

Full-feature 워크플로에서는 구현 결과가 좋아도 gatekeeper 형식 자체가 산출물이다.
Step 6-R 진입 시 먼저 8개 항목을 plan에 올리고, 각 tier가 닫힐 때마다 DoD 증거를 읽은 뒤
다음 tier로 넘어간다.
