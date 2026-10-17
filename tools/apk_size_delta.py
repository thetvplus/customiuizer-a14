#!/usr/bin/env python3
"""Compute an APK size delta report from explicit manifest inputs.

This tool is purely computational. It does not build APKs, check out commits,
modify the worktree, or require repository-side generated artifacts.

Usage:
    python tools/apk_size_delta.py \
        --inputs-manifest path/to/manifest.json \
        --baseline-commit 55fc2a21d0e96f9ef643f53fcc9b74374bd959db \
        --current-commit 1856c4e229213dfae47ff575aee446ce6a7b5f22 \
        --gradle-file app/build.gradle.kts \
        --out-json path/to/report.json \
        --out-md path/to/report.md

All input and output paths are explicit. The manifest is the single source of
truth for which variants and measurement files are compared.
"""

from __future__ import annotations

import argparse
import copy
import json
import re
import sys
from pathlib import Path

DELTA_FIELDS = [
    "apkFileBytes",
    "zipEntriesUncompressedBytes",
    "zipEntriesCompressedBytes",
    "fileCount",
    "dexUncompressedBytes",
    "dexCompressedBytes",
    "resourcesArscUncompressedBytes",
    "resourcesArscCompressedBytes",
    "libUncompressedBytes",
    "libCompressedBytes",
    "resUncompressedBytes",
    "resCompressedBytes",
    "assetsUncompressedBytes",
    "assetsCompressedBytes",
    "metaUncompressedBytes",
    "metaCompressedBytes",
    "manifestUncompressedBytes",
    "manifestCompressedBytes",
]

REQUIRED_MEASUREMENT_FIELDS = {
    "variant",
    "sha256",
    "apkFileBytes",
    "apkPath",
    "zipEntriesUncompressedBytes",
    "zipEntriesCompressedBytes",
    "fileCount",
    "dexUncompressedBytes",
    "dexCompressedBytes",
    "resourcesArscUncompressedBytes",
    "resourcesArscCompressedBytes",
    "libUncompressedBytes",
    "libCompressedBytes",
    "resUncompressedBytes",
    "resCompressedBytes",
    "assetsUncompressedBytes",
    "assetsCompressedBytes",
    "metaUncompressedBytes",
    "metaCompressedBytes",
    "manifestUncompressedBytes",
    "manifestCompressedBytes",
    "files",
}

SECRETS_PATTERNS = [
    re.compile(r"BEGIN (?:RSA |DSA |EC |OPENSSH )?PRIVATE KEY", re.I),
    re.compile(r"BEGIN CERTIFICATE", re.I),
    re.compile(r"api[_-]?key\s*=\s*['\"][^'\"]+['\"]", re.I),
    re.compile(r"password\s*=\s*['\"][^'\"]+['\"]", re.I),
    re.compile(r"token\s*=\s*['\"][^'\"]+['\"]", re.I),
    re.compile(r"keystore\.properties", re.I),
    re.compile(r"storePassword", re.I),
    re.compile(r"keyPassword", re.I),
]

ABSOLUTE_PATH_RE = re.compile(r"^[A-Za-z]:[\\/]|^[\\/]|^(?:/home/|/Users/|~[\\/])")
HEX64_RE = re.compile(r"^[0-9a-f]{64}$")
HEX40_RE = re.compile(r"^[0-9a-f]{40}$")


class ApkSizeError(Exception):
    """Report error with enough context for a failing CLI exit."""

    def __init__(self, message: str, variant: str | None = None) -> None:
        if variant:
            message = f"{variant}: {message}"
        super().__init__(message)


