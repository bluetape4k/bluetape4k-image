# 이미지 시그니처 헬퍼 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use `subagent-driven-development` to execute this plan task-by-task with review checkpoints.

**Goal:** PNG, JPEG, WebP 바이트 시그니처 검사를 공용 `ByteArray` extension으로 제공하고, 이미지 업로드 예제와 두 README가 이 계약을 사용하게 한다.

**Architecture:** `images` 모듈의 `io.bluetape4k.images` 패키지에 무복사 top-level extension 네 개를 추가한다. 예제의 기존 private 비교를 이 API로 교체하고, ABI 기준 파일과 영문·한국어 README를 갱신한다. 각 단계는 기존 MIME 결과, 실제 디코딩, 크기 제한을 보존한다.

**Tech Stack:** Kotlin, Gradle, JUnit 5, bluetape4k assertions, Kotlin ABI validator, Detekt.

---

## 승인 범위와 근거

- 설계: [`2026-10-12-image-signature-helpers-design.md`](../specs/2026-10-12-image-signature-helpers-design.md)
- 이슈: [Issue #698](https://github.com/bluetape4k/bluetape4k-image/issues/698)
- 저장소/기준/작업 브랜치: `bluetape4k/bluetape4k-image`, `develop`, `fix/issue-698-image-signatures`
- 시작 HEAD: `2d843c001956482b7c5a61ca22134819d4c904a1`
- 중단 지점: PR exact-head의 CI와 리뷰를 확인한 뒤 머지 승인 대기. 머지, integration 브랜치 동기화, worktree 정리, GNO 인덱싱은 실행하지 않는다.
- 쓰기 소유권: Kotlin 구현·테스트·ABI 파일은 구현 lane, README 두 locale은 문서 lane, 설계·계획·리뷰·lesson 통합은 주 세션 소유다. 외부 GitHub 작업은 주 세션만 수행한다.

## 변경 파일과 책임

| 경로 | 책임 |
|---|---|
| `images/src/main/kotlin/io/bluetape4k/images/ByteArraySignatureExtensions.kt` | 공개 prefix 및 이미지 시그니처 extension, 한국어 KDoc |
| `images/src/test/kotlin/io/bluetape4k/images/ByteArraySignatureExtensionsTest.kt` | prefix와 PNG/JPEG/WebP 경계·실패 테스트 |
| `api/bluetape4k-images.api` | 신규 공개 JVM 시그니처의 ABI 기준 |
| `examples/spring-boot-image-intelligence-api/src/main/kotlin/io/bluetape4k/images/examples/spring/intelligence/service/ImageUploadQualifier.kt` | 공용 extension을 사용하도록 기존 판별기 교체 |
| `examples/spring-boot-image-intelligence-api/src/test/kotlin/io/bluetape4k/images/examples/spring/intelligence/service/ImageUploadQualifierTest.kt` | 기존 MIME 결과와 디코딩 흐름 회귀 확인 |
| `images/README.md` | 영어 API 설명 및 업로드 한계 |
| `images/README.ko.md` | 동등한 한국어 API 설명 및 업로드 한계 |
| `docs/review/2026-10-12-image-signature-plan-review.md` | 통합 계획 리뷰와 SPW writer 증거 |
| `docs/review/2026-10-12-image-signature-code-review.md` | 최종 구현 및 PR-head 리뷰 근거 |
| `docs/lessons/2026-10-12-issue-698-image-signature-helpers.md` | 실제 경로·CI·검증에서 얻은 재발 방지 기록 |

## 실행 계획

모든 단계의 `Expected DoD`를 얻기 전에는 다음 단계로 진행하지 않는다. 프로덕션 코드는 해당 테스트가 기능 부재로 실패한 출력을 확인하기 전에는 작성하지 않는다. Task 1의 RED와 Task 2의 GREEN은 같은 executor가 연속으로 수행하는 하나의 TDD 작업이다. GREEN과 타깃 검증 전에는 커밋하거나 별도 리뷰를 시작하지 않는다.
계획 실행은 `subagent-driven-development` 절차를 따른다. 각 독립 작업은 새 `executor`가 맡고, 구현자는 타깃 검증·자체 검토와 한국어 Lore 커밋을 마친다. Task 1–2는 위에 정한 한 작업 단위로 처리한다. 이후 별도 `code-reviewer`가 설계 준수 여부를, 두 번째 독립 `code-reviewer`가 코드 품질을 검토한다. README·lesson만 다루는 문서 작업은 두 번째 검토에 `writer`를 쓴다. 발견 사항은 해당 작업에서 수정하고 reviewer 재검토를 통과한 뒤 다음 작업으로 이동하며, 이미 기록한 커밋은 reset이나 force-push로 다시 쓰지 않는다. 서로 의존하는 구현 작업은 병렬로 실행하지 않는다.

### 사전 게이트: 계획 리뷰 통합과 계획 커밋 (Task 1 이전)

- **Action:** 여섯 관점 Step 3-R 리뷰를 통합하고 `docs/review/2026-10-12-image-signature-plan-review.md`를 작성한다. P0/P1은 0건이어야 하며, P2/P3는 수정 후 재검토하거나 근거 있는 이월로 판정한다. 한국어 writer의 SPW-01..05를 통과한 뒤 spec, plan, plan review만 Lore 형식 한국어 커밋으로 기록한다.
- **Expected DoD:** 세 문서만 커밋되고 구현·테스트·ABI 파일은 포함되지 않는다. `git show --stat --format=fuller HEAD`와 parent diff로 커밋 범위를 확인한다.
- **Gate:** 이 사전 게이트가 PASS이고 계획 커밋이 확인되기 전에는 Task 1이나 코드 변경을 시작하지 않는다.

### Task 1: `hasPrefix` 계약을 테스트로 고정

**Files:**
- Create: `images/src/test/kotlin/io/bluetape4k/images/ByteArraySignatureExtensionsTest.kt`

- [ ] **Step 1: prefix 경계 테스트를 추가한다.**

```kotlin
package io.bluetape4k.images

import io.bluetape4k.assertions.shouldBeFalse
import io.bluetape4k.assertions.shouldBeTrue
import org.junit.jupiter.api.Test

class ByteArraySignatureExtensionsTest {
    @Test
    fun `matches prefix using unsigned byte values`() {
        byteArrayOf(0x89.toByte(), 0x50, 0x4E)
            .hasPrefix(intArrayOf(0x89, 0x50))
            .shouldBeTrue()
    }

    @Test
    fun `accepts an empty prefix`() {
        byteArrayOf().hasPrefix(intArrayOf()).shouldBeTrue()
    }

    @Test
    fun `rejects a mismatch and a shorter input`() {
        byteArrayOf(0x89.toByte(), 0x51).hasPrefix(intArrayOf(0x89, 0x50)).shouldBeFalse()
        byteArrayOf(0x89.toByte()).hasPrefix(intArrayOf(0x89, 0x50)).shouldBeFalse()
    }

    @Test
    fun `rejects prefix values outside the unsigned byte range`() {
        byteArrayOf(0xFF.toByte()).hasPrefix(intArrayOf(-1)).shouldBeFalse()
        byteArrayOf(0x00).hasPrefix(intArrayOf(256)).shouldBeFalse()
    }
}
```

  - **Action:** 위 테스트 파일만 만든다.
  - **Run:** `./gradlew :bluetape4k-images:test --tests "io.bluetape4k.images.ByteArraySignatureExtensionsTest" --console=plain`
  - **Expected DoD:** `hasPrefix` 미구현에 따른 unresolved reference로 실패한다. 테스트 import나 문법 오류가 원인이면 테스트를 고친 뒤 다시 실행한다.
  - **Step DoD:** 예상된 기능 부재 실패가 확인되면 RED 증거를 기록하고 같은 executor가 Task 2 GREEN으로 이어간다. 이 단계만으로 Task 완료·커밋·리뷰 판정을 하지 않는다.

### Task 2: prefix 구현과 GREEN 확인

**Files:**
- Create: `images/src/main/kotlin/io/bluetape4k/images/ByteArraySignatureExtensions.kt`
- Test: `images/src/test/kotlin/io/bluetape4k/images/ByteArraySignatureExtensionsTest.kt`

- [ ] **Step 1: `hasPrefix`만 구현한다.**

```kotlin
package io.bluetape4k.images

/**
 * 이 바이트 배열이 지정한 unsigned byte 값으로 시작하는지 확인합니다.
 *
 * 빈 prefix는 `true`이고, 배열이 짧거나 prefix 값이 `0..255` 범위를 벗어나면 `false`입니다.
 */
fun ByteArray.hasPrefix(prefix: IntArray): Boolean {
    if (size < prefix.size) return false

    for (index in prefix.indices) {
        val expected = prefix[index]
        if (expected !in 0..0xFF || (this[index].toInt() and 0xFF) != expected) {
            return false
        }
    }
    return true
}
```

  - **Action:** 새 프로덕션 파일에 위 함수만 추가한다.
  - **Run:** `./gradlew :bluetape4k-images:test --tests "io.bluetape4k.images.ByteArraySignatureExtensionsTest" --console=plain`
  - **Expected DoD:** Task 1의 빈 prefix, 일치, 불일치, 짧은 입력, `-1`, `256` 테스트가 모두 통과한다.
  - **Step DoD:** Task 1 RED 증거와 이 단계의 GREEN 결과를 같은 TDD 작업의 DoD로 기록한다. 테스트 파일과 구현 파일을 함께 커밋하고 이후 두 리뷰를 통과해야 Task 1–2 작업을 완료한다.

### Task 3: 형식별 시그니처 테스트와 extension 추가

**Files:**
- Modify: `images/src/test/kotlin/io/bluetape4k/images/ByteArraySignatureExtensionsTest.kt`
- Modify: `images/src/main/kotlin/io/bluetape4k/images/ByteArraySignatureExtensions.kt`

- [ ] **Step 1: 각 형식의 헤더 테스트를 먼저 추가한다.**

```kotlin
    @Test
    fun `identifies PNG header and rejects mismatch or truncation`() {
        val header = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        header.isPng().shouldBeTrue()
        header.copyOf().also { it[7] = 0 }.isPng().shouldBeFalse()
        header.copyOf(7).isPng().shouldBeFalse()
    }

    @Test
    fun `identifies JPEG prefix and rejects mismatch or truncation`() {
        val header = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
        header.isJpeg().shouldBeTrue()
        byteArrayOf(0xFF.toByte(), 0xD8.toByte()).isJpeg().shouldBeFalse()
        byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x00).isJpeg().shouldBeFalse()
    }

    @Test
    fun `identifies WebP markers at their required offsets`() {
        val header = byteArrayOf(
            0x52, 0x49, 0x46, 0x46, 0x00, 0x00, 0x00, 0x00, 0x57, 0x45, 0x42, 0x50,
        )
        header.isWebp().shouldBeTrue()
        header.copyOf(11).isWebp().shouldBeFalse()
        header.copyOf().also { it[0] = 0 }.isWebp().shouldBeFalse()
        header.copyOf().also { it[8] = 0 }.isWebp().shouldBeFalse()
        header.copyOf().also {
            it[4] = 0xFF.toByte()
            it[5] = 0xFF.toByte()
            it[6] = 0xFF.toByte()
            it[7] = 0xFF.toByte()
        }.isWebp().shouldBeTrue()
    }
```

  - **Action:** 테스트만 추가하고 API 구현은 아직 바꾸지 않는다.
  - **Run:** 위와 같은 타깃 테스트 명령을 실행한다.
  - **Expected DoD:** 신규 `isPng`, `isJpeg`, `isWebp` 참조가 아직 없어 컴파일 단계에서 실패한다.
  - **Step DoD:** `PASS`는 실패가 새 extension 부재 때문임을 확인한 경우에만 기록한다.

- [ ] **Step 2: 형식별 API와 상수를 추가한다.**

```kotlin
private val PNG_SIGNATURE = intArrayOf(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
private val JPEG_SIGNATURE = intArrayOf(0xFF, 0xD8, 0xFF)
private const val WEBP_HEADER_SIZE: Int = 12

/** PNG 파일 시그니처로 시작하는지 확인합니다. 이 검사는 전체 페이로드의 유효성이나 디코딩 성공을 보증하지 않습니다. */
fun ByteArray.isPng(): Boolean = hasPrefix(PNG_SIGNATURE)

/** JPEG 파일 시그니처로 시작하는지 확인합니다. 이 검사는 전체 페이로드의 유효성이나 디코딩 성공을 보증하지 않습니다. */
fun ByteArray.isJpeg(): Boolean = hasPrefix(JPEG_SIGNATURE)

/** 12바이트 이상의 WebP 헤더에서 `RIFF`와 offset 8의 `WEBP`를 확인합니다. 전체 페이로드 검증은 수행하지 않습니다. */
fun ByteArray.isWebp(): Boolean =
    size >= WEBP_HEADER_SIZE &&
            (this[0].toInt() and 0xFF) == 0x52 &&
            (this[1].toInt() and 0xFF) == 0x49 &&
            (this[2].toInt() and 0xFF) == 0x46 &&
            (this[3].toInt() and 0xFF) == 0x46 &&
            (this[8].toInt() and 0xFF) == 0x57 &&
            (this[9].toInt() and 0xFF) == 0x45 &&
            (this[10].toInt() and 0xFF) == 0x42 &&
            (this[11].toInt() and 0xFF) == 0x50
```

  - **Action:** private 상수와 공개 함수만 추가하고 RIFF 길이 필드는 검증하지 않는다.
  - **Run:** `./gradlew :bluetape4k-images:test --tests "io.bluetape4k.images.ByteArraySignatureExtensionsTest" --console=plain`
  - **Expected DoD:** PNG/JPEG/WebP의 일치, 불일치, 짧은 입력, unsigned PNG `0x89`, WebP offset 및 12바이트 헤더 전용 입력 테스트가 통과한다.
  - **Step DoD:** `PASS`는 타깃 테스트 클래스 전체가 성공한 경우에만 기록한다.

### Task 4: 업로드 예제를 공용 API로 전환

**Files:**
- Modify: `examples/spring-boot-image-intelligence-api/src/main/kotlin/io/bluetape4k/images/examples/spring/intelligence/service/ImageUploadQualifier.kt`
- Test: `examples/spring-boot-image-intelligence-api/src/test/kotlin/io/bluetape4k/images/examples/spring/intelligence/service/ImageUploadQualifierTest.kt`

- [ ] **Step 1: 기존 테스트로 현재 MIME 동작을 고정한다.**
  - **Action:** 프로덕션 변경 전에 PNG, JPEG, WebP의 qualifier 테스트를 실행한다.
  - **Run:** `./gradlew :spring-boot-image-intelligence-api:test --tests "io.bluetape4k.images.examples.spring.intelligence.service.ImageUploadQualifierTest" --console=plain`
  - **Expected DoD:** 현재 PNG/JPEG/WebP MIME 반환과 기존 업로드 검증 테스트가 통과한다.
  - **Step DoD:** `PASS`는 해당 테스트 클래스의 성공 출력으로 기록한다.

- [ ] **Step 2: 기존 private 비교 코드를 공용 extension 호출로 교체한다.**

```kotlin
import io.bluetape4k.images.isJpeg
import io.bluetape4k.images.isPng
import io.bluetape4k.images.isWebp

private fun detectMediaType(bytes: ByteArray): String? =
    when {
        bytes.isPng() -> MediaType.IMAGE_PNG_VALUE
        bytes.isJpeg() -> MediaType.IMAGE_JPEG_VALUE
        bytes.isWebp() -> "image/webp"
        else -> null
    }
```

  - **Action:** PNG/JPEG/WebP 검사 순서를 유지하고 예제의 private `hasPrefix`, `isWebp`, 시그니처 배열 상수만 제거한다. 크기 제한, 차원·메타데이터 판독기, 디코더 동작과 취소 전파는 보존한다. 기본 byte-array metadata adapter가 반환한 `EOFException`만 잘린 입력으로 분류해 `MalformedImageProbeException`으로 전달하고, 다른 probe 실패는 기존 내부 실패 분류를 유지한다. 공용 metadata 결과 계약은 변경하지 않는다.
  - **Run:** 앞의 `ImageUploadQualifierTest` 명령을 다시 실행한다.
  - **Expected DoD:** 세 형식의 MIME 결과와 content type 불일치 거부를 확인한다. 선언된 크기 제한은 `getBytes()` 호출 전에 검사하고, 실제 바이트 크기는 차원 판독 전에 검사한다. 차원 판독 후 변의 길이 제한과 픽셀 수 제한을 각각 검사하고, 하나라도 초과하면 디코더를 호출하지 않는다. 모든 제한을 통과한 경우에만 실제 디코더를 호출한다. 이 순서와 거부는 `ImageUploadQualifierTest`에서 읽기·판독·디코딩 호출 횟수로 확인한다.
  - **회귀 조건:** PNG/JPEG/WebP 헤더만 있는 입력은 각 시그니처 헬퍼가 `true`를 반환하더라도 기본 probe 경로에서 정확히 `reasonCode == "image_not_decodable"`로 거부하고 디코더를 호출하지 않는다. 기존 probe 주입 경로도 세 형식 모두 같은 reason code로 거부하는지 확인한다. 실제 관찰한 JPEG `EOFException` 외의 probe 실패 분류는 기존 동작을 유지한다. 기존 `rethrows cancellation from dimension probing` 및 `rethrows cancellation while reading upload bytes` 테스트도 유지해 취소 예외가 삼켜지지 않는지 확인한다.
  - **Step DoD:** `PASS`는 타깃 예제 테스트 클래스 전체가 성공한 경우에만 기록한다.

### Task 5: ABI 기준과 양쪽 README 갱신

**Files:**
- Modify: `api/bluetape4k-images.api`
- Modify: `images/README.md`
- Modify: `images/README.ko.md`

- [ ] **Step 1: Kotlin ABI 기준을 현재 public API에 맞춘다.**
  - **Action:** 구현을 끝낸 뒤 모듈 전용 ABI 갱신 작업을 실행한다.
  - **Run:** `./gradlew :bluetape4k-images:updateKotlinAbi --console=plain`
  - **Expected DoD:** `api/bluetape4k-images.api`에 새 확장 함수 네 개만 공개 ABI로 기록된다. unrelated module dump는 바뀌지 않는다.
  - **Step DoD:** 기준 파일 diff가 새 함수 서명과 필요한 파일 facade 변경으로 한정되면 `PASS`다.

- [ ] **Step 2: 영어 README에 시그니처 API와 한계를 설명한다.**
  - **Action:** `images/README.md`의 `Supported Image Formats` 절 아래에 네 import와 설계서의 동일 사용 예제를 추가한다. PNG/JPEG/WebP 판별에는 형식별 헬퍼를 우선 쓰고, `hasPrefix`는 다른 명시적 접두어 검사에 쓰도록 예제 앞에서 선택 기준을 설명한다. 코드 예제 바로 앞뒤에 이 API는 헤더 식별만 수행하고 업로드 검증을 대신하지 않는다는 안내를 둔다. 시그니처 검사만으로 업로드 안전성이나 디코딩 성공을 보장하지 않으며 인코딩 크기·디코딩 차원·픽셀 제한과 디코더 검증을 이어서 해야 한다고 쓴다.
  - **Expected DoD:** 코드에서 실제 제공하는 이름만 사용하고 API 선택 기준과 한계 설명이 설계 계약과 일치한다.
  - **Step DoD:** 링크·식별자·설명 read-back이 완료되면 `PASS`다.

- [ ] **Step 3: 한국어 README에 동등한 설명을 추가한다.**
  - **Action:** `images/README.ko.md`의 `이미지 포맷 지원` 절에 영어 README와 같은 import와 코드 블록을 추가한다. PNG/JPEG/WebP 판별에는 형식별 헬퍼를 우선 쓰고, `hasPrefix`는 다른 명시적 접두어 검사에 쓰도록 예제 앞에서 선택 기준을 설명한다. 코드 예제 바로 앞뒤에 이 API는 헤더 식별만 수행하고 업로드 검증을 대신하지 않는다는 안내를 둔다. 한계 문장은 자연스러운 한국어로 쓴다.
  - **Expected DoD:** 두 README의 API명, 순서, 예제 코드가 같고 API 선택 기준과 독자용 prose만 locale에 맞게 다르다.
  - **Step DoD:** 코드 블록 비교와 한국어 자연스러움/용어 검사가 완료되면 `PASS`다.

- [ ] **Step 4: API 기준을 검증한다.**
  - **Action:** Kotlin ABI 기준 검사와 public API production 기준 검사를 실행한다.
  - **Run:** `./gradlew :bluetape4k-images:checkKotlinAbi checkProductionAbi --console=plain`
  - **Expected DoD:** 두 ABI 검사가 모두 성공한다.
  - **Step DoD:** exit code 0과 변경된 기준 파일 read-back으로 `PASS`를 기록한다.

### Task 6: 전체 타깃 검증과 성능·안정성 점검

**Files:** Task 1–5의 변경 파일 전체.

- [ ] **Step 1: 모듈 테스트를 실행한다.**
  - **Action:** 새 API와 기존 `images` 회귀 테스트를 실행한다.
  - **Run:** `./gradlew :bluetape4k-images:test --console=plain`
  - **Expected DoD:** 테스트 실패 0건이며 skip은 이름과 환경 사유를 확인해 기록한다.
  - **Step DoD:** 전체 타깃 테스트 결과를 읽고 기록하면 `PASS`다.

- [ ] **Step 2: 예제 테스트를 순차 실행한다.**
  - **Action:** 이미지 인텔리전스 예제 테스트를 독립 실행한다.
  - **Run:** `./gradlew :spring-boot-image-intelligence-api:test --console=plain`
  - **Expected DoD:** qualifier, OCR/barcode 오케스트레이션의 타깃 예제 테스트가 성공한다.
  - **Step DoD:** 출력과 테스트 수를 기록하면 `PASS`다.

- [ ] **Step 3: 정적 검사와 whitespace 검사를 실행한다.**
  - **Action:** 저장소 Detekt와 patch 검사를 실행한다.
  - **Run:** `./gradlew detekt --console=plain` 및 `git diff --check`
  - **Expected DoD:** 두 명령이 성공하고 신규 warning/error가 없다.
  - **Step DoD:** 각 종료 코드와 결과를 기록하면 `PASS`다.

- [ ] **Step 4: 할당 및 경계 계약을 정적으로 확인한다.**
  - **Action:** 새 시그니처 구현에 `copyOfRange`, 임시 `ByteArray`/`IntArray`, 위험한 인덱스 접근이 없는지 읽고, 경계 테스트가 구현 계약과 일치하는지 확인한다.
  - **Expected DoD:** prefix 길이 및 WebP 길이를 검사한 뒤 직접 인덱스 비교를 하고, `-1`/`256`, 빈 prefix, 헤더만 있는 입력의 계약을 검증한다.
  - **Step DoD:** 전체 `ByteArraySignatureExtensions.kt`와 테스트를 확인해 증거를 남기면 `PASS`다.

- [ ] **Step 5: Step 4-P 성능·안정성 스캔을 완료한다.**
  - **Action:** `~/.codex/skills/bluetape-full-feature/references/performance-stability-scan.md`를 읽고 빈번한 비교 경로의 할당, 경계 인덱스 안전성, 업로드 판별기의 디코더·크기 제한 회귀를 점검한다.
  - **Expected DoD:** 신규 비교 API는 입력별 임시 배열을 만들지 않고, 직접 인덱스 접근 전에 길이를 확인한다. benchmark는 정량 성능 향상을 주장하지 않으므로 N/A로 기록한다.
  - **Step DoD:** 스캔 결과, 위험 신호, 수정·재검증 또는 근거 있는 N/A를 기록하면 `PASS`다.

- [ ] **Step 6: N/A 트리거와 실행된 CI 범위를 기록한다.**
  - **Action:** 취소 전파 회귀는 기존 업로드 판별기 동작이므로 타깃 예제 테스트에 포함한다. 새 resource lifecycle, native/JNI, Testcontainers, dependency/catalog, module registration 변화 여부를 diff로 확인하고, benchmark와 PR의 `CI` 및 `Examples` workflow 실행 범위를 기록한다.
  - **Expected DoD:** dimension probe와 upload read 중 `CancellationException` 전파 테스트는 PASS여야 한다. 새 resource lifecycle, native/JNI, Testcontainers, dependency/catalog, module registration 검증은 구체적인 변경 범위 근거와 함께 N/A다. PR의 `images/**` 경로는 `Examples.yml` 전체 workflow를 실행하고, `test-examples`의 7개 matrix 예제를 `max-parallel: 1`로 순차 실행한 뒤 `Examples Status`에서 집계한다. 메인 CI는 `changes`의 `images` 감지 결과가 `true`인지, `test-images`가 skip되지 않았는지 확인하고 `CI Status`, `Build (compile only)`의 `checkProductionAbi`, `Test / images` 결과를 집계한다.
  - **Step DoD:** 로컬 회귀와 exact-head CI의 개별·집계 결과가 구분되고 N/A에 변경 범위 근거가 있으면 `PASS`다.

### Task 7: 구현 리뷰와 lesson

**Files:**
- Review: 최종 변경 전체
- Create: `docs/review/2026-10-12-image-signature-code-review.md`
- Create: `docs/lessons/2026-10-12-issue-698-image-signature-helpers.md`

- [ ] **Step 1: 여섯 독립 관점과 통합 리뷰를 실행한다.**
  - **Action:** 최신 base/head diff를 성능, 안정성, 보안, 운영, 개발자/API, 사용자/호출자 관점으로 리뷰하고 주 세션에서 심각도와 중복을 통합한다.
  - **Expected DoD:** 통합 표에 파일·줄·근거·조치가 있고 P0=0, P1=0이다. P2/P3는 수정, 근거 있는 이월 또는 후속 이슈로 결정한다.
  - **Step DoD:** reviewer 응답을 로컬 diff로 확인하고 SPW-01..05를 기록하면 `PASS`다.

- [ ] **Step 2: 한국어 lesson을 작성한다.**
  - **Action:** 프로젝트 경로 오류 `:images:test`와 실제 `:bluetape4k-images:test`, 별도 예제 workflow, API 기준 갱신, 확인한 결과와 재발 방지 규칙을 사실대로 기록한다.
  - **Expected DoD:** lesson은 issue, 결정, 실제 실수, 원인, 검증, 재발 방지 규칙을 포함하고 추정된 실패를 쓰지 않는다.
  - **Step DoD:** bluetape-writer SPW-01..05 및 KO-01..07 검사를 통과하면 `PASS`다.

### Task 8: 한국어 커밋, push, PR, exact-head CI

**Files:** Task 1–7의 승인된 변경만.

- [ ] **Step 1: 로컬 전달 게이트를 다시 읽는다.**
  - **Action:** 현재 issue, base, feature branch, dirty diff, 리뷰·테스트 증거와 PR 생성 authority를 재확인한다.
  - **Expected DoD:** issue #698이 열려 있고 repository `bluetape4k/bluetape4k-image`, base `develop`, head `fix/issue-698-image-signatures`가 일치한다. 요청 또는 승인된 계획이 이 repository/base/head의 PR 생성을 명시하고 CG-01..10이 완료됐음을 확인해 CG-11 authority gate를 통과한다.
  - **Step DoD:** 기준 정보와 PR 생성 authority, 앞선 공통 게이트가 확인되면 `CG-11 PASS`다.

- [ ] **Step 2: 작업별 Lore 커밋과 별도 리뷰·lesson 커밋을 확인한다.**
  - **Action:** Task 1–2 TDD 단위의 단일 GREEN 구현 커밋과 Task 3–5 변경 커밋이 각각 해당 executor 작업과 검토 수정을 포함하는지 확인한다. Task 6은 검증 증거만 기록하고 별도 구현 커밋을 만들지 않는다. Task 7의 통합 코드 리뷰와 lesson은 각각 별도 한국어 Lore 커밋으로 기록해 구현 작업을 되돌려도 검토·재발 방지 기록이 보존되게 한다.
  - **Expected DoD:** Task 1–2는 RED 테스트와 GREEN 구현을 함께 담은 Lore 커밋 하나를 만들고, Task 3–5는 각 변경 task별 구현 Lore 커밋을 둔다. Task 6은 테스트·정적 검사 결과를 기록하되 별도 커밋은 만들지 않는다. 리뷰 후 수정이 필요하면 해당 작업의 후속 Lore 커밋을 둔다. 각 commit trailer에 이유, 제약, confidence, scope risk, tested/not-tested를 기록하고 unrelated 파일이 없다. 각 커밋과 parent diff를 read-back한다. rollback 때는 구현 task 커밋을 역순으로 `git revert`하고 review/lesson 커밋은 보존한다. revert commit은 `git show --check <revert-commit>`과 `git show --stat --oneline <revert-commit>`으로 공백 및 복구 파일 범위를 read-back한다. 이후 `./gradlew :bluetape4k-images:checkKotlinAbi checkProductionAbi --console=plain`, `./gradlew :spring-boot-image-intelligence-api:test --tests "io.bluetape4k.images.examples.spring.intelligence.service.ImageUploadQualifierTest" --console=plain`, `./gradlew :bluetape4k-images:test --console=plain`을 다시 실행한다.
  - **Step DoD:** 각 commit 파일 목록과 parent diff를 확인하면 `PASS`다.

- [ ] **Step 3: feature branch를 push하고 remote head를 확인한다.**
  - **Action:** `fix/issue-698-image-signatures`를 origin에 push한다.
  - **Expected DoD:** 원격 feature branch SHA가 검증한 로컬 HEAD와 일치한다.
  - **Step DoD:** push 결과와 두 SHA가 일치하면 common-gates `CG-12 PASS`다.

- [ ] **Step 4: PR 생성 직전 CG-12A 지침을 새로 확인한다.**
  - **Action:** `gh pr create` 또는 PR update 직전에 현재 사용자·workspace·repository/worktree `AGENTS.md` 계층, 선택된 leaf skill, `common-gates.md`, `pr-body-step-dod.md`, Issue #698 metadata를 다시 읽고 시작 시점의 `CG-01` 지침·대상 기준 정보와 비교한다.
  - **Expected DoD:** 적용 경로와 범위, 읽은 시각 또는 content hash, PR 규칙, live issue metadata, drift 여부를 기록한다. drift가 있으면 checklist를 갱신하고 영향받는 게이트를 다시 검증한다.
  - **Step DoD:** 무변경 또는 재검증 결과가 확인될 때 `CG-12A PASS`이며, 그렇지 않으면 PR 생성 전 중단한다.

- [ ] **Step 5: PR을 만들고 metadata/body를 다시 읽는다.**
  - **Action:** `develop`을 대상으로 Issue #698에 연결한 한국어 PR을 생성한다. 담당자는 `debop`, label은 live issue의 `enhancement`, milestone은 issue의 현재 값(null)이면 비워 둔다. 본문은 `~/.codex/skills/bluetape-workflow/templates/pr-body-step-dod.md`를 사용한다.
  - **Expected DoD:** PR의 repository/base/head와 본문, 담당자, label을 `gh pr view`로 다시 읽는다. 템플릿의 한국어 섹션과 common-gates 순서의 DoD 표를 사용하고 `## DoD Status`를 마지막 섹션으로 둔다.
  - **Step DoD:** remote head가 로컬 HEAD와 같고 PR metadata/body가 정확하면 common-gates `CG-13 PASS`다.

- [ ] **Step 6: PR diff를 여섯 관점으로 재검토한다.**
  - **Action:** PR 생성 뒤 실제 PR diff, live review, unresolved thread를 기준으로 성능, 안정성, 보안, 운영, 개발자/API, 사용자/호출자 여섯 독립 리뷰와 주 세션 통합 검토를 실행한다. P0/P1 또는 `REQUEST_CHANGES`가 있으면 Task 1–7의 수정·검증·해당 관점 재리뷰를 반복하고 PR 본문 DoD를 갱신한다.
  - **Expected DoD:** 미해결 P0/P1과 review thread가 없고, `docs/review/2026-10-12-image-signature-code-review.md`의 PR-head 부록, 통합 리뷰 및 한국어 PR prose writer gate의 SPW-01..05가 통과한다. 단일 maintainer `debop` lane의 human-review 하위 항목은 근거를 남겨 `N/A (single-developer lane)`로 기록하되, exact-head CI, live review/thread read-back, 독립 최종 코드 리뷰와 적용 가능한 artifacts는 모두 완료한다.
  - **Step DoD:** live review/thread read-back, 통합 결과와 writer 증거가 최신 PR HEAD에 연결되면 `PASS`다.

- [ ] **Step 7: exact-head CI와 최종 CG-14/15 보고를 완료한다.**
  - **Action:** `CI`와 `Examples` workflow의 `headSha`가 PR HEAD와 일치하는지 확인한다. CI에서는 `changes`의 `images=true`, `Test / images` 비-skipped 성공, `Build (compile only)`와 `checkProductionAbi` 성공, `CI Status` 성공을 각각 읽는다. Examples에서는 7개 matrix 결과가 모두 성공하고 `Examples Status` 집계가 성공했는지 확인한다. CI 성공 후 live reviews와 unresolved threads를 다시 읽어 최종 코드 리뷰와 artifact evidence를 정확한 HEAD에 연결한다. PR 본문의 `## DoD Status`를 증거에 맞춰 update한다.
  - **Expected DoD:** 워크플로별 run `headSha`, 모든 7개 `Test / <example>` 결론, `Examples Status`, `images` 변경 감지, `Test / images`, `Build (compile only)`/`checkProductionAbi`, `CI Status`, live reviews/threads의 결과를 명시적으로 확인한다. `## DoD Status`에는 `Required checks: X/Y; N/A: N; Blocked: 0`을 공통 게이트 계약에 맞게 재계산해 기록한다. single-developer human-review 하위 항목은 근거와 함께 N/A로 한 번 계수하고, CG-14는 PASS로 둔다. CG-15는 merge-ready 보고를 마친 뒤 PASS, CG-16은 fresh merge approval 전까지 PENDING, CG-17, CG-17A, CG-17B, CG-18은 common-gates 순서대로 unchecked로 남긴다. GNO는 사용자가 수동으로 수행할 post-merge 항목이므로 PENDING으로 유지하고, 연기했다는 이유만으로 N/A 처리하지 않는다.
  - **Step DoD:** 정확한 PR HEAD에 모든 적용 CI와 최종 review/thread/artifact evidence가 결합되고 PR 본문 DoD 수치가 일치하면 `CG-14 PASS`, merge-ready report가 게시되면 `CG-15 PASS`다. `CG-16 PENDING`에서 멈추며 merge를 실행하지 않는다.

## 위험, 롤백, 검증 제외

| 위험 | 신호 | 완화·복구 |
|---|---|---|
| 헤더만 맞는 공격성 입력이 형식 판별을 통과한다. | 시그니처 바이트만 있는 테스트가 `true`를 반환한다. | 형식 판별 계약을 KDoc/README에 명시하고, 업로드 판별기의 크기·차원·픽셀 제한과 실제 디코더 검증을 보존한다. |
| signed `Byte`, 짧은 배열, offset 오류가 오분류나 예외를 만든다. | `0x89`, 길이 경계 또는 WebP 마커 테스트 실패 | 인덱스 비교에서 unsigned 변환, 사전 길이 확인, 각 경계 회귀 테스트를 유지한다. |
| public ABI dump가 구현과 불일치한다. | `checkKotlinAbi` 또는 `checkProductionAbi` 실패 | `updateKotlinAbi` 결과를 검토하고 네 공개 함수만 반영한다. 구현 변경을 되돌릴 때는 구현 task 커밋을 역순으로 `git revert`하고 `reset`/force-push를 하지 않는다. 이미 push한 경우 revert commit으로 PR head를 갱신한 뒤 영향받는 review와 exact-head CI를 다시 실행하고 아래 롤백 검증 명령을 확인한다. |
| 이미지 예제 테스트가 메인 `CI`에 포함된다고 오판한다. | PR에 `Examples` workflow가 없거나 대상 행이 skip됨 | path filter와 workflow run을 exact head에서 확인한다. 로컬 예제 테스트를 별도로 실행한다. |
| 문서 예제가 API/보안 계약과 달라진다. | locale 간 코드 차이, 시그니처 검사만으로 디코더 성공을 보장하는 표현 | 예제 코드 블록을 비교하고 KDoc/README에서 헤더만 확인한다는 한계를 일치시킨다. |

- 롤백은 Task 1–2의 TDD 커밋과 Task 3–5의 변경 커밋만 최신 순서부터 각각 `git revert`해 되돌린다. Task 6은 검증 전용이므로 롤백 대상 커밋이 없다. `reset`과 force-push는 금지하고 별도 spec/plan/review/lesson 커밋은 이력과 검토 근거로 보존한다. 이미 push했다면 revert commit을 push해 PR head를 갱신하고 영향을 받는 review와 exact-head CI를 다시 실행한다. 모든 롤백에서 `git show --check <revert-commit>`으로 revert patch의 공백을 검사하고, `git show --stat --oneline <revert-commit>`으로 복구 파일이 해당 구현 범위인지 확인한다. 이어서 `./gradlew :bluetape4k-images:checkKotlinAbi checkProductionAbi --console=plain`, `./gradlew :spring-boot-image-intelligence-api:test --tests "io.bluetape4k.images.examples.spring.intelligence.service.ImageUploadQualifierTest" --console=plain`, `./gradlew :bluetape4k-images:test --console=plain`을 실행한다.
- 취소 전파는 기존 업로드 판별기 계약이므로 회귀 테스트를 적용한다. 새 resource lifecycle, native backend, Testcontainers 동작은 수정 파일과 call graph에 해당하지 않아 근거와 함께 N/A다.
- benchmark는 성능 수치 개선을 주장하지 않으므로 실행하지 않는다. 무할당 비교 요구는 코드 검사로 확인하고 benchmark 수치를 근거로 주장하지 않는다.
- Step 3-P 위험 예측은 빈번한 비교 경로의 할당, 짧거나 범위를 벗어난 입력, 형식 헤더만 일치하는 업로드 입력을 상위 위험으로 본다. 신호·완화·재실행 지점은 이 절의 표와 Task 6 정적/성능·안정성 점검에 지정하며, 실패 시 해당 헬퍼 및 업로드 판별기 테스트부터 다시 실행한다.
- GNO는 사용자가 수동으로 처리하기로 한 경계이므로 실행하지 않는다. 머지·로컬 동기화·정리는 새 머지 승인이 있기 전까지 실행하지 않는다.

## 수용 기준 추적

| 설계 수용 기준 | 구현/검증 단계 |
|---|---|
| 공용 네 extension과 private 시그니처 | Task 1–3, ABI 기준 갱신·검사는 Task 5 |
| prefix 및 PNG/JPEG/WebP 성공·실패·경계 사례 | Task 1–3, 전체 모듈 테스트 |
| 업로드 판별기의 MIME 값·순서, 선언/실제 바이트 크기, 변의 길이·픽셀 수 제한, 디코더 순서 보존 | Task 4, 예제 테스트에서 읽기·판독·디코딩 호출 횟수 확인 |
| PNG/JPEG/WebP 헤더만 있는 입력의 정확한 `image_not_decodable` 거부와 취소 전파 | Task 4 및 Task 6, 예제 회귀 테스트 |
| KDoc과 두 언어 README의 동등 예제, 형식별 API 선택 기준 및 보안 한계 | Task 3, Task 5, Task 6; 헤더만 확인한다는 경고를 예제와 연결 |
| 모듈·예제 테스트, ABI·정적 검사, 독립 리뷰 | Task 5–7 |
| 한국어 commit, Issue #698 및 PR #699 연결, exact-head CI, merge hold | Task 8 |

## 계획 리뷰 통합 상태

| 관점 | 리뷰 상태 | 결과와 현재 처리 |
|---|---|---|
| 성능 | PASS | 배열 복사 없는 비교 요구를 확인했고 추가 계획 수정은 없었다. |
| 안정성 | PASS | 헤더 판별과 페이로드 검증의 경계, 취소 전파, 업로드 판별기 회귀 조건을 확인했다. |
| 보안 | PASS | 업로드 제한과 디코더 검증을 보존하며 헤더 일치만으로 안전성을 보증하지 않는 계약을 확인했다. |
| 운영 | PASS | CG-11/12, 계획 커밋 순서, rollback 검증, CI·Examples exact-head 확인, GNO 및 머지 보류를 재검토했다. |
| 개발자/API | PASS | additive ABI, unsigned prefix 범위, 공개 범위와 함수 서명을 확인했다. |
| 사용자/호출자 | PASS — 독립 재검토 완료 | 최초 P3는 설계 예제, 수용 기준, 영·한 README 계획에 선택 기준을 추가해 해결했다. 재검토에서 P0–P3 추가 발견이 없었고 헤더 판별과 페이로드 검증의 경계도 확인했다. |

운영 리뷰에서 발견된 절차·게이트 불일치는 CG-11/12 매핑, Task 1 이전 계획 커밋 게이트, revert patch·파일 범위·ABI·테스트 확인 명령, CG-12A 및 exact-head CI/Examples 세부 확인으로 반영했다. 세부 발견 근거와 처리표는 [`docs/review/2026-10-12-image-signature-plan-review.md`](../../review/2026-10-12-image-signature-plan-review.md)에 기록한다.

## Plan 작성자 self-review

- 각 설계 수용 기준은 위 traceability 표에서 실행 task와 명령에 연결했다.
- task는 spec/plan 승인·리뷰 및 커밋 후에만 코드 구현을 시작한다.
- 각 코드 변경은 정확한 파일과 RED/GREEN 명령 및 기대 결과가 있다.
- CI 경로는 `CI Status`가 확인하는 `images` 감지, `Build (compile only)`/`checkProductionAbi`, `Test / images`와 `Examples`의 7개 matrix 결과/집계 job을 분리해 기록했다.
- 변경하지 않는 모듈, dependency/catalog, schema, workflow, lifecycle/native/Testcontainers 경계와 구현 전용 rollback을 명시했다. 기존 cancellation 전파는 예제 회귀 테스트로 보존한다.
- 한국어 terminology audit과 Markdown 구조·상대 링크·공백 검사는 통합 writer review에서 완료했고, 세 문서의 SPW/KO 결과는 `docs/review/2026-10-12-image-signature-plan-review.md`에 기록했다.

**Plan status:** Task 1 사전 커밋 검토 `PASS` — 주 세션이 SPW-01..05와 KO-01..07을 통과했다. 독립 writer `plan_writer_recheck_r2`는 이전 문서본을 `PENDING`으로 판정했으며, 그 발견 사항을 수정한 뒤 주 세션이 최종 감사를 마쳤다. 세 계획 문서만 커밋하고 범위를 확인하기 전에는 Task 1을 시작하지 않는다.
