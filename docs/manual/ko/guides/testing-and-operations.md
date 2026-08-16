---
manualId: "testing-and-operations"
title: "테스트와 운영"
locale: "ko"
releaseRef: "0.4.0"
---

# 테스트와 운영

실제 배포에서 사용하는 경계를 테스트해야 한다. JVM 단위 테스트만으로는 실행 환경의 Tesseract, 시스템 libvips, S3 자격 증명이나 파일 권한을 검증할 수 없다.

## 테스트 단계

1. 작은 고정 테스트 이미지로 변환, 검증, CAPTCHA 상태와 저장 정책을 단위 테스트한다.
2. 시각 연산은 기준 이미지 또는 수치 유사도로 검증한다. 인코더 메타데이터가 매번 다를 수 있다면 전체 바이트 비교는 피한다.
3. 선택한 프레임워크 모듈 테스트를 실행한다.
4. 필요한 패키지가 설치된 실행기에서 실제 OCR과 libvips 검사를 순차 실행한다.
5. 배포 환경에서 저장소와 CDN 설정을 최소 수준으로 검증한다.

대표 명령:

    ./gradlew :bluetape4k-images:test
    ./gradlew :bluetape4k-images-ocr:test -Docr.enabled=true
    ./gradlew :bluetape4k-images-vips-java21:test

## 운영 지표

입력 바이트, 디코딩한 이미지 크기, 처리 시간, 출력 바이트, 실패 유형, 대기열 길이와 저장소 지연 시간을 측정한다. Spring Boot 연동에서 상태 점검과 메트릭을 제공하지만 애플리케이션에 필요한 태그와 경보 기준은 서비스가 정한다.

## 용량과 격리

디코딩 전에 요청 크기를 제한한다. 코루틴을 무한히 늘리지 말고 OCR과 네이티브 작업의 동시 실행 수를 제한한다. 실행 환경의 요구 사항이 다른 벤치마크와 네이티브 테스트는 빠른 CI와 분리할 수 있지만, 주기적으로 실제 경계를 검증하는 경로는 남겨야 한다.

## 다이어그램 provenance

매뉴얼 다이어그램은 `scripts/manual/render_image_diagrams.rb`의 SVG source와 controlled `rsvg-convert` delivery profile을 사용한다. [`diagram-provenance.yaml`](../../diagram-provenance.yaml)은 다섯 SVG/PNG pair 각각의 renderer version, 요청·해결된 font inventory, 실행 환경, source와 PNG의 SHA-256, dimensions, color/alpha metadata와 content fingerprint를 기록한다. 각 asset에는 tracked PNG baseline과 기록된 toolchain으로 렌더링한 receipt를 모두 저장하며, tracked 파일을 사후에 읽어 receipt로 간주하지 않는다.

매뉴얼을 변경해 배포하기 전에 provenance 검사를 실행한다.

    ruby -I scripts/manual scripts/manual/diagram_provenance_test.rb
    ruby scripts/manual/verify_diagram_provenance.rb
    ruby scripts/manual/render_image_diagrams.rb --output-root build/manual/diagram-render
    ruby scripts/manual/validate_diagrams.rb

Verifier는 격리된 directory에서 source를 두 번 렌더링하고 controlled run의 PNG SHA-256이 서로 같은지 확인한 뒤 기록된 render receipt와 비교한다. 또한 tracked baseline의 content fingerprint를 검증하므로 크기만 같은 다른 PNG로 바꾸면 실패한다. Tracked baseline은 다른 renderer, font inventory, 운영체제나 architecture에서 PNG bytes가 이식된다고 가정하지 않으므로 semantic-fingerprint mode를 사용한다. 따라서 이 mode에서는 tracked 파일과 receipt의 byte 또는 semantic 차이를 의도된 비차단 note로 표시하며, renderer와 font drift는 기록된 delivery profile을 진단에 표시하고 실패한다. `--write-manifest`도 manifest를 기록하기 전에 격리 렌더링을 수행하고 tracked baseline과 receipt를 별도로 저장한다.

## 릴리스 기준 지키기

이 매뉴얼은 0.4.0을 대상으로 한다. 테스트와 소스 링크도 해당 릴리스 커밋에 고정한다. Develop 빌드가 성공했다고 동결한 매뉴얼 예제가 맞는 것은 아니다.

## 근거 소스

- [릴리스 테스트 설정](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/build.gradle.kts)
- [Spring Boot health와 metric 소스](https://github.com/bluetape4k/bluetape4k-image/tree/ea5175b083babf8880f53cf80c9a264a0c61777e/images-spring-boot/src/main/kotlin/io/bluetape4k/images/spring)
