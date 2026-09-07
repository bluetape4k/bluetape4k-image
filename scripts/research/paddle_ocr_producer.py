from __future__ import annotations

"""Fail-closed CLI for the Issue #638 PaddleOCR trusted producer."""

import argparse
import logging
import os
import re
import select
import shutil
import signal
import stat
import subprocess
import sys
import time
from collections.abc import Iterator, Sequence
from contextlib import contextmanager
from pathlib import Path, PurePosixPath
from types import FrameType
from typing import Any
from urllib.parse import urlsplit

from paddle_ocr_producer_lib.contracts import (
    ProducerValidationError,
    exact_object,
    jcs_bytes,
    load_json_bytes,
    require_sha256,
    sha256_hex,
    validate_input_lock,
)
from paddle_ocr_producer_lib.evidence import (
    MaterializationLimits,
    validate_evidence_file_manifest,
    validate_ledger_fragment,
    validate_oci_handoff,
    validate_producer_evidence,
    verify_public_evidence,
)
from paddle_ocr_producer_lib.filesystem import (
    ARCHIVE_LIMITS,
    ORAS_LINUX_AMD64_SHA256,
    ORAS_LINUX_AMD64_URL,
    ORAS_VERSION,
    bootstrap_oras_archive,
    canonical_tree_manifest,
    extract_archive,
    fetch_to_regular_file,
    tree_sha256,
    verify_regular_file,
)
from paddle_ocr_producer_lib.lifecycle import (
    STATUS_CONTRACT,
    append_revocations,
    build_producer_result,
    merge_cleanup_fragments,
    validate_attempt,
    validate_cleanup_aggregate,
    validate_cleanup_fragment,
    validate_emergency_receipt,
    validate_reconciliation,
    validate_revocations,
)

MAX_DOCUMENT_BYTES = 1024 * 1024
LOGGER = logging.getLogger("paddle_ocr_producer")


class ProducerUsageError(ValueError):
    pass


class ProducerBlockedError(ValueError):
    pass


class ProducerLegalError(ValueError):
    pass


class ProducerRejectedError(ValueError):
    pass


class ProducerCancelled(BaseException):
    pass


class ProducerInterrupted(BaseException):
    pass


class StrictArgumentParser(argparse.ArgumentParser):
    def error(self, message: str) -> None:
        raise ProducerUsageError("invalid command arguments")


@contextmanager
def installed_signal_handlers() -> Iterator[None]:
    previous: dict[int, Any] = {}

    def handle_interrupt(signum: int, _: FrameType | None) -> None:
        if signum == signal.SIGINT:
            raise ProducerCancelled()
        raise ProducerInterrupted()

    for signum in (signal.SIGINT, signal.SIGTERM):
        previous[signum] = signal.getsignal(signum)
        signal.signal(signum, handle_interrupt)
    try:
        yield
    finally:
        for signum, handler in previous.items():
            signal.signal(signum, handler)


def _read_regular_bytes(path: Path, limit: int = MAX_DOCUMENT_BYTES) -> bytes:
    flags = os.O_RDONLY | getattr(os, "O_CLOEXEC", 0) | getattr(os, "O_NOFOLLOW", 0)
    try:
        descriptor = os.open(path, flags)
    except OSError as exc:
        raise ProducerBlockedError("input document is unavailable") from exc
    try:
        metadata = os.fstat(descriptor)
        if not stat.S_ISREG(metadata.st_mode):
            raise ProducerBlockedError("input document must be a regular file")
        if metadata.st_size > limit:
            raise ProducerBlockedError("input document exceeds byte limit")
        chunks: list[bytes] = []
        total = 0
        while True:
            chunk = os.read(descriptor, min(64 * 1024, limit + 1 - total))
            if not chunk:
                break
            total += len(chunk)
            if total > limit:
                raise ProducerBlockedError("input document exceeds byte limit")
            chunks.append(chunk)
        return b"".join(chunks)
    finally:
        os.close(descriptor)


def _load_canonical_document(
    path: Path, *, contract_error: bool = False
) -> tuple[dict[str, Any], bytes]:
    raw = _read_regular_bytes(path)
    try:
        document = load_json_bytes(raw, MAX_DOCUMENT_BYTES)
    except ProducerValidationError as exc:
        if contract_error:
            raise ProducerValidationError("input document violates the strict JSON contract") from exc
        raise ProducerBlockedError("input document violates the strict JSON contract") from exc
    if jcs_bytes(document) != raw:
        if contract_error:
            raise ProducerValidationError("input document must use canonical JSON without a trailing newline")
        raise ProducerBlockedError("input document must use canonical JSON without a trailing newline")
    return document, raw


def _policy_hosts(value: Any) -> set[str]:
    policy = exact_object(
        value,
        required={
            "schemaVersion",
            "repositories",
            "workflows",
            "refs",
            "actors",
            "runnerEnvironments",
            "oidcIssuers",
            "audiences",
            "hosts",
        },
    )
    if policy["schemaVersion"] != 1:
        raise ProducerBlockedError("trust policy schemaVersion must be 1")
    for name in (
        "repositories",
        "workflows",
        "refs",
        "actors",
        "runnerEnvironments",
        "oidcIssuers",
        "audiences",
        "hosts",
    ):
        values = policy[name]
        if not isinstance(values, list) or not values or any(
            not isinstance(item, str) or not item or "PENDING" in item.upper()
            for item in values
        ):
            raise ProducerBlockedError(f"trust policy {name} must be a non-empty resolved array")
        if len(values) != len(set(values)):
            raise ProducerBlockedError(f"trust policy {name} must be unique")
    return set(policy["hosts"])


def _validate_legal_inventory(value: Any) -> None:
    inventory = exact_object(value, required={"schemaVersion", "components"})
    if inventory["schemaVersion"] != 1:
        raise ProducerLegalError("legal inventory schemaVersion must be 1")
    components = inventory["components"]
    if not isinstance(components, list) or not components:
        raise ProducerLegalError("legal inventory must contain components")
    keys = {
        "id",
        "licenseExpression",
        "licenseSourceUrl",
        "licenseSha256",
        "notice",
        "noticeSha256",
    }
    ids: set[str] = set()
    for component in components:
        item = exact_object(component, required=keys)
        for name in ("id", "licenseExpression", "licenseSourceUrl", "notice"):
            value = item[name]
            if not isinstance(value, str) or not value or "PENDING" in value.upper():
                raise ProducerLegalError(f"legal inventory {name} is incomplete")
        require_sha256(item["licenseSha256"], "legal inventory licenseSha256")
        require_sha256(item["noticeSha256"], "legal inventory noticeSha256")
        if sha256_hex(item["notice"].encode("utf-8")) != item["noticeSha256"]:
            raise ProducerLegalError("legal inventory notice sha256 differs")
        if item["id"] in ids:
            raise ProducerLegalError("legal inventory component ids must be unique")
        ids.add(item["id"])


