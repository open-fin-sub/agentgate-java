#!/usr/bin/env python3
"""Generate TargetDescriptor/MetricSummary golden fixtures for the Java contract tests.

Read-only against the Python agentgate implementation. The Python repo is never
modified.

Usage:
    /path/to/pythonwork/agentgate/.venv/bin/python scripts/generate_target_descriptors_golden.py \
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
DEFAULT_OUTPUT = REPO_ROOT / "src/test/resources/contract/target-descriptors.json"

T0 = datetime(2026, 1, 1, tzinfo=UTC)
T1 = datetime(2026, 1, 2, tzinfo=UTC)


def tool(name: str = "search", **overrides: object) -> dict:
    value: dict[str, object] = {"name": name}
    value.update(overrides)
    return value


def skill(skill_id: str = "skill-1", version: str = "v1", **overrides: object) -> dict:
    value: dict[str, object] = {
        "external_skill_id": skill_id, "external_version_id": version,
        "name": f"技能-{skill_id}",
    }
    value.update(overrides)
    return value


def agent_ref() -> dict:
    return {"source_id": "demo", "target_type": "agent",
            "external_target_id": "loan", "external_version_id": "v1"}


def descriptor(ref: dict | None = None, **overrides: object) -> dict:
    value: dict[str, object] = {
        "ref": ref or agent_ref(), "display_name": "信贷助手",
        "fetched_at": T0,
    }
    value.update(overrides)
    return value


def summary(key: str, level: str, **overrides: object) -> dict:
    value: dict[str, object] = {"key": key, "level": level, "passed": 9, "failed": 1,
                                "reviewed": 0, "not_applicable": 0, "errors": 0,
                                "applicable": 10, "total": 10, "score": 0.9}
    value.update(overrides)
    return value


LEGAL_SAMPLES: list[tuple[str, str, dict]] = [
    ("tool_minimal", "tool", tool()),
    ("tool_full", "tool", tool("transfer", description="转账工具",
                               input_schema={"type": "object"},
                               output_schema={"type": "object", "properties": {"ok": {"type": "boolean"}}})),
    ("skill_minimal", "skill", skill()),
    ("skill_with_tools_and_prompt", "skill",
     skill(prompt="你是信贷助手", tools=[tool("search"), tool("transfer", description="转账")],
           metadata={"owner": "team-1"})),
    ("skill_schemas", "skill", skill(input_schema={"type": "object"},
                                     output_schema={"type": "string"})),
    ("descriptor_agent_full", "descriptor", descriptor(
        description="完整描述", prompt="系统提示词",
        skills=[skill("skill-1", "v1", tools=[tool()]),
                skill("skill-2", "v2", prompt="技能提示")],
        tools=[tool("fallback")],
        input_schema={"type": "object"}, output_schema={"type": "object"},
        metadata={"region": "cn"})),
    ("descriptor_skill_type", "descriptor", descriptor(
        ref={"source_id": "demo", "target_type": "skill",
             "external_target_id": "loan-skill", "external_version_id": "v1"},
        tools=[tool("execute")])),
    ("descriptor_fetched_at_variant", "descriptor", descriptor(fetched_at=T1)),
    ("summary_overall", "metric_summary", summary("overall", "overall")),
    ("summary_dimension", "metric_summary", summary("state", "dimension", passed=4, failed=1,
                                                    applicable=5, total=5, score=0.8)),
    ("summary_no_applicable", "metric_summary",
     summary("kind-rule", "kind", score=None, passed=0, failed=0, reviewed=0,
             not_applicable=0, errors=0, applicable=0, total=0)),
    ("summary_with_errors_and_na", "metric_summary",
     summary("metric-x", "metric", passed=2, failed=1, reviewed=1, not_applicable=2,
             errors=1, applicable=4, total=7, score=0.75)),
]

INVALID_SAMPLES: list[tuple[str, str, dict]] = [
    ("tool_blank_name", "tool", tool(" ")),
    ("skill_blank_id", "skill", skill(" ")),
    ("skill_dup_tools", "skill", skill(tools=[tool("search"), tool("search")])),
    ("skill_prompt_hash_mismatch", "skill", skill(prompt="文本", prompt_sha256="0" * 64)),
    ("skill_bad_prompt_hash_format", "skill", skill(prompt_sha256="xyz")),
    ("skill_metadata_credential", "skill", skill(metadata={"api_key": "sk-1"})),
    ("descriptor_skill_with_nested_skills", "descriptor", descriptor(
        ref={"source_id": "demo", "target_type": "skill",
             "external_target_id": "s", "external_version_id": "v"},
        skills=[skill()])),
    ("descriptor_dup_skills", "descriptor", descriptor(
        skills=[skill("skill-1", "v1"), skill("skill-1", "v1")])),
    ("descriptor_dup_tools", "descriptor", descriptor(
        tools=[tool("search"), tool("search")])),
    ("descriptor_prompt_hash_mismatch", "descriptor",
     descriptor(prompt="提示", prompt_sha256="0" * 64)),
    ("descriptor_hash_bad_format", "descriptor", descriptor(content_sha256="xyz")),
    ("descriptor_hash_mismatch", "descriptor", descriptor(content_sha256="0" * 64)),
    ("descriptor_blank_display_name", "descriptor", descriptor(display_name=" ")),
    ("summary_blank_key", "metric_summary", {
        "key": " ", "level": "overall", "score": 0.9, "passed": 1, "failed": 0,
        "reviewed": 0, "not_applicable": 0, "errors": 0, "applicable": 1, "total": 1}),
    ("summary_bad_level", "metric_summary", summary("k", "weird")),
    ("summary_total_mismatch", "metric_summary", summary("overall", "overall", total=11)),
    ("summary_applicable_mismatch", "metric_summary",
     summary("overall", "overall", applicable=9)),
    ("summary_score_existence", "metric_summary",
     summary("overall", "overall", score=None)),
    ("summary_score_without_applicable", "metric_summary",
     summary("k", "kind", score=0.5, passed=0, failed=0, reviewed=0, applicable=0, total=0)),
    ("summary_overall_key_mismatch", "metric_summary",
     summary("overall", "dimension")),
    ("summary_negative_count", "metric_summary",
     summary("overall", "overall", failed=-1)),
]


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--python-src", default=DEFAULT_PYTHON_SRC)
    parser.add_argument("--output", default=str(DEFAULT_OUTPUT))
    args = parser.parse_args()

    sys.path.insert(0, args.python_src)
    from agentgate.domain.base import canonical_json, content_sha256
    from agentgate.domain.metric import MetricSummary
    from agentgate.domain.target import SkillDescriptor, TargetDescriptor, ToolDescriptor

    classes = {"tool": ToolDescriptor, "skill": SkillDescriptor,
               "descriptor": TargetDescriptor, "metric_summary": MetricSummary}

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
            "Golden fixtures for ToolDescriptor/SkillDescriptor/TargetDescriptor/"
            "MetricSummary parity with Python agentgate. Generated by "
            "scripts/generate_target_descriptors_golden.py."
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
