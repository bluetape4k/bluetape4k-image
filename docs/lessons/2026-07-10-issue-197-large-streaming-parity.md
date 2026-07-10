# Issue #197 Large Streaming Benchmark Parity

## Context

#197 aligned the Scrimage and libvips large-streaming benchmark to one
color-preserving decode-resize-JPEG-encode contract and made the comparable
lane Java 25 FFM-only.

## Decision or Finding

Do not promote one short benchmark snapshot into a universal `Path` throughput
or memory recommendation. Current Java 21 and Java 25 vips `Path` overloads
both read bounded compressed input just like the stream overloads: all current
vips input boundaries apply the 50 MiB guard and buffer the input. `Path` is a
caller API/lifecycle choice, not a streaming-memory or guard-bypass path.

## Outcome

The EN/KO root and benchmark README files state that boundary contract. The
Java 25 full benchmark command explicitly includes FFM-only large streaming;
the Java 21 instructions run only named JNI-compatible configurations. The
rebased large-streaming SVG/PNG was regenerated because its prior rendered
middle-dot glyph had become tofu.

## Verification

Targeted contract test, Java 25 benchmark compilation, a fresh 16-row Java 25
benchmark run, and a controlled invalid-FFM cleanup run passed. The chart SVG
was XML-checked and the full-size published PNG was visually inspected. The
Java 21 named task set was verified with Gradle `--dry-run`.

## Future Guidance

After rebasing benchmark evidence, regenerate the affected PNG and inspect the
published raster, not only its SVG source. When a benchmark backend becomes
exclusive, validate every documented full-suite command against the selected
target. Keep README recommendations tied to the raw measurement scope and the
actual input-boundary contract.
