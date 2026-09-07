from __future__ import annotations

import json
import unittest

from paddle_ocr_producer_lib.contracts import ProducerValidationError, jcs_bytes
from paddle_ocr_producer_lib.registry import (
    HttpLimits,
    HttpStatusError,
    classify_http_status,
    collect_package_pages,
    retry_delays,
    run_with_retry,
    select_dispatched_run,
    select_exact_version,
    validate_anonymous_environment,
)


def package_version(version_id: int, tag: str) -> dict[str, object]:
    return {
        "id": version_id,
        "name": "sha256:" + f"{version_id:064x}",
        "metadata": {"container": {"tags": [tag]}},
    }


class RegistryContractTest(unittest.TestCase):
    def test_dispatched_run_selection_is_new_unique_and_exact(self) -> None:
        before = {40, 41}
        run = {
            "databaseId": 42,
            "runAttempt": 1,
            "headSha": "a" * 40,
            "workflowPath": ".github/workflows/paddleocr-producer.yml",
            "event": "workflow_dispatch",
        }
        self.assertEqual(
            select_dispatched_run(
                before, [[run]], expected_head="a" * 40,
                expected_workflow=".github/workflows/paddleocr-producer.yml",
            ),
            run,
        )
        for pages in ([], [[run, {**run, "databaseId": 43}]], [[{**run, "runAttempt": 2}]]):
            with self.subTest(pages=pages), self.assertRaises(ProducerValidationError):
                select_dispatched_run(
                    before, pages, expected_head="a" * 40,
                    expected_workflow=".github/workflows/paddleocr-producer.yml",
                )

    def test_two_matching_versions_are_rejected_across_pages(self) -> None:
        pages = [[package_version(1, "image-44.2")], [package_version(2, "image-44.2")]]
        with self.assertRaisesRegex(ProducerValidationError, "ambiguous package state"):
            select_exact_version(pages, "image-44.2")

    def test_zero_and_one_exact_version_have_distinct_results(self) -> None:
        self.assertIsNone(select_exact_version([[package_version(1, "other")]], "image-44.2"))
        selected = select_exact_version([[package_version(1, "image-44.2")]], "image-44.2")
        self.assertEqual(selected["id"], 1)

    def test_page_twenty_with_next_link_fails_closed(self) -> None:
        limits = HttpLimits(max_pages=2, max_page_items=2, max_total_bytes=4096)
        bodies = {
            None: (jcs_bytes([package_version(1, "other")]), "page-2"),
            "page-2": (jcs_bytes([package_version(2, "other")]), "page-3"),
        }

        def fetch(cursor: str | None) -> tuple[bytes, str | None]:
            return bodies[cursor]

        with self.assertRaisesRegex(ProducerValidationError, "pagination"):
            collect_package_pages(fetch, limits=limits)

    def test_page_parser_rejects_oversize_malformed_and_too_many_items(self) -> None:
        fixtures = (
            (b"[", HttpLimits(), "malformed"),
            (json.dumps([package_version(1, "a"), package_version(2, "b")]).encode(), HttpLimits(max_page_items=1), "items"),
            (b"[]" * 20, HttpLimits(max_page_bytes=4), "bytes"),
        )
        for body, limits, message in fixtures:
            with self.subTest(message=message), self.assertRaisesRegex(ProducerValidationError, message):
                collect_package_pages(lambda _, payload=body: (payload, None), limits=limits)

    def test_retry_classification_is_operation_specific(self) -> None:
        self.assertEqual(retry_delays("GITHUB_API", 429, None), (2, 4))
        self.assertEqual(retry_delays("REGISTRY", 503, None), (2, 4))
        self.assertEqual(retry_delays("REGISTRY", None, TimeoutError()), (2, 4))
        self.assertEqual(retry_delays("PUBLIC_AFTER_VISIBILITY", 404, None), (2, 4))
        self.assertEqual(retry_delays("GITHUB_API", 404, None), ())
        self.assertEqual(retry_delays("GITHUB_API", 403, None), ())
        self.assertEqual(classify_http_status("GITHUB_API", 401), "BLOCKED")
        self.assertEqual(classify_http_status("GITHUB_API", 403), "BLOCKED")
        self.assertEqual(classify_http_status("GITHUB_API", 404), "ABSENT")
        self.assertEqual(classify_http_status("PUBLIC_AFTER_VISIBILITY", 404), "TRANSIENT")
        self.assertEqual(classify_http_status("GITHUB_API", 422), "REJECTED")

    def test_retry_runner_records_backoff_and_stops_after_success(self) -> None:
        clock = [10.0]
        attempts = []

        def call() -> str:
            attempts.append(len(attempts) + 1)
            if len(attempts) < 3:
                raise HttpStatusError(503)
            return "ok"

        def sleep(seconds: float) -> None:
            clock[0] += seconds

        result, receipt = run_with_retry(
            "registry-list-44.2",
            "REGISTRY",
            call,
            deadline_seconds=30,
            monotonic=lambda: clock[0],
            sleeper=sleep,
        )
        self.assertEqual(result, "ok")
        self.assertEqual(receipt, {
            "operationId": "registry-list-44.2",
            "attempts": 3,
            "delaysSeconds": [2, 4],
            "outcome": "SUCCESS",
        })

    def test_retry_runner_does_not_retry_permanent_or_cancellation_failures(self) -> None:
        for error in (HttpStatusError(403), ValueError("schema"), KeyboardInterrupt()):
            calls = []

            def call(failure: BaseException = error, bound_calls: list[int] = calls) -> None:
                bound_calls.append(1)
                raise failure

            with self.subTest(error=type(error).__name__), self.assertRaises(type(error)):
                run_with_retry(
                    "operation",
                    "GITHUB_API",
                    call,
                    deadline_seconds=30,
                    monotonic=lambda: 1.0,
                    sleeper=lambda _: self.fail("permanent error must not sleep"),
                )
            self.assertEqual(calls, [1])

    def test_retry_runner_fails_before_sleep_would_cross_deadline(self) -> None:
        clock = [1.0]
        with self.assertRaisesRegex(ProducerValidationError, "deadline"):
            run_with_retry(
                "operation",
                "REGISTRY",
                lambda: (_ for _ in ()).throw(TimeoutError()),
                deadline_seconds=2,
                monotonic=lambda: clock[0],
                sleeper=lambda _: self.fail("deadline must prevent sleep"),
            )

    def test_public_verifier_rejects_credentials_before_transport(self) -> None:
        clean = {"HOME": "/tmp/anonymous", "DOCKER_CONFIG": "/tmp/anonymous/docker"}
        validate_anonymous_environment(clean, auth_files_exist=lambda _: False)
        for key in ("GH_TOKEN", "GITHUB_TOKEN", "DOCKER_AUTH_CONFIG", "REGISTRY_AUTH_FILE"):
            env = dict(clean)
            env[key] = "secret"
            with self.subTest(key=key), self.assertRaisesRegex(ProducerValidationError, "credential"):
                validate_anonymous_environment(env, auth_files_exist=lambda _: False)
        with self.assertRaisesRegex(ProducerValidationError, "credential"):
            validate_anonymous_environment(clean, auth_files_exist=lambda _: True)


if __name__ == "__main__":
    unittest.main()
