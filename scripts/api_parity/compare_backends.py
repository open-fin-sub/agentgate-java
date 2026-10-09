#!/usr/bin/env python3
"""Compare AgentGate HTTP behavior across Python and Java backends."""

import argparse
import base64
import copy
import fnmatch
import hashlib
import json
import re
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path


VARIABLE_PATTERN = re.compile(r"\$\{([A-Za-z_][A-Za-z0-9_]*)\}")


def load_json(path):
    with Path(path).open("r", encoding="utf-8") as stream:
        return json.load(stream)


def render(value, variables):
    if isinstance(value, str):
        match = VARIABLE_PATTERN.fullmatch(value)
        if match and match.group(1) in variables:
            return copy.deepcopy(variables[match.group(1)])

        def replace(match_value):
            name = match_value.group(1)
            if name not in variables:
                raise KeyError(name)
            return str(variables[name])

        return VARIABLE_PATTERN.sub(replace, value)
    if isinstance(value, list):
        return [render(item, variables) for item in value]
    if isinstance(value, dict):
        return {key: render(item, variables) for key, item in value.items()}
    return value


def required_variables(case):
    names = set(case.get("required_variables", []))

    def visit(value):
        if isinstance(value, str):
            names.update(VARIABLE_PATTERN.findall(value))
        elif isinstance(value, list):
            for item in value:
                visit(item)
        elif isinstance(value, dict):
            for item in value.values():
                visit(item)

    visit(case.get("path", ""))
    visit(case.get("headers", {}))
    visit(case.get("body"))
    body_variable = case.get("body_variable")
    if body_variable:
        names.add(body_variable)
    raw_body_variable = case.get("raw_body_variable")
    if raw_body_variable:
        names.add(raw_body_variable)
        names.add(case["content_type_variable"])
    return names


def json_pointer(document, pointer):
    if pointer == "":
        return document
    if not pointer.startswith("/"):
        raise ValueError("JSON pointer must start with '/': " + pointer)
    current = document
    for raw_part in pointer[1:].split("/"):
        part = raw_part.replace("~1", "/").replace("~0", "~")
        current = current[int(part)] if isinstance(current, list) else current[part]
    return current


def parse_response_body(raw, content_type):
    if not raw:
        return None, "empty"
    if "json" in content_type.lower():
        try:
            return json.loads(raw.decode("utf-8")), "json"
        except (UnicodeDecodeError, json.JSONDecodeError):
            pass
    return {
        "sha256": hashlib.sha256(raw).hexdigest(),
        "size": len(raw),
    }, "binary"


def http_request(base_url, case, variables, timeout):
    path = render(case["path"], variables)
    headers = render(case.get("headers", {}), variables)
    body = None
    if "raw_body_variable" in case:
        body = base64.b64decode(variables[case["raw_body_variable"]])
        headers.setdefault("Content-Type", variables[case["content_type_variable"]])
        body_value = None
    elif "body_variable" in case:
        body_value = variables[case["body_variable"]]
    else:
        body_value = render(case.get("body"), variables)
    if body is None and body_value is not None:
        body = json.dumps(body_value, ensure_ascii=False).encode("utf-8")
        headers.setdefault("Content-Type", "application/json")
    request = urllib.request.Request(
        base_url.rstrip("/") + path,
        data=body,
        headers=headers,
        method=case["method"],
    )
    started = time.monotonic()
    try:
        response = urllib.request.urlopen(request, timeout=timeout)
        raw = response.read()
        status = response.status
        content_type = response.headers.get("Content-Type", "")
    except urllib.error.HTTPError as error:
        raw = error.read()
        status = error.code
        content_type = error.headers.get("Content-Type", "")
    parsed, body_kind = parse_response_body(raw, content_type)
    return {
        "status": status,
        "body": parsed,
        "body_kind": body_kind,
        "elapsed_ms": round((time.monotonic() - started) * 1000, 3),
    }


def normalize(value, normalizer_names, config, pointer=""):
    ignored = config.get("ignore_pointers", [])
    if any(fnmatch.fnmatchcase(pointer, pattern) for pattern in ignored):
        return "<ignored>"
    if isinstance(value, dict):
        ignored_keys = set(config.get("ignore_keys", []))
        return {
            key: "<ignored>" if key in ignored_keys else normalize(
                item, normalizer_names, config, pointer + "/" + key.replace("~", "~0").replace("/", "~1")
            )
            for key, item in sorted(value.items())
        }
    if isinstance(value, list):
        return [normalize(item, normalizer_names, config, pointer + "/" + str(index))
                for index, item in enumerate(value)]
    if isinstance(value, str):
        result = value
        patterns = config.get("patterns", {})
        for name in normalizer_names:
            rule = patterns.get(name)
            if rule:
                result = re.sub(rule["regex"], rule["replacement"], result)
        return result
    return value


def differences(expected, actual, pointer="", limit=100):
    found = []
    if type(expected) is not type(actual):
        return [{"path": pointer or "/", "python": expected, "java": actual}]
    if isinstance(expected, dict):
        for key in sorted(set(expected) | set(actual)):
            child = pointer + "/" + key.replace("~", "~0").replace("/", "~1")
            if key not in expected:
                found.append({"path": child, "python": "<missing>", "java": actual[key]})
            elif key not in actual:
                found.append({"path": child, "python": expected[key], "java": "<missing>"})
            else:
                found.extend(differences(expected[key], actual[key], child, limit - len(found)))
            if len(found) >= limit:
                break
        return found
    if isinstance(expected, list):
        if len(expected) != len(actual):
            found.append({"path": pointer or "/", "python_length": len(expected), "java_length": len(actual)})
        for index, pair in enumerate(zip(expected, actual)):
            found.extend(differences(pair[0], pair[1], pointer + "/" + str(index), limit - len(found)))
            if len(found) >= limit:
                break
        return found
    if expected != actual:
        found.append({"path": pointer or "/", "python": expected, "java": actual})
    return found


