# Issue #197 Implementation Review (6-R)

Date: 2026-07-10
Scope: `origin/develop...perf/issue-197-large-streaming-parity`, including the
final documentation and generated-chart repairs in the worktree.

## Result

**PASS — P0: 0, P1: 0**

The large-streaming comparison now measures the same color-preserving
decode-resize-JPEG-encode contract for Scrimage and Java 25 FFM libvips.
`ImageLargeStreamingBenchmark` requires FFM and fails fast rather than silently
substituting JNI or emitting null rows.

## Independent review lenses

| Lens | P0 | P1 | P2 | Verdict |
| --- | ---: | ---: | ---: | --- |
| Performance / benchmark | 0 | 0 | 1 | PASS |
| Stability / lifecycle | 0 | 0 | 1 | PASS |
| Security / evidence handling | 0 | 0 | 0 | PASS |
| Operations / runbook | 0 | 0 | 0 | PASS |
| Developer / API | 0 | 0 | 1 | PASS |
| Library user / documentation | 0 | 0 | 0 | PASS |

## Blockers found and repaired

1. The rebased large-streaming SVG was stale and rendered a middle-dot glyph as
   tofu in CairoSVG output. Regenerated only the target SVG/PNG from the
   current chart generator; the published PNG was inspected at 3120x1720 and
   now uses the ASCII `ms/op - lower is better` label.
2. README text incorrectly promoted vips `Path` as the universal strongest
   large-file throughput/memory option. The committed short Java 25 snapshot
   does not establish that rule. EN/KO docs now describe `Path` as an
   API/lifecycle boundary only.
3. The Java 21 full-suite command would include the FFM-only
   `ImageLargeStreamingBenchmark`. EN/KO runbooks now use named JNI-compatible
   benchmark tasks and explicitly exclude FFM-only large streaming; the Java
   25 full-suite command explicitly includes it.
4. The first documentation wording implied that only non-`Path` vips loads were
   bounded. Source inspection confirms every current vips input overload,
   including `Path`, validates and buffers compressed input inside the 50 MiB
   guard. EN/KO root and benchmark documentation now state that invariant.

## Verification evidence

- `:bluetape4k-images-benchmark:test --tests '*ImageLargeStreamingBenchmarkContractTest'` passed.
- `:bluetape4k-images-benchmark:benchmarkBenchmarkCompile -Pvips.impl=java25` passed.
- A fresh Java 25 `benchmarkLargeStreamingBenchmark -Pvips.impl=java25` run
  completed all 16 rows and produced a local raw JSON artifact. Its normal
  short-run score variance was not copied into the committed evidence snapshot.
- A controlled invalid FFM library override produced the expected fail-fast
  benchmark error rows without leaving temporary-run residue.
- Primary and GC JSON artifacts passed structural, metadata, method/scenario,
  and sensitive-string gates; the SVG is XML-valid and the target PNG is valid.
- Java 21 named-task documentation was checked with Gradle `--dry-run`.
- `git diff --check` passed.

## Non-blocking follow-ups

- The committed raw evidence does not record compressed fixture byte sizes; a
  future evidence refresh can add them when they are relevant to comparison.
- The source contract test does not execute reflective FFM initialization. The
  fresh success/failure benchmark runs cover this release candidate, but an
  isolated automated startup/cleanup regression test would improve repeatable
  coverage.
- `Path` and stream paths currently both buffer bounded compressed input; future
  API work must not infer native streaming behavior from the benchmark boundary
  names alone.

## Handoff

Step 6-R is complete. The implementation is eligible for the pre-PR lesson,
commit, PR review, and CI gates; merge remains subject to explicit user approval.
