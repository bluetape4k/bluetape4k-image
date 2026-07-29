# 통합 이미지 인텔리전스 API 예제 구현 계획

> **에이전트 작업자용:** 필수 서브 스킬: 이 계획은 작업 단위로 구현해야 하며, `superpowers:subagent-driven-development` 사용을 권장하고 대안으로 `superpowers:executing-plans`를 사용할 수 있다. 진행 추적에는 체크박스(`- [ ]`) 문법을 사용한다.

**목표:** 업로드된 이미지 하나를 검증하고 정확히 한 번만 디코딩하는 실행 가능한 Spring Boot 예제를 추가한다. 이 예제는 OCR, 감지, 실제 ZXing 바코드 분석을 동시에 실행하고, 부분 결과를 보존하며, 별도의 방문자 패스 정책을 적용한다.

**아키텍처:** `ImageUploadQualifier`가 하나의 `QualifiedImage`를 만든다. 세 provider adapter는 보호된 `SuspendParallelFlow` lane에서 실행되고, domain `AnalysisResult` 값을 서로 다른 `WorkContext` key에 기록한다. workflow 출력은 집계된 뒤 `VisitorPassPolicy`가 해석하며, workflow library type을 노출하지 않는 안정적인 HTTP DTO로 매핑한다.

**기술 스택:** Kotlin 2.4, Spring Boot 4 Web MVC, Kotlin Coroutines, `bluetape4k-images`, `bluetape4k-images-ocr`, `bluetape4k-images-barcode-zxing`, `bluetape4k-workflow`, JUnit 6, MockK, bluetape4k assertions, ZXing test fixture generation.

**상태:** 2026-07-27에 구현 승인됨.

---

## 1. 실행 계약

- 기존 격리 worktree에서 인라인으로 실행한다:
  `/Users/debop/work/bluetape4k/bluetape4k-image/.worktrees/feat-issue-299-image-intelligence-api`.
- Base branch: `develop`.
- Feature branch: `feat/issue-299-image-intelligence-api`.
- 모든 Kotlin 동작에는 `test-driven-development`와 `bluetape-kotlin-patterns`를 사용한다.
- SVG/PNG diagram을 만들거나 변경하기 전에는 `bluetape-diagram`을 사용한다.
- native OCR, container, JNI, 기타 무거운 검증은 순차 실행한다.
- 각 작업 후 Lore commit protocol로 commit한다.
- merge하지 않는다. PR CI, review, merge-ready evidence를 확보한 뒤 새 사용자 결정을 위해 중지한다.

## 2. 파일 맵

다음 경로 아래에 책임이 좁은 파일을 만든다:

`examples/spring-boot-image-intelligence-api/src/main/kotlin/io/bluetape4k/images/examples/spring/intelligence/`

| 파일 | 책임 |
|---|---|
| `ImageIntelligenceApiApplication.kt` | Spring Boot application entry point |
| `config/ImageIntelligenceConfiguration.kt` | 검증된 properties, profile별 provider, ZXing, service bean |
| `model/AnalysisModels.kt` | `AnalysisResult`, lane payload, aggregate status |
| `model/ApiModels.kt` | 안정적인 HTTP response와 policy DTO |
| `service/ImageUploadQualifier.kt` | 제한된 multipart read, MIME/magic check, dimension probe, 단일 decode |
| `service/GuardedAnalysisRunner.kt` | semaphore, timeout, cancellation, elapsed time, 정제된 failure mapping |
| `service/ImageAnalysisProviders.kt` | OCR, detector, barcode provider adapter 계약과 구현 |
| `service/ImageIntelligenceWorkflow.kt` | `SuspendParallelFlow`, 분리된 context key, typed result extraction |
| `service/ImageIntelligenceAggregator.kt` | `COMPLETED`, `PARTIAL`, `FAILED` 계산 |
| `service/VisitorPassPolicy.kt` | `ALLOW`, `MANUAL_REVIEW`, `REJECT`, `QUARANTINE` |
| `service/ImageIntelligenceService.kt` | qualify -> analyze -> aggregate -> policy -> response |
| `web/ImageIntelligenceController.kt` | multipart endpoint |
| `web/ImageIntelligenceExceptionHandler.kt` | 안정적인 `ProblemDetail` mapping |

각 책임을 `src/test/kotlin` 아래에 대응시킨다. 결정적인 test helper는 다음 파일에 둔다:

`src/test/kotlin/io/bluetape4k/images/examples/spring/intelligence/support/ImageIntelligenceFixtures.kt`.

production binary fixture는 필요하지 않다. test는 ZXing으로 결정적인 QR image를 생성하고 PNG로 encode한 뒤, 생성된 payload와 dimension을 고정한다.

## 3. 수용 기준 추적성

| Spec 수용 기준 | 계획 작업 |
|---|---|
| 실행 가능한 non-published Spring Boot 예제와 local project dependency | Task 1 |
| 제한된 입력, full decode 전 dimension probe, 하나의 `ImmutableImage` | Task 2 |
| 분리된 `Completed`, `Empty`, `Unavailable`, `Failed` 결과 | Task 3 |
| 제한된 concurrency, timeout, cancellation 구분 | Task 3 |
| local OCR/detection adapter와 실제 ZXing provider | Task 4 |
| `WorkReport.Success`는 domain success가 아니라 step completion을 의미 | Task 5 |
| 한 lane 실패 후에도 sibling result 보존 | Task 5 |
| aggregate status와 visitor-pass policy 분리 | Task 5 |
| 안정적인 multipart HTTP 계약과 정제된 error | Task 6 |
| full success, empty, unavailable, failure, timeout, cancellation test | Tasks 2-7 |
| bilingual README와 dark SVG/PNG diagram | Task 8 |
| settings, AGENTS, root README, Examples workflow 등록 | Tasks 1 and 8 |
| targeted/full validation, actionlint, diagram check, review, lesson, PR | Task 9 |
| versioned manual과 publish BOM 불변 | Tasks 8 and 9 |

## 4. 예상 위험과 통제

