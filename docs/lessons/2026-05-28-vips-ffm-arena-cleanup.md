# Vips FFM Arena 정리

## 배경

#107은 네이티브 로드, 래퍼 생성, 파생 이미지 연산이 실패해도 소유권 상태가
불명확하게 남지 않도록 Java 25 FFM 이미지 생성 경로를 강화하도록 요구했다.

## 결정

소유 `Arena` 생성을 작은 도우미로 단일화한다. 생성에 실패하면 도우미가 Arena를
닫고, 억제된 close 실패를 포함해 원래 오류를 보존한다. 파생 크기 조정, 썸네일,
자르기 연산은 원본 Arena를 공유하므로 해당 실패가 원본 이미지에 영향을 주지
않도록 격리한다.

## 결과

`ffmVipsImageOf(Path)`와 바이트 배열 디코딩이 하나의 소유 Arena 계약을
사용한다. 회귀 테스트는 네이티브 로드 및 래퍼 생성 실패 시 Arena가 닫히는지,
파생 연산 실패 후에도 원본 이미지를 사용할 수 있는지 검증한다.

## 검증

- `./gradlew :bluetape4k-images-vips-java25:test --tests 'io.bluetape4k.images.vips.java25.FfmVipsImageTest'`
- `./gradlew :bluetape4k-images-vips-java25:test`

## 향후 방지책

수동 `arena.close()` catch 분기가 있는 새 FFM 디코딩/로드 진입점을 추가하지
않는다. 소유 네이티브 생성은 공통 도우미를 거쳐 정리와 예외 보존 동작을
일관되게 유지한다.
