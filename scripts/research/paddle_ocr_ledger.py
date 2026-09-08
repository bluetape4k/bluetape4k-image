from __future__ import annotations

"""Fail-closed image/model ledger builder and verifier for Issue #609-C.

The ledger deliberately stops at immutable image, package, model, legal, and
producer-claim evidence. Runtime isolation (#609-E), attestation acceptance
(#609-D), and comparison/adoption decisions remain separate gates.
"""

import argparse
import json
import os
import re
import stat
import sys
from collections.abc import Mapping, Sequence
from pathlib import Path, PurePosixPath
from typing import Any
from urllib.parse import urlsplit

from paddle_ocr_producer_lib.contracts import (
    ProducerValidationError,
    exact_object,
    jcs_bytes,
    load_json_bytes,
    sha256_hex,
    validate_input_lock,
)
from paddle_ocr_producer_lib.filesystem import canonical_tree_manifest

SCHEMA_VERSION = 1
LEDGER_KIND = "IMAGE_MODEL_LEDGER"
EVIDENCE_ARTIFACT_TYPE = "application/vnd.bluetape4k.paddleocr.producer-evidence.v1"
MAX_DOCUMENT_BYTES = 2 * 1024 * 1024
_SHA256_RE = re.compile(r"\A[0-9a-f]{64}\Z")
_OCI_DIGEST_RE = re.compile(r"\Asha256:[0-9a-f]{64}\Z")
_IMAGE_REF_RE = re.compile(r"\A[^\s@]+@sha256:[0-9a-f]{64}\Z")
_COMMIT_RE = re.compile(r"\A[0-9a-f]{40}\Z")
_ATTEMPT_RE = re.compile(r"\A[1-9][0-9]*\.[1-9][0-9]*\Z")
_CORE_EVIDENCE_FILES = (
    "inputs/producer-input.lock.json",
    "manifests/package-lock.json",
    "manifests/model-detector.json",
    "manifests/model-recognizer.json",
    "platform-manifest.json",
    "legal-inventory.json",
)
_MODEL_ROLES = ("detector", "recognizer")
_VERIFICATION_KEYS = (
    "indexSelectsPlatformManifest",
    "manifestConfigMatches",
    "baseDigestsMatch",
    "packageLockMatches",
    "modelFilesMatch",
    "modelTreeRecomputed",
    "pairBindingMatches",
    "licenseNoticeVerified",
    "trustPolicyMatched",
    "canonicalJson",
)


class LedgerValidationError(ValueError):
    """Raised when a #609-C ledger or one of its inputs is not trustworthy."""


def pair_binding_sha256(models: Sequence[Mapping[str, Any]]) -> str:
    """Hash detector and recognizer trees in a fixed, unambiguous order."""

    trees: dict[str, str] = {}
    for model in models:
        role = model.get("role")
        tree = model.get("treeSha256")
        if role in _MODEL_ROLES and isinstance(tree, str):
            trees[role] = tree
    if set(trees) != set(_MODEL_ROLES):
        raise LedgerValidationError("model pair requires exactly detector and recognizer trees")
    raw = (
        f"detector\n{trees['detector']}\n"
        f"recognizer\n{trees['recognizer']}\n"
    ).encode()
    return sha256_hex(raw)


def _raise(message: str) -> LedgerValidationError:
    return LedgerValidationError(message)


def _exact(value: Any, required: set[str], field: str) -> dict[str, Any]:
    try:
        return exact_object(value, required=required)
    except ProducerValidationError as exc:
        raise _raise(f"{field}: {exc}") from exc


def _sha(value: Any, field: str) -> str:
    if not isinstance(value, str) or _SHA256_RE.fullmatch(value) is None:
        raise _raise(f"{field} must be 64 lowercase hexadecimal characters")
    return value


def _oci(value: Any, field: str) -> str:
    if not isinstance(value, str) or _OCI_DIGEST_RE.fullmatch(value) is None:
        raise _raise(f"{field} must be a lowercase sha256 OCI digest")
    return value


def _image_ref(value: Any, field: str) -> str:
    if not isinstance(value, str) or _IMAGE_REF_RE.fullmatch(value) is None:
        raise _raise(f"{field} must be a digest-pinned OCI reference")
    return value


def _commit(value: Any, field: str) -> str:
    if not isinstance(value, str) or _COMMIT_RE.fullmatch(value) is None:
        raise _raise(f"{field} must be a full lowercase commit")
    return value


def _string(value: Any, field: str) -> str:
    if not isinstance(value, str) or not value.strip() or "PENDING" in value.upper():
        raise _raise(f"{field} must be a resolved non-empty string")
    return value


def _positive(value: Any, field: str) -> int:
    if type(value) is not int or value <= 0:
        raise _raise(f"{field} must be a positive integer")
    return value


def _read_regular(path: Path, *, max_bytes: int = MAX_DOCUMENT_BYTES) -> bytes:
    """Read one regular, non-symlink file without following the final path."""

    try:
        descriptor = os.open(
            path,
            os.O_RDONLY
            | getattr(os, "O_NOFOLLOW", 0)
            | getattr(os, "O_CLOEXEC", 0),
        )
    except OSError as exc:
        raise _raise(f"file is unavailable or is a symlink: {path}") from exc
    try:
        metadata = os.fstat(descriptor)
        if not stat.S_ISREG(metadata.st_mode):
            raise _raise(f"file must be regular: {path}")
        if metadata.st_size > max_bytes:
            raise _raise(f"file exceeds {max_bytes} bytes: {path}")
        chunks: list[bytes] = []
        total = 0
        while True:
            chunk = os.read(descriptor, 1024 * 1024)
            if not chunk:
                break
            total += len(chunk)
            if total > max_bytes:
                raise _raise(f"file exceeds {max_bytes} bytes: {path}")
            chunks.append(chunk)
        return b"".join(chunks)
    finally:
        os.close(descriptor)


