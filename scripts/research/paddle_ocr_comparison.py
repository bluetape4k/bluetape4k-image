"""Run the Issue #544 OCR provider comparison on the canonical corpus v2.

The runner deliberately stays outside the production modules.  It consumes the
trusted PaddleOCR service through the same loopback-only, digest-pinned Docker
contract as the #545 acceptance gate and emits bounded JSON evidence for the
benchmark receipt validator.
"""

from __future__ import annotations

import argparse
import base64
import csv
import hashlib
import io
import json
import math
import os
import platform
import re
import resource
import stat
import struct
import subprocess
import sys
import time
import unicodedata
from collections import defaultdict
from collections.abc import Callable, Iterable, Mapping, Sequence
from dataclasses import dataclass
from pathlib import Path, PurePosixPath
from typing import Any

from paddle_ocr_acceptance import (
    _HEALTH_PROBE,
    AcceptanceValidationError,
    _cleanup_container,
    _docker_inspect,
    build_container_command,
    build_exec_command,
    parse_host_platform,
    parse_image_inspect,
    parse_probe_output,
    validate_container_inspect,
)
from paddle_ocr_smoke import (
    SmokeValidationError,
    load_service_config,
    validate_inputs,
)

MANIFEST_RELATIVE_PATH = (
    "benchmark/images-benchmark/src/main/resources/bench/ocr-v2/manifest.json"
)
MANIFEST_SHA256 = "99502a59751f68aff19634c33239d0f0e50931a17746621e7384ef169faaebb6"
PADDLE_MODEL_DIGEST = (
    "sha256:d2f455ea0e0a34cfa8fac7bba38887cf136d62525e69cf9f433eac8009d9dd8f"
)
PADDLE_IMAGE_DIGEST = (
    "sha256:cc21ee6edc03c672d11cadaadda828ef83e4ecfdee0da8838b402a763fcdec46"
)
REQUIRED_PLATFORM = "linux/amd64"
MAX_MANIFEST_BYTES = 256_000
MAX_RESOURCE_BYTES = 5 * 1024 * 1024
MAX_OUTPUT_BYTES = 16 * 1024 * 1024
MAX_ERROR_CHARS = 4_096
WARM_ITERATIONS = 3
PADDLE_QUERY_MARKER = "BLUETAPE4K_OCR:"
SHA256_RE = re.compile(r"\A[0-9a-f]{64}\Z")
IMAGE_RE = re.compile(r"\A[^\x00-\x1f\x7f\s@]+@sha256:[0-9a-f]{64}\Z")


class ComparisonValidationError(ValueError):
    """Raised when comparison input or provider output violates the contract."""


@dataclass(frozen=True)
class CorpusEntry:
    fixture_id: str
    scenario: str
    expected_outcome: str
    resource_path: Path
    resource_relative: str
    width: int | None
    height: int | None
    languages: tuple[str, ...]
    expected_text: str
    expected_boxes: tuple[dict[str, Any], ...]


@dataclass(frozen=True)
class ProviderRun:
    rows: tuple[dict[str, Any], ...]
    metrics: dict[str, float]
    identity: dict[str, str]


