#!/usr/bin/env ruby

require "find"
require "pathname"
require "set"

ROOT = Pathname.new(__dir__).parent.expand_path
TEXT_EXTENSIONS = %w[.md .mdx .adoc .txt].freeze
CODE_EXTENSIONS = %w[.kt .java].freeze
PRUNED_DIRS = %w[.git .gradle build .kotlin .idea].freeze

def relative(path)
  Pathname.new(path).expand_path.relative_path_from(ROOT).to_s
end

def pruned?(path)
  parts = relative(path).split(File::SEPARATOR)
  parts.any? { |part| PRUNED_DIRS.include?(part) }
end

def repository_files
  files = []
  Find.find(ROOT.to_s) do |path|
    if File.directory?(path)
      Find.prune if pruned?(path) && Pathname.new(path).expand_path != ROOT
      next
    end
    files << relative(path)
  end
  files.sort
end

def text_document?(path)
  TEXT_EXTENSIONS.include?(File.extname(path))
end

def code_file?(path)
  CODE_EXTENSIONS.include?(File.extname(path))
end

def readme?(path)
  File.basename(path).match?(/\AREADME(?:\.ko)?\.md\z/)
end

def operating_doc?(path)
  %w[AGENTS.md CLAUDE.md].include?(path)
end

def manual_pair_doc?(path)
  path.start_with?("docs/manual/en/") || path.start_with?("docs/manual/ko/")
end

def in_scope_document?(path)
  text_document?(path) &&
    !readme?(path) &&
    !operating_doc?(path) &&
    !manual_pair_doc?(path) &&
    !path.start_with?(".omx/")
end

def group_for(path)
  parts = path.split("/")
  return parts.first if parts.length == 1
  return parts.take(2).join("/") if %w[benchmark docs examples].include?(parts.first)

  parts.first
end

def manual_relative_set(locale)
  prefix = "docs/manual/#{locale}/"
  repository_files
    .select { |path| path.start_with?(prefix) && text_document?(path) }
    .map { |path| path.delete_prefix(prefix) }
    .to_set
end

def code_comment_file?(path)
  return false unless code_file?(path)

  File.foreach(ROOT.join(path), encoding: "UTF-8").any? do |line|
    line.include?("//") || line.include?("/*") || line.include?("* @") ||
      line.match?(/^\s+\*\s+\S/)
  end
end

files = repository_files
documents = files.select { |path| text_document?(path) }
in_scope_documents = documents.select { |path| in_scope_document?(path) }
excluded_readmes = documents.select { |path| readme?(path) }
excluded_operating = documents.select { |path| operating_doc?(path) }
excluded_manual = documents.select { |path| manual_pair_doc?(path) }
code_comment_files = files.select { |path| code_comment_file?(path) }

en_manual = manual_relative_set("en")
ko_manual = manual_relative_set("ko")
missing_ko = (en_manual - ko_manual).sort
missing_en = (ko_manual - en_manual).sort

puts "Korean localization scope audit"
puts "repository: #{ROOT}"
puts
puts "Excluded README docs: #{excluded_readmes.length}"
puts "Excluded operating docs: #{excluded_operating.join(", ")}"
puts "Excluded bilingual manual docs: #{excluded_manual.length}"
puts "Manual EN docs: #{en_manual.length}"
puts "Manual KO docs: #{ko_manual.length}"
puts "Manual missing KO pairs: #{missing_ko.empty? ? "0" : missing_ko.join(", ")}"
puts "Manual missing EN pairs: #{missing_en.empty? ? "0" : missing_en.join(", ")}"
puts
puts "In-scope single-language docs by group:"
in_scope_documents.group_by { |path| group_for(path) }.sort.each do |group, paths|
  puts "- #{group}: #{paths.length}"
end
puts
puts "Code files with comments by group:"
code_comment_files.group_by { |path| group_for(path) }.sort.each do |group, paths|
  puts "- #{group}: #{paths.length}"
end
puts
puts "Totals:"
puts "- in_scope_documents=#{in_scope_documents.length}"
puts "- code_comment_files=#{code_comment_files.length}"
puts "- manual_pair_mismatches=#{missing_ko.length + missing_en.length}"

exit(missing_ko.empty? && missing_en.empty? ? 0 : 1)
