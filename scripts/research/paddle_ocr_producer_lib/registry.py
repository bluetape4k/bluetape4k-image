from __future__ import annotations

"""Bounded registry pagination and credential-free verification primitives."""

import os
import re
import time
from collections.abc import Callable, Mapping, Sequence
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from .contracts import ProducerValidationError, exact_object, load_json_bytes

_OCI_DIGEST_RE = re.compile(r"\Asha256:[0-9a-f]{64}\Z")
_ATTEMPT_TAG_RE = re.compile(r"\A(?:image|evidence)-[1-9][0-9]*\.[1-9][0-9]*\Z")
_CREDENTIAL_ENV_KEYS = (
    "GH_TOKEN",
    "GITHUB_TOKEN",
    "DOCKER_AUTH_CONFIG",
    "REGISTRY_AUTH_FILE",
    "ORAS_AUTH_FILE",
)


class HttpStatusError(Exception):
    """Transport response with an HTTP status available for retry classification."""

    def __init__(self, status: int):
        super().__init__(f"HTTP {status}")
        self.status = status


@dataclass(frozen=True)
class HttpLimits:
    connect_timeout_seconds: int = 10
    read_timeout_seconds: int = 30
    max_page_bytes: int = 2 * 1024 * 1024
    max_page_items: int = 100
    max_pages: int = 20
    max_total_bytes: int = 40 * 1024 * 1024
    max_attestation_bytes: int = 4 * 1024 * 1024

    def __post_init__(self) -> None:
        for name, value in vars(self).items():
            if type(value) is not int or value <= 0:
                raise ProducerValidationError(f"{name} must be a positive integer")


def retry_delays(
    kind: str, status: int | None, error: BaseException | None
) -> tuple[int, ...]:
    """Return the only permitted retry schedule for one isolated operation."""

    transient = status is not None and classify_http_status(kind, status) == "TRANSIENT"
    transient = transient or isinstance(error, (TimeoutError, ConnectionResetError))
    return (2, 4) if transient else ()


def classify_http_status(kind: str, status: int) -> str:
    """Classify registry/API status without turning public-visibility lag into success."""

    if type(status) is not int or not 100 <= status <= 599:
        raise ProducerValidationError("HTTP status is invalid")
    if 200 <= status <= 299:
        return "SUCCESS"
    if kind == "PUBLIC_AFTER_VISIBILITY" and status in {401, 403, 404}:
        return "TRANSIENT"
    if status in {401, 403}:
        return "BLOCKED"
    if status == 404:
        return "ABSENT"
    if status in {408, 429} or 500 <= status <= 599:
        return "TRANSIENT"
    return "REJECTED"


def run_with_retry(
    operation_id: str,
    kind: str,
    call: Callable[[], Any],
    *,
    deadline_seconds: float,
    monotonic: Callable[[], float] = time.monotonic,
    sleeper: Callable[[float], None] = time.sleep,
) -> tuple[Any, dict[str, Any]]:
    """Run one independent operation at most three times within an absolute deadline."""

    if not isinstance(operation_id, str) or not operation_id:
        raise ProducerValidationError("operationId is required")
    if not isinstance(deadline_seconds, (int, float)) or deadline_seconds <= 0:
        raise ProducerValidationError("deadline_seconds must be positive")
    deadline = monotonic() + deadline_seconds
    receipt: dict[str, Any] = {
        "operationId": operation_id,
        "attempts": 0,
        "delaysSeconds": [],
        "outcome": "PENDING",
    }
    for attempt in range(1, 4):
        if monotonic() >= deadline:
            receipt["outcome"] = "TRANSIENT_EXHAUSTED"
            raise ProducerValidationError("operation deadline exhausted")
        receipt["attempts"] = attempt
        try:
            result = call()
        except (KeyboardInterrupt, SystemExit):
            raise
        except BaseException as exc:
            status = exc.status if isinstance(exc, HttpStatusError) else None
            delays = retry_delays(kind, status, exc)
            if not delays:
                receipt["outcome"] = "REJECTED"
                raise
            if attempt == 3:
                receipt["outcome"] = "TRANSIENT_EXHAUSTED"
                raise ProducerValidationError("transient operation retries exhausted") from exc
            delay = delays[attempt - 1]
            if monotonic() + delay >= deadline:
                receipt["outcome"] = "TRANSIENT_EXHAUSTED"
                raise ProducerValidationError("operation deadline exhausted") from exc
            receipt["delaysSeconds"].append(delay)
            sleeper(delay)
            continue
        receipt["outcome"] = "SUCCESS"
        return result, receipt
    raise AssertionError("unreachable retry state")


def _parse_page(raw: bytes, limits: HttpLimits) -> list[dict[str, Any]]:
    if len(raw) > limits.max_page_bytes:
        raise ProducerValidationError("registry page bytes exceed limit")
    try:
        wrapped = load_json_bytes(
            b'{"items":' + raw + b"}",
            limits.max_page_bytes + 10,
            max_depth=12,
            max_entries=max(100, limits.max_page_items * 20),
        )
    except ProducerValidationError as exc:
        raise ProducerValidationError("malformed registry page") from exc
    items = wrapped["items"]
    if not isinstance(items, list):
        raise ProducerValidationError("registry page must be a JSON array")
    if len(items) > limits.max_page_items:
        raise ProducerValidationError("registry page items exceed limit")
    if any(not isinstance(item, dict) for item in items):
        raise ProducerValidationError("registry page items must be objects")
    return items


