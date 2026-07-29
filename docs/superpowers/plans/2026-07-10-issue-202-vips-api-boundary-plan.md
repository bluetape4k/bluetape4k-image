# Vips API Dependency Boundary 구현 계획

> **Agentic worker 필수 지침:** 이 계획은 task 단위로 구현한다. 구현 표면은 `superpowers:subagent-driven-development`(권장) 또는 `superpowers:executing-plans`를 사용한다. 진행 추적에는 checkbox(`- [ ]`) 문법을 사용한다.

**목표:** 일반 consumer variant에서 `bluetape4k-images`, Scrimage, TwelveMonkeys를 노출하지 않는 binding-neutral Vips API artifact를 게시한다. AVIF/HEIC 명시 opt-in과 fixture-only pixel comparison support는 유지한다.

**아키텍처:** Vips 소유 binary opt-in marker를 도입하고, AVIF/HEIC enum entry만 caller에게 opt-in을 전파하게 한다. Vips API와 Java 21/25 implementation의 기존 image-marker import를 교체한 뒤 main `images` project dependency를 제거한다. `testFixturesApi`는 image comparison support에만 한정하고, generated Maven POM과 Gradle Module Metadata에서 boundary를 증명한다.

**기술 스택:** Kotlin 2.3, Gradle Kotlin DSL, `java-test-fixtures`, JUnit 5, bluetape4k assertion, Maven Publish.

---

## 파일 구조와 범위

| Path | 책임 |
|---|---|
| `images-vips-api/build.gradle.kts` | Main-vs-fixture dependency boundary와 strict opt-in compiler fixture source set. |
| `images-vips-api/src/main/kotlin/io/bluetape4k/images/vips/VipsIncubatingApi.kt` | Public Vips-specific opt-in marker. |
| `images-vips-api/src/main/kotlin/io/bluetape4k/images/vips/VipsImageFormat.kt` | Caller-visible AVIF/HEIC opt-in propagation. |
| `images-vips-api/src/main/kotlin/io/bluetape4k/images/vips/VipsCodecCapabilityReport.kt` | Stable report container를 experimental로 만들지 않고 AVIF/HEIC를 참조하기 위한 internal permission. |
| `images-vips-api/src/test/kotlin/io/bluetape4k/images/vips/*.kt` | Capability와 stable-report regression test. |
| `images-vips-api/src/{optedVipsOptInFixture,unoptedVipsOptInFixture}/kotlin/...` | Opted success와 unopted expected failure를 위한 strict compiler fixture. |
| `images-vips-java21/src/{main,test}/kotlin/...` | Java 21 backend marker migration. Runtime/native-code 변경 없음. |
| `images-vips-java25/src/{main,test}/kotlin/...` | Java 25 FFM backend marker migration. Runtime/native-code 변경 없음. |
| `README*.md`, `images-vips-{api,java21,java25}/README*.md` | Copy-paste-safe AVIF/HEIC opt-in migration. API README pair는 fixture-only boundary도 문서화한다. |
| `images/src/main/kotlin/io/bluetape4k/images/{avif/AvifWriter.kt,heic/HeicReader.kt}` | Reverse Vips documentation dependency가 없는 contract-only image-module KDoc. |

Module registration, BOM/catalog, CI workflow, dependency upgrade, runtime codec behavior, benchmark source, release, publication configuration은 범위 밖이다. 기존 old-marker direct import가 있는 16개 Vips API/backend file이 전체 migration set이며, 편집 전 다시 count해야 한다.

## Task 1: Public Vips marker 추가와 stable-report contract 잠금

**Complexity:** medium

**Apply:** 이 task의 모든 Kotlin source, KDoc, test change에 `$bluetape4k-code-patterns`를 적용한다.

**Files:**

- Create: `images-vips-api/src/main/kotlin/io/bluetape4k/images/vips/VipsIncubatingApi.kt`
- Modify: `images-vips-api/src/main/kotlin/io/bluetape4k/images/vips/VipsImageFormat.kt`
- Modify: `images-vips-api/src/main/kotlin/io/bluetape4k/images/vips/VipsCodecCapabilityReport.kt`
- Modify: `images-vips-api/src/test/kotlin/io/bluetape4k/images/vips/VipsCodecCapabilityReportTest.kt`
- Create: `images-vips-api/src/test/kotlin/io/bluetape4k/images/vips/VipsStableCodecCapabilityReportTest.kt`

