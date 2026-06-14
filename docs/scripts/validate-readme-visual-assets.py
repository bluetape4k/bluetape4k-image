#!/usr/bin/env python3
"""Validate README visual assets generated for docs/images."""

from __future__ import annotations

import re
import subprocess
import xml.etree.ElementTree as ET
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
DIAGRAM_DIR = ROOT / "docs" / "images" / "readme-diagrams"
CHART_DIR = ROOT / "docs" / "images" / "readme-charts"
SUMMARY = DIAGRAM_DIR / "validation-summary.txt"
FINAL_EVIDENCE_TOKENS = ("Graphviz evidence", "-graphviz", ".plain", ".dot")
LEGACY_STYLE_TOKENS = (
    "feDropShadow",
    "filter:url(#shadow)",
    'class="frame"',
    "#F6F9FC",
    "#E8F3FF",
    "#EAF7EF",
    "#FFF3D9",
    "#FDECEF",
    "#E9F7F6",
    "#F1ECFF",
    "#F7F1E7",
    "#FFF1E8",
)
SVG_NS = {"s": "http://www.w3.org/2000/svg"}


def readme_files() -> list[Path]:
    return sorted(path for path in ROOT.rglob("README*.md") if "build" not in path.parts)


def markdown_image_links(text: str) -> list[str]:
    return re.findall(r"!\[[^\]]*\]\(([^)]+)\)", text)


def validate_readme_links(lines: list[str]) -> None:
    missing: list[str] = []
    svg_embeds: list[str] = []
    legacy_refs: list[str] = []
    for readme in readme_files():
        text = readme.read_text(encoding="utf-8")
        for link in markdown_image_links(text):
            if "docs/assets" in link or "images-ocr/docs/assets" in link:
                legacy_refs.append(f"{readme.relative_to(ROOT)} -> {link}")
            if link.endswith(".svg") and "docs/images/readme-" in link:
                svg_embeds.append(f"{readme.relative_to(ROOT)} -> {link}")
            if link.startswith(("http://", "https://")):
                continue
            target = (readme.parent / link).resolve()
            if not target.exists():
                missing.append(f"{readme.relative_to(ROOT)} -> {link}")
    if missing:
        raise AssertionError("missing README image links:\n" + "\n".join(missing))
    if svg_embeds:
        raise AssertionError("README embeds SVG instead of PNG:\n" + "\n".join(svg_embeds))
    if legacy_refs:
        raise AssertionError("README still references legacy assets:\n" + "\n".join(legacy_refs))
    lines.append(f"README links: missing=0 svgEmbeds=0 legacyRefs=0 files={len(readme_files())}")


def final_svgs() -> list[Path]:
    return sorted(
        path
        for path in [*DIAGRAM_DIR.glob("*.svg"), *CHART_DIR.glob("*.svg")]
        if "-graphviz" not in path.stem
    )


def path_points(path_data: str) -> list[tuple[float, float]]:
    nums = [float(value) for value in re.findall(r"-?\d+(?:\.\d+)?", path_data)]
    return list(zip(nums[0::2], nums[1::2]))


def route_orientation(segment: tuple[tuple[float, float], tuple[float, float]]) -> str:
    (x1, y1), (x2, y2) = segment
    if abs(x1 - x2) < 0.1:
        return "vertical"
    if abs(y1 - y2) < 0.1:
        return "horizontal"
    return "diagonal"


def routes_cross(
    first: tuple[tuple[float, float], tuple[float, float]],
    second: tuple[tuple[float, float], tuple[float, float]],
) -> bool:
    first_orientation = route_orientation(first)
    second_orientation = route_orientation(second)
    if "diagonal" in {first_orientation, second_orientation} or first_orientation == second_orientation:
        return False
    (x1, y1), (x2, y2) = first
    (x3, y3), (x4, y4) = second
    if first_orientation == "horizontal":
        horizontal_min, horizontal_max = sorted((x1, x2))
        horizontal_y = y1
        vertical_x = x3
        vertical_min, vertical_max = sorted((y3, y4))
    else:
        horizontal_min, horizontal_max = sorted((x3, x4))
        horizontal_y = y3
        vertical_x = x1
        vertical_min, vertical_max = sorted((y1, y2))
    return horizontal_min < vertical_x < horizontal_max and vertical_min < horizontal_y < vertical_max


