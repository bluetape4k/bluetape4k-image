require "fileutils"
require "json"
require "minitest/autorun"
require "tmpdir"

require_relative "export_settings_inventory"

class SettingsInventoryTest < Minitest::Test
  def test_exports_project_directories_and_classifies_examples_and_benchmarks
    Dir.mktmpdir("settings-inventory") do |root|
      settings = File.join(root, "settings.gradle.kts")
      output = File.join(root, "build/manual/module-inventory.json")
      File.write(settings, <<~KOTLIN)
        include("bluetape4k-images", "bluetape4k-images-benchmark")
        project(":bluetape4k-images").projectDir = file("images")
        project(":bluetape4k-images-benchmark").projectDir = file("benchmark/images-benchmark")
        include("basic-processing")
        project(":basic-processing").projectDir = file("examples/basic-processing")
      KOTLIN

      rows = ManualDocs::SettingsInventory.new(settings_path: settings, output_path: output).write

      assert_equal 3, rows.length
      assert_equal %w[example library benchmark], rows.map { |row| row.fetch("kind") }
      assert_equal rows, JSON.parse(File.read(output))
    end
  end

  def test_fails_when_settings_has_no_project_directory_assignments
    Dir.mktmpdir("settings-inventory") do |root|
      settings = File.join(root, "settings.gradle.kts")
      File.write(settings, "rootProject.name = \"empty\"\n")

      error = assert_raises(RuntimeError) do
        ManualDocs::SettingsInventory.new(
          settings_path: settings,
          output_path: File.join(root, "inventory.json"),
        ).write
      end
      assert_match(/no Gradle project directories found/, error.message)
    end
  end
end
