# Issue #246 BoofCV Provider 조사 검토

## 범위

#246의 research-only 결정을 issue acceptance criteria와 #215, #244, #245의 barcode provider architecture에 맞춰 검토한다.

## 발견 사항

P0/P1 발견 사항 없음.

## 점검

| 관점 | 결과 | 근거 |
| --- | --- | --- |
| license와 maintenance | PASS | BoofCV Apache-2.0 license file and Maven Central license metadata were checked. GitHub reports v1.4.0 published on 2026-05-25 and recent repository push activity. |
| runtime compatibility | PASS | BoofCV docs state Java 11+ runtime and Java 17 build requirement; Java 21 consumers are compatible. |
| provider 범위 | PASS | Official BoofCV examples and source grep show QR, Micro QR, and Aztec detector surfaces; no observed 1D/Data Matrix/PDF417 detector surface. |
| ZXing 비교 | PASS | ZXing public docs show materially broader 1D/2D barcode support and match the provider already implemented in #245. |
| architecture 경계 | PASS | Recommendation does not add dependencies or modules; BoofCV remains a deferred provider behind the existing `BarcodeReader` contract. |
| 후속 위생 | PASS | #247 is identified as the place to record BoofCV as a deferred specialized 2D provider in the capability matrix. |

## P0/P1 게이트

- P0 (CRITICAL): 0
- P1 (HIGH): 0
- P2/P3: 없음

## 검증 계획

- `git diff --check`
- Targeted `rg` for BoofCV/provider references
- Wiki preservation checks for the external research note