| 위험 | 신호 | 통제 | 재실행 또는 rollback 지점 |
|---|---|---|---|
| compressed image와 decoded image를 함께 보관 | concurrent request 중 heap 증가 | `QualifiedImage`가 upload byte를 보관하지 않음 | Task 2 qualification test |
| 과도한 dimension을 거절하기 전에 full decode 수행 | validation 전 큰 allocation 발생 | `probeImageDimensions`와 metadata fallback을 `immutableImageOf`보다 먼저 실행 | Task 2 oversized fixture test |
| provider timeout을 external cancellation으로 오인 | caller cancellation이 lane failure로 반환됨 | local `TimeoutCancellationException`만 catch하고 다른 `CancellationException`은 rethrow | Task 3 cancellation test |
| in-process native OCR이 interruption을 무시 | timeout이 끝나도 native call이 계속 실행 | best-effort interruption을 문서화하고, 엄격한 SLA에는 process/remote isolation 요구 | Tasks 3, 4, 8 |
| 한 lane failure가 sibling을 취소 | 성공한 partial result 누락 | 예상 가능한 provider failure를 `AnalysisResult`로 정규화하고 `WorkReport.Success` 반환 | Task 5 partial-result test |
| shared context key 충돌 | nondeterministic result replacement | lane마다 하나의 constant key를 쓰고, 한 번만 write하며, workflow completion 뒤에만 extract | Task 5 context test |
| failed detector를 empty detector result로 처리 | 안전하지 않은 automatic `ALLOW` | `Failed`/`Unavailable`을 유지하고 policy decision-table test로 검증 | Task 5 policy test |
| test가 fake barcode reader만 사용 | integration contract 미검증 | QR을 생성하고 실제 `ZxingBarcodeReader` 실행 | Tasks 4 and 7 |
| 새 예제가 CI 또는 docs에서 누락 | local success지만 repository drift 발생 | registration search, `./gradlew projects`, `actionlint` | Tasks 8 and 9 |
| diagram SVG는 정상처럼 보이나 PNG가 깨짐 | README asset 판독 불가 | 둘 다 render하고 full size PNG를 inspect하며 diagram validator 실행 | Task 8 |

## Task 1: 예제 등록과 configuration 계약 고정

**복잡도:** Medium
**의존성:** committed design
**Pattern skills:** `bluetape-kotlin-patterns`, `test-driven-development`
**Rollback 지점:** 이후 task가 의존하기 전에 새 settings entry와 example directory만 제거한다.

**파일:**

- Modify: `settings.gradle.kts`
- Modify: `AGENTS.md`
- Create: `examples/spring-boot-image-intelligence-api/build.gradle.kts`
- Create: `examples/spring-boot-image-intelligence-api/src/main/kotlin/io/bluetape4k/images/examples/spring/intelligence/ImageIntelligenceApiApplication.kt`
- Create: `examples/spring-boot-image-intelligence-api/src/main/kotlin/io/bluetape4k/images/examples/spring/intelligence/config/ImageIntelligenceConfiguration.kt`
- Create: `examples/spring-boot-image-intelligence-api/src/main/resources/application.yml`
- Create: `examples/spring-boot-image-intelligence-api/src/test/kotlin/io/bluetape4k/images/examples/spring/intelligence/config/ImageIntelligencePropertiesTest.kt`
- Create: `examples/spring-boot-image-intelligence-api/src/test/resources/junit-platform.properties`
- Create: `examples/spring-boot-image-intelligence-api/src/test/resources/logback-test.xml`

- [ ] **Step 1: 빈 Gradle project를 등록하고 실패하는 property test 추가**

`settings.gradle.kts`에서 다른 Spring Boot 예제 옆에 추가한다:

```kotlin
include("spring-boot-image-intelligence-api")
project(":spring-boot-image-intelligence-api").projectDir =
    file("examples/spring-boot-image-intelligence-api")
```

`build.gradle.kts`를 만든다:

```kotlin
plugins {
    application
    alias(bt4k.plugins.kotlin.spring)
    alias(bt4k.plugins.spring.boot)
}

dependencies {
    implementation(project(":bluetape4k-images"))
    implementation(project(":bluetape4k-images-ocr"))
    implementation(project(":bluetape4k-images-barcode-zxing"))
    implementation(bt4k.bluetape4k.workflow)
    implementation(libs.spring.boot.starter.web)
    implementation(libs.kotlinx.coroutines.reactor)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.webmvc.test)
    testImplementation(bt4k.bluetape4k.junit5)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.zxing.core)
}

application {
    mainClass.set(
        "io.bluetape4k.images.examples.spring.intelligence.ImageIntelligenceApiApplicationKt"
    )
}

springBoot {
    mainClass.set(
        "io.bluetape4k.images.examples.spring.intelligence.ImageIntelligenceApiApplicationKt"
    )
}
```

`ImageIntelligenceProperties`를 생성하고 다음 값을 거절하는 test를 작성한다:

```kotlin
@Test
fun `rejects non-positive upload and provider limits`() {
    assertFailsWith<IllegalArgumentException> {
        ImageIntelligenceProperties(maxInputBytes = 0)
    }
    assertFailsWith<IllegalArgumentException> {
        ImageIntelligenceProperties(ocrTimeout = Duration.ZERO)
    }
    assertFailsWith<IllegalArgumentException> {
        ImageIntelligenceProperties(ocrTimeout = Duration.ofNanos(1))
    }
    assertFailsWith<IllegalArgumentException> {
        ImageIntelligenceProperties(barcodeConcurrency = 0)
    }
}
```

- [ ] **Step 2: focused test를 실행하고 RED 확인**

실행:

```bash
./gradlew :spring-boot-image-intelligence-api:test \
  --tests '*ImageIntelligencePropertiesTest' --no-daemon
```

예상 결과: `ImageIntelligenceProperties`가 없으므로 Kotlin compilation이 실패한다.

- [ ] **Step 3: application과 검증된 properties 구현**

Spring configuration binding을 직접 유지하도록 `java.time.Duration`을 사용한다:

```kotlin
@ConfigurationProperties(prefix = "example.image-intelligence")
data class ImageIntelligenceProperties(
    val maxInputBytes: Long = 5L * 1024L * 1024L,
    val maxInputPixels: Long = 16_777_216L,
    val maxInputSide: Int = 8_192,
    val ocrTimeout: Duration = Duration.ofSeconds(3),
    val detectionTimeout: Duration = Duration.ofSeconds(2),
    val barcodeTimeout: Duration = Duration.ofSeconds(2),
    val ocrConcurrency: Int = 1,
    val detectionConcurrency: Int = 2,
    val barcodeConcurrency: Int = 4,
    val tessdataPath: String? = null,
) : Serializable {
    init {
        maxInputBytes.requirePositiveNumber("maxInputBytes")
        require(maxInputBytes <= Int.MAX_VALUE) { "maxInputBytes must fit Int" }
        maxInputPixels.requirePositiveNumber("maxInputPixels")
        maxInputSide.requirePositiveNumber("maxInputSide")
        require(ocrTimeout.toMillis() > 0L) { "ocrTimeout must be at least 1 ms" }
        require(detectionTimeout.toMillis() > 0L) { "detectionTimeout must be at least 1 ms" }
        require(barcodeTimeout.toMillis() > 0L) { "barcodeTimeout must be at least 1 ms" }
        ocrConcurrency.requirePositiveNumber("ocrConcurrency")
        detectionConcurrency.requirePositiveNumber("detectionConcurrency")
        barcodeConcurrency.requirePositiveNumber("barcodeConcurrency")
    }

    private companion object {
        private const val serialVersionUID: Long = 1L
    }
}
```

`@SpringBootApplication`으로 application entry point를 만든다. `@Configuration(proxyBeanMethods = false)` class에서 `@EnableConfigurationProperties(ImageIntelligenceProperties::class)`를 통해 properties를 등록한다.

