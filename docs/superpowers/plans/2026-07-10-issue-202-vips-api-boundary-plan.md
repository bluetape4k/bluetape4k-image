# Vips API Dependency Boundary Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Publish a binding-neutral Vips API artifact whose normal consumer variants do not expose `bluetape4k-images`, Scrimage, or TwelveMonkeys while preserving explicit AVIF/HEIC opt-in and fixture-only pixel comparison support.

**Architecture:** Introduce a Vips-owned binary opt-in marker and make AVIF/HEIC enum entries the only declarations that propagate it to callers. Replace old image-marker imports in the Vips API and Java 21/25 implementations, then remove the main `images` project dependency. Keep `testFixturesApi` scoped to image comparison support and prove the boundary from generated Maven POM and Gradle Module Metadata.

**Tech Stack:** Kotlin 2.3, Gradle Kotlin DSL, `java-test-fixtures`, JUnit 5, bluetape4k assertions, Maven Publish.

---

## File Structure and Scope

| Path | Responsibility |
|---|---|
| `images-vips-api/build.gradle.kts` | Main-vs-fixture dependency boundary and strict opt-in compiler fixture source sets. |
| `images-vips-api/src/main/kotlin/io/bluetape4k/images/vips/VipsIncubatingApi.kt` | Public Vips-specific opt-in marker. |
| `images-vips-api/src/main/kotlin/io/bluetape4k/images/vips/VipsImageFormat.kt` | Caller-visible AVIF/HEIC opt-in propagation. |
| `images-vips-api/src/main/kotlin/io/bluetape4k/images/vips/VipsCodecCapabilityReport.kt` | Internal permission to refer to AVIF/HEIC without making stable report containers experimental. |
| `images-vips-api/src/test/kotlin/io/bluetape4k/images/vips/*.kt` | Capability and stable-report regression tests. |
| `images-vips-api/src/{optedVipsOptInFixture,unoptedVipsOptInFixture}/kotlin/...` | Strict compiler fixtures for opted success and unopted expected failure. |
| `images-vips-java21/src/{main,test}/kotlin/...` | Java 21 backend marker migration; no runtime/native-code changes. |
| `images-vips-java25/src/{main,test}/kotlin/...` | Java 25 FFM backend marker migration; no runtime/native-code changes. |
| `README*.md`, `images-vips-{api,java21,java25}/README*.md` | Copy-paste-safe AVIF/HEIC opt-in migration; API README pair also documents the fixture-only boundary. |
| `images/src/main/kotlin/io/bluetape4k/images/{avif/AvifWriter.kt,heic/HeicReader.kt}` | Contract-only image-module KDoc with no reverse Vips documentation dependency. |

No module registration, BOM/catalog, CI workflow, dependency upgrade, runtime codec behavior, benchmark source, release, or publication configuration is in scope. The existing 16 Vips API/backend files with direct old-marker imports are the complete migration set and must be re-counted before editing.

## Task 1: Add the public Vips marker and lock the stable-report contract

**Complexity:** medium

**Apply:** `$bluetape4k-code-patterns` to every Kotlin source, KDoc, and test change in this task.

**Files:**

- Create: `images-vips-api/src/main/kotlin/io/bluetape4k/images/vips/VipsIncubatingApi.kt`
- Modify: `images-vips-api/src/main/kotlin/io/bluetape4k/images/vips/VipsImageFormat.kt`
- Modify: `images-vips-api/src/main/kotlin/io/bluetape4k/images/vips/VipsCodecCapabilityReport.kt`
- Modify: `images-vips-api/src/test/kotlin/io/bluetape4k/images/vips/VipsCodecCapabilityReportTest.kt`
- Create: `images-vips-api/src/test/kotlin/io/bluetape4k/images/vips/VipsStableCodecCapabilityReportTest.kt`

**Current-code assumption to recheck:** `VipsImageFormat.AVIF` and `.HEIC` are currently the only public enum entries marked incubating, while `VipsCodecCapability` and `VipsCodecCapabilityReport` are internal `@OptIn` users rather than propagated opt-in declarations.

- [ ] **Step 1: Write the failing source-level migration test.**

  Change the existing capability test to import the not-yet-created `VipsIncubatingApi`, apply it at class scope, and add the stable, unannotated regression test below. The missing import intentionally makes test compilation fail before the marker exists.

  In `VipsCodecCapabilityReportTest.kt`, make this exact replacement before adding the new test file:

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