def _safe_path(root: Path, relative: Any) -> Path:
    if not isinstance(relative, str) or not relative or "\\" in relative:
        raise _raise("relative path is invalid")
    if root.is_symlink() or not root.is_dir():
        raise _raise("root must be a non-symlink directory")
    parsed = PurePosixPath(relative)
    if parsed.is_absolute() or any(part in {"", ".", ".."} for part in parsed.parts):
        raise _raise("relative path escapes its root")
    current = root
    for part in parsed.parts[:-1]:
        current = current / part
        if current.is_symlink() or not current.is_dir():
            raise _raise("relative path parent must be a non-symlink directory")
    target = current / parsed.parts[-1]
    if target.is_symlink():
        raise _raise("relative path must not be a symlink")
    return target


def _file_descriptor(root: Path, relative: str) -> dict[str, Any]:
    target = _safe_path(root, relative)
    raw = _read_regular(target, max_bytes=512 * 1024 * 1024)
    return {"path": relative, "bytes": len(raw), "sha256": sha256_hex(raw)}


def _read_strict_json(path: Path) -> tuple[dict[str, Any], bytes]:
    raw = _read_regular(path)
    try:
        document = load_json_bytes(raw, MAX_DOCUMENT_BYTES, max_depth=64, max_entries=50_000)
    except ProducerValidationError as exc:
        raise _raise(f"{path}: strict JSON validation failed") from exc
    return document, raw


def _read_canonical(path: Path) -> tuple[dict[str, Any], bytes]:
    document, raw = _read_strict_json(path)
    canonical = jcs_bytes(document)
    if raw not in (canonical, canonical + b"\n"):
        raise _raise(f"{path}: JSON must use canonical JCS bytes with at most one trailing newline")
    return document, raw


def _validate_descriptor(value: Any, field: str) -> dict[str, Any]:
    descriptor = _exact(value, {"path", "bytes", "sha256"}, field)
    path = descriptor["path"]
    if not isinstance(path, str) or not path or "\\" in path:
        raise _raise(f"{field}.path is invalid")
    parsed = PurePosixPath(path)
    if parsed.is_absolute() or any(part in {"", ".", ".."} for part in parsed.parts):
        raise _raise(f"{field}.path escapes its root")
    _positive(descriptor["bytes"], f"{field}.bytes")
    _sha(descriptor["sha256"], f"{field}.sha256")
    return descriptor


def _sorted_unique_descriptors(value: Any, field: str) -> list[dict[str, Any]]:
    if not isinstance(value, list) or not value:
        raise _raise(f"{field} must be a non-empty array")
    result = [_validate_descriptor(item, f"{field}[{index}]") for index, item in enumerate(value)]
    paths = [item["path"] for item in result]
    if paths != sorted(paths) or len(paths) != len(set(paths)):
        raise _raise(f"{field} paths must be unique and sorted")
    return result


def _hosts_from_lock(value: Any) -> set[str]:
    hosts: set[str] = set()

    def collect(nested: Any) -> None:
        if isinstance(nested, Mapping):
            url = nested.get("url")
            if isinstance(url, str):
                try:
                    hostname = urlsplit(url).hostname
                except ValueError:
                    hostname = None
                if hostname:
                    hosts.add(hostname.lower().rstrip("."))
            for child in nested.values():
                collect(child)
        elif isinstance(nested, list):
            for child in nested:
                collect(child)

    collect(value)
    return hosts


def _validate_legal_inventory(value: Any) -> dict[str, Any]:
    inventory = _exact(value, {"schemaVersion", "components"}, "legal inventory")
    if inventory["schemaVersion"] != 1:
        raise _raise("legal inventory schemaVersion must be 1")
    components = inventory["components"]
    if not isinstance(components, list) or not components:
        raise _raise("legal inventory must contain components")
    required = {
        "id",
        "licenseExpression",
        "licenseSourceUrl",
        "licenseSha256",
        "notice",
        "noticeSha256",
    }
    ids: set[str] = set()
    for index, raw_component in enumerate(components):
        component = _exact(raw_component, required, f"legal inventory.components[{index}]")
        for name in ("id", "licenseExpression", "licenseSourceUrl", "notice"):
            _string(component[name], f"legal inventory.components[{index}].{name}")
        _sha(component["licenseSha256"], f"legal inventory.components[{index}].licenseSha256")
        _sha(component["noticeSha256"], f"legal inventory.components[{index}].noticeSha256")
        if sha256_hex(component["notice"].encode("utf-8")) != component["noticeSha256"]:
            raise _raise(f"legal inventory notice sha256 differs at component {index}")
        if component["id"] in ids:
            raise _raise("legal inventory component ids must be unique")
        ids.add(component["id"])
    return inventory


def _validate_policy(policy: Mapping[str, Any], field: str = "trust policy") -> dict[str, Any]:
    required = {
        "actors",
        "audiences",
        "hosts",
        "oidcIssuers",
        "refs",
        "repositories",
        "runnerEnvironments",
        "schemaVersion",
        "workflows",
    }
    document = _exact(policy, required, field)
    if document["schemaVersion"] != 1:
        raise _raise(f"{field}.schemaVersion must be 1")
    for name in required - {"schemaVersion"}:
        values = document[name]
        if (
            not isinstance(values, list)
            or not values
            or any(not isinstance(item, str) or not item or "PENDING" in item.upper() for item in values)
            or len(values) != len(set(values))
        ):
            raise _raise(f"{field}.{name} must be a unique non-empty string array")
    return document


