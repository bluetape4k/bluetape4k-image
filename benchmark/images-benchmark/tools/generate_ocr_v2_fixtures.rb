#!/usr/bin/env ruby
# frozen_string_literal: true

# Deterministically rebuilds the repo-owned OCR corpus v2 additions.
#
# The historical clean-text-v2-001 fixture remains in place because its
# receipt is part of the Issue #563 baseline. New v2 fixtures are generated
# from this script and are deliberately synthetic, local, and license-free.

require "digest"
require "fileutils"
require "json"
require "pathname"

ROOT = Pathname(__dir__).join("..").realpath
RESOURCE_ROOT = ROOT.join("src", "main", "resources")
CORPUS_ROOT = RESOURCE_ROOT.join("bench", "ocr-v2")
IMAGE_ROOT = CORPUS_ROOT.join("images")
TEXT_ROOT = CORPUS_ROOT.join("text")
BOX_ROOT = CORPUS_ROOT.join("boxes")
FONT = Pathname("/System/Library/Fonts/Supplemental/Arial Unicode.ttf")
FONT_RECEIPT = {
  "name" => "Arial Unicode MS",
  "sourceUrl" => "https://learn.microsoft.com/en-us/typography/font-list/arial-unicode-ms",
  "bytes" => 23_278_008,
  "sha256" => "876af2cd4854644e7f3e7feb2f688997fdb3343c6df6693611209c9dfb47ccec",
  "spdx" => "LicenseRef-Arial-Unicode-MS",
  "noticePath" => "docs/licenses/ocr-fixtures.txt"
}.freeze
LICENSE = {
  "component" => "generator",
  "spdx" => "Apache-2.0",
  "sourceUrl" => "https://imagemagick.org/script/license.php",
  "noticePath" => "docs/licenses/ocr-fixtures.txt"
}.freeze
SCENARIOS = %w[clean low-resolution noisy rotated table multi-column multilingual valid-blank].freeze

def sha256(path)
  Digest::SHA256.file(path).hexdigest
end

def bytes(path)
  path.size
end

def run!(*args)
  command = ["magick", *args]
  warn "> #{command.map { |value| value.include?(" ") ? value.inspect : value }.join(" ")}"
  abort "ImageMagick command failed: #{command.join(" ")}" unless system(*command)
end

def write_text(path, value)
  path.dirname.mkpath
  path.binwrite(value.encode("UTF-8"))
end

def image_receipt(path, width, height)
  {
    "path" => path.relative_path_from(RESOURCE_ROOT).to_s,
    "bytes" => bytes(path),
    "width" => width,
    "height" => height,
    "sha256" => sha256(path)
  }
end

def text_receipt(path)
  {
    "path" => path.relative_path_from(RESOURCE_ROOT).to_s,
    "bytes" => bytes(path),
    "sha256" => sha256(path),
    "encoding" => "UTF-8",
    "normalization" => "NFC+LF",
    "whitespacePolicy" => "PRESERVE"
  }
end

def boxes_receipt(path, schema_path, schema_sha256, schema_bytes)
  {
    "path" => path.relative_path_from(RESOURCE_ROOT).to_s,
    "bytes" => bytes(path),
    "sha256" => sha256(path),
    "schema" => "ocr-boxes-v1",
    "schemaResource" => {
      "path" => schema_path.relative_path_from(RESOURCE_ROOT).to_s,
      "bytes" => schema_bytes,
      "sha256" => schema_sha256
    },
    "coordinateSpace" => "pixel",
    "order" => "reading-order"
  }
end

