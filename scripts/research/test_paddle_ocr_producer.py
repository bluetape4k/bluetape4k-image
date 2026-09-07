from __future__ import annotations

import hashlib
import io
import json
import os
import stat
import subprocess
import sys
import tarfile
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import paddle_ocr_producer as producer_cli
import paddle_ocr_producer_lib.filesystem as producer_filesystem
from paddle_ocr_producer_lib.contracts import (
    AttemptIdentity,
    ProducerValidationError,
    exact_object,
    jcs_bytes,
    load_json_bytes,
    load_jsonl_bytes,
    require_sha256,
    sha256_hex,
    validate_input_lock,
)
from paddle_ocr_producer_lib.filesystem import (
    ARCHIVE_LIMITS,
    ORAS_LINUX_AMD64_SHA256,
    ORAS_LINUX_AMD64_URL,
    ORAS_VERSION,
    ArchiveLimits,
    DownloadResponse,
    bootstrap_oras_archive,
    canonical_tree_manifest,
    extract_archive,
    fetch_to_regular_file,
    preflight_archive,
    tree_sha256,
    verify_regular_file,
)
from paddle_ocr_producer_lib.lifecycle import (
    STATUS_CONTRACT,
    append_revocations,
    apply_cleanup_outcome,
    initial_revocations,
    merge_cleanup_fragments,
    validate_cleanup_fragment,
    validate_emergency_receipt,
    validate_reconciliation,
    validate_revocation_successor,
)

SHA_A = "a" * 64
SHA_B = "b" * 64
SHA_C = "c" * 64


def valid_reconciliation(status: str = "PRODUCER_PASS") -> dict[str, object]:
    stage = STATUS_CONTRACT[status].allowed_stages[-1]
    package = {
        "package": "ghcr.io/bluetape4k/paddleocr-runtime",
        "versionId": 123,
        "attemptTag": "image-1234.2",
        "visibility": "private",
        "pullMode": "PACKAGES_READ",
        "pullVerified": True,
        "indexDigest": "sha256:" + SHA_A,
        "manifestDigest": "sha256:" + SHA_B,
        "configDigest": "sha256:" + SHA_C,
        "baseDigest": "sha256:" + "d" * 64,
    }
    staging = package if stage != "NONE" else None
    release = None
    evidence = None
    if stage in ("RELEASE", "PUBLIC_EVIDENCE"):
        release = dict(package)
        release.update({"visibility": "public", "pullMode": "ANONYMOUS"})
    if stage == "PUBLIC_EVIDENCE":
        evidence = {
            "package": package["package"],
            "versionId": 124,
            "attemptTag": "evidence-1234.2",
            "visibility": "public",
            "pullMode": "ANONYMOUS",
            "pullVerified": True,
            "artifactType": "application/vnd.bluetape4k.paddleocr.evidence.v1",
            "manifestDigest": "sha256:" + "e" * 64,
            "subjectDigest": release["manifestDigest"],
            "fileManifestSha256": "f" * 64,
        }
    terminal_incident = status in {
        "QUARANTINE_PENDING", "QUARANTINED", "REJECTED", "REVOKED",
        "FAILED", "CANCELLED", "INTERRUPTED",
    }
    return {
        "schemaVersion": 1,
        "attemptId": "1234.2",
        "producerStatus": status,
        "lastCompletedStage": stage,
        "mapped609Status": STATUS_CONTRACT[status].mapped_609_status,
        "inputLockSha256": SHA_A,
        "staging": staging,
        "release": release,
        "evidence": evidence,
        "replacesAttemptId": None,
        "replacedReleaseDigest": None,
        "replacedEvidenceDigest": None,
        "replacedAttemptSha256": None,
        "replacedEvidenceSha256": None,
        "replacedReconciliationSha256": None,
        "replacedCleanupSha256": None,
        "workflowRetryOrdinal": 1,
        "retryReceipts": [],
        "secretScan": "PASS" if status == "PRODUCER_PASS" else "NOT_RUN",
        "cleanupVerified": status == "PRODUCER_PASS",
        "denylistVerified": status == "PRODUCER_PASS",
        "visibilityReadBack": status == "PRODUCER_PASS",
        "downstreamReadBack": status == "PRODUCER_PASS",
        "revocationsSha256": SHA_B,
        "revocationsCommitSha": "1" * 40,
        "previousDocumentSha256": None,
        "statusChangedAt": "2026-09-07T01:00:00Z",
        "incidentUrl": "https://github.com/bluetape4k/bluetape4k-image/issues/638" if terminal_incident else None,
        "ownerAcknowledgedAt": "2026-09-07T01:01:00Z" if terminal_incident else None,
        "closedAt": None,
    }


def valid_input_lock() -> dict[str, object]:
    source_revision = "1" * 40
    source = {
        "id": "paddleocr",
        "url": "https://github.com/PaddlePaddle/PaddleOCR/archive/" + source_revision + ".tar.gz",
        "bytes": 123,
        "sha256": SHA_A,
        "mediaType": "application/gzip",
        "revision": source_revision,
    }
    paddlex_revision = "2" * 40
    paddlex = {
        "id": "paddlex",
        "url": "https://github.com/PaddlePaddle/PaddleX/archive/" + paddlex_revision + ".tar.gz",
        "bytes": 124,
        "sha256": SHA_B,
        "mediaType": "application/gzip",
        "revision": paddlex_revision,
    }
    model_common = {
        "sourceId": "paddleocr",
        "modelRevision": source_revision,
        "url": "https://paddle-model-ecology.bj.bcebos.com/model.tar",
        "bytes": 100,
        "sha256": SHA_C,
        "mediaType": "application/gzip",
        "archiveType": "tar.gz",
        "treeSha256": SHA_A,
        "licenseExpression": "Apache-2.0",
        "licenseSourcePath": "LICENSE",
        "licenseSha256": SHA_B,
        "noticePath": "NOTICE",
        "noticeSha256": SHA_C,
    }
    return {
        "schemaVersion": 1,
        "targetPlatform": "linux/amd64",
        "baseImage": {
            "reference": "docker.io/library/python@sha256:" + SHA_A,
            "indexDigest": "sha256:" + SHA_A,
            "platformDigest": "sha256:" + SHA_B,
            "configDigest": "sha256:" + SHA_C,
            "os": "linux",
            "architecture": "amd64",
            "variant": None,
        },
        "sources": [source, paddlex],
        "packages": [
            {
                "id": "paddleocr-wheel",
                "name": "paddleocr",
                "version": "3.2.0",
                "filename": "paddleocr-3.2.0-py3-none-any.whl",
                "wheelTags": ["py3-none-any"],
                "derivedSourceId": "paddleocr",
                "sourceRevision": source_revision,
                "bytes": 101,
                "sha256": SHA_A,
                "mediaType": "application/zip",
                "buildToolchain": {
                    "backend": "hatchling.build",
                    "backendVersion": "1.27.0",
                    "pythonVersion": "3.10.18",
                    "sourceDateEpoch": 1_750_000_000,
                    "artifacts": [
                        {
                            "name": "hatchling",
                            "version": "1.27.0",
                            "filename": "hatchling-1.27.0-py3-none-any.whl",
                            "url": "https://files.pythonhosted.org/packages/hatchling.whl",
                            "bytes": 102,
                            "sha256": SHA_B,
                        }
                    ],
                },
            }
        ],
        "models": [
            {"modelId": "PP-OCRv5_mobile_det", "role": "detector", **model_common},
            {"modelId": "PP-OCRv5_mobile_rec", "role": "recognizer", **model_common},
        ],
        "legalInventorySha256": SHA_A,
    }