class GradleInfo:
    def __init__(self, gradle_text: str) -> None:
        self.text = gradle_text
        self.applicationId = self._string("applicationId")
        self.minSdk = self._literal_int("minSdk")
        self.targetSdk = self._literal_int("targetSdk")
        self.abi = self._first(r'abiFilters\s*\+?=\s*"([^"]+)"')

        last_version = self._int_var("lastVersion")
        last_version_name = self._string_var("lastVersionName")
        self.versionCode = self._resolve_or_literal("versionCode", last_version)
        self.versionName = self._resolve_or_string("versionName", last_version_name)

    def _first(self, pattern: str) -> str | None:
        match = re.search(pattern, self.text)
        return match.group(1) if match else None

    def _string(self, key: str) -> str | None:
        return self._first(rf"{re.escape(key)}\s*=\s*\"([^\"]+)\"")

    def _literal_int(self, key: str) -> int | None:
        match = re.search(rf"{re.escape(key)}\s*=\s*(\d+)", self.text)
        return int(match.group(1)) if match else None

    def _int_var(self, key: str) -> int | None:
        match = re.search(rf"val\s+{re.escape(key)}\s*=\s*(\d+)", self.text)
        return int(match.group(1)) if match else None

    def _string_var(self, key: str) -> str | None:
        match = re.search(rf"val\s+{re.escape(key)}\s*=\s*\"([^\"]+)\"", self.text)
        return match.group(1) if match else None

    def _resolve_or_literal(self, key: str, fallback: int | None) -> int | None:
        lit = self._literal_int(key)
        if lit is not None:
            return lit
        match = re.search(rf"{re.escape(key)}\s*=\s*(\w+)", self.text)
        if match and match.group(1) in ("lastVersion",) and fallback is not None:
            return fallback
        return fallback

    def _resolve_or_string(self, key: str, fallback: str | None) -> str | None:
        lit = self._string(key)
        if lit is not None:
            return lit
        match = re.search(rf"{re.escape(key)}\s*=\s*(\w+)", self.text)
        if match and match.group(1) in ("lastVersionName",) and fallback is not None:
            return fallback
        return fallback


def load_json(path: Path) -> dict:
    with path.open("r", encoding="utf-8") as f:
        return json.load(f)


def percent(delta: int, baseline: int) -> float:
    if baseline == 0:
        return 0.0 if delta == 0 else float("inf")
    return (delta / baseline) * 100.0


def trend(delta: int) -> str:
    if delta > 0:
        return "increase"
    if delta < 0:
        return "decrease"
    return "unchanged"


def compute_deltas(baseline: dict, current: dict) -> dict:
    metrics: dict[str, dict] = {}
    for field in DELTA_FIELDS:
        b = baseline[field]
        c = current[field]
        d = c - b
        metrics[field] = {
            "baseline": b,
            "current": c,
            "delta": d,
            "deltaPercent": round(percent(d, b), 6),
            "trend": trend(d),
        }
    return metrics


def zip_entry_diffs(baseline: dict, current: dict) -> dict:
    b_files = {f["name"]: f for f in baseline["files"]}
    c_files = {f["name"]: f for f in current["files"]}

    added = []
    removed = []
    changed = []

    for name, c in c_files.items():
        if name not in b_files:
            added.append({
                "name": name,
                "bucket": c["bucket"],
                "uncompressedSize": c["uncompressedSize"],
                "compressedSize": c["compressedSize"],
            })
            continue
        b = b_files[name]
        if (
            b["uncompressedSize"] != c["uncompressedSize"]
            or b["compressedSize"] != c["compressedSize"]
        ):
            changed.append({
                "name": name,
                "bucket": c["bucket"],
                "baselineUncompressed": b["uncompressedSize"],
                "currentUncompressed": c["uncompressedSize"],
                "uncompressedDelta": c["uncompressedSize"] - b["uncompressedSize"],
                "baselineCompressed": b["compressedSize"],
                "currentCompressed": c["compressedSize"],
                "compressedDelta": c["compressedSize"] - b["compressedSize"],
            })

    for name, b in b_files.items():
        if name not in c_files:
            removed.append({
                "name": name,
                "bucket": b["bucket"],
                "uncompressedSize": b["uncompressedSize"],
                "compressedSize": b["compressedSize"],
            })

    changed_sorted = sorted(changed, key=lambda x: x["compressedDelta"], reverse=True)
    return {
        "added": sorted(added, key=lambda x: x["compressedSize"], reverse=True),
        "removed": sorted(removed, key=lambda x: x["compressedSize"], reverse=True),
        "changed": changed_sorted,
        "topIncreases": [c for c in changed_sorted if c["compressedDelta"] > 0][:20],
        "topDecreases": [c for c in changed_sorted if c["compressedDelta"] < 0][-20:][::-1],
    }


def conclude(all_metrics: list[dict]) -> str:
    """Pick one of the five allowed conclusion categories across all variants."""
    all_values = [m for metrics in all_metrics for m in metrics.values()]
    if not any(abs(m["delta"]) > 0 for m in all_values):
        return "NO_MEANINGFUL_CHANGE"
    any_increase = any(m["delta"] > 0 for m in all_values)
    any_decrease = any(m["delta"] < 0 for m in all_values)
    if any_increase and any_decrease:
        return "MIXED_CHANGE"
    if any_increase:
        return "EXPLAINED_INCREASE"
    if any_decrease:
        return "EXPLAINED_DECREASE"
    return "NO_MEANINGFUL_CHANGE"


