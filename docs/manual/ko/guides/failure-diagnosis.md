---
manualId: "failure-diagnosis"
title: "실패 진단"
locale: "ko"
releaseRef: "0.4.0"
---

# 실패 진단

이미지 코드를 고치기 전에 어느 경계에서 실패했는지 먼저 나눈다.

## Decode와 포맷 실패

입력 바이트, 콘텐츠 타입, 이미지 크기와 선택한 백엔드를 확인한다. 파일 확장자가 실제 인코딩 포맷을 증명하지는 않는다. SVG, TIFF, WebP, AVIF, HEIC는 필요한 JVM 또는 네이티브 코덱이 있는지 확인한다. 가장 작은 테스트 이미지와 정확한 릴리스 백엔드로 재현한다.

## Native 시작 실패

<code>UnsatisfiedLinkError</code>나 라이브러리를 찾을 수 없다는 메시지는 대개 libvips가 없거나 프로세스에서 보이지 않는다는 뜻이다. 패키지 설치와 동적 라이브러리 검색 경로를 확인한다. Java 25 FFM은 <code>--enable-native-access=ALL-UNNAMED</code>도 필요하다. 대체 동작을 설계하지 않았다면 시작 실패를 다른 백엔드로 몰래 바꾸지 않는다.

## OCR 실패

“Error opening data file”은 traineddata가 없거나 데이터 경로가 틀렸을 때 주로 발생한다. <code>tesseract --list-langs</code>, 요청 언어와 프로세스 권한을 확인한다. 인식 결과가 빈 문자열인 경우와 엔진 초기화 실패를 구분한다.

## Storage와 route 실패

로컬 저장소는 루트 경로, 경로 이탈 방지, 디렉터리 권한과 부분 파일을 확인한다. S3나 CloudFront는 리전, 자격 증명, 버킷/키 정책, 서명 키와 시계를 확인한다. Ktor에서는 JSON, 오류 처리, 인증과 요청 제한을 애플리케이션이 설치했는지 본다.

## 자원과 종료 실패

네이티브 메모리가 계속 늘면 연산 결과인 <code>VipsImage</code>를 닫지 않았거나 동시 실행이 무제한인 경우가 많다. 종료 뒤 실패한다면 작업 실행이 끝나기 전에 런타임을 종료했는지 확인한다.

실패 입력의 안전한 식별자, 백엔드, 코덱, 크기, 처리 시간, 예외 유형, 실행 환경의 패키지 버전과 릴리스 커밋을 남긴다. 이미지 내용이나 자격 증명은 로그에 넣지 않는다.

## 근거 소스

- [릴리스 문제 해결 안내](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/README.ko.md#libvips-시작-문제-해결)
- [Storage 예외 계층](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/images-spring-boot/src/main/kotlin/io/bluetape4k/images/spring/ImageStorageException.kt)
