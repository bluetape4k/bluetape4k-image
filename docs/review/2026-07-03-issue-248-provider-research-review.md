# Issue #248 Commercial and Native Provider 조사 검토

## 범위

future barcode provider candidate인 Dynamsoft, Aspose.BarCode, OpenCV, ZBar에 대한 #248 research conclusion을 검토한다.

## 발견 사항

P0/P1 발견 사항 없음.

## 점검

| 관점 | 결과 | 근거 |
| --- | --- | --- |
| license와 pricing | PASS | Dynamsoft and Aspose are commercial; OpenCV is Apache-2.0; ZBar is LGPL-2.1. |
| runtime과 CI 영향 | PASS | Dynamsoft and ZBar require native/runtime handling; Aspose requires commercial license handling for unrestricted CI; OpenCV server JVM packaging remains native/AAR-shaped. |
| symbology 범위 | PASS | Dynamsoft and Aspose have broad coverage; OpenCV barcode coverage is narrow; ZBar coverage is useful but native. |
| API 경계 | PASS | recommendation은 모든 candidate를 다음 범위 밖에 둔다: `images-barcode-api` and `bluetape4k-images`. |
| 후속 위생 | PASS | license/CI/runtime approval 없이는 implementation issue를 만들지 않는다. |

## P0/P1 게이트

- P0 (CRITICAL): 0
- P1 (HIGH): 0
- P2/P3: 없음

## 검증 계획

- Preserve research note in `bluetape4k-wiki`.
- Run `git diff --check`.
- Run targeted `rg` checks for candidate names, recommendations, and source links.
