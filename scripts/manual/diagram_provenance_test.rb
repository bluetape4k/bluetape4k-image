#!/usr/bin/env ruby
# frozen_string_literal: true

require "minitest/autorun"
require "tmpdir"
require_relative "diagram_provenance"

class DiagramProvenanceTest < Minitest::Test
  def test_semantic_fingerprint_ignores_source_formatting
    Dir.mktmpdir("diagram-fingerprint") do |directory|
      first = File.join(directory, "first.svg")
      second = File.join(directory, "second.svg")
      svg = <<~SVG
        <svg xmlns="http://www.w3.org/2000/svg" width="1600" height="1040" viewBox="0 0 1600 1040">
          <title id="title">A</title>
          <desc id="desc">B</desc>
          <path id="edge" d="M0 0 H10" marker-end="url(#arrow)" stroke="#fff" stroke-width="4"/>
          <marker id="arrow" markerWidth="14" markerHeight="14" markerUnits="userSpaceOnUse" orient="auto" data-role="primary"/>
          <text x="10" y="20" font-family="Menlo" font-size="15">Label</text>
          <rect x="1" y="2" width="3" height="4" rx="1" fill="#000"/>
        </svg>
      SVG
      File.write(first, svg)
      File.write(second, svg.gsub(/>\s+</, "><").gsub("\n", "\r\n"))

      assert_equal DiagramProvenance.semantic_fingerprint(first), DiagramProvenance.semantic_fingerprint(second)
    end
  end

  def test_renderer_version_drift_is_actionable
    assert_equal ["renderer version drift: expected rsvg-convert version 2.62.3, actual rsvg-convert version 2.63.0"],
                 DiagramProvenance.toolchain_drift("rsvg-convert version 2.62.3", "rsvg-convert version 2.63.0")
  end

  def test_font_inventory_drift_is_actionable
    expected = {"Menlo" => "Menlo"}
    actual = {"Menlo" => "Noto Sans Mono"}
    assert_includes DiagramProvenance.font_drift(expected, actual).first, "font inventory drift"
    assert_includes DiagramProvenance.font_drift(expected, actual).first, "Noto Sans Mono"
  end

  def test_missing_font_fails_closed
    error = assert_raises(DiagramProvenance::ContractError) do
      DiagramProvenance.resolve_font("Bluetape4k Missing Font #{Process.pid}", strict: true)
    end

    assert_includes error.message, "font unavailable"
  end

  def test_png_metadata_records_dimensions_and_alpha
    metadata = DiagramProvenance.png_metadata(File.join(DiagramProvenance::ROOT, "docs/manual/assets/overview/repository-learning-map.png"))

    assert_equal({"width" => 3200, "height" => 2080, "bitDepth" => 8, "colorType" => 2, "alpha" => false}, metadata)
  end

  def test_png_semantic_fingerprint_rejects_same_sized_unrelated_asset
    first = File.join(DiagramProvenance::ROOT, "docs/manual/assets/architecture/processing-lifecycle.png")
    second = File.join(DiagramProvenance::ROOT, "docs/manual/assets/integrations/ocr-web-flow.png")

    assert_equal DiagramProvenance.png_metadata(first), DiagramProvenance.png_metadata(second)
    refute_equal DiagramProvenance.png_semantic_fingerprint(first), DiagramProvenance.png_semantic_fingerprint(second)
  end

  def test_manifest_keeps_tracked_baseline_and_render_receipt_separate
    manifest = DiagramProvenance::Manifest.new

    assert_equal "semantic-fingerprint", manifest.data.fetch("delivery").fetch("reproducibility").fetch("mode")
    manifest.data.fetch("assets").each do |asset|
      assert asset.fetch("pngSha256")
      assert asset.fetch("pngSemanticFingerprint")
      assert asset.fetch("renderedPngSha256")
      assert asset.fetch("renderedPngSemanticFingerprint")
    end
    assert_equal "rendered SVG source with the recorded renderer and scale; tracked PNG baseline is recorded separately",
                 manifest.data.fetch("delivery").fetch("reproducibility").fetch("renderReceipt")
  end

  def test_manifest_rejects_unexpected_generated_output
    manifest = DiagramProvenance::Manifest.new
    verifier = DiagramProvenance::Verifier.new

    Dir.mktmpdir("diagram-inventory") do |directory|
      manifest.assets.each do |asset|
        svg = File.join(directory, asset.svg.delete_prefix("docs/manual/assets/"))
        png = File.join(directory, asset.png.delete_prefix("docs/manual/assets/"))
        FileUtils.mkdir_p(File.dirname(svg))
        File.write(svg, "<svg/>")
        File.write(png, "not-a-real-png")
      end
      extra = File.join(directory, "unexpected/extra.svg")
      FileUtils.mkdir_p(File.dirname(extra))
      File.write(extra, "<svg/>")

      failures = verifier.send(:compare_inventory, directory, manifest)
      assert_includes failures, "generated SVG inventory drift: unexpected unexpected/extra.svg"
    end
  end

  def test_png_structure_checks_crc_and_iend
    source = File.join(DiagramProvenance::ROOT, "docs/manual/assets/overview/repository-learning-map.png")
    bytes = File.binread(source)

    Dir.mktmpdir("diagram-png-structure") do |directory|
      missing_iend = File.join(directory, "missing-iend.png")
      File.binwrite(missing_iend, bytes.byteslice(0, bytes.bytesize - 12))
      assert_raises(DiagramProvenance::ContractError) { DiagramProvenance.png_semantic_fingerprint(missing_iend) }

      corrupt_crc = File.join(directory, "corrupt-crc.png")
      corrupt = bytes.dup
      crc_offset = 8 + 4 + 4 + 13
      corrupt.setbyte(crc_offset, corrupt.getbyte(crc_offset) ^ 0x01)
      File.binwrite(corrupt_crc, corrupt)
      assert_raises(DiagramProvenance::ContractError) { DiagramProvenance.png_semantic_fingerprint(corrupt_crc) }
    end
  end

  def test_png_pixel_budget_fails_before_decode
    source = File.join(DiagramProvenance::ROOT, "docs/manual/assets/overview/repository-learning-map.png")
    bytes = File.binread(source).dup
    bytes[16, 4] = [65_000].pack("N")
    bytes[20, 4] = [65_000].pack("N")
    bytes[29, 4] = [Zlib.crc32(bytes.byteslice(12, 17))].pack("N")

    Dir.mktmpdir("diagram-png-budget") do |directory|
      oversized = File.join(directory, "oversized.png")
      File.binwrite(oversized, bytes)

      error = assert_raises(DiagramProvenance::ContractError) do
        DiagramProvenance.png_semantic_fingerprint(oversized)
      end
      assert_includes error.message, "pixel budget"
    end
  end

  def test_svg_fingerprint_includes_tspan_and_marker_geometry
    source = File.join(DiagramProvenance::ROOT, "docs/manual/assets/architecture/processing-lifecycle.svg")
    original = File.read(source)

    Dir.mktmpdir("diagram-svg-fingerprint") do |directory|
      tspan_path = File.join(directory, "tspan.svg")
      File.write(tspan_path, original.sub('x="800" y="96"', 'x="801" y="96"'))
      refute_equal DiagramProvenance.semantic_fingerprint(source), DiagramProvenance.semantic_fingerprint(tspan_path)

      marker_path = File.join(directory, "marker.svg")
      File.write(marker_path, original.sub("M0 0 L14 7 L0 14 Z", "M1 0 L14 7 L0 14 Z"))
      refute_equal DiagramProvenance.semantic_fingerprint(source), DiagramProvenance.semantic_fingerprint(marker_path)

      marker_placement_path = File.join(directory, "marker-placement.svg")
      File.write(marker_placement_path, original.sub('viewBox="0 0 14 14" refX="13" refY="7"', 'viewBox="0 0 12 14" refX="11" refY="6"'))
      refute_equal DiagramProvenance.semantic_fingerprint(source), DiagramProvenance.semantic_fingerprint(marker_placement_path)

      connector_transform_path = File.join(directory, "connector-transform.svg")
      File.write(connector_transform_path, original.sub('<g id="main-flow">', '<g id="main-flow" transform="translate(1 0)">'))
      refute_equal DiagramProvenance.semantic_fingerprint(source), DiagramProvenance.semantic_fingerprint(connector_transform_path)
    end
  end

  def test_manifest_rejects_duplicate_asset_identity
    data = YAML.safe_load(File.read(DiagramProvenance::DEFAULT_MANIFEST))
    data["assets"] << data["assets"].first.dup

    error = assert_raises(DiagramProvenance::ContractError) do
      DiagramProvenance::Manifest.new(data: data)
    end
    assert_includes error.message, "provenance asset id values must be unique"
  end

  def test_manifest_rejects_malformed_entries_and_unsafe_paths
    data = YAML.safe_load(File.read(DiagramProvenance::DEFAULT_MANIFEST))

    malformed_delivery = Marshal.load(Marshal.dump(data))
    malformed_delivery["delivery"] = nil
    error = assert_raises(DiagramProvenance::ContractError) do
      DiagramProvenance::Manifest.new(data: malformed_delivery)
    end
    assert_includes error.message, "delivery must be a mapping"

    malformed = Marshal.load(Marshal.dump(data))
    malformed["assets"] = [nil]
    error = assert_raises(DiagramProvenance::ContractError) do
      DiagramProvenance::Manifest.new(data: malformed)
    end
    assert_includes error.message, "asset entry must be a mapping"

    unsafe = Marshal.load(Marshal.dump(data))
    unsafe["assets"][0]["svg"] = "../../outside.svg"
    error = assert_raises(DiagramProvenance::ContractError) do
      DiagramProvenance::Manifest.new(data: unsafe)
    end
    assert_includes error.message, "svg must be a safe relative path"
  end
end
