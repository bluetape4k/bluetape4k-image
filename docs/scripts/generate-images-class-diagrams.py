#!/usr/bin/env python3
"""Generate split class diagrams for the bluetape4k-images README."""

from __future__ import annotations

import html
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
class ClassNode:
    key: str
    title: str
    stereotype: str
    members: tuple[str, ...]
    x: int
    y: int
    w: int = 330
    h: int = 148
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
class ClassEdge:
    source: str
    target: str
    label: str
    points: tuple[tuple[int, int], ...]
    kind: str = "dependency"


@dataclass(frozen=True)
class ClassDiagram:
    base: str
    title: str
    subtitle: str
    width: int
    height: int
    nodes: tuple[ClassNode, ...]
    edges: tuple[ClassEdge, ...]
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
            ratio = 0.0 if length == 0 else (half - traversed) / length
            return (x1 + (x2 - x1) * ratio, y1 + (y2 - y1) * ratio)
        traversed += length
    return points[-1]


def svg_header(diagram: ClassDiagram) -> list[str]:
    return [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{diagram.width}" height="{diagram.height}" viewBox="0 0 {diagram.width} {diagram.height}" role="img" aria-label="{esc(diagram.title)}">',
        "<defs>",
        '  <marker id="arrow-blue" markerWidth="10" markerHeight="7" refX="9" refY="3.5" orient="auto">',
        '    <polygon points="0 0, 10 3.5, 0 7" fill="#2563eb"/>',
        "  </marker>",
        '  <marker id="arrow-gray" markerWidth="10" markerHeight="7" refX="9" refY="3.5" orient="auto">',
        '    <polygon points="0 0, 10 3.5, 0 7" fill="#6b7280"/>',
        "  </marker>",
        '  <marker id="inherit-open" markerWidth="10" markerHeight="7" refX="9" refY="3.5" orient="auto">',
        '    <path d="M 0 0 L 10 3.5 L 0 7 Z" fill="#ffffff" stroke="#2563eb" stroke-width="1.4"/>',
        "  </marker>",
        "  <style>",
        "    .canvas{fill:#ffffff}",
        '    .title{font-family:"Architects Daughter";font-size:42px;fill:#111827;font-weight:400}',
        '    .subtitle{font-family:"Comic Mono";font-size:16px;fill:#6b7280;font-weight:400}',
        '    .class-name{font-family:"Architects Daughter";font-size:22px;fill:#111827;font-weight:400}',
        '    .stereo{font-family:"Comic Mono";font-size:12px;fill:#6b7280;font-weight:400}',
        '    .member{font-family:"Comic Mono";font-size:13px;fill:#6b7280;font-weight:400}',
        '    .edge-label{font-family:"Comic Mono";font-size:12px;fill:#374151;font-weight:400}',
        '    .footer{font-family:"Comic Mono";font-size:13px;fill:#6b7280;font-weight:400}',
        "    .card{fill:#ffffff;stroke:#94a3b8;stroke-width:1.9}",
        "    .divider{stroke-width:1.4}",
        "    .dependency{stroke:#6b7280;stroke-width:1.8;fill:none;marker-end:url(#arrow-gray);stroke-linecap:round;stroke-linejoin:round;stroke-dasharray:6 4}",
        "    .inheritance{stroke:#2563eb;stroke-width:2.1;fill:none;marker-end:url(#inherit-open);stroke-linecap:round;stroke-linejoin:round}",
        "  </style>",
        "</defs>",
        f'<rect class="canvas" width="{diagram.width}" height="{diagram.height}"/>',
        f'<text class="title" x="70" y="82">{esc(diagram.title)}</text>',
        f'<text class="subtitle" x="74" y="116">{esc(diagram.subtitle)}</text>',
    ]


