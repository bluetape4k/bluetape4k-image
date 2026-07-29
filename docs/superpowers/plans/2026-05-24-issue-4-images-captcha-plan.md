# Issue #4 images-captcha 구현 계획

- 이슈: [#4](https://github.com/bluetape4k/bluetape4k-image/issues/4)
- 명세: `docs/superpowers/specs/2026-05-24-issue-4-images-captcha-design.md`
- 워크플로: `bluetape4k-workflow` Type A Full Design
- 브랜치/워크트리: `feat/issue-4-images-captcha`

## 1. 구현 전 상태

- `.worktrees/feat-issue-4-images-captcha`에 워크트리가 존재한다.
- 레거시 소스는 `bluetape4k-projects` Git 이력의 다음 위치에서 복구했다.
  `494d95ee1^:x-obsoleted/captcha`.
- 명세 Step 2-R Claude advisor gate를 재실행해 통과했다.
  `.omx/artifacts/claude-issue-4-images-captcha-spec-rerun-20260524165543.md`.

## 2. 구현 작업

### 작업 1: 모듈 등록

- `settings.gradle.kts`에 `bluetape4k-images-captcha`를 추가한다.
- `project(":bluetape4k-images-captcha").projectDir = file("images-captcha")`로 설정한다.
- `images-captcha/build.gradle.kts`를 생성한다.
- 인접 Kotlin/JVM 모듈 규칙을 적용한다.
  - `alias(libs.plugins.kotlin.jvm)`
  - `alias(libs.plugins.dependency.management)`
  - Java/Kotlin toolchain 21
  - 기존 모듈과 같은 compiler flag (`-Xjsr305=strict`,
    `-jvm-default=enable`)
- 다음에 의존한다.
  - `api(project(":bluetape4k-images"))`
  - `implementation(libs.kotlinx.coroutines.core)`
  - `testImplementation(libs.bluetape4k.junit5)`
  - `testImplementation(libs.kotlinx.coroutines.test)`
- `systemProperty("java.awt.headless", "true")`로 테스트를 설정한다.
- 등록 후 `./gradlew projects`를 실행한다.

### 작업 2: 공개 모델 및 옵션 API 추가

`io.bluetape4k.images.captcha` 패키지를 생성한다.

- `CaptchaChallenge`는 data class가 아니고 Serializable도 아닌 일반 class로 둔다.
- `CaptchaGenerator` interface는 sync와 suspend method를 제공한다.
- `CaptchaOptions` data class는 validation-compatible immutable field를 가진다.
- `CaptchaImageSize` data class를 추가한다.
- `CaptchaNoise` sealed interface는 명세대로 `None`, `Low`, `Medium`, `High`,
  `Custom(lines, dots)`를 제공한다.
- `CaptchaDistortion` sealed interface는 `None`과 `Wave(strength)`를 제공한다.
- `CaptchaFont` data class와 `CaptchaFontStyle` enum을 명세대로 추가하되 bundled binary는
  포함하지 않는다.
- `captchaGenerator { ... }` factory와 builder를 제공한다.
- 모든 모듈의 `data class`는 `Serializable`을 구현하고 `serialVersionUID`를 정의한다.
- detekt/Kover가 루트 규칙을 상속하는지, 아니면 명시적인 모듈 연결이 필요한지 확인한 뒤
  `:detekt`와 coverage aggregation에 의존한다.

공개 KDoc은 영어로 작성하고 다음을 명시해야 한다.

- CAPTCHA는 완전한 bot-defense system이 아니라 가벼운 friction이다.
- `expiresAt`은 advisory 값이다.
- `CaptchaChallenge`는 `ImmutableImage`를 담기 때문에 serializable하지 않다.
- 기본 charset은 uppercase-only이며 혼동되는 `I`, `O`, `0`, `1`을 제외한다.

### 작업 3: 렌더링 구현

- `ImmutableImage`를 통해 Java2D/scrimage를 사용한다.
- 보안성이 있는 기본 난수로 텍스트를 생성한다.
- 동기 API와 suspend API 모두에서 호출별 `length`를 검증한다.
- 배경과 제한된 회전/흔들림이 적용된 문자를 렌더링하고, 선택적 선/점 noise와 선택적
  bounded wave distortion을 적용한다.
- 기본값으로 `Clock.systemUTC()`를 사용하고 테스트에서는 고정 `Clock`을 지원한다.
- `generateSuspend()`가 `withContext(Dispatchers.Default)`를 사용하며 렌더링 시작 전
  cancellation을 존중하도록 보장한다.

### 작업 4: 테스트 및 리소스 추가

다음을 추가한다.

- `images-captcha/src/test/resources/junit-platform.properties`
- `images-captcha/src/test/resources/logback-test.xml`

테스트 항목:

- option validation이 잘못된 length, charset, image size, font size, expiration, empty
  text color, invalid distortion strength를 거부하는지 확인한다.
- 기본 challenge 텍스트 길이가 옵션 길이와 같은지 확인한다.
- 호출별 `generate(length)`가 requested length를 검증하고 사용하는지 확인한다.
- generated text가 configured charset만 사용하는지 확인한다.
- 생성된 이미지 크기가 옵션과 일치하는지 확인한다.
- fixed clock이 deterministic `expiresAt`을 만드는지 확인한다.
- 기본 generator가 100회 생성에서 서로 다른 텍스트를 만드는지 확인한다.
- generated image가 기존 writer API로 byte encoding되는지 확인한다.
- suspend generation이 valid challenge를 반환하는지 확인한다.
- pre-cancelled coroutine이 rendering 시작 전에 `CancellationException`을 전파하는지 확인한다.
  test seam/counter로 image artifact가 capture되지 않았음을 증명하고, mid-render Java2D
  cancellation을 주장하지 않는다.
- headless 테스트 JVM에서 이미지를 생성할 수 있는지 확인한다.

bluetape4k assertion만 사용한다.

### 작업 5: 문서화

- `images-captcha/README.md`를 추가한다.
- `images-captcha/README.ko.md`를 추가한다.
- 루트 `README.md`와 `README.ko.md`를 갱신한다.
  - module table
  - dependency snippet
  - module README link
- root diagram/chart가 module inventory를 포함한다면 기존 README 의미상 필요한 경우에만
  갱신한다. 그렇지 않으면 visual churn을 피한다.

### 작업 6: CI 및 릴리스 메타데이터

- `.github/workflows/ci.yml`과 nightly workflow를 검사한다. 새 모듈이 자동으로 포함된다는
  inspection evidence가 없으면 workflow를 갱신한다.
- explicit module pattern이 있으면 `.github/workflows/ci.yml`에 `images-captcha` change output,
  `dorny/paths-filter` entry, `test-images-captcha` job, Kover XML upload를 추가한다.
- explicit module block이 있으면 nightly workflow module block에
  `bluetape4k-images-captcha`를 추가한다.
- 어느 쪽이든 workflow coverage evidence를 기록한다.
- workflow YAML 변경 후 `actionlint`를 실행한다.
- BOM 포함은 `bom/build.gradle.kts`를 통해 자동이어야 한다. constraint rule을 읽고
  `./gradlew projects`로 확인한다.

### 작업 7: 검증

다음 순서로 실행한다.

1. `./gradlew projects`
2. 가능한 경우 IDE diagnostic을 실행하고, 불가능하면 fallback을 기록한다.
3. `./gradlew :bluetape4k-images-captcha:test`
4. `./gradlew :bluetape4k-images-captcha:build`
5. `./gradlew :bluetape4k-images-captcha:detekt`
6. Kover aggregation이 새 모듈을 포함하는지 확인하고 사용한 command를 기록한다.
7. `git diff --check`
8. workflow 파일이 변경됐다면 `actionlint`

그 다음 Step 6-R 코드 리뷰를 실행한다.

- 변경 diff에 대한 현재 Codex review를 수행한다.
- 같은 diff에 대한 Claude Code CLI code review artifact를 남긴다.
- PR 생성 전에 모든 P0/P1 finding을 수정한다.

## 3. 커밋 및 PR

- review gate가 통과하면 구현 전에 spec과 plan을 commit한다.
- 유용하다면 implementation, docs, tests, lessons를 별도 commit으로 나눈다.
- `docs/lessons/YYYY-MM-DD-issue-4-images-captcha.md`를 추가하거나 갱신한다.
- `develop` 대상 PR을 생성한다.
- PR body에는 `Fixes #4`, 수행 작업, validation, not-run note를 포함한다.
- PR 생성 후 자동 merge하지 않는다.

## 4. 롤백

이 모듈은 추가형 변경이다. 롤백 절차는 다음과 같다.

- `images-captcha/` 제거
- `settings.gradle.kts` registration 제거
- README/workflow update revert

기존 런타임 동작은 변경되지 않아야 한다.

## 5. 위험

- AWT rendering은 font/platform에 따라 조금 달라질 수 있다. test는 exact pixel이 아니라
  structural property를 assert해야 한다.
- `ImmutableImage`는 serializable로 취급하지 않는다. application은 persistence 전에 image를
  encode해야 한다.
- CAPTCHA security claim을 과도하게 넓히면 사용자를 오도할 수 있다. 문서는 범위를 challenge
  image generation으로 유지해야 한다.
- Wave distortion은 edge artifact를 만들 수 있다. strength를 제한하고 output dimension과
  encodability를 test한다.
