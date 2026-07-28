# Issue #1 OCR 설계 검토

- Step: 2-R
- 설계:
  `docs/superpowers/specs/2026-06-05-issue-1-ocr-design.md`
- Reference:
  `/Users/debop/.codex/skills/bluetape4k-full-feature/references/step-2r-spec-review.md`
- 날짜: 2026-06-05

## 판정

P0 = 0
P1 = 0

아래 P2 명확성 발견 사항을 설계 문서에 반영했으므로 이 설계는 Step 3 계획으로
넘어갈 준비가 됐다.

## 관점별 검토

| 관점 | P0 | P1 | P2 | P3 | 근거 |
|---|---:|---:|---:|---:|---|
| 개발자/Kotlin | 0 | 0 | 1 | 0 | API는 optional module, `OcrEngine`, options/result 모델, suspend wrapper를 사용한다. |
| 보안 | 0 | 0 | 0 | 0 | credential이나 network OCR provider는 없고, Tesseract 변수는 caller-controlled이지만 local-only다. |
| 운영/SRE | 0 | 0 | 2 | 0 | native runtime 설치, language-pack 사전 점검, Testcontainers 근거 경계를 다룬다. |
| 사용자/호출자 | 0 | 0 | 0 | 0 | README/KDoc/troubleshooting과 지원하지 않는 backend 범위가 명시돼 있다. |

## 로컬 7계층 검토

| 계층 | P0 | P1 | P2 | P3 | 참고 |
|---|---:|---:|---:|---:|---|
| Tier 1 보안 | 0 | 0 | 0 | 0 | local OCR만 사용하며 auth, network, cloud credential, deserialization boundary를 추가하지 않는다. |
| Tier 2 운영/SRE | 0 | 0 | 1 | 0 | language pack이 없을 때 Gradle 전에 CI가 실패하도록 `tesseract --list-langs` 사전 점검을 추가했다. |
| Tier 3 구조 | 0 | 0 | 0 | 0 | Separate module keeps core dependency surface clean and matches optional native module pattern |
| Tier 4 Kotlin/API | 0 | 0 | 1 | 0 | 일반 호출자가 `ITessAPI`를 import하지 않도록 Tess4J integer constant용 enum wrapper를 추가했다. |
| Tier 5 테스트/타입 | 0 | 0 | 1 | 0 | Testcontainers image ownership 전략과 host-native/container 근거 분리를 명시했다. |
| Tier 6 성능/안정성 | 0 | 0 | 0 | 0 | 호출마다 새 Tess4J instance를 사용해 shared mutable state를 피하고, suspend path는 `Dispatchers.IO`를 사용한다. |
| Tier 7 문서/릴리스/근거 | 0 | 0 | 0 | 0 | README locale set, diagrams, CI/Nightly, BOM, AGENTS, review evidence가 범위에 포함된다. |

## 해결된 발견 사항

| 우선순위 | 발견 사항 | 해결 |
|---|---|---|
| P2 | 설계가 `TesseractEngineMode`와 `TesseractPageSegmentationMode`를 이름만 언급하고 존재 이유와 매핑 방식을 설명하지 않았다. | Tess4J integer constant 주변의 enum wrapper 규칙을 추가했다. |
| P2 | Testcontainers lane이 public image를 신뢰할지, 테스트 소유 runtime을 빌드할지 명시하지 않았다. | 테스트 소유 Dockerfile 전략을 추가하고 검증되지 않은 public OCR image를 기각했다. |
| P2 | CI lane이 language pack을 설치하지만 Gradle 전에 명시적으로 사전 점검하지 않았다. | `tesseract --list-langs` 사전 점검 요구사항을 추가했다. |

## 수용한 비차단 위험

| 우선순위 | 위험 | 근거 |
|---|---|---|
| P2 | Korean/Japanese OCR 문자열의 정확한 일치는 font rendering과 OCR recognition이 host마다 달라 flaky할 수 있다. | 설계는 language pack 가용성을 요구하고, 신뢰성이 낮으면 non-Latin exact matching을 후속으로 넘길 수 있게 한다. English OCR은 blocking native text extraction proof로 남긴다. |

## 수렴 결과

After the spec edits above:

- P0 = 0
- P1 = 0
- Remaining P2 = 1 accepted with rationale
- P3 = 0

Step 2-R은 종료됐다.