`application.yml`에 Spring multipart 설정과 example default를 맞춰 둔다. Spring multipart size는 `max-input-bytes`보다 작으면 안 된다.

- [ ] **Step 4: 필요한 test resource와 repository module map 추가**

`examples/spring-boot-barcode-api`와 같은 JUnit parallel-disable 및 Logback test configuration을 사용한다. `AGENTS.md`에 다음 행을 추가한다:

```markdown
| `examples/spring-boot-image-intelligence-api` | Non-published Spring Boot OCR, detection, and barcode orchestration example |
```

command section에 `./gradlew :spring-boot-image-intelligence-api:test`를 추가한다.

- [ ] **Step 5: GREEN과 project registration 증거 확인**

실행:

```bash
./gradlew :spring-boot-image-intelligence-api:test \
  --tests '*ImageIntelligencePropertiesTest' --no-daemon
./gradlew projects --no-daemon | rg ':spring-boot-image-intelligence-api'
```

예상 결과: property test가 통과하고 project가 정확히 한 번 표시된다.

- [ ] **Step 6: Commit**

```bash
git add settings.gradle.kts AGENTS.md examples/spring-boot-image-intelligence-api
git commit -m "Establish the integrated image example boundary" \
  -m "Constraint: The example remains non-published and uses local image projects." \
  -m "Confidence: high" \
  -m "Scope-risk: narrow" \
  -m "Tested: property contract test and Gradle project listing"
```

## Task 2: 각 upload를 검증하고 정확히 한 번만 decode

**복잡도:** Medium
**의존성:** Task 1
**Pattern skills:** `bluetape-kotlin-patterns`, `test-driven-development`
**Rollback 지점:** provider 또는 workflow code에 영향을 주지 않고 qualifier와 test만 revert한다.

**파일:**

- Create: `examples/spring-boot-image-intelligence-api/src/main/kotlin/io/bluetape4k/images/examples/spring/intelligence/service/ImageUploadQualifier.kt`
- Create: `examples/spring-boot-image-intelligence-api/src/test/kotlin/io/bluetape4k/images/examples/spring/intelligence/service/ImageUploadQualifierTest.kt`
- Create: `examples/spring-boot-image-intelligence-api/src/test/kotlin/io/bluetape4k/images/examples/spring/intelligence/support/ImageIntelligenceFixtures.kt`

- [ ] **Step 1: qualification boundary test 작성**

다음을 검증한다:

```kotlin
@Test
fun `probes limits before decoding and returns one qualified image`() = runTest {
    val qualified = qualifier.qualify(
        TestMultipartFile("file", "pass.png", "image/png", pngBytes(40, 30))
    )

    qualified.mediaType shouldBeEqualTo "image/png"
    qualified.dimensions shouldBeEqualTo ImageDimensions(40, 30)
    qualified.image.width shouldBeEqualTo 40
    decodeCalls.get() shouldBeEqualTo 1
}

@Test
fun `rejects MIME mismatch before decode`() = runTest {
    assertFailsWith<InvalidImageUploadException> {
        qualifier.qualify(
            TestMultipartFile("file", "pass.png", "image/png", jpegBytes(40, 30))
        )
    }
    decodeCalls.get() shouldBeEqualTo 0
}

@Test
fun `rejects pixel overflow before decode`() = runTest {
    assertFailsWith<ImagePayloadTooLargeException> {
        qualifier.qualify(
            TestMultipartFile("file", "large.png", "image/png", pngBytes(101, 100))
        )
    }
    decodeCalls.get() shouldBeEqualTo 0
}
```

content type 누락, empty bytes, reported-size overflow, actual-size overflow, unsupported GIF, malformed bytes, maximum side, byte read 중 caller cancellation도 함께 test한다.

- [ ] **Step 2: focused test를 실행하고 RED 확인**

```bash
./gradlew :spring-boot-image-intelligence-api:test \
  --tests '*ImageUploadQualifierTest' --no-daemon
```

예상 결과: qualifier type이 없으므로 compilation이 실패한다.

- [ ] **Step 3: 제한된 qualification sequence 구현**

다음 계약을 사용한다:

```kotlin
internal data class QualifiedImage(
    val mediaType: String,
    val dimensions: ImageDimensions,
    val image: ImmutableImage,
)

internal open class InvalidImageUploadException(
    val reasonCode: String,
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)

internal class ImagePayloadTooLargeException(
    reasonCode: String,
    message: String,
) : InvalidImageUploadException(reasonCode, message)
```

`ImageUploadQualifier.qualify(file)`은 정확히 다음 순서로 실행해야 한다:

1. empty input과 지원하지 않는 declared content type을 거절한다.
2. `MultipartFile.size` overflow를 거절한다.
3. `Dispatchers.IO`에서 byte를 읽고 `CancellationException`은 다시 throw한다.
4. actual byte overflow를 거절한다.
5. magic byte에서 PNG, JPEG, WebP를 식별하고 declared type과 비교한다.
6. `probeImageDimensions`를 호출하고, 실패하면 제한된 metadata parsing으로 fallback한다.
7. full decode 전에 maximum side와 pixel count를 거절한다.
8. `Dispatchers.Default`에서 `immutableImageOf(bytes)`를 한 번만 호출한다.
9. media type, dimension, decoded image만 반환하고 source byte는 절대 보관하지 않는다.

예상하지 못한 decode exception은 catch하고 다음 형태로만 노출한다:

```kotlin
InvalidImageUploadException(
    reasonCode = "image_not_decodable",
    message = "The uploaded file is not a decodable image.",
    cause = exception,
)
```

- [ ] **Step 4: GREEN 실행**

```bash
./gradlew :spring-boot-image-intelligence-api:test \
  --tests '*ImageUploadQualifierTest' --no-daemon
```

예상 결과: 모든 qualification 및 cancellation test가 통과한다.

- [ ] **Step 5: Commit**

```bash
git add examples/spring-boot-image-intelligence-api/src/main \
  examples/spring-boot-image-intelligence-api/src/test
git commit -m "Reject unsafe image input before full decoding" \
  -m "Constraint: Pixel and side budgets must be checked before immutableImageOf." \
  -m "Confidence: high" \
  -m "Scope-risk: narrow" \
  -m "Tested: ImageUploadQualifier boundary and cancellation tests"
```

## Task 3: domain outcome을 모델링하고 provider execution 보호

**복잡도:** High
**의존성:** Task 1
**Pattern skills:** `bluetape-kotlin-patterns`, `kotlin-coroutines-skill`, `test-driven-development`
**Rollback 지점:** runner와 model은 internal이므로 provider adapter가 사용하기 전에 되돌릴 수 있다.

**파일:**

