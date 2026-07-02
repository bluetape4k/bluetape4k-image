# Issue #248 Commercial and Native Barcode Provider Research

- Issue: [#248](https://github.com/bluetape4k/bluetape4k-image/issues/248)
- Parent epic: [#215](https://github.com/bluetape4k/bluetape4k-image/issues/215)
- Date: 2026-07-03 KST

## Decision

Do not add a commercial or native barcode provider module for 0.4.0.

`images-barcode-api` and `images-barcode-zxing` remain the right 0.4.0 shape.
Commercial/native providers should stay future optional backends until license,
CI-secret, native runtime, and redistribution constraints are explicitly
approved.

## Candidate Matrix

| Candidate | License / pricing | Runtime and CI impact | Symbology evidence | Kotlin/JVM fit | Recommendation |
| --- | --- | --- | --- | --- | --- |
| Dynamsoft Barcode Reader Java | Commercial. Docs require an active license key; current overview advertises a fully functional 30-day trial and flexible subscription/usage/license-server/offline licensing. | Java samples list Windows, Linux x64, Linux arm64, macOS universal 10.15+, and JDK 1.8+. CI would need license secrets and native runtime coverage. Individual SDK instances are not thread-safe, so concurrent use needs one instance per thread. | Official overview lists 30+ symbologies including QR Code, Data Matrix, PDF417, Code 128, Code 39, EAN-13, UPC-A, Aztec, MaxiCode, Micro QR, DotCode, postal and GS1 families. | Good API surface for enterprise use, but commercial/native deployment is outside default OSS module constraints. | Defer. Candidate for a future optional enterprise module only after license/CI policy exists. |
| Aspose.BarCode for Java | Commercial. Pricing page currently lists Developer Small Business at US$999. Licensing docs describe 30-day temporary licenses and evaluation limitations; unrestricted production use needs a commercial license. | Java page lists JRE 8 / Java SE 8+. Maven-oriented usage is straightforward, but unrestricted OSS CI would require a license or accept masked trial output. | Java product page lists many numeric, alphanumeric, and 2D symbologies; release page shows 26.6 on 2026-06-26. | Plausible pure-Java optional module; less native friction than Dynamsoft, but license restrictions block default CI. | Defer. Revisit only if a licensed commercial provider module is explicitly desired. |
| OpenCV barcode / QRCodeDetector | Apache-2.0. `org.opencv:opencv` Maven metadata shows Apache-2.0, but the artifact is an AAR and native/OpenCV packaging still matters for server JVM use. | Native/AAR packaging complicates JVM server distribution. OpenPnP's turn-key native package is convenient but latest release observed is 4.9.0-0 from 2023. | OpenCV barcode tutorial says BarcodeDetector supports EAN-8, EAN-13, UPC-A, and UPC-E. QR is handled by separate `QRCodeDetector`. | Useful for CV preprocessing or QR geometry experiments, not a broad barcode decoder. | Reject as barcode provider for now; defer only as CV helper research. |
| ZBar | LGPL-2.1. SourceForge and maintained GitHub fork describe C/native library usage. | JNI/native packaging and LGPL review would be required. Current docs emphasize C/C++/Python/Perl bindings, not a current JVM server artifact. | Supports EAN/UPC, Code 128, Code 93, Code 39, Codabar, Interleaved 2 of 5, QR Code, and SQ Code in the maintained fork. | Mature native reader, but JVM integration and license obligations do not fit default bluetape4k distribution. | Reject for JVM default; keep only as native/embedded reference. |

## Follow-up Policy

- Do not create implementation issues for these providers from #248 alone.
- Create a future implementation issue only when a concrete user case requires
  higher detection quality or symbology coverage than ZXing, and the license,
  CI secret, native runtime, and redistribution policy are already approved.
- If a commercial provider is later selected, keep it behind a separate optional
  module and do not let provider dependencies leak into `images-barcode-api` or
  `bluetape4k-images`.

## Source Notes

- Dynamsoft Java docs require an active license key and say individual SDK
  instances are not thread-safe for concurrent processing.
- Dynamsoft samples document desktop/server platform requirements across
  Windows, Linux x64, Linux arm64, macOS, and JDK 1.8+.
- Aspose licensing docs describe trial restrictions and commercial licensing.
- OpenCV docs currently limit `BarcodeDetector` barcode standards to
  EAN-8/EAN-13/UPC-A/UPC-E and document QR through `QRCodeDetector`.
- ZBar docs and maintained fork document LGPL/native library constraints.

## Sources

- Dynamsoft Java user guide: https://www.dynamsoft.com/barcode-reader/docs/server/programming/java/user-guide.html
- Dynamsoft Java introduction: https://www.dynamsoft.com/barcode-reader/docs/server/programming/java/
- Dynamsoft overview: https://www.dynamsoft.com/barcode-reader/overview-2/
- Dynamsoft Java samples: https://github.com/Dynamsoft/barcode-reader-java-samples
- Aspose.BarCode Java releases: https://releases.aspose.com/barcode/java/
- Aspose.BarCode Java pricing: https://purchase.aspose.com/pricing/barcode/java/
- Aspose.BarCode Java licensing: https://docs.aspose.com/barcode/java/licensing/
- OpenCV barcode tutorial: https://docs.opencv.org/4.x/d6/d25/tutorial_barcode_detect_and_decode.html
- OpenCV BarcodeDetector docs: https://docs.opencv.org/4.x/dc/df7/classcv_1_1barcode_1_1BarcodeDetector.html
- OpenCV QRCodeDetector docs: https://docs.opencv.org/4.x/de/dc3/classcv_1_1QRCodeDetector.html
- OpenCV Maven metadata: https://central.sonatype.com/artifact/org.opencv/opencv
- OpenPnP OpenCV package: https://github.com/openpnp/opencv
- ZBar maintained fork: https://github.com/mchehab/zbar
- ZBar SourceForge homepage: https://zbar.sourceforge.net/
- ZBar API docs: https://zbar.sourceforge.net/api/
