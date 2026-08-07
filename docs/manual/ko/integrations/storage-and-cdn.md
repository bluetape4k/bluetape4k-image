---
manualId: "storage-and-cdn"
title: "Storage와 CDN"
locale: "ko"
releaseRef: "0.4.0"
---

# Storage와 CDN

Spring Boot 모듈은 로컬과 S3 구현을 갖춘 <code>ImageStorage</code> 경계와 선택적 CloudFront URL 서명을 제공한다. 단순히 개발/운영 환경 이름으로 구분하지 말고 내구성, 배포 구조와 접근 정책을 기준으로 고른다.

## Local storage

로컬 저장소는 단일 노드 개발, 통제된 일괄 작업, 영구 볼륨을 마운트한 배포에 맞는다. 모든 키를 설정한 루트 아래에서 해석하고 경로 이탈을 막는다. 디렉터리 생성, 원자적 쓰기와 정리 정책도 정해야 한다. 컨테이너의 쓰기 가능 계층은 영구 저장소가 아니다.

## S3 storage

S3는 여러 노드가 공유하는 내구성 있는 객체 저장소에 맞는다. 버킷 생성, 암호화, 자격 증명, 리전, 키 이름, 수명 주기, 보존, 재시도/멱등성 정책은 애플리케이션이 관리한다. 객체 쓰기가 성공하기 전에 메타데이터나 CDN URL을 먼저 공개하지 않는다.

## CDN URL

CloudFront 서명으로 접근 시간을 제한한 URL을 만들 수 있지만 S3 정책을 대신하지는 않는다. 개인 키를 보호하고 사용 사례에 맞게 URL 만료 시간을 짧게 잡으며 시계 차이도 고려한다. 바이트를 바꾸는 모든 변환 매개변수는 캐시 키에 포함해야 한다.

## Health와 metric

상태 점검은 가볍게 실행해야 하며 사용자 객체를 만들거나 지우면 안 된다. 메트릭은 작업, 백엔드, 결과, 지연 시간을 구분하되 객체 키나 사용자 데이터를 고유값이 지나치게 많은 태그로 넣지 않는다.

[Spring Boot 이미지 API 워크숍](../modules/spring-boot-image-api.md)은 로컬 저장소로 시작한다. 같은 저장소 계약과 실패 동작을 검증한 뒤 S3로 확장한다.

## 근거 소스

- [ImageStorage 계약](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/images-spring-boot/src/main/kotlin/io/bluetape4k/images/spring/storage/ImageStorage.kt)
- [S3 구현](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/images-spring-boot/src/main/kotlin/io/bluetape4k/images/spring/storage/s3/S3ImageStorage.kt)
