# bluetape4k-image WIP

> 버전: 0.1.0-SNAPSHOT | 브랜치: `develop`
> 원본 출처: bluetape4k-projects TODO.md § 6.5 (이관 2026-05-06)

---

## 우선순위

- 🔴 **High** — 릴리스 전 반드시 처리
- 🟡 **Medium** — 다음 마일스톤 대상
- 🟢 **Low** — 장기 개선 과제

---

## 1. images — 이미지 처리 확장

### 1.1 유사도 지표 확장 🟡

(2026-04-20 `ImageSimilarity` 기반 후속)

- [ ] **MSSIM** — 11×11 sliding window 기반 정밀 SSIM (현재는 global luminance SSIM)
- [ ] **aHash / dHash / wHash** — Average/Difference/Wavelet Hash (pHash 보완)
- [ ] **pHash 크기 옵션** — 64bit 고정 → 256bit/1024bit 선택 가능
- [ ] **색 히스토그램 유사도** — Chi-square, Bhattacharyya, Earth Mover's Distance
- [ ] **키포인트 매칭** — SIFT/ORB/AKAZE (OpenCV 또는 BoofCV 통합 검토)
- [ ] **CLIP/DINOv2 임베딩** — neural similarity

#### 참고 자료
- [scrimage 공식 문서](https://sksamuel.github.io/scrimage/)
- [SSIM 알고리즘 논문 (Wang et al., 2004)](https://ece.uwaterloo.ca/~z70wang/publications/ssim.pdf)
- [pHash 공식 사이트](http://phash.org/)
- [BoofCV (Java 컴퓨터 비전)](https://boofcv.org/)

### 1.2 필터 / 색 보정 🟢

- [ ] **Brightness / Contrast / Saturation / Gamma** Filter 래퍼 DSL
- [ ] **GaussianBlur / Sharpen / Sepia / Grayscale / Invert** scrimage 래퍼 통일
- [ ] **Vignette / Border / Rounded-corner** 장식 필터
- [ ] **Pixelate / Mosaic / Median filter** 노이즈 제거·모자이크
- [ ] **Color space 변환** — RGB ↔ HSV/HSL/LAB/YCbCr

### 1.3 변환 / 조작 🟢

- [ ] **AutoCrop** — 여백 자동 제거 (whitespace trim, 임계값 기반)
- [ ] **Smart crop** — 얼굴/관심 영역 중심 크롭 (saliency 기반)
- [ ] **Rotation/Flip/Mirror** 확장 API 일관화
- [ ] **Perspective transform** — 4점 호모그래피
- [ ] **Histogram equalization** — 콘트라스트 자동 보정

### 1.4 분석 API 🟡

- [x] **Dominant color extraction** — `DominantColor` scrimage 래퍼 구현 완료
- [x] **Blur/defocus detection** — `BlurDetector` Laplacian variance 기반 구현 완료
- [x] **EXIF 메타데이터** 읽기/쓰기 — `ExifData` metadata-extractor 래퍼 구현 완료
- [ ] **OCR** — Tesseract (tess4j) 또는 PaddleOCR 통합 인터페이스 → Issue [#1](https://github.com/bluetape4k/bluetape4k-image/issues/1)
- [ ] **얼굴/객체 탐지** — MediaPipe 또는 ONNX Runtime → Issue [#2](https://github.com/bluetape4k/bluetape4k-image/issues/2)
- [ ] **이미지 분류 ML 모델** — ONNX Runtime 기반 → Issue [#3](https://github.com/bluetape4k/bluetape4k-image/issues/3)

#### 참고 자료
- [drewnoakes/metadata-extractor GitHub](https://github.com/drewnoakes/metadata-extractor)
- [Tesseract OCR (tess4j)](https://github.com/nguyenq/tess4j)
- [MediaPipe Java](https://developers.google.com/mediapipe/solutions/guide)
- [ONNX Runtime Java](https://onnxruntime.ai/docs/get-started/with-java.html)

### 1.5 포맷 지원 🟢

- [ ] **AVIF** — 읽기/쓰기 (libavif 바인딩)
- [ ] **HEIC/HEIF** — iPhone 기본 포맷 지원
- [ ] **TIFF multi-page** — 문서 스캔용 다중 페이지 지원
- [ ] **Raw 카메라 포맷** — dcraw 연동
- [ ] **SVG 래스터화** — Apache Batik 래퍼

#### 참고 자료
- [libavif GitHub](https://github.com/AOMediaCodec/libavif)
- [Apache Batik SVG Toolkit](https://xmlgraphics.apache.org/batik/)
- [LibRaw GitHub](https://github.com/LibRaw/LibRaw)

### 1.6 성능 / 동시성 🟢

- [ ] **배치 처리 Flow DSL** — `Flow<File>.processImages { ... }`
- [ ] **썸네일 자동 생성 파이프라인** — 다중 사이즈 일괄 생성
- [ ] **Tile-based 대용량 이미지 처리** — 메모리 초과 없이 기가픽셀 처리

---

## 2. images-vips — libvips 고성능 백엔드 🟡

Scrimage(Java2D) 대비 4~10× 처리 속도·1/10 메모리. AVIF/HEIC/DZI/OpenSlide 등
Java2D가 못 다루는 포맷도 단일 API로 처리. Instagram/Cloudflare Images/Shopify 프로덕션 검증.

> `images-vips-api` + `images-vips-java21` + `images-vips-java25` 모듈 구현 완료 (FFM 기반)

### 2.1 남은 작업

**Phase 4 — ImageProcessor 추상화**

- [ ] `ImageProcessor` 공통 인터페이스 — scrimage/vips 중 자동 선택
- [ ] 선택 정책 — 파일 크기 > 10MB 또는 지원 포맷(AVIF/HEIC/DZI)이면 vips, 나머지는 scrimage
- [ ] `AutoImageProcessor` 편의 API — 내부 구현 숨김

**Phase 5 — 검증**

- [ ] JMH 벤치마크 — resize/encode/thumbnail (scrimage vs vips, 10개 대표 이미지)
- [ ] 메모리 프로파일링 — 기가픽셀 이미지 처리 시 heap/native 사용량
- [ ] Spring Boot 4 자동 구성 — `VipsImageAutoConfiguration`

### 2.2 통합

- [ ] **S3 업로드/다운로드** — `bluetape4k-aws` 활용 → Issue [#5](https://github.com/bluetape4k/bluetape4k-image/issues/5)
- [ ] **CDN URL 서명** — CloudFront/S3 pre-signed URL 유틸

#### 참고 자료
- [libvips 공식 문서](https://libvips.github.io/libvips/)
- [libvips GitHub](https://github.com/libvips/libvips)
- [OpenSeadragon (DZI 뷰어)](https://openseadragon.github.io/)
- [Java 22 Foreign Function & Memory API](https://docs.oracle.com/en/java/javase/22/core/foreign-function-and-memory-api.html)

---

## 3. images-captcha — CAPTCHA 이미지 생성 🟡

Issue [#4](https://github.com/bluetape4k/bluetape4k-image/issues/4)

- [ ] `x-obsoleted/captcha` (bluetape4k-projects, 10 kt) 코드 이관
- [ ] `images-captcha` 신규 모듈 생성
- [ ] `CaptchaGenerator` 인터페이스 + `SimpleCaptchaGenerator` / `NoiseCaptchaGenerator` 구현
- [ ] `CaptchaChallenge` (text + ImmutableImage) 모델
- [ ] captchaGenerator DSL 빌더
- [ ] Java2D headless CI 설정 (`-Djava.awt.headless=true`)

---

## 4. images-spring-boot4 — Spring Boot 통합 🟢

Issue [#5](https://github.com/bluetape4k/bluetape4k-image/issues/5)

- [ ] `ImageStorage` 인터페이스 + `S3ImageStorage` 구현
- [ ] `CdnUrlSigner` 인터페이스 + `CloudFrontUrlSigner` / `S3PreSignedUrlSigner`
- [ ] `ImageProcessingAutoConfiguration` Spring Boot 4 자동 구성
- [ ] `ImageProcessingProperties` 설정 프로퍼티 클래스
- [ ] floci Testcontainers S3 통합 테스트
