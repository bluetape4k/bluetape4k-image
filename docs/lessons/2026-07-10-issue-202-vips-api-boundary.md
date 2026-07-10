# Issue #202 Vips API Boundary Lessons

## Context

`images-vips-api`의 main API에서 `bluetape4k-images` 구현 의존성을 제거하면서,
Vips 전용 AVIF/HEIC capability API의 opt-in 경계와 publication metadata를 함께
검증했다.

## Decision

- AVIF/HEIC enum entry에만 `VipsIncubatingApi`를 적용하고, stable capability report
  사용은 opt-in 없이 유지한다.
- `testFixturesApi(project(":bluetape4k-images"))`는 유지하되, Maven POM에서는
  Gradle test-fixtures가 투영한 optional image-stack 의존성만 제거한다.
- POM XML `Node.name()`은 namespace-qualified QName이므로 element 이름 비교에는
  namespace local name을 사용한다.
- custom Kotlin source set으로 compiler opt-in 진단을 검증할 때 `main.output`뿐 아니라
  `main.compileClasspath`도 포함해야 한다.

## Outcome

- opt-in fixture는 컴파일되고, opt-in 없는 fixture는 `VipsIncubatingApi` 경고를
  `-Werror`로 승격해 실패한다.
- 생성 POM의 forbidden direct dependency 수는 `0`이며, Gradle normal variant는
  image implementation dependency가 없고 test-fixtures variant에만 해당 의존성이 남는다.
- EN/KO README와 core AVIF/HEIC KDoc은 consumer migration과 binding-neutral contract를
  각각 명시한다.

## Verification

- `:bluetape4k-images:compileKotlin`
- `:bluetape4k-images-vips-api:test`
- opted/unopted compiler fixture tasks
- Java 21/25 main/test compilation
- `BluetapeImage` POM/module metadata assertions
  - POM: `images-vips-api/build/publications/BluetapeImage/pom-default.xml`
    (`sha256=2e5ccf6fd18b1165d0118c85be922c7a503e6dbac39ff595395848dd0776ce4c`)
  - module metadata: `images-vips-api/build/publications/BluetapeImage/module.json`
    (`sha256=8931fb3d8bef7e428d56d4734fedbc241e36b2a2ca7d62871a834c5c10a3631f`)
  - final green group exit `0`; unopted fixture expected exit `1` with
    `VipsIncubatingApi` under `-Werror`; normal/fixture boundary assertions passed
- Step 6-R six-perspective review: final `P0=0`, `P1=0`

## Future Guidance

Gradle `java-test-fixtures`를 사용하는 API module의 Maven publication을 수정할 때는
POM과 Gradle module metadata를 별도로 검사한다. XML filter는 fixture-derived optional
entry로 범위를 좁히고, dependency boundary migration에는 복사 가능한 direct dependency
snippet을 README locale pair에 함께 제공한다.
