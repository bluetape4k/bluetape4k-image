# Issue #197 Design 2-R Review

## Scope

Reviewed the parity design for `ImageLargeStreamingBenchmark` before
implementation. The review covered performance, stability/lifecycle, security,
operations, developer evidence, and library-user documentation/chart concerns.

## Verdict

**FAIL — P0: 0, P1: 5.** Do not write an implementation plan or change the
benchmark until every P1 finding is repaired and the affected lenses are
rerun.

## P1 Findings

1. **Cross-backend readiness is not a pass condition.**
   - The current vips path can consume `null` when initialization is unavailable,
     so a successful task and a present vips row do not prove a real libvips
     measurement.
   - Evidence: `ImageLargeStreamingBenchmark.kt:179-181, 368-373, 395-397`;
     design `:100-101, 140-142`.
   - Repair: add Java 25 FFM/libvips readiness and non-skipped vips execution as
     mandatory preflight evidence. Treat unavailable vips as a publication
     blocker for this cross-backend result.

2. **The full evidence lifecycle is incomplete.**
   - The primary result, GC-profiler addendum, both locations in each benchmark
     README, and root `README.md`/`README.ko.md` currently make claims from the
     asymmetric workload. The old detailed report also embeds the shared chart,
     which would mix an invalid table with a refreshed visual.
   - Evidence: `benchmark/images-benchmark/README.md:119-143, 347-360`,
     `benchmark/images-benchmark/README.ko.md:121-145, 350-363`,
     `README.md:82-89`, `README.ko.md:71-78`,
     `large-streaming-2026-06-05.md:41-105`.
   - Repair: decide and document refresh versus removal for the GC addendum and
     allocation claims; update both root README locales; make the old report a
     visible superseded page that cannot embed the current shared chart beside
     invalid values.

3. **The chart has incorrect semantic labels.**
   - Its values are for `large-photo` and `ocr-document`, but its legend says
     `JPEG` and `PNG`; both compared paths encode JPEG. The rendered scale text
     and tick behavior also need one consistent interpretation.
   - Evidence: `docs/scripts/generate-readme-visual-assets.py:850`;
     `ImageLargeStreamingBenchmark.kt:69-71, 183-185, 199-202, 220-223`.
   - Repair: require scenario legend labels, backend/boundary categories, and
     report/README/chart label-value parity in the design acceptance criteria.

4. **Measurement configuration has no authoritative precedence contract.**
   - The source annotation declares two warmups, while the generated Gradle
     task and existing raw result use one warmup and three measurements. The
     design records the latter without explaining the override or requiring the
     new raw JSON to prove its effective settings.
   - Evidence: `ImageLargeStreamingBenchmark.kt:61-63`,
     `benchmark/images-benchmark/build.gradle.kts:98-107`.
   - Repair: name the Gradle task as the authoritative measurement surface and
     require raw-result verification of forks, warmups, iterations, duration,
     mode, and time unit.

5. **Chart visual validation and generation scope are not closed.**
   - The generator writes the entire diagram/chart set, not only the target
     chart. The design requires XML validation but omits the required CairoSVG
     render, full-size PNG inspection, PNG validity/dimension check, and an
     allowlist/restore rule for unrelated generated assets.
   - Evidence: `generate-readme-visual-assets.py:855-861`; diagram skill
     common/chart contracts; observed generator side effect in this worktree.
   - Repair: add a one-asset visual QA ledger for the target SVG/PNG and a
     generated-file allowlist with explicit restoration of unrelated assets.

## P2/P3 Follow-ups

- P2: scrub refreshed raw JSON/report command metadata for user home paths,
  host names, and token-like JVM properties before committing it.
- P2: check for `bt4k-image-large-streaming-*` temporary-directory residue if
  setup fails before benchmark teardown.
- P3: document the effective Gradle-task override near the benchmark command.

## Positive Evidence

- The selected parity direction is sound: Scrimage alone applies
  `GRAYSCALE_FILTER`, while vips resizes and JPEG-encodes.
- Existing benchmark fixtures are deterministic, and normal output paths use
  `finally` cleanup.
- No new dependency, native-access expansion, or untrusted write path is
  introduced by the proposed change.

## Required Re-Review

After the P1 repairs, rerun performance, stability, security, architecture,
developer/API, and library-user lenses. The main session must then normalize
findings and record `P0=0` and `P1=0` before Step 3-R.

## Re-Review After Repair

**PASS — P0: 0, P1: 0.** The repaired design is eligible for a user review
gate before implementation planning. No source or benchmark artifact changed
during this review.

| Lens | Result | Evidence checked |
| --- | --- | --- |
| Performance | PASS | Color-preserving parity, authoritative Gradle/JMH settings, raw-row evidence, chart scenario/scale contract |
| Stability/SRE and security | PASS | Fail-fast libvips readiness, run-owned temporary residue, metadata scrubbing, generator allowlist, SVG/PNG QA |
| Architecture/scope | PASS | New-versus-archived evidence lifecycle, GC addendum policy, all README surfaces, narrow regression guards |
| Developer/API | PASS | Cross-backend execution contract, configuration precedence, raw-data traceability, artifact generation boundary |
| Library user | PASS | Both benchmark README locations and locales, root README locales, report supersession, chart labels and rendered visual checks |

### Non-Blocking Follow-Ups

- Make the implementation plan describe exception-safe benchmark-setup cleanup
  and preserve the libvips initialization cause in its fail-fast error.
- Make raw-data validation enumerate the expected backend/scenario rows.
- When checking the Scrimage path, verify the shared transform has no grayscale
  filter and that every affected benchmark method still JPEG-encodes after that
  transform.
