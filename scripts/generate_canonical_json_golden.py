#!/usr/bin/env python3
"""Generate canonical-JSON golden fixtures for the Java domain contract tests.

Read-only against the Python agentgate implementation: imports canonical_json /
content_sha256 from the reference codebase and records their outputs for a
curated + seeded-random sample set. Script and fixtures live in the Java repo;
the Python repo is never modified.

Usage:
    /path/to/pythonwork/agentgate/.venv/bin/python scripts/generate_canonical_json_golden.py \
        [--python-src /path/to/agentgate/src] [--output path.json] [--seed N]
"""

from __future__ import annotations

import argparse
import json
import math
import platform
import random
import struct
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_PYTHON_SRC = "/Users/eric/hw/xql/pythonwork/agentgate/src"
DEFAULT_OUTPUT = REPO_ROOT / "src/test/resources/contract/canonical-json.json"

RANDOM_DOUBLE_COUNT = 600
RANDOM_INT_COUNT = 200
RANDOM_OBJECT_COUNT = 250

INVALID_FLOATS = [
    ("double_nan", float("nan")),
    ("positive_infinity", float("inf")),
    ("negative_infinity", float("-inf")),
]

KEY_POOL = [
    "a", "b", "z", "A", "Z", "0", "9", "中", "文", "🚀", "🎉",
    "café", "日本語", "￿", "", "ключ", "key-with-dash", "snake_case",
]

STRING_POOL = [
    "", "x", "中文", "🚀", "hello world", "line\nbreak",
    "quote\"inside", "tab\there", "\u0000\u001f",
]


def curated_samples() -> list[tuple[str, object]]:
    return [
        ("scalar_null", None),
        ("scalar_true", True),
        ("scalar_false", False),
        ("scalar_empty_string", ""),
        ("scalar_string_chinese", "中文评测平台AgentGate"),
        ("scalar_string_emoji", "🚀🎉🇨🇳火箭发射"),
        ("scalar_string_escapes", "line1\nline2\ttab\"quote\"\\back"),
        ("scalar_string_control", "\u0000\u0001\b\t\n\f\r\u001f\u007f\u2028\u2029"),
        ("int_zero", 0),
        ("int_negative", -42),
        ("int_long_max", 9223372036854775807),
        ("int_long_min", -9223372036854775808),
        ("int_beyond_long", 10 ** 30),
        ("int_beyond_long_negative", -(10 ** 25)),
        ("float_simple", 0.1),
        ("float_two_decimals", -2.5),
        ("float_repr_artifact", 0.30000000000000004),
        ("float_integral", 1.0),
        ("float_zero", 0.0),
        ("float_negative_zero", -0.0),
        ("float_huge", 1e300),
        ("float_max_double", 1.7976931348623157e308),
        ("float_min_subnormal", 5e-324),
        ("float_scientific_neg", 1.5e-7),
        ("float_boundary_1e16", 1e16),
        ("float_boundary_1e15", 1e15),
        ("float_boundary_1e_neg4", 0.0001),
        ("float_boundary_1e_neg5", 0.00001),
        ("float_positional_16_digits", 123456789012345.6),
        ("float_scientific_17_digits", 12345678901234567.0),
        ("object_key_sorting", {"b": 1, "a": "x", "C": True, "0": None, "中": 2}),
        ("object_non_ascii_keys", {"🚀": "emoji", "中": "bmp", "￿": "last", "": "private", "a": "ascii"}),
        ("object_int_float_distinction", {"int": 1, "float": 1.0}),
        ("object_empty", {}),
        ("object_empty_containers", {"empty_map": {}, "empty_list": [], "empty_string": "", "zero": 0}),
        (
            "object_nested",
            {
                "agent": "评测",
                "runs": [{"id": "r1", "metrics": {"accuracy": 0.95, "loss": 1e-7}}],
                "tags": ["中文", "🚀"],
                "meta": {"deep": {"deeper": {"deepest": [None, True, False]}}},
            },
        ),
        ("object_credential_like", {"model": "gpt", "api_key": "sk-test", "nested": {"password": "p"}}),
        ("array_empty", []),
        ("array_mixed", [None, True, False, 0, -1, "", "0", 1.0, 0.1]),
        ("array_nested", [1, [2, [3, [4, [5]]]]]),
    ]


