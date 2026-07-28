# Issue 4 images-captcha 모듈

## 배경

Issue #4는 CAPTCHA 모듈을 요청했지만 이슈 본문은 오래된
`bluetape4k-projects/x-obsoleted/captcha` 경로를 가리켰다. 기존 모듈은
`bluetape4k-projects` 커밋 `494d95ee1`에서 제거되었으므로 현재 이슈 본문이
아니라 Git 이력이 유효한 원본이었다.

## 결정

`bluetape4k-images` 위에 순수 JVM Java2D 모듈인
`bluetape4k-images-captcha`를 추가하고, 제거된 레거시 변경 도우미 대신
`ImmutableImage.withGraphics`를 사용한다. `captchaGenerator { }`와 직렬화
가능한 옵션 값 타입으로 공개 API를 작게 유지한다. `ImmutableImage`는 저장
페이로드가 아니므로 `CaptchaChallenge`는 직렬화하지 않는다.

## 결과

모듈을 `settings.gradle.kts`에 등록하고 CI 및 Nightly 모듈 작업에 포함했다.
영어/한국어 루트 및 모듈 README에도 문서화했다. `bom/build.gradle.kts`가
게시되는 모든 루트 하위 프로젝트에 제약을 적용하므로 BOM에는 자동으로 포함된다.

## 검증

- `./gradlew projects`
- `./gradlew :bluetape4k-images-captcha:test :bluetape4k-images-captcha:koverXmlReport`
- `./gradlew build -x test`
- `actionlint .github/workflows/ci.yml .github/workflows/nightly-tests.yml`
- `git diff --check`
- Claude 코드 검토 아티팩트:
  `.omx/artifacts/claude-issue-4-images-captcha-code-review-rerun-20260524172738.md`
  결과는 P0=0, P1=0이다.

## 향후 방지책

기존 bluetape4k 이슈가 `x-obsoleted/*`를 참조하면 먼저 삭제 커밋과 삭제된 파일을
검사한다. 새 모듈에서는 이 저장소의 BOM이 포함된 게시 하위 프로젝트에서 자동
생성된다는 점을 기억한다. 수동 BOM 제약을 추가하기 전에 `settings.gradle.kts`와
`./gradlew projects`를 검증한다.
