# 루트 README 개요 보강

## 배경

루트 README 개요 다이어그램은 모듈을 서로 독립된 카드로 나열했다. 순수 JVM 처리,
서비스 통합, native libvips 가속, example, benchmark 근거를 함께 제공하는 저장소를
설명하기에는 지나치게 단순했다.

## 결정

루트 README 개요를 선택 흐름 다이어그램으로 다시 생성한다.

- 진입점과 버전 정렬
- 순수 JVM 처리 경로
- CAPTCHA와 서비스 통합 경로
- native libvips API와 backend 선택
- example과 benchmark 근거

기존 README 이미지 경로는 유지하고 generator와 고정 위치 Graphviz 근거를 추가한다.

## 결과

루트 README는 세 가지 도입 경로를 글로 설명하고 소스로 확인한 모듈 관계를 더 풍부한
개요 다이어그램으로 보여 준다.

## 검증

- `python3 docs/scripts/generate-root-readme-overview.py`
- `python3 -m py_compile docs/scripts/generate-root-readme-overview.py`
- 최종 SVG와 Graphviz SVG에 `xmllint --noout` 실행
- README PNG 링크와 SVG embed 여부 확인
- 렌더링한 PNG 검사
- `git diff --check`

## 이후 지침

루트 README 개요 다이어그램은 모듈을 나열하기 전에 "사용자는 어떤 경로를 선택해야
하는가?"에 답해야 한다. 인벤토리에는 모듈 구성 차트를, 도입 흐름에는 개요
다이어그램을 사용한다.
같은 계층의 native backend 경로에서는 connector를 카드 내부 밖에 두면서 계층을 하나의
공통 기능 band로 읽을 수 있다면 아래쪽끼리 또는 위쪽끼리 연결하는 경로를 우선한다.
