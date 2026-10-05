#!/usr/bin/env python3
"""Generate Trace/Artifact/EvaluationTask golden fixtures for the Java contract tests.

Read-only against the Python agentgate implementation. The Python repo is never
modified.

Usage:
    /path/to/pythonwork/agentgate/.venv/bin/python scripts/generate_trace_artifacts_tasks_golden.py \
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
DEFAULT_OUTPUT = REPO_ROOT / "src/test/resources/contract/trace-artifacts-tasks.json"

T0 = datetime(2026, 1, 1, tzinfo=UTC)
T1 = datetime(2026, 1, 2, tzinfo=UTC)
TRACE = "0123456789abcdef0123456789abcdef"
SPAN_ROOT = "0123456789abcdef"
SPAN_CHILD = "fedcba9876543210"
SPAN_GRAND = "abcdef0123456789"


def span(span_id: str, sequence: int, **overrides: object) -> dict:
    value: dict[str, object] = {
        "trace_id": TRACE, "span_id": span_id, "name": f"操作-{span_id}",
        "operation_type": "turn" if sequence == 0 else "tool",
        "sequence": sequence, "started_at": T0, "ended_at": T1,
    }
    value.update(overrides)
    return value


def trace(**overrides: object) -> dict:
    value: dict[str, object] = {
        "trace_id": TRACE, "run_id": "run-1", "case_id": "c1",
        "spans": [span(SPAN_ROOT, 0, attributes={"agentgate.turn.id": "t1"})],
    }
    value.update(overrides)
    return value


def artifact(**overrides: object) -> dict:
    value: dict[str, object] = {
        "id": "art-1", "run_id": "run-1", "case_id": "c1", "producer": "tool",
        "artifact_type": "screenshot", "filename": "shot.png",
        "media_type": "image/png", "storage_uri": "file:///tmp/shot.png",
        "sha256": "a" * 64, "size_bytes": 1024, "created_at": T0,
    }
    value.update(overrides)
    return value


def task(kind: str, run_ids: list[str], **overrides: object) -> dict:
    value: dict[str, object] = {"id": "task-1", "kind": kind, "created_at": T0,
                                "run_ids": run_ids}
    value.update(overrides)
    return value


LEGAL_SAMPLES: list[tuple[str, str, dict]] = [
    ("span_minimal", "span", span(SPAN_ROOT, 0)),
    ("span_full", "span", span(SPAN_CHILD, 1, parent_span_id=SPAN_ROOT, status="ok",
                               attributes={"tool.name": "search", "result.count": 3},
                               events=[{"name": "tool.call", "ts": "2026-01-01T00:00:00Z"}])),
    ("trace_minimal", "trace", trace()),
    ("trace_multi_spans", "trace", trace(spans=[
        span(SPAN_ROOT, 0, operation_type="turn", attributes={"agentgate.turn.id": "t1"}),
        span(SPAN_CHILD, 1, parent_span_id=SPAN_ROOT, status="ok"),
        span(SPAN_GRAND, 2, parent_span_id=SPAN_CHILD, status="error",
             events=[{"name": "exception"}]),
    ])),
    ("trace_with_outcomes", "trace", trace(
        turn_outcomes={"t1": {"output": {"answer": "答复"}, "state": {"balance": 100}}},
        final_output={"answer": "答复"}, final_state={"balance": 100})),
    ("artifact_minimal", "artifact", artifact()),
    ("artifact_full", "artifact", artifact(producer="agent", producer_name="信贷助手",
                                           trace_id=TRACE, metadata={"size": "large"})),
    ("task_single", "task", task("single", ["run-1"])),
    ("task_ab", "task", task("ab", ["run-1", "run-2"],
                             static_report_ids=["r1", "r2"],
                             git_commit_refs=["a" * 40, "b" * 40])),
    ("task_stability", "task", task("stability", ["run-1", "run-2", "run-3"],
                                    static_report_ids=["r1"], credential_id="cred-1")),
]

INVALID_SAMPLES: list[tuple[str, str, dict]] = [
    ("span_bad_trace_id", "span", span(SPAN_ROOT, 0, trace_id="XYZ")),
    ("span_bad_span_id", "span", span("NOTHEX", 0)),
    ("span_bad_parent", "span", span(SPAN_ROOT, 0, parent_span_id="NOTHEX")),
    ("span_blank_name", "span", span(SPAN_ROOT, 0, name=" ")),
    ("span_negative_sequence", "span", span(SPAN_ROOT, -1)),
    ("span_ended_before_started", "span", span(SPAN_ROOT, 0,
                                               started_at=T1, ended_at=T0)),
    ("trace_mismatched_span_trace_id", "trace", trace(spans=[
        span(SPAN_ROOT, 0, trace_id="ffffffffffffffffffffffffffffffff")])),
    ("trace_dup_span_ids", "trace", trace(spans=[
        span(SPAN_ROOT, 0), span(SPAN_ROOT, 1)])),
    ("trace_dup_sequences", "trace", trace(spans=[
        span(SPAN_ROOT, 0), span(SPAN_CHILD, 0)])),
    ("trace_blank_run_id", "trace", trace(run_id=" ")),
    ("artifact_blank_filename", "artifact", artifact(filename=" ")),
    ("artifact_bad_sha256", "artifact", artifact(sha256="xyz")),
    ("artifact_empty_trace_id", "artifact", artifact(trace_id="")),
    ("artifact_empty_producer_name", "artifact", artifact(producer_name="")),
    ("artifact_negative_size", "artifact", artifact(size_bytes=-1)),
    ("artifact_bad_producer", "artifact", artifact(producer="user")),
    ("task_blank_id", "task", task("single", ["run-1"], id=" ")),
    ("task_blank_run_ref", "task", task("single", ["run-1", " "])),
    ("task_dup_run_refs", "task", task("ab", ["run-1", "run-1"])),
    ("task_bad_git", "task", task("single", ["run-1"], git_commit_refs=["abc123"])),
    ("task_single_two_runs", "task", task("single", ["run-1", "run-2"])),
    ("task_ab_one_run", "task", task("ab", ["run-1"])),
    ("task_stability_one_run", "task", task("stability", ["run-1"])),
    ("task_stability_git", "task", task("stability", ["run-1", "run-2"],
                                        git_commit_refs=["a" * 40])),
    ("task_too_many_static", "task", task("single", ["run-1"],
                                          static_report_ids=["r1", "r2"])),
    ("task_git_count_mismatch", "task", task("single", ["run-1"],
                                             git_commit_refs=["a" * 40, "b" * 64])),
    ("task_bad_kind", "task", task("weird", ["run-1"])),
]


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--python-src", default=DEFAULT_PYTHON_SRC)
    parser.add_argument("--output", default=str(DEFAULT_OUTPUT))
    args = parser.parse_args()

    sys.path.insert(0, args.python_src)
    from agentgate.domain.artifact import Artifact
    from agentgate.domain.base import canonical_json, content_sha256
    from agentgate.domain.evaluation_task import EvaluationTask
    from agentgate.domain.trace import Trace, TraceSpan

    classes = {"span": TraceSpan, "trace": Trace,
               "artifact": Artifact, "task": EvaluationTask}

    samples = []
    for name, union, value in LEGAL_SAMPLES:
        model = classes[union].model_validate(value)
        samples.append({
            "name": name, "union": union, "input": value,
            "payload": model.model_dump(mode="json"),
            "canonical": canonical_json(model),
            "sha256": content_sha256(model),
        })

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
            "Golden fixtures for Trace/TraceSpan/Artifact/EvaluationTask parity with "
            "Python agentgate. Generated by scripts/generate_trace_artifacts_tasks_golden.py."
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