def collect_package_pages(
    fetch_page: Callable[[str | None], tuple[bytes, str | None]],
    *,
    limits: HttpLimits | None = None,
) -> list[list[dict[str, Any]]]:
    """Collect a cursor chain within exact page and byte budgets."""

    active_limits = limits or HttpLimits()
    pages: list[list[dict[str, Any]]] = []
    cursor: str | None = None
    seen_cursors: set[str] = set()
    total_bytes = 0
    for page_number in range(1, active_limits.max_pages + 1):
        raw, next_cursor = fetch_page(cursor)
        if not isinstance(raw, bytes):
            raise ProducerValidationError("registry page body must be bytes")
        total_bytes += len(raw)
        if total_bytes > active_limits.max_total_bytes:
            raise ProducerValidationError("registry pagination bytes exceed total limit")
        pages.append(_parse_page(raw, active_limits))
        if next_cursor is None:
            return pages
        if not isinstance(next_cursor, str) or not next_cursor:
            raise ProducerValidationError("registry pagination cursor is invalid")
        if next_cursor in seen_cursors or next_cursor == cursor:
            raise ProducerValidationError("registry pagination cursor loop")
        seen_cursors.add(next_cursor)
        cursor = next_cursor
        if page_number == active_limits.max_pages:
            raise ProducerValidationError("registry pagination exceeds page limit")
    raise ProducerValidationError("registry pagination did not terminate")


def _validate_package_version(value: Any, index: int) -> dict[str, Any]:
    try:
        version = exact_object(value, required={"id", "name", "metadata"})
        metadata = exact_object(version["metadata"], required={"container"})
        container = exact_object(metadata["container"], required={"tags"})
    except ProducerValidationError as exc:
        raise ProducerValidationError(f"package version {index}: {exc}") from exc
    if type(version["id"]) is not int or version["id"] <= 0:
        raise ProducerValidationError("package version id must be positive")
    if not isinstance(version["name"], str) or _OCI_DIGEST_RE.fullmatch(version["name"]) is None:
        raise ProducerValidationError("package version name must be an OCI digest")
    tags = container["tags"]
    if not isinstance(tags, list) or any(not isinstance(tag, str) or not tag for tag in tags):
        raise ProducerValidationError("package version tags must be strings")
    if len(tags) != len(set(tags)):
        raise ProducerValidationError("package version tags must be unique")
    return version


def select_exact_version(
    pages: Sequence[Sequence[Mapping[str, Any]]], tag: str
) -> dict[str, Any] | None:
    """Return absent or one exact package version and reject ambiguous state."""

    if not isinstance(tag, str) or _ATTEMPT_TAG_RE.fullmatch(tag) is None:
        raise ProducerValidationError("attempt tag is invalid")
    matches = []
    seen_ids: set[int] = set()
    index = 0
    for page in pages:
        if not isinstance(page, Sequence):
            raise ProducerValidationError("package page must be a sequence")
        for raw in page:
            version = _validate_package_version(raw, index)
            index += 1
            if version["id"] in seen_ids:
                raise ProducerValidationError("duplicate package version id across pages")
            seen_ids.add(version["id"])
            if tag in version["metadata"]["container"]["tags"]:
                matches.append(version)
    if len(matches) > 1:
        raise ProducerValidationError("ambiguous package state for exact tag")
    return matches[0] if matches else None


def validate_anonymous_environment(
    environment: Mapping[str, str],
    *,
    auth_files_exist: Callable[[Sequence[Path]], bool] | None = None,
) -> None:
    """Reject public verification when any ambient registry credential is visible."""

    for key in _CREDENTIAL_ENV_KEYS:
        if environment.get(key):
            raise ProducerValidationError("credential environment is forbidden")
    home = environment.get("HOME")
    docker_config = environment.get("DOCKER_CONFIG")
    if not home or not docker_config:
        raise ProducerValidationError("anonymous HOME and DOCKER_CONFIG are required")
    home_path = Path(home)
    docker_path = Path(docker_config)
    if not home_path.is_absolute() or not docker_path.is_absolute():
        raise ProducerValidationError("anonymous paths must be absolute")
    candidates = (
        docker_path / "config.json",
        home_path / ".docker" / "config.json",
        Path(environment.get("GH_CONFIG_DIR", str(home_path / ".config" / "gh"))) / "hosts.yml",
        Path(environment.get("REGISTRY_AUTH_FILE", "/nonexistent-registry-auth")),
    )
    checker = auth_files_exist or (lambda paths: any(os.path.lexists(path) for path in paths))
    if checker(candidates):
        raise ProducerValidationError("credential file or helper is forbidden")
