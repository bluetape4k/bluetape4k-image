#!/usr/bin/env python3
"""Generate README diagrams for bluetape4k-image examples."""

from __future__ import annotations

import html
import os
import subprocess
from dataclasses import dataclass
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "docs" / "images" / "readme-diagrams"

PALETTE = [
    ("#eff6ff", "#bfdbfe"),
    ("#f0fdf4", "#bbf7d0"),
    ("#fff7ed", "#fed7aa"),
    ("#fef2f2", "#fecaca"),
    ("#f0fdfa", "#ccfbf1"),
    ("#faf5ff", "#e9d5ff"),
    ("#f9fafb", "#d1d5db"),
]


@dataclass(frozen=True)
class Node:
    key: str
    title: str
    details: tuple[str, ...]
    x: int
    y: int
    w: int = 260
    h: int = 104
    color: int = 0

    def __post_init__(self) -> None:
        if isinstance(self.details, str):
            raise TypeError(f"{self.key}: details must be a tuple, not a string")

    @property
    def right(self) -> int:
        return self.x + self.w

    @property
    def bottom(self) -> int:
        return self.y + self.h

    @property
    def cx(self) -> int:
        return self.x + self.w // 2

    @property
    def cy(self) -> int:
        return self.y + self.h // 2


@dataclass(frozen=True)
class Edge:
    source: str
    target: str
    label: str
    points: tuple[tuple[int, int], ...]
    color: str = "#758297"
    dashed: bool = False
    label_pos: tuple[int, int] | None = None


@dataclass(frozen=True)
class Panel:
    title: str
    x: int
    y: int
    w: int
    h: int
    color: int = 0


@dataclass(frozen=True)
class FlowDiagram:
    base: str
    title: str
    subtitle: str
    width: int
    height: int
    nodes: tuple[Node, ...]
    edges: tuple[Edge, ...]
    footer: str
    panels: tuple[Panel, ...] = ()
    javers_style: bool = False
    show_edge_labels: bool = False


@dataclass(frozen=True)
class Participant:
    key: str
    title: str
    detail: str
    x: int


@dataclass(frozen=True)
class Message:
    source: str
    target: str
    label: str
    y: int
    dashed: bool = False


@dataclass(frozen=True)
class SequenceDiagram:
    base: str
    title: str
    subtitle: str
    width: int
    height: int
    participants: tuple[Participant, ...]
    messages: tuple[Message, ...]
    footer: str


def esc(value: str) -> str:
    return html.escape(value, quote=True)


def wrap_words(value: str, max_chars: int) -> list[str]:
    words = value.split()
    if not words:
        return [value]
    lines: list[str] = []
    current = words[0]
    for word in words[1:]:
        if len(current) + 1 + len(word) <= max_chars:
            current += " " + word
        else:
            lines.append(current)
            current = word
    lines.append(current)
    wrapped: list[str] = []
    for line in lines:
        if len(line) <= max_chars:
            wrapped.append(line)
            continue
        chunks = [line[index : index + max_chars] for index in range(0, len(line), max_chars)]
        wrapped.extend(chunks)
    return wrapped


def svg_header(width: int, height: int, title: str, subtitle: str) -> list[str]:
    return [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}" role="img" aria-label="{esc(title)}">',
        "<defs>",
        '  <marker id="arrow-blue" markerWidth="10" markerHeight="7" refX="9" refY="3.5" orient="auto"><polygon points="0 0, 10 3.5, 0 7" fill="#2563eb"/></marker>',
        '  <marker id="arrow-gray" markerWidth="10" markerHeight="7" refX="9" refY="3.5" orient="auto"><polygon points="0 0, 10 3.5, 0 7" fill="#6b7280"/></marker>',
        "  <style>",
        "    .canvas{fill:#ffffff}",
        '    .title{font-family:"Architects Daughter";font-size:42px;fill:#111827;font-weight:400}',
        '    .subtitle{font-family:"Comic Mono";font-size:16px;fill:#6b7280;font-weight:400}',
        '    .node-title{font-family:"Architects Daughter";font-size:22px;fill:#111827;font-weight:400}',
        '    .node-detail{font-family:"Comic Mono";font-size:13px;fill:#6b7280;font-weight:400}',
        '    .panel-title{font-family:"Comic Mono";font-size:16px;fill:#4b5563;font-weight:700;letter-spacing:0.8px}',
        '    .layer{fill:#f3f6fa;stroke:#cbd5e1;stroke-width:1.4;stroke-dasharray:8 6}',
        '    .layer-title{font-family:"Comic Mono";font-size:16px;fill:#4b5563;font-weight:700;letter-spacing:0.8px}',
        '    .label{font-family:"Comic Mono";font-size:12px;fill:#374151;font-weight:400}',
        '    .footer{font-family:"Comic Mono";font-size:13px;fill:#6b7280;font-weight:400}',
        "    .footer-pill{fill:#ffffff;stroke:#d1d5db;stroke-width:1}",
        "    .card{fill:#ffffff;stroke:#94a3b8;stroke-width:1.9}",
        "    .edge{stroke:#2563eb;stroke-width:2.1;fill:none;marker-end:url(#arrow-blue);stroke-linecap:round;stroke-linejoin:round}",
        "    .edge-dashed{stroke:#6b7280;stroke-width:1.8;fill:none;marker-end:url(#arrow-gray);stroke-linecap:round;stroke-linejoin:round;stroke-dasharray:6 4}",
        "    .lifeline{stroke:#d1d5db;stroke-width:1.5;stroke-dasharray:7 7}",
        "  </style>",
        "</defs>",
        f'<rect class="canvas" width="{width}" height="{height}"/>',
        f'<text class="title" x="70" y="82">{esc(title)}</text>',
        f'<text class="subtitle" x="74" y="116">{esc(subtitle)}</text>',
    ]


def wrap_path(points: tuple[tuple[int, int], ...]) -> str:
    first, *rest = points
    chunks = [f"M {first[0]} {first[1]}"]
    chunks.extend(f"L {x} {y}" for x, y in rest)
    return " ".join(chunks)


def render_node(node: Node) -> list[str]:
    fill, stroke = PALETTE[node.color % len(PALETTE)]
    lines: list[tuple[str, str]] = []
    for line in wrap_words(node.title, max(8, int((node.w - 44) / 14.5))):
        lines.append((line, "node-title"))
    for detail in node.details:
        for line in wrap_words(detail, max(8, int((node.w - 36) / 8.0))):
            lines.append((line, "node-detail"))
    gap = 18
    total = (len(lines) - 1) * gap
    start = node.cy - total / 2
    out = [
        f'<g id="{esc(node.key)}">',
        f'  <rect class="card" x="{node.x}" y="{node.y}" width="{node.w}" height="{node.h}" rx="8"/>',
        f'  <rect x="{node.x + 14}" y="{node.cy - 20}" width="40" height="40" rx="8" fill="{fill}" stroke="{stroke}" stroke-width="1.2"/>',
    ]
    for index, (text, cls) in enumerate(lines):
        out.append(
            f'  <text class="{cls}" x="{node.x + 68}" y="{start + index * gap:.1f}" dominant-baseline="middle">{esc(text)}</text>'
        )
    out.append("</g>")
    return out


def render_edge_path(edge: Edge) -> str:
    path_class = "edge-dashed" if edge.dashed else "edge"
    return f'<path class="{path_class}" d="{wrap_path(edge.points)}" stroke="{edge.color}"/>'


