# Issue #490 DOCS-1 manual provenance 구현 계획

## 목표

Epic #511의 DOCS-1 child로서 `0.4.0` release source를 단일 기준으로 삼아
README, versioned manual manifest/index, topology inventory, overview diagram의
provenance와 EN/KO 구조를 정합화한다. 이 train은 Type-E maintenance이며
production Kotlin API, Vips backend, AI/ML dependency를 변경하지 않는다.

기준 release는 tag `0.4.0`이 가리키는
`ea5175b083babf8880f53cf80c9a264a0c61777e`다. 현재 release topology는
`settings.gradle.kts`와 tag tree에서 19개 project로 확인했다.

## 범위와 제외

| 범위 | 대상 | 결과 |
|---|---|---|
| inventory | `scripts/manual/export_settings_inventory.rb`, test | 여러 줄 `project(...).projectDir` assignment도 수집하고 regression test로 고정 |
| manifest | `scripts/manual/build_image_manifest.rb`, `docs/manual/manifest.yaml` | `spring-boot-image-intelligence-api`를 포함한 19개 module과 release metadata 생성 |
| module docs | `docs/manual/{en,ko}/modules/spring-boot-image-intelligence-api.md` | stable release source 기반 EN/KO 문서 추가 |
| overview docs | `README.md`, `README.ko.md`, `docs/manual/{en,ko}/index.md` | `0.4.0`, full SHA, 10 published libraries, 1 BOM, 7 examples, 1 benchmark, 19 projects 정합화 |
| diagram | `scripts/manual/render_image_diagrams.rb`, overview SVG/PNG | 0.4.0 topology 문구와 생성기 source를 함께 수정·렌더링 |

제외: `images-*` Kotlin production source, Gradle dependency/catalog, Vips native
tests, AI/ML implementation, release/tag/publication, Dependabot PR.

## 실행 순서

### 1. RED: parser regression 고정

`export_settings_inventory_test.rb`에 multiline assignment fixture와
`spring-boot-image-intelligence-api` row 기대값을 먼저 추가한다. 기존 single-line
분류와 JSON output 보존도 유지한다. 새 테스트가 현재 parser에서 실패하는 것을
확인한 뒤 최소 parser 변경을 적용한다.

### 2. GREEN: canonical inventory와 manifest 생성

1. parser가 `project("...").projectDir =` 다음 줄의 `file("...")`까지 읽도록
   구현하되 unrelated Kotlin syntax를 임의로 해석하지 않는다.
2. `build_image_manifest.rb`에 intelligence example의 EN/KO title을 추가한다.
3. current settings inventory를 release tag `0.4.0`과 대조하여 19 rows를 생성하고,
   manifest generator로 누락 entry를 보충한다.
4. `export_manifest.rb`로 `docs/manual/generated/manifest.json` snapshot을 다시
   생성하고 YAML/JSON module set과 intelligence entry가 같은지 확인한다.
5. generator가 만든 두 intelligence module 문서를 stable README/build/source 링크와
   동일한 manual contract로 채운다. 두 문서의 required heading/anchor, EN/KO parity,
   placeholder 0건, `0.3.0`·`a571c300` drift 0건을 read-back으로 고정한다.

### 3. README·index·diagram provenance 정합화

- README EN/KO의 manual 링크와 release 설명을 0.4.0으로 맞춘다.
- 현재 artifact coordinates가 서로 다른 historical versions를 설명하는 부분은
  release provenance 문구와 혼동하지 않도록 보존 범위를 확인하고, unrelated
  dependency migration은 하지 않는다.
- EN/KO index의 commit label을 `ea5175b0`로 고치고 library/BOM/example/benchmark/
  project counts를 inventory에서 재생성한 값으로 맞춘다.
- `render_image_diagrams.rb`의 overview subtitle/description과 generated
  `repository-learning-map.svg/.png`를 0.4.0 topology에 맞춘다.
- `sync_release_diagrams.rb --check`로 release diagram inventory의 tag peel, full
  SHA, README asset, EN/KO pinned URL을 확인한다. overview source에는 semantic ledger를
  남기고 SVG/XML, connector, arrowhead, geometry, asset pair와 full-size PNG를 검사한다.

### 4. 독립 review와 문서 품질 gate

독립 reviewer lane은 parser multiline 경계, release count 산출, EN/KO parity,
diagram source/render parity, link/hash drift를 별도로 검토한다. P0/P1은 PR 전에
0이어야 하며, finding은 `docs/superpowers/reviews/2026-08-16-issue-490-*.md`에
source/test/command 근거와 disposition을 기록한다.

Superpowers writer gate는 각 artifact에 SPW-01(독자·목적), SPW-02(구조·결정),
SPW-03(한국어 기술 문체와 token 보존), SPW-04(source-to-evidence), SPW-05
(최종 read-back)를 기록한다. overview diagram에는 semantic ledger와 full-size
PNG inspection evidence를 남긴다.

