# Issue #248 Commercial and Native Provider Research Review

## Scope

Review #248 research conclusions for Dynamsoft, Aspose.BarCode, OpenCV, and
ZBar as future barcode provider candidates.

## Findings

No P0/P1 findings.

## Checks

| Lens | Result | Evidence |
| --- | --- | --- |
| License and pricing | PASS | Dynamsoft and Aspose are commercial; OpenCV is Apache-2.0; ZBar is LGPL-2.1. |
| Runtime and CI impact | PASS | Dynamsoft and ZBar require native/runtime handling; Aspose requires commercial license handling for unrestricted CI; OpenCV server JVM packaging remains native/AAR-shaped. |
| Symbology scope | PASS | Dynamsoft and Aspose have broad coverage; OpenCV barcode coverage is narrow; ZBar coverage is useful but native. |
| API boundary | PASS | Recommendation keeps every candidate out of `images-barcode-api` and `bluetape4k-images`. |
| Follow-up hygiene | PASS | No implementation issue is created without license/CI/runtime approval. |

## P0/P1 Gate

- P0 (CRITICAL): 0
- P1 (HIGH): 0
- P2/P3: none

## Verification Plan

- Preserve research note in `bluetape4k-wiki`.
- Run `git diff --check`.
- Run targeted `rg` checks for candidate names, recommendations, and source links.
