# #490 DOCS-1 구현 독립 코드·문서 검토

## 검토 메타데이터

- lane: `reviewer` (`epic511_513_triage`)
- run: `20260816T111756Z-d2cb2920`
- 대상: `docs/issue-490-manual-provenance` worktree
- base: `9637f0bfa6f651c6dcb1269d1fe309dfe82990bb`
- exact working-tree HEAD: `9637f0bfa6f651c6dcb1269d1fe309dfe82990bb` (commit 전 diff)
- 범위: manual provenance, release inventory, EN/KO parity, generated snapshot,
  overview diagram, parser regression

## 최종 판정

- P0: 0
- P1: 0
- P2: 2 — LF 전용 parser, renderer 환경에 따른 PNG pixel 재현성
- P3: 1 — 향후 release SHA/count drift 자동화 개선
- Architectural Status: `PASS`
- 최종: **`CLEAR`**

P2/P3는 현재 DOCS-1 구현 blocker가 아니다. PR 생성 후 exact-head hosted CI,
GitHub review/mergeability, merge 승인, post-merge sync와 cleanup은 별도 delivery
gate로 남긴다.

## 수용 기준별 evidence

| 영역 | 판정 | 근거 |
|---|---|---|
| multiline parser | PASS | `PROJECT_DIR_PATTERN`이 줄바꿈 `projectDir = file(...)`을 처리하고 실제 intelligence fixture가 inventory에 포함된다. |
| inventory/topology | PASS | release `0.4.0` 기준 19 rows; exact-tag settings와 current settings의 current-only/tag-only가 모두 `[]`; kinds는 `example=7, library=11, benchmark=1`이다. |
| manifest/generated snapshot | PASS | YAML/JSON에 full release SHA와 intelligence row가 있으며 `export_manifest.rb --check`, `validate_manuals.rb`가 PASS했다. |
| EN/KO parity | PASS | 전체 module/index/repository-map 구조와 신규 intelligence page의 anchor parity가 일치한다. 신규 page는 placeholder 0건과 stable source full SHA link를 갖는다. |
| release SHA/link | PASS | `ea5175b083babf8880f53cf80c9a264a0c61777e`에 pin되고 화면 label `ea5175b0`은 full SHA prefix와 일치한다. release validation은 `316 checked, 0 missing`이다. |
| diagram source/SVG/PNG | PASS | `sync_release_diagrams.rb --check`는 `failures=0 entries=37`; `validate_diagrams.rb`는 5 SVG/PNG pair, labels/connectors/14x14 arrowheads를 통과했다. 3200x2080 full-size PNG inspection에서 clipping/겹침이 없다. |
| semantic ledger | PASS | 아래 ledger가 release SHA, 6 nodes, 7 edges, 7 workshop topology, `repairs=[]`를 기록하고 semantic audit가 `diagnostics=[]`로 통과했다. |
| scope leakage | PASS | README/manual/scripts/overview/review evidence만 변경되었고 production Kotlin, dependency, workflow/CI, tag/publication은 변경하지 않았다. |

## 검증 명령 및 결과

```text
./gradlew :spring-boot-image-intelligence-api:test
  BUILD SUCCESSFUL; 56 passing

ruby -I scripts/manual scripts/manual/export_settings_inventory_test.rb
  5 runs, 45 assertions, 0 failures
ruby -I scripts/manual scripts/manual/release_inventory_test.rb
  5 runs, 11 assertions, 0 failures
ruby -I scripts/manual scripts/manual/manual_contract_test.rb
  9 runs, 44 assertions, 0 failures
ruby -I scripts/manual scripts/manual/generate_manuals_test.rb
  7 runs, 255 assertions, 0 failures
ruby -I scripts/manual scripts/manual/release_diagram_contract_test.rb
  5 runs, 19 assertions, 0 failures
ruby -I scripts/manual scripts/manual/export_manifest_test.rb
  1 run, 5 assertions, 0 failures

ruby scripts/manual/export_manifest.rb --check
  Manual manifest snapshot is current.
ruby scripts/manual/validate_manuals.rb build/manual/release-module-inventory.json docs/manual/manifest.yaml
  Manuals are aligned.
ruby scripts/manual/sync_release_diagrams.rb --check
  failures=0 entries=37 release=0.4.0
ruby scripts/manual/validate_release_manuals.rb 0.4.0 ea5175b083babf8880f53cf80c9a264a0c61777e
  316 checked, 0 missing
ruby scripts/manual/validate_diagrams.rb
  5 SVG/PNG pairs; accessible labels, directed connectors, and arrowheads pass
git diff --check
  pass
```

