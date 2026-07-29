# Issue #246 BoofCV barcode provider 연구

- 이슈: [#246](https://github.com/bluetape4k/bluetape4k-image/issues/246)
- Parent epic: [#215](https://github.com/bluetape4k/bluetape4k-image/issues/215)
- Milestone: `0.4.0`
- Branch/worktree: `docs/issue-246-boofcv-provider-research` at `.worktrees/docs-issue-246-boofcv-provider-research`
- Research date: 2026-07-03

## 결정

`0.4.0`에서는 전용 `images-barcode-boofcv` provider를 보류한다.

BoofCV는 향후 specialized geometric 2D provider로는 가능성이 있다. 특히 QR, Micro QR, Aztec에서 rejected candidate, finder-pattern geometry, pose utility, 더 풍부한 internal detector state가 중요할 때 유용할 수 있다. 그러나 `0.4.0`의 기본 OSS barcode provider로는 ZXing보다 낫지 않다. 관찰된 BoofCV barcode surface는 fiducial/2D 중심이며 Code 128, EAN, UPC, ITF, Codabar, Code 39 같은 일반 1D barcode family를 cover하지 않는다.

## primary source evidence

| 영역 | evidence | source |
| --- | --- | --- |
| License | BoofCV는 Apache License 2.0으로 배포된다. `org.boofcv:boofcv-all:1.4.0`의 Maven Central metadata도 Apache-2.0을 보고한다. | `lessthanoptimal/BoofCV`의 `LICENSE-2.0.txt`; Maven Central `org.boofcv:boofcv-all` |
| Maintenance | GitHub 기준 latest release는 `v1.4.0`, published `2026-05-25T19:07:44Z`, repository push activity는 `2026-07-02T16:37:36Z`다. | `gh repo view lessthanoptimal/BoofCV` |
| Runtime | BoofCV 문서는 runtime Java 11+, build Java 17 필요를 명시한다. Java 21 consumer는 이 runtime floor 안에 있다. | BoofCV Download page |
| Module shape | BoofCV docs는 Maven Central 사용을 권장하고 core functionality를 `all` module로 참조할 수 있으며 individual integration module은 분리된다고 설명한다. | BoofCV Download page |
| Dependency weight | `boofcv-all:1.4.0` POM은 Swing, JCodec, WebcamCapture, JavaCV, FFmpeg integration module을 포함해 9개 compile dependency를 가진다. `boofcv-core:1.4.0`은 13개 compile dependency와 recognition stack을 가져온다. `boofcv-recognition:1.4.0`은 6개 compile dependency를 가진다. | `repo1.maven.org`의 Maven Central POM |
| QR support | official QR example은 `FactoryFiducial.qrcode(config, GrayU8.class)`를 사용하고, BoofCV가 large image, small marker, rotation invariance, richer internal information, rejected marker를 위해 설계되었다고 설명한다. | BoofCV Example Detect QR Code |
| Micro QR support | official Micro QR example은 `FactoryFiducial.microqr(config, GrayU8.class)`를 사용하고 `MicroQrCodeDetector`로 decoded detection을 노출한다. | BoofCV Example Detect Micro QR Code |
| Aztec support | official Aztec example은 `FactoryFiducial.aztec(config, GrayU8.class)`를 사용하고 failed detection과 finder-pattern information을 노출한다. | BoofCV Example Detect Aztec Code |
| 1D coverage gap | BoofCV `SNAPSHOT` shallow source grep에서 `main/boofcv-recognition` 아래 QR, Micro QR, Aztec detector surface는 확인했지만 `DataMatrix`, `Code128`, `CODE_128`, `EAN_13`, `PDF417`, `PDF_417` source hit는 없었다. | `lessthanoptimal/BoofCV`에서 받은 local shallow clone `/tmp/boofcv-research` |
| ZXing breadth | ZXing은 자신을 Java multi-format 1D/2D barcode library로 문서화하며 UPC, EAN, Code 39, Code 93, Code 128, Codabar, ITF, QR, Data Matrix, Aztec, PDF417, MaxiCode, RSS-14, RSS-Expanded를 나열한다. | ZXing GitHub README and BarcodeFormat API |

## provider 비교

| 기준 | ZXing provider (`images-barcode-zxing`) | 잠재 BoofCV provider |
| --- | --- | --- |
| Default OSS provider fit | 강함. Pure JVM이고 barcode symbology coverage가 넓다. #245에서 이미 구현됐다. | default role로는 약함. specialized 2D geometric detection에서만 강함. |
| License | Apache-2.0. | Apache-2.0. |
| Java 21 compatibility | 현재 repo test가 Java 21 line에서 통과한다. | runtime floor는 Java 11+라 Java 21은 허용된다. build floor는 Java 17이다. |
| Supported barcode families | QR, Data Matrix, Aztec, PDF417, Code 128, EAN/UPC, ITF, Codabar 등 넓은 1D/2D coverage. | source-backed로 관찰된 official barcode-like scope는 QR, Micro QR, Aztec이다. 1D, Data Matrix, PDF417 detector surface는 관찰되지 않았다. |
| QR-family detection | 좋은 baseline이다. `tryHarder`는 rotation/noisy case에 도움이 된다. | QR geometry, rejected detection, pose detail이 유용한 경우 강한 후보다. |
| Bounding geometry | ZXing은 #245가 pixel-space region과 derived bounding box로 mapping하는 `ResultPoint` 값을 노출한다. | BoofCV는 QR/Aztec family에 더 풍부한 detector structure와 finder-pattern geometry를 노출한다. |
| Rotation handling | #245는 `tryHarder = true`로 rotated QR을 test한다. | official QR docs는 rotation-invariant detector design을 명시한다. |
| Dependency footprint | `core`는 POM에서 test-only JUnit 외 runtime dependency가 없다. `javase`는 `core`, JCommander, runtime `jai-imageio-core`를 추가한다. | 더 무겁다. targeted recognition stack도 BoofCV, GeoRegression, DDogleg, Trove dependency를 가져오며 `all`은 GUI/video/native-adjacent integration module도 가져온다. |
| Kotlin/JVM ergonomics | `MultiFormatReader`와 image luminance source를 통한 단순한 Java API다. | Kotlin에서 Java API를 사용할 수 있지만 BoofCV image conversion(`GrayU8`)과 symbology family별 detector type이 필요하다. |
| Multi-provider value | 0.4.0의 현재 provider다. | #247 fixture가 QR/Aztec geometry에서 구체적인 이점을 보일 때만 future optional provider다. |

## 권고

`0.4.0`에서 shipped provider set은 다음으로 유지한다:

- `images-barcode-api`
- `images-barcode-zxing`

지금 `images-barcode-boofcv`는 만들지 않는다. BoofCV는 epic/provider comparison surface에 보류로 기록하고, #247 fixture/capability matrix에는 BoofCV용 `Deferred specialized 2D provider` row를 포함한다.

다음 조건 중 하나가 참일 때만 follow-up BoofCV implementation issue를 만든다:

- #247 fixture result가 bluetape4k user에게 필요한 QR/Aztec localization 또는 rotation/noise case에서 ZXing이 실질적으로 약함을 보여준다.
- caller가 provider-neutral API에 BoofCV overfitting 없이 노출할 수 있는 rejected-marker diagnostics, finder-pattern geometry, pose metadata를 요구한다.
- provider scope가 general barcode backend가 아니라 QR, Micro QR, Aztec으로 명시적으로 제한된다.

## 출처

- BoofCV GitHub repository: <https://github.com/lessthanoptimal/BoofCV>
- BoofCV license file: <https://github.com/lessthanoptimal/BoofCV/blob/SNAPSHOT/LICENSE-2.0.txt>
- BoofCV Download page: <https://boofcv.org/index.php?title=Download>
- BoofCV QR example: <https://boofcv.org/index.php?title=Example_Detect_QR_Code>
- BoofCV Micro QR example: <https://boofcv.org/index.php?title=Example_Detect_Micro_QR_Code>
- BoofCV Aztec example: <https://boofcv.org/index.php?title=Example_Detect_Aztec_Code>
- Maven Central `org.boofcv:boofcv-all`: <https://central.sonatype.com/artifact/org.boofcv/boofcv-all>
- ZXing repository: <https://github.com/zxing/zxing>
- ZXing `BarcodeFormat` API: <https://zxing.github.io/zxing/apidocs/com/google/zxing/BarcodeFormat.html>

## #247 follow-up

provider capability matrix는 다음을 포함해야 한다:

- ZXing: implemented, broad 1D/2D OSS provider.
- BoofCV: deferred specialized 2D provider, source-backed scope QR, Micro QR, Aztec; observed 1D coverage 없음.
- Commercial/native providers: #248의 future research.
