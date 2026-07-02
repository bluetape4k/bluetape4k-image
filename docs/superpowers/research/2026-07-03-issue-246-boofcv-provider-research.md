# Issue #246 BoofCV Barcode Provider Research

- Issue: [#246](https://github.com/bluetape4k/bluetape4k-image/issues/246)
- Parent epic: [#215](https://github.com/bluetape4k/bluetape4k-image/issues/215)
- Milestone: `0.4.0`
- Branch/worktree: `docs/issue-246-boofcv-provider-research` at `.worktrees/docs-issue-246-boofcv-provider-research`
- Research date: 2026-07-03

## Decision

Defer a dedicated `images-barcode-boofcv` provider for `0.4.0`.

BoofCV is viable as a future specialized geometric 2D provider, especially for
QR, Micro QR, and Aztec cases where rejected candidates, finder-pattern
geometry, pose utilities, or richer internal detector state matter. It is not a
better default OSS barcode provider than ZXing for `0.4.0` because the observed
BoofCV barcode surface is fiducial/2D focused and does not cover common 1D
barcode families such as Code 128, EAN, UPC, ITF, Codabar, or Code 39.

## Primary Source Evidence

| Area | Evidence | Source |
| --- | --- | --- |
| License | BoofCV is distributed under Apache License 2.0. Maven Central metadata for `org.boofcv:boofcv-all:1.4.0` also reports Apache-2.0. | `LICENSE-2.0.txt` from `lessthanoptimal/BoofCV`; Maven Central `org.boofcv:boofcv-all` |
| Maintenance | GitHub reported latest release `v1.4.0`, published `2026-05-25T19:07:44Z`, with repository push activity on `2026-07-02T16:37:36Z`. | `gh repo view lessthanoptimal/BoofCV` |
| Runtime | BoofCV documentation says runtime requires Java 11+, while building requires Java 17. Java 21 consumers are inside that runtime floor. | BoofCV Download page |
| Module shape | BoofCV docs recommend Maven Central and state that core functionality can be referenced through the `all` module, with individual integration modules separate. | BoofCV Download page |
| Dependency weight | `boofcv-all:1.4.0` POM has 9 compile dependencies, including Swing, JCodec, WebcamCapture, JavaCV, and FFmpeg integration modules. `boofcv-core:1.4.0` has 13 compile dependencies and pulls the recognition stack. `boofcv-recognition:1.4.0` has 6 compile dependencies. | Maven Central POMs from `repo1.maven.org` |
| QR support | Official QR example uses `FactoryFiducial.qrcode(config, GrayU8.class)` and says BoofCV is designed for large images, small markers, rotation invariance, richer internal information, and rejected markers. | BoofCV Example Detect QR Code |
| Micro QR support | Official Micro QR example uses `FactoryFiducial.microqr(config, GrayU8.class)` and exposes decoded detections through `MicroQrCodeDetector`. | BoofCV Example Detect Micro QR Code |
| Aztec support | Official Aztec example uses `FactoryFiducial.aztec(config, GrayU8.class)` and exposes failed detections plus finder-pattern information. | BoofCV Example Detect Aztec Code |
| 1D coverage gap | A shallow source grep of BoofCV `SNAPSHOT` found QR, Micro QR, and Aztec detector surfaces under `main/boofcv-recognition`, but no source hits for `DataMatrix`, `Code128`, `CODE_128`, `EAN_13`, `PDF417`, or `PDF_417`. | Local shallow clone `/tmp/boofcv-research` from `lessthanoptimal/BoofCV` |
| ZXing breadth | ZXing documents itself as a Java multi-format 1D/2D barcode library and lists UPC, EAN, Code 39, Code 93, Code 128, Codabar, ITF, QR, Data Matrix, Aztec, PDF417, MaxiCode, RSS-14, and RSS-Expanded. | ZXing GitHub README and BarcodeFormat API |

## Provider Comparison

| Criterion | ZXing provider (`images-barcode-zxing`) | Potential BoofCV provider |
| --- | --- | --- |
| Default OSS provider fit | Strong. Pure JVM and broad barcode symbology coverage. Already implemented in #245. | Weak for default role. Strong only for specialized 2D geometric detection. |
| License | Apache-2.0. | Apache-2.0. |
| Java 21 compatibility | Current repo tests pass on the Java 21 line. | Runtime floor is Java 11+, so Java 21 is acceptable. Build floor is Java 17. |
| Supported barcode families | Broad 1D and 2D coverage: QR, Data Matrix, Aztec, PDF417, Code 128, EAN/UPC, ITF, Codabar, and others. | Observed official/source-backed barcode-like scope: QR, Micro QR, Aztec. No observed 1D, Data Matrix, or PDF417 detector surface. |
| QR-family detection | Good baseline. `tryHarder` helps rotation/noisy cases. | Strong candidate where QR geometry, rejected detections, and pose details are useful. |
| Bounding geometry | ZXing exposes `ResultPoint` values that #245 maps into pixel-space regions and a derived bounding box. | BoofCV exposes richer detector structures and finder-pattern geometry for QR/Aztec families. |
| Rotation handling | #245 tests rotated QR with `tryHarder = true`. | Official QR docs state rotation-invariant detector design. |
| Dependency footprint | `core` has no runtime dependencies beyond test-only JUnit in the POM; `javase` adds `core`, JCommander, and runtime `jai-imageio-core`. | Heavier. Even the targeted recognition stack pulls BoofCV, GeoRegression, DDogleg, and Trove dependencies; `all` also pulls GUI/video/native-adjacent integration modules. |
| Kotlin/JVM ergonomics | Simple Java APIs through `MultiFormatReader` and image luminance sources. | Java APIs are usable from Kotlin, but they require BoofCV image conversion (`GrayU8`) and separate detector types per symbology family. |
| Multi-provider value | Current provider for 0.4.0. | Future optional provider only if #247 fixtures show concrete QR/Aztec geometry wins. |

## Recommendation

For `0.4.0`, keep the shipped provider set as:

- `images-barcode-api`
- `images-barcode-zxing`

Do not create `images-barcode-boofcv` now. Record BoofCV as deferred in the
epic/provider comparison surface and let #247's fixture/capability matrix
include a `Deferred specialized 2D provider` row for BoofCV.

Create a follow-up BoofCV implementation issue only when one of these becomes
true:

- #247 fixture results show ZXing is materially weaker for QR/Aztec localization
  or rotation/noise cases that bluetape4k users need.
- A caller requires rejected-marker diagnostics, finder-pattern geometry, or
  pose metadata that the provider-neutral API can expose without overfitting to
  BoofCV.
- The provider scope is explicitly limited to QR, Micro QR, and Aztec, not a
  general barcode backend.

## Sources

- BoofCV GitHub repository: <https://github.com/lessthanoptimal/BoofCV>
- BoofCV license file: <https://github.com/lessthanoptimal/BoofCV/blob/SNAPSHOT/LICENSE-2.0.txt>
- BoofCV Download page: <https://boofcv.org/index.php?title=Download>
- BoofCV QR example: <https://boofcv.org/index.php?title=Example_Detect_QR_Code>
- BoofCV Micro QR example: <https://boofcv.org/index.php?title=Example_Detect_Micro_QR_Code>
- BoofCV Aztec example: <https://boofcv.org/index.php?title=Example_Detect_Aztec_Code>
- Maven Central `org.boofcv:boofcv-all`: <https://central.sonatype.com/artifact/org.boofcv/boofcv-all>
- ZXing repository: <https://github.com/zxing/zxing>
- ZXing `BarcodeFormat` API: <https://zxing.github.io/zxing/apidocs/com/google/zxing/BarcodeFormat.html>

## Follow-Up for #247

The provider capability matrix should include:

- ZXing: implemented, broad 1D/2D OSS provider.
- BoofCV: deferred specialized 2D provider, source-backed scope QR, Micro QR,
  and Aztec; no observed 1D coverage.
- Commercial/native providers: future research from #248.
