# #490 DOCS-1 계획 독립 검토

## 검토 메타데이터

- lane: `reviewer`
- run: `20260816T111756Z-d2cb2920`
- 대상: `docs/issue-490-manual-provenance`
- 기준 문서: `docs/superpowers/plans/2026-08-16-issue-490-manual-provenance-plan.md`
- 검토 시점: 2026-08-16
- 방식: 구현 전 계획 검토 및 읽기 전용 source/command 대조
- 최종 구현 diff는 아직 exact-head가 아니므로 이 문서는 계획 gate의 중간 결과다.

## 통합 판정

**REQUEST CHANGES — 계획 보완 필요**

- P0: 0건
- P1: 5건
- P2: 3건
- P3: 1건

생산 Kotlin/Vips/AI 소스와 dependency를 건드리지 않는 범위는 안전하다. 다만 현재 계획의 실행 목록만으로는 #490 수용 기준을 재현할 수 없고, stale generated output과 semantic provenance drift가 남을 수 있다. 구현은 가능하지만, 아래 P1을 최종 DoD와 명령 목록에 반영한 뒤 PR gate에서 P1=0이어야 한다.

## P1 findings

### P1-1 — intelligence EN/KO 문서를 실제로 생성·검증하는 명령이 빠짐

- 근거: 계획 §2.4는 `generate_manuals.rb`가 두 문서를 만든다고 하지만 §5 명령 목록에는 `ruby scripts/manual/generate_manuals.rb`가 없다.
- 현재 generator는 기존 문서를 skip하고, 신규 문서에는 대부분 `stable release source` placeholder를 쓴다: `scripts/manual/generate_manuals.rb:60-76, 180-205`.
- 필요한 증거: generator 실행 결과 `created=2`, 신규 두 문서의 stable release source 링크/read-back, placeholder 0건, EN/KO heading/anchor parity.

### P1-2 — generated manifest snapshot 동기화가 빠짐

- 대상 누락: `docs/manual/generated/manifest.json`.
- `build_image_manifest.rb`는 YAML만 쓰며, `export_manifest.rb`가 별도로 JSON snapshot을 생성한다: `scripts/manual/build_image_manifest.rb:6-7,88-110`, `scripts/manual/export_manifest.rb:45-54`.
- 필요한 증거: `ruby scripts/manual/export_manifest.rb`, snapshot current check, YAML/JSON module count와 intelligence entry 동일성.

### P1-3 — release diagram provenance contract의 실제 check가 빠짐

- 계획은 `release_diagram_contract_test.rb`만 실행하지만, fixture test는 현재 release asset provenance를 검증하지 않는다.
- 실제 contract entry/hash/link 검증 명령은 `ruby scripts/manual/sync_release_diagrams.rb --check`이며, 이 명령이 `releaseRef`가 실제 tag SHA로 resolve되고 release README/PNG/SVG 링크가 pinned 되었는지 확인한다.
- `validate_diagrams.rb`는 구조·label·connector를 검사하지만 semantic release text와 pinned release source를 보장하지 않는다.
- 필요한 증거: `sync_release_diagrams.rb --check` PASS와 SVG/PNG pair 및 full-size PNG visual audit.

### P1-4 — “단일 release source”가 코드/검증으로 고정되지 않음

- `build_image_manifest.rb:88-92`가 `releaseRef`와 `releaseCommit`을 hardcode한다.
- README/index/diagram count도 계획상 수동 편집이며, 현재 release inventory는 입력 expected count `19`를 외부 인자로 받는다: `scripts/manual/release_inventory.rb:13-19,35-45`.
- 따라서 현재 계획만으로는 tag topology와 publishing rule에서 산출한 `19 / 10 libraries / 1 BOM / 7 examples / 1 benchmark`가 모든 consumer에 동일하게 전달된다는 보장이 없다.
- 필요한 증거: tag peel + tag settings topology 대조, publishing-rule count audit, manifest/index/README/diagram semantic ledger 또는 자동 assertion. 단순 문자열 read-back만으로는 부족하다.

