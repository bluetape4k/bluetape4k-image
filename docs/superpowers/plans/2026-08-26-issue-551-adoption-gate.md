# Issue #551 이미지 분류 채택 게이트 실행 계획

## 계획 상태

- Issue: [#551](https://github.com/bluetape4k/bluetape4k-image/issues/551)
- child epic: [#3](https://github.com/bluetape4k/bluetape4k-image/issues/3)
- main epic: [#513](https://github.com/bluetape4k/bluetape4k-image/issues/513)
- 작업 유형: Type-E 유지보수·의사결정 문서
- 기본 판정 가설: DEFER
- 금지선: 채택 게이트가 닫히기 전 production classifier, ONNX Runtime dependency, model auto-download, public API를 추가하지 않는다.

## 입력 원장

| 입력 | 기준 commit | 현재 상태 | 이 계획에서 확인할 항목 |
|---|---|---|---|
| #543 공통 공급망 정책 | 83f8a3b888425e4706ab8e0a7d92e4700a6d4868 | DONE | manifest, offline, license/SBOM, 보안, CI 공통 gate |
| #548 model manifest | 979b45a7865b172c250e199d338e9ad8b1c03732 | DONE / 연구 한계 유지 | model bytes, labels, schema, license와 golden fixture의 상태 |
| #549 API 경계 | 13d3d59c8e68d9a8d63b61ba00e7b0acb07d8e2e | DONE / 구현 차단 | provider-neutral API, module, lifecycle, compatibility gate |
| #550 ORT native/platform/BOM/CI | 56967af050630d78d7ac362206052627ae4100e4 | DONE / runtime 검증 대기 | exact version, native matrix, Java 25, BOM, benchmark의 상태 |

## 순서와 산출물

1. PR #601 merge와 Issue #548 closeout을 live metadata로 확인하고 완료 댓글을 남긴다.
2. 네 입력의 issue·artifact·commit을 source-to-decision ledger로 대조한다.
3. 품질·성능·운영·보안·공급망 기준을 ADOPT/DEFER/REJECT 표로 평가한다.
4. 결정 문서에 재평가 조건과 기존 이미지 API에 미치는 영향을 기록한다.
5. 7-Tier review와 독립 reviewer lane을 실행한다. 독립 lane timeout은 PASS로 승격하지 않고 inline resolution으로 분리한다.
6. lesson에 이번 게이트에서 재사용할 방어선과 N/A 근거를 남긴다.
7. 최종 diff, Korean terminology, Markdown 구조, 링크, issue metadata를 검증한 뒤 Issue #551에 연결한 PR을 만든다.

## 결정 분기

### DEFER 또는 REJECT

- production dependency·model·API를 만들지 않는다.
- 기존 images API와 Tesseract/Tess4J baseline에는 영향을 주지 않는다고 명시한다.
- 실제 재평가에 필요한 receipt, fixture, native smoke, benchmark, legal evidence를 bounded checklist로 남긴다.
- Type-A implementation issue와 PR train은 만들지 않는다.

### ADOPT

- 네 입력의 모든 필수 gate가 실제 증거로 PASS일 때만 선택한다.
- 별도 Type-A implementation issue와 plan을 만든다.
- API, module, dependency, model distribution, CI tier, migration 순서를 별도 train으로 고정한다.
- 이 PR에서는 production 구현을 시작하지 않는다.

## 검증과 중단 조건

- source ledger의 commit, path, issue, PR URL이 live 상태와 일치해야 한다.
- license·NOTICE·SBOM·attestation, golden logits/top-k, 실제 preprocessing rounding, native/platform/Java 25, BOM consumer smoke, benchmark 중 하나라도 미완료면 ADOPT를 승인하지 않는다.
- 문서-only 변경이므로 Gradle, Detekt, ORT inference, native/JNI, OCR, Testcontainers는 N/A로 기록하고 변경 경로를 증거로 제시한다.
- P0/P1은 PR 전에 수정한다. P2/P3는 결정에 영향을 주지 않으면 후속 항목으로 명시한다.
- 사용자 merge approval 전에는 병합하지 않는다.

## 롤백

- 문서 오류는 같은 branch에서 수정하고 영향을 받은 review/validation을 다시 실행한다.
- 결정 근거가 stale이면 해당 문장을 PENDING으로 되돌리고 source를 재조회한다.
- ADOPT 조건을 충족하지 못하면 DEFER로 수렴하고 implementation branch/issue를 만들지 않는다.

## 계획 DoD

- SPW-01: 독자, 의사결정 질문, 네 입력과 미확정 claim을 고정한다.
- SPW-02: decision matrix, acceptance, 분기, rollback을 포함한다.
- SPW-03: 한국어 기술 문체와 token/URL/SHA 보존을 검증한다.
- SPW-04: 각 결론을 선행 artifact와 live metadata에 연결한다.
- SPW-05: 최종 문서 read-back과 남은 gap을 기록한다.
