#!/usr/bin/env python3
"""Generate an A14 APK size baseline or compare two existing baselines.

Usage:
    python tools/apk_size_report.py path/to/app.apk --out path/to/baseline.json
    python tools/apk_size_report.py before.json --compare after.json
"""

import argparse
import hashlib
import json
import sys
import zipfile
from pathlib import Path


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(8192), b""):
            h.update(chunk)
    return h.hexdigest()


def bucket(name: str) -> str:
    if name.startswith("classes") and name.endswith(".dex"):
        return "dex"
    if name.startswith("lib/"):
        return "lib"
    if name.startswith("res/"):
        return "res"
    if name == "resources.arsc":
        return "arsc"
    if name.startswith("assets/"):
        return "assets"
    if name.startswith("META-INF/"):
        return "meta"
    if name == "AndroidManifest.xml":
        return "manifest"
    return "other"


def report(apk_path: Path) -> dict:
    with zipfile.ZipFile(apk_path, "r") as zf:
        files = [
            {
                "name": info.filename,
                "uncompressedSize": info.file_size,
                "compressedSize": info.compress_size,
                "bucket": bucket(info.filename),
            }
            for info in zf.infolist()
            if not info.is_dir()
        ]

    uncompressed_total = sum(f["uncompressedSize"] for f in files)
    compressed_total = sum(f["compressedSize"] for f in files)

    def sums(b: str) -> tuple[int, int]:
        u = sum(f["uncompressedSize"] for f in files if f["bucket"] == b)
        c = sum(f["compressedSize"] for f in files if f["bucket"] == b)
        return u, c

    dex_u, dex_c = sums("dex")
    arsc_u, arsc_c = sums("arsc")
    lib_u, lib_c = sums("lib")
    res_u, res_c = sums("res")
    assets_u, assets_c = sums("assets")
    meta_u, meta_c = sums("meta")
    manifest_u, manifest_c = sums("manifest")

    return {
        "apkPath": str(apk_path),
        "sha256": sha256(apk_path),
        "apkFileBytes": apk_path.stat().st_size,
        "zipEntriesUncompressedBytes": uncompressed_total,
        "zipEntriesCompressedBytes": compressed_total,
        "fileCount": len(files),
        "dexUncompressedBytes": dex_u,
        "dexCompressedBytes": dex_c,
        "resourcesArscUncompressedBytes": arsc_u,
        "resourcesArscCompressedBytes": arsc_c,
        "libUncompressedBytes": lib_u,
        "libCompressedBytes": lib_c,
        "resUncompressedBytes": res_u,
        "resCompressedBytes": res_c,
        "assetsUncompressedBytes": assets_u,
        "assetsCompressedBytes": assets_c,
        "metaUncompressedBytes": meta_u,
        "metaCompressedBytes": meta_c,
        "manifestUncompressedBytes": manifest_u,
        "manifestCompressedBytes": manifest_c,
        "files": sorted(files, key=lambda f: (-f["compressedSize"], f["name"])),
    }


def fmt(value: int) -> str:
    sign = "+" if value > 0 else ""
    return f"{sign}{value:,} bytes ({sign}{value / 1024:.1f} KB)"


def compare(before_path: Path, after_path: Path, apk_budget_kb: float, dex_budget_kb: float) -> int:
    """Keep the historical absolute-delta budgets, including size decreases."""
    before = json.loads(before_path.read_text(encoding="utf-8"))
    after = json.loads(after_path.read_text(encoding="utf-8"))
    metrics = (
        ("Real APK file size", "apkFileBytes"),
        ("ZIP compressed total", "zipEntriesCompressedBytes"),
        ("ZIP uncompressed total", "zipEntriesUncompressedBytes"),
        ("classes*.dex (compressed)", "dexCompressedBytes"),
        ("classes*.dex (uncompressed)", "dexUncompressedBytes"),
        ("resources.arsc (compressed)", "resourcesArscCompressedBytes"),
        ("lib/ (compressed)", "libCompressedBytes"),
    )
    print(f"APK: {before['apkPath']} -> {after['apkPath']}")
    for label, field in metrics:
        print(f"{label}: {fmt(after[field] - before[field])}")

    before_files = {entry["name"]: entry["compressedSize"] for entry in before["files"]}
    after_files = {entry["name"]: entry["compressedSize"] for entry in after["files"]}
    changes = [
        (name, after_files.get(name, 0) - before_files.get(name, 0))
        for name in before_files.keys() | after_files.keys()
        if after_files.get(name, 0) != before_files.get(name, 0)
    ]
    changes.sort(key=lambda change: (-abs(change[1]), change[0]))
    print("\nTop file changes (compressed):")
    for name, delta in changes[:20]:
        print(f"  {name}: {fmt(delta)}")

    failed = False
    for label, field, budget in (
        ("APK file", "apkFileBytes", apk_budget_kb),
        ("DEX (compressed)", "dexCompressedBytes", dex_budget_kb),
    ):
        delta = after[field] - before[field]
        if abs(delta) > budget * 1024:
            print(f"FAIL: {label} delta {delta / 1024:.1f} KB exceeds budget {budget} KB")
            failed = True
    return 1 if failed else 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", help="APK path, or the before baseline JSON with --compare.")
    parser.add_argument("--out", help="Path to write the JSON baseline.")
    parser.add_argument("--compare", metavar="AFTER_JSON", type=Path, help="Compare two existing baseline JSON files.")
    parser.add_argument("--apk-budget-kb", type=float, default=100.0, help="Absolute APK delta budget in KB (compare only).")
    parser.add_argument("--dex-budget-kb", type=float, default=50.0, help="Absolute compressed DEX delta budget in KB (compare only).")
    args = parser.parse_args(argv)

    if args.compare is not None:
        if args.out:
            parser.error("--out applies to baseline generation, not --compare")
        return compare(Path(args.apk), args.compare, args.apk_budget_kb, args.dex_budget_kb)

    apk_path = Path(args.apk)
    if not apk_path.is_file():
        print(f"APK not found: {apk_path}", file=sys.stderr)
        return 1

    data = report(apk_path)
    if args.out:
        with Path(args.out).open("w", encoding="utf-8", newline="\n") as f:
            json.dump(data, f, indent=2, ensure_ascii=False)
            f.write("\n")
    else:
        print(json.dumps(data, indent=2, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    sys.exit(main())
