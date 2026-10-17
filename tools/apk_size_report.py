#!/usr/bin/env python3
"""Generate a machine-readable A14 APK size baseline with compressed/uncompressed attribution.

Usage:
    python tools/apk_size_report.py path/to/app.apk --out path/to/baseline.json
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


def main() -> int:
    parser = argparse.ArgumentParser(description="Generate APK size baseline JSON.")
    parser.add_argument("apk", help="Path to the APK file.")
    parser.add_argument("--out", help="Path to write the JSON baseline.")
    args = parser.parse_args()

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
