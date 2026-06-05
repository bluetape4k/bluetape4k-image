#!/usr/bin/env python3
"""Generate the root README overview diagram."""

from __future__ import annotations

import html
import subprocess
from dataclasses import dataclass
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "docs" / "assets" / "readme-diagrams"
BASE = "root-readme-overview-01"

WIDTH = 1900
HEIGHT = 1120


PALETTE = {
    "blue": ("#E8F3FF", "#5B8DEF"),
    "green": ("#EAF7EF", "#58A978"),
    "amber": ("#FFF3D9", "#D6A441"),
    "pink": ("#FDECEF", "#DC6B82"),
    "teal": ("#E9F7F6", "#45A7A1"),
    "lavender": ("#F1ECFF", "#8A72D6"),
    "sand": ("#F7F1E7", "#B88A44"),
    "orange": ("#FFF1E8", "#E58554"),
}


@dataclass(frozen=True)
class Node:
    key: str
    title: str
    detail: tuple[str, ...]
    x: int
    y: int
    w: int
    h: int
    tone: str
    kind: str = "card"

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
    tone: str = "#758297"


def esc(value: str) -> str:
    return html.escape(value, quote=True)


def path_d(points: tuple[tuple[int, int], ...]) -> str:
    first, *rest = points
    return " ".join([f"M {first[0]} {first[1]}", *(f"L {x} {y}" for x, y in rest)])


def label_point(points: tuple[tuple[int, int], ...]) -> tuple[float, float]:
    lengths = [abs(b[0] - a[0]) + abs(b[1] - a[1]) for a, b in zip(points, points[1:])]
    half = sum(lengths) / 2
    traversed = 0.0
    for (x1, y1), (x2, y2), length in zip(points, points[1:], lengths):
        if traversed + length >= half:
            ratio = 0 if length == 0 else (half - traversed) / length
            return (x1 + (x2 - x1) * ratio, y1 + (y2 - y1) * ratio)
        traversed += length
    return points[-1]


def nodes() -> tuple[Node, ...]:
    return (
        Node("bom", "BOM", ("bluetape4k-image-bom", "aligned artifact versions"), 90, 190, 330, 128, "blue"),
        Node("consumer", "Kotlin Services", ("apps choose a path", "pure JVM or native"), 540, 190, 340, 128, "green"),
        Node("examples", "Runnable Examples", ("basic-processing", "Spring Boot and Ktor APIs"), 1020, 190, 380, 128, "amber"),
        Node("benchmark", "Benchmark Lane", ("kotlinx-benchmark", "scrimage vs libvips"), 1500, 190, 320, 128, "sand"),
        Node("scrimage", "Pure JVM Processing", ("images", "load, resize, crop, filter", "analyze, batch, encode"), 90, 455, 420, 172, "teal"),
        Node("captcha", "CAPTCHA Generation", ("images-captcha", "Java2D challenge images", "bounded options"), 560, 455, 380, 172, "orange"),
        Node("service", "Service Integrations", ("images-ktor", "images-spring-boot", "routes, storage, health, metrics"), 990, 455, 430, 172, "pink"),
        Node("vipsApi", "Vips Runtime API", ("images-vips-api", "VipsImage / VipsRuntime", "binding-neutral contract"), 1470, 455, 360, 172, "lavender"),
        Node("jni", "Java 21 JNI Backend", ("images-vips-java21", "JVips + system libvips"), 1030, 750, 360, 142, "blue"),
        Node("ffm", "Java 25 FFM Backend", ("images-vips-java25", "Panama FFM + native access"), 1480, 750, 360, 142, "green"),
        Node("host", "Native Host Capability", ("libvips codecs decide", "AVIF / HEIC availability"), 560, 750, 370, 142, "amber"),
    )


def edges() -> tuple[Edge, ...]:
    return (
        Edge("bom", "consumer", "align", ((420, 254), (540, 254))),
        Edge("consumer", "scrimage", "default path", ((710, 318), (710, 385), (300, 385), (300, 455))),
        Edge("consumer", "captcha", "challenge path", ((710, 318), (710, 455))),
        Edge("consumer", "service", "web path", ((710, 318), (710, 385), (1205, 385), (1205, 455))),
        Edge("consumer", "vipsApi", "native path", ((880, 254), (940, 254), (940, 385), (1650, 385), (1650, 455))),
        Edge("scrimage", "service", "thumbnail bytes", ((300, 627), (300, 670), (1205, 670), (1205, 627)), "#45A7A1"),
        Edge("captcha", "service", "verify", ((940, 541), (990, 541)), "#E58554"),
        Edge("service", "vipsApi", "", ((1420, 541), (1470, 541)), "#DC6B82"),
        Edge("vipsApi", "jni", "Java 21+", ((1650, 627), (1650, 690), (1210, 690), (1210, 750)), "#8A72D6"),
        Edge("vipsApi", "ffm", "Java 25+", ((1650, 627), (1650, 750)), "#8A72D6"),
        Edge("host", "jni", "libvips", ((930, 821), (1030, 821)), "#D6A441"),
        Edge("host", "ffm", "libvips", ((930, 821), (930, 950), (1660, 950), (1660, 892)), "#D6A441"),
        Edge("benchmark", "vipsApi", "compare", ((1660, 318), (1660, 455)), "#B88A44"),
    )