def _load_sources(evidence_root: Path) -> dict[str, Any]:
    if evidence_root.is_symlink() or not evidence_root.is_dir():
        raise _raise("evidence root must be a non-symlink directory")
    evidence_manifest, evidence_manifest_raw = _read_strict_json(evidence_root / "evidence-manifest.json")
    manifest = _exact(
        evidence_manifest,
        {
            "schemaVersion",
            "artifactType",
            "imageIndexDigest",
            "imagePlatformDigest",
            "imageConfigDigest",
            "baseDigest",
            "subjectDigest",
            "attemptId",
            "runId",
            "runAttempt",
            "inputLockSha256",
            "revocationsSha256",
            "revocationsCommitSha",
            "files",
        },
        "evidence-manifest",
    )
    if manifest["schemaVersion"] != 1 or manifest["artifactType"] != EVIDENCE_ARTIFACT_TYPE:
        raise _raise("evidence-manifest schema or artifact type differs")
    for name in ("imageIndexDigest", "imagePlatformDigest", "imageConfigDigest", "baseDigest", "subjectDigest"):
        _oci(manifest[name], f"evidence-manifest.{name}")
    if manifest["imageIndexDigest"] != manifest["imagePlatformDigest"]:
        raise _raise("609-C requires a single-platform index selecting its platform manifest")
    if manifest["subjectDigest"] != manifest["imagePlatformDigest"]:
        raise _raise("evidence subject differs from image platform digest")
    if not isinstance(manifest["attemptId"], str) or _ATTEMPT_RE.fullmatch(manifest["attemptId"]) is None:
        raise _raise("evidence-manifest.attemptId is invalid")
    _positive(manifest["runId"], "evidence-manifest.runId")
    _positive(manifest["runAttempt"], "evidence-manifest.runAttempt")
    if manifest["attemptId"] != f'{manifest["runId"]}.{manifest["runAttempt"]}':
        raise _raise("evidence-manifest attempt identity differs")
    _sha(manifest["inputLockSha256"], "evidence-manifest.inputLockSha256")
    _sha(manifest["revocationsSha256"], "evidence-manifest.revocationsSha256")
    _commit(manifest["revocationsCommitSha"], "evidence-manifest.revocationsCommitSha")

    raw_descriptors = manifest["files"]
    if not isinstance(raw_descriptors, list) or not raw_descriptors:
        raise _raise("evidence-manifest.files must be a non-empty array")
    descriptors: dict[str, dict[str, Any]] = {}
    for index, raw_descriptor in enumerate(raw_descriptors):
        descriptor = _validate_descriptor(raw_descriptor, f"evidence-manifest.files[{index}]")
        if descriptor["path"] in descriptors:
            raise _raise("evidence-manifest.files paths must be unique")
        descriptors[descriptor["path"]] = descriptor
        actual = _file_descriptor(evidence_root, descriptor["path"])
        if actual != descriptor:
            raise _raise(f"evidence file descriptor differs: {descriptor['path']}")
    for relative in _CORE_EVIDENCE_FILES:
        if relative not in descriptors:
            raise _raise(f"evidence-manifest is missing {relative}")

    input_lock, input_lock_raw = _read_strict_json(evidence_root / "inputs/producer-input.lock.json")
    package_lock, package_lock_raw = _read_strict_json(evidence_root / "manifests/package-lock.json")
    detector_manifest, detector_raw = _read_strict_json(evidence_root / "manifests/model-detector.json")
    recognizer_manifest, recognizer_raw = _read_strict_json(evidence_root / "manifests/model-recognizer.json")
    platform_manifest, platform_raw = _read_strict_json(evidence_root / "platform-manifest.json")
    legal_inventory, legal_raw = _read_strict_json(evidence_root / "legal-inventory.json")

    try:
        validate_input_lock(input_lock, allowed_hosts=_hosts_from_lock(input_lock))
    except ProducerValidationError as exc:
        raise _raise(f"input lock validation failed: {exc}") from exc
    legal_inventory = _validate_legal_inventory(legal_inventory)

    if sha256_hex(input_lock_raw) != manifest["inputLockSha256"]:
        raise _raise("input lock digest differs from evidence-manifest")
    if "sha256:" + sha256_hex(platform_raw) != manifest["imagePlatformDigest"]:
        raise _raise("platform manifest bytes differ from image platform digest")
    if sha256_hex(legal_raw) != input_lock.get("legalInventorySha256"):
        raise _raise("legal inventory bytes differ from input lock")
    if not isinstance(input_lock, dict) or input_lock.get("schemaVersion") != 1:
        raise _raise("input lock schemaVersion must be 1")
    if input_lock.get("targetPlatform") != "linux/amd64":
        raise _raise("609-C target platform must be linux/amd64")
    base = _exact(input_lock.get("baseImage"), {"reference", "indexDigest", "platformDigest", "configDigest", "os", "architecture", "variant"}, "input lock.baseImage")
    if base["platformDigest"] != manifest["baseDigest"]:
        raise _raise("base platform digest differs from evidence-manifest")
    if base["os"] != "linux" or base["architecture"] != "amd64" or base["variant"] is not None:
        raise _raise("input lock base platform is not canonical linux/amd64")

    package_lock = _exact(package_lock, {"schemaVersion", "packages"}, "package lock")
    if package_lock["schemaVersion"] != 1 or package_lock["packages"] != input_lock.get("packages"):
        raise _raise("package lock differs from input lock")
    if not isinstance(input_lock.get("models"), list) or len(input_lock["models"]) != 2:
        raise _raise("input lock must contain exactly two models")
    models_by_role = {item.get("role"): item for item in input_lock["models"] if isinstance(item, dict)}
    if set(models_by_role) != set(_MODEL_ROLES):
        raise _raise("input lock model roles must be detector and recognizer")
    for role, model_manifest in (("detector", detector_manifest), ("recognizer", recognizer_manifest)):
        model = models_by_role[role]
        for name in (
            "role", "modelId", "modelRevision", "sourceId", "url", "bytes", "sha256",
            "mediaType", "archiveType", "treeSha256", "licenseExpression",
            "licenseSourcePath", "licenseSha256", "noticePath", "noticeSha256",
        ):
            if model_manifest.get(name) != model.get(name):
                raise _raise(f"{role} model manifest differs from input lock at {name}")
        _commit(model["modelRevision"], f"{role}.modelRevision")
        _positive(model["bytes"], f"{role}.bytes")
        _sha(model["sha256"], f"{role}.sha256")
        _sha(model["treeSha256"], f"{role}.treeSha256")
        _sha(model["licenseSha256"], f"{role}.licenseSha256")
        _sha(model["noticeSha256"], f"{role}.noticeSha256")

    platform = _exact(platform_manifest, {"schemaVersion", "mediaType", "config", "layers"}, "platform manifest")
    if platform["schemaVersion"] != 2 or platform["mediaType"] != "application/vnd.oci.image.manifest.v1+json":
        raise _raise("platform manifest schema or media type differs")
    config = _exact(platform["config"], {"mediaType", "digest", "size"}, "platform manifest.config")
    if config["mediaType"] != "application/vnd.oci.image.config.v1+json":
        raise _raise("platform manifest config media type differs")
    _oci(config["digest"], "platform manifest.config.digest")
    _positive(config["size"], "platform manifest.config.size")
    if config["digest"] != manifest["imageConfigDigest"]:
        raise _raise("platform manifest config digest differs")
    layers = platform["layers"]
    if not isinstance(layers, list) or not layers:
        raise _raise("platform manifest must contain layers")
    for index, layer in enumerate(layers):
        descriptor = _exact(layer, {"mediaType", "digest", "size"}, f"platform manifest.layers[{index}]")
        _string(descriptor["mediaType"], f"platform manifest.layers[{index}].mediaType")
        _oci(descriptor["digest"], f"platform manifest.layers[{index}].digest")
        _positive(descriptor["size"], f"platform manifest.layers[{index}].size")

    return {
        "evidenceManifest": manifest,
        "evidenceManifestRaw": evidence_manifest_raw,
        "evidenceManifestSha256": sha256_hex(evidence_manifest_raw),
        "evidenceDescriptors": descriptors,
        "inputLock": input_lock,
        "inputLockRaw": input_lock_raw,
        "inputLockSha256": sha256_hex(input_lock_raw),
        "packageLock": package_lock,
        "packageLockRaw": package_lock_raw,
        "packageLockSha256": sha256_hex(package_lock_raw),
        "modelManifests": {"detector": detector_manifest, "recognizer": recognizer_manifest},
        "modelManifestRaws": {"detector": detector_raw, "recognizer": recognizer_raw},
        "platformManifest": platform,
        "platformManifestRaw": platform_raw,
        "legalInventory": legal_inventory,
        "legalInventoryRaw": legal_raw,
        "legalInventorySha256": sha256_hex(legal_raw),
    }