- [ ] **Step 2: Verify the test source fails for the intended reason.**

  Run: `./gradlew :bluetape4k-images-vips-api:compileTestKotlin --rerun-tasks`

  Expected: `FAILURE` with an unresolved reference to `VipsIncubatingApi`; do not accept unrelated compiler errors as the red result.

- [ ] **Step 3: Add the marker and migrate only the API-side declarations.**

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

  In `VipsImageFormat.kt`, replace the old import/KDoc reference and annotate only `AVIF` and `HEIC` with `@VipsIncubatingApi`. In `VipsCodecCapabilityReport.kt`, replace both existing class-level `@OptIn(IncubatingImageApi::class)` declarations with `@OptIn(VipsIncubatingApi::class)`; do not place `@VipsIncubatingApi` on the report container classes. Keep public signatures and serialization IDs unchanged.

- [ ] **Step 4: Verify the API regression tests pass.**

  Run: `./gradlew :bluetape4k-images-vips-api:test --tests '*VipsCodecCapabilityReportTest' --tests '*VipsStableCodecCapabilityReportTest' --rerun-tasks`

  Expected: `BUILD SUCCESSFUL`; the stable-report test compiles without `@OptIn`, while the capability test uses the Vips-owned marker.

- [ ] **Step 5: Commit the API contract slice.**

  Run `git diff --check`, then commit with Lore trailers. Intent line: `refactor: own Vips capability opt-in contract`.

**Rollback / rerun point:** Revert this commit if the annotation cannot be applied to enum entries with the approved target set; do not widen the marker to report container types as a workaround.

## Task 2: Prove opt-in diagnostics with strict Kotlin compiler fixtures

**Complexity:** high

**Apply:** `$bluetape4k-code-patterns` to the Gradle Kotlin DSL and Kotlin fixture sources in this task.

**Files:**

- Modify: `images-vips-api/build.gradle.kts`
- Create: `images-vips-api/src/unoptedVipsOptInFixture/kotlin/io/bluetape4k/images/vips/UnoptedVipsOptInFixture.kt`
- Create: `images-vips-api/src/optedVipsOptInFixture/kotlin/io/bluetape4k/images/vips/OptedVipsOptInFixture.kt`

**Current-code assumption to recheck:** the Java/Kotlin plugins create Kotlin compile tasks for custom Java source sets using the `compile<SourceSetName>Kotlin` convention. Confirm generated task names with `:bluetape4k-images-vips-api:tasks --all` before executing either fixture.

- [ ] **Step 1: Add the unopted fixture source and strict source-set wiring.**

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

  The unopted fixture is:

  ~~~kotlin
  package io.bluetape4k.images.vips

  internal object UnoptedVipsOptInFixture {
      val format: VipsImageFormat = VipsImageFormat.AVIF
  }
  ~~~

- [ ] **Step 2: Verify strict compilation fails with the marker diagnostic.**

  Run: `./gradlew :bluetape4k-images-vips-api:compileUnoptedVipsOptInFixtureKotlin -PverifyVipsOptInFixtures --rerun-tasks --console=plain`

  Expected: `FAILURE` caused by warnings-as-errors; the diagnostic names `VipsIncubatingApi`. Record this expected failure in the Step DoD evidence.

- [ ] **Step 3: Add the opted fixture and verify strict compilation succeeds.**

  ~~~kotlin
  package io.bluetape4k.images.vips

  @OptIn(VipsIncubatingApi::class)
  internal object OptedVipsOptInFixture {
      val format: VipsImageFormat = VipsImageFormat.AVIF
  }
  ~~~

  Run: `./gradlew :bluetape4k-images-vips-api:compileOptedVipsOptInFixtureKotlin -PverifyVipsOptInFixtures --rerun-tasks --console=plain`

  Expected: `BUILD SUCCESSFUL` with no opt-in warning promoted to an error.

- [ ] **Step 4: Commit the compiler-fixture slice.**

  Run `git diff --check`, then commit with Lore trailers. Intent line: `test: lock Vips opt-in compiler diagnostics`.