### 5. 검증과 delivery

다음 순서로 검증한다.

```bash
ruby -I scripts/manual scripts/manual/export_settings_inventory_test.rb
ruby -I scripts/manual scripts/manual/release_inventory_test.rb
ruby -I scripts/manual scripts/manual/manual_contract_test.rb
ruby -I scripts/manual scripts/manual/generate_manuals_test.rb
ruby -I scripts/manual scripts/manual/release_diagram_contract_test.rb
ruby scripts/manual/export_settings_inventory.rb
ruby scripts/manual/release_inventory.rb 0.4.0 ea5175b083babf8880f53cf80c9a264a0c61777e build/manual/module-inventory.json build/manual/release-module-inventory.json 19
ruby scripts/manual/build_image_manifest.rb
ruby scripts/manual/generate_manuals.rb docs/manual/manifest.yaml
ruby scripts/manual/export_manifest.rb
ruby scripts/manual/export_manifest.rb --check
ruby scripts/manual/validate_manuals.rb build/manual/release-module-inventory.json docs/manual/manifest.yaml
ruby scripts/manual/sync_release_diagrams.rb --check
ruby scripts/manual/validate_release_manuals.rb 0.4.0 ea5175b083babf8880f53cf80c9a264a0c61777e
ruby scripts/manual/render_image_diagrams.rb
ruby scripts/manual/validate_diagrams.rb
rg -n 'This section will be completed|0\.3\.0|a571c300|Image 0\.3|9 artifacts|5 runnable|15 release' README.md README.ko.md docs/manual scripts/manual
git diff --name-status 9637f0bfa6f651c6dcb1269d1fe309dfe82990bb..HEAD
git diff --check
```

추가로 touched overview PNG를 full size로 열고, SVG XML/semantic/connector/
arrowhead/visual/asset-pair audit 결과를 review artifact에 기록한다. EN/KO index와
module document 구조는 heading/anchor inventory로 비교한다.

## 수용 기준 추적

| Issue #490 기준 | 구현·검증 증거 |
|---|---|
| releaseRef/commit/count를 한 release source에서 동기화 | tag peel, current/tag topology 비교, release inventory 19, manifest/YAML+JSON/index/README/diagram read-back |
| intelligence example 포함 또는 명시적 제외 | manifest entry와 EN/KO module docs 존재 |
| full hash와 표시 label 일치 | `ea5175b083babf8880f53cf80c9a264a0c61777e` link/hash audit |
| manual validation/render와 EN/KO parity | Ruby manual/diagram contracts, structural parity, placeholder/old-label drift search, semantic ledger, full-size PNG review |

## 중단 조건과 rollback

- tag가 expected SHA로 resolve되지 않거나 release inventory가 19가 아니면
  manifest/문서 생성을 중단하고 근거를 review에 기록한다.
- generator output이 기존 authored manual bytes를 덮어쓰면 즉시 중단하고,
  `generate_manuals.rb`의 skip contract를 먼저 고친다.
- diagram PNG가 full-size에서 clipping, wrong count, unreadable text를 보이면
  source generator를 수정하고 SVG/PNG pair를 다시 생성한다.
- 모든 변경은 이 branch의 단일 commit 범위에서 revert 가능하게 유지한다.

## Delivery gate

- base: `develop`
- branch: `docs/issue-490-manual-provenance`
- Epic: #511, child: #490, milestone: `0.5.0`, assignee: `debop`
- Korean PR body, final section `## DoD Status`
- exact-head hosted CI와 live PR metadata/review/mergeability를 확인한 뒤 별도
  merge 승인을 받는다. merge 전에는 local sync·worktree 삭제를 수행하지 않는다.

## SPW writer gate

- SPW-01: PASS — #490/#511, stable tag, 독자와 문서 정합성 목적을 고정했다.
- SPW-02: PASS — parser RED/GREEN, manifest, docs, diagram, validation, delivery 순서를 명시했다.
- SPW-03: PASS — 한국어 기술 문체를 사용하고 API·명령·SHA·경로 token을 보존했다.
- SPW-04: PASS — 각 수용 기준에 source, test, command, generated snapshot, semantic ledger, artifact 증거를 연결했다.
- SPW-05: PASS — 승인 전 plan read-back에서 placeholder와 미정 파일명이 없다.

## 상태

- [x] 계획 승인: 사용자 승인 완료
- [x] 구현 및 검증 — 로컬 manual/inventory/diagram 계약과 exact tag provenance 검증 완료
- [x] 독립 review P0/P1 = 0 — Architectural Status `PASS`, 최종 `CLEAR`
- [ ] PR/hosted CI/merge approval
- [ ] merge 후 local sync와 안전한 cleanup
