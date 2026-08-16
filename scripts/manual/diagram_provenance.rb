#!/usr/bin/env ruby
# frozen_string_literal: true

require "digest"
require "fileutils"
require "json"
require "open3"
require "rexml/document"
require "rbconfig"
require "tmpdir"
require "yaml"
require "zlib"

module DiagramProvenance
  SCHEMA_VERSION = 1
  ROOT = File.expand_path("../..", __dir__).freeze
  DEFAULT_MANIFEST = File.join(ROOT, "docs/manual/diagram-provenance.yaml").freeze
  RENDERER_ARGUMENTS = ["-w", "3200", "-h", "2080"].freeze
  DEFAULT_FONT_FAMILIES = [
    "Architects Daughter", "Comic Sans MS", "cursive",
    "ui-monospace", "SFMono-Regular", "Menlo", "monospace",
  ].freeze
  MAX_PNG_PIXELS = 20_000_000
  MAX_INFLATED_PNG_BYTES = 128 * 1024 * 1024

  Asset = Struct.new(
    :id, :svg, :png, :svg_sha256, :semantic_fingerprint,
    :png_sha256, :png_semantic_fingerprint, :rendered_png_sha256,
    :rendered_png_semantic_fingerprint, :png_metadata,
    keyword_init: true,
  )

  class ContractError < StandardError
  end

  module_function

  def sha256(path)
    Digest::SHA256.file(path).hexdigest
  end

  def png_metadata(path)
    header, = png_chunks(path)
    width, height, bit_depth, color_type = header.unpack("NNCC")
    {
      "width" => width,
      "height" => height,
      "bitDepth" => bit_depth,
      "colorType" => color_type,
      "alpha" => [4, 6].include?(color_type),
    }
  end

  # Returns a renderer-tolerant content signature for a PNG.
  #
  # A PNG's IHDR only describes its container.  The provenance contract also
  # needs to detect a swapped or unrelated raster that happens to have the
  # expected dimensions.  Sampling a fixed grid with coarse colour buckets
  # keeps this signature stable enough for the documented semantic mode while
  # still making accidental asset substitution fail closed.
  def png_semantic_fingerprint(path, grid_width: 64, grid_height: 42)
    png = decode_png(path)
    samples = []
    (0...grid_height).each do |grid_y|
      y0 = grid_y * png[:height] / grid_height
      y1 = [(grid_y + 1) * png[:height] / grid_height, y0 + 1].max
      (0...grid_width).each do |grid_x|
        x0 = grid_x * png[:width] / grid_width
        x1 = [(grid_x + 1) * png[:width] / grid_width, x0 + 1].max
        red = green = blue = alpha = 0
        count = 0
        4.times do |sample_y|
          y = [y0 + ((sample_y + 0.5) * (y1 - y0) / 4).floor, png[:height] - 1].min
          4.times do |sample_x|
            x = [x0 + ((sample_x + 0.5) * (x1 - x0) / 4).floor, png[:width] - 1].min
            pixel = png[:pixels][y].byteslice(x * png[:channels], png[:channels]).bytes
            red += pixel[0]
            green += pixel[1] || pixel[0]
            blue += pixel[2] || pixel[0]
            alpha += pixel[3] || 255
            count += 1
          end
        end
        samples << [red / count / 16, green / count / 16, blue / count / 16, alpha / count / 16]
      end
    end
    Digest::SHA256.hexdigest(JSON.generate(
      "width" => png[:width], "height" => png[:height],
      "grid" => [grid_width, grid_height], "samples" => samples,
    ))
  end

  def decode_png(path)
    header, idat = png_chunks(path)

    width, height, bit_depth, color_type, compression, filter, interlace = header.unpack("NNCCCCC")
    raise ContractError, "#{path}: PNG dimensions must be positive" unless width.positive? && height.positive?
    raise ContractError, "#{path}: PNG pixel budget exceeded" if width * height > MAX_PNG_PIXELS
    unless bit_depth == 8 && compression == 0 && filter == 0 && interlace == 0 && [0, 2, 4, 6].include?(color_type)
      raise ContractError, "#{path}: unsupported PNG format for semantic fingerprint"
    end
    channels = {0 => 1, 2 => 3, 4 => 2, 6 => 4}.fetch(color_type)
    row_bytes = width * channels
    inflated = inflate_png(idat, path)
    expected_size = height * (row_bytes + 1)
    raise ContractError, "#{path}: PNG pixel data is truncated" unless inflated.bytesize == expected_size

    rows = []
    previous = Array.new(row_bytes, 0)
    height.times do |row_index|
      start = row_index * (row_bytes + 1)
      filter_type = inflated.getbyte(start)
      encoded = inflated.byteslice(start + 1, row_bytes).bytes
      decoded = Array.new(row_bytes, 0)
      row_bytes.times do |index|
        left = index >= channels ? decoded[index - channels] : 0
        up = previous[index]
        upper_left = index >= channels ? previous[index - channels] : 0
        decoded[index] = case filter_type
                         when 0 then encoded[index]
                         when 1 then (encoded[index] + left) & 0xff
                         when 2 then (encoded[index] + up) & 0xff
                         when 3 then (encoded[index] + ((left + up) / 2)) & 0xff
                         when 4 then (encoded[index] + paeth_predictor(left, up, upper_left)) & 0xff
                         else raise ContractError, "#{path}: unsupported PNG filter #{filter_type}"
                         end
      end
      rows << decoded.pack("C*")
      previous = decoded
    end
    {width: width, height: height, channels: channels, pixels: rows}
  rescue Zlib::Error => error
    raise ContractError, "#{path}: invalid PNG pixel data (#{error.message})"
  end

  def png_chunks(path)
    bytes = File.binread(path)
    signature = "\x89PNG\r\n\x1A\n".b
    raise ContractError, "#{path}: PNG signature is missing" unless bytes.start_with?(signature)

    offset = signature.bytesize
    header = nil
    idat = +"".b
    saw_idat = false
    saw_iend = false
    while offset < bytes.bytesize
      raise ContractError, "#{path}: truncated PNG chunk header" if offset + 12 > bytes.bytesize

      length = bytes.byteslice(offset, 4).unpack1("N")
      type = bytes.byteslice(offset + 4, 4)
      data = bytes.byteslice(offset + 8, length)
      crc_bytes = bytes.byteslice(offset + 8 + length, 4)
      raise ContractError, "#{path}: truncated PNG chunk" unless data && crc_bytes && data.bytesize == length
      expected_crc = crc_bytes.unpack1("N")
      actual_crc = Zlib.crc32(type + data)
      raise ContractError, "#{path}: PNG CRC mismatch in #{type}" unless expected_crc == actual_crc

      if header.nil?
        raise ContractError, "#{path}: PNG IHDR must be the first chunk" unless type == "IHDR" && length == 13
        header = data
      elsif type == "IHDR"
        raise ContractError, "#{path}: duplicate PNG IHDR"
      end

      case type
      when "IDAT"
        saw_idat = true
        idat << data
      when "IEND"
        raise ContractError, "#{path}: PNG IEND must be empty" unless length.zero?
        saw_iend = true
      end
      offset += 12 + length
      break if saw_iend
    end
    raise ContractError, "#{path}: PNG IEND is missing" unless saw_iend
    raise ContractError, "#{path}: trailing bytes follow PNG IEND" unless offset == bytes.bytesize
    raise ContractError, "#{path}: PNG IDAT is missing" unless saw_idat
    raise ContractError, "#{path}: PNG IHDR is missing" unless header

    width, height = header.unpack("NN")
    raise ContractError, "#{path}: PNG dimensions must be positive" unless width.positive? && height.positive?
    raise ContractError, "#{path}: PNG pixel budget exceeded" if width * height > MAX_PNG_PIXELS
    [header, idat]
  rescue Errno::ENOENT
    raise ContractError, "#{path}: PNG is missing"
  rescue Zlib::Error => error
    raise ContractError, "#{path}: invalid PNG structure (#{error.message})"
  end

  def inflate_png(idat, path)
    inflater = Zlib::Inflate.new
    inflated = +"".b
    0.step(idat.bytesize - 1, 8192) do |offset|
      inflated << inflater.inflate(idat.byteslice(offset, 8192))
      raise ContractError, "#{path}: PNG inflated data budget exceeded" if inflated.bytesize > MAX_INFLATED_PNG_BYTES
    end
    inflated << inflater.finish
    raise ContractError, "#{path}: PNG inflated data budget exceeded" if inflated.bytesize > MAX_INFLATED_PNG_BYTES

    inflated
  ensure
    inflater&.close
  end

  def paeth_predictor(left, up, upper_left)
    estimate = left + up - upper_left
    left_distance = (estimate - left).abs
    up_distance = (estimate - up).abs
    upper_left_distance = (estimate - upper_left).abs
    return left if left_distance <= up_distance && left_distance <= upper_left_distance
    return up if up_distance <= upper_left_distance

    upper_left
  end

  def transform_chain(node)
    transforms = []
    current = node
    while current.is_a?(REXML::Element)
      transform = current.attributes["transform"]
      transforms << transform unless transform.nil? || transform.empty?
      current = current.parent
    end
    transforms.reverse
  end

  def semantic_fingerprint(svg_path)
    document = REXML::Document.new(File.read(svg_path))
    root = document.root
    raise ContractError, "#{svg_path}: SVG root is missing" unless root
    payload = {
      "canvas" => {
        "width" => root.attributes["width"],
        "height" => root.attributes["height"],
        "viewBox" => root.attributes["viewBox"],
      },
      "labels" => REXML::XPath.match(document, "//text").map do |node|
        spans = node.elements.to_a("tspan")
        {
          "text" => spans.empty? ? node.texts.join : spans.map { |span| span.texts.join },
          "x" => node.attributes["x"],
          "y" => node.attributes["y"],
          "font" => node.attributes["font-family"],
          "size" => node.attributes["font-size"],
          "fill" => node.attributes["fill"],
          "anchor" => node.attributes["text-anchor"],
          "transform" => node.attributes["transform"],
          "spans" => spans.map do |span|
            {
              "text" => span.texts.join,
              "x" => span.attributes["x"],
              "y" => span.attributes["y"],
              "fill" => span.attributes["fill"],
              "transform" => span.attributes["transform"],
            }
          end,
        }
      end,
      "connectors" => REXML::XPath.match(document, "//path[@marker-end]").map do |node|
        {
          "id" => node.attributes["id"],
          "d" => node.attributes["d"],
          "markerEnd" => node.attributes["marker-end"],
          "stroke" => node.attributes["stroke"],
          "width" => node.attributes["stroke-width"],
          "dash" => node.attributes["stroke-dasharray"],
          "transform" => node.attributes["transform"],
          "transformChain" => transform_chain(node),
        }
      end.sort_by { |entry| entry["id"].to_s },
      "markers" => REXML::XPath.match(document, "//marker").map do |node|
        {
          "id" => node.attributes["id"],
          "viewBox" => node.attributes["viewBox"],
          "refX" => node.attributes["refX"],
          "refY" => node.attributes["refY"],
          "width" => node.attributes["markerWidth"],
          "height" => node.attributes["markerHeight"],
          "units" => node.attributes["markerUnits"],
          "orient" => node.attributes["orient"],
          "role" => node.attributes["data-role"],
          "tipDirection" => node.attributes["data-tip-direction"],
          "paths" => node.elements.to_a("path").map do |path|
            {
              "d" => path.attributes["d"],
              "fill" => path.attributes["fill"],
              "stroke" => path.attributes["stroke"],
              "strokeWidth" => path.attributes["stroke-width"],
              "dash" => path.attributes["stroke-dasharray"],
              "transform" => path.attributes["transform"],
            }
          end,
        }
      end.sort_by { |entry| entry["id"].to_s },
      "rectangles" => REXML::XPath.match(document, "//rect").map do |node|
        %w[x y width height rx fill stroke stroke-width].each_with_object({}) do |attribute, result|
          result[attribute] = node.attributes[attribute]
        end
      end,
    }
    Digest::SHA256.hexdigest(JSON.generate(payload))
  rescue Errno::ENOENT
    raise ContractError, "#{svg_path}: SVG source is missing"
  rescue REXML::ParseException => error
    raise ContractError, "#{svg_path}: invalid SVG XML (#{error.message})"
  end

  def renderer_version(command)
    stdout, stderr, status = Open3.capture3(command, "--version")
    output = (stdout.to_s + stderr.to_s).lines.first.to_s.strip
    raise ContractError, "renderer unavailable: #{command}" unless status.success? && !output.empty?

    output
  rescue Errno::ENOENT
    raise ContractError, "renderer unavailable: #{command}"
  end

  def resolve_font(family, strict: false)
    stdout, stderr, status = Open3.capture3("fc-match", "-f", "%{family}\\n", family)
    resolved = stdout.to_s.lines.first.to_s.strip
    if status.success? && !resolved.empty? && (!strict || resolved.split(",").map(&:strip).include?(family))
      return resolved
    end

    detail = stderr.to_s.lines.first.to_s.strip
    suffix = if detail.empty?
               " (resolved #{resolved.empty? ? "nothing" : resolved})"
             else
               " (#{detail})"
             end
    raise ContractError, "font unavailable: #{family}#{suffix}"
  rescue Errno::ENOENT
    raise ContractError, "font inventory unavailable: fc-match"
  end

  def font_inventory(families)
    families.each_with_object({}) { |family, result| result[family] = resolve_font(family) }
  end

  def toolchain_drift(expected, actual)
    return [] if expected == actual

    ["renderer version drift: expected #{expected}, actual #{actual}"]
  end

  def font_drift(expected, actual)
    return [] if expected == actual

    ["font inventory drift: expected #{JSON.generate(expected)}, actual #{JSON.generate(actual)}"]
  end

  def environment_snapshot(renderer, fonts)
    {
      "ruby" => RUBY_DESCRIPTION,
      "os" => `uname -sr 2>/dev/null`.to_s.strip,
      "architecture" => `uname -m 2>/dev/null`.to_s.strip,
      "renderer" => renderer,
      "fonts" => fonts,
    }
  end

  class Manifest
    attr_reader :path, :data

    def initialize(path = DEFAULT_MANIFEST, data: nil)
      @path = File.expand_path(path)
      @data = data || load
      validate!
    end

    def renderer
      data.fetch("delivery").fetch("renderer")
    end

    def font_families
      data.fetch("delivery").fetch("fonts").fetch("requested")
    end

    def assets
      data.fetch("assets").map do |entry|
        Asset.new(
          id: entry.fetch("id"),
          svg: entry.fetch("svg"),
          png: entry.fetch("png"),
          svg_sha256: entry.fetch("svgSha256"),
          semantic_fingerprint: entry.fetch("semanticFingerprint"),
          png_sha256: entry.fetch("pngSha256"),
          png_semantic_fingerprint: entry.fetch("pngSemanticFingerprint"),
          rendered_png_sha256: entry.fetch("renderedPngSha256"),
          rendered_png_semantic_fingerprint: entry.fetch("renderedPngSemanticFingerprint"),
          png_metadata: entry.fetch("pngMetadata"),
        )
      end
    end

    private

    def load
      YAML.safe_load(File.read(path))
    rescue Errno::ENOENT
      raise ContractError, "provenance manifest missing: #{path}"
    rescue Psych::Exception => error
      raise ContractError, "provenance manifest is invalid: #{error.message}"
    end

    def validate!
      raise ContractError, "provenance manifest must be a mapping" unless data.is_a?(Hash)
      raise ContractError, "provenance schemaVersion must be #{SCHEMA_VERSION}" unless data["schemaVersion"] == SCHEMA_VERSION
      source = data["source"]
      raise ContractError, "provenance source must be a mapping" unless source.is_a?(Hash)
      raise ContractError, "provenance source script is missing" unless source["script"].is_a?(String) && !source["script"].empty?
      raise ContractError, "provenance source script SHA-256 is missing" unless source["scriptSha256"].is_a?(String) && !source["scriptSha256"].empty?
      delivery = data["delivery"]
      raise ContractError, "provenance delivery must be a mapping" unless delivery.is_a?(Hash)
      renderer_data = delivery["renderer"]
      raise ContractError, "provenance renderer must be a mapping" unless renderer_data.is_a?(Hash)
      raise ContractError, "provenance renderer command is missing" unless renderer_data["command"].is_a?(String) && !renderer_data["command"].empty?
      raise ContractError, "provenance renderer version is missing" unless renderer_data["version"].is_a?(String) && !renderer_data["version"].empty?
      fonts = delivery["fonts"]
      raise ContractError, "provenance fonts must be a mapping" unless fonts.is_a?(Hash)
      raise ContractError, "provenance font list is missing" unless fonts["requested"].is_a?(Array) && !fonts["requested"].empty?
      reproducibility = delivery["reproducibility"]
      raise ContractError, "provenance reproducibility must be a mapping" unless reproducibility.is_a?(Hash)
      raise ContractError, "provenance reproducibility mode is missing" unless %w[byte-identity semantic-fingerprint].include?(reproducibility["mode"])
      raise ContractError, "provenance render receipt is missing" unless reproducibility["renderReceipt"].is_a?(String)
      raise ContractError, "provenance assets must be an array" unless data["assets"].is_a?(Array) && !data["assets"].empty?
      data["assets"].each do |entry|
        raise ContractError, "provenance asset entry must be a mapping" unless entry.is_a?(Hash)
        %w[id svg png svgSha256 semanticFingerprint pngSha256 pngSemanticFingerprint renderedPngSha256 renderedPngSemanticFingerprint pngMetadata].each do |key|
          raise ContractError, "provenance asset #{entry["id"] || "?"} missing #{key}" unless entry.key?(key)
        end
        %w[id svg png].each do |key|
          raise ContractError, "provenance asset #{entry["id"] || "?"} #{key} must be a string" unless entry[key].is_a?(String) && !entry[key].empty?
        end
        %w[svgSha256 semanticFingerprint pngSha256 pngSemanticFingerprint renderedPngSha256 renderedPngSemanticFingerprint].each do |key|
          unless entry[key].is_a?(String) && entry[key].match?(/\A[0-9a-f]{64}\z/)
            raise ContractError, "provenance asset #{entry["id"] || "?"} #{key} must be a SHA-256 hex string"
          end
        end
        %w[svg png].each do |key|
          raise ContractError, "provenance asset #{entry["id"]} #{key} must be a safe relative path" unless safe_relative_path?(entry[key])
        end
        png_metadata = entry["pngMetadata"]
        raise ContractError, "provenance asset #{entry["id"]} pngMetadata must be a mapping" unless png_metadata.is_a?(Hash)
        %w[width height bitDepth colorType].each do |key|
          raise ContractError, "provenance asset #{entry["id"]} pngMetadata missing #{key}" unless png_metadata[key].is_a?(Integer) && png_metadata[key].positive?
        end
        raise ContractError, "provenance asset #{entry["id"]} pngMetadata alpha must be boolean" unless [true, false].include?(png_metadata["alpha"])
      end
      %w[id svg png].each do |key|
        values = data["assets"].map { |entry| entry.fetch(key) }
        duplicates = values.group_by(&:itself).select { |_value, entries| entries.length > 1 }.keys
        raise ContractError, "provenance asset #{key} values must be unique: #{duplicates.join(", ")}" unless duplicates.empty?
      end
    rescue KeyError => error
      raise ContractError, "provenance manifest missing #{error.message}"
    end

    def safe_relative_path?(value)
      return false unless value.is_a?(String) && !value.empty? && !value.include?("\0")
      return false if value.start_with?("/", "\\") || value.include?("\\")

      root = File.expand_path("../..", File.dirname(path))
      resolved = File.expand_path(value, root)
      resolved == root || resolved.start_with?("#{root}#{File::SEPARATOR}")
    end
  end

  class Verifier
    attr_reader :root, :manifest, :render_script

    def initialize(root: ROOT, manifest_path: nil, render_script: nil)
      @root = File.expand_path(root)
      @manifest = manifest_path && Manifest.new(manifest_path)
      @render_script = File.expand_path(render_script || File.join(@root, "scripts/manual/render_image_diagrams.rb"))
      @png_semantic_cache = {}
    end

    def verify!(render: true)
      raise ContractError, "render script missing: #{render_script}" unless File.file?(render_script)
      @manifest ||= Manifest.new(File.join(root, "docs/manual/diagram-provenance.yaml"))
      manifest = @manifest
      renderer = manifest.renderer
      actual_renderer = DiagramProvenance.renderer_version(renderer.fetch("command"))
      failures = compare_source(manifest)
      failures.concat(compare_toolchain(manifest, actual_renderer))
      actual_fonts = DiagramProvenance.font_inventory(manifest.font_families)
      failures.concat(compare_fonts(manifest, actual_fonts))
      if render
        Dir.mktmpdir("diagram-provenance") do |directory|
          first = render_to(File.join(directory, "first"))
          second = render_to(File.join(directory, "second"))
          failures.concat(compare_repeated(first, second))
          failures.concat(compare_assets(first, manifest))
        end
      else
        failures.concat(compare_assets(File.join(root, "docs/manual/assets"), manifest))
      end
      raise ContractError, failures.join("\n") unless failures.empty?

      {
        "renderer" => actual_renderer,
        "fonts" => actual_fonts,
        "assets" => manifest.assets.length,
        "warnings" => @warnings || [],
      }
    end

    def self.build_manifest(root: ROOT, renderer_command:, font_families:)
      assets_root = File.join(root, "docs/manual/assets")
      renderer = DiagramProvenance.renderer_version(renderer_command)
      fonts = DiagramProvenance.font_inventory(font_families)
      render_script = File.join(root, "scripts/manual/render_image_diagrams.rb")
      raise ContractError, "render script missing: #{render_script}" unless File.file?(render_script)

      Dir.mktmpdir("diagram-provenance-baseline") do |directory|
        stdout, stderr, status = Open3.capture3(
          {"DIAGRAM_RENDERER" => renderer_command, "DIAGRAM_SCALE" => "2"},
          RbConfig.ruby, render_script, "--output-root", directory,
        )
        raise ContractError, "baseline render failed: #{stdout}#{stderr}" unless status.success?

        assets = Dir[File.join(assets_root, "**/*.svg")].sort.map do |svg_path|
          relative = svg_path.delete_prefix("#{root}/")
          png_path = svg_path.sub(/\.svg\z/, ".png")
          rendered_svg = File.join(directory, relative.delete_prefix("docs/manual/assets/"))
          rendered_png = rendered_svg.sub(/\.svg\z/, ".png")
          raise ContractError, "#{relative}: paired PNG missing" unless File.file?(png_path)
          raise ContractError, "#{relative}: baseline render changed SVG source" unless DiagramProvenance.sha256(rendered_svg) == DiagramProvenance.sha256(svg_path)
          id = relative.delete_prefix("docs/manual/assets/").sub(/\.svg\z/, "").tr("/", "-")
          metadata = DiagramProvenance.png_metadata(png_path)
          {
            "id" => id,
            "svg" => relative,
            "png" => png_path.delete_prefix("#{root}/"),
            "svgSha256" => DiagramProvenance.sha256(svg_path),
            "semanticFingerprint" => DiagramProvenance.semantic_fingerprint(svg_path),
            "pngSha256" => DiagramProvenance.sha256(png_path),
            "pngSemanticFingerprint" => DiagramProvenance.png_semantic_fingerprint(png_path),
            "renderedPngSha256" => DiagramProvenance.sha256(rendered_png),
            "renderedPngSemanticFingerprint" => DiagramProvenance.png_semantic_fingerprint(rendered_png),
            "pngMetadata" => metadata,
          }
        end
        {
          "schemaVersion" => SCHEMA_VERSION,
          "source" => {
            "script" => "scripts/manual/render_image_diagrams.rb",
            "scriptSha256" => DiagramProvenance.sha256(render_script),
            "assetsRoot" => "docs/manual/assets",
            "svgSize" => {"width" => 1600, "height" => 1040},
          },
          "delivery" => {
            "renderer" => {
              "command" => renderer_command,
              "version" => renderer,
              "arguments" => RENDERER_ARGUMENTS,
              "scale" => 2,
            },
            "fonts" => {
              "requested" => font_families,
              "resolved" => fonts,
              "inventorySha256" => Digest::SHA256.hexdigest(JSON.generate(fonts)),
            },
            "environment" => DiagramProvenance.environment_snapshot(renderer, fonts.dup),
            "reproducibility" => {
              "scope" => "controlled-delivery-toolchain",
              "mode" => "semantic-fingerprint",
              "byteIdentity" => "not-guaranteed-outside-recorded-renderer-and-font-inventory",
              "outsideScope" => "other renderer, font, OS, or architecture may change PNG bytes",
              "renderReceipt" => "rendered SVG source with the recorded renderer and scale; tracked PNG baseline is recorded separately",
            },
          },
          "assets" => assets,
        }
      end
    end

    private

    def render_to(directory)
      FileUtils.mkdir_p(directory)
      stdout, stderr, status = Open3.capture3(
        {"DIAGRAM_RENDERER" => manifest.renderer.fetch("command"), "DIAGRAM_SCALE" => manifest.renderer.fetch("scale", 2).to_s},
        RbConfig.ruby, render_script, "--output-root", directory,
      )
      raise ContractError, "render failed: #{stdout}#{stderr}" unless status.success?
      directory
    end

    def compare_toolchain(manifest, actual_renderer)
      expected = manifest.renderer.fetch("version")
      DiagramProvenance.toolchain_drift(expected, actual_renderer)
    end

    def compare_source(manifest)
      source = manifest.data.fetch("source")
      expected_script = File.join(root, source.fetch("script"))
      return ["provenance source script missing: #{expected_script}"] unless File.file?(expected_script)

      actual_hash = DiagramProvenance.sha256(expected_script)
      expected_hash = source.fetch("scriptSha256")
      return [] if actual_hash == expected_hash

      ["source script SHA-256 drift: expected #{expected_hash}, actual #{actual_hash}"]
    rescue KeyError => error
      ["provenance source contract missing #{error.message}"]
    end

    def compare_fonts(manifest, actual_fonts)
      expected = manifest.data.fetch("delivery").fetch("fonts").fetch("resolved")
      failures = DiagramProvenance.font_drift(expected, actual_fonts)
      expected_digest = manifest.data.fetch("delivery").fetch("fonts").fetch("inventorySha256")
      actual_digest = Digest::SHA256.hexdigest(JSON.generate(actual_fonts))
      failures << "font inventory SHA-256 drift: expected #{expected_digest}, actual #{actual_digest}" unless expected_digest == actual_digest
      failures
    end

    def compare_repeated(first, second)
      manifest_assets = manifest.assets
      manifest_assets.each_with_object([]) do |asset, failures|
        first_svg = File.join(first, asset.svg.delete_prefix("docs/manual/assets/"))
        second_svg = File.join(second, asset.svg.delete_prefix("docs/manual/assets/"))
        first_path = File.join(first, asset.svg.delete_prefix("docs/manual/assets/").sub(/\.svg\z/, ".png"))
        second_path = File.join(second, asset.svg.delete_prefix("docs/manual/assets/").sub(/\.svg\z/, ".png"))
        first_svg_hash = DiagramProvenance.sha256(first_svg)
        second_svg_hash = DiagramProvenance.sha256(second_svg)
        first_hash = DiagramProvenance.sha256(first_path)
        second_hash = DiagramProvenance.sha256(second_path)
        failures << "#{asset.id}: repeated SVG SHA-256 differs (#{first_svg_hash} vs #{second_svg_hash})" unless first_svg_hash == second_svg_hash
        failures << "#{asset.id}: repeated PNG SHA-256 differs (#{first_hash} vs #{second_hash})" unless first_hash == second_hash
      end
    end

    def compare_assets(generated_root, manifest)
      failures = compare_inventory(generated_root, manifest)
      manifest.assets.each do |asset|
        generated_svg = File.join(generated_root, asset.svg.delete_prefix("docs/manual/assets/"))
        generated_png = File.join(generated_root, asset.png.delete_prefix("docs/manual/assets/"))
        tracked_svg = File.join(root, asset.svg)
        tracked_png = File.join(root, asset.png)
        failures << "#{asset.id}: generated SVG missing" unless File.file?(generated_svg)
        failures << "#{asset.id}: generated PNG missing" unless File.file?(generated_png)
        next unless File.file?(generated_svg) && File.file?(generated_png)

        generated_svg_hash = DiagramProvenance.sha256(generated_svg)
        generated_png_hash = DiagramProvenance.sha256(generated_png)
        failures << "#{asset.id}: SVG SHA-256 drift (expected #{asset.svg_sha256}, actual #{generated_svg_hash})" unless generated_svg_hash == asset.svg_sha256
        rendered_root = File.expand_path(generated_root) != File.join(root, "docs/manual/assets")
        if rendered_root
          failures << "#{asset.id}: rendered PNG SHA-256 drift from receipt (expected #{asset.rendered_png_sha256}, actual #{generated_png_hash})" unless generated_png_hash == asset.rendered_png_sha256
          generated_png_semantic = png_semantic_fingerprint(generated_png)
          failures << "#{asset.id}: rendered PNG semantic fingerprint drift from receipt (expected #{asset.rendered_png_semantic_fingerprint}, actual #{generated_png_semantic})" unless generated_png_semantic == asset.rendered_png_semantic_fingerprint
          if generated_png_hash != asset.png_sha256
            message = "#{asset.id}: tracked PNG SHA-256 differs from the recorded renderer receipt (expected #{asset.png_sha256}, actual #{generated_png_hash})"
            if semantic_reproducibility?(manifest)
              (@warnings ||= []) << message
            else
              failures << message
            end
          end
          if generated_png_semantic != asset.png_semantic_fingerprint
            message = "#{asset.id}: tracked PNG semantic fingerprint differs from the recorded renderer receipt (expected #{asset.png_semantic_fingerprint}, actual #{generated_png_semantic})"
            if semantic_reproducibility?(manifest)
              (@warnings ||= []) << message
            else
              failures << message
            end
          end
        end
        actual_metadata = DiagramProvenance.png_metadata(generated_png)
        expected_metadata = asset.png_metadata
        failures << "#{asset.id}: PNG metadata drift (expected #{JSON.generate(expected_metadata)}, actual #{JSON.generate(actual_metadata)})" unless actual_metadata == expected_metadata
        failures << "#{asset.id}: semantic fingerprint drift" unless DiagramProvenance.semantic_fingerprint(generated_svg) == asset.semantic_fingerprint
        failures << "#{asset.id}: tracked SVG differs from provenance baseline" unless DiagramProvenance.sha256(tracked_svg) == asset.svg_sha256
        failures << "#{asset.id}: tracked PNG differs from provenance baseline" unless DiagramProvenance.sha256(tracked_png) == asset.png_sha256
        failures << "#{asset.id}: tracked PNG semantic fingerprint differs from provenance baseline" unless png_semantic_fingerprint(tracked_png) == asset.png_semantic_fingerprint
      end
      failures
    end

    def semantic_reproducibility?(manifest)
      manifest.data.fetch("delivery").fetch("reproducibility").fetch("mode") == "semantic-fingerprint"
    end

    def compare_inventory(generated_root, manifest)
      expected_svg = manifest.assets.map { |asset| asset.svg.delete_prefix("docs/manual/assets/") }.sort
      expected_png = manifest.assets.map { |asset| asset.png.delete_prefix("docs/manual/assets/") }.sort
      actual_svg = Dir[File.join(generated_root, "**/*.svg")].map { |path| path.delete_prefix("#{generated_root}/") }.sort
      actual_png = Dir[File.join(generated_root, "**/*.png")].map { |path| path.delete_prefix("#{generated_root}/") }.sort
      failures = []
      unexpected_svg = actual_svg - expected_svg
      unexpected_png = actual_png - expected_png
      failures << "generated SVG inventory drift: unexpected #{unexpected_svg.join(", ")}" unless unexpected_svg.empty?
      failures << "generated PNG inventory drift: unexpected #{unexpected_png.join(", ")}" unless unexpected_png.empty?
      failures
    end

    def png_semantic_fingerprint(path)
      @png_semantic_cache[path] ||= DiagramProvenance.png_semantic_fingerprint(path)
    end
  end
end
