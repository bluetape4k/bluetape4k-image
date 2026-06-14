#!/usr/bin/env python3
"""Generate the root README overview diagram."""

from __future__ import annotations

import html
import subprocess
from dataclasses import dataclass
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "docs" / "images" / "readme-diagrams"
BASE = "root-readme-overview-01"

WIDTH = 1900
HEIGHT = 1120


PALETTE = {
    "blue": ("#eff6ff", "#bfdbfe"),
    "green": ("#f0fdf4", "#bbf7d0"),
    "amber": ("#fff7ed", "#fed7aa"),
    "pink": ("#fef2f2", "#fecaca"),
    "teal": ("#f0fdfa", "#ccfbf1"),
    "lavender": ("#faf5ff", "#e9d5ff"),
    "sand": ("#f9fafb", "#d1d5db"),
    "orange": ("#fff7ed", "#fed7aa"),
}

ARROWS = {
    "#758297": ("#2563eb", "arrow-blue"),
    "#45A7A1": ("#16a34a", "arrow-green"),
    "#E58554": ("#ea580c", "arrow-orange"),
    "#DC6B82": ("#ea580c", "arrow-orange"),
    "#8A72D6": ("#9333ea", "arrow-purple"),
    "#D6A441": ("#16a34a", "arrow-green"),
    "#B88A44": ("#6b7280", "arrow-gray"),
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
        else:
            wrapped.extend(line[index : index + max_chars] for index in range(0, len(line), max_chars))
    return wrapped


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
        Node("host", "Native Host Capability", ("libvips codecs decide", "AVIF / HEIC availability"), 520, 750, 430, 142, "amber"),
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
        Edge("host", "jni", "libvips", ((950, 821), (1030, 821)), "#D6A441"),
        Edge("host", "ffm", "libvips", ((735, 892), (735, 950), (1660, 950), (1660, 892)), "#D6A441"),
        Edge("benchmark", "vipsApi", "compare", ((1660, 318), (1808, 318), (1808, 430), (1760, 430), (1760, 455)), "#B88A44"),
    )


def render_header() -> list[str]:
    return [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{WIDTH}" height="{HEIGHT}" viewBox="0 0 {WIDTH} {HEIGHT}" role="img" aria-label="Bluetape4k Image overview">',
        "<defs>",
        '  <marker id="arrow-blue" markerWidth="10" markerHeight="7" refX="9" refY="3.5" orient="auto"><polygon points="0 0, 10 3.5, 0 7" fill="#2563eb"/></marker>',
        '  <marker id="arrow-green" markerWidth="10" markerHeight="7" refX="9" refY="3.5" orient="auto"><polygon points="0 0, 10 3.5, 0 7" fill="#16a34a"/></marker>',
        '  <marker id="arrow-orange" markerWidth="10" markerHeight="7" refX="9" refY="3.5" orient="auto"><polygon points="0 0, 10 3.5, 0 7" fill="#ea580c"/></marker>',
        '  <marker id="arrow-purple" markerWidth="10" markerHeight="7" refX="9" refY="3.5" orient="auto"><polygon points="0 0, 10 3.5, 0 7" fill="#9333ea"/></marker>',
        '  <marker id="arrow-gray" markerWidth="10" markerHeight="7" refX="9" refY="3.5" orient="auto"><polygon points="0 0, 10 3.5, 0 7" fill="#6b7280"/></marker>',
        "  <style>",
        "    .canvas{fill:#ffffff}",
        "    .layer{fill:#f3f6fa;stroke:#cbd5e1;stroke-width:1.4;stroke-dasharray:8 6}",
        '    .title{font-family:"Architects Daughter";font-size:44px;fill:#111827;font-weight:400}',
        '    .subtitle{font-family:"Comic Mono";font-size:16px;fill:#6b7280;font-weight:400}',
        '    .section{font-family:"Comic Mono";font-size:16px;fill:#4b5563;font-weight:700;letter-spacing:0.8px}',
        '    .card-title{font-family:"Architects Daughter";font-size:24px;fill:#111827;font-weight:400}',
        '    .detail{font-family:"Comic Mono";font-size:13px;fill:#6b7280;font-weight:400}',
        '    .label{font-family:"Comic Mono";font-size:12px;fill:#374151;font-weight:400}',
        '    .footer{font-family:"Comic Mono";font-size:13px;fill:#6b7280;font-weight:400}',
        '    .badge-text{font-family:"Comic Mono";font-size:11px;fill:#374151;font-weight:700}',
        "    .card{fill:#ffffff;stroke:#94a3b8;stroke-width:1.9}",
        "  </style>",
        "</defs>",
        f'<rect class="canvas" width="{WIDTH}" height="{HEIGHT}"/>',
        '<text class="title" x="72" y="82">Bluetape4k Image overview</text>',
        '<text class="subtitle" x="76" y="118">One repository covers pure JVM image processing, service adapters, native libvips acceleration, and benchmark evidence.</text>',
        '<rect class="layer" x="64" y="142" width="1772" height="236" rx="8"/>',
        '<rect class="layer" x="64" y="404" width="1772" height="272" rx="8"/>',
        '<rect class="layer" x="64" y="710" width="1772" height="320" rx="8"/>',
        '<text class="section" x="92" y="165">ENTRY AND SELECTION</text>',
        '<text class="section" x="92" y="430">PROCESSING AND INTEGRATION SURFACE</text>',
        '<text class="section" x="92" y="733">NATIVE ACCELERATION CHOICES</text>',
    ]


