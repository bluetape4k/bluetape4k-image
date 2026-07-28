# Issue #245 ZXing Barcode Provider 검토

## 범위

- `bluetape4k-images-barcode-api`의 첫 concrete provider로 `bluetape4k-images-barcode-zxing`을 추가했다.
- Registered the module in Gradle, README locale set, AGENTS, CI, Nightly,
  release, snapshot publish, and Examples path filters.

## 검토 발견 사항

- P0: 없음.
- P1: 없음.
- P2: 없음.

## 근거

- `:bluetape4k-images-barcode-zxing:test` covers QR, Code 128, no-code,
  rotated QR, malformed input, unsupported formats, raw bytes, and region
  mapping.
- `rg` check confirms `com.google.zxing` imports are confined to
  `images-barcode-zxing`.
- `rg` check confirms no `!!` or MockK setup lifecycle issues in the new
  provider module.

## 남은 위험

- provider는 일반적으로 image당 decoded barcode 하나를 반환하는 ZXing의 simple `MultiFormatReader` path를 사용한다. 더 넓은 multi-barcode/capability matrix 작업은 #247과 future provider comparison issue에 남긴다.
