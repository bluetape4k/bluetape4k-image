require "json"
require "fileutils"
require "minitest/autorun"
require "open3"
require "pathname"
require "tmpdir"
require "yaml"

require_relative "release_drift"

class ReleaseDriftTest < Minitest::Test
  TAG = "0.3.0"
  COMMIT = "a" * 40

  def test_derives_tag_topology_and_validates_all_release_surfaces
    with_fixture do |root, paths|
      result = contract(root, paths).validate

      assert_empty result.errors
      assert_equal TAG, result.expected.fetch(:release_ref)
      assert_equal COMMIT, result.expected.fetch(:release_commit)
      assert_equal 4, result.expected.fetch(:project_count)
      assert_equal 2, result.expected.fetch(:library_count)
      assert_equal 1, result.expected.fetch(:published_library_count)
      assert_equal 1, result.expected.fetch(:bom_count)
      assert_equal 1, result.expected.fetch(:example_count)
      assert_equal 1, result.expected.fetch(:benchmark_count)
      assert_equal "Image 0.3 / 4 projects / 1 workshops / 1 benchmark", result.expected.fetch(:diagram_label)
    end
  end

  def test_rejects_full_sha_and_short_sha_drift
    with_fixture do |root, paths|
      manifest = YAML.safe_load(File.read(paths.fetch(:manifest)))
      manifest["releaseCommit"] = "b" * 40
      File.write(paths.fetch(:manifest), YAML.dump(manifest))
      File.write(paths.fetch(:english_index), "The release commit bbbbbbbb is documented.\n")

      errors = contract(root, paths).validate.errors

      assert_includes errors, "manual manifest releaseCommit \"#{'b' * 40}\", expected #{COMMIT}"
      assert_includes errors, "#{relative(root, paths.fetch(:english_index))}: missing Release commit short/full SHA pair"
    end
  end

  def test_rejects_tag_derived_count_and_intelligence_drift
    with_fixture do |root, paths|
      manifest = YAML.safe_load(File.read(paths.fetch(:manifest)))
      manifest["modules"].reject! { |entry| entry.fetch("id") == "spring-boot-image-intelligence-api" }
      File.write(paths.fetch(:manifest), YAML.dump(manifest))
      generated = JSON.parse(File.read(paths.fetch(:generated_manifest)))
      generated["modules"].reject! { |entry| entry.fetch("id") == "spring-boot-image-intelligence-api" }
      File.write(paths.fetch(:generated_manifest), JSON.pretty_generate(generated) + "\n")

      errors = contract(root, paths).validate.errors

      assert errors.any? { |error| error.start_with?("manual manifest topology mismatch;") }
      assert_includes errors, "manual manifest is missing the spring-boot-image-intelligence-api example"
      assert errors.any? { |error| error.start_with?("generated manifest topology mismatch;") }
      assert_includes errors, "generated manifest is missing the spring-boot-image-intelligence-api example"
    end
  end

  def test_rejects_missing_english_repository_map_intelligence_reference
    with_fixture do |root, paths|
      map = paths.fetch(:english_map)
      original = File.read(map)
      mutated = original.sub("The image intelligence workshop is included. ", "")
      refute_equal original, mutated
      File.write(map, mutated)

      errors = contract(root, paths).validate.errors

      assert_includes errors, "docs/manual/en/architecture/repository-map.md: missing intelligence example reference"
    end
  end

  def test_rejects_missing_korean_repository_map_intelligence_reference
    with_fixture do |root, paths|
      map = paths.fetch(:korean_map)
      original = File.read(map)
      mutated = original.sub("이미지 인텔리전스 워크숍도 포함한다. ", "")
      refute_equal original, mutated
      File.write(map, mutated)

      errors = contract(root, paths).validate.errors

      assert_includes errors, "docs/manual/ko/architecture/repository-map.md: missing intelligence example reference"
    end
  end

  def test_rejects_a_moved_tag_even_when_the_old_snapshot_is_self_consistent
    with_fixture do |root, paths|
      moved_commit = "c" * 40
      runner = git_runner(moved_commit)

      errors = contract(root, paths, runner: runner).validate.errors

      assert_includes errors, "manual manifest releaseCommit \"#{COMMIT}\", expected #{moved_commit}"
      assert_includes errors, "generated manifest releaseCommit \"#{COMMIT}\", expected #{moved_commit}"
      assert_includes errors, "#{relative(root, paths.fetch(:english_index))}: visible short SHA aaaaaaaa, expected #{moved_commit[0, 8]}"
    end
  end

  def test_rejects_an_unresolvable_tag
    with_fixture do |root, paths|
      runner = lambda do |arguments|
        next ["", false] if arguments == ["rev-parse", "--verify", "refs/tags/#{TAG}^{commit}"]

        git_runner(COMMIT).call(arguments)
      end

      errors = contract(root, paths, runner: runner).validate.errors

      assert_equal ["release tag not found: refs/tags/#{TAG}"], errors
    end
  end

  def test_rejects_release_inventory_topology_drift
    with_fixture do |root, paths|
      inventory = JSON.parse(File.read(paths.fetch(:inventory)))
      inventory.first["sourceDir"] = "wrong/source"
      File.write(paths.fetch(:inventory), JSON.pretty_generate(inventory) + "\n")

      errors = contract(root, paths).validate.errors

      assert errors.any? { |error| error.start_with?("release module inventory topology mismatch;") }
      assert_includes errors.join("\n"), "wrong/source"
    end
  end

  def test_rejects_stale_visible_short_sha_when_full_link_is_current
    with_fixture do |root, paths|
      content = File.read(paths.fetch(:english_index)).sub("aaaaaaaa", "bbbbbbbb")
      File.write(paths.fetch(:english_index), content)

      errors = contract(root, paths).validate.errors

      assert_includes errors, "docs/manual/en/index.md: visible short SHA bbbbbbbb, expected aaaaaaaa"
    end
  end

  def test_rejects_frontmatter_and_visible_release_ref_drift
    with_fixture do |root, paths|
      index = File.read(paths.fetch(:english_index))
      File.write(paths.fetch(:english_index), "---\nreleaseRef: \"0.2.0\"\n---\n#{index}")
      map = File.read(paths.fetch(:english_map)).sub("0.3.0 release", "0.2.0 release")
      File.write(paths.fetch(:english_map), map)

      errors = contract(root, paths).validate.errors

      assert_includes errors, "docs/manual/en/index.md: frontmatter releaseRef [\"0.2.0\"], expected [0.3.0]"
      assert_includes errors, "docs/manual/en/architecture/repository-map.md: visible release refs [\"0.2.0\"], expected [0.3.0]"
    end
  end

  def test_rejects_missing_frontmatter_release_ref
    with_fixture do |root, paths|
      index = File.read(paths.fetch(:english_index)).sub("releaseRef: \"#{TAG}\"\n", "")
      File.write(paths.fetch(:english_index), index)

      errors = contract(root, paths).validate.errors

      assert_includes errors, "docs/manual/en/index.md: frontmatter releaseRef [], expected [#{TAG}]"
    end
  end

  def test_peels_an_annotated_tag_before_deriving_release_topology
    Dir.mktmpdir("release-drift-annotated") do |root|
      paths = write_fixture(root)
      write_git_fixture(root)
      run_git!(root, "init", "-q")
      run_git!(root, "config", "user.email", "test@example.test")
      run_git!(root, "config", "user.name", "Release Drift Test")
      run_git!(root, "add", ".")
      run_git!(root, "commit", "-qm", "fixture")
      run_git!(root, "tag", "-a", TAG, "-m", "annotated fixture")
      peeled_commit = run_git!(root, "rev-parse", "HEAD").strip

      %i[manifest generated_manifest].each do |key|
        path = paths.fetch(key)
        if key == :manifest
          manifest = YAML.safe_load(File.read(path))
          manifest["releaseCommit"] = peeled_commit
          File.write(path, YAML.dump(manifest))
        else
          generated = JSON.parse(File.read(path))
          generated["releaseCommit"] = peeled_commit
          File.write(path, JSON.pretty_generate(generated) + "\n")
        end
      end
      %i[english_index korean_index english_map korean_map].each do |key|
        path = paths.fetch(key)
        File.write(path, File.read(path).sub(COMMIT, peeled_commit).sub(COMMIT[0, 8], peeled_commit[0, 8]))
      end

      result = contract(root, paths, runner: nil).validate

      assert_empty result.errors
      assert_equal peeled_commit, result.expected.fetch(:release_commit)
    end
  end

  private

  def with_fixture
    Dir.mktmpdir("release-drift") do |root|
      paths = write_fixture(root)
      yield root, paths
    end
  end

  def write_fixture(root)
    FileUtils.mkdir_p(File.join(root, "docs/manual/en/architecture"))
    FileUtils.mkdir_p(File.join(root, "docs/manual/ko/architecture"))
    FileUtils.mkdir_p(File.join(root, "docs/manual/en"))
    FileUtils.mkdir_p(File.join(root, "docs/manual/ko"))
    FileUtils.mkdir_p(File.join(root, "build/manual"))

    modules = [
      { "id" => "bluetape4k-image-bom", "sourceDir" => "bom", "gradlePath" => ":bluetape4k-image-bom", "kind" => "library", "group" => "platform" },
      { "id" => "bluetape4k-images", "sourceDir" => "images", "gradlePath" => ":bluetape4k-images", "kind" => "library", "group" => "foundation" },
      { "id" => "bluetape4k-images-benchmark", "sourceDir" => "benchmark/images-benchmark", "gradlePath" => ":bluetape4k-images-benchmark", "kind" => "benchmark", "group" => "benchmarks" },
      { "id" => "spring-boot-image-intelligence-api", "sourceDir" => "examples/spring-boot-image-intelligence-api", "gradlePath" => ":spring-boot-image-intelligence-api", "kind" => "example", "group" => "workshops" },
    ]
    manifest = { "releaseRef" => TAG, "releaseCommit" => COMMIT, "modules" => modules }
    manifest_path = File.join(root, "docs/manual/manifest.yaml")
    generated_path = File.join(root, "docs/manual/generated/manifest.json")
    FileUtils.mkdir_p(File.dirname(manifest_path))
    FileUtils.mkdir_p(File.dirname(generated_path))
    File.write(manifest_path, YAML.dump(manifest))
    File.write(generated_path, JSON.pretty_generate(manifest) + "\n")

    inventory_path = File.join(root, "build/manual/release-module-inventory.json")
    inventory = modules.map do |entry|
      {
        "gradlePath" => entry.fetch("gradlePath"),
        "kind" => entry.fetch("kind"),
        "projectName" => entry.fetch("kind") == "example" ? entry.fetch("sourceDir").split("/").last : entry.fetch("id"),
        "sourceDir" => entry.fetch("sourceDir"),
      }
    end
    File.write(inventory_path, JSON.pretty_generate(inventory) + "\n")

    english_index = File.join(root, "docs/manual/en/index.md")
    korean_index = File.join(root, "docs/manual/ko/index.md")
    File.write(english_index, "---\nreleaseRef: \"#{TAG}\"\n---\nThe 0.3.0 release contains 1 published libraries, 1 published image BOM, 1 runnable examples, and 1 non-published benchmark project (4 Gradle projects in total). [Release commit aaaaaaaa](https://example.test/commit/#{COMMIT}).\n")
    File.write(korean_index, "---\nreleaseRef: \"#{TAG}\"\n---\n0.3.0 릴리스에는 배포 라이브러리 1개, 배포 BOM 1개, 실행 예제 1개, 배포하지 않는 벤치마크 프로젝트 1개가 들어 있으며 Gradle 프로젝트는 모두 4개다. [릴리스 커밋 aaaaaaaa](https://example.test/commit/#{COMMIT}).\n")
    repository_map_en = File.join(root, "docs/manual/en/architecture/repository-map.md")
    repository_map_ko = File.join(root, "docs/manual/ko/architecture/repository-map.md")
    File.write(repository_map_en, "---\nreleaseRef: \"#{TAG}\"\n---\n0.3.0 release: 1 published libraries, 1 published image BOM, 1 runnable examples, 1 non-published benchmark project, 4 Gradle projects. The image intelligence workshop is included. [Commit](https://example.test/commit/#{COMMIT}).\n")
    File.write(repository_map_ko, "---\nreleaseRef: \"#{TAG}\"\n---\n0.3.0 릴리스: 배포 라이브러리 1개, 배포 BOM 1개, 실행 예제 1개, 배포하지 않는 벤치마크 프로젝트 1개, Gradle 프로젝트 4개. 이미지 인텔리전스 워크숍도 포함한다. [커밋](https://example.test/commit/#{COMMIT}).\n")
    diagram_source = File.join(root, "scripts/manual/render_image_diagrams.rb")
    FileUtils.mkdir_p(File.dirname(diagram_source))
    File.write(diagram_source, 'canvas("title", "Image 0.3 / 4 projects / 1 workshops / 1 benchmark", "description", body)\n')

    {
      manifest: manifest_path,
      generated_manifest: generated_path,
      english_index: english_index,
      korean_index: korean_index,
      english_map: repository_map_en,
      korean_map: repository_map_ko,
      diagram_source: diagram_source,
      inventory: inventory_path,
    }
  end

  def contract(root, paths, runner: git_runner(COMMIT))
    ManualDocs::ReleaseDrift.new(
      repository_root: root,
      tag: TAG,
      manifest_path: paths.fetch(:manifest),
      generated_manifest_path: paths.fetch(:generated_manifest),
      index_paths: { "en" => paths.fetch(:english_index), "ko" => paths.fetch(:korean_index) },
      repository_map_paths: { "en" => paths.fetch(:english_map), "ko" => paths.fetch(:korean_map) },
      diagram_source_path: paths.fetch(:diagram_source),
      inventory_path: paths.fetch(:inventory),
      git_runner: runner,
    )
  end

  def git_runner(commit)
    settings = <<~KOTLIN
      project(":bluetape4k-images").projectDir = file("images")
      project(":bluetape4k-image-bom").projectDir = file("bom")
      project(":bluetape4k-images-benchmark").projectDir = file("benchmark/images-benchmark")
      project(":spring-boot-image-intelligence-api").projectDir = file("examples/spring-boot-image-intelligence-api")
    KOTLIN
    tree = <<~TREE
      images/build.gradle.kts
      bom/build.gradle.kts
      benchmark/images-benchmark/build.gradle.kts
      examples/spring-boot-image-intelligence-api/build.gradle.kts
      settings.gradle.kts
    TREE
    publishing_policy = <<~KOTLIN
      fun Project.isNonPublishedModule(): Boolean {
          val relativePath = rootProject.rootDir.toPath().relativize(projectDir.toPath()).toString()
          return relativePath == "examples" || relativePath.startsWith("examples/") ||
              relativePath == "benchmark" || relativePath.startsWith("benchmark/") ||
              name.contains("-demo") || name.endsWith("-benchmark")
      }
      subprojects.filterNot { it.isNonPublishedModule() }.forEach { add("nmcpAggregation", project(it.path)) }
    KOTLIN
    lambda do |arguments|
      case arguments
      when ["rev-parse", "--verify", "refs/tags/#{TAG}^{commit}"] then ["#{commit}\n", true]
      when ["ls-tree", "-r", "--name-only", commit] then [tree, true]
      when ["show", "#{commit}:settings.gradle.kts"] then [settings, true]
      when ["show", "#{commit}:build.gradle.kts"] then [publishing_policy, true]
      else ["", false]
      end
    end
  end

  def write_git_fixture(root)
    settings = <<~KOTLIN
      project(":bluetape4k-images").projectDir = file("images")
      project(":bluetape4k-image-bom").projectDir = file("bom")
      project(":bluetape4k-images-benchmark").projectDir = file("benchmark/images-benchmark")
      project(":spring-boot-image-intelligence-api").projectDir = file("examples/spring-boot-image-intelligence-api")
    KOTLIN
    policy = <<~KOTLIN
      fun Project.isNonPublishedModule(): Boolean {
          val relativePath = rootProject.rootDir.toPath().relativize(projectDir.toPath()).toString()
          return relativePath == "examples" || relativePath.startsWith("examples/") ||
              relativePath == "benchmark" || relativePath.startsWith("benchmark/") ||
              name.contains("-demo") || name.endsWith("-benchmark")
      }
      subprojects.filterNot { it.isNonPublishedModule() }.forEach { add("nmcpAggregation", project(it.path)) }
    KOTLIN
    File.write(File.join(root, "settings.gradle.kts"), settings)
    File.write(File.join(root, "build.gradle.kts"), policy)
    %w[images bom benchmark/images-benchmark examples/spring-boot-image-intelligence-api].each do |source|
      directory = File.join(root, source)
      FileUtils.mkdir_p(directory)
      File.write(File.join(directory, "build.gradle.kts"), "plugins { }\n")
    end
  end

  def run_git!(root, *arguments)
    stdout, stderr, status = Open3.capture3("git", "-C", root, *arguments)
    raise "git fixture command failed: #{arguments.join(' ')}: #{stderr}" unless status.success?

    stdout
  end

  def relative(root, path)
    Pathname.new(path).relative_path_from(Pathname.new(root)).to_s
  end
end