def render_node(node: Node) -> list[str]:
    fill, stroke = PALETTE[node.tone]
    title_lines = wrap_words(node.title, max(8, int((node.w - 44) / 14.5)))
    detail_lines: list[str] = []
    for detail in node.detail:
        detail_lines.extend(wrap_words(detail, max(8, int((node.w - 64) / 8.0))))
    title_start = node.y + 34 - (len(title_lines) - 1) * 12
    out = [
        f'<g id="{esc(node.key)}">',
        f'  <rect class="card" x="{node.x}" y="{node.y}" width="{node.w}" height="{node.h}" rx="8"/>',
        f'  <rect x="{node.x + 18}" y="{node.y + 20}" width="44" height="44" rx="8" fill="{fill}" stroke="{stroke}" stroke-width="1.3"/>',
        f'  <text class="badge-text" x="{node.x + 40}" y="{node.y + 48}" text-anchor="middle">{esc(node.key[:3].upper())}</text>',
    ]
    for index, line in enumerate(title_lines):
        out.append(f'  <text class="card-title" x="{node.x + 82}" y="{title_start + index * 24}">{esc(line)}</text>')
    start_y = node.y + 72 + max(0, len(title_lines) - 1) * 10
    for index, detail in enumerate(detail_lines):
        y = start_y + index * 24
        out.append(f'  <text class="detail" x="{node.x + 82}" y="{y}">{esc(detail)}</text>')
    out.append("</g>")
    return out


def render_edge(edge: Edge) -> list[str]:
    stroke, marker = ARROWS.get(edge.tone, ARROWS["#758297"])
    out = [f'<path d="{path_d(edge.points)}" stroke="{stroke}" stroke-width="2.1" fill="none" marker-end="url(#{marker})" stroke-linecap="round" stroke-linejoin="round"/>']
    if edge.label:
        mid = label_point(edge.points)
        out.append(f'<text class="label" x="{mid[0]:.1f}" y="{mid[1] - 10:.1f}" text-anchor="middle">{esc(edge.label)}</text>')
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
        "https://github.com/bluetape4k/bluetape4k-image | project: bluetape4k-image | module: root"
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
        title_lines = wrap_words(node.title, max(8, int((node.w - 44) / 14.5)))
        if any(len(line) * 14.5 > node.w - 40 for line in title_lines):
            raise ValueError(f"{BASE}: title overflows {node.key}")
        rendered_lines = len(title_lines)
        for detail in node.detail:
            detail_lines = wrap_words(detail, max(8, int((node.w - 64) / 8.0)))
            rendered_lines += len(detail_lines)
            if any(len(line) * 8 > node.w - 64 for line in detail_lines):
                raise ValueError(f"{BASE}: detail overflows {node.key}: {detail}")
        if 34 + rendered_lines * 24 > node.h - 14:
            raise ValueError(f"{BASE}: text block too tall for {node.key}")
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


def main() -> int:
    OUT.mkdir(parents=True, exist_ok=True)
    validate()
    svg_path = OUT / f"{BASE}.svg"
    svg_path.write_text(render_svg(), encoding="utf-8")
    subprocess.run(["rsvg-convert", str(svg_path), "-o", str(svg_path.with_suffix(".png"))], check=True)
    content = svg_path.read_text(encoding="utf-8")
    for forbidden in ("Inter", "Arial", "Helvetica"):
        if forbidden in content:
            raise ValueError(f"{BASE}: forbidden font family {forbidden}")
    print(f"{BASE}: nodes={len(nodes())} edges={len(edges())} manual_exceptions=0")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