def _read_model_legal_file(root: Path, relative: Any) -> bytes:
    if not isinstance(relative, str) or not relative or "\\" in relative:
        raise ProducerLegalError("model legal path is invalid")
    path = PurePosixPath(relative)
    if path.is_absolute() or any(part in ("", ".", "..") for part in path.parts):
        raise ProducerLegalError("model legal path escapes the input root")
    current = root
    for part in path.parts[:-1]:
        current = current / part
        if current.is_symlink() or not current.is_dir():
            raise ProducerLegalError("model legal parent must be a non-symlink directory")
    try:
        return _read_regular_bytes(current / path.parts[-1])
    except ProducerBlockedError as exc:
        raise ProducerLegalError("model legal file is unavailable") from exc


def _validate_model_legal_files(lock: dict[str, Any], root: Path) -> None:
    for model in lock["models"]:
        for path_field, sha_field in (
            ("licenseSourcePath", "licenseSha256"),
            ("noticePath", "noticeSha256"),
        ):
            raw = _read_model_legal_file(root, model[path_field])
            if sha256_hex(raw) != model[sha_field]:
                raise ProducerLegalError("model legal file sha256 differs")


def _hosts_from_lock(lock: dict[str, Any]) -> set[str]:
    hosts: set[str] = set()

    def collect(value: Any) -> None:
        if isinstance(value, dict):
            url = value.get("url")
            if isinstance(url, str):
                host = urlsplit(url).hostname
                if host:
                    hosts.add(host.lower().rstrip("."))
            for nested in value.values():
                collect(nested)
        elif isinstance(value, list):
            for nested in value:
                collect(nested)

    collect(lock)
    return hosts


def _atomic_write_new(path: Path, raw: bytes) -> str:
    if path.exists() or path.is_symlink():
        raise ProducerBlockedError("output already exists")
    path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    temporary = path.parent / ("." + path.name + ".partial")
    descriptor: int | None = None
    try:
        descriptor = os.open(
            temporary,
            os.O_WRONLY | os.O_CREAT | os.O_EXCL | getattr(os, "O_CLOEXEC", 0),
            0o600,
        )
        offset = 0
        while offset < len(raw):
            offset += os.write(descriptor, raw[offset:])
        os.fsync(descriptor)
        os.close(descriptor)
        descriptor = None
        os.link(temporary, path)
        temporary.unlink()
    finally:
        if descriptor is not None:
            os.close(descriptor)
        try:
            temporary.unlink()
        except FileNotFoundError:
            pass
    return sha256_hex(raw)


def _success(command: str, data: dict[str, Any]) -> dict[str, Any]:
    return {
        "schemaVersion": 1,
        "command": command,
        "status": "PASS",
        "data": data,
        "errorCode": "NONE",
        "errorMessage": None,
    }


def _failure(command: str, status: str, code: str, message: str) -> dict[str, Any]:
    return {
        "schemaVersion": 1,
        "command": command,
        "status": status,
        "data": {},
        "errorCode": code,
        "errorMessage": message[:512],
    }


def _validate_inputs(args: argparse.Namespace) -> dict[str, Any]:
    policy, policy_raw = _load_canonical_document(args.policy)
    try:
        hosts = _policy_hosts(policy)
    except ProducerValidationError as exc:
        raise ProducerBlockedError("trust policy violates its contract") from exc
    lock, lock_raw = _load_canonical_document(args.lock)
    try:
        validated_lock = validate_input_lock(lock, allowed_hosts=hosts)
    except ProducerValidationError as exc:
        raise ProducerBlockedError("input lock violates its contract") from exc
    _validate_model_legal_files(validated_lock, args.lock.parent)
    legal, legal_raw = _load_canonical_document(args.legal)
    try:
        _validate_legal_inventory(legal)
    except ProducerValidationError as exc:
        raise ProducerLegalError("legal inventory violates its contract") from exc
    legal_sha256 = sha256_hex(legal_raw)
    if lock["legalInventorySha256"] != legal_sha256:
        raise ProducerLegalError("legal inventory sha256 differs from input lock")
    return _success(
        "validate-inputs",
        {
            "inputLockSha256": sha256_hex(lock_raw),
            "legalInventorySha256": legal_sha256,
            "trustPolicySha256": sha256_hex(policy_raw),
        },
    )


def _input_value(args: argparse.Namespace) -> dict[str, Any]:
    lock, lock_raw = _load_canonical_document(args.lock)
    try:
        validated = validate_input_lock(lock, allowed_hosts=_hosts_from_lock(lock))
    except ProducerValidationError as exc:
        raise ProducerBlockedError("input lock violates its contract") from exc
    derived = [package for package in validated["packages"] if "derivedSourceId" in package]
    if len(derived) != 1:
        raise ProducerBlockedError("sourceDateEpoch requires one derived source package")
    package = derived[0]
    document = _success(
        "input-value",
        {
            "sourceDateEpoch": package["buildToolchain"]["sourceDateEpoch"],
            "derivedPackageId": package["id"],
            "inputLockSha256": sha256_hex(lock_raw),
        },
    )
    raw = jcs_bytes(document)
    _atomic_write_new(args.output, raw)
    return document


def _validate_requirements_lock(raw: bytes, packages: list[dict[str, Any]]) -> None:
    if len(raw) > 8 * 1024 * 1024:
        raise ProducerBlockedError("requirements lock exceeds byte limit")
    if not raw.endswith(b"\n") or b"\r" in raw:
        raise ProducerBlockedError("requirements lock must use LF-terminated UTF-8 lines")
    try:
        lines = raw.decode("utf-8", errors="strict").splitlines()
    except UnicodeDecodeError as exc:
        raise ProducerBlockedError("requirements lock must be valid UTF-8") from exc
    actual: set[tuple[str, str, str]] = set()
    pattern = re.compile(
        r"\A([A-Za-z0-9][A-Za-z0-9._-]*)==([^\s]+) --hash=sha256:([0-9a-f]{64})\Z"
    )
    for line in lines:
        payload = line.partition("#")[0].rstrip()
        if not payload:
            continue
        match = pattern.fullmatch(payload)
        if match is None:
            raise ProducerBlockedError("requirements lock line is not exactly hash-pinned")
        name = re.sub(r"[-_.]+", "-", match.group(1)).lower()
        identity = (name, match.group(2), match.group(3))
        if identity in actual:
            raise ProducerBlockedError("requirements lock contains a duplicate package")
        actual.add(identity)
    expected = {
        (
            re.sub(r"[-_.]+", "-", package["name"]).lower(),
            package["version"],
            package["sha256"],
        )
        for package in packages
    }
    if actual != expected:
        raise ProducerBlockedError("requirements lock differs from package input lock")


