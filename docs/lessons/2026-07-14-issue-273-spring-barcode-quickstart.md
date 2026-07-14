# Issue #273 Spring Boot Barcode Quickstart Lessons

## Context

Issue #273 adds a runnable, non-published Spring Boot 4 example around the
provider-neutral barcode API and ZXing provider. It exposes one multipart POST
endpoint for user uploads and three deterministic GET endpoints for success,
no-result, and malformed-input behavior. The example accepts declared PNG,
JPEG, and WebP content types but stores no upload.

## Decision or Finding

- Treat upload safety as layered guards, not one size check. The declared media
  type is only an early allowlist; reported multipart size and actual byte count
  bound compressed input; decoded side and pixel count bound decompression
  cost. The decode/provider path starts only after all applicable guards pass.
- Keep parser-level and service-level limits aligned but independently tested.
  MockMvc can prove service and exception-mapping behavior without reproducing
  the embedded servlet container's multipart parser. A random-port test or
  process-level smoke is needed to prove `spring.servlet.multipart` behavior.
- A multipart parser can reject a request before Spring selects a controller
  method. In that path the exception resolver has no handler type. A
  `@RestControllerAdvice` narrowed with `basePackageClasses` is therefore not
  applicable, even when the intended controller is in that package. For a
  focused quickstart application, global advice is the correct way to preserve
  the stable JSON error contract for pre-handler multipart failures.
- `probeImageDimensions` is the preferred cheap decoded-size guard, but a valid
  WebP may have no ImageIO reader in the runtime. Use the library's bounded
  metadata report as a WebP-capable fallback, then apply the same side/pixel
  limits before creating an `ImmutableImage`.
- Separate coroutine cancellation, request validation, provider-neutral
  failures, and unexpected decode errors. Rethrow `CancellationException`,
  preserve deliberate request/provider exceptions, and normalize only unknown
  decode failures to a sanitized `MALFORMED_INPUT` response.
- Return a bounded HTTP DTO instead of serializing `BarcodeResult` directly.
  The example exposes text, provider-neutral format, and provider name only;
  raw provider bytes, backend metadata, points, regions, filenames, causes, and
  stack traces remain outside the response contract.
- Cache only fixed enum-owned classpath fixtures and clone bytes on load and on
  every read. This avoids repeated request-time I/O without sharing a mutable
  `ByteArray` across requests.
- Real HTTP regression tests need their own connect and request deadlines. A
  random-port embedded server removes fixed-port collision risk, while bounded
  client waits keep CI failure modes diagnosable.
- A diagram audit can false-pass when distinct paths reuse identity metadata.
  In this case every path used `data-connector="true"`, and the connector audit
  skipped comparisons between paths it believed had the same name. Give each
  connector a unique semantic identity and require meaningful connector counts;
  a visible PNG contradiction always overrides a helper's PASS.
- Diagram visual review needs an asset-by-asset evidence note, not one generic
  sentence for a related set. Inspect each final CairoSVG PNG at full size and
  record the exact route order, crossings, card intrusions, arrowheads, bends,
  labels, and clipping that were checked.

## Outcome

The example provides four documented endpoints backed by one extraction
service. PNG, JPEG, and WebP uploads share the same bounded response contract;
no-result remains a successful empty response; malformed, unsupported, and
oversized inputs receive stable sanitized errors. The provider implementation
is confined to configuration, and the example introduces no public artifact or
production storage surface.

The first process-level oversized-upload smoke exposed a gap that handler-only
and MockMvc tests did not: status `413` was correct but the body was empty. A
real-server RED test locked that behavior, the advice scope was corrected, and
the same smoke then returned the expected `payload_too_large` JSON.

The post-review diagram repair removed two avoidable request-line crossings,
made the Spring Boot architecture links share one clean departure point, and
replaced collapsed `data-connector` metadata across all three related SVGs.
The resulting audits now compare distinct paths, and each final PNG has a
specific full-size visual inspection record.

## Verification

- Clean quickstart module tests pass, including configuration, fixture, service,
  MockMvc, exception-handler, cancellation, WebP fallback, and random-port
  multipart parser coverage.
- Barcode API and ZXing provider regression suites pass.
- Real HTTP smoke returns `200/200/400/200/413` for sample, no-result,
  malformed, fixture upload, and oversized upload; every response matches the
  documented JSON contract and the server process shuts down cleanly.
- Module build, project registration, Examples workflow syntax, root static
  check, unsafe-Kotlin/provider-boundary scans, fixture hashes, documentation
  parity, unique connector identities, CairoSVG 2x diagram renders,
  connector/geometry/endpoint/mixed-corner audits, asset-by-asset full-size PNG
  inspection, and `git diff --check` pass.
- The six-lens implementation review converged at `P0=0`, `P1=0` after the
  multipart-body repair; the test timeout P2 was also fixed.

## Future Guidance

For future Spring upload examples, test three layers separately: pure service
guards, MVC response mapping, and one real embedded-container request for
parser-level limits. When a stable error body must cover failures that happen
before handler selection, do not narrow controller advice with a handler-type
predicate unless a separate global resolver owns those exceptions. Keep
compressed-size and decoded-size limits explicit, use format-capable bounded
metadata fallback before decode, and document that authentication, rate
limiting, concurrency limits, logging policy, malware scanning, and
observability remain responsibilities of a production service.

For future connector-heavy diagrams, use unique semantic connector IDs before
running crossing audits, reject zero or collapsed comparison evidence, render
with the checklist's canonical command, and record a separate full-size visual
inspection result for every PNG. Never let a script's PASS overrule an obvious
crossing in the rendered asset.