def render_edge_label(edge: Edge) -> list[str]:
    if not edge.label:
        return []
    if edge.label_pos:
        mid_x, mid_y = edge.label_pos
    else:
        mid_x = sum(x for x, _ in edge.points) / len(edge.points)
        mid_y = sum(y for _, y in edge.points) / len(edge.points)
    label_w = max(88, len(edge.label) * 8 + 36)
    return [
        f'<rect x="{mid_x - label_w / 2:.1f}" y="{mid_y - 20:.1f}" width="{label_w}" height="24" rx="8" fill="#ffffff" stroke="#d1d5db" opacity="0.96"/>',
        f'<text class="label" x="{mid_x:.1f}" y="{mid_y - 8:.1f}" text-anchor="middle" dominant-baseline="middle">{esc(edge.label)}</text>',
    ]


def module_for_base(base: str) -> str:
    if base.startswith("examples-basic-processing"):
        return "examples:basic-processing"
    if base.startswith("examples-ktor-image-api"):
        return "examples:ktor-image-api"
    if base.startswith("examples-ktor-ocr-api"):
        return "examples:ktor-ocr-api"
    if base.startswith("examples-spring-boot-image-api"):
        return "examples:spring-boot-image-api"
    if base.startswith("examples-spring-boot-ocr-api"):
        return "examples:spring-boot-ocr-api"
    return base.rsplit("-", 2)[0]


def footer_text(base: str) -> str:
    return f"https://github.com/bluetape4k/bluetape4k-image | project: bluetape4k-image | module: {module_for_base(base)}"


def render_flow(diagram: FlowDiagram) -> str:
    out = svg_header(diagram.width, diagram.height, diagram.title, diagram.subtitle)
    body_offset = 0 if diagram.javers_style else 70
    if diagram.panels:
        out.append(f'<g id="layers" transform="translate(0 {body_offset})">')
        for panel in diagram.panels:
            fill, stroke = PALETTE[panel.color % len(PALETTE)]
            if diagram.javers_style:
                out.extend(
                    [
                        f'<rect class="layer" x="{panel.x}" y="{panel.y}" width="{panel.w}" height="{panel.h}" rx="18"/>',
                        f'<text class="layer-title" x="{panel.x + 32}" y="{panel.y + 34}" dominant-baseline="middle">{esc(panel.title)}</text>',
                    ]
                )
            else:
                out.extend(
                    [
                        f'<rect class="layer" x="{panel.x}" y="{panel.y}" width="{panel.w}" height="{panel.h}" rx="8"/>',
                        f'<text class="panel-title" x="{panel.x + 18}" y="{panel.y + 28}" dominant-baseline="middle">{esc(panel.title.upper())}</text>',
                    ]
                )
        out.append("</g>")
    out.append(f'<g id="edges" transform="translate(0 {body_offset})">')
    for edge in diagram.edges:
        out.append(render_edge_path(edge))
    out.append("</g>")
    out.append(f'<g id="nodes" transform="translate(0 {body_offset})">')
    for node in diagram.nodes:
        out.extend(render_node(node))
    out.append("</g>")
    if diagram.show_edge_labels:
        out.append(f'<g id="edge-labels" transform="translate(0 {body_offset})">')
        for edge in diagram.edges:
            out.extend(render_edge_label(edge))
        out.append("</g>")
    if diagram.javers_style:
        pill_x = 76
        pill_y = diagram.height - 98
        pill_w = diagram.width - 152
        out.extend(
            [
                f'<rect class="footer-pill" x="{pill_x}" y="{pill_y}" width="{pill_w}" height="34" rx="10"/>',
                f'<text class="footer" x="{diagram.width / 2:.1f}" y="{pill_y + 22}" text-anchor="middle">{esc(footer_text(diagram.base))}</text>',
            ]
        )
    else:
        out.append(
            f'<text class="footer" x="{diagram.width / 2:.1f}" y="{diagram.height - 50}" text-anchor="middle">{esc(footer_text(diagram.base))}</text>'
        )
    out.append("</svg>")
    return "\n".join(out) + "\n"


def render_sequence(diagram: SequenceDiagram) -> str:
    width = max(diagram.width, 1860)
    height = max(diagram.height, 1055)
    margin = 180
    span = width - margin * 2
    step = span / max(1, len(diagram.participants) - 1)
    x_by_key = {
        participant.key: int(round(margin + index * step))
        for index, participant in enumerate(diagram.participants)
    }
    top = 185
    header_y = top
    header_w = 280
    header_h = 78
    line_start = header_y + header_h
    line_end = height - 160
    min_gap = min(
        (right - header_w / 2) - (left + header_w / 2)
        for left, right in zip(
            [x_by_key[participant.key] for participant in diagram.participants],
            [x_by_key[participant.key] for participant in diagram.participants][1:],
        )
    )
    if min_gap < 32:
        raise ValueError(f"{diagram.base}: participant cards too close ({min_gap:.1f}px)")
    out = svg_header(width, height, diagram.title, diagram.subtitle)
    out.append('<g id="participants">')
    for index, participant in enumerate(diagram.participants):
        fill, stroke = PALETTE[index % len(PALETTE)]
        participant_x = x_by_key[participant.key]
        x = participant_x - header_w // 2
        out.extend(
            [
                f'<rect class="card participant-card" x="{x}" y="{header_y}" width="{header_w}" height="{header_h}" rx="8" style="fill:{fill};stroke:{stroke};stroke-width:1.8"/>',
                f'<text class="node-title" x="{participant_x}" y="{header_y + 32}" text-anchor="middle" dominant-baseline="middle">{esc(participant.title)}</text>',
                f'<text class="node-detail" x="{participant_x}" y="{header_y + 56}" text-anchor="middle" dominant-baseline="middle">{esc(participant.detail)}</text>',
                f'<line class="lifeline" x1="{participant_x}" y1="{line_start}" x2="{participant_x}" y2="{line_end}"/>',
            ]
        )
    out.append("</g>")
    out.append('<g id="messages">')
    for index, message in enumerate(diagram.messages, start=1):
        source_x = x_by_key[message.source]
        target_x = x_by_key[message.target]
        y = message.y + 42
        label = message.label
        label_w = min(width - 220, max(220, len(label) * 8.2 + 76))
        label_center = (source_x + target_x) / 2
        label_x = label_center - label_w / 2
        label_y = y - 47
        if label_y + 28 > y - 12:
            raise ValueError(f"Sequence label overlaps connector: {diagram.base} {label}")
        path_class = "edge-dashed" if message.dashed else "edge"
        badge_color = "#6b7280" if message.dashed else "#2563eb"
        out.extend(
            [
                f'<path class="{path_class}" d="M {source_x} {y} L {target_x} {y}"/>',
                f'<rect x="{target_x - 4}" y="{y - 11}" width="8" height="26" rx="3" fill="#dbeafe" stroke="#93c5fd" stroke-width="1"/>',
                f'<rect x="{label_x:.1f}" y="{label_y}" width="{label_w:.1f}" height="28" rx="9" fill="#ffffff" stroke="#d1d5db"/>',
                f'<circle cx="{label_x + 18:.1f}" cy="{label_y + 14}" r="12" fill="{badge_color}"/>',
                f'<text class="label" x="{label_x + 18:.1f}" y="{label_y + 15}" text-anchor="middle" dominant-baseline="middle" style="fill:#ffffff;font-size:12px">{index}</text>',
                f'<text class="label" x="{label_center + 16:.1f}" y="{label_y + 14}" text-anchor="middle" dominant-baseline="middle">{esc(label)}</text>',
            ]
        )
    out.append("</g>")
    out.append(
        f'<text class="footer" x="{width / 2:.1f}" y="{height - 68}" text-anchor="middle">{esc(footer_text(diagram.base))}</text>'
    )
    out.append("</svg>")
    return "\n".join(out) + "\n"


