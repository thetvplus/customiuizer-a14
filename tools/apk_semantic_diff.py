#!/usr/bin/env python3
"""Content-level APK comparison independent of ZIP ordering and timestamps."""
from __future__ import annotations

import argparse
import hashlib
import json
import sys
import zipfile
from collections import defaultdict
from pathlib import Path


SIGNATURE_SUFFIXES = (".RSA", ".DSA", ".EC", ".SF", ".MF")


def ignored(name: str) -> bool:
    upper = name.upper()
    return upper.startswith("META-INF/") and upper.endswith(SIGNATURE_SUFFIXES)


def category(name: str) -> str:
    if name.endswith(".dex"):
        return "dex"
    if name.startswith("lib/"):
        return "native"
    if name.startswith("res/") or name == "resources.arsc":
        return "resources"
    if name.startswith("assets/"):
        return "assets"
    if name == "AndroidManifest.xml":
        return "manifest"
    return "other"


def inspect(path: Path) -> dict:
    entries = {}
    totals = defaultdict(int)
    with zipfile.ZipFile(path) as zf:
        for info in zf.infolist():
            if info.is_dir() or ignored(info.filename):
                continue
            data = zf.read(info.filename)
            digest = hashlib.sha256(data).hexdigest()
            cat = category(info.filename)
            entries[info.filename] = {
                "sha256": digest,
                "size": len(data),
                "compressed": info.compress_size,
                "category": cat,
            }
            totals[cat] += len(data)
    return {"path": str(path), "entries": entries, "totals": dict(totals)}


def compare(a: dict, b: dict) -> dict:
    ae, be = a["entries"], b["entries"]
    added = sorted(be.keys() - ae.keys())
    removed = sorted(ae.keys() - be.keys())
    changed = sorted(k for k in ae.keys() & be.keys() if ae[k]["sha256"] != be[k]["sha256"])
    categories = sorted(set(a["totals"]) | set(b["totals"]))
    delta = {c: b["totals"].get(c, 0) - a["totals"].get(c, 0) for c in categories}
    return {
        "added": added,
        "removed": removed,
        "changed": changed,
        "sizeDelta": delta,
        "oldEntryCount": len(ae),
        "newEntryCount": len(be),
        "normalizedEqual": not (added or removed or changed),
    }


def main(argv=None) -> int:
    p = argparse.ArgumentParser()
    p.add_argument("old")
    p.add_argument("new")
    p.add_argument("--json-output")
    p.add_argument("--max-total-growth", type=int)
    p.add_argument("--max-dex-growth", type=int)
    p.add_argument("--forbid-native-added", action="store_true")
    p.add_argument("--require-reproducible", action="store_true")
    args = p.parse_args(argv)

    old = inspect(Path(args.old))
    new = inspect(Path(args.new))
    result = compare(old, new)
    result["old"] = old["path"]
    result["new"] = new["path"]

    print(
        f"APK semantic diff: +{len(result['added'])} -{len(result['removed'])} "
        f"~{len(result['changed'])}; normalizedEqual={result['normalizedEqual']}"
    )
    for cat, value in result["sizeDelta"].items():
        print(f"  {cat}: {value:+d} bytes")
    for name in result["added"][:30]:
        print(f"  ADD {name}")
    for name in result["removed"][:30]:
        print(f"  DEL {name}")
    for name in result["changed"][:30]:
        print(f"  CHG {name}")

    if args.json_output:
        Path(args.json_output).write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")

    failures = []
    total_growth = sum(result["sizeDelta"].values())
    if args.max_total_growth is not None and total_growth > args.max_total_growth:
        failures.append(f"total growth {total_growth} > {args.max_total_growth}")
    dex_growth = result["sizeDelta"].get("dex", 0)
    if args.max_dex_growth is not None and dex_growth > args.max_dex_growth:
        failures.append(f"dex growth {dex_growth} > {args.max_dex_growth}")
    if args.forbid_native_added:
        native_added = [n for n in result["added"] if n.startswith("lib/")]
        if native_added:
            failures.append(f"new native entries: {native_added}")
    if args.require_reproducible and not result["normalizedEqual"]:
        failures.append("normalized APK contents differ")

    if failures:
        for f in failures:
            print(f"FAIL: {f}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
