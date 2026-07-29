# Issue #247 Barcode Fixture와 Capability 문서 검토

## 범위

#247의 deterministic barcode fixture helper, ZXing provider test reuse, README capability documentation을 검토한다.

## 발견 사항

P0/P1 발견 사항 없음.

## 점검

| 관점 | 결과 | 근거 |
| --- | --- | --- |
| fixture source safety | PASS | fixture는 test runtime에 생성된다. external barcode image binary는 commit하지 않는다. `BarcodeTestFixtures.GENERATED_SOURCE_NOTE` records the source note. |
| dependency boundary | PASS | `images-barcode-api` production dependencies remain provider-neutral. Test fixtures do not add ZXing, BoofCV, native, or commercial decoder dependencies to the API module. |
| provider test | PASS | ZXing tests reuse shared no-code, rotation, and malformed-byte helpers while keeping QR/Code 128 generation in ZXing test code. |
| docs parity | PASS | `README.md`, `README.ko.md`, `images-barcode-api/README.*`, and `images-barcode-zxing/README.*` document fixtures and provider capability scope. |
| BoofCV 결과 | PASS | Root README matrix records BoofCV as deferred specialized QR/Micro QR/Aztec provider, consistent with #246. |

## P0/P1 게이트

- P0 (CRITICAL): 0
- P1 (HIGH): 0
- P2/P3: 없음

## 검증 계획

- `./gradlew :bluetape4k-images-barcode-api:test :bluetape4k-images-barcode-zxing:test --configuration-cache --build-cache`
- `./gradlew :bluetape4k-images-barcode-api:compileTestFixturesKotlin :bluetape4k-images-barcode-zxing:compileTestKotlin --warning-mode all --configuration-cache --build-cache`
- `git diff --check`
- Targeted `rg` checks for fixture class names and provider matrix rows
