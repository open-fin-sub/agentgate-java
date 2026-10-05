#!/usr/bin/env python3
"""Generate Expectation/Condition golden fixtures for the Java domain contract tests.

Read-only against the Python agentgate implementation: validates sample inputs
through pydantic TypeAdapter(Condition)/TypeAdapter(Expectation) (the mirror of
the Java fromPayload dispatch) and records model_dump payloads, canonical JSON
and SHA-256 digests plus validation error messages. The Python repo is never
modified.

Usage:
    /path/to/pythonwork/agentgate/.venv/bin/python scripts/generate_expectations_golden.py \
        [--python-src /path/to/agentgate/src] [--output path.json]
"""

from __future__ import annotations

import argparse
import json
import platform
import sys
from pathlib import Path

from pydantic import TypeAdapter, ValidationError

REPO_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_PYTHON_SRC = "/Users/eric/hw/xql/pythonwork/agentgate/src"
DEFAULT_OUTPUT = REPO_ROOT / "src/test/resources/contract/expectations.json"

LEGAL_CONDITIONS: list[tuple[str, dict]] = [
    ("equals_number", {"kind": "equals", "expected": 42}),
    ("equals_string", {"kind": "equals", "expected": "文本答案"}),
    ("equals_null", {"kind": "equals", "expected": None}),
    ("equals_bool", {"kind": "equals", "expected": False}),
    ("within_tolerance_default_epsilon", {"kind": "within_tolerance", "expected": 0.5}),
    ("within_tolerance_explicit", {"kind": "within_tolerance", "expected": 1e-7, "epsilon": 0.001}),
    ("within_tolerance_int_input", {"kind": "within_tolerance", "expected": 10, "epsilon": 2}),
    ("within_range_both", {"kind": "within_range", "minimum": 0.0, "maximum": 1.0}),
    ("within_range_min_only", {"kind": "within_range", "minimum": -3.5}),
    ("within_range_max_only", {"kind": "within_range", "maximum": 100}),
    ("within_range_null_min", {"kind": "within_range", "minimum": None, "maximum": 10}),
    ("matches_pattern", {"kind": "matches_pattern", "pattern": "^A-\\d+$"}),
    ("matches_pattern_empty", {"kind": "matches_pattern", "pattern": ""}),
    ("one_of_mixed", {"kind": "one_of", "allowed": [1, "two", None, True]}),
    ("must_be_missing", {"kind": "must_be_missing"}),
    ("matches_json_schema", {"kind": "matches_json_schema",
                             "json_schema": {"type": "object", "properties": {"x": {"type": "number"}}}}),
    ("matches_json_schema_empty", {"kind": "matches_json_schema", "json_schema": {}}),
]

LEGAL_EXPECTATIONS: list[tuple[str, dict]] = [
    ("skill_route", {"kind": "skill_route", "id": "e1",
                     "condition": {"kind": "equals", "expected": "loan_skill"}}),
    ("skill_route_with_name", {"kind": "skill_route", "id": "e2", "name": "主技能路由",
                               "condition": {"kind": "one_of", "allowed": ["a", "b"]}}),
    ("skill_route_null_name", {"kind": "skill_route", "id": "e3", "name": None,
                               "condition": {"kind": "must_be_missing"}}),
    ("tool_call_default_mode", {"kind": "tool_call", "id": "e4", "tool": "search"}),
    ("tool_call_forbidden", {"kind": "tool_call", "id": "e5", "tool": "transfer", "mode": "forbidden"}),
    ("tool_call_with_name", {"kind": "tool_call", "id": "e6", "name": "禁止转账", "tool": "transfer",
                             "mode": "forbidden"}),
    ("tool_argument_default", {"kind": "tool_argument", "id": "e7", "tool": "search", "path": "query",
                               "condition": {"kind": "within_tolerance", "expected": 10, "epsilon": 0.5}}),
    ("tool_argument_first", {"kind": "tool_argument", "id": "e8", "tool": "search",
                             "path": "filters[0].city", "occurrence": "first",
                             "condition": {"kind": "within_range", "minimum": 1}}),
    ("tool_argument_all", {"kind": "tool_argument", "id": "e9", "tool": "search", "path": "q",
                           "occurrence": "all",
                           "condition": {"kind": "matches_pattern", "pattern": "^.+$"}}),
    ("state_expectation", {"kind": "state", "id": "e10", "path": "account.balance",
                           "condition": {"kind": "within_range", "minimum": 0}}),
    ("output_with_path", {"kind": "output", "id": "e11", "path": "summary.text",
                          "condition": {"kind": "matches_pattern", "pattern": "^.+$"}}),
    ("output_without_path", {"kind": "output", "id": "e12",
                             "condition": {"kind": "must_be_missing"}}),
    ("policy", {"kind": "policy", "id": "e13", "policy_id": "compliance.kyc.v2"}),
]

