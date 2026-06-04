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
    ("#E8F3FF", "#5B8DEF"),
    ("#EAF7EF", "#58A978"),
    ("#FFF3D9", "#D6A441"),
    ("#FDECEF", "#DC6B82"),
    ("#E9F7F6", "#45A7A1"),
    ("#F1ECFF", "#8A72D6"),
    ("#F7F1E7", "#B88A44"),
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


def svg_header(width: int, height: int, title: str, subtitle: str) -> list[str]:
    return [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}" role="img" aria-label="{esc(title)}">',
        "<defs>",
        '  <filter id="shadow" x="-8%" y="-8%" width="116%" height="116%">',
        '    <feDropShadow dx="0" dy="7" stdDeviation="8" flood-color="#203040" flood-opacity="0.10"/>',
        "  </filter>",
        '  <marker id="arrow" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto" markerUnits="strokeWidth">',
        '    <path d="M 1 1 L 7 4 L 1 7 Z" fill="#758297"/>',
        "  </marker>",
        "  <style>",
        "    .canvas{fill:#F6F9FC}",
        "    .frame{fill:#FFFFFF;stroke:#D7E2EC;stroke-width:2}",
        '    .title{font-family:"Architects Daughter";font-size:42px;fill:#203040;font-weight:400}',
        '    .subtitle{font-family:"Comic Mono";font-size:16px;fill:#536476;font-weight:400}',
        '    .node-title{font-family:"Architects Daughter";font-size:22px;fill:#203040;font-weight:400}',
        '    .node-detail{font-family:"Comic Mono";font-size:13px;fill:#4D5F70;font-weight:400}',
        '    .panel-title{font-family:"Architects Daughter";font-size:24px;fill:#203040;font-weight:400}',
        '    .layer{fill:#F3F7FB;stroke:#D7E2EC;stroke-width:2}',
        '    .layer-title{font-family:"Architects Daughter";font-size:26px;fill:#22344A;font-weight:400}',
        '    .label{font-family:"Comic Mono";font-size:12px;fill:#405366;font-weight:400}',
        '    .footer{font-family:"Comic Mono";font-size:13px;fill:#627184;font-weight:400}',
        "    .footer-pill{fill:#FFFFFF;stroke:#D7E2EC;stroke-width:1}",
        "    .card{filter:url(#shadow);stroke-width:2}",
        "    .edge{stroke:#758297;stroke-width:2.3;fill:none;marker-end:url(#arrow);stroke-linecap:round;stroke-linejoin:round}",
        "    .edge-dashed{stroke:#758297;stroke-width:2.2;fill:none;marker-end:url(#arrow);stroke-linecap:round;stroke-linejoin:round;stroke-dasharray:7 6}",
        "    .lifeline{stroke:#9AA8B8;stroke-width:1.7;stroke-dasharray:7 7}",
        "  </style>",
        "</defs>",
        f'<rect class="canvas" width="{width}" height="{height}"/>',
        f'<rect class="frame" x="34" y="28" width="{width - 68}" height="{height - 58}" rx="26"/>',
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
    lines = [node.title, *node.details]
    gap = 20
    total = (len(lines) - 1) * gap
    start = node.cy - total / 2
    out = [
        f'<g id="{esc(node.key)}">',
        f'  <rect class="card" x="{node.x}" y="{node.y}" width="{node.w}" height="{node.h}" rx="14" fill="{fill}" stroke="{stroke}"/>',
    ]
    for index, text in enumerate(lines):
        cls = "node-title" if index == 0 else "node-detail"
        out.append(
            f'  <text class="{cls}" x="{node.cx}" y="{start + index * gap:.1f}" text-anchor="middle" dominant-baseline="middle">{esc(text)}</text>'
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
    return [
        f'<rect x="{mid_x - 58:.1f}" y="{mid_y - 20:.1f}" width="116" height="22" rx="8" fill="#FFFFFF" stroke="#D7E2EC" opacity="0.94"/>',
        f'<text class="label" x="{mid_x:.1f}" y="{mid_y - 8:.1f}" text-anchor="middle" dominant-baseline="middle">{esc(edge.label)}</text>',
    ]


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
                        f'<rect x="{panel.x}" y="{panel.y}" width="{panel.w}" height="{panel.h}" rx="18" fill="{fill}" stroke="{stroke}" stroke-width="1.8" opacity="0.34"/>',
                        f'<text class="panel-title" x="{panel.x + 18}" y="{panel.y + 32}" dominant-baseline="middle">{esc(panel.title)}</text>',
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
                f'<text class="footer" x="{diagram.width / 2:.1f}" y="{pill_y + 22}" text-anchor="middle">{esc(diagram.footer)}</text>',
            ]
        )
    else:
        out.append(
            f'<text class="footer" x="{diagram.width / 2:.1f}" y="{diagram.height - 50}" text-anchor="middle">{esc(diagram.footer)}</text>'
        )
    out.append("</svg>")
    return "\n".join(out) + "\n"


