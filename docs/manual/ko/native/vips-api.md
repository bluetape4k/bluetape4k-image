---
manualId: "vips-api"
title: "libvips 공통 API"
locale: "ko"
releaseRef: "0.3.0"
---

# libvips 공통 API

<code>bluetape4k-images-vips-api</code>는 이미지 처리 계약을 Java 21 JVips와 Java 25 FFM 바인딩에서 분리한다. 애플리케이션 코드는 <code>VipsImage</code>와 <code>VipsRuntime</code>을 기준으로 컴파일하고 런타임 구현 하나를 선택한다.

## 핵심 계약

<code>VipsRuntime</code>은 네이티브 라이브러리를 초기화하고 이미지를 읽는다. <code>VipsImage</code>는 크기, 리사이즈, 썸네일, 자르기, 인코딩과 닫기를 제공한다. 포맷과 작성기 옵션 타입은 바인딩 클래스가 애플리케이션 공개 경계로 새지 않게 한다. Okio 지원으로 인코딩 결과를 싱크에 쓸 수 있다.

모든 이미지는 닫아야 한다. <code>VipsImage</code>를 반환하는 연산은 새 네이티브 객체의 소유권을 호출자에게 넘긴다. [Native 자원 수명 주기](../guides/native-resource-lifecycle.md)를 함께 읽는다.

## Binding과 분리한 애플리케이션 설계

백엔드 선택은 조립 코드에 둔다.

- 비즈니스 코드는 공통 런타임이나 더 작은 애플리케이션 어댑터를 받는다.
- 시작 시 백엔드 하나만 초기화한다.
- 처리 코드는 원본과 연산 결과를 모두 닫는다.
- 요청 처리와 작업 실행기를 멈춘 다음 런타임을 종료한다.

애플리케이션이 특정 백엔드에 기대도록 설계한 것이 아니라면 공개 서비스에서 JVips나 FFM 바인딩 타입을 노출하지 않는다.

## 보안 경계

네이티브 구현을 호출하기 전에 경로 루트, 인코딩된 입력 크기, 출력 옵션과 허용 포맷을 검증한다. 네이티브 코드를 쓴다고 애플리케이션 제한이 사라지지 않는다. 클라이언트 오류 메시지에 파일 경로나 네이티브 로더 정보가 드러나지 않게 한다.

## 근거 소스

- [VipsImage](https://github.com/bluetape4k/bluetape4k-image/blob/a571c30004f571fe8cfcddc29670c1404d212ec6/images-vips-api/src/main/kotlin/io/bluetape4k/images/vips/VipsImage.kt)
- [VipsRuntime](https://github.com/bluetape4k/bluetape4k-image/blob/a571c30004f571fe8cfcddc29670c1404d212ec6/images-vips-api/src/main/kotlin/io/bluetape4k/images/vips/VipsRuntime.kt)
