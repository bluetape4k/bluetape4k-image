# #490 DOCS-1 매뉴얼 provenance 정합화 lesson

## 결정

- `0.4.0` tag가 가리키는 `ea5175b083babf8880f53cf80c9a264a0c61777e`를
  versioned manual의 단일 release source로 고정했다.
- 현재 `settings.gradle.kts`와 tag의 topology를 비교해 19개 project를 확인하고,
  published library 10개, BOM 1개, example 7개, benchmark 1개를 모든 provenance
  surface에 재사용했다.
- 여러 줄 `project(...).projectDir =` 구문은 기존 single-line parser의 의미를
  바꾸지 않는 최소 정규식 확장과 regression test로 처리했다.
- `spring-boot-image-intelligence-api`는 generator가 기존 문서를 덮어쓰지 않는
  skip 계약을 보존하면서 EN/KO manual을 stable release source와 full SHA 링크로
  작성했다.
- consumer 설치 안내는 개별 Image artifact version을 나열하지 않고
  `bluetape4k-dependencies` BOM을 먼저 사용하는 형태로 정리했다.
- overview diagram은 0.4 provenance와 7개 workshop 경로를 표시하고, 공통 직선
  corridor를 피하는 Q-bend connector로 source와 SVG/PNG pair를 함께 갱신했다.

## 검증에서 얻은 교훈

- YAML manifest만 갱신하면 generated JSON snapshot이 stale 상태로 남으므로
  `export_manifest.rb`와 `--check`를 release manual 검증 순서에 포함해야 한다.
- `validate_diagrams.rb`만으로는 tag provenance와 pinned source link를 증명할 수
  없다. `sync_release_diagrams.rb --check`, semantic ledger, XML/connector/
  arrowhead/geometry/visual audit를 분리해 실행해야 한다.
- renderer 전체 실행은 unrelated diagram pair도 재생성할 수 있다. 변경 의도가
  overview 하나라면 전체 결과를 read-back하고 unrelated pair는 복원한 뒤,
  touched pair에 대해 별도 CairoSVG와 audit를 남긴다.
- EN/KO 문서는 heading 존재만으로 충분하지 않다. anchor parity, placeholder 0건,
  old release label과 짧은 SHA drift 0건, stable source URL의 full SHA를 함께
  확인해야 한다.

## 범위와 남은 위험

- 이번 train은 Type-E documentation maintenance로 제한하며 production Kotlin,
  Vips native backend, dependency catalog, publication, tag 생성은 변경하지 않는다.
- 생성 산출물은 source와 함께 commit해야 하지만, generated file을 수동 편집하지
  않고 generator 재실행으로 갱신한다.
- hosted PR CI와 merge 후 canonical `develop` 동기화는 delivery gate에서 별도로
  검증한다. 로컬 Ruby/diagram 검증이 hosted CI 성공을 대신하지 않는다.

## 재사용할 운영 규칙

다음 release manual을 만들 때도 `tag peel → topology inventory → manifest/YAML+
JSON → EN/KO docs → diagram render/audit → exact-head CI` 순서를 유지하고, 각
단계의 결과를 review artifact와 `## DoD Status`에 연결한다.