INVALID_SAMPLES: list[tuple[str, str, dict, bool]] = [
    ("within_range_none", "condition", {"kind": "within_range"}, True),
    ("within_range_reversed", "condition", {"kind": "within_range", "minimum": 5, "maximum": 1}, True),
    ("one_of_empty", "condition", {"kind": "one_of", "allowed": []}, True),
    ("one_of_duplicates", "condition", {"kind": "one_of", "allowed": [{"a": 1}, {"a": 1}]}, True),
    ("unknown_condition_kind", "expectation",
     {"kind": "skill_route", "id": "e1", "condition": {"kind": "mystery"}}, False),
    ("unknown_expectation_kind", "expectation", {"kind": "mystery", "id": "e1"}, False),
    ("expectation_blank_id", "expectation",
     {"kind": "policy", "id": "   ", "policy_id": "p"}, True),
    ("expectation_blank_name", "expectation",
     {"kind": "policy", "id": "e1", "name": "  ", "policy_id": "p"}, True),
    ("tool_call_blank_tool", "expectation", {"kind": "tool_call", "id": "e1", "tool": " "}, True),
    ("tool_call_bad_mode", "expectation",
     {"kind": "tool_call", "id": "e1", "tool": "t", "mode": "sometimes"}, True),
    ("tool_call_missing_tool", "expectation", {"kind": "tool_call", "id": "e1"}, True),
    ("skill_route_missing_condition", "expectation", {"kind": "skill_route", "id": "e1"}, True),
    ("tool_argument_blank_path", "expectation",
     {"kind": "tool_argument", "id": "e1", "tool": "t", "path": "",
      "condition": {"kind": "must_be_missing"}}, True),
    ("tool_argument_bad_occurrence", "expectation",
     {"kind": "tool_argument", "id": "e1", "tool": "t", "path": "p", "occurrence": "every",
      "condition": {"kind": "must_be_missing"}}, True),
    ("state_blank_path", "expectation", {"kind": "state", "id": "e1", "path": " ",
                                         "condition": {"kind": "must_be_missing"}}, True),
    ("output_blank_path", "expectation",
     {"kind": "output", "id": "e1", "path": " ", "condition": {"kind": "must_be_missing"}}, True),
    ("policy_blank_id", "expectation", {"kind": "policy", "id": "e1", "policy_id": "  "}, True),
]


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--python-src", default=DEFAULT_PYTHON_SRC)
    parser.add_argument("--output", default=str(DEFAULT_OUTPUT))
    args = parser.parse_args()

    sys.path.insert(0, args.python_src)
    from agentgate.domain.base import canonical_json, content_sha256
    from agentgate.domain.expectation import Condition, Expectation

    condition_adapter: TypeAdapter = TypeAdapter(Condition)
    expectation_adapter: TypeAdapter = TypeAdapter(Expectation)

    samples = []
    for union, entries in (("condition", LEGAL_CONDITIONS), ("expectation", LEGAL_EXPECTATIONS)):
        adapter = condition_adapter if union == "condition" else expectation_adapter
        for name, value in entries:
            model = adapter.validate_python(value)
            payload = model.model_dump(mode="json")
            assert payload == json.loads(json.dumps(payload, ensure_ascii=False)), \
                f"sample {name} payload does not round-trip as JSON"
            samples.append({
                "name": name,
                "union": union,
                "input": value,
                "payload": payload,
                "canonical": canonical_json(model),
                "sha256": content_sha256(model),
            })

    invalid = []
    for name, union, value, alignable in INVALID_SAMPLES:
        adapter = condition_adapter if union == "condition" else expectation_adapter
        try:
            adapter.validate_python(value)
        except ValidationError as exc:
            errors = exc.errors()
            assert len(errors) == 1, f"sample {name}: expected a single error, got {errors}"
            invalid.append({
                "name": name,
                "union": union,
                "input": value,
                "message_alignable": alignable,
                "error": errors[0]["msg"].removeprefix("Value error, "),
            })
        else:
            raise SystemExit(f"invalid sample {name} unexpectedly passed validation")

    document = {
        "description": (
            "Golden fixtures for Condition/Expectation parity with Python "
            "agentgate.domain.expectation. Generated by "
            "scripts/generate_expectations_golden.py via pydantic TypeAdapter dispatch."
        ),
        "python_version": platform.python_version(),
        "samples": samples,
        "invalid_samples": invalid,
    }

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    with open(output, "w", encoding="utf-8") as handle:
        json.dump(document, handle, ensure_ascii=False, indent=2)
        handle.write("\n")
    print(f"wrote {len(samples)} legal samples and {len(invalid)} invalid samples to {output}")


if __name__ == "__main__":
    main()
