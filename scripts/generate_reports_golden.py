#!/usr/bin/env python3
"""Generate EvaluationReport golden fixtures for the Java domain contract tests.

Read-only against the Python agentgate implementation: builds the full chain
(published DatasetVersion + TargetSnapshot + EvaluatorSpecs + completed
EvaluationRun + EvaluationResults + MetricSummaries + ReleaseGateDecision).
The Python repo is never modified.

Usage:
    /path/to/pythonwork/agentgate/.venv/bin/python scripts/generate_reports_golden.py \
        [--python-src /path/to/agentgate/src] [--output path.json]
"""

from __future__ import annotations

import argparse
import json
import platform
import sys
from datetime import UTC, datetime
from pathlib import Path

from pydantic import ValidationError

REPO_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_PYTHON_SRC = "/Users/eric/hw/xql/pythonwork/agentgate/src"
DEFAULT_OUTPUT = REPO_ROOT / "src/test/resources/contract/reports.json"

T0 = datetime(2026, 1, 1, tzinfo=UTC)
T1 = datetime(2026, 1, 2, tzinfo=UTC)
T2 = datetime(2026, 1, 3, tzinfo=UTC)
TRACE = "0123456789abcdef0123456789abcdef"


def case_payload(case_id: str) -> dict:
    return {
        "id": case_id, "name": f"用例-{case_id}",
        "turns": [{"id": f"{case_id}-t1", "input": {"question": case_id}}],
    }


def spec(evaluator_id: str, **overrides: object) -> dict:
    value: dict[str, object] = {
        "id": evaluator_id, "name": evaluator_id, "dimension": "state",
        "metric": f"{evaluator_id}_metric", "implementation_id": "final_state",
    }
    value.update(overrides)
    return value


def manifest_input() -> dict:
    return {
        "dataset": {
            "id": "dv-1", "dataset_id": "ds-1", "version": 1, "status": "published",
            "cases": [case_payload("c1"), case_payload("c2")],
            "published_at": T0, "created_at": T0, "updated_at": T0,
        },
        "target": {
            "ref": {"source_id": "demo", "target_type": "agent",
                    "external_target_id": "loan", "external_version_id": "v1"},
            "display_name": "信贷助手", "adapter_type": "python_function",
            "adapter_version": "1", "descriptor_sha256": "a" * 64,
            "captured_at": T0,
        },
        "evaluator_specs": [spec("state"), spec("judge", dimension="quality",
                                               metric="judge_metric")],
        "primary_evaluator_ids": ["state"],
        "metric_plan": {},
        "gate_spec": {},
        "created_at": T0,
    }


def completed_run() -> dict:
    return {
        "id": "run-1", "manifest": manifest_input(), "status": "completed",
        "created_at": T0, "started_at": T1, "completed_at": T2,
    }


SPEC_VALUES: dict[str, dict] = {}


def result(case_id: str, evaluator_id: str, outcome: str, score: float,
           **overrides: object) -> dict:
    spec_value = SPEC_VALUES.get(evaluator_id)
    if spec_value is None:
        spec_value = {"name": evaluator_id, "content_sha256": "b" * 64,
                      "dimension": "state", "metric": "m"}
    value: dict[str, object] = {
        "id": f"res-{case_id}-{evaluator_id}", "run_id": "run-1", "case_id": case_id,
        "trace_id": TRACE, "evaluator_id": evaluator_id,
        "evaluator_name": spec_value["name"], "evaluator_version": "1",
        "evaluator_content_sha256": spec_value["content_sha256"],
        "evaluator_kind": "rule", "dimension": spec_value["dimension"],
        "metric": spec_value["metric"], "severity": "standard",
        "outcome": outcome, "score": score, "reason": "原因",
    }
    if outcome in ("pass", "review"):
        value["checks"] = [{
            "id": f"chk-{case_id}-{evaluator_id}", "name": "检查",
            "outcome": outcome, "score": score, "reason": "原因",
        }]
    elif outcome == "fail":
        value["primary_failure_stage"] = "tool_selection"
        value["checks"] = [{
            "id": f"chk-{case_id}-{evaluator_id}", "name": "检查",
            "outcome": "fail", "score": score, "reason": "原因",
            "failure_stage": "tool_selection", "failure_sequence": 1,
        }]
    value.update(overrides)
    return value


def overall_summary(passed: int, failed: int, score: float | None,
                    reviewed: int = 0, errors: int = 0) -> dict:
    return {
        "key": "overall", "level": "overall", "score": score,
        "passed": passed, "failed": failed, "reviewed": reviewed,
        "not_applicable": 0, "errors": errors,
        "applicable": passed + failed + reviewed, "total": passed + failed + reviewed + errors,
    }


