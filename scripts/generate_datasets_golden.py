#!/usr/bin/env python3
"""Generate Dataset/DatasetVersion golden fixtures for the Java domain contract tests.

Read-only against the Python agentgate implementation: validates sample inputs
through pydantic model_validate and records model_dump payloads, canonical JSON
and SHA-256 digests plus validation error messages. Timestamp samples verify the
"Z"-suffix serialization and offset normalization contract. The Python repo is
never modified.

Usage:
    /path/to/pythonwork/agentgate/.venv/bin/python scripts/generate_datasets_golden.py \
        [--python-src /path/to/agentgate/src] [--output path.json]
"""

from __future__ import annotations

import argparse
import json
import platform
import sys
from datetime import UTC, datetime, timedelta, timezone
from pathlib import Path

from pydantic import ValidationError

REPO_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_PYTHON_SRC = "/Users/eric/hw/xql/pythonwork/agentgate/src"
DEFAULT_OUTPUT = REPO_ROOT / "src/test/resources/contract/datasets.json"

T0 = datetime(2026, 1, 1, tzinfo=UTC)
T1 = datetime(2026, 1, 2, tzinfo=UTC)
T0_MICRO = datetime(2026, 1, 1, 0, 0, 0, 1, tzinfo=UTC)
T1_MICRO = datetime(2026, 1, 2, 0, 0, 0, 123456, tzinfo=UTC)
BEIJING = timezone(timedelta(hours=8))


def case_payload(case_id: str, name: str = None) -> dict:
    value = {
        "id": case_id,
        "name": name or f"用例-{case_id}",
        "turns": [
            {
                "id": f"{case_id}-t1",
                "input": {"question": f"问题-{case_id}"},
                "expectations": [
                    {"kind": "policy", "id": f"{case_id}-e1", "policy_id": f"policy-{case_id}"},
                ],
            }
        ],
    }
    return value


LEGAL_DATASETS: list[tuple[str, dict]] = [
    ("dataset_minimal", {"id": "d1", "name": "信贷评测集", "created_at": T0, "updated_at": T0}),
    ("dataset_full", {
        "id": "d2", "name": "客服评测集", "description": "多轮客服对话",
        "archived": True, "created_at": T0, "updated_at": T1,
        "user_team_id": "team-1", "user_id": "u1", "user_name": "张三",
    }),
    ("dataset_defaults", {"id": "d3", "name": "默认字段", "created_at": T0, "updated_at": T0}),
]

LEGAL_VERSIONS: list[tuple[str, dict]] = [
    ("version_draft_minimal", {"id": "v1", "dataset_id": "d1", "created_at": T0, "updated_at": T0}),
    ("version_draft_with_cases", {
        "id": "v2", "dataset_id": "d1", "cases": [case_payload("c1"), case_payload("c2")],
        "created_at": T0, "updated_at": T0,
    }),
    ("version_draft_notes", {
        "id": "v3", "dataset_id": "d1", "notes": "草稿备注-包含中文与空格",
        "created_at": T0, "updated_at": T0,
    }),
    ("version_published_full", {
        "id": "v4", "dataset_id": "d1", "version": 3, "status": "published",
        "based_on_version": 2, "cases": [case_payload("c1")],
        "published_at": T1, "created_at": T0, "updated_at": T1,
    }),
    ("version_published_no_based_on", {
        "id": "v5", "dataset_id": "d1", "version": 1, "status": "published",
        "cases": [case_payload("c1")],
        "published_at": T0, "created_at": T0, "updated_at": T1,
    }),
    ("version_dataset_meta", {
        "id": "v6", "dataset_id": "d1", "dataset_name": "数据集名快照",
        "dataset_description": "数据集描述快照", "created_at": T0, "updated_at": T0,
    }),
    ("version_user_fields", {
        "id": "v7", "dataset_id": "d1", "user_team_id": "team-9",
        "user_id": "u9", "user_name": "李四", "created_at": T0, "updated_at": T0,
    }),
    ("version_offset_normalized", {
        "id": "v8", "dataset_id": "d1",
        "created_at": datetime(2026, 1, 1, 8, 0, 0, tzinfo=BEIJING),
        "updated_at": datetime(2026, 1, 2, 8, 0, 0, tzinfo=BEIJING),
    }),
    ("version_microseconds", {
        "id": "v9", "dataset_id": "d1",
        "created_at": T0_MICRO, "updated_at": T1_MICRO,
    }),
    ("version_explicit_correct_hash", None),
]

