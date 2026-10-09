import importlib.util
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch


MODULE_PATH = Path(__file__).with_name("compare_backends.py")
SPEC = importlib.util.spec_from_file_location("compare_backends", MODULE_PATH)
COMPARE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(COMPARE)


class ApiParityTest(unittest.TestCase):

    def test_render_preserves_typed_whole_variable(self):
        value = COMPARE.render({"body": "${payload}", "path": "/${id}"}, {
            "payload": {"count": 2},
            "id": "abc",
        })
        self.assertEqual({"count": 2}, value["body"])
        self.assertEqual("/abc", value["path"])

    def test_normalization_and_structural_diff(self):
        config = {
            "patterns": {
                "uuid": {
                    "regex": "[0-9a-f]{8}-[0-9a-f-]{27,}",
                    "replacement": "<uuid>",
                }
            }
        }
        left = COMPARE.normalize({"id": "123e4567-e89b-12d3-a456-426614174000", "value": 1}, ["uuid"], config)
        right = COMPARE.normalize({"id": "223e4567-e89b-12d3-a456-426614174001", "value": 2}, ["uuid"], config)
        self.assertEqual([{"path": "/value", "python": 1, "java": 2}], COMPARE.differences(left, right))

    def test_independent_extracted_variables_drive_each_backend(self):
        manifest = {
            "cases": [
                {
                    "id": "create",
                    "method": "POST",
                    "path": "/items",
                    "default": True,
                    "extract": {"item_id": "/data/id"},
                    "normalizers": ["uuid"],
                },
                {
                    "id": "read",
                    "method": "GET",
                    "path": "/items/${item_id}",
                    "default": True,
                    "normalizers": ["uuid"],
                },
            ]
        }
        config = {
            "default_normalizers": [],
            "patterns": {
                "uuid": {
                    "regex": "(?:python|java)-id",
                    "replacement": "<uuid>",
                }
            },
        }
        seen = []

        def fake_request(base_url, case, variables, timeout):
            backend = "python" if "python" in base_url else "java"
            generated = backend + "-id"
            path = COMPARE.render(case["path"], variables)
            seen.append((backend, path))
            body = {"code": "0", "data": {"id": generated}}
            return {"status": 200, "body": body, "body_kind": "json", "elapsed_ms": 1}

        args = SimpleNamespace(
            all=False,
            case=[],
            variables=None,
            python_url="http://python",
            java_url="http://java",
            timeout=1,
            strict_skips=False,
        )
        with patch.object(COMPARE, "http_request", side_effect=fake_request):
            report = COMPARE.run_comparison(manifest, config, args)
        self.assertEqual(0, report["summary"]["failed"])
        self.assertIn(("python", "/items/python-id"), seen)
        self.assertIn(("java", "/items/java-id"), seen)

    def test_missing_fixture_is_skip_or_failure(self):
        case = {"id": "read", "method": "GET", "path": "/items/${item_id}"}
        results = COMPARE.execute_backend("python", "http://unused", [case], {}, 1)
        comparisons, failed = COMPARE.compare_results(
            [case], results, results, {"default_normalizers": []}, False
        )
        self.assertEqual("SKIPPED", comparisons[0]["state"])
        self.assertEqual(0, failed)
        _, strict_failed = COMPARE.compare_results(
            [case], results, results, {"default_normalizers": []}, True
        )
        self.assertEqual(1, strict_failed)


if __name__ == "__main__":
    unittest.main()
