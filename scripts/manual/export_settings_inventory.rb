#!/usr/bin/env ruby

require "fileutils"
require "json"

module ManualDocs
  class SettingsInventory
    PROJECT_DIR_PATTERN = /^[ \t]*project\("(:[^"]+)"\)\.projectDir[ \t]*=[ \t]*(?:\n[ \t]*)?file\("([^"]+)"\)[ \t]*(?=\n|\z)/

    def initialize(settings_path:, output_path:)
      @settings_path = settings_path
      @output_path = output_path
    end

    def write
      rows = File.read(@settings_path).scan(PROJECT_DIR_PATTERN).each_with_object([]) do |(gradle_path, source_dir), result|
        result << {
          "gradlePath" => gradle_path,
          "kind" => kind_for(source_dir),
          "projectName" => gradle_path.delete_prefix(":"),
          "sourceDir" => source_dir,
        }
      end.sort_by { |row| row.fetch("gradlePath") }

      raise "no Gradle project directories found in #{@settings_path}" if rows.empty?
      duplicate = rows.group_by { |row| row.fetch("gradlePath") }.find { |_path, matches| matches.length > 1 }
      raise "duplicate Gradle path: #{duplicate.first}" if duplicate

      FileUtils.mkdir_p(File.dirname(@output_path))
      File.binwrite(@output_path, JSON.pretty_generate(rows) + "\n")
      rows
    end

    private

    def kind_for(source_dir)
      return "example" if source_dir.start_with?("examples/")
      return "benchmark" if source_dir == "benchmark" || source_dir.start_with?("benchmark/")

      "library"
    end
  end
end

if $PROGRAM_NAME == __FILE__
  settings = ARGV.fetch(0, "settings.gradle.kts")
  output = ARGV.fetch(1, "build/manual/module-inventory.json")
  rows = ManualDocs::SettingsInventory.new(settings_path: settings, output_path: output).write
  puts "Gradle settings inventory written: #{rows.length} projects."
end
