# Issue #272 ZXing Barcode Benchmark Lessons

## Context

Issue #272 adds reproducible latency and throughput evidence for ZXing barcode
extraction from immutable QR, Code 128, and no-result PNG fixtures. The measured
boundary starts with an already decoded `ImmutableImage` and excludes fixture
generation, resource loading, and PNG decoding.

## Decision or Finding

- Commit the exact fixture bytes and a strict hash/dimension/expectation
  manifest. Runtime-generated barcodes are useful provider tests, but generator
  or encoder changes would make longitudinal benchmark rows incomparable.
- Measure average latency and throughput independently. They use different JMH
  modes and should not be presented as reciprocal conversions because harness
  behavior, rounding, and statistical error differ.
- Keep provider-neutral fixture validation in main and the ZXing dependency in
  benchmark/test configurations. Confirm the boundary with resolved runtime
  classpaths, not only the dependency declaration text.
- Treat raw evidence as an owned run. Record a fresh task start, accept exactly
  one new report per mode, validate every row and metric, then atomically promote
  one append-only directory. A rerun gets a new run ID rather than overwriting
  accepted evidence.
- A contract test that counts common Gradle literals across an entire build
  file is coupled to unrelated benchmark configurations. Scope assertions to
  the named configuration whose protocol they protect.
- Failure tests must assert the intended error, not merely the exception type.
  The first path test passed for `./` because a later hash mismatch threw the
  same exception; checking the normalization message exposed the missing guard.
- Gradle command-line `-D` properties do not automatically become test-worker
  system properties. The temporary fixture generator initially saw no property
  until it was explicitly propagated. Future generators should use a dedicated
  JavaExec task, a Gradle property wired with `systemProperty`, or an explicit
  test-task mapping instead of relying on launcher inheritance.

## Outcome

The accepted run contains three average-time rows and three throughput rows for
the same fixtures. Each fixture is immutable and hash-pinned; each raw report is
validated for benchmark name, scenario set, mode, unit, thread, fork, warmup,
measurement, positive finite score, and finite non-negative score error. The
main runtime classpath contains no ZXing provider, while the benchmark runtime
uses ZXing `3.5.4`.

## Verification

- Benchmark module: 84 passing clean tests plus benchmark source compilation.
- Barcode API/provider regression: 14 and 8 fresh passing tests.
- Evidence: four accepted files, three rows per mode, canonical fixture
  manifest parity, recomputed SHA-256 values, and duplicate-promotion rejection.
- Documentation: all six rounded score/error pairs match across the report and
  both README locales; raw links and metric directions are present.
- Static and scope checks: module build, task listing, projects, detekt,
  dependency boundaries, unsafe-Kotlin scan, and `git diff --check` pass.
- Six-lens implementation review converged at `P0=0`, `P1=0`.

## Future Guidance

Use tables for one-provider results when metrics have incompatible units or
directions. If a later issue compares exactly two providers, use a complementary
pastel pair and matching legend swatches; use a categorical palette for three or
more series. Keep fixture identity, provider version, JVM, host, protocol, raw
JSON, and accepted-run hashes together so future runs can establish comparability
before making performance claims.
