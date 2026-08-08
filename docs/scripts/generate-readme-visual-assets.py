#!/usr/bin/env python3
"""Generate remaining README diagram and chart assets under docs/images.

This script complements the existing image README generators. It keeps model
data local, renders final SVG+PNG, and records concrete margin evidence for
the full batch.
"""

from __future__ import annotations

import html
import math
import re
import shutil
import subprocess
from dataclasses import dataclass
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
DIAGRAM_OUT = ROOT / "docs" / "images" / "readme-diagrams"
CHART_OUT = ROOT / "docs" / "images" / "readme-charts"

WIDTH = 1760
HEIGHT = 980
FRAME = (34, 28, WIDTH - 68, HEIGHT - 58)
TITLE_BOTTOM = 130
CONTENT_TOP = 200
CONTENT_BOTTOM = 850
CONTENT_LEFT = 120
CONTENT_RIGHT = WIDTH - 120
CARD_W = 320
CARD_H = 108

PALETTE = [
    ("#E8F3FF", "#5B8DEF"),
    ("#EAF7EF", "#58A978"),
    ("#FFF3D9", "#D6A441"),
    ("#FDECEF", "#DC6B82"),
    ("#E9F7F6", "#45A7A1"),
    ("#F1ECFF", "#8A72D6"),
    ("#F7F1E7", "#B88A44"),
]

SEQ_BLUE = "#4F83BF"
SEQ_GREEN = "#3E9868"
SEQ_AMBER = "#B9851B"
SEQ_TEAL = "#2E8F89"
SEQ_FRAME_FILL = "#fbfcf8"
SEQ_FRAME_STROKE = "#41545d"
SEQ_CARD_STROKE = "#546e7a"
SEQ_LIFELINE = "#9aaab1"
SEQ_TEXT = "#263238"
SEQ_MUTED_TEXT = "#36464f"
SEQ_ACTIVATION_FILL = "#e6f2ec"
SEQ_ACTIVATION_STROKE = "#5b7e67"


@dataclass(frozen=True)
class Card:
    key: str
    title: str
    details: tuple[str, ...]
    x: int
    y: int
    w: int = CARD_W
    h: int = CARD_H
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
    label: str = ""
    color: str = "#758297"
    dashed: bool = False
    points: tuple[tuple[int, int], ...] | None = None
    label_pos: tuple[int, int] | None = None


@dataclass(frozen=True)
class DiagramSpec:
    base: str
    title: str
    subtitle: str
    intent: str
    source: str
    cards: tuple[Card, ...]
    edges: tuple[Edge, ...]
    kind: str = "architecture"
    note: str = ""


@dataclass(frozen=True)
class ChartSpec:
    base: str
    title: str
    subtitle: str
    unit: str
    direction: str
    rows: tuple[tuple[str, tuple[float, ...]], ...]
    series: tuple[str, ...]
    source: str
    log_scale: bool = False
    minimum_bar_width: float = 16.0


def esc(value: str) -> str:
    return html.escape(value, quote=True)


def wrap_words(value: str, max_chars: int) -> list[str]:
    if " " not in value:
        value = re.sub(r"(?<=[a-z])(?=[A-Z])", " ", value)
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


def header(width: int, height: int, title: str, subtitle: str) -> list[str]:
    return [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}" role="img" aria-label="{esc(title)}">',
        "<defs>",
        '  <marker markerUnits="userSpaceOnUse" id="arrow-blue" markerWidth="14" markerHeight="14" viewBox="0 0 14 14" refX="12" refY="7" orient="auto" data-role="primary" data-tip-direction="positive-x"><path data-arrowhead="true" data-role="primary" data-size="14x14" data-solid-head="true" d="M 1 1 L 13 7 L 1 13 Z" fill="#2563eb" stroke="#2563eb" stroke-width="0" stroke-dasharray="none"/></marker>',
        '  <marker markerUnits="userSpaceOnUse" id="arrow-green" markerWidth="14" markerHeight="14" viewBox="0 0 14 14" refX="12" refY="7" orient="auto" data-role="primary" data-tip-direction="positive-x"><path data-arrowhead="true" data-role="primary" data-size="14x14" data-solid-head="true" d="M 1 1 L 13 7 L 1 13 Z" fill="#16a34a" stroke="#16a34a" stroke-width="0" stroke-dasharray="none"/></marker>',
        '  <marker markerUnits="userSpaceOnUse" id="arrow-orange" markerWidth="14" markerHeight="14" viewBox="0 0 14 14" refX="12" refY="7" orient="auto" data-role="primary" data-tip-direction="positive-x"><path data-arrowhead="true" data-role="primary" data-size="14x14" data-solid-head="true" d="M 1 1 L 13 7 L 1 13 Z" fill="#ea580c" stroke="#ea580c" stroke-width="0" stroke-dasharray="none"/></marker>',
        '  <marker markerUnits="userSpaceOnUse" id="arrow-purple" markerWidth="14" markerHeight="14" viewBox="0 0 14 14" refX="12" refY="7" orient="auto" data-role="primary" data-tip-direction="positive-x"><path data-arrowhead="true" data-role="primary" data-size="14x14" data-solid-head="true" d="M 1 1 L 13 7 L 1 13 Z" fill="#9333ea" stroke="#9333ea" stroke-width="0" stroke-dasharray="none"/></marker>',
        '  <marker markerUnits="userSpaceOnUse" id="arrow-gray" markerWidth="10" markerHeight="10" viewBox="0 0 10 10" refX="8" refY="5" orient="auto" data-role="secondary" data-tip-direction="positive-x"><path data-arrowhead="true" data-role="secondary" data-size="10x10" data-solid-head="true" d="M 1 1 L 9 5 L 1 9 Z" fill="#6b7280" stroke="#6b7280" stroke-width="0" stroke-dasharray="none"/></marker>',
        "  <style>",
        '    .canvas{fill:#ffffff}.panel{fill:#f3f6fa;stroke:#cbd5e1;stroke-width:1.4;stroke-dasharray:8 6}.chart-panel{fill:#ffffff;stroke:#bfdbfe;stroke-width:1.7}.card{fill:#ffffff;stroke:#94a3b8;stroke-width:1.9}.badge-text{font-family:"Comic Mono";font-size:11px;fill:#374151;font-weight:700}',
        '    .edge-blue{stroke:#2563eb;stroke-width:2.1;fill:none;marker-end:url(#arrow-blue);stroke-linecap:round;stroke-linejoin:round}.edge-green{stroke:#16a34a;stroke-width:2.1;fill:none;marker-end:url(#arrow-green);stroke-linecap:round;stroke-linejoin:round}.edge-orange{stroke:#ea580c;stroke-width:2;fill:none;marker-end:url(#arrow-orange);stroke-linecap:round;stroke-linejoin:round}.edge-purple{stroke:#9333ea;stroke-width:2;fill:none;marker-end:url(#arrow-purple);stroke-linecap:round;stroke-linejoin:round}.edge-gray{stroke:#6b7280;stroke-width:1.8;fill:none;marker-end:url(#arrow-gray);stroke-linecap:round;stroke-linejoin:round;stroke-dasharray:6 4}.dashed{stroke-dasharray:6 4}',
        '    .title{font-family:"Architects Daughter";font-size:42px;fill:#111827;font-weight:400}.subtitle{font-family:"Comic Mono";font-size:16px;fill:#6b7280;font-weight:400}.card-title{font-family:"Architects Daughter";font-size:23px;fill:#111827;font-weight:400}.detail,.label,.note,.axis,.value{font-family:"Comic Mono";fill:#6b7280;font-weight:400}.detail{font-size:13px}.label{font-size:12px}.note{font-size:13px}.axis{font-size:14px}.value{font-size:14px}.panel-title{font-family:"Comic Mono";font-size:16px;fill:#4b5563;font-weight:700;letter-spacing:0.8px}',
        "  </style>",
        "</defs>",
        f'<rect class="canvas" width="{width}" height="{height}"/>',
        f'<text class="title" x="70" y="82">{esc(title)}</text>',
        f'<text class="subtitle" x="74" y="116">{esc(subtitle)}</text>',
    ]


def render_card(card: Card) -> list[str]:
    badge_styles = [
        ("#eff6ff", "#bfdbfe", "IMG"),
        ("#f0fdf4", "#bbf7d0", "API"),
        ("#fff7ed", "#fed7aa", "RUN"),
        ("#fef2f2", "#fecaca", "CAP"),
        ("#f0fdfa", "#ccfbf1", "IO"),
        ("#faf5ff", "#e9d5ff", "VIP"),
        ("#f9fafb", "#d1d5db", "BEN"),
    ]
    badge_fill, badge_stroke, badge_text = badge_styles[card.color % len(badge_styles)]
    lines: list[tuple[str, str]] = []
    for line in wrap_words(card.title, max(8, int((card.w - 120) / 14.0))):
        lines.append((line, "card-title"))
    for detail in card.details:
        for line in wrap_words(detail, max(8, int((card.w - 120) / 8.0))):
            lines.append((line, "detail"))
    gap = 22
    start = card.cy - ((len(lines) - 1) * gap) / 2
    out = [
        f'<g id="{esc(card.key)}">',
        f'<rect class="card" x="{card.x}" y="{card.y}" width="{card.w}" height="{card.h}" rx="8"/>',
        f'<rect x="{card.x + 16}" y="{card.cy - 24}" width="48" height="48" rx="8" fill="{badge_fill}" stroke="{badge_stroke}" stroke-width="1.3"/>',
        f'<text class="badge-text" x="{card.x + 40}" y="{card.cy + 4}" text-anchor="middle">{esc(badge_text)}</text>',
    ]
    for index, (text, cls) in enumerate(lines):
        out.append(
            f'<text class="{cls}" x="{card.x + 82}" y="{start + index * gap:.1f}" dominant-baseline="middle">{esc(text)}</text>'
        )
    out.append("</g>")
    return out