def lines_for(scenario, index)
  case scenario
  when "clean"
    ["BLUETAPE OCR CLEAN #{index}", "Deterministic English document #{index}", "Tesseract corpus v2"]
  when "low-resolution"
    ["LOW RESOLUTION OCR #{index}", "Small text remains measurable", "Corpus v2"]
  when "noisy"
    ["NOISY SCAN OCR #{index}", "Synthetic noise is deterministic", "Corpus v2"]
  when "rotated"
    ["ROTATED DOCUMENT OCR #{index}", "Right rotation is a preprocessing case", "Corpus v2"]
  when "table"
    ["TABLE OCR #{index}", "Name | Count | Status", "alpha | 3 | ready", "beta | 5 | ready"]
  when "multi-column"
    ["MULTI COLUMN OCR #{index}", "Left column: invoice number #{index}", "Right column: total #{index * 10}", "Reading order is explicit"]
  when "multilingual"
    ["MULTILINGUAL OCR #{index}", "한국어 OCR 문서 #{index}", "日本語 OCR 文書 #{index}"]
  when "valid-blank"
    []
  else
    raise "unknown scenario: #{scenario}"
  end
end

def languages_for(scenario)
  scenario == "valid-blank" ? ["eng"] : ["eng", "kor", "jpn"]
end

def transformations_for(scenario)
  case scenario
  when "clean" then ["none"]
  when "low-resolution" then ["resize:50%"]
  when "noisy" then ["gaussian-noise:0.12"]
  when "rotated" then ["rotate:90deg"]
  when "table" then ["table-grid"]
  when "multi-column" then ["two-column"]
  when "multilingual" then ["unicode-script-mix"]
  when "valid-blank" then ["blank-canvas"]
  end
end

manifest_path = CORPUS_ROOT.join("manifest.json")
manifest = JSON.parse(manifest_path.read)
schema_path = CORPUS_ROOT.join("ocr-boxes-v1.schema.json")
schema_sha256 = sha256(schema_path)
schema_bytes = bytes(schema_path)

# Keep only the immutable Issue #563 baseline entry before rebuilding generated
# additions. This also makes repeated local replays idempotent.
manifest["fixtures"] = manifest["fixtures"].select { |fixture| fixture["fixtureId"] == "clean-text-v2-001" }
manifest["negatives"] = manifest["negatives"].select { |fixture| fixture["fixtureId"] == "malformed-v2-001" }
FileUtils.rm_rf([IMAGE_ROOT, TEXT_ROOT, BOX_ROOT])
2.upto(10) do |number|
  generated_negative = CORPUS_ROOT.join("malformed-%03d.bin" % number)
  File.delete(generated_negative) if generated_negative.exist?
end
[IMAGE_ROOT, TEXT_ROOT, BOX_ROOT].each(&:mkpath)

SCENARIOS.each do |scenario|
  indices = scenario == "clean" ? (2..3) : (1..3)
  indices.each do |index|
    fixture_id = "#{scenario}-v2-%03d" % index
    image_path = IMAGE_ROOT.join("#{fixture_id}.png")
    text_path = TEXT_ROOT.join("#{fixture_id}.txt")
    boxes_path = BOX_ROOT.join("#{fixture_id}.boxes.json")
    width, height =
      case scenario
      when "low-resolution" then [800, 500]
      when "rotated" then [1000, 1600]
      else [1600, 1000]
      end
    canvas_width, canvas_height = scenario == "low-resolution" ? [1600, 1000] : [1600, 1000]
    base_path = image_path.sub_ext(".base.png")
    lines = lines_for(scenario, index)

    if scenario == "valid-blank"
      run!("-size", "#{canvas_width}x#{canvas_height}", "xc:white", "-define", "png:exclude-chunk=date,time", image_path.to_s)
    else
      args = ["-size", "#{canvas_width}x#{canvas_height}", "xc:white", "-font", FONT.to_s,
              "-pointsize", scenario == "low-resolution" ? "20" : "38", "-fill", "black",
              "-stroke", "none"]
      lines.each_with_index do |line, line_index|
        args += ["-annotate", "+100+#{110 + line_index * 92}", line]
      end
      args += ["-define", "png:exclude-chunk=date,time", base_path.to_s]
      run!(*args)
      if scenario == "noisy"
        run!("-seed", "565#{index}", base_path.to_s, "-attenuate", "0.12", "+noise", "Gaussian",
             "-define", "png:exclude-chunk=date,time", image_path.to_s)
        File.delete(base_path)
      elsif scenario == "rotated"
        run!(base_path.to_s, "-rotate", "90", "-define", "png:exclude-chunk=date,time", image_path.to_s)
        File.delete(base_path)
      elsif scenario == "low-resolution"
        run!(base_path.to_s, "-resize", "50%", "-define", "png:exclude-chunk=date,time", image_path.to_s)
        File.delete(base_path)
      else
        base_path.rename(image_path)
      end
    end

    text = lines.empty? ? "" : "#{lines.join("\n")}\n"
    write_text(text_path, text)
    entries = lines.each_with_index.map do |line, line_index|
      {
        "boxId" => "#{fixture_id}-box-%02d" % line_index,
        "pageIndex" => 0,
        "text" => line,
        "x" => 100,
        "y" => 110 + line_index * 92,
        "width" => width - 200,
        "height" => 60,
        "order" => line_index
      }
    end
    boxes_path.write(JSON.pretty_generate({"schema" => "ocr-boxes-v1", "coordinateSpace" => "pixel", "entries" => entries}) + "\n")

    manifest["fixtures"] << {
      "fixtureId" => fixture_id,
      "scenario" => scenario,
      "sourceType" => "synthetic",
      "resource" => image_receipt(image_path, width, height),
      "languages" => languages_for(scenario),
      "transformations" => transformations_for(scenario),
      "groundTruth" => {
        "text" => text_receipt(text_path),
        "boxes" => boxes_receipt(boxes_path, schema_path, schema_sha256, schema_bytes)
      },
      "licenses" => [LICENSE],
      "provenance" => {"font" => FONT_RECEIPT},
      "expectedOutcome" => lines.empty? ? "EMPTY" : "TEXT"
    }
  end
