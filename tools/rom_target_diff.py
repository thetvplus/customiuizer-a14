#!/usr/bin/env python3
"""Diff two feature target matrices (e.g. A14 vs A15, or baseline vs inventory).

Produces a classified change matrix: UNCHANGED, ADDED, REMOVED, RENAMED, MOVED,
SIGNATURE_CHANGED, PROCESS_CHANGED, DEXKIT_REQUIRED, UNKNOWN.
"""

from __future__ import annotations

import argparse
import csv
import json
import re
import sys
from pathlib import Path
from typing import Any

REPO_ROOT = Path(__file__).resolve().parent.parent


def fail(message: str, code: int = 2) -> None:
    print(f"rom_target_diff: {message}", file=sys.stderr)
    sys.exit(code)


def load_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def load_csv(path: Path) -> list[dict[str, Any]]:
    with path.open("r", encoding="utf-8") as f:
        return list(csv.DictReader(f))


def parse_smali(ref: str) -> dict[str, str]:
    """Parse a smali-like reference into class/member/descriptor components."""
    ref = ref.strip()
    result = {
        "class": "",
        "member": "",
        "descriptor": "",
        "params": "",
        "return": "",
        "kind": "",
    }
    if not ref:
        return result
    m = re.match(r"^L([^;]+);->([^(]+)\(([^)]*)\)(.+)$", ref)
    if m:
        result["class"] = m.group(1)
        result["member"] = m.group(2)
        result["params"] = m.group(3)
        result["return"] = m.group(4)
        result["descriptor"] = f"({m.group(3)}){m.group(4)}"
        result["kind"] = "method"
        return result
    m = re.match(r"^L([^;]+);->([^:]+):(.+)$", ref)
    if m:
        result["class"] = m.group(1)
        result["member"] = m.group(2)
        result["descriptor"] = m.group(3)
        result["return"] = m.group(3)
        result["kind"] = "field"
        return result
    m = re.match(r"^L([^;]+);$", ref)
    if m:
        result["class"] = m.group(1)
        result["kind"] = "class"
    return result


def normalize_row(row: Any) -> dict[str, Any]:
    """Accept either a feature id string or a row dict."""
    if isinstance(row, str):
        return {"featureId": row}
    if not isinstance(row, dict):
        return {}
    return row