def text_width_estimate(value: str, css_class: str) -> float:
    if any(token in css_class for token in ("title", "name", "card-title", "node-title", "class-name")):
        return len(value) * 14.8
    if any(token in css_class for token in ("member", "detail", "stereo", "label")):
        return len(value) * 8.4
    return 0.0


def rect_bounds(rect: ET.Element) -> tuple[float, float, float, float]:
    x = float(rect.get("x", "0"))
    y = float(rect.get("y", "0"))
    width = float(rect.get("width", "0"))
    height = float(rect.get("height", "0"))
    return (x, y, x + width, y + height)


def text_position(text: ET.Element) -> tuple[float, float] | None:
    x = text.get("x")
    y = text.get("y")
    if x is None or y is None:
        return None
    return (float(x), float(y))


def validate_visual_geometry(svg: Path) -> tuple[int, int]:
    tree = ET.parse(svg)
    card_text_checked = 0
    crossing_count = 0
    for group in tree.findall(".//s:g", SVG_NS):
        rect = group.find("s:rect", SVG_NS)
        if rect is None or "card" not in rect.get("class", ""):
            continue
        width = float(rect.get("width", "0"))
        for text in group.findall("s:text", SVG_NS):
            value = "".join(text.itertext()).strip()
            if not value:
                continue
            css_class = text.get("class", "")
            if any(token in css_class for token in ("title", "name", "card-title", "node-title", "class-name")):
                factor = 14.8
            elif any(token in css_class for token in ("member", "detail", "stereo")):
                factor = 8.4
            else:
                continue
            if len(value) * factor > width - 30:
                raise AssertionError(
                    f"{svg.relative_to(ROOT)} text overflows card: {value!r} ({len(value) * factor:.1f}px > {width - 30:.1f}px)"
                )
            card_text_checked += 1

    for group in tree.findall(".//s:g", SVG_NS):
        rects = [
            rect
            for rect in group.findall("s:rect", SVG_NS)
            if not any(token in rect.get("class", "") for token in ("canvas", "frame", "layer"))
        ]
        if not rects:
            continue
        for text in group.findall("s:text", SVG_NS):
            value = "".join(text.itertext()).strip()
            if not value:
                continue
            css_class = text.get("class", "")
            estimate = text_width_estimate(value, css_class)
            if estimate <= 0:
                continue
            position = text_position(text)
            if position is None:
                continue
            x, y = position
            candidates: list[tuple[float, ET.Element]] = []
            for rect in rects:
                left, top, right, bottom = rect_bounds(rect)
                if left <= x <= right and top <= y <= bottom:
                    candidates.append(((right - left) * (bottom - top), rect))
            if not candidates:
                continue
            rect = min(candidates, key=lambda candidate: candidate[0])[1]
            left, _, right, _ = rect_bounds(rect)
            available = right - left - 18
            if estimate > available:
                raise AssertionError(
                    f"{svg.relative_to(ROOT)} text overflows containing shape: {value!r} ({estimate:.1f}px > {available:.1f}px)"
                )
            card_text_checked += 1

    route_segments: list[tuple[int, tuple[tuple[float, float], tuple[float, float]]]] = []
    for path_index, path in enumerate(tree.findall(".//s:path", SVG_NS)):
        css_class = path.get("class", "")
        if not any(token in css_class for token in ("edge", "dependency", "inheritance")):
            continue
        points = path_points(path.get("d", ""))
        for segment in zip(points, points[1:]):
            if route_orientation(segment) == "diagonal":
                raise AssertionError(f"{svg.relative_to(ROOT)} contains diagonal connector segment {segment}")
            route_segments.append((path_index, segment))
    for index, (first_path, first_segment) in enumerate(route_segments):
        for second_path, second_segment in route_segments[index + 1 :]:
            if first_path == second_path:
                continue
            if routes_cross(first_segment, second_segment):
                raise AssertionError(
                    f"{svg.relative_to(ROOT)} connector routes cross: {first_segment} x {second_segment}"
                )
                crossing_count += 1
    return card_text_checked, crossing_count


