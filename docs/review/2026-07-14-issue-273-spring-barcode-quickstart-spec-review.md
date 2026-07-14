# Issue #273 Spring Boot Barcode Quickstart Spec Review

## Scope

- Artifact: `docs/superpowers/specs/2026-07-14-issue-273-spring-barcode-quickstart-design.md`
- Artifact kind: spec
- Research basis: live issue #273, existing barcode API/ZXing source and tests,
  `spring-boot-image-api`, `Examples.yml`, module-registration guards, and issue
  #272 deterministic barcode fixtures
- Lenses: performance, stability, security, operator/Ops, developer/API,
  user/caller, followed by main-session integration

The active native-agent interface does not expose the required `agent_type`
field and the session has fewer than six free lanes. Per `model-routing.md`,
each required lens was executed as a separate read-only main-session pass
rather than inventing agent roles.

## Initial Findings

| Priority | Lens | Evidence | Required edit | Resolution |
|---|---|---|---|---|
| P1 | Security | The initial dimension guard named only `probeImageDimensions`, whose implementation depends on an ImageIO reader, while the upload allowlist includes WebP. | Define a bounded metadata-report fallback that can read WebP dimensions before full decode, and test all three accepted formats. | Fixed in sections 6.3 and 9. |
| P1 | Developer/API | The initial response shape serialized the full library `BarcodeResult`, which can expose backend metadata, points, and future fields not intended as an HTTP contract. | Map provider-neutral results to a bounded DTO containing text, normalized format, and provider name. | Fixed in section 6.4. |
| P1 | User/caller | The selected copied QR fixture exposed the issue #272 benchmark payload in a new-user quickstart. | Generate a module-owned QR with the stable payload `bluetape4k-barcode-quickstart`; keep the benchmark directory out of runtime source sets. | Fixed in sections 6.4 and 8. |
| P2 | Performance | The initial fixture component contract did not say whether every GET repeated classpath I/O. | Load and validate fixed resources once at startup and avoid sharing mutable byte arrays. | Fixed in section 6.1. |

## Rerun Verdicts

| Lens | Verdict | Evidence |
|---|---|---|
| Performance | PASS | Sections 6.1 and 6.3 load fixtures once, bound upload size before byte reads, and dispatch blocking I/O and CPU decode work to the appropriate coroutine dispatchers. |
| Stability | PASS | Sections 6.1, 6.3, 8, 9, and 12 define startup failure for missing fixtures, immutable request isolation, cancellation propagation, deterministic failure cases, and stateless rollback. |
| Security | PASS | Sections 6.3, 6.4, and 7 bound encoded and decoded size, validate actual image structure after untrusted content type, sanitize errors, omit payload/backend metadata, and document the unauthenticated local-example boundary. |
| Operator/Ops | PASS | Sections 6.4 and 12 define stable status/error mappings, no persistent state or migration, default port behavior, startup diagnostics, and directory-level rollback. |
| Developer/API | PASS | Sections 5-6 keep ZXing construction in configuration, use `BarcodeReader` in the service, isolate controller/service/fixture/DTO responsibilities, and avoid production API changes. |
| User/caller | PASS | Sections 4, 6.2, 7, 10, and 13 provide a real upload path, three reproducible scenarios, exact commands/responses, bilingual docs, capability limits, and production-deployment warnings. |

## Integration Verdict

- The selected dedicated module is narrower and more teachable than extending
  the existing storage-focused Spring example.
- Upload validation, malformed normalization, no-result semantics, provider
  boundary, fixture ownership, HTTP DTO shape, and rollback are explicit and
  testable.
- The full registration chain is represented by settings, AGENTS, Examples
  workflow, README locales, project listing, and diagram QA.
- BOM/catalog, publication, Kover aggregation, benchmark updates, native/JNI,
  OCR, Docker, and Testcontainers have concrete non-published pure-JVM N/A
  evidence.
- Chart N/A is evidence-backed because this issue has no measured series; the
  three README diagrams remain required visual artifacts.
- Latest convergence: **P0=0, P1=0**. The P2 fixture-I/O finding is fixed.

Required checks: 7/7; N/A: 0; Blocked: 0.