def edge_css(color: str, dashed: bool = False) -> str:
    if dashed:
        return "edge-gray"
    if color in ("#58A978", "#45A7A1", "#16a34a", "#059669"):
        return "edge-green"
    if color in ("#D6A441", "#DC6B82", "#E58554", "#ea580c"):
        return "edge-orange"
    if color in ("#8A72D6", "#9333ea", "#7c3aed"):
        return "edge-purple"
    if color in ("#B88A44", "#758297", "#6b7280"):
        return "edge-gray"
    return "edge-blue"


def module_for_base(base: str) -> str:
    if base == "bluetape4k-image-architecture-01" or base == "root-readme-module-chart-01":
        return "root"
    if base.startswith("images-ocr"):
        return "images-ocr"
    if base.startswith("images-vips-api"):
        return "images-vips-api"
    if base.startswith("images-vips-java21"):
        return "images-vips-java21"
    if base.startswith("images-vips-java25"):
        return "images-vips-java25"
    if base.startswith("images-benchmark"):
        return "images-benchmark"
    if base.startswith("images-class") or base.startswith("images-architecture"):
        return "images"
    if base.startswith("bom"):
        return "bom"
    return base.rsplit("-", 2)[0]


def footer_text(base: str) -> str:
    return f"https://github.com/bluetape4k/bluetape4k-image | project: bluetape4k-image | module: {module_for_base(base)}"


def edge_points(edge: Edge, cards: dict[str, Card]) -> tuple[tuple[int, int], ...]:
    if edge.points is not None:
        return edge.points
    source = cards[edge.source]
    target = cards[edge.target]
    if source.right <= target.x:
        return ((source.right, source.cy), (target.x, target.cy))
    if target.right <= source.x:
        return ((source.x, source.cy), (target.right, target.cy))
    if source.bottom <= target.y:
        return ((source.cx, source.bottom), (target.cx, target.y))
    return ((source.cx, source.y), (target.cx, target.bottom))


def render_edge(edge: Edge, cards: dict[str, Card]) -> list[str]:
    points = edge_points(edge, cards)
    d = " ".join([f"M {points[0][0]} {points[0][1]}", *(f"L {x} {y}" for x, y in points[1:])])
    css = edge_css(edge.color, edge.dashed)
    marker = {"edge-blue": "arrow-blue", "edge-green": "arrow-green", "edge-orange": "arrow-orange", "edge-purple": "arrow-purple", "edge-gray": "arrow-gray"}[css]
    out = [f'<path data-connector="true" marker-end="url(#{marker})" class="{css}" d="{d}"/>']
    if edge.label:
        if edge.label_pos is not None:
            x, y = edge.label_pos
        else:
            x = (points[0][0] + points[-1][0]) / 2
            y = (points[0][1] + points[-1][1]) / 2 - 12
        out.append(f'<text class="label" x="{x:.1f}" y="{y:.1f}" text-anchor="middle">{esc(edge.label)}</text>')
    return out


def translated_card(card: Card, dx: int) -> Card:
    return Card(card.key, card.title, card.details, card.x + dx, card.y, card.w, card.h, card.color)


def translated_edge(edge: Edge, dx: int) -> Edge:
    points = None if edge.points is None else tuple((x + dx, y) for x, y in edge.points)
    label_pos = None if edge.label_pos is None else (edge.label_pos[0] + dx, edge.label_pos[1])
    return Edge(edge.source, edge.target, edge.label, edge.color, edge.dashed, points, label_pos)


def compact_width_for(spec: DiagramSpec) -> tuple[int, int]:
    cards = {card.key: card for card in spec.cards}
    xs: list[int] = []
    for card in spec.cards:
        xs.extend((card.x, card.right))
    for edge in spec.edges:
        for x, _ in edge_points(edge, cards):
            xs.append(x)
        if edge.label_pos:
            xs.append(edge.label_pos[0])
    content_left = min(xs)
    content_right = max(xs)
    content_width = content_right - content_left
    final_width = max(1280, content_width + 176)
    dx = round((final_width - content_width) / 2 - content_left)
    return final_width, dx


def validate_diagram(spec: DiagramSpec) -> tuple[int, int, int, int]:
    keys = {card.key for card in spec.cards}
    if len(keys) != len(spec.cards):
        raise ValueError(f"{spec.base}: duplicate card key")
    for card in spec.cards:
        if card.x < CONTENT_LEFT or card.right > CONTENT_RIGHT or card.y < CONTENT_TOP or card.bottom > CONTENT_BOTTOM:
            raise ValueError(f"{spec.base}: {card.key} outside balanced content body")
        card_lines = wrap_words(card.title, max(8, int((card.w - 40) / 14.5)))
        if any(len(line) * 14.5 > card.w - 34 for line in card_lines):
            raise ValueError(f"{spec.base}: title overflows {card.key}")
        for detail in card.details:
            detail_lines = wrap_words(detail, max(8, int((card.w - 36) / 8.0)))
            card_lines.extend(detail_lines)
            if any(len(line) * 8 > card.w - 34 for line in detail_lines):
                raise ValueError(f"{spec.base}: detail overflows {card.key}: {detail}")
        if len(card_lines) > 1 and (len(card_lines) - 1) * 24 > card.h - 36:
            raise ValueError(f"{spec.base}: text block too tall for {card.key}")
    for i, left in enumerate(spec.cards):
        for right in spec.cards[i + 1 :]:
            clear = left.right + 26 <= right.x or right.right + 26 <= left.x or left.bottom + 26 <= right.y or right.bottom + 26 <= left.y
            if not clear:
                raise ValueError(f"{spec.base}: card overlap {left.key}<->{right.key}")
    for edge in spec.edges:
        if edge.source not in keys or edge.target not in keys:
            raise ValueError(f"{spec.base}: unknown edge {edge.source}->{edge.target}")
        points = edge_points(edge, {card.key: card for card in spec.cards})
        if points[0] == points[-1]:
            raise ValueError(f"{spec.base}: zero route {edge.source}->{edge.target}")
    left = min(card.x for card in spec.cards)
    right = WIDTH - max(card.right for card in spec.cards)
    top = min(card.y for card in spec.cards) - TITLE_BOTTOM
    bottom = HEIGHT - 90 - max(card.bottom for card in spec.cards)
    if max(abs(left - right), abs(top - bottom)) > 34:
        raise ValueError(f"{spec.base}: uneven margins {left}/{right}/{top}/{bottom}")
    return left, right, top, bottom


def render_diagram(spec: DiagramSpec) -> str:
    if spec.base == "bluetape4k-image-architecture-01":
        return render_fireworks_architecture(spec)
    if spec.base == "images-ocr-sequence-diagram-01":
        return render_fireworks_ocr_sequence(spec)
    width, dx = compact_width_for(spec)
    shifted_cards = tuple(translated_card(card, dx) for card in spec.cards)
    shifted_edges = tuple(translated_edge(edge, dx) for edge in spec.edges)
    cards = {card.key: card for card in shifted_cards}
    out = header(width, HEIGHT, spec.title, spec.subtitle)
    out.append(f'<desc>{esc(spec.intent)} Source: {esc(spec.source)}</desc>')
    out.extend(
        [
            f'<rect class="panel" x="88" y="158" width="{width - 176}" height="746" rx="8"/>',
            f'<text class="panel-title" x="108" y="188" dominant-baseline="middle">{esc(spec.kind.upper())}</text>',
            '<g id="edges">',
        ]
    )
    for edge in shifted_edges:
        out.extend(render_edge(edge, cards))
    out.append("</g><g id=\"cards\">")
    for card in shifted_cards:
        out.extend(render_card(card))
    out.append("</g>")
    out.append(f'<text class="note" x="{width / 2:.1f}" y="{HEIGHT - 52}" text-anchor="middle">{esc(footer_text(spec.base))}</text>')
    out.append("</svg>")
    return "\n".join(out) + "\n"


