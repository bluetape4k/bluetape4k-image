# Issue 212: VIPS codec capability report

## 배경

#212는 모든 libvips host가 같은 HEIF-family codec set을 갖는다고 가장하지 않는 AVIF/HEIC
support guidance가 필요했다. Java 25 backend는 native operation availability를 검사할 수
있지만, Java 21 JVips binding은 native codec surface를 직접 증명할 수 없다.

## 결정

`VipsRuntime.codecCapabilityReport()`와 `VipsRuntime.smokeTestCodec(...)`를 노출한다.
report는 JPEG/PNG/WebP를 stable/unconditional로 유지하고, Java 25 AVIF/HEIC support는
`heifload_buffer`와 `heifsave_buffer`에서 보고하며, Java 21 JVips uncertainty는
backend limitation인 HEIC encode만 `UNAVAILABLE`로 두고 나머지는 `UNKNOWN`으로 보고한다.

## 결과

service는 native error를 parsing하지 않고 structured support state에서 deployment decision을
내릴 수 있다. Smoke helper는 sanitized result object를 반환하므로 raw native path 또는
internal libvips message는 public response 밖에 머문다.

## 검증

- `./gradlew :bluetape4k-images-vips-api:test :bluetape4k-images-vips-java21:test :bluetape4k-images-vips-java25:test --configuration-cache --build-cache`
- `git diff --check`

## 향후 방지책

AVIF/HEIC를 universal available로 문서화하지 않는다. Java 21 JVips는 native operation을
검사할 수 없는 곳에서 unknown support를 보고한다. Java 25 FFM은 operation availability를
보고하지만 deployment host에서 caller-provided sample smoke test가 여전히 필요하다.
