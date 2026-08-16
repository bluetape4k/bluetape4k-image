#!/usr/bin/env ruby

require "json"
require "yaml"

INVENTORY = ARGV.fetch(0, "build/manual/release-module-inventory.json")
OUTPUT = ARGV.fetch(1, "docs/manual/manifest.yaml")

TITLES = {
  "basic-processing" => ["Basic processing workshop", "기본 이미지 처리 워크숍"],
  "bluetape4k-image-bom" => ["Image BOM", "Image BOM"],
  "bluetape4k-images" => ["Immutable image processing", "불변 이미지 처리"],
  "bluetape4k-images-barcode-api" => ["Barcode API", "바코드 API"],
  "bluetape4k-images-barcode-zxing" => ["ZXing barcode backend", "ZXing 바코드 백엔드"],
  "bluetape4k-images-benchmark" => ["Image processing benchmarks", "이미지 처리 벤치마크"],
  "bluetape4k-images-captcha" => ["CAPTCHA generation and verification", "CAPTCHA 생성과 검증"],
  "bluetape4k-images-ktor" => ["Ktor image routes", "Ktor 이미지 라우트"],
  "bluetape4k-images-ocr" => ["Tesseract OCR integration", "Tesseract OCR 연동"],
  "bluetape4k-images-spring-boot" => ["Spring Boot image platform", "Spring Boot 이미지 플랫폼"],
  "bluetape4k-images-vips-api" => ["libvips common API", "libvips 공통 API"],
  "bluetape4k-images-vips-java21" => ["JDK 25 JVips JNI backend (legacy java21 artifact)", "JDK 25 JVips JNI 백엔드 (legacy java21 artifact)"],
  "bluetape4k-images-vips-java25" => ["Java 25 FFM backend", "Java 25 FFM 백엔드"],
  "ktor-image-api" => ["Ktor image API workshop", "Ktor 이미지 API 워크숍"],
  "ktor-ocr-api" => ["Ktor OCR API workshop", "Ktor OCR API 워크숍"],
  "spring-boot-image-api" => ["Spring Boot image API workshop", "Spring Boot 이미지 API 워크숍"],
  "spring-boot-barcode-api" => ["Spring Boot barcode API workshop", "Spring Boot 바코드 API 워크숍"],
  "spring-boot-image-intelligence-api" => ["Spring Boot image intelligence API workshop", "Spring Boot 이미지 인텔리전스 API 워크숍"],
  "spring-boot-ocr-api" => ["Spring Boot OCR API workshop", "Spring Boot OCR API 워크숍"],
}.freeze

def manual_id(row)
  row.fetch("kind") == "example" ? row.fetch("sourceDir").split("/").last : row.fetch("projectName")
end

def group_for(id, kind)
  return "workshops" if kind == "example"
  return "benchmarks" if kind == "benchmark"
  return "platform" if id.end_with?("-bom")
  return "foundation" if id == "bluetape4k-images"
  return "native" if id.include?("vips")
  return "frameworks" if id.match?(/spring-boot|ktor/)
  "capabilities"
end

rows = JSON.parse(File.read(INVENTORY))
modules = rows.map do |row|
  id = manual_id(row)
  kind = row.fetch("kind")
  source = row.fetch("sourceDir")
  artifact = kind == "library" ? "io.github.bluetape4k.image:#{row.fetch('projectName')}" : nil
  source_paths = ["#{source}/src/main", "#{source}/README.md", "#{source}/README.ko.md"]
  test_paths = ["#{source}/src/test"]
  if id == "bluetape4k-image-bom"
    source_paths = ["#{source}/build.gradle.kts", "#{source}/README.md", "#{source}/README.ko.md"]
    test_paths = []
  elsif kind == "benchmark"
    source_paths = ["#{source}/src/main", "#{source}/docs", "#{source}/README.md", "#{source}/README.ko.md"]
    test_paths = []
  end
  title = TITLES.fetch(id)
  {
    "id" => id,
    "title" => { "en" => title.first, "ko" => title.last },
    "gradlePath" => row.fetch("gradlePath"),
    "sourceDir" => source,
    "kind" => kind,
    "group" => group_for(id, kind),
    "status" => "stable",
    "artifact" => artifact,
    "en" => "en/modules/#{id}.md",
    "ko" => "ko/modules/#{id}.md",
    "sourcePaths" => source_paths,
    "testPaths" => test_paths,
    "workshops" => kind == "example" ? [source] : [],
  }
end.sort_by { |entry| entry.fetch("id") }

overview_documents = %w[
  index.md getting-started.md architecture/repository-map.md architecture/runtime-boundaries.md
  guides/learning-path.md guides/backend-selection.md guides/codec-and-format-selection.md
  guides/native-resource-lifecycle.md guides/ocr-setup.md guides/spring-vs-ktor.md
  guides/testing-and-operations.md guides/failure-diagnosis.md guides/performance-selection.md
  core/immutable-image-model.md core/loading-and-writing.md core/transforms-and-filters.md
  core/analysis-and-similarity.md native/vips-api.md native/java21-jni.md native/java25-ffm.md
  integrations/captcha.md integrations/ocr.md integrations/ktor.md integrations/spring-boot.md
  integrations/storage-and-cdn.md benchmarks/interpreting-results.md
]

manifest = {
  "schemaVersion" => 2,
  "repository" => "bluetape4k/bluetape4k-image",
  "releaseRef" => "0.4.0",
  "releaseCommit" => "ea5175b083babf8880f53cf80c9a264a0c61777e",
  "overview" => {
    "documents" => {
      "en" => overview_documents.map { |path| "en/#{path}" },
      "ko" => overview_documents.map { |path| "ko/#{path}" },
    },
    "assets" => %w[
      assets/overview/repository-learning-map.svg assets/overview/repository-learning-map.png
      assets/architecture/processing-lifecycle.svg assets/architecture/processing-lifecycle.png
      assets/backends/backend-decision-map.svg assets/backends/backend-decision-map.png
      assets/integrations/ocr-web-flow.svg assets/integrations/ocr-web-flow.png
      assets/benchmarks/benchmark-interpretation-map.svg assets/benchmarks/benchmark-interpretation-map.png
    ],
  },
  "modules" => modules,
}

rendered = YAML.dump(manifest).each_line.map(&:rstrip).join("\n") + "\n"
File.write(OUTPUT, rendered)
puts "Image manual manifest written: #{modules.length} projects, #{overview_documents.length * 2} overview documents."
