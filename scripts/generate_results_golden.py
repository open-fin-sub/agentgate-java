#!/usr/bin/env python3
"""Generate result-domain golden fixtures for the Java domain contract tests.

Read-only against the Python agentgate implementation: validates CheckResult,
EvaluationResult, JudgeRecord, EvaluatorErrorDetail, ReleaseGateDecision and
classify_release_gate samples. The Python repo is never modified.

Usage:
    /path/to/pythonwork/agentgate/.venv/bin/python scripts/generate_results_golden.py \
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
DEFAULT_OUTPUT = REPO_ROOT / "src/test/resources/contract/results.json"

SPAN = "0123456789abcdef"
TRACE = "0123456789abcdef0123456789abcdef"


def check(outcome: str, **overrides: object) -> dict:
    value: dict[str, object] = {"id": "chk-1", "name": "检查", "outcome": outcome,
                                "reason": "原因"}
    if outcome != "not_applicable":
        value["score"] = 0.8
    if outcome == "fail":
        value["failure_stage"] = "tool_selection"
        value["failure_sequence"] = 1
    value.update(overrides)
    return value


def judge_record(**overrides: object) -> dict:
    value: dict[str, object] = {
        "provider_id": "openai", "requested_model": "gpt-4o",
        "request_sha256": "a" * 64, "raw_response": "raw",
    }
    value.update(overrides)
    return value


def error_detail(**overrides: object) -> dict:
    value: dict[str, object] = {"category": "timeout", "exception_type": "TimeoutError",
                                "message": "执行超时"}
    value.update(overrides)
    return value


def result(outcome: str, kind: str = "rule", **overrides: object) -> dict:
    value: dict[str, object] = {
        "id": "res-1", "run_id": "run-1", "case_id": "c1", "trace_id": TRACE,
        "evaluator_id": "state", "evaluator_name": "状态", "evaluator_version": "1",
        "evaluator_content_sha256": "b" * 64, "evaluator_kind": kind,
        "dimension": "state", "metric": "state_metric", "severity": "standard",
        "outcome": outcome, "reason": "原因",
    }
    if outcome not in ("not_applicable", "error"):
        value["score"] = 0.9
    if outcome == "error":
        value["error_detail"] = error_detail()
    if kind == "llm_judge" and outcome in ("pass", "fail", "review"):
        value["judge_record"] = judge_record()
    value.update(overrides)
    return value


LEGAL_SAMPLES: list[tuple[str, str, dict]] = [
    ("method_ref", "method_ref", {"implementation_id": "final_state",
                                  "implementation_version": "2"}),
    ("judge_record_minimal", "judge", judge_record()),
    ("judge_record_full", "judge", judge_record(
        resolved_model="gpt-4o-2024", request_id="req-9",
        input_tokens=120, output_tokens=30, latency_ms=1500.5,
        previous_attempts=[judge_record(request_id="req-8", latency_ms=900.0)])),
    ("error_detail", "error_detail", error_detail(retryable=True, reference="case-42")),
    ("check_pass", "check", check("pass", turn_id="t1", expectation_id="e1",
                                  expected="文本", actual="文本",
                                  methods=[{"implementation_id": "final_state",
                                            "implementation_version": "1"}])),
    ("check_fail", "check", check("fail", span_ids=[SPAN, "fedcba9876543210"],
                                  failure_span_id=SPAN,
                                  expected=1, actual=2, actual_missing=False)),
    ("check_review", "check", check("review", score=0.5)),
    ("check_not_applicable", "check", check("not_applicable", actual_missing=True)),
    ("result_pass", "result", result("pass", checks=[check("pass", score=0.9)])),
    ("result_pass_llm", "result", result("pass", kind="llm_judge",
                                         checks=[check("pass", score=0.9)])),
    ("result_fail", "result", result("fail", score=0.4,
                                     primary_failure_stage="tool_selection",
                                     checks=[check("pass", score=1.0),
                                             check("fail", id="chk-2", failure_sequence=2,
                                                   failure_stage="routing"),
                                             check("fail", id="chk-3", failure_sequence=1,
                                                   failure_stage="tool_selection")])),
    ("result_review", "result", result("review", score=0.6,
                                       checks=[check("review", score=0.6)])),
    ("result_not_applicable", "result", result("not_applicable",
                                               checks=[check("not_applicable")])),
    ("result_error", "result", result("error", checks=[])),
    ("gate_pass", "gate", {"outcome": "pass", "minimum_score": 0.95, "score": 0.97,
                           "reason_code": "threshold_met"}),
    ("gate_missing_results", "gate", {"outcome": "fail", "minimum_score": 0.95,
                                      "missing_results": [["c1", "state"], ["c2", "judge"]],
                                      "reason_code": "missing_results"}),
    ("gate_score_below", "gate", {"outcome": "fail", "minimum_score": 0.95, "score": 0.5,
                                  "reason_code": "score_below_threshold"}),
]

CLASSIFY_CASES: list[tuple[str, dict]] = [
    ("classify_missing", {"has_missing_results": True, "has_evaluator_errors": False,
                          "has_blocking_failures": False, "has_reviews": False,
                          "score": None, "minimum_score": 0.95}),
    ("classify_evaluator_error", {"has_missing_results": False, "has_evaluator_errors": True,
                                  "has_blocking_failures": True, "has_reviews": True,
                                  "score": 0.99, "minimum_score": 0.95}),
    ("classify_blocking", {"has_missing_results": False, "has_evaluator_errors": False,
                           "has_blocking_failures": True, "has_reviews": False,
                           "score": 0.99, "minimum_score": 0.95}),
    ("classify_review", {"has_missing_results": False, "has_evaluator_errors": False,
                         "has_blocking_failures": False, "has_reviews": True,
                         "score": 0.99, "minimum_score": 0.95}),
    ("classify_no_score", {"has_missing_results": False, "has_evaluator_errors": False,
                           "has_blocking_failures": False, "has_reviews": False,
                           "score": None, "minimum_score": 0.95}),
    ("classify_threshold_met", {"has_missing_results": False, "has_evaluator_errors": False,
                                "has_blocking_failures": False, "has_reviews": False,
                                "score": 0.95, "minimum_score": 0.95}),
    ("classify_below_threshold", {"has_missing_results": False, "has_evaluator_errors": False,
                                  "has_blocking_failures": False, "has_reviews": False,
                                  "score": 0.949, "minimum_score": 0.95}),
]

INVALID_SAMPLES: list[tuple[str, str, dict]] = [
    ("judge_blank_provider", "judge", judge_record(provider_id=" ")),
    ("judge_blank_resolved_model", "judge", judge_record(resolved_model=" ")),
    ("judge_bad_hash", "judge", judge_record(request_sha256="xyz")),
    ("judge_negative_tokens", "judge", judge_record(input_tokens=-1)),
    ("error_detail_bad_category", "error_detail", error_detail(category="fatal")),
    ("error_detail_blank_reference", "error_detail", error_detail(reference=" ")),
    ("check_error_outcome", "check", check("error")),
    ("check_fail_missing_stage", "check", check("fail", failure_stage=None)),
    ("check_fail_missing_sequence", "check", check("fail", failure_sequence=None)),
    ("check_fail_span_not_included", "check", check("fail", span_ids=["fedcba9876543210"],
                                                    failure_span_id=SPAN)),
    ("check_pass_with_failure_fields", "check", check("pass", failure_stage="routing",
                                                      failure_sequence=1)),
    ("check_na_with_score", "check", check("not_applicable", score=0.5)),
    ("check_pass_without_score", "check", check("pass", score=None)),
    ("check_bad_span_format", "check", check("pass", span_ids=["NOTHEX"])),
    ("check_duplicate_spans", "check", check("pass", span_ids=[SPAN, SPAN])),
    ("check_bad_failure_span", "check", check("fail", failure_span_id="NOTHEX",
                                              span_ids=[SPAN])),
    ("check_blank_turn_id", "check", check("pass", turn_id=" ")),
    ("result_bad_trace_id", "result", result("pass", trace_id="xyz")),
    ("result_dup_check_ids", "result", result("pass", checks=[check("pass"),
                                                              check("pass")])),
    ("result_na_with_score", "result", result("not_applicable", score=0.5)),
    ("result_measured_without_score", "result", result("pass", score=None,
                                                       checks=[check("pass", score=0.9)])),
    ("result_fail_without_failed_check", "result", result("fail", score=0.4,
                                                          checks=[check("pass")])),
    ("result_fail_wrong_primary_stage", "result", result("fail", score=0.4,
                                                         primary_failure_stage="routing",
                                                         checks=[check("fail")])),
    ("result_pass_with_failed_check", "result", result("pass", score=0.9,
                                                       checks=[check("fail")])),
    ("result_review_with_failed_check", "result", result("review", score=0.6,
                                                         checks=[check("fail")])),
    ("result_na_with_other_check", "result", result("not_applicable",
                                                    checks=[check("pass")])),
    ("result_error_without_detail", "result", result("error", error_detail=None)),
    ("result_pass_with_error_detail", "result", result("pass",
                                                       error_detail=error_detail(),
                                                       checks=[check("pass")])),
    ("result_rule_with_judge_record", "result", result("pass",
                                                       judge_record=judge_record(),
                                                       checks=[check("pass")])),
    ("result_llm_measured_without_judge", "result", result("pass", kind="llm_judge",
                                                           judge_record=None,
                                                           checks=[check("pass")])),
    ("gate_outcome_mismatch", "gate", {"outcome": "pass", "minimum_score": 0.95,
                                       "score": 0.5, "reason_code": "score_below_threshold"}),
    ("gate_missing_reason_without_refs", "gate", {"outcome": "fail", "minimum_score": 0.95,
                                                  "reason_code": "missing_results"}),
    ("gate_other_reason_with_refs", "gate", {"outcome": "fail", "minimum_score": 0.95,
                                             "score": 0.5,
                                             "missing_results": [["c1", "state"]],
                                             "reason_code": "score_below_threshold"}),
    ("gate_threshold_met_low_score", "gate", {"outcome": "pass", "minimum_score": 0.95,
                                              "score": 0.9, "reason_code": "threshold_met"}),
    ("gate_no_applicable_with_score", "gate", {"outcome": "fail", "minimum_score": 0.95,
                                               "score": 0.5,
                                               "reason_code": "no_applicable_results"}),
    ("gate_duplicate_missing", "gate", {"outcome": "fail", "minimum_score": 0.95,
                                        "missing_results": [["c1", "state"], ["c1", "state"]],
                                        "reason_code": "missing_results"}),
]


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--python-src", default=DEFAULT_PYTHON_SRC)
    parser.add_argument("--output", default=str(DEFAULT_OUTPUT))
    args = parser.parse_args()

    sys.path.insert(0, args.python_src)
    from agentgate.domain.base import canonical_json, content_sha256
    from agentgate.domain.evaluator import EvaluatorKind, EvaluatorSeverity
    from agentgate.domain.gate import ReleaseGateDecision, classify_release_gate
    from agentgate.domain.result import (
        CheckResult,
        EvaluationResult,
        EvaluatorErrorDetail,
        JudgeRecord,
        MethodRef,
        Outcome,
    )

    classes = {
        "method_ref": MethodRef,
        "judge": JudgeRecord,
        "error_detail": EvaluatorErrorDetail,
        "check": CheckResult,
        "result": EvaluationResult,
        "gate": ReleaseGateDecision,
    }

    samples = []
    for name, union, value in LEGAL_SAMPLES:
        model = classes[union].model_validate(value)
        samples.append({
            "name": name, "union": union, "input": value,
            "payload": model.model_dump(mode="json"),
            "canonical": canonical_json(model),
            "sha256": content_sha256(model),
        })

    classifications = []
    for name, kwargs in CLASSIFY_CASES:
        outcome, reason = classify_release_gate(**kwargs)
        classifications.append({"name": name, "input": kwargs,
                                "outcome": outcome.value, "reason": reason})

    invalid_samples = []
    for name, union, value in INVALID_SAMPLES:
        try:
            classes[union].model_validate(value)
        except ValidationError as exc:
            errors = exc.errors()
            assert errors, f"sample {name}: no validation errors"
            invalid_samples.append({
                "name": name, "union": union, "input": value,
                "error": errors[0]["msg"].removeprefix("Value error, "),
            })
        else:
            raise SystemExit(f"invalid sample {name} unexpectedly passed validation")

    document = {
        "description": (
            "Golden fixtures for the result domain (CheckResult/EvaluationResult/"
            "JudgeRecord/EvaluatorErrorDetail/MethodRef/ReleaseGateDecision) parity with "
            "Python agentgate. Generated by scripts/generate_results_golden.py."
        ),
        "python_version": platform.python_version(),
        "samples": samples,
        "classifications": classifications,
        "invalid_samples": invalid_samples,
    }

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    with open(output, "w", encoding="utf-8") as handle:
        json.dump(document, handle, ensure_ascii=False, indent=2)
        handle.write("\n")
    print(f"wrote {len(samples)} legal samples, {len(classifications)} classifications, "
          f"and {len(invalid_samples)} invalid samples to {output}")


if __name__ == "__main__":
    main()
