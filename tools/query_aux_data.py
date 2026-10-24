#!/usr/bin/env python3
"""Read canonical auxiliary data without dumping whole files into Agent context.

No filters means summary only. Filtered queries return at most 10 projected
records by default; --offset/--limit page results and --full includes evidence.
This tool reads existing files only: no index, cache, generation or ROM access.
"""
from __future__ import annotations

import argparse
import json
from collections import Counter
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]

# Paths and projections, not another copy of the underlying records or schema.
DATASETS = {
    "semantics": {
        "path": "feature-semantics/a14.json", "records": "entries",
        "evidence": "source inventory; confidence is per record, not device acceptance",
        "fields": ("featureId", "preferenceKeys", "targetPackage", "runtimeReadMode",
                   "restartTarget", "hotReloadable", "confidence"),
        "facets": ("confidence", "runtimeReadMode", "restartTarget"),
    },
    "matrix": {
        "path": "docs/rom-intelligence/A14_PROCESS_MATRIX.json", "records": "features",
        "evidence": "generated static routing view; not ROM coverage",
        "fields": ("featureIdInt", "featureIdName", "preferenceKey", "target", "phase",
                   "allowedProcess"),
        "facets": ("target", "phase", "installer"),
    },
    "rom": {
        "path": "rom-contracts/hyperos1-a14-core.json", "records": "targets",
        "evidence": "partial source contract; query does not validate a ROM",
        "fields": ("target", "class", "sourceFile", "sourceHookFunction"),
        "facets": ("target",),
    },
    "hazards": {
        "path": "tools/SOURCE_HAZARD_BASELINE.json", "records": "findings",
        "evidence": "stored baseline; not a fresh scan or acceptance of new risk",
        "fields": ("fingerprint", "rule", "path", "line", "disposition"),
        "facets": ("rule", "disposition"),
    },
}


def canonical_key(value: str) -> str:
    return value.removeprefix("pref_key_")


def query(args: argparse.Namespace) -> dict[str, Any]:
    spec = DATASETS[args.dataset]
    path = args.repo_root / spec["path"]
    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, dict):
        raise ValueError(f"{spec['path']}: expected an object")
    records = data.get(spec["records"])
    if not isinstance(records, list) or any(not isinstance(row, dict) for row in records):
        raise ValueError(f"{spec['path']}: expected a list of records at {spec['records']}")

    # Reject inapplicable filters rather than quietly returning a false match.
    if args.key and args.dataset not in ("semantics", "matrix"):
        raise ValueError("--key applies to semantics or matrix")
    if args.feature and args.dataset not in ("semantics", "matrix"):
        raise ValueError("--feature applies to semantics or matrix (their ID namespaces differ)")
    if args.confidence and args.dataset != "semantics":
        raise ValueError("--confidence applies to semantics")
    if args.target and args.dataset == "hazards":
        raise ValueError("--target does not apply to hazards; use --source or --contains")

    def matches(row: dict[str, Any]) -> bool:
        if args.key:
            keys = row.get("preferenceKeys", [row.get("preferenceKey", "")])
            if canonical_key(args.key) not in [canonical_key(k) for k in keys]:
                return False
        if args.feature and args.feature not in [str(row.get(k, "")) for k in
                                                ("featureId", "featureIdName", "featureIdInt")]:
            return False
        if args.target and args.target != row.get("targetPackage", row.get("target")):
            return False
        if args.confidence and args.confidence != row.get("confidence"):
            return False
        if args.source and args.source.casefold() not in str(
                row.get("sourceFile", row.get("path", row.get("installer", "")))).casefold():
            return False
        if args.contains and args.contains.casefold() not in json.dumps(row, ensure_ascii=False).casefold():
            return False
        return True

    selected = [row for row in records if matches(row)]
    fields = args.fields.split(",") if args.fields else spec["fields"]
    if args.fields:
        available = {key for row in records for key in row}
        unknown = sorted(set(fields) - available)
        if unknown:
            raise ValueError("unknown fields: " + ", ".join(unknown))
    filtered = any((args.key, args.feature, args.target, args.source, args.contains, args.confidence))
    summary = args.summary or not (filtered or args.full or args.fields)
    page = [] if summary else selected[args.offset:args.offset + args.limit]
    result = {
        "dataset": args.dataset, "source": spec["path"], "evidence": spec["evidence"],
        "total": len(records), "matched": len(selected),
        "counts": {field: dict(sorted(Counter(str(row.get(field, "UNKNOWN")) for row in selected).items()))
                   for field in spec["facets"]},
        "offset": args.offset, "returned": len(page),
        "nextOffset": args.offset + len(page) if not summary and args.offset + len(page) < len(selected) else None,
        "records": page if args.full else [{key: row[key] for key in fields if key in row} for row in page],
    }
    if args.dataset == "semantics":
        result["exclusions"] = [ex for ex in data.get("exclusions", [])
                                if args.key and canonical_key(ex["preferenceKey"]) == canonical_key(args.key)]
    return result


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("dataset", choices=DATASETS)
    parser.add_argument("--repo-root", type=Path, default=ROOT)
    parser.add_argument("--key", help="exact storage or runtime preference key")
    parser.add_argument("--feature", help="exact semantics featureId or matrix featureIdName/featureIdInt")
    parser.add_argument("--target", help="exact targetPackage (semantics) or target (matrix/rom)")
    parser.add_argument("--source", help="source path substring; matrix uses its installer name")
    parser.add_argument("--contains", help="case-insensitive substring anywhere in a record")
    parser.add_argument("--confidence", choices=("UNKNOWN", "INFERRED", "CODE_VERIFIED", "DEVICE_VERIFIED"))
    parser.add_argument("--summary", action="store_true", help="counts only, including after filtering")
    projection = parser.add_mutually_exclusive_group()
    projection.add_argument("--fields", help="comma-separated original field names")
    projection.add_argument("--full", action="store_true", help="include all fields of the bounded result page")
    parser.add_argument("--offset", type=int, default=0)
    parser.add_argument("--limit", type=int, default=10, help="page size, 1..100 (default: 10)")
    args = parser.parse_args(argv)
    if args.offset < 0 or not 1 <= args.limit <= 100:
        parser.error("--offset must be non-negative and --limit must be 1..100")
    try:
        result = query(args)
    except (OSError, ValueError, TypeError, KeyError) as error:
        parser.exit(2, f"query: {error}\n")
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