def _accept_resolved_inputs(args: argparse.Namespace) -> dict[str, Any]:
    root = args.resolution_root
    if root.is_symlink() or not root.is_dir():
        raise ProducerBlockedError("resolution root must be a non-symlink directory")
    lock, lock_raw = _load_canonical_document(root / "producer-input-candidate.json")
    try:
        validated = validate_input_lock(lock, allowed_hosts=_hosts_from_lock(lock))
    except ProducerValidationError as exc:
        raise ProducerBlockedError("resolved input lock violates its contract") from exc
    legal, legal_raw = _load_canonical_document(root / "legal-inventory.json")
    try:
        _validate_legal_inventory(legal)
    except ProducerValidationError as exc:
        raise ProducerLegalError("resolved legal inventory violates its contract") from exc
    if validated["legalInventorySha256"] != sha256_hex(legal_raw):
        raise ProducerLegalError("resolved legal inventory sha256 differs")
    _validate_model_legal_files(validated, root)
    requirements_raw = _read_regular_bytes(
        root / "requirements.cpu.lock.txt", 8 * 1024 * 1024
    )
    _validate_requirements_lock(requirements_raw, validated["packages"])

    destinations = (args.input_lock, args.requirements_lock, args.legal_inventory)
    if any(path.exists() or path.is_symlink() for path in destinations):
        raise ProducerBlockedError("accepted input destination already exists")
    input_sha = _atomic_write_new(args.input_lock, lock_raw)
    requirements_sha = _atomic_write_new(args.requirements_lock, requirements_raw)
    legal_sha = _atomic_write_new(args.legal_inventory, legal_raw)
    return _success(
        "accept-resolved-inputs",
        {
            "inputLockPath": str(args.input_lock),
            "inputLockSha256": input_sha,
            "requirementsPath": str(args.requirements_lock),
            "requirementsSha256": requirements_sha,
            "legalPath": str(args.legal_inventory),
            "legalSha256": legal_sha,
        },
    )


def _verify_source_reproducibility(args: argparse.Namespace) -> dict[str, Any]:
    lock, _ = _load_canonical_document(args.input_lock)
    try:
        validated = validate_input_lock(lock, allowed_hosts=_hosts_from_lock(lock))
    except ProducerValidationError as exc:
        raise ProducerBlockedError("input lock violates its contract") from exc
    derived = [package for package in validated["packages"] if "derivedSourceId" in package]
    if len(derived) != 1:
        raise ProducerBlockedError("source reproducibility requires one derived package")
    package = derived[0]
    hosts = _hosts_from_lock(validated)
    registry = [package for package in validated["packages"] if "url" in package]
    registry_wheelhouse = args.registry_wheelhouse
    if args.prebuilt_wheel:
        if len(args.prebuilt_wheel) != 2:
            raise ProducerBlockedError(
                "source reproducibility requires two independent wheel builds"
            )
        first, second = args.prebuilt_wheel
    else:
        if args.work_root.exists() or args.work_root.is_symlink():
            raise ProducerBlockedError("source reproducibility work root already exists")
        args.work_root.mkdir(parents=True, mode=0o700)
        source = next(
            item for item in validated["sources"] if item["id"] == package["derivedSourceId"]
        )
        source_archive = args.work_root / "source.tar.gz"
        try:
            fetch_to_regular_file(
                source,
                source_archive,
                allowed_hosts=hosts,
            )
            source_root = args.work_root / "source"
            extract_archive(
                source_archive,
                source_root,
                ARCHIVE_LIMITS,
                strip_single_root=True,
            )
            tool_root = args.work_root / "build-tools"
            tool_root.mkdir(mode=0o700)
            for artifact in package["buildToolchain"]["artifacts"]:
                fetch_to_regular_file(
                    artifact,
                    tool_root / artifact["filename"],
                    allowed_hosts=hosts,
                )
            builds: list[Path] = []
            environment_name = "SETUPTOOLS_SCM_PRETEND_VERSION_FOR_" + re.sub(
                r"[^A-Za-z0-9]", "_", package["name"]
            ).upper()
            platform_reference = (
                validated["baseImage"]["reference"].split("@", 1)[0]
                + "@"
                + validated["baseImage"]["platformDigest"]
            )
            command = (
                "python -m pip install --disable-pip-version-check --no-index "
                "--find-links=/tools /tools/*.whl >/tmp/build-tools.log && "
                "cp -a /source /tmp/source && "
                "python -m pip wheel --disable-pip-version-check --no-deps "
                "--no-build-isolation --wheel-dir /output /tmp/source >/tmp/build.log"
            )
            for build_number in (1, 2):
                build_root = args.work_root / f"build-{build_number}"
                build_root.mkdir(mode=0o700)
                completed = subprocess.run(
                    [
                        "docker",
                        "run",
                        "--rm",
                        "--platform",
                        validated["targetPlatform"],
                        "-v",
                        f"{source_root.resolve()}:/source:ro",
                        "-v",
                        f"{tool_root.resolve()}:/tools:ro",
                        "-v",
                        f"{build_root.resolve()}:/output",
                        "-e",
                        f"SOURCE_DATE_EPOCH={package['buildToolchain']['sourceDateEpoch']}",
                        "-e",
                        f"{environment_name}={package['version']}",
                        platform_reference,
                        "sh",
                        "-eu",
                        "-c",
                        command,
                    ],
                    stdin=subprocess.DEVNULL,
                    capture_output=True,
                    check=False,
                    timeout=5400,
                )
                if completed.returncode != 0:
                    raise ChildProcessError("derived wheel build failed")
                builds.append(build_root / package["filename"])
            first, second = builds
            if registry_wheelhouse is None:
                registry_wheelhouse = args.work_root / "registry-wheelhouse"
                registry_wheelhouse.mkdir(mode=0o700)
                for registry_package in registry:
                    fetch_to_regular_file(
                        registry_package,
                        registry_wheelhouse / registry_package["filename"],
                        allowed_hosts=hosts,
                    )
        except BaseException:
            shutil.rmtree(args.work_root, ignore_errors=True)
            raise
    try:
        for wheel in (first, second):
            verify_regular_file(
                wheel,
                expected_bytes=package["bytes"],
                expected_sha256=package["sha256"],
            )
    except ProducerValidationError as exc:
        raise ProducerRejectedError("derived wheel differs from its locked receipt") from exc
    if _read_regular_bytes(first, package["bytes"]) != _read_regular_bytes(
        second, package["bytes"]
    ):
        raise ProducerRejectedError("independent derived wheel builds differ")

    if registry and registry_wheelhouse is None:
        raise ProducerBlockedError("registry wheelhouse is required")
    sources: list[tuple[Path, dict[str, Any]]] = [(first, package)]
    if registry_wheelhouse is not None:
        if registry_wheelhouse.is_symlink() or not registry_wheelhouse.is_dir():
            raise ProducerBlockedError("registry wheelhouse must be a non-symlink directory")
        for registry_package in registry:
            source = registry_wheelhouse / registry_package["filename"]
            try:
                verify_regular_file(
                    source,
                    expected_bytes=registry_package["bytes"],
                    expected_sha256=registry_package["sha256"],
                )
            except ProducerValidationError as exc:
                raise ProducerRejectedError("registry wheel differs from its locked receipt") from exc
            sources.append((source, registry_package))

    if args.output_wheelhouse.exists() or args.output_wheelhouse.is_symlink():
        raise ProducerBlockedError("output wheelhouse already exists")
    args.output_wheelhouse.mkdir(parents=True, mode=0o700)
    try:
        for source, locked_package in sources:
            destination = args.output_wheelhouse / locked_package["filename"]
            with source.open("rb") as input_stream, destination.open("xb") as output_stream:
                shutil.copyfileobj(input_stream, output_stream, length=1024 * 1024)
        manifest = canonical_tree_manifest(args.output_wheelhouse)
    except BaseException:
        shutil.rmtree(args.output_wheelhouse, ignore_errors=True)
        raise
    return _success(
        "verify-source-reproducibility",
        {
            "wheelhousePath": str(args.output_wheelhouse),
            "wheelhouseSha256": sha256_hex(manifest),
            "sourceDateEpoch": package["buildToolchain"]["sourceDateEpoch"],
        },
    )