def _tree_descriptors(model_root: Path, role: str) -> tuple[list[dict[str, Any]], str]:
    role_root = _safe_path(model_root, f"models/{role}")
    if role_root.is_symlink() or not role_root.is_dir():
        raise _raise(f"model {role} root must be a non-symlink directory")
    try:
        manifest_raw = canonical_tree_manifest(role_root)
    except (OSError, ProducerValidationError) as exc:
        raise _raise(f"model {role} tree is invalid: {exc}") from exc
    entries: list[dict[str, Any]] = []
    for line in manifest_raw.decode("utf-8").splitlines():
        try:
            relative, bytes_text, digest = line.split("\t")
            size = int(bytes_text)
        except (ValueError, UnicodeError) as exc:
            raise _raise(f"model {role} tree manifest is malformed") from exc
        entries.append({"path": f"models/{role}/{relative}", "bytes": size, "sha256": digest})
    entries.sort(key=lambda item: item["path"])
    return entries, sha256_hex(manifest_raw)


def _model_ledger_entry(
    role: str,
    lock_model: Mapping[str, Any],
    manifest: Mapping[str, Any],
    entries: list[dict[str, Any]],
    tree_sha256: str,
    legal_root: Path,
) -> tuple[dict[str, Any], list[dict[str, Any]]]:
    if tree_sha256 != lock_model["treeSha256"] or tree_sha256 != manifest["treeSha256"]:
        raise _raise(f"model {role} tree sha256 differs from lock/manifest")
    legal_files: list[dict[str, Any]] = []
    for kind, path_field, sha_field in (
        ("license", "licenseSourcePath", "licenseSha256"),
        ("notice", "noticePath", "noticeSha256"),
    ):
        relative = lock_model[path_field]
        descriptor = _file_descriptor(legal_root, relative)
        if descriptor["sha256"] != lock_model[sha_field]:
            raise _raise(f"model {role} {kind} bytes differ from lock")
        legal_files.append({"kind": kind, **descriptor})
    legal_files.sort(key=lambda item: item["path"])
    entry = {
        "role": role,
        "modelId": lock_model["modelId"],
        "modelRevision": lock_model["modelRevision"],
        "sourceId": lock_model["sourceId"],
        "sourceUrl": lock_model["url"],
        "archiveBytes": lock_model["bytes"],
        "archiveSha256": lock_model["sha256"],
        "mediaType": lock_model["mediaType"],
        "archiveType": lock_model["archiveType"],
        "licenseExpression": lock_model["licenseExpression"],
        "licensePath": lock_model["licenseSourcePath"],
        "licenseSha256": lock_model["licenseSha256"],
        "noticePath": lock_model["noticePath"],
        "noticeSha256": lock_model["noticeSha256"],
        "files": entries,
        "treeSha256": tree_sha256,
    }
    return entry, legal_files


def _build_trust_policy(
    policy: Mapping[str, Any],
    *,
    repository: str,
    workflow: str,
    workflow_ref: str,
    builder_identity: str,
    runner_environment: str,
    signer_identity: str,
    oidc_issuer: str,
    policy_sha256: str,
) -> dict[str, Any]:
    source = _validate_policy(policy)
    if repository not in source["repositories"]:
        raise _raise("producer repository is not in trust policy")
    if workflow not in source["workflows"]:
        raise _raise("producer workflow is not in trust policy")
    if not workflow_ref.endswith("@" + source["refs"][0]) or not workflow_ref.startswith(repository + "/" + workflow):
        raise _raise("producer workflowRef does not bind repository/workflow/ref")
    if runner_environment not in source["runnerEnvironments"]:
        raise _raise("producer runner environment is not in trust policy")
    if not builder_identity.startswith(runner_environment + "/"):
        raise _raise("builder identity does not bind runner environment")
    if signer_identity != repository + "/" + workflow:
        raise _raise("signer identity does not bind repository/workflow")
    if oidc_issuer not in source["oidcIssuers"]:
        raise _raise("producer OIDC issuer is not in trust policy")
    return {
        "allowedRepositories": list(source["repositories"]),
        "allowedWorkflowPaths": list(source["workflows"]),
        "allowedWorkflowRefs": [workflow_ref],
        "allowedBuilderIdentities": [builder_identity],
        "allowedSignerIdentities": [signer_identity],
        "allowedOidcIssuers": list(source["oidcIssuers"]),
        "sourceRevisionPolicy": "full-commit",
        "policySha256": policy_sha256,
        "allowlistNonEmpty": True,
    }


