#!/usr/bin/env python3
"""Generate Evaluator golden fixtures for the Java domain contract tests.

Read-only against the Python agentgate implementation: validates sample inputs
through pydantic model_validate and records model_dump payloads, canonical JSON
and SHA-256 digests plus validation error messages. The Python repo is never
modified.

Usage:
    /path/to/pythonwork/agentgate/.venv/bin/python scripts/generate_evaluators_golden.py \
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
DEFAULT_OUTPUT = REPO_ROOT / "src/test/resources/contract/evaluators.json"

T0 = datetime(2026, 1, 1, tzinfo=UTC)
T1 = datetime(2026, 1, 2, tzinfo=UTC)


def ref(evaluator_id: str, version: str = "1", weight: float | None = None) -> dict:
    value = {"evaluator_id": evaluator_id, "evaluator_version": version}
    if weight is not None:
        value["weight"] = weight
    return value


def llm_config() -> dict:
    return {"model": {"provider_id": "openai", "model_id": "gpt-4o",
                      "credential_ref": "cred-main"}}


LEGAL_SAMPLES: list[tuple[str, str, dict]] = [
    ("ref_minimal", "ref", ref("child-a")),
    ("ref_weighted", "ref", ref("child-b", "3", 1.5)),
    ("evaluator_minimal", "evaluator", {"id": "e1", "name": "状态评测器",
                                        "created_at": T0, "updated_at": T0}),
    ("evaluator_builtin_enabled", "evaluator", {"id": "e2", "name": "内置评测器",
                                                "source": "builtin", "enabled": True,
                                                "created_at": T0, "updated_at": T0}),
    ("evaluator_full", "evaluator", {"id": "e3", "name": "完整字段", "description": "描述",
                                     "created_at": T0, "updated_at": T1,
                                     "user_team_id": "t", "user_id": "u", "user_name": "张三"}),
    ("draft_rule_minimal", "draft", {"id": "d1", "evaluator_id": "e1", "dimension": "state",
                                     "metric": "final_state", "implementation_id": "final_state",
                                     "created_at": T0, "updated_at": T0}),
    ("draft_llm_judge", "draft", {"id": "d2", "evaluator_id": "e2", "kind": "llm_judge",
                                  "dimension": "answer_quality", "metric": "judge_score",
                                  "implementation_id": "llm_judge",
                                  "implementation_version": "2", "config": llm_config(),
                                  "created_at": T0, "updated_at": T0}),
    ("draft_hybrid_all", "draft", {"id": "d3", "evaluator_id": "e3", "kind": "hybrid",
                                   "dimension": "combined", "metric": "combo",
                                   "implementation_id": "hybrid_impl",
                                   "children": [ref("a"), ref("b")], "combination": "all",
                                   "created_at": T0, "updated_at": T0}),
    ("draft_hybrid_weighted", "draft", {"id": "d4", "evaluator_id": "e4", "kind": "hybrid",
                                        "dimension": "weighted", "metric": "wcombo",
                                        "implementation_id": "hybrid_impl",
                                        "children": [ref("a", "1", 0.6), ref("b", "2", 0.4)],
                                        "combination": "weighted_score",
                                        "created_at": T0, "updated_at": T0}),
    ("draft_based_on_blocking", "draft", {"id": "d5", "evaluator_id": "e5",
                                          "based_on_version": "3", "severity": "blocking",
                                          "dimension": "safety", "metric": "safe",
                                          "implementation_id": "safety_impl",
                                          "created_at": T0, "updated_at": T0}),
    ("spec_minimal", "spec", {"id": "s1", "name": "规格", "dimension": "state",
                              "metric": "final_state", "implementation_id": "final_state"}),
    ("spec_llm_judge", "spec", {"id": "s2", "name": "LLM 评审", "kind": "llm_judge",
                                "dimension": "answer_quality", "metric": "judge_score",
                                "implementation_id": "llm_judge", "config": llm_config()}),
    ("spec_hybrid_weighted", "spec", {"id": "s3", "name": "组合", "kind": "hybrid",
                                      "dimension": "combined", "metric": "combo",
                                      "implementation_id": "hybrid_impl",
                                      "children": [ref("a", "1", 0.6), ref("b", "2", 0.4)],
                                      "combination": "weighted_score"}),
    ("spec_user_fields_excluded_from_hash", "spec", {"id": "s4", "name": "哈希稳定",
                                                     "dimension": "d", "metric": "m",
                                                     "implementation_id": "i",
                                                     "user_team_id": "t", "user_id": "u",
                                                     "user_name": "李四"}),
    ("spec_explicit_correct_hash", "spec", None),
]

INVALID_SAMPLES: list[tuple[str, str, dict, bool]] = [
    ("ref_blank_id", "ref", ref(" "), True),
    ("ref_blank_version", "ref", {"evaluator_id": "a", "evaluator_version": " "}, True),
    ("ref_weight_zero", "ref", ref("a", "1", 0), True),
    ("ref_weight_negative", "ref", ref("a", "1", -0.5), True),
    ("evaluator_blank_name", "evaluator", {"id": "e1", "name": " ",
                                           "created_at": T0, "updated_at": T0}, True),
    ("evaluator_updated_before_created", "evaluator", {"id": "e1", "name": "n",
                                                       "created_at": T1, "updated_at": T0}, True),
    ("evaluator_builtin_disabled", "evaluator", {"id": "e1", "name": "n", "source": "builtin",
                                                 "enabled": False,
                                                 "created_at": T0, "updated_at": T0}, True),
    ("draft_blank_dimension", "draft", {"id": "d", "evaluator_id": "e", "dimension": " ",
                                        "metric": "m", "implementation_id": "i",
                                        "created_at": T0, "updated_at": T0}, True),
    ("draft_blank_based_on", "draft", {"id": "d", "evaluator_id": "e", "based_on_version": " ",
                                       "dimension": "d", "metric": "m", "implementation_id": "i",
                                       "created_at": T0, "updated_at": T0}, True),
    ("draft_config_credential", "draft", {"id": "d", "evaluator_id": "e", "dimension": "d",
                                          "metric": "m", "implementation_id": "i",
                                          "config": {"api_key": "sk-x"},
                                          "created_at": T0, "updated_at": T0}, True),
    ("draft_llm_no_model", "draft", {"id": "d", "evaluator_id": "e", "kind": "llm_judge",
                                     "dimension": "d", "metric": "m", "implementation_id": "i",
                                     "created_at": T0, "updated_at": T0}, True),
    ("draft_llm_model_not_mapping", "draft", {"id": "d", "evaluator_id": "e", "kind": "llm_judge",
                                              "dimension": "d", "metric": "m",
                                              "implementation_id": "i", "config": {"model": 123},
                                              "created_at": T0, "updated_at": T0}, True),
    ("draft_llm_provider_not_string", "draft", {"id": "d", "evaluator_id": "e",
                                                "kind": "llm_judge", "dimension": "d",
                                                "metric": "m", "implementation_id": "i",
                                                "config": {"model": {"provider_id": 1,
                                                                     "model_id": "x"}},
                                                "created_at": T0, "updated_at": T0}, True),
    ("draft_llm_provider_blank", "draft", {"id": "d", "evaluator_id": "e",
                                           "kind": "llm_judge", "dimension": "d",
                                           "metric": "m", "implementation_id": "i",
                                           "config": {"model": {"provider_id": " ",
                                                                "model_id": "x"}},
                                           "created_at": T0, "updated_at": T0}, True),
    ("draft_llm_credential_ref_not_string", "draft", {"id": "d", "evaluator_id": "e",
                                                      "kind": "llm_judge", "dimension": "d",
                                                      "metric": "m", "implementation_id": "i",
                                                      "config": {"model": {"provider_id": "p",
                                                                           "model_id": "m",
                                                                           "credential_ref": 5}},
                                                      "created_at": T0, "updated_at": T0}, True),
    ("draft_llm_credential_ref_blank", "draft", {"id": "d", "evaluator_id": "e",
                                                 "kind": "llm_judge", "dimension": "d",
                                                 "metric": "m", "implementation_id": "i",
                                                 "config": {"model": {"provider_id": "p",
                                                                      "model_id": "m",
                                                                      "credential_ref": " "}},
                                                 "created_at": T0, "updated_at": T0}, True),
    ("draft_dup_children", "draft", {"id": "d", "evaluator_id": "e", "kind": "hybrid",
                                     "dimension": "d", "metric": "m", "implementation_id": "i",
                                     "children": [ref("a"), ref("a")], "combination": "all",
                                     "created_at": T0, "updated_at": T0}, True),
    ("draft_non_hybrid_with_children", "draft", {"id": "d", "evaluator_id": "e",
                                                 "dimension": "d", "metric": "m",
                                                 "implementation_id": "i",
                                                 "children": [ref("a"), ref("b")],
                                                 "combination": "all",
                                                 "created_at": T0, "updated_at": T0}, True),
    ("draft_non_hybrid_with_combination", "draft", {"id": "d", "evaluator_id": "e",
                                                    "dimension": "d", "metric": "m",
                                                    "implementation_id": "i",
                                                    "combination": "any",
                                                    "created_at": T0, "updated_at": T0}, True),
    ("draft_hybrid_one_child", "draft", {"id": "d", "evaluator_id": "e", "kind": "hybrid",
                                         "dimension": "d", "metric": "m",
                                         "implementation_id": "i",
                                         "children": [ref("a")], "combination": "all",
                                         "created_at": T0, "updated_at": T0}, True),
    ("draft_hybrid_no_combination", "draft", {"id": "d", "evaluator_id": "e", "kind": "hybrid",
                                              "dimension": "d", "metric": "m",
                                              "implementation_id": "i",
                                              "children": [ref("a"), ref("b")],
                                              "created_at": T0, "updated_at": T0}, True),
    ("draft_weighted_missing_weight", "draft", {"id": "d", "evaluator_id": "e", "kind": "hybrid",
                                                "dimension": "d", "metric": "m",
                                                "implementation_id": "i",
                                                "children": [ref("a", "1", 0.6), ref("b")],
                                                "combination": "weighted_score",
                                                "created_at": T0, "updated_at": T0}, True),
    ("draft_unweighted_with_weight", "draft", {"id": "d", "evaluator_id": "e", "kind": "hybrid",
                                               "dimension": "d", "metric": "m",
                                               "implementation_id": "i",
                                               "children": [ref("a", "1", 0.6), ref("b", "2", 0.4)],
                                               "combination": "all",
                                               "created_at": T0, "updated_at": T0}, True),
    ("draft_updated_before_created", "draft", {"id": "d", "evaluator_id": "e", "dimension": "d",
                                               "metric": "m", "implementation_id": "i",
                                               "created_at": T1, "updated_at": T0}, True),
    ("spec_blank_version", "spec", {"id": "s", "name": "n", "version": " ", "dimension": "d",
                                    "metric": "m", "implementation_id": "i"}, True),
    ("spec_config_credential", "spec", {"id": "s", "name": "n", "dimension": "d", "metric": "m",
                                        "implementation_id": "i",
                                        "config": {"nested": {"password": "p"}}}, True),
    ("spec_hash_bad_format", "spec", {"id": "s", "name": "n", "dimension": "d", "metric": "m",
                                      "implementation_id": "i", "content_sha256": "xyz"}, True),
    ("spec_hash_mismatch", "spec", {"id": "s", "name": "n", "dimension": "d", "metric": "m",
                                    "implementation_id": "i",
                                    "content_sha256": "0" * 64}, True),
    ("spec_bad_kind", "spec", {"id": "s", "name": "n", "kind": "weird", "dimension": "d",
                               "metric": "m", "implementation_id": "i"}, True),
    ("spec_bad_combination", "spec", {"id": "s", "name": "n", "combination": "weird",
                                      "dimension": "d", "metric": "m",
                                      "implementation_id": "i"}, True),
]


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--python-src", default=DEFAULT_PYTHON_SRC)
    parser.add_argument("--output", default=str(DEFAULT_OUTPUT))
    args = parser.parse_args()

    sys.path.insert(0, args.python_src)
    from agentgate.domain.base import canonical_json, content_sha256
    from agentgate.domain.evaluator import Evaluator, EvaluatorDraft, EvaluatorRef, EvaluatorSpec

    base_spec = {"id": "s5", "name": "显式哈希", "dimension": "d", "metric": "m",
                 "implementation_id": "i"}
    probe = EvaluatorSpec.model_validate(dict(base_spec))
    explicit_hash_sample = dict(base_spec)
    explicit_hash_sample["content_sha256"] = probe.content_sha256
    for index, entry in enumerate(LEGAL_SAMPLES):
        if entry[0] == "spec_explicit_correct_hash":
            LEGAL_SAMPLES[index] = ("spec_explicit_correct_hash", "spec", explicit_hash_sample)

    classes = {"ref": EvaluatorRef, "evaluator": Evaluator,
               "draft": EvaluatorDraft, "spec": EvaluatorSpec}

    samples = []
    for name, union, value in LEGAL_SAMPLES:
        model = classes[union].model_validate(value)
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
        try:
            classes[union].model_validate(value)
        except ValidationError as exc:
            errors = exc.errors()
            assert errors, f"sample {name}: no validation errors"
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
            "Golden fixtures for Evaluator/EvaluatorRef/EvaluatorDraft/EvaluatorSpec parity "
            "with Python agentgate.domain.evaluator. Generated by "
            "scripts/generate_evaluators_golden.py."
        ),
        "python_version": platform.python_version(),
        "samples": samples,
        "invalid_samples": invalid,
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
    print(f"wrote {len(samples)} legal samples and {len(invalid)} invalid samples to {output}")


if __name__ == "__main__":
    main()