INVALID_SAMPLES: list[tuple[str, str, dict, bool]] = [
    ("dataset_blank_id", "dataset",
     {"id": " ", "name": "n", "created_at": T0, "updated_at": T0}, True),
    ("dataset_blank_name", "dataset",
     {"id": "d1", "name": " ", "created_at": T0, "updated_at": T0}, True),
    ("dataset_updated_before_created", "dataset",
     {"id": "d1", "name": "n", "created_at": T1, "updated_at": T0}, True),
    ("version_blank_dataset_id", "version",
     {"id": "v1", "dataset_id": " ", "created_at": T0, "updated_at": T0}, True),
    ("version_ge_zero", "version",
     {"id": "v1", "dataset_id": "d1", "version": 0, "created_at": T0, "updated_at": T0}, True),
    ("version_based_on_ge_zero", "version",
     {"id": "v1", "dataset_id": "d1", "based_on_version": 0,
      "created_at": T0, "updated_at": T0}, True),
    ("version_bad_status", "version",
     {"id": "v1", "dataset_id": "d1", "status": "weird",
      "created_at": T0, "updated_at": T0}, True),
    ("version_draft_numbered", "version",
     {"id": "v1", "dataset_id": "d1", "version": 1,
      "created_at": T0, "updated_at": T0}, True),
    ("version_draft_published_at", "version",
     {"id": "v1", "dataset_id": "d1", "published_at": T1,
      "created_at": T0, "updated_at": T1}, True),
    ("version_published_no_version", "version",
     {"id": "v1", "dataset_id": "d1", "status": "published", "published_at": T1,
      "cases": [case_payload("c1")], "created_at": T0, "updated_at": T1}, True),
    ("version_published_no_published_at", "version",
     {"id": "v1", "dataset_id": "d1", "status": "published", "version": 1,
      "cases": [case_payload("c1")], "created_at": T0, "updated_at": T1}, True),
    ("version_published_no_cases", "version",
     {"id": "v1", "dataset_id": "d1", "status": "published", "version": 1,
      "published_at": T1, "cases": [], "created_at": T0, "updated_at": T1}, True),
    ("version_published_early", "version",
     {"id": "v1", "dataset_id": "d1", "status": "published", "version": 1,
      "cases": [case_payload("c1")], "published_at": T0 - timedelta(seconds=1),
      "created_at": T0, "updated_at": T1}, True),
    ("version_published_late", "version",
     {"id": "v1", "dataset_id": "d1", "status": "published", "version": 1,
      "cases": [case_payload("c1")], "published_at": T1 + timedelta(seconds=1),
      "created_at": T0, "updated_at": T1}, True),
    ("version_based_on_ge_version", "version",
     {"id": "v1", "dataset_id": "d1", "status": "published", "version": 2,
      "based_on_version": 2, "cases": [case_payload("c1")],
      "published_at": T1, "created_at": T0, "updated_at": T1}, True),
    ("version_updated_before_created", "version",
     {"id": "v1", "dataset_id": "d1", "created_at": T1, "updated_at": T0}, True),
    ("version_dup_case_ids", "version",
     {"id": "v1", "dataset_id": "d1", "cases": [case_payload("c1"), case_payload("c1")],
      "created_at": T0, "updated_at": T0}, True),
    ("version_hash_mismatch", "version",
     {"id": "v1", "dataset_id": "d1", "content_sha256": "0" * 64,
      "created_at": T0, "updated_at": T0}, True),
    ("version_bad_datetime", "version",
     {"id": "v1", "dataset_id": "d1", "created_at": "not-a-date",
      "updated_at": T0}, False),
    ("version_float_version", "version",
     {"id": "v1", "dataset_id": "d1", "version": 1.5,
      "created_at": T0, "updated_at": T0}, True),
]


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--python-src", default=DEFAULT_PYTHON_SRC)
    parser.add_argument("--output", default=str(DEFAULT_OUTPUT))
    args = parser.parse_args()

    sys.path.insert(0, args.python_src)
    from agentgate.domain.base import canonical_json, content_sha256
    from agentgate.domain.dataset import Dataset, DatasetVersion

    published = {
        "id": "v10", "dataset_id": "d1", "version": 1, "status": "published",
        "cases": [case_payload("c1")],
        "published_at": T1, "created_at": T0, "updated_at": T1,
    }
    probe = DatasetVersion.model_validate(dict(published))
    correct_hash_sample = dict(published)
    correct_hash_sample["content_sha256"] = probe.content_sha256
    for index, entry in enumerate(LEGAL_VERSIONS):
        if entry[0] == "version_explicit_correct_hash":
            LEGAL_VERSIONS[index] = ("version_explicit_correct_hash", correct_hash_sample)

    samples = []
    for union, entries in (("dataset", LEGAL_DATASETS), ("version", LEGAL_VERSIONS)):
        model_cls = Dataset if union == "dataset" else DatasetVersion
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
    for name, union, value, alignable in INVALID_SAMPLES:
        model_cls = Dataset if union == "dataset" else DatasetVersion
        try:
            model_cls.model_validate(value)
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
            "Golden fixtures for Dataset/DatasetVersion parity with Python "
            "agentgate.domain.dataset. Generated by scripts/generate_datasets_golden.py."
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