def _validate_trust_policy(
    ledger: Mapping[str, Any],
    policy_path: Path,
) -> bool:
    policy, policy_raw = _read_canonical(policy_path)
    source = _validate_policy(policy)
    trust = _exact(
        ledger["trustPolicy"],
        {
            "allowedRepositories",
            "allowedWorkflowPaths",
            "allowedWorkflowRefs",
            "allowedBuilderIdentities",
            "allowedSignerIdentities",
            "allowedOidcIssuers",
            "sourceRevisionPolicy",
            "policySha256",
            "allowlistNonEmpty",
        },
        "trustPolicy",
    )
    if trust["policySha256"] != sha256_hex(policy_raw):
        raise _raise("trust policy bytes differ from policySha256")
    if trust["sourceRevisionPolicy"] != "full-commit" or trust["allowlistNonEmpty"] is not True:
        raise _raise("trust policy source revision or allowlist contract differs")
    producer = ledger["producer"]
    repository = producer["repository"]
    workflow = producer["workflow"]
    ref = producer["ref"]
    workflow_ref = producer["workflowRef"]
    expected_workflow_ref = f"{repository}/{workflow}@{ref}"
    if workflow_ref != expected_workflow_ref:
        raise _raise("producer.workflowRef does not equal repository/workflow/ref")
    if repository not in source["repositories"]:
        raise _raise("producer repository is not allowlisted")
    if workflow not in source["workflows"]:
        raise _raise("producer workflow is not allowlisted")
    if ref not in source["refs"]:
        raise _raise("producer ref is not allowlisted")
    if producer["runnerEnvironment"] not in source["runnerEnvironments"]:
        raise _raise("producer runner environment is not allowlisted")
    if producer["oidcIssuer"] not in source["oidcIssuers"]:
        raise _raise("producer OIDC issuer is not allowlisted")
    if trust["allowedRepositories"] != source["repositories"]:
        raise _raise("trustPolicy.allowedRepositories differs from source policy")
    if trust["allowedWorkflowPaths"] != source["workflows"]:
        raise _raise("trustPolicy.allowedWorkflowPaths differs from source policy")
    if trust["allowedWorkflowRefs"] != [workflow_ref]:
        raise _raise("trustPolicy.allowedWorkflowRefs differs from producer")
    if trust["allowedBuilderIdentities"] != [producer["builderIdentity"]]:
        raise _raise("trustPolicy.allowedBuilderIdentities differs from producer")
    if trust["allowedSignerIdentities"] != [producer["signerIdentity"]]:
        raise _raise("trustPolicy.allowedSignerIdentities differs from producer")
    if trust["allowedOidcIssuers"] != source["oidcIssuers"]:
        raise _raise("trustPolicy.allowedOidcIssuers differs from source policy")
    if not producer["builderIdentity"].startswith(producer["runnerEnvironment"] + "/"):
        raise _raise("builder identity does not bind runner environment")
    if producer["signerIdentity"] != repository + "/" + workflow:
        raise _raise("signer identity does not bind repository/workflow")
    return True


def _validate_model_ledger(
    ledger_models: Sequence[Mapping[str, Any]],
    sources: Mapping[str, Any],
    model_root: Path,
    legal_root: Path,
) -> tuple[bool, bool, list[dict[str, Any]]]:
    if not isinstance(ledger_models, list) or len(ledger_models) != 2:
        raise _raise("model.models must contain exactly detector and recognizer")
    by_role = {item.get("role"): item for item in ledger_models if isinstance(item, dict)}
    if set(by_role) != set(_MODEL_ROLES):
        raise _raise("model.models roles must be detector and recognizer")
    lock_models = {item["role"]: item for item in sources["inputLock"]["models"]}
    all_legal_files: list[dict[str, Any]] = []
    tree_ok = True
    files_ok = True
    for role in _MODEL_ROLES:
        model = _exact(
            by_role[role],
            {
                "role",
                "modelId",
                "modelRevision",
                "sourceId",
                "sourceUrl",
                "archiveBytes",
                "archiveSha256",
                "mediaType",
                "archiveType",
                "licenseExpression",
                "licensePath",
                "licenseSha256",
                "noticePath",
                "noticeSha256",
                "files",
                "treeSha256",
            },
            f"model.models[{role}]",
        )
        lock_model = lock_models[role]
        mapping = {
            "modelId": "modelId",
            "modelRevision": "modelRevision",
            "sourceId": "sourceId",
            "sourceUrl": "url",
            "archiveBytes": "bytes",
            "archiveSha256": "sha256",
            "mediaType": "mediaType",
            "archiveType": "archiveType",
            "licenseExpression": "licenseExpression",
            "licensePath": "licenseSourcePath",
            "licenseSha256": "licenseSha256",
            "noticePath": "noticePath",
            "noticeSha256": "noticeSha256",
            "treeSha256": "treeSha256",
        }
        for ledger_name, lock_name in mapping.items():
            if model[ledger_name] != lock_model[lock_name]:
                raise _raise(f"model {role}.{ledger_name} differs from input lock")
        entries = _sorted_unique_descriptors(model["files"], f"model.models[{role}].files")
        actual_entries, actual_tree = _tree_descriptors(model_root, role)
        if entries != actual_entries:
            files_ok = False
            raise _raise(f"model file manifest for {role} differs from staged files")
        if actual_tree != model["treeSha256"]:
            tree_ok = False
            raise _raise(f"model {role} tree sha256 differs from staged files")
        for entry in entries:
            actual = _file_descriptor(model_root, entry["path"])
            if actual != entry:
                files_ok = False
                raise _raise(f"model {role} file digest differs: {entry['path']}")
        for kind, path_name, sha_name in (
            ("license", "licensePath", "licenseSha256"),
            ("notice", "noticePath", "noticeSha256"),
        ):
            descriptor = _file_descriptor(legal_root, model[path_name])
            if descriptor["sha256"] != model[sha_name]:
                raise _raise(f"model {role} {kind} digest differs")
            all_legal_files.append({"kind": kind, **descriptor})
    return files_ok, tree_ok, sorted(all_legal_files, key=lambda item: item["path"])