- Create: `examples/spring-boot-image-intelligence-api/src/main/kotlin/io/bluetape4k/images/examples/spring/intelligence/model/AnalysisModels.kt`
- Create: `examples/spring-boot-image-intelligence-api/src/main/kotlin/io/bluetape4k/images/examples/spring/intelligence/service/GuardedAnalysisRunner.kt`
- Create: `examples/spring-boot-image-intelligence-api/src/test/kotlin/io/bluetape4k/images/examples/spring/intelligence/service/GuardedAnalysisRunnerTest.kt`

- [ ] **Step 1: 네 가지 outcome에 대한 RED test 작성**

다음을 증명하는 test를 작성한다:

```kotlin
runner.run("fixture", timeout, semaphore) { "value" }
    .shouldBeInstanceOf<AnalysisResult.Completed<String>>()

runner.run("fixture", timeout, semaphore, isEmpty = { it.isEmpty() }) { "" }
    .shouldBeInstanceOf<AnalysisResult.Empty>()

runner.run<String>("disabled", timeout, semaphore) {
    throw ProviderUnavailableException("provider_not_configured")
}.shouldBeInstanceOf<AnalysisResult.Unavailable>()

runner.run<String>("broken", timeout, semaphore) {
    error("raw-secret")
}.shouldBeInstanceOf<AnalysisResult.Failed>()
```

elapsed time이 음수가 아니고 `Failed.reasonCode`에 `raw-secret`이 포함되지 않는지도 assert한다.

- [ ] **Step 2: timeout, semaphore, cancellation RED test 작성**

필요한 곳에는 `runTest`와 실제 cancellation job을 사용한다:

- local timeout은 `Failed("timeout")`으로만 변환된다.
- external parent cancellation은 다시 throw되어 child를 취소한다.
- 최대 concurrent entry 수는 configured permit count를 절대 넘지 않는다.
- success, failure, timeout, cancellation 후 permit이 release된다.
- 대기 중인 caller cancellation 하나가 permit을 소비하지 않는다.

- [ ] **Step 3: Run RED**

```bash
./gradlew :spring-boot-image-intelligence-api:test \
  --tests '*GuardedAnalysisRunnerTest' --no-daemon
```

예상 결과: runner와 model이 없으므로 compilation이 실패한다.

- [ ] **Step 4: immutable domain outcome 구현**

```kotlin
internal sealed interface AnalysisResult<out T> {
    val provider: String
    val elapsedMillis: Long

    data class Completed<T>(
        override val provider: String,
        override val elapsedMillis: Long,
        val value: T,
    ) : AnalysisResult<T>

    data class Empty(
        override val provider: String,
        override val elapsedMillis: Long,
    ) : AnalysisResult<Nothing>

    data class Unavailable(
        override val provider: String,
        override val elapsedMillis: Long,
        val reasonCode: String,
    ) : AnalysisResult<Nothing>

    data class Failed(
        override val provider: String,
        override val elapsedMillis: Long,
        val reasonCode: String,
    ) : AnalysisResult<Nothing>
}
```

다음을 추가한다:

```kotlin
internal class ProviderUnavailableException(
    val reasonCode: String,
) : RuntimeException(reasonCode)
```

domain result를 통해 original exception message를 노출하지 않는다.

- [ ] **Step 5: `GuardedAnalysisRunner` 구현**

`Semaphore.withPermit`, `withTimeout`, `TimeSource.Monotonic`을 사용한다. public-to-example execution contract를 다음 internal signature로 고정한다:

```kotlin
internal suspend fun <T : Any> run(
    provider: String,
    timeout: Duration,
    semaphore: Semaphore,
    isEmpty: (T) -> Boolean = { false },
    block: suspend () -> T,
): AnalysisResult<T>
```

여기서 `Duration`은 `java.time.Duration`이다. `timeout.toMillis()` 변환은 `withTimeout` 경계에서만 수행한다. runner bean을 만들 때 blank provider, non-positive timeout, permit이 1개보다 적게 configured된 semaphore를 거절한다. catch 순서는 다음과 같아야 한다:

```kotlin
try {
    semaphore.withPermit {
        withTimeout(timeout.toMillis()) {
            val value = block()
            if (isEmpty(value)) empty(provider, elapsed()) else completed(provider, elapsed(), value)
        }
    }
} catch (exception: TimeoutCancellationException) {
    failed(provider, elapsed(), "timeout")
} catch (exception: CancellationException) {
    throw exception
} catch (exception: ProviderUnavailableException) {
    unavailable(provider, elapsed(), exception.reasonCode)
} catch (exception: Exception) {
    log.warn { "Image analysis provider failed. provider=$provider reason=provider_failure" }
    failed(provider, elapsed(), "provider_failure")
}
```

실행 전에 provider, timeout, concurrency configuration을 검증한다. logging에는 provider와 reason code를 포함할 수 있지만 raw exception message, stack trace, image, OCR, barcode, detection payload는 포함하지 않는다.

- [ ] **Step 6: GREEN 실행**

```bash
./gradlew :spring-boot-image-intelligence-api:test \
  --tests '*GuardedAnalysisRunnerTest' --no-daemon
```

예상 결과: outcome, permit, timeout, 실제 cancellation test가 통과한다.

- [ ] **Step 7: Commit**

```bash
git add examples/spring-boot-image-intelligence-api/src/main \
  examples/spring-boot-image-intelligence-api/src/test
git commit -m "Separate provider execution from analysis outcomes" \
  -m "Constraint: External cancellation must never be normalized as a business failure." \
  -m "Confidence: high" \
  -m "Scope-risk: moderate" \
  -m "Tested: guarded runner outcome, semaphore, timeout, and cancellation tests"
```

## Task 4: Reuse OCR, detection, and real ZXing through provider adapters

**Complexity:** High
**Depends on:** Tasks 2 and 3
**Pattern skills:** `bluetape-kotlin-patterns`, `kotlin-coroutines-skill`, `test-driven-development`
**Rollback point:** remove profile/provider beans while retaining generic runner and models.

**Files:**

- Create: `examples/spring-boot-image-intelligence-api/src/main/kotlin/io/bluetape4k/images/examples/spring/intelligence/service/ImageAnalysisProviders.kt`
- Modify: `examples/spring-boot-image-intelligence-api/src/main/kotlin/io/bluetape4k/images/examples/spring/intelligence/config/ImageIntelligenceConfiguration.kt`
- Create: `examples/spring-boot-image-intelligence-api/src/test/kotlin/io/bluetape4k/images/examples/spring/intelligence/service/ImageAnalysisProvidersTest.kt`
- Modify: `examples/spring-boot-image-intelligence-api/src/test/kotlin/io/bluetape4k/images/examples/spring/intelligence/support/ImageIntelligenceFixtures.kt`

- [ ] **Step 1: Write provider contract tests**

Prove:

- default OCR returns `Unavailable("provider_not_configured")`;
- default detector returns `Unavailable("provider_not_configured")`;
- demo OCR returns structured text with page metadata;
- demo detector returns one face fact and no policy action;
- blank image produces barcode `Empty`;
- generated QR image produces barcode `Completed` with `QR_CODE` and payload
  `visitor:PASS-001`;