def random_double_samples(rng: random.Random, count: int) -> list[tuple[str, float]]:
    samples: list[tuple[str, float]] = []
    for i in range(count):
        strategy = rng.randrange(6)
        if strategy == 0:
            while True:
                bits = rng.getrandbits(64)
                value = struct.unpack("<d", struct.pack("<Q", bits))[0]
                if math.isfinite(value):
                    break
        elif strategy == 1:
            value = math.ldexp(rng.random(), rng.randint(-1074, 971))
        elif strategy == 2:
            value = float(rng.randint(-10 ** 6, 10 ** 6))
        elif strategy == 3:
            value = rng.randint(-2 ** 40, 2 ** 40) / float(2 ** rng.randint(0, 30))
        elif strategy == 4:
            value = float(10) ** rng.randint(-300, 300)
        else:
            value = rng.randint(0, 2 ** 53) / float(10 ** rng.randint(0, 17))
        samples.append((f"random_double_{i:04d}", value))
    return samples


def random_int_samples(rng: random.Random, count: int) -> list[tuple[str, int]]:
    samples: list[tuple[str, int]] = []
    for i in range(count):
        strategy = rng.randrange(3)
        if strategy == 0:
            value = rng.randint(-1000, 1000)
        elif strategy == 1:
            value = rng.randint(-(2 ** 63), 2 ** 63 - 1)
        else:
            value = rng.randint(-(10 ** 40), 10 ** 40)
        samples.append((f"random_int_{i:04d}", value))
    return samples


def random_scalar(rng: random.Random, doubles: list[float], ints: list[int]) -> object:
    choice = rng.randrange(7)
    if choice == 0:
        return None
    if choice == 1:
        return rng.choice([True, False])
    if choice == 2:
        return rng.choice(doubles)
    if choice == 3:
        return rng.choice(ints)
    if choice == 4:
        return rng.choice(STRING_POOL)
    if choice == 5:
        return rng.choice(KEY_POOL)
    return rng.randint(-(10 ** 30), 10 ** 30)


def random_container(
    rng: random.Random, doubles: list[float], ints: list[int], depth: int
) -> object:
    if depth <= 0 or rng.randrange(4) == 0:
        return random_scalar(rng, doubles, ints)
    if rng.randrange(2) == 0:
        return {
            rng.choice(KEY_POOL) + str(rng.randrange(50)): random_container(rng, doubles, ints, depth - 1)
            for _ in range(rng.randrange(1, 6))
        }
    return [random_container(rng, doubles, ints, depth - 1) for _ in range(rng.randrange(1, 6))]


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--python-src", default=DEFAULT_PYTHON_SRC,
                        help="path to the Python agentgate src directory")
    parser.add_argument("--output", default=str(DEFAULT_OUTPUT),
                        help="output fixture path")
    parser.add_argument("--seed", type=int, default=20261001,
                        help="random seed for reproducible samples")
    args = parser.parse_args()

    sys.path.insert(0, args.python_src)
    from agentgate.domain.base import canonical_json, content_sha256

    rng = random.Random(args.seed)
    double_samples = random_double_samples(rng, RANDOM_DOUBLE_COUNT)
    int_samples = random_int_samples(rng, RANDOM_INT_COUNT)
    double_pool = [value for _, value in double_samples]
    int_pool = [value for _, value in int_samples]
    object_samples = [
        (f"random_object_{i:04d}", random_container(rng, double_pool, int_pool, 5))
        for i in range(RANDOM_OBJECT_COUNT)
    ]
    samples = curated_samples() + double_samples + int_samples + object_samples

    records = []
    for name, value in samples:
        encoded = json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
        assert json.loads(encoded) == value, f"sample {name} does not round-trip as JSON input"
        canonical = canonical_json(value)
        assert json.loads(canonical) == value, f"sample {name} canonical does not re-parse to the value"
        records.append({
            "name": name,
            "value": value,
            "canonical": canonical,
            "sha256": content_sha256(value),
        })

    invalid = []
    for name, value in INVALID_FLOATS:
        try:
            canonical_json(value)
        except ValueError as exc:
            assert "Out of range" in str(exc), f"{name}: unexpected error message {exc!r}"
            invalid.append(name)
        else:
            raise SystemExit(f"{name} did not raise ValueError")

    document = {
        "description": (
            "Golden fixtures for CanonicalJson/ContentSha256 parity with Python "
            "agentgate.domain.base. Generated by scripts/generate_canonical_json_golden.py; "
            "regenerate with the same seed to reproduce."
        ),
        "python_version": platform.python_version(),
        "seed": args.seed,
        "sample_count": len(records),
        "samples": records,
        "invalid_floats": invalid,
    }

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    with open(output, "w", encoding="utf-8") as handle:
        json.dump(document, handle, ensure_ascii=False, indent=2)
        handle.write("\n")
    print(f"wrote {len(records)} samples ({len(invalid)} invalid-float cases) to {output}")


if __name__ == "__main__":
    main()
