# Projects 1.10.0 BOM handoff

## Context

`bluetape4k-projects` 1.10.0 was released and `bluetape4k-bom:1.10.0` is visible
from Maven Central.

## Decision

Update the local catalog's projects BOM version from 1.9.2 to 1.10.0 while
leaving image and AWS release lines unchanged.

## Outcome

Image builds now consume the stable projects 1.10.0 BOM for shared bluetape4k
module versions.

## Verification

- Maven Central HTTP 200 for `bluetape4k-bom:1.10.0`.

