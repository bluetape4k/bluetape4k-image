# Issue 82 libvips README 사전 조건

## 배경

Issue #82는 사용자용 README 설정을 현재 libvips, JVips, Java 25 FFM 런타임
요구사항과 일치시키도록 요청했다.

## 결정

루트 README 설정은 간결하게 유지하되 네이티브 경계를 명시한다. 순수 JVM
`images` 모듈에는 네이티브 라이브러리가 필요 없고, `images-vips-*` 모듈에는
libvips가 필요하다. `images-vips-java25` 사용자는 JVM 시작 옵션으로
`--enable-native-access=ALL-UNNAMED`를 제공해야 한다. 올바른 예제에서는 이
플래그를 `-jar`보다 앞에 배치해야 한다.

## 결과

영어와 한국어 루트 README의 설정/문제 해결 내용을 갱신하고, 명령줄,
Spring Boot/컨테이너 실행, IDE VM 옵션, Homebrew macOS 라이브러리 검색에 대한
Java 25 모듈 README 예제를 일치시켰다.

## 검증

- README 설명을 `images-vips-java21/build.gradle.kts` 및
  `images-vips-java25/build.gradle.kts`.
- `git diff --check`를 통과했다.
- 변경한 README 파일에서 `rg`로 검사한 결과
  `java -jar ... --enable-native-access` 예제가 남아 있지 않았다.

## 향후 지침

FFM 네이티브 접근은 애플리케이션 속성이 아니라 JVM 실행 문제로 문서화한다.
macOS Homebrew libvips 오류에는 `vips --version`과 함께
`DYLD_LIBRARY_PATH=/opt/homebrew/lib`을 안내한다.
