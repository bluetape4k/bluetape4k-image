#!/usr/bin/env ruby

require "cgi"
require "fileutils"
require "open3"

ROOT = File.expand_path("../..", __dir__)
ASSETS = File.join(ROOT, "docs/manual/assets")
W = 1600
H = 1040

COLORS = {
  cyan: "#9ed8ff", teal: "#5eead4", purple: "#c4b5fd", amber: "#f6c96b",
  rose: "#fda4af", text: "#f8fafc", muted: "#b6c4d6", dim: "#91a4bb",
}.freeze

def esc(value)
  CGI.escapeHTML(value.to_s)
end

def defs
  markers = COLORS.slice(:cyan, :teal, :purple, :amber, :rose).map do |name, color|
    <<~SVG
      <marker id="arrow-#{name}" viewBox="0 0 14 14" refX="13" refY="7" markerWidth="14" markerHeight="14" orient="auto" markerUnits="userSpaceOnUse" data-role="primary" data-tip-direction="positive-x">
        <path d="M0 0 L14 7 L0 14 Z" fill="#{color}" stroke="#{color}" stroke-dasharray="none"/>
      </marker>
    SVG
  end.join
  <<~SVG
    <defs>
      <linearGradient id="canvas" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#0b1220"/><stop offset="0.58" stop-color="#152235"/><stop offset="1" stop-color="#213149"/></linearGradient>
      <linearGradient id="panel" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#1f2937"/><stop offset="1" stop-color="#101827"/></linearGradient>
      <filter id="shadow" x="-20%" y="-20%" width="140%" height="140%"><feDropShadow dx="0" dy="9" stdDeviation="10" flood-color="#020617" flood-opacity="0.38"/></filter>
      <filter id="glow" x="-20%" y="-20%" width="140%" height="140%"><feDropShadow dx="0" dy="0" stdDeviation="3" flood-color="#67e8f9" flood-opacity="0.2"/></filter>
      #{markers}
    </defs>
  SVG
end

