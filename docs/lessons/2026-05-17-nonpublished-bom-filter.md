# 비공개 모듈 BOM 필터

## 배경

이미지 벤치마크 모듈은 BOM 제약이나 Central Portal 아티팩트가 아니라
로컬 성능 측정 도구로 유지해야 한다.

## 결정

예제, 데모, 벤치마크에는 정규화한 비공개 모듈 필터를 적용한다. 또한 Central
검증에 명시적인 의존성 버전이 전달되도록 Spring 의존성 관리 POM 사용자 정의를
계속 활성화한다.

## 결과

`bluetape4k-images-benchmark`를 BOM 제약, NMCP 집계, 게시/서명 설정,
커버리지 집계에서 제외했다.

## 검증

- `./gradlew clean generatePomFileForBluetapeImagePublication --no-daemon --no-configuration-cache --no-build-cache`
- 생성한 BOM POM을 검사한 결과 `examples`, `demo`, `benchmark` 항목이 없었다.