def _validate_ledger_inputs(
    ledger: Mapping[str, Any],
    *,
    evidence_root: Path,
    model_root: Path,
    legal_root: Path,
    policy_path: Path,
) -> dict[str, Any]:
    sources = _load_sources(evidence_root)
    producer = _exact(
        ledger["producer"],
        {
            "repository",
            "workflow",
            "workflowRef",
            "workflowSha",
            "ref",
            "workflowRunId",
            "sourceRevision",
            "builderIdentity",
            "runnerEnvironment",
            "signerIdentity",
            "oidcIssuer",
        },
        "producer",
    )
    for field in ("repository", "workflow", "workflowRef", "builderIdentity", "runnerEnvironment", "signerIdentity", "oidcIssuer", "ref"):
        _string(producer[field], f"producer.{field}")
    if not isinstance(producer["workflowRunId"], str) or _ATTEMPT_RE.fullmatch(producer["workflowRunId"]) is None:
        raise _raise("producer.workflowRunId is invalid")
    _commit(producer["sourceRevision"], "producer.sourceRevision")
    _commit(producer["workflowSha"], "producer.workflowSha")
    if producer["workflowRunId"] != sources["evidenceManifest"]["attemptId"]:
        raise _raise("producer.workflowRunId differs from evidence attempt")
    if producer["sourceRevision"] == "0" * 40:
        raise _raise("producer.sourceRevision must be immutable")

    image = _exact(
        ledger["image"],
        {
            "imageRef",
            "imageIndexDigest",
            "platformManifestDigest",
            "configDigest",
            "os",
            "architecture",
            "variant",
            "targetPlatform",
            "baseImageDigests",
            "packageLockSha256",
        },
        "image",
    )
    _image_ref(image["imageRef"], "image.imageRef")
    for field in ("imageIndexDigest", "platformManifestDigest", "configDigest"):
        _oci(image[field], f"image.{field}")
    if image["imageIndexDigest"] != sources["evidenceManifest"]["imageIndexDigest"]:
        raise _raise("image index digest differs from evidence manifest")
    if image["platformManifestDigest"] != sources["evidenceManifest"]["imagePlatformDigest"]:
        raise _raise("image platform digest differs from evidence manifest")
    if image["configDigest"] != sources["evidenceManifest"]["imageConfigDigest"]:
        raise _raise("image config digest differs from evidence manifest")
    if image["os"] != "linux" or image["architecture"] != "amd64" or image["variant"] is not None:
        raise _raise("image platform must be canonical linux/amd64")
    if image["targetPlatform"] != "linux/amd64":
        raise _raise("image targetPlatform must be linux/amd64")
    base_digests = image["baseImageDigests"]
    if not isinstance(base_digests, list) or base_digests != [sources["inputLock"]["baseImage"]["platformDigest"]]:
        raise _raise("image.baseImageDigests differs from input lock")
    _sha(image["packageLockSha256"], "image.packageLockSha256")
    if image["packageLockSha256"] != sources["packageLockSha256"]:
        raise _raise("image.packageLockSha256 differs from package manifest bytes")

    files_ok, tree_ok, legal_files = _validate_model_ledger(
        ledger["model"]["models"], sources, model_root, legal_root
    )
    model = _exact(ledger["model"], {"models", "pairBindingSha256"}, "model")
    if model["pairBindingSha256"] != pair_binding_sha256(model["models"]):
        raise _raise("model.pairBindingSha256 differs from fixed role binding")
    legal = _exact(
        ledger["licenseNotice"],
        {"inventoryPath", "inventorySha256", "files", "complete", "verified"},
        "licenseNotice",
    )
    if legal["inventoryPath"] != "legal-inventory.json":
        raise _raise("licenseNotice.inventoryPath must point to evidence legal-inventory.json")
    if legal["inventorySha256"] != sources["legalInventorySha256"]:
        raise _raise("licenseNotice.inventorySha256 differs from legal inventory bytes")
    if legal["files"] != legal_files:
        raise _raise("licenseNotice.files differs from model legal files")
    if legal["complete"] is not True or legal["verified"] is not True:
        raise _raise("licenseNotice must be complete and verified")
    components = sources["legalInventory"].get("components")
    if not isinstance(components, list):
        raise _raise("legal inventory components must be an array")
    components_by_id = {item.get("id"): item for item in components if isinstance(item, dict)}
    for role in _MODEL_ROLES:
        component = components_by_id.get("model-" + role)
        if not isinstance(component, dict):
            raise _raise(f"legal inventory is missing model-{role}")
        ledger_model = next(item for item in ledger["model"]["models"] if item["role"] == role)
        if component.get("licenseSha256") != ledger_model["licenseSha256"]:
            raise _raise(f"legal inventory license hash differs for {role}")
        if component.get("noticeSha256") != ledger_model["noticeSha256"]:
            raise _raise(f"legal inventory notice hash differs for {role}")
        notice = component.get("notice")
        if not isinstance(notice, str) or sha256_hex(notice.encode("utf-8")) != component["noticeSha256"]:
            raise _raise(f"legal inventory notice bytes differ for {role}")

    trust_ok = _validate_trust_policy(ledger, policy_path)
    evidence = _exact(
        ledger["evidence"],
        {"manifest", "files", "inputLock", "packageLock", "platformManifest", "modelManifests", "legalInventory"},
        "evidence",
    )
    evidence_files = _sorted_unique_descriptors(evidence["files"], "evidence.files")
    expected_files = [
        _file_descriptor(evidence_root, relative)
        for relative in ("evidence-manifest.json", *_CORE_EVIDENCE_FILES)
    ]
    if evidence_files != sorted(expected_files, key=lambda item: item["path"]):
        raise _raise("evidence.files differs from core evidence bytes")
    for name, relative, expected_sha in (
        ("manifest", "evidence-manifest.json", sources["evidenceManifestSha256"]),
        ("inputLock", "inputs/producer-input.lock.json", sources["inputLockSha256"]),
        ("packageLock", "manifests/package-lock.json", sources["packageLockSha256"]),
        ("platformManifest", "platform-manifest.json", sources["evidenceManifest"]["imagePlatformDigest"][7:]),
        ("legalInventory", "legal-inventory.json", sources["legalInventorySha256"]),
    ):
        descriptor = _exact(evidence[name], {"path", "bytes", "sha256"}, f"evidence.{name}")
        if descriptor["path"] != relative or descriptor["sha256"] != expected_sha:
            raise _raise(f"evidence.{name} descriptor differs")
        actual = _file_descriptor(evidence_root, relative)
        if descriptor != actual:
            raise _raise(f"evidence.{name} bytes differ")
    model_manifest_descriptors = evidence["modelManifests"]
    if not isinstance(model_manifest_descriptors, dict) or set(model_manifest_descriptors) != set(_MODEL_ROLES):
        raise _raise("evidence.modelManifests must contain detector and recognizer")
    for role in _MODEL_ROLES:
        descriptor = _exact(model_manifest_descriptors[role], {"path", "bytes", "sha256"}, f"evidence.modelManifests.{role}")
        relative = f"manifests/model-{role}.json"
        actual = _file_descriptor(evidence_root, relative)
        if descriptor["path"] != relative or descriptor != actual:
            raise _raise(f"evidence model manifest differs for {role}")

    return {
        "sources": sources,
        "filesOk": files_ok,
        "treeOk": tree_ok,
        "trustOk": trust_ok,
    }