end

2.times do |offset|
  number = offset + 2
  path = CORPUS_ROOT.join("malformed-%03d.bin" % number)
  path.binwrite("not-an-image-v2-#{number}\n".b)
  manifest["negatives"] << {
    "fixtureId" => "malformed-v2-%03d" % number,
    "scenario" => "malformed",
    "path" => path.relative_path_from(RESOURCE_ROOT).to_s,
    "bytes" => bytes(path),
    "sha256" => sha256(path),
    "expectedReason" => "DECODE_FAILED",
    "sourceType" => "synthetic",
    "expectedOutcome" => "ERROR"
  }
end

manifest["generator"] = {
  "name" => "issue-565-imagemagick-synthetic",
  "version" => "7.1.2-29",
  "command" => "ruby benchmark/images-benchmark/tools/generate_ocr_v2_fixtures.rb",
  "replayStatus" => "PENDING",
  "seed" => 565,
  "config" => {
    "path" => "bench/ocr-v2/generator.toml",
    "bytes" => 0,
    "sha256" => "0" * 64,
    "encoding" => "UTF-8",
    "normalization" => "NFC+LF",
    "spdx" => "Apache-2.0",
    "noticePath" => "docs/licenses/ocr-fixtures.txt"
  }
}

config = <<~CONFIG
  tool=ImageMagick
  version=7.1.2-29
  font=/System/Library/Fonts/Supplemental/Arial Unicode.ttf
  fixture=issue-565 synthetic OCR corpus v2
  scenarios=clean,low-resolution,noisy,rotated,table,multi-column,multilingual,valid-blank,malformed
  positive-fixtures=24
  negative-fixtures=3
  seed=565
  replay=PENDING (historical clean-text-v2-001 is retained from issue-563 baseline)
CONFIG
write_text(CORPUS_ROOT.join("generator.toml"), config)
manifest["generator"]["config"]["bytes"] = bytes(CORPUS_ROOT.join("generator.toml"))
manifest["generator"]["config"]["sha256"] = sha256(CORPUS_ROOT.join("generator.toml"))

manifest["fixtures"] = manifest["fixtures"].uniq { |fixture| fixture["fixtureId"] }
manifest["negatives"] = manifest["negatives"].uniq { |fixture| fixture["fixtureId"] }
manifest_path.write(JSON.pretty_generate(manifest) + "\n")
puts "generated #{manifest["fixtures"].length} positive and #{manifest["negatives"].length} negative OCR v2 receipts"