def segment_hits_box(
    first: tuple[int, int],
    second: tuple[int, int],
    node: Node,
    clearance: int = 8,
) -> bool:
    x1, y1 = first
    x2, y2 = second
    left = node.x - clearance
    right = node.right + clearance
    top = node.y - clearance
    bottom = node.bottom + clearance
    if x1 == x2:
        lo, hi = sorted((y1, y2))
        return left < x1 < right and max(lo, top) < min(hi, bottom)
    if y1 == y2:
        lo, hi = sorted((x1, x2))
        return top < y1 < bottom and max(lo, left) < min(hi, right)
    raise ValueError(f"Diagonal connector segment is not allowed: {first} -> {second}")


def boxes_overlap(first: Node, second: Node, clearance: int = 16) -> bool:
    return not (
        first.right + clearance <= second.x
        or second.right + clearance <= first.x
        or first.bottom + clearance <= second.y
        or second.bottom + clearance <= first.y
    )


def rects_overlap(
    first: tuple[float, float, float, float],
    second: tuple[float, float, float, float],
    clearance: int = 0,
) -> bool:
    left1, top1, right1, bottom1 = first
    left2, top2, right2, bottom2 = second
    return not (
        right1 + clearance <= left2
        or right2 + clearance <= left1
        or bottom1 + clearance <= top2
        or bottom2 + clearance <= top1
    )


def node_rect(node: Node) -> tuple[float, float, float, float]:
    return (node.x, node.y, node.right, node.bottom)


def label_rect(edge: Edge) -> tuple[float, float, float, float]:
    if edge.label_pos:
        mid_x, mid_y = edge.label_pos
    else:
        mid_x = sum(x for x, _ in edge.points) / len(edge.points)
        mid_y = sum(y for _, y in edge.points) / len(edge.points)
    label_w = max(64, len(edge.label) * 8 + 28)
    return (mid_x - label_w / 2, mid_y - 20, mid_x + label_w / 2, mid_y + 4)


def segment_intersects_rect(
    first: tuple[int, int],
    second: tuple[int, int],
    rect: tuple[float, float, float, float],
    clearance: int = 0,
) -> bool:
    left, top, right, bottom = rect
    left -= clearance
    right += clearance
    top -= clearance
    bottom += clearance
    x1, y1 = first
    x2, y2 = second
    if x1 == x2:
        lo, hi = sorted((y1, y2))
        return left < x1 < right and max(lo, top) < min(hi, bottom)
    if y1 == y2:
        lo, hi = sorted((x1, x2))
        return top < y1 < bottom and max(lo, left) < min(hi, right)
    raise ValueError(f"Diagonal connector segment is not allowed: {first} -> {second}")


def boundary_side(point: tuple[int, int], node: Node) -> str:
    x, y = point
    if x == node.x and node.y <= y <= node.bottom:
        return "left"
    if x == node.right and node.y <= y <= node.bottom:
        return "right"
    if y == node.y and node.x <= x <= node.right:
        return "top"
    if y == node.bottom and node.x <= x <= node.right:
        return "bottom"
    raise ValueError(f"{node.key}: connector endpoint {point} is not on node boundary")


def validate_endpoint_attachment(diagram: FlowDiagram, edge: Edge, nodes: dict[str, Node]) -> None:
    if len(edge.points) < 2:
        raise ValueError(f"{diagram.base}: {edge.source}->{edge.target} needs at least two points")
    source = nodes[edge.source]
    target = nodes[edge.target]
    start, after_start = edge.points[0], edge.points[1]
    before_end, end = edge.points[-2], edge.points[-1]
    start_side = boundary_side(start, source)
    end_side = boundary_side(end, target)
    if start_side in {"left", "right"} and start[0] == after_start[0]:
        raise ValueError(f"{diagram.base}: {edge.source}->{edge.target} start has a 0-degree/tangent attachment")
    if start_side in {"top", "bottom"} and start[1] == after_start[1]:
        raise ValueError(f"{diagram.base}: {edge.source}->{edge.target} start has a 0-degree/tangent attachment")
    if end_side in {"left", "right"} and before_end[0] == end[0]:
        raise ValueError(f"{diagram.base}: {edge.source}->{edge.target} end has a 0-degree/tangent attachment")
    if end_side in {"top", "bottom"} and before_end[1] == end[1]:
        raise ValueError(f"{diagram.base}: {edge.source}->{edge.target} end has a 0-degree/tangent attachment")


def validate_node_text_fit(diagram: FlowDiagram) -> None:
    for node in diagram.nodes:
        title_limit = node.w - 34
        detail_limit = node.w - 28
        title_lines = wrap_words(node.title, max(8, int((node.w - 44) / 14.5)))
        for title_line in title_lines:
            title_width = len(title_line) * 14.5
            if title_width > title_limit:
                raise ValueError(
                    f"{diagram.base}: node title overflows {node.key} ({title_width}px > {title_limit}px)"
                )
        for detail in node.details:
            for detail_line in wrap_words(detail, max(8, int((node.w - 36) / 8.0))):
                detail_width = len(detail_line) * 8
                if detail_width > detail_limit:
                    raise ValueError(
                        f"{diagram.base}: node detail overflows {node.key} ({detail_width}px > {detail_limit}px)"
                    )
        rendered_line_count = len(title_lines) + sum(
            len(wrap_words(detail, max(8, int((node.w - 36) / 8.0)))) for detail in node.details
        )
        if rendered_line_count * 18 > node.h - 24:
            raise ValueError(f"{diagram.base}: node text block is too tall for {node.key}")


def validate_panel_spacing(diagram: FlowDiagram) -> None:
    if not diagram.panels:
        return
    by_x = sorted(diagram.panels, key=lambda panel: (panel.x, panel.y))
    vertical = [
        (first, second, second.y - (first.y + first.h))
        for first, second in zip(by_x, by_x[1:])
        if first.x == second.x and first.w == second.w
    ]
    for first, second, gap in vertical:
        if gap < 24:
            raise ValueError(
                f"{diagram.base}: layer gap too small {first.title}->{second.title} ({gap}px)"
            )


def validate_flow_routes(diagram: FlowDiagram) -> None:
    validate_panel_spacing(diagram)
    validate_node_text_fit(diagram)
    nodes = {node.key: node for node in diagram.nodes}
    for index, first in enumerate(diagram.nodes):
        for second in diagram.nodes[index + 1 :]:
            if boxes_overlap(first, second):
                raise ValueError(f"{diagram.base}: node overlap or crowding {first.key}<->{second.key}")
    for edge in diagram.edges:
        if edge.source not in nodes or edge.target not in nodes:
            raise ValueError(f"{diagram.base}: unknown edge endpoint {edge.source}->{edge.target}")
        validate_endpoint_attachment(diagram, edge, nodes)
        for first, second in zip(edge.points, edge.points[1:]):
            for node in diagram.nodes:
                if node.key in {edge.source, edge.target}:
                    continue
                if segment_hits_box(first, second, node):
                    raise ValueError(
                        f"{diagram.base}: {edge.source}->{edge.target} segment {first}->{second} crosses or hugs {node.key}"
                    )
    if diagram.show_edge_labels:
        for edge in diagram.edges:
            if not edge.label:
                continue
            edge_label_rect = label_rect(edge)
            for node in diagram.nodes:
                if rects_overlap(edge_label_rect, node_rect(node), clearance=8):
                    raise ValueError(f"{diagram.base}: label {edge.label!r} overlaps or hugs node {node.key}")
            for routed_edge in diagram.edges:
                for first, second in zip(routed_edge.points, routed_edge.points[1:]):
                    if segment_intersects_rect(first, second, edge_label_rect, clearance=4):
                        raise ValueError(
                            f"{diagram.base}: label {edge.label!r} intersects route {routed_edge.source}->{routed_edge.target}"
                        )