- provider exceptions become sanitized `Failed`;
- provider cancellation is propagated.

- [ ] **Step 2: Run RED**

```bash
./gradlew :spring-boot-image-intelligence-api:test \
  --tests '*ImageAnalysisProvidersTest' --no-daemon
```

Expected: compilation fails because provider adapters do not exist.

- [ ] **Step 3: Implement narrow provider contracts**

```kotlin
internal interface OcrAnalysisProvider {
    val id: String
    suspend fun analyze(image: ImmutableImage): OcrStructuredResult
}

internal interface DetectionAnalysisProvider {
    val id: String
    suspend fun analyze(image: ImmutableImage): List<DetectionResult>
}

internal interface BarcodeAnalysisProvider {
    val id: String
    suspend fun analyze(image: ImmutableImage): List<BarcodeResult>
}
```

Implement:

- `DisabledOcrAnalysisProvider`
- `FixtureOcrAnalysisProvider`
- `TesseractOcrAnalysisProvider`
- `DisabledDetectionAnalysisProvider`
- `FixtureDetectionAnalysisProvider`
- `ZxingBarcodeAnalysisProvider`

Reuse the existing suspend adapters instead of wrapping providers again:

```kotlin
image.suspendExtractOcr(options, engine, ocrDispatcher)
image.suspendDetectRegions(detector, options, detectionDispatcher)
image.suspendExtractBarcodes(reader, options, barcodeDispatcher)
```

Those adapters dispatch blocking work with `withContext`. README must later state that
dispatch prevents work from starting after cancellation, but an already-running native
call may ignore cancellation.

- [ ] **Step 4: Configure profile ownership explicitly**

Use `@Profile("demo")`, `@Profile("native-ocr")`, and negated profile conditions so exactly
one OCR provider and one detector provider exist:

```text
default         disabled OCR + disabled detector + ZXing
demo            fixture OCR + fixture detector + ZXing
native-ocr      Tesseract OCR + disabled detector + ZXing
demo,native-ocr invalid combination rejected by a configuration test
```

Add a startup guard rather than depending on bean ordering:

```kotlin
internal class ImageIntelligenceProfileGuard(
    private val environment: Environment,
) : SmartInitializingSingleton {
    override fun afterSingletonsInstantiated() {
        val active = environment.activeProfiles.toSet()
        require(!active.containsAll(setOf("demo", "native-ocr"))) {
            "Profiles 'demo' and 'native-ocr' cannot be active together."
        }
    }
}
```

Register the guard unconditionally and use these exact profile expressions:

```text
fixture OCR                  @Profile("demo & !native-ocr")
Tesseract OCR                @Profile("native-ocr & !demo")
disabled OCR                 @Profile("!demo & !native-ocr")
fixture detector             @Profile("demo")
disabled detector            @Profile("!demo")
ZXing barcode                no profile restriction
```

The conflicting-profile test must assert context startup failure and the stable
configuration message above.

Do not auto-download models or traineddata. Pass optional `tessdataPath` only from validated
configuration.

- [ ] **Step 5: Generate the QR fixture at test runtime**

Use ZXing `QRCodeWriter` in test support to render `visitor:PASS-001` into a
`BufferedImage`, then encode with the existing image utilities. Pin:

- payload;
- format `QR_CODE`;
- dimensions;
- generated-source note.

No external image license or binary fixture is introduced.

- [ ] **Step 6: Run GREEN**

```bash
./gradlew :spring-boot-image-intelligence-api:test \
  --tests '*ImageAnalysisProvidersTest' --no-daemon
```

Expected: default/demo adapters and actual ZXing extraction pass without Tesseract.

- [ ] **Step 7: Commit**

```bash
git add examples/spring-boot-image-intelligence-api/src/main \
  examples/spring-boot-image-intelligence-api/src/test
git commit -m "Compose image capabilities through explicit providers" \
  -m "Constraint: Default tests must not require native OCR or a production ML model." \
  -m "Rejected: Bundle an ML detector | The example owns orchestration, not model selection." \
  -m "Confidence: high" \
  -m "Scope-risk: moderate" \
  -m "Tested: provider profile, unavailable, fixture, cancellation, and real ZXing tests"
```

## Task 5: Orchestrate parallel lanes and apply a separate policy

**Complexity:** High
**Depends on:** Tasks 3 and 4
**Pattern skills:** `bluetape-kotlin-patterns`, `kotlin-coroutines-skill`, `test-driven-development`
**Rollback point:** workflow, aggregator, and policy are internal and can be reverted together.

**Files:**

- Create: `examples/spring-boot-image-intelligence-api/src/main/kotlin/io/bluetape4k/images/examples/spring/intelligence/service/ImageIntelligenceWorkflow.kt`
- Create: `examples/spring-boot-image-intelligence-api/src/main/kotlin/io/bluetape4k/images/examples/spring/intelligence/service/ImageIntelligenceAggregator.kt`
- Create: `examples/spring-boot-image-intelligence-api/src/main/kotlin/io/bluetape4k/images/examples/spring/intelligence/service/VisitorPassPolicy.kt`
- Create: `examples/spring-boot-image-intelligence-api/src/test/kotlin/io/bluetape4k/images/examples/spring/intelligence/service/ImageIntelligenceWorkflowTest.kt`
- Create: `examples/spring-boot-image-intelligence-api/src/test/kotlin/io/bluetape4k/images/examples/spring/intelligence/service/VisitorPassPolicyTest.kt`

- [ ] **Step 1: Write RED workflow tests**

Prove:

- three controlled lanes overlap in time rather than executing sequentially;
- each lane writes one unique context key;
- a provider `Failed` result still yields workflow `WorkReport.Success`;
- OCR failure preserves detection and barcode outcomes;
- a missing context key becomes `ImageWorkflowException`;
- an unexpected programming exception yields workflow failure and a sanitized service error;
- external job cancellation reaches all active provider adapters.

- [ ] **Step 2: Write RED aggregate and policy decision-table tests**

Aggregate rules:

```text
all Completed/Empty       -> COMPLETED
available + degraded      -> PARTIAL
no available result       -> FAILED
```

Policy order:

```text
sensitive detection fact                     -> QUARANTINE
invalid completed visitor QR                  -> REJECT
Failed or Unavailable required lane           -> MANUAL_REVIEW
missing/multiple face or missing/multiple QR   -> MANUAL_REVIEW
valid OCR + one face + one visitor QR          -> ALLOW
```

Explicitly prove `Detection Empty` and `Detection Failed` are not equivalent.

- [ ] **Step 3: Run RED**

```bash
./gradlew :spring-boot-image-intelligence-api:test \
  --tests '*ImageIntelligenceWorkflowTest' \
  --tests '*VisitorPassPolicyTest' --no-daemon
```

Expected: compilation fails because workflow and policy types are absent.

- [ ] **Step 4: Implement the workflow with separate keys**

Use:

```kotlin
private const val OCR_RESULT = "analysis.ocr"
private const val DETECTION_RESULT = "analysis.detection"
private const val BARCODE_RESULT = "analysis.barcode"
```

Each `execute` block records its `AnalysisResult` and returns:

```kotlin
WorkReport.success(context)
```

After `flow.execute(context)`, require `WorkReport.Success`, then read all three typed values.
Do not expose or return `WorkContext` outside `ImageIntelligenceWorkflow`.
Use one checked extraction helper so a missing key or wrong value type becomes a stable
orchestration defect rather than a later null failure:

```kotlin
private inline fun <reified T : Any> WorkContext.requireResult(key: String): T =
    this[key]
        ?: throw ImageWorkflowException(
            reasonCode = "missing_workflow_result",
            message = "Workflow result is missing for key=$key.",
        )
```

- [ ] **Step 5: Implement aggregate status and policy**

Keep `ImageIntelligenceAggregator` purely deterministic. Keep `VisitorPassPolicy` free of
provider execution and HTTP types. Return:

```kotlin
internal data class VisitorPassDecision(
    val action: VisitorPassAction,
    val reasons: List<String>,
)
```

Use stable reason codes such as:

- `SENSITIVE_REGION_DETECTED`
- `INVALID_VISITOR_QR`
- `OCR_UNAVAILABLE`
- `DETECTION_FAILED`
- `FACE_COUNT_REQUIRES_REVIEW`
- `QR_COUNT_REQUIRES_REVIEW`

- [ ] **Step 6: Run GREEN and repeat the cancellation test**

```bash
./gradlew :spring-boot-image-intelligence-api:test \
  --tests '*ImageIntelligenceWorkflowTest' \
  --tests '*VisitorPassPolicyTest' --no-daemon
```

Expected: concurrency, partial results, missing-key failure, cancellation, aggregate, and policy
tests pass.

- [ ] **Step 7: Commit**

```bash
git add examples/spring-boot-image-intelligence-api/src/main \
  examples/spring-boot-image-intelligence-api/src/test
git commit -m "Preserve partial analysis before applying visitor policy" \
  -m "Constraint: WorkReport.Success records step completion, not provider success." \
  -m "Confidence: high" \
  -m "Scope-risk: moderate" \
  -m "Tested: parallel workflow, partial outcome, cancellation, aggregate, and policy tests"
```

## Task 6: Expose stable HTTP responses without workflow leakage

**Complexity:** Medium
**Depends on:** Tasks 2 and 5
**Pattern skills:** `bluetape-kotlin-patterns`, `test-driven-development`
**Rollback point:** remove web/service DTO layer while retaining tested domain orchestration.

**Files:**

- Create: `examples/spring-boot-image-intelligence-api/src/main/kotlin/io/bluetape4k/images/examples/spring/intelligence/model/ApiModels.kt`
- Create: `examples/spring-boot-image-intelligence-api/src/main/kotlin/io/bluetape4k/images/examples/spring/intelligence/service/ImageIntelligenceService.kt`
- Create: `examples/spring-boot-image-intelligence-api/src/main/kotlin/io/bluetape4k/images/examples/spring/intelligence/web/ImageIntelligenceController.kt`
- Create: `examples/spring-boot-image-intelligence-api/src/main/kotlin/io/bluetape4k/images/examples/spring/intelligence/web/ImageIntelligenceExceptionHandler.kt`
- Modify: `examples/spring-boot-image-intelligence-api/src/main/kotlin/io/bluetape4k/images/examples/spring/intelligence/config/ImageIntelligenceConfiguration.kt`
- Create: `examples/spring-boot-image-intelligence-api/src/test/kotlin/io/bluetape4k/images/examples/spring/intelligence/web/ImageIntelligenceControllerTest.kt`

- [ ] **Step 1: Write RED HTTP tests**

With MockMvc or the repository Spring test pattern, prove:

- `POST /api/images/intelligence` with generated QR under `demo` returns HTTP 200,
  aggregate `COMPLETED`, decision `ALLOW`, and provider identifiers;
- one injected lane failure returns HTTP 200 and aggregate `PARTIAL`;
- all required lanes unavailable/failed returns HTTP 200 and aggregate `FAILED`;
- missing part, empty file, unsupported type, MIME mismatch, malformed image, side overflow,
  and pixel overflow return stable 4xx `ProblemDetail`;
- unexpected workflow corruption returns sanitized 500 without raw exception text;
- JSON contains no `WorkContext`, `WorkReport`, stack trace, raw image bytes, or native path.

- [ ] **Step 2: Run RED**

```bash
./gradlew :spring-boot-image-intelligence-api:test \
  --tests '*ImageIntelligenceControllerTest' --no-daemon
```

Expected: compilation or context startup fails because the web layer is absent.

- [ ] **Step 3: Implement serializable API DTOs**

Use dedicated response types:

```kotlin
internal enum class AnalysisStatus {
    COMPLETED,
    EMPTY,
    UNAVAILABLE,
    FAILED,
}

internal data class OcrAnalysisResponse(
    val status: AnalysisStatus,
    val provider: String,
    val elapsedMillis: Long,
    val result: OcrResponse? = null,
    val reasonCode: String? = null,
) : Serializable

internal data class DetectionAnalysisResponse(
    val status: AnalysisStatus,
    val provider: String,
    val elapsedMillis: Long,
    val regions: List<DetectionResponse> = emptyList(),
    val reasonCode: String? = null,
) : Serializable

internal data class BarcodeAnalysisResponse(
    val status: AnalysisStatus,
    val provider: String,
    val elapsedMillis: Long,
    val items: List<BarcodeResponse> = emptyList(),
    val reasonCode: String? = null,
) : Serializable

internal data class ImageIntelligenceResponse(
    val requestId: String,
    val status: AggregateStatus,
    val decision: VisitorPassAction,
    val reasons: List<String>,
    val image: QualifiedImageResponse,
    val ocr: OcrAnalysisResponse,
    val detection: DetectionAnalysisResponse,
    val barcodes: BarcodeAnalysisResponse,
) : Serializable
```

All data classes implement `Serializable` and define `serialVersionUID`. Do not serialize
raw OCR engine objects, raw bytes, `Throwable`, `WorkContext`, or `WorkReport`.
The mapper must enforce these invariants:

```text
OCR COMPLETED -> result != null, reasonCode == null
OCR EMPTY     -> result == null, reasonCode == null
detection/barcode COMPLETED -> collection contains the mapped results
detection/barcode EMPTY     -> collection is empty, reasonCode == null
UNAVAILABLE or FAILED       -> payload is absent/empty, reasonCode != null
```

- [ ] **Step 4: Implement service, controller, and advice**

Controller:

```kotlin
@PostMapping(
    "/api/images/intelligence",
    consumes = [MediaType.MULTIPART_FORM_DATA_VALUE],
)
suspend fun analyze(
    @RequestParam("file") file: MultipartFile,
): ImageIntelligenceResponse =
    service.analyze(file)
```

