# Issue 177 CI Snapshot 검토

## 범위

- PR: #178
- 이슈: #177
- 검토 파일:
  - `.github/workflows/ci.yml`
  - `.github/workflows/Examples.yml`
  - `.github/workflows/nightly-tests.yml`
  - `settings.gradle.kts`
  - `build.gradle.kts`
  - `docs/lessons/2026-06-06-issue-177-central-snapshot-auth.md`

## 발견 사항

- P0: 0
- P1: 0
- P2: 0
- P3: 0

## 근거

- 실패한 PR run 27047331885는 `CI / Build (compile only)`가
  `CENTRAL_USERNAME` 또는 `CENTRAL_PASSWORD`를 받지 못했고, 이후 Central snapshot
  metadata HTTP 403으로 실패했음을 보여줬다.
- 실패한 PR run 27047331894는 `Examples / spring-boot-image-api`가 Central secret은
  받았지만 sibling example job은 통과한 parallel Examples matrix 실행에서 여전히
  실패했음을 보여줬다.
- 실패한 PR run 27047765648은 `Examples / ktor-image-api`가 반복된 Central snapshot
  metadata connect timeout 뒤 시간 초과됐고, 같은 명령은 로컬에서 통과했음을 보여줬다.
- 실패한 PR run 27048363331과 27048363343은 workflow command가
  `--refresh-dependencies`를 강제하는 동안 Central credential 전달만으로는 충분하지
  않았음을 보여줬다. 각 retry는 Central snapshot metadata를 다시 조회했고 HTTP 403
  실패를 반복했다.
- Examples와 Nightly의 Gradle-heavy job timeout을 늘려 다섯 번의 retry loop가 뒤쪽
  시도를 실행하기 전에 취소되지 않도록 했다.
- 변경 모듈은 이제 하루 동안 cache되고, CI, Examples, Nightly는 routine Gradle command에
  더 이상 `--refresh-dependencies`를 전달하지 않는다.
- `actionlint .github/workflows/ci.yml .github/workflows/Examples.yml .github/workflows/nightly-tests.yml`: PASS
- `git diff --check`: PASS
- `rg -n --fixed-strings "\\'" .github/workflows`: no matches
- `./gradlew :basic-processing:test --no-configuration-cache --no-daemon`: PASS
- `./gradlew :spring-boot-image-api:test --no-configuration-cache --no-daemon`: PASS
- `./gradlew :ktor-image-api:test --no-configuration-cache --no-daemon`: PASS
- `./gradlew build -x test --max-workers=1 --no-configuration-cache`: PASS

## 판정

게이트는 P0=0, P1=0으로 통과한다. 변경은 workflow reliability와 optional Central
snapshot repository credential에 한정된다. Merge는 updated commit의 fresh PR CI와
Examples check를 기다려야 한다.
