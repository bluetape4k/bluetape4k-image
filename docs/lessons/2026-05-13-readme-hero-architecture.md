# README Hero And Architecture Refresh

## Context

The image repository already had a root architecture diagram but lacked a visual
entrypoint and explicit project-purpose section.

## Decision

Store the generated image-processing workbench in `docs/assets/image-workbench.png`
and add purpose and feature sections ahead of the module table in both README
locales.

## Outcome

The root README now introduces the scrimage, coroutine I/O, libvips, JNI, FFM,
and benchmark story before the detailed module list.

## Verification

- Confirmed the generated asset exists as a PNG under `docs/assets`.
- Verified both README locales reference the shared image path.

## Future Guidance

Keep native backend constraints in `AGENTS.md`, `CLAUDE.md`, and README
architecture docs aligned when adding new image modules.
