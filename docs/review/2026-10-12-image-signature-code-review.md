# Issue #698 이미지 시그니처 헬퍼 코드 리뷰

## 범위와 기준

- 저장소: `bluetape4k/bluetape4k-image`
- Issue: [#698 — Image 종류 검증을 위한 Helper 제공](https://github.com/bluetape4k/bluetape4k-image/issues/698)
- base: `develop` / `2d843c001956482b7c5a61ca22134819d4c904a1`
- 검토 HEAD: `1bf7d195cff6626c40d3d55782c066b8810a4e9d`
- 검토 범위: 위 두 커밋 사이 전체 변경
- GitHub PR 및 exact-head CI: 아직 실행하지 않았다.

## 독립 코드 리뷰

| 리뷰어 | 결과 | P0 | P1 | P2 | P3 | 근거 |
|---|---|---:|---:|---:|---:|---|
| `issue698_final_code_reviewer` | APPROVE | 0 | 0 | 0 | 0 | 현재 전체 diff와 EOF 분류 범위, 기존 probe 실패 및 취소 처리, 기본 생성자 PNG/JPEG/WebP 테스트를 검토했다. 리뷰어는 테스트를 다시 실행하지 않았고 아래 로컬 실행 결과를 근거로 삼았다. |
| `issue698_final_arch_review` | CLEAR | 0 | 0 | 0 | 0 | 메타데이터 API 의미가 바뀌지 않고 예제의 기본 `ByteArray` 어댑터에만 특례가 적용됨을 확인했다. 공개 JVM API는 추가됐으며 기존 업로드 제한과 디코더 순서도 보존된다. 리뷰어는 테스트·ABI·CI를 다시 실행하지 않았다. |
| `issue698_writer_review` | PASS | 0 | 0 | 0 | 0 | 두 한국어 문서를 독립 검토했다. 실행할 수 없던 Gradle task 표기를 수정한 뒤 SPW-01..05와 KO-01..07을 통과했다. |

### 검토 중 확인된 JPEG 잘림 오류와 수정

이전 리뷰에서 기본 probe 경로에 JPEG 헤더 전용 테스트가 빠진 점을 확인했다. 기본 생성자 회귀 테스트에 추가하자 메타데이터 판독기가 내보낸 `EOFException`을 내부 장애로 분류해 `image_probe_failed`가 되는 문제가 드러났다. `ImageUploadQualifier.kt`의 기본 `ByteArray` 메타데이터 어댑터에서 직접 발생한 `EOFException`만 `MalformedImageProbeException`으로 바꾸고, 다른 실패는 기존대로 다시 던지도록 수정했다. 이후 기본 생성자에서 PNG, JPEG, WebP 헤더 전용 입력이 모두 `image_not_decodable`이 되고 디코더가 호출되지 않는지 테스트했다.

현재 독립 코드 리뷰는 이 수정이 문제를 해결하며 다른 probe 실패와 취소 전파를 보존한다고 판단했다. 현재 검토 범위에서 남은 코드 finding은 P0–P3 모두 0건이다.

아키텍처 리뷰도 공용 메타데이터 판독기와 다른 호출자의 오류 의미를 넓히지 않는 어댑터 경계, 기존 API를 유지하고 공개 함수만 추가한 ABI, 업로드 제한과 실제 디코더 검증의 보존을 확인해 `CLEAR`로 판정했다. 두 독립 리뷰 모두 이 변경에서 테스트·ABI·GitHub CI를 다시 실행하지 않았다.

## 검증 증거와 한계

- `:bluetape4k-images:test`: 788개 테스트 실행, 18개 건너뜀, 실패 없음. 10개 건너뜀은 작은 property fixture의 전제조건 미충족이고, 8개는 골든 이미지 수동 생성 테스트의 `@Disabled`다.
- `./gradlew :spring-boot-image-intelligence-api:test`: 58개 테스트 통과. 집중 테스트 `./gradlew :spring-boot-image-intelligence-api:test --tests "io.bluetape4k.images.examples.spring.intelligence.service.ImageUploadQualifierTest"` 결과도 성공이며, 마지막 집중 task 실행은 Gradle 캐시 결과였다.
- `:bluetape4k-images:updateKotlinAbi`, `:bluetape4k-images:checkKotlinAbi`, `checkProductionAbi`: 통과. production ABI 검사에서 공개 JVM 모듈 10개를 확인했다.
- `detekt`: `NO-SOURCE`; Detekt가 이 코드에 대해 분석을 수행한 증거로 보지 않는다.
- 영문·한국어 README의 시그니처 예제 코드를 비교해 동일함을 확인했다. `git diff --check`도 통과했다.
- Kotlin LSP 진단 도구를 사용할 수 없어 IDE 진단은 확인하지 못했다.
- GitHub PR의 정확한 HEAD에 연결된 workflow는 아직 없으므로 원격 CI 결과는 확인하지 못했다.

## 한국어 문서 검토

- SPW-01..05: 독자·범위·출처와 발견 사항의 처분을 명시했다. 변경 범위, 검증 경계, 아직 확인하지 않은 CI를 구분했다. 독립 writer 재검토가 task 표기 P2를 찾았고, 두 명령에 `./gradlew`를 넣은 뒤 PASS했다.
- KO-01..07: issue·branch·SHA·테스트 수·리뷰 결론과 실행 여부를 원본 증거에 맞춰 대조했다. 문장을 자연스러운 기술 문체로 다듬고, 의미가 불분명하거나 근거를 넘는 주장을 넣지 않았다. 독립 writer 재검토에서 PASS했다.
- bluetape-writer 용어 감사를 이 파일과 lesson 파일에 실행했다. 2개 파일에서 발견은 0건이었다.
- README.ko에 대한 앞선 용어 감사는 수정하지 않은 기존 Privacy Snapshot 설명에서 두 항목을 표시했다. 이는 API 이름과 자료형 사용 문맥으로 확인되어 이 변경과 무관하므로 그대로 뒀다.
- 사용 가능한 Markdown 렌더러가 없어 시각 미리보기 대신 fence, 표 열 수, 상대 링크, 후행 공백을 구조적으로 확인한다.

## 현재 결론

독립 코드 리뷰는 `APPROVE`, 아키텍처 리뷰는 `CLEAR`, writer 리뷰는 `PASS`이며 세 리뷰에서 P0–P3 발견 사항은 없었다. GitHub exact-head CI는 아직 완료되지 않았으므로 전체 전달 DoD는 PENDING이다. 머지, 로컬 동기화, worktree 정리, GNO 처리는 이 요청의 중단 범위에 포함하지 않는다.
