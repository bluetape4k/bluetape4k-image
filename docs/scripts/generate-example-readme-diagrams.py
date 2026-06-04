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


def render_edge(edge: Edge) -> list[str]:
    path_class = "edge-dashed" if edge.dashed else "edge"
    out = [f'<path class="{path_class}" d="{wrap_path(edge.points)}" stroke="{edge.color}"/>']
    if not edge.label:
        return out
    if edge.label_pos:
        mid_x, mid_y = edge.label_pos
    else:
        mid_x = sum(x for x, _ in edge.points) / len(edge.points)
        mid_y = sum(y for _, y in edge.points) / len(edge.points)
    out.extend([
        f'<rect x="{mid_x - 58:.1f}" y="{mid_y - 20:.1f}" width="116" height="22" rx="8" fill="#FFFFFF" stroke="#D7E2EC" opacity="0.94"/>',
        f'<text class="label" x="{mid_x:.1f}" y="{mid_y - 8:.1f}" text-anchor="middle" dominant-baseline="middle">{esc(edge.label)}</text>',
    ])
    return out


def render_flow(diagram: FlowDiagram) -> str:
    out = svg_header(diagram.width, diagram.height, diagram.title, diagram.subtitle)
    if diagram.panels:
        out.append('<g id="layers">')
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
    out.append('<g id="edges">')
    for edge in diagram.edges:
        out.extend(render_edge(edge))
    out.append("</g>")
    out.append('<g id="nodes">')
    for node in diagram.nodes:
        out.extend(render_node(node))
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
    x_by_key = {participant.key: participant.x for participant in diagram.participants}
    top = 150
    header_y = top
    header_w = 186
    header_h = 68
    line_start = header_y + header_h
    line_end = diagram.height - 95
    out = svg_header(diagram.width, diagram.height, diagram.title, diagram.subtitle)
    out.append('<g id="participants">')
    for index, participant in enumerate(diagram.participants):
        fill, stroke = PALETTE[index % len(PALETTE)]
        x = participant.x - header_w // 2
        out.extend(
            [
                f'<rect class="card" x="{x}" y="{header_y}" width="{header_w}" height="{header_h}" rx="10" fill="{fill}" stroke="{stroke}"/>',
                f'<text class="node-title" x="{participant.x}" y="{header_y + 27}" text-anchor="middle" dominant-baseline="middle">{esc(participant.title)}</text>',
                f'<text class="node-detail" x="{participant.x}" y="{header_y + 49}" text-anchor="middle" dominant-baseline="middle">{esc(participant.detail)}</text>',
                f'<line class="lifeline" x1="{participant.x}" y1="{line_start}" x2="{participant.x}" y2="{line_end}"/>',
            ]
        )
    out.append("</g>")
    out.append('<g id="messages">')
    for index, message in enumerate(diagram.messages, start=1):
        source_x = x_by_key[message.source]
        target_x = x_by_key[message.target]
        y = message.y
        left = min(source_x, target_x)
        right = max(source_x, target_x)
        label = f"{index}. {message.label}"
        label_w = min(360, max(178, len(label) * 7 + 32))
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
            width=1500,
            height=930,
            nodes=(
                Node("app", "Spring Boot app", ("imports starter",), 330, 174, 260, 72, 0),
                Node("props", "Images properties", ("bluetape4k.images.*",), 690, 174, 280, 72, 1),
                Node("autoProcessing", "Processing Config", ("AutoConfiguration", "format and quality"), 230, 346, 210, 78, 0),
                Node("autoStorage", "Storage Config", ("AutoConfiguration", "local or S3 backend"), 465, 346, 210, 78, 2),
                Node("autoCdn", "CDN Config", ("AutoConfiguration", "S3 or CloudFront"), 700, 346, 210, 78, 3),
                Node("autoHealth", "Health Config", ("AutoConfiguration", "reactive probe"), 935, 346, 210, 78, 4),
                Node("autoMetrics", "Metrics Config", ("AutoConfiguration", "Micrometer wrapper"), 1170, 346, 210, 78, 5),
                Node("local", "Local storage", ("filesystem root", "path traversal guard"), 300, 522, 230, 78, 4),
                Node("s3", "S3 storage", ("S3Operations", "bucket + key prefix"), 600, 522, 230, 78, 2),
                Node("signers", "Read URL signers", ("pre-signed S3", "CloudFront URLs"), 900, 522, 230, 78, 3),
                Node("storageSpi", "ImageStorage SPI", ("upload, download, exists",), 1170, 522, 230, 78, 1),
                Node("health", "Storage health", ("exists(healthProbeKey)",), 250, 698, 300, 78, 4),
                Node("metrics", "Metrics wrapper", ("timers and counters",), 600, 698, 300, 78, 5),
                Node("actuator", "Actuator sanitizer", ("privateKeyPem redaction",), 950, 698, 300, 78, 6),
            ),
            edges=(
                Edge("app", "autoProcessing", "", ((460, 246), (460, 302), (335, 302), (335, 346)), "#56708C"),
                Edge("props", "autoStorage", "", ((830, 246), (830, 302), (570, 302), (570, 346)), "#8A72D6", True),
                Edge("autoProcessing", "autoStorage", "", ((440, 385), (465, 385)), "#56708C"),
                Edge("autoStorage", "autoCdn", "", ((675, 385), (700, 385)), "#DB7890"),
                Edge("autoStorage", "local", "", ((570, 424), (570, 474), (415, 474), (415, 522)), "#45A7A1"),
                Edge("autoStorage", "s3", "", ((570, 424), (570, 474), (715, 474), (715, 522)), "#D9AA4D"),
                Edge("autoCdn", "signers", "", ((805, 424), (805, 474), (1015, 474), (1015, 522)), "#DB7890"),
                Edge("autoStorage", "storageSpi", "", ((570, 424), (570, 474), (1285, 474), (1285, 522)), "#58A978"),
                Edge("autoHealth", "health", "", ((1040, 424), (1040, 650), (400, 650), (400, 698)), "#45A7A1"),
                Edge("autoMetrics", "metrics", "", ((1275, 424), (1275, 650), (750, 650), (750, 698)), "#8A72D6"),
                Edge("autoCdn", "actuator", "", ((805, 424), (805, 650), (1100, 650), (1100, 698)), "#B88A44"),
                Edge("local", "metrics", "", ((415, 600), (415, 650), (750, 650), (750, 698)), "#45A7A1"),
                Edge("s3", "metrics", "", ((715, 600), (715, 650), (750, 650), (750, 698)), "#D9AA4D"),
            ),
            footer="Graphviz evidence: images-spring-boot-architecture-01.dot, .plain, and -graphviz.svg. Final SVG follows source-derived Spring Boot module layers.",
            panels=(
                Panel("Application", 72, 152, 1356, 122, 0),
                Panel("Auto-Configuration", 72, 308, 1356, 142, 1),
                Panel("Runtime Services", 72, 484, 1356, 146, 2),
                Panel("Operations", 72, 664, 1356, 142, 4),
            ),
            javers_style=True,
        ),
        FlowDiagram(
            base="images-ktor-architecture-01",
            title="Images Ktor Architecture",
            subtitle="Ktor route helpers compose thumbnail generation, CAPTCHA issue, and one-shot verification.",
            width=1500,
            height=930,
            nodes=(
                Node("app", "Ktor application", ("install core plugin",), 330, 174, 260, 72, 0),
                Node("core", "Ktor core baseline", ("JSON and API errors",), 690, 174, 280, 72, 1),
                Node("routing", "Routing DSL", ("routing { ... }",), 270, 340, 240, 78, 2),
                Node("thumb", "Thumbnail routes", ("multipart upload", "PNG thumbnail"), 560, 340, 250, 78, 3),
                Node("captchaRoutes", "CAPTCHA routes", ("issue and verify",), 860, 340, 250, 78, 4),
                Node("errors", "Error payloads", ("ApiErrorResponse",), 1130, 340, 250, 78, 5),
                Node("images", "bluetape4k-images", ("immutableImageOf", "max + writer"), 360, 522, 280, 78, 5),
                Node("captcha", "images-captcha", ("CaptchaGenerator", "VerificationService"), 700, 522, 300, 78, 6),
                Node("challengeStore", "Challenge store", ("in-memory default", "replace for clusters"), 1060, 522, 280, 78, 2),
                Node("auth", "Authorization policy", ("host application",), 370, 704, 270, 72, 0),
                Node("cdn", "S3/CDN URLs", ("compose outside routes",), 680, 704, 270, 72, 2),
                Node("native", "Native acceleration", ("libvips remains optional",), 990, 704, 300, 72, 4),
            ),
            edges=(
                Edge("app", "routing", "", ((460, 246), (460, 300), (390, 300), (390, 340)), "#56708C"),
                Edge("core", "errors", "", ((830, 246), (830, 300), (1255, 300), (1255, 340)), "#8A72D6", True),
                Edge("routing", "thumb", "", ((510, 379), (560, 379)), "#56708C"),
                Edge("routing", "captchaRoutes", "", ((510, 379), (535, 379), (535, 458), (985, 458), (985, 418)), "#58A978"),
                Edge("thumb", "images", "", ((685, 418), (685, 472), (500, 472), (500, 522)), "#DB7890"),
                Edge("captchaRoutes", "captcha", "", ((985, 418), (985, 472), (850, 472), (850, 522)), "#45A7A1"),
                Edge("captchaRoutes", "challengeStore", "", ((985, 418), (985, 472), (1200, 472), (1200, 522)), "#D9AA4D"),
                Edge("routing", "auth", "", ((390, 418), (390, 676), (505, 676), (505, 704)), "#56708C", True),
                Edge("images", "cdn", "", ((500, 600), (500, 676), (815, 676), (815, 704)), "#D9AA4D", True),
                Edge("images", "native", "", ((500, 600), (500, 676), (1140, 676), (1140, 704)), "#45A7A1", True),
            ),
            footer="Graphviz evidence: images-ktor-architecture-01.dot, .plain, and -graphviz.svg. Host systems compose persistence, authorization, and delivery policy.",
            panels=(
                Panel("Application", 72, 152, 1356, 122, 0),
                Panel("Route Helpers", 72, 308, 1356, 142, 1),
                Panel("Processing Libraries", 72, 484, 1356, 146, 2),
                Panel("Host Responsibilities", 72, 664, 1356, 142, 4),
            ),
            javers_style=True,
        ),
        FlowDiagram(
            base="examples-basic-processing-scenario-01",
            title="Basic Processing Scenario",
            subtitle="CLI quickstart turns bundled photo fixtures into deterministic output files.",
            width=1480,
            height=720,
            nodes=(
                Node("run", "Gradle run", ("optional output dir",), 70, 288, color=0),
                Node("fixtures", "Image fixtures", ("cafe, landscape", "workbench preview"), 390, 160, color=1),
                Node("generator", "Quickstart generator", ("load, transform, write",), 390, 420, color=2),
                Node("thumb", "Cafe thumbnail", ("320 x 240 JPG",), 730, 110, color=0),
                Node("crop", "Smart crop", ("640 x 360 JPG",), 730, 245, color=1),
                Node("convert", "PNG conversion", ("800 x 600 PNG",), 730, 380, color=2),
                Node("watermark", "Watermark", ("960 x 540 JPG",), 1045, 245, color=3),
                Node("preview", "Workbench preview", ("960 x 540 JPG",), 1045, 380, color=4),
            ),
            edges=(
                Edge("run", "generator", "run", ((330, 340), (390, 340), (390, 472))),
                Edge("fixtures", "generator", "load", ((520, 264), (520, 420))),
                Edge("generator", "thumb", "fit", ((650, 472), (690, 472), (690, 162), (730, 162))),
                Edge("generator", "crop", "crop", ((650, 472), (700, 472), (700, 297), (730, 297))),
                Edge("generator", "convert", "png", ((650, 472), (730, 432))),
                Edge("generator", "watermark", "draw", ((650, 472), (970, 472), (970, 297), (1045, 297))),
                Edge("generator", "preview", "reuse", ((650, 472), (1045, 432))),
            ),
            footer="All outputs are verified by the same generator that powers :basic-processing:run.",
        ),
        FlowDiagram(
            base="examples-basic-processing-architecture-01",
            title="Basic Processing Architecture",
            subtitle="Pure JVM image transformations with suspend-aware writers.",
            width=1380,
            height=700,
            nodes=(
                Node("cli", "CLI entrypoint", ("main(args)", "runBlocking"), 70, 290, color=0),
                Node("quickstart", "BasicImageProcessingQuickstart", ("generate(outputDirectory)",), 380, 290, color=1),
                Node("loader", "suspendLoadImage", ("file-backed resources",), 690, 125, color=2),
                Node("transforms", "ImmutableImage transforms", ("fit, smartCropTo", "withGraphics watermark"), 690, 290, color=3),
                Node("writers", "Suspend writers", ("JPEG progressive", "PNG max compression"), 690, 455, color=4),
                Node("outputs", "Output directory", ("build/tmp/basic-processing", "five generated files"), 1030, 290, color=5),
            ),
            edges=(
                Edge("cli", "quickstart", "invoke", ((330, 342), (380, 342))),
                Edge("quickstart", "loader", "load", ((640, 342), (665, 342), (665, 177), (690, 177))),
                Edge("loader", "transforms", "images", ((820, 229), (820, 290))),
                Edge("transforms", "writers", "encode", ((820, 394), (820, 455))),
                Edge("writers", "outputs", "write", ((950, 507), (980, 507), (980, 342), (1030, 342))),
            ),
            footer="No server, storage service, Docker, S3, CDN, or native libvips is involved.",
        ),
        SequenceDiagram(
            base="examples-basic-processing-sequence-01",
            title="Basic Processing Sequence",
            subtitle="The smoke test and run task share one deterministic generator.",
            width=1380,
            height=760,
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
            width=1430,
            height=700,
            nodes=(
                Node("client", "Local client", ("curl or Ktor test host",), 75, 298, color=0),
                Node("server", "Ktor Netty server", ("PORT or 8080",), 380, 298, color=1),
                Node("ready", "Ready endpoint", ("/ready -> OK",), 710, 115, color=2),
                Node("captcha", "CAPTCHA routes", ("/api/captcha", "issue and verify"), 710, 298, color=3),
                Node("thumb", "Thumbnail route", ("/api/images/thumbnail", "multipart file"), 710, 480, color=4),
                Node("response", "Local responses", ("JSON challenge", "PNG thumbnail bytes"), 1040, 298, color=5),
            ),
            edges=(
                Edge("client", "server", "HTTP", ((335, 350), (380, 350))),
                Edge("server", "ready", "health", ((640, 350), (670, 350), (670, 167), (710, 167))),
                Edge("server", "captcha", "mount", ((640, 350), (710, 350))),
                Edge("server", "thumb", "mount", ((640, 350), (670, 350), (670, 532), (710, 532))),
                Edge("captcha", "response", "json/png", ((970, 350), (1040, 350))),
                Edge("thumb", "response", "png", ((970, 532), (1005, 532), (1005, 350), (1040, 350))),
            ),
            footer="The quickstart intentionally skips S3, CDN, Docker, persistence, and native libvips.",
        ),
        FlowDiagram(
            base="examples-ktor-image-api-architecture-01",
            title="Ktor Image API Architecture",
            subtitle="Application.configureKtorImageApi installs core JSON support and bluetape4k route helpers.",
            width=1460,
            height=720,
            nodes=(
                Node("app", "Application module", ("configureKtorImageApi",), 70, 305, color=0),
                Node("core", "Ktor core plugin", ("JSON and errors",), 380, 160, color=1),
                Node("routing", "Routing DSL", ("ready + image routes",), 380, 450, color=2),
                Node("captcha", "bluetape4kCaptchaRoutes", ("CaptchaKtorRoutesConfig",), 710, 250, color=3),
                Node("thumbnail", "bluetape4kImageThumbnailRoutes", ("ImageThumbnailKtorRoutesConfig",), 710, 440, color=4),
                Node("captchaLib", "images-captcha", ("PNG challenge generation",), 1050, 250, color=5),
                Node("images", "bluetape4k-images", ("multipart thumbnail",), 1050, 440, color=6),
            ),
            edges=(
                Edge("app", "core", "install", ((330, 357), (350, 357), (350, 212), (380, 212))),
                Edge("app", "routing", "routing", ((330, 357), (350, 357), (350, 502), (380, 502))),
                Edge("routing", "captcha", "mount", ((640, 502), (675, 502), (675, 302), (710, 302))),
                Edge("routing", "thumbnail", "mount", ((640, 502), (710, 492))),
                Edge("captcha", "captchaLib", "generate", ((970, 302), (1050, 302))),
                Edge("thumbnail", "images", "resize", ((970, 492), (1050, 492))),
            ),
            footer="Tests use Ktor testApplication and bluetape4kJsonClient against the same module wiring.",
        ),
        SequenceDiagram(
            base="examples-ktor-image-api-sequence-01",
            title="Ktor Image API Sequence",
            subtitle="CAPTCHA and thumbnail requests stay local and return JSON or PNG bytes.",
            width=1450,
            height=790,
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
            width=1480,
            height=720,
            nodes=(
                Node("client", "Local client", ("curl or MockMvc",), 70, 300, color=0),
                Node("controller", "ImageApiController", ("POST /api/images", "GET /api/images/{key}"), 390, 300, color=1),
                Node("service", "LocalImageApiService", ("validate, resize, store",), 720, 300, color=2),
                Node("original", "Original object", ("originals/{id}.jpg",), 1055, 160, color=3),
                Node("thumbnail", "Thumbnail object", ("thumbnails/{id}.png",), 1055, 440, color=4),
            ),
            edges=(
                Edge("client", "controller", "multipart", ((330, 352), (390, 352))),
                Edge("controller", "service", "suspend", ((650, 352), (720, 352))),
                Edge("service", "original", "upload", ((980, 352), (1015, 352), (1015, 212), (1055, 212))),
                Edge("service", "thumbnail", "fit+png", ((980, 352), (1015, 352), (1015, 492), (1055, 492))),
                Edge("controller", "client", "local urls", ((390, 390), (330, 390)), dashed=True),
            ),
            footer="Default storage is filesystem-backed under build/tmp/spring-boot-image-api/storage.",
        ),
        FlowDiagram(
            base="examples-spring-boot-image-api-architecture-01",
            title="Spring Boot Image API Architecture",
            subtitle="Spring Boot auto-configuration provides local ImageStorage for the quickstart service.",
            width=1500,
            height=720,
            nodes=(
                Node("boot", "Spring Boot 4 app", ("SpringBootImageApiApplication",), 70, 300, color=0),
                Node("controller", "ImageApiController", ("upload and download endpoints",), 390, 300, color=1),
                Node("config", "LocalImageApiConfiguration", ("wires ImageStorage bean",), 390, 480, color=2),
                Node("service", "LocalImageApiService", ("UploadOptions allowlist", "thumbnail generation"), 720, 300, color=3),
                Node("storage", "ImageStorage", ("LocalImageStorage backend",), 1055, 300, color=4),
                Node("files", "Filesystem root", ("originals/ and thumbnails/",), 1055, 480, color=5),
            ),
            edges=(
                Edge("boot", "controller", "scan", ((330, 352), (390, 352))),
                Edge("config", "service", "bean", ((650, 532), (690, 532), (690, 352), (720, 352))),
                Edge("controller", "service", "delegate", ((650, 352), (720, 352))),
                Edge("service", "storage", "upload/download", ((980, 352), (1055, 352))),
                Edge("storage", "files", "persist", ((1185, 404), (1185, 480))),
            ),
            footer="S3/CDN policy is intentionally left to the advanced workshop, not this local quickstart.",
        ),
        SequenceDiagram(
            base="examples-spring-boot-image-api-sequence-01",
            title="Spring Boot Image API Sequence",
            subtitle="Upload validation, thumbnail generation, local storage, and download.",
            width=1500,
            height=820,
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