**다시 확인할 current-code assumption:** `VipsImageFormat.AVIF`와 `.HEIC`만 현재 incubating public enum entry이고, `VipsCodecCapability`/`VipsCodecCapabilityReport`는 propagated opt-in declaration이 아니라 internal `@OptIn` user이다.

- [ ] **Step 1: 실패하는 source-level migration test를 작성한다.**

  기존 capability test가 아직 존재하지 않는 `VipsIncubatingApi`를 import하고 class scope에 적용하게 바꾼 뒤, 아래 stable unannotated regression test를 추가한다. Missing import는 marker가 생기기 전 test compilation이 실패하도록 의도한 것이다.

  `VipsCodecCapabilityReportTest.kt`에서 새 test file을 추가하기 전에 아래와 같이 정확히 교체한다.

  ~~~kotlin
  import io.bluetape4k.images.vips.VipsIncubatingApi

  @OptIn(VipsIncubatingApi::class)
  class VipsCodecCapabilityReportTest {
  ~~~

  ~~~kotlin
  package io.bluetape4k.images.vips

  import io.bluetape4k.assertions.shouldBeTrue
  import org.junit.jupiter.api.Test

  class VipsStableCodecCapabilityReportTest {
      @Test
      fun `stable report inspection needs no Vips opt in`() {
          val report = VipsCodecCapabilityReport(
              backendName = "test-backend",
              codecs = emptyList(),
          )

          report.isStableFormat(VipsImageFormat.JPEG).shouldBeTrue()
      }
  }
  ~~~

- [ ] **Step 2: Test source가 의도한 이유로 실패하는지 확인한다.**

  Run: `./gradlew :bluetape4k-images-vips-api:compileTestKotlin --rerun-tasks`

  Expected: `VipsIncubatingApi` unresolved reference 때문에 `FAILURE`가 발생한다. 관련 없는 compiler error를 red result로 수용하지 않는다.

- [ ] **Step 3: Marker를 추가하고 API-side declaration만 migration한다.**

  ~~~~kotlin
  package io.bluetape4k.images.vips

  /**
   * Marks a Vips codec capability API that is still incubating.
   *
   * ## Contract
   * - Marked declarations may change without binary-compatibility guarantees.
   * - Stable Vips report containers remain available without this opt-in.
   *
   * ```kotlin
   * @OptIn(VipsIncubatingApi::class)
   * val format = VipsImageFormat.AVIF
   * ```
   */
  @RequiresOptIn(
      level = RequiresOptIn.Level.WARNING,
      message = "This Vips codec capability API is incubating and may change without binary compatibility guarantees. Use @OptIn(VipsIncubatingApi::class) to acknowledge it.",
  )
  @MustBeDocumented
  @Retention(AnnotationRetention.BINARY)
  @Target(
      AnnotationTarget.CLASS,
      AnnotationTarget.ANNOTATION_CLASS,
      AnnotationTarget.FUNCTION,
      AnnotationTarget.PROPERTY,
      AnnotationTarget.PROPERTY_GETTER,
      AnnotationTarget.PROPERTY_SETTER,
      AnnotationTarget.CONSTRUCTOR,
      AnnotationTarget.TYPEALIAS,
  )
  annotation class VipsIncubatingApi
  ~~~~

  `VipsImageFormat.kt`에서는 old import/KDoc reference를 교체하고 `AVIF`, `HEIC`에만 `@VipsIncubatingApi`를 붙인다. `VipsCodecCapabilityReport.kt`에서는 기존 class-level `@OptIn(IncubatingImageApi::class)` 두 곳을 `@OptIn(VipsIncubatingApi::class)`로 교체한다. Report container class에는 `@VipsIncubatingApi`를 붙이지 않는다. Public signature와 serialization ID는 바꾸지 않는다.

- [ ] **Step 4: API regression test가 통과하는지 검증한다.**

  Run: `./gradlew :bluetape4k-images-vips-api:test --tests '*VipsCodecCapabilityReportTest' --tests '*VipsStableCodecCapabilityReportTest' --rerun-tasks`

  Expected: `BUILD SUCCESSFUL`. Stable-report test는 `@OptIn` 없이 compile되고, capability test는 Vips-owned marker를 사용한다.

- [ ] **Step 5: API contract slice를 commit한다.**

  `git diff --check`를 실행한 뒤 Lore trailer로 commit한다. Intent line: `refactor: own Vips capability opt-in contract`.

**Rollback / rerun point:** 승인된 target set으로 enum entry에 annotation을 적용할 수 없으면 이 commit을 되돌린다. Report container type에 marker를 넓게 붙이는 workaround를 사용하지 않는다.

## Task 2: Strict Kotlin compiler fixture로 opt-in diagnostic 증명

**Complexity:** high

**Apply:** 이 task의 Gradle Kotlin DSL과 Kotlin fixture source에 `$bluetape4k-code-patterns`를 적용한다.

**Files:**

- Modify: `images-vips-api/build.gradle.kts`
- Create: `images-vips-api/src/unoptedVipsOptInFixture/kotlin/io/bluetape4k/images/vips/UnoptedVipsOptInFixture.kt`
- Create: `images-vips-api/src/optedVipsOptInFixture/kotlin/io/bluetape4k/images/vips/OptedVipsOptInFixture.kt`

**다시 확인할 current-code assumption:** Java/Kotlin plugin은 custom Java source set에 대해 `compile<SourceSetName>Kotlin` convention의 Kotlin compile task를 만든다. Fixture 실행 전 `:bluetape4k-images-vips-api:tasks --all`로 generated task name을 확인한다.

- [ ] **Step 1: Unopted fixture source와 strict source-set wiring을 추가한다.**

  ~~~kotlin
  sourceSets {
      val main by getting
      val unoptedVipsOptInFixture by creating {
          compileClasspath += main.output
          runtimeClasspath += main.output
      }
      val optedVipsOptInFixture by creating {
          compileClasspath += main.output
          runtimeClasspath += main.output
      }
  }

  tasks.named<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>("compileUnoptedVipsOptInFixtureKotlin") {
      compilerOptions.allWarningsAsErrors.set(true)
      onlyIf { providers.gradleProperty("verifyVipsOptInFixtures").isPresent }
  }
  tasks.named<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>("compileOptedVipsOptInFixtureKotlin") {
      compilerOptions.allWarningsAsErrors.set(true)
      onlyIf { providers.gradleProperty("verifyVipsOptInFixtures").isPresent }
  }
  ~~~

  Unopted fixture는 다음과 같다.

  ~~~kotlin
  package io.bluetape4k.images.vips

  internal object UnoptedVipsOptInFixture {
      val format: VipsImageFormat = VipsImageFormat.AVIF
  }
  ~~~

- [ ] **Step 2: Strict compilation이 marker diagnostic으로 실패하는지 확인한다.**

  Run: `./gradlew :bluetape4k-images-vips-api:compileUnoptedVipsOptInFixtureKotlin -PverifyVipsOptInFixtures --rerun-tasks --console=plain`

  Expected: warnings-as-errors 때문에 `FAILURE`가 발생하고 diagnostic에 `VipsIncubatingApi`가 표시된다. 이 expected failure를 Step DoD evidence에 기록한다.

- [ ] **Step 3: Opted fixture를 추가하고 strict compilation success를 확인한다.**

  ~~~kotlin
  package io.bluetape4k.images.vips

  @OptIn(VipsIncubatingApi::class)
  internal object OptedVipsOptInFixture {
      val format: VipsImageFormat = VipsImageFormat.AVIF
  }
  ~~~

  Run: `./gradlew :bluetape4k-images-vips-api:compileOptedVipsOptInFixtureKotlin -PverifyVipsOptInFixtures --rerun-tasks --console=plain`

  Expected: opt-in warning이 error로 승격되지 않고 `BUILD SUCCESSFUL`이 나온다.

- [ ] **Step 4: Compiler-fixture slice를 commit한다.**

  `git diff --check`를 실행한 뒤 Lore trailer로 commit한다. Intent line: `test: lock Vips opt-in compiler diagnostics`.

**Rollback / rerun point:** Kotlin이 예상 task name을 만들지 않으면 `tasks --all`에 출력된 이름에 맞춰 task lookup만 조정한다. 두 isolated source set은 유지하고 `kotlin-compile-testing`을 추가하지 않는다.

## Task 3: Main image dependency 제거와 두 backend family migration

**Complexity:** high

**Apply:** 모든 Kotlin import와 opt-in change에 `$bluetape4k-code-patterns`를 적용한다.

**Files:**

- Modify: `images-vips-api/build.gradle.kts`
- Modify: `images-vips-java21/src/main/kotlin/io/bluetape4k/images/vips/java21/{JVipsImage.kt,JVipsImageSupport.kt,JVipsRuntime.kt,internal/JVipsFormatSupport.kt}`
- Modify: `images-vips-java21/src/test/kotlin/io/bluetape4k/images/vips/java21/{JVipsCodecCapabilityTest.kt,JVipsImageTest.kt}`
- Modify: `images-vips-java25/src/main/kotlin/io/bluetape4k/images/vips/java25/{FfmVipsImage.kt,FfmVipsImageSupport.kt,FfmVipsRuntime.kt,internal/FfmVipsFormatSupport.kt,writer/FfmVipsHeifWriter.kt}`
- Modify: `images-vips-java25/src/test/kotlin/io/bluetape4k/images/vips/java25/{FfmVipsCodecCapabilityTest.kt,FfmVipsImageTest.kt}`

**다시 확인할 current-code assumption:** `rg -l 'IncubatingImageApi' images-vips-api images-vips-java21 images-vips-java25 --glob '*.kt'`는 design에 설명된 16개 migration file만 반환한다. `testFixturesApi(project(":bluetape4k-images"))`는 `VipsGoldenAssert`를 위해 계속 필요하다.

- [ ] **Step 1: Main API dependency만 제거하고 expected backend failure를 캡처한다.**

  아래 dependency와 stale comment를 제거하되 `testFixturesApi`는 유지한다.

  ~~~kotlin
  api(project(":bluetape4k-images"))
  ~~~

  Run: `./gradlew :bluetape4k-images-vips-java21:compileKotlin :bluetape4k-images-vips-java25:compileKotlin --rerun-tasks`

  Expected: 두 backend family에서 unresolved `IncubatingImageApi` import로 `FAILURE`가 발생한다. 이는 migration이 removed public boundary를 실제로 검증하고 있음을 보여 주며 accidental transitive dependency를 피한다.

- [ ] **Step 2: Vips scope의 모든 direct old-marker import를 migration한다.**

  위 파일 전체에서 아래를 교체한다.

  ~~~kotlin
  import io.bluetape4k.images.IncubatingImageApi
  @OptIn(IncubatingImageApi::class)
  ~~~

  다음으로 바꾼다.

  ~~~kotlin
  import io.bluetape4k.images.vips.VipsIncubatingApi
  @OptIn(VipsIncubatingApi::class)
  ~~~

  JNI `NativeHandle` ownership, Java 25 `Arena` lifecycle, runtime initialization, codec detection, exception behavior, test input은 변경하지 않는다.

- [ ] **Step 3: Migration completeness와 backend compilation을 검증한다.**

  Run: `rg -n 'IncubatingImageApi' images-vips-api images-vips-java21 images-vips-java25 --glob '*.kt'`

  Expected: match가 없어야 한다.

  Run: `./gradlew :bluetape4k-images-vips-api:test :bluetape4k-images-vips-java21:compileKotlin :bluetape4k-images-vips-java21:compileTestKotlin :bluetape4k-images-vips-java25:compileKotlin :bluetape4k-images-vips-java25:compileTestKotlin --rerun-tasks`

  Expected: `BUILD SUCCESSFUL`. 네 backend compile task는 JNI/FFM native test execution을 요구하지 않는다.

- [ ] **Step 4: Dependency-boundary slice를 commit한다.**

  `git diff --check`를 실행한 뒤 Lore trailer로 commit한다. Intent line: `refactor: decouple Vips API from image implementation`.

**Rollback / rerun point:** Backend가 marker 외의 `bluetape4k-images` type을 필요로 하면 plan/spec를 수정한다. 넓은 main `api(project(":bluetape4k-images"))` dependency를 기본 workaround로 되돌리지 않는다.

## Task 4: Publication variant 검증과 caller migration 문서화

**Complexity:** medium

**Apply:** KDoc example과 public API name을 해석할 때 `$bluetape4k-code-patterns`를 적용한다.

**Files:**

- Modify: `README.md`
- Modify: `README.ko.md`
- Modify: `images-vips-api/README.md`
- Modify: `images-vips-api/README.ko.md`
- Modify: `images-vips-java21/README.md`
- Modify: `images-vips-java21/README.ko.md`
- Modify: `images-vips-java25/README.md`
- Modify: `images-vips-java25/README.ko.md`
- Modify: `images/src/main/kotlin/io/bluetape4k/images/avif/AvifWriter.kt`
- Modify: `images/src/main/kotlin/io/bluetape4k/images/heic/HeicReader.kt`

**다시 확인할 current-code assumption:** `VipsImageFormat.AVIF`/`.HEIC` capability/smoke snippet은 위 여덟 README variant 모두에 있으며, 두 image-module KDoc에는 reverse dependency 없이 Vips-owned annotation을 import할 수 없는 Vips-specific example이 있다.

- [ ] **Step 1: 실제 publication descriptor를 생성한다.**

  Run: `./gradlew :bluetape4k-images-vips-api:generatePomFileForBluetapeImagePublication :bluetape4k-images-vips-api:generateMetadataFileForBluetapeImagePublication --rerun-tasks`

  Expected: `BUILD SUCCESSFUL`이며 아래 generated file이 존재한다.

  ~~~text
  images-vips-api/build/publications/BluetapeImage/pom-default.xml
  images-vips-api/build/publications/BluetapeImage/module.json
  ~~~

- [ ] **Step 2: Normal Maven/Gradle consumer variant가 존재하고 clean한지 assert한다.**

  Run:

  ~~~bash
  pom=images-vips-api/build/publications/BluetapeImage/pom-default.xml
  forbidden=$(xmllint --xpath '
    count(/*[local-name() = "project"]
      /*[local-name() = "dependencies"]
      /*[local-name() = "dependency"][
        (*[local-name() = "groupId" and normalize-space(.) = "io.github.bluetape4k.image"] and
         *[local-name() = "artifactId" and normalize-space(.) = "bluetape4k-images"]) or
        *[local-name() = "groupId" and normalize-space(.) = "com.sksamuel.scrimage"] or
        *[local-name() = "groupId" and normalize-space(.) = "com.twelvemonkeys.imageio"]
      ])
  ' "$pom")
  test "$forbidden" = "0"
  ~~~

  Expected: exit code `0`. XPath는 dependency entry만 검사하므로 published `bluetape4k-images-vips-api` artifactId 자체를 forbidden `bluetape4k-images` dependency로 오해하지 않는다.

  Dependency array를 검사하기 전에 아래 command를 각각 실행한다.

  ~~~bash
  jq -e '[.variants[] | select(.name == "apiElements")] | length == 1' images-vips-api/build/publications/BluetapeImage/module.json
  jq -e '[.variants[] | select(.name == "runtimeElements")] | length == 1' images-vips-api/build/publications/BluetapeImage/module.json
  jq -e '[.variants[] | select(.name | test("testFixtures"))] | length > 0' images-vips-api/build/publications/BluetapeImage/module.json
  ~~~

  Expected: 모든 command가 exit `0`이어야 한다. Normal 또는 fixture variant가 없으면 empty dependency list가 아니라 publication-metadata failure이다.

  Run:

  ~~~bash
  jq -e '
    [.variants[]
     | select(.name == "apiElements" or .name == "runtimeElements")
     | .dependencies[]?
     | select(
         (.group == "io.github.bluetape4k.image" and .module == "bluetape4k-images") or
         (.group == "com.sksamuel.scrimage") or
         (.group == "com.twelvemonkeys.imageio")
       )]
    | length == 0
  ' images-vips-api/build/publications/BluetapeImage/module.json
  ~~~

  Expected: exit code `0`.

  Run:

  ~~~bash
  jq -e '
    [.variants[]
     | select(.name | test("testFixtures"))
     | .dependencies[]?
     | select(.group == "io.github.bluetape4k.image" and .module == "bluetape4k-images")]
    | length > 0
  ' images-vips-api/build/publications/BluetapeImage/module.json
  ~~~

  Expected: exit code `0`. Fixture variant는 의도적으로 image dependency를 유지한다.

- [ ] **Step 3: 모든 Vips capability README example을 업데이트하고 fixture boundary를 설명한다.**

  English/Korean README variant마다 main Vips API artifact가 Scrimage image implementation artifact를 요구하지 않는다고 명시한다. 모든 AVIF/HEIC capability 또는 smoke example에는 필요한 Vips import와 scoped opt-in을 추가한다. API README example은 독립적으로 copy-paste 가능해야 한다.

  ~~~kotlin
  import io.bluetape4k.images.vips.VipsImageFormat
  import io.bluetape4k.images.vips.VipsIncubatingApi
  import io.bluetape4k.images.vips.VipsRuntime

  @OptIn(VipsIncubatingApi::class)
  fun inspectAvif(runtime: VipsRuntime, avifSampleBytes: ByteArray) {
      val avif = runtime.codecCapabilityReport().codec(VipsImageFormat.AVIF)
      val smoke = runtime.smokeTestCodec(
          sampleBytes = avifSampleBytes,
          outputFormat = VipsImageFormat.AVIF,
      )
      require(smoke.succeeded) { smoke.failureReason.orEmpty() }
  }
  ~~~

  Java 21/25 backend README snippet도 `VipsIncubatingApi`, `VipsImageFormat`를 import하고, snippet에 concrete runtime type(`JVipsRuntime` 또는 `FfmVipsRuntime`)이 나오면 그 explicit import를 사용한다. Native libvips availability caveat는 유지한다.

  Vips API README pair에는 repository-build test-source example을 추가하고, 이것이 published main-artifact dependency가 아니라 의도적인 fixture-only boundary임을 English/Korean으로 설명한다.

  ~~~kotlin
  dependencies {
      // Repository test source only: uses the local test-fixtures variant.
      testImplementation(testFixtures(project(":bluetape4k-images-vips-api")))
  }
  ~~~

  이 test dependency는 `VipsGoldenAssert` 같은 pixel-comparison helper 전용이다. 일반 consumer는 Vips API artifact를 통해 `bluetape4k-images`를 받지 않는다.

- [ ] **Step 4: Reverse-boundary KDoc example을 contract-only English KDoc으로 교체한다.**

  `AvifWriter.kt`와 `HeicReader.kt`에서 Vips type과 Vips enum value를 example에서 제거한다. Interface의 `@IncubatingImageApi` contract는 보존한다. 업데이트된 public KDoc은 English로 두며, Vips marker를 naming/import하지 않고 compatible backend가 runtime support를 제공한다고 설명할 수 있다.

- [ ] **Step 5: Documentation을 source와 metadata에 대조해 검증한다.**

  Run: `rg -n 'IncubatingImageApi|VipsIncubatingApi|VipsImageFormat\.(AVIF|HEIC)' README.md README.ko.md images-vips-api/README.md images-vips-api/README.ko.md images-vips-java21/README.md images-vips-java21/README.ko.md images-vips-java25/README.md images-vips-java25/README.ko.md images/src/main/kotlin/io/bluetape4k/images/avif/AvifWriter.kt images/src/main/kotlin/io/bluetape4k/images/heic/HeicReader.kt`

  Expected: 모든 AVIF/HEIC README result를 수동 점검해 code block이 `VipsIncubatingApi`를 import하고 scoped `@OptIn(VipsIncubatingApi::class)`를 사용하는지 확인한다. API snippet은 `VipsRuntime`/`VipsImageFormat`을 resolve하고, backend snippet은 해당 runtime type/`VipsImageFormat`을 resolve한다. Image-module KDoc에는 Vips implementation type이 없어야 하며, 자체 `IncubatingImageApi` usage는 유지된다.

- [ ] **Step 6: Documentation과 publication-evidence slice를 commit한다.**

  `git diff --check`를 실행한 뒤 Lore trailer로 commit한다. Intent line: `docs: explain Vips opt-in dependency boundary`.

**Rollback / rerun point:** Normal metadata variant에 forbidden dependency가 포함되면 PR 생성 전에 중단하고 generated descriptor를 보존한 상태로 Task 3으로 돌아간다. Acceptance check를 약하게 만들지 않는다.

## Task 5: 최종 local verification pass 수행

**Complexity:** medium

**Apply:** Kotlin compiler/test result를 해석할 때 `$bluetape4k-code-patterns`를 적용한다. 이 변경은 concurrent behavior, coroutine, JNI/FFM lifecycle, Testcontainers usage를 추가하거나 변경하지 않으므로 concurrency helper는 적용하지 않는다.

**Files:** 의도적 source change 없음. Task 1-4의 전체 diff만 검증한다.

- [ ] **Step 1: 전체 targeted Gradle validation sequence를 serial로 실행한다.**

  Run:

  ~~~bash
  ./gradlew \
    :bluetape4k-images-vips-api:test \
    :bluetape4k-images-vips-api:compileOptedVipsOptInFixtureKotlin \
    :bluetape4k-images-vips-java21:compileKotlin \
    :bluetape4k-images-vips-java21:compileTestKotlin \
    :bluetape4k-images-vips-java25:compileKotlin \
    :bluetape4k-images-vips-java25:compileTestKotlin \
    :bluetape4k-images-vips-api:generatePomFileForBluetapeImagePublication \
    :bluetape4k-images-vips-api:generateMetadataFileForBluetapeImagePublication \
    -PverifyVipsOptInFixtures --rerun-tasks
  ~~~

  Expected: `BUILD SUCCESSFUL`. Unopted fixture는 expected failure가 required assertion이므로 별도 실행한다.

- [ ] **Step 2: Unopted expected-failure command를 다시 실행하고 diagnostic을 점검한다.**

  Run: `./gradlew :bluetape4k-images-vips-api:compileUnoptedVipsOptInFixtureKotlin -PverifyVipsOptInFixtures --rerun-tasks --console=plain`

  Expected: non-zero exit이고 diagnostic에 `VipsIncubatingApi`가 포함된다.

- [ ] **Step 3: Final source/documentation boundary check를 실행한다.**

  Run: `git diff --check`

  Expected: exit code `0`.

  Run: `rg -n 'api\(project\(":bluetape4k-images"\)\)|IncubatingImageApi' images-vips-api images-vips-java21 images-vips-java25 --glob '*.kt' --glob 'build.gradle.kts'`

  Expected: Vips API/backend match가 없어야 한다. `images-vips-api/build.gradle.kts`의 `testFixturesApi(project(":bluetape4k-images"))`는 유지된다.

- [ ] **Step 4: PR 작업 전 필수 review와 learning artifact를 만든다.**

  `docs/review/2026-07-10-issue-202-implementation-review.md`에 Step 6-R review evidence를 만들고, POM-versus-Gradle-metadata guard를 다루는 `docs/lessons/2026-07-10-issue-202-vips-api-boundary.md`를 만든다. Generated POM/module path, 각 boundary assertion command와 exit code, unopted fixture expected diagnostic, 두 descriptor의 SHA-256 hash를 기록한다. PR 생성 전 final implementation change와 함께 commit한다.

  PR 생성 전 branch에서 `Test / images-vips-api`, `Test / images-vips-java21`, `Test / images-vips-java25` CI run이 성공했는지 확인한다. Local compile-only backend verification은 libvips environment를 설치하고 실행하는 CI gate를 대체하지 않는다.

**Rollback / rerun point:** Compile, metadata, source-boundary check 중 하나라도 실패하면 PR을 만들지 않는다. 실패 invariant를 소유한 task로 돌아가 수정 후 targeted verification을 다시 실행한다.

## Requirement Coverage Matrix

| Approved design requirement | Plan task와 evidence |
|---|---|
| Exact target을 가진 Vips-owned BINARY opt-in marker | Task 1 Step 1-4; API test compilation. |
| AVIF/HEIC만 caller opt-in 전파; report는 stable 유지 | Task 1 Step 1-4; unannotated stable report test. |
| Main artifact가 `bluetape4k-images` 제외 | Task 3 Step 1; Task 4 Step 1-2. |
| Fixture-only image dependency는 intentional | Task 3 Step 1; Task 4 Step 2 fixture-variant assertion. |
| API와 두 backend의 모든 main/test opt-in migration | Task 3 Step 2-3; 네 backend compile task. |
| Opted/unopted compiler behavior를 정확히 증명 | Task 2 Step 1-3; Task 5 Step 2. |
| README와 KDoc migration이 boundary-correct | Task 4 Step 3-5. |
| JNI/FFM runtime/codec behavior 변경 없음 | Task 3 Step 2; Task 5 Step 1. |
| Evidence와 rollback guard 전 release/PR 없음 | Task 4 Step 2; Task 5 Step 4. |

## Plan Self-Review

- **Spec coverage:** 모든 acceptance criterion은 위 matrix의 task와 fresh verification command에 연결된다.
- **Ordering:** Task 1은 marker를 도입하고, Task 2는 compiler contract를 증명하며, Task 3은 새 marker가 존재한 뒤 dependency를 제거한다. Task 4는 publication output과 docs를 검증하고, Task 5는 final gate다.
- **Placeholder scan:** unresolved implementation placeholder가 남아 있지 않다.
- **Type consistency:** `VipsIncubatingApi`, 두 fixture source-set name, publication `BluetapeImage`, generated descriptor path는 계획 전체에서 같은 이름을 사용한다.
