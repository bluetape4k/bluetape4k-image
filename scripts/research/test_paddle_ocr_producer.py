from __future__ import annotations

import hashlib
import unittest

from paddle_ocr_producer_lib.contracts import (
    AttemptIdentity,
    ProducerValidationError,
    exact_object,
    jcs_bytes,
    load_json_bytes,
    load_jsonl_bytes,
    require_sha256,
    sha256_hex,
)


class StrictContractTest(unittest.TestCase):
    def test_duplicate_key_fails_closed(self) -> None:
        with self.assertRaisesRegex(ProducerValidationError, "duplicate JSON key"):
            load_json_bytes(b'{"schemaVersion":1,"schemaVersion":1}', 1024)

    def test_size_bom_trailing_and_root_type_fail_closed(self) -> None:
        cases = (
            (b'{"a":1}', 6, "exceeds"),
            (b'\xef\xbb\xbf{"a":1}', 1024, "BOM"),
            (b'{"a":1} trailing', 1024, "malformed JSON"),
            (b'[1]', 1024, "root must be an object"),
        )
        for payload, limit, message in cases:
            with self.subTest(message=message), self.assertRaisesRegex(
                ProducerValidationError, message
            ):
                load_json_bytes(payload, limit)

    def test_depth_and_entry_limits_are_exact(self) -> None:
        self.assertEqual(
            load_json_bytes(b'{"a":{"b":1}}', 1024, max_depth=3, max_entries=2),
            {"a": {"b": 1}},
        )
        with self.assertRaisesRegex(ProducerValidationError, "depth"):
            load_json_bytes(b'{"a":{"b":1}}', 1024, max_depth=2, max_entries=2)
        with self.assertRaisesRegex(ProducerValidationError, "entries"):
            load_json_bytes(b'{"a":{"b":1}}', 1024, max_depth=3, max_entries=1)

    def test_exact_object_reports_sorted_missing_and_unknown_keys(self) -> None:
        with self.assertRaisesRegex(
            ProducerValidationError,
            r"missing keys: a, z; unexpected keys: b, y",
        ):
            exact_object({"b": 1, "y": 2}, required={"z", "a"})

    def test_jcs_orders_keys_and_rejects_float(self) -> None:
        self.assertEqual(jcs_bytes({"b": 2, "a": 1}), b'{"a":1,"b":2}')
        self.assertEqual(
            jcs_bytes({"emoji": "한글", "items": [True, None, -2]}),
            '{"emoji":"한글","items":[true,null,-2]}'.encode(),
        )
        with self.assertRaisesRegex(ProducerValidationError, "integer"):
            jcs_bytes({"a": 1.0})

    def test_jcs_rejects_non_string_key_and_out_of_range_integer(self) -> None:
        for value, message in (
            ({1: "a"}, "string"),
            ({"a": 2**63}, "safe integer"),
        ):
            with self.subTest(value=value), self.assertRaisesRegex(
                ProducerValidationError, message
            ):
                jcs_bytes(value)

    def test_sha256_helpers_use_prefix_free_lowercase_hex(self) -> None:
        digest = hashlib.sha256(b"payload").hexdigest()
        self.assertEqual(sha256_hex(b"payload"), digest)
        self.assertEqual(require_sha256(digest), digest)
        for invalid in ("sha256:" + digest, digest.upper(), "0" * 63):
            with self.subTest(invalid=invalid), self.assertRaises(
                ProducerValidationError
            ):
                require_sha256(invalid)

    def test_attempt_identity_is_derived(self) -> None:
        identity = AttemptIdentity.from_run(1234, 2)
        self.assertEqual(identity.attempt_id, "1234.2")
        self.assertEqual(identity.image_tag, "image-1234.2")
        self.assertEqual(identity.evidence_tag, "evidence-1234.2")

    def test_attempt_identity_rejects_bool_zero_and_caller_tag(self) -> None:
        for run_id, run_attempt in ((True, 1), (0, 1), (1, False), (1, 0)):
            with self.subTest(run_id=run_id, run_attempt=run_attempt), self.assertRaises(
                ProducerValidationError
            ):
                AttemptIdentity.from_run(run_id, run_attempt)

    def test_jsonl_preserves_raw_bytes_and_validates_line_contract(self) -> None:
        raw = b'{"payload":"first"}\n{"payload":"second"}\n'
        parsed = load_jsonl_bytes(raw, 1024, max_lines=2)
        self.assertEqual(parsed.raw_bytes, raw)
        self.assertEqual(parsed.envelopes[1], {"payload": "second"})
        for invalid, message in (
            (b'\xef\xbb\xbf{}\n', "BOM"),
            (b'{}\r\n', "CR"),
            (b'{}\n\n', "blank"),
            (b'{}', "LF"),
        ):
            with self.subTest(message=message), self.assertRaisesRegex(
                ProducerValidationError, message
            ):
                load_jsonl_bytes(invalid, 1024, max_lines=2)

    def test_validation_error_never_echoes_secret_payload(self) -> None:
        secret = "ghp_super_secret_token"
        with self.assertRaises(ProducerValidationError) as raised:
            load_json_bytes((secret + " not-json").encode(), 1024)
        self.assertNotIn(secret, str(raised.exception))


if __name__ == "__main__":
    unittest.main()
