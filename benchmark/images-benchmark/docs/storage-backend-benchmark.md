# 스토리지 백엔드 벤치마크 (Issue #204)

이 스위트는 운영 S3 네트워크가 아니라 `ImageStorage` 어댑터 경계를 측정한다.
로컬 경로는 임시 파일 시스템 루트와 `LocalImageStorage`를 사용한다. S3 경로는
메모리 내 `S3Operations` 대역을 사용하는 `S3ImageStorage`로 구성하므로 실행
결과가 결정적이며 자격 증명이 필요하지 않다.

## 실행 명령

```bash
./gradlew :bluetape4k-images-benchmark:benchmarkStorageLocalBenchmark
./gradlew :bluetape4k-images-benchmark:benchmarkStorageS3Benchmark \
  -Pstorage.s3.enabled=true
```

S3 명령은 의도적으로 선택 실행 방식이다. 어댑터와 바이트 구체화 오버헤드를
측정하므로 클라우드 지연 시간이나 처리량으로 해석해서는 안 된다. 실제 S3 호환
엔드포인트는 동일한 `ImageStorage` 계약과 환경별 하네스를 사용해 별도로 평가할
수 있다.

## 워크로드

| 항목 | 값 |
|-----------|-------|
| 페이로드 | 결정적으로 생성한 `homer.jpg` JPEG와 PNG 인코딩 |
| 크기 제한 | 최대 4 MiB, 제한 이내와 4 MiB + 1바이트 거부 |
| 객체 수 | 백엔드당 객체 9개(페이로드 1개와 목록용 픽스처 8개) |
| 작업 | 바이트 업로드/다운로드, 경로 다운로드, 접두사 목록 조회, 제한 초과 업로드 |
| 정리 | JMH tear-down에서 임시 로컬 루트를 삭제하며 메모리 내 S3 맵은 trial 범위로 유지 |

벤치마크 메서드는 준비용 업로드를 측정 반복 구간 밖에서 수행한다. 따라서 API
작업 비용과 픽스처 생성 및 정리 비용을 분리할 수 있다.

Java 25/macOS 원본 출력: [`storage-local.json`](raw/issue-204-20260726-macos-java25/storage-local.json),
[`storage-s3-inmemory.json`](raw/issue-204-20260726-macos-java25/storage-s3-inmemory.json).

![스토리지 백엔드 벤치마크 차트](../../../docs/images/readme-charts/images-benchmark-storage-backend-chart-01.png)

## 해석

지연 시간은 `AverageTime ms/op`이며 낮을수록 좋다. 결과는 로컬 스냅샷이다.
파일 시스템 행에는 OS 캐시 효과가 포함되고 메모리 내 S3 행은 어댑터 오버헤드만
나타낸다. 할당량이나 네트워크 관련 결론을 내려면 별도의 GC 프로파일러 실행과
실제 S3 호환 서비스가 필요하다.