def validate_commit(commit: str, label: str) -> None:
    if not isinstance(commit, str) or not HEX40_RE.match(commit):
        raise ApkSizeError(f"{label} must be a 40-character hex SHA: {commit!r}")


def validate_measurement(data: dict, variant: str) -> None:
    """Validate a single baseline or current measurement JSON."""
    missing = REQUIRED_MEASUREMENT_FIELDS - set(data.keys())
    if missing:
        raise ApkSizeError(f"missing measurement fields: {sorted(missing)}", variant=variant)

    if data["variant"] != variant:
        raise ApkSizeError(
            f"measurement variant mismatch: expected {variant!r}, got {data['variant']!r}",
            variant=variant,
        )

    if not HEX64_RE.match(data["sha256"]):
        raise ApkSizeError(f"invalid sha256: {data['sha256']!r}", variant=variant)

    apk_path = data["apkPath"]
    if not isinstance(apk_path, str) or ABSOLUTE_PATH_RE.search(apk_path):
        raise ApkSizeError(f"apkPath must be relative and not absolute: {apk_path!r}", variant=variant)

    for pat in SECRETS_PATTERNS:
        if pat.search(apk_path):
            raise ApkSizeError("apkPath contains secret-like pattern", variant=variant)

    for field in DELTA_FIELDS:
        if field not in data:
            raise ApkSizeError(f"missing metric {field}", variant=variant)
        value = data[field]
        if not isinstance(value, int) or value < 0:
            raise ApkSizeError(f"{field} must be a non-negative integer", variant=variant)

    files = data.get("files")
    if not isinstance(files, list) or not files:
        raise ApkSizeError("files must be a non-empty list", variant=variant)

    seen_names: set[str] = set()
    for entry in files:
        if not isinstance(entry, dict):
            raise ApkSizeError("each file entry must be an object", variant=variant)
        for key in ("name", "bucket", "uncompressedSize", "compressedSize"):
            if key not in entry:
                raise ApkSizeError(f"file entry missing {key}", variant=variant)
        name = entry["name"]
        if name in seen_names:
            raise ApkSizeError(f"duplicate file name: {name}", variant=variant)
        seen_names.add(name)
        for size_key in ("uncompressedSize", "compressedSize"):
            value = entry[size_key]
            if not isinstance(value, int) or value < 0:
                raise ApkSizeError(f"{size_key} negative for {name}", variant=variant)
        for pat in SECRETS_PATTERNS:
            if pat.search(name):
                raise ApkSizeError(f"file name contains secret-like pattern: {name}", variant=variant)


def load_inputs_manifest(manifest_path: Path) -> tuple[Path, dict]:
    """Load and validate the inputs manifest. Return (manifest_dir, manifest)."""
    manifest_dir = manifest_path.resolve().parent
    manifest = load_json(manifest_path)

    if not isinstance(manifest, dict):
        raise ApkSizeError("manifest must be a JSON object")

    schema = manifest.get("schema")
    if schema != 1:
        raise ApkSizeError(f"unsupported manifest schema: {schema!r}")

    variants = manifest.get("variants")
    if not isinstance(variants, dict) or not variants:
        raise ApkSizeError("manifest must define at least one variant")

    variant_names = list(variants.keys())
    empty_or_invalid = [v for v in variant_names if not isinstance(v, str) or not v.strip()]
    if empty_or_invalid:
        raise ApkSizeError(f"variant names must be non-empty strings: {empty_or_invalid}")
    if len(variant_names) != len(set(variant_names)):
        raise ApkSizeError("variant names must be unique")

    baseline_variants = set()
    current_variants = set()
    for name, spec in variants.items():
        if not isinstance(spec, dict):
            raise ApkSizeError(f"variant {name!r} must be an object")
        baseline = spec.get("baseline")
        current = spec.get("current")
        if isinstance(baseline, str) and baseline:
            baseline_variants.add(name)
        if isinstance(current, str) and current:
            current_variants.add(name)

    if baseline_variants != current_variants:
        raise ApkSizeError("baseline and current variant sets must match exactly")

    for name, spec in variants.items():
        baseline = spec.get("baseline")
        current = spec.get("current")
        if not isinstance(baseline, str) or not baseline:
            raise ApkSizeError(f"variant {name!r} missing baseline path")
        if not isinstance(current, str) or not current:
            raise ApkSizeError(f"variant {name!r} missing current path")

    return manifest_dir, manifest