def render_header() -> list[str]:
    return [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{WIDTH}" height="{HEIGHT}" viewBox="0 0 {WIDTH} {HEIGHT}" role="img" aria-label="Bluetape4k Image overview">',
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
        '    .title{font-family:"Architects Daughter";font-size:44px;fill:#203040;font-weight:400}',
        '    .subtitle{font-family:"Comic Mono";font-size:16px;fill:#536476;font-weight:400}',
        '    .section{font-family:"Architects Daughter";font-size:21px;fill:#405366;font-weight:400}',
        '    .card-title{font-family:"Architects Daughter";font-size:24px;fill:#203040;font-weight:400}',
        '    .detail{font-family:"Comic Mono";font-size:13px;fill:#405366;font-weight:400}',
        '    .label{font-family:"Comic Mono";font-size:12px;fill:#405366;font-weight:400}',
        '    .footer{font-family:"Comic Mono";font-size:13px;fill:#627184;font-weight:400}',
        "    .card{filter:url(#shadow);stroke-width:2}",
        "    .edge{stroke-width:2.2;fill:none;marker-end:url(#arrow);stroke-linecap:round;stroke-linejoin:round}",
        "  </style>",
        "</defs>",
        f'<rect class="canvas" width="{WIDTH}" height="{HEIGHT}"/>',
        f'<rect class="frame" x="34" y="28" width="{WIDTH - 68}" height="{HEIGHT - 58}" rx="26"/>',
        '<text class="title" x="72" y="82">Bluetape4k Image overview</text>',
        '<text class="subtitle" x="76" y="118">One repository covers pure JVM image processing, service adapters, native libvips acceleration, and benchmark evidence.</text>',
        '<text class="section" x="92" y="165">Entry and selection</text>',
        '<text class="section" x="92" y="430">Processing and integration surface</text>',
        '<text class="section" x="92" y="725">Native acceleration choices</text>',
    ]


def render_node(node: Node) -> list[str]:
    fill, stroke = PALETTE[node.tone]
    out = [
        f'<g id="{esc(node.key)}">',
        f'  <rect class="card" x="{node.x}" y="{node.y}" width="{node.w}" height="{node.h}" rx="10" fill="{fill}" stroke="{stroke}"/>',
        f'  <text class="card-title" x="{node.cx}" y="{node.y + 38}" text-anchor="middle">{esc(node.title)}</text>',
    ]
    start_y = node.y + 72
    for index, detail in enumerate(node.detail):
        y = start_y + index * 24
        out.append(f'  <rect x="{node.x + 22}" y="{y - 16}" width="{node.w - 44}" height="22" rx="7" fill="#FFFFFF" stroke="#D7E2EC" opacity="0.92"/>')
        out.append(f'  <text class="detail" x="{node.cx}" y="{y}" text-anchor="middle">{esc(detail)}</text>')
    out.append("</g>")
    return out


def render_edge(edge: Edge) -> list[str]:
    out = [f'<path class="edge" d="{path_d(edge.points)}" stroke="{edge.tone}"/>']
    if edge.label:
        mid = label_point(edge.points)
        label_w = max(74, len(edge.label) * 8 + 26)
        out.extend(
            [
                f'<rect x="{mid[0] - label_w / 2:.1f}" y="{mid[1] - 26:.1f}" width="{label_w}" height="24" rx="8" fill="#FFFFFF" stroke="#D7E2EC" opacity="0.96"/>',
                f'<text class="label" x="{mid[0]:.1f}" y="{mid[1] - 10:.1f}" text-anchor="middle">{esc(edge.label)}</text>',
            ]
        )
    return out


def render_svg() -> str:
    out = render_header()
    out.append('<g id="edges">')
    for edge in edges():
        out.extend(render_edge(edge))
    out.append("</g>")
    out.append('<g id="nodes">')
    for node in nodes():
        out.extend(render_node(node))
    out.append("</g>")
    out.append(
        f'<text class="footer" x="{WIDTH / 2:.1f}" y="{HEIGHT - 50}" text-anchor="middle">'
        "Source evidence: settings.gradle.kts modules, root README capabilities, module README examples, and libvips requirements."
        "</text>"
    )
    out.append("</svg>")
    return "\n".join(out) + "\n"