def dimension_summary(key: str, passed: int, failed: int, score: float | None) -> dict:
    return {
        "key": key, "level": "dimension", "score": score,
        "passed": passed, "failed": failed, "reviewed": 0, "not_applicable": 0, "errors": 0,
        "applicable": passed + failed, "total": passed + failed,
    }


def report_input(results: list[dict], metrics: list[dict], gate: dict) -> dict:
    return {"run": completed_run(), "results": results, "metrics": metrics,
            "release_gate": gate}


def legal_samples() -> list[tuple[str, dict]]:
    return [
    ("report_all_pass", report_input(
        [result("c1", "state", "pass", 1.0), result("c2", "state", "pass", 0.9)],
        [overall_summary(2, 0, 0.95), dimension_summary("state", 2, 0, 0.95)],
        {"outcome": "pass", "minimum_score": 0.95, "score": 0.95,
         "reason_code": "threshold_met"})),
    ("report_with_fail", report_input(
        [result("c1", "state", "pass", 1.0), result("c2", "state", "fail", 0.0)],
        [overall_summary(1, 1, 0.5)],
        {"outcome": "fail", "minimum_score": 0.95, "score": 0.5,
         "reason_code": "score_below_threshold"})),
    ("report_missing_result", report_input(
        [result("c1", "state", "pass", 1.0)],
        [overall_summary(1, 0, 1.0)],
        {"outcome": "fail", "minimum_score": 0.95, "score": 1.0,
         "missing_results": [["c2", "state"]], "reason_code": "missing_results"})),
    ("report_empty_results", report_input(
        [],
        [overall_summary(0, 0, None)],
        {"outcome": "fail", "minimum_score": 0.95,
         "missing_results": [["c1", "state"], ["c2", "state"]],
         "reason_code": "missing_results"})),
    ("report_secondary_evaluator_error", report_input(
        [result("c1", "state", "pass", 1.0), result("c2", "state", "pass", 0.9),
         result("c1", "judge", "error", None,
                error_detail={"category": "timeout", "exception_type": "TimeoutError",
                              "message": "超时"})],
        [overall_summary(2, 0, 0.95)],
        {"outcome": "pass", "minimum_score": 0.95, "score": 0.95,
         "reason_code": "threshold_met"})),
]