def _unsigned_checksum(ledger: Mapping[str, Any]) -> str:
    unsigned = dict(ledger)
    unsigned.pop("checksum", None)
    return sha256_hex(jcs_bytes(unsigned))


def validate_ledger(
    ledger: Mapping[str, Any],
    *,
    evidence_root: Path,
    model_root: Path,
    legal_root: Path,
    policy_path: Path,
) -> dict[str, Any]:
    """Validate a complete #609-C ledger against staged bytes."""

    if not isinstance(ledger, dict):
        raise _raise("ledger root must be an object")
    document = _exact(
        ledger,
        {
            "schemaVersion",
            "kind",
            "status",
            "scope",
            "producer",
            "image",
            "model",
            "licenseNotice",
            "trustPolicy",
            "evidence",
            "verification",
            "checksum",
        },
        "ledger",
    )
    if document["schemaVersion"] != SCHEMA_VERSION or document["kind"] != LEDGER_KIND:
        raise _raise("ledger schemaVersion or kind differs")
    if document["status"] != "PASS":
        raise _raise("ledger status must be PASS for execution input")
    scope = _exact(document["scope"], {"gate", "deferred"}, "scope")
    if scope["gate"] != "609-C" or not isinstance(scope["deferred"], list) or scope["deferred"] != ["609-D", "609-E", "544-B", "547"]:
        raise _raise("ledger scope must preserve deferred #609-D/#609-E/#544-B/#547 gates")
    verification = _exact(document["verification"], set(_VERIFICATION_KEYS), "verification")
    for field in _VERIFICATION_KEYS:
        if type(verification[field]) is not bool:
            raise _raise(f"verification.{field} must be boolean")
    checksum = _exact(document["checksum"], {"algorithm", "canonicalization", "covers", "sha256"}, "checksum")
    if checksum["algorithm"] != "SHA-256" or checksum["canonicalization"] != "JCS" or checksum["covers"] != "ledger-without-checksum":
        raise _raise("ledger checksum contract differs")
    _sha(checksum["sha256"], "checksum.sha256")
    if checksum["sha256"] != _unsigned_checksum(document):
        raise _raise("ledger checksum differs")

    result = _validate_ledger_inputs(
        document,
        evidence_root=evidence_root,
        model_root=model_root,
        legal_root=legal_root,
        policy_path=policy_path,
    )
    expected = {
        "indexSelectsPlatformManifest": True,
        "manifestConfigMatches": True,
        "baseDigestsMatch": True,
        "packageLockMatches": True,
        "modelFilesMatch": result["filesOk"],
        "modelTreeRecomputed": result["treeOk"],
        "pairBindingMatches": True,
        "licenseNoticeVerified": True,
        "trustPolicyMatched": result["trustOk"],
        "canonicalJson": True,
    }
    if verification != expected:
        raise _raise("ledger verification flags do not match recomputed checks")
    return {
        "status": "PASS",
        "kind": LEDGER_KIND,
        "scope": scope["gate"],
        "ledgerSha256": checksum["sha256"],
        "verification": expected,
    }