class StrictContractTest(unittest.TestCase):
    def test_duplicate_key_fails_closed(self) -> None:
        with self.assertRaisesRegex(ProducerValidationError, "duplicate JSON key"):
            load_json_bytes(b'{"schemaVersion":1,"schemaVersion":1}', 1024)

    def test_size_bom_trailing_and_root_type_fail_closed(self) -> None:
        cases = (
            (b'{"a":1}', 6, "exceeds"),
            (b'\xef\xbb\xbf{"a":1}', 1024, "BOM"),
            (b'{"a":1} trailing', 1024, "malformed JSON"),
            (b'[1]', 1024, "root must be an object"),
        )
        for payload, limit, message in cases:
            with self.subTest(message=message), self.assertRaisesRegex(
                ProducerValidationError, message
            ):
                load_json_bytes(payload, limit)

    def test_depth_and_entry_limits_are_exact(self) -> None:
        self.assertEqual(
            load_json_bytes(b'{"a":{"b":1}}', 1024, max_depth=3, max_entries=2),
            {"a": {"b": 1}},
        )
        with self.assertRaisesRegex(ProducerValidationError, "depth"):
            load_json_bytes(b'{"a":{"b":1}}', 1024, max_depth=2, max_entries=2)
        with self.assertRaisesRegex(ProducerValidationError, "entries"):
            load_json_bytes(b'{"a":{"b":1}}', 1024, max_depth=3, max_entries=1)

    def test_exact_object_reports_sorted_missing_and_unknown_keys(self) -> None:
        with self.assertRaisesRegex(
            ProducerValidationError,
            r"missing keys: a, z; unexpected keys: b, y",
        ):
            exact_object({"b": 1, "y": 2}, required={"z", "a"})

    def test_jcs_orders_keys_and_rejects_float(self) -> None:
        self.assertEqual(jcs_bytes({"b": 2, "a": 1}), b'{"a":1,"b":2}')
        self.assertEqual(
            jcs_bytes({"emoji": "한글", "items": [True, None, -2]}),
            '{"emoji":"한글","items":[true,null,-2]}'.encode(),
        )
        with self.assertRaisesRegex(ProducerValidationError, "integer"):
            jcs_bytes({"a": 1.0})

    def test_jcs_rejects_non_string_key_and_out_of_range_integer(self) -> None:
        for value, message in (
            ({1: "a"}, "string"),
            ({"a": 2**63}, "safe integer"),
        ):
            with self.subTest(value=value), self.assertRaisesRegex(
                ProducerValidationError, message
            ):
                jcs_bytes(value)

    def test_sha256_helpers_use_prefix_free_lowercase_hex(self) -> None:
        digest = hashlib.sha256(b"payload").hexdigest()
        self.assertEqual(sha256_hex(b"payload"), digest)
        self.assertEqual(require_sha256(digest), digest)
        for invalid in ("sha256:" + digest, digest.upper(), "0" * 63):
            with self.subTest(invalid=invalid), self.assertRaises(
                ProducerValidationError
            ):
                require_sha256(invalid)

    def test_attempt_identity_is_derived(self) -> None:
        identity = AttemptIdentity.from_run(1234, 2)
        self.assertEqual(identity.attempt_id, "1234.2")
        self.assertEqual(identity.image_tag, "image-1234.2")
        self.assertEqual(identity.evidence_tag, "evidence-1234.2")

    def test_attempt_identity_rejects_bool_zero_and_caller_tag(self) -> None:
        for run_id, run_attempt in ((True, 1), (0, 1), (1, False), (1, 0)):
            with self.subTest(run_id=run_id, run_attempt=run_attempt), self.assertRaises(
                ProducerValidationError
            ):
                AttemptIdentity.from_run(run_id, run_attempt)

    def test_jsonl_preserves_raw_bytes_and_validates_line_contract(self) -> None:
        raw = b'{"payload":"first"}\n{"payload":"second"}\n'
        parsed = load_jsonl_bytes(raw, 1024, max_lines=2)
        self.assertEqual(parsed.raw_bytes, raw)
        self.assertEqual(parsed.envelopes[1], {"payload": "second"})
        for invalid, message in (
            (b'\xef\xbb\xbf{}\n', "BOM"),
            (b'{}\r\n', "CR"),
            (b'{}\n\n', "blank"),
            (b'{}', "LF"),
        ):
            with self.subTest(message=message), self.assertRaisesRegex(
                ProducerValidationError, message
            ):
                load_jsonl_bytes(invalid, 1024, max_lines=2)

    def test_validation_error_never_echoes_secret_payload(self) -> None:
        secret = "ghp_super_secret_token"
        with self.assertRaises(ProducerValidationError) as raised:
            load_json_bytes((secret + " not-json").encode(), 1024)
        self.assertNotIn(secret, str(raised.exception))


class InputLockTest(unittest.TestCase):
    def test_complete_input_lock_is_accepted(self) -> None:
        validated = validate_input_lock(
            valid_input_lock(),
            allowed_hosts={"github.com", "paddle-model-ecology.bj.bcebos.com", "files.pythonhosted.org"},
        )
        self.assertEqual(validated["targetPlatform"], "linux/amd64")

    def test_input_lock_requires_two_complete_model_roles(self) -> None:
        lock = valid_input_lock()
        lock["models"] = [lock["models"][0]]  # type: ignore[index]
        with self.assertRaisesRegex(ProducerValidationError, "detector and recognizer"):
            validate_input_lock(
                lock,
                allowed_hosts={"github.com", "paddle-model-ecology.bj.bcebos.com", "files.pythonhosted.org"},
            )

    def test_input_lock_rejects_pending_unknown_and_revision_drift(self) -> None:
        for mutate, message in (
            (lambda lock: lock.update({"unexpected": True}), "unexpected keys"),
            (lambda lock: lock["models"][0].update({"licenseExpression": "PENDING"}), "PENDING"),  # type: ignore[index]
            (lambda lock: lock["models"][0].update({"modelRevision": "3" * 40}), "modelRevision"),  # type: ignore[index]
        ):
            lock = valid_input_lock()
            mutate(lock)
            with self.subTest(message=message), self.assertRaisesRegex(
                ProducerValidationError, message
            ):
                validate_input_lock(
                    lock,
                    allowed_hosts={"github.com", "paddle-model-ecology.bj.bcebos.com", "files.pythonhosted.org"},
                )

    def test_source_date_epoch_is_nested_and_unique(self) -> None:
        lock = validate_input_lock(
            valid_input_lock(),
            allowed_hosts={"github.com", "paddle-model-ecology.bj.bcebos.com", "files.pythonhosted.org"},
        )
        package = lock["packages"][0]
        self.assertEqual(package["buildToolchain"]["sourceDateEpoch"], 1_750_000_000)

    def test_registry_and_derived_packages_are_distinct_variants(self) -> None:
        lock = valid_input_lock()
        lock["packages"].append(  # type: ignore[union-attr]
            {
                "id": "numpy-wheel",
                "name": "numpy",
                "version": "2.2.6",
                "filename": "numpy-2.2.6-cp310-cp310-manylinux2014_x86_64.whl",
                "wheelTags": ["cp310-cp310-manylinux2014_x86_64"],
                "url": "https://files.pythonhosted.org/packages/numpy.whl",
                "bytes": 102,
                "sha256": SHA_B,
                "mediaType": "application/zip",
            }
        )
        validated = validate_input_lock(
            lock,
            allowed_hosts={
                "github.com",
                "paddle-model-ecology.bj.bcebos.com",
                "files.pythonhosted.org",
            },
        )
        self.assertEqual(validated["packages"][1]["name"], "numpy")

        mixed = valid_input_lock()
        mixed["packages"][0]["url"] = "https://files.pythonhosted.org/packages/paddleocr.whl"  # type: ignore[index]
        with self.assertRaisesRegex(ProducerValidationError, "registry or derived"):
            validate_input_lock(
                mixed,
                allowed_hosts={
                    "github.com",
                    "paddle-model-ecology.bj.bcebos.com",
                    "files.pythonhosted.org",
                },
            )

    def test_derived_package_requires_immutable_build_tool_artifacts(self) -> None:
        lock = valid_input_lock()
        lock["packages"][0]["buildToolchain"].pop("artifacts")  # type: ignore[index]
        with self.assertRaisesRegex(ProducerValidationError, "buildToolchain"):
            validate_input_lock(
                lock,
                allowed_hosts={
                    "github.com",
                    "paddle-model-ecology.bj.bcebos.com",
                    "files.pythonhosted.org",
                },
            )


class FilesystemBoundaryTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)

    def tearDown(self) -> None:
        self.temp.cleanup()

    def test_model_archive_mismatch_is_blocked_input(self) -> None:
        archive = self.root / "model.tar"
        archive.write_bytes(b"archive")
        with self.assertRaisesRegex(ProducerValidationError, "archive sha256 differs"):
            verify_regular_file(archive, expected_bytes=7, expected_sha256="0" * 64)

    def _tar(self, name: str, payload: bytes, *, kind: str = "file") -> Path:
        path = self.root / (name.replace("/", "_") + ".tar.gz")
        with tarfile.open(path, "w:gz") as archive:
            info = tarfile.TarInfo(name)
            if kind == "symlink":
                info.type = tarfile.SYMTYPE
                info.linkname = "target"
                archive.addfile(info)
            elif kind == "hardlink":
                info.type = tarfile.LNKTYPE
                info.linkname = "target"
                archive.addfile(info)
            else:
                info.size = len(payload)
                archive.addfile(info, io.BytesIO(payload))
        return path

    def test_archive_preflight_rejects_traversal_link_nested_and_bomb(self) -> None:
        fixtures = (
            self._tar("/absolute", b"x"),
            self._tar("../parent", b"x"),
            self._tar("link", b"", kind="symlink"),
            self._tar("hard", b"", kind="hardlink"),
            self._tar("nested.tar", b"x"),
            self._tar("ratio.txt", b"0" * 50_000),
        )
        limits = ArchiveLimits(
            max_expanded_bytes=1_000_000,
            max_files=10,
            max_depth=4,
            max_path_bytes=100,
            max_compression_ratio=10,
            deadline_seconds=10,
        )
        for fixture in fixtures:
            with self.subTest(fixture=fixture.name), self.assertRaises(
                ProducerValidationError
            ):
                preflight_archive(fixture, limits)

    def test_archive_preflight_accepts_bounded_regular_files(self) -> None:
        archive = self._tar("model/inference.json", b"{}")
        entries = preflight_archive(archive, ARCHIVE_LIMITS)
        self.assertEqual([entry.path for entry in entries], ["model/inference.json"])

    def test_oras_bootstrap_pins_distribution_allowlist_and_modes(self) -> None:
        self.assertEqual(ORAS_VERSION, "1.3.4")
        self.assertEqual(
            ORAS_LINUX_AMD64_URL,
            "https://github.com/oras-project/oras/releases/download/v1.3.4/"
            "oras_1.3.4_linux_amd64.tar.gz",
        )
        self.assertEqual(
            ORAS_LINUX_AMD64_SHA256,
            "f27adb935022d94df8dc77719c322dda592c78a0d57a6f7dcdd8d900b248c454",
        )
        archive = self.root / "oras.tar.gz"
        with tarfile.open(archive, "w:gz") as stream:
            for name, payload in (("oras", b"binary"), ("LICENSE", b"license"), ("README.md", b"readme")):
                info = tarfile.TarInfo(name)
                info.size = len(payload)
                stream.addfile(info, io.BytesIO(payload))
        digest = hashlib.sha256(archive.read_bytes()).hexdigest()
        tool_root = self.root / "oras-tool"
        with patch.object(producer_filesystem, "ORAS_LINUX_AMD64_SHA256", digest):
            oras_bin = bootstrap_oras_archive(archive, tool_root)
        self.assertEqual(oras_bin, tool_root / "oras")
        self.assertEqual(stat.S_IMODE(tool_root.stat().st_mode), 0o700)
        self.assertEqual(stat.S_IMODE(oras_bin.stat().st_mode), 0o700)
        self.assertEqual(oras_bin.read_bytes(), b"binary")

        invalid = self.root / "oras-invalid.tar.gz"
        with tarfile.open(invalid, "w:gz") as stream:
            for name in ("oras", "unexpected.txt"):
                info = tarfile.TarInfo(name)
                info.size = 1
                stream.addfile(info, io.BytesIO(b"x"))
        invalid_digest = hashlib.sha256(invalid.read_bytes()).hexdigest()
        with (
            patch.object(producer_filesystem, "ORAS_LINUX_AMD64_SHA256", invalid_digest),
            self.assertRaisesRegex(ProducerValidationError, "allowlist"),
        ):
            bootstrap_oras_archive(invalid, self.root / "invalid-tool")
        self.assertFalse((self.root / "invalid-tool").exists())

    def test_allowlisted_fetcher_rejects_private_and_rebound_address(self) -> None:
        destination = self.root / "download.bin"

        def resolver(_: str) -> tuple[str, ...]:
            return ("93.184.216.34",)

        class ReboundTransport:
            def request(self, *args: object, **kwargs: object) -> DownloadResponse:
                return DownloadResponse(200, {}, (b"payload",), "169.254.169.254")

        source = {
            "url": "https://allowed.example/input",
            "bytes": 7,
            "sha256": hashlib.sha256(b"payload").hexdigest(),
        }
        with self.assertRaisesRegex(ProducerValidationError, "public allowlisted address"):
            fetch_to_regular_file(
                source,
                destination,
                allowed_hosts={"allowed.example"},
                resolver=resolver,
                transport=ReboundTransport(),
            )
        self.assertFalse(destination.exists())

    def test_allowlisted_fetcher_ignores_proxy_environment(self) -> None:
        destination = self.root / "download.bin"
        seen: list[tuple[str, tuple[str, ...]]] = []

        def resolver(_: str) -> tuple[str, ...]:
            return ("93.184.216.34",)

        class Transport:
            def request(
                self, url: str, addresses: tuple[str, ...], **_: object
            ) -> DownloadResponse:
                seen.append((url, addresses))
                return DownloadResponse(200, {}, (b"payload",), addresses[0])

        source = {
            "url": "https://allowed.example/input",
            "bytes": 7,
            "sha256": hashlib.sha256(b"payload").hexdigest(),
        }
        with patch.dict(os.environ, {"HTTPS_PROXY": "http://127.0.0.1:9"}):
            receipt = fetch_to_regular_file(
                source,
                destination,
                allowed_hosts={"allowed.example"},
                resolver=resolver,
                transport=Transport(),
            )
        self.assertEqual(receipt.sha256, source["sha256"])
        self.assertEqual(seen, [(source["url"], ("93.184.216.34",))])

    def test_fetcher_closes_redirects_and_retries_only_transient_failures(self) -> None:
        destination = self.root / "download.bin"
        events: list[str] = []
        attempts = iter(
            (
                DownloadResponse(503, {}, (), "93.184.216.34", lambda: events.append("503-closed")),
                DownloadResponse(
                    302,
                    {"location": "https://allowed.example/final"},
                    (),
                    "93.184.216.34",
                    lambda: events.append("302-closed"),
                ),
                DownloadResponse(
                    200,
                    {},
                    (b"payload",),
                    "93.184.216.34",
                    lambda: events.append("200-closed"),
                ),
            )
        )

        class Transport:
            def request(self, *_: object, **__: object) -> DownloadResponse:
                return next(attempts)

        receipt = fetch_to_regular_file(
            {
                "id": "download",
                "url": "https://allowed.example/input",
                "bytes": 7,
                "sha256": hashlib.sha256(b"payload").hexdigest(),
            },
            destination,
            allowed_hosts={"allowed.example"},
            resolver=lambda _: ("93.184.216.34",),
            transport=Transport(),
            sleeper=lambda seconds: events.append(f"sleep-{seconds}"),
        )
        self.assertEqual(receipt.attempts, 2)
        self.assertEqual(receipt.retry_delays, (2,))
        self.assertEqual(
            events,
            ["503-closed", "sleep-2", "302-closed", "200-closed"],
        )

    def test_fetcher_does_not_retry_permanent_status(self) -> None:
        destination = self.root / "download.bin"
        calls = 0

        class Transport:
            def request(self, *_: object, **__: object) -> DownloadResponse:
                nonlocal calls
                calls += 1
                return DownloadResponse(404, {}, (), "93.184.216.34")

        with self.assertRaisesRegex(ProducerValidationError, "permanent"):
            fetch_to_regular_file(
                {
                    "id": "download",
                    "url": "https://allowed.example/input",
                    "bytes": 7,
                    "sha256": hashlib.sha256(b"payload").hexdigest(),
                },
                destination,
                allowed_hosts={"allowed.example"},
                resolver=lambda _: ("93.184.216.34",),
                transport=Transport(),
                sleeper=lambda _: self.fail("permanent failures must not sleep"),
            )
        self.assertEqual(calls, 1)
        self.assertFalse(destination.exists())

    def test_extract_archive_is_root_pinned_and_tree_hash_is_canonical(self) -> None:
        archive = self.root / "model.tar.gz"
        with tarfile.open(archive, "w:gz") as stream:
            for name, payload in (
                ("upstream/inference.yml", b"yml"),
                ("upstream/inference.json", b"json"),
            ):
                info = tarfile.TarInfo(name)
                info.size = len(payload)
                stream.addfile(info, io.BytesIO(payload))
        output = self.root / "model"
        extract_archive(archive, output, ARCHIVE_LIMITS, strip_single_root=True)
        expected = (
            "inference.json\t4\t" + hashlib.sha256(b"json").hexdigest() + "\n"
            "inference.yml\t3\t" + hashlib.sha256(b"yml").hexdigest() + "\n"
        ).encode()
        self.assertEqual(canonical_tree_manifest(output), expected)
        self.assertEqual(tree_sha256(output), hashlib.sha256(expected).hexdigest())


class ProducerCliTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.script = Path(__file__).with_name("paddle_ocr_producer.py")

    def tearDown(self) -> None:
        self.temp.cleanup()

    def _run(self, *args: str) -> subprocess.CompletedProcess[bytes]:
        return subprocess.run(
            [sys.executable, str(self.script), *args],
            cwd=Path(__file__).resolve().parents[2],
            capture_output=True,
            check=False,
        )

    def test_public_command_runner_enforces_output_bound_and_has_no_credential_options(self) -> None:
        output = producer_cli._run_bounded_command(
            [sys.executable, "-c", "import sys; sys.stdout.buffer.write(b'abc')"],
            3,
            5,
        )
        self.assertEqual(output, b"abc")
        with self.assertRaisesRegex(producer_cli.ProducerRejectedError, "byte limit"):
            producer_cli._run_bounded_command(
                [sys.executable, "-c", "import sys; sys.stdout.buffer.write(b'abcd')"],
                3,
                5,
            )
        parser = producer_cli.build_parser()
        subparsers = next(
            action for action in parser._actions
            if isinstance(action, producer_cli.argparse._SubParsersAction)
        )
        public_parser = subparsers.choices["verify-public-evidence"]
        options = {option for action in public_parser._actions for option in action.option_strings}
        self.assertTrue({"--oras-bin", "--gh-bin", "--ref", "--root"}.issubset(options))
        self.assertTrue({"--token", "--username", "--password", "--registry-config"}.isdisjoint(options))

    def _write_inputs(self) -> tuple[Path, Path, Path]:
        license_file = self.root / "LICENSE"
        license_file.write_bytes(b"license evidence")
        notice_file = self.root / "NOTICE"
        notice_file.write_bytes(b"model notice")
        notice = "PaddlePaddle PP-OCRv5_mobile_det"
        legal = self.root / "legal.json"
        legal.write_bytes(
            jcs_bytes(
                {
                    "schemaVersion": 1,
                    "components": [
                        {
                            "id": "model-detector",
                            "licenseExpression": "Apache-2.0",
                            "licenseSourceUrl": "https://huggingface.co/PaddlePaddle/PP-OCRv5_mobile_det",
                            "licenseSha256": SHA_A,
                            "notice": notice,
                            "noticeSha256": hashlib.sha256(notice.encode()).hexdigest(),
                        }
                    ],
                }
            )
        )
        lock_value = valid_input_lock()
        for model in lock_value["models"]:  # type: ignore[union-attr]
            model["licenseSourcePath"] = "LICENSE"
            model["licenseSha256"] = hashlib.sha256(license_file.read_bytes()).hexdigest()
            model["noticePath"] = "NOTICE"
            model["noticeSha256"] = hashlib.sha256(notice_file.read_bytes()).hexdigest()
        lock_value["legalInventorySha256"] = hashlib.sha256(legal.read_bytes()).hexdigest()
        lock = self.root / "lock.json"
        lock.write_bytes(jcs_bytes(lock_value))
        policy = self.root / "policy.json"
        policy.write_bytes(
            jcs_bytes(
                {
                    "schemaVersion": 1,
                    "repositories": ["bluetape4k/bluetape4k-image"],
                    "workflows": [".github/workflows/paddleocr-producer.yml"],
                    "refs": ["refs/heads/develop"],
                    "actors": ["debop"],
                    "runnerEnvironments": ["github-hosted"],
                    "oidcIssuers": ["https://token.actions.githubusercontent.com"],
                    "audiences": ["sigstore"],
                    "hosts": [
                        "files.pythonhosted.org",
                        "github.com",
                        "paddle-model-ecology.bj.bcebos.com",
                    ],
                }
            )
        )
        return lock, policy, legal

    def test_validate_inputs_emits_one_canonical_result_line(self) -> None:
        lock, policy, legal = self._write_inputs()
        result = self._run(
            "validate-inputs",
            "--lock",
            str(lock),
            "--policy",
            str(policy),
            "--legal",
            str(legal),
        )
        self.assertEqual(result.returncode, 0, (result.stdout + result.stderr).decode())
        self.assertEqual(result.stdout.count(b"\n"), 1)
        document = json.loads(result.stdout)
        self.assertEqual(document["command"], "validate-inputs")
        self.assertEqual(document["status"], "PASS")
        self.assertEqual(document["data"]["legalInventorySha256"], hashlib.sha256(legal.read_bytes()).hexdigest())

    def test_validate_inputs_rejects_tampered_model_legal_file(self) -> None:
        lock, policy, legal = self._write_inputs()
        (self.root / "NOTICE").write_bytes(b"tampered")
        result = self._run(
            "validate-inputs",
            "--lock",
            str(lock),
            "--policy",
            str(policy),
            "--legal",
            str(legal),
        )
        self.assertEqual(result.returncode, 11)
        self.assertEqual(json.loads(result.stdout)["status"], "BLOCKED_LEGAL_INVENTORY")

    def test_input_value_reads_nested_source_date_epoch_atomically(self) -> None:
        lock, _, _ = self._write_inputs()
        output = self.root / "epoch.json"
        result = self._run(
            "input-value",
            "--lock",
            str(lock),
            "--field",
            "sourceDateEpoch",
            "--output",
            str(output),
        )
        self.assertEqual(result.returncode, 0, result.stderr.decode())
        document = json.loads(output.read_bytes())
        self.assertEqual(document["data"]["sourceDateEpoch"], 1_750_000_000)
        self.assertNotIn("value", document["data"])

    def test_accept_resolved_inputs_validates_before_writing(self) -> None:
        lock, _, legal = self._write_inputs()
        resolution = self.root / "resolution"
        resolution.mkdir()
        (resolution / "producer-input-candidate.json").write_bytes(lock.read_bytes())
        (resolution / "legal-inventory.json").write_bytes(legal.read_bytes())
        (resolution / "LICENSE").write_bytes((self.root / "LICENSE").read_bytes())
        (resolution / "NOTICE").write_bytes((self.root / "NOTICE").read_bytes())
        (resolution / "requirements.cpu.lock.txt").write_text(
            f"paddleocr==3.2.0 --hash=sha256:{SHA_A}\n",
            encoding="utf-8",
        )
        target = self.root / "accepted"
        result = self._run(
            "accept-resolved-inputs",
            "--resolution-root",
            str(resolution),
            "--input-lock",
            str(target / "producer-input.lock.json"),
            "--requirements-lock",
            str(target / "requirements.cpu.lock.txt"),
            "--legal-inventory",
            str(target / "legal-inventory.json"),
        )
        self.assertEqual(result.returncode, 0, result.stderr.decode())
        document = json.loads(result.stdout)
        self.assertEqual(document["data"]["inputLockSha256"], hashlib.sha256(lock.read_bytes()).hexdigest())
        self.assertEqual((target / "producer-input.lock.json").read_bytes(), lock.read_bytes())

    def test_accept_resolved_inputs_rejects_unhashed_requirement_without_writes(self) -> None:
        lock, _, legal = self._write_inputs()
        resolution = self.root / "invalid-resolution"
        resolution.mkdir()
        (resolution / "producer-input-candidate.json").write_bytes(lock.read_bytes())
        (resolution / "legal-inventory.json").write_bytes(legal.read_bytes())
        (resolution / "LICENSE").write_bytes((self.root / "LICENSE").read_bytes())
        (resolution / "NOTICE").write_bytes((self.root / "NOTICE").read_bytes())
        (resolution / "requirements.cpu.lock.txt").write_text(
            "paddleocr==3.2.0\n",
            encoding="utf-8",
        )
        target = self.root / "rejected"
        result = self._run(
            "accept-resolved-inputs",
            "--resolution-root",
            str(resolution),
            "--input-lock",
            str(target / "producer-input.lock.json"),
            "--requirements-lock",
            str(target / "requirements.cpu.lock.txt"),
            "--legal-inventory",
            str(target / "legal-inventory.json"),
        )
        self.assertEqual(result.returncode, 10)
        self.assertFalse(target.exists())

    def test_verify_source_reproducibility_accepts_two_exact_prebuilt_wheels(self) -> None:
        lock, _, _ = self._write_inputs()
        value = json.loads(lock.read_bytes())
        payload = b"reproducible-wheel"
        package = value["packages"][0]
        package["bytes"] = len(payload)
        package["sha256"] = hashlib.sha256(payload).hexdigest()
        package["filename"] = "paddleocr-3.2.0-py3-none-any.whl"
        lock.write_bytes(jcs_bytes(value))
        first = self.root / "first.whl"
        second = self.root / "second.whl"
        first.write_bytes(payload)
        second.write_bytes(payload)
        output = self.root / "wheelhouse"
        result = self._run(
            "verify-source-reproducibility",
            "--input-lock",
            str(lock),
            "--work-root",
            str(self.root / "work"),
            "--output-wheelhouse",
            str(output),
            "--prebuilt-wheel",
            str(first),
            "--prebuilt-wheel",
            str(second),
        )
        self.assertEqual(result.returncode, 0, result.stderr.decode())
        document = json.loads(result.stdout)
        self.assertEqual(document["data"]["sourceDateEpoch"], 1_750_000_000)
        self.assertEqual((output / package["filename"]).read_bytes(), payload)

    def test_verify_source_reproducibility_rejects_different_builds(self) -> None:
        lock, _, _ = self._write_inputs()
        first = self.root / "first-different.whl"
        second = self.root / "second-different.whl"
        first.write_bytes(b"a" * 101)
        second.write_bytes(b"b" * 101)
        output = self.root / "rejected-wheelhouse"
        result = self._run(
            "verify-source-reproducibility",
            "--input-lock",
            str(lock),
            "--work-root",
            str(self.root / "work-different"),
            "--output-wheelhouse",
            str(output),
            "--prebuilt-wheel",
            str(first),
            "--prebuilt-wheel",
            str(second),
        )
        self.assertEqual(result.returncode, 22)
        self.assertFalse(output.exists())

    def test_stage_inputs_recomputes_model_tree_and_pair_hash(self) -> None:
        lock, _, legal = self._write_inputs()
        value = json.loads(lock.read_bytes())
        wheel_payload = b"locked-wheel"
        package = value["packages"][0]
        package["bytes"] = len(wheel_payload)
        package["sha256"] = hashlib.sha256(wheel_payload).hexdigest()
        wheelhouse = self.root / "verified-wheelhouse"
        wheelhouse.mkdir()
        (wheelhouse / package["filename"]).write_bytes(wheel_payload)
        archives: list[Path] = []
        for model in value["models"]:
            role = model["role"]
            archive = self.root / f"{role}.tar.gz"
            payload = role.encode()
            with tarfile.open(archive, "w:gz") as stream:
                info = tarfile.TarInfo(f"upstream/{role}.bin")
                info.size = len(payload)
                stream.addfile(info, io.BytesIO(payload))
            manifest = (
                f"{role}.bin\t{len(payload)}\t{hashlib.sha256(payload).hexdigest()}\n"
            ).encode()
            model["bytes"] = archive.stat().st_size
            model["sha256"] = hashlib.sha256(archive.read_bytes()).hexdigest()
            model["treeSha256"] = hashlib.sha256(manifest).hexdigest()
            archives.append(archive)
        lock.write_bytes(jcs_bytes(value))
        requirements = self.root / "requirements.lock.txt"
        requirements.write_text(
            f"paddleocr==3.2.0 --hash=sha256:{package['sha256']}\n",
            encoding="utf-8",
        )
        output = self.root / "staged-inputs"
        result = self._run(
            "stage-inputs",
            "--input-lock",
            str(lock),
            "--requirements-lock",
            str(requirements),
            "--legal",
            str(legal),
            "--wheelhouse",
            str(wheelhouse),
            "--model-archive",
            f"detector={archives[0]}",
            "--model-archive",
            f"recognizer={archives[1]}",
            "--output-artifact",
            str(output),
        )
        self.assertEqual(result.returncode, 0, result.stderr.decode())
        document = json.loads(result.stdout)
        self.assertEqual(set(document["data"]["modelTreeDigests"]), {"detector", "recognizer"})
        self.assertTrue((output / "models/detector/detector.bin").is_file())
        self.assertTrue((output / "models/recognizer/recognizer.bin").is_file())

    def test_resolve_inputs_rejects_source_revision_drift_before_output(self) -> None:
        lock, _, legal = self._write_inputs()
        accepted = self.root / "accepted-root"
        accepted.mkdir()
        (accepted / "producer-input.lock.json").write_bytes(lock.read_bytes())
        (accepted / "legal-inventory.json").write_bytes(legal.read_bytes())
        (accepted / "requirements.cpu.lock.txt").write_text(
            f"paddleocr==3.2.0 --hash=sha256:{SHA_A}\n",
            encoding="utf-8",
        )
        output = self.root / "resolution-output"
        result = self._run(
            "resolve-inputs",
            "--target-platform",
            "linux/amd64",
            "--python-version",
            "3.10",
            "--base-image-candidate",
            "docker.io/library/python:3.10-slim",
            "--paddleocr-commit",
            "f" * 40,
            "--paddlex-commit",
            "2" * 40,
            "--paddle-commit",
            "3" * 40,
            "--detector",
            "PP-OCRv5_mobile_det",
            "--recognizer",
            "PP-OCRv5_mobile_rec",
            "--accepted-root",
            str(accepted),
            "--output-root",
            str(output),
        )
        self.assertEqual(result.returncode, 10)
        self.assertFalse(output.exists())

    def test_lifecycle_cli_merges_cleanup_reconciles_and_appends_revocations(self) -> None:
        fragment_paths = []
        for job, digest in (("validation", SHA_A), ("staging", SHA_B)):
            path = self.root / f"{job}.json"
            path.write_bytes(jcs_bytes({
                "schemaVersion": 1,
                "attemptId": "1234.2",
                "jobId": job,
                "originalProducerStatus": "FAILED",
                "startedAt": "2026-09-07T01:00:00Z",
                "finishedAt": "2026-09-07T01:01:00Z",
                "targets": [{"kind": "TEMP_DIRECTORY", "idSha256": digest, "result": "REMOVED"}],
            }))
            fragment_paths.append(path)
        cleanup = self.root / "cleanup.json"
        result = self._run(
            "merge-cleanup", "--attempt-id", "1234.2", "--original-status", "FAILED",
            "--started-job", "validation", "--started-job", "staging",
            "--fragment", str(fragment_paths[0]), "--fragment", str(fragment_paths[1]),
            "--merged-at", "2026-09-07T01:02:00Z", "--output", str(cleanup),
        )
        self.assertEqual(result.returncode, 0, result.stderr.decode())
        self.assertTrue(json.loads(cleanup.read_bytes())["cleanupVerified"])

        reconciliation = self.root / "reconciliation.json"
        reconciliation.write_bytes(jcs_bytes(valid_reconciliation("PRODUCER_PASS")))
        result = self._run("reconcile", "--reconciliation", str(reconciliation))
        self.assertEqual(result.returncode, 0, result.stderr.decode())
        self.assertEqual(json.loads(result.stdout)["data"]["producerStatus"], "PRODUCER_PASS")
        malformed = self.root / "malformed-reconciliation.json"
        malformed.write_bytes(reconciliation.read_bytes() + b"\n")
        result = self._run("reconcile", "--reconciliation", str(malformed))
        self.assertEqual(result.returncode, 40)
        self.assertEqual(json.loads(result.stdout)["status"], "SCHEMA_INVALID")

        previous = self.root / "previous.json"
        previous.write_bytes(jcs_bytes(initial_revocations()))
        entries = []
        for kind, digest in (("IMAGE", SHA_A), ("EVIDENCE", SHA_B)):
            path = self.root / f"{kind.lower()}.json"
            path.write_bytes(jcs_bytes({
                "incidentId": "638",
                "artifactKind": kind,
                "digest": "sha256:" + digest,
                "reasonCode": "PUBLIC_VERIFICATION_FAILED",
                "incidentUrl": "https://github.com/bluetape4k/bluetape4k-image/issues/638",
                "issuedAt": "2026-09-07T01:00:00Z",
                "acknowledgedAt": "2026-09-07T01:01:00Z",
                "signer": "repo:bluetape4k/bluetape4k-image:ref:refs/heads/develop",
            }))
            entries.append(path)
        candidate = self.root / "candidate.json"
        result = self._run(
            "append-revocation", "--previous", str(previous),
            "--entry", str(entries[0]), "--entry", str(entries[1]), "--output", str(candidate),
        )
        self.assertEqual(result.returncode, 0, result.stderr.decode())
        self.assertEqual(len(json.loads(candidate.read_bytes())["digests"]), 2)

    def test_verify_attempt_emits_exact_result_and_rejects_cross_document_identity(self) -> None:
        bundle = self.root / "bundle"
        fragments_dir = bundle / "cleanup-fragments"
        fragments_dir.mkdir(parents=True)
        fragment = {
            "schemaVersion": 1,
            "attemptId": "1234.2",
            "jobId": "validation",
            "originalProducerStatus": "PRODUCER_PASS",
            "startedAt": "2026-09-07T01:00:00Z",
            "finishedAt": "2026-09-07T01:01:00Z",
            "targets": [{"kind": "TEMP_DIRECTORY", "idSha256": SHA_A, "result": "REMOVED"}],
        }
        fragment_path = fragments_dir / "validation.json"
        fragment_path.write_bytes(jcs_bytes(fragment))
        cleanup_value = merge_cleanup_fragments(
            "1234.2", "PRODUCER_PASS", ["validation"], [fragment],
            merged_at="2026-09-07T01:02:00Z",
        )
        cleanup_path = bundle / "cleanup.json"
        cleanup_path.write_bytes(jcs_bytes(cleanup_value))
        evidence_path = bundle / "producer-evidence.json"
        revocations_path = bundle / "revocations.json"
        revocations_path.write_bytes(jcs_bytes(initial_revocations()))
        reconciliation_value = valid_reconciliation("PRODUCER_PASS")
        reconciliation_value["revocationsSha256"] = sha256_hex(revocations_path.read_bytes())
        reconciliation_path = bundle / "reconciliation.json"
        reconciliation_path.write_bytes(jcs_bytes(reconciliation_value))
        artifact = {"path": "artifact.json", "bytes": 1, "sha256": SHA_A}
        attestation = {
            "path": "attestations/provenance.bundle.jsonl",
            "bytes": 1,
            "sha256": SHA_B,
            "predicateType": "https://slsa.dev/provenance/v1",
            "subjectDigest": reconciliation_value["release"]["manifestDigest"],
            "signer": "bluetape4k/bluetape4k-image/.github/workflows/paddleocr-producer.yml",
            "issuer": "https://token.actions.githubusercontent.com",
            "verified": True,
        }
        sbom_attestation = dict(attestation)
        sbom_attestation.update({
            "path": "attestations/sbom.bundle.jsonl",
            "predicateType": "https://spdx.dev/Document/v2.3",
        })
        evidence_value = {
            "schemaVersion": 1,
            "attemptId": "1234.2",
            "producerStatus": "PRODUCER_PASS",
            "lastCompletedStage": "PUBLIC_EVIDENCE",
            "inputLockSha256": SHA_A,
            "staging": artifact,
            "release": artifact,
            "payload": {
                "packageLock": artifact,
                "detectorModel": artifact,
                "recognizerModel": artifact,
                "legalInventory": artifact,
                "spdx": artifact,
                "provenanceAttestation": attestation,
                "sbomAttestation": sbom_attestation,
            },
            "evidenceSubjectDigest": reconciliation_value["release"]["manifestDigest"],
        }
        evidence_path.write_bytes(jcs_bytes(evidence_value))
        ledger_value = {
            "schemaVersion": 1,
            "attemptId": "1234.2",
            "producerStatus": "PRODUCER_PASS",
            "lastCompletedStage": "PUBLIC_EVIDENCE",
            "mapped609Status": "PENDING",
            "inputLockSha256": SHA_A,
            "imagePlatformDigest": reconciliation_value["release"]["manifestDigest"],
            "evidenceSha256": sha256_hex(evidence_path.read_bytes()),
            "reconciliationSha256": sha256_hex(reconciliation_path.read_bytes()),
            "cleanupSha256": sha256_hex(cleanup_path.read_bytes()),
            "revocationsSha256": sha256_hex(revocations_path.read_bytes()),
            "revocationsCommitSha": "1" * 40,
        }
        ledger_path = bundle / "artifact-ledger.fragment.json"
        ledger_path.write_bytes(jcs_bytes(ledger_value))
        attempt_value = {
            "schemaVersion": 1,
            "attemptId": "1234.2",
            "producerStatus": "PRODUCER_PASS",
            "lastCompletedStage": "PUBLIC_EVIDENCE",
            "inputLockSha256": SHA_A,
            "policySha256": SHA_B,
            "repository": "bluetape4k/bluetape4k-image",
            "workflowPath": ".github/workflows/paddleocr-producer.yml",
            "workflowRef": "bluetape4k/bluetape4k-image/.github/workflows/paddleocr-producer.yml@refs/heads/develop",
            "workflowSha": "1" * 40,
            "ref": "refs/heads/develop",
            "headSha": "2" * 40,
            "runId": 1234,
            "runAttempt": 2,
            "actor": "debop",
            "runner": "github-hosted-linux-amd64",
            "environment": "github-hosted",
            "replacesAttemptId": None,
            "replacedReleaseDigest": None,
            "replacedEvidenceDigest": None,
            "replacedAttemptSha256": None,
            "replacedEvidenceSha256": None,
            "replacedReconciliationSha256": None,
            "replacedCleanupSha256": None,
            "timestamps": {
                "VALIDATING": "2026-09-07T01:00:00Z",
                "BUILDING": "2026-09-07T01:01:00Z",
                "STAGING": "2026-09-07T01:02:00Z",
                "EVIDENCE": "2026-09-07T01:03:00Z",
                "RELEASE": "2026-09-07T01:04:00Z",
                "PUBLIC_EVIDENCE": "2026-09-07T01:05:00Z",
                "TERMINAL": "2026-09-07T01:06:00Z",
            },
            "evidenceSha256": sha256_hex(evidence_path.read_bytes()),
            "reconciliationSha256": sha256_hex(reconciliation_path.read_bytes()),
            "cleanupSha256": sha256_hex(cleanup_path.read_bytes()),
            "ledgerFragmentSha256": sha256_hex(ledger_path.read_bytes()),
            "revocationsSha256": sha256_hex(revocations_path.read_bytes()),
            "revocationsCommitSha": "1" * 40,
            "previousDocumentSha256": None,
        }
        attempt_path = bundle / "producer-attempt.json"
        attempt_path.write_bytes(jcs_bytes(attempt_value))
        command = (
            "verify-attempt", "--attempt", str(attempt_path), "--evidence", str(evidence_path),
            "--reconciliation", str(reconciliation_path), "--cleanup", str(cleanup_path),
            "--ledger-fragment", str(ledger_path), "--revocations", str(revocations_path),
        )
        result = self._run(*command)
        self.assertEqual(result.returncode, 0, (result.stdout + result.stderr).decode())
        document = json.loads(result.stdout)
        self.assertEqual(set(document), {
            "schemaVersion", "attemptId", "producerStatus", "lastCompletedStage",
            "mapped609Status", "imagePlatformDigest", "evidenceManifestDigest",
            "reconciliationSha256", "ledgerFragmentSha256", "revocationsSha256",
            "errorCode", "errorMessage",
        })
        evidence_path.write_bytes(jcs_bytes({"attemptId": "9999.1"}))
        result = self._run(*command)
        self.assertEqual(result.returncode, 40)
        self.assertEqual(json.loads(result.stdout)["status"], "SCHEMA_INVALID")

    def test_usage_and_secret_payload_are_canonical_and_redacted(self) -> None:
        result = self._run("validate-inputs", "--token", "ghp_super_secret_token")
        self.assertEqual(result.returncode, 40)
        self.assertEqual(result.stdout.count(b"\n"), 1)
        self.assertNotIn(b"ghp_super_secret_token", result.stdout + result.stderr)
        document = json.loads(result.stdout)
        self.assertEqual((document["status"], document["errorCode"]), ("SCHEMA_INVALID", "BLOCKED_INPUT"))


