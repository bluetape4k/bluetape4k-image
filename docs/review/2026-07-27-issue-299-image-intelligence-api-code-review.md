# Issue #299 통합 이미지 인텔리전스 API 코드 리뷰

**Base:** `origin/develop`
**Head:** `feat/issue-299-image-intelligence-api`
**Scope:** `examples/spring-boot-image-intelligence-api`와 등록·문서·CI 변경
**Method:** Type A Step 6-R의 여섯 관점을 순차 검토한 뒤 현재 세션에서 통합

## 발견 사항

| Priority | 관점 | 근거 | 조치 | 결과 |
| --- | --- | --- | --- | --- |
| P1 | Operator/Ops | `application.yml`에서 `max-file-size`와 `max-request-size`가 모두 5 MB였다. multipart 헤더까지 포함하는 요청 한도 때문에 5 MiB 경계의 정상 파일이 입력 검증 전에 거부될 수 있었다. | 전체 요청 한도를 6 MB로 늘리고 `maxRequestSize > maxFileSize` 회귀 테스트를 추가했다. | 수정 후 테스트 통과 |

초기 발견 수는 P0 0, P1 1, P2 0, P3 0이다. P1을 수정하고 영향을 받는
애플리케이션 테스트와 전체 예제 테스트를 다시 실행했다.

## 관점별 최종 검토

| 관점 | 검토 범위와 근거 | P0 | P1 | P2 | P3 |
| --- | --- | ---: | ---: | ---: | ---: |
| Performance | 업로드 크기·픽셀 제한, 디코딩 횟수, 공급자별 Semaphore, 세 분석 경로의 겹친 실행, `QualifiedImage`의 필드 구성을 검토했다. 집중 테스트 20개가 단일 디코딩, 디코딩 전 거부, 최대 동시 실행 수와 permit 반환을 검증했다. 처리량이나 순위를 주장하지 않으므로 벤치마크는 N/A다. | 0 | 0 | 0 | 0 |
| Stability | 로컬 timeout과 외부 cancellation의 catch 순서, `withPermit` 정리, 형제 결과 보존, 다음 요청 복구, 프로필 충돌을 검토했다. 비협조적인 네이티브 호출의 강제 종료 한계는 README에 명시돼 있다. | 0 | 0 | 0 | 0 |
| Security | 선언 미디어 타입, 실제 시그니처, 압축 크기, 한 변·픽셀 수를 공급자 실행 전에 검증한다. 오류 응답과 로그는 decoder/provider 원문, 경로, OCR·QR payload를 내보내지 않는다. 인증·인가와 악성 파일 검사는 실행 예제의 명시적 운영 제외 범위다. | 0 | 0 | 0 | 0 |
| Operator/Ops | multipart 한도, 프로필 소유권, 고정 reason code, request/provider/status/elapsed 로그, 민감 정보 비노출, Examples CI 등록을 검토했다. 발견한 multipart 여유 공간 문제는 회귀 테스트와 함께 수정했다. | 0 | 0 | 0 | 0 |
| Developer/API | 기존 image/OCR/barcode API와 관리된 workflow 의존성을 재사용한다. DTO와 정책은 불변이고, 공급자 결과와 워크플로 완료 상태를 별도 타입으로 유지한다. production coroutine quick scan에서 금지 패턴은 없었고 `detekt`가 통과했다. | 0 | 0 | 0 | 0 |
| User/caller | 영어·한국어 README가 같은 순서로 실행법, 상태 의미, 실패 사례, 정책 교체, 운영 한계를 설명한다. 상대 소스 링크, 공개 블로그 링크, SVG/PNG를 검증했다. 기본 프로필의 `PARTIAL`과 선택적 네이티브 조건도 명시돼 있다. | 0 | 0 | 0 | 0 |

## 통합 검토

- 새 코드는 비배포 예제 모듈 하나에 한정되며 BOM, 버전 카탈로그, 버전 매뉴얼을
  변경하지 않는다.
- settings, AGENTS, 루트 README 두 언어, Examples workflow 등록이 서로 일치한다.
- 공개 클래스의 KDoc은 영어이며 나머지 구현 타입은 예제 모듈 내부로 제한된다.
- changelog와 migration note는 배포 artifact나 기존 API를 바꾸지 않으므로 N/A다.
- 기본 테스트는 Tesseract, traineddata, 외부 ML 모델, 컨테이너를 요구하지 않는다.
- 선택적 `native-ocr` 실호출은 현재 호스트 환경에 의존하므로 실행하지 않았다.
  기본 프로필과 프로필 소유권 테스트가 이 미실행 항목을 대체하지 않으며, README가
  필요한 설치 조건과 in-process timeout 한계를 별도로 밝힌다.

최종 결과는 **P0 = 0, P1 = 0, P2 = 0, P3 = 0**이다.