def _copy_regular_file(source: Path, destination: Path) -> None:
    destination.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    raw = _read_regular_bytes(source, max(source.stat().st_size, 1))
    _atomic_write_new(destination, raw)


def _parse_model_archives(values: list[str]) -> dict[str, Path]:
    result: dict[str, Path] = {}
    for value in values:
        role, separator, path = value.partition("=")
        if separator != "=" or role not in {"detector", "recognizer"} or not path:
            raise ProducerUsageError("invalid model archive binding")
        if role in result:
            raise ProducerUsageError("duplicate model archive binding")
        result[role] = Path(path)
    if set(result) != {"detector", "recognizer"}:
        raise ProducerUsageError("detector and recognizer archives are required")
    return result


def _stage_inputs(args: argparse.Namespace) -> dict[str, Any]:
    lock, lock_raw = _load_canonical_document(args.input_lock)
    try:
        validated = validate_input_lock(lock, allowed_hosts=_hosts_from_lock(lock))
    except ProducerValidationError as exc:
        raise ProducerBlockedError("input lock violates its contract") from exc
    legal, legal_raw = _load_canonical_document(args.legal)
    _validate_legal_inventory(legal)
    if validated["legalInventorySha256"] != sha256_hex(legal_raw):
        raise ProducerLegalError("legal inventory sha256 differs")
    _validate_model_legal_files(validated, args.input_lock.parent)
    requirements_raw = _read_regular_bytes(args.requirements_lock, 8 * 1024 * 1024)
    _validate_requirements_lock(requirements_raw, validated["packages"])
    if args.wheelhouse.is_symlink() or not args.wheelhouse.is_dir():
        raise ProducerBlockedError("wheelhouse must be a non-symlink directory")
    expected_wheels = {package["filename"] for package in validated["packages"]}
    actual_wheels = {
        path.name
        for path in args.wheelhouse.iterdir()
        if path.is_file() and not path.is_symlink()
    }
    if actual_wheels != expected_wheels:
        raise ProducerRejectedError("wheelhouse file inventory differs from input lock")
    for package in validated["packages"]:
        try:
            verify_regular_file(
                args.wheelhouse / package["filename"],
                expected_bytes=package["bytes"],
                expected_sha256=package["sha256"],
            )
        except ProducerValidationError as exc:
            raise ProducerRejectedError("wheelhouse package receipt differs") from exc
    model_archives = _parse_model_archives(args.model_archive)
    models_by_role = {model["role"]: model for model in validated["models"]}
    for role, model in models_by_role.items():
        try:
            verify_regular_file(
                model_archives[role],
                expected_bytes=model["bytes"],
                expected_sha256=model["sha256"],
            )
        except ProducerValidationError as exc:
            raise ProducerRejectedError("model archive receipt differs") from exc

    if args.output_artifact.exists() or args.output_artifact.is_symlink():
        raise ProducerBlockedError("staging artifact already exists")
    temporary = args.output_artifact.parent / ("." + args.output_artifact.name + ".partial")
    if temporary.exists() or temporary.is_symlink():
        raise ProducerBlockedError("staging temporary path already exists")
    temporary.mkdir(parents=True, mode=0o700)
    try:
        inputs = temporary / "inputs"
        _atomic_write_new(inputs / "producer-input.lock.json", lock_raw)
        _atomic_write_new(inputs / "requirements.cpu.lock.txt", requirements_raw)
        _atomic_write_new(inputs / "legal-inventory.json", legal_raw)
        for model in validated["models"]:
            for field in ("licenseSourcePath", "noticePath"):
                relative = PurePosixPath(model[field])
                source = args.input_lock.parent.joinpath(*relative.parts)
                destination = temporary.joinpath(*relative.parts)
                if not destination.exists():
                    _copy_regular_file(source, destination)
        for package in validated["packages"]:
            _copy_regular_file(
                args.wheelhouse / package["filename"],
                temporary / "wheelhouse" / package["filename"],
            )
        tree_digests: dict[str, str] = {}
        for role in ("detector", "recognizer"):
            model_root = temporary / "models" / role
            extract_archive(
                model_archives[role],
                model_root,
                ARCHIVE_LIMITS,
                strip_single_root=True,
            )
            manifest = canonical_tree_manifest(model_root)
            actual_tree = tree_sha256(model_root)
            if actual_tree != models_by_role[role]["treeSha256"]:
                raise ProducerRejectedError("model tree sha256 differs")
            tree_digests[role] = actual_tree
            _atomic_write_new(temporary / "models" / f"{role}.manifest.txt", manifest)
        pair_document = {
            "detector": tree_digests["detector"],
            "recognizer": tree_digests["recognizer"],
        }
        pair_raw = jcs_bytes(pair_document)
        pair_sha = sha256_hex(pair_raw)
        _atomic_write_new(temporary / "models" / "model-pair.json", pair_raw)
        staging_document = {
            "schemaVersion": 1,
            "inputLockSha256": sha256_hex(lock_raw),
            "legalInventorySha256": sha256_hex(legal_raw),
            "requirementsSha256": sha256_hex(requirements_raw),
            "modelTreeDigests": tree_digests,
            "modelPairSha256": pair_sha,
        }
        _atomic_write_new(temporary / "staging-manifest.json", jcs_bytes(staging_document))
        artifact_sha = sha256_hex(canonical_tree_manifest(temporary))
        os.rename(temporary, args.output_artifact)
    except BaseException:
        shutil.rmtree(temporary, ignore_errors=True)
        raise
    return _success(
        "stage-inputs",
        {
            "artifactPath": str(args.output_artifact),
            "artifactSha256": artifact_sha,
            "modelTreeDigests": tree_digests,
            "modelPairSha256": pair_sha,
        },
    )


def _inspect_image_raw(reference: str) -> bytes:
    completed = subprocess.run(
        ["docker", "buildx", "imagetools", "inspect", "--raw", reference],
        stdin=subprocess.DEVNULL,
        capture_output=True,
        check=False,
        timeout=60,
    )
    if completed.returncode != 0 or not completed.stdout:
        raise ProducerBlockedError("base image manifest could not be resolved")
    if len(completed.stdout) > 4 * 1024 * 1024:
        raise ProducerBlockedError("base image manifest exceeds byte limit")
    return completed.stdout


