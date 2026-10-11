# PR #696 직렬화와 CI 경계 검증 교훈

## 맥락

PR #696은 모듈별 테스트가 통과했다고 보고됐지만, 최신 CI에서는 vips API의 opt-in 음성 fixture가 예상과 달리 컴파일되고 JNI/FFM 테스트가 컴파일되지 않았다. 별도 리뷰에서는 Java 직렬화가 Kotlin 생성자 검증을 우회해 모델 불변식과 non-null 계약을 깨뜨릴 수 있음을 확인했다.

## 원인과 결정

- `UnoptedVipsOptInFixture`에 `@OptIn(VipsIncubatingApi::class)`가 붙어 있어 음성 컴파일 검증이 실제로 opt-in 경계를 확인하지 못했다. 음성 fixture에서는 opt-in을 제거하고, CI가 진단 토큰 `VipsIncubatingApi`를 확인하도록 유지한다.
- 두 vips 테스트가 다른 파일에 선언된 private `FTYP_MARKER`를 참조해 테스트 소스 컴파일이 실패했다. 테스트는 계약상 고정된 ISO BMFF `ftyp` 표기를 자체 기대값으로 검사하고, production의 private 상수에 의존하지 않는다.
- `ObjectInputStream`은 Kotlin 생성자와 `init` 검증을 호출하지 않는다. 검증된 factory 또는 `copy()`를 호출하는 `readResolve`를 두고, `serialVersionUID`와 배열·컬렉션 방어 복사 동작을 보존한다. 생성자 불변식뿐 아니라 Kotlin non-null 프로퍼티 계약도 회귀 테스트에 포함한다.

## 검증

- RED: opt-in 음성 fixture가 성공적으로 컴파일되는 현상과 Java 21/25 테스트의 `FTYP_MARKER` private 접근 오류를 재현했다.
- GREEN: opt-in 양성 fixture 컴파일 성공, 음성 fixture의 `VipsIncubatingApi` 진단 실패를 확인했다. Java 21/25 테스트 소스 모두 컴파일됐다.
- `images-vips-api`: 25 tests, 0 failures.
- `images-vips-java25`: 93 tests, 0 failures.
- `images-vips-java21`: 70 tests, 0 failures, 47 skipped. 로컬 macOS arm64에서 JNI 초기화가 x86_64 `libJVips.dylib`을 arm64 JVM에 적재하려 해 실패했으므로 동작 테스트 증거로 계산하지 않는다. CI Linux x86_64 실행 결과를 별도로 확인한다.
- 직렬화 회귀를 포함한 최종 대상 테스트는 Barcode 9, OCR 9, image 46, Ktor 3개가 모두 통과했다. 여러 모듈을 한 번에 실행했을 때 image 테스트의 Gradle 중간 결과 파일이 누락되어 실패했지만, 같은 image 대상을 단독 재실행하자 46개가 통과했다.
- OCR와 moderation의 null 필드 역직렬화 회귀 테스트는 수정 전 실패하고 `readResolve = copy()` 적용 후 통과했다.
- 변경을 포함한 최종 `checkKotlinAbi --continue build -x test --no-configuration-cache`는 181개 task 중 101개 실행, 80개 최신 상태로 성공했다. `detekt --no-configuration-cache --no-daemon`도 성공했으며 이 저장소 구성에서 `detekt NO-SOURCE`였다. PR exact-head CI는 수정 커밋을 올린 뒤 별도로 확인한다.

## 다음 guard

직렬화 가능 Kotlin 모델을 추가하거나 변경하면 필드의 invariants, nullable/non-null 계약, mutable payload를 확인하고 Java 역직렬화 회귀 테스트를 둔다. 컴파일 실패를 검증하는 Gradle fixture는 양성/음성 소스가 서로의 opt-in을 복사하지 않는지 확인한다. 테스트는 private production 세부 상수보다 공개 포맷 계약을 직접 검사한다. GNO CG-18 인덱싱은 머지와 canonical checkout 동기화 후 수행하며, 사용자가 수동으로 처리하기로 한 현재 작업에서는 보류한다.
