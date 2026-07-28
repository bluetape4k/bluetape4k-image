# README 시각 자료 의미 체계

## 배경

루트 README 개요 시각 자료는 이미 있었지만, 모듈 표와 다이어그램 레이블이 현재
Gradle 소스 레이아웃을 완전히 반영하지 못했다. 루트 모듈 목록에는 BOM 프로젝트가
없었고, 여러 Vips 레이블은 아티팩트 이름 대신 자동 생성한 제목 표기를 사용했다.

## 결정

첫 번째 README 시각 자료는 영어 전용 개요로 유지하되 역할 경계를 설명하는
의미 기반 그룹명인 BOM, core processing, Spring Boot 4, Vips API,
Vips Java 21, Vips Java 25, benchmarks를 사용한다. 설치 좌표는
`projectGroup=io.github.bluetape4k.image` 및 현재 Gradle 프로젝트 이름과
일치시킨다.

## 결과

루트 README 모듈 표를 갱신하고 루트 개요 및 모듈 차트 PNG를 다시 생성했다.
오래된 의존성 예제를 정규화하고, 오해를 부르는 레이블이 있던 현지화 다이어그램의
대체 텍스트를 수정했다.

## 검증

- `rsvg-convert`로 SVG 원본에서 갱신한 PNG 자산을 다시 생성했다.
- 갱신한 루트 SVG 자산이 `xmllint --noout`을 통과했다.
- `./gradlew -q projects`로 현재 프로젝트인 `:bluetape4k-image-bom`,
  `:bluetape4k-images`, Spring Boot, Vips API, Vips Java 21,
  Vips Java 25, 벤치마크 모듈을 확인했다.
- 육안 검사로 레이블이 중앙에 정렬되고 레이아웃을 읽을 수 있는지 확인했다.

## 향후 지침

README 다이어그램을 개선할 때 PNG를 렌더링하기 전에 모듈 이름을
`settings.gradle.kts`와 대조하고, 아티팩트 그룹을 `gradle.properties`와
대조한다.