def _resolve_inputs(args: argparse.Namespace) -> dict[str, Any]:
    if args.target_platform != "linux/amd64" or args.python_version != "3.10":
        raise ProducerBlockedError("unsupported producer target")
    accepted_lock, _ = _load_canonical_document(
        args.accepted_root / "producer-input.lock.json"
    )
    try:
        validated = validate_input_lock(
            accepted_lock, allowed_hosts=_hosts_from_lock(accepted_lock)
        )
    except ProducerValidationError as exc:
        raise ProducerBlockedError("accepted input lock violates its contract") from exc
    sources = {source["id"]: source for source in validated["sources"]}
    requested_revisions = {
        "paddleocr": args.paddleocr_commit,
        "paddlex": args.paddlex_commit,
        "paddle": args.paddle_commit,
    }
    for source_id, revision in requested_revisions.items():
        if source_id not in sources or sources[source_id]["revision"] != revision:
            raise ProducerBlockedError("requested source revision differs from accepted input")
    models = {model["role"]: model["modelId"] for model in validated["models"]}
    if models != {"detector": args.detector, "recognizer": args.recognizer}:
        raise ProducerBlockedError("requested model pair differs from accepted input")

    legal, legal_raw = _load_canonical_document(args.accepted_root / "legal-inventory.json")
    _validate_legal_inventory(legal)
    if validated["legalInventorySha256"] != sha256_hex(legal_raw):
        raise ProducerLegalError("accepted legal inventory sha256 differs")
    _validate_model_legal_files(validated, args.accepted_root)
    requirements_raw = _read_regular_bytes(
        args.accepted_root / "requirements.cpu.lock.txt", 8 * 1024 * 1024
    )
    _validate_requirements_lock(requirements_raw, validated["packages"])

    index_raw = _inspect_image_raw(args.base_image_candidate)
    index = load_json_bytes(index_raw, 4 * 1024 * 1024)
    manifests = index.get("manifests")
    if not isinstance(manifests, list):
        raise ProducerBlockedError("base image candidate is not a multi-platform index")
    matches = []
    for manifest in manifests:
        if not isinstance(manifest, dict):
            continue
        platform = manifest.get("platform")
        if (
            isinstance(platform, dict)
            and platform.get("os") == "linux"
            and platform.get("architecture") == "amd64"
            and platform.get("variant") in (None, "")
        ):
            matches.append(manifest)
    if len(matches) != 1 or not isinstance(matches[0].get("digest"), str):
        raise ProducerBlockedError("base image has no unique linux/amd64 manifest")
    index_digest = "sha256:" + sha256_hex(index_raw)
    platform_digest = matches[0]["digest"]
    platform_raw = _inspect_image_raw(
        args.base_image_candidate.split(":", 1)[0] + "@" + platform_digest
    )
    platform_manifest = load_json_bytes(platform_raw, 4 * 1024 * 1024)
    config = platform_manifest.get("config")
    if not isinstance(config, dict) or not isinstance(config.get("digest"), str):
        raise ProducerBlockedError("base image platform manifest has no config digest")
    config_digest = config["digest"]

    candidate = dict(validated)
    candidate["baseImage"] = dict(validated["baseImage"])
    base_name = args.base_image_candidate.rsplit(":", 1)[0]
    candidate["baseImage"].update(
        {
            "reference": base_name + "@" + index_digest,
            "indexDigest": index_digest,
            "platformDigest": platform_digest,
            "configDigest": config_digest,
        }
    )
    candidate_raw = jcs_bytes(candidate)
    if args.output_root.exists() and not args.output_root.is_dir():
        raise ProducerBlockedError("resolution output root must be a directory")
    outputs = (
        args.output_root / "producer-input-candidate.json",
        args.output_root / "requirements.cpu.lock.txt",
        args.output_root / "legal-inventory.json",
    )
    if any(path.exists() or path.is_symlink() for path in outputs):
        raise ProducerBlockedError("resolution output already exists")
    args.output_root.mkdir(parents=True, exist_ok=True, mode=0o700)
    _atomic_write_new(outputs[0], candidate_raw)
    _atomic_write_new(outputs[1], requirements_raw)
    _atomic_write_new(outputs[2], legal_raw)
    for model in candidate["models"]:
        for field in ("licenseSourcePath", "noticePath"):
            relative = PurePosixPath(model[field])
            destination = args.output_root.joinpath(*relative.parts)
            if not destination.exists():
                _copy_regular_file(
                    args.accepted_root.joinpath(*relative.parts), destination
                )
    base_digests = {
        "indexDigest": index_digest,
        "platformDigest": platform_digest,
        "configDigest": config_digest,
    }
    return _success(
        "resolve-inputs",
        {
            "candidatePath": str(outputs[0]),
            "candidateSha256": sha256_hex(candidate_raw),
            "baseDigests": base_digests,
            "operationReceipts": [
                {
                    "operationId": "resolve-base-image",
                    "reference": args.base_image_candidate,
                    **base_digests,
                }
            ],
        },
    )


def _merge_cleanup(args: argparse.Namespace) -> dict[str, Any]:
    fragments = []
    for path in args.fragment:
        document, _ = _load_canonical_document(path, contract_error=True)
        fragments.append(document)
    aggregate = merge_cleanup_fragments(
        args.attempt_id,
        args.original_status,
        args.started_job,
        fragments,
        merged_at=args.merged_at,
    )
    raw = jcs_bytes(aggregate)
    digest = _atomic_write_new(args.output, raw)
    return _success(
        "merge-cleanup",
        {"attemptId": args.attempt_id, "cleanupSha256": digest, "cleanupVerified": aggregate["cleanupVerified"]},
    )


def _reconcile(args: argparse.Namespace) -> dict[str, Any]:
    document, raw = _load_canonical_document(args.reconciliation, contract_error=True)
    validated = validate_reconciliation(document)
    return _success(
        "reconcile",
        {
            "attemptId": validated["attemptId"],
            "producerStatus": validated["producerStatus"],
            "reconciliationSha256": sha256_hex(raw),
        },
    )


def _append_revocation(args: argparse.Namespace) -> dict[str, Any]:
    previous, _ = _load_canonical_document(args.previous, contract_error=True)
    entries = []
    for path in args.entry:
        entry, _ = _load_canonical_document(path, contract_error=True)
        entries.append(entry)
    candidate = append_revocations(previous, entries)
    raw = jcs_bytes(candidate)
    digest = _atomic_write_new(args.output, raw)
    return _success(
        "append-revocation",
        {"revocationsSha256": digest, "entryCount": len(candidate["digests"])},
    )


def _verify_emergency_receipt(args: argparse.Namespace) -> dict[str, Any]:
    receipt, receipt_raw = _load_canonical_document(args.receipt, contract_error=True)
    revocations, revocations_raw = _load_canonical_document(args.revocations, contract_error=True)
    validate_emergency_receipt(receipt, revocations)
    return _success(
        "verify-emergency-receipt",
        {
            "receiptSha256": sha256_hex(receipt_raw),
            "revocationsSha256": sha256_hex(revocations_raw),
            "commitSha": receipt["revocationsCommitSha"],
        },
    )