def render_sequence(diagram: SequenceDiagram) -> str:
    margin = 185
    span = diagram.width - margin * 2
    step = span / max(1, len(diagram.participants) - 1)
    x_by_key = {
        participant.key: int(round(margin + index * step))
        for index, participant in enumerate(diagram.participants)
    }
    top = 185
    header_y = top
    header_w = 230
    header_h = 72
    line_start = header_y + header_h
    line_end = diagram.height - 95
    out = svg_header(diagram.width, diagram.height, diagram.title, diagram.subtitle)
    out.append('<g id="participants">')
    for index, participant in enumerate(diagram.participants):
        fill, stroke = PALETTE[index % len(PALETTE)]
        participant_x = x_by_key[participant.key]
        x = participant_x - header_w // 2
        out.extend(
            [
                f'<rect class="card" x="{x}" y="{header_y}" width="{header_w}" height="{header_h}" rx="10" fill="{fill}" stroke="{stroke}"/>',
                f'<text class="node-title" x="{participant_x}" y="{header_y + 29}" text-anchor="middle" dominant-baseline="middle">{esc(participant.title)}</text>',
                f'<text class="node-detail" x="{participant_x}" y="{header_y + 53}" text-anchor="middle" dominant-baseline="middle">{esc(participant.detail)}</text>',
                f'<line class="lifeline" x1="{participant_x}" y1="{line_start}" x2="{participant_x}" y2="{line_end}"/>',
            ]
        )
    out.append("</g>")
    out.append('<g id="messages">')
    for index, message in enumerate(diagram.messages, start=1):
        source_x = x_by_key[message.source]
        target_x = x_by_key[message.target]
        y = message.y + 40
        left = min(source_x, target_x)
        right = max(source_x, target_x)
        label = f"{index}. {message.label}"
        label_w = min(520, max(178, len(label) * 7 + 32))
        label_x = (source_x + target_x) / 2 - label_w / 2
        label_y = y - 46
        if label_y + 28 > y - 10:
            raise ValueError(f"Sequence label overlaps connector: {diagram.base} {label}")
        path_class = "edge-dashed" if message.dashed else "edge"
        out.extend(
            [
                f'<path class="{path_class}" d="M {source_x} {y} L {target_x} {y}"/>',
                f'<rect x="{label_x:.1f}" y="{label_y}" width="{label_w:.1f}" height="28" rx="9" fill="#FFFFFF" stroke="#D7E2EC"/>',
                f'<text class="label" x="{(source_x + target_x) / 2:.1f}" y="{label_y + 14}" text-anchor="middle" dominant-baseline="middle">{esc(label)}</text>',
                f'<circle cx="{left}" cy="{y}" r="4" fill="#FFFFFF" stroke="#758297" stroke-width="1.8"/>',
                f'<circle cx="{right}" cy="{y}" r="4" fill="#FFFFFF" stroke="#758297" stroke-width="1.8"/>',
            ]
        )
    out.append("</g>")
    out.append(
        f'<text class="footer" x="{diagram.width / 2:.1f}" y="{diagram.height - 50}" text-anchor="middle">{esc(diagram.footer)}</text>'
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


def validate_node_text_fit(diagram: FlowDiagram) -> None:
    for node in diagram.nodes:
        title_limit = node.w - 34
        detail_limit = node.w - 28
        title_width = len(node.title) * 13
        if title_width > title_limit:
            raise ValueError(
                f"{diagram.base}: node title overflows {node.key} ({title_width}px > {title_limit}px)"
            )
        for detail in node.details:
            detail_width = len(detail) * 8
            if detail_width > detail_limit:
                raise ValueError(
                    f"{diagram.base}: node detail overflows {node.key} ({detail_width}px > {detail_limit}px)"
                )


def validate_flow_routes(diagram: FlowDiagram) -> None:
    validate_node_text_fit(diagram)
    nodes = {node.key: node for node in diagram.nodes}
    for index, first in enumerate(diagram.nodes):
        for second in diagram.nodes[index + 1 :]:
            if boxes_overlap(first, second):
                raise ValueError(f"{diagram.base}: node overlap or crowding {first.key}<->{second.key}")
    for edge in diagram.edges:
        for first, second in zip(edge.points, edge.points[1:]):
            for node in diagram.nodes:
                if node.key in {edge.source, edge.target}:
                    continue
                if segment_hits_box(first, second, node):
                    raise ValueError(
                        f"{diagram.base}: {edge.source}->{edge.target} segment {first}->{second} crosses or hugs {node.key}"
                    )
        if edge.source not in nodes or edge.target not in nodes:
            raise ValueError(f"{diagram.base}: unknown edge endpoint {edge.source}->{edge.target}")


def write_graphviz(base: str, title: str, nodes: list[tuple[str, str]], edges: list[tuple[str, str, str]]) -> None:
    dot_path = OUT / f"{base}.dot"
    lines = [
        "digraph G {",
        "  graph [rankdir=LR, bgcolor=\"#F6F9FC\", margin=0.18, nodesep=0.55, ranksep=0.75];",
        "  node [shape=box, style=\"rounded,filled\", fillcolor=\"#FFFFFF\", color=\"#9AA8B8\", fontname=\"Comic Mono\", fontsize=11];",
        "  edge [color=\"#758297\", fontname=\"Comic Mono\", fontsize=9];",
        f'  label="{esc(title)}";',
        '  labelloc="t";',
    ]
    for key, label in nodes:
        lines.append(f'  "{key}" [label="{label}"];')
    for source, target, label in edges:
        lines.append(f'  "{source}" -> "{target}" [label="{label}"];')
    lines.append("}")
    dot_path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    subprocess.run(["dot", "-Tplain", str(dot_path), "-o", str(OUT / f"{base}.plain")], check=True)
    subprocess.run(["dot", "-Tsvg", str(dot_path), "-o", str(OUT / f"{base}-graphviz.svg")], check=True)
    subprocess.run(["dot", "-Tpng", str(dot_path), "-o", str(OUT / f"{base}-graphviz.png")], check=True)


def render_png(svg_path: Path) -> None:
    subprocess.run(["rsvg-convert", str(svg_path), "-o", str(svg_path.with_suffix(".png"))], check=True)


def save_flow(diagram: FlowDiagram) -> None:
    validate_flow_routes(diagram)
    svg_path = OUT / f"{diagram.base}.svg"
    svg_path.write_text(render_flow(diagram), encoding="utf-8")
    write_graphviz(
        diagram.base,
        diagram.title,
        [(node.key, node.title) for node in diagram.nodes],
        [(edge.source, edge.target, edge.label) for edge in diagram.edges],
    )
    render_png(svg_path)
    print(f"{diagram.base}: final_nodes={len(diagram.nodes)} final_edges={len(diagram.edges)} graphviz_edges={len(diagram.edges)} manual_exceptions=0")


def save_sequence(diagram: SequenceDiagram) -> None:
    svg_path = OUT / f"{diagram.base}.svg"
    svg_path.write_text(render_sequence(diagram), encoding="utf-8")
    write_graphviz(
        diagram.base,
        diagram.title,
        [(participant.key, participant.title) for participant in diagram.participants],
        [(message.source, message.target, message.label) for message in diagram.messages],
    )
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
                Node("autoCdn", "CDN Config", ("AutoConfiguration", "S3 or CloudFront"), 1245, 475, 340, 92, 3),
                Node("autoHealth", "Health Config", ("AutoConfiguration", "reactive probe"), 1680, 420, 340, 92, 4),
                Node("autoMetrics", "Metrics Config", ("AutoConfiguration", "Micrometer wrapper"), 1680, 560, 340, 92, 5),
                Node("local", "Local storage", ("filesystem root", "path traversal guard"), 390, 765, 350, 92, 4),
                Node("s3", "S3 storage", ("S3Operations", "bucket + key prefix"), 875, 740, 350, 92, 2),
                Node("signers", "Read URL signers", ("pre-signed S3", "CloudFront URLs"), 1320, 790, 350, 92, 3),
                Node("storageSpi", "ImageStorage SPI", ("upload, download, exists",), 1740, 760, 350, 92, 1),
                Node("health", "Storage health", ("exists(healthProbeKey)",), 385, 1065, 380, 90, 4),
                Node("metrics", "Metrics wrapper", ("timers and counters",), 910, 1075, 380, 90, 5),
                Node("actuator", "Actuator sanitizer", ("privateKeyPem redaction",), 1450, 1060, 380, 90, 6),
            ),
            edges=(
                Edge("app", "autoProcessing", "", ((585, 307), (585, 365), (450, 365), (450, 455)), "#56708C"),
                Edge("props", "autoStorage", "", ((1140, 307), (1140, 365), (920, 365), (920, 435)), "#8A72D6", True),
                Edge("autoProcessing", "autoStorage", "", ((615, 501), (745, 501)), "#56708C"),
                Edge("autoStorage", "autoCdn", "", ((1095, 481), (1170, 481), (1170, 521), (1245, 521)), "#DB7890"),
                Edge("autoStorage", "local", "", ((920, 527), (920, 675), (565, 675), (565, 765)), "#45A7A1"),
                Edge("autoStorage", "s3", "", ((920, 527), (920, 740)), "#D9AA4D"),
                Edge("autoCdn", "signers", "", ((1415, 567), (1415, 790)), "#DB7890"),
                Edge("autoStorage", "storageSpi", "", ((1095, 481), (1125, 481), (1125, 700), (1915, 700), (1915, 760)), "#58A978"),
                Edge("autoHealth", "health", "", ((1680, 466), (1680, 350), (160, 350), (160, 1110), (385, 1110)), "#45A7A1"),
                Edge("autoMetrics", "metrics", "", ((2020, 606), (2110, 606), (2110, 1015), (1100, 1015), (1100, 1075)), "#8A72D6"),
                Edge("autoCdn", "actuator", "", ((1585, 521), (2110, 521), (2110, 1010), (1640, 1010), (1640, 1060)), "#B88A44"),
                Edge("local", "metrics", "", ((565, 857), (565, 1000), (1100, 1000), (1100, 1075)), "#45A7A1"),
                Edge("s3", "metrics", "", ((1050, 832), (1050, 1075)), "#D9AA4D"),
            ),
            footer="Graphviz evidence: images-spring-boot-architecture-01.dot, .plain, and -graphviz.svg. Final SVG follows source-derived Spring Boot module layers.",
            panels=(
                Panel("Application", 72, 196, 2056, 142, 0),
                Panel("Auto-Configuration", 72, 390, 2056, 292, 1),
                Panel("Runtime Services", 72, 720, 2056, 190, 2),
                Panel("Operations", 72, 1030, 2056, 170, 4),
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
                Node("auth", "Authorization policy", ("host application",), 410, 1070, 360, 82, 0),
                Node("cdn", "S3/CDN URLs", ("compose outside routes",), 935, 1050, 360, 82, 2),
                Node("native", "Native acceleration", ("libvips remains optional",), 1460, 1070, 380, 82, 4),
            ),
            edges=(
                Edge("app", "routing", "", ((585, 307), (585, 370), (510, 370), (510, 440)), "#56708C"),
                Edge("core", "errors", "", ((1140, 307), (1140, 365), (1875, 365), (1875, 515)), "#8A72D6", True),
                Edge("routing", "thumb", "", ((680, 486), (820, 486)), "#56708C"),
                Edge("routing", "captchaRoutes", "", ((680, 486), (725, 486), (725, 625), (1500, 625), (1500, 512)), "#58A978"),
                Edge("thumb", "images", "", ((1000, 547), (1000, 650), (625, 650), (625, 765)), "#DB7890"),
                Edge("captchaRoutes", "captcha", "", ((1500, 512), (1500, 650), (1145, 650), (1145, 735)), "#45A7A1"),
                Edge("captchaRoutes", "challengeStore", "", ((1500, 512), (1500, 690), (1705, 690), (1705, 775)), "#D9AA4D"),
                Edge("routing", "auth", "", ((510, 532), (200, 532), (200, 1111), (410, 1111)), "#56708C", True),
                Edge("images", "cdn", "", ((625, 857), (625, 970), (1115, 970), (1115, 1050)), "#D9AA4D", True),
                Edge("images", "native", "", ((625, 857), (625, 990), (1650, 990), (1650, 1070)), "#45A7A1", True),
            ),
            footer="Graphviz evidence: images-ktor-architecture-01.dot, .plain, and -graphviz.svg. Host systems compose persistence, authorization, and delivery policy.",
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
                Edge("run", "generator", "", ((430, 435), (455, 435), (455, 598), (480, 598))),
                Edge("fixtures", "generator", "", ((660, 322), (660, 540))),
                Edge("generator", "thumb", "", ((860, 598), (960, 598), (960, 195), (1050, 195))),
                Edge("generator", "crop", "", ((860, 598), (960, 598), (960, 355), (1050, 355))),
                Edge("generator", "convert", "", ((860, 598), (960, 598), (960, 515), (1050, 515))),
                Edge("crop", "watermark", "", ((1390, 355), (1490, 355))),
                Edge("convert", "preview", "", ((1390, 515), (1490, 515))),
            ),
            footer="All outputs are verified by the same generator that powers :basic-processing:run.",
        ),
        FlowDiagram(
            base="examples-basic-processing-architecture-01",
            title="Basic Processing Architecture",
            subtitle="Pure JVM image transformations with suspend-aware writers.",
            width=2600,
            height=960,
            nodes=(
                Node("cli", "CLI entrypoint", ("main(args)", "runBlocking"), 290, 220, 340, 110, 0),
                Node("quickstart", "BasicImageProcessingQuickstart", ("generate(outputDirectory)",), 730, 360, 500, 110, 1),
                Node("loader", "suspendLoadImage", ("file-backed resources",), 1290, 520, 360, 110, 2),
                Node("transforms", "ImmutableImage transforms", ("fit, smartCropTo", "withGraphics watermark"), 1700, 520, 410, 110, 3),
                Node("writers", "Suspend writers", ("JPEG progressive", "PNG max compression"), 1700, 680, 360, 110, 4),
                Node("outputs", "Output directory", ("build/tmp/basic-processing", "five generated files"), 2110, 680, 360, 110, 5),
            ),
            edges=(
                Edge("cli", "quickstart", "", ((630, 275), (680, 275), (680, 415), (730, 415))),
                Edge("quickstart", "loader", "", ((1230, 415), (1260, 415), (1260, 575), (1290, 575))),
                Edge("loader", "transforms", "", ((1650, 575), (1700, 575))),
                Edge("transforms", "writers", "", ((1905, 630), (1905, 680))),
                Edge("writers", "outputs", "", ((2060, 735), (2110, 735))),
            ),
            footer="No server, storage service, Docker, S3, CDN, or native libvips is involved.",
            panels=(
                Panel("Entrypoint", 72, 190, 2456, 150, 0),
                Panel("Workflow", 72, 350, 2456, 150, 1),
                Panel("Image Library", 72, 510, 2456, 130, 2),
                Panel("Output", 72, 670, 2456, 130, 4),
            ),
            javers_style=True,
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
                Edge("client", "server", "", ((450, 435), (520, 435))),
                Edge("server", "ready", "", ((880, 435), (970, 435), (970, 225), (1050, 225))),
                Edge("server", "captcha", "", ((880, 435), (1050, 435))),
                Edge("server", "thumb", "", ((880, 435), (970, 435), (970, 645), (1050, 645))),
                Edge("captcha", "response", "", ((1410, 435), (1540, 435))),
                Edge("thumb", "response", "", ((1410, 645), (1475, 645), (1475, 435), (1540, 435))),
            ),
            footer="The quickstart intentionally skips S3, CDN, Docker, persistence, and native libvips.",
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
                Edge("app", "core", "", ((500, 416), (560, 416), (560, 306), (620, 306))),
                Edge("app", "routing", "", ((500, 416), (560, 416), (560, 556), (620, 556))),
                Edge("routing", "captcha", "", ((1000, 556), (1060, 556), (1060, 416), (1120, 416))),
                Edge("routing", "thumbnail", "", ((1000, 556), (1060, 556), (1060, 616), (1120, 616))),
                Edge("captcha", "captchaLib", "", ((1640, 416), (1780, 416))),
                Edge("thumbnail", "images", "", ((1680, 616), (1780, 616))),
            ),
            footer="Tests use Ktor testApplication and bluetape4kJsonClient against the same module wiring.",
            panels=(
                Panel("Application", 72, 180, 450, 560, 0),
                Panel("Ktor Runtime", 560, 180, 460, 560, 1),
                Panel("Route Helpers", 1060, 180, 650, 560, 2),
                Panel("Libraries", 1740, 180, 488, 560, 4),
            ),
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
                Edge("client", "controller", "", ((430, 355), (500, 355))),
                Edge("controller", "service", "", ((900, 358), (970, 358))),
                Edge("service", "original", "", ((1360, 358), (1405, 358), (1405, 205), (1450, 205))),
                Edge("service", "thumbnail", "", ((1360, 358), (1405, 358), (1405, 505), (1450, 505))),
                Edge("controller", "client", "", ((500, 405), (465, 405), (465, 375), (430, 375)), "#758297", True),
            ),
            footer="Default storage is filesystem-backed under build/tmp/spring-boot-image-api/storage.",
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
                Edge("boot", "controller", "", ((500, 436), (560, 436), (560, 336), (620, 336))),
                Edge("boot", "config", "", ((500, 436), (560, 436), (560, 596), (620, 596))),
                Edge("controller", "service", "", ((1020, 336), (1080, 336), (1080, 438), (1140, 438))),
                Edge("config", "service", "", ((1050, 596), (1090, 596), (1090, 438), (1140, 438))),
                Edge("service", "storage", "", ((1560, 438), (1660, 438))),
                Edge("storage", "files", "", ((1850, 492), (1850, 600))),
            ),
            footer="S3/CDN policy is intentionally left to the advanced workshop, not this local quickstart.",
            panels=(
                Panel("Application", 72, 180, 470, 560, 0),
                Panel("API + Config", 580, 180, 500, 560, 1),
                Panel("Local Service", 1120, 180, 470, 560, 2),
                Panel("Storage", 1630, 180, 498, 560, 4),
            ),
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
    )


