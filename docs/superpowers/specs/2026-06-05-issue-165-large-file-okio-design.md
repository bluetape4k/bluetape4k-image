# Issue #165 설계 — 대용량 파일 Okio IO API

## 배경

Issue #165는 Okio를 사용하는 메모리 의식형 대용량 이미지 IO API를 요구한다. 출발 증거는
`kotlinx-benchmark` 기반 대용량 스트리밍 벤치마크를 추가한 PR #167 / issue #166이다.
이 벤치마크에서는 Scrimage `Path`, `InputStream`, Okio `Source/Sink` 행의 지연 시간과
관리 힙 할당량이 비슷하게 나타난다. Scrimage의 decode/encode가 여전히 디코딩된 이미지
힙을 지배하기 때문이다. 대용량 파일에서 가장 강한 측정 행은 libvips Java 25 FFM `Path`이다.

사용자는 이 작업이 단순히 raw Okio 타입만 받는 수준이 아니라 `bluetape4k-okio`를 적극적으로
사용해야 한다고 명확히 했다. 현재 코드에는 이미
`images/src/main/kotlin/io/bluetape4k/images/ImmutableImageSupport.kt`에 Scrimage
`ImmutableImage`용 `BufferedSource`, `Source`, `BufferedSuspendedSource`,
`SuspendedSource`, `BufferedSink`, `Sink`, `BufferedSuspendedSink`,
`SuspendedSink` overload가 있다.

## 현재 증거

- `images/build.gradle.kts`는 이미 `libs.bluetape4k.okio`를 노출한다.
- Scrimage load/write helper는 이미 다음 항목을 import한다.
  - `io.bluetape4k.okio.buffered`
  - `io.bluetape4k.okio.coroutines.buffered`
  - `io.bluetape4k.okio.coroutines.asBlocking`
  - `SuspendedSource`, `SuspendedSink`, `BufferedSuspendedSource`, `BufferedSuspendedSink`
- `images/src/test/.../ImmutableImageSupportTest.kt`는 기본 Okio load와 suspended
  source/sink 사례를 다루지만, lifecycle/close 의미와 대용량 생성 fixture 동작 검증은 얇다.
- `images-vips-api`는 현재 `VipsImage.writeTo(Path)`, `VipsImage.writeTo(OutputStream)`,
  그리고 coroutine wrapper를 노출한다.
- `images-vips-java21`과 `images-vips-java25`는 `ByteArray`, `File`, `Path`,
  `InputStream` load 함수를 노출한다. stream load는 `VipsLimits.MAX_INPUT_BYTES`로 제한된다.
- `bluetape4k-okio`는 선호되는 생태계 bridge surface를 제공한다.
  - `InputStream.asSource()`, `OutputStream.asSink()`
  - `Source.buffered()`, `Sink.buffered()`
  - `AsynchronousFileChannel.asSuspendedSource()`, `asSuspendedSink()`
  - `SuspendedSource.buffered()`, `SuspendedSink.buffered()`
  - `SuspendedSource.asBlocking()`, `SuspendedSink.asBlocking()`
- Okio upstream 문서는 ownership이 있는 source/sink에 `use {}`를 적용하며, source/sink wrapper가
  stream을 소유한 뒤에는 Okio를 통해 작업하는 방식을 권장한다.

## 요구사항

1. public API 경계에서 `ByteArray` staging을 강제하지 않는 명확한 Okio-first 대용량 파일 경로를 제공한다.
2. `bluetape4k-okio` bridge helper와 suspended source/sink API를 직접 사용한다.
3. Scrimage 문서는 정직하게 유지한다. Okio는 lifecycle과 통합에는 도움이 되지만 Scrimage의
   디코딩된 이미지 힙 할당을 줄인다고 말하지 않는다.
4. Okio sink용 binding-neutral vips write extension을 추가한다.
5. 기존 크기, 형식, 픽셀 안전성 검사를 유지하면서 Okio source와 suspended source용 backend-specific
   vips load overload를 추가한다.
6. resource ownership을 명시한다.
   - `BufferedSource` / `BufferedSink` / buffered suspended variant는 호출자 소유이며 helper가 닫지 않는다.
   - raw `Source` / `Sink` / suspended variant는 helper가 buffering하고 닫는다.
7. README와 KDoc은 측정된 대용량 로컬 파일 성능에서 vips `Path`가 선호되는 시점을 명시해야 한다.

## 설계 선택지

### 선택지 A — 기존 Scrimage Okio API만 문서화

이는 #165에 비해 범위가 너무 작다. vips 사용자에게 대용량 파일 Okio surface를 제공하지 못하고,
벤치마크 증거와 API 선택의 연결도 남겨둔다.

### 선택지 B — raw Okio overload만 추가

이 방식은 `okio.Source`와 `okio.Sink`를 사용하지만 `bluetape4k-okio`를 충분히 활용하지 않는다.
또한 coroutine file-channel 경로를 각 호출자에게 맡기며, bluetape4k의 나머지 영역과의 일관성을 약화한다.

