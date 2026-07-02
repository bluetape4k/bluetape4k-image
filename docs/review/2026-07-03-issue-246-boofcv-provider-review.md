# Issue #246 BoofCV Provider Research Review

## Scope

Review the research-only decision for #246 against the issue acceptance
criteria and the barcode provider architecture in #215, #244, and #245.

## Findings

No P0/P1 findings.

## Checks

| Lens | Result | Evidence |
| --- | --- | --- |
| License and maintenance | PASS | BoofCV Apache-2.0 license file and Maven Central license metadata were checked. GitHub reports v1.4.0 published on 2026-05-25 and recent repository push activity. |
| Runtime compatibility | PASS | BoofCV docs state Java 11+ runtime and Java 17 build requirement; Java 21 consumers are compatible. |
| Provider scope | PASS | Official BoofCV examples and source grep show QR, Micro QR, and Aztec detector surfaces; no observed 1D/Data Matrix/PDF417 detector surface. |
| ZXing comparison | PASS | ZXing public docs show materially broader 1D/2D barcode support and match the provider already implemented in #245. |
| Architecture boundary | PASS | Recommendation does not add dependencies or modules; BoofCV remains a deferred provider behind the existing `BarcodeReader` contract. |
| Follow-up hygiene | PASS | #247 is identified as the place to record BoofCV as a deferred specialized 2D provider in the capability matrix. |

## P0/P1 Gate

- P0 (CRITICAL): 0
- P1 (HIGH): 0
- P2/P3: none

## Verification Plan

- `git diff --check`
- Targeted `rg` for BoofCV/provider references
- Wiki preservation checks for the external research note