def _verify_cleanup_fragments(cleanup_path: Path, cleanup: dict[str, Any]) -> None:
    expected_paths = set()
    for descriptor in cleanup["fragments"]:
        relative = PurePosixPath(descriptor["path"])
        if relative.is_absolute() or ".." in relative.parts:
            raise ProducerValidationError("cleanup fragment path escapes its bundle")
        path = cleanup_path.parent.joinpath(*relative.parts)
        fragment, raw = _load_canonical_document(path, contract_error=True)
        validate_cleanup_fragment(fragment)
        if fragment["attemptId"] != cleanup["attemptId"]:
            raise ProducerValidationError("cleanup fragment attemptId differs")
        if fragment["jobId"] != descriptor["jobId"]:
            raise ProducerValidationError("cleanup fragment jobId differs")
        if len(raw) != descriptor["bytes"] or sha256_hex(raw) != descriptor["sha256"]:
            raise ProducerValidationError("cleanup fragment descriptor differs from bytes")
        expected_paths.add(path)
    directory = cleanup_path.parent / "cleanup-fragments"
    if directory.exists():
        actual_paths = {path for path in directory.iterdir() if path.is_file() or path.is_symlink()}
        if actual_paths != expected_paths:
            raise ProducerValidationError("cleanup fragment directory contains missing or extra files")


def _finalize(args: argparse.Namespace) -> dict[str, Any]:
    reconciliation, reconciliation_raw = _load_canonical_document(args.reconciliation, contract_error=True)
    cleanup, cleanup_raw = _load_canonical_document(args.cleanup, contract_error=True)
    _, ledger_raw = _load_canonical_document(args.ledger_fragment, contract_error=True)
    revocations, revocations_raw = _load_canonical_document(args.revocations, contract_error=True)
    validated_reconciliation = validate_reconciliation(reconciliation)
    validated_cleanup = validate_cleanup_aggregate(cleanup)
    validate_revocations(revocations)
    require_sha256(args.attempt_sha256, "attemptSha256")
    _verify_cleanup_fragments(args.cleanup, validated_cleanup)
    if validated_cleanup["attemptId"] != validated_reconciliation["attemptId"]:
        raise ProducerValidationError("cleanup attemptId differs from reconciliation")
    if validated_reconciliation["cleanupVerified"] != validated_cleanup["cleanupVerified"]:
        raise ProducerValidationError("cleanupVerified differs between documents")
    result = build_producer_result(
        validated_reconciliation,
        reconciliation_sha256=sha256_hex(reconciliation_raw),
        ledger_fragment_sha256=sha256_hex(ledger_raw),
        revocations_sha256=sha256_hex(revocations_raw),
    )
    result_sha = _atomic_write_new(args.output, jcs_bytes(result))
    return _success(
        "finalize",
        {
            "attemptId": result["attemptId"],
            "attemptSha256": args.attempt_sha256,
            "cleanupSha256": sha256_hex(cleanup_raw),
            "reconciliationSha256": result["reconciliationSha256"],
            "resultSha256": result_sha,
        },
    )


def _verify_attempt(args: argparse.Namespace) -> dict[str, Any]:
    attempt, _ = _load_canonical_document(args.attempt, contract_error=True)
    evidence, evidence_raw = _load_canonical_document(args.evidence, contract_error=True)
    reconciliation, reconciliation_raw = _load_canonical_document(args.reconciliation, contract_error=True)
    cleanup, cleanup_raw = _load_canonical_document(args.cleanup, contract_error=True)
    ledger, ledger_raw = _load_canonical_document(args.ledger_fragment, contract_error=True)
    revocations, revocations_raw = _load_canonical_document(args.revocations, contract_error=True)
    validated_attempt = validate_attempt(attempt)
    validated_evidence = validate_producer_evidence(evidence)
    validated_ledger = validate_ledger_fragment(ledger)
    validated_reconciliation = validate_reconciliation(reconciliation)
    validated_cleanup = validate_cleanup_aggregate(cleanup)
    validate_revocations(revocations)
    _verify_cleanup_fragments(args.cleanup, validated_cleanup)
    attempt_id = validated_attempt["attemptId"]
    for field, document in (
        ("evidence", validated_evidence), ("reconciliation", validated_reconciliation),
        ("cleanup", validated_cleanup), ("ledger fragment", validated_ledger),
    ):
        if document.get("attemptId") != attempt_id:
            raise ProducerValidationError(f"{field} attemptId differs")
    hashes = {
        "evidenceSha256": sha256_hex(evidence_raw),
        "reconciliationSha256": sha256_hex(reconciliation_raw),
        "cleanupSha256": sha256_hex(cleanup_raw),
        "ledgerFragmentSha256": sha256_hex(ledger_raw),
        "revocationsSha256": sha256_hex(revocations_raw),
    }
    for field, actual in hashes.items():
        if validated_attempt[field] != actual:
            raise ProducerValidationError(f"attempt {field} differs from companion bytes")
    for field in ("evidenceSha256", "reconciliationSha256", "cleanupSha256", "revocationsSha256"):
        if validated_ledger[field] != hashes[field]:
            raise ProducerValidationError(f"ledger fragment {field} differs from companion bytes")
    if validated_attempt["producerStatus"] != validated_reconciliation["producerStatus"]:
        raise ProducerValidationError("producerStatus differs between attempt and reconciliation")
    if validated_attempt["lastCompletedStage"] != validated_reconciliation["lastCompletedStage"]:
        raise ProducerValidationError("lastCompletedStage differs between attempt and reconciliation")
    if validated_attempt["inputLockSha256"] != validated_reconciliation["inputLockSha256"]:
        raise ProducerValidationError("inputLockSha256 differs between attempt and reconciliation")
    for field, document in (("evidence", validated_evidence), ("ledger fragment", validated_ledger)):
        if document["producerStatus"] != validated_attempt["producerStatus"]:
            raise ProducerValidationError(f"producerStatus differs in {field}")
        if document["lastCompletedStage"] != validated_attempt["lastCompletedStage"]:
            raise ProducerValidationError(f"lastCompletedStage differs in {field}")
        if document["inputLockSha256"] != validated_attempt["inputLockSha256"]:
            raise ProducerValidationError(f"inputLockSha256 differs in {field}")
    if validated_ledger["mapped609Status"] != validated_reconciliation["mapped609Status"]:
        raise ProducerValidationError("mapped609Status differs in ledger fragment")
    expected_image = (
        validated_reconciliation["release"]["manifestDigest"]
        if validated_reconciliation["release"] is not None else None
    )
    if validated_ledger["imagePlatformDigest"] != expected_image:
        raise ProducerValidationError("imagePlatformDigest differs in ledger fragment")
    if validated_attempt["revocationsCommitSha"] != validated_reconciliation["revocationsCommitSha"]:
        raise ProducerValidationError("revocationsCommitSha differs between attempt and reconciliation")
    if validated_attempt["previousDocumentSha256"] != validated_reconciliation["previousDocumentSha256"]:
        raise ProducerValidationError("previousDocumentSha256 differs between attempt and reconciliation")
    return build_producer_result(
        validated_reconciliation,
        reconciliation_sha256=hashes["reconciliationSha256"],
        ledger_fragment_sha256=hashes["ledgerFragmentSha256"],
        revocations_sha256=hashes["revocationsSha256"],
    )


