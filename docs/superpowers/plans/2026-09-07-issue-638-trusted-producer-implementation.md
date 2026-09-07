# Issue #638 PaddleOCR Trusted Producer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** 고정된 PaddleOCR/PaddleX/source/model 입력으로 native `linux/amd64` image를 만들고, 동일 platform digest의 SBOM·provenance·public evidence를 fail-closed 방식으로 검증하는 수동 GitHub Actions producer를 구축한다.

**Architecture:** stdlib-only Python CLI를 contract, filesystem/OCI, GitHub/GHCR read-back 책임으로 분리하고 `scripts/research/paddle_ocr_producer.py`를 얇은 진입점으로 유지한다. `.github/workflows/paddleocr-producer.yml`은 validation, source reproduction, unprivileged build, private staging, attestation, promotion, public verification, finalization을 job별 최소 권한으로 분리하며 모든 handoff를 immutable digest와 run/attempt identity로 결합한다.

**Tech Stack:** Python 3.13 GitHub runner와 현재 로컬 Python 3.9 호환 stdlib, JSON/JCS, OCI Image Layout, Docker Buildx, GHCR, ORAS `v1.3.4`, GitHub Artifact Attestations, GitHub Actions, `unittest`, `actionlint`, gitleaks.

---

## 계획 메타데이터

