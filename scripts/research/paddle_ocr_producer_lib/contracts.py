from __future__ import annotations

"""Strict, bounded document primitives for the PaddleOCR producer."""

import hashlib
import json
import re
from dataclasses import dataclass
from typing import Any, Iterable, Mapping, Optional, Set, Tuple

_SAFE_INTEGER = 9_007_199_254_740_991
_SHA256_RE = re.compile(r"\A[0-9a-f]{64}\Z")
_UTF8_BOM = b"\xef\xbb\xbf"


class ProducerValidationError(ValueError):
    """Raised when producer-controlled input violates its declared contract."""


def _reject_float(_: str) -> int:
    raise ProducerValidationError("numeric fields must be JSON integer values")


def _reject_constant(_: str) -> int:
    raise ProducerValidationError("numeric fields must be finite JSON integer values")


def _pairs_to_object(pairs: Iterable[Tuple[str, Any]]) -> dict[str, Any]:
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
    required: Set[str],
    optional: Optional[Set[str]] = None,
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


@dataclass(frozen=True)
class OpaqueJsonLines:
    raw_bytes: bytes
    envelopes: Tuple[dict[str, Any], ...]


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
