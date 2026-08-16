#!/usr/bin/env ruby

require "rexml/document"
require_relative "diagram_provenance"

root = File.expand_path("../..", __dir__)
assets = Dir[File.join(root, "docs/manual/assets/**/*.svg")].sort
errors = []

errors << "expected 5 SVG diagrams, found #{assets.length}" unless assets.length == 5
assets.each do |svg_path|
  relative = svg_path.delete_prefix("#{root}/")
  manual_relative = relative.delete_prefix("docs/manual/")
  png_path = svg_path.sub(/\.svg\z/, ".png")
  errors << "#{relative}: paired PNG missing" unless File.file?(png_path)
  %w[en ko].each do |locale|
    referenced = Dir[File.join(root, "docs/manual/#{locale}/**/*.md")].any? do |manual_path|
      File.read(manual_path).include?(manual_relative)
    end
    errors << "#{relative}: not referenced by #{locale} manual" unless referenced
  end
  document = REXML::Document.new(File.read(svg_path))
  svg = document.root
  errors << "#{relative}: expected 1600x1040 SVG" unless svg.attributes["width"] == "1600" && svg.attributes["height"] == "1040"
  errors << "#{relative}: accessible title missing" unless REXML::XPath.first(document, "/svg/title")
  errors << "#{relative}: accessible description missing" unless REXML::XPath.first(document, "/svg/desc")
  paths = REXML::XPath.match(document, "//path[@marker-end]")
  errors << "#{relative}: no directed connectors" if paths.empty?
  paths.each do |path|
    errors << "#{relative}: connector #{path.attributes['id']} is thinner than 4" if path.attributes["stroke-width"].to_f < 4
  end
  markers = REXML::XPath.match(document, "//marker")
  markers.each do |marker|
    width = marker.attributes["markerWidth"].to_f
    height = marker.attributes["markerHeight"].to_f
    errors << "#{relative}: marker #{marker.attributes['id']} is smaller than 14x14" if width < 14 || height < 14
  end
end

begin
  DiagramProvenance::Verifier.new(root: root).verify!(render: false)
rescue DiagramProvenance::ContractError => error
  errors.concat(error.message.lines.map { |line| "diagram provenance: #{line.strip}" })
end

abort(errors.join("\n")) unless errors.empty?
puts "Image diagram contract passed: #{assets.length} SVG/PNG pairs, provenance, accessible labels, directed connectors, and 14x14 arrowheads."
