#!/usr/bin/env python3
"""Generate Run state-machine golden fixtures for the Java domain contract tests.

Read-only against the Python agentgate implementation: constructs real
EvaluationRun objects (mirroring tests/test_run_models.py factories) and records
transition_run outcomes plus lifecycle validation errors. Only lifecycle fields
are projected; the manifest passes through untouched. The Python repo is never
modified.

Usage:
    /path/to/pythonwork/agentgate/.venv/bin/python scripts/generate_run_transitions_golden.py \
        [--python-src /path/to/agentgate/src] [--output path.json]
"""

from __future__ import annotations

import argparse
import json
import platform
import sys
from datetime import UTC, datetime, timedelta
from pathlib import Path

from pydantic import ValidationError

REPO_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_PYTHON_SRC = "/Users/eric/hw/xql/pythonwork/agentgate/src"
DEFAULT_OUTPUT = REPO_ROOT / "src/test/resources/contract/run-transitions.json"

T0 = datetime(2026, 1, 1, 0, 0, 0, 0, tzinfo=UTC)
SCHEDULED_FOR = T0 + timedelta(hours=1)
STARTED = T0 + timedelta(hours=1)
COMPLETED = T0 + timedelta(hours=2)
OCCURRED = T0 + timedelta(hours=3)
EARLY = T0 + timedelta(minutes=30)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--python-src", default=DEFAULT_PYTHON_SRC)
    parser.add_argument("--output", default=str(DEFAULT_OUTPUT))
    args = parser.parse_args()

    sys.path.insert(0, args.python_src)
    from agentgate.demo.loan import LOAN_DATASET_VERSION
    from agentgate.domain import (
        EvaluationRun,
        EvaluatorSpec,
        MetricPlan,
        ReleaseGateSpec,
        RunManifest,
        RunStatus,
        TargetRef,
        TargetSnapshot,
        TargetType,
        transition_run,
    )

    def evaluator(evaluator_id: str = "state") -> EvaluatorSpec:
        return EvaluatorSpec(
            id=evaluator_id,
            name=evaluator_id,
            dimension="state",
            metric=f"{evaluator_id}_metric",
            implementation_id="final_state",
        )

    def manifest(**overrides: object) -> RunManifest:
        values: dict[str, object] = {
            "dataset": LOAN_DATASET_VERSION,
            "target": TargetSnapshot(
                ref=TargetRef(
                    source_id="demo",
                    target_type=TargetType.AGENT,
                    external_target_id="loan",
                    external_version_id="v1",
                ),
                display_name="loan",
                adapter_type="python_function",
                adapter_version="1",
                descriptor_sha256="a" * 64,
            ),
            "evaluator_specs": (evaluator(),),
            "primary_evaluator_ids": ("state",),
            "metric_plan": MetricPlan(),
            "gate_spec": ReleaseGateSpec(),
            "created_at": datetime(2026, 9, 5, tzinfo=UTC),
        }
        values.update(overrides)
        return RunManifest(**values)

    def build_run(status: RunStatus, **overrides: object) -> EvaluationRun:
        values: dict[str, object] = {
            "manifest": manifest(),
            "status": status,
            "created_at": T0,
        }
        if status is RunStatus.SCHEDULED:
            values["scheduled_for"] = SCHEDULED_FOR
        elif status is RunStatus.RUNNING:
            values["started_at"] = STARTED
        elif status in (RunStatus.COMPLETED, RunStatus.CANCELLED):
            values["started_at"] = STARTED
            values["completed_at"] = COMPLETED
        elif status is RunStatus.FAILED:
            values["started_at"] = STARTED
            values["completed_at"] = COMPLETED
            values["error"] = "boom"
        values.update(overrides)
        return EvaluationRun(**values)

    def project(run: EvaluationRun) -> dict[str, object]:
        return {
            "status": run.status.value,
            "created_at": run.created_at.isoformat(),
            "scheduled_for": run.scheduled_for.isoformat() if run.scheduled_for else None,
            "started_at": run.started_at.isoformat() if run.started_at else None,
            "completed_at": run.completed_at.isoformat() if run.completed_at else None,
            "error": run.error,
        }

    def message_of(exc: ValueError) -> str:
        if isinstance(exc, ValidationError):
            errors = exc.errors()
            assert len(errors) == 1, f"expected a single validation error, got {errors}"
            return errors[0]["msg"].removeprefix("Value error, ")
        return str(exc)

    baselines: dict[str, EvaluationRun] = {
        status.value: build_run(status) for status in RunStatus
    }

    matrix = []
    for from_name, run in baselines.items():
        for target in RunStatus:
            error_input = "boom" if target is RunStatus.FAILED else None
            try:
                result = transition_run(run, target, OCCURRED, error_input)
                matrix.append({
                    "from": from_name,
                    "to": target.value,
                    "legal": True,
                    "result": project(result),
                })
            except ValueError as exc:
                matrix.append({
                    "from": from_name,
                    "to": target.value,
                    "legal": False,
                    "error": message_of(exc),
                })

    variants = []

    def record_variant(name: str, from_status: RunStatus, target: RunStatus,
                       occurred_at: object, error_input: object, run: object = None) -> None:
        source = run if run is not None else baselines[from_status.value]
        try:
            result = transition_run(source, target, occurred_at, error_input)
            variants.append({
                "name": name,
                "from": from_status.value,
                "to": target.value,
                "occurred_at": occurred_at.isoformat() if occurred_at else None,
                "error_input": error_input,
                "legal": True,
                "result": project(result),
            })
        except ValueError as exc:
            variants.append({
                "name": name,
                "from": from_status.value,
                "to": target.value,
                "occurred_at": occurred_at.isoformat() if occurred_at else None,
                "error_input": error_input,
                "legal": False,
                "error": message_of(exc),
            })

    record_variant("release_before_scheduled_for", RunStatus.SCHEDULED, RunStatus.PENDING, EARLY, None)
    record_variant("release_exactly_at_scheduled_for", RunStatus.SCHEDULED, RunStatus.PENDING, SCHEDULED_FOR, None)
    record_variant("pending_to_running_with_error", RunStatus.PENDING, RunStatus.RUNNING, OCCURRED, "x")
    record_variant("failed_without_error", RunStatus.PENDING, RunStatus.FAILED, OCCURRED, None)
    record_variant("failed_with_ascii_blank_error", RunStatus.PENDING, RunStatus.FAILED, OCCURRED, "   ")
    record_variant("failed_with_nbsp_blank_error", RunStatus.PENDING, RunStatus.FAILED, OCCURRED, " ")
    record_variant("completed_with_error", RunStatus.RUNNING, RunStatus.COMPLETED, OCCURRED, "x")
    record_variant("cancelled_with_error", RunStatus.RUNNING, RunStatus.CANCELLED, OCCURRED, "x")
    record_variant("occurred_before_started_activity", RunStatus.RUNNING, RunStatus.COMPLETED, EARLY, None)
    record_variant("occurred_equals_started_activity", RunStatus.RUNNING, RunStatus.COMPLETED, STARTED, None)
    record_variant("waiting_keeps_timestamps", RunStatus.PENDING, RunStatus.WAITING, OCCURRED, None)
    record_variant("waiting_back_to_pending", RunStatus.WAITING, RunStatus.PENDING, OCCURRED, None)
    record_variant("scheduled_to_cancelled", RunStatus.SCHEDULED, RunStatus.CANCELLED, OCCURRED, None)
    record_variant("pending_failed_with_error", RunStatus.PENDING, RunStatus.FAILED, OCCURRED, "dispatch failed")
    record_variant("waiting_to_failed", RunStatus.WAITING, RunStatus.FAILED, OCCURRED, "timeout")

    lifecycle_validations = []

    def record_validation(name: str, **state: object) -> None:
        values: dict[str, object] = {"manifest": manifest(), "created_at": T0}
        values.update(state)
        try:
            run = EvaluationRun(**values)
            lifecycle_validations.append({"name": name, "state": project(run), "error": None})
        except ValueError as exc:
            lifecycle_validations.append({
                "name": name,
                "state": {
                    "status": values["status"].value if isinstance(values["status"], RunStatus)
                    else values.get("status", "pending"),
                    "created_at": T0.isoformat(),
                    "scheduled_for": values["scheduled_for"].isoformat()
                    if values.get("scheduled_for") else None,
                    "started_at": values["started_at"].isoformat()
                    if values.get("started_at") else None,
                    "completed_at": values["completed_at"].isoformat()
                    if values.get("completed_at") else None,
                    "error": values.get("error"),
                },
                "error": message_of(exc),
            })

    record_validation("scheduled_without_scheduled_for", status="scheduled")
    record_validation("scheduled_with_started_at", status="scheduled",
                      scheduled_for=SCHEDULED_FOR, started_at=STARTED)
    record_validation("pending_with_completed_at", status="pending", completed_at=COMPLETED)
    record_validation("waiting_with_error", status="waiting", error="x")
    record_validation("running_without_started_at", status="running")
    record_validation("running_with_completed_at", status="running",
                      started_at=STARTED, completed_at=COMPLETED)
    record_validation("running_with_error", status="running", started_at=STARTED, error="x")
    record_validation("completed_without_started_at", status="completed", completed_at=COMPLETED)
    record_validation("completed_with_error", status="completed",
                      started_at=STARTED, completed_at=COMPLETED, error="x")
    record_validation("failed_without_completed_at", status="failed", started_at=STARTED, error="x")
    record_validation("failed_without_error", status="failed",
                      started_at=STARTED, completed_at=COMPLETED)
    record_validation("cancelled_with_error", status="cancelled",
                      started_at=STARTED, completed_at=COMPLETED, error="x")
    record_validation("cancelled_without_completed_at", status="cancelled", started_at=STARTED)
    record_validation("scheduled_for_equals_created_at", status="scheduled", scheduled_for=T0)
    record_validation("scheduled_for_before_created_at", status="scheduled",
                      scheduled_for=T0 - timedelta(seconds=1))
    record_validation("started_before_created_at", status="running", started_at=T0 - timedelta(seconds=1))
    record_validation("completed_before_activity", status="completed",
                      started_at=STARTED, completed_at=T0)
    record_validation("blank_error", status="pending", error="   ")

    for entry in lifecycle_validations:
        assert entry["error"] is not None, f"validation scenario {entry['name']} unexpectedly succeeded"

    document = {
        "description": (
            "Golden fixtures for the Run state machine (RunStatus/RunLifecycle/RunTransitions) "
            "parity with Python agentgate.domain.run. Generated by "
            "scripts/generate_run_transitions_golden.py from real EvaluationRun/transition_run calls."
        ),
        "python_version": platform.python_version(),
        "baseline_states": [project(run) for run in baselines.values()],
        "transition_matrix": matrix,
        "variants": variants,
        "lifecycle_validations": lifecycle_validations,
    }

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    with open(output, "w", encoding="utf-8") as handle:
        json.dump(document, handle, ensure_ascii=False, indent=2)
        handle.write("\n")
    legal = sum(1 for item in matrix if item["legal"])
    print(f"wrote {len(matrix)} matrix entries ({legal} legal), "
          f"{len(variants)} variants, {len(lifecycle_validations)} validation cases to {output}")


if __name__ == "__main__":
    main()
