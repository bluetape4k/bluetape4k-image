---
manualId: "performance-selection"
title: "성능 선택"
locale: "ko"
releaseRef: "0.4.0"
---

# 성능 선택

저장소 벤치마크는 Scrimage와 libvips 연산, I/O 경계, 스트리밍, 동시 실행, 메모리를 비교한다. 어느 환경에서나 통하는 순위표가 아니라 가설을 세우는 자료로 사용한다.

## 작업에 맞춰 측정하기

디코딩, 변환, 인코딩, 파일 읽기와 쓰기, 네트워크 전송, 대기열에서 기다리는 시간을 나눠 본다. 리사이즈 커널이 빨라도 객체 저장소나 인코딩이 대부분을 차지하는 요청은 빨라지지 않을 수 있다. 실제 이미지 크기, 코덱, 품질, 연산 순서, 동시 실행 수를 사용한다.

## Benchmark 행 읽기

지표의 방향을 먼저 확인한다. 평균 지연 시간은 낮을수록 좋고 처리량은 높을수록 좋다. JDK, 백엔드, 테스트 이미지, 워밍업, 반복 횟수와 GC/네이티브 메모리 포함 여부를 함께 본다. 0.4.0 보고서에는 리사이즈, 인코딩, 필터, 처리 과정의 할당량, I/O 경계, 파일 처리량, 대용량 스트리밍과 메모리 자료가 있다.

## 운영 예산 정하기

다음을 수치로 기록한다.

- 최대 입력 바이트와 디코딩할 픽셀 수
- 지연 시간 백분위수와 처리량 목표
- JVM 힙과 네이티브 메모리 제한
- OCR 또는 네이티브 연산의 최대 동시 실행 수
- 출력 품질과 크기 조건

이후 저장소와 프레임워크 비용까지 포함한 서비스 수준 테스트를 실행한다. Scrimage가 목표를 만족하면 배포가 단순하다는 장점이 네이티브 커널의 시간 차이보다 클 수 있다. 만족하지 못할 때 실제 실행 환경에서 libvips 백엔드 하나를 검증한다.

현재 JDK 25 FFM 결과를 과거 JDK 21 JNI 행에 그대로 적용하거나 자연 사진 결과를 문서와 alpha가 많은 그래픽에 확대 해석하면 안 된다. 현재 배포 JNI 백엔드도 JDK 25를 요구한다. [벤치마크 결과 해석](../benchmarks/interpreting-results.md)에서 보고서 읽는 법을 이어서 설명한다.

## 근거 소스

- [릴리스 벤치마크 안내](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/benchmark/images-benchmark/README.ko.md)
- [릴리스 벤치마크 보고서](https://github.com/bluetape4k/bluetape4k-image/tree/ea5175b083babf8880f53cf80c9a264a0c61777e/benchmark/images-benchmark/docs)