def invalid_samples() -> list[tuple[str, dict]]:
    return [
    ("report_not_completed", report_input(
        [], [overall_summary(0, 0, None)],
        {"outcome": "fail", "minimum_score": 0.95, "reason_code": "no_applicable_results"})
    | {"run": {"id": "run-1", "manifest": manifest_input(), "created_at": T0}}),
    ("report_foreign_run_id", report_input(
        [result("c1", "state", "pass", 1.0, run_id="run-other")],
        [overall_summary(1, 0, 1.0)],
        {"outcome": "pass", "minimum_score": 0.95, "score": 1.0,
         "reason_code": "threshold_met"})),
    ("report_unknown_case", report_input(
        [result("c9", "state", "pass", 1.0)],
        [overall_summary(1, 0, 1.0)],
        {"outcome": "pass", "minimum_score": 0.95, "score": 1.0,
         "reason_code": "threshold_met"})),
    ("report_unknown_evaluator", report_input(
        [result("c1", "unknown", "pass", 1.0)],
        [overall_summary(1, 0, 1.0)],
        {"outcome": "pass", "minimum_score": 0.95, "score": 1.0,
         "reason_code": "threshold_met"})),
    ("report_mismatched_evaluator", report_input(
        [result("c1", "state", "pass", 1.0, evaluator_name="改名")],
        [overall_summary(1, 0, 1.0)],
        {"outcome": "pass", "minimum_score": 0.95, "score": 1.0,
         "reason_code": "threshold_met"})),
    ("report_dup_result_keys", report_input(
        [result("c1", "state", "pass", 1.0), result("c1", "state", "pass", 0.8)],
        [overall_summary(2, 0, 0.9)],
        {"outcome": "fail", "minimum_score": 0.95, "score": 0.9,
         "reason_code": "score_below_threshold"})),
    ("report_dup_metrics", report_input(
        [result("c1", "state", "pass", 1.0), result("c2", "state", "pass", 1.0)],
        [overall_summary(2, 0, 1.0), overall_summary(2, 0, 1.0)],
        {"outcome": "pass", "minimum_score": 0.95, "score": 1.0,
         "reason_code": "threshold_met"})),
    ("report_no_overall", report_input(
        [result("c1", "state", "pass", 1.0), result("c2", "state", "pass", 1.0)],
        [dimension_summary("state", 2, 0, 1.0)],
        {"outcome": "pass", "minimum_score": 0.95, "score": 1.0,
         "reason_code": "threshold_met"})),
    ("report_overall_counts_mismatch", report_input(
        [result("c1", "state", "pass", 1.0), result("c2", "state", "pass", 1.0)],
        [overall_summary(1, 0, 1.0)],
        {"outcome": "pass", "minimum_score": 0.95, "score": 1.0,
         "reason_code": "threshold_met"})),
    ("report_gate_score_mismatch", report_input(
        [result("c1", "state", "pass", 1.0), result("c2", "state", "pass", 1.0)],
        [overall_summary(2, 0, 1.0)],
        {"outcome": "pass", "minimum_score": 0.95, "score": 0.99,
         "reason_code": "threshold_met"})),
    ("report_gate_min_mismatch", report_input(
        [result("c1", "state", "pass", 1.0), result("c2", "state", "pass", 1.0)],
        [overall_summary(2, 0, 1.0)],
        {"outcome": "pass", "minimum_score": 0.90, "score": 1.0,
         "reason_code": "threshold_met"})),
    ("report_gate_missing_mismatch", report_input(
        [result("c1", "state", "pass", 1.0)],
        [overall_summary(1, 0, 1.0)],
        {"outcome": "fail", "minimum_score": 0.95, "score": 1.0,
         "missing_results": [["c2", "state"], ["c1", "state"]], "reason_code": "missing_results"})),
    ("report_gate_decision_mismatch", report_input(
        [result("c1", "state", "pass", 1.0), result("c2", "state", "pass", 0.5)],
        [overall_summary(2, 0, 0.75)],
        {"outcome": "fail", "minimum_score": 0.95, "score": 0.75,
         "reason_code": "review_required"})),
    ("report_empty_metrics", report_input(
        [], [],
        {"outcome": "fail", "minimum_score": 0.95,
         "missing_results": [["c1", "state"], ["c2", "state"]],
         "reason_code": "missing_results"})),
]


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--python-src", default=DEFAULT_PYTHON_SRC)
    parser.add_argument("--output", default=str(DEFAULT_OUTPUT))
    args = parser.parse_args()

    sys.path.insert(0, args.python_src)
    from agentgate.domain.base import canonical_json, content_sha256
    from agentgate.domain.evaluator import EvaluatorSpec
    from agentgate.domain.report import EvaluationReport

    for evaluator_id, spec_kwargs in (
        ("state", {}),
        ("judge", {"dimension": "quality", "metric": "judge_metric"}),
    ):
        built = EvaluatorSpec.model_validate(spec(evaluator_id, **spec_kwargs))
        SPEC_VALUES[evaluator_id] = {
            "name": built.name, "content_sha256": built.content_sha256,
            "dimension": built.dimension, "metric": built.metric,
        }

    samples = []
    for name, value in legal_samples():
        model = EvaluationReport.model_validate(value)
        samples.append({
            "name": name, "input": value,
            "payload": model.model_dump(mode="json"),
            "canonical": canonical_json(model),
            "sha256": content_sha256(model),
        })

    invalid_records = []
    for name, value in invalid_samples():
        try:
            EvaluationReport.model_validate(value)
        except ValidationError as exc:
            errors = exc.errors()
            assert errors, f"sample {name}: no validation errors"
            invalid_records.append({
                "name": name, "input": value,
                "error": errors[0]["msg"].removeprefix("Value error, "),
            })
        else:
            raise SystemExit(f"invalid sample {name} unexpectedly passed validation")

    document = {
        "description": (
            "Golden fixtures for EvaluationReport parity with Python "
            "agentgate.domain.report. Generated by scripts/generate_reports_golden.py."
        ),
        "python_version": platform.python_version(),
        "samples": samples,
        "invalid_samples": invalid_records,
    }

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)

    def json_default(value: object) -> str:
        if isinstance(value, datetime):
            return value.isoformat()
        raise TypeError(f"not JSON serializable: {type(value).__name__}")

    with open(output, "w", encoding="utf-8") as handle:
        json.dump(document, handle, ensure_ascii=False, indent=2, default=json_default)
        handle.write("\n")
    print(f"wrote {len(samples)} legal samples and {len(invalid_records)} invalid samples "
          f"to {output}")


if __name__ == "__main__":
    main()