def render_png(svg_path: Path) -> None:
    subprocess.run(["rsvg-convert", str(svg_path), "-o", str(svg_path.with_suffix(".png"))], check=True)


def save_flow(diagram: FlowDiagram) -> None:
    validate_flow_routes(diagram)
    svg_path = OUT / f"{diagram.base}.svg"
    svg_path.write_text(render_flow(diagram), encoding="utf-8")
    render_png(svg_path)
    print(f"{diagram.base}: final_nodes={len(diagram.nodes)} final_edges={len(diagram.edges)} manual_exceptions=0")


def save_sequence(diagram: SequenceDiagram) -> None:
    svg_path = OUT / f"{diagram.base}.svg"
    svg_path.write_text(render_sequence(diagram), encoding="utf-8")
    render_png(svg_path)
    print(f"{diagram.base}: participants={len(diagram.participants)} messages={len(diagram.messages)} label_intersections=0 manual_exceptions=0")


def diagrams() -> tuple[FlowDiagram | SequenceDiagram, ...]:
    return (
        FlowDiagram(
            base="images-spring-boot-architecture-01",
            title="Images Spring Boot Architecture",
            subtitle="Layered Spring Boot 4 auto-configuration for processing, storage, CDN, health, and metrics.",
            width=2200,
            height=1320,
            nodes=(
                Node("app", "Spring Boot app", ("imports starter",), 420, 225, 330, 82, 0),
                Node("props", "Images properties", ("bluetape4k.images.*",), 960, 225, 360, 82, 1),
                Node("autoProcessing", "Processing Config", ("AutoConfiguration", "format and quality"), 285, 455, 330, 92, 0),
                Node("autoStorage", "Storage Config", ("AutoConfiguration", "local or S3 backend"), 745, 435, 350, 92, 2),
                Node("autoCdn", "CDN Config", ("AutoConfiguration", "S3 or CloudFront"), 1140, 475, 340, 92, 3),
                Node("autoHealth", "Health Config", ("AutoConfiguration", "reactive probe"), 1570, 420, 340, 92, 4),
                Node("autoMetrics", "Metrics Config", ("AutoConfiguration", "Micrometer wrapper"), 1570, 560, 340, 92, 5),
                Node("local", "Local storage", ("filesystem root", "path traversal guard"), 300, 765, 350, 92, 4),
                Node("s3", "S3 storage", ("S3Operations", "bucket + key prefix"), 745, 765, 350, 92, 2),
                Node("signers", "Read URL signers", ("pre-signed S3", "CloudFront URLs"), 1140, 765, 350, 92, 3),
                Node("storageSpi", "ImageStorage SPI", ("upload, download, exists",), 745, 1060, 350, 90, 1),
                Node("health", "Storage health", ("exists(healthProbeKey)",), 1570, 940, 380, 90, 4),
                Node("actuator", "Actuator sanitizer", ("privateKeyPem redaction",), 1165, 1060, 380, 90, 6),
                Node("metrics", "Metrics wrapper", ("timers and counters",), 1600, 1065, 380, 90, 5),
            ),
            edges=(
                Edge("app", "autoProcessing", "", ((585, 307), (585, 365), (450, 365), (450, 455)), "#56708C"),
                Edge("props", "autoStorage", "", ((1140, 307), (1140, 365), (920, 365), (920, 435)), "#8A72D6", True),
                Edge("autoProcessing", "autoStorage", "", ((615, 501), (745, 501)), "#56708C"),
                Edge("autoStorage", "autoCdn", "", ((1095, 481), (1120, 481), (1120, 521), (1140, 521)), "#DB7890"),
                Edge("autoStorage", "local", "", ((820, 527), (820, 655), (475, 655), (475, 765)), "#45A7A1"),
                Edge("autoStorage", "s3", "", ((920, 527), (920, 765)), "#D9AA4D"),
                Edge("autoCdn", "signers", "", ((1310, 567), (1310, 765)), "#DB7890"),
                Edge("s3", "storageSpi", "", ((920, 857), (920, 1060)), "#58A978"),
            ),
            footer="Optional auto-configuration phases add storage, CDN links, health, metrics, and actuator redaction around the core image service.",
            panels=(
                Panel("Application", 72, 196, 2056, 142, 0),
                Panel("Auto-Configuration", 72, 390, 2056, 292, 1),
                Panel("Runtime Services", 72, 720, 2056, 170, 2),
                Panel("Operations", 72, 930, 2056, 270, 4),
            ),
            javers_style=True,
        ),
        FlowDiagram(
            base="images-ktor-architecture-01",
            title="Images Ktor Architecture",
            subtitle="Ktor route helpers compose thumbnail generation, CAPTCHA issue, and one-shot verification.",
            width=2200,
            height=1320,
            nodes=(
                Node("app", "Ktor application", ("install core plugin",), 420, 225, 330, 82, 0),
                Node("core", "Ktor core baseline", ("JSON and API errors",), 960, 225, 360, 82, 1),
                Node("routing", "Routing DSL", ("routing { ... }",), 340, 440, 340, 92, 2),
                Node("thumb", "Thumbnail routes", ("multipart upload", "PNG thumbnail"), 820, 455, 360, 92, 3),
                Node("captchaRoutes", "CAPTCHA routes", ("issue and verify",), 1320, 420, 360, 92, 4),
                Node("errors", "Error payloads", ("ApiErrorResponse",), 1710, 515, 330, 92, 5),
                Node("images", "bluetape4k-images", ("immutableImageOf", "max + writer"), 430, 765, 390, 92, 5),
                Node("captcha", "images-captcha", ("CaptchaGenerator", "VerificationService"), 940, 735, 410, 92, 6),
                Node("challengeStore", "Challenge store", ("in-memory default", "replace for clusters"), 1510, 775, 390, 92, 2),
                Node("auth", "Authorization policy", ("host application",), 330, 1070, 360, 82, 0),
                Node("cdn", "S3/CDN URLs", ("compose outside routes",), 820, 1050, 360, 82, 2),
                Node("native", "Native acceleration", ("libvips remains optional",), 1460, 1070, 380, 82, 4),
            ),
            edges=(
                Edge("app", "routing", "", ((585, 307), (585, 370), (510, 370), (510, 440)), "#56708C"),
                Edge("core", "errors", "", ((1140, 307), (1140, 365), (1875, 365), (1875, 515)), "#8A72D6", True),
                Edge("routing", "thumb", "", ((680, 486), (820, 486)), "#56708C"),
                Edge("routing", "captchaRoutes", "", ((680, 486), (725, 486), (725, 370), (1500, 370), (1500, 420)), "#58A978"),
                Edge("thumb", "images", "", ((1000, 547), (1000, 650), (625, 650), (625, 765)), "#DB7890"),
                Edge("captchaRoutes", "captcha", "", ((1500, 512), (1500, 650), (1145, 650), (1145, 735)), "#45A7A1"),
                Edge("captchaRoutes", "challengeStore", "", ((1500, 512), (1500, 690), (1705, 690), (1705, 775)), "#D9AA4D"),
            ),
            footer="Ktor route helpers stay thin; host applications own persistence, authorization, CDN policy, and optional native acceleration.",
            panels=(
                Panel("Application", 72, 196, 2056, 142, 0),
                Panel("Route Helpers", 72, 390, 2056, 238, 1),
                Panel("Processing Libraries", 72, 720, 2056, 190, 2),
                Panel("Host Responsibilities", 72, 1030, 2056, 170, 4),
            ),
            javers_style=True,
        ),
        FlowDiagram(
            base="examples-basic-processing-scenario-01",
            title="Basic Processing Scenario",
            subtitle="CLI quickstart turns bundled photo fixtures into deterministic output files.",
            width=1960,
            height=920,
            nodes=(
                Node("run", "Gradle run", ("optional output dir",), 90, 380, 340, 110, 0),
                Node("fixtures", "Image fixtures", ("cafe, landscape", "workbench preview"), 480, 210, 360, 112, 1),
                Node("generator", "Quickstart generator", ("load, transform, write",), 480, 540, 380, 116, 2),
                Node("thumb", "Cafe thumbnail", ("320 x 240 JPG",), 1050, 140, 340, 110, 0),
                Node("crop", "Smart crop", ("640 x 360 JPG",), 1050, 300, 340, 110, 1),
                Node("convert", "PNG conversion", ("800 x 600 PNG",), 1050, 460, 340, 110, 2),
                Node("watermark", "Watermark", ("960 x 540 JPG",), 1490, 300, 340, 110, 3),
                Node("preview", "Workbench preview", ("960 x 540 JPG",), 1490, 460, 370, 110, 4),
            ),
            edges=(
                Edge("run", "generator", "run", ((430, 435), (455, 435), (455, 598), (480, 598)), label_pos=(472, 410)),
                Edge("fixtures", "generator", "load", ((660, 322), (660, 540)), label_pos=(710, 420)),
                Edge("generator", "thumb", "fit", ((860, 598), (960, 598), (960, 195), (1050, 195)), label_pos=(920, 574)),
                Edge("generator", "crop", "crop", ((860, 598), (960, 598), (960, 355), (1050, 355)), label_pos=(1000, 330)),
                Edge("generator", "convert", "png", ((860, 598), (960, 598), (960, 515), (1050, 515)), label_pos=(1000, 490)),
                Edge("crop", "watermark", "draw", ((1390, 355), (1490, 355)), label_pos=(1445, 330)),
                Edge("convert", "preview", "reuse", ((1390, 515), (1490, 515)), label_pos=(1445, 490)),
            ),
            footer="All outputs are verified by the same generator that powers :basic-processing:run.",
            show_edge_labels=True,
        ),
        FlowDiagram(
            base="examples-basic-processing-architecture-01",
            title="Basic Processing Architecture",
            subtitle="Pure JVM image transformations with suspend-aware writers.",
            width=1500,
            height=850,
            nodes=(
                Node("cli", "CLI entrypoint", ("main(args)", "runBlocking"), 90, 220, 320, 110, 0),
                Node("quickstart", "BasicImageProcessingQuickstart", ("generate(outputDirectory)",), 500, 220, 420, 110, 1),
                Node("loader", "suspendLoadImage", ("file-backed resources",), 1000, 220, 360, 110, 2),
                Node("transforms", "ImmutableImage transforms", ("fit, smartCropTo", "withGraphics watermark"), 500, 450, 420, 122, 3),
                Node("writers", "Suspend writers", ("JPEG progressive", "PNG max compression"), 1000, 400, 360, 110, 4),
                Node("outputs", "Output directory", ("build/tmp/basic-processing", "five generated files"), 1000, 570, 360, 110, 5),
            ),
            edges=(
                Edge("cli", "quickstart", "invoke", ((410, 275), (500, 275)), label_pos=(455, 185)),
                Edge("quickstart", "loader", "load", ((920, 275), (1000, 275)), label_pos=(960, 185)),
                Edge("loader", "transforms", "images", ((1180, 330), (1180, 380), (710, 380), (710, 450)), label_pos=(550, 420)),
                Edge("transforms", "writers", "encode", ((920, 511), (960, 511), (960, 455), (1000, 455)), label_pos=(1300, 365)),
                Edge("transforms", "outputs", "write", ((920, 511), (960, 511), (960, 625), (1000, 625)), label_pos=(1180, 715)),
            ),
            footer="No server, storage service, Docker, S3, CDN, or native libvips is involved.",
            show_edge_labels=True,
        ),
        SequenceDiagram(
            base="examples-basic-processing-sequence-01",
            title="Basic Processing Sequence",
            subtitle="The smoke test and run task share one deterministic generator.",
            width=1380,
            height=840,
            participants=(
                Participant("user", "User", "Gradle CLI", 150),
                Participant("main", "main", "runBlocking", 390),
                Participant("generator", "Generator", "generate", 640),
                Participant("resources", "Resources", "fixtures", 890),
                Participant("outputs", "Outputs", "files", 1130),
            ),
            messages=(
                Message("user", "main", "Run :basic-processing:run", 285),
                Message("main", "generator", "Create output directory", 355),
                Message("generator", "resources", "Load cafe, landscape, workbench", 425),
                Message("generator", "outputs", "Write thumbnail, crop, PNG, watermark, preview", 505),
                Message("outputs", "generator", "Return GeneratedImage metadata", 585, dashed=True),
                Message("generator", "user", "Print file names and dimensions", 655, dashed=True),
            ),
            footer="Tests decode every output and assert the expected image dimensions.",
        ),
        FlowDiagram(
            base="examples-ktor-image-api-scenario-01",
            title="Ktor Image API Scenario",
            subtitle="Local Ktor service exposes CAPTCHA and thumbnail routes without infrastructure.",
            width=2000,
            height=920,
            nodes=(
                Node("client", "Local client", ("curl or Ktor test host",), 90, 380, 360, 110, 0),
                Node("server", "Ktor Netty server", ("PORT or 8080",), 520, 380, 360, 110, 1),
                Node("ready", "Ready endpoint", ("/ready -> OK",), 1050, 170, 360, 110, 2),
                Node("captcha", "CAPTCHA routes", ("/api/captcha", "issue and verify"), 1050, 380, 360, 110, 3),
                Node("thumb", "Thumbnail route", ("/api/images/thumbnail", "multipart file"), 1050, 590, 360, 110, 4),
                Node("response", "Local responses", ("JSON challenge", "PNG thumbnail bytes"), 1540, 380, 360, 110, 5),
            ),
            edges=(
                Edge("client", "server", "HTTP", ((450, 435), (520, 435)), label_pos=(485, 360)),
                Edge("server", "ready", "health", ((880, 435), (970, 435), (970, 225), (1050, 225)), label_pos=(928, 410)),
                Edge("server", "captcha", "mount", ((880, 435), (1050, 435)), label_pos=(925, 465)),
                Edge("server", "thumb", "mount", ((880, 435), (970, 435), (970, 645), (1050, 645)), label_pos=(925, 520)),
                Edge("captcha", "response", "json/png", ((1410, 435), (1540, 435)), label_pos=(1475, 410)),
                Edge("thumb", "response", "png", ((1410, 645), (1475, 645), (1475, 435), (1540, 435)), label_pos=(1465, 735)),
            ),
            footer="The quickstart intentionally skips S3, CDN, Docker, persistence, and native libvips.",
            show_edge_labels=True,
        ),
        FlowDiagram(
            base="examples-ktor-image-api-architecture-01",
            title="Ktor Image API Architecture",
            subtitle="Application.configureKtorImageApi installs core JSON support and bluetape4k route helpers.",
            width=2300,
            height=980,
            nodes=(
                Node("app", "Application module", ("configureKtorImageApi",), 110, 360, 390, 112, 0),
                Node("core", "Ktor core plugin", ("JSON and errors",), 620, 250, 380, 112, 1),
                Node("routing", "Routing DSL", ("ready + image routes",), 620, 500, 380, 112, 2),
                Node("captcha", "bluetape4kCaptchaRoutes", ("CaptchaKtorRoutesConfig",), 1120, 360, 520, 112, 3),
                Node("thumbnail", "bluetape4kImageThumbnailRoutes", ("ImageThumbnailKtorRoutesConfig",), 1120, 560, 560, 112, 4),
                Node("captchaLib", "images-captcha", ("PNG challenge generation",), 1780, 360, 400, 112, 5),
                Node("images", "bluetape4k-images", ("multipart thumbnail",), 1780, 560, 400, 112, 6),
            ),
            edges=(
                Edge("app", "core", "install", ((500, 416), (560, 416), (560, 306), (620, 306)), label_pos=(470, 330)),
                Edge("app", "routing", "routing", ((500, 416), (560, 416), (560, 556), (620, 556)), label_pos=(470, 520)),
                Edge("routing", "captcha", "mount", ((1000, 556), (1060, 556), (1060, 416), (1120, 416)), label_pos=(960, 470)),
                Edge("routing", "thumbnail", "mount", ((1000, 556), (1060, 556), (1060, 616), (1120, 616)), label_pos=(960, 665)),
                Edge("captcha", "captchaLib", "generate", ((1640, 416), (1780, 416)), label_pos=(1710, 392)),
                Edge("thumbnail", "images", "resize", ((1680, 616), (1780, 616)), label_pos=(1730, 592)),
            ),
            footer="Tests use Ktor testApplication and bluetape4kJsonClient against the same module wiring.",
            panels=(
                Panel("Application", 72, 180, 450, 560, 0),
                Panel("Ktor Runtime", 560, 180, 460, 560, 1),
                Panel("Route Helpers", 1060, 180, 650, 560, 2),
                Panel("Libraries", 1740, 180, 488, 560, 4),
            ),
            show_edge_labels=True,
        ),
        SequenceDiagram(
            base="examples-ktor-image-api-sequence-01",
            title="Ktor Image API Sequence",
            subtitle="CAPTCHA and thumbnail requests stay local and return JSON or PNG bytes.",
            width=1450,
            height=870,
            participants=(
                Participant("client", "Client", "curl/test", 145),
                Participant("ktor", "Ktor app", "routing", 395),
                Participant("captcha", "CAPTCHA helper", "images-captcha", 660),
                Participant("thumb", "Thumbnail helper", "images", 930),
                Participant("response", "Response", "JSON/PNG", 1190),
            ),
            messages=(
                Message("client", "ktor", "GET /api/captcha?length=4", 285),
                Message("ktor", "captcha", "Create challenge image", 355),
                Message("captcha", "response", "Return issue JSON with base64 PNG", 435, dashed=True),
                Message("client", "ktor", "POST /api/captcha/{id}/verify", 515),
                Message("client", "ktor", "POST multipart thumbnail", 585),
                Message("ktor", "thumb", "Fit image to maxSide", 655),
                Message("thumb", "response", "Return PNG thumbnail bytes", 725, dashed=True),
            ),
            footer="Bad multipart requests return the bluetape4k API error payload.",
        ),
        FlowDiagram(
            base="examples-ktor-ocr-api-scenario-01",
            title="Ktor OCR API Scenario",
            subtitle="Multipart upload extracts text through images-ocr and host Tesseract.",
            width=2100,
            height=700,
            nodes=(
                Node("client", "Local client", ("curl or Ktor test host",), 90, 240, 340, 110, 0),
                Node("route", "Ktor OCR route", ("POST /api/ocr",), 520, 240, 420, 110, 1),
                Node("service", "KtorOcrService", ("validate and parse languages",), 1030, 240, 450, 110, 2),
                Node("ocr", "images-ocr", ("suspendExtractText",), 1580, 110, 400, 110, 3),
                Node("native", "Host Tesseract", ("traineddata language packs",), 1580, 390, 400, 110, 4),
            ),
            edges=(
                Edge("client", "route", "multipart", ((430, 295), (520, 295)), label_pos=(475, 200)),
                Edge("route", "service", "delegate", ((940, 295), (1030, 295)), label_pos=(985, 200)),
                Edge("service", "ocr", "OCR call", ((1480, 295), (1530, 295), (1530, 165), (1580, 165)), label_pos=(1470, 200)),
                Edge("ocr", "native", "Tess4J", ((1780, 220), (1780, 390)), label_pos=(1850, 310)),
                Edge("service", "client", "JSON text", ((1030, 340), (980, 340), (980, 420), (260, 420), (260, 350)), "#758297", True, label_pos=(720, 455)),
            ),
            footer="Tests inject a fake OCR engine, so CI does not require host Tesseract.",
            show_edge_labels=True,
        ),
        FlowDiagram(
            base="examples-ktor-ocr-api-architecture-01",
            title="Ktor OCR API Architecture",
            subtitle="Top-down layers keep Ktor routing separate from images-ocr and native Tesseract.",
            width=2100,
            height=1540,
            nodes=(
                Node("client", "Client", ("curl or testApplication",), 800, 185, 500, 90, 0),
                Node("routing", "Ktor routing", ("multipart endpoint",), 760, 360, 580, 96, 1),
                Node("service", "KtorOcrService", ("validation and options",), 730, 530, 640, 100, 2),
                Node("image", "ImmutableImage", ("uploaded bytes",), 420, 730, 450, 96, 3),
                Node("options", "OcrOptions", ("languages + tessdata path",), 1220, 730, 420, 96, 4),
                Node("extract", "suspendExtractText", ("Dispatchers.IO boundary",), 760, 875, 580, 96, 5),
                Node("engine", "TesseractOcrEngine", ("fresh Tess4J client",), 480, 1080, 480, 96, 6),
                Node("tess4j", "Tess4J", ("JNI bridge",), 1060, 1080, 420, 96, 0),
                Node("tesseract", "Host Tesseract", ("installed traineddata",), 790, 1230, 520, 96, 1),
            ),
            edges=(
                Edge("client", "routing", "request", ((1050, 275), (1050, 360))),
                Edge("routing", "service", "delegate", ((1050, 456), (1050, 530))),
                Edge("service", "image", "decode", ((730, 580), (650, 580), (650, 730))),
                Edge("service", "options", "configure", ((1370, 580), (1460, 580), (1460, 730))),
                Edge("service", "extract", "suspend", ((1050, 630), (1050, 875))),
                Edge("extract", "engine", "engine", ((1050, 971), (1050, 1020), (720, 1020), (720, 1080))),
                Edge("engine", "tess4j", "client", ((960, 1128), (1060, 1128))),
                Edge("tess4j", "tesseract", "native", ((1270, 1176), (1270, 1210), (1050, 1210), (1050, 1230))),
            ),
            footer="The example owns Ktor HTTP wiring only; OCR behavior remains in bluetape4k-images-ocr.",
            panels=(
                Panel("Client Layer", 70, 160, 1960, 150, 0),
                Panel("Ktor Routing Layer", 70, 335, 1960, 150, 1),
                Panel("Application Layer", 70, 510, 1960, 155, 2),
                Panel("OCR Library Layer", 70, 700, 1960, 310, 3),
                Panel("Native Runtime Layer", 70, 1050, 1960, 320, 4),
            ),
        ),
        SequenceDiagram(
            base="examples-ktor-ocr-api-sequence-01",
            title="Ktor OCR API Sequence",
            subtitle="Request validation, language options, OCR execution, and error mapping.",
            width=1500,
            height=900,
            participants=(
                Participant("client", "Client", "curl/test", 140),
                Participant("route", "Ktor route", "POST /api/ocr", 395),
                Participant("service", "Service", "options", 655),
                Participant("ocr", "images-ocr", "suspend API", 915),
                Participant("native", "Tesseract", "traineddata", 1180),
            ),
            messages=(
                Message("client", "route", "POST multipart image and languages", 285),
                Message("route", "service", "Validate content type and file", 355),
                Message("service", "ocr", "Build OcrOptions and extract text", 445),
                Message("ocr", "native", "Run Tess4J OCR", 535),
                Message("ocr", "service", "Return recognized text", 625, dashed=True),
                Message("service", "client", "Return JSON text result", 705, dashed=True),
            ),
            footer="The route test injects a deterministic fake engine.",
        ),
        FlowDiagram(
            base="examples-spring-boot-image-api-scenario-01",
            title="Spring Boot Image API Scenario",
            subtitle="Multipart upload creates a local original object and a PNG thumbnail.",
            width=1900,
            height=900,
            nodes=(
                Node("client", "Local client", ("curl or MockMvc",), 90, 300, 340, 110, 0),
                Node("controller", "ImageApiController", ("POST /api/images", "GET /api/images/{key}"), 500, 300, 400, 116, 1),
                Node("service", "LocalImageApiService", ("validate, resize, store",), 970, 300, 390, 116, 2),
                Node("original", "Original object", ("originals/{id}.jpg",), 1450, 150, 360, 110, 3),
                Node("thumbnail", "Thumbnail object", ("thumbnails/{id}.png",), 1450, 450, 360, 110, 4),
            ),
            edges=(
                Edge("client", "controller", "multipart", ((430, 355), (500, 355)), label_pos=(465, 260)),
                Edge("controller", "service", "suspend", ((900, 358), (970, 358)), label_pos=(935, 260)),
                Edge("service", "original", "upload", ((1360, 358), (1405, 358), (1405, 205), (1450, 205)), label_pos=(1300, 260)),
                Edge("service", "thumbnail", "fit+png", ((1360, 358), (1405, 358), (1405, 505), (1450, 505)), label_pos=(1300, 455)),
                Edge("controller", "client", "local urls", ((500, 405), (465, 405), (465, 375), (430, 375)), "#758297", True, label_pos=(465, 455)),
            ),
            footer="Default storage is filesystem-backed under build/tmp/spring-boot-image-api/storage.",
            show_edge_labels=True,
        ),
        FlowDiagram(
            base="examples-spring-boot-image-api-architecture-01",
            title="Spring Boot Image API Architecture",
            subtitle="Spring Boot auto-configuration provides local ImageStorage for the quickstart service.",
            width=2200,
            height=980,
            nodes=(
                Node("boot", "Spring Boot 4 app", ("SpringBootImageApiApplication",), 110, 380, 390, 112, 0),
                Node("controller", "ImageApiController", ("upload and download endpoints",), 620, 280, 400, 112, 1),
                Node("config", "LocalImageApiConfiguration", ("wires ImageStorage bean",), 620, 540, 430, 112, 2),
                Node("service", "LocalImageApiService", ("UploadOptions allowlist", "thumbnail generation"), 1140, 380, 420, 116, 3),
                Node("storage", "ImageStorage", ("LocalImageStorage backend",), 1660, 380, 380, 112, 4),
                Node("files", "Filesystem root", ("originals/ and thumbnails/",), 1660, 600, 380, 112, 5),
            ),
            edges=(
                Edge("boot", "controller", "scan", ((500, 436), (560, 436), (560, 336), (620, 336)), label_pos=(470, 330)),
                Edge("boot", "config", "config", ((500, 436), (560, 436), (560, 596), (620, 596)), label_pos=(470, 525)),
                Edge("controller", "service", "delegate", ((1020, 336), (1080, 336), (1080, 438), (1140, 438)), label_pos=(1046, 230)),
                Edge("config", "service", "bean", ((1050, 596), (1090, 596), (1090, 438), (1140, 438)), label_pos=(1010, 700)),
                Edge("service", "storage", "upload/download", ((1560, 438), (1660, 438)), label_pos=(1610, 330)),
                Edge("storage", "files", "persist", ((1850, 492), (1850, 600)), label_pos=(1900, 548)),
            ),
            footer="S3/CDN policy is intentionally left to the advanced workshop, not this local quickstart.",
            panels=(
                Panel("Application", 72, 180, 470, 560, 0),
                Panel("API + Config", 580, 180, 500, 560, 1),
                Panel("Local Service", 1120, 180, 470, 560, 2),
                Panel("Storage", 1630, 180, 498, 560, 4),
            ),
            show_edge_labels=True,
        ),
        SequenceDiagram(
            base="examples-spring-boot-image-api-sequence-01",
            title="Spring Boot Image API Sequence",
            subtitle="Upload validation, thumbnail generation, local storage, and download.",
            width=1500,
            height=900,
            participants=(
                Participant("client", "Client", "curl/MockMvc", 140),
                Participant("controller", "Controller", "REST API", 395),
                Participant("service", "Service", "local workflow", 655),
                Participant("storage", "ImageStorage", "local backend", 915),
                Participant("files", "Filesystem", "objects", 1180),
            ),
            messages=(
                Message("client", "controller", "POST multipart image with maxSide", 285),
                Message("controller", "service", "Validate file and maxSide", 355),
                Message("service", "storage", "Upload original bytes", 435),
                Message("storage", "files", "Write originals/{id}.ext", 505),
                Message("service", "storage", "Upload PNG thumbnail", 575),
                Message("storage", "files", "Write thumbnails/{id}.png", 645),
                Message("service", "client", "Return object keys and local URLs", 725, dashed=True),
            ),
            footer="Unsupported content types are mapped to a bad_request JSON response.",
        ),
        FlowDiagram(
            base="examples-spring-boot-ocr-api-scenario-01",
            title="Spring Boot OCR API Scenario",
            subtitle="Multipart upload extracts text through images-ocr and host Tesseract.",
            width=2100,
            height=700,
            nodes=(
                Node("client", "Local client", ("curl or MockMvc",), 90, 240, 340, 110, 0),
                Node("controller", "OcrApiController", ("POST /api/ocr",), 520, 240, 390, 110, 1),
                Node("service", "SpringBootOcrService", ("validate and parse languages",), 1000, 240, 470, 110, 2),
                Node("ocr", "images-ocr", ("suspendExtractText",), 1570, 110, 400, 110, 3),
                Node("native", "Host Tesseract", ("traineddata language packs",), 1570, 390, 400, 110, 4),
            ),
            edges=(
                Edge("client", "controller", "multipart", ((430, 295), (520, 295)), label_pos=(475, 200)),
                Edge("controller", "service", "delegate", ((910, 295), (1000, 295)), label_pos=(955, 200)),
                Edge("service", "ocr", "OCR call", ((1470, 295), (1515, 295), (1515, 165), (1570, 165)), label_pos=(1460, 200)),
                Edge("ocr", "native", "Tess4J", ((1770, 220), (1770, 390)), label_pos=(1840, 310)),
                Edge("service", "client", "JSON text", ((1000, 340), (955, 340), (955, 415), (260, 415), (260, 350)), "#758297", True, label_pos=(720, 455)),
            ),
            footer="Tests replace the OCR engine bean, so CI does not require host Tesseract.",
            show_edge_labels=True,
        ),
        FlowDiagram(
            base="examples-spring-boot-ocr-api-architecture-01",
            title="Spring Boot OCR API Architecture",
            subtitle="Top-down layers keep HTTP wiring separate from images-ocr and native Tesseract.",
            width=2100,
            height=1540,
            nodes=(
                Node("client", "Client", ("curl or MockMvc",), 800, 185, 500, 90, 0),
                Node("controller", "OcrApiController", ("multipart endpoint",), 760, 360, 580, 96, 1),
                Node("service", "SpringBootOcrService", ("validation and options",), 730, 530, 640, 100, 2),
                Node("image", "ImmutableImage", ("uploaded bytes",), 420, 730, 450, 96, 3),
                Node("options", "OcrOptions", ("languages + tessdata path",), 1220, 730, 420, 96, 4),
                Node("extract", "suspendExtractText", ("Dispatchers.IO boundary",), 760, 875, 580, 96, 5),
                Node("engine", "TesseractOcrEngine", ("fresh Tess4J client",), 480, 1080, 480, 96, 6),
                Node("tess4j", "Tess4J", ("JNI bridge",), 1060, 1080, 420, 96, 0),
                Node("tesseract", "Host Tesseract", ("installed traineddata",), 790, 1230, 520, 96, 1),
            ),
            edges=(
                Edge("client", "controller", "request", ((1050, 275), (1050, 360))),
                Edge("controller", "service", "delegate", ((1050, 456), (1050, 530))),
                Edge("service", "image", "decode", ((730, 580), (650, 580), (650, 730))),
                Edge("service", "options", "configure", ((1370, 580), (1460, 580), (1460, 730))),
                Edge("service", "extract", "suspend", ((1050, 630), (1050, 875))),
                Edge("extract", "engine", "engine", ((1050, 971), (1050, 1020), (720, 1020), (720, 1080))),
                Edge("engine", "tess4j", "client", ((960, 1128), (1060, 1128))),
                Edge("tess4j", "tesseract", "native", ((1270, 1176), (1270, 1210), (1050, 1210), (1050, 1230))),
            ),
            footer="The example owns HTTP wiring only; OCR behavior remains in bluetape4k-images-ocr.",
            panels=(
                Panel("Client Layer", 70, 160, 1960, 150, 0),
                Panel("Spring Web Layer", 70, 335, 1960, 150, 1),
                Panel("Application Layer", 70, 510, 1960, 155, 2),
                Panel("OCR Library Layer", 70, 700, 1960, 310, 3),
                Panel("Native Runtime Layer", 70, 1050, 1960, 320, 4),
            ),
        ),
        SequenceDiagram(
            base="examples-spring-boot-ocr-api-sequence-01",
            title="Spring Boot OCR API Sequence",
            subtitle="Request validation, language options, OCR execution, and error mapping.",
            width=1500,
            height=900,
            participants=(
                Participant("client", "Client", "curl/MockMvc", 140),
                Participant("controller", "Controller", "REST API", 395),
                Participant("service", "Service", "options", 655),
                Participant("ocr", "images-ocr", "suspend API", 915),
                Participant("native", "Tesseract", "traineddata", 1180),
            ),
            messages=(
                Message("client", "controller", "POST multipart image and languages", 285),
                Message("controller", "service", "Validate content type and file", 355),
                Message("service", "ocr", "Build OcrOptions and extract text", 445),
                Message("ocr", "native", "Run Tess4J OCR", 535),
                Message("ocr", "service", "Return recognized text", 625, dashed=True),
                Message("service", "client", "Return JSON text result", 705, dashed=True),
            ),
            footer="The test profile replaces the native engine with a deterministic fake engine.",
        ),
    )