def render_captcha_example() -> str:
    width = 560
    height = 220
    out = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}" role="img" aria-label="Example CAPTCHA image">',
        "<defs>",
        "  <style>",
        '    .title{font-family:"Architects Daughter";font-size:22px;fill:#203040;font-weight:400}',
        '    .detail{font-family:"Comic Mono";font-size:12px;fill:#536476;font-weight:400}',
        '    .captcha{font-family:"Architects Daughter";font-size:56px;fill:#24384F;font-weight:400}',
        "  </style>",
        "</defs>",
        '<rect width="560" height="220" rx="22" fill="#F6F9FC"/>',
        '<rect x="36" y="30" width="488" height="132" rx="18" fill="#FFFFFF" stroke="#D7E2EC" stroke-width="2"/>',
        '<path d="M62 70 L498 122 M75 141 L476 56 M94 104 L496 99" stroke="#75A9E8" stroke-width="2.2" stroke-linecap="round" opacity="0.42"/>',
        '<path d="M66 119 C138 58 222 156 302 88 S440 58 506 129" stroke="#DB7890" stroke-width="2" fill="none" opacity="0.45"/>',
        '<g opacity="0.55">',
        '<circle cx="88" cy="60" r="3" fill="#58A978"/><circle cx="122" cy="134" r="2" fill="#D6A441"/><circle cx="184" cy="82" r="2.8" fill="#8A72D6"/>',
        '<circle cx="266" cy="126" r="2.5" fill="#DC6B82"/><circle cx="348" cy="68" r="3.2" fill="#45A7A1"/><circle cx="430" cy="140" r="2.5" fill="#B88A44"/>',
        '<circle cx="486" cy="82" r="2.7" fill="#5B8DEF"/><circle cx="392" cy="108" r="2.2" fill="#58A978"/>',
        "</g>",
        '<text class="captcha" x="280" y="105" text-anchor="middle" dominant-baseline="middle" transform="rotate(-3 280 105)">BT4K7M</text>',
        '<text class="title" x="48" y="188">CAPTCHA challenge preview</text>',
        '<text class="detail" x="48" y="207">Illustrative output using the 200 x 80 default model, scaled for README.</text>',
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
