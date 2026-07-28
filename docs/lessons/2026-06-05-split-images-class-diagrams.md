# images class 다이어그램을 책임별로 분리

## 배경

`images-class-02`는 core API, filter 구현, writer 타입, helper를 class 다이어그램
하나에 담았다. README 크기로 검토하기에는 다이어그램이 지나치게 복잡했고 connector
경로 결함을 눈으로 확인하기도 어려웠다.

## 결정

`images` 모듈 class 다이어그램을 다음 세 가지 책임 중심 다이어그램으로 나눈다.

- Core API class
- Filter class
- Writer class

각 다이어그램에는 생성된 SVG/PNG 쌍과 고정 위치 Graphviz 근거(`.dot`, `.plain`,
`-graphviz.svg`, `-graphviz.png`)가 있다. generator는 node 겹침, 텍스트 넘침,
source/target 경계 연결, 관련 없는 box와 connector 사이의 여유를 검증한다.

## 결과

README는 이제 이전의 복잡한 단일 이미지 대신 PNG 다이어그램 세 개를 삽입한다. 공통
asset의 label은 영어로 유지하고 주변 README 설명은 locale에 맞게 작성한다.

## 검증

- `python3 docs/scripts/generate-images-class-diagrams.py`
- `python3 -m py_compile docs/scripts/generate-images-class-diagrams.py`
- 새 최종 SVG와 Graphviz SVG에 `xmllint --noout` 실행
- README PNG 링크 존재 여부 확인
- Core, Filters, Writers, Graphviz 근거의 렌더링된 PNG 검사
- `git diff --check`

## 이후 지침

복잡한 class 다이어그램은 경로를 더 복잡하게 만들기 전에 class 책임별로 나눈다.
connector가 endpoint가 아닌 class box를 가로지르거나 지나치게 가까이 지나간다면 긴
우회 경로를 수용하지 말고 배치를 수정하거나 가치가 낮은 관계를 제거한다.