def _reject_duplicate_keys(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise ComparisonValidationError(f"duplicate JSON key: {key}")
        result[key] = value
    return result


def _json_object(raw: bytes | str, label: str) -> dict[str, Any]:
    try:
        value = json.loads(
            raw.decode("utf-8") if isinstance(raw, bytes) else raw,
            object_pairs_hook=_reject_duplicate_keys,
        )
    except (
        UnicodeDecodeError,
        json.JSONDecodeError,
        ComparisonValidationError,
    ) as error:
        raise ComparisonValidationError(f"{label} is invalid JSON") from error
    if not isinstance(value, dict):
        raise ComparisonValidationError(f"{label} must be a JSON object")
    return value


def _read_file(
    path: Path,
    label: str,
    maximum: int = MAX_RESOURCE_BYTES,
    *,
    allow_empty: bool = False,
) -> bytes:
    if path.is_symlink() or not path.is_file():
        raise ComparisonValidationError(f"{label} must be a regular file")
    try:
        payload = path.read_bytes()
    except OSError as error:
        raise ComparisonValidationError(f"{label} could not be read") from error
    if (not allow_empty and not payload) or len(payload) > maximum:
        raise ComparisonValidationError(f"{label} byte size is out of bounds")
    return payload


def _safe_relative(value: Any, label: str) -> str:
    if (
        not isinstance(value, str)
        or not value
        or value.startswith("/")
        or "\\" in value
    ):
        raise ComparisonValidationError(f"{label} is not a safe relative path")
    path = PurePosixPath(value)
    if str(path) != value or any(part in {"", ".", ".."} for part in path.parts):
        raise ComparisonValidationError(f"{label} is not a safe relative path")
    return value


def _verify_declared(
    path: Path,
    label: str,
    expected_bytes: Any,
    expected_sha: Any,
    *,
    allow_empty: bool = False,
) -> bytes:
    if (
        type(expected_bytes) is not int
        or expected_bytes < (0 if allow_empty else 1)
        or expected_bytes > MAX_RESOURCE_BYTES
    ):
        raise ComparisonValidationError(f"{label} byte receipt is invalid")
    if not isinstance(expected_sha, str) or SHA256_RE.fullmatch(expected_sha) is None:
        raise ComparisonValidationError(f"{label} SHA-256 receipt is invalid")
    payload = _read_file(path, label, allow_empty=allow_empty)
    if (
        len(payload) != expected_bytes
        or hashlib.sha256(payload).hexdigest() != expected_sha
    ):
        raise ComparisonValidationError(f"{label} hash or byte receipt differs")
    return payload


def _png_dimensions(payload: bytes, label: str) -> tuple[int, int]:
    if (
        len(payload) < 24
        or payload[:8] != b"\x89PNG\r\n\x1a\n"
        or payload[12:16] != b"IHDR"
    ):
        raise ComparisonValidationError(f"{label} must be a PNG image")
    width, height = struct.unpack(">II", payload[16:24])
    if width <= 0 or height <= 0:
        raise ComparisonValidationError(f"{label} image dimensions are invalid")
    return width, height


def normalize_text(value: str) -> str:
    """Apply the corpus NFC/LF and metric whitespace policy."""

    if not isinstance(value, str):
        raise ComparisonValidationError("OCR text must be a string")
    normalized = unicodedata.normalize(
        "NFC", value.replace("\r\n", "\n").replace("\r", "\n")
    )
    return re.sub(r"\s+", " ", normalized).strip()


def _levenshtein(left: Sequence[str], right: Sequence[str]) -> int:
    if len(left) < len(right):
        left, right = right, left
    previous = list(range(len(right) + 1))
    for left_index, left_value in enumerate(left, start=1):
        current = [left_index]
        for right_index, right_value in enumerate(right, start=1):
            current.append(
                min(
                    current[-1] + 1,
                    previous[right_index] + 1,
                    previous[right_index - 1] + (left_value != right_value),
                )
            )
        previous = current
    return previous[-1]


def score_text(reference: str, prediction: str) -> dict[str, float | int]:
    reference_normalized = normalize_text(reference)
    prediction_normalized = normalize_text(prediction)
    reference_chars = list(reference_normalized)
    prediction_chars = list(prediction_normalized)
    reference_words = reference_normalized.split() if reference_normalized else []
    prediction_words = prediction_normalized.split() if prediction_normalized else []
    character_edits = _levenshtein(reference_chars, prediction_chars)
    word_edits = _levenshtein(reference_words, prediction_words)
    return {
        "characterEdits": character_edits,
        "wordEdits": word_edits,
        "cer": character_edits / len(reference_chars)
        if reference_chars
        else (0.0 if not prediction_chars else 1.0),
        "wer": word_edits / len(reference_words)
        if reference_words
        else (0.0 if not prediction_words else 1.0),
    }


def _number(value: Any, label: str) -> float:
    if (
        isinstance(value, bool)
        or not isinstance(value, (int, float))
        or not math.isfinite(float(value))
    ):
        raise ComparisonValidationError(f"{label} must be finite")
    return float(value)


def _bbox(
    points: Iterable[Sequence[Any]], width: int, height: int, label: str
) -> tuple[int, int, int, int]:
    converted: list[tuple[float, float]] = []
    for point in points:
        if not isinstance(point, (list, tuple)) or len(point) != 2:
            raise ComparisonValidationError(f"{label} geometry point is invalid")
        x = _number(point[0], f"{label} x")
        y = _number(point[1], f"{label} y")
        if x < 0 or y < 0 or x > width or y > height:
            raise ComparisonValidationError(f"{label} geometry is outside image bounds")
        converted.append((x, y))
    if len(converted) < 4:
        raise ComparisonValidationError(
            f"{label} geometry must contain at least four points"
        )
    left = math.floor(min(point[0] for point in converted))
    top = math.floor(min(point[1] for point in converted))
    right = math.ceil(max(point[0] for point in converted))
    bottom = math.ceil(max(point[1] for point in converted))
    if right <= left or bottom <= top:
        raise ComparisonValidationError(f"{label} geometry has no area")
    if right > width or bottom > height:
        raise ComparisonValidationError(f"{label} geometry exceeds image bounds")
    return left, top, right - left, bottom - top


def _result_payload(value: dict[str, Any]) -> dict[str, Any]:
    result = value.get("result")
    if not isinstance(result, dict):
        raise ComparisonValidationError("PaddleOCR response result is missing")
    ocr_results = result.get("ocrResults")
    if (
        not isinstance(ocr_results, list)
        or len(ocr_results) != 1
        or not isinstance(ocr_results[0], dict)
    ):
        raise ComparisonValidationError(
            "PaddleOCR response ocrResults shape is invalid"
        )
    pruned = ocr_results[0].get("prunedResult")
    if not isinstance(pruned, dict):
        raise ComparisonValidationError("PaddleOCR response prunedResult is missing")
    return pruned


def parse_paddle_response(
    raw: bytes | str, *, width: int, height: int
) -> dict[str, Any]:
    """Parse the pinned PaddleX OCR response and retain only bounded fields."""

    value = _json_object(raw, "PaddleOCR response")
    pruned = _result_payload(value)
    texts = pruned.get("rec_texts")
    scores = pruned.get("rec_scores")
    polygons = pruned.get("rec_polys", pruned.get("rec_boxes"))
    if (
        not isinstance(texts, list)
        or not isinstance(scores, list)
        or not isinstance(polygons, list)
    ):
        raise ComparisonValidationError(
            "PaddleOCR response geometry or text arrays are missing"
        )
    if len(texts) != len(scores) or len(texts) != len(polygons):
        raise ComparisonValidationError(
            "PaddleOCR response text, score, and geometry counts differ"
        )
    geometry: list[dict[str, Any]] = []
    for index, (text, score, polygon) in enumerate(zip(texts, scores, polygons)):
        if not isinstance(text, str):
            raise ComparisonValidationError("PaddleOCR response text entry is invalid")
        normalized = normalize_text(text)
        if not normalized:
            continue
        confidence = _number(score, "PaddleOCR response confidence")
        if confidence < 0.0 or confidence > 1.0:
            raise ComparisonValidationError(
                "PaddleOCR response confidence is out of range"
            )
        if (
            isinstance(polygon, list)
            and polygon
            and isinstance(polygon[0], (list, tuple))
        ):
            points = polygon
        elif isinstance(polygon, (list, tuple)) and len(polygon) == 4:
            x, y, right, bottom = (
                _number(item, "PaddleOCR response box") for item in polygon
            )
            points = [[x, y], [right, y], [right, bottom], [x, bottom]]
        else:
            raise ComparisonValidationError(
                "PaddleOCR response geometry shape is invalid"
            )
        x, y, box_width, box_height = _bbox(points, width, height, "PaddleOCR response")
        geometry.append(
            {
                "boxId": f"paddleocr-{index:04d}",
                "pageIndex": 0,
                "text": normalized,
                "x": x,
                "y": y,
                "width": box_width,
                "height": box_height,
                "order": len(geometry),
                "confidence": confidence,
            }
        )
    text = "\n".join(box["text"] for box in geometry)
    return {
        "text": text,
        "actualOutcome": "TEXT" if text and geometry else "EMPTY",
        "geometry": geometry,
    }


TSV_FIELDS = frozenset(
    {
        "level",
        "page_num",
        "block_num",
        "par_num",
        "line_num",
        "word_num",
        "left",
        "top",
        "width",
        "height",
        "conf",
        "text",
    }
)


def parse_tesseract_tsv(raw: bytes | str, *, width: int, height: int) -> dict[str, Any]:
    """Collapse Tesseract TSV word rows into reading-order line geometry."""

    try:
        text = raw.decode("utf-8") if isinstance(raw, bytes) else raw
    except UnicodeDecodeError as error:
        raise ComparisonValidationError("Tesseract TSV is not UTF-8") from error
    reader = csv.DictReader(io.StringIO(text), delimiter="\t")
    if reader.fieldnames is None or not TSV_FIELDS.issubset(set(reader.fieldnames)):
        raise ComparisonValidationError("Tesseract TSV header is incomplete")
    lines: dict[tuple[int, int, int, int, int], list[dict[str, Any]]] = defaultdict(
        list
    )
    for row_index, row in enumerate(reader, start=2):
        if None in row:
            raise ComparisonValidationError(
                f"Tesseract TSV row {row_index} has extra fields"
            )
        try:
            level = int(row["level"] or "")
            key = tuple(
                int(row[field] or "")
                for field in ("page_num", "block_num", "par_num", "line_num")
            )
            word_num = int(row["word_num"] or "")
            left = int(row["left"] or "")
            top = int(row["top"] or "")
            box_width = int(row["width"] or "")
            box_height = int(row["height"] or "")
            confidence = float(row["conf"] or "")
        except (TypeError, ValueError) as error:
            raise ComparisonValidationError(
                f"Tesseract TSV row {row_index} is invalid"
            ) from error
        if level != 5:
            continue
        value = (row.get("text") or "").strip()
        if not value:
            continue
        if (
            left < 0
            or top < 0
            or box_width <= 0
            or box_height <= 0
            or left + box_width > width
            or top + box_height > height
        ):
            raise ComparisonValidationError(
                f"Tesseract TSV row {row_index} geometry is invalid"
            )
        if not math.isfinite(confidence) or confidence < -1.0 or confidence > 100.0:
            raise ComparisonValidationError(
                f"Tesseract TSV row {row_index} confidence is invalid"
            )
        lines[key].append(
            {
                "wordNum": word_num,
                "text": normalize_text(value),
                "x": left,
                "y": top,
                "width": box_width,
                "height": box_height,
                "confidence": confidence if confidence >= 0.0 else None,
            }
        )
    geometry: list[dict[str, Any]] = []
    for line_index, key in enumerate(sorted(lines), start=0):
        words = sorted(lines[key], key=lambda word: word["wordNum"])
        left = min(word["x"] for word in words)
        top = min(word["y"] for word in words)
        right = max(word["x"] + word["width"] for word in words)
        bottom = max(word["y"] + word["height"] for word in words)
        confidence_values = [
            word["confidence"] for word in words if word["confidence"] is not None
        ]
        geometry.append(
            {
                "boxId": f"tesseract-{line_index:04d}",
                "pageIndex": key[0] - 1,
                "text": normalize_text(" ".join(word["text"] for word in words)),
                "x": left,
                "y": top,
                "width": right - left,
                "height": bottom - top,
                "order": line_index,
                "confidence": sum(confidence_values) / len(confidence_values) / 100.0
                if confidence_values
                else None,
            }
        )
    output_text = "\n".join(box["text"] for box in geometry)
    return {
        "text": output_text,
        "actualOutcome": "TEXT" if output_text and geometry else "EMPTY",
        "geometry": geometry,
    }


def _intersection_over_union(
    left: Mapping[str, Any], right: Mapping[str, Any]
) -> float:
    left_x2 = left["x"] + left["width"]
    left_y2 = left["y"] + left["height"]
    right_x2 = right["x"] + right["width"]
    right_y2 = right["y"] + right["height"]
    intersection_width = max(0, min(left_x2, right_x2) - max(left["x"], right["x"]))
    intersection_height = max(0, min(left_y2, right_y2) - max(left["y"], right["y"]))
    intersection = intersection_width * intersection_height
    union = (
        left["width"] * left["height"] + right["width"] * right["height"] - intersection
    )
    return intersection / union if union > 0 else 0.0


def geometry_accuracy(
    expected: Sequence[Mapping[str, Any]], predicted: Sequence[Mapping[str, Any]]
) -> dict[str, float | int]:
    """Match line boxes by normalized text and report IoU-weighted accuracy."""

    used: set[int] = set()
    matched = 0
    iou_sum = 0.0
    for expected_box in expected:
        expected_text = normalize_text(str(expected_box.get("text", "")))
        candidates = [
            (index, candidate, _intersection_over_union(expected_box, candidate))
            for index, candidate in enumerate(predicted)
            if index not in used
            and normalize_text(str(candidate.get("text", ""))) == expected_text
        ]
        if not candidates:
            continue
        index, _candidate, iou = max(candidates, key=lambda item: item[2])
        if iou >= 0.5:
            used.add(index)
            matched += 1
            iou_sum += iou
    expected_count = len(expected)
    return {
        "expectedBoxes": expected_count,
        "matchedBoxes": matched,
        "meanIoU": iou_sum / matched if matched else 0.0,
        "score": iou_sum / expected_count if expected_count else 1.0,
    }


def load_corpus(repo_root: Path) -> tuple[str, tuple[CorpusEntry, ...]]:
    manifest_path = repo_root / MANIFEST_RELATIVE_PATH
    manifest_raw = _read_file(
        manifest_path, "OCR corpus v2 manifest", MAX_MANIFEST_BYTES
    )
    manifest_sha = hashlib.sha256(manifest_raw).hexdigest()
    if manifest_sha != MANIFEST_SHA256:
        raise ComparisonValidationError("OCR corpus v2 manifest SHA-256 differs")
    manifest = _json_object(manifest_raw, "OCR corpus v2 manifest")
    if (
        manifest.get("schemaVersion") != 2
        or not isinstance(manifest.get("fixtures"), list)
        or not isinstance(manifest.get("negatives"), list)
    ):
        raise ComparisonValidationError("OCR corpus v2 manifest shape is invalid")
    entries: list[CorpusEntry] = []
    for item in manifest["fixtures"]:
        if not isinstance(item, dict):
            raise ComparisonValidationError("OCR corpus fixture entry is invalid")
        fixture_id = item.get("fixtureId")
        scenario = item.get("scenario")
        expected_outcome = item.get("expectedOutcome")
        resource = item.get("resource")
        truth = item.get("groundTruth")
        if (
            not isinstance(fixture_id, str)
            or not isinstance(scenario, str)
            or expected_outcome not in {"TEXT", "EMPTY"}
        ):
            raise ComparisonValidationError(
                "OCR corpus fixture classification is invalid"
            )
        if not isinstance(resource, dict) or not isinstance(truth, dict):
            raise ComparisonValidationError(
                f"OCR corpus fixture is incomplete: {fixture_id}"
            )
        resource_relative = _safe_relative(
            resource.get("path"), f"{fixture_id} resource.path"
        )
        image_path = (
            repo_root
            / "benchmark/images-benchmark/src/main/resources"
            / PurePosixPath(resource_relative)
        )
        image_raw = _verify_declared(
            image_path,
            f"{fixture_id} image",
            resource.get("bytes"),
            resource.get("sha256"),
        )
        actual_width, actual_height = _png_dimensions(image_raw, f"{fixture_id} image")
        width = resource.get("width")
        height = resource.get("height")
        if (
            type(width) is not int
            or type(height) is not int
            or (actual_width, actual_height) != (width, height)
        ):
            raise ComparisonValidationError(f"{fixture_id} image dimensions differ")
        text_info = truth.get("text")
        boxes_info = truth.get("boxes")
        if not isinstance(text_info, dict) or not isinstance(boxes_info, dict):
            raise ComparisonValidationError(f"{fixture_id} ground truth is incomplete")
        text_relative = _safe_relative(text_info.get("path"), f"{fixture_id} text.path")
        text_raw = _verify_declared(
            repo_root
            / "benchmark/images-benchmark/src/main/resources"
            / PurePosixPath(text_relative),
            f"{fixture_id} ground truth text",
            text_info.get("bytes"),
            text_info.get("sha256"),
            allow_empty=True,
        )
        try:
            expected_text = (
                unicodedata.normalize("NFC", text_raw.decode("utf-8"))
                .replace("\r\n", "\n")
                .replace("\r", "\n")
            )
        except UnicodeDecodeError as error:
            raise ComparisonValidationError(
                f"{fixture_id} ground truth text is not UTF-8"
            ) from error
        boxes_relative = _safe_relative(
            boxes_info.get("path"), f"{fixture_id} boxes.path"
        )
        boxes_raw = _verify_declared(
            repo_root
            / "benchmark/images-benchmark/src/main/resources"
            / PurePosixPath(boxes_relative),
            f"{fixture_id} ground truth boxes",
            boxes_info.get("bytes"),
            boxes_info.get("sha256"),
        )
        boxes_value = _json_object(boxes_raw, f"{fixture_id} boxes")
        raw_entries = boxes_value.get("entries")
        if (
            boxes_value.get("schema") != "ocr-boxes-v1"
            or boxes_value.get("coordinateSpace") != "pixel"
            or not isinstance(raw_entries, list)
        ):
            raise ComparisonValidationError(f"{fixture_id} boxes schema is invalid")
        expected_boxes: list[dict[str, Any]] = []
        for box in raw_entries:
            if not isinstance(box, dict):
                raise ComparisonValidationError(f"{fixture_id} box is invalid")
            required = {
                "boxId",
                "pageIndex",
                "text",
                "x",
                "y",
                "width",
                "height",
                "order",
            }
            if set(box) != required or box["pageIndex"] != 0:
                raise ComparisonValidationError(f"{fixture_id} box fields differ")
            if any(
                type(box[field]) is not int
                for field in ("x", "y", "width", "height", "order")
            ):
                raise ComparisonValidationError(
                    f"{fixture_id} box coordinates are invalid"
                )
            if (
                box["x"] < 0
                or box["y"] < 0
                or box["width"] <= 0
                or box["height"] <= 0
                or box["x"] + box["width"] > width
                or box["y"] + box["height"] > height
            ):
                raise ComparisonValidationError(
                    f"{fixture_id} box is outside image bounds"
                )
            expected_boxes.append(box)
        expected_lines = expected_text.rstrip("\n").split("\n") if expected_text else []
        if [box["text"] for box in expected_boxes] != expected_lines:
            raise ComparisonValidationError(
                f"{fixture_id} box text differs from ground truth"
            )
        entries.append(
            CorpusEntry(
                fixture_id=fixture_id,
                scenario=scenario,
                expected_outcome=expected_outcome,
                resource_path=image_path,
                resource_relative=resource_relative,
                width=width,
                height=height,
                languages=tuple(
                    item for item in item.get("languages", []) if isinstance(item, str)
                ),
                expected_text=expected_text,
                expected_boxes=tuple(expected_boxes),
            )
        )
    for item in manifest["negatives"]:
        if not isinstance(item, dict):
            raise ComparisonValidationError("OCR corpus negative entry is invalid")
        fixture_id = item.get("fixtureId")
        relative = _safe_relative(item.get("path"), "OCR corpus negative.path")
        if not isinstance(fixture_id, str):
            raise ComparisonValidationError("OCR corpus negative fixtureId is invalid")
        path = (
            repo_root
            / "benchmark/images-benchmark/src/main/resources"
            / PurePosixPath(relative)
        )
        _verify_declared(
            path, f"{fixture_id} negative", item.get("bytes"), item.get("sha256")
        )
        entries.append(
            CorpusEntry(
                fixture_id=fixture_id,
                scenario=item.get("scenario", "malformed"),
                expected_outcome=item.get("expectedOutcome", "ERROR"),
                resource_path=path,
                resource_relative=relative,
                width=None,
                height=None,
                languages=(),
                expected_text="",
                expected_boxes=(),
            )
        )
    if len(entries) != 27 or len(entries[:24]) != 24 or len(entries[24:]) != 3:
        raise ComparisonValidationError("OCR corpus v2 fixture count differs")
    return manifest_sha, tuple(entries)


def _safe_error(error: BaseException) -> str:
    value = str(error).replace("\x00", "").strip()
    return value[:MAX_ERROR_CHARS] or type(error).__name__


def _sha256_text(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def _rss_bytes() -> int:
    usage = resource.getrusage(resource.RUSAGE_SELF)
    value = int(usage.ru_maxrss)
    if sys.platform == "darwin":
        return max(1, value)
    return max(1, value * 1024)


def _children_rss_bytes() -> int:
    value = int(resource.getrusage(resource.RUSAGE_CHILDREN).ru_maxrss)
    if sys.platform == "darwin":
        return max(1, value)
    return max(1, value * 1024)


def _metric_row(
    entry: CorpusEntry,
    result: Mapping[str, Any],
    cold_ns: int,
    warm_ns: int,
    throughput: float,
    rss_before: int,
    rss_peak: int,
) -> dict[str, Any]:
    text = normalize_text(str(result.get("text", "")))
    geometry = list(result.get("geometry", []))
    actual_outcome = str(result.get("actualOutcome", "ERROR"))
    if actual_outcome not in {"TEXT", "EMPTY", "ERROR"}:
        actual_outcome = "ERROR"
    error_message = result.get("errorMessage")
    if actual_outcome == "ERROR":
        text = ""
        geometry = []
        error_message = _safe_error(str(error_message or "provider returned an error"))
    else:
        error_message = None
    return {
        "fixtureId": entry.fixture_id,
        "scenario": entry.scenario,
        "expectedOutcome": entry.expected_outcome,
        "actualOutcome": actual_outcome,
        "text": text,
        "geometry": geometry,
        "errorMessage": error_message,
        "coldLatencyNanos": max(1, int(cold_ns)),
        "warmLatencyNanos": max(1, int(warm_ns)),
        "throughputOpsPerSecond": float(max(1e-9, throughput)),
        "warmIterations": WARM_ITERATIONS,
        "rssBeforeBytes": max(1, int(rss_before)),
        "rssPeakBytes": max(max(1, int(rss_before)), int(rss_peak)),
        "outputSha256": _sha256_text(text),
    }


def _run_command(
    command: Sequence[str], *, input_bytes: bytes | None = None, timeout: float = 120.0
) -> subprocess.CompletedProcess[bytes]:
    try:
        result = subprocess.run(
            tuple(command),
            input=input_bytes,
            capture_output=True,
            check=False,
            timeout=timeout,
        )
    except (OSError, subprocess.TimeoutExpired) as error:
        raise ComparisonValidationError(
            f"command failed: {command[0] if command else 'unknown'}"
        ) from error
    if (
        len(result.stdout or b"") > MAX_OUTPUT_BYTES
        or len(result.stderr or b"") > MAX_OUTPUT_BYTES
    ):
        raise ComparisonValidationError("command output exceeds the comparison limit")
    return result


def _measure(call: Callable[[], Mapping[str, Any]]) -> tuple[dict[str, Any], int]:
    started = time.perf_counter_ns()
    result = dict(call())
    elapsed = max(1, time.perf_counter_ns() - started)
    return result, elapsed


def _tesseract_once(
    entry: CorpusEntry, *, raw_root: Path, run_index: str
) -> dict[str, Any]:
    languages = "+".join(entry.languages) if entry.languages else "eng"
    command = (
        "tesseract",
        str(entry.resource_path),
        "stdout",
        "--psm",
        "6",
        "-l",
        languages,
        "tsv",
    )
    result = _run_command(command, timeout=180.0)
    raw_path = raw_root / f"{entry.fixture_id}-{run_index}.tsv"
    _write_exclusive(raw_path, result.stdout or b"")
    if result.returncode != 0:
        detail = (result.stderr or b"")[:MAX_ERROR_CHARS].decode(
            "utf-8", errors="replace"
        )
        raise ComparisonValidationError(
            f"tesseract exited with code {result.returncode}: {detail.strip()}"
        )
    if entry.width is None or entry.height is None:
        raise ComparisonValidationError(
            "Tesseract cannot process a malformed fixture as a normal image"
        )
    return parse_tesseract_tsv(
        result.stdout or b"", width=entry.width, height=entry.height
    )


def _run_tesseract(entry: CorpusEntry, raw_root: Path) -> dict[str, Any]:
    rss_before = _rss_bytes()
    started = time.perf_counter_ns()
    try:

        def attempt(run_index: str) -> Mapping[str, Any]:
            try:
                return _tesseract_once(entry, raw_root=raw_root, run_index=run_index)
            except ComparisonValidationError as error:
                return {"actualOutcome": "ERROR", "errorMessage": _safe_error(error)}

        cold_result, cold_ns = _measure(lambda: attempt("cold"))
        warm_results: list[dict[str, Any]] = []
        warm_ns_total = 0
        for index in range(WARM_ITERATIONS):
            _measure(lambda index=index: attempt(f"warmup-{index}"))
        for index in range(WARM_ITERATIONS):
            result, elapsed = _measure(lambda index=index: attempt(f"warm-{index}"))
            warm_results.append(result)
            warm_ns_total += elapsed
        final = warm_results[-1]
        outcomes = {
            str(item.get("actualOutcome", "ERROR"))
            for item in [cold_result, *warm_results]
        }
        output_hashes = {
            _sha256_text(normalize_text(str(item.get("text", ""))))
            for item in [cold_result, *warm_results]
        }
        if len(outcomes) != 1 or len(output_hashes) != 1:
            raise ComparisonValidationError(
                "Tesseract output changed between repeated runs"
            )
        warm_ns = max(1, warm_ns_total // WARM_ITERATIONS)
        return _metric_row(
            entry,
            final,
            cold_ns,
            warm_ns,
            WARM_ITERATIONS / (warm_ns_total / 1_000_000_000),
            rss_before,
            max(rss_before, _children_rss_bytes()),
        )
    except ComparisonValidationError as error:
        elapsed = max(1, time.perf_counter_ns() - started)
        return _metric_row(
            entry,
            {"actualOutcome": "ERROR", "errorMessage": _safe_error(error)},
            elapsed,
            elapsed,
            1.0,
            rss_before,
            max(rss_before, _children_rss_bytes()),
        )


def _parse_memory_usage(value: str) -> int:
    match = re.match(
        r"\s*([0-9]+(?:\.[0-9]+)?)\s*([kmgt]?i?b)\s*/", value.strip(), re.IGNORECASE
    )
    if not match:
        raise ComparisonValidationError("docker stats memory value is invalid")
    amount = float(match.group(1))
    unit = match.group(2).lower()
    factors = {
        "b": 1,
        "kb": 1000,
        "mb": 1000**2,
        "gb": 1000**3,
        "tb": 1000**4,
        "kib": 1024,
        "mib": 1024**2,
        "gib": 1024**3,
        "tib": 1024**4,
    }
    if unit not in factors or amount < 0 or not math.isfinite(amount):
        raise ComparisonValidationError("docker stats memory value is invalid")
    return max(1, int(amount * factors[unit]))


def _docker_stats(container_name: str) -> int:
    result = _run_command(
        ("docker", "stats", "--no-stream", "--format", "{{json .}}", container_name),
        timeout=30.0,
    )
    if result.returncode != 0:
        raise ComparisonValidationError("docker stats failed")
    value = _json_object(result.stdout or b"", "docker stats")
    memory = value.get("MemUsage")
    if not isinstance(memory, str):
        raise ComparisonValidationError("docker stats memory usage is missing")
    return _parse_memory_usage(memory)


PADDLE_QUERY_SCRIPT = f"""import hashlib,json,sys,urllib.error,urllib.request
body=sys.stdin.buffer.read(16777217)
request=urllib.request.Request('http://127.0.0.1:8080/ocr',data=body,method='POST')
request.add_header('Content-Type','application/json')
opener=urllib.request.build_opener(urllib.request.ProxyHandler({{}}))
try:
 response=opener.open(request,timeout=120)
 status=response.status
 payload=response.read(16777217)
except urllib.error.HTTPError as error:
 status=error.code
 payload=error.read(16777217)
except Exception:
 status=599
 payload=b''
print({PADDLE_QUERY_MARKER!r}+json.dumps({{'status':status,'bytes':len(payload),'sha256':hashlib.sha256(payload).hexdigest()}},sort_keys=True),file=sys.stderr)
sys.stdout.buffer.write(payload)
"""


def _paddle_once(
    session: Mapping[str, Any], entry: CorpusEntry, *, raw_root: Path, run_index: str
) -> dict[str, Any]:
    payload = base64.b64encode(
        _read_file(
            entry.resource_path,
            f"{entry.fixture_id} image"
            if entry.width is not None
            else f"{entry.fixture_id} negative",
        )
    ).decode("ascii")
    request = json.dumps(
        {"file": payload, "fileType": 1}, separators=(",", ":"), sort_keys=True
    ).encode("utf-8")
    result = _run_command(
        build_exec_command(
            session["containerName"], PADDLE_QUERY_SCRIPT, interactive=True
        ),
        input_bytes=request,
        timeout=180.0,
    )
    if result.returncode != 0:
        raise ComparisonValidationError(
            f"docker exec exited with code {result.returncode}"
        )
    marker_lines = [
        line
        for line in (result.stderr or b"")
        .decode("utf-8", errors="replace")
        .splitlines()
        if line.startswith(PADDLE_QUERY_MARKER)
    ]
    if len(marker_lines) != 1:
        raise ComparisonValidationError("PaddleOCR response marker is missing")
    marker = _json_object(
        marker_lines[0][len(PADDLE_QUERY_MARKER) :], "PaddleOCR response marker"
    )
    if (
        set(marker) != {"status", "bytes", "sha256"}
        or type(marker["status"]) is not int
        or type(marker["bytes"]) is not int
        or not isinstance(marker["sha256"], str)
        or SHA256_RE.fullmatch(marker["sha256"]) is None
    ):
        raise ComparisonValidationError("PaddleOCR response marker is invalid")
    response_body = result.stdout or b""
    if (
        len(response_body) != marker["bytes"]
        or len(response_body) > MAX_OUTPUT_BYTES
        or hashlib.sha256(response_body).hexdigest() != marker["sha256"]
    ):
        raise ComparisonValidationError(
            "PaddleOCR response byte or hash receipt differs"
        )
    _write_exclusive(raw_root / f"{entry.fixture_id}-{run_index}.json", response_body)
    if not 200 <= marker["status"] <= 299:
        raise ComparisonValidationError(
            f"PaddleOCR service returned HTTP {marker['status']}"
        )
    if entry.width is None or entry.height is None:
        raise ComparisonValidationError("PaddleOCR must reject malformed fixture")
    return parse_paddle_response(response_body, width=entry.width, height=entry.height)


def _run_paddle(
    entry: CorpusEntry, session: Mapping[str, Any], raw_root: Path
) -> dict[str, Any]:
    rss_before = _docker_stats(session["containerName"])
    started = time.perf_counter_ns()
    try:

        def attempt(run_index: str) -> Mapping[str, Any]:
            try:
                return _paddle_once(
                    session, entry, raw_root=raw_root, run_index=run_index
                )
            except ComparisonValidationError as error:
                return {"actualOutcome": "ERROR", "errorMessage": _safe_error(error)}

        cold_result, cold_ns = _measure(lambda: attempt("cold"))
        warm_results: list[dict[str, Any]] = []
        warm_ns_total = 0
        for index in range(WARM_ITERATIONS):
            _measure(lambda index=index: attempt(f"warmup-{index}"))
        for index in range(WARM_ITERATIONS):
            result, elapsed = _measure(lambda index=index: attempt(f"warm-{index}"))
            warm_results.append(result)
            warm_ns_total += elapsed
        final = warm_results[-1]
        outcomes = {
            str(item.get("actualOutcome", "ERROR"))
            for item in [cold_result, *warm_results]
        }
        output_hashes = {
            _sha256_text(normalize_text(str(item.get("text", ""))))
            for item in [cold_result, *warm_results]
        }
        if len(outcomes) != 1 or len(output_hashes) != 1:
            raise ComparisonValidationError(
                "PaddleOCR output changed between repeated runs"
            )
        warm_ns = max(1, warm_ns_total // WARM_ITERATIONS)
        return _metric_row(
            entry,
            final,
            cold_ns,
            warm_ns,
            WARM_ITERATIONS / (warm_ns_total / 1_000_000_000),
            rss_before,
            max(rss_before, _docker_stats(session["containerName"])),
        )
    except ComparisonValidationError as error:
        elapsed = max(1, time.perf_counter_ns() - started)
        try:
            rss_peak = max(rss_before, _docker_stats(session["containerName"]))
        except ComparisonValidationError:
            rss_peak = rss_before
        return _metric_row(
            entry,
            {"actualOutcome": "ERROR", "errorMessage": _safe_error(error)},
            elapsed,
            elapsed,
            1.0,
            rss_before,
            rss_peak,
        )


def _write_exclusive(path: Path, payload: bytes) -> None:
    if path.parent.is_symlink() or not path.parent.is_dir() or path.is_symlink():
        raise ComparisonValidationError("comparison artifact parent is unsafe")
    flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL | getattr(os, "O_NOFOLLOW", 0)
    descriptor: int | None = None
    try:
        descriptor = os.open(path, flags, 0o600)
        written = 0
        while written < len(payload):
            written += os.write(descriptor, payload[written:])
        os.fsync(descriptor)
    except (OSError, TypeError) as error:
        try:
            path.unlink()
        except OSError:
            pass
        raise ComparisonValidationError(
            f"comparison artifact could not be written: {path.name}"
        ) from error
    finally:
        if descriptor is not None:
            os.close(descriptor)


def _write_json(path: Path, value: Mapping[str, Any]) -> None:
    _write_exclusive(
        path,
        (
            json.dumps(value, ensure_ascii=False, separators=(",", ":"), sort_keys=True)
            + "\n"
        ).encode("utf-8"),
    )


def _model_digest(languages: Iterable[str]) -> tuple[str, str]:
    candidates = (
        Path("/usr/share/tesseract-ocr/5/tessdata"),
        Path("/usr/share/tesseract-ocr/4.00/tessdata"),
        Path("/usr/share/tessdata"),
        Path("/opt/homebrew/share/tessdata"),
    )
    root = next(
        (
            candidate
            for candidate in candidates
            if candidate.is_dir() and not candidate.is_symlink()
        ),
        None,
    )
    if root is None:
        raise ComparisonValidationError("Tesseract tessdata directory is missing")
    digest = hashlib.sha256()
    for language in sorted(set(languages)):
        if not re.fullmatch(r"[A-Za-z0-9_-]+", language):
            raise ComparisonValidationError("Tesseract language identifier is invalid")
        path = root / f"{language}.traineddata"
        resolved = path.resolve(strict=False) if path.is_symlink() else path
        allowed_roots = (root, root.parent.parent)
        if (
            not any(allowed in resolved.parents for allowed in allowed_roots)
            or resolved.is_symlink()
            or not resolved.is_file()
        ):
            raise ComparisonValidationError(
                f"Tesseract {language} traineddata is not a regular file"
            )
        payload = _read_file(
            resolved, f"Tesseract {language} traineddata", 256 * 1024 * 1024
        )
        digest.update(language.encode("ascii"))
        digest.update(b"\0")
        digest.update(hashlib.sha256(payload).digest())
    return f"sha256:{digest.hexdigest()}", str(root)


def _tesseract_identity(entries: Sequence[CorpusEntry]) -> dict[str, str]:
    result = _run_command(("tesseract", "--version"), timeout=30.0)
    if result.returncode != 0 or not result.stdout:
        raise ComparisonValidationError("Tesseract version could not be read")
    runtime = result.stdout.decode("utf-8", errors="replace").splitlines()[0].strip()
    languages = [language for entry in entries for language in entry.languages]
    model, _root = _model_digest(languages)
    host_material = f"{REQUIRED_PLATFORM}\n{runtime}\n{model}\n{platform.machine()}\n{platform.release()}"
    return {
        "provider": "tesseract",
        "runtime": runtime,
        "model": model,
        "imageDigest": f"sha256:{_sha256_text(host_material)}",
    }


def _aggregate_metrics(
    entries: Sequence[CorpusEntry], rows: Sequence[Mapping[str, Any]]
) -> dict[str, float]:
    row_by_id = {row["fixtureId"]: row for row in rows}
    quality = [entry for entry in entries if entry.expected_outcome == "TEXT"]
    text_scores = [
        score_text(entry.expected_text, str(row_by_id[entry.fixture_id]["text"]))
        for entry in quality
    ]
    geometry_scores = [
        geometry_accuracy(entry.expected_boxes, row_by_id[entry.fixture_id]["geometry"])
        for entry in quality
    ]
    outcome_accuracy = sum(
        row["actualOutcome"] == row["expectedOutcome"] for row in rows
    ) / len(rows)
    return {
        "cer": sum(float(score["cer"]) for score in text_scores) / len(text_scores),
        "wer": sum(float(score["wer"]) for score in text_scores) / len(text_scores),
        "geometryAccuracy": sum(float(score["score"]) for score in geometry_scores)
        / len(geometry_scores),
        "outcomeAccuracy": float(outcome_accuracy),
        "averageColdLatencyNanos": sum(row["coldLatencyNanos"] for row in rows)
        / len(rows),
        "averageWarmLatencyNanos": sum(row["warmLatencyNanos"] for row in rows)
        / len(rows),
        "averageThroughputOpsPerSecond": sum(
            row["throughputOpsPerSecond"] for row in rows
        )
        / len(rows),
        "averageRssPeakBytes": sum(row["rssPeakBytes"] for row in rows) / len(rows),
    }


def _comparison_receipt(
    manifest_sha: str,
    entries: Sequence[CorpusEntry],
    tesseract: ProviderRun,
    paddle: ProviderRun,
) -> dict[str, Any]:
    baseline = tesseract.metrics
    candidate = paddle.metrics
    baseline_throughput = baseline["averageThroughputOpsPerSecond"]
    throughput_delta = (
        (candidate["averageThroughputOpsPerSecond"] / baseline_throughput - 1.0) * 100.0
        if baseline_throughput > 0
        else 0.0
    )
    baseline_latency = baseline["averageWarmLatencyNanos"]
    latency_delta = (
        (candidate["averageWarmLatencyNanos"] / baseline_latency - 1.0) * 100.0
        if baseline_latency > 0
        else 0.0
    )
    summary = {
        "baselineProvider": "tesseract",
        "candidateProvider": "paddleocr",
        "comparedFixtureCount": len(entries),
        "cer": candidate["cer"],
        "wer": candidate["wer"],
        "throughputDeltaPercent": throughput_delta,
        "rssPeakDeltaBytes": round(
            candidate["averageRssPeakBytes"] - baseline["averageRssPeakBytes"]
        ),
        "baselineCer": baseline["cer"],
        "candidateCer": candidate["cer"],
        "baselineWer": baseline["wer"],
        "candidateWer": candidate["wer"],
        "baselineGeometryAccuracy": baseline["geometryAccuracy"],
        "candidateGeometryAccuracy": candidate["geometryAccuracy"],
        "baselineOutcomeAccuracy": baseline["outcomeAccuracy"],
        "candidateOutcomeAccuracy": candidate["outcomeAccuracy"],
        "coldLatencyDeltaPercent": (
            (
                candidate["averageColdLatencyNanos"]
                / baseline["averageColdLatencyNanos"]
                - 1.0
            )
            * 100.0
        )
        if baseline["averageColdLatencyNanos"] > 0
        else 0.0,
        "warmLatencyDeltaPercent": latency_delta,
    }
    return {
        "schemaVersion": 1,
        "issue": 544,
        "status": "COMPARABLE",
        "manifestSha256": manifest_sha,
        "providers": [
            {
                "manifestSha256": manifest_sha,
                "identity": tesseract.identity,
                "fixtures": list(tesseract.rows),
            },
            {
                "manifestSha256": manifest_sha,
                "identity": paddle.identity,
                "fixtures": list(paddle.rows),
            },
        ],
        "comparison": summary,
    }


def _start_paddle(
    repo_root: Path,
    image: str,
    config_path: Path,
    work_root: Path,
    fixture: CorpusEntry,
    container_name: str,
) -> tuple[dict[str, Any], Any]:
    if image != PADDLE_IMAGE_DIGEST or IMAGE_RE.fullmatch(image) is None:
        raise ComparisonValidationError(
            "PaddleOCR image must match the trusted immutable digest"
        )
    del repo_root
    service_dir = work_root
    fixture_dir = service_dir / "fixture"
    output_dir = service_dir / "output"
    fixture_dir.mkdir(parents=True, exist_ok=False)
    output_dir.mkdir(parents=True, exist_ok=False)
    payload = _read_file(fixture.resource_path, "PaddleOCR preflight fixture")
    fixture_copy = fixture_dir / "fixture.bin"
    fixture_copy.write_bytes(payload)
    fixture_copy.chmod(stat.S_IRUSR | stat.S_IWUSR)
    fixture_manifest = {
        "schemaVersion": 1,
        "fixtures": [
            {
                "id": "fixture",
                "path": "fixture.bin",
                "bytes": len(payload),
                "sha256": hashlib.sha256(payload).hexdigest(),
            }
        ],
    }
    fixture_manifest_path = fixture_dir / "manifest.json"
    fixture_manifest_path.write_text(
        json.dumps(fixture_manifest, separators=(",", ":"), sort_keys=True) + "\n",
        encoding="utf-8",
    )
    config_sha, service_config = load_service_config(config_path)
    inputs = validate_inputs(
        image=image,
        model_manifest=None,
        model_root=None,
        fixture_manifest=fixture_manifest_path,
        config=config_path,
        output_root=output_dir,
    )
    image_command = (
        "docker",
        "image",
        "inspect",
        image,
        "--format",
        '{{.Os}}/{{.Architecture}}{{printf "\\n"}}{{index .RepoDigests 0}}',
    )
    image_result = _run_command(image_command, timeout=30.0)
    if image_result.returncode != 0:
        raise ComparisonValidationError("trusted PaddleOCR image is unavailable")
    parse_image_inspect(image_result.stdout or b"", image)
    host_result = _run_command(
        ("docker", "info", "--format", "{{.OSType}}/{{.Architecture}}"), timeout=30.0
    )
    if host_result.returncode != 0:
        raise ComparisonValidationError("Docker host platform could not be read")
    parse_host_platform(host_result.stdout or b"")
    started_container = False
    try:
        start_command = build_container_command(inputs.docker_command, container_name)
        started = _run_command(start_command, timeout=30.0)
        if started.returncode != 0:
            raise ComparisonValidationError("PaddleOCR container could not start")
        started_container = True
        try:
            inspect_value = _docker_inspect(_run_command, container_name)
        except (AcceptanceValidationError, ComparisonValidationError) as error:
            raise ComparisonValidationError(
                "PaddleOCR container inspect failed"
            ) from error
        security = validate_container_inspect(inspect_value)
        deadline = time.monotonic() + service_config.readiness_timeout_seconds
        readiness: dict[str, Any] | None = None
        while time.monotonic() < deadline:
            probe = _run_command(
                build_exec_command(container_name, _HEALTH_PROBE), timeout=10.0
            )
            if probe.returncode == 0:
                try:
                    candidate = parse_probe_output(probe.stdout or b"")
                    if candidate["status"] == 200:
                        readiness = candidate
                        break
                except (AcceptanceValidationError, ComparisonValidationError):
                    pass
            time.sleep(1.0)
        if readiness is None:
            raise ComparisonValidationError("PaddleOCR readiness deadline exceeded")
        return {
            "containerName": container_name,
            "security": security,
            "readiness": readiness,
            "configSha256": config_sha,
            "image": image,
        }, inputs.model_snapshot
    except BaseException:
        if started_container:
            _cleanup_container(_cleanup_runner, container_name)
        raise


def _cleanup_runner(
    command: Sequence[str], *, timeout: float | None = None
) -> subprocess.CompletedProcess[bytes]:
    try:
        return _run_command(command, timeout=timeout or 30.0)
    except ComparisonValidationError as error:
        return subprocess.CompletedProcess(
            tuple(command), 1, b"", _safe_error(error).encode("utf-8")
        )


def _artifact_digest(path: Path) -> dict[str, Any]:
    payload = _read_file(path, "comparison artifact", MAX_OUTPUT_BYTES)
    return {
        "path": path.name,
        "bytes": len(payload),
        "sha256": hashlib.sha256(payload).hexdigest(),
    }


def _source_commit(repo_root: Path) -> str:
    result = _run_command(
        ("git", "-C", str(repo_root), "rev-parse", "HEAD"), timeout=30.0
    )
    if result.returncode != 0:
        raise ComparisonValidationError("comparison source commit could not be read")
    commit = (result.stdout or b"").decode("ascii", errors="strict").strip()
    if re.fullmatch(r"[0-9a-f]{40}", commit) is None:
        raise ComparisonValidationError("comparison source commit is invalid")
    return commit


def _artifact_inventory(root: Path) -> list[dict[str, Any]]:
    artifacts: list[dict[str, Any]] = []
    for path in sorted(root.rglob("*")):
        if path.is_symlink() or not path.is_file():
            continue
        payload = path.read_bytes()
        if len(payload) > MAX_OUTPUT_BYTES:
            raise ComparisonValidationError(
                f"raw comparison artifact exceeds the byte limit: {path.name}"
            )
        artifacts.append(
            {
                "path": path.relative_to(root).as_posix(),
                "bytes": len(payload),
                "sha256": hashlib.sha256(payload).hexdigest(),
            }
        )
    return artifacts


def run_comparison(
    repo_root: Path,
    output_root: Path,
    receipt_path: Path,
    run_manifest_path: Path,
    image: str,
    config_path: Path,
) -> dict[str, Any]:
    manifest_sha, entries = load_corpus(repo_root)
    output_root.mkdir(parents=True, exist_ok=True)
    for directory in (
        output_root / "raw" / "tesseract",
        output_root / "raw" / "paddleocr",
        output_root / "service",
    ):
        directory.mkdir(parents=True, exist_ok=False)
    tesseract_identity = _tesseract_identity(entries)
    tesseract_rows = tuple(
        _run_tesseract(entry, output_root / "raw" / "tesseract") for entry in entries
    )
    tesseract = ProviderRun(
        tesseract_rows, _aggregate_metrics(entries, tesseract_rows), tesseract_identity
    )
    container_name = f"bluetape4k-paddleocr-comparison-{os.getpid()}"
    session: dict[str, Any] | None = None
    cleanup: dict[str, Any] | None = None
    receipt: dict[str, Any] | None = None
    try:
        session, _snapshot = _start_paddle(
            repo_root,
            image,
            config_path,
            output_root / "service",
            entries[0],
            container_name,
        )
        paddle_rows = tuple(
            _run_paddle(entry, session, output_root / "raw" / "paddleocr")
            for entry in entries
        )
        paddle_identity = {
            "provider": "paddleocr",
            "runtime": "paddlex-http@OCR",
            "model": PADDLE_MODEL_DIGEST,
            "imageDigest": image,
        }
        paddle = ProviderRun(
            paddle_rows, _aggregate_metrics(entries, paddle_rows), paddle_identity
        )
        receipt = _comparison_receipt(manifest_sha, entries, tesseract, paddle)
    finally:
        if session is not None:
            cleanup = _cleanup_container(_cleanup_runner, container_name)
    if receipt is None:
        raise ComparisonValidationError("comparison receipt was not produced")
    if cleanup is None or not cleanup.get("verified"):
        raise ComparisonValidationError(
            "PaddleOCR container cleanup could not be verified"
        )
    _write_json(receipt_path, receipt)
    run_manifest = {
        "schemaVersion": 1,
        "kind": "ocr-provider-comparison",
        "status": "PASS",
        "issue": 544,
        "sourceCommit": _source_commit(repo_root),
        "manifestSha256": manifest_sha,
        "platform": {
            "required": REQUIRED_PLATFORM,
            "host": REQUIRED_PLATFORM,
            "runner": platform.platform(),
        },
        "protocol": {
            "coldRuns": 1,
            "warmupRuns": WARM_ITERATIONS,
            "warmRuns": WARM_ITERATIONS,
            "throughputMetric": "warm-ops-per-second",
            "rssUnit": "bytes",
        },
        "providers": [
            {"identity": tesseract.identity, "metrics": tesseract.metrics},
            {"identity": paddle.identity, "metrics": paddle.metrics},
        ],
        "service": {
            "image": image,
            "model": PADDLE_MODEL_DIGEST,
            "readiness": session["readiness"],
            "security": session["security"],
            "cleanupVerified": True,
        },
        "rawArtifacts": _artifact_inventory(output_root / "raw"),
        "receipt": _artifact_digest(receipt_path),
    }
    _write_json(run_manifest_path, run_manifest)
    return run_manifest


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo-root", type=Path, default=Path.cwd())
    parser.add_argument("--output-root", required=True, type=Path)
    parser.add_argument("--receipt", required=True, type=Path)
    parser.add_argument("--run-manifest", required=True, type=Path)
    parser.add_argument("--image", required=True)
    parser.add_argument("--config", required=True, type=Path)
    args = parser.parse_args(argv)
    try:
        run_manifest = run_comparison(
            args.repo_root.resolve(),
            args.output_root.resolve(),
            args.receipt.resolve(),
            args.run_manifest.resolve(),
            args.image,
            args.config.resolve(),
        )
        print(
            json.dumps(
                run_manifest, ensure_ascii=False, separators=(",", ":"), sort_keys=True
            )
        )
        return 0
    except (
        ComparisonValidationError,
        SmokeValidationError,
        AcceptanceValidationError,
        OSError,
        subprocess.TimeoutExpired,
    ) as error:
        print(f"comparison failed: {_safe_error(error)}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
