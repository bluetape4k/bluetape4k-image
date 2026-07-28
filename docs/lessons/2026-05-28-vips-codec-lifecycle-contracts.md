# Vips 코덱과 생명주기 계약

## 배경

#108은 네이티브 기반 `VipsImage` 인스턴스에 대해 결정적인 close 계약 회귀
검증을 요구했다. #100은 모든 libvips 설치 환경이 같은 HEIF 코덱 기능을
제공한다고 가정하지 않는 AVIF/HEIC 지원을 요구했다.

## 결정

안정적인 JPEG/PNG/WebP 동작은 무조건 제공하되, HEIF 계열 형식은 기능 유무에
따라 제한한다. Java 25 FFM은 `vips_type_find`로 네이티브 libvips 연산의
가용성을 검사할 수 있다. Java 21 JVips는 바인딩을 통해 AVIF를 제공할 수 있지만
HEIC는 백엔드 제약으로 보고해야 한다.

## 결과

close 계약 테스트가 JVips와 FFM 백엔드 모두에서 이중 close, close 이후의 모든
공개 연산, 연산 실패 이후의 close를 검증한다. AVIF/HEIC 인코딩 경로는 올바른
ISO BMFF 출력을 생성하거나 정제된 코덱 지원 오류로 실패한다.

## 검증

- `./gradlew :bluetape4k-images-vips-java21:test`
- `./gradlew :bluetape4k-images-vips-java25:test`
- `./gradlew detekt`
- `./gradlew build -x test`

## 향후 방지책

AVIF/HEIC가 모든 환경에서 제공된다고 문서화하지 않는다. 지원 범위는 백엔드와
`heifload_buffer`, `heifsave_buffer` 같은 네이티브 libvips 코덱 연산에
연결해 설명한다.
