# 이미지 시그니처 헬퍼 설계

## 문제

이미지 업로드 소비자는 디코더를 호출하기 전에 `ByteArray`의 파일 시그니처를 확인한다. 현재 `spring-boot-image-intelligence-api`의 `ImageUploadQualifier`는 PNG, JPEG, WebP 헤더 비교를 자체 구현한다. 이 로직을 `bluetape4k-images`의 공용 API로 옮기면 같은 검사 코드를 재사용하고, 업로드 예제도 공용 계약을 사용하게 할 수 있다.

이 작업은 [Issue #698](https://github.com/bluetape4k/bluetape4k-image/issues/698)의 승인된 범위인 `ByteArray.hasPrefix`, `isPng`, `isJpeg`, `isWebp`를 구현하고, 기존 호출부·테스트·영어 및 한국어 README에 반영한다. 대상 저장소는 `bluetape4k/bluetape4k-image`, 기준 브랜치는 `develop`, 작업 브랜치는 `fix/issue-698-image-signatures`다. 구현 후 PR과 exact-head CI까지 진행하고 머지 전에 멈춘다. GNO 인덱싱은 사용자가 수동 처리하기로 한 범위이므로 실행하지 않는다.

## 근거와 제약

- 승인 시점의 `develop`과 `origin/develop`은 `2d843c001956482b7c5a61ca22134819d4c904a1`로 같고, 작업 worktree는 이 커밋에서 분기했다.
- `ImageFormat`은 `JPG`, `PNG`, `WEBP`를 이미 열거하지만 바이트 헤더를 판별하는 공용 API는 없다.
- `examples/spring-boot-image-intelligence-api/src/main/kotlin/io/bluetape4k/images/examples/spring/intelligence/service/ImageUploadQualifier.kt`의 `ImageUploadQualifier.detectMediaType`은 PNG `89 50 4E 47 0D 0A 1A 0A`, JPEG `FF D8 FF`, WebP의 `RIFF` 및 offset 8의 `WEBP`를 확인한다. WebP는 최소 12바이트를 요구한다.
- `settings.gradle.kts`에서 `images/` 디렉터리는 Gradle 프로젝트 `:bluetape4k-images`로 등록되어 있다. 기준 테스트 `./gradlew :bluetape4k-images:test`는 781개 통과, 18개 skip으로 완료됐다.
- Kotlin ABI 설정에는 `api/bluetape4k-images.api` 기준 파일과 `:bluetape4k-images:checkKotlinAbi`/`:bluetape4k-images:updateKotlinAbi` 작업이 있다. 새 공개 extension을 추가하면 기준 파일도 갱신하고 검사해야 한다.
- `.github/workflows/ci.yml`의 `build` job은 `checkProductionAbi`를 실행하고, `test-images` job은 `:bluetape4k-images:test`를 실행한다. 별도 `.github/workflows/Examples.yml`은 `images/**` 또는 `examples/**` 변경으로 `develop` 대상 PR에서 실행되며, 예제 테스트 행렬에 `:spring-boot-image-intelligence-api:test`가 포함되어 있다.
- API는 `io.bluetape4k.images` 패키지의 Kotlin top-level extension으로 제공한다. 신규 의존성, 모듈 등록, 이미지 디코더 동작 변경은 범위에 포함하지 않는다.

## 대안과 결정

| 접근 | 장점 | 비용과 위험 | 결정 |
|---|---|---|---|
| `ByteArray` extension 네 개와 파일 내부 private 시그니처 | 사용부가 간결하고 요청된 형식만 판별한다. 헤더 비교를 배열 할당 없이 수행할 수 있다. | 시그니처 확장 시 함수와 테스트를 함께 갱신해야 한다. | 채택 |
| `ImageFormat.detect(bytes)` 같은 enum 기반 탐지 API | 포맷 결과를 기존 enum과 연결한다. | 현재 enum의 모든 포맷이 동일한 시그니처 계약을 갖지 않으며, 요청 범위보다 넓은 탐지 API를 만든다. | 제외 |
| PNG/JPEG/WebP 시그니처 배열을 public 상수로 노출 | 소비자가 임의 시그니처를 직접 조합할 수 있다. | Kotlin 배열은 가변이므로 공유 상수가 외부 호출로 변경될 수 있고, 세부 구현이 공개 계약에 포함된다. | 제외 |

추천 접근은 Issue #698에 제시된 extension API를 채택하되, 시그니처 값은 파일 내부 `private`으로 유지한다. `hasPrefix`는 입력 `IntArray`를 unsigned byte 값으로 비교하고, `isWebp`는 `RIFF`와 offset 8의 `WEBP`를 확인한다. 기존 업로드 경로의 MIME 판별 순서와 반환 값은 유지한다.

PNG/JPEG/WebP에는 형식별 헬퍼를 우선 사용하고, `hasPrefix`는 전용 헬퍼가 없는 다른 명시적 접두어 검사에 사용한다. 두 `images/README` locale의 예제도 이 선택 기준을 먼저 설명하고 같은 import와 코드를 유지한다.

```kotlin
import io.bluetape4k.images.hasPrefix
import io.bluetape4k.images.isJpeg
import io.bluetape4k.images.isPng
import io.bluetape4k.images.isWebp

private fun detectMediaType(bytes: ByteArray): String? = when {
    bytes.isPng() -> "image/png"
    bytes.isJpeg() -> "image/jpeg"
    bytes.isWebp() -> "image/webp"
    else -> null
}

private val customPrefix = intArrayOf(0x42, 0x54, 0x34, 0x4B)
private fun hasCustomPrefix(bytes: ByteArray): Boolean = bytes.hasPrefix(customPrefix)
```

예제에서는 내장 형식은 형식별 헬퍼로 분류하고, 반복해서 쓸 다른 prefix 배열은 한 번 만들어 재사용한다. 이 코드는 형식 헤더 분류만 보여 주며, 업로드 검증을 대신하지 않는다는 설명도 각 locale에 함께 둔다.

## 계약

- `ByteArray.hasPrefix(prefix: IntArray)`는 배열이 주어진 unsigned byte 값으로 시작하면 `true`를 반환한다. 입력이 짧거나 값이 일치하지 않으면 `false`를 반환한다. 빈 prefix는 접두어 정의에 따라 `true`다. prefix 원소가 `0..255` 범위를 벗어나면 예외 없이 `false`를 반환한다.
- `ByteArray.isPng()`는 PNG의 8바이트 시그니처를 비교하고, `ByteArray.isJpeg()`는 JPEG의 `FF D8 FF` 접두 바이트를 비교한다.
- `ByteArray.isWebp()`는 전체 길이가 12바이트 이상이고, 첫 4바이트가 `RIFF`, 8~11번째 바이트가 `WEBP`일 때 `true`를 반환한다. RIFF 길이 필드인 4~7번째 바이트 값은 추가 제약으로 해석하지 않는다.
- 세 형식 함수는 파일 헤더 시그니처만 확인한다. 시그니처 바이트만 있는 입력도 일치한 형식으로 판별하며, 나머지 페이로드의 유효성이나 디코딩 성공을 보증하지 않는다. 따라서 이 API는 외부 업로드의 안전성 검증을 대체하지 않는다. 업로드 경로는 인코딩된 바이트 크기와 디코딩 후 크기·픽셀 수를 제한하고 실제 디코더로 입력을 검증해야 한다.
- 시그니처 상수 배열을 public 또는 internal API로 노출하지 않는다. `hasPrefix`는 인덱스로 비교하고, WebP는 길이 확인 후 offset 0과 8에서 직접 비교한다. 검사 중 `copyOfRange`나 임시 prefix/header 배열을 만들지 않는다.

## 실패 모드와 대응

| 실패 모드 | 대응과 검증 |
|---|---|
| Kotlin `Byte`가 signed 값이어서 PNG의 `0x89`를 잘못 비교한다. | 비교 시 `toInt() and 0xFF`를 사용하고 `0x89`를 포함한 PNG 테스트를 둔다. |
| `RIFF`만 있는 임의 데이터가 WebP로 오인된다. | 12바이트 최소 길이와 offset 8의 `WEBP`를 모두 검사하고 각 마커 누락·위치 오류를 테스트한다. |
| 입력이 시그니처보다 짧아 인덱스 접근에서 예외가 발생한다. | 비교 전에 offset과 남은 길이를 확인하고 짧은 입력은 `false`로 테스트한다. |
| 시그니처 상수가 호출부에서 변경되어 이후 결과가 달라진다. | 상수 배열은 파일 내부 `private`으로 두고 공개 함수만 노출한다. |
| prefix 값이 unsigned byte 범위를 벗어난다. | `-1`, `256`을 포함한 prefix는 예외 없이 `false`가 되는 계약과 테스트를 둔다. |
| 헤더는 일치하지만 페이로드가 없거나 유효하지 않아 디코더가 파일을 거부한다. | 시그니처 바이트만 있는 입력이 `true`인 동작을 테스트하고, KDoc과 README에서 전체 검증이 아님을 밝힌다. 업로드에는 크기·차원·픽셀 제한과 실제 디코더 검증을 이어서 적용한다. |
| 비교 중 임시 배열 복사로 불필요한 할당이 생긴다. | 구현 리뷰에서 인덱스 직접 비교와 `copyOfRange` 미사용을 확인한다. 동작 테스트만으로 무복사 조건을 증명했다고 보지 않는다. |

## 호환성과 마이그레이션

기존 선언을 이동하거나 제거하지 않고 top-level 함수를 추가하므로 기존 JVM owner와 descriptor는 바뀌지 않는다. 새 API가 `images` 모듈에만 추가되고 공개 시그니처에 외부 타입을 노출하지 않아 의존성이나 catalog 변경은 없다. `api/bluetape4k-images.api`에 새 공개 시그니처를 기록하고 `:bluetape4k-images:checkKotlinAbi`로 검증한다. 기존 예제 호출부는 private 구현을 공용 extension 호출로 바꾸되 PNG/JPEG/WebP 판별 순서와 MIME 문자열을 보존한다. 영문·한국어 README 예제의 구조와 코드를 맞춘다.

변경 대상은 `images/src/main/kotlin/io/bluetape4k/images/ByteArraySignatureExtensions.kt`, `images/src/test/kotlin/io/bluetape4k/images/ByteArraySignatureExtensionsTest.kt`, ABI 기준 파일 `api/bluetape4k-images.api`, 예제의 `examples/spring-boot-image-intelligence-api/src/main/kotlin/io/bluetape4k/images/examples/spring/intelligence/service/ImageUploadQualifier.kt` 및 같은 패키지의 테스트 파일이다. 문서 변경은 공용 API 설명이 있는 `images/README.md`와 `images/README.ko.md`에 한정한다. 예제 README와 workflow는 이번 범위에 포함하지 않는다.

메인 CI의 `test-images` job은 `:bluetape4k-images:test`를 실행하고 `build`는 `-x test`를 사용하지만, build job은 별도로 `checkProductionAbi`를 실행한다. `.github/workflows/Examples.yml`은 `images/**` 변경을 감지해 대상 예제 테스트를 PR에서 실행한다. 따라서 두 모듈 테스트를 로컬에서도 실행하고, exact-head CI에서는 `CI`와 `Examples` workflow 결과를 각각 확인한다. workflow 변경은 이번 범위에 포함하지 않는다. 새 의존성, catalog 또는 schema 마이그레이션은 없으며, 구현 커밋을 되돌리면 예제의 기존 private 판별 헬퍼로 복구된다.

## 수용 기준

1. 공용 `io.bluetape4k.images` 패키지에서 네 extension을 호출할 수 있고 시그니처 배열은 노출되지 않는다.
2. `hasPrefix`의 빈/일치/불일치/짧은 입력과 `-1`, `256` 원소를 직접 테스트한다. PNG, JPEG, WebP는 일치·불일치·짧은 입력 및 시그니처 바이트만 있는 입력을 테스트한다. `0x89` unsigned 비교, WebP 두 마커의 offset과 최소 길이, RIFF 길이 필드를 추가 검증에 사용하지 않는 동작도 확인한다.
3. `ImageUploadQualifier`가 공용 헬퍼를 사용하며 기존 MIME 결과와 판별 순서를 유지한다.
4. `images/README.md`와 `images/README.ko.md`에 동일한 import와 코드 블록으로 네 API 예제를 넣는다. 두 README는 PNG/JPEG/WebP에 형식별 헬퍼를 우선 사용하고 `hasPrefix`는 다른 명시적 접두어 검사에 사용하도록 안내한다. KDoc과 두 README는 이 검사가 헤더 식별만 수행하고 외부 입력 검증을 대체하지 않음을 설명한다.
5. 모듈 테스트, 예제 테스트(`./gradlew :spring-boot-image-intelligence-api:test`), Kotlin ABI 기준 갱신·검사, 정적 검사, 독립 리뷰가 통과한다. PR exact-head에서는 메인 CI의 production ABI 및 이미지 모듈 검사와 `Examples` workflow를 확인한다.
6. 한국어 커밋으로 지정 저장소에 push하고 Issue #698을 연결한 PR을 만든다. 사용자의 별도 머지 승인이 오기 전에는 머지하지 않는다.

## DoD

- [ ] 공개 API, KDoc, 호출부, 회귀 테스트, README 두 locale이 수용 기준을 만족한다.
- [ ] 타깃 모듈과 예제 테스트, Kotlin ABI 기준 갱신·검사, production ABI 검사, Detekt, `git diff --check`가 통과한다.
- [ ] 설계·계획·최종 diff의 독립 관점 리뷰에서 P0/P1이 없다.
- [ ] 재발 방지 lesson을 커밋한다. GNO 인덱싱은 사용자 수동 처리 지시에 따라 남겨 둔다.
- [ ] 한국어 커밋과 push 후 PR의 repository/base/head/본문/메타데이터를 실시간으로 확인하고 exact-head `CI` 및 `Examples` workflow를 통과시킨다.
- [ ] 머지, 로컬 integration 동기화, worktree 정리는 이번 승인 범위 밖이다.

## 설계 리뷰 통합 결과

여섯 관점 리뷰와 주 세션 통합 검토에서 P0/P1은 없었다. 아래 P2/P3는 설계 계약과 수용 기준에 반영했다.

| 우선순위 | 관점 | 근거와 발견 사항 | 반영 사항 |
|---|---|---|---|
| P2 | 성능 | 기존 WebP 검사는 `copyOfRange` 두 번으로 임시 배열을 만든다. 동작 테스트만으로 무복사 요구를 보증하지 못한다. | 인덱스 직접 비교, `copyOfRange` 미사용을 명시하고 코드 리뷰 수용 기준으로 추가했다. |
| P2 | 안정성 | `hasPrefix`의 범위 밖 값과 공개 헬퍼의 빈/짧은/직접 일치 경계가 고정되어 있지 않았다. | 범위 밖 값은 `false`; 경계별 테스트와 시그니처 바이트만 있는 입력 테스트를 추가했다. |
| P2 | 보안 | 헤더만 맞는 임의 데이터도 형식 판별에 성공할 수 있어 업로드 검증으로 오용될 수 있다. | KDoc/README에 한계를 설명하고 업로드 크기·디코딩 크기·픽셀 제한 및 실제 디코더 검증을 유지하도록 했다. |
| P2 | 운영 | 메인 CI `test-images` job은 예제 테스트를 실행하지 않지만 별도 `Examples.yml`이 이미지 경로 변경으로 대상 예제 테스트를 실행한다. 최초 검토에서 별도 workflow를 누락했다. | `.github/workflows/Examples.yml`의 PR 경로 필터와 대상 테스트 행렬을 확인했다. 로컬에서 예제 테스트도 실행하고 exact-head에서는 `CI`와 `Examples` 결과를 구분해 확인한다. workflow 변경은 범위에서 제외했다. |
| P2 | 개발자/API | README 대상 외에 새 public extension의 Kotlin ABI 기준 파일이 구현 범위에서 빠져 있었다. 승인 범위에 없는 enum 탐지 API보다 요청된 확장 함수가 작고 기존 호출부와 맞다. | `api/bluetape4k-images.api`를 변경 대상으로 추가하고 `updateKotlinAbi`/`checkKotlinAbi`를 계획에 포함한다. 구현 파일·테스트 파일과 `images/README.md`, `images/README.ko.md`의 경로를 고정하고 extension 설계를 유지한다. |
| P2 | 사용자/호출자 | README 예제의 동등성과 KDoc의 제한 계약이 검토 가능하게 구체적이지 않았다. | 두 README에 동일한 import와 코드 블록, 시그니처만 검사한다는 설명 및 보안 한계를 요구한다. |
| P3 | 운영 | 되돌림 절차가 명시되지 않았다. | schema/catalog 변경이 없고 구현 커밋 revert로 기존 private 헬퍼를 복구한다고 명시했다. |

개발자/API와 사용자/호출자 관점을 함께 맡은 독립 리뷰어가 r3에서 PASS를 제출했고, 수정한 README 선택 기준은 별도 호출자 재검토에서도 PASS했다. 초기 reviewer 응답 지연을 최종 결과로 오인하지 않는다. 코드베이스의 `images/src/main/kotlin/io/bluetape4k/images/ImageFormat.kt`와 `ImageDimensionProbe.kt`는 같은 패키지의 공개 API 패턴을 보여 준다. 요청된 네 top-level extension은 기존 API 삭제나 descriptor 변경 없이 추가할 수 있다. `ImageFormat.detect`는 모든 열거 포맷에 같은 시그니처 계약을 적용할 수 없어 제외한다.
