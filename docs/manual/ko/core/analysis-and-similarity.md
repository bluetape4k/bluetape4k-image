---
manualId: "analysis-and-similarity"
title: "분석과 유사도"
locale: "ko"
releaseRef: "0.3.0"
---

# 분석과 유사도

0.3.0은 흐림 정도와 대표 색상 분석, 여러 유사도 계열을 제공한다. 제품에서 묻는 질문에 맞춰 지표를 골라야 한다. 어떤 상황에서나 “같은 이미지”를 뜻하는 점수 하나는 없다.

## 분석

<code>blurScore</code>와 <code>isBlurry</code>는 애플리케이션이 정한 임계값으로 품질을 판정한다. 대표 색상 도우미는 색상 분포를 요약한다. 여러 업로드 결과를 비교해야 한다면 크기와 색상 처리 경로를 정규화한 뒤 분석한다.

## 유사도 계열

- 픽셀 차이와 MSE는 직접 차이를 재며 두 입력이 거의 정렬되어 있어야 한다.
- PSNR은 재구성 오차를 요약한다.
- SSIM과 MSSIM은 구조를 비교해 일부 픽셀 차이를 허용한다.
- aHash, dHash, wHash, pHash는 후보 검색에 쓸 수 있는 작은 지각 해시를 만든다.
- 히스토그램 유사도는 위치를 무시하고 색상 분포를 비교한다.
- 블록 평균 기술자와 회전을 고려한 비교는 픽셀 위치가 정확히 맞지 않을 때 쓸 수 있다.

이미지가 많다면 두 단계로 구성한다. 작은 해시로 후보를 찾고 더 비싼 구조 지표로 순위를 정하거나 최종 확인한다. 결과에는 지표 이름, 정규화 규칙, 기술자 크기와 임계값을 함께 저장한다.

## Threshold는 제품 데이터다

다른 테스트 이미지에서 쓴 임계값을 그대로 가져오지 않는다. 제품 영역의 양성/음성 표본을 만들고 오탐과 누락을 평가해 정책을 버전으로 관리한다. 비교 전에는 두 입력을 같은 규칙으로 리사이즈한다.

## 근거 소스

- [분석 패키지](https://github.com/bluetape4k/bluetape4k-image/tree/a571c30004f571fe8cfcddc29670c1404d212ec6/images/src/main/kotlin/io/bluetape4k/images/analysis)
- [유사도 패키지](https://github.com/bluetape4k/bluetape4k-image/tree/a571c30004f571fe8cfcddc29670c1404d212ec6/images/src/main/kotlin/io/bluetape4k/images/similarity)
