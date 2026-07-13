# Issue #208 Codec/Runtime Matrix Lessons

## Context

Issue #208 adds a reproducible codec/runtime matrix for PNG, WebP, AVIF, and
HEIC across the Java 21 JNI and Java 25 FFM backend families. The accepted run
uses `cafe.jpg` as a web-photo fixture and `homer.jpg` as a profile fixture.

## Decision or Finding

- Treat a matrix cell as comparable only when scenario, format, direction,
  input hash, codec options, backend, JVM, libvips version, and JMH protocol are
  fixed. This report compares cells within one accepted Java 25 run and does
  not rank the unavailable Java 21 lane.
- Make unsupported or unavailable runtime paths explicit terminal states.
  Java 21 host incompatibility is evidence as 16 `N/A` cells, not an omitted
  backend and not a Java 25 win.
- Use direction-specific smoke probes before experimental AVIF/HEIC work. An
  encoder can be available while its decoder is not, so format-level support
  is too coarse.
- Promote only a complete, hash-linked, append-only evidence directory. A
  failed or interrupted attempt gets a new run ID rather than overwriting an
  accepted run.
- Keep the serialization catalog pin local and temporary until the governed
  alias appears in a release-train central catalog tag. Do not mutate the
  dependencies repository as part of image benchmark work.
- Disable atomicfu transformation in a module that does not use atomicfu but
  switches its Kotlin target between Java 21 and Java 25. Otherwise a Java 21
  verification can fail while loading stale Java 25 output from the same
  worktree.

## Outcome

The accepted run records 16 measured Java 25 cells and 16 Java 21 `N/A` cells.
For both the web-photo and profile scenarios, the report includes latency,
managed-heap allocation, input/output bytes, dimensions, hashes, backend/JVM,
libvips identity, and terminal status. English and Korean README summaries link
the report, immutable evidence, and matching SVG/PNG charts.

## Verification

- Benchmark-module tests: 70 passing tests on Java 25.
- Benchmark compilation: Java 25 followed by Java 21 passed without an
  intervening clean after the cross-toolchain atomicfu regression was repaired.
- Task graph: all 11 codec tasks registered; documented default dry run passed
  with an explicit run ID.
- Evidence audit: 32 terminal cells, 11 verified artifact links, 13 parseable
  JSON files, no symlinks, and no modified/deleted accepted raw evidence.
- Documentation audit: identical locale tables, valid SVG XML, rendered PNGs
  inspected at original size, and `git diff --check` passed.
- Six-lens implementation review converged at `P0=0`, `P1=0`.

## Future Guidance

If a future run measures both runtime families, generate comparison tables and
charts from an explicit canonical comparability key rather than joining on
format name alone. Keep no-op and lazy-row guards so unsupported experimental
directions cannot silently appear as measured. Remove the local serialization
version pin as soon as a release-train central catalog tag exposes the governed
alias, then rerun benchmark-module tests against that tag.
