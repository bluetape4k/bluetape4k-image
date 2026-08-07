---
manualId: "ocr-setup"
title: "OCR 설정"
locale: "ko"
releaseRef: "0.4.0"
---

# OCR 설정

OCR 모듈은 Tess4J/Tesseract를 <code>ImmutableImage</code>에 연결한다. Maven 아티팩트를 추가하면 Java 연동 코드는 들어오지만 네이티브 엔진과 언어 데이터까지 설치되지는 않는다.

## Host engine 설치하기

macOS:

    brew install tesseract tesseract-lang

Ubuntu 또는 Debian:

    sudo apt-get install tesseract-ocr tesseract-ocr-eng

사용할 언어를 모두 설치한다. <code>tesseract --list-langs</code> 결과에 <code>OcrOptions</code>로 넘길 언어가 있는지 확인한다. 배포 디렉터리가 Tesseract 기본값과 다르면 데이터 경로도 명시한다.

## 가장 작은 API부터 실행하기

[OCR 모듈](../modules/bluetape4k-images-ocr.md)을 추가하고 <code>ImmutableImage</code>를 읽은 뒤 언어와 페이지 분할 옵션을 넣어 <code>extractText</code>를 호출한다. 기본 엔진은 호출마다 새 Tess4J 인스턴스를 만든다. 변경 가능한 엔진 상태를 공유하지는 않지만 OCR은 CPU와 네이티브 자원을 많이 쓰므로 동시 실행 수와 요청 시간을 제한해야 한다.

## 단계별로 테스트하기

- 일반 단위 테스트에서는 네이티브 OCR을 끈다.
- 실제 OCR 검사는 <code>-Docr.enabled=true</code>로 켠다.
- Docker를 사용할 수 있으면 <code>-Docr.container.enabled=true</code>로 컨테이너 검사를 켠다.

네이티브와 컨테이너 OCR 검사는 순차로 실행한다. 짧고 결과가 알려진 테스트 이미지를 쓰고, 배치에 민감한 공백 전체보다 정규화한 문자열을 검증한다.

이후 [Ktor OCR 워크숍](../modules/ktor-ocr-api.md)이나 [Spring Boot OCR 워크숍](../modules/spring-boot-ocr-api.md)으로 이어간다.

## 근거 소스

- [OCR 런타임 안내](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/images-ocr/README.ko.md)
- [OCR 테스트 옵션](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/images-ocr/build.gradle.kts)