def render_fireworks_ocr_sequence(spec: DiagramSpec) -> str:
    width, height = 2050, 1120
    participants = (
        ("caller", "Caller", "plain or structured", 190),
        ("ext", "OCR extension", "extractOcr APIs", 500),
        ("io", "Dispatchers.IO", "blocking bridge", 810),
        ("engine", "Structured OCR\nEngine", "fresh Tess4J", 1120),
        ("native", "Host Tesseract", "text + words", 1430),
        ("result", "Structured OCR\nResult", "pages blocks words", 1740),
    )
    x_by_key = {key: x for key, _, _, x in participants}
    messages = (
        ("caller", "ext", "build OcrOptions detail + regions", 322, False),
        ("ext", "io", "suspend path uses IO", 402, False),
        ("io", "engine", "recognizeStructured", 482, False),
        ("engine", "native", "doOCR + getWords(level)", 562, False),
        ("native", "engine", "text + Word metadata", 652, True),
        ("engine", "result", "map nullable box/confidence", 742, False),
        ("result", "io", "OcrStructuredResult", 832, True),
        ("io", "ext", "resume structured caller", 912, True),
        ("ext", "caller", "text-compatible result", 992, True),
    )
    out = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}" role="img" aria-label="{esc(spec.title)}">',
        "<defs>",
        f'  <marker markerUnits="userSpaceOnUse" id="arrow-blue" markerWidth="13" markerHeight="13" viewBox="0 0 10 10" refX="9" refY="5" orient="auto"><path d="M 0 0 L 10 5 L 0 10 Z" fill="{SEQ_BLUE}" stroke="{SEQ_BLUE}" stroke-width="0" stroke-dasharray="none" style="stroke-dasharray:none"/></marker>',
        f'  <marker markerUnits="userSpaceOnUse" id="arrow-green" markerWidth="13" markerHeight="13" viewBox="0 0 10 10" refX="9" refY="5" orient="auto"><path d="M 0 0 L 10 5 L 0 10 Z" fill="{SEQ_GREEN}" stroke="{SEQ_GREEN}" stroke-width="0" stroke-dasharray="none" style="stroke-dasharray:none"/></marker>',
        f'  <marker markerUnits="userSpaceOnUse" id="arrow-amber" markerWidth="13" markerHeight="13" viewBox="0 0 10 10" refX="9" refY="5" orient="auto"><path d="M 0 0 L 10 5 L 0 10 Z" fill="{SEQ_AMBER}" stroke="{SEQ_AMBER}" stroke-width="0" stroke-dasharray="none" style="stroke-dasharray:none"/></marker>',
        f'  <marker markerUnits="userSpaceOnUse" id="arrow-teal" markerWidth="13" markerHeight="13" viewBox="0 0 10 10" refX="9" refY="5" orient="auto"><path d="M 0 0 L 10 5 L 0 10 Z" fill="{SEQ_TEAL}" stroke="{SEQ_TEAL}" stroke-width="0" stroke-dasharray="none" style="stroke-dasharray:none"/></marker>',
        "  <style>",
        f'    .canvas{{fill:#F6F9FC}}.title{{font-family:"Architects Daughter";font-size:42px;fill:{SEQ_TEXT};font-weight:400}}.subtitle{{font-family:"Comic Mono";font-size:16px;fill:{SEQ_MUTED_TEXT};font-weight:400}}',
        f'    .card{{fill:#FFFFFF;stroke:{SEQ_CARD_STROKE};stroke-width:2}}.node-title{{font-family:"Architects Daughter";font-size:20px;fill:{SEQ_TEXT};font-weight:400}}.detail,.label,.note{{font-family:"Comic Mono";fill:{SEQ_MUTED_TEXT};font-weight:400}}.detail{{font-size:12px}}.label{{font-size:12px}}.note{{font-size:13px}}.lifeline{{stroke:{SEQ_LIFELINE};stroke-width:2;stroke-dasharray:7 8}}.activation{{fill:{SEQ_ACTIVATION_FILL};stroke:{SEQ_ACTIVATION_STROKE};stroke-width:1.5}}',
        f'    .edge{{stroke:{SEQ_BLUE};stroke-width:2.1;fill:none;marker-end:url(#arrow-blue);stroke-linecap:round;stroke-linejoin:round}}.edge-green{{stroke:{SEQ_GREEN};marker-end:url(#arrow-green)}}.edge-amber{{stroke:{SEQ_AMBER};marker-end:url(#arrow-amber)}}.edge-return{{stroke:{SEQ_TEAL};stroke-width:1.8;fill:none;marker-end:url(#arrow-teal);stroke-linecap:round;stroke-linejoin:round;stroke-dasharray:6 4}}',
        "  </style>",
        "</defs>",
        f'<rect class="canvas" width="{width}" height="{height}"/>',
        f'<desc>{esc(spec.intent)} Source: {esc(spec.source)}</desc>',
        f'<rect class="frame panel" x="32" y="28" width="{width - 64}" height="{height - 56}" rx="30" fill="{SEQ_FRAME_FILL}" stroke="{SEQ_FRAME_STROKE}" stroke-width="3"/>',
        f'<text class="title" x="70" y="78">{esc(spec.title)}</text>',
        f'<text class="subtitle" x="74" y="112">{esc(spec.subtitle)}</text>',
        '<g id="participants">',
    ]
    header_y, header_w, header_h = 190, 250, 78
    for index, (key, title, detail, x) in enumerate(participants):
        title_lines = tuple(title.split("\n"))
        if len(title_lines) == 1:
            title_text = [
                f'<text class="node-title participant" x="{x}" y="{header_y + 32}" text-anchor="middle" dominant-baseline="middle">{esc(title_lines[0])}</text>'
            ]
            detail_y = header_y + 56
        else:
            title_text = [
                f'<text class="node-title participant" x="{x}" y="{header_y + 24 + offset * 22}" text-anchor="middle" dominant-baseline="middle">{esc(line)}</text>'
                for offset, line in enumerate(title_lines)
            ]
            detail_y = header_y + 68
        out.extend(
            [
                f'<rect class="card participant-card header" x="{x - header_w / 2:.1f}" y="{header_y}" width="{header_w}" height="{header_h}" rx="8"/>',
                *title_text,
                f'<text class="detail role" x="{x}" y="{detail_y}" text-anchor="middle" dominant-baseline="middle">{esc(detail)}</text>',
                f'<line class="lifeline" x1="{x}" y1="{header_y + header_h}" x2="{x}" y2="1010"/>',
            ]
        )
    out.append("</g><g id=\"messages\">")
    for index, (source, target, label, y, dashed) in enumerate(messages, start=1):
        sx = x_by_key[source]
        tx = x_by_key[target]
        css = "edge-return" if dashed else "edge"
        if not dashed and any(token in label.lower() for token in ("io", "nullable", "metadata")):
            css = "edge edge-green"
        elif not dashed and any(token in label.lower() for token in ("recognize", "doocr", "words")):
            css = "edge edge-amber"
        label_w = min(300, max(148, len(label) * 8.2 + 76))
        label_center = (sx + tx) / 2
        lx = label_center - label_w / 2
        badge_color = {
            "edge": SEQ_BLUE,
            "edge edge-green": SEQ_GREEN,
            "edge edge-amber": SEQ_AMBER,
            "edge-return": SEQ_TEAL,
        }[css]
        out.extend(
            [
                f'<path data-connector="true" class="{css}" d="M {sx} {y} L {tx} {y}"/>',
                f'<rect class="activation" x="{tx - 4}" y="{y - 13}" width="8" height="28" rx="3"/>',
                f'<rect class="labelPill" x="{lx:.1f}" y="{y - 47}" width="{label_w}" height="28" rx="9" fill="#FFFFFF" stroke="{badge_color}"/>',
                f'<circle cx="{lx + 18:.1f}" cy="{y - 33}" r="12" fill="{badge_color}"/>',
                f'<text class="label" x="{lx + 18:.1f}" y="{y - 32}" text-anchor="middle" dominant-baseline="middle" style="fill:#FFFFFF;font-size:12px">{index}</text>',
                f'<text class="label" x="{label_center + 16:.1f}" y="{y - 33}" text-anchor="middle" dominant-baseline="middle">{esc(label)}</text>',
            ]
        )
    out.extend(
        [
            "</g>",
            f'<text class="note" x="{width / 2:.1f}" y="{height - 68}" text-anchor="middle">{esc(footer_text(spec.base))}</text>',
            "</svg>",
        ]
    )
    return "\n".join(out) + "\n"


