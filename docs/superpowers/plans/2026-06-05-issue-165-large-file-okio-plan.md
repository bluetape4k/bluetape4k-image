# Issue #165 계획 - Large-file Okio IO APIs

## 범위

`docs/superpowers/specs/2026-06-05-issue-165-large-file-okio-design.md`에서 선택한
설계를 구현한다.

핵심 제약은 `bluetape4k-okio`를 적극적으로 사용하는 것이다.

- `io.bluetape4k.okio.buffered`를 import하고 재사용한다.
- `io.bluetape4k.okio.coroutines.buffered`를 import하고 재사용한다.
- `io.bluetape4k.okio.coroutines.asBlocking`을 import하고 재사용한다.
- `InputStream.asSource()`, `OutputStream.asSink()`로 test한다.
- suspended IO는 `AsynchronousFileChannel.asSuspendedSource()`와 `asSuspendedSink()`로
  test한다.

## 작업

| Task | Description | Files |
|---|---|---|
| T1 | vips API module에 명시적 `bluetape4k-okio` API dependency 추가 | `images-vips-api/build.gradle.kts` |
| T2 | binding-neutral vips Okio write extension 추가 | `images-vips-api/src/main/kotlin/io/bluetape4k/images/vips/VipsImageOkioSupport.kt`, `.../coroutines/SuspendVipsOkioOps.kt` |
| T3 | Java 21 vips Okio 및 suspended source load overload 추가 | `images-vips-java21/src/main/kotlin/io/bluetape4k/images/vips/java21/JVipsImageSupport.kt` |
| T4 | Java 25 FFM vips Okio 및 suspended source load overload 추가 | `images-vips-java25/src/main/kotlin/io/bluetape4k/images/vips/java25/FfmVipsImageSupport.kt` |
| T5 | Scrimage Okio API용 lifecycle 및 generated-large-fixture test 추가 | `images/src/test/kotlin/io/bluetape4k/images/ImmutableImageSupportTest.kt` |
| T6 | sink ownership과 flush behavior용 vips API fake test 추가 | `images-vips-api/src/test/kotlin/io/bluetape4k/images/vips/VipsImageOkioSupportTest.kt` |
| T7 | Java 21과 Java 25 backend Okio source test 추가 | existing backend test files or new focused tests |
| T8 | README locale set에 large-file Okio guidance와 #166 benchmark link 추가 | `README.md`, `README.ko.md`, module READMEs if source names require |
| T9 | lesson과 PR body DoD evidence 추가 | `docs/lessons/...`, PR body |

## 구현 규칙

- 새 third-party dependency를 추가하지 않는다.
- 기존 `ByteArray`, `Path`, `InputStream`, `OutputStream` behavior를 바꾸지 않는다.
- 당시 계획 기준으로 public KDoc과 GitHub artifact는 English로 둔다.
- buffered source/sink는 caller-owned이며 helper가 close하지 않는다.
- raw source/sink는 helper가 buffering하고 close한다.
- suspended source/sink bridge는 suspend code 주변에서 `runCatching`을 사용하지 않는다.
- `CancellationException` propagation을 보존한다.
- vips non-Path source load는 bounded compressed-byte load로 유지한다. 이를 native streaming
  decode가 아니라 integration API로 문서화한다.

## 검증

다음 순서로 실행한다.

1. `./gradlew :bluetape4k-images:compileKotlin :bluetape4k-images:compileTestKotlin --console=plain`
2. `./gradlew :bluetape4k-images:test --tests "io.bluetape4k.images.ImmutableImageSupportTest" --console=plain`
3. `./gradlew :bluetape4k-images-vips-api:compileKotlin :bluetape4k-images-vips-api:compileTestKotlin --console=plain`
4. `./gradlew :bluetape4k-images-vips-api:test --console=plain`
5. `./gradlew :bluetape4k-images-vips-java21:compileKotlin :bluetape4k-images-vips-java21:compileTestKotlin --console=plain`
6. `./gradlew :bluetape4k-images-vips-java25:compileKotlin :bluetape4k-images-vips-java25:compileTestKotlin --console=plain`
7. libvips/runtime availability가 허용할 때만 backend test를 실행한다.
   - `./gradlew :bluetape4k-images-vips-java21:test --tests "*Vips*Okio*" --console=plain`
   - `./gradlew :bluetape4k-images-vips-java25:test --tests "*Vips*Okio*" --console=plain`
8. `git diff --check`
9. P0=0, P1=0인 Step 6-R local 7-tier code review.

## Step 3-R Review

| Tier | Findings | Verdict |
|---|---|---|
| Security | 새 auth/trust boundary는 없다. 기존 `InputStream` delegate를 통해 vips format allowlist와 max input limit를 유지한다. | P0=0/P1=0 |
| Ops/SRE | ownership/close behavior가 명시적이고 test 가능하다. JNI/FFM test는 계속 serial하게 실행한다. | P0=0/P1=0 |
| Structural | Okio public type에는 명시적 vips API dependency가 필요하다. Backend overload는 backend module에 유지한다. | P0=0/P1=0 |
| Kotlin/API | extension function은 `VipsImage` interface ABI 변경을 피한다. | P0=0/P1=0 |
| Tests | fake vips image와 backend smoke test가 새 API surface를 다룬다. | P0=0/P1=0 |
| Performance | README는 Scrimage allocation win을 주장하지 않아야 한다. local large file에는 vips Path가 계속 권장 경로다. | P0=0/P1=0 |
| Docs/Evidence | PR 전 README locale set과 #166 benchmark link가 필요하다. | P0=0/P1=0 |
