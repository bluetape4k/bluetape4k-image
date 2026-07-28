# Issue #248 commercial 및 native barcode provider 연구

- 이슈: [#248](https://github.com/bluetape4k/bluetape4k-image/issues/248)
- Parent epic: [#215](https://github.com/bluetape4k/bluetape4k-image/issues/215)
- Date: 2026-07-03 KST

## 결정

`0.4.0`에는 commercial 또는 native barcode provider module을 추가하지 않는다.

`images-barcode-api`와 `images-barcode-zxing`이 `0.4.0`에 맞는 형태다. Commercial/native provider는 license, CI-secret, native runtime, redistribution constraint가 명시적으로 승인될 때까지 future optional backend로 남긴다.

## 후보 matrix

| 후보 | license / pricing | runtime 및 CI impact | symbology evidence | Kotlin/JVM fit | 권고 |
| --- | --- | --- | --- | --- | --- |
| Dynamsoft Barcode Reader Java | Commercial. docs는 active license key를 요구한다. 현재 overview는 fully functional 30-day trial과 subscription/usage/license-server/offline licensing을 광고한다. | Java sample은 Windows, Linux x64, Linux arm64, macOS universal 10.15+, JDK 1.8+를 나열한다. CI에는 license secret과 native runtime coverage가 필요하다. 개별 SDK instance는 thread-safe하지 않아 concurrent use에는 thread당 하나의 instance가 필요하다. | official overview는 QR Code, Data Matrix, PDF417, Code 128, Code 39, EAN-13, UPC-A, Aztec, MaxiCode, Micro QR, DotCode, postal/GS1 family 등 30개 이상의 symbology를 나열한다. | enterprise use에는 좋은 API surface지만 commercial/native deployment는 default OSS module constraint 밖이다. | 보류. license/CI policy가 생긴 뒤 future optional enterprise module 후보로만 유지한다. |
| Aspose.BarCode for Java | Commercial. pricing page는 현재 Developer Small Business를 US$999로 나열한다. licensing docs는 30-day temporary license와 evaluation limitation을 설명하며, unrestricted production use에는 commercial license가 필요하다. | Java page는 JRE 8 / Java SE 8+를 나열한다. Maven-oriented usage는 단순하지만 unrestricted OSS CI에는 license가 필요하거나 masked trial output을 받아들여야 한다. | Java product page는 많은 numeric, alphanumeric, 2D symbology를 나열한다. release page는 2026-06-26의 26.6을 보여준다. | 가능한 pure-Java optional module이다. Dynamsoft보다 native friction은 작지만 license restriction 때문에 default CI가 막힌다. | 보류. licensed commercial provider module이 명시적으로 필요할 때만 재검토한다. |
| OpenCV barcode / QRCodeDetector | Apache-2.0. `org.opencv:opencv` Maven metadata는 Apache-2.0을 보이지만 artifact가 AAR이고 server JVM 사용에는 native/OpenCV packaging 문제가 남는다. | Native/AAR packaging은 JVM server distribution을 복잡하게 만든다. OpenPnP의 turn-key native package는 편리하지만 관찰된 latest release는 2023년의 4.9.0-0이다. | OpenCV barcode tutorial은 BarcodeDetector가 EAN-8, EAN-13, UPC-A, UPC-E를 지원한다고 말한다. QR은 별도 `QRCodeDetector`가 처리한다. | CV preprocessing 또는 QR geometry experiment에는 유용하지만 broad barcode decoder는 아니다. | 현재 barcode provider로는 제외한다. CV helper research로만 보류한다. |
| ZBar | LGPL-2.1. SourceForge와 maintained GitHub fork는 C/native library 사용을 설명한다. | JNI/native packaging과 LGPL review가 필요하다. 현재 docs는 current JVM server artifact보다 C/C++/Python/Perl binding을 강조한다. | maintained fork는 EAN/UPC, Code 128, Code 93, Code 39, Codabar, Interleaved 2 of 5, QR Code, SQ Code를 지원한다. | 성숙한 native reader지만 JVM integration과 license obligation이 default bluetape4k distribution에 맞지 않는다. | JVM default에서는 제외한다. native/embedded reference로만 유지한다. |

## follow-up policy

- #248만으로 이 provider들의 implementation issue를 만들지 않는다.
- concrete user case가 ZXing보다 높은 detection quality 또는 symbology coverage를 요구하고, license, CI secret, native runtime, redistribution policy가 이미 승인된 경우에만 future implementation issue를 만든다.
- commercial provider를 나중에 선택하더라도 별도 optional module 뒤에 두고 provider dependency가 `images-barcode-api` 또는 `bluetape4k-images`로 새지 않게 한다.

## source notes

- Dynamsoft Java docs는 active license key를 요구하며 개별 SDK instance가 concurrent processing에 thread-safe하지 않다고 말한다.
- Dynamsoft sample은 Windows, Linux x64, Linux arm64, macOS, JDK 1.8+의 desktop/server platform requirement를 문서화한다.
- Aspose licensing docs는 trial restriction과 commercial licensing을 설명한다.
- OpenCV docs는 현재 `BarcodeDetector` barcode standard를 EAN-8/EAN-13/UPC-A/UPC-E로 제한하고 QR은 `QRCodeDetector`로 문서화한다.
- ZBar docs와 maintained fork는 LGPL/native library constraint를 문서화한다.

## 출처

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