### P1-5 — manual-grade source content의 품질 gate가 추상적임

- 계획은 intelligence 문서를 stable README/build/source 링크와 동일 contract로 채운다고 하지만, authored content의 최소 기준·placeholder 금지·release-pinned link 검사가 명시되지 않았다.
- `manual_contract.rb`는 파일/heading/frontmatter/link 존재를 검사하지만 문서가 placeholder인지, source link가 `ea5175...`에 고정됐는지는 별도 확인이 필요하다.
- 필요한 증거: 두 신규 문서의 EN/KO 내용 read-back, placeholder/0.3.0/a571c300 drift 검색, source link full SHA audit.

## P2/P3 findings

| 등급 | finding | 필요한 처분 |
|---|---|---|
| P2 | 현재 settings 19개와 `0.4.0` tag의 settings topology를 직접 비교하는 명령이 없다. `release_inventory.rb`는 current inventory row의 `build.gradle.kts` 존재만 필터링한다. | tag `settings.gradle.kts`와 current inventory의 project/source/kind 비교 결과를 review evidence에 남긴다. |
| P2 | scope leakage 검사 명령이 없다. | `git diff --name-status <base>...HEAD`를 allowlist와 대조하고 generated/unrelated source/dependency/CI 변경 0건을 기록한다. |
| P2 | worktree 시작 시 untracked temporary topology evidence가 관찰되었다. | 최종 commit에 포함하지 않거나 review artifact로 목적을 명시하고, temp 파일 잔류를 DoD에서 판정한다. |
| P3 | `ea5175b0` 표시 label은 full SHA의 prefix이므로 허용 여부가 issue 문구상 모호하다. | short label은 full SHA prefix임을 assertion으로 고정하거나 full SHA 표시를 선택한다. |

## Positive checks

- 기준 tag `0.4.0`은 `ea5175b083babf8880f53cf80c9a264a0c61777e`로 resolve된다.
- 현재 settings의 multiline `spring-boot-image-intelligence-api` registration은 계획의 parser regression 대상과 일치한다.
- plan은 `10 published libraries + 1 BOM + 7 examples + 1 benchmark + 19 projects`라는 목표 count를 명시하여 현재 stale `8 + 1 + 5 + 1 + 15` 문구를 교정할 방향을 제시한다.
- production Kotlin/Vips/AI/ML source, Gradle dependency/catalog, publication, tag, merge, cleanup를 제외하여 scope는 적절하다.
- parser RED/GREEN 순서와 tag SHA mismatch 중단 조건은 보존 가치가 있다.

## Required amendments before final gate

1. `generate_manuals.rb` 실제 실행 및 신규 문서 content/read-back을 명령 목록에 추가한다.
2. `export_manifest.rb` 실행과 `generated/manifest.json` parity를 scope/DoD에 추가한다.
3. `sync_release_diagrams.rb --check`를 실제 release provenance 검증에 추가한다.
4. release metadata/count semantic ledger를 tag settings + publishing rule에서 산출하고 README/index/manifest/diagram에 대해 assertion한다.
5. tag settings topology 비교, exact-head scope allowlist, placeholder/old SHA drift 검색을 evidence 명령으로 고정한다.

## Planned verification commands

```bash
git diff --name-status <base>...HEAD
ruby -I scripts/manual scripts/manual/export_settings_inventory_test.rb
ruby -I scripts/manual scripts/manual/release_inventory_test.rb
ruby -I scripts/manual scripts/manual/manual_contract_test.rb
ruby -I scripts/manual scripts/manual/generate_manuals_test.rb
ruby -I scripts/manual scripts/manual/release_diagram_contract_test.rb
ruby scripts/manual/export_settings_inventory.rb
ruby scripts/manual/release_inventory.rb 0.4.0 ea5175b083babf8880f53cf80c9a264a0c61777e build/manual/module-inventory.json build/manual/release-module-inventory.json 19
ruby scripts/manual/build_image_manifest.rb
ruby scripts/manual/generate_manuals.rb
ruby scripts/manual/export_manifest.rb
ruby scripts/manual/validate_manuals.rb build/manual/release-module-inventory.json docs/manual/manifest.yaml
ruby scripts/manual/sync_release_diagrams.rb --check
ruby scripts/manual/validate_release_manuals.rb 0.4.0 ea5175b083babf8880f53cf80c9a264a0c61777e
ruby scripts/manual/render_image_diagrams.rb
ruby scripts/manual/validate_diagrams.rb
git diff --check
```