def diff_inputs(old: list[Any], new: list[Any]) -> dict[str, Any]:
    """Compare two lists of target rows and classify changes."""
    old_rows = [normalize_row(r) for r in old]
    new_rows = [normalize_row(r) for r in new]

    old_map = {r.get("featureId", f"__{i}"): r for i, r in enumerate(old_rows)}
    new_map = {r.get("featureId", f"__{i}"): r for i, r in enumerate(new_rows)}

    categories = (
        "UNCHANGED",
        "ADDED",
        "REMOVED",
        "RENAMED",
        "MOVED",
        "SIGNATURE_CHANGED",
        "PROCESS_CHANGED",
        "DEXKIT_REQUIRED",
        "UNKNOWN",
    )
    summary = {c: 0 for c in categories}
    changes: list[dict[str, Any]] = []

    for fid in sorted(set(old_map) | set(new_map)):
        old_r = old_map.get(fid)
        new_r = new_map.get(fid)

        if not old_r:
            summary["ADDED"] += 1
            changes.append({"featureId": fid, "changeType": "ADDED", "notes": "New target"})
            continue
        if not new_r:
            summary["REMOVED"] += 1
            changes.append({"featureId": fid, "changeType": "REMOVED", "notes": "Target no longer tracked"})
            continue

        # If the old record is only a feature id, treat existence as unchanged.
        old_a14 = old_r.get("a14_target", "")
        new_a14 = new_r.get("a14_target", "")
        old_a15 = old_r.get("a15_target", "")
        new_a15 = new_r.get("a15_target", "")
        old_proc = old_r.get("targetProcess", "")
        new_proc = new_r.get("targetProcess", "")

        if not old_a14 and not new_a14:
            summary["UNCHANGED"] += 1
            changes.append({"featureId": fid, "changeType": "UNCHANGED", "notes": ""})
            continue

        # A15 mapping or process information missing means we need runtime discovery.
        if new_a15 == "" and old_a15 != "":
            summary["DEXKIT_REQUIRED"] += 1
            changes.append({
                "featureId": fid,
                "changeType": "DEXKIT_REQUIRED",
                "notes": "A15 target mapping lost; DexKit or manual dump required",
            })
            continue

        if new_proc == "" and old_proc != "":
            summary["DEXKIT_REQUIRED"] += 1
            changes.append({
                "featureId": fid,
                "changeType": "DEXKIT_REQUIRED",
                "notes": "Target process missing in new matrix; DexKit or manual dump required",
            })
            continue

        # If the reference did not exist in the old matrix, just report unchanged (presence).
        if not old_a14:
            summary["UNCHANGED"] += 1
            changes.append({"featureId": fid, "changeType": "UNCHANGED", "notes": ""})
            continue

        old_sig = parse_smali(old_a14)
        new_sig = parse_smali(new_a14)

        if old_a14 != new_a14 and old_sig["class"] and new_sig["class"]:
            class_changed = old_sig["class"] != new_sig["class"]
            member_changed = old_sig["member"] != new_sig["member"]
            desc_changed = old_sig["descriptor"] != new_sig["descriptor"]

            if class_changed and not member_changed and not desc_changed:
                ct = "MOVED"
                notes = f"Class moved from {old_sig['class']} to {new_sig['class']}"
            elif not class_changed and member_changed and not desc_changed:
                ct = "RENAMED"
                notes = f"Member renamed from {old_sig['member']} to {new_sig['member']}"
            elif not class_changed and not member_changed and desc_changed:
                ct = "SIGNATURE_CHANGED"
                notes = f"Descriptor changed from {old_sig['descriptor']} to {new_sig['descriptor']}"
            else:
                ct = "SIGNATURE_CHANGED"
                notes = f"Reference changed from {old_a14} to {new_a14}"

            summary[ct] += 1
            changes.append({"featureId": fid, "changeType": ct, "notes": notes})
            continue

        if old_a14 == new_a14:
            if old_proc != new_proc and new_proc:
                summary["PROCESS_CHANGED"] += 1
                changes.append({
                    "featureId": fid,
                    "changeType": "PROCESS_CHANGED",
                    "notes": f"Process changed from {old_proc} to {new_proc}",
                })
                continue

        # Catch-all for any remaining differences.
        if old_a14 == new_a14:
            summary["UNCHANGED"] += 1
            changes.append({"featureId": fid, "changeType": "UNCHANGED", "notes": ""})
        else:
            summary["UNKNOWN"] += 1
            changes.append({
                "featureId": fid,
                "changeType": "UNKNOWN",
                "notes": f"Cannot classify change from {old_a14} to {new_a14}",
            })

    summary["total"] = len(changes)
    return {"summary": summary, "changes": changes}


def generate_markdown(report: dict[str, Any], old_path: Path, new_path: Path) -> str:
    lines = [
        "# ROM Target Diff Matrix",
        "",
        f"Old: `{old_path}`",
        f"New: `{new_path}`",
        "",
        "## Summary",
        "",
    ]
    for key, value in report["summary"].items():
        lines.append(f"- **{key}**: {value}")
    lines.extend(["", "## Changes", "", "| Feature | Change Type | Notes |", "|---------|-------------|-------|"])
    for c in report["changes"]:
        lines.append(f"| {c['featureId']} | {c['changeType']} | {c.get('notes', '')} |")
    return "\n".join(lines) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("old", type=Path, help="old target JSON or CSV")
    parser.add_argument("new", type=Path, help="new target JSON or CSV")
    parser.add_argument("--output-json", type=Path, help="write JSON report")
    parser.add_argument("--output-markdown", type=Path, help="write Markdown report")
    args = parser.parse_args()

    if not args.old.is_file():
        fail(f"old file not found: {args.old}")
    if not args.new.is_file():
        fail(f"new file not found: {args.new}")

    old = load_json(args.old) if args.old.suffix == ".json" else load_csv(args.old)
    new = load_json(args.new) if args.new.suffix == ".json" else load_csv(args.new)

    report = diff_inputs(old, new)

    if args.output_json:
        args.output_json.parent.mkdir(parents=True, exist_ok=True)
        args.output_json.write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")
        print(f"wrote JSON: {args.output_json}")

    if args.output_markdown:
        args.output_markdown.parent.mkdir(parents=True, exist_ok=True)
        args.output_markdown.write_text(generate_markdown(report, args.old, args.new), encoding="utf-8")
        print(f"wrote Markdown: {args.output_markdown}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
