#!/usr/bin/env python3
"""Generate README preview images for the detector sample corpus."""

from __future__ import annotations

import json
import subprocess
from dataclasses import dataclass
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SAMPLE_ROOT = ROOT / "images" / "src" / "test" / "resources"
MANIFEST = SAMPLE_ROOT / "detection" / "samples" / "metadata.json"
OUTPUT = ROOT / "docs" / "images" / "detection-samples"
FONT = "Comic-Mono"

CATEGORY_COLORS = {
    "FACE": "#f97316",
    "PERSON": "#2563eb",
    "OBJECT": "#16a34a",
    "TEXT": "#dc2626",
    "LANDMARK": "#7c3aed",
}


@dataclass(frozen=True)
class SamplePreview:
    sample_id: str
    title: str
    categories: tuple[str, ...]
    image_path: Path


def run(args: list[str]) -> None:
    subprocess.run(args, check=True, cwd=ROOT)


def label_width(label: str) -> int:
    return max(90, min(230, 9 * len(label) + 16))


def draw_args(sample: dict) -> list[str]:
    width = int(sample["expectedDimensions"]["width"])
    height = int(sample["expectedDimensions"]["height"])
    args: list[str] = []
    for index, expected in enumerate(sample["expectedDetections"]):
        category = expected["category"]
        label = f"{category} {expected['confidence']:.2f}"
        color = CATEGORY_COLORS.get(category, "#0f172a")
        region = expected["region"]
        x1 = round(float(region["x"]) * width)
        y1 = round(float(region["y"]) * height)
        x2 = round((float(region["x"]) + float(region["width"])) * width)
        y2 = round((float(region["y"]) + float(region["height"])) * height)
        label_y1 = min(height - 30, y1 + index * 28)
        label_x2 = min(width - 3, x1 + label_width(label))
        label_y2 = min(height - 3, label_y1 + 26)
        text_y = min(height - 8, label_y1 + 19)
        args.extend(
            [
                "-fill",
                "none",
                "-stroke",
                color,
                "-strokewidth",
                "4",
                "-draw",
                f"rectangle {x1},{y1} {x2},{y2}",
                "-fill",
                color,
                "-stroke",
                color,
                "-strokewidth",
                "1",
                "-draw",
                f"rectangle {x1},{label_y1} {label_x2},{label_y2}",
                "-font",
                FONT,
                "-pointsize",
                "15",
                "-fill",
                "white",
                "-stroke",
                "none",
                "-draw",
                f"text {x1 + 7},{text_y} '{label}'",
            ]
        )
    return args


def preview_title(sample: dict) -> str:
    tags = sample["expectedTags"]
    if "portrait" in tags:
        return "Face/person sample"
    if "traffic-sign" in tags:
        return "Object/text sample"
    if "space" in tags:
        return "Landmark/object sample"
    return "Document text sample"


def render_sample(sample: dict) -> SamplePreview:
    image_path = SAMPLE_ROOT / sample["resourcePath"]
    output_path = OUTPUT / f"{sample['id']}-detections.png"
    run(
        [
            "magick",
            str(image_path),
            *draw_args(sample),
            "-strip",
            str(output_path),
        ]
    )
    categories = tuple(dict.fromkeys(item["category"] for item in sample["expectedDetections"]))
    return SamplePreview(
        sample_id=sample["id"],
        title=preview_title(sample),
        categories=categories,
        image_path=output_path,
    )


def render_contact_sheet(previews: list[SamplePreview]) -> None:
    args = [
        "magick",
        "montage",
        "-background",
        "#f8fafc",
        "-bordercolor",
        "#cbd5e1",
        "-font",
        FONT,
        "-pointsize",
        "18",
        "-fill",
        "#0f172a",
    ]
    for preview in previews:
        args.extend(
            [
                "-label",
                f"{preview.title}\\n{', '.join(preview.categories)}",
                str(preview.image_path),
            ]
        )
    args.extend(
        [
            "-tile",
            "2x2",
            "-geometry",
            "420x430+18+20",
            "-strip",
            "-depth",
            "8",
            str(OUTPUT / "sample-detection-results.png"),
        ]
    )
    run(args)


def main() -> None:
    OUTPUT.mkdir(parents=True, exist_ok=True)
    samples = json.loads(MANIFEST.read_text(encoding="utf-8"))
    previews = [render_sample(sample) for sample in samples]
    render_contact_sheet(previews)


if __name__ == "__main__":
    main()