def validate_layer_spacing(svg: Path) -> int:
    tree = ET.parse(svg)
    layers: list[tuple[float, float, float, float]] = []
    for rect in tree.findall(".//s:rect", SVG_NS):
        if "layer" in rect.get("class", "").split():
            layers.append(rect_bounds(rect))
    checked = 0
    by_column: dict[tuple[float, float], list[tuple[float, float, float, float]]] = {}
    for bounds in layers:
        left, _, right, _ = bounds
        by_column.setdefault((left, right), []).append(bounds)
    for column_layers in by_column.values():
        ordered = sorted(column_layers, key=lambda bounds: bounds[1])
        for first, second in zip(ordered, ordered[1:]):
            gap = second[1] - first[3]
            if gap < 24:
                raise AssertionError(
                    f"{svg.relative_to(ROOT)} layer gap too small: {gap:.1f}px"
                )
            checked += 1
    return checked


def validate_sequence_geometry(svg: Path) -> tuple[int, int]:
    if "sequence" not in svg.stem:
        return 0, 0
    tree = ET.parse(svg)
    participant_group = tree.find(".//s:g[@id='participants']", SVG_NS)
    if participant_group is None:
        raise AssertionError(f"{svg.relative_to(ROOT)} sequence missing participants group")
    headers: list[tuple[float, float, float]] = []
    for rect in participant_group.findall("s:rect", SVG_NS):
        width = float(rect.get("width", "0"))
        if width < 100:
            continue
        x = float(rect.get("x", "0"))
        headers.append((x, x + width, width))
    if len(headers) < 2:
        raise AssertionError(f"{svg.relative_to(ROOT)} sequence has fewer than two participant headers")
    headers.sort()
    min_gap = min(right_next[0] - left_prev[1] for left_prev, right_next in zip(headers, headers[1:]))
    if min_gap < 32:
        raise AssertionError(f"{svg.relative_to(ROOT)} participant headers too close: {min_gap:.1f}px")

    message_group = tree.find(".//s:g[@id='messages']", SVG_NS)
    if message_group is None:
        raise AssertionError(f"{svg.relative_to(ROOT)} sequence missing messages group")
    arrow_ys: list[float] = []
    for path in message_group.findall("s:path", SVG_NS):
        points = path_points(path.get("d", ""))
        if len(points) >= 2 and abs(points[0][1] - points[1][1]) < 0.1:
            arrow_ys.append(points[0][1])
    if not arrow_ys:
        raise AssertionError(f"{svg.relative_to(ROOT)} sequence has no horizontal message arrows")

    label_checks = 0
    for rect in message_group.findall("s:rect", SVG_NS):
        width = float(rect.get("width", "0"))
        height = float(rect.get("height", "0"))
        if width < 100 or height < 20:
            continue
        _, top, _, bottom = rect_bounds(rect)
        below = [arrow_y for arrow_y in arrow_ys if arrow_y >= bottom]
        if not below:
            raise AssertionError(f"{svg.relative_to(ROOT)} sequence label has no arrow below it")
        clearance = min(below) - bottom
        if clearance < 10:
            raise AssertionError(f"{svg.relative_to(ROOT)} sequence label too close to arrow: {clearance:.1f}px")
        if top < 140:
            raise AssertionError(f"{svg.relative_to(ROOT)} sequence label intrudes into title/header area")
        label_checks += 1
    if label_checks != len(arrow_ys):
        raise AssertionError(
            f"{svg.relative_to(ROOT)} sequence label count mismatch: labels={label_checks} arrows={len(arrow_ys)}"
        )
    return len(headers), label_checks