| 항목 | 값 |
|---|---|
| issue | [#638](https://github.com/bluetape4k/bluetape4k-image/issues/638) |
| milestone | `1.1.0` |
| 승인된 spec | [`2026-09-07-issue-638-trusted-producer-design.md`](../specs/2026-09-07-issue-638-trusted-producer-design.md) |
| plan review | [`2026-09-07-issue-638-trusted-producer-plan-review.md`](../reviews/2026-09-07-issue-638-trusted-producer-plan-review.md) |
| 기준 branch/commit | `develop` / `d6d6c2f30ed18e666090e5e39164b890e1fea4df` |
| 실행 branch | 계획 승인 후 `feat/issue-638-trusted-producer` worktree를 `origin/develop`에서 새로 만든다. |
| Python boundary | publishable package와 dependency를 추가하지 않는다. `scripts/research` 도구는 stdlib-only다. |
| 외부 side effect | PR, workflow dispatch, 최초 GHCR push, visibility 변경은 구현·로컬 검증과 분리한다. |
| stop condition | model legal inventory, immutable hash, exact action pin, environment/permission 또는 read-back 계약이 불완전하면 publish를 실행하지 않는다. |

## 파일 구조

| 경로 | 책임 |
|---|---|
| `scripts/research/paddle_ocr_producer.py` | argparse subcommand와 canonical one-line result/exit code 진입점 |
| `scripts/research/paddle_ocr_producer_lib/contracts.py` | bounded JSON, duplicate/unknown field, JCS, digest/tag/schema 검증 |
| `scripts/research/paddle_ocr_producer_lib/filesystem.py` | no-follow file/archive, canonical tree hash, root-pinned materialization |
| `scripts/research/paddle_ocr_producer_lib/registry.py` | bounded GitHub/GHCR pagination, retry 분류, read-back |
| `scripts/research/paddle_ocr_producer_lib/lifecycle.py` | attempt, cleanup, reconciliation, revocation 상태 전이 |
| `scripts/research/paddle_ocr_producer_lib/evidence.py` | OCI handoff와 exact 11-file evidence 생성·검증 |
| `scripts/research/test_paddle_ocr_producer*.py` | contract, registry, evidence success/failure/boundary tests |
| `docker/paddleocr/*.schema.json` | spec에 정의된 12개 strict JSON document contract |
| `docker/paddleocr/producer-input.lock.json` | source/base/package/model immutable input |
| `docker/paddleocr/requirements.cpu.lock.txt` | hash-enforced offline wheelhouse lock |
| `docker/paddleocr/trust-policy.json` | repository/workflow/ref/actor/runner/OIDC/host allowlist |
| `docker/paddleocr/legal-inventory.json` | source/base/package/model license와 NOTICE hash |
| `docker/paddleocr/revocations.json` | hash-chained append-only deny source 초기 document |
| `docker/paddleocr/Dockerfile`, `docker/paddleocr/service.py`, `docker/paddleocr/bin/bluetape4k-paddleocr-service` | credential-free baked-model CPU image와 fixed wrapper |
| `docker/paddleocr/test_service.py` | first-use download, override, readiness, bounds tests |
| `.github/workflows/paddleocr-producer.yml` | `PRODUCE`, `RECONCILE` 수동 workflow |
| `.github/scripts/test-paddleocr-producer-workflow.py` | job graph, permissions, pins, environment, gate contract |
| `.github/CODEOWNERS` | workflow, input lock, policy와 producer script의 owner 보호 |
| `.github/workflows/ci.yml` | producer path selection과 stdlib test job |
| `docs/superpowers/runbooks/2026-09-07-issue-638-paddleocr-producer.md` | 운영·public verifier·reconcile·quarantine runbook |
| `README.md`, `README.ko.md`, `CHANGELOG.md`, `WIP.md` | evidence producer와 adoption 경계, 1.1.0 상태 |

## CLI contract

`--help`만 human-readable stdout/exit 0이다. `verify-attempt`는 승인 spec의
`producer-result.schema.json` exact top-level contract를 stdout 한 줄로 쓴다. 그 밖의
operational subcommand는 성공과 실패 모두
`{"schemaVersion":1,"command":"<name>","status":"<stable status>","data":{...}}`
형태의 JCS 한 줄을 stdout에 쓰고 stable exit table을 사용한다. file output은 temp file을
같은 parent에 쓴 뒤 no-replace rename하며 stdout `data`에 path와 prefix-free SHA-256을
기록한다. 모든 network 명령은 내부 monotonic absolute deadline을 사용하므로 GNU
`timeout`에 의존하지 않는다.

| subcommand | 성공 `data`의 필수 key | mutation | absolute deadline / 구현·test task |
|---|---|---|---|
| `validate-inputs` | `inputLockSha256`, `legalInventorySha256`, `trustPolicySha256` | 없음 | local / 1, 2 |
| `resolve-inputs` | `candidatePath`, `candidateSha256`, `baseDigests`, `operationReceipts` | candidate/temp만 | 15분 / 2 |
| `accept-resolved-inputs` | `inputLockPath`, `requirementsPath`, `legalPath`, 각 SHA | tracked local files | local / 2 |
| `stage-inputs` | `artifactPath`, `artifactSha256`, `modelTreeDigests`, `modelPairSha256` | run temp/artifact | 90분 / 2, 7 |
| `input-value --field sourceDateEpoch` | `sourceDateEpoch`, `derivedPackageId`, `inputLockSha256` | 없음 | local / 2 |
| `verify-source-reproducibility` | `wheelhousePath`, `wheelhouseSha256`, `sourceDateEpoch` | run artifact | 90분 / 2, 7 |
| `verify-dockerfile` | `baseReference`, `baseDigests`, `entrypoint`, `command` | 없음 | local / 5 |
| `finalize`, `merge-cleanup`, `reconcile` | `attemptSha256`, `cleanupSha256`, `reconciliationSha256`, `resultSha256` | run documents | 15분 / 3, 9 |
| `verify-attempt` | **generic envelope 없음**; exact `schemaVersion`, `attemptId`, `producerStatus`, `lastCompletedStage`, `mapped609Status`, image/evidence digest, 세 document SHA, `errorCode`, `errorMessage` | 없음 | local / 3, 4, 9, 10 |
| `append-revocation`, `verify-emergency-receipt` | `revocationsSha256`, `commitSha`, `receiptSha256` | candidate 또는 tracked revocation | 15분 / 3, 9 |
| `readback-packages`, `readback-public-gate` | `staging`, `release`, `evidence`, `retryReceipts` | 없음 | 60초/operation / 4, 8, 12 |
| `verify-public-evidence` | `manifestDigest`, `fileManifestSha256`, `provenanceVerified`, `sbomVerified` | anonymous temp만 | manifest 60초, 전체 10분 / 4, 8, 10 |
| `snapshot-workflow-runs`, `select-dispatched-run` | `runIds` 또는 `databaseId`, `runAttempt`, `headSha`, `workflowPath` | bounded local output | 60초/operation / 4, 9, 12 |
| `wait-workflow-job`, `wait-workflow-run` | `runId`, `runAttempt`, `status`, `conclusion`, `retryReceipts` | 없음 | 전체 7200초, call 60초 / 9, 12 |
| `readback-workflow-run`, `inspect-live-producer-settings` | exact head/workflow/ruleset/environment/actor fields | 없음 | 60초/operation / 9, 10, 12 |
| `readback-retention` | `runDays`, `artifactDays`, `acceptedDays`, `failedStagingDays` | 없음 | 60초/operation / 9, 10, 12 |
| `write-step-summary` | `summarySha256`, `documentDigests` | `$GITHUB_STEP_SUMMARY` | local / 6, 9 |
| `prepare-reconcile-inputs` | six exact resume fields와 `priorDocumentHashes` | bounded local output | 60초/operation / 9, 10, 12 |
| `open-or-link-incident` | `incidentUrl`, `statusChangedAt` | 승인된 issue/comment | 60초/operation / 9, 10, 12 |
| `acknowledge-incident`, `close-incident`, `readback-incident` | incident URL, ordered timestamps, closure booleans | reconciliation/승인된 comment | 60초/operation / 9, 10, 12 |
| `plan-known-good-rollback` | current/known-good digest, revocation/notification hashes | dry-run output만 | 60초/operation / 9, 10, 12 |
| `execute-known-good-rollback`, `readback-known-good-rollback` | package ID, before/after digest/visibility/tag, approval marker hash | 별도 승인된 GHCR mutation/read-back | 60초/operation / 9, 10, 12 |

각 행은 missing/unknown/conflicting argument, success payload의 exact keys, stable failure
exit, output atomicity, mutation-before-validation 거부와 deadline fixture를 RED/GREEN으로
검증한다. 외부 mutation subcommand는 fresh approval marker가 없으면 network call 전에
실패한다.

## 의존 순서

```text
strict primitives
  -> immutable input/legal lock
  -> lifecycle/cleanup/revocation
  -> OCI/registry/evidence verifier
  -> service image contract
  -> manual producer workflow + credential-free PR CI
  -> build/private staging
  -> attestation/promotion/public evidence
  -> reconciliation/quarantine/finalizer
  -> docs + exact-head verification
```

### Task 1: stdlib strict contract kernel을 test-first로 추가

**Files:**
- Create: `scripts/research/paddle_ocr_producer_lib/__init__.py`
- Create: `scripts/research/paddle_ocr_producer_lib/contracts.py`
- Create: `scripts/research/paddle_ocr_producer_lib/filesystem.py`
- Create: `scripts/research/test_paddle_ocr_producer.py`

새로 만들거나 수정하는 모든 Python module/test의 첫 executable line은
`from __future__ import annotations`로 고정해 Python 3.9에서 PEP 604 annotation을
평가하지 않는다. Python 3.9/3.13 import smoke가 producer lib, CLI, service와 smoke module을
각각 import한 뒤 test를 실행한다.

- [x] **Step 1: duplicate key, size, unknown field, JCS와 attempt identity RED test를 작성한다.**

```python
class StrictContractTest(unittest.TestCase):
    def test_duplicate_key_fails_closed(self) -> None:
        with self.assertRaisesRegex(ProducerValidationError, "duplicate JSON key"):
            load_json_bytes(b'{"schemaVersion":1,"schemaVersion":1}', 1024)

    def test_jcs_orders_keys_and_rejects_float(self) -> None:
        self.assertEqual(jcs_bytes({"b": 2, "a": 1}), b'{"a":1,"b":2}')
        with self.assertRaisesRegex(ProducerValidationError, "integer"):
            jcs_bytes({"a": 1.0})

    def test_attempt_identity_is_derived(self) -> None:
        identity = AttemptIdentity.from_run(1234, 2)
        self.assertEqual(identity.attempt_id, "1234.2")
        self.assertEqual(identity.image_tag, "image-1234.2")
        self.assertEqual(identity.evidence_tag, "evidence-1234.2")
```

- [x] **Step 2: RED를 확인한다.**

Run: `python3 scripts/research/test_paddle_ocr_producer.py -v`

Expected: module 또는 symbol 미구현으로 실패한다.

- [x] **Step 3: bounded loader, exact object, digest, tag와 JCS primitive를 구현한다.**

```python
@dataclass(frozen=True)
class AttemptIdentity:
    run_id: int
    run_attempt: int

    @classmethod
    def from_run(cls, run_id: int, run_attempt: int) -> "AttemptIdentity":
        if type(run_id) is not int or run_id <= 0:
            raise ProducerValidationError("runId must be a positive integer")
        if type(run_attempt) is not int or run_attempt <= 0:
            raise ProducerValidationError("runAttempt must be a positive integer")
        return cls(run_id, run_attempt)

    @property
    def attempt_id(self) -> str:
        return f"{self.run_id}.{self.run_attempt}"

    @property
    def image_tag(self) -> str:
        return f"image-{self.attempt_id}"

    @property
    def evidence_tag(self) -> str:
        return f"evidence-{self.attempt_id}"
```

`load_json_bytes`는 UTF-8, duplicate key, root object, byte limit, maximum depth/entries,
BOM, trailing data와 malformed number를 검사한다.
`exact_object`는 missing/unexpected key를 정렬된 오류로 반환한다. `jcs_bytes`는
`null`, boolean, integer, string, list, object만 허용하고 float/NaN/Infinity를 거부한다.
JSONL loader는 raw bytes와 LF 경계를 보존하며 BOM, CR, blank/trailing line, duplicate
envelope와 signed content의 재정렬/재직렬화를 거부한다. byte/depth/entry 경계 바로
아래/위를 test하고 secret/path fixture가 validation error에 포함되지 않음을 확인한다.

- [x] **Step 4: 새 kernel과 기존 regression을 GREEN으로 만든다.**

```bash
python3 scripts/research/test_paddle_ocr_producer.py -v
python3 scripts/research/test_paddle_ocr_receipt.py
python3 scripts/research/test_paddle_ocr_smoke.py
```

Expected: 새 test와 기존 `22 + 23` tests가 모두 `OK`다.

- [x] **Step 5: Lore commit을 만든다.**

```bash
git add scripts/research/paddle_ocr_producer_lib scripts/research/test_paddle_ocr_producer.py
git commit -m 'feat: producer 입력을 fail-closed로 해석한다' \
  -m 'Constraint: producer document는 stdlib-only bounded parser와 canonical hash를 사용한다' \
  -m 'Confidence: high' -m 'Scope-risk: narrow' \
  -m 'Tested: python3 scripts/research/test_paddle_ocr_producer.py -v'
```

### Task 2: source, package, model과 legal input을 실제 bytes에 고정

**Files:**
- Create: `docker/paddleocr/producer-input.schema.json`
- Create: `docker/paddleocr/producer-input.lock.json`
- Create: `docker/paddleocr/requirements.cpu.lock.txt`
- Create: `docker/paddleocr/trust-policy.json`
- Create: `docker/paddleocr/legal-inventory.json`
- Create: `scripts/research/paddle_ocr_producer.py`
- Modify: `scripts/research/paddle_ocr_producer_lib/contracts.py`
- Modify: `scripts/research/paddle_ocr_producer_lib/filesystem.py`
- Modify: `scripts/research/test_paddle_ocr_producer.py`

- [x] **Step 1: incomplete legal/model/source lock RED test를 추가한다.**

```python
def test_input_lock_requires_two_complete_model_roles(self) -> None:
    lock = valid_input_lock()
    lock["models"] = [lock["models"][0]]
    with self.assertRaisesRegex(ProducerValidationError, "detector and recognizer"):
        validate_input_lock(lock)

def test_model_archive_mismatch_is_blocked_input(self) -> None:
    with self.assertRaisesRegex(ProducerValidationError, "archive sha256 differs"):
        verify_regular_file(self.model_archive, expected_bytes=9, expected_sha256="0" * 64)

def test_archive_preflight_rejects_traversal_link_and_bomb(self) -> None:
    for fixture in ("absolute", "parent", "symlink", "hardlink", "special",
                    "sparse", "nested", "duplicate", "ratio-101"):
        with self.subTest(fixture=fixture), self.assertRaises(ProducerValidationError):
            preflight_archive(archive_fixture(fixture), ARCHIVE_LIMITS)

def test_allowlisted_fetcher_rejects_private_and_rebound_address(self) -> None:
    with self.assertRaisesRegex(ProducerValidationError, "public allowlisted address"):
        fetch_to_regular_file(source("https://allowed.example/input"),
                              transport=rebinds_to("169.254.169.254"))

def test_allowlisted_fetcher_rejects_every_ssrf_bypass(self) -> None:
    for fixture in ("http", "non-443", "userinfo", "ip-literal", "empty-host",
                    "non-normalized-host", "redirect-scheme", "redirect-port",
                    "redirect-userinfo", "redirect-ip-literal", "redirect-host-mismatch",
                    "redirect-hop-4", "redirect-dns-rebind", "proxy-environment"):
        with self.subTest(fixture=fixture), self.assertRaises(ProducerValidationError):
            fetch_to_regular_file(source_fixture(fixture), transport=transport_fixture(fixture))
```

- [x] **Step 2: RED를 확인한다.**

Run: `python3 scripts/research/test_paddle_ocr_producer.py -v`

Expected: input lock API 미구현으로 실패한다.

- [x] **Step 3: `validate-inputs`, `resolve-inputs`, `accept-resolved-inputs`, `stage-inputs`, `input-value`, `verify-source-reproducibility`를 구현한다.**

```python
def main(argv: Sequence[str] | None = None) -> int:
    outcome = ProducerOutcome.failed("HANDLER_NOT_STARTED")
    cleanup = CleanupOutcome.not_started()
    with installed_signal_handlers(sigint=ProducerCancelled, sigterm=ProducerInterrupted):
        try:
            parser = build_parser(error_handler=raise_usage_error)
            if help_requested(argv):
                parser.print_help()
                return 0
            args = parser.parse_args(argv)
            outcome = ProducerOutcome.success(args.handler(args))
        except ProducerUsageError as error:
            outcome = ProducerOutcome.schema_invalid(redacted_error(error))
        except ProducerBlockedError as error:
            outcome = ProducerOutcome.from_blocked(error)
        except (ProducerSchemaError, json.JSONDecodeError) as error:
            outcome = ProducerOutcome.schema_invalid(redacted_error(error))
        except ProducerLegalError as error:
            outcome = ProducerOutcome.legal_blocked(redacted_error(error))
        except ProducerRejectedError as error:
            outcome = ProducerOutcome.rejected(redacted_error(error))
        except ProducerRemoteExhausted as error:
            outcome = ProducerOutcome.remote_exhausted(error.stage, redacted_error(error))
        except (TimeoutError, ProducerInterrupted) as error:
            outcome = ProducerOutcome.interrupted(redacted_error(error))
        except (KeyboardInterrupt, ProducerCancelled, ChildProcessError) as error:
            outcome = ProducerOutcome.cancelled(redacted_error(error))
        except OSError as error:
            outcome = ProducerOutcome.failed(redacted_error(error))
        except Exception as error:
            outcome = ProducerOutcome.failed(redacted_error(error))
        finally:
            cleanup = cleanup_run_owned_resources_no_raise(outcome)
    result = finalize_outcome(outcome, cleanup)
    sys.stdout.buffer.write(jcs_bytes(result.document) + b"\n")
    return result.exit_code
```

stdout은 canonical result 한 줄만 사용한다. operational event, retry와 failure는 stdlib
`logging`으로 stderr에 남기며 URL query, header, token, model content를 기록하지 않는다.
source/model, GitHub API, GHCR registry와 ORAS bootstrap이 모두 사용하는 공통 allowlisted
fetcher는 proxy 환경을 제거하고 HTTPS/443, non-empty host allowlist,
최대 3 redirect hop을 강제한다. 매 hop마다 DNS를 다시 해석해 public IP에 고정하며
private, loopback, link-local, multicast와 cloud metadata 대역을 거부한다. 각 input lock의
개별 byte limit과 전체 staging 1 GiB를 streaming 중 강제하고 SHA-256을 동시에 계산한다.
connect/read timeout은 각각 30초, operation별 시도는 최대 3회, retry는 timeout,
connection reset과 HTTP `408|429|5xx`에만 2/4초 backoff로 적용한다. digest/host/redirect/
일반 `4xx`/archive/legal mismatch와 cancellation은 재시도하지 않는다. injected transport,
clock과 sleeper로 timeout, 408, reset, exhaustion, permanent failure, cancellation 및
`operationId`별 retry receipt를 실제 sleep/network 없이 검증한다.

archive extractor는 preflight에서 총 expanded 1 GiB, 10,000 files, depth 16,
UTF-8 path 240 bytes, compression ratio 100:1, 10분 absolute deadline을 강제하고 absolute/
parent traversal, symlink, hardlink, device/special/sparse file, nested archive와 normalized
duplicate를 거부한다. write는 staging root dirfd, no-follow와 regular-file-only 계약을
사용하며 실패·중단 시 partial file과 directory를 제거한다. boundary 바로 아래/위와
slow stream, cleanup failure를 table-driven test로 고정한다.

CLI는 `ProducerBlockedError`뿐 아니라 schema/JSON decode, `OSError`, `TimeoutError`,
`KeyboardInterrupt`, SIGTERM과 child-process cancellation을 stable status/error/exit/result로
변환하고 traceback, host path, secret을 출력하지 않는다. secret/query/header/model fixture를
stdout/stderr와 생성 artifact에 주입해 redaction을 검증한다.
`installed_signal_handlers`는 SIGINT를 cancellation, SIGTERM을 interruption으로 변환한다.
`cleanup_run_owned_resources_no_raise`는 process-group/FD/temp/auth 정리를 순서대로 시도하고
cleanup 자체의 모든 예외를 receipt로 흡수해 원래 outcome을 보존하며
`cleanupVerified=false`를 반환한다. subprocess가 실제 SIGINT/SIGTERM을 받은 test와
cleanup 단계별 injected failure test가 canonical result/exit 31/32와 원래 failure 보존을
검증한다.
`argparse`의 `error()`는 process exit 대신 `ProducerUsageError`를 던진다. `--help`만
human-readable stdout/exit 0이며 missing/unknown/conflicting 실행 인자는 canonical
`BLOCKED_INPUT/SCHEMA_INVALID/40` 한 줄을 반환한다. failure subclass mapping은
`BLOCKED_INPUT/10`, `BLOCKED_LEGAL_INVENTORY/11`, stage-aware
`PARTIAL_PUBLICATION/20|FAILED/30`, `REJECTED/22`, `CANCELLED/31`, `INTERRUPTED/32`,
`SCHEMA_INVALID/40`의 유일한 error table과 연결한다. digest/tag/malformed fragment는
`ProducerRejectedError`, legal gap은 `ProducerLegalError`, retry exhaustion은
`ProducerRemoteExhausted(stage, operation_id, cause)`로 분리한다. stage와 operation ID는
exception이 보유하며 argparse namespace의 optional field를 읽지 않는다. 모든 network
subcommand/error/stage 조합을 table-driven test로 검증한다.

- [x] **Step 4: 통제된 resolution을 실행해 tracked lock을 생성한다.**

```bash
python3 scripts/research/paddle_ocr_producer.py resolve-inputs \
  --target-platform linux/amd64 --python-version 3.10 \
  --base-image-candidate docker.io/library/python:3.10-slim \
  --paddleocr-commit b03f46425e8ff4442b268ce449e3eef758146cd4 \
  --paddlex-commit ffb64904d23708863ff5b8da312a5cbd52a7f462 \
  --paddle-commit 659ebc95069b4a5f34cf8102f0d0474fad5696c0 \
  --detector PP-OCRv5_mobile_det --recognizer PP-OCRv5_mobile_rec \
  --output-root build/paddleocr-resolution
python3 scripts/research/paddle_ocr_producer.py accept-resolved-inputs \
  --resolution-root build/paddleocr-resolution \
  --input-lock docker/paddleocr/producer-input.lock.json \
  --requirements-lock docker/paddleocr/requirements.cpu.lock.txt \
  --legal-inventory docker/paddleocr/legal-inventory.json
```

Expected: archive/wheel/model/license/NOTICE URL, bytes, SHA-256, source revision과 model
role tree/pair hash가 채워진다. model 재배포 근거가 없으면 exit `11`
`BLOCKED_LEGAL_INVENTORY`로 끝나며 publish mode를 실행하지 않는다.

- [x] **Step 5: source build를 두 번 수행해 wheelhouse equality를 검증한다.**

```bash
python3 scripts/research/paddle_ocr_producer.py input-value \
  --lock docker/paddleocr/producer-input.lock.json \
  --field sourceDateEpoch \
  --output build/paddleocr-source-date-epoch.json
SOURCE_DATE_EPOCH=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["data"]["sourceDateEpoch"])' \
  build/paddleocr-source-date-epoch.json)
export SOURCE_DATE_EPOCH
python3 scripts/research/paddle_ocr_producer.py verify-source-reproducibility \
  --input-lock docker/paddleocr/producer-input.lock.json \
  --work-root build/paddleocr-source-repro \
  --output-wheelhouse build/paddleocr-wheelhouse
python3 scripts/research/paddle_ocr_producer.py validate-inputs \
  --lock docker/paddleocr/producer-input.lock.json \
  --policy docker/paddleocr/trust-policy.json \
  --legal docker/paddleocr/legal-inventory.json
```

Expected: 두 source wheel filename/bytes/SHA와 wheelhouse가 exact-match한다. mutable URL,
sdist, source build in final image와 unexpected platform tag는 거부된다.
`input-value --field sourceDateEpoch`은 root에 새 key를 만들지 않고 unique derived source package의 기존
`buildToolchain.sourceDateEpoch` positive integer에서만 읽는다. resolution candidate는
exact source commit timestamp와 고정 build backend/toolchain으로 이 값을 산출하고
accept 단계가 package entry에 보존한다. missing/duplicate/non-integer epoch와 source
revision 불일치를 거부하는 fixture를 추가한다. base candidate tag는 resolution에서만
허용하며 accepted lock에는 resolved index/platform/config digest와 digest-pinned
`baseImage.reference`만 기록한다.
full-block fixture는 command가 쓴 nested `data.sourceDateEpoch`을 읽어
`SOURCE_DATE_EPOCH` 환경 변수로 export하는 단계까지 실행하고 generic `data.value`나
top-level key로 drift하면 실패한다.

- [x] **Step 6: resolved input commit을 만든다.**

```bash
git add docker/paddleocr scripts/research
git commit -m 'feat: PaddleOCR producer 입력을 실제 bytes에 고정한다' \
  -m 'Constraint: model 재배포 근거와 immutable digest가 없으면 publish를 차단한다' \
  -m 'Confidence: high' -m 'Scope-risk: broad' \
  -m 'Tested: validate-inputs와 source reproducibility, producer unit tests'
```

### Task 3: attempt, cleanup, reconciliation과 revocation lifecycle을 고정

**Files:**
- Create: `docker/paddleocr/producer-attempt.schema.json`
- Create: `docker/paddleocr/cleanup-fragment.schema.json`
- Create: `docker/paddleocr/cleanup.schema.json`
- Create: `docker/paddleocr/producer-result.schema.json`
- Create: `docker/paddleocr/producer-reconciliation.schema.json`
- Create: `docker/paddleocr/revocations.schema.json`
- Create: `docker/paddleocr/revocations.json`
- Create: `docker/paddleocr/emergency-deny-receipt.schema.json`
- Create: `scripts/research/paddle_ocr_producer_lib/lifecycle.py`
- Modify: `scripts/research/paddle_ocr_producer.py`
- Modify: `scripts/research/test_paddle_ocr_producer.py`

- [x] **Step 1: exhaustive state/last-stage, cleanup merge와 hash-chain RED test를 작성한다.**

```python
def test_producer_pass_requires_all_readbacks(self) -> None:
    document = valid_reconciliation("PRODUCER_PASS")
    document["visibilityReadBack"] = False
    with self.assertRaisesRegex(ProducerValidationError, "visibilityReadBack"):
        validate_reconciliation(document)

def test_revocation_chain_rejects_wrong_previous_hash(self) -> None:
    previous = initial_revocations()
    candidate = append_revocation(previous, revoked_digest())
    candidate["previousDocumentSha256"] = "f" * 64
    with self.assertRaisesRegex(ProducerValidationError, "previousDocumentSha256"):
        validate_revocation_successor(previous, candidate)

def test_incident_timestamps_follow_status_group_and_order(self) -> None:
    for case in incident_lifecycle_cases():
        with self.subTest(case=case.name):
            assert_incident_contract(case.document, case.expected_valid)
```

- [x] **Step 2: RED를 확인한다.**

Run: `python3 scripts/research/test_paddle_ocr_producer.py -v`

Expected: lifecycle API 미구현으로 실패한다.

- [x] **Step 3: transition table과 status별 invariant를 구현한다.**

```python
LAST_COMPLETED_STAGES = ("NONE", "STAGING", "EVIDENCE", "RELEASE", "PUBLIC_EVIDENCE")

@dataclass(frozen=True)
class ErrorContract:
    producer_status: str
    error_code: Optional[str]
    exit_code: Optional[int]
    mapped_609_status: str
    allowed_stages: tuple[str, ...]

STATUS_CONTRACT = {
    "VALIDATING": ErrorContract("VALIDATING", None, None, "PENDING", ("NONE",)),
    "BLOCKED_INPUT": ErrorContract("BLOCKED_INPUT", "BLOCKED_INPUT", 10, "BLOCKED", ("NONE",)),
    "BLOCKED_LEGAL_INVENTORY": ErrorContract("BLOCKED_LEGAL_INVENTORY", "BLOCKED_LEGAL_INVENTORY", 11, "BLOCKED", ("NONE",)),
    "BUILDING": ErrorContract("BUILDING", None, None, "PENDING", ("NONE",)),
    "PUBLISHED_UNVERIFIED": ErrorContract("PUBLISHED_UNVERIFIED", "PARTIAL_PUBLICATION", 20, "PENDING", ("STAGING",)),
    "PROMOTING": ErrorContract("PROMOTING", "PARTIAL_PUBLICATION", 20, "PENDING", ("EVIDENCE",)),
    "RELEASE_UNVERIFIED": ErrorContract("RELEASE_UNVERIFIED", "PARTIAL_PUBLICATION", 20, "PENDING", ("RELEASE", "PUBLIC_EVIDENCE")),
    "QUARANTINE_PENDING": ErrorContract("QUARANTINE_PENDING", "PARTIAL_PUBLICATION", 20, "BLOCKED", ("PUBLIC_EVIDENCE",)),
    "QUARANTINED": ErrorContract("QUARANTINED", "QUARANTINED", 21, "BLOCKED", ("PUBLIC_EVIDENCE",)),
    "PRODUCER_PASS": ErrorContract("PRODUCER_PASS", "NONE", 0, "PENDING", ("PUBLIC_EVIDENCE",)),
    "REJECTED": ErrorContract("REJECTED", "REJECTED", 22, "REJECTED", LAST_COMPLETED_STAGES),
    "REVOKED": ErrorContract("REVOKED", "REVOKED", 23, "REJECTED", LAST_COMPLETED_STAGES),
    "FAILED": ErrorContract("FAILED", "FAILED", 30, "BLOCKED", LAST_COMPLETED_STAGES),
    "CANCELLED": ErrorContract("CANCELLED", "CANCELLED", 31, "BLOCKED", LAST_COMPLETED_STAGES),
    "INTERRUPTED": ErrorContract("INTERRUPTED", "INTERRUPTED", 32, "BLOCKED", LAST_COMPLETED_STAGES),
}
SCHEMA_ERROR_CONTRACT = ErrorContract("BLOCKED_INPUT", "SCHEMA_INVALID", 40, "BLOCKED", ("NONE",))
```

`finalize`, `merge-cleanup`, `reconcile`, `verify-attempt`, `append-revocation`,
`verify-emergency-receipt`를 연결한다. 위 표를 status/error/exit/mapped609Status/stage의
유일한 source로 사용하고 다섯 stage별 evidence/reconciliation/result nullability 표도
같은 정의에서 schema와 validator로 생성한다. 모든 상태·stage와 invalid schema를
table-driven test로 순회한다. hard runner loss로 started job fragment가 없으면
`INTERRUPTED`/32와 `cleanupVerified=false`이며 receipt를 위조하지 않는다. malformed 또는
duplicate fragment, remote release/evidence가 있는데 staging이 없는 계약 위반만
`REJECTED`/22다. 두 분기를 독립 negative test로 고정한다.
`verify-attempt`는 attempt/evidence/reconciliation/cleanup/ledger/revocations를 no-follow로
열어 identity, stage, digest와 companion hash를 재계산하고 generic envelope 없이
`producer-result.schema.json`의 exact 12개 top-level key만 JCS 한 줄로 반환한다. success,
failure, missing/extra key와 cross-document mismatch fixture를 모두 검증한다.

각 job cleanup은 `if: always()`인 마지막 step에서 child process group을 먼저 종료하고,
open FD를 닫은 뒤 run-owned temp directory, Docker helper/config와 auth/credential file을
삭제하고 fragment를 업로드한다. `KeyboardInterrupt`, timeout, SIGTERM, hanging child와
cleanup failure fixture는 원래 failure status가 유지되고 `cleanupVerified=false`인지
검증한다. finalizer ordering은 cleanup fragment upload -> strict aggregate -> terminal
result이며 injected subprocess/clock으로 결정적으로 시험한다.
reconciliation의 `incidentUrl`, `ownerAcknowledgedAt`, `closedAt`은 status-group별
required/nullability를 exhaustive table로 강제한다. timestamp는 RFC3339이고
`statusChangedAt <= ownerAcknowledgedAt <= closedAt` 순서다. timeout/비정상 conclusion,
partial publication, quarantine와 terminal failure 각각에 incident 생성·owner 확인·종료
가능 조건을 table-driven test로 고정하며 downstream/denylist/visibility/cleanup read-back이
하나라도 false면 `closedAt`을 허용하지 않는다.

- [x] **Step 4: lifecycle test와 JSON parse를 GREEN으로 만든다.**

```bash
python3 scripts/research/test_paddle_ocr_producer.py -v
for schema in docker/paddleocr/*.schema.json docker/paddleocr/revocations.json; do
  python3 -m json.tool "$schema" >/dev/null
done
```

Expected: lifecycle cases가 `OK`, 모든 JSON이 parse된다.

- [x] **Step 5: lifecycle commit을 만든다.**

```bash
git add docker/paddleocr scripts/research
git commit -m 'feat: producer 상태 전이를 재시작 가능하게 고정한다' \
  -m 'Constraint: terminal attempt는 수정하지 않고 새 RECONCILE attempt로만 재개한다' \
  -m 'Confidence: high' -m 'Scope-risk: moderate' \
  -m 'Tested: lifecycle, cleanup, reconciliation과 revocation negative tests'
```

### Task 4: OCI handoff, registry read-back과 public evidence verifier를 구현

**Files:**
- Create: `docker/paddleocr/oci-handoff.schema.json`
- Create: `docker/paddleocr/evidence-manifest.schema.json`
- Create: `docker/paddleocr/producer-evidence.schema.json`
- Create: `docker/paddleocr/artifact-ledger-fragment.schema.json`
- Create: `scripts/research/paddle_ocr_producer_lib/registry.py`
- Create: `scripts/research/paddle_ocr_producer_lib/evidence.py`
- Create: `scripts/research/test_paddle_ocr_producer_registry.py`
- Create: `scripts/research/test_paddle_ocr_producer_evidence.py`
- Modify: `scripts/research/paddle_ocr_producer.py`
- Modify: `scripts/research/paddle_ocr_producer_lib/filesystem.py`

- [x] **Step 1: pagination, ambiguous tag, foreign layer와 traversal RED test를 작성한다.**

```python
def test_two_matching_versions_are_rejected(self) -> None:
    pages = [[package_version(1, "image-44.2")], [package_version(2, "image-44.2")]]
    with self.assertRaisesRegex(ProducerValidationError, "ambiguous package state"):
        select_exact_version(pages, "image-44.2")

def test_manifest_urls_are_rejected_before_blob_download(self) -> None:
    manifest = evidence_manifest()
    manifest["layers"][0]["urls"] = ["https://example.invalid/blob"]
    with self.assertRaisesRegex(ProducerValidationError, "urls"):
        validate_remote_evidence_manifest(manifest)

def test_materializer_rejects_every_path_escape_before_commit(self) -> None:
    for fixture in ("empty", "dot", "parent", "unallowlisted", "symlink-swap",
                    "hardlink-swap", "special", "non-regular", "pre-existing",
                    "oversize", "visible-temp"):
        with self.subTest(fixture=fixture), self.assertRaises(ProducerValidationError):
            materialize_fixture(fixture)
```

- [x] **Step 2: RED를 확인한다.**

```bash
python3 scripts/research/test_paddle_ocr_producer_registry.py -v
python3 scripts/research/test_paddle_ocr_producer_evidence.py -v
```

Expected: registry/evidence API 미구현으로 실패한다.

- [x] **Step 3: bounded HTTP, retry/result 분류와 OCI preflight를 구현한다.**

```python
@dataclass(frozen=True)
class HttpLimits:
    connect_timeout_seconds: int = 10
    read_timeout_seconds: int = 30
    max_page_bytes: int = 2 * 1024 * 1024
    max_page_items: int = 100
    max_pages: int = 20
    max_total_bytes: int = 40 * 1024 * 1024
    max_attestation_bytes: int = 4 * 1024 * 1024

def retry_delays(kind: str, status: int | None, error: BaseException | None) -> tuple[int, ...]:
    transient = status in {408, 429} or (status is not None and 500 <= status <= 599)
    transient |= isinstance(error, (TimeoutError, ConnectionResetError))
    transient |= kind == "PUBLIC_AFTER_VISIBILITY" and status in {401, 403, 404}
    return (2, 4) if transient else ()
```

일반 API의 `401/403`은 `BLOCKED`, `404`는 absent다. public 확인 직후 registry
`401/403/404`만 transient이며 exhausted `408/429/5xx`, reset/timeout은
`TRANSIENT_EXHAUSTED`, schema/tag/digest mismatch와 cancellation은 retry 없는
`REJECTED`다. 각 operation은 absolute deadline, 독립 최대 3회, 2/4초 backoff와
operationId/retry receipt를 가진다. page당 100 item, 20 page/40 MiB, page body 2 MiB,
attestation body 4 MiB를 streaming parser로 강제하고 20번째 page의 `next`, duplicate
match, malformed JSON/number, depth/entry 초과, BOM/trailing data를 거부한다. JSONL은 raw
bytes를 보존하며 BOM, CR, trailing blank, line 재정렬/재직렬화와 malformed signed
envelope를 거부한다. fake transport/clock/sleeper로 모든 boundary를 시험한다.

- [x] **Step 4: exact evidence allowlist와 root-pinned materializer를 구현한다.**

```python
EVIDENCE_FILES = (
    "producer-evidence.json",
    "artifact-ledger.fragment.json",
    "inputs/producer-input.lock.json",
    "manifests/package-lock.json",
    "manifests/model-detector.json",
    "manifests/model-recognizer.json",
    "platform-manifest.json",
    "sbom.spdx.json",
    "legal-inventory.json",
    "attestations/provenance.bundle.jsonl",
    "attestations/sbom.bundle.jsonl",
)
```

manifest 1 MiB, file당 128 MiB, file 합계 512 MiB, descriptor 합계 513 MiB, layer
16개, path depth 3/UTF-8 240 bytes를 강제한다. materializer는 root directory fd에서
component별 no-follow, directory `0700`, file `0600`, same-parent no-replace rename을
사용한다.
`MaterializationLimits`는 manifest fetch 60초, blob connect/read 10/30초와 전체 10분을
고정한다. slow/oversize stream은 즉시 abort하고 partial temp를 삭제한다. test는 root
dirfd pinning, directory 재open `O_DIRECTORY|O_NOFOLLOW`, final
`O_NOFOLLOW|O_CREAT|O_EXCL`, exact 0700/0600와 same-parent no-replace rename을 직접
assert한다. public ORAS bootstrap은 literal v1.3.4 linux/amd64 URL/SHA, download 8 MiB,
expanded 16 MiB, safe member allowlist와 version을 확인하며 link/special/sparse/absolute/
parent/nested/duplicate entry를 추출 전에 거부한다.
`verify-public-evidence`는 credential option을 정의하지 않고 `GH_TOKEN`, `GITHUB_TOKEN`,
Docker/ORAS auth config나 credential helper가 보이면 시작 전에 거부한다. empty HOME,
empty Docker config와 token 없는 clean environment fixture는 digest-pinned public ref를
검증하며 credential env/option/auth file 주입 fixture는 network call 전에 실패한다.

- [x] **Step 5: registry/evidence test를 GREEN으로 만들고 commit한다.**

```bash
python3 scripts/research/test_paddle_ocr_producer_registry.py -v
python3 scripts/research/test_paddle_ocr_producer_evidence.py -v
git add docker/paddleocr scripts/research
git commit -m 'feat: OCI evidence를 digest 기준으로 검증한다' \
  -m 'Constraint: descriptor preflight와 root-pinned materialization이 blob 사용보다 먼저 실행된다' \
  -m 'Confidence: high' -m 'Scope-risk: broad' \
  -m 'Tested: registry pagination, OCI descriptor와 materializer tests'
```

### Task 5: baked-model CPU service image contract를 구현

**Files:**
- Create: `docker/paddleocr/Dockerfile`
- Create: `docker/paddleocr/service.py`
- Create: `docker/paddleocr/bin/bluetape4k-paddleocr-service`
- Create: `docker/paddleocr/test_service.py`
- Modify: `scripts/research/paddle_ocr_smoke.py`
- Modify: `scripts/research/test_paddle_ocr_smoke.py`
- Modify: `scripts/research/test_paddle_ocr_producer.py`

- [ ] **Step 1: external model override, missing model, readiness와 request bound RED test를 작성한다.**

```python
def test_external_model_root_is_rejected(self) -> None:
    with patch.dict(os.environ, {"PADDLEOCR_MODEL_ROOT": "/tmp/models"}):
        with self.assertRaisesRegex(ServiceConfigurationError, "external model override"):
            load_service_configuration()

def test_ready_requires_both_verified_models(self) -> None:
    state = ReadinessState(detector_verified=True, recognizer_verified=False)
    self.assertEqual(render_readiness(state), (503, b'{"ready":false}\n'))

def test_base_image_cannot_be_overridden_or_unpinned(self) -> None:
    for value in ("python:3.10", "example.invalid/base@sha256:" + "f" * 64):
        with self.subTest(value=value), self.assertRaises(ProducerValidationError):
            verify_build_base(value, valid_input_lock())

def test_image_and_legacy_smoke_variants_reject_cross_mode_fields(self) -> None:
    for config in cross_mode_configs():
        with self.subTest(config=config), self.assertRaises(SmokeValidationError):
            load_service_config(write_json_fixture(config))

def test_image_mode_uses_entrypoint_without_model_volume(self) -> None:
    _, config = load_service_config(image_config_path())
    output = output_root()
    command = build_docker_command(image_ref(), None, output, config)
    self.assertFalse(any(token.endswith(":/models:ro") for token in command))
    self.assertEqual(
        command,
        (
            "docker", "run", "--rm", "--network", "none", "--read-only",
            "--cap-drop", "ALL", "--security-opt", "no-new-privileges:true",
            "--user", "65532:65532", "--pids-limit", "128", "--memory", "1g",
            "--cpus", "2", "--tmpfs", "/tmp:rw,noexec,nosuid,size=64m",
            "--volume", f"{output.resolve()}:/out:rw", image_ref(),
            "--host", "127.0.0.1", "--port", "8080",
        ),
    )
```

- [ ] **Step 2: RED를 확인한다.**

Run: `python3 docker/paddleocr/test_service.py -v`

Expected: service API 미구현으로 실패한다.

- [ ] **Step 3: fixed runtime wrapper와 immutable-base Dockerfile을 구현한다.**

```dockerfile
ARG BASE_IMAGE
FROM ${BASE_IMAGE}
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
WORKDIR /app
COPY wheelhouse/ /wheelhouse/
COPY requirements.cpu.lock.txt /app/requirements.cpu.lock.txt
RUN python -m pip install --no-index --no-deps --require-hashes \
      --find-links=/wheelhouse -r /app/requirements.cpu.lock.txt
COPY models/ /opt/bluetape4k/paddleocr/models/
COPY model-manifest.json /opt/bluetape4k/paddleocr/model-manifest.json
COPY ocr-pipeline.yaml /opt/bluetape4k/paddleocr/ocr-pipeline.yaml
COPY legal-inventory.json /opt/bluetape4k/paddleocr/legal-inventory.json
COPY service.py /app/service.py
COPY bin/bluetape4k-paddleocr-service /opt/bluetape4k/bin/bluetape4k-paddleocr-service
USER 65532:65532
EXPOSE 8080
ENTRYPOINT ["/opt/bluetape4k/bin/bluetape4k-paddleocr-service"]
CMD ["--host", "127.0.0.1", "--port", "8080"]
```

workflow는 `BASE_IMAGE`를 verified input lock의 digest-pinned ref에서만 읽는다.
build command는 lock에서 값을 파생하며 caller의 임의 `--build-arg BASE_IMAGE`, tag와
digest mismatch를 거부한다. Dockerfile verifier와 OCI read-back도 arbitrary caller
override와 unpinned base를 거부한다. service는 startup 때
`/opt/bluetape4k/paddleocr/model-manifest.json`, fixed local
`ocr-pipeline.yaml`과 detector/recognizer tree hash를 검사하며 first-use network path를
호출하지 않는다. startup은 fixed legal inventory도 input lock의
`legalInventorySha256`과 exact-match하고 missing/changed legal file이면 readiness를 열지
않는다. Dockerfile verifier, OCI filesystem read-back과 producer evidence는 fixed path,
legal SHA와 model tree/pair SHA를 함께 결합한다. 기존
`paddle_ocr_smoke.py`에는 `modelSource=LEGACY_MOUNT|IMAGE`를 추가한다. `IMAGE` mode는
model mount와 command override를 거부하고 exact ENTRYPOINT/CMD를 검사하며,
`LEGACY_MOUNT`의 기존 23개 regression은 유지한다.

strict config의 common required key는 `schemaVersion`, `modelSource`, `host`, `port`,
`network`, `command`, `requestMaxBytes`, `responseMaxBytes`,
`readinessTimeoutSeconds`, `modelMount`, `outputMount`다. `ServiceConfig`는
`model_source: str`, `model_mount: Optional[str]`, `output_mount: str`을 가진다.
`LEGACY_MOUNT`는 `modelMount="/models"`, `outputMount="/out"`과 exact
`["paddlex","--serve","--pipeline","OCR","--host","127.0.0.1","--port","8080"]`
command를 요구하고 기존 read-only model volume을 유지한다. `IMAGE`는
`modelMount=null`, `outputMount="/out"`, command
`["--host","127.0.0.1","--port","8080"]`만 허용하며 Docker argv는 image의
exec-form ENTRYPOINT 뒤에 이 세 argument만 붙고 model volume/command override를 만들지
않는다. 두 variant의 missing/unknown/nullability와 cross-mode command/mount를 각각
negative test로 고정한다.
기존 API를 직접 확장한다. `ServiceConfig`에 `model_source`와 optional `model_mount`를,
`ValidatedInputs`에 optional `model_manifest_sha256`, `model_tree_sha256`,
`model_snapshot`을 추가한다. `load_service_config(path)`는 위 strict oneOf를 해석한다.
`validate_inputs(..., model_manifest: Optional[Path], model_root: Optional[Path], ...)`는
LEGACY에서 두 path와 snapshot을 요구하고 IMAGE에서 둘을 금지한다.
`build_docker_command(image, model_snapshot: Optional[ModelSnapshot], output_root, config)`는
LEGACY에서 기존 model/output volume을, IMAGE에서 output volume만 만들고 image ref 뒤에
fixed CMD 세 argument만 둔다. `main` preflight도 같은 branch를 사용한다. 기존 함수 이름과
return shape를 유지하며 IMAGE positive test는 command에 `/models` volume이 없고 final
argv 전체가 output volume 뒤 즉시
`(image,"--host","127.0.0.1","--port","8080")`으로 끝나는지 검사한다. exact tuple과
모든 token의 `:/models:ro` suffix 거부를 함께 사용해 host path가 붙은 model volume도
통과하지 못하게 한다.

- [ ] **Step 4: service/Dockerfile contract를 GREEN으로 만든다.**

```bash
python3 docker/paddleocr/test_service.py -v
python3 scripts/research/test_paddle_ocr_smoke.py
python3 scripts/research/paddle_ocr_producer.py verify-dockerfile \
  --dockerfile docker/paddleocr/Dockerfile \
  --input-lock docker/paddleocr/producer-input.lock.json
```

Expected: fixed user/entrypoint/model root, immutable base binding, no network installer와 no
external model override가 PASS다.
request/response/readiness timeout boundary도 unit test로 검증한다. throughput, peak memory,
runtime resource와 no-egress 성능 증거는 #609-E/#544의 `PENDING`이며 #638 PASS로
완료 처리하지 않는다.

- [ ] **Step 5: image contract commit을 만든다.**

```bash
git add docker/paddleocr scripts/research
git commit -m 'feat: PaddleOCR model을 image 안에 고정한다' \
  -m 'Constraint: runtime은 first-use download와 외부 model override를 허용하지 않는다' \
  -m 'Confidence: high' -m 'Scope-risk: moderate' \
  -m 'Tested: service unit test와 Dockerfile input-lock verifier'
```

### Task 6: manual producer workflow와 credential-free PR CI contract를 추가

**Files:**
- Create: `.github/workflows/paddleocr-producer.yml`
- Create: `.github/scripts/test-paddleocr-producer-workflow.py`
- Create: `.github/CODEOWNERS`
- Modify: `.github/workflows/ci.yml`

- [ ] **Step 1: job graph, dispatch mode, permission과 action pin RED test를 작성한다.**

```python
REQUIRED_JOBS = {
    "validation", "staging", "source-repro-check", "image-build",
    "staging-push", "staging-attest", "staging-readback", "release-promotion",
    "release-attest", "release-readback", "release-evidence-push",
    "consumer-verify-private", "public-visibility-readback",
    "consumer-verify-public", "emergency-deny-attest", "release-readback-finalize",
}
assert REQUIRED_JOBS <= workflow_job_ids(workflow_text)
assert all_uses_are_full_sha(workflow_text)
assert top_level_permissions_are_empty(workflow_text)
assert_exact_permissions(workflow_text, EXPECTED_PERMISSIONS)
assert_workflow_concurrency(
    workflow_text,
    group="paddleocr-producer-${{ github.repository }}-linux-amd64",
    cancel_in_progress=False,
)
assert_exact_job_timeouts(workflow_text, {
    "validation": 15, "staging": 90, "source-repro-check": 90, "image-build": 90,
    "staging-push": 15, "staging-attest": 15, "staging-readback": 15,
    "release-promotion": 15, "release-attest": 15, "release-readback": 15,
    "release-evidence-push": 15, "consumer-verify-private": 15,
    "public-visibility-readback": 15, "consumer-verify-public": 15,
    "emergency-deny-attest": 15, "release-readback-finalize": 15,
})
```

- [ ] **Step 2: RED를 확인한다.**

Run: `python3 .github/scripts/test-paddleocr-producer-workflow.py`

Expected: workflow file이 없어 실패한다.

- [ ] **Step 3: `PRODUCE|RECONCILE` workflow skeleton과 verified action pins를 구현한다.**

workflow는 `workflow_dispatch`만 허용하고 mode는 `PRODUCE|RECONCILE`다. PR의
credential-free validation은 `ci.yml`이 담당한다. top-level `permissions: {}`, concurrency는
`paddleocr-producer-${{ github.repository }}-linux-amd64`,
`cancel-in-progress: false`다. action tag를 다음처럼 commit SHA로 역참조하고 verified
40-hex SHA만 `uses:`에 기록한다.

```bash
for spec in actions/checkout:v7 actions/setup-python:v7 actions/upload-artifact:v7 \
  actions/download-artifact:v8 actions/attest-build-provenance:v3 actions/attest-sbom:v3; do
  repo=${spec%%:*}
  tag=${spec##*:}
  GH_HTTP_TIMEOUT=60 gh api "repos/$repo/commits/$tag" \
    --jq '[.sha, .commit.verification.verified] | @tsv'
done
```

Expected: 각 행이 40-hex SHA와 `true`를 출력한다. 하나라도 아니면 workflow 작성은
`BLOCKED`다.
이 조회 결과를 검토해 공식 action repository와 exact SHA의 정적 `ALLOWED_ACTIONS`
map으로 계획/코드에 기록한다. contract test는 모든 `uses:`가 이 map과 exact-match하고
unexpected repository, tag/runtime lookup, unsigned 또는 다른 SHA를 fail closed한다.

모든 checkout은 `persist-credentials: false`다. privileged job은 첫 단계에서 공통
`validate-trust-context`를 실행해 repository/ref/dispatch HEAD, native linux/amd64,
github-hosted runner, actor, environment/ruleset, non-empty trust policy membership와
input-lock/legal 완결을 확인한다. mismatch fixture는 package/OIDC token 발급 및 side
effect step보다 앞서 실패하는 순서를 assert한다. `EXPECTED_PERMISSIONS`는 전체 job의
exact permission equality를 검증하고 예상 밖 `contents: write`, packages/attestations/
id-token, secret context, public verifier의 `GH_TOKEN`/registry credential을 거부한다.
GitHub/OIDC token은 build context/arg/secret/cache/artifact에 전달하지 않는다.

- [ ] **Step 4: `ci.yml`에 producer path와 contract job을 연결한다.**

```yaml
# changes.outputs
paddleocr-producer: ${{ steps.filter.outputs.paddleocr-producer }}

# dorny/paths-filter input
paddleocr-producer:
  - 'docker/paddleocr/**'
  - 'scripts/research/paddle_ocr_producer.py'
  - 'scripts/research/paddle_ocr_producer_lib/**'
  - 'scripts/research/test_paddle_ocr_producer*.py'
  - 'scripts/research/paddle_ocr_smoke.py'
  - 'scripts/research/test_paddle_ocr_smoke.py'
  - '.github/scripts/test-paddleocr-producer-workflow.py'
  - '.github/workflows/paddleocr-producer.yml'
  - '.github/CODEOWNERS'
  - '.github/workflows/ci.yml'

# top-level job
producer-contract:
  name: Test / PaddleOCR producer contract / Python ${{ matrix.python-version }}
  runs-on: ubuntu-24.04
  timeout-minutes: 10
  needs: [changes]
  if: ${{ needs.changes.outputs['paddleocr-producer'] == 'true' || needs.changes.outputs['build-logic'] == 'true' || github.event_name == 'workflow_dispatch' }}
  strategy:
    fail-fast: false
    matrix:
      python-version: ['3.9', '3.13']
  steps:
    - uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1
      with:
        persist-credentials: false
    - uses: actions/setup-python@5fda3b95a4ea91299a34e894583c3862153e4b97
      with:
        python-version: ${{ matrix.python-version }}
    - run: |
        python3 scripts/research/test_paddle_ocr_producer.py -v
        python3 scripts/research/test_paddle_ocr_producer_registry.py -v
        python3 scripts/research/test_paddle_ocr_producer_evidence.py -v
        python3 scripts/research/test_paddle_ocr_receipt.py
        python3 scripts/research/test_paddle_ocr_smoke.py
        python3 .github/scripts/test-paddleocr-producer-workflow.py

# ci-status additions
needs:
  - producer-contract
env:
  PADDLEOCR_PRODUCER_CHANGED: ${{ needs.changes.outputs['paddleocr-producer'] }}
  TEST_PADDLEOCR_PRODUCER_RESULT: ${{ needs['producer-contract'].result }}
run: |
  require_test "paddleocr-producer" "$PADDLEOCR_PRODUCER_CHANGED" "$TEST_PADDLEOCR_PRODUCER_RESULT"
```

`producer-contract` job은 Python 3.13에서 producer tests, 기존 receipt/smoke tests와
workflow contract를 Python 3.9와 3.13 matrix에서 실행한다. package/model download, Docker build와 registry write는
PR CI에서 실행하지 않는다. `ci-status`의 required job 목록에도
`Test / PaddleOCR producer contract`를 추가한다. `.github/CODEOWNERS`는 workflow,
`docker/paddleocr/producer-input.lock.json`, `trust-policy.json`, producer CLI를
`@debop` 소유로 고정한다.
CODEOWNERS는 workflow/CI, CODEOWNERS 자체, producer CLI/lib, 모든 input/legal/trust/
revocation/evidence/lifecycle schema와 data, service/Dockerfile 및 producer runbook 전체를
`@debop`으로 보호하며 contract test가 expected path별 owner를 exact-match한다.
CI contract test는 producer path changed, unchanged, build-logic changed,
`workflow_dispatch`, matrix failure와 required job skipped fixture를 검증한다. producer
또는 build-logic이 changed이거나 dispatch인데 `producer-contract`가 skipped면
`ci-status`가 실패해야 한다.
각 보호 경로, 특히 CODEOWNERS와 smoke implementation/test만 단독 변경한 fixture도
`paddleocr-producer=true`와 required matrix 실행을 요구한다.
workflow contract fixture는 동시 dispatch가 같은 group에서 serialize되고 진행 중 run을
취소하지 않는지, 동일 input-lock SHA의 기존 `PRODUCER_PASS`가 있으면 새 build 전에
중복을 거부하는지 검사한다.
`PRODUCE`에 resume field가 하나라도 있으면 거부하고, `RECONCILE`의 six field 누락,
empty/다른 sentinel, prior object가 non-null인데 `NONE`, prior object가 null인데 digest를
준 경우를 각각 contract/CLI fixture로 거부한다.
producer CLI의 `write-step-summary` subcommand와 모든 job의 final summary step을 구현한다.
summary는 redacted canonical run/attempt, status/exit, last stage, document/image/evidence
digest, retry, cleanup, incident candidate를 기록하며 write/digest read-back failure는 job을
fail closed한다. workflow contract는 summary step이 cleanup aggregate 뒤, terminal result
전에 있고 required field를 모두 쓰는지 검사한다.

- [ ] **Step 5: contract/syntax를 GREEN으로 만들고 commit한다.**

```bash
python3 .github/scripts/test-paddleocr-producer-workflow.py
actionlint .github/workflows/paddleocr-producer.yml .github/workflows/ci.yml
python3 scripts/research/test_paddle_ocr_producer.py -v
python3 scripts/research/test_paddle_ocr_producer_registry.py -v
python3 scripts/research/test_paddle_ocr_producer_evidence.py -v
git add .github scripts/research docker/paddleocr
git commit -m 'ci: producer의 validation 경계를 PR에서 검증한다' \
  -m 'Constraint: PR CI에는 registry write와 model download 권한을 부여하지 않는다' \
  -m 'Confidence: high' -m 'Scope-risk: broad' \
  -m 'Tested: workflow contract, actionlint와 producer unit tests'
```

### Task 7: source artifact, OCI handoff와 private staging job을 연결

**Files:**
- Modify: `.github/workflows/paddleocr-producer.yml`
- Modify: `.github/scripts/test-paddleocr-producer-workflow.py`
- Modify: `scripts/research/paddle_ocr_producer.py`
- Modify: `scripts/research/test_paddle_ocr_producer_evidence.py`

- [ ] **Step 1: exact artifact binding과 private staging RED test를 추가한다.**

```python
assert_job_permissions(workflow, "image-build", {"contents": "read", "actions": "read"})
assert_job_permissions(workflow, "staging-push", {
    "contents": "read", "actions": "read", "packages": "write",
})
assert_job_environment(workflow, "staging-push", "paddleocr-producer")
assert_needs(workflow, "staging-push", {"validation", "source-repro-check", "image-build"})
assert_needs(workflow, "image-build", {"validation", "staging", "source-repro-check"})
assert_same_run_artifact(workflow, "image-build", "paddleocr-models-${attemptId}")
assert_same_run_artifact(workflow, "image-build", "paddleocr-wheelhouse-${attemptId}")
assert_same_run_artifact(workflow, "staging-push", "paddleocr-oci-${attemptId}")
assert_no_registry_write_in_job(workflow, "image-build")
```

- [ ] **Step 2: RED를 확인한다.**

Run: `python3 .github/scripts/test-paddleocr-producer-workflow.py`

Expected: staging job body/permission assertion이 실패한다.

- [ ] **Step 3: source-repro-check와 unprivileged image-build를 구현한다.**

`staging`은 exact `paddleocr-models-<attemptId>` artifact를 만든다. content는
`models/{detector,recognizer}/**`, `model-manifest.json`, `ocr-pipeline.yaml`,
`legal-inventory.json`, `staging-manifest.json`만 허용한다. strict staging manifest는
artifact name/ID/digest, run ID/attempt, input-lock SHA, detector/recognizer tree SHA,
pair SHA, legal inventory SHA와 모든 file bytes/SHA를 요구한다. upload 전/다운로드 후
regular-file/no-symlink, exact file set과 aggregate limit을 다시 검증한다.

`source-repro-check`는 exact wheelhouse를 만들고
`paddleocr-wheelhouse-<attemptId>`를 upload한다. `image-build`는 두 artifact의
ID/digest/run/attempt/input-lock/tree/pair/legal binding을 검사하며 둘 다 같은 run의
`staging`/`source-repro-check`에서 왔는지 확인한 뒤 model files와 pipeline config를
network-none Docker context에 materialize한다. missing/cross-run/swapped model role,
tree/pair/legal mismatch와 unexpected file negative fixture를 추가한다. 그 뒤 Buildx `--network=none`,
`--platform=linux/amd64`, `--output=type=oci`로 build한다. handoff artifact는
exact `paddleocr-oci-<attemptId>` 이름이며 `paddleocr-service.oci.tar`와 `handoff.json`
두 regular file뿐이다. upload output의 artifact ID/digest와 tar SHA를 handoff에 기록한다.
`staging-push`는 같은 run의 exact artifact name/ID/digest/run ID/attempt/input-lock SHA를
download 직후 검증하고 swapped/cross-run/renamed artifact, tar SHA와 identity mismatch를
privileged registry access 전에 거부한다.
shared/remote cache와 cache import/export는 사용하지 않으며 workflow contract가 이를
거부한다. build start/end/duration, network-none와 cache-disabled receipt를 handoff에
결합해 Task 11/12에서 exact run 기준으로 read-back한다.

- [ ] **Step 4: privileged staging-push/readback을 구현한다.**

staging-push는 OCI tar를 실행하거나 임의 extract하지 않는다. verified ORAS absolute
path로 `ghcr.io/bluetape4k/paddleocr-service-staging:image-<attemptId>`에 copy한 뒤
index/platform/config/base digest와 private visibility를 read-back한다.
모든 job의 마지막 `if: always()` cleanup은 자기 process group/FD/temp/auth만 정리하고
fragment를 올리며 원래 실패를 보존한다. secret/query/header/model fixture가 stdout,
stderr, uploaded artifacts, Docker context/args와 cache에 나타나지 않는지 capture test로
검증하고 cleanup failure 경로도 같은 redaction assertion을 통과해야 한다.

- [ ] **Step 5: contract/syntax를 GREEN으로 만들고 commit한다.**

```bash
python3 .github/scripts/test-paddleocr-producer-workflow.py
python3 scripts/research/test_paddle_ocr_producer_evidence.py -v
actionlint .github/workflows/paddleocr-producer.yml
git add .github scripts/research
git commit -m 'ci: unprivileged build를 private staging과 분리한다' \
  -m 'Constraint: registry write token은 검증된 OCI handoff를 복사하는 job에서만 사용한다' \
  -m 'Confidence: high' -m 'Scope-risk: broad' \
  -m 'Tested: workflow contract, OCI handoff tests와 actionlint'
```

### Task 8: attestation, release promotion과 public evidence gate를 연결

**Files:**
- Modify: `.github/workflows/paddleocr-producer.yml`
- Modify: `.github/scripts/test-paddleocr-producer-workflow.py`
- Modify: `scripts/research/paddle_ocr_producer.py`
- Modify: `scripts/research/paddle_ocr_producer_lib/evidence.py`
- Modify: `scripts/research/test_paddle_ocr_producer_evidence.py`

- [ ] **Step 1: same-subject, permission separation과 public credential RED test를 추가한다.**

```python
assert_job_permissions(workflow, "staging-attest", {
    "contents": "read", "actions": "read", "packages": "read",
    "attestations": "write", "id-token": "write",
})
assert_job_permissions(workflow, "release-promotion", {"contents": "read", "packages": "write"})
assert_job_permissions(workflow, "consumer-verify-public", {"contents": "read"})
assert_no_credentials(workflow, "consumer-verify-public")
```

- [ ] **Step 2: RED를 확인한다.**

Run: `python3 .github/scripts/test-paddleocr-producer-workflow.py`

Expected: attest/promotion/evidence/public jobs가 불완전해 실패한다.

- [ ] **Step 3: platform digest subject의 SPDX/provenance와 read-back을 구현한다.**

attest job은 OIDC write를 독점하고 readback job은 read permission만 사용한다. 두
attestation은 `linux/amd64` platform manifest digest를 같은 subject로 사용한다. API와
`gh attestation verify` 결과가 repository, workflow full SHA/ref, environment, issuer,
signer, audience, run/attempt와 exact-match해야 다음 job이 실행된다.

- [ ] **Step 4: digest-preserving promotion과 public evidence verification을 구현한다.**

release-promotion은 staging→release copy 후 four-digest equality를 읽는다. exact 11 files를
evidence OCI로 push하고 private consumer를 먼저 통과한다. visibility는 workflow가
바꾸지 않는다. 별도 외부 변경과 protected read-back 뒤 public consumer가 credential
없이 ORAS SHA, descriptor, streamed blobs와 두 bundle을 검증한다.

- [ ] **Step 5: contract/syntax를 GREEN으로 만들고 commit한다.**

```bash
python3 .github/scripts/test-paddleocr-producer-workflow.py
python3 scripts/research/test_paddle_ocr_producer_evidence.py -v
actionlint .github/workflows/paddleocr-producer.yml
git add .github scripts/research
git commit -m 'ci: 동일 digest의 attestation과 public evidence를 검증한다' \
  -m 'Constraint: package visibility 변경은 workflow 밖의 별도 승인 작업으로 남긴다' \
  -m 'Confidence: high' -m 'Scope-risk: broad' \
  -m 'Tested: attestation/evidence contract tests와 actionlint'
```

### Task 9: finalizer, reconcile과 quarantine recovery를 완결

**Files:**
- Modify: `.github/workflows/paddleocr-producer.yml`
- Modify: `.github/scripts/test-paddleocr-producer-workflow.py`
- Modify: `scripts/research/paddle_ocr_producer.py`
- Modify: `scripts/research/paddle_ocr_producer_lib/lifecycle.py`
- Modify: `scripts/research/test_paddle_ocr_producer.py`
- Modify: `scripts/research/test_paddle_ocr_producer_registry.py`

- [ ] **Step 1: cancelled/interrupted/ambiguous/quarantine RED test를 추가한다.**

```python
def test_cancelled_attempt_cannot_become_pass(self) -> None:
    result = finalize_attempt(cancelled_attempt(), complete_readbacks())
    self.assertEqual(result["producerStatus"], "CANCELLED")
    self.assertEqual(result["exitCode"], 31)

def test_release_without_staging_is_rejected(self) -> None:
    remote = remote_state(staging=None, release=release_state(), evidence=evidence_state())
    self.assertEqual(reconcile_remote_state(remote)["producerStatus"], "REJECTED")
```

- [ ] **Step 2: RED를 확인한다.**

```bash
python3 scripts/research/test_paddle_ocr_producer.py -v
python3 scripts/research/test_paddle_ocr_producer_registry.py -v
python3 .github/scripts/test-paddleocr-producer-workflow.py
```

Expected: finalizer/reconcile/emergency assertions가 실패한다.

- [ ] **Step 3: `always()` finalizer와 cleanup aggregation을 구현한다.**

finalizer는 started job fragments, run conclusion, remote package/evidence, visibility,
attestation과 revocation commit을 다시 읽는다. required hash는 모든 terminal path에서
non-null이어야 하며 missing fragment는 `PRODUCER_PASS`를 금지한다.
runner loss의 missing fragment는 `INTERRUPTED`/32와 `cleanupVerified=false`, malformed/
duplicate fragment는 `REJECTED`/22다. workflow contract는 각 job의 cleanup
`if: always()`와 fragment upload -> aggregate -> result ordering을 검사한다.

- [ ] **Step 4: RECONCILE과 emergency deny path를 구현한다.**

workflow/CLI는 `resumeAttemptId`, `expectedPriorStatus`, `expectedInputLockSha256`,
`expectedStagingDigest`, `expectedReleaseDigest`, `expectedEvidenceDigest` 여섯 field를 모두
요구한다. prior object가 null인 digest만 exact sentinel `NONE`을 허용하고 validator가
null로 정규화한다. prior status, input lock, staging/release/evidence digest와 registry
absence, prior result/evidence/reconciliation/cleanup hash가 하나라도 다르면 거부한다.
RECONCILE job graph는 read-back/compensation만 실행하며 build/push/promotion이 없음을
contract test로 고정한다. public
failure는 `QUARANTINE_PENDING`과 revocation candidate를 만든다. protected `develop`에
병합된 current revocation ancestry와 signed digest-bound emergency receipt가 확인돼야
`QUARANTINED`가 된다. visibility rollback/삭제는 수행하지 않는다.
failure ordering matrix는 cleanup aggregate, result, `QUARANTINE_PENDING`, revocation
merge, emergency attestation, `QUARANTINED`, 별도 visibility rollback의 선행 관계를
table-driven test와 workflow `needs`/`always()` assertion으로 고정한다.
`select-dispatched-run`은 dispatch 전 ID set과 paginated candidate를 비교해 새로 생긴
동일 workflow/event/head run의 exact 1건만 반환한다. 0건/2건 이상, invalid runAttempt,
다른 path/head와 same-head manual rerun은 fail closed한다.
`readback-public-gate`와 `wait-workflow-run`도 같은 bounded client를 사용해 visibility,
anonymous consumer, cleanup과 finalizer를 읽고 timeout/runner loss를 `INTERRUPTED`와
RECONCILE receipt로 고정한다.

timeout 또는 비정상 conclusion이면 workflow는 credential-free incident candidate와
Step Summary만 만들고 `issues: write`를 받지 않는다. 별도 외부 승인 뒤 owner가
`open-or-link-incident`를 실행해 이 저장소의 #638 issue comment 또는 별도 incident issue
URL을 reconciliation에 결합하고 acknowledgement/downstream notification receipt를
요구한다. `acknowledge-incident`와 `close-incident`는
exact current reconciliation hash를 입력받고 denylist/visibility/cleanup/downstream
read-back 뒤에만 timestamp를 추가한다. duplicated incident, 다른 repository URL,
acknowledgement 이전 close와 incomplete read-back을 거부하는 fixture를 추가한다.

`plan-known-good-rollback`은 current revocations, last accepted non-revoked platform/evidence
digest와 stable tag를 read-only로 확인해 deterministic dry-run receipt만 만든다. revoked/
ambiguous digest, mutable-tag-only 입력과 notification 미완료를 거부한다. 실제 visibility/
tag/delete 변경은 `execute-known-good-rollback`의 별도 파괴적 승인 gate 뒤에만 가능하며
workflow 기본 경로에서는 호출하지 않는다. failure ordering fixture는 quarantine,
revocation merge, emergency receipt, known-good selection, downstream notification,
owner acknowledgement와 closure 순서를 검증한다.

- [ ] **Step 5: lifecycle/workflow를 GREEN으로 만들고 commit한다.**

```bash
python3 scripts/research/test_paddle_ocr_producer.py -v
python3 scripts/research/test_paddle_ocr_producer_registry.py -v
python3 .github/scripts/test-paddleocr-producer-workflow.py
actionlint .github/workflows/paddleocr-producer.yml
git add .github scripts/research docker/paddleocr
git commit -m 'ci: producer 중단과 quarantine을 증거로 종결한다' \
  -m 'Constraint: terminal attempt를 수정하지 않고 signed denial과 새 reconciliation으로만 복구한다' \
  -m 'Confidence: high' -m 'Scope-risk: broad' \
  -m 'Tested: finalizer, reconciliation, revocation과 workflow contract tests'
```

### Task 10: 운영 문서, README parity와 1.1.0 evidence를 갱신

**Files:**
- Create: `docs/superpowers/runbooks/2026-09-07-issue-638-paddleocr-producer.md`
- Create: `.github/scripts/test-paddleocr-runbook.py`
- Create: `.github/scripts/test-paddleocr-readme-parity.py`
- Modify: `README.md`
- Modify: `README.ko.md`
- Modify: `CHANGELOG.md`
- Modify: `WIP.md`

- [ ] **Step 1: runbook에 exact 실행·read-back 명령을 기록한다.**

```bash
REPO=bluetape4k/bluetape4k-image
REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"
mkdir -p build
RUN_ID="${RUN_ID:?export the decimal workflow run ID}"
ATTEMPT_ID="${ATTEMPT_ID:?export run-id.run-attempt}"
ARTIFACT_DIR="${ARTIFACT_DIR:?export extracted producer artifact directory}"
IMAGE_TAG="image-$ATTEMPT_ID"
EVIDENCE_TAG="evidence-$ATTEMPT_ID"
python3 scripts/research/paddle_ocr_producer.py validate-inputs \
  --lock docker/paddleocr/producer-input.lock.json \
  --policy docker/paddleocr/trust-policy.json \
  --legal docker/paddleocr/legal-inventory.json
python3 scripts/research/paddle_ocr_producer.py verify-attempt \
  --attempt "$ARTIFACT_DIR/producer-attempt.json" \
  --evidence "$ARTIFACT_DIR/producer-evidence.json" \
  --reconciliation "$ARTIFACT_DIR/reconciliation.json" \
  --cleanup "$ARTIFACT_DIR/cleanup.json" \
  --ledger-fragment "$ARTIFACT_DIR/artifact-ledger.fragment.json" \
  --revocations docker/paddleocr/revocations.json
python3 scripts/research/paddle_ocr_producer.py readback-workflow-run \
  --repo "$REPO" --run-id "$RUN_ID" --run-attempt "${RUN_ATTEMPT:?export run attempt}" \
  --expected-head "${HEAD_SHA:?export exact producer head}" \
  --output build/run-readback.json
python3 scripts/research/paddle_ocr_producer.py readback-packages \
  --owner bluetape4k --release-package paddleocr-service \
  --staging-package paddleocr-service-staging \
  --attempt-id "$ATTEMPT_ID" --image-tag "$IMAGE_TAG" --evidence-tag "$EVIDENCE_TAG" \
  --connect-timeout-seconds 10 --read-timeout-seconds 30 \
  --max-page-bytes 2097152 --max-page-items 100 --max-pages 20 \
  --max-total-bytes 41943040
```

runbook은 PR의 credential-free validation, `PRODUCE`, visibility read-back, public verify,
`RECONCILE`, revocation, emergency denial과 별도 destructive rollback 순서다.
public verify 절차에는 literal ORAS v1.3.4 linux/amd64 URL과 SHA-256, 8 MiB download와
16 MiB expanded limit, archive member preflight, no-follow extraction, absolute verified
`ORAS_BIN`, version check, digest-pinned anonymous manifest/blob fetch와 60초/10분 deadline을
복사 가능한 명령으로 기록한다.

```bash
ANON_HOME="$(mktemp -d)"
ANON_DOCKER_CONFIG="$ANON_HOME/docker"
mkdir -m 0700 "$ANON_DOCKER_CONFIG"
PYTHON_BIN="$(command -v python3)"
GH_BIN="$(command -v gh)"
ORAS_BIN="${ORAS_BIN:?export absolute verified ORAS v1.3.4 path}"
EVIDENCE_REF="${EVIDENCE_REF:?export digest-pinned public evidence ref}"
trap 'rm -rf "$ANON_HOME"' EXIT
env -i HOME="$ANON_HOME" DOCKER_CONFIG="$ANON_DOCKER_CONFIG" \
  PATH="$(dirname "$PYTHON_BIN"):$(dirname "$GH_BIN"):/usr/bin:/bin" \
  "$PYTHON_BIN" scripts/research/paddle_ocr_producer.py verify-public-evidence \
  --oras-bin "$ORAS_BIN" --gh-bin "$GH_BIN" --ref "$EVIDENCE_REF" \
  --root "$ANON_HOME/evidence" --operation-timeout-seconds 60 \
  --materialization-timeout-seconds 600
```
incident 절에는 `open-or-link-incident`, `acknowledge-incident`, bounded downstream
notification read-back과 `close-incident` 명령을 exact reconciliation hash와 함께 기록한다.
retention 절은 workflow logs/artifacts 최소 90일, accepted release image/evidence/SBOM/
legal/attestation/revocation 최소 3년 및 failed private staging 30일 quarantine 후 삭제
후보 규칙을 기록한다. accepted evidence는 자동 삭제를 금지하고 3년 뒤에도 별도 파괴적
승인과 독립 export 검증 없이는 삭제하지 않는다. rollback 절에는 read-only
`plan-known-good-rollback` rehearsal과 별도 승인 후 실행 명령을 분리한다.

```bash
RECONCILIATION="${RECONCILIATION:?export exact reconciliation JSON}"
RECONCILIATION_SHA="${RECONCILIATION_SHA:?export prefix-free SHA-256}"
INCIDENT_APPROVAL="${INCIDENT_APPROVAL:?export fresh issue/comment approval marker}"
python3 scripts/research/paddle_ocr_producer.py open-or-link-incident \
  --repo bluetape4k/bluetape4k-image --reconciliation "$RECONCILIATION" \
  --expected-sha256 "$RECONCILIATION_SHA" --approval-marker "$INCIDENT_APPROVAL" \
  --output build/incident-opened.json
python3 scripts/research/paddle_ocr_producer.py acknowledge-incident \
  --reconciliation "$RECONCILIATION" --expected-sha256 "$RECONCILIATION_SHA" \
  --incident build/incident-opened.json --output build/incident-acknowledged.json
python3 scripts/research/paddle_ocr_producer.py close-incident \
  --reconciliation "$RECONCILIATION" --expected-sha256 "$RECONCILIATION_SHA" \
  --incident build/incident-acknowledged.json \
  --require-denylist --require-visibility --require-cleanup --require-downstream \
  --output build/incident-closed.json
```

`open-or-link-incident`만 GitHub issue/comment side effect를 가지며 fresh approval marker가
없으면 호출 전에 실패한다. acknowledgement와 close는 read-back receipt를 만들고 모든
closure condition이 true가 아니면 `closedAt`을 기록하지 않는다.

```bash
ROLLBACK_PLAN="${ROLLBACK_PLAN:?export reviewed rollback rehearsal JSON}"
APPROVAL_MARKER="${APPROVAL_MARKER:?export fresh destructive approval marker}"
python3 scripts/research/paddle_ocr_producer.py execute-known-good-rollback \
  --repo bluetape4k/bluetape4k-image \
  --release-package-id "${RELEASE_PACKAGE_ID:?export exact package ID}" \
  --rollback-plan "$ROLLBACK_PLAN" --approval-marker "$APPROVAL_MARKER" \
  --expected-current-digest "${CURRENT_DIGEST:?export current sha256 digest}" \
  --known-good-digest "${KNOWN_GOOD_DIGEST:?export non-revoked sha256 digest}" \
  --mutation visibility-and-stable-tag \
  --output build/known-good-rollback-result.json
python3 scripts/research/paddle_ocr_producer.py readback-known-good-rollback \
  --repo bluetape4k/bluetape4k-image \
  --release-package-id "$RELEASE_PACKAGE_ID" \
  --expected-digest "$KNOWN_GOOD_DIGEST" \
  --output build/known-good-rollback-readback.json
```

위 명령은 fresh approval marker가 없으면 mutation 전에 실패하고 exact package ID/current
digest/non-revoked known-good digest를 다시 확인한다. 실제 실행은 incident의 별도 파괴적
승인 때만 수행하며 정상 #638 producer run에서는 예시와 rehearsal만 검증한다.

runbook은 `STATUS_CONTRACT`에서 생성·검증한 `status / exit / publish 가능 여부 / 다음
명령` 표를 싣는다. 특히 exit `20`은 publish 중단 후 read-back/RECONCILE, `21`은 quarantine
유지, `22`는 digest 폐기, `31|32`는 cleanup/remote reconciliation, `40`은 input/schema
수정 뒤 새 commit이라는 서로 다른 조치를 표시한다.

지원 범위와 prerequisites는 manual GitHub-hosted `linux/amd64`, Python 3.9 또는 3.13,
`gh`, Docker Buildx, verified ORAS v1.3.4, repository/environment/package owner 권한으로
고정한다. GPU, arm64/multiarch, arbitrary tag/ref/model override, local publish와 workflow
artifact fallback을 AC-08 public evidence로 사용하는 경로는 비지원으로 명시한다.

```bash
python3 scripts/research/paddle_ocr_producer.py prepare-reconcile-inputs \
  --prior-result "${PRIOR_RESULT:?export prior result JSON}" \
  --prior-reconciliation "${PRIOR_RECONCILIATION:?export prior reconciliation JSON}" \
  --prior-evidence "${PRIOR_EVIDENCE:?export prior evidence JSON}" \
  --prior-cleanup "${PRIOR_CLEANUP:?export prior cleanup JSON}" \
  --output build/reconcile-inputs.json
RESUME_ATTEMPT_ID=$(python3 -c 'import json; print(json.load(open("build/reconcile-inputs.json"))["data"]["resumeAttemptId"])')
EXPECTED_PRIOR_STATUS=$(python3 -c 'import json; print(json.load(open("build/reconcile-inputs.json"))["data"]["expectedPriorStatus"])')
EXPECTED_INPUT_LOCK_SHA256=$(python3 -c 'import json; print(json.load(open("build/reconcile-inputs.json"))["data"]["expectedInputLockSha256"])')
EXPECTED_STAGING_DIGEST=$(python3 -c 'import json; print(json.load(open("build/reconcile-inputs.json"))["data"]["expectedStagingDigest"])')
EXPECTED_RELEASE_DIGEST=$(python3 -c 'import json; print(json.load(open("build/reconcile-inputs.json"))["data"]["expectedReleaseDigest"])')
EXPECTED_EVIDENCE_DIGEST=$(python3 -c 'import json; print(json.load(open("build/reconcile-inputs.json"))["data"]["expectedEvidenceDigest"])')
GH_HTTP_TIMEOUT=60 gh workflow run paddleocr-producer.yml \
  --repo bluetape4k/bluetape4k-image --ref develop \
  -f mode=RECONCILE \
  -f resumeAttemptId="$RESUME_ATTEMPT_ID" \
  -f expectedPriorStatus="$EXPECTED_PRIOR_STATUS" \
  -f expectedInputLockSha256="$EXPECTED_INPUT_LOCK_SHA256" \
  -f expectedStagingDigest="$EXPECTED_STAGING_DIGEST" \
  -f expectedReleaseDigest="$EXPECTED_RELEASE_DIGEST" \
  -f expectedEvidenceDigest="$EXPECTED_EVIDENCE_DIGEST"
```

`prepare-reconcile-inputs`는 prior 네 문서 hash와 registry absence까지 검증하고 null digest만
`NONE`으로 출력한다. 위 dispatch도 exact head/target과 fresh RECONCILE 승인을 다시 받은
뒤에만 실행한다.

- [ ] **Step 2: README 영어/한국어 구조를 함께 갱신한다.**

AI/ML status의 같은 bullet 위치에 producer가 adoption이 아니라 #609/#611 evidence
source임을 기록한다. model/runtime을 Kotlin module에 포함한다고 쓰지 않고 runbook을
연결한다. 중앙 manual을 복제하지 않는다.

- [ ] **Step 3: CHANGELOG와 WIP를 한국어로 갱신한다.**

`CHANGELOG.md`의 `Unreleased / 추가`에 #638 workflow/strict evidence contract를,
`WIP.md`에는 implementation merge와 dispatch/visibility gate의 분리를 기록한다.

- [ ] **Step 4: 문서 link/parity와 diff를 검증한다.**

```bash
python3 -m json.tool docker/paddleocr/producer-input.lock.json >/dev/null
python3 .github/scripts/test-paddleocr-runbook.py
python3 .github/scripts/test-paddleocr-readme-parity.py
git diff --check
```

Expected: heading role/order, #638/runbook link, `PRODUCER_PASS`, `#609 PENDING`, adoption
미승인 의미, local links, JSON과 diff가 PASS다. parity test는 한쪽의 bullet/link/status/
adoption 문구를 메모리에서 하나씩 제거한 negative fixture가 모두 실패하는지 검증한다.
runbook test는 repo-root/output-directory preamble, GNU `timeout` 부재, 내부 deadline
argument와 mutation approval marker를 검사하고 fake CLI/gh transport로 bash와 zsh,
macOS/Linux path fixture에서 block을 실행한다.
run selection fixture의 fake CLI는 nested `data.databaseId`/`data.runAttempt`만 반환해
전체 block의 extraction이 성공하는지 검사하고 top-level key로 회귀하면 실패한다.

- [ ] **Step 5: 문서 commit을 만든다.**

```bash
git add docs README.md README.ko.md CHANGELOG.md WIP.md \
  .github/scripts/test-paddleocr-runbook.py .github/scripts/test-paddleocr-readme-parity.py
git commit -m 'docs: producer 운영과 adoption 경계를 분리한다' \
  -m 'Constraint: trusted producer evidence는 PaddleOCR 채택 승인이 아니다' \
  -m 'Confidence: high' -m 'Scope-risk: moderate' \
  -m 'Tested: README parity, link, JSON parse와 diff check'
```

### Task 11: 전체 local/PR 검증과 exact-head review를 수행

**Files:**
- Create: `docs/review/2026-09-07-issue-638-trusted-producer-verification.md`
- Create: `docs/review/2026-09-07-issue-638-trusted-producer-code-review.md`
- Modify: finding이 있는 implementation file만 수정

- [ ] **Step 1: Python/schema validation ladder를 실행한다.**

```bash
PYTHON39="${PYTHON39:-/usr/bin/python3}"
PYTHON313="${PYTHON313:-$(command -v python3.13)}"
for python_bin in "$PYTHON39" "$PYTHON313"; do
  test -x "$python_bin"
  "$python_bin" -c 'import sys; assert sys.version_info[:2] in {(3, 9), (3, 13)}'
  "$python_bin" - <<'PY'
import importlib
import importlib.util
import sys
from pathlib import Path

importlib.import_module("scripts.research.paddle_ocr_producer_lib.contracts")
importlib.import_module("scripts.research.paddle_ocr_smoke")
for module_name, relative_path in (
    ("paddle_ocr_producer_cli", "scripts/research/paddle_ocr_producer.py"),
    ("paddle_ocr_service", "docker/paddleocr/service.py"),
):
    spec = importlib.util.spec_from_file_location(module_name, Path(relative_path))
    assert spec is not None and spec.loader is not None
    module = importlib.util.module_from_spec(spec)
    sys.modules[module_name] = module
    spec.loader.exec_module(module)
PY
  "$python_bin" scripts/research/test_paddle_ocr_producer.py -v
  "$python_bin" scripts/research/test_paddle_ocr_producer_registry.py -v
  "$python_bin" scripts/research/test_paddle_ocr_producer_evidence.py -v
  "$python_bin" docker/paddleocr/test_service.py -v
  "$python_bin" scripts/research/test_paddle_ocr_receipt.py
  "$python_bin" scripts/research/test_paddle_ocr_smoke.py
done
for file in docker/paddleocr/*.json; do python3 -m json.tool "$file" >/dev/null; done
```

Expected: tests와 JSON parse가 PASS다. package metadata/dependency를 바꾸지 않으므로
`uv sync`, wheel build와 import smoke는 `PY-05 N/A`다.
producer verification은 build receipt의 start/end/duration, `network=none`, shared/remote
cache 미사용과 handoff binding을 함께 검사한다.
각 job은 `$GITHUB_STEP_SUMMARY`에 run/attempt, status/exit, last stage, document/image/
evidence digest, retry, cleanup과 incident URL을 redacted canonical form으로 기록한다.
summary write 또는 digest read-back 실패는 job을 fail closed하고 workflow contract test가
모든 job의 summary step 순서와 required field를 검사한다.

- [ ] **Step 2: workflow/security validation ladder를 실행한다.**

```bash
python3 .github/scripts/test-paddleocr-producer-workflow.py
actionlint .github/workflows/paddleocr-producer.yml .github/workflows/ci.yml
gitleaks detect --source . --redact --no-git --config .gitleaks.toml
git diff --check
```

Expected: graph, permissions, pins, syntax, secret scan과 diff가 PASS다.

- [ ] **Step 3: local stop condition과 exact HEAD를 기록한다.**

```bash
python3 scripts/research/paddle_ocr_producer.py validate-inputs \
  --lock docker/paddleocr/producer-input.lock.json \
  --policy docker/paddleocr/trust-policy.json \
  --legal docker/paddleocr/legal-inventory.json
git status --short
git rev-parse HEAD
```

Expected: canonical `PASS` 한 줄과 clean worktree다. GitHub runner/environment/GHCR/
attestation은 local evidence로 PASS 처리하지 않는다.

- [ ] **Step 4: 여섯 관점 code review와 main integration을 수행한다.**

performance, stability, security, operator/Ops, developer/API, user/caller lane을 exact
HEAD에서 독립 실행한다. P0/P1은 수정 후 affected lane을 다시 실행하고 P2/P3은 수정
또는 근거를 기록한다. 최종 문서는 P0=0/P1=0, tests, action pins, 미실행 external
evidence를 분리한다.

- [ ] **Step 5: verification/review artifact를 commit하고 PR-ready 상태를 보고한다.**

```bash
git add docs/review
git commit -m 'docs: producer 구현의 exact-head 검증을 보존한다' \
  -m 'Constraint: local PASS와 승인된 producer dispatch 증거를 분리한다' \
  -m 'Confidence: high' -m 'Scope-risk: narrow' \
  -m 'Tested: producer validation ladder와 여섯 관점 exact-head review' \
  -m 'Not-tested: workflow dispatch, GHCR push, visibility 변경과 public consumer run'
```

PR은 repository `bluetape4k/bluetape4k-image`, base `develop`, exact head
`feat/issue-638-trusted-producer`를 다시 확인한 뒤 별도 gate로 생성한다. dispatch,
package 생성과 visibility 변경은 PR/merge 뒤의 독립 gate다.

### Task 12: merge 뒤 외부 설정과 승인된 producer run을 증명

**Files:**
- Create: `docs/review/2026-09-07-issue-638-trusted-producer-run.md`
- Modify: `WIP.md`

- [ ] **Step 1: merge와 exact `develop` head 뒤 live ruleset/environment/package state를 읽는다.**

```bash
REPO=bluetape4k/bluetape4k-image
git fetch origin develop
git worktree add .worktrees/docs/issue-638-producer-run-evidence \
  -b docs/issue-638-producer-run-evidence origin/develop
cd .worktrees/docs/issue-638-producer-run-evidence
mkdir -p build
DEVELOP_SHA=$(git rev-parse origin/develop)
python3 scripts/research/paddle_ocr_producer.py inspect-live-producer-settings \
  --repo "$REPO" --expected-head "$DEVELOP_SHA" \
  --workflow .github/workflows/paddleocr-producer.yml \
  --environment paddleocr-producer \
  --staging-package paddleocr-service-staging --release-package paddleocr-service \
  --connect-timeout-seconds 10 --read-timeout-seconds 30 \
  --max-page-bytes 2097152 --max-page-items 100 --max-pages 20 \
  --max-total-bytes 41943040 --output build/live-producer-settings.json
python3 scripts/research/paddle_ocr_producer.py readback-retention \
  --repo "$REPO" \
  --required-run-days 90 --required-accepted-days 1095 \
  --failed-staging-quarantine-days 30 \
  --output build/retention-readback.json
printf 'develop_sha=%s\n' "$DEVELOP_SHA"
```

Expected: workflow SHA가 `DEVELOP_SHA`, actor/ruleset/environment가 trust policy와
일치한다. package `404`는 최초 push 전 absent로 기록할 수 있지만 `401/403`은
`BLOCKED`다. environment/ruleset 생성 또는 변경은 repository merge와 분리된 외부
mutation이므로 구체 설정 diff와 별도 승인을 먼저 받는다.
repository run/log retention이 90일 미만이거나 accepted GHCR/evidence/attestation/
revocation에 자동 삭제 정책이 있으면 `BLOCKED`다. workflow artifacts는
`retention-days: 90`을 exact assertion하며 failed staging은 30일 뒤 삭제 후보로만
표시하고 metadata/receipt/incident는 보존한다.

- [ ] **Step 2: exact head와 target을 다시 제시하고 `PRODUCE` dispatch 승인을 받는다.**

승인 요청에는 repository, `DEVELOP_SHA`, workflow path, input-lock SHA, target staging/
release package, 예상 write/OIDC jobs, package가 아직 absent/private인지 포함한다. 이전
spec/plan/merge 승인을 dispatch 승인으로 재사용하지 않는다.

- [ ] **Step 3: 승인된 `PRODUCE`를 한 번 실행하고 private gate까지 read-back한다.**

```bash
mkdir -p build
python3 scripts/research/paddle_ocr_producer.py snapshot-workflow-runs \
  --repo "$REPO" --workflow paddleocr-producer.yml --branch develop \
  --event workflow_dispatch --output build/producer-run-before.json \
  --connect-timeout-seconds 10 --read-timeout-seconds 30 \
  --max-page-bytes 2097152 --max-page-items 100 --max-pages 20 \
  --max-total-bytes 41943040
gh workflow run paddleocr-producer.yml --repo "$REPO" --ref develop -f mode=PRODUCE
python3 scripts/research/paddle_ocr_producer.py select-dispatched-run \
  --repo "$REPO" --workflow paddleocr-producer.yml --branch develop \
  --event workflow_dispatch --before build/producer-run-before.json \
  --expected-head "$DEVELOP_SHA" \
  --expected-workflow .github/workflows/paddleocr-producer.yml \
  --connect-timeout-seconds 10 --read-timeout-seconds 30 \
  --max-page-bytes 2097152 --max-page-items 100 --max-pages 20 \
  --max-total-bytes 41943040 \
  > build/selected-producer-run.json
RUN_ID=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["data"]["databaseId"])' \
  build/selected-producer-run.json)
RUN_ATTEMPT=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["data"]["runAttempt"])' \
  build/selected-producer-run.json)
python3 scripts/research/paddle_ocr_producer.py wait-workflow-job \
  --repo "$REPO" --run-id "$RUN_ID" --run-attempt "$RUN_ATTEMPT" \
  --job-name 'Consumer verify private' --poll-seconds 15 \
  --connect-timeout-seconds 10 --read-timeout-seconds 30 \
  --max-page-bytes 2097152 --max-page-items 100 --max-pages 20 \
  --max-total-bytes 41943040
python3 scripts/research/paddle_ocr_producer.py readback-workflow-run \
  --repo "$REPO" --run-id "$RUN_ID" --run-attempt "$RUN_ATTEMPT" \
  --expected-head "$DEVELOP_SHA" --output build/private-gate-run.json
```

Expected: exact head의 유일한 run을 선택한다. private consumer까지 PASS하지 않거나
run이 partial이면 visibility를 바꾸지 않고 `RECONCILE` 또는 incident path로 이동한다.
workflow contract/운영 test는 newer unrelated run, same-head rerun과 0/2+ candidate를
fixture로 두고 unique `databaseId`, `run_attempt`, `headSha`, workflow path가 아니면 fail
closed하는지 검증한다. 각 단계의 timeout, retry receipt, build duration/cache-disabled
receipt도 같은 run/attempt에 결합됐는지 read-back한다.
partial result의 RECONCILE은 Task 10 runbook의 `prepare-reconcile-inputs`와 six-field
`gh workflow run ... mode=RECONCILE` 명령을 사용하며 fresh dispatch 승인 전에는 실행하지
않는다.
`snapshot-workflow-runs`, `select-dispatched-run`, `wait-workflow-job`은 모두 공통 bounded
HTTP client를 사용하고 60초 operation wrapper, API 10/30초, 100 items/page, 20 page/
40 MiB, 2 MiB/page 및 20번째 `next` 거부를 적용한다. fake transport test는 slow call,
0/2+ run, oversized page/aggregate와 polling deadline을 실제 GitHub/sleep 없이 검증한다.

- [ ] **Step 4: package visibility 변경을 별도 승인 뒤 수행하고 anonymous gate를 읽는다.**

workflow는 visibility를 변경하지 않는다. owner가 GitHub package settings에서 exact
`paddleocr-service` package를 `public`으로 바꾸기 전에 대상 package ID, image/evidence
digest와 현재 visibility를 다시 제시해 승인을 받는다. 변경 뒤 다음 read-back이
`public`일 때만 protected workflow approval을 진행한다.

```bash
python3 scripts/research/paddle_ocr_producer.py readback-public-gate \
  --repo "$REPO" --package paddleocr-service --run-id "$RUN_ID" \
  --run-attempt "$RUN_ATTEMPT" --expected-head "$DEVELOP_SHA" \
  --connect-timeout-seconds 10 --read-timeout-seconds 30 \
  --max-page-bytes 2097152 --max-page-items 100 --max-pages 20 \
  --max-total-bytes 41943040 --output build/public-gate-readback.json
python3 scripts/research/paddle_ocr_producer.py wait-workflow-run \
  --repo "$REPO" --run-id "$RUN_ID" --run-attempt "$RUN_ATTEMPT" \
  --expected-head "$DEVELOP_SHA" --poll-seconds 15 \
  --connect-timeout-seconds 10 --read-timeout-seconds 30 \
  --max-page-bytes 2097152 --max-page-items 100 --max-pages 20 \
  --max-total-bytes 41943040 --output build/final-run.json
```

Expected: anonymous `consumer-verify-public`, cleanup과 finalizer가 PASS한 경우에만
`PRODUCER_PASS`다. #609는 여전히 `PENDING`이며 offline smoke/no-egress는 별도다.
60초 operation 또는 7200초 run deadline, runner loss와 cancellation은 stable
`INTERRUPTED` result/retry receipt를 남기고 다음 실행을 RECONCILE gate로 보낸다.

- [ ] **Step 4a: incident와 known-good rollback rehearsal을 read-only로 증명한다.**

```bash
python3 scripts/research/paddle_ocr_producer.py readback-incident \
  --repo "$REPO" --run-id "$RUN_ID" --run-attempt "$RUN_ATTEMPT" \
  --reconciliation build/public-gate-readback.json \
  --output build/incident-readback.json
python3 scripts/research/paddle_ocr_producer.py plan-known-good-rollback \
  --repo "$REPO" --current-run-id "$RUN_ID" \
  --revocations docker/paddleocr/revocations.json \
  --require-downstream-notification \
  --output build/known-good-rollback-rehearsal.json
```

Expected: 정상 run은 incident가 불필요함을 명시하고 rehearsal만 PASS한다. partial/failed
fixture에서는 이 저장소 incident URL, owner acknowledgement, downstream notification,
current reconciliation hash와 non-revoked known-good digest가 exact-match해야 한다. 실제
rollback, tag/visibility/delete mutation은 수행하지 않는다.

- [ ] **Step 5: run evidence를 보존하고 issue/PR closeout 전 final validation을 실행한다.**

run ID/attempt, exact head, input-lock, image/evidence digest, attestation identity,
visibility, cleanup, reconciliation과 `mapped609Status=PENDING`을 run 문서와 `WIP.md`에
기록한다. 실패/partial run이면 성공으로 쓰지 않고 status/exit/incident/recovery를
기록한다.
90일/3년/30일 retention read-back, step summary digest, owner acknowledgement/downstream
notification과 known-good rollback rehearsal도 기록한다. terminal incident는 모든
read-back이 true일 때만 bounded `close-incident`로 닫으며 issue/comment mutation은 별도
외부 승인 뒤 수행한다.

```bash
python3 scripts/research/paddle_ocr_producer.py readback-retention \
  --repo "$REPO" --run-id "$RUN_ID" --run-attempt "$RUN_ATTEMPT" \
  --required-run-days 90 --required-accepted-days 1095 \
  --failed-staging-quarantine-days 30 \
  --output build/final-retention-readback.json
```

```bash
git add docs/review/2026-09-07-issue-638-trusted-producer-run.md WIP.md
git commit -m 'docs: producer 실행 증거를 exact digest에 결합한다' \
  -m 'Constraint: producer PASS는 #609 offline acceptance를 완료하지 않는다' \
  -m 'Confidence: high' -m 'Scope-risk: narrow' \
  -m 'Tested: live run, package visibility, attestation, cleanup과 public read-back'
```

이 commit의 PR/merge와 #638 close 여부도 별도 live GitHub gate에서 결정한다.

## Acceptance criteria traceability

| AC | 구현 task | 검증 명령/증거 |
|---|---|---|
| AC-01 producer/workflow/builder identity | 1, 2, 6, 11, 12 | trust validator, full-SHA workflow test, exact-head/live run review |
| AC-02 GHCR package/visibility/permission | 6, 7, 8, 9, 12 | job permission contract, package API read-back, consumer 분리 |
| AC-03 image/index/platform/config/base/package lock | 2, 4, 5, 7 | input verifier, OCI handoff/evidence tests, digest equality |
| AC-04 model source/file/tree/pair/legal | 2, 5 | resolution, tree/pair recomputation, legal fail-closed tests |
| AC-05 same-subject SPDX/provenance | 4, 8, 12 | evidence/attestation negative tests와 live read-back |
| AC-06 issuer/audience/signer/repository/workflow allowlist | 2, 4, 8, 12 | trust membership와 missing/mismatch tests |
| AC-07 fixed service/no first-use download | 5, 11, 12 | service tests, Dockerfile verifier와 승인된 native run |
| AC-08 secret scan/cleanup/#611 read-only verify | 3, 4, 8, 9, 10, 11, 12 | cleanup/reconcile tests, gitleaks, public job, runbook/live read-back |

## Risk prediction과 rollback

| 위험 | 조기 신호 | 구현 대응 | rollback/rerun |
|---|---|---|---|
| model/legal 미완결 | resolver exit 10/11 | publish fail closed, candidate bytes 미commit | 근거 보강 후 Task 2 재실행 |
| parser 자원 증가 | oversize/page test latency | streaming read와 fixed limits | Task 1/4 수정·rerun |
| temp/process leak | timeout 후 fd/process 잔존 | context manager와 `try/finally` | lifecycle tests에서 수정 |
| artifact/run 혼동 | identity mismatch | same-run binding, exact prior RECONCILE | mutation 없이 `REJECTED` |
| action tag drift | non-SHA `uses:` | verified full SHA | pin만 교체 후 lint/test |
| staging/release drift | digest equality false | promotion/finalize 차단 | private staging 보존·reconcile |
| public verify 실패 | anonymous/attestation non-zero | quarantine와 signed denial | revocation merge 후 reconcile |
| secret 노출 | gitleaks/redaction failure | raw header/body/token log 금지 | token rotation과 새 attempt |
| adoption 오해 | README review failure | producer와 Kotlin runtime 분리 | docs만 수정 |

## Plan self-review

| 점검 | 결과 |
|---|---|
| Spec coverage | PASS — AC-01~08과 input, image, workflow, lifecycle, evidence, operations를 Task 1~12에 매핑했다. |
| Ordering | PASS — primitive/input이 lifecycle/OCI/image/workflow보다 먼저 생성된다. |
| Later dependency | PASS — 각 task는 앞 task 산출물만 참조한다. |
| Placeholder scan | PASS — 미완성 구현 지시를 두지 않았고 resolved digest는 Task 2 검증 결과로만 기록한다. |
| Type consistency | PASS — `AttemptIdentity`, status, tags, evidence paths와 subcommand 이름을 일관되게 사용했다. |
| Python pattern | PASS — stdlib-only, logging/redaction, cleanup, boundary/timeout tests와 PY-05 N/A를 고정했다. |
| Repository hazards | PASS — action pin, path CI, package/OIDC, dispatch/visibility/rollback gate를 분리했다. |

## Writer SPW-01~05

| 항목 | 결과와 근거 |
|---|---|
| SPW-01 대상·목적·근거 | PASS — zero-context 구현자에게 #638 spec, repo paths, #609/#611와 base를 제공한다. |
| SPW-02 plan contract | PASS — 12 tasks에 files, RED/GREEN, code, command, expected result와 commit을 포함한다. |
| SPW-03 한국어 technical register | PASS — 한국어 설명과 path/API/status/digest/schema token을 분리한다. |
| SPW-04 추적성 | PASS — AC-01~08, Python gate, workflow permission, rollback과 evidence를 연결한다. |
| SPW-05 최종 read-back | PASS — 여섯 관점 P0/P1/P2/P3=0, 구조, local link, 34개 shell block, 14개 Python block, 미해결 표식과 whitespace를 다시 검사했다. |

## DoD Status

- [x] 승인된 spec을 파일과 task 단위로 분해했다.
- [x] RED/GREEN, exact command, expected result와 Lore commit 경계를 정의했다.
- [x] AC-01~08 traceability와 risk/rollback을 기록했다.
- [x] Python/CI/workflow/package/dispatch 권한 경계를 기록했다.
- [x] 여섯 관점 plan review와 main-session integration을 완료했다.
- [x] SPW-05 final read-back을 완료했다.
- [x] 계획과 통합 검토 문서를 같은 branch commit으로 묶었다.

최종 상태: `PLAN REVIEWED / USER EXECUTION CHOICE PENDING`
