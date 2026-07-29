# README 다이어그램 이미지 검증

## 배경

bluetape4k-image의 README 다이어그램을 공통 파스텔 인포그래픽 렌더러로
개선했다. 현재 Mermaid 블록과 Git 이력에서 복구한 기존 README 다이어그램
이미지 링크를 모두 작업 범위에 포함했다.

## 결정

README에는 PNG 아티팩트를 사용하고 재사용할 SVG 원본은 PNG 파일 옆에
보관한다. 다이어그램 레이블은 영어로만 작성한다. `Diagram`, `Architecture`,
`Sequence Diagram` 같은 일반 제목은 모듈에 맞는 영어 제목으로 교체한다.
영어가 아닌 텍스트를 제거해 의미가 사라진 시퀀스 레이블은 무의미한 일반
레이블 대신 참여 컴포넌트 이름으로 대체한다.

## 결과

- 렌더링한 아티팩트 33개
- PNG 파일 17개
- SVG 원본 파일 17개
- 누락된 README 이미지 링크 없음
- README 파일에 로컬 SVG 이미지 삽입 없음
- 남은 Mermaid 코드 블록 없음
- 형태 검사 후보 없음

## 검증

- `node /Users/debop/work/bluetape4k/.omx/scripts/refine-readme-diagrams.mjs .`
- README 이미지 링크 및 Mermaid 잔여물 검사기
- PNG/SVG 형태 검사기
- 시각적 콘택트 시트 검토: `/tmp/bluetape4k-image-diagram-review-samples.png`
- `git diff --check`

## 향후 지침

원본 Mermaid 소스를 사용할 수 있으면 이전에 교체한 블록의 Git 이력까지
포함해 다시 생성한다. 이미지 크기는 콘텐츠에 맞추고, 가짜 채움 노드는 넣지
않으며, SVG 원본을 보존한다. 게시하기 전에 표본 시트를 검사한다.

## 2026-05-20 클래스 레이아웃 후속 조치

생성한 클래스 맵에는 다이어그램 중앙을 가로지르는 긴 곡선이 많았기 때문에
`images-class-02`를 수동으로 자유 배치해야 했다. 수정한 이미지는 로컬 관계
경로를 사용한다. 이미지 연산은 `ImmutableImage`에 의존하고, 필터 클래스는
로컬 `ImageFilterChain` 버스에 연결하며, 코루틴 Writer는 구현 경로 하나를
공유한다.

이후 클래스 다이어그램은 클래스 카드 내부를 통과하는 경로를 거부해야 한다.
간선이 뒤엉킨 결과를 수용하기 전에 직교 로컬 버스를 사용하거나 가치가 낮은
의존성을 생략한다.