def render_captcha_example() -> str:
    width = 560
    height = 220
    out = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}" role="img" aria-label="Example CAPTCHA image">',
        "<defs>",
        "  <style>",
        '    .title{font-family:"Architects Daughter";font-size:22px;fill:#111827;font-weight:400}',
        '    .detail{font-family:"Comic Mono";font-size:12px;fill:#6b7280;font-weight:400}',
        '    .footer{font-family:"Comic Mono";font-size:9.5px;fill:#6b7280;font-weight:400}',
        '    .captcha{font-family:"Architects Daughter";font-size:56px;fill:#111827;font-weight:400}',
        "  </style>",
        "</defs>",
        '<rect width="560" height="220" fill="#ffffff"/>',
        '<rect x="36" y="30" width="488" height="132" rx="8" fill="#ffffff" stroke="#94a3b8" stroke-width="1.9"/>',
        '<path d="M62 70 L498 122 M75 141 L476 56 M94 104 L496 99" stroke="#2563eb" stroke-width="2.2" stroke-linecap="round" opacity="0.32"/>',
        '<path d="M66 119 C138 58 222 156 302 88 S440 58 506 129" stroke="#ea580c" stroke-width="2" fill="none" opacity="0.34"/>',
        '<g opacity="0.55">',
        '<circle cx="88" cy="60" r="3" fill="#16a34a"/><circle cx="122" cy="134" r="2" fill="#ea580c"/><circle cx="184" cy="82" r="2.8" fill="#9333ea"/>',
        '<circle cx="266" cy="126" r="2.5" fill="#dc2626"/><circle cx="348" cy="68" r="3.2" fill="#059669"/><circle cx="430" cy="140" r="2.5" fill="#6b7280"/>',
        '<circle cx="486" cy="82" r="2.7" fill="#2563eb"/><circle cx="392" cy="108" r="2.2" fill="#16a34a"/>',
        "</g>",
        '<text class="captcha" x="280" y="105" text-anchor="middle" dominant-baseline="middle" transform="rotate(-3 280 105)">BT4K7M</text>',
        '<text class="title" x="48" y="182">CAPTCHA challenge preview</text>',
        '<text class="footer" x="280" y="202" text-anchor="middle">https://github.com/bluetape4k/bluetape4k-image</text>',
        '<text class="footer" x="280" y="215" text-anchor="middle">project: bluetape4k-image | module: images-captcha</text>',
        "</svg>",
    ]
    return "\n".join(out) + "\n"


def validate_svg_text(svg_path: Path) -> None:
    content = svg_path.read_text(encoding="utf-8")
    forbidden = ("Inter", "Arial", "Helvetica")
    for item in forbidden:
        if item in content:
            raise ValueError(f"{svg_path} contains forbidden font family {item}")
    if "Architects Daughter" not in content or "Comic Mono" not in content:
        raise ValueError(f"{svg_path} is missing required font roles")


def main() -> int:
    OUT.mkdir(parents=True, exist_ok=True)
    for diagram in diagrams():
        if isinstance(diagram, FlowDiagram):
            save_flow(diagram)
        else:
            save_sequence(diagram)
        validate_svg_text(OUT / f"{diagram.base}.svg")
        if not (OUT / f"{diagram.base}.png").exists():
            raise FileNotFoundError(f"{diagram.base}.png was not rendered")
    captcha_path = OUT / "images-captcha-example-01.svg"
    captcha_path.write_text(render_captcha_example(), encoding="utf-8")
    validate_svg_text(captcha_path)
    render_png(captcha_path)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