def _validate_oci_handoff(args: argparse.Namespace) -> dict[str, Any]:
    document, raw = _load_canonical_document(args.handoff, contract_error=True)
    validated = validate_oci_handoff(document)
    return _success(
        "validate-oci-handoff",
        {
            "attemptId": validated["attemptId"],
            "imagePlatformDigest": validated["imagePlatformDigest"],
            "handoffSha256": sha256_hex(raw),
        },
    )


def _validate_evidence_manifest(args: argparse.Namespace) -> dict[str, Any]:
    document, raw = _load_canonical_document(args.manifest, contract_error=True)
    validated = validate_evidence_file_manifest(document)
    return _success(
        "validate-evidence-manifest",
        {
            "attemptId": validated["attemptId"],
            "subjectDigest": validated["subjectDigest"],
            "fileManifestSha256": sha256_hex(raw),
        },
    )


def _bootstrap_oras(args: argparse.Namespace) -> dict[str, Any]:
    oras_bin = bootstrap_oras_archive(args.archive, args.tool_root)
    return _success(
        "bootstrap-oras",
        {
            "version": ORAS_VERSION,
            "sourceUrl": ORAS_LINUX_AMD64_URL,
            "archiveSha256": ORAS_LINUX_AMD64_SHA256,
            "orasBin": str(oras_bin.resolve()),
        },
    )


def _run_bounded_command(
    command: Sequence[str], limit: int, timeout_seconds: int
) -> bytes:
    process: subprocess.Popen[bytes] | None = None
    try:
        process = subprocess.Popen(
            list(command),
            stdin=subprocess.DEVNULL,
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
            env=dict(os.environ),
        )
        if process.stdout is None:
            raise ProducerRejectedError("public evidence command stdout is unavailable")
        deadline = time.monotonic() + timeout_seconds
        chunks: list[bytes] = []
        total = 0
        while True:
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                raise ProducerInterrupted()
            readable, _, _ = select.select([process.stdout.fileno()], [], [], remaining)
            if not readable:
                raise ProducerInterrupted()
            chunk = os.read(process.stdout.fileno(), min(64 * 1024, limit + 1 - total))
            if not chunk:
                break
            total += len(chunk)
            if total > limit:
                raise ProducerRejectedError("public evidence command output violates byte limit")
            chunks.append(chunk)
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            raise ProducerInterrupted()
        return_code = process.wait(timeout=remaining)
    except subprocess.TimeoutExpired as exc:
        raise ProducerInterrupted() from exc
    except BaseException:
        if process is not None and process.poll() is None:
            process.kill()
            process.wait()
        raise
    finally:
        if process is not None and process.stdout is not None:
            process.stdout.close()
    if return_code != 0:
        raise ProducerRejectedError("public evidence command failed")
    output = b"".join(chunks)
    if not output:
        raise ProducerRejectedError("public evidence command output violates byte limit")
    return output


def _verify_public_evidence(args: argparse.Namespace) -> dict[str, Any]:
    if not 1 <= args.operation_timeout_seconds <= 60:
        raise ProducerValidationError("operation timeout exceeds the public verifier limit")
    if not 1 <= args.materialization_timeout_seconds <= 600:
        raise ProducerValidationError("materialization timeout exceeds the public verifier limit")
    limits = MaterializationLimits(
        manifest_timeout_seconds=args.operation_timeout_seconds,
        blob_read_timeout_seconds=args.operation_timeout_seconds,
        total_timeout_seconds=args.materialization_timeout_seconds,
    )

    def verify_attestation(artifact: Path, bundle: Path, predicate_type: str) -> None:
        raw = _run_bounded_command(
            [
                str(args.gh_bin),
                "attestation", "verify", str(artifact),
                "--repo", "bluetape4k/bluetape4k-image",
                "--bundle", str(bundle),
                "--signer-workflow", "bluetape4k/bluetape4k-image/.github/workflows/paddleocr-producer.yml",
                "--cert-oidc-issuer", "https://token.actions.githubusercontent.com",
                "--source-ref", "refs/heads/develop",
                "--predicate-type", predicate_type,
                "--deny-self-hosted-runners",
                "--format", "json",
            ],
            4 * 1024 * 1024,
            args.operation_timeout_seconds,
        )
        try:
            wrapped = load_json_bytes(b'{"results":' + raw + b"}", 4 * 1024 * 1024 + 12)
        except ProducerValidationError as exc:
            raise ProducerRejectedError("attestation verifier returned malformed JSON") from exc
        if not isinstance(wrapped["results"], list) or not wrapped["results"]:
            raise ProducerRejectedError("attestation verifier returned no verified result")

    result = verify_public_evidence(
        oras_bin=args.oras_bin,
        gh_bin=args.gh_bin,
        reference=args.ref,
        root=args.root,
        environment=dict(os.environ),
        run_command=_run_bounded_command,
        verify_attestation=verify_attestation,
        limits=limits,
    )
    return _success("verify-public-evidence", result)