def render_node(node: ClassNode) -> list[str]:
    fill, stroke = PALETTE[node.color % len(PALETTE)]
    title_lines = wrap_words(node.title, max(8, int((node.w - 40) / 14.0)))
    title_start = node.y + 52 - (len(title_lines) - 1) * 12
    out = [
        f'<g id="{esc(node.key)}">',
        f'  <rect class="card" x="{node.x}" y="{node.y}" width="{node.w}" height="{node.h}" rx="8"/>',
        f'  <rect x="{node.x}" y="{node.y}" width="{node.w}" height="72" rx="8" fill="{fill}" stroke="none"/>',
        f'  <line class="divider" x1="{node.x}" y1="{node.y + 72}" x2="{node.right}" y2="{node.y + 72}" stroke="#d1d5db"/>',
        f'  <text class="stereo" x="{node.cx}" y="{node.y + 25}" text-anchor="middle">{esc(node.stereotype)}</text>',
    ]
    for index, line in enumerate(title_lines):
        out.append(f'  <text class="class-name" x="{node.cx}" y="{title_start + index * 24}" text-anchor="middle">{esc(line)}</text>')
    for index, member in enumerate(node.members):
        out.append(f'  <text class="member" x="{node.x + 18}" y="{node.y + 102 + index * 22}">{esc(member)}</text>')
    out.append("</g>")
    return out


def render_edge(edge: ClassEdge) -> list[str]:
    cls = "inheritance" if edge.kind == "inheritance" else "dependency"
    out = [f'<path class="{cls}" d="{path_d(edge.points)}"/>']
    if edge.label:
        mid = label_point(edge.points)
        label_w = max(70, len(edge.label) * 8 + 26)
        out.extend(
            [
                f'<rect x="{mid[0] - label_w / 2:.1f}" y="{mid[1] - 26:.1f}" width="{label_w}" height="24" rx="8" fill="#ffffff" stroke="#d1d5db" opacity="0.96"/>',
                f'<text class="edge-label" x="{mid[0]:.1f}" y="{mid[1] - 10:.1f}" text-anchor="middle">{esc(edge.label)}</text>',
            ]
        )
    return out


def render_diagram(diagram: ClassDiagram) -> str:
    out = svg_header(diagram)
    out.append('<g id="edges">')
    for edge in diagram.edges:
        out.extend(render_edge(edge))
    out.append("</g>")
    out.append('<g id="classes">')
    for node in diagram.nodes:
        out.extend(render_node(node))
    out.append("</g>")
    out.append(f'<text class="footer" x="{diagram.width / 2:.1f}" y="{diagram.height - 50}" text-anchor="middle">{esc(footer_text(diagram.base))}</text>')
    out.append("</svg>")
    return "\n".join(out) + "\n"


def footer_text(base: str) -> str:
    module = "images"
    return f"https://github.com/bluetape4k/bluetape4k-image | project: bluetape4k-image | module: {module}"


def validate_diagram(diagram: ClassDiagram) -> None:
    node_by_key = {node.key: node for node in diagram.nodes}
    for node in diagram.nodes:
        title_lines = wrap_words(node.title, max(8, int((node.w - 40) / 14.0)))
        if any(len(line) * 14 > node.w - 36 for line in title_lines):
            raise ValueError(f"{diagram.base}: title overflows {node.key}")
        if len(title_lines) > 2:
            raise ValueError(f"{diagram.base}: title wraps too much {node.key}")
        if node.x < 60 or node.y < 130 or node.right > diagram.width - 60 or node.bottom > diagram.height - 84:
            raise ValueError(f"{diagram.base}: node outside balanced canvas area {node.key}")
        for member in node.members:
            if len(member) * 8 > node.w - 36:
                raise ValueError(f"{diagram.base}: member overflows {node.key}: {member}")
    for i, first in enumerate(diagram.nodes):
        for second in diagram.nodes[i + 1 :]:
            if not (
                first.right + 22 <= second.x
                or second.right + 22 <= first.x
                or first.bottom + 22 <= second.y
                or second.bottom + 22 <= first.y
            ):
                raise ValueError(f"{diagram.base}: node overlap {first.key}<->{second.key}")
    validate_edges(diagram, node_by_key)