**Rollback / rerun point:** If Kotlin does not create the expected task names, adjust only the task lookup to the names printed by `tasks --all`; retain the two isolated source sets and do not add `kotlin-compile-testing`.

## Task 3: Remove the main image dependency and migrate both backend families

**Complexity:** high

**Apply:** `$bluetape4k-code-patterns` to every Kotlin import and opt-in change.

**Files:**

- Modify: `images-vips-api/build.gradle.kts`
- Modify: `images-vips-java21/src/main/kotlin/io/bluetape4k/images/vips/java21/{JVipsImage.kt,JVipsImageSupport.kt,JVipsRuntime.kt,internal/JVipsFormatSupport.kt}`
- Modify: `images-vips-java21/src/test/kotlin/io/bluetape4k/images/vips/java21/{JVipsCodecCapabilityTest.kt,JVipsImageTest.kt}`
- Modify: `images-vips-java25/src/main/kotlin/io/bluetape4k/images/vips/java25/{FfmVipsImage.kt,FfmVipsImageSupport.kt,FfmVipsRuntime.kt,internal/FfmVipsFormatSupport.kt,writer/FfmVipsHeifWriter.kt}`
- Modify: `images-vips-java25/src/test/kotlin/io/bluetape4k/images/vips/java25/{FfmVipsCodecCapabilityTest.kt,FfmVipsImageTest.kt}`

**Current-code assumption to recheck:** `rg -l 'IncubatingImageApi' images-vips-api images-vips-java21 images-vips-java25 --glob '*.kt'` returns exactly the 16 migration files described in the design. `testFixturesApi(project(":bluetape4k-images"))` remains necessary for `VipsGoldenAssert`.

- [ ] **Step 1: Remove only the main API dependency and capture the expected backend failure.**

  Delete this dependency and its stale comment; leave `testFixturesApi` intact:

  ~~~kotlin
  api(project(":bluetape4k-images"))
  ~~~

  Run: `./gradlew :bluetape4k-images-vips-java21:compileKotlin :bluetape4k-images-vips-java25:compileKotlin --rerun-tasks`

  Expected: `FAILURE` with unresolved `IncubatingImageApi` imports in both backend families. This confirms the migration is exercising the removed public boundary rather than an accidental transitive dependency.

- [ ] **Step 2: Migrate every direct old-marker import in the Vips scope.**

  In all files listed above, replace:

  ~~~kotlin
  import io.bluetape4k.images.IncubatingImageApi
  @OptIn(IncubatingImageApi::class)
  ~~~

  with:

  ~~~kotlin
  import io.bluetape4k.images.vips.VipsIncubatingApi
  @OptIn(VipsIncubatingApi::class)
  ~~~

  Do not change JNI `NativeHandle` ownership, Java 25 `Arena` lifecycle, runtime initialization, codec detection, exception behavior, or test inputs.

- [ ] **Step 3: Verify migration completeness and backend compilation.**

  Run: `rg -n 'IncubatingImageApi' images-vips-api images-vips-java21 images-vips-java25 --glob '*.kt'`

  Expected: no matches.

  Run: `./gradlew :bluetape4k-images-vips-api:test :bluetape4k-images-vips-java21:compileKotlin :bluetape4k-images-vips-java21:compileTestKotlin :bluetape4k-images-vips-java25:compileKotlin :bluetape4k-images-vips-java25:compileTestKotlin --rerun-tasks`

  Expected: `BUILD SUCCESSFUL`; the four backend compile tasks do not require JNI/FFM native test execution.

- [ ] **Step 4: Commit the dependency-boundary slice.**

  Run `git diff --check`, then commit with Lore trailers. Intent line: `refactor: decouple Vips API from image implementation`.

**Rollback / rerun point:** If either backend needs a type other than the marker from `bluetape4k-images`, stop and revise the plan/spec; do not restore the broad main `api(project(":bluetape4k-images"))` dependency by default.

## Task 4: Verify publication variants and document the caller migration

**Complexity:** medium

**Apply:** `$bluetape4k-code-patterns` to KDoc examples and public API names.

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

**Current-code assumption to recheck:** capability/smoke snippets using `VipsImageFormat.AVIF`/`.HEIC` occur in all eight README variants above, and the two image-module KDocs contain Vips-specific examples that cannot import a Vips-owned annotation without a reverse dependency.

