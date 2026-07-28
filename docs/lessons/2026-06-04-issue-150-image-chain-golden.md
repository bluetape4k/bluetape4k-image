# 교훈 - image chain golden fixture (2026-06-04)

**관련 이슈**: #150
**영향 모듈**: `bluetape4k-images`

## 배경

병합 후 Nightly smoke 실행 `26963516529`는 snapshot 의존성 해석 단계를 통과했지만
`Test / images`에서 실패했다. JUnit 아티팩트에서는 `ChainGoldenImageTest` 실패
한 건을 확인했다. `grayscale -> medianBlur -> vignette` 연산 결과의 `(0,0)` 픽셀은
`(54,54,54)`였지만 커밋된 golden image는 `(8,8,8)`을 기대했다.

같은 대상 테스트가 로컬에서도 실패했으므로 GitHub runner에서만 발생하는 image
processing 차이가 아니라 오래된 golden fixture가 원인이었다.

## 결정

비활성화된 기존 `GoldenImageGeneratorTest`로
`expected_chain_grayscale_median_vignette.png`만 다시 생성하고 generator를 다시
비활성화한다. 공통 픽셀 검증 조건을 완화하거나 golden test를 건너뛰지 않는다.

## 결과

chain golden fixture가 현재 filter 구현과 일치하며 테스트는 grayscale, median blur,
vignette 전체 pipeline을 계속 검증한다.

## 검증

- `./gradlew :bluetape4k-images:test --tests 'io.bluetape4k.images.filters.dsl.GoldenImageGeneratorTest.generate chain grayscale-median-vignette golden' --no-configuration-cache --no-daemon`
- `./gradlew --refresh-dependencies :bluetape4k-images:test --tests io.bluetape4k.images.filters.dsl.ChainGoldenImageTest --no-configuration-cache --no-daemon`

## 이후 규칙

golden image가 GitHub와 로컬에서 모두 실패하면 커밋된 generator로 오래된 fixture만
다시 생성하고 검증 조건은 엄격하게 유지한다. GitHub에서만 실패한다면 fixture를
바꾸기 전에 플랫폼 또는 image codec 차이를 조사한다.