def validate_svg_png(lines: list[str]) -> None:
    svgs = final_svgs()
    if not svgs:
        raise AssertionError("no final SVG assets found")
    visual_text_checks = 0
    layer_gap_checks = 0
    sequence_headers = 0
    sequence_labels = 0
    for svg in svgs:
        ET.parse(svg)
        png = svg.with_suffix(".png")
        if not png.exists():
            raise AssertionError(f"missing PNG for {svg.relative_to(ROOT)}")
        subprocess.run(["identify", str(png)], check=True, stdout=subprocess.DEVNULL)
        content = svg.read_text(encoding="utf-8")
        for forbidden in ("Inter", "Arial", "Helvetica"):
            if forbidden in content:
                raise AssertionError(f"{svg.relative_to(ROOT)} contains forbidden font {forbidden}")
        if "Architects Daughter" not in content or "Comic Mono" not in content:
            raise AssertionError(f"{svg.relative_to(ROOT)} missing required font role")
        for stale in ('markerWidth="8"', 'markerHeight="8"', 'markerWidth="11"', 'markerHeight="10"', 'markerWidth="13"', 'markerHeight="13"'):
            if stale in content:
                raise AssertionError(f"{svg.relative_to(ROOT)} contains stale marker size {stale}")
        for token in FINAL_EVIDENCE_TOKENS:
            if token in content:
                raise AssertionError(f"{svg.relative_to(ROOT)} exposes validation evidence token {token}")
        for token in LEGACY_STYLE_TOKENS:
            if token in content:
                raise AssertionError(f"{svg.relative_to(ROOT)} still contains legacy bluetape style token {token}")
        if "github.com/debop/bluetape4k-image" in content:
            raise AssertionError(f"{svg.relative_to(ROOT)} contains wrong repository owner in footer")
        for token in ("https://github.com/bluetape4k/bluetape4k-image", "project: bluetape4k-image", "module:"):
            if token not in content:
                raise AssertionError(f"{svg.relative_to(ROOT)} missing footer token {token}")
        for css_class in ("panel-title", "layer-title", "section"):
            match = re.search(rf"\.{css_class}{{[^}}]*font-size:(\d+(?:\.\d+)?)px", content)
            if match and float(match.group(1)) < 16:
                raise AssertionError(
                    f"{svg.relative_to(ROOT)} has too small {css_class} font: {match.group(1)}px"
                )
        checked, _ = validate_visual_geometry(svg)
        visual_text_checks += checked
        layer_gap_checks += validate_layer_spacing(svg)
        headers, labels = validate_sequence_geometry(svg)
        sequence_headers += headers
        sequence_labels += labels
    lines.append(
        f"SVG/PNG assets: finalSvg={len(svgs)} xml=pass png=pass fonts=pass markerSize=pass evidenceText=pass fireworksStyle=pass footer=pass textFit={visual_text_checks} routeCrossings=0 layerGaps={layer_gap_checks} sequenceHeaders={sequence_headers} sequenceLabels={sequence_labels}"
    )


def validate_graphviz_removed(lines: list[str]) -> None:
    leftovers = [
        path
        for path in DIAGRAM_DIR.glob("*")
        if "-graphviz" in path.stem or path.suffix in {".dot", ".plain"}
    ]
    if leftovers:
        raise AssertionError(
            "Graphviz artifacts remain:\n" + "\n".join(str(path.relative_to(ROOT)) for path in leftovers)
        )
    lines.append("Graphviz artifacts: removed=pass")


def validate_charts(lines: list[str]) -> None:
    charts = sorted(CHART_DIR.glob("*.svg"))
    for svg in charts:
        content = svg.read_text(encoding="utf-8")
        if "Source:" not in content or "Unit:" not in content:
            raise AssertionError(f"{svg.relative_to(ROOT)} missing chart source/unit evidence")
        if not ("lower is better" in content or "higher is better" in content):
            raise AssertionError(f"{svg.relative_to(ROOT)} missing chart direction")
    lines.append(f"Charts: checked={len(charts)} source=pass unit=pass direction=pass")


def validate_legacy_removed(lines: list[str]) -> None:
    legacy = [path for path in (ROOT / "docs").glob("assets/**") if path.is_file()]
    legacy.extend(path for path in (ROOT / "images-ocr" / "docs").glob("assets/**") if path.is_file())
    if legacy:
        raise AssertionError("legacy asset files remain:\n" + "\n".join(str(path.relative_to(ROOT)) for path in legacy))
    lines.append("Legacy asset locations: docs/assets=0 images-ocr/docs/assets=0")


def main() -> int:
    lines: list[str] = []
    validate_readme_links(lines)
    validate_svg_png(lines)
    validate_graphviz_removed(lines)
    validate_charts(lines)
    validate_legacy_removed(lines)
    SUMMARY.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print("\n".join(lines))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
