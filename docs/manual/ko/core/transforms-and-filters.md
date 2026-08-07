---
manualId: "transforms-and-filters"
title: "변환과 필터"
locale: "ko"
releaseRef: "0.4.0"
---

# 변환과 필터

핵심 모듈은 <code>ImmutableImage</code>를 직접 변환하는 함수와 <code>ImageFilterChain</code> DSL을 제공한다. 이름이 분명한 연산 하나에는 직접 함수를 쓰고, 여러 단계를 하나의 처리 정책으로 묶을 때 DSL을 쓴다.

## 기하 변환

0.4.0에는 리사이즈, 분할, 여백 추가, 회전과 뒤집기, 원근 변환, 자동 자르기, 중요 영역 자르기, CLAHE와 전역 히스토그램 평활화가 있다. 꾸미기 필터보다 목표 크기와 자르기 정책을 먼저 정한다. 기하 변환은 이후 분석이 볼 픽셀 자체를 바꾼다.

## Filter chain

<code>applyFilters</code>는 <code>ImageFilterChain</code>을 실행한다. 흐림과 선명화, 밝기/대비/감마와 색상 연산, 스타일 효과, 픽셀화, 테두리, 둥근 모서리, 워터마크, 설명 문구와 기하 변환 확장 함수를 조합할 수 있다.

반복해서 쓰는 정책은 이름 있는 함수로 만든다.

    fun prepareThumbnail(source: ImmutableImage): ImmutableImage =
        source.applyFilters {
            smartCrop(640, 360)
            sharpen()
        }

옵션은 애플리케이션 경계에서 검증한다. 요청값을 반지름, 목표 크기, 좌표, 불투명도, 문구에 그대로 넣으면 과도한 연산이나 잘못된 기하 구조가 생길 수 있다.

## 적용 순서

방향과 자르기 영역을 먼저 정규화하고, 최종 크기에 가깝게 리사이즈한 뒤 픽셀 크기에 따라 동작하는 필터를 적용한다. 워터마크와 설명 문구는 이미지를 잘라내는 연산이 끝난 다음 넣는다. 필터 체인을 모두 수행한 뒤 한 번 인코딩한다.

기준 이미지로 의도를 확인하고 크기와 불변 조건은 수치로 검증한다. 의도적으로 시각 결과를 바꿨다면 새 테스트 이미지를 승인하기 전에 실제 이미지를 눈으로 확인한다.

## 근거 소스

- [Filter DSL](https://github.com/bluetape4k/bluetape4k-image/tree/ea5175b083babf8880f53cf80c9a264a0c61777e/images/src/main/kotlin/io/bluetape4k/images/filters/dsl)
- [Transform 구현](https://github.com/bluetape4k/bluetape4k-image/tree/ea5175b083babf8880f53cf80c9a264a0c61777e/images/src/main/kotlin/io/bluetape4k/images/transforms)
