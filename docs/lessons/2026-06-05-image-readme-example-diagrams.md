# Image README example 다이어그램

## 배경

example README 파일은 실행 가능한 image 워크플로를 설명했지만 scenario, architecture,
sequence를 시각적으로 보여 주지 않았다. 기존 모듈 README 다이어그램도 소스를 근거로
정리해야 했다. `images-spring-boot`는 소문자 제목과 계층이 없는 architecture
layout을 사용했고, `images-ktor`에는 architecture 다이어그램이 없었으며,
`images-captcha`에는 눈으로 확인할 example 출력이 없었다.

## 결정

`docs/images/readme-diagrams` 아래에 영어 label의 SVG/PNG 쌍을 생성하고 README에는
PNG만 삽입한다. connector가 많은 최종 asset 옆에는 Graphviz `.dot`, `.plain`,
sketch SVG, sketch PNG 근거를 함께 둔다.

## 결과

- example 세 개 모두에 scenario, architecture, sequence 다이어그램을 추가했다.
- `Images Spring Boot Architecture`를 javers 스타일의 수평 계층 band, 더 넓은 카드,
  Graphviz 근거를 반영한 비격자 배치로 다시 만들었다.
- 같은 javers 스타일 계층 표현으로 `Images Ktor Architecture`를 추가했다.
- `images-captcha`에 정적 CAPTCHA challenge 미리 보기를 추가했다.
- 카드가 선 위의 텍스트를 가리지 않도록 README용 다이어그램에서 여유가 부족한 흐름
  edge label을 제거했다.
- javers 스타일 architecture asset에 route와 카드 겹침 검증을 추가했다.
- 한 줄의 상세 설명을 글자별 세로 텍스트로 렌더링하게 했던 잘못된 문자열
  `Node.details` 사용을 막는 generator 검사를 추가했다.
- 촘촘한 격자 배치 대신 더 넓은 Graphviz 스타일 layout으로 example scenario와
  architecture 다이어그램을 다시 만들었다.
- route, 밀집도, 텍스트 적합성 검증을 javers 스타일 모듈 architecture 다이어그램뿐
  아니라 모든 흐름 다이어그램으로 확대했다.

## 검증

- Generator gate 출력에서 node, edge, message 수와 `manual_exceptions=0`,
  sequence `label_intersections=0`을 확인했다.
- `rsvg-convert`로 최종 PNG를 렌더링했다.
- 변경한 최종 PNG asset 전체의 contact sheet를 검사했다.
- canvas를 넓히고 본문 시작 위치를 낮추며 균일한 표 형태 배치를 해체한 뒤 Spring
  Boot와 Ktor Architecture PNG를 다시 검사했다.
- participant를 좌우 대칭 여백으로 중앙에 배치하고 message label을 participant
  header 아래로 옮긴 뒤 example sequence PNG를 다시 검사했다.
- canvas를 넓히고 계층 label 공간을 확보하며 connector 경로를 단순화한 뒤 example
  scenario·architecture PNG 여섯 개를 다시 검사했다.
- layout 지침을 얻기 위해 로컬 Claude advisor 경로를 시도했지만 CLI가 조직 비활성화
  API 오류로 실패했다. 대신 Graphviz 근거와 렌더링한 PNG 검사를 사용했다.
- 변경한 SVG 파일을 `xmllint --noout`으로 파싱했다.
- 로컬 README 이미지 링크가 유효하며 README에 SVG가 아닌 PNG를 삽입했음을 확인했다.
- 다이어그램 SVG가 UI font family 없이 `Architects Daughter`와 `Comic Mono`를
  사용하는지 확인했다.
- `git diff --check` passed.

## 이후 지침

README 모듈 다이어그램으로 Spring, Ktor, storage 계층을 설명할 때는 왼쪽 계층 label,
넓은 카드, Graphviz 근거 footer를 갖춘 javers 스타일 수평 계층 band를 우선한다.
Graphviz 근거가 엇갈린 흐름을 제시한다면 카드를 동일한 크기의 셀로 이루어진 균일한 표에
배치하지 않는다. 텍스트가 카드 가장자리에 가까우면 label을 줄이기 전에 canvas와 카드를
넓힌다. label과 모든 카드 사이의 여유를 입증하지 못했다면 README 흐름 다이어그램에서
edge label을 숨긴다. 제목·부제 간격과 아래쪽 여백의 시각적 균형을 유지하고 sequence
lifeline은 좌우 대칭 여백으로 중앙에 배치한다.

example 다이어그램에서는 첫 canvas 크기에 들어간다는 이유만으로 촘촘한 격자를 유지하지
않는다. Graphviz `.plain` 방향을 최종 layout 기준으로 사용한다. architecture에는 넓은
좌우 pipeline을, scenario에는 중앙 fan-out을 사용하고, example에
application/runtime, route/helper, library, storage 책임이 있다면 column이나 band
계층을 사용한다. 카드를 배치하기 전에 계층 label을 위한 공간을 눈에 보이게 확보한다.
route에 비좁은 bus가 필요하거나 label이 카드와 충돌한다면 먼저 canvas를 넓히고 카드를
옮긴다.
