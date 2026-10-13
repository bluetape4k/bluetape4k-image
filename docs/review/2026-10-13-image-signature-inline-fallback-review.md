# Issue #698 최종 인라인 폴백 리뷰

## 목적과 범위

이 문서는 merge 전에 미완료 reviewer terminal lane을 정직하게 정리하기 위한 주 세션의 인라인 폴백 리뷰다. 독립 리뷰 완료를 대신하거나 실패한 reviewer lane을 성공으로 재분류하지 않는다. 워크플로 receipt는 원래 terminal 상태를 보존하고 이 문서의 verdict와 검토 head를 별도 증거로 연결한다.

- 저장소: `bluetape4k/bluetape4k-image`
- Issue/PR: #698 / #699
- 검토 대상 코드 head: `f7bdd8d1fa59640430ae3ab66e0d71f15c150079`
- 기준: `develop` at `48fa9cc967e0a86afd2c6fdc9bb627d1432b3fce`
- 코드 변경 범위: 기준부터 검토 대상 head까지 12개 파일
- 현재 PR head에 새로 추가되는 것은 이 리뷰 기록뿐이며 제품 코드·테스트·API·README 변경을 포함하지 않는다.

## 검토 결과

### 공개 API와 호환성

`ByteArray.hasPrefix`, `isPng`, `isJpeg`, `isWebp`는 `io.bluetape4k.images`의 top-level 확장 함수다. ABI 기준 파일은 네 신규 JVM 시그니처만 추가하며 기존 선언을 제거하거나 이동하지 않는다. 공개 시그니처에는 구현 의존성이나 가변 시그니처 배열을 노출하지 않는다. `hasPrefix`는 unsigned byte 범위와 signed `Byte` 변환을 함께 확인한다. 빈 prefix는 접두어 계약대로 일치하고, 짧은 입력·불일치·범위 밖 prefix 값은 예외 대신 `false`를 반환한다.

`isWebp`는 먼저 12바이트 길이를 확인한 뒤 offset 0의 `RIFF`와 offset 8의 `WEBP`를 직접 비교한다. 입력별 slice 복사 없이 헤더만 판별하며, RIFF 길이 필드를 별도 유효성 계약으로 만들지 않는다. 세 형식 헬퍼는 payload 유효성이나 디코딩 성공을 보증하지 않는다고 KDoc과 두 README가 밝힌다.

### 업로드 오류·보안·수명주기

`ImageUploadQualifier`는 기존 PNG, JPEG, WebP 판별 순서와 MIME 값을 유지하면서 private 중복 비교를 공용 helper로 대체한다. 기본 `ByteArray` metadata adapter에서 직접 반환된 `EOFException`만 잘린 입력으로 분류한다. 다른 probe 실패는 다시 던지고, 취소 예외는 기존 경로대로 전파한다. 크기·차원·픽셀 제한과 실제 decoder 검증은 유지되며 헤더 일치가 decoder 호출을 대체하지 않는다.

### 테스트·호출자·문서

새 helper 테스트는 unsigned 비교, 빈 prefix, 짧은 입력, 값 범위, PNG/JPEG 최소 헤더, WebP 최소 길이와 marker 위치를 고정한다. 업로드 테스트는 주입형 probe뿐 아니라 기본 생성자 경로에서 header-only PNG/JPEG/WebP가 `image_not_decodable`로 거부되고 decoder를 호출하지 않는 경우를 확인한다. 기존 취소 전파 테스트는 보존되어 있다. equality assertion은 repository value matcher `shouldBeEqualTo`를 사용한다.

영문·한국어 README는 동일한 import와 Kotlin 예제를 제공하고, 전용 형식 helper를 먼저 쓰고 그 외 명시적 접두어에 `hasPrefix`를 쓰도록 안내한다. 설계·계획 문서는 시그니처 탐지와 전체 입력 검증의 경계, 공개 ABI 영향, 두 workflow 검증 범위를 설명한다. 리뷰에서 문서 간 계약 불일치나 실행 범위를 넘는 주장을 확인하지 못했다.

## 결함과 검증 한계

- P0: 0건
- P1: 0건
- P2: 0건
- P3: 0건
- 기존 코드 리뷰 보고서와 독립 exact-head 리뷰는 별도 증거다. 독립 exact-head 리뷰는 `f7bdd8d`에서 APPROVE를 반환했다.
- `f7bdd8d` exact-head CI 및 Examples workflow가 성공했다. 로컬 `:bluetape4k-images:test`는 770 passed, 18 pending이며, `:spring-boot-image-intelligence-api:test`는 58 passed다. `checkProductionAbi`와 base 대비 `git diff --check`도 통과했다.
- 경로 필터의 미변경 모듈 job과 PaddleOCR/VIPS job은 생략됐으며 성공으로 간주하지 않는다. 이 문서 전용 commit의 새 exact-head CI는 별도로 확인해야 한다.
- 이 인라인 검토는 main-session fallback이다. 실패·만료된 reviewer lane을 독립 PASS로 바꾸지 않으며, 승인된 receipt helper가 각 eligible terminal lane에 이 report와 exact head를 연결해야 한다.

Inline verdict: PASS; P0=0, P1=0.