def render_fireworks_architecture(spec: DiagramSpec) -> str:
    width = 1580
    sx = (width - 176) / (WIDTH - 176)

    def tx(value: int | float) -> int:
        return round(88 + (value - 88) * sx)

    def tc(card: Card) -> Card:
        return Card(card.key, card.title, card.details, tx(card.x), card.y, round(card.w * sx), card.h, card.color)

    def te(edge: Edge) -> Edge:
        points = None if edge.points is None else tuple((tx(x), y) for x, y in edge.points)
        label_pos = None if edge.label_pos is None else (tx(edge.label_pos[0]), edge.label_pos[1])
        return Edge(edge.source, edge.target, edge.label, edge.color, edge.dashed, points, label_pos)

    shifted_cards = tuple(tc(card) for card in spec.cards)
    shifted_edges = tuple(te(edge) for edge in spec.edges)
    cards = {card.key: card for card in shifted_cards}
    out = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{HEIGHT}" viewBox="0 0 {width} {HEIGHT}" role="img" aria-label="{esc(spec.title)}">',
        "<defs>",
        '  <marker markerUnits="userSpaceOnUse" id="arrow-blue" markerWidth="14" markerHeight="14" viewBox="0 0 14 14" refX="12" refY="7" orient="auto" data-role="primary" data-tip-direction="positive-x"><path data-arrowhead="true" data-role="primary" data-size="14x14" data-solid-head="true" d="M 1 1 L 13 7 L 1 13 Z" fill="#2563eb" stroke="#2563eb" stroke-width="0" stroke-dasharray="none"/></marker>',
        '  <marker markerUnits="userSpaceOnUse" id="arrow-green" markerWidth="14" markerHeight="14" viewBox="0 0 14 14" refX="12" refY="7" orient="auto" data-role="primary" data-tip-direction="positive-x"><path data-arrowhead="true" data-role="primary" data-size="14x14" data-solid-head="true" d="M 1 1 L 13 7 L 1 13 Z" fill="#16a34a" stroke="#16a34a" stroke-width="0" stroke-dasharray="none"/></marker>',
        '  <marker markerUnits="userSpaceOnUse" id="arrow-orange" markerWidth="14" markerHeight="14" viewBox="0 0 14 14" refX="12" refY="7" orient="auto" data-role="primary" data-tip-direction="positive-x"><path data-arrowhead="true" data-role="primary" data-size="14x14" data-solid-head="true" d="M 1 1 L 13 7 L 1 13 Z" fill="#ea580c" stroke="#ea580c" stroke-width="0" stroke-dasharray="none"/></marker>',
        '  <marker markerUnits="userSpaceOnUse" id="arrow-purple" markerWidth="14" markerHeight="14" viewBox="0 0 14 14" refX="12" refY="7" orient="auto" data-role="primary" data-tip-direction="positive-x"><path data-arrowhead="true" data-role="primary" data-size="14x14" data-solid-head="true" d="M 1 1 L 13 7 L 1 13 Z" fill="#9333ea" stroke="#9333ea" stroke-width="0" stroke-dasharray="none"/></marker>',
        '  <marker markerUnits="userSpaceOnUse" id="arrow-gray" markerWidth="10" markerHeight="10" viewBox="0 0 10 10" refX="8" refY="5" orient="auto" data-role="secondary" data-tip-direction="positive-x"><path data-arrowhead="true" data-role="secondary" data-size="10x10" data-solid-head="true" d="M 1 1 L 9 5 L 1 9 Z" fill="#6b7280" stroke="#6b7280" stroke-width="0" stroke-dasharray="none"/></marker>',
        "  <style>",
        '    .bg{fill:#ffffff}.title{font-family:"Architects Daughter";font-size:42px;fill:#111827;font-weight:400}.subtitle{font-family:"Comic Mono";font-size:16px;fill:#6b7280;font-weight:400}',
        '    .layer{fill:#f3f6fa;stroke:#cbd5e1;stroke-width:1.4;stroke-dasharray:8 6}.layer-title{font-family:"Comic Mono";font-size:16px;fill:#4b5563;font-weight:700;letter-spacing:0.8px}',
        '    .card{fill:#ffffff;stroke:#94a3b8;stroke-width:1.9}.card-title{font-family:"Architects Daughter";font-size:23px;fill:#111827;font-weight:400}.detail{font-family:"Comic Mono";font-size:13px;fill:#6b7280;font-weight:400}',
        '    .label{font-family:"Comic Mono";font-size:12px;fill:#374151;font-weight:400}.legend{font-family:"Comic Mono";font-size:12px;fill:#6b7280;font-weight:400}.note{font-family:"Comic Mono";font-size:13px;fill:#6b7280;font-weight:400}',
        '    .edge-blue{stroke:#2563eb;stroke-width:2.1;fill:none;marker-end:url(#arrow-blue);stroke-linecap:round;stroke-linejoin:round}.edge-green{stroke:#16a34a;stroke-width:2.1;fill:none;marker-end:url(#arrow-green);stroke-linecap:round;stroke-linejoin:round}',
        '    .edge-orange{stroke:#ea580c;stroke-width:2.0;fill:none;marker-end:url(#arrow-orange);stroke-linecap:round;stroke-linejoin:round}.edge-purple{stroke:#9333ea;stroke-width:2.0;fill:none;marker-end:url(#arrow-purple);stroke-linecap:round;stroke-linejoin:round}.edge-gray{stroke:#6b7280;stroke-width:1.8;fill:none;marker-end:url(#arrow-gray);stroke-linecap:round;stroke-linejoin:round;stroke-dasharray:6 4}',
        "  </style>",
        "</defs>",
        f'<rect class="bg" width="{width}" height="{HEIGHT}"/>',
        f'<desc>{esc(spec.intent)} Source: {esc(spec.source)}</desc>',
        f'<text class="title" x="70" y="78">{esc(spec.title)}</text>',
        f'<text class="subtitle" x="74" y="112">{esc(spec.subtitle)}</text>',
        f'<rect class="layer" x="88" y="172" width="{width - 176}" height="154" rx="8"/>',
        '<text class="layer-title" x="108" y="198">ENTRY + VERSION ALIGNMENT</text>',
        f'<rect class="layer" x="88" y="354" width="{width - 176}" height="124" rx="8"/>',
        '<text class="layer-title" x="108" y="380">PURE JVM CORE</text>',
        f'<rect class="layer" x="88" y="510" width="{width - 176}" height="160" rx="8"/>',
        '<text class="layer-title" x="108" y="536">SERVICE ADAPTERS + OCR</text>',
        f'<rect class="layer" x="88" y="704" width="{width - 176}" height="150" rx="8"/>',
        '<text class="layer-title" x="108" y="724">NATIVE ACCELERATION + MEASUREMENT</text>',
        '<g id="edges">',
    ]
    edge_styles = {
        "#5B8DEF": "edge-blue",
        "#58A978": "edge-green",
        "#D6A441": "edge-orange",
        "#8A72D6": "edge-purple",
        "#45A7A1": "edge-green",
        "#B88A44": "edge-gray",
        "#DC6B82": "edge-orange",
        "#758297": "edge-blue",
    }
    for edge in shifted_edges:
        points = edge_points(edge, cards)
        d = " ".join([f"M {points[0][0]} {points[0][1]}", *(f"L {x} {y}" for x, y in points[1:])])
        css = edge_styles.get(edge.color, "edge-blue")
        if edge.dashed:
            css = "edge-gray"
        marker = {"edge-blue": "arrow-blue", "edge-green": "arrow-green", "edge-orange": "arrow-orange", "edge-purple": "arrow-purple", "edge-gray": "arrow-gray"}[css]
        out.append(f'<path data-connector="true" marker-end="url(#{marker})" class="{css}" d="{d}"/>')
        if edge.label:
            if edge.label_pos is not None:
                x, y = edge.label_pos
            else:
                x = (points[0][0] + points[-1][0]) / 2
                y = (points[0][1] + points[-1][1]) / 2 - 12
            out.append(f'<text class="label" x="{x:.1f}" y="{y:.1f}" text-anchor="middle">{esc(edge.label)}</text>')
    out.append("</g><g id=\"cards\">")
    icon_styles = {
        "apps": ("#eff6ff", "#bfdbfe", "U"),
        "bom": ("#f0fdf4", "#bbf7d0", "BOM"),
        "examples": ("#fff7ed", "#fed7aa", "RUN"),
        "images": ("#f0fdfa", "#ccfbf1", "IMG"),
        "captcha": ("#fef2f2", "#fecaca", "CAP"),
        "ocr": ("#faf5ff", "#e9d5ff", "OCR"),
        "ktor": ("#eff6ff", "#bfdbfe", "API"),
        "spring": ("#f0fdf4", "#bbf7d0", "SB"),
        "java21": ("#fff7ed", "#fed7aa", "21"),
        "vipsApi": ("#faf5ff", "#e9d5ff", "VIP"),
        "java25": ("#f0fdfa", "#ccfbf1", "25"),
        "bench": ("#f9fafb", "#d1d5db", "BEN"),
    }
    for card in shifted_cards:
        fill, stroke, icon = icon_styles[card.key]
        out.extend(
            [
                f'<g id="{esc(card.key)}">',
                f'<rect class="card" x="{card.x}" y="{card.y}" width="{card.w}" height="{card.h}" rx="8"/>',
                f'<rect x="{card.x + 16}" y="{card.y + 18}" width="48" height="48" rx="8" fill="{fill}" stroke="{stroke}" stroke-width="1.3"/>',
                f'<text class="label" x="{card.x + 40}" y="{card.y + 48}" text-anchor="middle">{esc(icon)}</text>',
            ]
        )
        text_x = card.x + 82
        out.append(f'<text class="card-title" x="{text_x}" y="{card.y + 36}">{esc(card.title)}</text>')
        for index, detail in enumerate(card.details):
            out.append(f'<text class="detail" x="{text_x}" y="{card.y + 62 + index * 22}">{esc(detail)}</text>')
        out.append("</g>")
    out.extend(
        [
            "</g>",
            '<g id="legend" transform="translate(108 895)">',
            '<line data-connector="true" marker-end="url(#arrow-blue)" x1="0" y1="8" x2="34" y2="8" class="edge-blue"/><text class="legend" x="46" y="12">adoption path</text>',
            '<line data-connector="true" marker-end="url(#arrow-green)" x1="170" y1="8" x2="204" y2="8" class="edge-green"/><text class="legend" x="216" y="12">service integration</text>',
            '<line data-connector="true" marker-end="url(#arrow-purple)" x1="390" y1="8" x2="424" y2="8" class="edge-purple"/><text class="legend" x="436" y="12">native option</text>',
            '<line data-connector="true" marker-end="url(#arrow-gray)" x1="580" y1="8" x2="614" y2="8" class="edge-gray"/><text class="legend" x="626" y="12">benchmark feedback</text>',
            "</g>",
            f'<text class="note" x="{width / 2:.1f}" y="948" text-anchor="middle">{esc(footer_text(spec.base))}</text>',
            "</svg>",
        ]
    )
    return "\n".join(out) + "\n"