def point_on_boundary(point: tuple[int, int], node: Node) -> bool:
    x, y = point
    return (x in (node.x, node.right) and node.y <= y <= node.bottom) or (
        y in (node.y, node.bottom) and node.x <= x <= node.right
    )


def segment_intersects_rect(a: tuple[int, int], b: tuple[int, int], node: Node, padding: int) -> bool:
    x1, y1 = a
    x2, y2 = b
    left, right = node.x - padding, node.right + padding
    top, bottom = node.y - padding, node.bottom + padding
    if x1 == x2:
        return left <= x1 <= right and min(y1, y2) <= bottom and max(y1, y2) >= top
    if y1 == y2:
        return top <= y1 <= bottom and min(x1, x2) <= right and max(x1, x2) >= left
    raise ValueError("overview routes must be orthogonal")


def validate() -> None:
    ns = nodes()
    es = edges()
    by_key = {node.key: node for node in ns}
    for node in ns:
        if node.x < 60 or node.y < 145 or node.right > WIDTH - 60 or node.bottom > HEIGHT - 84:
            raise ValueError(f"{BASE}: node outside balanced canvas area {node.key}")
        if len(node.title) * 13 > node.w - 40:
            raise ValueError(f"{BASE}: title overflows {node.key}")
        for detail in node.detail:
            if len(detail) * 8 > node.w - 64:
                raise ValueError(f"{BASE}: detail overflows {node.key}: {detail}")
    for i, first in enumerate(ns):
        for second in ns[i + 1 :]:
            if not (
                first.right + 24 <= second.x
                or second.right + 24 <= first.x
                or first.bottom + 24 <= second.y
                or second.bottom + 24 <= first.y
            ):
                raise ValueError(f"{BASE}: node overlap {first.key}<->{second.key}")
    for edge in es:
        source = by_key[edge.source]
        target = by_key[edge.target]
        if not point_on_boundary(edge.points[0], source):
            raise ValueError(f"{BASE}: edge {edge.source}->{edge.target} starts off boundary")
        if not point_on_boundary(edge.points[-1], target):
            raise ValueError(f"{BASE}: edge {edge.source}->{edge.target} ends off boundary")
        for a, b in zip(edge.points, edge.points[1:]):
            if a == b:
                raise ValueError(f"{BASE}: zero-length segment {edge.source}->{edge.target}")
            for node in ns:
                if node.key in (edge.source, edge.target):
                    continue
                if segment_intersects_rect(a, b, node, padding=8):
                    raise ValueError(f"{BASE}: edge {edge.source}->{edge.target} segment {a}->{b} crosses or crowds {node.key}")


def write_graphviz() -> None:
    dot_path = OUT / f"{BASE}.dot"
    lines = [
        "digraph G {",
        "  graph [layout=neato, splines=ortho, overlap=false, bgcolor=\"#F6F9FC\", margin=0.18];",
        "  node [shape=box, style=\"rounded,filled\", fixedsize=true, fillcolor=\"#FFFFFF\", color=\"#9AA8B8\", fontname=\"Comic Mono\", fontsize=11];",
        "  edge [color=\"#758297\", fontname=\"Comic Mono\", fontsize=9];",
        '  label="Bluetape4k Image overview";',
        '  labelloc="t";',
    ]
    for node in nodes():
        lines.append(
            f'  "{node.key}" [label="{node.title}", width="{node.w / 72:.3f}", height="{node.h / 72:.3f}", pos="{node.cx},{HEIGHT - node.cy}!"];'
        )
    for edge in edges():
        label = f' [label="{esc(edge.label)}"]' if edge.label else ""
        lines.append(f'  "{edge.source}" -> "{edge.target}"{label};')
    lines.append("}")
    dot_path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    cmd = ["neato", "-n2"]
    subprocess.run([*cmd, "-Tplain", str(dot_path), "-o", str(OUT / f"{BASE}.plain")], check=True)
    subprocess.run([*cmd, "-Tsvg", str(dot_path), "-o", str(OUT / f"{BASE}-graphviz.svg")], check=True)
    subprocess.run([*cmd, "-Tpng", str(dot_path), "-o", str(OUT / f"{BASE}-graphviz.png")], check=True)


def main() -> int:
    OUT.mkdir(parents=True, exist_ok=True)
    validate()
    svg_path = OUT / f"{BASE}.svg"
    svg_path.write_text(render_svg(), encoding="utf-8")
    write_graphviz()
    subprocess.run(["rsvg-convert", str(svg_path), "-o", str(svg_path.with_suffix(".png"))], check=True)
    content = svg_path.read_text(encoding="utf-8")
    for forbidden in ("Inter", "Arial", "Helvetica"):
        if forbidden in content:
            raise ValueError(f"{BASE}: forbidden font family {forbidden}")
    print(f"{BASE}: nodes={len(nodes())} edges={len(edges())} manual_exceptions=0")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
