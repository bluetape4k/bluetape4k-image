# 이슈 190 FFM 파생 이미지 소유권에서 얻은 교훈 (2026-07-01)

**관련 이슈**: #190
**영향받는 모듈**: `bluetape4k-images-vips-java25`

## 배경

`FfmVipsImage`가 반환한 `resize`, `thumbnail`, `crop` 결과는 원본 이미지의
arena를 사용했다. 원본을 먼저 닫으면 파생 이미지는 열린 상태로 남았지만,
해당 이미지가 사용하는 native arena는 이미 닫힌 상태였다.

## 결정

바인딩 독립적인 `VipsImage` 계약을 단순하게 유지한다. 반환된 모든
`VipsImage`는 native 리소스를 직접 소유하며 각각 독립적으로 닫아야 한다.
vips-ffm 파생 연산에서는 원시 픽셀을 새 arena로 복사한 뒤
`VImage.newFromMemory`로 감싼다.

## 결과

파생된 Java 25 FFM 이미지는 원본 이미지가 닫힌 뒤에도 사용할 수 있으며,
파생 이미지를 닫아도 원본 이미지는 닫히지 않는다.

## 검증

- 수정 전 실패 테스트: `derived image remains usable after source closes`가
  `IllegalStateException: Already closed`.
- 수정 후 성공 테스트: 소유권 문제를 해결한 뒤 같은 테스트가 통과했다.
- `./gradlew :bluetape4k-images-vips-api:compileKotlin :bluetape4k-images-vips-java25:test --no-daemon`
  실행 결과는 `53 passing`, `BUILD SUCCESSFUL`이었다.

## 향후 지침

public API가 새 `VipsImage`를 반환한다면 원본 arena에서 얻은 vips-ffm 연산
결과를 그대로 반환하지 않는다. 독립적인 소유권을 가진 이미지를 만들거나,
명시적인 생명주기 공유 계약을 문서화하고 테스트해야 한다.