- [ ] **Step 1: Generate the actual publication descriptors.**

  Run: `./gradlew :bluetape4k-images-vips-api:generatePomFileForBluetapeImagePublication :bluetape4k-images-vips-api:generateMetadataFileForBluetapeImagePublication --rerun-tasks`

  Expected: `BUILD SUCCESSFUL` and these generated files exist:

  ~~~text
  images-vips-api/build/publications/BluetapeImage/pom-default.xml
  images-vips-api/build/publications/BluetapeImage/module.json
  ~~~

- [ ] **Step 2: Assert normal Maven and Gradle consumer variants are present and clean.**

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

  Expected: exit code `0`; the XPath inspects dependency entries only, so the published `bluetape4k-images-vips-api` artifactId itself cannot be mistaken for the forbidden `bluetape4k-images` dependency.

  Run each command below before inspecting dependency arrays:

  ~~~bash
  jq -e '[.variants[] | select(.name == "apiElements")] | length == 1' images-vips-api/build/publications/BluetapeImage/module.json
  jq -e '[.variants[] | select(.name == "runtimeElements")] | length == 1' images-vips-api/build/publications/BluetapeImage/module.json
  jq -e '[.variants[] | select(.name | test("testFixtures"))] | length > 0' images-vips-api/build/publications/BluetapeImage/module.json
  ~~~

  Expected: every command exits `0`; a missing normal or fixture variant is a publication-metadata failure, not an empty dependency list.

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

  Expected: exit code `0`; the fixture variant intentionally retains the image dependency.

- [ ] **Step 3: Update every Vips capability README example and explain the fixture boundary.**

  In each English and Korean README variant, state that the main Vips API artifact does not require the Scrimage image implementation artifact. For every AVIF/HEIC capability or smoke example, add the required Vips imports and a scoped opt-in. API README examples must be independently copy-pasteable:

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

  Java 21/25 backend README snippets must likewise import `VipsIncubatingApi` and `VipsImageFormat`, and use their concrete runtime type (`JVipsRuntime` or `FfmVipsRuntime`) with its explicit import when that type appears in the snippet. Keep the native libvips availability caveat.

  In the Vips API README pair, add a repository-build test-source example and explain in English and Korean that it is intentionally fixture-only, not a published main-artifact dependency:

  ~~~kotlin
  dependencies {
      // Repository test source only: uses the local test-fixtures variant.
      testImplementation(testFixtures(project(":bluetape4k-images-vips-api")))
  }
  ~~~

  Explain that this test dependency is only for pixel-comparison helpers such as `VipsGoldenAssert`; normal consumers do not receive `bluetape4k-images` through the Vips API artifact.

- [ ] **Step 4: Replace reverse-boundary KDoc examples with contract-only English KDoc.**

  In `AvifWriter.kt` and `HeicReader.kt`, remove Vips types and Vips enum values from examples. Preserve the interfaces' `@IncubatingImageApi` contract; the updated public KDoc must be English and may state that a compatible backend supplies runtime support without naming or importing the Vips marker.

- [ ] **Step 5: Verify documentation against source and metadata.**

  Run: `rg -n 'IncubatingImageApi|VipsIncubatingApi|VipsImageFormat\.(AVIF|HEIC)' README.md README.ko.md images-vips-api/README.md images-vips-api/README.ko.md images-vips-java21/README.md images-vips-java21/README.ko.md images-vips-java25/README.md images-vips-java25/README.ko.md images/src/main/kotlin/io/bluetape4k/images/avif/AvifWriter.kt images/src/main/kotlin/io/bluetape4k/images/heic/HeicReader.kt`

  Expected: manually inspect every AVIF/HEIC README result to confirm its code block imports `VipsIncubatingApi` and uses a scoped `@OptIn(VipsIncubatingApi::class)`; API snippets also resolve `VipsRuntime`/`VipsImageFormat`, backend snippets resolve their runtime type/`VipsImageFormat`, image-module KDocs do not contain Vips implementation types, and their own `IncubatingImageApi` usage remains.

- [ ] **Step 6: Commit the documentation and publication-evidence slice.**

  Run `git diff --check`, then commit with Lore trailers. Intent line: `docs: explain Vips opt-in dependency boundary`.

