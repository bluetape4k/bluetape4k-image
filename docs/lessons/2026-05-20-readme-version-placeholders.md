# README Version Placeholders

## Context

README dependency snippets still used `0.1.0-SNAPSHOT` after the source version moved to `0.1.1-SNAPSHOT`.

## Decision

Use `<version>` placeholders for image artifacts in both English and Korean README files.

## Verification

Grep README files for stale `0.1.0-SNAPSHOT` artifact examples before publishing.

## Future Guidance

Prefer placeholders in dependency snippets so release bumps do not create documentation drift.
