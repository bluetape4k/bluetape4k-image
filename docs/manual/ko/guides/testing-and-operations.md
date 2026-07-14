---
manualId: "testing-and-operations"
title: "테스트와 운영"
locale: "ko"
releaseRef: "0.3.0"
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

## 릴리스 기준 지키기

이 매뉴얼은 0.3.0을 대상으로 한다. 테스트와 소스 링크도 해당 릴리스 커밋에 고정한다. Develop 빌드가 성공했다고 동결한 매뉴얼 예제가 맞는 것은 아니다.

## 근거 소스

- [릴리스 테스트 설정](https://github.com/bluetape4k/bluetape4k-image/blob/a571c30004f571fe8cfcddc29670c1404d212ec6/build.gradle.kts)
- [Spring Boot health와 metric 소스](https://github.com/bluetape4k/bluetape4k-image/tree/a571c30004f571fe8cfcddc29670c1404d212ec6/images-spring-boot/src/main/kotlin/io/bluetape4k/images/spring)
