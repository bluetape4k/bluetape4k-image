# Issue #273 Spring Boot Barcode Quickstart Plan Review

## Scope

- Artifact: `docs/superpowers/plans/2026-07-14-issue-273-spring-barcode-quickstart-plan.md`
- Artifact kind: plan
- Basis: approved issue #273 design, current Spring examples, barcode
  API/ZXing source and tests, image dimension/metadata APIs, Examples workflow,
  and repository module-registration rules
- Lenses: performance, stability, security, operator/Ops, developer/API,
  user/caller, followed by main-session integration and Step 3-R ordering review

The native-agent interface available in this session does not expose the
required `agent_type` field and has fewer than six independent lanes. Per
`model-routing.md`, the six required lenses were run as separate read-only
main-session passes. No role label was invented and the main session performed
the final severity normalization.

## Initial Findings and Repairs

| Priority | Lens/area | Evidence | Required plan edit | Resolution |
|---|---|---|---|---|
| P1 | Developer/API | Task 3 configured `BarcodeExtractionService` before Task 4 created the type, so Task 3 could not compile independently. | Keep Task 3 to application/properties/reader/fixture beans and add the service bean only after Task 4 creates the service. | Fixed in Tasks 3-4. |
| P1 | Security | The initial properties model made the PNG/JPEG/WebP allowlist externally replaceable although the approved quickstart contract fixes those three types. | Keep only resource limits configurable and enforce an internal fixed media-type set. | Fixed in Tasks 3-4. |
| P1 | Stability/Ops | `max-request-size: 5MB` left no multipart envelope room for a valid 5 MiB file, and the plan assumed synthetic MockMvc uploads exercise the servlet resolver's byte limit. | Keep `max-file-size` at 5 MiB, set request size to 6 MiB, test resolver exception mapping directly, and verify the real resolver with bootRun. | Fixed in Tasks 3, 5, and 8. |
| P1 | User/caller | An omitted multipart `file` part could escape to Spring's default error shape instead of the quickstart's stable error DTO. | Map `MissingServletRequestPartException` to `400 empty_input` and test it without assuming async controller dispatch. | Fixed in Task 5. |
| P1 | Stability/coroutines | The first dispatcher test requirement did not define how it would distinguish IO from CPU execution or close test executors. | Use named single-thread executor dispatchers, record executing threads in injected seams, and close both with `use`. | Fixed in Task 4. |
| P1 | Test evidence | The initial Task 5 GREEN command selected only the MockMvc application test and could leave the newly added exception-handler test unexecuted. | Select both the application and handler test classes in the focused GREEN command. | Fixed in Task 5. |
| P2 | Developer/API | The initial controller constructor injected fixtures during the POST-only task, leaving an unused private dependency until the later GET task. | Add the fixture dependency only when Task 6 adds the three GET methods. | Fixed in Tasks 5-6. |
| P2 | Build | The plan header said Kotlin 2.3+ while the repository and central catalog resolve Kotlin 2.4.0. | Pin the plan's actual toolchain description to Kotlin 2.4. | Fixed in the plan header. |

## Lens Rerun

| Lens | Verdict | Evidence |
|---|---|---|
| Performance | PASS | Tasks 3-4 cap encoded bytes before I/O/decode, cap dimensions before full decode, load fixtures once, clone only bounded bytes, and separate blocking multipart reads from CPU probe/decode/provider work. A benchmark is unnecessary for a non-production quickstart with no performance acceptance target. |
| Stability | PASS | Tasks 2-6 cover missing fixtures, immutable copies, malformed inputs, both dimension-limit forms, provider failures, cancellation, dispatcher ownership, missing parts, and servlet/application size boundaries. Task 8 adds clean-start tests and a live server smoke path. |
| Security | PASS | Tasks 3-5 fix the media allowlist, bound bytes/pixels/sides, probe before decode with bounded WebP fallback, sanitize messages, omit raw bytes/backend metadata, and document the unauthenticated local-only boundary. |
| Operator/Ops | PASS | Tasks 5 and 8 define stable status/error mapping, real resolver verification, startup failure for missing resources, exact registration/CI ownership, stateless rollback, exact-head review, and post-merge cleanup. Actuator/metrics are intentionally excluded by the approved example scope. |
| Developer/API | PASS | The repaired task order compiles producer types before consumers; ZXing construction is confined to configuration while service/controller types use `BarcodeReader`; every behavior has a focused RED/GREEN command and commit boundary. |
| User/caller | PASS | Tasks 5-7 cover the real multipart path, missing/empty/unsupported/oversized/malformed inputs, three deterministic GET scenarios, exact JSON/curl examples, English/Korean parity, diagrams, capability limits, and production warnings. |

