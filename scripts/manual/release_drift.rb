#!/usr/bin/env ruby
# frozen_string_literal: true

require "json"
require "open3"
require "pathname"
require "yaml"

require_relative "export_settings_inventory"

module ManualDocs
  class ReleaseDriftError < StandardError; end

  class ReleaseDrift
    ValidationResult = Struct.new(:errors, :expected, keyword_init: true)

    TAG_PATTERN = /\Av?\d+\.\d+\.\d+\z/
    SHA_PATTERN = /\A[0-9a-f]{40}\z/i
    DISPLAY_SHA_LENGTH = 8
    INTELLIGENCE_ID = "spring-boot-image-intelligence-api"
    TOPOLOGY_KEYS = %w[id gradlePath sourceDir kind].freeze
    PUBLISHING_POLICY_MARKERS = [
      'fun Project.isNonPublishedModule()',
      'relativePath == "examples"',
      'relativePath.startsWith("examples/")',
      'relativePath == "benchmark"',
      'relativePath.startsWith("benchmark/")',
      'name.contains("-demo")',
      'name.endsWith("-benchmark")',
      'filterNot { it.isNonPublishedModule() }',
    ].freeze

    def initialize(
      repository_root:, tag:, manifest_path:, generated_manifest_path:, index_paths:,
      repository_map_paths:, diagram_source_path:, inventory_path: nil, git_runner: nil
    )
      @repository_root = File.expand_path(repository_root)
      @tag = tag
      @manifest_path = File.expand_path(manifest_path)
      @generated_manifest_path = File.expand_path(generated_manifest_path)
      @index_paths = index_paths
      @repository_map_paths = repository_map_paths
      @diagram_source_path = File.expand_path(diagram_source_path)
      @inventory_path = inventory_path && File.expand_path(inventory_path)
      @git_runner = git_runner || method(:run_git)
    end

    def validate
      unless TAG_PATTERN.match?(@tag)
        return ValidationResult.new(errors: ["release tag must match v?MAJOR.MINOR.PATCH: #{@tag}"], expected: nil)
      end

      expected = derive_expected
      errors = []
      errors.concat(validate_snapshot(load_yaml(@manifest_path, "manual manifest"), "manual manifest", expected))
      errors.concat(validate_snapshot(load_json(@generated_manifest_path, "generated manifest"), "generated manifest", expected))
      errors.concat(validate_inventory(expected)) if @inventory_path
      errors.concat(validate_indexes(expected))
      errors.concat(validate_repository_maps(expected))
      errors.concat(validate_diagram_source(expected))
      ValidationResult.new(errors: errors.compact.sort, expected: expected)
    rescue ReleaseDriftError => error
      ValidationResult.new(errors: [error.message], expected: nil)
    rescue JSON::ParserError => error
      ValidationResult.new(errors: ["generated manifest JSON is invalid: #{error.message}"], expected: nil)
    rescue Psych::SyntaxError => error
      ValidationResult.new(errors: ["manual manifest YAML is invalid: #{error.problem}"], expected: nil)
    end

    private

    def derive_expected
      commit = resolve_tag
      tree = git_output(["ls-tree", "-r", "--name-only", commit], "release inventory could not be read: #{commit}")
      settings = git_output(["show", "#{commit}:settings.gradle.kts"], "release settings could not be read: #{@tag}")
      publishing_policy = git_output(["show", "#{commit}:build.gradle.kts"], "release publishing policy could not be read: #{@tag}")
      missing_policy = PUBLISHING_POLICY_MARKERS.reject { |marker| publishing_policy.include?(marker) }
      unless missing_policy.empty?
        raise ReleaseDriftError, "release publishing policy markers missing: #{missing_policy.join(', ')}"
      end

      release_paths = tree.lines(chomp: true)
      rows = SettingsInventory.parse(settings).select do |row|
        release_paths.include?(File.join(row.fetch("sourceDir"), "build.gradle.kts"))
      end
      raise ReleaseDriftError, "release topology is empty: #{@tag}" if rows.empty?

      topology_rows = rows.map { |row| topology_row(row) }.sort_by { |row| row.fetch("id") }
      kind_counts = rows.group_by { |row| row.fetch("kind") }.transform_values(&:length)
      bom_rows = rows.select { |row| bom_row?(row) }
      published_library_rows = rows.select { |row| row.fetch("kind") == "library" && !non_published_row?(row) && !bom_row?(row) }
      release_ref = @tag.delete_prefix("v")
      release_version = release_ref.split(".").first(2).join(".")

      {
        release_ref: release_ref,
        release_commit: commit.downcase,
        short_commit: commit[0, DISPLAY_SHA_LENGTH].downcase,
        module_rows: topology_rows,
        module_ids: topology_rows.map { |row| row.fetch("id") },
        project_count: topology_rows.length,
        library_count: kind_counts.fetch("library", 0),
        published_library_count: published_library_rows.length,
        published_library_ids: published_library_rows.map { |row| manual_id(row) }.sort,
        bom_count: bom_rows.length,
        example_count: kind_counts.fetch("example", 0),
        benchmark_count: kind_counts.fetch("benchmark", 0),
        intelligence_present: topology_rows.any? { |row| row.fetch("id") == INTELLIGENCE_ID },
        diagram_label: "Image #{release_version} / #{topology_rows.length} projects / #{kind_counts.fetch('example', 0)} workshops / #{kind_counts.fetch('benchmark', 0)} benchmark",
      }
    end

    def resolve_tag
      output, success = @git_runner.call(["rev-parse", "--verify", "refs/tags/#{@tag}^{commit}"])
      commit = output.to_s.strip
      raise ReleaseDriftError, "release tag not found: refs/tags/#{@tag}" unless success
      raise ReleaseDriftError, "release tag #{@tag} resolves to an invalid commit: #{commit}" unless SHA_PATTERN.match?(commit)

      commit
    end

    def git_output(arguments, error_message)
      output, success = @git_runner.call(arguments)
      raise ReleaseDriftError, error_message unless success

      output
    end

    def validate_snapshot(snapshot, label, expected)
      return ["#{label} not found: #{relative(snapshot_path(label))}"] unless snapshot.is_a?(Hash)

      errors = []
      errors << "#{label} releaseRef #{snapshot['releaseRef'].inspect}, expected #{expected.fetch(:release_ref)}" unless snapshot["releaseRef"] == expected.fetch(:release_ref)
      errors << "#{label} releaseCommit #{snapshot['releaseCommit'].inspect}, expected #{expected.fetch(:release_commit)}" unless snapshot["releaseCommit"].to_s.casecmp?(expected.fetch(:release_commit))
      modules = snapshot["modules"]
      return errors << "#{label} modules must be an array" unless modules.is_a?(Array)

      actual_rows = normalize_manifest_rows(modules, label, errors)
      errors.concat(compare_topology(actual_rows, label, expected))
      errors.concat(validate_kind_count(actual_rows, label, "library", expected.fetch(:library_count)))
      errors.concat(validate_kind_count(actual_rows, label, "example", expected.fetch(:example_count)))
      errors.concat(validate_kind_count(actual_rows, label, "benchmark", expected.fetch(:benchmark_count)))
      actual_bom_count = actual_rows.count { |row| bom_id?(row.fetch("id")) }
      errors << "#{label} BOM count #{actual_bom_count}, expected #{expected.fetch(:bom_count)}" unless actual_bom_count == expected.fetch(:bom_count)
      actual_published_ids = actual_rows.select { |row| row.fetch("kind") == "library" && !bom_id?(row.fetch("id")) }.map { |row| row.fetch("id") }.sort
      unless actual_published_ids == expected.fetch(:published_library_ids)
        errors << "#{label} published library ids #{actual_published_ids.inspect}, expected #{expected.fetch(:published_library_ids).inspect}"
      end
      if expected.fetch(:intelligence_present) && !actual_rows.any? { |row| row.fetch("id") == INTELLIGENCE_ID }
        errors << "#{label} is missing the #{INTELLIGENCE_ID} example"
      end
      errors
    end

    def validate_inventory(expected)
      snapshot = load_json(@inventory_path, "release module inventory")
      return ["release module inventory not found: #{relative(@inventory_path)}"] unless snapshot
      return ["release module inventory must be an array"] unless snapshot.is_a?(Array)

      errors = []
      actual_rows = normalize_inventory_rows(snapshot, errors)
      errors.concat(compare_topology(actual_rows, "release module inventory", expected))
      errors
    end

    def normalize_manifest_rows(modules, label, errors)
      modules.each_with_index.map do |entry, index|
        unless entry.is_a?(Hash)
          errors << "#{label} module[#{index}] must be an object"
          next
        end
        missing = TOPOLOGY_KEYS.reject { |key| entry.key?(key) && !entry[key].nil? }
        unless missing.empty?
          errors << "#{label} module[#{index}] missing #{missing.join(', ')}"
          next
        end
        TOPOLOGY_KEYS.each_with_object({}) { |key, row| row[key] = entry.fetch(key).to_s }
      end.compact.sort_by { |row| row.fetch("id") }
    end

    def normalize_inventory_rows(rows, errors)
      rows.each_with_index.map do |entry, index|
        unless entry.is_a?(Hash)
          errors << "release module inventory row[#{index}] must be an object"
          next
        end
        begin
          row = {
            "id" => manual_id(entry),
            "gradlePath" => entry.fetch("gradlePath").to_s,
            "sourceDir" => entry.fetch("sourceDir").to_s,
            "kind" => entry.fetch("kind").to_s,
          }
        rescue KeyError => error
          errors << "release module inventory row[#{index}] missing #{error.key}"
          next
        end
        row
      end.compact.sort_by { |row| row.fetch("id") }
    end

    def compare_topology(actual_rows, label, expected)
      expected_rows = expected.fetch(:module_rows)
      return [] if actual_rows == expected_rows

      missing = expected_rows - actual_rows
      extra = actual_rows - expected_rows
      ["#{label} topology mismatch; missing=#{missing.inspect}; extra=#{extra.inspect}"]
    end

    def validate_kind_count(rows, label, kind, expected)
      actual = rows.count { |entry| entry.fetch("kind") == kind }
      actual == expected ? [] : ["#{label} #{kind} count #{actual}, expected #{expected}"]
    end

    def validate_indexes(expected)
      @index_paths.each_with_object([]) do |(locale, path), errors|
        content = read_file(path, "#{locale} manual index", errors)
        next unless content

        errors << "#{relative(path)}: missing release ref #{expected.fetch(:release_ref)}" unless content.include?(expected.fetch(:release_ref))
        errors.concat(validate_release_ref(content, path, expected))
        errors.concat(validate_index_commit(content, locale, path, expected))
        expected_index_patterns(locale, expected).each do |label, pattern|
          errors << "#{relative(path)}: missing #{label} derived value" unless content.match?(pattern)
        end
      end
    end

    def validate_index_commit(content, locale, path, expected)
      label = locale.to_s == "ko" ? "릴리스 커밋" : "Release commit"
      match = content.match(/#{Regexp.escape(label)}\s+([0-9a-f]{#{DISPLAY_SHA_LENGTH}})[^\n]*?\/(?:commit)\/([0-9a-f]{40})/i)
      return ["#{relative(path)}: missing #{label} short/full SHA pair"] unless match

      errors = []
      short_sha = match[1].downcase
      full_sha = match[2].downcase
      errors << "#{relative(path)}: visible short SHA #{short_sha}, expected #{expected.fetch(:short_commit)}" unless short_sha == expected.fetch(:short_commit)
      errors << "#{relative(path)}: commit link SHA #{full_sha}, expected #{expected.fetch(:release_commit)}" unless full_sha == expected.fetch(:release_commit)
      errors
    end

    def validate_repository_maps(expected)
      @repository_map_paths.each_with_object([]) do |(locale, path), errors|
        content = read_file(path, "#{locale} repository map", errors)
        next unless content

        errors << "#{relative(path)}: missing release ref #{expected.fetch(:release_ref)}" unless content.include?(expected.fetch(:release_ref))
        errors.concat(validate_release_ref(content, path, expected))
        hashes = content.scan(%r{/(?:commit|blob|tree)/([0-9a-f]{40})(?:[/#)]|\z)}i).flatten.map(&:downcase).uniq
        if hashes.empty?
          errors << "#{relative(path)}: missing full release SHA #{expected.fetch(:release_commit)}"
        elsif hashes != [expected.fetch(:release_commit)]
          errors << "#{relative(path)}: release link SHAs #{hashes.inspect}, expected [#{expected.fetch(:release_commit)}]"
        end
        expected_index_patterns(locale, expected).each do |label, pattern|
          errors << "#{relative(path)}: missing #{label} derived value" unless content.match?(pattern)
        end
        if expected.fetch(:intelligence_present)
          intelligence_pattern = locale.to_s == "ko" ? /이미지 인텔리전스/ : /image intelligence/i
          errors << "#{relative(path)}: missing intelligence example reference" unless content.match?(intelligence_pattern)
        end
      end
    end

    def validate_release_ref(content, path, expected)
      errors = []
      frontmatter = content[/\A---\s*\n(.*?)\n---\s*\n/m, 1]
      front_refs = frontmatter.to_s.scan(/^releaseRef:\s*["']?([^"'\s]+)["']?/).flatten.uniq
      unless front_refs == [expected.fetch(:release_ref)]
        errors << "#{relative(path)}: frontmatter releaseRef #{front_refs.inspect}, expected [#{expected.fetch(:release_ref)}]"
      end

      body = frontmatter ? content.sub(/\A---\s*\n.*?\n---\s*\n/m, "") : content
      visible_refs = body.scan(/\b\d+\.\d+\.\d+\b/).uniq
      if visible_refs.empty?
        errors << "#{relative(path)}: missing visible release ref #{expected.fetch(:release_ref)}"
      elsif visible_refs != [expected.fetch(:release_ref)]
        errors << "#{relative(path)}: visible release refs #{visible_refs.inspect}, expected [#{expected.fetch(:release_ref)}]"
      end
      errors
    end

    def validate_diagram_source(expected)
      return ["diagram source not found: #{relative(@diagram_source_path)}"] unless File.file?(@diagram_source_path)
      content = File.read(@diagram_source_path)
      content.include?(expected.fetch(:diagram_label)) ? [] : ["diagram source missing derived label #{expected.fetch(:diagram_label)}"]
    end

    def expected_index_patterns(locale, expected)
      if locale.to_s == "ko"
        [
          ["published library count", /(?:배포 라이브러리\s*#{expected.fetch(:published_library_count)}개|#{expected.fetch(:published_library_count)}개는 라이브러리 Maven 좌표)/],
          ["BOM count", /(?:배포 BOM\s*#{expected.fetch(:bom_count)}개|#{expected.fetch(:bom_count)}개는 이미지 BOM)/],
          ["example count", /(?:실행 예제\s*#{expected.fetch(:example_count)}개|#{expected.fetch(:example_count)}개는 실행 예제)/],
          ["benchmark count", /(?:배포하지 않는 벤치마크 프로젝트\s*#{expected.fetch(:benchmark_count)}개|#{expected.fetch(:benchmark_count)}개는 벤치마크 프로젝트)/],
          ["project count", /Gradle 프로젝트(?:는 모두\s*)?\s*#{expected.fetch(:project_count)}개/],
        ]
      else
        [
          ["published library count", english_count_pattern(expected.fetch(:published_library_count), "published (?:library coordinates|libraries)")],
          ["BOM count", english_count_pattern(expected.fetch(:bom_count), "published image BOM")],
          ["example count", english_count_pattern(expected.fetch(:example_count), "runnable examples")],
          ["benchmark count", english_count_pattern(expected.fetch(:benchmark_count), "benchmark project")],
          ["project count", english_count_pattern(expected.fetch(:project_count), "Gradle projects")],
        ]
      end
    end

    def english_count_pattern(count, suffix)
      words = %w[zero one two three four five six seven eight nine ten eleven twelve thirteen fourteen fifteen sixteen seventeen eighteen nineteen twenty]
      token = "(?:#{count}|#{words.fetch(count, count.to_s)})"
      /\b#{token}\b[^.\n]*\b#{suffix}\b/i
    end

    def load_yaml(path, label)
      YAML.safe_load(File.read(path))
    rescue Errno::ENOENT
      nil
    rescue Psych::SyntaxError
      raise
    rescue StandardError => error
      raise ReleaseDriftError, "#{label} could not be read: #{error.message}"
    end

    def load_json(path, label)
      JSON.parse(File.read(path))
    rescue Errno::ENOENT
      nil
    rescue JSON::ParserError
      raise
    rescue StandardError => error
      raise ReleaseDriftError, "#{label} could not be read: #{error.message}"
    end

    def read_file(path, label, errors)
      return errors << "#{label} not found: #{relative(path)}" unless File.file?(path)
      File.read(path)
    rescue StandardError => error
      errors << "#{label} could not be read: #{error.message}"
      nil
    end

    def snapshot_path(label)
      label == "manual manifest" ? @manifest_path : @generated_manifest_path
    end

    def topology_row(row)
      {
        "id" => manual_id(row),
        "gradlePath" => row.fetch("gradlePath"),
        "sourceDir" => row.fetch("sourceDir"),
        "kind" => row.fetch("kind"),
      }
    end

    def manual_id(row)
      row.fetch("kind") == "example" ? row.fetch("sourceDir").split("/").last : row.fetch("projectName")
    end

    def bom_row?(row)
      row.fetch("sourceDir") == "bom" || row.fetch("projectName").end_with?("-bom")
    end

    def bom_id?(id)
      id == "bluetape4k-image-bom" || id.end_with?("-bom")
    end

    def non_published_row?(row)
      source = row.fetch("sourceDir")
      project = row.fetch("projectName")
      source == "examples" || source.start_with?("examples/") ||
        source == "benchmark" || source.start_with?("benchmark/") ||
        project.include?("-demo") || project.end_with?("-benchmark")
    end

    def relative(path)
      Pathname.new(path).relative_path_from(Pathname.new(@repository_root)).to_s
    end

    def run_git(arguments)
      stdout, _stderr, status = Open3.capture3("git", "-C", @repository_root, *arguments)
      [stdout, status.success?]
    end
  end
end
