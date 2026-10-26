"""Validate named design DTO cases; this is not runtime acceptance evidence.

Run from any directory: python scripts/contract-checks/check-outpatient-scheduling.py
Requires jsonschema (Draft 2020-12).
"""
import json
from pathlib import Path
from jsonschema import Draft202012Validator, FormatChecker

base = Path(__file__).resolve().parents[2] / "docs/plans/contracts"
schema = json.loads((base / "outpatient-scheduling-v2.schema.json").read_text(encoding="utf-8"))
examples = json.loads((base / "outpatient-scheduling-v2.examples.json").read_text(encoding="utf-8"))
Draft202012Validator.check_schema(schema)
failures = []
for case in examples["schemaCases"]:
    selected = {"$schema": schema["$schema"], "$defs": schema["$defs"], "$ref": "#/$defs/" + case["definition"]}
    valid = Draft202012Validator(selected, format_checker=FormatChecker()).is_valid(case["payload"])
    if valid != case["schemaValid"]:
        failures.append(case["id"])
if failures:
    raise SystemExit("Unexpected schema results: " + ", ".join(failures))
print(f"Passed {len(examples['schemaCases'])} cases across {len(schema['$defs'])} definitions")
