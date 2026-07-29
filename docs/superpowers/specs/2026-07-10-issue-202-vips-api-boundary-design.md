# Issue #202 설계 — Binding-neutral Vips API 분리

## 배경

`bluetape4k-images-vips-api`는 binding-neutral libvips contract를 약속하지만, main artifact가
`api(project(":bluetape4k-images"))`를 선언한다. 해당 project는 Scrimage와 TwelveMonkeys dependency를
export하므로, `VipsImage`와 `VipsRuntime`만 사용하는 consumer도 public compile boundary에서
Scrimage/Java2D stack을 받는다.

현재 source inspection에 따르면 main Vips API source는 `bluetape4k-images`에서
`io.bluetape4k.images.IncubatingImageApi`만 import한다. test fixture는 별도로 `VipsGoldenAssert`를 통한
pixel comparison에 `bluetape4k-images`를 사용한다.

## 목표

1. main Vips API artifact의 public dependency graph에서 `bluetape4k-images`를 제거한다.
2. Vips API consumer에 대해 AVIF/HEIC capability marker를 explicit하고 opt-in guarded하게 유지한다.
3. Scrimage를 main-artifact dependency로 publish하지 않으면서 Vips test-fixture pixel-comparison support를 보존한다.
4. generated Maven POM을 의도한 boundary의 release evidence로 삼는다.

## 비목표

- shared module이나 새 external dependency를 추가하지 않는다.
- libvips JNI/FFM runtime behavior, codec detection, image encode/decode semantics를 변경하지 않는다.
- `bluetape4k-images`에서 `IncubatingImageApi`를 제거하거나 rename하지 않는다. 이는 Scrimage-family API의
  opt-in marker로 남는다.
- 이 issue에서 artifact를 publish/release하지 않는다.

## 검증된 제약

- Gradle은 public annotation type을 API dependency로 요구한다. 기존 project dependency를 `implementation`으로
  바꾸면 ABI boundary를 고치는 대신 Vips public annotation에 쓰이는 type을 consumer에게 숨기게 된다.
- `images-vips-api`에는 `generatePomFileForBluetapeImagePublication` task가 있다.
- `bluetape4k-images`에 대한 유일한 main-source dependency는 기존 image incubating annotation이다.
  test-fixture support는 별도 dependency scope다.
- 기존 Vips API README에는 English/Korean variant가 있으며, 이 user-visible migration에서 sync 상태를 유지해야 한다.

## 검토한 접근법

### A. `bluetape4k-images`를 `implementation`으로 재분류

거부한다. `IncubatingImageApi`는 Vips API declaration에 붙는 public annotation type이다. 이를 consumer에게
숨기면 public contract가 불완전해지고 API가 진정으로 binding-neutral해지지 않는다.

### B. Vips-owned opt-in annotation을 추가하고 main project dependency 제거

선택한다. `io.bluetape4k.images.vips` 아래에 `VipsIncubatingApi`를 추가하고 Vips-only capability
declaration을 이 marker로 migrate한 뒤 main `api(project(":bluetape4k-images"))` dependency를 제거한다.
test-fixture project dependency는 main publication의 일부가 아니므로 유지한다.

이 방식은 consumer에게 explicit한 Vips-scoped opt-in surface를 제공하고, 기존 image-module annotation을
독립적으로 유지한다.

### C. 새 shared image-contract module 도입

거부한다. opt-in marker 하나를 공유하기 위해 module을 추가하면 이 issue가 다루는 boundary를 개선하지 않으면서
settings, BOM, CI, Nightly, coverage, consumer migration scope만 넓힌다.

## 선택한 설계

### Public contract

Vips API package에 `@RequiresOptIn(Level.WARNING)`, `@MustBeDocumented`, binary-retained annotation인
`VipsIncubatingApi`를 만든다. 기존 marker의 supported target과 정확히 맞춘다. 대상은 `CLASS`,
`ANNOTATION_CLASS`, `FUNCTION`, `PROPERTY`, `PROPERTY_GETTER`, `PROPERTY_SETTER`,
`CONSTRUCTOR`, `TYPEALIAS`다. public KDoc과 compiler message는 한국어로 작성하며, Vips codec
capability API가 binary-compatibility guarantee 없이 변경될 수 있음을 설명하고 정확한
`@OptIn(VipsIncubatingApi::class)` migration path를 제시한다.

main/test source set을 포함해 Vips API와 Java 21/25 backend consumer 전반의 `IncubatingImageApi` 사용을
교체한다.

- `VipsImageFormat.AVIF`와 `VipsImageFormat.HEIC`에 `@VipsIncubatingApi`를 적용한다. 이들은 caller
  opt-in이 필요한 public declaration이다.
- `VipsCodecCapability`와 `VipsCodecCapabilityReport`의 기존 implementation-only
  `@OptIn(IncubatingImageApi::class)`를 `@OptIn(VipsIncubatingApi::class)`로 교체한다.
  해당 report container type에는 `@VipsIncubatingApi`를 적용하지 않는다. caller가 불필요한 새 opt-in
  contract를 받아들이지 않고도 stable codec report data를 inspect할 수 있어야 하기 때문이다.
- 해당 Vips capability type을 사용하는 모든 `images-vips-api`와 Java 21/25 backend main-source/test
  `@OptIn` declaration을 교체한다. backend module은 `bluetape4k-images`가 아니라 `images-vips-api`에
  의존하므로, old import를 유지하면 main boundary 제거 뒤 compilation이 깨진다.

`IncubatingImageApi`는 `bluetape4k-images`에서 변경하지 않는다. Vips AVIF/HEIC 기능에 명시적으로
opt-in한 existing caller는 opt-in import를 `VipsIncubatingApi`로 바꿔야 한다. 이는 이미 incubating인 API에
대한 intentional source migration이며 두 Vips API README에 문서화해야 한다.

