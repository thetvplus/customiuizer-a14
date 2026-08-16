#!/usr/bin/env python3
"""Offline ROM contract scanner.

Reads a JSON contract that declares classes, methods and fields a ROM must
provide for the module's hooks. Scans baksmali-style smali directories and
produces JSON/Markdown reports without sending any ROM data to the network.

Exit codes:
    0  all required contracts for supplied targets are present
    1  at least one required contract is missing
    2  input, schema or scan error
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any

REPO_ROOT = Path(__file__).resolve().parent.parent


def fail(message: str, code: int = 2) -> None:
    print(f"error: {message}", file=sys.stderr)
    sys.exit(code)


def validate_schema(contract: dict[str, Any], schema: dict[str, Any]) -> None:
    """Minimal structural validation of the contract against the schema."""
    if contract.get("schemaVersion") != schema.get("schemaVersion"):
        fail(f"unsupported schemaVersion: {contract.get('schemaVersion')!r}")
    for target in contract.get("targets", []):
        if not isinstance(target, dict):
            fail("each target must be an object")
        if not target.get("target"):
            fail("target name is required")
        for key in ("classes", "methods", "fields"):
            for item in target.get(key, []):
                if not isinstance(item, dict):
                    fail(f"{key} item must be an object in target {target['target']}")
                if "name" not in item:
                    fail(f"{key} item missing 'name' in target {target['target']}")


def read_smali_class(path: Path) -> tuple[str | None, set[str], set[str]]:
    """Return (class descriptor, method signatures, field signatures)."""
    text = path.read_text(encoding="utf-8")
    class_match = re.search(r"^\.class\s+.*?\s+(L[^;]+;)", text, re.MULTILINE)
    if not class_match:
        return None, set(), set()
    class_desc = class_match.group(1)

    methods: set[str] = set()
    for m in re.finditer(r"^\.method\s+(?:public\s+|private\s+|protected\s+|static\s+|final\s+|abstract\s+|synthetic\s+)*(.+?)\n", text, re.MULTILINE):
        sig = m.group(1).strip()
        if "(" in sig:
            methods.add(sig)

    fields: set[str] = set()
    for m in re.finditer(r"^\.field\s+(?:public\s+|private\s+|protected\s+|static\s+|final\s+|synthetic\s+)*([^\s:]+):([^\n]+)\n", text, re.MULTILINE):
        name = m.group(1).strip()
        ftype = m.group(2).strip()
        fields.add(f"{name}:{ftype}")

    return class_desc, methods, fields


def scan_target(target_name: str, roots: list[Path]) -> dict[str, dict[str, Any]]:
    """Index every .smali file under the given roots."""
    index: dict[str, dict[str, Any]] = {}
    for root in roots:
        if not root.exists():
            fail(f"smali root not found: {root}")
        for smali in root.rglob("*.smali"):
            class_desc, methods, fields = read_smali_class(smali)
            if class_desc is None:
                continue
            index[class_desc] = {"methods": methods, "fields": fields, "path": smali}
    return index


def status_name(code: int) -> str:
    return {
        0: "present",
        1: "missing",
        2: "alternative_matched",
        3: "optional_missing",
        4: "target_not_supplied",
        5: "invalid_contract",
    }[code]


def check_class(target_name: str, class_desc: str, index: dict[str, Any], supplied: bool, required: bool) -> dict[str, Any]:
    if not supplied:
        return {"target": target_name, "kind": "class", "name": class_desc, "status": "target_not_supplied"}
    if class_desc in index:
        return {"target": target_name, "kind": "class", "name": class_desc, "status": "present"}
    if required:
        return {"target": target_name, "kind": "class", "name": class_desc, "status": "missing"}
    return {"target": target_name, "kind": "class", "name": class_desc, "status": "optional_missing"}


def method_signature(method: dict[str, Any], class_desc: str) -> str:
    name = method["name"]
    descriptor = method.get("descriptor", "")
    return f"{class_desc}->{name}{descriptor}"


def check_method(target_name: str, class_desc: str, method: dict[str, Any], index: dict[str, Any], supplied: bool) -> dict[str, Any]:
    sig = method_signature(method, class_desc)
    if not supplied:
        return {"target": target_name, "kind": "method", "name": sig, "status": "target_not_supplied"}
    if class_desc not in index:
        return {"target": target_name, "kind": "method", "name": sig, "status": "missing"}
    name = method["name"]
    descriptor = method.get("descriptor", "")
    # Optional entries may omit the descriptor and match any overload of the name.
    if not descriptor:
        if any(m.startswith(f"{name}(") for m in index[class_desc]["methods"]):
            return {"target": target_name, "kind": "method", "name": sig, "status": "present"}
        if method.get("required", True):
            return {"target": target_name, "kind": "method", "name": sig, "status": "missing"}
        return {"target": target_name, "kind": "method", "name": sig, "status": "optional_missing"}
    expected = f"{name}{descriptor}"
    if expected in index[class_desc]["methods"]:
        return {"target": target_name, "kind": "method", "name": sig, "status": "present"}
    if "alternatives" in method:
        for alt in method["alternatives"]:
            alt_sig = f"{name}{alt}"
            if alt_sig in index[class_desc]["methods"]:
                return {"target": target_name, "kind": "method", "name": sig, "status": "alternative_matched", "matched": alt_sig}
    if method.get("required", True):
        return {"target": target_name, "kind": "method", "name": sig, "status": "missing"}
    return {"target": target_name, "kind": "method", "name": sig, "status": "optional_missing"}


def check_field(target_name: str, class_desc: str, field: dict[str, Any], index: dict[str, Any], supplied: bool) -> dict[str, Any]:
    expected = f"{field['name']}:{field.get('type', '')}"
    if not supplied:
        return {"target": target_name, "kind": "field", "name": expected, "status": "target_not_supplied"}
    if class_desc not in index:
        return {"target": target_name, "kind": "field", "name": expected, "status": "missing"}
    if expected in index[class_desc]["fields"]:
        return {"target": target_name, "kind": "field", "name": expected, "status": "present"}
    if field.get("required", True):
        return {"target": target_name, "kind": "field", "name": expected, "status": "missing"}
    return {"target": target_name, "kind": "field", "name": expected, "status": "optional_missing"}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--contract", required=True, type=Path, help="JSON contract file")
    parser.add_argument("--schema", type=Path, default=REPO_ROOT / "rom-contracts" / "schema.json", help="contract schema file")
    parser.add_argument("--target", action="append", metavar="NAME=PATH", help="smali root for a target, can be given multiple times")
    parser.add_argument("--output-json", type=Path, help="write JSON report to this file")
    parser.add_argument("--output-markdown", type=Path, help="write Markdown report to this file")
    args = parser.parse_args()

    if not args.contract.is_file():
        fail(f"contract file not found: {args.contract}")
    if not args.schema.is_file():
        fail(f"schema file not found: {args.schema}")

    try:
        schema = json.loads(args.schema.read_text(encoding="utf-8"))
        contract = json.loads(args.contract.read_text(encoding="utf-8"))
    except json.JSONDecodeError as e:
        fail(f"JSON parse error: {e}")

    validate_schema(contract, schema)

    targets: dict[str, list[Path]] = {}
    if args.target:
        for t in args.target:
            if "=" not in t:
                fail(f"invalid target format: {t}")
            name, path = t.split("=", 1)
            targets.setdefault(name, []).append(Path(path))

    all_results: list[dict[str, Any]] = []
    any_required_missing = False

    for target in contract.get("targets", []):
        target_name = target["target"]
        roots = targets.get(target_name, [])
        supplied = bool(roots)

        index: dict[str, Any] = {}
        if supplied:
            index = scan_target(target_name, roots)

        class_desc = target.get("class")
        if not class_desc:
            fail(f"target {target_name} missing 'class'")

        anyof = target.get("anyOf")
        class_present = any(c in index for c in ([class_desc] if anyof is None else anyof))

        if not class_present:
            if anyof:
                for c in anyof:
                    all_results.append(check_class(target_name, c, index, supplied, required=False))
            else:
                all_results.append(check_class(target_name, class_desc, index, supplied, required=True))
            any_required_missing = any_required_missing or (not anyof)
        else:
            actual_class = next(c for c in ([class_desc] if anyof is None else anyof) if c in index)
            all_results.append({"target": target_name, "kind": "class", "name": actual_class, "status": "present"})

            for method in target.get("methods", []):
                result = check_method(target_name, actual_class, method, index, supplied)
                all_results.append(result)
                if result["status"] in ("missing", "target_not_supplied") and method.get("required", True):
                    any_required_missing = True

            for field in target.get("fields", []):
                result = check_field(target_name, actual_class, field, index, supplied)
                all_results.append(result)
                if result["status"] in ("missing", "target_not_supplied") and field.get("required", True):
                    any_required_missing = True

    stats = {
        "total": len(all_results),
        "present": sum(1 for r in all_results if r["status"] == "present"),
        "missing": sum(1 for r in all_results if r["status"] == "missing"),
        "alternative_matched": sum(1 for r in all_results if r["status"] == "alternative_matched"),
        "optional_missing": sum(1 for r in all_results if r["status"] == "optional_missing"),
        "target_not_supplied": sum(1 for r in all_results if r["status"] == "target_not_supplied"),
        "invalid_contract": sum(1 for r in all_results if r["status"] == "invalid_contract"),
    }

    report = {
        "schemaVersion": contract.get("schemaVersion"),
        "contractName": contract.get("contractName"),
        "exit_code": 1 if any_required_missing else 0,
        "statistics": stats,
        "results": all_results,
    }

    if args.output_json:
        args.output_json.write_text(json.dumps(report, indent=2), encoding="utf-8")

    if args.output_markdown:
        lines = [
            f"# ROM Contract Scan: {contract.get('contractName', 'unnamed')}",
            "",
            f"- Total: {stats['total']}",
            f"- Present: {stats['present']}",
            f"- Missing: {stats['missing']}",
            f"- Alternative matched: {stats['alternative_matched']}",
            f"- Optional missing: {stats['optional_missing']}",
            f"- Target not supplied: {stats['target_not_supplied']}",
            f"- Exit code: {report['exit_code']}",
            "",
            "| Target | Kind | Name | Status |",
            "| --- | --- | --- | --- |",
        ]
        for r in all_results:
            name = r.get("matched", r["name"])
            lines.append(f"| {r['target']} | {r['kind']} | `{name}` | {r['status']} |")
        args.output_markdown.write_text("\n".join(lines), encoding="utf-8")

    for r in all_results:
        if r["status"] in ("missing", "alternative_matched", "optional_missing", "target_not_supplied"):
            print(f"{r['target']} {r['kind']} {r.get('matched', r['name'])}: {r['status']}")

    if any_required_missing:
        return 1
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except SystemExit as e:
        raise
    except Exception as e:
        fail(f"scan failed: {e}", code=2)
