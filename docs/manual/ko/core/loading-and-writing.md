---
manualId: "loading-and-writing"
title: "로드와 저장"
locale: "ko"
releaseRef: "0.4.0"
---

# 로드와 저장

이미지 I/O에서는 신뢰할 수 없는 바이트가 큰 메모리 구조로 바뀌고, 쓰다 만 출력이 외부에 보일 수 있다. 디코딩과 인코딩 정책을 명시해야 한다.

## 로드

<code>immutableImageOf</code>는 <code>ByteArray</code>, <code>InputStream</code>, Okio <code>BufferedSource</code>/<code>Source</code>, <code>File</code>, <code>Path</code>를 받는다. 임시 표현을 여러 번 거치지 말고 실제 입력 경계에 맞는 오버로드를 고른다.

도우미는 이미지를 디코딩하지만 애플리케이션의 업로드 정책까지 대신하지 않는다. 인코딩된 바이트 수를 제한하고, 가능하면 크기를 미리 확인하며, 지원하지 않는 포맷을 거부하고, 전체 처리 전에 타임아웃을 적용한다. 파일 경로는 애플리케이션이 소유한 루트 아래로 해석해야 한다.

## 저장

JPEG, PNG, WebP를 동기 또는 코루틴 친화적인 경로로 저장할 수 있다. 제품 요구에 맞춰 품질과 압축률을 고르고 출력 크기와 다시 디코딩할 수 있는지 검증한다. 소비자가 부분 파일을 보면 안 된다면 같은 디렉터리의 임시 파일에 쓴 뒤 최종 경로로 옮긴다.

Suspend 작성기를 쓴다고 CPU 인코딩이 저절로 논블로킹이 되는 것은 아니다. CPU와 파일 작업을 어느 디스패처에서 실행할지 애플리케이션이 정하고 동시 인코딩 수를 제한한다.

## 대용량 파일과 Okio

Okio 어댑터는 불필요한 API 변환을 줄이고 파일 경계를 분명하게 만든다. 백엔드가 픽셀을 메모리에 펼친다면 디코딩 자체가 스트리밍으로 바뀌는 것은 아니다. 허용할 최대 크기로 최대 메모리 사용량을 측정한다.

[기본 이미지 처리 워크숍](../modules/basic-processing.md)에서 읽기-변환-쓰기 경로를 완성할 수 있다. 네이티브 I/O와 비교하기 전에는 [Vips API](../native/vips-api.md)를 읽는다.

## 근거 소스

- [로드와 작성기 지원](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/images/src/main/kotlin/io/bluetape4k/images/ImmutableImageSupport.kt)
- [핵심 모듈 안내](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/images/README.ko.md)