### Semantic ledger read-back

```json
{
  "kind": "architecture",
  "source": {
    "question": "How does the 0.4.0 Image repository learning path connect its dependency, immutable foundation, backend choices, integrations, and seven workshops?",
    "revision": "0.4.0@ea5175b083babf8880f53cf80c9a264a0c61777e",
    "paths": [
      "scripts/manual/render_image_diagrams.rb",
      "settings.gradle.kts",
      "docs/manual/en/index.md",
      "docs/manual/en/architecture/repository-map.md"
    ]
  },
  "nodes": [
    {"id":"dependencies","label":"bluetape4k-dependencies","source":"docs/manual/en/index.md"},
    {"id":"images","label":"bluetape4k-images","source":"docs/manual/en/architecture/repository-map.md"},
    {"id":"pure-jvm","label":"Pure JVM processing","source":"scripts/manual/render_image_diagrams.rb"},
    {"id":"native","label":"Native acceleration","source":"scripts/manual/render_image_diagrams.rb"},
    {"id":"integrations","label":"Integrations and ops","source":"docs/manual/en/architecture/repository-map.md"},
    {"id":"workshops","label":"7 runnable workshops","source":"settings.gradle.kts"}
  ],
  "edges": [
    {"id":"dependencies-images","from":"dependencies","to":"images","kind":"foundation","source":"docs/manual/en/index.md"},
    {"id":"images-pure-jvm","from":"images","to":"pure-jvm","kind":"choice","source":"scripts/manual/render_image_diagrams.rb"},
    {"id":"images-native","from":"images","to":"native","kind":"choice","source":"scripts/manual/render_image_diagrams.rb"},
    {"id":"images-integrations","from":"images","to":"integrations","kind":"integration","source":"docs/manual/en/architecture/repository-map.md"},
    {"id":"pure-jvm-workshops","from":"pure-jvm","to":"workshops","kind":"learning-path","source":"settings.gradle.kts"},
    {"id":"native-workshops","from":"native","to":"workshops","kind":"learning-path","source":"settings.gradle.kts"},
    {"id":"integrations-workshops","from":"integrations","to":"workshops","kind":"learning-path","source":"settings.gradle.kts"}
  ],
  "behavior": {"branches":3,"loops":0},
  "repairs": []
}
```

## 잔여 비차단 관찰

- parser regression은 repository의 LF settings를 고정한다. 외부 도구가 CRLF로
  settings를 변환할 가능성이 있으면 별도 CRLF fixture를 추가한다.
- tracked PNG는 유효하고 시각 검토를 통과했지만 renderer/font 환경은 pixel byte를
  달리 만들 수 있다. strict pixel identity가 필요한 release에서는 project delivery
  toolchain으로 재렌더링한다.
- release SHA/count assertion은 0.4.0에 의도적으로 고정되어 있다. 다음 release에서
  tag-derived helper를 도입하면 수동 drift 위험을 더 줄일 수 있다.

## 독립 reviewer DoD

독립 reviewer lane은 구현 diff를 직접 읽고, parser multiline, 19-row topology,
YAML/JSON snapshot, intelligence EN/KO docs, SHA/link pinning, count parity,
semantic ledger, diagram contracts를 재검증했다. reviewer는 implementation blocker
없음과 `CLEAR`를 판정했으며, 위 P2/P3와 hosted delivery gate만 잔여 항목으로
기록했다.