`ImageIntelligenceService` receives
`requestIdProvider: () -> String = { UUID.randomUUID().toString() }` so production gets a
generated identifier and tests remain deterministic. It qualifies the file once, invokes
workflow, aggregates, applies policy, and maps DTOs.

Advice maps:

- `InvalidImageUploadException` → `400` or `413` based on exception subtype;
- missing multipart part → `400`;
- Spring multipart overflow → `413`;
- `ImageWorkflowException` → sanitized `500`;
- no raw exception message in response.

- [ ] **Step 5: Run GREEN**

```bash
./gradlew :spring-boot-image-intelligence-api:test \
  --tests '*ImageIntelligenceControllerTest' --no-daemon
```

Expected: success, partial, failed-envelope, input rejection, and sanitized error tests pass.

- [ ] **Step 6: Commit**

```bash
git add examples/spring-boot-image-intelligence-api/src/main \
  examples/spring-boot-image-intelligence-api/src/test
git commit -m "Expose image analysis as a stable partial-result API" \
  -m "Constraint: HTTP success reports envelope creation, not guaranteed business success." \
  -m "Confidence: high" \
  -m "Scope-risk: moderate" \
  -m "Tested: multipart success, partial, failed-envelope, input, and sanitized 500 tests"
```

## Task 7: Prove lifecycle, profile, and full example behavior

**Complexity:** High
**Depends on:** Tasks 1–6
**Pattern skills:** `bluetape-kotlin-patterns`, `kotlin-coroutines-skill`, `test-driven-development`
**Rollback point:** integration fixtures/tests can be reverted independently; production behavior remains covered by focused tests.

**Files:**

- Create: `examples/spring-boot-image-intelligence-api/src/test/kotlin/io/bluetape4k/images/examples/spring/intelligence/ImageIntelligenceApplicationTest.kt`
- Create: `examples/spring-boot-image-intelligence-api/src/test/kotlin/io/bluetape4k/images/examples/spring/intelligence/ImageIntelligenceCancellationTest.kt`
- Create: `examples/spring-boot-image-intelligence-api/src/test/kotlin/io/bluetape4k/images/examples/spring/intelligence/ImageIntelligenceObservabilityTest.kt`
- Modify: `examples/spring-boot-image-intelligence-api/src/test/kotlin/io/bluetape4k/images/examples/spring/intelligence/support/ImageIntelligenceFixtures.kt`

- [ ] **Step 1: Add application profile tests**

Prove the Spring context provides exactly:

```text
default       disabled OCR, disabled detector, ZXing
demo          fixture OCR, fixture detector, ZXing
native-ocr    Tesseract OCR, disabled detector, ZXing
```

Do not execute native OCR in the default test suite. Verify conflicting `demo,native-ocr`
profiles fail closed rather than relying on bean ordering.

- [ ] **Step 2: Add end-to-end generated-image tests**

Generate:

- a QR visitor pass producing `ALLOW`;
- a blank valid image producing policy `MANUAL_REVIEW`;
- a valid image with injected OCR failure preserving detection/barcode;
- a valid image with unavailable OCR/detector;
- malformed and oversized uploads rejected before providers.

Pin generated QR payload and dimensions and call actual `ZxingBarcodeReader`.

- [ ] **Step 3: Add real cancellation and concurrency proof**

Start a request/service coroutine with three controllable providers, cancel the parent, and assert:

- all provider jobs observe cancellation;
- no response DTO is produced;
- permits return to their initial count;
- a subsequent request completes;
- internal lane timeout still produces a response and does not cancel siblings.

- [ ] **Step 4: Add structured-log redaction proof**

Capture application logs for one completed request and one provider failure. Assert logs contain:

- request ID;
- provider ID;
- outcome status;
- timeout or elapsed milliseconds.

Assert logs do not contain the generated QR payload, OCR text, image bytes, native path,
exception message, or stack trace at the API boundary.

- [ ] **Step 5: Run targeted lifecycle tests**

```bash
./gradlew :spring-boot-image-intelligence-api:test \
  --tests '*ImageIntelligenceApplicationTest' \
  --tests '*ImageIntelligenceCancellationTest' \
  --tests '*ImageIntelligenceObservabilityTest' --no-daemon
```

Expected: all profile, end-to-end, cancellation, permit-recovery, subsequent-request, and
log-redaction tests pass.

- [ ] **Step 6: Run the whole example test task from clean test outputs**

```bash
./gradlew :spring-boot-image-intelligence-api:cleanTest \
  :spring-boot-image-intelligence-api:test --no-build-cache --no-daemon
```

Expected: all example tests pass with zero skipped default-path behavior and no native runtime.

- [ ] **Step 7: Commit**

```bash
git add examples/spring-boot-image-intelligence-api/src/test
git commit -m "Prove the integrated image example across failure boundaries" \
  -m "Constraint: Default CI remains deterministic and host independent." \
  -m "Confidence: high" \
  -m "Scope-risk: moderate" \
  -m "Tested: profile, generated QR, partial failure, cancellation, log redaction, and clean full example suite"
```

## Task 8: Add bilingual learning material, diagrams, and repository registration

**Complexity:** High
**Depends on:** Tasks 1–7
**Pattern skills:** `bluetape-writer`, `bluetape-diagram`
**Rollback point:** docs and workflow registration are independently reversible; do not remove tested implementation.

**Files:**

- Create: `examples/spring-boot-image-intelligence-api/README.md`
- Create: `examples/spring-boot-image-intelligence-api/README.ko.md`
- Create: `examples/spring-boot-image-intelligence-api/docs/images/readme-diagrams/image-intelligence-architecture.svg`
- Create: `examples/spring-boot-image-intelligence-api/docs/images/readme-diagrams/image-intelligence-architecture.png`
- Create: `examples/spring-boot-image-intelligence-api/docs/images/readme-diagrams/image-intelligence-interactions.svg`
- Create: `examples/spring-boot-image-intelligence-api/docs/images/readme-diagrams/image-intelligence-interactions.png`
- Modify: `README.md`
- Modify: `README.ko.md`
- Modify: `.github/workflows/Examples.yml`

- [ ] **Step 1: Load diagram and writer contracts**

Read `bluetape-diagram` and `bluetape-writer` fully. Instantiate every required diagram
checklist row before creating visual assets.

- [ ] **Step 2: Write equivalent English and Korean READMEs**

Both files must cover:

- visitor-pass scenario and non-goals;
- one qualification and one decode before fan-out;
- `WorkReport.Success` versus domain `Completed`;
- default, `demo`, and optional `native-ocr` profiles;
- request and `COMPLETED`, `PARTIAL`, `FAILED` response examples;
- provider timeout and concurrency properties;
- non-cooperative native timeout limitation;
- policy replacement for shipping and product labels;
- production gaps: auth, malware scanning, storage/deletion, privacy, retry/circuit breaker;
- source links to OCR, detection, barcode, workflow, tests, and relevant public articles.