def text_lines(x, y, lines, size: 18, color: COLORS[:muted], anchor: "middle", gap: 28, family: "ui-monospace, SFMono-Regular, Menlo, monospace", weight: 500)
  content = lines.each_with_index.map do |line, index|
    %(<tspan x="#{x}" y="#{y + index * gap}">#{esc(line)}</tspan>)
  end.join
  %(<text text-anchor="#{anchor}" font-family="#{family}" font-size="#{size}" font-weight="#{weight}" fill="#{color}">#{content}</text>)
end

def card(x, y, width, height, title, lines = [], color: COLORS[:cyan], tag: nil)
  title_y = y + 43
  lines_y = y + 80
  tag_svg = tag ? %(<text x="#{x + width - 20}" y="#{y + 26}" text-anchor="end" font-family="ui-monospace, monospace" font-size="13" fill="#{color}">#{esc(tag)}</text>) : ""
  <<~SVG
    <g filter="url(#shadow)">
      <rect x="#{x}" y="#{y}" width="#{width}" height="#{height}" rx="22" fill="#172033" stroke="#{color}" stroke-width="4"/>
      #{tag_svg}
      #{text_lines(x + width / 2, title_y, [title], size: 27, color: color, family: "Architects Daughter, Comic Sans MS, cursive", weight: 700)}
      #{text_lines(x + width / 2, lines_y, lines, size: 15, color: COLORS[:muted], gap: 25)}
    </g>
  SVG
end

def edge(id, d, color: :cyan, dashed: false, width: 4)
  dash = dashed ? ' stroke-dasharray="10 8"' : ""
  %(<path id="#{id}" d="#{d}" fill="none" stroke="#{COLORS.fetch(color)}" stroke-width="#{width}" stroke-linecap="round" stroke-linejoin="round"#{dash} marker-end="url(#arrow-#{color})"/>)
end

def canvas(title, subtitle, description, body)
  <<~SVG
    <svg xmlns="http://www.w3.org/2000/svg" width="#{W}" height="#{H}" viewBox="0 0 #{W} #{H}" role="img" aria-labelledby="title desc">
      <title id="title">#{esc(title)}</title><desc id="desc">#{esc(description)}</desc>
      #{defs}
      <rect width="#{W}" height="#{H}" fill="url(#canvas)"/>
      <rect x="42" y="34" width="1516" height="972" rx="34" fill="url(#panel)" stroke="#536377" stroke-width="4" filter="url(#shadow)"/>
      #{text_lines(800, 96, [title], size: 48, color: COLORS[:text], family: "Architects Daughter, Comic Sans MS, cursive", weight: 700)}
      #{text_lines(800, 137, [subtitle], size: 18, color: COLORS[:muted])}
      #{body}
    </svg>
  SVG
end

def repository_learning_map
  connectors = [
    edge("deps-core", "M800 270 V325", color: :purple),
    edge("core-model", "M800 445 V490 H300 V525", color: :cyan),
    edge("core-backend", "M800 445 V525", color: :teal),
    edge("core-framework", "M800 445 V490 H1300 V525", color: :amber),
    edge("model-workshops", "M300 675 V720 H570 V760", color: :cyan),
    edge("backend-workshops", "M800 675 V760", color: :teal),
    edge("framework-workshops", "M1300 675 V720 H1030 V760", color: :amber),
  ].join
  body = <<~SVG
    <g id="connectors" filter="url(#glow)">#{connectors}</g>
    #{card(360, 175, 880, 95, "bluetape4k-dependencies", ["the only version selected by the application"], color: COLORS[:purple])}
    #{card(510, 325, 580, 120, "bluetape4k-images", ["immutable loading / transforms / analysis", "filters / writers / coroutine I/O"], color: COLORS[:cyan])}
    #{card(105, 525, 390, 150, "Pure JVM processing", ["Scrimage immutable images", "Java2D drawing and CAPTCHA", "batch, filters, similarity"], color: COLORS[:cyan])}
    #{card(605, 525, 390, 150, "Native acceleration", ["JVips JNI / vips-ffm FFM", "native memory / codec capability"], color: COLORS[:teal])}
    #{card(1105, 525, 390, 150, "Integrations and ops", ["OCR / Spring Boot / Ktor", "storage / CDN / health / metrics"], color: COLORS[:amber])}
    #{card(185, 760, 1230, 145, "5 runnable workshops", ["start with basic-processing, then choose a web framework or native backend", "observe output files, OCR failures, native ownership, metrics, and recovery"], color: COLORS[:rose])}
    #{text_lines(800, 955, ["manual path: load -> transform -> encode -> serve -> operate"], size: 17, color: COLORS[:dim])}
  SVG
  canvas("Learn Image Processing from the Boundary Inward", "Image 0.3 / 9 artifacts / 5 workshops / 1 benchmark", "Repository and learning map for the Image 0.3 manual.", body)
end

def processing_lifecycle
  xs = [90, 335, 580, 825, 1070, 1315]
  titles = ["Load", "Decode", "Transform", "Observe", "Encode", "Close"]
  lines = [
    ["path / stream", "bounded bytes"], ["codec capability", "immutable image"],
    ["resize / filter", "draw on a copy"], ["metadata", "similarity / metrics"],
    ["format / quality", "destination"], ["native handles", "close exactly"],
  ]
  colors = %i[cyan teal purple amber rose teal]
  cards = xs.each_with_index.map { |x, i| card(x, 360, 195, 150, titles[i], lines[i], color: COLORS[colors[i]]) }.join
  edges = xs.each_cons(2).each_with_index.map do |(a, b), i|
    edge("step-#{i}", "M#{a + 195} 435 H#{b - 12}", color: colors[i])
  end.join
  body = <<~SVG
    #{cards}
    <g id="main-flow">#{edges}</g>
    #{card(180, 180, 500, 135, "Immutable JVM path", ["each transform returns a new image", "Java2D drawing mutates only the copied buffer"], color: COLORS[:cyan])}
    #{card(920, 180, 500, 135, "Explicit native path", ["JVips and FFM handles are AutoCloseable", "children never outlive their root image"], color: COLORS[:rose])}
    <g id="recovery" filter="url(#glow)">
      #{edge("retry", "M1412 510 V640 H188 V530", color: :amber, dashed: true)}
      #{edge("cancel", "M922 510 V700 H800 V780", color: :rose, dashed: true)}
    </g>
    #{card(560, 780, 480, 115, "Coroutine I/O boundary", ["suspend loading and writing use Dispatchers.IO", "cancellation is rethrown instead of hidden"], color: COLORS[:rose])}
    #{text_lines(800, 955, ["retry starts from a fresh source; never reuse a closed native handle"], size: 17, color: COLORS[:dim])}
  SVG
  canvas("An Image Pipeline Is a Resource Lifecycle", "load -> transform -> observe -> encode -> close", "Image processing lifecycle across immutable JVM images and explicitly owned native handles.", body)
end

def backend_decision_map
  body = <<~SVG
    <g id="connectors" filter="url(#glow)">
      #{edge("start-runtime", "M800 280 V340", color: :purple)}
      #{edge("runtime-jvm", "M800 455 V500 H280 V545", color: :cyan)}
      #{edge("runtime-jni", "M800 455 V545", color: :teal)}
      #{edge("runtime-ffm", "M800 455 V500 H1320 V545", color: :amber)}
      #{edge("jvm-contract", "M280 690 V745 H550 V790", color: :cyan)}
      #{edge("jni-contract", "M800 690 V790", color: :teal)}
      #{edge("ffm-contract", "M1320 690 V745 H1050 V790", color: :amber)}
    </g>
    #{card(430, 175, 740, 105, "What must the pipeline optimize?", ["choose the runtime only after the workload is clear"], color: COLORS[:purple])}
    #{card(540, 340, 520, 115, "Which runtime can the application own?", ["pure JVM / JNI + libvips / Java 25 FFM"], color: COLORS[:purple])}
    #{card(90, 545, 380, 145, "Scrimage + Java2D", ["portable immutable model", "simple deploy and broad codecs"], color: COLORS[:cyan])}
    #{card(610, 545, 380, 145, "JVips on JDK 25", ["mature JNI integration", "legacy java21 artifact name", "explicit native lifetime"], color: COLORS[:teal])}
    #{card(1130, 545, 380, 145, "vips-ffm on Java 25", ["Foreign Function and Memory API", "root arena owns child handles"], color: COLORS[:amber])}
    #{card(310, 790, 480, 120, "Capability check", ["verify codec support on the target host", "HEIC encode is not universal"], color: COLORS[:cyan])}
    #{card(810, 790, 480, 120, "Lifetime contract", ["use / close every native root deterministically", "never return a child after the root closes"], color: COLORS[:teal])}
    #{text_lines(800, 966, ["benchmark the complete load-transform-encode path on the deployment environment"], size: 17, color: COLORS[:dim])}
  SVG
  canvas("Choose the Backend After the Workload", "workload -> runtime -> capability -> lifetime", "Decision map for JVM, JNI, and FFM image-processing backends.", body)
end

def ocr_web_flow
  body = <<~SVG
    <g id="connectors" filter="url(#glow)">
      #{edge("request-spring", "M800 270 V335 H370 V390", color: :cyan)}
      #{edge("request-ktor", "M800 270 V335 H1230 V390", color: :amber)}
      #{edge("spring-image", "M370 600 V650 H760 V695", color: :cyan)}
      #{edge("ktor-image", "M1230 600 V650 H840 V695", color: :amber)}
    </g>
    #{card(430, 175, 740, 95, "Image request enters an application boundary", ["validate size, format, authentication, and rate limits first"], color: COLORS[:purple])}
    #{card(120, 390, 500, 210, "Spring Boot path", ["storage abstraction + auto-configuration", "local or S3-backed objects", "health and Micrometer observations", "application owns policy"], color: COLORS[:cyan])}
    #{card(980, 390, 500, 210, "Ktor path", ["thumbnail and CAPTCHA routes", "bounded request defaults", "application owns storage and JSON", "no hidden persistence"], color: COLORS[:amber])}
    #{card(560, 695, 480, 145, "ImmutableImage boundary", ["decode once, then transform or OCR", "recognition failures remain distinct"], color: COLORS[:teal])}
    #{card(125, 865, 420, 115, "Tesseract runtime", ["host language data + native libraries", "fresh client per recognition"], color: COLORS[:rose])}
    #{card(590, 865, 420, 115, "Storage and delivery", ["local or S3 object lifecycle", "CDN and cache policy stay explicit"], color: COLORS[:teal])}
    #{card(1055, 865, 420, 115, "Operations", ["limits / failures / latency / health", "test native paths in containers"], color: COLORS[:purple])}
    <g id="comparison-flow" filter="url(#glow)">
      #{edge("image-ocr", "M680 840 V852 H335 V865", color: :rose)}
      #{edge("image-storage", "M800 840 V865", color: :teal)}
      #{edge("image-ops", "M920 840 V852 H1265 V865", color: :purple)}
    </g>
  SVG
  canvas("Web Convenience Must Preserve Image Boundaries", "request validation -> framework adapter -> image -> OCR, storage, and operations", "Flow from Spring Boot or Ktor requests to image processing, OCR, storage, and operational boundaries.", body)
end

def benchmark_interpretation_map
  body = <<~SVG
    <g id="connectors" filter="url(#glow)">
      #{edge("question-resize", "M800 285 V327 Q800 335 792 335 H198 Q190 335 190 343 V390", color: :cyan)}
      #{edge("question-encode", "M800 285 V327 Q800 335 792 335 H503 Q495 335 495 343 V390", color: :teal)}
      #{edge("question-pipeline", "M800 285 V390", color: :purple)}
      #{edge("question-io", "M800 285 V327 Q800 335 808 335 H1097 Q1105 335 1105 343 V390", color: :amber)}
      #{edge("question-memory", "M800 285 V327 Q800 335 808 335 H1402 Q1410 335 1410 343 V390", color: :rose)}
      #{edge("cases-env", "M800 610 V675", color: :purple)}
      #{edge("env-decision", "M800 795 V850", color: :teal)}
    </g>
    #{card(500, 180, 600, 105, "Which production question matters?", ["one benchmark result cannot answer every question"], color: COLORS[:purple])}
    #{card(70, 390, 240, 220, "Resize", ["geometry only?", "decoded image?", "lazy vips work?", "avoid false wins"], color: COLORS[:cyan])}
    #{card(375, 390, 240, 220, "Encode", ["codec and quality", "output size", "native capability", "destination cost"], color: COLORS[:teal])}
    #{card(680, 390, 240, 220, "Full pipeline", ["load + decode", "transform", "encode + close", "best comparison"], color: COLORS[:purple])}
    #{card(985, 390, 240, 220, "I/O boundary", ["file / stream", "coroutine dispatch", "storage latency", "not CPU alone"], color: COLORS[:amber])}
    #{card(1290, 390, 240, 220, "Memory", ["managed heap", "native allocation", "peak lifetime", "measure both"], color: COLORS[:rose])}
    #{card(470, 675, 660, 120, "Environment validity", ["same inputs, warmup, codecs, CPU, libvips, JVM, and native-access flags", "record unavailable backends instead of inventing a winner"], color: COLORS[:purple])}
    #{card(390, 850, 820, 115, "Decision evidence", ["prefer ImageLargeStreamingBenchmark for end-to-end behavior", "repeat on the deployment host before choosing JNI, FFM, or pure JVM"], color: COLORS[:teal])}
  SVG
  canvas("Benchmark the Work You Actually Ship", "question -> representative case -> controlled environment -> decision", "Interpretation map for image resize, encode, full-pipeline, I/O, and memory benchmarks.", body)
end

DIAGRAMS = {
  "overview/repository-learning-map" => repository_learning_map,
  "architecture/processing-lifecycle" => processing_lifecycle,
  "backends/backend-decision-map" => backend_decision_map,
  "integrations/ocr-web-flow" => ocr_web_flow,
  "benchmarks/benchmark-interpretation-map" => benchmark_interpretation_map,
}.freeze

DIAGRAMS.each do |relative, svg|
  svg_path = File.join(ASSETS, "#{relative}.svg")
  png_path = File.join(ASSETS, "#{relative}.png")
  FileUtils.mkdir_p(File.dirname(svg_path))
  normalized_svg = svg.each_line.map(&:rstrip).join("\n") + "\n"
  File.write(svg_path, normalized_svg)
  stdout, stderr, status = Open3.capture3("rsvg-convert", "-w", (W * 2).to_s, "-h", (H * 2).to_s, "-o", png_path, svg_path)
  abort("render failed for #{relative}: #{stdout}#{stderr}") unless status.success?
end

puts "Rendered #{DIAGRAMS.length} dark Image diagrams as SVG and 2x PNG pairs."
