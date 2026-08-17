#!/usr/bin/env ruby
# frozen_string_literal: true

require_relative "release_drift"

root = File.expand_path("../..", __dir__)
tag = ARGV.fetch(0, "0.4.0")
contract = ManualDocs::ReleaseDrift.new(
  repository_root: root,
  tag: tag,
  manifest_path: File.join(root, "docs/manual/manifest.yaml"),
  generated_manifest_path: File.join(root, "docs/manual/generated/manifest.json"),
  index_paths: {
    "en" => File.join(root, "docs/manual/en/index.md"),
    "ko" => File.join(root, "docs/manual/ko/index.md"),
  },
  repository_map_paths: {
    "en" => File.join(root, "docs/manual/en/architecture/repository-map.md"),
    "ko" => File.join(root, "docs/manual/ko/architecture/repository-map.md"),
  },
  diagram_source_path: File.join(root, "scripts/manual/render_image_diagrams.rb"),
  inventory_path: File.join(root, "build/manual/release-module-inventory.json"),
)
result = contract.validate
abort(result.errors.join("\n")) unless result.errors.empty?

expected = result.expected
puts "Release drift contract passed: #{expected.fetch(:release_ref)} #{expected.fetch(:release_commit)}; #{expected.fetch(:project_count)} projects (#{expected.fetch(:published_library_count)} libraries, #{expected.fetch(:bom_count)} BOM, #{expected.fetch(:example_count)} examples, #{expected.fetch(:benchmark_count)} benchmark)."
