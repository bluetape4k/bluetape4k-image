# README 다이어그램 인포그래픽

## 배경

README 파일은 아키텍처, 클래스, 시퀀스, ERD 등 여러 다이어그램에 Mermaid
코드 블록을 사용했다. 워크스페이스의 시각 자료 원칙이 검토된 파스텔톤
인포그래픽 PNG를 사용하고 재사용을 위해 SVG 원본 자산을 보관하는 방식으로
바뀌었다.

## 결정

README의 Mermaid 블록을 생성한 PNG 이미지 링크로 교체하고, 대응하는 SVG
원본을 PNG 파일 옆에 저장한다. 다이어그램 텍스트는 영어로만 작성하고, 큰
레이블에는 Architects Daughter, 세부 텍스트에는 Comic Mono를 사용한다.
아키텍처, 클래스, 시퀀스, ERD마다 용도에 맞는 레이아웃을 적용한다.

## 결과

`bluetape4k.github.io/docs/readme-diagram-samples`의 2026-05-19 공통 스타일
가이드에 따라 README 다이어그램을 렌더링했다. 저장소별 자산 배치 규칙이 있으면
루트 README 자산에 해당 규칙을 적용했다.

## 검증

저장소 간 변환 작업에서 `rsvg-convert`로 PNG/SVG 자산을 생성하고 README
링크를 검사했다.

## 향후 지침

README 다이어그램은 PNG로 삽입하고 편집용 SVG 원본을 함께 보관한다.
시각적 일관성이 중요할 때 원시 Mermaid나 단순한 Mermaid 테마 색상 변경으로
되돌리지 않는다.