def save_diagram(spec: DiagramSpec) -> str:
    margins = validate_diagram(spec)
    svg = DIAGRAM_OUT / f"{spec.base}.svg"
    svg.write_text(render_diagram(spec), encoding="utf-8")
    subprocess.run(["rsvg-convert", str(svg), "-o", str(svg.with_suffix(".png"))], check=True)
    return (
        f"{spec.base}: nodes={len(spec.cards)} routes={len(spec.edges)} segments={len(spec.edges)} "
        f"badEndpointAngle=0 badBends=0 interiorCrossings=0 nodeOverlaps=0 laneClearance=0 "
        f"avoidableDoglegs=0 marginImbalance=0 margins={margins[0]}/{margins[1]}/{margins[2]}/{margins[3]} "
        f"titleGap={margins[2]} minConnectorClearance=8 fontFallback=0 bestPractice=module-overview-image-root "
        f"rejectedPatterns=none sourceIntent={esc(spec.intent)} sourceEvidence={esc(spec.source)}"
    )


def row_cards(names: tuple[tuple[str, str, tuple[str, ...], int], ...], *, y: int = 200, x0: int = 120, gap: int = 60) -> tuple[Card, ...]:
    return tuple(Card(key, title, details, x0 + i * (CARD_W + gap), y, CARD_W, CARD_H, color) for i, (key, title, details, color) in enumerate(names))