## Step 3-R Integration

| Check | Result | Evidence |
|---|---|---|
| Spec and DoD mapping | PASS | Acceptance Traceability maps every approved endpoint, format, limit, fixture, documentation, and workflow requirement to Tasks 1-8. |
| Implementable ordering | PASS | Registration -> fixtures -> configuration -> service -> POST -> GET -> docs -> delivery; Task 3 no longer consumes the later service type. |
| Success/failure/edge paths | PASS | QR, no-result, JPEG, WebP fallback, empty/missing part, unsupported type, encoded size, side, pixels, malformed bytes, and provider reasons are assigned. |
| Coroutine/concurrency/lifecycle | PASS | Task 4 rethrows cancellation, injects dispatchers and probe seams, separates IO/CPU work, and closes named executor dispatchers. Fixture arrays are copy-on-read. |
| Commands and evidence | PASS | Every code task has focused RED/GREEN commands; final verification includes clean reruns, build, detekt, projects, actionlint, diff check, bootRun, and workflow verification. |
| Documentation/KDoc | PASS | Task 3 assigns English KDoc to public example types; Task 7 assigns equivalent English/Korean READMEs, exact curl/JSON, and three validated SVG/PNG pairs. |
| New-module chain | PASS | Task 1 and Task 8 cover settings, AGENTS, Examples PR/push/daily matrix, required test resources, project listing, publication surface, and root/provider locale links. |
| Compatibility/rollback | PASS | No production API/artifact/state/migration changes occur; the Rollback section removes the example and all registrations as one unit. |
| Duplication decision | PASS | Task 2 copies reviewed fixture bytes into module ownership instead of coupling runtime source sets to the benchmark module; provider logic remains reused through the API/provider modules. |

## Conditional N/A Evidence

- Spring Boot auto-configuration conditions and `AutoConfiguration.imports`:
  N/A because this is a runnable application with normal internal
  `@Configuration`, not a published auto-configuration artifact.
- Exposed/deprecated SQL operators and receiver shadowing: N/A because no
  database or Exposed code is in scope.
- Streaming EOF/truncation/reuse: N/A because the bounded endpoint reads one
  multipart file into a maximum 5 MiB byte array and exposes no streaming API.
- JDK preview/FFM migration: N/A because the module runs on Java 21 pure JVM
  Scrimage/ZXing and touches neither Java 25 nor native/FFM APIs.
- Testcontainers, OCR, libvips, JNI, and other heavyweight lifecycle checks are
  N/A from the pure-JVM Spring/ZXing dependency graph; the plan explicitly runs
  these checks sequentially only if the final diff unexpectedly introduces one.
- BOM, Maven publication, Kover/Codecov aggregation, benchmark evidence, and
  production `ci.yml`/nightly jobs are N/A because `examples/**` is
  non-published and the Examples workflow owns both PR and daily coverage.

## Final Verdict

- All initial P0/P1 findings have exact plan repairs and affected-lens reruns.
- The P2 toolchain and unused-dependency findings are fixed rather than
  deferred.
- No unresolved user decision, placeholder hash, later-produced type, or
  merge-authorization ambiguity remains.
- Latest convergence: **P0=0, P1=0**.

Required checks: 14/14; N/A: 6; Blocked: 0.