Use natural Korean technical prose, not word-for-word translation.

- [ ] **Step 3: Create two dark technical diagrams**

Architecture diagram:

```text
multipart -> qualification -> dimension probe -> one decode
          -> OCR / detection / ZXing lanes
          -> aggregate -> visitor policy -> response
```

Interaction diagram:

```text
normal lane completion
one lane Failed while siblings complete
external cancellation propagated to every lane
```

Use card-and-connector style, readable arrowheads, enough vertical spacing, and no label crossing.
Keep SVG source and same-basename PNG. README displays PNG and links to SVG.

- [ ] **Step 4: Visually inspect SVG and PNG**

Run SVG validation and PNG conversion from the diagram skill. Inspect both PNGs at full size.
Reject:

- clipped labels;
- missing or reversed arrowheads;
- overlapping call labels and connector lines;
- fonts smaller than the diagram checklist minimum;
- SVG-only correctness that breaks after PNG conversion.

- [ ] **Step 5: Register root learning paths and Examples CI**

Add the new example after the existing OCR and barcode quickstarts in both root READMEs.
Add exactly one workflow matrix row:

```yaml
- example: spring-boot-image-intelligence-api
  gradle_tasks: :spring-boot-image-intelligence-api:test
```

Do not add BOM/catalog publication entries. Do not edit `docs/manual/manifest.yaml` or
release-pinned manual pages before the 0.4.0 manual cycle.

- [ ] **Step 6: Validate docs and workflow**

```bash
rg -n 'spring-boot-image-intelligence-api' \
  settings.gradle.kts AGENTS.md README.md README.ko.md .github/workflows/Examples.yml
actionlint .github/workflows/Examples.yml
find examples/spring-boot-image-intelligence-api/docs/images/readme-diagrams \
  -name '*.svg' -print0 | xargs -0 -n1 xmllint --noout
find examples/spring-boot-image-intelligence-api/docs/images/readme-diagrams \
  -name '*.svg' -exec sh -c 'test -f "${1%.svg}.png"' sh {} \;
git diff --check
```

Expected: every registration surface contains the example, workflow lint passes, SVG parses,
each SVG has a PNG peer, and diff check is clean.

- [ ] **Step 7: Commit**

```bash
git add AGENTS.md README.md README.ko.md .github/workflows/Examples.yml \
  examples/spring-boot-image-intelligence-api
git commit -m "Teach the integrated image workflow from runnable examples" \
  -m "Constraint: English and Korean docs and SVG/PNG assets must remain equivalent." \
  -m "Confidence: high" \
  -m "Scope-risk: moderate" \
  -m "Tested: actionlint, diagram validators, PNG inspection, registration search, and diff check"
```

## Task 9: Converge verification, review, lesson, and PR delivery

**Complexity:** High
**Depends on:** Tasks 1–8
**Pattern skills:** `verification-before-completion`, Type A review references
**Rollback point:** repair the failing task and rerun all dependent proof; do not create PR on stale evidence.

**Files:**

- Create: `docs/review/2026-07-27-issue-299-image-intelligence-api-verification.md`
- Create: `docs/review/2026-07-27-issue-299-image-intelligence-api-code-review.md`
- Create: `docs/lessons/2026-07-27-issue-299-image-intelligence-api.md`
- Modify if findings require repair: only files already named in Tasks 1–8

- [ ] **Step 1: Verify spec-to-implementation traceability**

Read the committed design, this plan, current diff, and tests. Build a table mapping every
acceptance criterion to source, test, docs, and command evidence. Any missing row returns to the
owning task.

- [ ] **Step 2: Run targeted and affected-module validation sequentially**

```bash
./gradlew :spring-boot-image-intelligence-api:cleanTest \
  :spring-boot-image-intelligence-api:test --no-build-cache --no-daemon
./gradlew :bluetape4k-images:test \
  :bluetape4k-images-ocr:test \
  :bluetape4k-images-barcode-api:test \
  :bluetape4k-images-barcode-zxing:test \
  :spring-boot-barcode-api:test \
  :spring-boot-ocr-api:test \
  :spring-boot-image-intelligence-api:test --no-daemon
./gradlew projects --no-daemon
./gradlew detekt --no-daemon
actionlint .github/workflows/Examples.yml
git diff --check
```

Expected: all commands pass. Do not run optional native OCR in parallel with anything else.
If an optional native proof is unavailable, record it as an explicit environment-dependent gap;
default behavior must already be covered.

- [ ] **Step 3: Run performance/stability proof**

Use focused tests to record:

- only one full decode per request;
- dimension rejection before decode;
- three provider lanes overlap;
- concurrency never exceeds configured permits;
- permits recover after failure, timeout, and cancellation;
- successful sibling results survive one provider failure;
- source bytes are not retained by `QualifiedImage`.

No production throughput claim or benchmark ranking is required. This is a bounded behavior proof.

- [ ] **Step 4: Complete six-perspective code review and integration**

Review current branch diff for:

- performance;
- stability;
- security;
- operator/Ops;
- developer/API;
- user/caller;
- main-session integration, documentation, release, and evidence.

Fix all P0/P1 findings, rerun affected tests and review passes, and record final `P0=0`, `P1=0`.
P2/P3 must be fixed, explicitly deferred with rationale, or filed as follow-up.

- [ ] **Step 5: Commit the durable lesson**

The lesson must include:

- why the example moved from workshop to the image producer repository;
- why workflow completion and business outcome are different axes;
- why expected provider failure is data but external cancellation is control flow;
- why dimension probing precedes decode;
- why strict native timeout needs process or remote isolation;
- verification evidence and future guard.

Commit the lesson before PR creation.

- [ ] **Step 6: Verify authorized PR metadata and publish exact head**

Authority is the approved plan for:

- repository: `bluetape4k/bluetape4k-image`;
- base: `develop`;
- head: `feat/issue-299-image-intelligence-api`;
- action: create PR only, not merge.

Push without force, read back the remote SHA, and verify it matches local HEAD.

- [ ] **Step 7: Create and verify the PR**

Create an English PR linked to #299. Assign `debop`; mirror milestone `0.4.0` and labels
`enhancement`, `documentation`. The final Markdown `##` heading must be:

```markdown
## DoD Status
```

Verify live with:

```bash
gh pr view --json number,url,headRefName,baseRefName,headRefOid,assignees,labels,milestone,body
```

- [ ] **Step 8: Wait for CI and re-read live review state**

Use live check conclusions on the exact PR head. After green CI, re-read reviews and unresolved
threads. Any new blocker returns to the owning task and reopens verification.

- [ ] **Step 9: Report merge-ready and stop**

Report:

- exact PR URL and head SHA;
- CI and current review evidence;
- tests and diagrams;
- P0=0/P1=0;
- lesson commit;
- remaining risks;
- checklist counts;
- `CG-16`, `CG-17`, and `CG-18` still pending.

Do not merge until the user provides a fresh explicit approval for that exact merge-ready head.
