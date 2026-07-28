# Issue #273 Spring Boot barcode quickstart 교훈

## 배경

Issue #273은 provider-neutral barcode API와 ZXing provider를 중심으로 runnable
non-published Spring Boot 4 example을 추가한다. user upload용 multipart POST endpoint 하나와
success, no-result, malformed-input behavior용 deterministic GET endpoint 3개를 노출한다.
example은 declared PNG, JPEG, WebP content type을 허용하지만 upload를 저장하지 않는다.

## 결정 또는 확인 사항

- upload safety는 하나의 size check가 아니라 layered guard로 다룬다. declared media type은
  early allowlist일 뿐이다. reported multipart size와 actual byte count는 compressed input을
  bound하고, decoded side와 pixel count는 decompression cost를 bound한다. decode/provider path는
  적용 가능한 guard가 모두 통과한 뒤에만 시작한다.
- parser-level limit와 service-level limit는 정렬하되 독립적으로 test한다. MockMvc는 embedded
  servlet container의 multipart parser를 재현하지 않고도 service와 exception-mapping behavior를
  증명할 수 있다. `spring.servlet.multipart` behavior를 증명하려면 random-port test 또는
  process-level smoke가 필요하다.
- multipart parser는 Spring이 controller method를 선택하기 전에 request를 reject할 수 있다. 이
  path에서는 exception resolver에 handler type이 없다. 따라서 의도한 controller가 그 package에
  있어도 `basePackageClasses`로 좁힌 `@RestControllerAdvice`는 적용되지 않는다. focused
  quickstart application에서는 pre-handler multipart failure에 stable JSON error contract를
  유지하기 위해 global advice가 올바른 선택이다.
- `probeImageDimensions`가 선호되는 cheap decoded-size guard이지만, valid WebP가 runtime에
  ImageIO reader를 갖지 않을 수 있다. WebP-capable fallback으로 library의 bounded metadata
  report를 사용한 뒤, `ImmutableImage`를 만들기 전에 같은 side/pixel limit를 적용한다.
- coroutine cancellation, request validation, provider-neutral failure, unexpected decode
  error를 분리한다. `CancellationException`은 다시 던지고, deliberate request/provider exception은
  보존하며, unknown decode failure만 sanitized `MALFORMED_INPUT` response로 normalize한다.
- `BarcodeResult`를 직접 serialize하지 않고 bounded HTTP DTO를 반환한다. example은 text,
  provider-neutral format, provider name만 노출한다. raw provider byte, backend metadata, point,
  region, filename, cause, stack trace는 response contract 밖에 둔다.
- fixed enum-owned classpath fixture만 cache하고 load 시점과 매 read 시점에 byte를 clone한다.
  이렇게 하면 mutable `ByteArray`를 request 간 공유하지 않으면서 repeated request-time I/O를
  피할 수 있다.
- real HTTP regression test에는 자체 connect/request deadline이 필요하다. random-port embedded
  server는 fixed-port collision risk를 제거하고, bounded client wait는 CI failure mode를 진단
  가능하게 유지한다.
- 서로 다른 path가 identity metadata를 재사용하면 diagram audit이 false-pass할 수 있다. 이 경우
  모든 path가 `data-connector="true"`를 사용했고, connector audit은 같은 name이라고 믿은 path 간
  비교를 skip했다. 각 connector에 unique semantic identity를 부여하고 meaningful connector count를
  요구한다. visible PNG contradiction은 항상 helper의 PASS보다 우선한다.
- diagram visual review에는 관련 set에 대한 generic sentence 하나가 아니라 asset-by-asset evidence
  note가 필요하다. 최종 CairoSVG PNG 각각을 full size로 확인하고 검토한 route order, crossing,
  card intrusion, arrowhead, bend, label, clipping을 정확히 기록한다.

## 결과

example은 하나의 extraction service가 뒷받침하는 documented endpoint 4개를 제공한다. PNG,
JPEG, WebP upload는 같은 bounded response contract를 공유한다. no-result는 successful empty
response로 유지되고, malformed/unsupported/oversized input은 stable sanitized error를 받는다.
provider implementation은 configuration에 국한되며, example은 public artifact 또는 production
storage surface를 도입하지 않는다.

첫 process-level oversized-upload smoke는 handler-only와 MockMvc test가 잡지 못한 gap을
드러냈다. status `413`은 맞았지만 body가 비어 있었다. real-server RED test가 그 behavior를
고정했고, advice scope를 수정한 뒤 같은 smoke가 예상한 `payload_too_large` JSON을 반환했다.

post-review diagram repair는 피할 수 있던 request-line crossing 2개를 제거하고, Spring Boot
architecture link가 하나의 clean departure point를 공유하게 했으며, 관련 SVG 3개 전체에서
collapsed `data-connector` metadata를 교체했다. 결과 audit은 이제 distinct path를 비교하고,
각 최종 PNG에는 specific full-size visual inspection record가 있다.

## 검증

- clean quickstart module test가 통과한다. configuration, fixture, service, MockMvc,
  exception-handler, cancellation, WebP fallback, random-port multipart parser coverage를 포함한다.
- Barcode API와 ZXing provider regression suite가 통과한다.
- real HTTP smoke는 sample, no-result, malformed, fixture upload, oversized upload에 대해
  `200/200/400/200/413`를 반환한다. 모든 response는 documented JSON contract와 일치하고
  server process는 깨끗하게 종료된다.
- module build, project registration, Examples workflow syntax, root static check,
  unsafe-Kotlin/provider-boundary scan, fixture hash, documentation parity, unique connector
  identity, CairoSVG 2x diagram render, connector/geometry/endpoint/mixed-corner audit,
  asset-by-asset full-size PNG inspection, `git diff --check`가 통과한다.
- six-lens implementation review는 multipart-body repair 뒤 `P0=0`, `P1=0`으로 수렴했다.
  test timeout P2도 수정됐다.

## 향후 지침

향후 Spring upload example에서는 세 layer를 분리해 test한다. pure service guard, MVC
response mapping, parser-level limit용 real embedded-container request 하나다. stable error
body가 handler selection 전에 발생하는 failure를 덮어야 한다면 별도 global resolver가 그
exception을 소유하지 않는 한 handler-type predicate로 controller advice를 좁히지 않는다.
compressed-size와 decoded-size limit를 명시적으로 유지하고, decode 전에 format-capable bounded
metadata fallback을 사용하며, authentication, rate limiting, concurrency limit, logging policy,
malware scanning, observability는 production service 책임이라고 문서화한다.

향후 connector-heavy diagram에서는 crossing audit 전에 unique semantic connector ID를
사용하고, zero 또는 collapsed comparison evidence를 reject하며, checklist의 canonical command로
render하고, 모든 PNG에 별도 full-size visual inspection result를 기록한다. script의 PASS가
rendered asset의 명백한 crossing을 뒤집게 두지 않는다.