def execute_backend(name, base_url, cases, initial_variables, timeout):
    variables = copy.deepcopy(initial_variables)
    results = {}
    for case in cases:
        missing = sorted(required_variables(case) - set(variables))
        if missing:
            results[case["id"]] = {"state": "SKIPPED", "missing_variables": missing}
            continue
        try:
            result = http_request(base_url, case, variables, timeout)
            result["state"] = "EXECUTED"
            result["backend"] = name
            expected_status = case.get("expected_status")
            if expected_status is not None and result["status"] not in expected_status:
                result["expectation_error"] = "unexpected HTTP status"
            if case.get("extract"):
                if result["body_kind"] != "json":
                    raise ValueError("cannot extract variables from non-JSON response")
                for variable, pointer in case["extract"].items():
                    variables[variable] = json_pointer(result["body"], pointer)
            results[case["id"]] = result
        except (KeyError, ValueError, urllib.error.URLError, TimeoutError) as error:
            results[case["id"]] = {
                "state": "ERROR",
                "backend": name,
                "error": "%s: %s" % (type(error).__name__, error),
            }
    return results


def select_cases(manifest, include_all, patterns):
    selected = []
    for case in manifest["cases"]:
        if not include_all and not case.get("default", False):
            continue
        if patterns and not any(fnmatch.fnmatchcase(case["id"], pattern) for pattern in patterns):
            continue
        selected.append(case)
    return selected


def compare_results(cases, python_results, java_results, config, strict_skips):
    comparisons = []
    failed = 0
    for case in cases:
        case_id = case["id"]
        python_result = python_results[case_id]
        java_result = java_results[case_id]
        state = "PASSED"
        diff = []
        if python_result["state"] == "SKIPPED" or java_result["state"] == "SKIPPED":
            state = "FAILED" if strict_skips else "SKIPPED"
        elif python_result["state"] != "EXECUTED" or java_result["state"] != "EXECUTED":
            state = "FAILED"
        else:
            if python_result["status"] != java_result["status"]:
                diff.append({"path": "/http_status", "python": python_result["status"], "java": java_result["status"]})
            names = config.get("default_normalizers", []) + case.get("normalizers", [])
            python_body = normalize(python_result["body"], names, config)
            java_body = normalize(java_result["body"], names, config)
            diff.extend(differences(python_body, java_body, limit=max(1, 100 - len(diff))))
            if python_result.get("expectation_error") or java_result.get("expectation_error") or diff:
                state = "FAILED"
        if state == "FAILED":
            failed += 1
        comparisons.append({
            "id": case_id,
            "method": case["method"],
            "path": case["path"],
            "state": state,
            "differences": diff,
            "python": python_result,
            "java": java_result,
        })
    return comparisons, failed


def run_comparison(manifest, config, arguments):
    cases = select_cases(manifest, arguments.all, arguments.case)
    variables = copy.deepcopy(manifest.get("default_variables", {}))
    if arguments.variables:
        variables.update(load_json(arguments.variables))
    common_headers = manifest.get("default_headers", {})
    for case in cases:
        case["headers"] = dict(common_headers, **case.get("headers", {}))
    python_results = execute_backend("python", arguments.python_url, cases, variables, arguments.timeout)
    java_results = execute_backend("java", arguments.java_url, cases, variables, arguments.timeout)
    comparisons, failed = compare_results(cases, python_results, java_results, config, arguments.strict_skips)
    report = {
        "summary": {
            "inventory": len(manifest["cases"]),
            "selected": len(cases),
            "passed": sum(item["state"] == "PASSED" for item in comparisons),
            "failed": failed,
            "skipped": sum(item["state"] == "SKIPPED" for item in comparisons),
        },
        "python_url": arguments.python_url,
        "java_url": arguments.java_url,
        "cases": comparisons,
    }
    return report


def parse_arguments(argv=None):
    directory = Path(__file__).resolve().parent
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--python-url", required=True, help="Python base URL, for example http://127.0.0.1:8000")
    parser.add_argument("--java-url", required=True, help="Java base URL including context path, for example http://127.0.0.1:8080/race-api")
    parser.add_argument("--cases", default=str(directory / "cases.json"))
    parser.add_argument("--normalizers", default=str(directory / "normalizers.json"))
    parser.add_argument("--variables", help="JSON object containing fixture IDs and request bodies")
    parser.add_argument("--report", default="api-parity-report.json")
    parser.add_argument("--timeout", type=float, default=15.0)
    parser.add_argument("--all", action="store_true", help="Select all 79 endpoints, including writes and fixture-dependent cases")
    parser.add_argument("--case", action="append", default=[], help="Case id glob; may be repeated")
    parser.add_argument("--strict-skips", action="store_true", help="Treat missing fixture variables as failures")
    return parser.parse_args(argv)


def main(argv=None):
    arguments = parse_arguments(argv)
    manifest = load_json(arguments.cases)
    config = load_json(arguments.normalizers)
    report = run_comparison(manifest, config, arguments)
    report_path = Path(arguments.report)
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    summary = report["summary"]
    print("API parity: selected={selected} passed={passed} failed={failed} skipped={skipped}".format(**summary))
    print("Report: " + str(report_path.resolve()))
    return 1 if summary["failed"] else 0


if __name__ == "__main__":
    sys.exit(main())
