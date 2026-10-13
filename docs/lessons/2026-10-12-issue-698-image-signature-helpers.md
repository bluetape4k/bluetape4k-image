# Issue #698 이미지 시그니처 헬퍼

## 배경

이미지 업로드 예제가 PNG, JPEG, WebP 헤더를 자체 비교하고 있어 같은 로직을 재사용할 공용 API가 필요했다. `ByteArray` 확장 함수 네 개를 `bluetape4k-images` 모듈에 추가하고, 업로드 예제와 두 README가 같은 계약을 사용하도록 했다.

## 결정

`hasPrefix`, `isPng`, `isJpeg`, `isWebp`는 헤더 바이트만 비교한다. 입력별 임시 배열을 만들지 않고 길이를 확인한 뒤 필요한 인덱스만 읽는다. 헤더가 일치해도 페이로드 유효성이나 디코딩 성공을 보장하지 않으므로 업로드 경로의 바이트·차원·픽셀 제한과 실제 디코더 검증을 유지한다.

ABI 기준 파일에는 새 JVM 시그니처만 반영하고, 영문·한국어 README의 API import와 Kotlin 예제 코드를 동일하게 유지한다. Gradle 프로젝트 경로는 디렉터리명 `images`가 아니라 `:bluetape4k-images`다. 테스트 명령은 `settings.gradle.kts`의 프로젝트 등록명을 확인한다.

## 확인 과정에서 발견한 문제

`ImageUploadQualifier` 테스트 helper는 dimension/metadata probe를 주입하지만 애플리케이션 기본 생성자는 `probeImageDimensionsForUpload`와 `readImageMetadataReportDetailed`를 사용한다. 주입형 probe 테스트만으로 기본 경로의 오류 분류를 판단할 수 없다.

기본 경로에서 PNG와 WebP 헤더만 있는 입력은 `image_not_decodable`로 분류됐다. JPEG는 metadata reader가 `EOFException`을 반환했고, 기본 adapter가 이를 내부 probe 오류 `image_probe_failed`로 처리했다. 이 경로는 `ByteArray`를 읽으므로 EOF는 런타임 장애가 아니라 잘린 입력이다. adapter에서 `EOFException`만 malformed 입력으로 분류하고, 다른 probe 실패와 `CancellationException` 전파는 그대로 유지했다. 기본 생성자 회귀 테스트는 PNG, JPEG, WebP 모두 `image_not_decodable`로 거부되고 디코더가 호출되지 않는지 확인한다.

## 검증

- `:bluetape4k-images:updateKotlinAbi`, `:bluetape4k-images:checkKotlinAbi`, `checkProductionAbi`가 통과했다.
- `:bluetape4k-images:test`에서 788개가 실행되고 18개가 skip됐으며 실패는 없었다. 10개 skip은 작은 property fixture의 전제조건 미충족이고, 나머지 8개는 수동 골든 이미지 생성 테스트에 붙은 `@Disabled`다.
- `./gradlew :spring-boot-image-intelligence-api:test --tests "io.bluetape4k.images.examples.spring.intelligence.service.ImageUploadQualifierTest"`에서 17개, 전체 `./gradlew :spring-boot-image-intelligence-api:test`에서 58개가 통과했다. 기본 probe PNG/JPEG/WebP 회귀와 취소 전파 테스트가 포함된다.
- 저장소 루트 `detekt`는 `NO-SOURCE`였고 `:bluetape4k-images`에는 별도 Detekt 분석 task가 없다. 이를 코드 분석 통과로 간주하지 않는다.
- 영문·한국어 README의 signature 예제 코드 블록은 동일함을 비교했고 `git diff --check`가 통과했다.

## 향후 지침

- 공개 API 추가 후 모듈 ABI 기준과 production ABI 검사를 함께 실행한다.
- 업로드 분류 테스트는 주입형 probe 경로와 운영 생성자의 기본 probe 경로를 각각 검증한다. byte-array metadata 판독의 `EOFException`은 잘린 입력으로 처리하되 다른 내부 probe 실패는 `image_probe_failed`로 유지한다.
- 이미지 변경 PR에서는 메인 `CI`와 별도 `Examples` workflow를 모두 확인한다. 메인 workflow의 이미지 모듈 검사만으로 예제 테스트 실행을 추정하지 않는다.
- 시그니처 일치는 형식 식별일 뿐 안전성 검증이 아니다. 헤더만 있는 입력, 짧은 입력, unsigned 바이트와 WebP marker offset을 회귀 테스트로 유지한다.
