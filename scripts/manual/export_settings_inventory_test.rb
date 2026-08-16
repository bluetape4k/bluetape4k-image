require "fileutils"
require "json"
require "minitest/autorun"
require "tmpdir"
require "yaml"

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
      errors = ["\n", "\r\n"].map do |newline|
        File.binwrite(settings, "rootProject.name = \"empty\"#{newline}")

        error = assert_raises(RuntimeError) do
          ManualDocs::SettingsInventory.new(
            settings_path: settings,
            output_path: File.join(root, "inventory.json"),
          ).write
        end
        assert_match(/no Gradle project directories found/, error.message)
        error.message
      end

      assert_equal 1, errors.uniq.length
    end
  end

  def test_exports_multiline_project_directory_assignments
    Dir.mktmpdir("settings-inventory-multiline") do |root|
      settings = File.join(root, "settings.gradle.kts")
      output = File.join(root, "build/manual/module-inventory.json")
      File.write(settings, <<~KOTLIN)
        include("spring-boot-image-intelligence-api")
        project(":spring-boot-image-intelligence-api").projectDir =
            file("examples/spring-boot-image-intelligence-api")
      KOTLIN

      rows = ManualDocs::SettingsInventory.new(settings_path: settings, output_path: output).write

      assert_equal [":spring-boot-image-intelligence-api"], rows.map { |row| row.fetch("gradlePath") }
      assert_equal "examples/spring-boot-image-intelligence-api", rows.first.fetch("sourceDir")
      assert_equal "example", rows.first.fetch("kind")
    end
  end

  def test_exports_crlf_project_directory_assignments_without_changing_classification
    Dir.mktmpdir("settings-inventory-crlf") do |root|
      content = <<~KOTLIN
        project(":not-a-project").name = "not-a-project"
        val unrelatedSource = file("examples/not-a-project")
        project(":bluetape4k-images").projectDir = file("images")
        project(":bluetape4k-images-benchmark").projectDir =
            file("benchmark/images-benchmark")
        project(":basic-processing").projectDir = file("examples/basic-processing")
        project(":spring-boot-image-intelligence-api").projectDir =
            file("examples/spring-boot-image-intelligence-api")
      KOTLIN
      line_endings = { lf: "\n", crlf: "\r\n" }
      rows_by_line_ending = line_endings.transform_values do |newline|
        settings = File.join(root, "settings-#{newline == "\n" ? "lf" : "crlf"}.gradle.kts")
        output = File.join(root, "build", "#{newline == "\n" ? "lf" : "crlf"}", "module-inventory.json")
        File.binwrite(settings, content.gsub("\n", newline))

        ManualDocs::SettingsInventory.new(settings_path: settings, output_path: output).write
      end

      assert_equal rows_by_line_ending.fetch(:lf), rows_by_line_ending.fetch(:crlf)
      rows = rows_by_line_ending.fetch(:crlf)

      assert_equal %w[example library benchmark example], rows.map { |row| row.fetch("kind") }
      assert_equal [":basic-processing", ":bluetape4k-images", ":bluetape4k-images-benchmark", ":spring-boot-image-intelligence-api"],
                   rows.map { |row| row.fetch("gradlePath") }
    end
  end

  def test_reports_the_same_duplicate_error_for_lf_and_crlf
    Dir.mktmpdir("settings-inventory-duplicate") do |root|
      content = <<~KOTLIN
        project(":duplicate").projectDir = file("images")
        project(":duplicate").projectDir = file("examples/duplicate")
      KOTLIN
      errors = ["\n", "\r\n"].map do |newline|
        settings = File.join(root, "settings-#{newline == "\n" ? "lf" : "crlf"}.gradle.kts")
        File.binwrite(settings, content.gsub("\n", newline))

        error = assert_raises(RuntimeError) do
          ManualDocs::SettingsInventory.new(
            settings_path: settings,
            output_path: File.join(root, "inventory.json"),
          ).write
        end
        error.message
      end

      assert_equal 1, errors.uniq.length
      assert_match(/duplicate Gradle path: :duplicate/, errors.first)
    end
  end
end

class ManualProvenanceTest < Minitest::Test
  ROOT = File.expand_path("../..", __dir__)
  EXPECTED_COMMIT = "ea5175b083babf8880f53cf80c9a264a0c61777e"

  def test_manifest_snapshots_share_the_release_topology
    yaml = YAML.safe_load(File.read(File.join(ROOT, "docs/manual/manifest.yaml")))
    json = JSON.parse(File.read(File.join(ROOT, "docs/manual/generated/manifest.json")))

    assert_equal "0.4.0", yaml.fetch("releaseRef")
    assert_equal EXPECTED_COMMIT, yaml.fetch("releaseCommit")
    assert_equal 19, yaml.fetch("modules").length
    assert_equal 19, json.fetch("modules").length
    assert_equal yaml.fetch("modules").map { |entry| entry.fetch("id") }.sort,
                 json.fetch("modules").map { |entry| entry.fetch("id") }.sort
    assert_includes yaml.fetch("modules").map { |entry| entry.fetch("id") }, "spring-boot-image-intelligence-api"
    assert_equal 7, yaml.fetch("modules").count { |entry| entry.fetch("kind") == "example" }
    assert_equal 1, yaml.fetch("modules").count { |entry| entry.fetch("kind") == "benchmark" }
    assert_equal 10, yaml.fetch("modules").count { |entry| entry.fetch("kind") == "library" && entry.fetch("group") != "platform" }
  end

  def test_release_provenance_labels_and_authored_pages_are_current
    readme = File.read(File.join(ROOT, "README.md"))
    readme_ko = File.read(File.join(ROOT, "README.ko.md"))
    index = File.read(File.join(ROOT, "docs/manual/en/index.md"))
    index_ko = File.read(File.join(ROOT, "docs/manual/ko/index.md"))
    intelligence_en = File.read(File.join(ROOT, "docs/manual/en/modules/spring-boot-image-intelligence-api.md"))
    intelligence_ko = File.read(File.join(ROOT, "docs/manual/ko/modules/spring-boot-image-intelligence-api.md"))
    diagram_source = File.read(File.join(ROOT, "scripts/manual/render_image_diagrams.rb"))

    assert_includes readme, "Image 0.4 manual"
    assert_includes readme_ko, "Image 0.4 매뉴얼"
    assert_includes readme, "io.github.bluetape4k:bluetape4k-dependencies:<version>"
    assert_includes readme_ko, "io.github.bluetape4k:bluetape4k-dependencies:<version>"
    assert_includes index, "10 published libraries"
    assert_includes index_ko, "배포 라이브러리 10개"
    assert_includes index, "Release commit ea5175b0"
    assert_includes index_ko, "릴리스 커밋 ea5175b0"
    assert_includes diagram_source, "Image 0.4 / 19 projects / 7 workshops / 1 benchmark"
    [intelligence_en, intelligence_ko].each do |document|
      refute_match(/This section will be completed|안정판 소스를 바탕으로 내용을 보강할 예정입니다/, document)
      assert_includes document, EXPECTED_COMMIT
    end
  end
end
