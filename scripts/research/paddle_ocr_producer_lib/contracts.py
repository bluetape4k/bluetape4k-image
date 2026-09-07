from __future__ import annotations

"""Strict, bounded document primitives for the PaddleOCR producer."""

import hashlib
import json
import re
from collections.abc import Iterable, Mapping
from dataclasses import dataclass
from typing import Any
from urllib.parse import urlsplit

_SAFE_INTEGER = 9_007_199_254_740_991
_SHA256_RE = re.compile(r"\A[0-9a-f]{64}\Z")
_UTF8_BOM = b"\xef\xbb\xbf"


class ProducerValidationError(ValueError):
    """Raised when producer-controlled input violates its declared contract."""


def _reject_float(_: str) -> int:
    raise ProducerValidationError("numeric fields must be JSON integer values")


def _reject_constant(_: str) -> int:
    raise ProducerValidationError("numeric fields must be finite JSON integer values")


def _pairs_to_object(pairs: Iterable[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise ProducerValidationError(f"duplicate JSON key: {key}")
        result[key] = value
    return result


def _validate_shape(
    value: Any,
    *,
    depth: int,
    max_depth: int,
    counter: list[int],
    max_entries: int,
) -> None:
    if depth > max_depth:
        raise ProducerValidationError(f"JSON depth exceeds {max_depth}")
    if isinstance(value, dict):
        for key, nested in value.items():
            counter[0] += 1
            if counter[0] > max_entries:
                raise ProducerValidationError(f"JSON entries exceed {max_entries}")
            if not isinstance(key, str):
                raise ProducerValidationError("JSON object keys must be strings")
            _validate_shape(
                nested,
                depth=depth + 1,
                max_depth=max_depth,
                counter=counter,
                max_entries=max_entries,
            )
    elif isinstance(value, list):
        for nested in value:
            counter[0] += 1
            if counter[0] > max_entries:
                raise ProducerValidationError(f"JSON entries exceed {max_entries}")
            _validate_shape(
                nested,
                depth=depth + 1,
                max_depth=max_depth,
                counter=counter,
                max_entries=max_entries,
            )
    elif isinstance(value, bool) or value is None or isinstance(value, str):
        return
    elif type(value) is int:
        if not -_SAFE_INTEGER <= value <= _SAFE_INTEGER:
            raise ProducerValidationError("JSON integer exceeds the JCS safe integer range")
    else:
        raise ProducerValidationError("unsupported JSON value")


def load_json_bytes(
    raw: bytes,
    max_bytes: int,
    *,
    max_depth: int = 64,
    max_entries: int = 10_000,
) -> dict[str, Any]:
    """Parse one bounded strict UTF-8 JSON object without echoing input bytes."""

    if not isinstance(raw, bytes):
        raise ProducerValidationError("JSON input must be bytes")
    if type(max_bytes) is not int or max_bytes <= 0:
        raise ProducerValidationError("JSON byte limit must be a positive integer")
    if len(raw) > max_bytes:
        raise ProducerValidationError(f"JSON input exceeds {max_bytes} bytes")
    if raw.startswith(_UTF8_BOM):
        raise ProducerValidationError("JSON input must not contain a UTF-8 BOM")
    try:
        text = raw.decode("utf-8", errors="strict")
    except UnicodeDecodeError as exc:
        raise ProducerValidationError("JSON input must be valid UTF-8") from exc
    try:
        value = json.loads(
            text,
            object_pairs_hook=_pairs_to_object,
            parse_float=_reject_float,
            parse_constant=_reject_constant,
        )
    except ProducerValidationError:
        raise
    except (json.JSONDecodeError, UnicodeError, ValueError) as exc:
        raise ProducerValidationError("malformed JSON input") from exc
    if not isinstance(value, dict):
        raise ProducerValidationError("JSON root must be an object")
    if type(max_depth) is not int or max_depth <= 0:
        raise ProducerValidationError("JSON depth limit must be a positive integer")
    if type(max_entries) is not int or max_entries < 0:
        raise ProducerValidationError("JSON entry limit must be a non-negative integer")
    _validate_shape(
        value,
        depth=1,
        max_depth=max_depth,
        counter=[0],
        max_entries=max_entries,
    )
    return value


def exact_object(
    value: Mapping[str, Any],
    *,
    required: set[str],
    optional: set[str] | None = None,
) -> dict[str, Any]:
    """Return a copy only when an object's key set matches the declared contract."""

    if not isinstance(value, dict):
        raise ProducerValidationError("value must be a JSON object")
    allowed = set(required) | set(optional or ())
    missing = sorted(set(required) - set(value))
    unexpected = sorted(set(value) - allowed)
    if missing or unexpected:
        parts = []
        if missing:
            parts.append("missing keys: " + ", ".join(missing))
        if unexpected:
            parts.append("unexpected keys: " + ", ".join(unexpected))
        raise ProducerValidationError("; ".join(parts))
    return dict(value)


def _jcs_string(value: str) -> str:
    try:
        value.encode("utf-8", errors="strict")
    except UnicodeEncodeError as exc:
        raise ProducerValidationError("JSON strings must contain valid Unicode") from exc
    return json.dumps(value, ensure_ascii=False, separators=(",", ":"))


def _jcs_sort_key(value: str) -> bytes:
    try:
        return value.encode("utf-16-be", errors="strict")
    except UnicodeEncodeError as exc:
        raise ProducerValidationError("JSON object keys must contain valid Unicode") from exc


def _jcs_text(value: Any) -> str:
    if value is None:
        return "null"
    if value is True:
        return "true"
    if value is False:
        return "false"
    if type(value) is int:
        if not -_SAFE_INTEGER <= value <= _SAFE_INTEGER:
            raise ProducerValidationError("JSON integer exceeds the JCS safe integer range")
        return str(value)
    if isinstance(value, float):
        raise ProducerValidationError("JCS numeric fields must be JSON integer values")
    if isinstance(value, str):
        return _jcs_string(value)
    if isinstance(value, list):
        return "[" + ",".join(_jcs_text(item) for item in value) + "]"
    if isinstance(value, dict):
        if not all(isinstance(key, str) for key in value):
            raise ProducerValidationError("JCS object keys must be strings")
        keys = sorted(value, key=_jcs_sort_key)
        return "{" + ",".join(
            _jcs_string(key) + ":" + _jcs_text(value[key]) for key in keys
        ) + "}"
    raise ProducerValidationError("unsupported JCS value")


def jcs_bytes(value: Any) -> bytes:
    """Serialize the producer's integer-only RFC 8785 subset."""

    return _jcs_text(value).encode("utf-8")


def sha256_hex(raw: bytes) -> str:
    if not isinstance(raw, bytes):
        raise ProducerValidationError("SHA-256 input must be bytes")
    return hashlib.sha256(raw).hexdigest()


def require_sha256(value: Any, field: str = "SHA-256") -> str:
    if not isinstance(value, str) or _SHA256_RE.fullmatch(value) is None:
        raise ProducerValidationError(f"{field} must be 64 lowercase hexadecimal characters")
    return value


def _non_empty_string(value: Any, field: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise ProducerValidationError(f"{field} must be a non-empty string")
    if "PENDING" in value.upper():
        raise ProducerValidationError(f"{field} must not contain PENDING")
    return value


def _positive_integer(value: Any, field: str) -> int:
    if type(value) is not int or value <= 0:
        raise ProducerValidationError(f"{field} must be a positive integer")
    return value


def _exact_keys(value: Any, field: str, keys: set[str]) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise ProducerValidationError(f"{field} must be an object")
    try:
        return exact_object(value, required=keys)
    except ProducerValidationError as exc:
        raise ProducerValidationError(f"{field}: {exc}") from exc


def _validate_locked_url(value: Any, field: str, allowed_hosts: set[str]) -> str:
    url = _non_empty_string(value, field)
    parsed = urlsplit(url)
    if parsed.scheme != "https":
        raise ProducerValidationError(f"{field} must use https")
    if parsed.username is not None or parsed.password is not None:
        raise ProducerValidationError(f"{field} must not contain userinfo")
    if parsed.hostname is None:
        raise ProducerValidationError(f"{field} must contain a host")
    try:
        port = parsed.port
    except ValueError as exc:
        raise ProducerValidationError(f"{field} has an invalid port") from exc
    if port not in (None, 443):
        raise ProducerValidationError(f"{field} must use port 443")
    host = parsed.hostname.rstrip(".").lower()
    if host not in allowed_hosts:
        raise ProducerValidationError(f"{field} host is not allowlisted")
    if parsed.fragment:
        raise ProducerValidationError(f"{field} must not contain a fragment")
    return url


def _validate_artifact(
    value: Any,
    field: str,
    *,
    allowed_hosts: set[str],
    extra_keys: set[str],
) -> dict[str, Any]:
    common = {"id", "url", "bytes", "sha256", "mediaType"}
    artifact = _exact_keys(value, field, common | extra_keys)
    _non_empty_string(artifact["id"], f"{field}.id")
    _validate_locked_url(artifact["url"], f"{field}.url", allowed_hosts)
    _positive_integer(artifact["bytes"], f"{field}.bytes")
    require_sha256(artifact["sha256"], f"{field}.sha256")
    _non_empty_string(artifact["mediaType"], f"{field}.mediaType")
    return artifact


def validate_input_lock(
    value: Any,
    *,
    allowed_hosts: set[str],
) -> dict[str, Any]:
    """Validate the executable immutable producer input lock."""

    root_keys = {
        "schemaVersion",
        "targetPlatform",
        "baseImage",
        "sources",
        "packages",
        "models",
        "legalInventorySha256",
    }
    lock = _exact_keys(value, "input lock", root_keys)
    if lock["schemaVersion"] != 1:
        raise ProducerValidationError("input lock.schemaVersion must be 1")
    if lock["targetPlatform"] != "linux/amd64":
        raise ProducerValidationError("input lock.targetPlatform must be linux/amd64")

    base = _exact_keys(
        lock["baseImage"],
        "input lock.baseImage",
        {
            "reference",
            "indexDigest",
            "platformDigest",
            "configDigest",
            "os",
            "architecture",
            "variant",
        },
    )
    reference = _non_empty_string(base["reference"], "baseImage.reference")
    if re.fullmatch(r"[^\s@]+@sha256:[0-9a-f]{64}", reference) is None:
        raise ProducerValidationError("baseImage.reference must be digest-pinned")
    for name in ("indexDigest", "platformDigest", "configDigest"):
        digest = _non_empty_string(base[name], f"baseImage.{name}")
        if re.fullmatch(r"sha256:[0-9a-f]{64}", digest) is None:
            raise ProducerValidationError(f"baseImage.{name} must be a sha256 digest")
    if base["os"] != "linux" or base["architecture"] != "amd64":
        raise ProducerValidationError("baseImage platform must be linux/amd64")
    if base["variant"] is not None and not isinstance(base["variant"], str):
        raise ProducerValidationError("baseImage.variant must be null or a string")

    sources_value = lock["sources"]
    if not isinstance(sources_value, list) or len(sources_value) < 2:
        raise ProducerValidationError("sources must contain at least two entries")
    sources: dict[str, dict[str, Any]] = {}
    revisions: set[str] = set()
    for index, raw_source in enumerate(sources_value):
        field = f"sources[{index}]"
        source = _validate_artifact(
            raw_source,
            field,
            allowed_hosts=allowed_hosts,
            extra_keys={"revision"},
        )
        source_id = source["id"]
        revision = _non_empty_string(source["revision"], f"{field}.revision")
        if re.fullmatch(r"[0-9a-f]{40}", revision) is None:
            raise ProducerValidationError(f"{field}.revision must be a full commit SHA")
        if source_id in sources or revision in revisions:
            raise ProducerValidationError("source ids and revisions must be unique")
        sources[source_id] = source
        revisions.add(revision)
    if not {"paddleocr", "paddlex"}.issubset(sources):
        raise ProducerValidationError("sources must include paddleocr and paddlex")

    packages_value = lock["packages"]
    if not isinstance(packages_value, list) or not packages_value:
        raise ProducerValidationError("packages must contain at least one entry")
    package_identities: set[tuple[str, str, str]] = set()
    package_common_keys = {
        "id",
        "name",
        "version",
        "filename",
        "wheelTags",
        "bytes",
        "sha256",
        "mediaType",
    }
    registry_keys = package_common_keys | {"url"}
    derived_keys = package_common_keys | {
        "derivedSourceId",
        "sourceRevision",
        "buildToolchain",
    }
    toolchain_keys = {
        "backend",
        "backendVersion",
        "pythonVersion",
        "sourceDateEpoch",
        "artifacts",
    }
    build_artifact_keys = {"name", "version", "filename", "url", "bytes", "sha256"}
    for index, raw_package in enumerate(packages_value):
        field = f"packages[{index}]"
        if not isinstance(raw_package, dict):
            raise ProducerValidationError(f"{field} must be an object")
        keys = set(raw_package)
        if keys == registry_keys:
            package = dict(raw_package)
            variant = "registry"
        elif keys == derived_keys:
            package = dict(raw_package)
            variant = "derived"
        else:
            raise ProducerValidationError(
                f"{field} must be exactly one registry or derived package variant"
            )
        for name in ("id", "name", "version", "filename", "mediaType"):
            _non_empty_string(package[name], f"{field}.{name}")
        wheel_tags = package["wheelTags"]
        if not isinstance(wheel_tags, list) or not wheel_tags:
            raise ProducerValidationError(f"{field}.wheelTags must be a non-empty array")
        for tag in wheel_tags:
            _non_empty_string(tag, f"{field}.wheelTags[]")
        _positive_integer(package["bytes"], f"{field}.bytes")
        require_sha256(package["sha256"], f"{field}.sha256")
        if variant == "registry":
            _validate_locked_url(package["url"], f"{field}.url", allowed_hosts)
        else:
            source_id = _non_empty_string(
                package["derivedSourceId"], f"{field}.derivedSourceId"
            )
            if source_id not in sources:
                raise ProducerValidationError(
                    f"{field}.derivedSourceId must reference a source"
                )
            if package["sourceRevision"] != sources[source_id]["revision"]:
                raise ProducerValidationError(f"{field}.sourceRevision differs from source")
            toolchain = _exact_keys(
                package["buildToolchain"], f"{field}.buildToolchain", toolchain_keys
            )
            for name in ("backend", "backendVersion", "pythonVersion"):
                _non_empty_string(toolchain[name], f"{field}.buildToolchain.{name}")
            _positive_integer(
                toolchain["sourceDateEpoch"],
                f"{field}.buildToolchain.sourceDateEpoch",
            )
            artifacts = toolchain["artifacts"]
            if not isinstance(artifacts, list) or not artifacts:
                raise ProducerValidationError(
                    f"{field}.buildToolchain.artifacts must be a non-empty array"
                )
            build_identities: set[tuple[str, str, str]] = set()
            for artifact_index, raw_artifact in enumerate(artifacts):
                artifact_field = (
                    f"{field}.buildToolchain.artifacts[{artifact_index}]"
                )
                artifact = _exact_keys(
                    raw_artifact, artifact_field, build_artifact_keys
                )
                for name in ("name", "version", "filename"):
                    _non_empty_string(artifact[name], f"{artifact_field}.{name}")
                _validate_locked_url(
                    artifact["url"], f"{artifact_field}.url", allowed_hosts
                )
                _positive_integer(artifact["bytes"], f"{artifact_field}.bytes")
                require_sha256(artifact["sha256"], f"{artifact_field}.sha256")
                build_identity = (
                    artifact["name"],
                    artifact["version"],
                    artifact["filename"],
                )
                if build_identity in build_identities:
                    raise ProducerValidationError(
                        f"{field}.buildToolchain artifacts must be unique"
                    )
                build_identities.add(build_identity)
        identity = (package["name"], package["version"], package["filename"])
        if identity in package_identities:
            raise ProducerValidationError("package identity must be unique")
        package_identities.add(identity)

    models_value = lock["models"]
    if not isinstance(models_value, list) or len(models_value) != 2:
        raise ProducerValidationError("models must contain exactly detector and recognizer")
    model_keys = {
        "modelId",
        "modelRevision",
        "sourceId",
        "role",
        "url",
        "bytes",
        "sha256",
        "mediaType",
        "archiveType",
        "treeSha256",
        "licenseExpression",
        "licenseSourcePath",
        "licenseSha256",
        "noticePath",
        "noticeSha256",
    }
    roles: set[str] = set()
    model_ids: set[str] = set()
    for index, raw_model in enumerate(models_value):
        field = f"models[{index}]"
        model = _exact_keys(raw_model, field, model_keys)
        for name in (
            "modelId",
            "modelRevision",
            "sourceId",
            "role",
            "mediaType",
            "archiveType",
            "licenseExpression",
            "licenseSourcePath",
            "noticePath",
        ):
            _non_empty_string(model[name], f"{field}.{name}")
        if model["role"] not in {"detector", "recognizer"}:
            raise ProducerValidationError(f"{field}.role must be detector or recognizer")
        if model["role"] in roles or model["modelId"] in model_ids:
            raise ProducerValidationError("model roles and ids must be unique")
        roles.add(model["role"])
        model_ids.add(model["modelId"])
        source_id = model["sourceId"]
        if source_id not in sources:
            raise ProducerValidationError(f"{field}.sourceId must reference a source")
        if model["modelRevision"] != sources[source_id]["revision"]:
            raise ProducerValidationError(f"{field}.modelRevision differs from source revision")
        _validate_locked_url(model["url"], f"{field}.url", allowed_hosts)
        _positive_integer(model["bytes"], f"{field}.bytes")
        for name in ("sha256", "treeSha256", "licenseSha256", "noticeSha256"):
            require_sha256(model[name], f"{field}.{name}")
    if roles != {"detector", "recognizer"}:
        raise ProducerValidationError("models must contain exactly detector and recognizer")
    require_sha256(lock["legalInventorySha256"], "legalInventorySha256")
    return dict(lock)


@dataclass(frozen=True)
class AttemptIdentity:
    run_id: int
    run_attempt: int

    @classmethod
    def from_run(cls, run_id: int, run_attempt: int) -> AttemptIdentity:
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


@dataclass(frozen=True)
class OpaqueJsonLines:
    raw_bytes: bytes
    envelopes: tuple[dict[str, Any], ...]


def load_jsonl_bytes(
    raw: bytes,
    max_bytes: int,
    *,
    max_lines: int,
    max_depth: int = 64,
    max_entries: int = 10_000,
) -> OpaqueJsonLines:
    """Validate LF-delimited envelopes while preserving their exact signed bytes."""

    if not isinstance(raw, bytes):
        raise ProducerValidationError("JSONL input must be bytes")
    if len(raw) > max_bytes:
        raise ProducerValidationError(f"JSONL input exceeds {max_bytes} bytes")
    if raw.startswith(_UTF8_BOM):
        raise ProducerValidationError("JSONL input must not contain a UTF-8 BOM")
    if b"\r" in raw:
        raise ProducerValidationError("JSONL input must not contain CR characters")
    if not raw.endswith(b"\n"):
        raise ProducerValidationError("JSONL input must end with LF")
    lines = raw[:-1].split(b"\n")
    if not lines or any(not line for line in lines):
        raise ProducerValidationError("JSONL input must not contain blank lines")
    if type(max_lines) is not int or max_lines <= 0:
        raise ProducerValidationError("JSONL line limit must be a positive integer")
    if len(lines) > max_lines:
        raise ProducerValidationError(f"JSONL lines exceed {max_lines}")
    envelopes = tuple(
        load_json_bytes(
            line,
            max_bytes,
            max_depth=max_depth,
            max_entries=max_entries,
        )
        for line in lines
    )
    canonical = [jcs_bytes(envelope) for envelope in envelopes]
    if len(set(canonical)) != len(canonical):
        raise ProducerValidationError("JSONL input contains a duplicate envelope")
    return OpaqueJsonLines(raw_bytes=raw, envelopes=envelopes)