def resolve_measurement(manifest_dir: Path, rel_path: str, variant: str) -> dict:
    path = manifest_dir / rel_path
    try:
        data = load_json(path)
    except FileNotFoundError:
        raise ApkSizeError(f"measurement file not found: {rel_path}", variant=variant)
    except json.JSONDecodeError as e:
        raise ApkSizeError(f"invalid JSON in {rel_path}: {e}", variant=variant)
    validate_measurement(data, variant)
    return copy.deepcopy(data)


def build_report(
    manifest: dict,
    gradle_info: GradleInfo,
    baseline_commit: str,
    current_commit: str,
    variants_data: dict,
) -> dict:
    """Compute the report data structure without writing files."""
    build_config = {
        "versionCode": gradle_info.versionCode,
        "versionName": gradle_info.versionName,
        "applicationId": gradle_info.applicationId,
        "minSdk": gradle_info.minSdk,
        "targetSdk": gradle_info.targetSdk,
        "abi": gradle_info.abi,
    }

    all_metrics: list[dict] = []
    report_variants: dict[str, dict] = {}

    for variant in sorted(variants_data.keys()):
        baseline, current = variants_data[variant]
        metrics = compute_deltas(baseline, current)
        entry_diffs = zip_entry_diffs(baseline, current)
        all_metrics.append(metrics)

        def enrich(raw: dict, commit: str) -> dict:
            return {
                "sourceCommit": commit,
                "variant": raw["variant"],
                "versionName": gradle_info.versionName,
                "versionCode": gradle_info.versionCode,
                "sha256": raw["sha256"],
                "apkFileBytes": raw["apkFileBytes"],
                "apkPath": raw["apkPath"],
            }

        report_variants[variant] = {
            "baseline": enrich(baseline, baseline_commit),
            "current": enrich(current, current_commit),
            "metrics": metrics,
            "entryDiffs": entry_diffs,
        }

    return {
        "schema": 1,
        "reportVersion": 1,
        "baselineCommit": baseline_commit,
        "currentSourceCommit": current_commit,
        "buildConfig": build_config,
        "conclusion": conclude(all_metrics),
        "variants": report_variants,
    }


def render_markdown(data: dict) -> str:
    """Render a Markdown report from report data without touching the file system."""
    lines: list[str] = [
        "# APK Size Delta Report",
        "",
        "## 1. Measurement scope",
        "",
        "This report compares two sets of existing APK size measurements. It does not",
        "build, sign, or install APKs; it only consumes the supplied baseline and",
        "current measurement JSONs.",
        "",
        f"- Baseline commit: `{data['baselineCommit']}`",
        f"- Current source commit: `{data['currentSourceCommit']}`",
        "",
        "Variants measured:",
        "",
    ]
    for variant in sorted(data["variants"].keys()):
        lines.append(f"- **{variant}**: baseline vs. current measurement.")

    lines += [
        "",
        "## 2. Baseline provenance",
        "",
        "| Variant | Baseline commit | SHA-256 | APK bytes |",
        "| --- | --- | --- | --- |",
    ]
    for variant in sorted(data["variants"].keys()):
        b = data["variants"][variant]["baseline"]
        lines.append(f"| {b['variant']} | `{b['sourceCommit']}` | `{b['sha256']}` | {b['apkFileBytes']} |")

    lines += [
        "",
        "## 3. Current build provenance",
        "",
        "| Variant | Current commit | Version | Version code | SHA-256 | APK bytes |",
        "| --- | --- | --- | --- | --- | --- |",
    ]
    for variant in sorted(data["variants"].keys()):
        c = data["variants"][variant]["current"]
        lines.append(
            f"| {c['variant']} | `{c['sourceCommit']}` | {c['versionName']} | "
            f"{c['versionCode']} | `{c['sha256']}` | {c['apkFileBytes']} |"
        )

    lines += [
        "",
        "## 4. Build configuration",
        "",
        "| applicationId | minSdk | targetSdk | ABI |",
        "| --- | --- | --- | --- |",
        f"| {data['buildConfig']['applicationId']} | {data['buildConfig']['minSdk']} | "
        f"{data['buildConfig']['targetSdk']} | {data['buildConfig']['abi']} |",
    ]

    for variant in sorted(data["variants"].keys()):
        v = data["variants"][variant]
        lines += [
            "",
            f"## 5. {variant} comparison",
            "",
            "| Metric | Baseline | Current | Delta | Delta % | Trend |",
            "| --- | --- | --- | --- | --- | --- |",
        ]
        for metric, vals in v["metrics"].items():
            lines.append(
                f"| {metric} | {vals['baseline']} | {vals['current']} | "
                f"{vals['delta']} | {vals['deltaPercent']:.4f}% | {vals['trend']} |"
            )

    lines += ["", "## 6. Bucket-level changes", ""]
    for variant in sorted(data["variants"].keys()):
        lines += [f"", f"### {variant}", "", _entry_summary(data["variants"][variant]["entryDiffs"]), ""]

    lines += ["", "## 7. Largest entry changes", ""]
    for variant in sorted(data["variants"].keys()):
        diffs = data["variants"][variant]["entryDiffs"]
        lines += [
            f"",
            f"### {variant} top 20 compressed-size increases",
            "",
            _entry_table(diffs["topIncreases"]),
            "",
            f"### {variant} top 20 compressed-size decreases",
            "",
            _entry_table(diffs["topDecreases"]),
            "",
        ]

    lines += [
        "",
        "## 8. Interpretation",
        "",
        f"Conclusion: `{data['conclusion']}`",
        "",
        "The changes above reflect differences between the supplied baseline and",
        "current measurements. They do not prove runtime performance, installation",
        "size on a target device, or release-candidate suitability. Artifact names",
        "do not imply signing state; treat release-named variants as measurements",
        "only until explicitly verified.",
        "",
    ]
    return "\n".join(lines) + "\n"


