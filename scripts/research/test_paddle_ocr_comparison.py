import json
import sys
import unittest
from pathlib import Path

SCRIPT_ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(SCRIPT_ROOT))

from paddle_ocr_comparison import (
    MANIFEST_SHA256,
    PADDLE_QUERY_SCRIPT,
    ComparisonValidationError,
    _source_commit,
    geometry_accuracy,
    load_corpus,
    normalize_text,
    parse_paddle_response,
    parse_tesseract_tsv,
    score_text,
)


class PaddleOcrComparisonContractTest(unittest.TestCase):
    def test_normalize_and_score_use_nfc_lf_collapsed_whitespace(self) -> None:
        self.assertEqual(normalize_text("  A\r\nB\t"), "A B")
        rates = score_text("한글 A", "한글  A")
        self.assertEqual(rates["characterEdits"], 0)
        self.assertEqual(rates["wordEdits"], 0)
        self.assertEqual(rates["cer"], 0.0)
        self.assertEqual(rates["wer"], 0.0)

    def test_parse_paddle_response_reads_pruned_result_geometry(self) -> None:
        payload = {
            "result": {
                "ocrResults": [
                    {
                        "prunedResult": {
                            "rec_texts": ["Hello", "세계"],
                            "rec_scores": [0.98, 0.87],
                            "rec_polys": [
                                [[10, 20], [110, 20], [110, 50], [10, 50]],
                                [[12, 70], [80, 70], [80, 100], [12, 100]],
                            ],
                        }
                    }
                ]
            }
        }
        result = parse_paddle_response(
            json.dumps(payload).encode("utf-8"), width=1600, height=1000
        )
        self.assertEqual(result["text"], "Hello\n세계")
        self.assertEqual(result["actualOutcome"], "TEXT")
        self.assertEqual(result["geometry"][0]["width"], 100)
        self.assertEqual(result["geometry"][1]["height"], 30)
        self.assertEqual(result["geometry"][0]["confidence"], 0.98)

    def test_parse_paddle_response_rejects_unknown_shape(self) -> None:
        payload = {"result": {"ocrResults": [{"prunedResult": {"rec_texts": ["x"]}}]}}
        with self.assertRaisesRegex(ComparisonValidationError, "geometry"):
            parse_paddle_response(json.dumps(payload).encode(), width=100, height=100)

    def test_parse_paddle_response_rejects_mismatched_arrays(self) -> None:
        payload = {
            "result": {
                "ocrResults": [
                    {
                        "prunedResult": {
                            "rec_texts": ["x"],
                            "rec_scores": [],
                            "rec_polys": [],
                        }
                    }
                ]
            }
        }
        with self.assertRaisesRegex(ComparisonValidationError, "counts differ"):
            parse_paddle_response(json.dumps(payload).encode(), width=100, height=100)

    def test_parse_tesseract_tsv_groups_words_into_line_geometry(self) -> None:
        tsv = (
            "level\tpage_num\tblock_num\tpar_num\tline_num\tword_num\tleft\ttop\twidth\theight\tconf\ttext\n"
            "5\t1\t1\t1\t1\t1\t10\t20\t40\t20\t96.0\tHello\n"
            "5\t1\t1\t1\t1\t2\t60\t20\t30\t20\t90.0\tworld\n"
        )
        result = parse_tesseract_tsv(tsv.encode(), width=1600, height=1000)
        self.assertEqual(result["text"], "Hello world")
        self.assertEqual(len(result["geometry"]), 1)
        self.assertEqual(result["geometry"][0]["width"], 80)
        self.assertEqual(result["geometry"][0]["height"], 20)
        self.assertEqual(result["geometry"][0]["text"], "Hello world")

    def test_parse_tesseract_tsv_rejects_out_of_bounds_geometry(self) -> None:
        tsv = (
            "level\tpage_num\tblock_num\tpar_num\tline_num\tword_num\tleft\ttop\twidth\theight\tconf\ttext\n"
            "5\t1\t1\t1\t1\t1\t90\t20\t40\t20\t96.0\tHello\n"
        )
        with self.assertRaisesRegex(ComparisonValidationError, "geometry"):
            parse_tesseract_tsv(tsv.encode(), width=100, height=100)

    def test_load_corpus_verifies_manifest_and_full_fixture_order(self) -> None:
        repo_root = Path(__file__).resolve().parents[2]
        manifest_sha, entries = load_corpus(repo_root)
        self.assertEqual(manifest_sha, MANIFEST_SHA256)
        self.assertEqual(len(entries), 27)
        self.assertEqual(entries[0].fixture_id, "clean-text-v2-001")
        self.assertEqual(entries[23].expected_outcome, "EMPTY")
        self.assertEqual(entries[-1].expected_outcome, "ERROR")

    def test_paddle_query_script_is_self_contained_python(self) -> None:
        compile(PADDLE_QUERY_SCRIPT, "<paddle-query>", "exec")

    def test_source_commit_is_exact_git_head(self) -> None:
        repo_root = Path(__file__).resolve().parents[2]
        commit = _source_commit(repo_root)
        self.assertRegex(commit, r"^[0-9a-f]{40}$")

    def test_geometry_accuracy_matches_text_and_iou(self) -> None:
        expected = [
            {"text": "Hello", "x": 10, "y": 20, "width": 100, "height": 30},
            {"text": "세계", "x": 12, "y": 70, "width": 68, "height": 30},
        ]
        predicted = [
            {"text": "Hello", "x": 12, "y": 22, "width": 96, "height": 28},
            {"text": "세계", "x": 0, "y": 0, "width": 10, "height": 10},
        ]
        result = geometry_accuracy(expected, predicted)
        self.assertEqual(result["expectedBoxes"], 2)
        self.assertEqual(result["matchedBoxes"], 1)
        self.assertGreater(result["score"], 0.0)
        self.assertLess(result["score"], 1.0)


if __name__ == "__main__":
    unittest.main()
