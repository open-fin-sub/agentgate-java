#!/usr/bin/env python3
"""Generate Case/CaseTurn golden fixtures for the Java domain contract tests.

Read-only against the Python agentgate implementation: validates sample inputs
through pydantic model_validate and records model_dump payloads, canonical JSON
and SHA-256 digests plus validation error messages. The Python repo is never
modified.

Usage:
    /path/to/pythonwork/agentgate/.venv/bin/python scripts/generate_cases_golden.py \
        [--python-src /path/to/agentgate/src] [--output path.json]
"""

from __future__ import annotations

import argparse
import json
import platform
import sys
from pathlib import Path

from pydantic import ValidationError

REPO_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_PYTHON_SRC = "/Users/eric/hw/xql/pythonwork/agentgate/src"
DEFAULT_OUTPUT = REPO_ROOT / "src/test/resources/contract/cases.json"

LEGAL_TURNS: list[tuple[str, dict]] = [
    ("turn_minimal", {"id": "t1", "input": {"question": "查询余额"}}),
    ("turn_input_nested", {"id": "t1", "input": {"a": [1, 2.5, {"b": None}], "c": True, "emoji": "🚀"}}),
    ("turn_with_notes", {"id": "t1", "input": {"q": 1}, "notes": "边界备注"}),
    ("turn_with_expectations", {"id": "t1", "input": {"q": 1}, "expectations": [
        {"kind": "policy", "id": "e1", "policy_id": "compliance.kyc"},
        {"kind": "state", "id": "e2", "path": "account.balance",
         "condition": {"kind": "within_range", "minimum": 0}},
    ]}),
    ("turn_empty_expectations_explicit", {"id": "t1", "input": {"q": 1}, "expectations": []}),
]

LEGAL_CASES: list[tuple[str, dict]] = [
    ("case_minimal", {"id": "c1", "name": "转账查询", "turns": [
        {"id": "t1", "input": {"q": "我要转账"}}]}),
    ("case_multi_turn", {"id": "c2", "name": "多轮对话", "turns": [
        {"id": "t1", "input": {"q": "查余额"}, "expectations": [
            {"kind": "output", "id": "e1", "path": "answer",
             "condition": {"kind": "matches_pattern", "pattern": "^余额"}}]},
        {"id": "t2", "input": {"q": "转账"}, "expectations": [
            {"kind": "tool_call", "id": "e2", "tool": "transfer", "mode": "forbidden"}]},
    ]}),
    ("case_full", {"id": "c3", "name": "完整字段", "turns": [
        {"id": "t1", "input": {"q": 1}, "notes": "轮备注"}],
        "initial_state": {"account": {"balance": 100}},
        "category": "negative", "difficulty": "hard",
        "tags": ["中文标签", "smoke"], "notes": "用例备注"}),
    ("case_default_category_difficulty", {"id": "c4", "name": "默认值", "turns": [
        {"id": "t1", "input": {"q": 1}}]}),
    ("case_explicit_defaults", {"id": "c5", "name": "显式默认", "turns": [
        {"id": "t1", "input": {"q": 1}}],
        "initial_state": {}, "category": "positive", "difficulty": "medium",
        "tags": [], "notes": ""}),
    ("case_boundary_category", {"id": "c6", "name": "边界分类", "turns": [
        {"id": "t1", "input": {"q": 1}}], "category": "boundary", "difficulty": "easy"}),
    ("case_tags_with_unicode", {"id": "c7", "name": "unicode 标签", "turns": [
        {"id": "t1", "input": {"q": 1}}], "tags": ["🚀", "_release"]}),
]

INVALID_SAMPLES: list[tuple[str, str, dict]] = [
    ("turn_blank_id", "turn", {"id": " ", "input": {"q": 1}}),
    ("turn_missing_input", "turn", {"id": "t1"}),
    ("turn_empty_input", "turn", {"id": "t1", "input": {}}),
    ("turn_input_not_dict", "turn", {"id": "t1", "input": [1, 2]}),
    ("case_blank_id", "case", {"id": " ", "name": "n", "turns": [
        {"id": "t1", "input": {"q": 1}}]}),
    ("case_blank_name", "case", {"id": "c1", "name": " ", "turns": [
        {"id": "t1", "input": {"q": 1}}]}),
    ("case_no_turns", "case", {"id": "c1", "name": "n", "turns": []}),
    ("case_missing_turns", "case", {"id": "c1", "name": "n"}),
    ("case_dup_turn_ids", "case", {"id": "c1", "name": "n", "turns": [
        {"id": "t1", "input": {"q": 1}},
        {"id": "t1", "input": {"q": 2}}]}),
    ("case_dup_expectation_ids", "case", {"id": "c1", "name": "n", "turns": [
        {"id": "t1", "input": {"q": 1}, "expectations": [
            {"kind": "policy", "id": "e1", "policy_id": "p1"}]},
        {"id": "t2", "input": {"q": 2}, "expectations": [
            {"kind": "policy", "id": "e1", "policy_id": "p2"}]}]}),
    ("case_bad_category", "case", {"id": "c1", "name": "n", "turns": [
        {"id": "t1", "input": {"q": 1}}], "category": "weird"}),
    ("case_bad_difficulty", "case", {"id": "c1", "name": "n", "turns": [
        {"id": "t1", "input": {"q": 1}}], "difficulty": "impossible"}),
    ("case_blank_tags", "case", {"id": "c1", "name": "n", "turns": [
        {"id": "t1", "input": {"q": 1}}], "tags": ["a", " "]}),
    ("case_dup_tags", "case", {"id": "c1", "name": "n", "turns": [
        {"id": "t1", "input": {"q": 1}}], "tags": ["a", "a"]}),
    ("case_nested_turn_error_propagates", "case", {"id": "c1", "name": "n", "turns": [
        {"id": "t1", "input": {}}]}),
]


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--python-src", default=DEFAULT_PYTHON_SRC)
    parser.add_argument("--output", default=str(DEFAULT_OUTPUT))
    args = parser.parse_args()

    sys.path.insert(0, args.python_src)
    from agentgate.domain.base import canonical_json, content_sha256
    from agentgate.domain.case import Case, CaseTurn

    samples = []
    for union, entries in (("turn", LEGAL_TURNS), ("case", LEGAL_CASES)):
        model_cls = CaseTurn if union == "turn" else Case
        for name, value in entries:
            model = model_cls.model_validate(value)
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
    for name, union, value in INVALID_SAMPLES:
        model_cls = CaseTurn if union == "turn" else Case
        try:
            model_cls.model_validate(value)
        except ValidationError as exc:
            errors = exc.errors()
            assert errors, f"sample {name}: no validation errors"
            invalid.append({
                "name": name,
                "union": union,
                "input": value,
                "error": errors[0]["msg"].removeprefix("Value error, "),
            })
        else:
            raise SystemExit(f"invalid sample {name} unexpectedly passed validation")

    document = {
        "description": (
            "Golden fixtures for Case/CaseTurn parity with Python "
            "agentgate.domain.case. Generated by scripts/generate_cases_golden.py."
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