def _entry_summary(diffs: dict) -> str:
    summary = {
        "addedCount": len(diffs["added"]),
        "removedCount": len(diffs["removed"]),
        "changedCount": len(diffs["changed"]),
        "topIncreaseCount": len(diffs["topIncreases"]),
        "topDecreaseCount": len(diffs["topDecreases"]),
    }
    return json.dumps(summary, indent=2)


def _entry_table(entries: list[dict]) -> str:
    if not entries:
        return "*No entries.*"
    lines = ["| Name | Bucket | Baseline compressed | Current compressed | Delta |", "| --- | --- | --- | --- | --- |"]
    for e in entries:
        lines.append(
            f"| {e['name']} | {e['bucket']} | {e.get('baselineCompressed', '-')} | "
            f"{e.get('currentCompressed', '-')} | {e.get('compressedDelta', '-')} |"
        )
    return "\n".join(lines)


def write_report(report: dict, out_json: Path, out_md: Path) -> None:
    out_json.parent.mkdir(parents=True, exist_ok=True)
    out_md.parent.mkdir(parents=True, exist_ok=True)

    with out_json.open("w", encoding="utf-8", newline="\n") as f:
        json.dump(report, f, indent=2, ensure_ascii=False)
        f.write("\n")

    with out_md.open("w", encoding="utf-8", newline="\n") as f:
        f.write(render_markdown(report))


def run(
    inputs_manifest: Path,
    baseline_commit: str,
    current_commit: str,
    gradle_file: Path,
    out_json: Path,
    out_md: Path,
) -> int:
    validate_commit(baseline_commit, "baseline-commit")
    validate_commit(current_commit, "current-commit")

    if not gradle_file.is_file():
        raise ApkSizeError(f"gradle file not found: {gradle_file}")

    manifest_dir, manifest = load_inputs_manifest(inputs_manifest)
    gradle_info = GradleInfo(gradle_file.read_text(encoding="utf-8"))

    variants_data: dict[str, tuple[dict, dict]] = {}
    for variant in sorted(manifest["variants"].keys()):
        spec = manifest["variants"][variant]
        baseline = resolve_measurement(manifest_dir, spec["baseline"], variant)
        current = resolve_measurement(manifest_dir, spec["current"], variant)
        variants_data[variant] = (baseline, current)

    report = build_report(manifest, gradle_info, baseline_commit, current_commit, variants_data)
    write_report(report, out_json, out_md)
    return 0


def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--inputs-manifest", required=True, type=Path, help="path to the inputs manifest JSON")
    p.add_argument("--baseline-commit", required=True, help="40-character baseline SHA")
    p.add_argument("--current-commit", required=True, help="40-character current SHA")
    p.add_argument("--gradle-file", required=True, type=Path, help="path to build.gradle.kts")
    p.add_argument("--out-json", required=True, type=Path, help="path to write the JSON report")
    p.add_argument("--out-md", required=True, type=Path, help="path to write the Markdown report")
    args = p.parse_args(argv)

    try:
        return run(
            args.inputs_manifest,
            args.baseline_commit,
            args.current_commit,
            args.gradle_file,
            args.out_json,
            args.out_md,
        )
    except ApkSizeError as e:
        print(f"apk_size_delta: {e}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