## SPW / reviewer DoD

- SPW-01: PASS — issue/epic, release tag, 독자와 문서 정합성 목적을 고정했다.
- SPW-02: PASS — 범위, 제외, RED/GREEN, validation, delivery gate를 read-back했다.
- SPW-03: PASS — 한국어 설명과 명령/API/SHA/path token 보존을 확인했다.
- SPW-04: **PENDING** — 위 P1 명령과 semantic evidence가 계획에 추가되어야 source-to-evidence 연결이 완성된다.
- SPW-05: PASS — plan과 현재 source/command 목록을 재독했다.

## Reviewer state

## 구현 후 disposition

계획 검토에서 요구한 P1/P2 보완은 구현·검증 결과로 다음과 같이 닫혔다.

| finding | disposition | 근거 |
|---|---|---|
| P1-1 generator 실행·intelligence 문서 검증 누락 | CLOSED | `generate_manuals.rb` 실행 결과 `created=0; skipped=38`를 확인했고, 신규 EN/KO 문서는 실제 stable source 내용·full SHA 링크·placeholder 0건으로 read-back했다. |
| P1-2 generated manifest snapshot 누락 | CLOSED | `export_manifest.rb`와 `export_manifest.rb --check`가 PASS했으며 YAML/JSON이 19 project와 intelligence entry를 공유한다. |
| P1-3 release diagram provenance check 누락 | CLOSED | `sync_release_diagrams.rb --check`가 `failures=0 entries=37 release=0.4.0`으로 PASS했고 touched overview pair에 XML/connector/arrowhead/geometry/visual audit를 실행했다. |
| P1-4 단일 release source·count semantic assertion 부재 | CLOSED | tag peel, current/tag settings topology parity, publishing-kind count, 19-row release inventory와 semantic ledger를 대조했다. |
| P1-5 manual-grade content quality gate 추상적 | CLOSED | EN/KO anchor parity 15개, placeholder/`0.3.0`/`a571c300` drift 0건, full SHA source-link read-back을 확인했다. |
| P2 topology comparison | CLOSED | current/tag 모두 19 rows이며 project/source/kind drift 0건이다. |
| P2 scope leakage | CLOSED | intended docs/scripts/overview pair만 변경되었고 unrelated renderer outputs는 복원했다. |
| P2 temporary evidence | CLOSED | 최종 status에서 temporary file 잔류 0건을 확인한다. |
| P3 short SHA ambiguity | CLOSED | 표시 label `ea5175b0`가 full SHA prefix임을 index/README/source-link assertion으로 고정했다. |

최종 exact-head 독립 reviewer 판정은 `2026-08-16-issue-490-code-review.md`에
기록했다. 이 문서는 계획 gate의 원래 REQUEST CHANGES와 구현 후 처분을 모두 보존한다.

## Reviewer state

`CLEAR — 독립 reviewer가 P0/P1=0, Architectural Status=PASS를 판정했다. P2=2와
P3=1은 비차단 유지보수 관찰이며, PR/hosted CI/merge는 별도 delivery gate다.`

이 artifact는 구현 diff의 최종 verdict가 아니다. main lane이 commit한 뒤 동일 run에서 exact-head diff, generated outputs, 19/counts, EN/KO parity, SHA/link, diagram render/validation, scope leakage를 fresh read-back한다.
