# Issue #202 Vips API 경계에서 얻은 교훈

## 배경

`images-vips-api`의 주 API에서 `bluetape4k-images` 구현 의존성을 제거하면서,
Vips 전용 AVIF/HEIC 기능 API의 선택적 사용 경계와 게시 메타데이터를 함께
검증했다.

## 결정

- AVIF/HEIC 열거형 항목에만 `VipsIncubatingApi`를 적용하고, 안정 기능 보고서는
  선택적 사용 선언 없이 계속 사용할 수 있게 한다.
- `testFixturesApi(project(":bluetape4k-images"))`는 유지하되, Maven POM에서는
  Gradle 테스트 픽스처가 투영한 선택적 이미지 스택 의존성만 제거한다.
- POM XML `Node.name()`은 namespace-qualified QName이므로 element 이름 비교에는
  네임스페이스 로컬 이름을 사용한다.
- 사용자 정의 Kotlin 소스 세트로 컴파일러 선택적 사용 진단을 검증할 때 `main.output`뿐 아니라
  `main.compileClasspath`도 포함해야 한다.
- 의도적으로 실패하는 컴파일러 픽스처는 기본 `build` 생명주기에서 실행되면 안 된다.
  `-PverifyVipsOptInFixtures`가 있을 때만 전용 작업을 실행해 CI 빌드와 실패 검증을 분리한다.

## 결과

- 선택적 사용을 선언한 픽스처는 컴파일되고, 선언하지 않은 픽스처는 `VipsIncubatingApi` 경고를
  `-Werror`로 승격해 실패한다.
- 기본 `:bluetape4k-images-vips-api:build -x test` 생명주기는 속성 없이
  성공하고, 픽스처 계약은 `-PverifyVipsOptInFixtures`가 있는 별도 실행에서만
  검증한다.
- 생성 POM에서 금지된 직접 의존성 수는 `0`이며, Gradle 일반 변형에는
  이미지 구현 의존성이 없고 테스트 픽스처 변형에만 해당 의존성이 남는다.
- 영문·국문 README와 핵심 AVIF/HEIC KDoc은 소비자 마이그레이션과 바인딩 중립 계약을
  각각 명시한다.

## 검증

- `:bluetape4k-images:compileKotlin`
- `:bluetape4k-images-vips-api:test`
- 선택적 사용 선언 유무에 따른 컴파일러 픽스처 작업
- Java 21/25 주 코드·테스트 코드 컴파일
- `BluetapeImage` POM/모듈 메타데이터 단언
  - POM: `images-vips-api/build/publications/BluetapeImage/pom-default.xml`
    (`sha256=2e5ccf6fd18b1165d0118c85be922c7a503e6dbac39ff595395848dd0776ce4c`)
  - 모듈 메타데이터: `images-vips-api/build/publications/BluetapeImage/module.json`
    (`sha256=8931fb3d8bef7e428d56d4734fedbc241e36b2a2ca7d62871a834c5c10a3631f`)
- 최종 성공 그룹의 종료 코드는 `0`이었다. 선택적 사용을 선언하지 않은 픽스처는
    `-Werror`와 `-PverifyVipsOptInFixtures`에서 `VipsIncubatingApi`로 종료 코드 `1`이
    발생해야 하며, 일반 실행과 픽스처 실행의 경계 단언도 통과했다.
- 6-R 단계 6개 관점 검토의 최종 결과: `P0=0`, `P1=0`

## 향후 지침

Gradle `java-test-fixtures`를 사용하는 API 모듈의 Maven 게시 설정을 수정할 때는
POM과 Gradle 모듈 메타데이터를 별도로 검사한다. XML 필터의 범위는 픽스처에서
파생된 선택적 항목으로 좁힌다. 의존성 경계를 마이그레이션할 때는 바로 복사해 쓸
수 있는 직접 의존성 코드 조각을 영문·국문 README에 함께 제공한다.