def point_on_boundary(point: tuple[int, int], node: ClassNode) -> bool:
    x, y = point
    on_vertical = x in (node.x, node.right) and node.y <= y <= node.bottom
    on_horizontal = y in (node.y, node.bottom) and node.x <= x <= node.right
    return on_vertical or on_horizontal


def segment_intersects_rect(
    a: tuple[int, int],
    b: tuple[int, int],
    node: ClassNode,
    padding: int,
) -> bool:
    x1, y1 = a
    x2, y2 = b
    left = node.x - padding
    right = node.right + padding
    top = node.y - padding
    bottom = node.bottom + padding
    if x1 == x2:
        return left <= x1 <= right and min(y1, y2) <= bottom and max(y1, y2) >= top
    if y1 == y2:
        return top <= y1 <= bottom and min(x1, x2) <= right and max(x1, x2) >= left
    raise ValueError("class diagram routes must use straight or orthogonal segments")


def validate_edges(diagram: ClassDiagram, node_by_key: dict[str, ClassNode]) -> None:
    for edge in diagram.edges:
        if edge.source not in node_by_key or edge.target not in node_by_key:
            raise ValueError(f"{diagram.base}: unknown edge endpoint {edge.source}->{edge.target}")
        source = node_by_key[edge.source]
        target = node_by_key[edge.target]
        if not point_on_boundary(edge.points[0], source):
            raise ValueError(f"{diagram.base}: edge {edge.source}->{edge.target} starts off source boundary")
        if not point_on_boundary(edge.points[-1], target):
            raise ValueError(f"{diagram.base}: edge {edge.source}->{edge.target} ends off target boundary")
        for a, b in zip(edge.points, edge.points[1:]):
            if a == b:
                raise ValueError(f"{diagram.base}: zero-length edge segment {edge.source}->{edge.target}")
            for node in diagram.nodes:
                if node.key in (edge.source, edge.target):
                    continue
                if segment_intersects_rect(a, b, node, padding=8):
                    raise ValueError(
                        f"{diagram.base}: edge {edge.source}->{edge.target} segment {a}->{b} crosses or crowds {node.key}"
                    )


def save(diagram: ClassDiagram) -> None:
    validate_diagram(diagram)
    svg_path = OUT / f"{diagram.base}.svg"
    svg_path.write_text(render_diagram(diagram), encoding="utf-8")
    subprocess.run(["rsvg-convert", str(svg_path), "-o", str(svg_path.with_suffix(".png"))], check=True)
    content = svg_path.read_text(encoding="utf-8")
    for forbidden in ("Inter", "Arial", "Helvetica"):
        if forbidden in content:
            raise ValueError(f"{svg_path} contains forbidden font {forbidden}")
    print(f"{diagram.base}: nodes={len(diagram.nodes)} edges={len(diagram.edges)} manual_exceptions=0")


