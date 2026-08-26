# 변경 기록

`bluetape4k-image`의 주요 변경 사항을 이 파일에 기록한다.

형식은 [Keep a Changelog](https://keepachangelog.com/en/1.0.0/)를 따른다.
이 project는 [Semantic Versioning](https://semver.org/spec/v2.0.0.html)을 따른다.

## [Unreleased]

### 변경

- privacy pipeline의 runtime 객체와 Spring storage/CDN collaborator에서 허위
  `Serializable` 계약을 제거하고, Jackson 3 기반 `schemaVersion=1` snapshot codec,
  bounded stream decode, 방어적 byte/collection copy를 추가했다. 기존 runtime Java
  serialization 사용자는 `PrivacyDerivativePayload`/report/batch snapshot으로
  migration해야 한다 ([#481](https://github.com/bluetape4k/bluetape4k-image/issues/481)).
- AI/ML backend 연구 umbrella [#513](https://github.com/bluetape4k/bluetape4k-image/issues/513)와
  PaddleOCR child [#169](https://github.com/bluetape4k/bluetape4k-image/issues/169)를
  각각 `OPEN / Backlog / BACKLOG / DEFERRED`와 `Backlog / DEFERRED`로 보존한다.
  Tesseract/Tess4J baseline은 기본 OCR 경로로 유지하고, PaddleOCR model download, ONNX production backend,
  benchmark adoption은 license·immutable artifact·producer provenance·offline
  receipt가 확보될 때까지 활성화하지 않는다 ([#543](https://github.com/bluetape4k/bluetape4k-image/issues/543),
  [#544](https://github.com/bluetape4k/bluetape4k-image/issues/544),
  [#545](https://github.com/bluetape4k/bluetape4k-image/issues/545),
  [#609](https://github.com/bluetape4k/bluetape4k-image/issues/609),
  [#611](https://github.com/bluetape4k/bluetape4k-image/issues/611)). 최종
  PaddleOCR adoption gate [#547](https://github.com/bluetape4k/bluetape4k-image/issues/547)의
  현재 결정은 `DEFER`이며, 재개 증거는 새로운 결정의 입력으로만 사용한다.

## [0.4.0] - 2026-08-06

### 추가

- Provider-neutral barcode extraction contract와 pure-JVM ZXing provider를
  추가하고, deterministic fixture와 capability matrix를 문서화했다
  ([#244](https://github.com/bluetape4k/bluetape4k-image/issues/244),
  [#245](https://github.com/bluetape4k/bluetape4k-image/issues/245),
  [#247](https://github.com/bluetape4k/bluetape4k-image/issues/247)).
- 재현 가능한 ZXing extraction benchmark와 Spring Boot 4 barcode API quickstart를
  추가했다. Quickstart는 bounded multipart input과 decoded-image input을 다룬다
  ([#272](https://github.com/bluetape4k/bluetape4k-image/issues/272),
  [#273](https://github.com/bluetape4k/bluetape4k-image/issues/273)).
- Production ML runtime을 도입하지 않고 backend-neutral image detection model,
  sensitive-content result model, moderation policy decision, privacy-safe
  derivative generation, deterministic workflow example을 추가했다
  ([#2](https://github.com/bluetape4k/bluetape4k-image/issues/2),
  [#214](https://github.com/bluetape4k/bluetape4k-image/issues/214),
  [PR #237](https://github.com/bluetape4k/bluetape4k-image/pull/237),
  [PR #238](https://github.com/bluetape4k/bluetape4k-image/pull/238),
  [PR #239](https://github.com/bluetape4k/bluetape4k-image/pull/239)).
- 기존 plain-text extraction API를 유지하면서 structured OCR page, block, line,
  word result를 추가했다
  ([PR #240](https://github.com/bluetape4k/bluetape4k-image/pull/240),
  [PR #241](https://github.com/bluetape4k/bluetape4k-image/pull/241)).
- EXIF, IPTC, XMP, format-specific metadata extraction을 제한된 형태로 제공하는
  public-safe extended image metadata report를 추가했다
  ([#213](https://github.com/bluetape4k/bluetape4k-image/issues/213)).
- Java 21 JNI와 Java 25 FFM vips backend를 위한 stable/incubating WebP, HEIC,
  AVIF codec capability report를 추가했다
  ([#212](https://github.com/bluetape4k/bluetape4k-image/issues/212)).
- 측정됨, 건너뜀, 미지원, experimental 조합을 재현 가능한 evidence로 구분하는
  codec/runtime matrix benchmark를 추가했다
  ([#208](https://github.com/bluetape4k/bluetape4k-image/issues/208)).

### 변경

- Development train에 맞춰 managed catalog를
  `bluetape4k-bom:1.11.1-SNAPSHOT`, `bluetape4k-aws-bom:0.5.0-SNAPSHOT`,
  catalog ref `catalog/2026-07-08-00`과 정렬했다
  ([PR #268](https://github.com/bluetape4k/bluetape4k-image/pull/268),
  [PR #269](https://github.com/bluetape4k/bluetape4k-image/pull/269)).
- Binding-neutral vips API를 scrimage/Java2D implementation stack에서 분리하고
  consumer migration boundary를 문서화했다
  ([#202](https://github.com/bluetape4k/bluetape4k-image/issues/202)).
- Large-streaming benchmark가 equivalent backend work를 비교하도록 정리하고,
  natural-photo 결과와 재현 가능한 vips benchmark command를 갱신했다
  ([#197](https://github.com/bluetape4k/bluetape4k-image/issues/197),
  [#200](https://github.com/bluetape4k/bluetape4k-image/issues/200),
  [#201](https://github.com/bluetape4k/bluetape4k-image/issues/201)).
- Vips dependency coordinate를 바로잡고 Spring Boot consumer를 위한 BOM 또는
  explicit-version guidance를 추가했다
  ([#198](https://github.com/bluetape4k/bluetape4k-image/issues/198),
  [#199](https://github.com/bluetape4k/bluetape4k-image/issues/199)).
- Build-logic 변경 시 affected module test가 실행되고 publication 전에 OCR/vips
  full validation이 필요하도록 CI, snapshot, Maven Central workflow를 강화했다
  ([#184](https://github.com/bluetape4k/bluetape4k-image/issues/184),
  [#194](https://github.com/bluetape4k/bluetape4k-image/issues/194),
  [#195](https://github.com/bluetape4k/bluetape4k-image/issues/195),
  [#196](https://github.com/bluetape4k/bluetape4k-image/issues/196)).
- 미완료 `0.4.0` closeout과 Backlog로 미룬 작업이 분리되도록 roadmap을 갱신했다
  ([#271](https://github.com/bluetape4k/bluetape4k-image/issues/271)).

### 버그 수정

- Java 25 FFM derived-image ownership을 수정해 native memory가 `VipsImage`가
  약속한 lifetime 동안 유효하도록 했다
  ([#190](https://github.com/bluetape4k/bluetape4k-image/issues/190)).
- Oversized 또는 검증 불가능한 download에서 S3 storage가 fail closed 하도록 하고,
  필요한 `S3Operations` support가 없을 때 fail fast 하도록 했다
  ([#191](https://github.com/bluetape4k/bluetape4k-image/issues/191),
  [#192](https://github.com/bluetape4k/bluetape4k-image/issues/192)).
- Thumbnail/OCR processing 전에 bounded external-image decode와 decoded-dimension
  guardrail을 추가했다
  ([#193](https://github.com/bluetape4k/bluetape4k-image/issues/193),
  [#255](https://github.com/bluetape4k/bluetape4k-image/issues/255)).
- Local/S3 storage validation, CloudFront configuration, malformed Ktor thumbnail
  handling, vips path-input snapshot, in-memory CAPTCHA challenge retention을
  강화했다
  ([#256](https://github.com/bluetape4k/bluetape4k-image/issues/256),
  [#257](https://github.com/bluetape4k/bluetape4k-image/issues/257),
  [#258](https://github.com/bluetape4k/bluetape4k-image/issues/258),
  [#259](https://github.com/bluetape4k/bluetape4k-image/issues/259),
  [#260](https://github.com/bluetape4k/bluetape4k-image/issues/260),
  [#261](https://github.com/bluetape4k/bluetape4k-image/issues/261)).

## [0.3.0] - 2026-06-27

### 추가

- Basic OCR, Spring Boot, Ktor adoption path를 위한 OCR module support와 runnable
  usage example을 추가했다
  ([#1](https://github.com/bluetape4k/bluetape4k-image/issues/1),
  [#171](https://github.com/bluetape4k/bluetape4k-image/issues/171),
  [#172](https://github.com/bluetape4k/bluetape4k-image/issues/172),
  [#173](https://github.com/bluetape4k/bluetape4k-image/issues/173)).
- Okio 기반 memory-conscious large-file image I/O API를 추가하고 large-image 및
  streaming benchmark coverage를 확장했다
  ([#165](https://github.com/bluetape4k/bluetape4k-image/issues/165),
  [#166](https://github.com/bluetape4k/bluetape4k-image/issues/166)).

### 변경

- Coordinated dependencies train을 위해 release line을 `bluetape4k-bom:1.11.0`,
  `bluetape4k-aws-bom:0.4.0`과 정렬했다.
- Host-native OCR runtime compatibility를 평가하고 `0.3.0` line release
  documentation을 갱신했다
  ([#175](https://github.com/bluetape4k/bluetape4k-image/issues/175)).

### 버그 수정

- Image golden-chain Nightly coverage를 안정화하고 scheduled CI snapshot dependency
  read가 Central snapshot authentication과 retry failure에 더 강하도록 했다
  ([#144](https://github.com/bluetape4k/bluetape4k-image/issues/144),
  [#146](https://github.com/bluetape4k/bluetape4k-image/issues/146),
  [#148](https://github.com/bluetape4k/bluetape4k-image/issues/148),
  [#150](https://github.com/bluetape4k/bluetape4k-image/issues/150),
  [#152](https://github.com/bluetape4k/bluetape4k-image/issues/152),
  [#155](https://github.com/bluetape4k/bluetape4k-image/issues/155),
  [#158](https://github.com/bluetape4k/bluetape4k-image/issues/158),
  [#160](https://github.com/bluetape4k/bluetape4k-image/issues/160),
  [#177](https://github.com/bluetape4k/bluetape4k-image/issues/177)).

## [0.2.0] - 2026-06-01

### 추가

- CAPTCHA image challenge generation, refreshable challenge service, verification
  service contract를 위한 `images-captcha` module을 추가했다
  ([PR #88](https://github.com/bluetape4k/bluetape4k-image/pull/88),
  [PR #119](https://github.com/bluetape4k/bluetape4k-image/pull/119),
  [PR #127](https://github.com/bluetape4k/bluetape4k-image/pull/127),
  closes [#4](https://github.com/bluetape4k/bluetape4k-image/issues/4),
  [#101](https://github.com/bluetape4k/bluetape4k-image/issues/101)).
- Shared `bluetape4k-ktor-*` helper와 정렬된 `images-ktor` CAPTCHA
  issue/verification route helper와 multipart thumbnail API를 추가했다
  ([PR #119](https://github.com/bluetape4k/bluetape4k-image/pull/119),
  [PR #127](https://github.com/bluetape4k/bluetape4k-image/pull/127),
  [PR #136](https://github.com/bluetape4k/bluetape4k-image/pull/136),
  closes [#118](https://github.com/bluetape4k/bluetape4k-image/issues/118),
  [#135](https://github.com/bluetape4k/bluetape4k-image/issues/135)).
- Pure JVM processing, Spring Boot local storage, Ktor image API용 runnable
  example을 추가했다
  ([PR #128](https://github.com/bluetape4k/bluetape4k-image/pull/128),
  [PR #130](https://github.com/bluetape4k/bluetape4k-image/pull/130),
  [PR #131](https://github.com/bluetape4k/bluetape4k-image/pull/131),
  closes [#124](https://github.com/bluetape4k/bluetape4k-image/issues/124),
  [#125](https://github.com/bluetape4k/bluetape4k-image/issues/125),
  [#126](https://github.com/bluetape4k/bluetape4k-image/issues/126)).
- OCR, face/object detection, classification follow-up work를 위한 research-backed
  image AI dependency strategy를 기록했다
  ([PR #129](https://github.com/bluetape4k/bluetape4k-image/pull/129),
  closes [#83](https://github.com/bluetape4k/bluetape4k-image/issues/83),
  [#84](https://github.com/bluetape4k/bluetape4k-image/issues/84),
  [#85](https://github.com/bluetape4k/bluetape4k-image/issues/85)).

### 변경

- Release line이 `io.github.bluetape4k:bluetape4k-bom:1.10.0`과 stable
  `io.github.bluetape4k.aws:bluetape4k-aws-bom:0.3.0`을 소비하도록 준비했다
  ([PR #92](https://github.com/bluetape4k/bluetape4k-image/pull/92),
  [PR #94](https://github.com/bluetape4k/bluetape4k-image/pull/94),
  [PR #134](https://github.com/bluetape4k/bluetape4k-image/pull/134)).
- Vips/java21/java25 benchmark evidence, natural-photo fixture, chart, benchmark
  report regeneration guidance를 갱신했다
  ([PR #89](https://github.com/bluetape4k/bluetape4k-image/pull/89),
  [PR #117](https://github.com/bluetape4k/bluetape4k-image/pull/117),
  [PR #121](https://github.com/bluetape4k/bluetape4k-image/pull/121),
  [PR #122](https://github.com/bluetape4k/bluetape4k-image/pull/122),
  closes [#86](https://github.com/bluetape4k/bluetape4k-image/issues/86),
  [#103](https://github.com/bluetape4k/bluetape4k-image/issues/103),
  [#104](https://github.com/bluetape4k/bluetape4k-image/issues/104),
  [#105](https://github.com/bluetape4k/bluetape4k-image/issues/105)).
- AVIF/HEIC native codec boundary, libvips runtime setup, CAPTCHA lifecycle
  ownership, 완료된 0.2.0 roadmap 상태를 문서화했다
  ([PR #87](https://github.com/bluetape4k/bluetape4k-image/pull/87),
  [PR #120](https://github.com/bluetape4k/bluetape4k-image/pull/120),
  [PR #95](https://github.com/bluetape4k/bluetape4k-image/pull/95),
  closes [#111](https://github.com/bluetape4k/bluetape4k-image/issues/111),
  [#112](https://github.com/bluetape4k/bluetape4k-image/issues/112),
  [#113](https://github.com/bluetape4k/bluetape4k-image/issues/113),
  [#114](https://github.com/bluetape4k/bluetape4k-image/issues/114)).

### 제거

- `0.2.0`에서 removal 예정으로 deprecated 되었던 `ImmutableImage.useGraphics(...)`,
  `hammingDistance(...)` compatibility shim을 제거했다. 대신
  `ImmutableImage.withGraphics { }`와 `HashDistance.hamming(a, b)`를 사용한다
  ([#61](https://github.com/bluetape4k/bluetape4k-image/issues/61)).

### 버그 수정

- Optional S3/CDN auto-configuration fallback behavior와 release workflow catalog
  selection을 강화했다
  ([PR #97](https://github.com/bluetape4k/bluetape4k-image/pull/97),
  [PR #119](https://github.com/bluetape4k/bluetape4k-image/pull/119),
  closes [#109](https://github.com/bluetape4k/bluetape4k-image/issues/109)).
- AVIF/HEIC capability-gated read/write support, double-close, use-after-close,
  codec capability, failed creation path를 포함해 native vips lifecycle과 FFM
  arena cleanup contract를 방어했다
  ([PR #115](https://github.com/bluetape4k/bluetape4k-image/pull/115),
  [PR #116](https://github.com/bluetape4k/bluetape4k-image/pull/116),
  closes [#100](https://github.com/bluetape4k/bluetape4k-image/issues/100),
  [#107](https://github.com/bluetape4k/bluetape4k-image/issues/107),
  [#108](https://github.com/bluetape4k/bluetape4k-image/issues/108)).
- Repository automation의 workflow token permission을 제한했다
  ([PR #132](https://github.com/bluetape4k/bluetape4k-image/pull/132)).

## [0.1.2] - 2026-05-23

### 변경

- Release line이 `io.github.bluetape4k:bluetape4k-bom:1.9.1`, shared
  bluetape4k dependency catalog, `io.github.bluetape4k.aws:bluetape4k-aws-bom:0.2.1`을
  소비하도록 준비했다
  ([PR #77](https://github.com/bluetape4k/bluetape4k-image/pull/77),
  [PR #78](https://github.com/bluetape4k/bluetape4k-image/pull/78),
  [PR #80](https://github.com/bluetape4k/bluetape4k-image/pull/80)).
- Tag-triggered 및 manual release dispatch를 위해 release workflow catalog ref
  selection을 parameterize했다
  ([PR #79](https://github.com/bluetape4k/bluetape4k-image/pull/79)).

## [0.1.1] - 2026-05-22

### 추가

- `images-spring-boot` module을 추가했다. Spring Boot 4 image storage, CDN signing,
  reactive health, Micrometer metrics auto-configuration을 제공하며
  `LocalImageStorage`, `S3ImageStorage`, `S3PreSignedUrlSigner`,
  `CloudFrontUrlSigner`, 다섯 auto-configuration phase를 포함한다
  ([PR #42](https://github.com/bluetape4k/bluetape4k-image/pull/42),
  closes [#5](https://github.com/bluetape4k/bluetape4k-image/issues/5)).
- Root README hero image와 project-purpose/feature entrypoint documentation을
  갱신했다 ([PR #27](https://github.com/bluetape4k/bluetape4k-image/pull/27)).
- Image library consumer를 위한 `bluetape4k-image-bom` BOM module을 추가했다
  ([PR #12](https://github.com/bluetape4k/bluetape4k-image/pull/12)).
- Image BOM module의 English/Korean README를 추가했다
  ([PR #13](https://github.com/bluetape4k/bluetape4k-image/pull/13)).
- CI, nightly, snapshot, release, code-quality check용 GitHub Actions workflow를
  추가했다 ([PR #7](https://github.com/bluetape4k/bluetape4k-image/pull/7)).

### 변경

- `images` module test coverage를 batch processing, animated writer, filter DSL,
  utility function까지 확장했다
  ([PR #41](https://github.com/bluetape4k/bluetape4k-image/pull/41)).
- `images-vips-java21`와 `images-vips-java25`의 VipsImage writer class unit test를
  추가했다 ([PR #40](https://github.com/bluetape4k/bluetape4k-image/pull/40)).
- `images-vips-java25`에 `FfmVipsRuntime` concurrency test를 추가했다
  ([PR #39](https://github.com/bluetape4k/bluetape4k-image/pull/39)).
- `0.1.1` release line이 `io.github.bluetape4k:bluetape4k-bom:1.9.0`과
  `io.github.bluetape4k.aws:bluetape4k-aws-bom:0.2.0`을 소비하도록 준비했다.
- Nightly를 smoke/full lane으로 나누고 lessons, Kover, Dependabot, NMCP,
  compatibility-guard maintenance를 정규화했다
  ([PR #15](https://github.com/bluetape4k/bluetape4k-image/pull/15),
  [PR #16](https://github.com/bluetape4k/bluetape4k-image/pull/16),
  [PR #17](https://github.com/bluetape4k/bluetape4k-image/pull/17),
  [PR #18](https://github.com/bluetape4k/bluetape4k-image/pull/18),
  [PR #22](https://github.com/bluetape4k/bluetape4k-image/pull/22),
  [PR #24](https://github.com/bluetape4k/bluetape4k-image/pull/24),
  [PR #25](https://github.com/bluetape4k/bluetape4k-image/pull/25),
  [PR #26](https://github.com/bluetape4k/bluetape4k-image/pull/26)).
- JVips와 annotation을 포함한 image dependency catalog 및 관련 dependency bump를
  갱신했다
  ([PR #14](https://github.com/bluetape4k/bluetape4k-image/pull/14),
  [PR #20](https://github.com/bluetape4k/bluetape4k-image/pull/20),
  [PR #21](https://github.com/bluetape4k/bluetape4k-image/pull/21),
  [PR #23](https://github.com/bluetape4k/bluetape4k-image/pull/23)).
- CI가 path filtering과 retry configuration을 사용하도록 했다
  ([PR #10](https://github.com/bluetape4k/bluetape4k-image/pull/10)).
- Test code를 Kluent에서 `bluetape4k-assertions`로 migration했다
  ([PR #11](https://github.com/bluetape4k/bluetape4k-image/pull/11)).

### 제거

- `images`의 pre-stabilization typo compatibility API인 `usingSuspend(...)`,
  `SuspendPngWriter.NoComppression`, `ImageOuptputStreamSupportKt` Java facade를
  제거했다. `ImmutableImage.useGraphics(...)`와 `hammingDistance(...)`는
  `0.2.0` removal까지 deprecated 상태로 남겼다
  ([#61](https://github.com/bluetape4k/bluetape4k-image/issues/61)).

### 버그 수정

- 모든 published module의 POM license metadata를 Apache 2.0에서 MIT로 정렬했다
  ([PR #38](https://github.com/bluetape4k/bluetape4k-image/pull/38)).
- Repository license text를 모든 module에서 MIT로 정렬했다
  ([PR #28](https://github.com/bluetape4k/bluetape4k-image/pull/28)).