### Dependency boundary

`images-vips-api`의 main dependency에서 `api(project(":bluetape4k-images"))`를 제거한다.
`VipsGoldenAssert`용 `testFixturesApi(project(":bluetape4k-images"))`는 유지한다. fixture-only dependency는
main library POM contract를 형성하면 안 되기 때문이다.

migration 후 어떤 Vips API 또는 backend main/test source도 `io.bluetape4k.images.IncubatingImageApi`를
import하면 안 된다. 기존 `bluetape4k-core`, IO, Okio, coroutine dependency는 intentional Vips API
dependency로 남는다.

### 문서화와 migration

`images-vips-api/README.md`와 `README.ko.md`를 다음과 같이 업데이트한다.

- main Vips API artifact가 Scrimage image implementation artifact를 요구하지 않는다고 명시한다.
- capability와 smoke snippet을 포함한 모든 AVIF/HEIC example을 새 `VipsIncubatingApi` import와 scoped
  `@OptIn(VipsIncubatingApi::class)`로 업데이트한다.
- main-artifact dependency와 test-fixture support를 구분한다.
- native libvips codec availability caveat는 변경하지 않는다.

새 public annotation에는 한국어 KDoc을 추가하고 관련 Vips KDoc reference를 정확한 이름으로 업데이트한다.

`images` module의 `AvifWriter`, `HeicReader` KDoc에 있는 Vips-specific snippet은 contract-only example 또는
prose로 교체한다. 해당 interface는 계속 `IncubatingImageApi`로 guard되지만, `images` module은 Vips example을
문서화하려고 Vips-owned marker를 import하거나 reverse dependency를 얻으면 안 된다.

## 검증 설계

1. `:bluetape4k-images-vips-api:test`를 compile/run한다.
2. 두 backend consumer의 main/test source set을 compile한다.
   (`:bluetape4k-images-vips-java21:compileKotlin`,
   `:bluetape4k-images-vips-java21:compileTestKotlin`,
   `:bluetape4k-images-vips-java25:compileKotlin`, and
   `:bluetape4k-images-vips-java25:compileTestKotlin`). native-runtime test execution 없이 revised public
   contract를 사용함을 증명하기 위해서다.
3. 다음 task로 Vips API publication POM과 Gradle Module Metadata를 생성한다.
   `:bluetape4k-images-vips-api:generatePomFileForBluetapeImagePublication`
   및 `:bluetape4k-images-vips-api:generateMetadataFileForBluetapeImagePublication`.
4. generated main POM과 Gradle Module Metadata의 normal `apiElements`/`runtimeElements` variant가
   `bluetape4k-images`, Scrimage, TwelveMonkeys에 의존하지 않음을 assert한다. intentional API dependency
   allow-list는 유지한다. metadata assertion은 intentionally dependent test-fixtures variant를 normal consumer
   variant와 구분해야 한다.
5. 새 dependency 없이 dedicated Kotlin compiler fixture를 추가한다. warning을 error로 승격한 상태에서 unopted
   AVIF/HEIC 사용을 compile하고 expected failure가 `VipsIncubatingApi`를 언급하는지 assert한다. 같은 strict
   setting에서 opted counterpart compile success를 assert한다. stable report container에 marker를 강제하지 않고
   capability model behavior test를 유지한다.
6. English/Korean README와 영향을 받는 `images` KDoc claim을 generated publication metadata와 current source name에
   맞춰 검증한 뒤 `git diff --check`를 실행한다.

## 위험과 완화

| 위험 | 완화 |
|---|---|
| boundary 변경 후 consumer가 Vips AVIF/HEIC code를 compile할 수 없음 | 정확한 Vips opt-in migration을 문서화하고 두 backend module을 compile한다. |
| fixture dependency가 normal consumer variant로 누수됨 | generated main POM과 Gradle Module Metadata를 inspect하고 fixture variant를 `apiElements`/`runtimeElements`와 구분한다. |
| public annotation migration이 실수로 Scrimage API까지 넓어짐 | marker 변경을 `images-vips-api`와 Java 21/25 backend의 Vips capability opt-in으로 제한하고, `IncubatingImageApi` definition과 image API contract는 `images`에 유지한다. |
| refactor 중 runtime codec behavior가 변경됨 | backend 변경을 opt-in annotation import로 제한하고 기존 codec test와 targeted Vips API test를 유지한다. |
| publication metadata가 local resolution과 다른 configuration을 사용함 | 실제 `BluetapeImage` POM과 Gradle Module Metadata generation task를 acceptance evidence로 사용한다. |

## 인수 기준

- `bluetape4k-images-vips-api` main publication이 `bluetape4k-images`, Scrimage, TwelveMonkeys를 노출하지 않는다.
- Vips AVIF/HEIC capability API가 public Vips-scoped marker로 명시적인 opt-in 상태를 유지한다.
- stable codec report container는 Vips opt-in 없이 계속 사용할 수 있다. AVIF/HEIC enum entry만 caller에게 marker를 전파한다.
- test-fixture support는 main artifact boundary를 바꾸지 않고 계속 사용할 수 있다.
- 두 backend module이 revised API를 기준으로 compile된다.
- README/KDoc이 dependency와 migration boundary를 정확히 설명한다.

## Rollback

publication 전에는 feature branch를 revert하면 기존 POM, Gradle Module Metadata, annotation contract가 복원된다.
generated-POM validation 또는 normal Gradle metadata variant가 설명되지 않은 public-type이나 forbidden dependency를
드러내면 PR 생성 전 중단하고 evidence를 보존하며, 넓은 `images` API dependency를 기본으로 다시 도입하지 말고
spec/plan을 수정한다.
