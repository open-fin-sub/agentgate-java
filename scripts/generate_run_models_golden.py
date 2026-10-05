#!/usr/bin/env python3
"""Generate RunManifest/EvaluationRun golden fixtures for the Java domain contract tests.

Read-only against the Python agentgate implementation: builds a published
DatasetVersion, TargetSnapshot, EvaluatorSpec, MetricPlan and ReleaseGateSpec,
then records full manifest/run payloads, canonical JSON, SHA-256 digests,
transition results and validation error messages. The Python repo is never
modified.

Usage:
    /path/to/pythonwork/agentgate/.venv/bin/python scripts/generate_run_models_golden.py \
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
DEFAULT_OUTPUT = REPO_ROOT / "src/test/resources/contract/run-models.json"

T0 = datetime(2026, 1, 1, tzinfo=UTC)
T1 = datetime(2026, 1, 2, tzinfo=UTC)
T2 = datetime(2026, 1, 3, tzinfo=UTC)


def case_payload(case_id: str) -> dict:
    return {
        "id": case_id,
        "name": f"用例-{case_id}",
        "turns": [{"id": f"{case_id}-t1", "input": {"question": case_id}}],
    }


def published_dataset(cases: list[dict]) -> dict:
    return {
        "id": "dv-1", "dataset_id": "ds-1", "version": 1, "status": "published",
        "cases": cases, "published_at": T0, "created_at": T0, "updated_at": T0,
    }


def target_snapshot() -> dict:
    return {
        "ref": {
            "source_id": "demo", "target_type": "agent",
            "external_target_id": "loan", "external_version_id": "v1",
        },
        "display_name": "信贷助手",
        "adapter_type": "python_function",
        "adapter_version": "1",
        "descriptor_sha256": "a" * 64,
        "captured_at": T0,
    }


def evaluator_spec(evaluator_id: str = "state") -> dict:
    return {
        "id": evaluator_id, "name": evaluator_id, "dimension": "state",
        "metric": f"{evaluator_id}_metric", "implementation_id": "final_state",
    }


def manifest_input(**overrides: object) -> dict:
    values: dict[str, object] = {
        "dataset": published_dataset([case_payload("c1"), case_payload("c2")]),
        "target": target_snapshot(),
        "evaluator_specs": [evaluator_spec()],
        "primary_evaluator_ids": ["state"],
        "metric_plan": {},
        "gate_spec": {},
        "created_at": T0,
    }
    values.update(overrides)
    return values


def run_input(manifest: dict, **overrides: object) -> dict:
    values: dict[str, object] = {"id": "run-1", "manifest": manifest, "created_at": T0}
    values.update(overrides)
    return values


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--python-src", default=DEFAULT_PYTHON_SRC)
    parser.add_argument("--output", default=str(DEFAULT_OUTPUT))
    args = parser.parse_args()

    sys.path.insert(0, args.python_src)
    from agentgate.domain.base import canonical_json, content_sha256
    from agentgate.domain.run import EvaluationRun, RunManifest, RunStatus, transition_run

    base_manifest = manifest_input()
    probe = RunManifest.model_validate(dict(base_manifest))
    explicit_hash_manifest = dict(base_manifest)
    explicit_hash_manifest["manifest_sha256"] = probe.manifest_sha256
    later_created = dict(base_manifest)
    later_created["created_at"] = T2

    legal_manifests: list[tuple[str, dict]] = [
        ("manifest_minimal", base_manifest),
        ("manifest_selected_cases", manifest_input(selected_case_ids=["c2", "c1"])),
        ("manifest_explicit_params", manifest_input(
            timeout_seconds=120.5, max_retries=2, max_parallel_cases=4)),
        ("manifest_hash_time_independent", later_created),
        ("manifest_explicit_correct_hash", explicit_hash_manifest),
    ]

    legal_runs: list[tuple[str, dict, list[tuple[str, object]]]] = [
        ("run_pending_minimal", run_input(base_manifest), []),
        ("run_scheduled", run_input(base_manifest, status=RunStatus.SCHEDULED,
                                    scheduled_for=T1), []),
        ("run_full", run_input(base_manifest, user_team_id="t", user_id="u",
                               user_name="张三", api_key="sk-1", dispatch_attempts=2), []),
    ]
    # transition 派生样本
    transitions: list[tuple[str, list[tuple[str, object]]]] = [
        ("run_after_running", [(RunStatus.RUNNING, T1, None)]),
        ("run_after_completed", [(RunStatus.RUNNING, T1, None), (RunStatus.COMPLETED, T2, None)]),
        ("run_failed_direct", [(RunStatus.FAILED, T1, "dispatch failed")]),
    ]
    for name, steps in transitions:
        legal_runs.append((name, run_input(base_manifest), steps))

    samples = []
    for name, value in legal_manifests:
        model = RunManifest.model_validate(value)
        samples.append({
            "name": name, "union": "manifest", "input": value,
            "payload": model.model_dump(mode="json"),
            "canonical": canonical_json(model),
            "sha256": content_sha256(model),
        })

    for name, value, steps in legal_runs:
        if steps:
            model = EvaluationRun.model_validate(value)
            for new_status, occurred_at, error in steps:
                model = transition_run(model, new_status, occurred_at, error)
            input_used = value
        else:
            model = EvaluationRun.model_validate(value)
            input_used = value
        samples.append({
            "name": name, "union": "run", "input": input_used,
            "transitions": [
                {"to": status.value,
                 "occurred_at": occurred_at.isoformat() if occurred_at else None,
                 "error": error}
                for status, occurred_at, error in steps
            ],
            "payload": model.model_dump(mode="json"),
            "canonical": canonical_json(model),
            "sha256": content_sha256(model),
        })

    invalid: list[tuple[str, str, dict]] = [
        ("manifest_draft_dataset", "manifest",
         manifest_input(dataset=dict(published_dataset([case_payload("c1")]),
                                     status="draft", version=None, published_at=None))),
        ("manifest_selected_empty", "manifest", manifest_input(selected_case_ids=[])),
        ("manifest_selected_blank", "manifest", manifest_input(selected_case_ids=["c1", " "])),
        ("manifest_selected_duplicates", "manifest",
         manifest_input(selected_case_ids=["c1", "c1"])),
        ("manifest_selected_unknown", "manifest",
         manifest_input(selected_case_ids=["c9", "c8"])),
        ("manifest_duplicate_evaluators", "manifest",
         manifest_input(evaluator_specs=[evaluator_spec(), evaluator_spec()],
                        primary_evaluator_ids=["state"])),
        ("manifest_unknown_primary", "manifest",
         manifest_input(primary_evaluator_ids=["missing"])),
        ("manifest_primary_blank", "manifest", manifest_input(primary_evaluator_ids=["state", " "])),
        ("manifest_primary_duplicates", "manifest",
         manifest_input(primary_evaluator_ids=["state", "state"])),
        ("manifest_empty_specs", "manifest", manifest_input(evaluator_specs=[])),
        ("manifest_empty_primary", "manifest", manifest_input(primary_evaluator_ids=[])),
        ("manifest_timeout_zero", "manifest", manifest_input(timeout_seconds=0)),
        ("manifest_negative_retries", "manifest", manifest_input(max_retries=-1)),
        ("manifest_zero_parallel", "manifest", manifest_input(max_parallel_cases=0)),
        ("manifest_hash_bad_format", "manifest", manifest_input(manifest_sha256="xyz")),
        ("manifest_hash_mismatch", "manifest",
         manifest_input(manifest_sha256="0" * 64)),
        ("run_blank_id", "run", run_input(base_manifest, id=" ")),
        ("run_negative_dispatch", "run", run_input(base_manifest, dispatch_attempts=-1)),
        ("run_running_without_started", "run",
         run_input(base_manifest, status=RunStatus.RUNNING)),
    ]

    invalid_samples = []
    for name, union, value in invalid:
        model_cls = RunManifest if union == "manifest" else EvaluationRun
        try:
            model_cls.model_validate(value)
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
            "Golden fixtures for RunManifest/EvaluationRun parity with Python "
            "agentgate.domain.run. Generated by scripts/generate_run_models_golden.py."
        ),
        "python_version": platform.python_version(),
        "samples": samples,
        "invalid_samples": invalid_samples,
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
    print(f"wrote {len(samples)} legal samples and {len(invalid_samples)} invalid samples "
          f"to {output}")


if __name__ == "__main__":
    main()
