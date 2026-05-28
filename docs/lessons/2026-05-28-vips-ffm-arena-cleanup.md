# Vips FFM Arena Cleanup

## Context

#107 required hardening Java 25 FFM image creation paths so failed native
load, wrapper construction, and derived image operations do not leave unclear
ownership state behind.

## Decision

Centralize owned `Arena` creation behind a small helper that closes the arena
on any creation failure and preserves the original failure, including suppressed
close failures. Keep derived resize, thumbnail, and crop failures isolated from
the source image because those operations share the source arena.

## Outcome

`ffmVipsImageOf(Path)` and byte-array decoding now use one owned-arena contract.
Regression tests assert that native load failures and wrapper-construction
failures close their arena, and that failed derived operations leave the source
image usable.

## Verification

- `./gradlew :bluetape4k-images-vips-java25:test --tests 'io.bluetape4k.images.vips.java25.FfmVipsImageTest'`
- `./gradlew :bluetape4k-images-vips-java25:test`

## Future Guard

Do not add new FFM decode/load entry points with manual `arena.close()` catch
branches. Route owned native creation through the shared helper so cleanup and
exception preservation stay consistent.