def diagrams() -> tuple[ClassDiagram, ...]:
    return (
        ClassDiagram(
            base="images-class-core-01",
            title="Images Core API Classes",
            subtitle="Core loading, immutable transforms, batch processing, thumbnails, and analysis stay separate from filters and writers.",
            width=1840,
            height=1060,
            nodes=(
                ClassNode("sources", "Input Sources", "<<facade functions>>", ("immutableImageOf(...)", "suspendLoadImage(...)"), 100, 210, 360, 148, 0),
                ClassNode("image", "ImmutableImage", "<<Scrimage type>>", ("width, height, metadata", "copy(), filter(), output()"), 570, 210, 360, 148, 1),
                ClassNode("support", "ImmutableImageSupport", "<<extensions>>", ("suspendBytes(writer)", "suspendWrite(writer, sink)", "withGraphics { ... }"), 1040, 210, 390, 170, 2),
                ClassNode("format", "ImageFormat", "<<enum>>", ("JPG, PNG, GIF, WEBP", "TIFF, SVG, AVIF, HEIC"), 1480, 210, 280, 148, 3),
                ClassNode("scaler", "ImageScaler", "<<utility>>", ("scale(image, width, height)", "Scrimage-backed resize"), 110, 520, 340, 148, 4),
                ClassNode("splitter", "ImageSplitter", "<<utility>>", ("split(image, rows, cols)", "List<ImmutableImage>"), 520, 520, 340, 148, 5),
                ClassNode("batch", "ImageBatchFlow", "<<Flow API>>", ("processImages(options)", "writeImagesTo(outputDir)"), 930, 520, 370, 148, 6),
                ClassNode("thumbnail", "ThumbnailPipeline", "<<builder API>>", ("size(...), format(...)", "process(Flow<Path>)"), 1360, 520, 380, 148, 0),
                ClassNode("analysis", "Analysis APIs", "<<package>>", ("dominantColor(), readExif()", "blurScore(), similarity"), 520, 780, 400, 148, 1),
                ClassNode("transforms", "Transform APIs", "<<package>>", ("smartCropTo(), rotate()", "autoCrop(), equalize()"), 1030, 780, 400, 148, 2),
            ),
            edges=(
                ClassEdge("sources", "image", "", ((460, 284), (570, 284))),
                ClassEdge("image", "support", "", ((930, 284), (1040, 284))),
                ClassEdge("support", "format", "", ((1430, 284), (1480, 284))),
                ClassEdge("image", "scaler", "", ((750, 358), (750, 410), (280, 410), (280, 520))),
                ClassEdge("image", "splitter", "", ((750, 358), (750, 520))),
                ClassEdge("image", "batch", "", ((750, 358), (750, 450), (1115, 450), (1115, 520))),
                ClassEdge("image", "thumbnail", "", ((750, 358), (750, 430), (1550, 430), (1550, 520))),
            ),
            footer="Source evidence: ImmutableImageSupport, ImageBatchFlow, ThumbnailPipeline, scaler/splitter, analysis, and transforms packages.",
        ),
        ClassDiagram(
            base="images-class-filters-01",
            title="Images Filter Classes",
            subtitle="Filter DSL, color conversion helpers, and concrete Scrimage Filter implementations are isolated from writer classes.",
            width=1840,
            height=980,
            nodes=(
                ClassNode("filter", "Filter", "<<Scrimage interface>>", ("apply(image)", "native pipeline target"), 700, 180, 360, 148, 0),
                ClassNode("chain", "ImageFilterChain", "<<DSL builder>>", ("raw(filter), pixel { ... }", "compactAndApply(source)"), 1230, 180, 430, 170, 1),
                ClassNode("converter", "ColorSpaceConverter", "<<object>>", ("rgbToHsvInto(...)", "hsvToRgbInto(...)", "kelvinToRgb(kelvin)"), 1230, 470, 400, 170, 2),
                ClassNode("saturation", "SaturationAdjustFilter", "<<Filter>>", ("factor: Float", "uses HSV saturation"), 90, 700, 370, 148, 3),
                ClassNode("hue", "HueAdjustFilter", "<<Filter>>", ("deltaDegrees: Float", "rotates HSV hue"), 500, 700, 340, 148, 4),
                ClassNode("temperature", "ColorTemperatureFilter", "<<Filter>>", ("kelvin: Int", "Kelvin RGB balance"), 900, 700, 380, 148, 5),
                ClassNode("median", "MedianBlurFilter", "<<Filter>>", ("radius: Int", "MedianBoundaryMode"), 1320, 700, 360, 148, 6),
                ClassNode("rounded", "RoundedCornerFilter", "<<Filter>>", ("radius: Int", "alpha corner mask"), 90, 470, 360, 148, 0),
                ClassNode("support", "Watermark/Caption/Padding", "<<support functions>>", ("withGraphics overlays", "layout and padding helpers"), 90, 180, 430, 148, 1),
            ),
            edges=(
                ClassEdge("chain", "filter", "", ((1230, 264), (1060, 264))),
                ClassEdge("support", "filter", "", ((520, 254), (700, 254))),
                ClassEdge("chain", "converter", "", ((1445, 350), (1445, 470))),
                ClassEdge("rounded", "filter", "", ((270, 470), (270, 390), (880, 390), (880, 328)), "inheritance"),
                ClassEdge("saturation", "filter", "", ((275, 700), (275, 660), (880, 660), (880, 328)), "inheritance"),
                ClassEdge("hue", "filter", "", ((670, 700), (670, 660), (880, 660), (880, 328)), "inheritance"),
                ClassEdge("temperature", "filter", "", ((1090, 700), (1090, 660), (880, 660), (880, 328)), "inheritance"),
                ClassEdge("median", "filter", "", ((1500, 700), (1500, 660), (880, 660), (880, 328)), "inheritance"),
            ),
            footer="Source evidence: filters/* and filters/dsl/*; concrete filters implement Scrimage Filter while DSL composes native and pixel operations.",
        ),
        ClassDiagram(
            base="images-class-writers-01",
            title="Images Writer Classes",
            subtitle="Coroutine writers are grouped by single-image, multi-page, and animated output responsibilities.",
            width=1840,
            height=980,
            nodes=(
                ClassNode("context", "SuspendWriteContext", "<<value object>>", ("writer + image + metadata", "output helpers"), 90, 180, 360, 148, 2),
                ClassNode("imageWriter", "SuspendImageWriter", "<<interface>>", ("suspendWrite(image, out)", "write(image, out)"), 640, 180, 400, 148, 0),
                ClassNode("multi", "SuspendMultiPageImageWriter", "<<interface>>", ("suspendWrite(images, out)", "single-page extension"), 1250, 180, 460, 148, 1),
                ClassNode("tiff", "SuspendTiffWriter", "<<writer>>", ("TiffCompression, quality", "TwelveMonkeys ImageIO"), 90, 430, 400, 148, 0),
                ClassNode("tiffMulti", "SuspendTiffMultiPageWriter", "<<multi-page writer>>", ("ordered image pages", "DEFLATE/LZW/NONE/JPEG"), 1250, 430, 460, 148, 1),
                ClassNode("jpeg", "SuspendJpegWriter", "<<writer>>", ("compression, progressive", "Default, CompressionFromMetaData"), 90, 680, 380, 148, 3),
                ClassNode("png", "SuspendPngWriter", "<<writer>>", ("compressionLevel", "Max/Min/NoCompression"), 520, 680, 360, 148, 4),
                ClassNode("gif", "SuspendGifWriter", "<<writer>>", ("progressive", "Default, Progressive"), 930, 680, 350, 148, 5),
                ClassNode("webp", "SuspendWebpWriter", "<<writer>>", ("lossless, quality, method", "MaxLosslessCompression"), 1330, 680, 400, 148, 6),
            ),
            edges=(
                ClassEdge("context", "imageWriter", "", ((450, 264), (640, 264)), "dependency"),
                ClassEdge("tiffMulti", "multi", "", ((1480, 430), (1480, 328)), "inheritance"),
                ClassEdge("tiff", "imageWriter", "", ((290, 430), (290, 390), (840, 390), (840, 328)), "inheritance"),
                ClassEdge("jpeg", "imageWriter", "", ((280, 680), (280, 630), (840, 630), (840, 328)), "inheritance"),
                ClassEdge("png", "imageWriter", "", ((700, 680), (700, 630), (840, 630), (840, 328)), "inheritance"),
                ClassEdge("gif", "imageWriter", "", ((1105, 680), (1105, 630), (840, 630), (840, 328)), "inheritance"),
                ClassEdge("webp", "imageWriter", "", ((1530, 680), (1530, 630), (840, 630), (840, 328)), "inheritance"),
            ),
            footer="Source evidence: coroutines/Suspend*Writer.kt, SuspendWriteContext.kt, and animated writer extensions.",
        ),
    )


def main() -> int:
    OUT.mkdir(parents=True, exist_ok=True)
    for diagram in diagrams():
        save(diagram)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
