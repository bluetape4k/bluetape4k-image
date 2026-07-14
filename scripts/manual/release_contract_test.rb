require "fileutils"
require "minitest/autorun"
require "tmpdir"

require_relative "release_contract"

class ReleaseContractTest < Minitest::Test
  def test_rejects_a_moved_tag
    runner = ->(args) { args.first == "rev-parse" ? [("a" * 40) + "\n", true] : ["", true] }
    result = ManualDocs::ReleaseContract.new(
      repository_root: Dir.pwd, tag: "0.3.0", expected_sha: "b" * 40, git_runner: runner,
    ).validate
    assert_equal ["release tag 0.3.0 resolves to #{'a' * 40}, expected #{'b' * 40}"], result.errors
  end

  def test_reports_a_release_relative_link_absent_from_the_tag
    Dir.mktmpdir("release-contract") do |root|
      FileUtils.mkdir_p(File.join(root, "docs/manual/en/modules"))
      File.write(File.join(root, "docs/manual/en/modules/core.md"), "[Missing](../../../../images/src/Missing.kt)\n")
      runner = lambda do |args|
        case args
        when ["rev-parse", "--verify", "refs/tags/0.3.0^{commit}"] then [("a" * 40) + "\n", true]
        when ["ls-tree", "-r", "--name-only", "a" * 40] then ["images/src/Present.kt\n", true]
        else ["", false]
        end
      end
      result = ManualDocs::ReleaseContract.new(
        repository_root: root, tag: "0.3.0", expected_sha: "a" * 40, git_runner: runner,
      ).validate
      assert_equal ["docs/manual/en/modules/core.md:1: release path not found: images/src/Missing.kt"], result.errors
      assert_equal 1, result.checked_count
    end
  end

  def test_fails_closed_when_git_inventory_cannot_be_read
    runner = ->(_args) { ["", false] }
    errors = ManualDocs::ReleaseContract.new(
      repository_root: Dir.pwd, tag: "0.3.0", expected_sha: "a" * 40, git_runner: runner,
    ).errors
    assert_equal ["release tag not found: refs/tags/0.3.0"], errors
  end

  def test_validates_repository_relative_reference_links
    Dir.mktmpdir("release-contract") do |root|
      FileUtils.mkdir_p(File.join(root, "docs/manual/en/modules"))
      File.write(File.join(root, "docs/manual/en/modules/core.md"), <<~MARKDOWN)
        [Missing source][source]

        [source]: ../../../../images/src/Missing.kt
      MARKDOWN
      runner = lambda do |args|
        case args
        when ["rev-parse", "--verify", "refs/tags/0.3.0^{commit}"] then [("a" * 40) + "\n", true]
        when ["ls-tree", "-r", "--name-only", "a" * 40] then ["images/src/Present.kt\n", true]
        else ["", false]
        end
      end
      result = ManualDocs::ReleaseContract.new(
        repository_root: root, tag: "0.3.0", expected_sha: "a" * 40, git_runner: runner,
      ).validate
      assert_equal ["docs/manual/en/modules/core.md:1: release path not found: images/src/Missing.kt"], result.errors
      assert_equal 1, result.checked_count
    end
  end

  def test_validates_a_shallow_manuals_repository_relative_link
    Dir.mktmpdir("release-contract") do |root|
      FileUtils.mkdir_p(File.join(root, "docs/manual/en"))
      File.write(File.join(root, "docs/manual/en/index.md"), "[Missing](../../../images/src/Missing.kt)\n")
      runner = lambda do |args|
        case args
        when ["rev-parse", "--verify", "refs/tags/0.3.0^{commit}"] then [("a" * 40) + "\n", true]
        when ["ls-tree", "-r", "--name-only", "a" * 40] then ["images/src/Present.kt\n", true]
        else ["", false]
        end
      end
      result = ManualDocs::ReleaseContract.new(
        repository_root: root, tag: "0.3.0", expected_sha: "a" * 40, git_runner: runner,
      ).validate
      assert_equal ["docs/manual/en/index.md:1: release path not found: images/src/Missing.kt"], result.errors
      assert_equal 1, result.checked_count
    end
  end

  def test_validates_pinned_github_release_links
    Dir.mktmpdir("release-contract") do |root|
      FileUtils.mkdir_p(File.join(root, "docs/manual/en"))
      File.write(File.join(root, "docs/manual/en/index.md"), <<~MARKDOWN)
        [Good](https://github.com/bluetape4k/bluetape4k-image/blob/0.3.0/images/src/Present.kt)
        [Missing](https://github.com/bluetape4k/bluetape4k-image/tree/#{'a' * 40}/missing)
        [Drifted](https://github.com/bluetape4k/bluetape4k-image/blob/develop/images/src/Present.kt)
      MARKDOWN
      runner = lambda do |args|
        case args
        when ["rev-parse", "--verify", "refs/tags/0.3.0^{commit}"] then [("a" * 40) + "\n", true]
        when ["ls-tree", "-r", "--name-only", "a" * 40] then ["images/src/Present.kt\n", true]
        else ["", false]
        end
      end
      result = ManualDocs::ReleaseContract.new(
        repository_root: root, tag: "0.3.0", expected_sha: "a" * 40, git_runner: runner,
      ).validate
      assert_equal 3, result.checked_count
      assert_equal [
        "docs/manual/en/index.md:2: release path not found: missing",
        "docs/manual/en/index.md:3: release source uses develop, expected 0.3.0 or #{'a' * 40}: https://github.com/bluetape4k/bluetape4k-image/blob/develop/images/src/Present.kt",
      ], result.errors
    end
  end
end
