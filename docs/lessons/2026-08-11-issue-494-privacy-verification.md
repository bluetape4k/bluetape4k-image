# Issue #494 privacy derivative 검증 계약

**관련 이슈**: #494
**영향 모듈**: `bluetape4k-images`

## 배경

privacy derivative pipeline은 re-encode 결과를 source metadata와 option만으로
성공으로 보고할 수 있었다. 기존 best-effort metadata reader는 parser 실패를
`ImageMetadataReport.EMPTY`로 축약하므로, unreadable output을 metadata가 없는
안전한 결과로 오인할 위험이 있었다.

## 결정

enforcement 경계에는 bounded strict reader를 사용한다. strict 결과는
`Success(report)`와 제한된 `Failure` 분류를 구분하고, cancellation은 다시 던진다.
derivative output은 항상 다시 읽어야 하며 parser 실패 또는 요청 category 잔존은
`VERIFY` failure로 종료한다. public strict 결과는 Java serialization
round-trip으로 reachable object graph를 검증한다.

## 결과

EXIF/GPS directory 존재 여부와 XMP/IPTC/ICC를 output에서 재검증하고,
`requested/sourcePresent/remaining/verified`를 별도로 기록한다. `verified=true`는
output을 읽을 수 있고 요청된 category가 남지 않았다는 뜻으로 한정한다.

## 검증

- `./gradlew --no-daemon --rerun-tasks :bluetape4k-images:test`
- `./gradlew --no-daemon :bluetape4k-images:build -x test`
- strict metadata Java serialization round-trip 회귀 테스트
- 독립 reviewer 최종 APPROVE, P0/P1/P2/P3 모두 0

## 향후 방지책

새로운 privacy enforcement API는 best-effort 진단 API를 재사용하지 말고,
부재(absence)와 검증 불가(unavailable)를 구분하는 strict 결과를 먼저 정의한다.
`Serializable` marker를 추가할 때는 선언부만 보지 말고 reachable object graph와
실제 `ObjectOutputStream` round-trip을 함께 고정한다.