**Rollback / rerun point:** If normal metadata variants contain a forbidden dependency, stop before PR creation, retain generated descriptors, and return to Task 3 rather than weakening the acceptance check.

## Task 5: Perform the final local verification pass

**Complexity:** medium

**Apply:** `$bluetape4k-code-patterns` when interpreting Kotlin compiler/test results. No concurrency helper applies: this change does not add or alter concurrent behavior, coroutines, JNI/FFM lifecycle, or Testcontainers usage.

**Files:** no intentional source changes; verify the complete diff from Tasks 1–4 only.

- [ ] **Step 1: Run the complete targeted Gradle validation sequence serially.**

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

  Expected: `BUILD SUCCESSFUL`. Run the unopted fixture separately because its expected failure is a required assertion, not a normal build success.

- [ ] **Step 2: Re-run the unopted expected-failure command and inspect the diagnostic.**

  Run: `./gradlew :bluetape4k-images-vips-api:compileUnoptedVipsOptInFixtureKotlin -PverifyVipsOptInFixtures --rerun-tasks --console=plain`

  Expected: non-zero exit and a diagnostic containing `VipsIncubatingApi`.

- [ ] **Step 3: Run final source/documentation boundary checks.**

  Run: `git diff --check`

  Expected: exit code `0`.

  Run: `rg -n 'api\(project\(":bluetape4k-images"\)\)|IncubatingImageApi' images-vips-api images-vips-java21 images-vips-java25 --glob '*.kt' --glob 'build.gradle.kts'`

  Expected: no Vips API/backend matches; `testFixturesApi(project(":bluetape4k-images"))` remains in `images-vips-api/build.gradle.kts`.

- [ ] **Step 4: Produce required review and learning artifacts before PR work.**

  Create Step 6-R review evidence under `docs/review/2026-07-10-issue-202-implementation-review.md`, then create `docs/lessons/2026-07-10-issue-202-vips-api-boundary.md` covering the POM-versus-Gradle-metadata guard. Record the generated POM/module paths, each boundary assertion command and exit code, the unopted fixture's expected diagnostic, and SHA-256 hashes of both descriptors. Commit both with final implementation changes before creating a PR.

  Before PR creation, confirm that the CI runs named `Test / images-vips-api`, `Test / images-vips-java21`, and `Test / images-vips-java25` succeeded for the branch. Local compile-only backend verification does not replace this CI gate because it installs and exercises the libvips environment.

**Rollback / rerun point:** If any compile, metadata, or source-boundary check fails, do not create a PR. Return to the task that owns the failed invariant and rerun its targeted verification after repair.

## Requirement Coverage Matrix

| Approved design requirement | Plan task and evidence |
|---|---|
| Vips-owned, BINARY opt-in marker with exact targets | Task 1, Steps 1–4; API test compilation. |
| Only AVIF/HEIC propagates caller opt-in; reports stay stable | Task 1, Steps 1–4; unannotated stable report test. |
| Main artifact excludes `bluetape4k-images` | Task 3, Step 1; Task 4, Steps 1–2. |
| Fixture-only image dependency remains intentional | Task 3, Step 1; Task 4, Step 2 fixture-variant assertion. |
| API and both backends migrate all main/test opt-ins | Task 3, Steps 2–3; four backend compile tasks. |
| Exact opted/unopted compiler behavior is proven | Task 2, Steps 1–3; Task 5, Step 2. |
| README and KDoc migration stays boundary-correct | Task 4, Steps 3–5. |
| No JNI/FFM runtime/codec behavior change | Task 3, Step 2; Task 5, Step 1. |
| No release/PR before evidence and rollback guard | Task 4, Step 2; Task 5, Step 4. |

## Plan Self-Review

- **Spec coverage:** every acceptance criterion maps to a task and fresh verification command in the matrix above.
- **Ordering:** Task 1 introduces the marker; Task 2 proves its compiler contract; Task 3 removes the dependency only after the new marker exists; Task 4 validates publication output and docs; Task 5 is the final gate.
- **Placeholder scan:** no unresolved implementation placeholder remains.
- **Type consistency:** `VipsIncubatingApi`, the two fixture source-set names, publication `BluetapeImage`, and generated descriptor paths use the same names throughout the plan.