def build_parser() -> StrictArgumentParser:
    parser = StrictArgumentParser(description="PaddleOCR trusted producer verifier")
    subparsers = parser.add_subparsers(dest="command", required=True)

    validate = subparsers.add_parser("validate-inputs")
    validate.add_argument("--lock", type=Path, required=True)
    validate.add_argument("--policy", type=Path, required=True)
    validate.add_argument("--legal", type=Path, required=True)
    validate.set_defaults(handler=_validate_inputs)

    input_value = subparsers.add_parser("input-value")
    input_value.add_argument("--lock", type=Path, required=True)
    input_value.add_argument("--field", choices=("sourceDateEpoch",), required=True)
    input_value.add_argument("--output", type=Path, required=True)
    input_value.set_defaults(handler=_input_value)

    accept = subparsers.add_parser("accept-resolved-inputs")
    accept.add_argument("--resolution-root", type=Path, required=True)
    accept.add_argument("--input-lock", type=Path, required=True)
    accept.add_argument("--requirements-lock", type=Path, required=True)
    accept.add_argument("--legal-inventory", type=Path, required=True)
    accept.set_defaults(handler=_accept_resolved_inputs)

    source_reproducibility = subparsers.add_parser("verify-source-reproducibility")
    source_reproducibility.add_argument("--input-lock", type=Path, required=True)
    source_reproducibility.add_argument("--work-root", type=Path, required=True)
    source_reproducibility.add_argument("--output-wheelhouse", type=Path, required=True)
    source_reproducibility.add_argument(
        "--prebuilt-wheel", type=Path, action="append", default=[]
    )
    source_reproducibility.add_argument("--registry-wheelhouse", type=Path)
    source_reproducibility.set_defaults(handler=_verify_source_reproducibility)

    stage = subparsers.add_parser("stage-inputs")
    stage.add_argument("--input-lock", type=Path, required=True)
    stage.add_argument("--requirements-lock", type=Path, required=True)
    stage.add_argument("--legal", type=Path, required=True)
    stage.add_argument("--wheelhouse", type=Path, required=True)
    stage.add_argument("--model-archive", action="append", default=[])
    stage.add_argument("--output-artifact", type=Path, required=True)
    stage.set_defaults(handler=_stage_inputs)

    resolve = subparsers.add_parser("resolve-inputs")
    resolve.add_argument("--target-platform", required=True)
    resolve.add_argument("--python-version", required=True)
    resolve.add_argument("--base-image-candidate", required=True)
    resolve.add_argument("--paddleocr-commit", required=True)
    resolve.add_argument("--paddlex-commit", required=True)
    resolve.add_argument("--paddle-commit", required=True)
    resolve.add_argument("--detector", required=True)
    resolve.add_argument("--recognizer", required=True)
    resolve.add_argument("--accepted-root", type=Path, default=Path("docker/paddleocr"))
    resolve.add_argument("--output-root", type=Path, required=True)
    resolve.set_defaults(handler=_resolve_inputs)

    cleanup = subparsers.add_parser("merge-cleanup")
    cleanup.add_argument("--attempt-id", required=True)
    cleanup.add_argument("--original-status", choices=tuple(STATUS_CONTRACT), required=True)
    cleanup.add_argument("--started-job", action="append", default=[])
    cleanup.add_argument("--fragment", type=Path, action="append", default=[])
    cleanup.add_argument("--merged-at", required=True)
    cleanup.add_argument("--output", type=Path, required=True)
    cleanup.set_defaults(handler=_merge_cleanup)

    reconcile = subparsers.add_parser("reconcile")
    reconcile.add_argument("--reconciliation", type=Path, required=True)
    reconcile.set_defaults(handler=_reconcile)

    append = subparsers.add_parser("append-revocation")
    append.add_argument("--previous", type=Path, required=True)
    append.add_argument("--entry", type=Path, action="append", required=True)
    append.add_argument("--output", type=Path, required=True)
    append.set_defaults(handler=_append_revocation)

    emergency = subparsers.add_parser("verify-emergency-receipt")
    emergency.add_argument("--receipt", type=Path, required=True)
    emergency.add_argument("--revocations", type=Path, required=True)
    emergency.set_defaults(handler=_verify_emergency_receipt)

    finalize = subparsers.add_parser("finalize")
    finalize.add_argument("--reconciliation", type=Path, required=True)
    finalize.add_argument("--cleanup", type=Path, required=True)
    finalize.add_argument("--ledger-fragment", type=Path, required=True)
    finalize.add_argument("--revocations", type=Path, required=True)
    finalize.add_argument("--attempt-sha256", required=True)
    finalize.add_argument("--output", type=Path, required=True)
    finalize.set_defaults(handler=_finalize)

    verify = subparsers.add_parser("verify-attempt")
    verify.add_argument("--attempt", type=Path, required=True)
    verify.add_argument("--evidence", type=Path, required=True)
    verify.add_argument("--reconciliation", type=Path, required=True)
    verify.add_argument("--cleanup", type=Path, required=True)
    verify.add_argument("--ledger-fragment", type=Path, required=True)
    verify.add_argument("--revocations", type=Path, required=True)
    verify.set_defaults(handler=_verify_attempt)

    handoff = subparsers.add_parser("validate-oci-handoff")
    handoff.add_argument("--handoff", type=Path, required=True)
    handoff.set_defaults(handler=_validate_oci_handoff)

    evidence_manifest = subparsers.add_parser("validate-evidence-manifest")
    evidence_manifest.add_argument("--manifest", type=Path, required=True)
    evidence_manifest.set_defaults(handler=_validate_evidence_manifest)

    oras = subparsers.add_parser("bootstrap-oras")
    oras.add_argument("--archive", type=Path, required=True)
    oras.add_argument("--tool-root", type=Path, required=True)
    oras.set_defaults(handler=_bootstrap_oras)

    public_evidence = subparsers.add_parser("verify-public-evidence")
    public_evidence.add_argument("--oras-bin", type=Path, required=True)
    public_evidence.add_argument("--gh-bin", type=Path, required=True)
    public_evidence.add_argument("--ref", required=True)
    public_evidence.add_argument("--root", type=Path, required=True)
    public_evidence.add_argument("--operation-timeout-seconds", type=int, default=60)
    public_evidence.add_argument("--materialization-timeout-seconds", type=int, default=600)
    public_evidence.set_defaults(handler=_verify_public_evidence)
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    arguments = list(sys.argv[1:] if argv is None else argv)
    command = arguments[0] if arguments and not arguments[0].startswith("-") else "unknown"
    if any(argument in ("-h", "--help") for argument in arguments):
        build_parser().parse_args(arguments)
        return 0
    try:
        with installed_signal_handlers():
            args = build_parser().parse_args(arguments)
            document = args.handler(args)
        exit_code = 0
        if command == "verify-attempt":
            terminal_exit = STATUS_CONTRACT[document["producerStatus"]].exit_code
            exit_code = terminal_exit if terminal_exit is not None else 40
    except ProducerUsageError:
        document = _failure(command, "SCHEMA_INVALID", "BLOCKED_INPUT", "invalid command arguments")
        exit_code = 40
    except ProducerLegalError:
        document = _failure(command, "BLOCKED_LEGAL_INVENTORY", "LEGAL_INVENTORY", "legal inventory is incomplete or mismatched")
        exit_code = 11
    except ProducerBlockedError:
        document = _failure(command, "BLOCKED_INPUT", "BLOCKED_INPUT", "producer input is incomplete or invalid")
        exit_code = 10
    except ProducerRejectedError:
        document = _failure(command, "REJECTED", "REJECTED", "producer input was rejected")
        exit_code = 22
    except ProducerCancelled:
        document = _failure(command, "CANCELLED", "CANCELLED", "producer operation was cancelled")
        exit_code = 31
    except (ProducerInterrupted, TimeoutError):
        document = _failure(command, "INTERRUPTED", "INTERRUPTED", "producer operation was interrupted")
        exit_code = 32
    except KeyboardInterrupt:
        document = _failure(command, "CANCELLED", "CANCELLED", "producer operation was cancelled")
        exit_code = 31
    except ProducerValidationError:
        document = _failure(command, "SCHEMA_INVALID", "BLOCKED_INPUT", "input document violates its schema")
        exit_code = 40
    except OSError:
        LOGGER.error("producer command failed", extra={"command": command})
        document = _failure(command, "FAILED", "FAILED", "producer operation failed")
        exit_code = 30
    except Exception:  # noqa: BLE001 - the CLI boundary must emit one redacted result
        LOGGER.error("producer command failed", extra={"command": command})
        document = _failure(command, "FAILED", "FAILED", "producer operation failed")
        exit_code = 30
    sys.stdout.buffer.write(jcs_bytes(document) + b"\n")
    return exit_code


if __name__ == "__main__":
    raise SystemExit(main())
