#!/usr/bin/env python3
"""Read canonical auxiliary data without dumping whole files into Agent context.

No filters means summary only. Filtered queries return at most 10 projected
records by default; --offset/--limit page results and --full includes evidence.
Existing data queries only read their canonical files. `context` incrementally
reuses source facts in the existing external ROM Catalog; DEX access is explicit.
"""
from __future__ import annotations

import argparse
import json
import sqlite3
from collections import Counter
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]

# Paths and projections, not another copy of the underlying records or schema.
DATASETS = {
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
    if args.dataset == "context":
        try:
            from .agent_context import query as context_query
            from .rom_catalog import CatalogError
        except ImportError:
            from agent_context import query as context_query
            from rom_catalog import CatalogError
        try:
            return context_query(args)
        except CatalogError as error:
            raise ValueError(str(error)) from error
    spec = DATASETS[args.dataset]
    path = args.repo_root / spec["path"]
    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, dict):
        raise ValueError(f"{spec['path']}: expected an object")
    records = data.get(spec["records"])
    if not isinstance(records, list) or any(not isinstance(row, dict) for row in records):
        raise ValueError(f"{spec['path']}: expected a list of records at {spec['records']}")

    # Reject inapplicable filters rather than quietly returning a false match.
    if args.key and args.dataset != "matrix":
        raise ValueError("--key applies to matrix")
    if args.feature and args.dataset != "matrix":
        raise ValueError("--feature applies to matrix")
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
    filtered = any((args.key, args.feature, args.target, args.source, args.contains))
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
    return result


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("dataset", choices=(*DATASETS, "context"))
    parser.add_argument("--repo-root", type=Path, default=ROOT)
    parser.add_argument("--index-dir", type=Path, help="context: reuse the external ROM Catalog SQLite")
    parser.add_argument("--device", help="context: explicit metadata device (e.g. munch); default fuxi CN")
    parser.add_argument("--region", help="context: explicit metadata region (CN/TW/ID)")
    parser.add_argument("--refresh", action="store_true", help="context: explicitly rebuild source facts; never scans ROM")
    dex = parser.add_mutually_exclusive_group()
    dex.add_argument("--dex-class", help="context: explicitly fetch this exact DEX class after selecting one ROM")
    dex.add_argument("--dex-method", help="context: explicitly fetch this exact DEX method after selecting one ROM")
    parser.add_argument("--dex-calls", choices=("callers", "callees"), help="context: add bounded direct invoke evidence for --dex-method")
    parser.add_argument("--key", help="exact storage or runtime preference key")
    parser.add_argument("--feature", help="exact matrix featureIdName/featureIdInt")
    parser.add_argument("--target", help="exact target (matrix/rom)")
    parser.add_argument("--source", help="source path substring; matrix uses its installer name")
    parser.add_argument("--contains", help="case-insensitive substring anywhere in a record")
    parser.add_argument("--summary", action="store_true", help="counts only, including after filtering")
    projection = parser.add_mutually_exclusive_group()
    projection.add_argument("--fields", help="comma-separated original field names")
    projection.add_argument("--full", action="store_true", help="include all fields of the bounded result page")
    parser.add_argument("--offset", type=int, default=0)
    parser.add_argument("--limit", type=int, help="page size, 1..100 (default: 10; context: 3)")
    args = parser.parse_args(argv)
    if args.dex_calls and not args.dex_method:
        parser.error("--dex-calls requires --dex-method")
    if args.dataset != "context" and any((args.index_dir, args.device, args.region, args.refresh, args.dex_class, args.dex_method, args.dex_calls)):
        parser.error("--index-dir/--device/--region/--dex-class/--dex-method apply only to context")
    if args.dataset == "context":
        try:
            from .rom_catalog import default_index
        except ImportError:
            from rom_catalog import default_index
        args.index_dir = args.index_dir or default_index()
    args.limit = args.limit if args.limit is not None else (3 if args.dataset == "context" else 10)
    if args.offset < 0 or not 1 <= args.limit <= 100:
        parser.error("--offset must be non-negative and --limit must be 1..100")
    try:
        result = query(args)
    except (OSError, ValueError, TypeError, KeyError, sqlite3.Error) as error:
        parser.exit(2, f"query: {error}\n")
    print(json.dumps(result, ensure_ascii=False, separators=(",", ":")) if args.dataset == "context" else json.dumps(result, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