def build_ledger(
    *,
    evidence_root: Path,
    model_root: Path,
    legal_root: Path,
    policy_path: Path,
    repository: str,
    workflow: str,
    workflow_ref: str,
    workflow_run_id: str,
    source_revision: str,
    builder_identity: str,
    signer_identity: str,
    oidc_issuer: str,
    image_ref: str,
    workflow_sha: str | None = None,
    runner_environment: str = "github-hosted",
) -> dict[str, Any]:
    """Build a canonical ledger from the exact staged evidence and model tree."""

    sources = _load_sources(evidence_root)
    if workflow_sha is None:
        workflow_sha = source_revision
    for field, value in (
        ("repository", repository),
        ("workflow", workflow),
        ("workflowRef", workflow_ref),
        ("workflowRunId", workflow_run_id),
        ("sourceRevision", source_revision),
        ("builderIdentity", builder_identity),
        ("runnerEnvironment", runner_environment),
        ("signerIdentity", signer_identity),
        ("oidcIssuer", oidc_issuer),
        ("imageRef", image_ref),
    ):
        _string(value, field)
    _commit(source_revision, "sourceRevision")
    _commit(workflow_sha, "workflowSha")
    if not isinstance(workflow_run_id, str) or _ATTEMPT_RE.fullmatch(workflow_run_id) is None:
        raise _raise("workflowRunId is invalid")
    if workflow_run_id != sources["evidenceManifest"]["attemptId"]:
        raise _raise("workflowRunId differs from evidence attempt")
    _image_ref(image_ref, "imageRef")
    policy, policy_raw = _read_canonical(policy_path)
    trust = _build_trust_policy(
        policy,
        repository=repository,
        workflow=workflow,
        workflow_ref=workflow_ref,
        builder_identity=builder_identity,
        runner_environment=runner_environment,
        signer_identity=signer_identity,
        oidc_issuer=oidc_issuer,
        policy_sha256=sha256_hex(policy_raw),
    )

    lock_models = {item["role"]: item for item in sources["inputLock"]["models"]}
    ledger_models: list[dict[str, Any]] = []
    legal_files: list[dict[str, Any]] = []
    for role in _MODEL_ROLES:
        entries, tree_sha256 = _tree_descriptors(model_root, role)
        entry, role_legal_files = _model_ledger_entry(
            role,
            lock_models[role],
            sources["modelManifests"][role],
            entries,
            tree_sha256,
            legal_root,
        )
        ledger_models.append(entry)
        legal_files.extend(role_legal_files)
    legal_files.sort(key=lambda item: item["path"])

    evidence_files = [
        _file_descriptor(evidence_root, relative)
        for relative in ("evidence-manifest.json", *_CORE_EVIDENCE_FILES)
    ]
    evidence_files.sort(key=lambda item: item["path"])
    model_manifest_descriptors = {
        role: _file_descriptor(evidence_root, f"manifests/model-{role}.json")
        for role in _MODEL_ROLES
    }
    platform_digest = sources["evidenceManifest"]["imagePlatformDigest"]
    ledger: dict[str, Any] = {
        "schemaVersion": SCHEMA_VERSION,
        "kind": LEDGER_KIND,
        "status": "PASS",
        "scope": {"gate": "609-C", "deferred": ["609-D", "609-E", "544-B", "547"]},
        "producer": {
            "repository": repository,
            "workflow": workflow,
            "workflowRef": workflow_ref,
            "workflowSha": workflow_sha,
            "ref": workflow_ref.rsplit("@", 1)[-1],
            "workflowRunId": workflow_run_id,
            "sourceRevision": source_revision,
            "builderIdentity": builder_identity,
            "runnerEnvironment": runner_environment,
            "signerIdentity": signer_identity,
            "oidcIssuer": oidc_issuer,
        },
        "image": {
            "imageRef": image_ref,
            "imageIndexDigest": sources["evidenceManifest"]["imageIndexDigest"],
            "platformManifestDigest": platform_digest,
            "configDigest": sources["evidenceManifest"]["imageConfigDigest"],
            "os": "linux",
            "architecture": "amd64",
            "variant": None,
            "targetPlatform": "linux/amd64",
            "baseImageDigests": [sources["inputLock"]["baseImage"]["platformDigest"]],
            "packageLockSha256": sources["packageLockSha256"],
        },
        "model": {
            "models": ledger_models,
            "pairBindingSha256": pair_binding_sha256(ledger_models),
        },
        "licenseNotice": {
            "inventoryPath": "legal-inventory.json",
            "inventorySha256": sources["legalInventorySha256"],
            "files": legal_files,
            "complete": True,
            "verified": True,
        },
        "trustPolicy": trust,
        "evidence": {
            "manifest": _file_descriptor(evidence_root, "evidence-manifest.json"),
            "files": evidence_files,
            "inputLock": _file_descriptor(evidence_root, "inputs/producer-input.lock.json"),
            "packageLock": _file_descriptor(evidence_root, "manifests/package-lock.json"),
            "platformManifest": _file_descriptor(evidence_root, "platform-manifest.json"),
            "modelManifests": model_manifest_descriptors,
            "legalInventory": _file_descriptor(evidence_root, "legal-inventory.json"),
        },
        "verification": {field: True for field in _VERIFICATION_KEYS},
    }
    ledger["checksum"] = {
        "algorithm": "SHA-256",
        "canonicalization": "JCS",
        "covers": "ledger-without-checksum",
        "sha256": _unsigned_checksum(ledger),
    }
    validate_ledger(
        ledger,
        evidence_root=evidence_root,
        model_root=model_root,
        legal_root=legal_root,
        policy_path=policy_path,
    )
    return ledger


def _write_new(path: Path, document: Mapping[str, Any]) -> None:
    if path.exists() or path.is_symlink():
        raise LedgerValidationError(f"output already exists: {path}")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(jcs_bytes(document))


def _build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)

    common = argparse.ArgumentParser(add_help=False)
    common.add_argument("--evidence-root", type=Path, required=True)
    common.add_argument("--model-root", type=Path, required=True)
    common.add_argument("--legal-root", type=Path, required=True)
    common.add_argument("--policy", type=Path, required=True)

    validate_parser = subparsers.add_parser("validate", parents=[common])
    validate_parser.add_argument("--ledger", type=Path, required=True)

    build_parser = subparsers.add_parser("build", parents=[common])
    build_parser.add_argument("--output", type=Path, required=True)
    for name in (
        "repository",
        "workflow",
        "workflow-ref",
        "workflow-run-id",
        "source-revision",
        "builder-identity",
        "signer-identity",
        "oidc-issuer",
        "image-ref",
    ):
        build_parser.add_argument("--" + name, required=True)
    build_parser.add_argument("--workflow-sha")
    build_parser.add_argument("--runner-environment", default="github-hosted")
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    parser = _build_parser()
    args = parser.parse_args(argv)
    try:
        if args.command == "build":
            document = build_ledger(
                evidence_root=args.evidence_root,
                model_root=args.model_root,
                legal_root=args.legal_root,
                policy_path=args.policy,
                repository=args.repository,
                workflow=args.workflow,
                workflow_ref=args.workflow_ref,
                workflow_run_id=args.workflow_run_id,
                source_revision=args.source_revision,
                builder_identity=args.builder_identity,
                signer_identity=args.signer_identity,
                oidc_issuer=args.oidc_issuer,
                image_ref=args.image_ref,
                workflow_sha=args.workflow_sha,
                runner_environment=args.runner_environment,
            )
            _write_new(args.output, document)
            result = validate_ledger(
                document,
                evidence_root=args.evidence_root,
                model_root=args.model_root,
                legal_root=args.legal_root,
                policy_path=args.policy,
            )
        else:
            document, _raw = _read_canonical(args.ledger)
            result = validate_ledger(
                document,
                evidence_root=args.evidence_root,
                model_root=args.model_root,
                legal_root=args.legal_root,
                policy_path=args.policy,
            )
        print(json.dumps(result, ensure_ascii=False, separators=(",", ":"), sort_keys=True))
        return 0
    except (LedgerValidationError, ProducerValidationError, OSError, ValueError) as exc:
        print(json.dumps({"status": "REJECTED", "error": str(exc)[:512]}, ensure_ascii=False, separators=(",", ":")), file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
