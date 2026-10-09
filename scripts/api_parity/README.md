# AgentGate API parity runner

This standard-library-only tool sends the same ordered cases to the Python and
Java backends, normalizes explicitly configured nondeterministic values, and
writes a field-level JSON diff report. `cases.json` is the inventory of all 79
Python HTTP endpoints as of 2026-10-08.

## Safe smoke comparison

Start both backends against the same read-only fixture data, then run:

```bash
python3 scripts/api_parity/compare_backends.py \
  --python-url http://127.0.0.1:8000 \
  --java-url http://127.0.0.1:8080/race-api \
  --report target/api-parity-smoke.json
```

The default set contains only fixture-free read endpoints. The command exits
nonzero when a status code or normalized response field differs.

## Full inventory comparison

Write operations must run against two independently restored, equivalent
database snapshots. Do not point both backends at the production database or
at the same writable database: the first pass would change the second pass's
baseline.

Provide fixture identifiers and request bodies in a local JSON file, then run:

```bash
python3 scripts/api_parity/compare_backends.py \
  --python-url http://127.0.0.1:8000 \
  --java-url http://127.0.0.1:8080/race-api \
  --variables /path/to/parity-variables.json \
  --all --strict-skips \
  --report target/api-parity-full.json
```

Variables are backend-local during execution. For example, `dataset-create`
extracts each backend's returned ID into its own `dataset_id`, so later cases
address the corresponding record rather than reusing the Python ID on Java.
An entire JSON request can be supplied through a `*_body` variable. Import
endpoints accept a prebuilt multipart payload as base64 plus its exact content
type (including the boundary).

Useful narrower runs:

```bash
# One case or a glob of cases
python3 scripts/api_parity/compare_backends.py ... --all --case 'dataset-*'

# Missing fixture variables are reported as SKIPPED unless strict mode is set
python3 scripts/api_parity/compare_backends.py ... --all
```

`normalizers.json` intentionally has no global UUID or timestamp suppression.
Each case opts in only where the two isolated executions legitimately generate
different values. Add ignored paths or patterns only after confirming that the
field is nondeterministic and not part of the API contract.