### 선택지 C — `bluetape4k-okio`를 public IO bridge로 사용

`bluetape4k-okio` adapter를 사용해 vips Okio/suspended overload를 추가하고, 기존 overload 주변의
Scrimage test/docs를 강화한다. 이렇게 하면 `images`와 `images-vips-*` 전반에서 dependency와
ownership 의미가 일관되게 유지된다.

선택: 선택지 C.

## API 형태

### Scrimage `images`

기존 public function을 유지하고 집중된 tests/docs를 추가한다.

- `immutableImageOf(source: BufferedSource)`
- `immutableImageOf(source: Source)`
- `suspendLoadImage(source: BufferedSuspendedSource)`
- `suspendLoadImage(source: SuspendedSource)`
- `ImmutableImage.suspendWrite(writer, sink: BufferedSink)`
- `ImmutableImage.suspendWrite(writer, sink: Sink)`
- `ImmutableImage.suspendWrite(writer, sink: BufferedSuspendedSink)`
- `ImmutableImage.suspendWrite(writer, sink: SuspendedSink)`

### vips API module

`images-vips-api`에 binding-neutral extension function을 추가한다.

- `VipsImage.writeTo(sink: BufferedSink, format, options)`
- `VipsImage.writeTo(sink: Sink, format, options)`
- `VipsImage.suspendWriteTo(sink: BufferedSink, format, options)`
- `VipsImage.suspendWriteTo(sink: Sink, format, options)`
- `VipsImage.suspendWriteTo(sink: BufferedSuspendedSink, format, options)`
- `VipsImage.suspendWriteTo(sink: SuspendedSink, format, options)`

이 extension은 기존 `VipsImage.writeTo(OutputStream)`을 통해 encode하고 sink를 flush한다. 현재 vips
구현이 내부에서 여전히 `toBytes()`를 호출하므로, 이는 native streaming encoder 보장이 아니다.

### vips backend module

backend-specific load overload를 추가한다.

- Java 21:
  - `vipsImageOf(source: BufferedSource)`
  - `vipsImageOf(source: Source)`
  - `suspendVipsImageOf(source: BufferedSource)`
  - `suspendVipsImageOf(source: Source)`
  - `suspendVipsImageOf(source: BufferedSuspendedSource)`
  - `suspendVipsImageOf(source: SuspendedSource)`
- Java 25:
  - same shape under `ffmVipsImageOf` / `suspendFfmVipsImageOf`

모든 variant는 기존 bounded `InputStream` decode path 또는 suspended source용 기존 blocking bridge로
위임한다. 따라서 기존 `MAX_INPUT_BYTES`와 format allowlist 동작은 그대로 유지된다.

## 테스트 전략

- `images`: 생성된 더 큰 fixture와 명시적인 caller-owned/helper-owned close/flush 동작을
  `ImmutableImageSupportTest`에 추가한다.
- `images-vips-api`: Okio sink extension용 fake `VipsImage` test를 추가하고 flush와 close ownership을
  포함한다.
- `images-vips-java21/java25`: 기존 runtime availability base class로 gate되는 Okio source overload
  backend test를 추가한다. JNI/FFM test는 기존 Gradle 구성에 따라 serial로 유지한다.
- 모든 touch module에 compile check를 추가하고 targeted test를 실행한다. Java 25 check는 기존 Gradle 구성을
  통해 `--enable-native-access`를 포함해야 한다.

## 위험

1. public 문구가 Scrimage에 대해 진정한 bounded-memory transform을 암시할 수 있다. #166 allocation
   evidence에 연결된 README/KDoc 표현으로 완화한다.
2. vips `Source` load는 non-path source에서 backend API가 bytes에서 decode하기 때문에 현재 compressed input
   bytes를 buffering한다. 로컬 대용량 파일에는 `Path`를 권장하고 stream 경계를 integration API로 문서화해 완화한다.
3. suspended source/sink bridge가 `runCatching`으로 구현되면 cancellation을 숨길 수 있다. 직접적인 `try/finally`
   cleanup을 사용하고 넓은 cancellation swallowing을 피해서 완화한다.
4. `images-vips-api`에 Okio 타입을 추가하면 dependency surface가 영향을 받는다. public API가 Okio 타입을 포함하므로
   명시적인 `api(libs.bluetape4k.okio)`로 완화한다.

## 인수 기준 매핑

| 이슈 기준 | 설계 응답 |
|---|---|
| `ByteArray` 경계 없는 명확한 대용량 파일 경로 | Scrimage/vips Okio source/sink API와 `Path` 권장 |
| Okio API 격리 | `images`와 `images-vips-api`만 public Okio를 노출하고, backend module은 concrete load overload를 추가 |
| resource close/failure/cancellation | ownership 분리와 집중 test |
| README/README.ko 제한 설명 | #166 증거가 포함된 대용량 파일 section |
| benchmark evidence link | README와 PR body가 `benchmark/images-benchmark/docs/large-streaming-2026-06-05.md`를 link |
