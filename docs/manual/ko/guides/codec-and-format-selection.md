---
manualId: "codec-and-format-selection"
title: "코덱과 포맷 선택"
locale: "ko"
releaseRef: "0.3.0"
---

# 코덱과 포맷 선택

API에서 포맷 이름을 제공하는지, 선택한 백엔드가 실제로 인코딩/디코딩하는지, 대상 실행 환경에 필요한 코덱이 설치되어 있는지는 서로 다른 질문이다. 이를 하나로 보면 개발 환경에서는 통과하고 배포 환경에서만 실패한다.

## JVM 경로

[불변 이미지 처리](../modules/bluetape4k-images.md)는 Scrimage와 ImageIO 공급자를 쓴다. JPEG와 PNG가 가장 무난하다. TwelveMonkeys 의존성은 TIFF와 메타데이터 처리를 보강하고, WebP 저장은 Scrimage WebP 연동을 사용한다. SVG 래스터화에는 선택적 Batik 의존성이 필요하므로 SVG 입력을 받는 애플리케이션이 Batik을 직접 추가해야 한다.

움직이는 GIF에서 WebP로 바꾸는 작업, TIFF, SVG는 테스트 이미지를 따로 준비해 검증한다. 디코딩이 성공해도 메타데이터, 애니메이션, 알파 채널과 색상 처리가 달라질 수 있다.

## libvips 경로

공통 [Vips API](../native/vips-api.md)는 포맷과 인코딩 옵션을 정의하지만, Java 21과 Java 25 구현의 실제 기능은 네이티브 바인딩과 설치된 libvips에 달려 있다. AVIF와 HEIC는 아직 실험 단계다. 개발자 노트북의 결과만 믿지 말고 대상 장비에서 인코딩과 디코딩용 테스트 이미지를 실행한다.

## 선택 기준

- JPEG: 손실 압축을 허용하는 사진
- PNG: 무손실 출력, 알파 채널, 다이어그램과 시각 회귀용 테스트 이미지
- WebP: 클라이언트 지원과 인코더 동작을 확인한 뒤 사용
- TIFF: 문서와 보관용 입력, 여러 페이지와 메타데이터 요구는 별도 검증
- SVG: 래스터화 전에 크기와 외부 리소스 접근을 엄격히 제한
- AVIF/HEIC: 네이티브 코덱 확인과 대체 경로를 준비한 뒤 배포

입력으로 허용할 포맷과 출력으로 만들 포맷을 따로 기록한다. 만들 수 있는 모든 포맷을 업로드로 받을 필요는 없다.

## 근거 소스

- [릴리스 이미지 의존성](https://github.com/bluetape4k/bluetape4k-image/blob/a571c30004f571fe8cfcddc29670c1404d212ec6/images/build.gradle.kts)
- [Vips 이미지 포맷 계약](https://github.com/bluetape4k/bluetape4k-image/blob/a571c30004f571fe8cfcddc29670c1404d212ec6/images-vips-api/src/main/kotlin/io/bluetape4k/images/vips/VipsImageFormat.kt)
