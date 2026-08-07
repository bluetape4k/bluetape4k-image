---
manualId: "immutable-image-model"
title: "불변 이미지 모델"
locale: "ko"
releaseRef: "0.4.0"
---

# 불변 이미지 모델

<code>bluetape4k-images</code>는 Scrimage의 <code>ImmutableImage</code>를 로드, 변환, 분석과 저장 단계 사이에서 전달하는 JVM 이미지 값으로 사용한다. 연산은 원본을 바꾸지 않고 새 이미지를 반환한다.

## 왜 불변 모델을 쓰는가

불변 값은 처리 흐름을 이해하고 재사용하기 쉽다. 다만 연산 결과마다 전체 픽셀 버퍼를 가질 수 있으므로 메모리가 공짜인 것은 아니다. 중간 이미지를 오래 보관하지 말고 처리 범위를 짧게 유지한다. 처리 단계가 길다면 실제 이미지 크기로 메모리를 측정해야 한다.

바이트 배열, 스트림, Okio 소스, 파일, 경로는 <code>immutableImageOf</code> 오버로드로 읽는다. 디코딩 진입점을 한곳에 모으고 호출자가 같은 이미지 타입을 사용하게 해 준다. I/O 소유권은 [로드와 저장](loading-and-writing.md)에서 다룬다.

## Drawing과 mutable 연동

<code>ImmutableImage</code>에 그릴 때는 <code>withGraphics</code>를 쓴다. Java2D 그래픽 컨텍스트를 정리하고 불변 결과를 반환한다. <code>BufferedImage</code>가 필요한 API도 연결할 수 있지만 변경 가능한 객체는 어댑터 안의 짧은 범위에 둔다.

## 처리 pipeline 구성

다음 순서로 구성하면 경계가 분명해진다.

1. 인코딩된 입력 크기와 이미지 크기를 검증한다.
2. 한 번 디코딩한다.
3. 방향, 색상과 목표 크기를 정규화한다.
4. 변환이나 필터를 적용한다.
5. 비교 기준이 같아야 하는 분석은 정규화한 이미지로 수행한다.
6. 출력 경계에서 한 번 인코딩한다.

CAPTCHA와 OCR도 이 모델을 사용한다. 반면 libvips는 닫아야 하는 별도 <code>VipsImage</code> 계약을 쓴다. JVM 값 모델과 네이티브 자원 소유권을 같은 것으로 보면 안 된다.

## 근거 소스

- [ImmutableImage factory](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/images/src/main/kotlin/io/bluetape4k/images/ImmutableImageSupport.kt)
- [모듈 문서](../modules/bluetape4k-images.md)