class LifecycleContractTest(unittest.TestCase):
    def test_status_table_exhaustively_accepts_only_declared_stage_and_mapping(self) -> None:
        for status, contract in STATUS_CONTRACT.items():
            for stage in ("NONE", "STAGING", "EVIDENCE", "RELEASE", "PUBLIC_EVIDENCE"):
                document = valid_reconciliation(status)
                document["lastCompletedStage"] = stage
                document["mapped609Status"] = contract.mapped_609_status
                if stage in contract.allowed_stages:
                    document["staging"] = None if stage == "NONE" else document["staging"]
                    if stage not in ("RELEASE", "PUBLIC_EVIDENCE"):
                        document["release"] = None
                    if stage != "PUBLIC_EVIDENCE":
                        document["evidence"] = None
                    # Package-shape details are tested through the terminal success path.
                    if status in {"REJECTED", "REVOKED", "FAILED", "CANCELLED", "INTERRUPTED"}:
                        continue
                else:
                    with self.assertRaisesRegex(ProducerValidationError, "lastCompletedStage"):
                        validate_reconciliation(document)

    def test_producer_pass_requires_all_readbacks_and_digest_equality(self) -> None:
        document = valid_reconciliation("PRODUCER_PASS")
        self.assertEqual(validate_reconciliation(document)["producerStatus"], "PRODUCER_PASS")
        for field in (
            "visibilityReadBack", "downstreamReadBack", "cleanupVerified", "denylistVerified"
        ):
            invalid = dict(document)
            invalid[field] = False
            with self.subTest(field=field), self.assertRaisesRegex(
                ProducerValidationError, field
            ):
                validate_reconciliation(invalid)
        invalid = dict(document)
        invalid["release"] = dict(document["release"])  # type: ignore[arg-type]
        invalid["release"]["configDigest"] = "sha256:" + "9" * 64  # type: ignore[index]
        with self.assertRaisesRegex(ProducerValidationError, "configDigest"):
            validate_reconciliation(invalid)

    def test_incident_timestamps_follow_status_group_and_order(self) -> None:
        document = valid_reconciliation("FAILED")
        document.update(
            {
                "cleanupVerified": True,
                "denylistVerified": True,
                "visibilityReadBack": True,
                "downstreamReadBack": True,
                "closedAt": "2026-09-07T01:02:00Z",
            }
        )
        self.assertEqual(validate_reconciliation(document)["closedAt"], document["closedAt"])
        document["ownerAcknowledgedAt"] = "2026-09-07T01:03:00Z"
        with self.assertRaisesRegex(ProducerValidationError, "timestamp order"):
            validate_reconciliation(document)

    def test_cleanup_merge_is_attempt_scoped_complete_and_target_unique(self) -> None:
        fragments = [
            {
                "schemaVersion": 1,
                "attemptId": "1234.2",
                "jobId": job,
                "originalProducerStatus": "FAILED",
                "startedAt": "2026-09-07T01:00:00Z",
                "finishedAt": "2026-09-07T01:01:00Z",
                "targets": [{"kind": "TEMP_DIRECTORY", "idSha256": digest, "result": "REMOVED"}],
            }
            for job, digest in (("validation", SHA_A), ("staging", SHA_B))
        ]
        aggregate = merge_cleanup_fragments(
            "1234.2", "FAILED", ["validation", "staging"], fragments,
            merged_at="2026-09-07T01:02:00Z",
        )
        self.assertTrue(aggregate["cleanupVerified"])
        self.assertEqual(apply_cleanup_outcome("FAILED", aggregate), "FAILED")

        pass_fragment = dict(fragments[0])
        pass_fragment["originalProducerStatus"] = "PRODUCER_PASS"
        missing = merge_cleanup_fragments(
            "1234.2", "PRODUCER_PASS", ["validation", "staging"], [pass_fragment],
            merged_at="2026-09-07T01:02:00Z",
        )
        self.assertFalse(missing["cleanupVerified"])
        self.assertEqual(apply_cleanup_outcome("PRODUCER_PASS", missing), "INTERRUPTED")

        fragments[1]["attemptId"] = "9999.1"
        with self.assertRaisesRegex(ProducerValidationError, "attemptId"):
            merge_cleanup_fragments(
                "1234.2", "FAILED", ["validation", "staging"], fragments,
                merged_at="2026-09-07T01:02:00Z",
            )

    def test_cleanup_fragment_rejects_duplicate_targets_and_paths(self) -> None:
        document = {
            "schemaVersion": 1,
            "attemptId": "1234.2",
            "jobId": "validation",
            "originalProducerStatus": "FAILED",
            "startedAt": "2026-09-07T01:00:00Z",
            "finishedAt": "2026-09-07T01:01:00Z",
            "targets": [
                {"kind": "TEMP_DIRECTORY", "idSha256": SHA_A, "result": "REMOVED"},
                {"kind": "TEMP_DIRECTORY", "idSha256": SHA_A, "result": "ABSENT"},
            ],
        }
        with self.assertRaisesRegex(ProducerValidationError, "duplicate cleanup target"):
            validate_cleanup_fragment(document)
        document["targets"] = [
            {"kind": "/tmp/secret", "idSha256": SHA_A, "result": "REMOVED"}
        ]
        with self.assertRaisesRegex(ProducerValidationError, "kind"):
            validate_cleanup_fragment(document)

    def test_revocation_chain_is_append_only_and_pairs_public_artifacts(self) -> None:
        previous = initial_revocations()
        entries = [
            {
                "incidentId": "638",
                "artifactKind": kind,
                "digest": "sha256:" + digest,
                "reasonCode": "PUBLIC_VERIFICATION_FAILED",
                "incidentUrl": "https://github.com/bluetape4k/bluetape4k-image/issues/638",
                "issuedAt": "2026-09-07T01:00:00Z",
                "acknowledgedAt": "2026-09-07T01:01:00Z",
                "signer": "repo:bluetape4k/bluetape4k-image:ref:refs/heads/develop",
            }
            for kind, digest in (("IMAGE", SHA_A), ("EVIDENCE", SHA_B))
        ]
        candidate = append_revocations(previous, entries)
        self.assertEqual(validate_revocation_successor(previous, candidate), candidate)
        invalid = dict(candidate)
        invalid["previousDocumentSha256"] = "f" * 64
        with self.assertRaisesRegex(ProducerValidationError, "previousDocumentSha256"):
            validate_revocation_successor(previous, invalid)
        invalid = dict(candidate)
        invalid["digests"] = list(reversed(candidate["digests"]))
        with self.assertRaisesRegex(ProducerValidationError, "append-only|adjacent"):
            validate_revocation_successor(previous, invalid)

    def test_emergency_receipt_binds_both_current_revocation_entries(self) -> None:
        previous = initial_revocations()
        candidate = append_revocations(previous, [
            {
                "incidentId": "638",
                "artifactKind": kind,
                "digest": "sha256:" + digest,
                "reasonCode": "PUBLIC_VERIFICATION_FAILED",
                "incidentUrl": "https://github.com/bluetape4k/bluetape4k-image/issues/638",
                "issuedAt": "2026-09-07T01:00:00Z",
                "acknowledgedAt": "2026-09-07T01:01:00Z",
                "signer": "repo:bluetape4k/bluetape4k-image:ref:refs/heads/develop",
            }
            for kind, digest in (("IMAGE", SHA_A), ("EVIDENCE", SHA_B))
        ])
        receipt = {
            "schemaVersion": 1,
            "imageDigest": "sha256:" + SHA_A,
            "evidenceDigest": "sha256:" + SHA_B,
            "incidentUrl": "https://github.com/bluetape4k/bluetape4k-image/issues/638",
            "reasonCode": "PUBLIC_VERIFICATION_FAILED",
            "repository": "bluetape4k/bluetape4k-image",
            "workflowPath": ".github/workflows/paddleocr-producer.yml",
            "workflowRef": "bluetape4k/bluetape4k-image/.github/workflows/paddleocr-producer.yml@refs/heads/develop",
            "workflowSha": "2" * 40,
            "runId": 1234,
            "runAttempt": 2,
            "signer": "repo:bluetape4k/bluetape4k-image:ref:refs/heads/develop",
            "oidcIssuer": "https://token.actions.githubusercontent.com",
            "oidcAudience": "sigstore",
            "developHeadSha": "3" * 40,
            "revocationsCommitSha": "3" * 40,
            "revocationsSha256": sha256_hex(jcs_bytes(candidate)),
            "issuedAt": "2026-09-07T01:02:00Z",
            "revocationEntries": [
                {
                    "artifactKind": entry["artifactKind"],
                    "digest": entry["digest"],
                    "entrySha256": sha256_hex(jcs_bytes(entry)),
                }
                for entry in candidate["digests"]
            ],
        }
        self.assertEqual(validate_emergency_receipt(receipt, candidate)["runId"], 1234)
        receipt["revocationEntries"][0]["entrySha256"] = SHA_C  # type: ignore[index]
        with self.assertRaisesRegex(ProducerValidationError, "entrySha256"):
            validate_emergency_receipt(receipt, candidate)


if __name__ == "__main__":
    unittest.main()