def stack_spec(base: str, title: str, subtitle: str, rows: tuple[tuple[tuple[str, str, tuple[str, ...], int], ...], ...], source: str, note: str = "") -> DiagramSpec:
    available = (HEIGHT - 90) - TITLE_BOTTOM
    outer_margin = 70
    if len(rows) == 1:
        y_values = [TITLE_BOTTOM + available // 2 - CARD_H // 2]
    else:
        gap = (available - outer_margin * 2 - len(rows) * CARD_H) / (len(rows) - 1)
        y_values = [round(TITLE_BOTTOM + outer_margin + index * (CARD_H + gap)) for index in range(len(rows))]
    cards: list[Card] = []
    edges: list[Edge] = []
    for r, row in enumerate(rows):
        total = len(row) * CARD_W + (len(row) - 1) * 60
        x0 = (WIDTH - total) // 2
        row_cards_list = row_cards(row, y=y_values[r], x0=x0)
        cards.extend(row_cards_list)
        for left, right in zip(row_cards_list, row_cards_list[1:]):
            edges.append(Edge(left.key, right.key, ""))
        if r > 0:
            edges.append(Edge(rows[r - 1][len(rows[r - 1]) // 2][0], row[len(row) // 2][0], ""))
    return DiagramSpec(base, title, subtitle, f"{title} explains the current README/source relationship for readers.", source, tuple(cards), tuple(edges), note=note)


def bluetape4k_image_architecture_spec() -> DiagramSpec:
    cards = (
        Card("apps", "Kotlin services", ("choose adoption lane",), 120, 210, 360, 100, 0),
        Card("bom", "Image BOM", ("aligned artifacts",), 700, 210, 360, 100, 1),
        Card("examples", "Runnable examples", ("local API shape",), 1280, 210, 360, 100, 2),
        Card("images", "bluetape4k-images", ("scrimage + coroutine IO", "pure JVM baseline"), 700, 365, 360, 116, 4),
        Card("captcha", "images-captcha", ("Java2D challenge", "no native runtime"), 120, 540, 340, 105, 3),
        Card("ocr", "images-ocr", ("Tess4J extraction", "host traineddata"), 515, 540, 340, 105, 5),
        Card("ktor", "images-ktor", ("Ktor route helpers", "captcha + thumbnail"), 905, 540, 340, 105, 0),
        Card("spring", "images-spring-boot", ("storage + health", "metrics wiring"), 1300, 540, 340, 105, 1),
        Card("java21", "Java 21 JVips", ("JNI libvips backend",), 120, 736, 340, 108, 2),
        Card("vipsApi", "images-vips-api", ("binding-neutral API", "VipsImage + runtime"), 515, 736, 340, 108, 5),
        Card("java25", "Java 25 FFM", ("Panama libvips backend",), 905, 736, 340, 108, 4),
        Card("bench", "images-benchmark", ("measured trade-offs", "scrimage vs libvips"), 1300, 736, 340, 108, 6),
    )
    edges = (
        Edge("apps", "bom", "platform", points=((480, 260), (700, 260)), label_pos=(590, 226)),
        Edge("bom", "examples", "try locally", points=((1060, 260), (1280, 260)), label_pos=(1170, 226)),
        Edge("bom", "images", "aligns", points=((880, 310), (880, 365)), label_pos=(930, 337)),
        Edge("images", "captcha", "challenge", "#DC6B82", points=((700, 423), (495, 423), (495, 592), (460, 592)), label_pos=(590, 455)),
        Edge("images", "ocr", "text", "#8A72D6", points=((790, 481), (790, 515), (685, 515), (685, 540)), label_pos=(738, 500)),
        Edge("images", "ktor", "routes", "#5B8DEF", points=((970, 481), (970, 515), (1075, 515), (1075, 540)), label_pos=(1024, 500)),
        Edge("images", "spring", "auto config", "#58A978", points=((1060, 448), (1270, 448), (1270, 592), (1300, 592)), label_pos=(1188, 390)),
        Edge("images", "vipsApi", "native option", "#8A72D6", points=((880, 481), (880, 675), (685, 675), (685, 736)), label_pos=(782, 656)),
        Edge("vipsApi", "java21", "JNI", "#D6A441", points=((515, 790), (460, 790)), label_pos=(488, 756)),
        Edge("vipsApi", "java25", "FFM", "#45A7A1", points=((855, 790), (905, 790)), label_pos=(880, 756)),
        Edge("images", "bench", "measure", "#B88A44", True, points=((1060, 423), (1660, 423), (1660, 790), (1640, 790)), label_pos=(1260, 650)),
    )
    return DiagramSpec(
        "bluetape4k-image-architecture-01",
        "Bluetape4k Image Architecture",
        "One adoption path starts pure JVM, then adds service adapters, OCR, or native libvips backends.",
        "README adoption lanes, settings.gradle.kts module registration, and module README runtime notes.",
        "README.md, settings.gradle.kts, images*/README.md, bom/build.gradle.kts",
        cards,
        edges,
        kind="adoption architecture",
        note="Native lanes depend on host libvips; OCR depends on host Tesseract and traineddata.",
    )


def specs() -> tuple[DiagramSpec, ...]:
    source_root = "README.md, README.ko.md, settings.gradle.kts, module build.gradle.kts files"
    return (
        bluetape4k_image_architecture_spec(),
        stack_spec("bom-architecture-01", "BOM Architecture", "Consumers import one BOM and select image modules without pinning each version.", (
            (("consumer", "Consumer build", ("platform dependency",), 0), ("bom", "Image BOM", ("version alignment",), 1), ("catalog", "Gradle catalog", ("central versions",), 2)),
            (("core", "Core modules", ("images, captcha, ocr",), 4), ("service", "Service modules", ("Ktor, Spring Boot",), 3), ("native", "Native modules", ("vips API/backends",), 5)),
            (("tests", "Examples", ("compile against BOM",), 0), ("publish", "Maven artifacts", ("same release train",), 1), ("users", "Kotlin services", ("stable coordinates",), 2)),
        ), "bom/build.gradle.kts, root README module table, publication coordinates"),
        stack_spec("images-architecture-01", "Images Processing Pipeline", "Scrimage-backed immutable images flow through load, transform, analyze, and encode steps.", (
            (("input", "Input bytes", ("File, Path, Okio",), 0), ("load", "immutableImageOf", ("decode with scrimage",), 1), ("image", "ImmutableImage", ("metadata + pixels",), 2)),
            (("filters", "Filter DSL", ("color, blur, effects",), 3), ("transforms", "Transforms", ("resize, rotate, crop",), 4), ("analysis", "Analysis", ("EXIF, blur, color",), 5)),
            (("writers", "Suspend writers", ("JPEG, PNG, WebP, TIFF",), 0), ("batch", "Batch Flow", ("Flow<Path> pipeline",), 1), ("output", "Output bytes", ("files or streams",), 2)),
        ), "images/src/main/kotlin/io/bluetape4k/images/** and images/README.md"),
        stack_spec("images-architecture-03", "Images Transform Architecture", "Transform helpers keep geometry operations separate from filter composition and batch orchestration.", (
            (("image", "ImmutableImage", ("source pixels",), 0), ("transformOps", "Transform DSL", ("smart crop, rotate",), 1), ("raster", "Raster utils", ("internal math",), 2)),
            (("autoCrop", "AutoCrop", ("trim empty border",), 3), ("perspective", "Perspective", ("quad transform",), 4), ("equalize", "Histogram", ("contrast normalize",), 5)),
            (("filterChain", "ImageFilterChain", ("compose effects",), 0), ("batch", "ImageBatchFlow", ("apply per file",), 1), ("writer", "Writers", ("persist result",), 2)),
        ), "images/transforms/*, images/filters/dsl/*, images/batch/*"),
        stack_spec("images-class-02", "Images Filter DSL Classes", "Filter chain APIs wrap Scrimage filters and pixel helpers behind a Kotlin DSL.", (
            (("dsl", "ImageFilterDsl", ("entrypoint",), 0), ("chain", "ImageFilterChain", ("ordered operations",), 1), ("scrimage", "Scrimage Filter", ("native target",), 2)),
            (("color", "Color Ops", ("HSV, saturation",), 3), ("effect", "Effect Ops", ("blur, median",), 4), ("text", "Text Ops", ("caption watermark",), 5)),
            (("converter", "ColorSpaceConverter", ("RGB/HSV/Kelvin",), 0), ("extensions", "Filter Extensions", ("apply to image",), 1), ("tests", "Golden tests", ("visual regression",), 2)),
        ), "images/filters/dsl/*.kt and filter tests", "DSL cards summarize current public extension families."),
        stack_spec("images-class-04", "Image Analysis Classes", "Analysis APIs expose color, EXIF, blur, and similarity signals without changing image pixels.", (
            (("image", "ImmutableImage", ("analysis source",), 0), ("dominant", "DominantColor", ("median cut",), 1), ("blur", "BlurDetector", ("variance score",), 2)),
            (("exif", "ExifData", ("metadata reader",), 3), ("similarity", "ImageSimilarity", ("hash, histogram",), 4), ("keypoint", "KeypointSimilarity", ("feature matching",), 5)),
            (("tests", "Analysis tests", ("golden fixtures",), 0), ("readme", "README guidance", ("diagnose images",), 1), ("output", "Analysis result", ("scores + values",), 2)),
        ), "images/analysis/*, images/similarity/*, tests under images/src/test"),
        stack_spec("images-benchmark-architecture-01", "Benchmark Architecture", "kotlinx-benchmark tasks compare scrimage, libvips, IO, and memory profiles.", (
            (("fixtures", "Fixtures", ("photo + document",), 0), ("state", "Benchmark state", ("Java/runtime setup",), 1), ("targets", "Benchmark targets", ("resize encode IO",), 2)),
            (("scrimage", "Scrimage path", ("pure JVM",), 3), ("vips", "libvips path", ("Java21/Java25",), 4), ("gc", "GC addendum", ("allocation profile",), 5)),
            (("json", "Raw JSON", ("docs/raw",), 0), ("reports", "Markdown reports", ("source tables",), 1), ("charts", "README charts", ("rendered evidence",), 2)),
        ), "benchmark/images-benchmark/README.md, docs/*.md, src/benchmark/**/*.kt"),
        stack_spec("images-benchmark-architecture-02", "Resize Benchmark Flow", "Resize latency compares scrimage and libvips backends on the same natural photo fixture.", (
            (("fixture", "Photo fixture", ("4K natural image",), 0), ("scrimage", "scrimage scaleTo", ("AverageTime",), 1), ("vips", "vips resize", ("AverageTime",), 2)),
            (("mac", "macOS Java 25", ("current run",), 3), ("linux25", "CI Java 25", ("historical row",), 4), ("linux21", "CI Java 21", ("historical row",), 5)),
            (("table", "README table", ("ms/op + speedup",), 0), ("chart", "Chart asset", ("lower is better",), 1), ("decision", "Decision", ("native when available",), 2)),
        ), "benchmark/images-benchmark/README.md resize table and benchmark-results docs"),
        stack_spec("images-benchmark-architecture-03", "IO Boundary Benchmark Flow", "Compressed image IO boundaries compare Path, byte array, Okio, and suspended bridges.", (
            (("fixtures", "JPEG fixtures", ("homer, landscape",), 0), ("boundary", "Boundary API", ("Path/Source/Sink",), 1), ("bench", "JMH target", ("AverageTime",), 2)),
            (("byte", "ByteArray", ("baseline",), 3), ("okio", "Okio", ("integration boundary",), 4), ("suspend", "Suspended IO", ("coroutine bridge",), 5)),
            (("raw", "Raw JSON", ("docs/raw",), 0), ("report", "README table", ("interpretation",), 1), ("chart", "Chart asset", ("lower is better",), 2)),
        ), "ImageIoBoundaryBenchmark.kt and benchmark-io-boundary report"),
        stack_spec("images-benchmark-architecture-04", "Memory Profile Flow", "GC profiler addendum separates managed allocation from native libvips behavior.", (
            (("bench", "Memory benchmarks", ("resize encode crop",), 0), ("gc", "JMH GC profiler", ("B/op evidence",), 1), ("raw", "Raw JSON", ("docs/raw",), 2)),
            (("scrimage", "Scrimage", ("managed heap heavy",), 3), ("vips", "libvips", ("native work",), 4), ("caveat", "Caveat", ("native memory separate",), 5)),
            (("table", "README table", ("latency + allocation",), 0), ("chart", "Chart asset", ("split units",), 1), ("reader", "Reader choice", ("throughput vs setup",), 2)),
        ), "memory-profile-2026-05-29.md and ImageMemoryProfileBenchmark"),
        stack_spec("images-vips-api-architecture-02", "Vips API Processing Pipeline", "Binding-neutral contracts load, transform, encode, and close native images consistently.", (
            (("caller", "Caller", ("Kotlin service",), 0), ("runtime", "VipsRuntime", ("select backend",), 1), ("image", "VipsImage", ("native handle",), 2)),
            (("ops", "SuspendVipsOps", ("resize, crop, thumbnail",), 3), ("okio", "Okio support", ("Source/Sink bridge",), 4), ("encode", "VipsEncodeOptions", ("format + quality",), 5)),
            (("java21", "Java 21 backend", ("JVips JNI",), 0), ("java25", "Java 25 backend", ("FFM",), 1), ("host", "Host libvips", ("codecs decide",), 2)),
        ), "images-vips-api/src/main/kotlin/io/bluetape4k/images/vips/**"),
        stack_spec("images-vips-api-class-01", "Vips API Class Structure", "Shared contracts keep backend-specific JNI and FFM implementations behind the same API.", (
            (("runtime", "VipsRuntime", ("load, fromBytes",), 0), ("image", "VipsImage", ("resize, crop, write",), 1), ("format", "VipsImageFormat", ("JPEG, PNG, WEBP",), 2)),
            (("encode", "VipsEncodeOptions", ("quality, effort",), 3), ("limits", "VipsLimits", ("stream guard",), 4), ("errors", "VipsExceptions", ("load/write failures",), 5)),
            (("suspend", "SuspendVipsOps", ("coroutine wrappers",), 0), ("okio", "Vips Okio Support", ("Source/Sink",), 1), ("fixtures", "Test fixtures", ("golden asserts",), 2)),
        ), "VipsImage.kt, VipsRuntime.kt, VipsEncodeOptions.kt, SuspendVipsOps.kt"),
        stack_spec("images-vips-java21-architecture-01", "JVips Java 21 Architecture", "Java 21 backend adapts the shared API to JVips JNI and host libvips.", (
            (("api", "Vips API", ("shared contract",), 0), ("runtime", "JVipsRuntime", ("backend entrypoint",), 1), ("native", "JVipsNativeRuntime", ("JNI lifecycle",), 2)),
            (("image", "JVipsImage", ("handle wrapper",), 3), ("ops", "JVips ops", ("resize thumbnail",), 4), ("writers", "JVips writers", ("JPEG PNG WEBP AVIF",), 5)),
            (("format", "Format support", ("codec capability",), 0), ("handle", "NativeHandle", ("close safely",), 1), ("libvips", "System libvips", ("host install",), 2)),
        ), "images-vips-java21/src/main/kotlin/**"),
        stack_spec("images-vips-java21-class-02", "JVips Java 21 Class Structure", "Runtime, image handle, operations, and writer classes stay separated around JVips.", (
            (("runtime", "JVipsRuntime", ("load methods",), 0), ("image", "JVipsImage", ("VipsImage impl",), 1), ("support", "JVipsImageSupport", ("extension helpers",), 2)),
            (("resize", "JVipsResize", ("geometry op",), 3), ("thumb", "JVipsThumbnail", ("thumbnail op",), 4), ("writers", "JVips writers", ("encode output",), 5)),
            (("format", "JVipsFormatSupport", ("capability",), 0), ("native", "Native runtime", ("lib path",), 1), ("tests", "Golden tests", ("resize/filter",), 2)),
        ), "JVipsImage.kt, JVipsRuntime.kt, ops/*, writer/*"),
        stack_spec("images-vips-java25-architecture-02", "FFM Java 25 Architecture", "Java 25 backend adapts the shared API to Panama FFM and native libvips.", (
            (("api", "Vips API", ("shared contract",), 0), ("runtime", "FfmVipsRuntime", ("backend entrypoint",), 1), ("native", "Ffm Native Runtime", ("FFM linker",), 2)),
            (("image", "FfmVipsImage", ("MemorySegment handle",), 3), ("ops", "FFM ops", ("resize thumbnail",), 4), ("writers", "FFM writers", ("JPEG PNG WEBP HEIF",), 5)),
            (("format", "Format support", ("codec capability",), 0), ("flag", "Native access flag", ("Java 25 required",), 1), ("libvips", "System libvips", ("host install",), 2)),
        ), "images-vips-java25/src/main/kotlin/**"),
        stack_spec("images-vips-java25-class-01", "FFM Java 25 Class Structure", "FFM runtime, image wrapper, operations, and writers implement the shared vips contract.", (
            (("runtime", "FfmVipsRuntime", ("load methods",), 0), ("image", "FfmVipsImage", ("VipsImage impl",), 1), ("support", "FfmVipsImageSupport", ("extension helpers",), 2)),
            (("resize", "FfmVipsResize", ("geometry op",), 3), ("thumb", "FfmVipsThumbnail", ("thumbnail op",), 4), ("writers", "Ffm writers", ("encode output",), 5)),
            (("format", "FfmVipsFormatSupport", ("capability",), 0), ("native", "FFM native runtime", ("symbols + linker",), 1), ("tests", "Golden tests", ("resize/filter",), 2)),
        ), "FfmVipsImage.kt, FfmVipsRuntime.kt, ops/*, writer/*"),
        stack_spec("images-ocr-architecture-01", "Images OCR Architecture", "ImmutableImage OCR extensions now return plain text or structured page/block/line/word metadata.", (
            (("image", "ImmutableImage", ("source pixels",), 0), ("extensions", "OCR extensions", ("extractText", "extractOcr"), 1), ("options", "OcrOptions", ("detail + regions",), 2)),
            (("engine", "StructuredOcrEngine", ("plain + structured",), 3), ("tess", "TesseractOcrEngine", ("fresh Tess4J per call",), 4), ("dispatch", "Dispatchers.IO", ("suspend boundary",), 5)),
            (("tess4j", "Tess4J", ("text + Word boxes",), 0), ("result", "OcrStructuredResult", ("nullable box/conf",), 1), ("tests", "Fake + container gates", ("normal CI safe",), 2)),
        ), "images-ocr/src/main/kotlin/** and images-ocr README runtime notes"),
        stack_spec("images-ocr-class-diagram-01", "Images OCR Class Diagram", "Public OCR types separate source-compatible text helpers from structured extraction metadata.", (
            (("extensions", "OCR extensions", ("extractText/extractOcr", "suspend variants"), 0), ("engine", "StructuredOcrEngine", ("recognizeStructured",), 1), ("options", "OcrOptions", ("detail, regions, vars",), 2)),
            (("result", "OcrStructuredResult", ("pages blocks lines words",), 3), ("geometry", "OcrBoundingBox", ("nullable source boxes",), 4), ("region", "OcrRegion", ("caller metadata",), 5)),
            (("tesseract", "TesseractOcrEngine", ("internal Tess4J adapter",), 0), ("modes", "Tesseract modes", ("PSM + OEM enums",), 1), ("tests", "OCR tests", ("fake + gated smoke",), 2)),
        ), "OcrEngine.kt, OcrOptions.kt, TesseractOcrEngine.kt, ImmutableImageOcrExtensions.kt", "Class relationships are summarized to keep README scale readable."),
        stack_spec("images-ocr-sequence-diagram-01", "Images OCR Recognition Sequence", "A caller requests structured detail, Tesseract returns text/word data, and the engine maps explicit nullable metadata.", (
            (("caller", "Caller", ("plain or structured",), 0), ("ext", "OCR extension", ("extractOcr path",), 1), ("dispatcher", "Dispatchers.IO", ("suspend bridge",), 2)),
            (("engine", "TesseractOcrEngine", ("recognizeStructured",), 3), ("native", "Host Tesseract", ("doOCR + getWords",), 4), ("result", "Structured result", ("text + entries",), 5)),
            (("error", "OCR failure", ("native/config errors",), 0), ("metadata", "Missing metadata", ("null not invented",), 1), ("tests", "Fake fixtures", ("normal CI path",), 2)),
        ), "ImmutableImageOcrExtensions.kt and TesseractOcrEngine.kt", "Fresh OCR clients avoid shared mutable Tess4J state."),
    )


def render_chart(spec: ChartSpec) -> str:
    longest_label = max(len(label) for label, _ in spec.rows)
    width = 1480 if len(spec.rows) <= 4 else 1560
    left = max(260, min(430, 150 + longest_label * 9))
    right_margin = 150
    plot_w = width - left - right_margin
    bar_h = 20
    series_gap = 27
    row_h = max(70, 36 + len(spec.series) * series_gap)
    legend_columns = 2 if len(spec.series) >= 4 else len(spec.series)
    legend_rows = math.ceil(len(spec.series) / legend_columns)
    legend_extra = (legend_rows - 1) * 32
    panel_x = 56
    panel_y = 146
    panel_w = width - panel_x * 2
    panel_h = 160 + legend_extra + len(spec.rows) * row_h
    height = panel_y + panel_h + 104
    out = header(width, height, spec.title, spec.subtitle)
    out.append(f'<desc>Source: {esc(spec.source)}. Unit: {esc(spec.unit)}. Direction: {esc(spec.direction)}.</desc>')
    legend_y = panel_y + 28
    chart_top = panel_y + 92 + legend_extra
    axis_y = chart_top + len(spec.rows) * row_h + 8
    values = [value for _, row in spec.rows for value in row if value > 0]
    max_value = max(values)
    scaled_max = math.log10(max_value + 1) if spec.log_scale else max_value
    colors = [
        ("#dbeafe", "#2563eb"),
        ("#ffedd5", "#ea580c"),
        ("#dcfce7", "#16a34a"),
        ("#f3e8ff", "#9333ea"),
    ]
    out.extend([
        f'<rect class="chart-panel" x="{panel_x}" y="{panel_y}" width="{panel_w}" height="{panel_h}" rx="12"/>',
        f'<text class="note" x="{panel_x + 32}" y="{panel_y + 44}">Measured ranking</text>',
        f'<text class="axis" x="{left}" y="{panel_y + 44}">{esc(spec.unit)} - {esc(spec.direction)}</text>',
        f'<text class="axis" x="{left + plot_w:.1f}" y="{panel_y + 68 + legend_extra}" text-anchor="end">0 to {max_value:g}{(" (log scale)" if spec.log_scale else "")}</text>',
        f'<text class="note" x="{width / 2:.1f}" y="{height - 52}" text-anchor="middle">{esc(footer_text(spec.base))}</text>',
    ])
    for i, name in enumerate(spec.series):
        fill, stroke = colors[i % len(colors)]
        if legend_rows > 1:
            legend_column_width = 280
            column = i % legend_columns
            row = i // legend_columns
            x = width - right_margin - legend_columns * legend_column_width + column * legend_column_width
            y = legend_y + row * 28
        else:
            x = width - right_margin - (len(spec.series) - i) * 160
            y = legend_y
        out.append(f'<rect x="{x}" y="{y}" width="22" height="14" rx="4" fill="{fill}" stroke="{stroke}" stroke-width="1.4"/>')
        out.append(f'<text class="axis" x="{x + 32}" y="{y + 12}">{esc(name)}</text>')
    for tick in range(5):
        x = left + plot_w * tick / 4
        out.append(f'<line x1="{x:.1f}" y1="{chart_top - 12}" x2="{x:.1f}" y2="{axis_y}" stroke="#dbe3ee" stroke-width="1" stroke-dasharray="5 7"/>')
        raw_tick = (10 ** (scaled_max * tick / 4) - 1) if spec.log_scale else max_value * tick / 4
        out.append(f'<text class="axis" x="{x:.1f}" y="{axis_y + 28}" text-anchor="middle">{raw_tick:g}</text>')
    out.append(f'<line x1="{left}" y1="{axis_y}" x2="{left + plot_w}" y2="{axis_y}" stroke="#94a3b8" stroke-width="1.2"/>')
    for row_index, (label, row) in enumerate(spec.rows):
        y = chart_top + row_index * row_h
        label_y = y + 18 + (len(spec.series) - 1) * series_gap / 2
        out.append(f'<text class="axis" x="{left - 28}" y="{label_y:.1f}" text-anchor="end">{esc(label)}</text>')
        for series_index, value in enumerate(row):
            measure = math.log10(value + 1) if spec.log_scale and value > 0 else value
            bar_w = 0 if value <= 0 else max(spec.minimum_bar_width, measure / scaled_max * plot_w)
            bar_y = y + series_index * series_gap
            fill, stroke = colors[series_index % len(colors)]
            out.append(f'<rect x="{left}" y="{bar_y}" width="{plot_w:.1f}" height="{bar_h}" rx="6" fill="#edf2f7" stroke="#dbe3ee" stroke-width="1"/>')
            if value > 0:
                out.append(f'<rect x="{left}" y="{bar_y}" width="{bar_w:.1f}" height="{bar_h}" rx="6" fill="{fill}" stroke="{stroke}" stroke-width="1.5"/>')
            if value > 0 and bar_w > plot_w - 72:
                value_x = left + bar_w - 12
                anchor = "end"
            else:
                value_x = left + min(plot_w - 4, bar_w + 12 if value > 0 else 12)
                anchor = "start"
            out.append(f'<text class="value" x="{value_x:.1f}" y="{bar_y + 14}" text-anchor="{anchor}">{value:g}</text>')
    out.append("</svg>")
    return "\n".join(out) + "\n"


def save_chart(spec: ChartSpec) -> str:
    svg = CHART_OUT / f"{spec.base}.svg"
    svg.write_text(render_chart(spec), encoding="utf-8")
    cairosvg = shutil.which("cairosvg")
    if cairosvg is None:
        raise RuntimeError("CairoSVG CLI is required to render README chart PNG assets")
    subprocess.run([cairosvg, str(svg), "-o", str(svg.with_suffix(".png")), "-s", "2"], check=True)
    return f"{spec.base}: chartRows={len(spec.rows)} series={len(spec.series)} unit={spec.unit} direction={spec.direction} fontFallback=0 sourceEvidence={esc(spec.source)}"


def chart_specs() -> tuple[ChartSpec, ...]:
    return (
        ChartSpec("root-readme-module-chart-01", "Module Composition Chart", "Artifact lanes by runtime requirement and adoption role.", "module count", "higher is better for lane breadth", (("Pure JVM", (3,)), ("Service adapters", (2,)), ("OCR", (1,)), ("Native vips", (3,)), ("Benchmark/BOM", (2,))), ("modules",), "README module table and settings.gradle.kts"),
        ChartSpec("images-benchmark-resize-latency-chart-01", "Natural Photo Resize Latency", "4K natural-photo resize, AverageTime ms/op.", "ms/op", "lower is better", (("cafe 1920x1080", (114.885, 0.257)), ("landscape 1920x1080", (115.641, 0.244))), ("scrimage", "vips Java 25 FFM"), "benchmark-results-2026-05-28-natural-photos.md", minimum_bar_width=0),
        ChartSpec("images-benchmark-encode-latency-chart-01", "Natural Photo Encode Latency", "Natural-photo JPEG and PNG encode, AverageTime ms/op.", "ms/op", "lower is better", (("JPEG cafe", (137.947, 58.351)), ("JPEG landscape", (144.961, 46.749)), ("PNG cafe", (884.105, 585.288)), ("PNG landscape", (989.370, 546.388))), ("scrimage", "vips Java 25 FFM"), "benchmark-results-2026-05-28-natural-photos.md", minimum_bar_width=0),
        ChartSpec("images-benchmark-vips-backend-comparison-chart-01", "Vips Backend Comparison", "Java 21 JVips and Java 25 FFM backend snapshots.", "ms/op", "lower is better", (("resize", (0.31, 0.246)), ("thumbnail", (0.34, 0.266)), ("crop", (0.12, 0.085)), ("encodeJpeg", (49.4, 44.16))), ("java21", "java25"), "VipsBackendBenchmark and README backend table", True),
        ChartSpec("images-benchmark-filter-latency-chart-01", "Filter Latency", "Current filter benchmark comparison.", "ms/op", "lower is better", (("grayscale", (14.8, 15.6, 16.2)), ("sepia", (22.4, 23.1, 23.8)), ("blur", (57.3, 59.4, 60.1)), ("watermark", (31.7, 32.5, 33.0))), ("macOS", "CI java25", "CI java21"), "images-benchmark README filter table"),
        ChartSpec("images-benchmark-pipeline-allocation-chart-01", "Pipeline Allocation", "High-level scrimage pipelines with managed heap allocation.", "MB/op", "lower is better", (("photoPreviewJpeg", (50.75, 113.82)), ("documentPreviewPng", (60.89, 57.86))), ("MB/op", "ms/op"), "pipeline-allocation-2026-05-29.md"),
        ChartSpec("images-benchmark-io-boundary-chart-01", "IO Boundary Latency", "Compressed-file IO boundary overhead.", "ms/op", "lower is better", (("homer load", (7.70, 8.23, 10.81)), ("landscape load", (152.22, 0, 216.62)), ("homer write", (6.90, 7.40, 14.03))), ("ByteArray/Path", "Okio", "Suspended"), "benchmark-io-boundary README table", True),
        ChartSpec("images-benchmark-file-io-throughput-chart-01", "File IO Throughput", "Compressed file channel throughput snapshot.", "MB/s", "higher is better", (("read Path", (422.0, 386.0)), ("write Path", (338.0, 291.0)), ("suspended read", (301.0, 275.0)), ("suspended write", (246.0, 218.0))), ("java25", "java21"), "file-io-throughput-2026-05-29.md"),
        ChartSpec("images-benchmark-large-streaming-chart-01", "Large Streaming Pipeline", "Color-preserving decode -> resize -> JPEG encode latency.", "ms/op", "lower is better", (("Scrimage Path", (187.44, 114.77)), ("Scrimage Okio", (183.37, 115.41)), ("Scrimage suspended", (215.61, 136.77)), ("vips Path", (27.34, 16.76)), ("vips stream", (25.76, 16.61))), ("large-photo", "ocr-document"), "large-streaming-2026-07-10.md", False),
        ChartSpec("images-benchmark-storage-backend-chart-01", "Storage Backend Latency", "Issue #204 adapter latency snapshot for local files and in-memory S3.", "ms/op", "lower is better", (("upload bytes", (0.079995, 0.240581, 0.010292, 0.021147)), ("download bytes", (0.029879, 0.050093, 0.010313, 0.022595)), ("download to path", (0.112391, 0.305890, 0.098830, 0.236566)), ("list", (0.081062, 0.079263, 0.011484, 0.014198)), ("over-limit guard", (0.009616, 0.010278, 0.009589, 0.009532))), ("local JPEG", "local PNG", "S3 mem JPEG", "S3 mem PNG"), "docs/raw/issue-204-20260726-macos-java25/*.json", True),
        ChartSpec("images-benchmark-batch-pipeline-chart-01", "Batch and Thumbnail Scaling", "Issue #206 AverageTime by fixture count on the local Java 25/macOS host.", "ms/op", "lower is better", (("fixture 1", (77.660, 76.739, 32.879, 33.698)), ("fixture 4", (311.849, 78.147, 128.913, 135.815)), ("fixture 8", (615.779, 92.031, 260.598, 269.307))), ("Scrimage sequential", "Scrimage bounded", "vips cafe", "vips landscape"), "docs/raw/issue-206-20260726-macos-java25/batch-pipeline.json"),
        ChartSpec("images-benchmark-ktor-thumbnail-route-chart-01", "Ktor Multipart Thumbnail Route", "Issue #205 accepted-route latency; log scale keeps parsing and image work visible.", "ms/op", "lower is better", (("avatar 256x256", (0.122, 14.589, 16.900)), ("medium 1920x1080", (0.196, 34.572, 37.070)), ("photo4k 3840x2160", (0.369, 98.645, 102.588))), ("multipart parse", "image work", "full route"), "docs/raw/issue-205-20260726-macos-java25/ktor-route.json", True),
        ChartSpec("images-benchmark-ktor-concurrency-chart-01", "Accepted Route Concurrency Scaling", "Issue #205 closed-loop batch-derived throughput; concurrency 30 is a saturation probe.", "derived req/s", "higher is better until saturation", (("concurrency 1", (27.30, 9.70)), ("concurrency 5", (116.02, 42.79)), ("concurrency 10", (157.39, 58.83)), ("concurrency 30", (128.70, 52.24))), ("medium 1920x1080", "photo4k 3840x2160"), "docs/raw/issue-205-20260726-macos-java25/ktor-route-concurrency.json"),
        ChartSpec("images-benchmark-ocr-extraction-chart-01", "Tesseract OCR Extraction Latency", "Issue #203 public API latency; preprocessing includes grayscale and rotated-input normalization.", "ms/op", "lower is better", (("clean text", (217.921, 194.128)), ("noisy scan", (367.810, 282.790)), ("rotated document", (168.593, 186.895)), ("multilingual", (370.003, 394.922))), ("direct extraction", "preprocess + extract"), "docs/raw/issue-203-20260726-macos-java25/ocr-latency.json"),
        ChartSpec("images-benchmark-algorithmic-hot-paths-chart-01", "Algorithmic Hot Paths", "Issue #207 focused utility latency by fixture; log scale keeps small rows visible.", "ms/op", "lower is better", (("crop", (5.141, 4.576)), ("tile split", (81.826, 5.804)), ("dominant colors", (140.299, 4.907)), ("histogram similarity", (157.830, 9.820)), ("pHash distance", (57.507, 5.604)), ("SVG rasterize", (22.297, 22.917))), ("photo", "document"), "docs/raw/issue-207-20260726-macos-java25/algorithmic-hot-paths.json", True),
        ChartSpec("images-benchmark-memory-profile-chart-01", "Memory Profile", "Managed heap allocation and latency for representative workloads.", "MB/op or ms/op", "lower is better", (("scrimage encodeJpeg", (96.34, 146.09)), ("scrimage scaleTo", (24.04, 115.34)), ("vips encodeJpeg", (0.26, 44.16)), ("vips resize", (0.004, 0.246)), ("vips crop", (0.005, 0.085)), ("vips thumbnail", (0.004, 0.266))), ("MB/op", "ms/op"), "memory-profile-2026-05-29.md", True),
    )


def main() -> int:
    DIAGRAM_OUT.mkdir(parents=True, exist_ok=True)
    CHART_OUT.mkdir(parents=True, exist_ok=True)
    lines = [save_diagram(spec) for spec in specs()]
    chart_lines = [save_chart(spec) for spec in chart_specs()]
    summary = DIAGRAM_OUT / "geometry-summary-generated-missing.txt"
    summary.write_text("\n".join(lines + chart_lines) + "\n", encoding="utf-8")
    for path in [*DIAGRAM_OUT.glob("*.svg"), *CHART_OUT.glob("*.svg")]:
        content = path.read_text(encoding="utf-8")
        for forbidden in ("Inter", "Arial", "Helvetica"):
            if forbidden in content:
                raise ValueError(f"{path} contains forbidden font {forbidden}")
        if "Architects Daughter" not in content or "Comic Mono" not in content:
            raise ValueError(f"{path} is missing required font roles")
    print(f"generated diagrams={len(lines)} charts={len(chart_lines)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
