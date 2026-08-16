#!/usr/bin/env python3
"""Relationship-based Feature Catalog contract checker for A14.

Derives the current set of LazyFeatureSpec declarations from the real feature
files, validates one-to-one relationships among FeatureId object names,
canonical feature ids, and the committed A14 process matrix (CSV/MD).
"""
from __future__ import annotations

import argparse
import csv
import re
import sys
from collections import Counter
from pathlib import Path


def balanced_blocks(text: str, token: str) -> list[str]:
    blocks: list[str] = []
    pos = 0
    while True:
        start = text.find(token, pos)
        if start < 0:
            return blocks
        open_pos = text.find("(", start + len(token))
        if open_pos < 0:
            return blocks
        depth = 0
        in_string = False
        escaped = False
        for i in range(open_pos, len(text)):
            ch = text[i]
            if in_string:
                if escaped:
                    escaped = False
                elif ch == "\\":
                    escaped = True
                elif ch == '"':
                    in_string = False
                continue
            if ch == '"':
                in_string = True
            elif ch == "(":
                depth += 1
            elif ch == ")":
                depth -= 1
                if depth == 0:
                    blocks.append(text[start : i + 1])
                    pos = i + 1
                    break
        else:
            return blocks


def field(block: str, name: str) -> str | None:
    m = re.search(rf"\b{re.escape(name)}\s*=\s*\"([^\"]+)\"", block)
    return m.group(1) if m else None


def symbol_field(block: str, name: str) -> str | None:
    m = re.search(rf"\b{re.escape(name)}\s*=\s*([A-Za-z0-9_.]+)", block)
    return m.group(1) if m else None


def parse_feature_ids(path: Path) -> dict[str, str]:
    """Map FeatureId object name -> canonical feature id (name property)."""
    text = path.read_text(encoding="utf-8")
    pattern = re.compile(
        r"data object (\w+) : FeatureId \{\s*override val id = (\d+)\s*override val name = \"([^\"]+)\"\s*\}",
        re.DOTALL,
    )
    result: dict[str, str] = {}
    for m in pattern.finditer(text):
        result[m.group(1)] = m.group(3)
    return result


def parse_matrix_csv(path: Path) -> dict[str, dict[str, str]]:
    """Read the A14 process matrix CSV and return rows keyed by featureIdName."""
    rows: dict[str, dict[str, str]] = {}
    with path.open(encoding="utf-8", newline="") as f:
        reader = csv.DictReader(f)
        for row in reader:
            key = row.get("featureIdName", "").strip()
            if not key:
                continue
            rows[key] = {
                "name": (row.get("name") or "").strip(),
                "preferenceKey": "" if (row.get("preferenceKey") or "").strip().lower() == "null" else (row.get("preferenceKey") or "").strip(),
                "target": (row.get("target") or "").strip().upper(),
                "phase": (row.get("phase") or "").strip().upper(),
            }
    return rows


def parse_matrix_md(path: Path) -> set[str]:
    """Fallback for Markdown matrix: only the first column identifiers."""
    ids: set[str] = set()
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line.startswith("|"):
            continue
        cells = [c.strip() for c in line.strip("|").split("|")]
        if not cells:
            continue
        value = cells[0].strip("` ")
        if re.fullmatch(r"[A-Za-z][A-Za-z0-9_]+", value):
            ids.add(value)
    return ids


def strip_enum(value: str | None) -> str:
    if not value:
        return ""
    if "." in value:
        return value.split(".", 1)[1].upper()
    return value.strip().upper()


def extract_preference_key(value: str | None) -> str:
    if not value or value == "null":
        return ""
    value = value.strip()
    if value.startswith('"') and value.endswith('"'):
        return value[1:-1]
    return value


def parse_lazy_spec(block: str) -> dict[str, str] | None:
    spec: dict[str, str] = {}

    m = re.search(r"\bid\s*=\s*([A-Za-z0-9_]+)", block)
    if not m:
        return None
    spec["id"] = m.group(1)

    name = field(block, "name")
    spec["name"] = name or ""

    m2 = re.search(r"\bpreferenceKey\s*=\s*(?:\"([^\"]+)\"|null)", block)
    spec["preferenceKey"] = (m2.group(1) or "") if m2 else ""

    spec["target"] = strip_enum(symbol_field(block, "target"))
    spec["phase"] = strip_enum(symbol_field(block, "phase"))
    return spec


def collect_catalog_files(catalog: Path) -> list[Path]:
    if catalog.is_file():
        return [catalog]
    if catalog.is_dir():
        return sorted(catalog.glob("*Features.kt"))
    return []


def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--catalog", required=True)
    p.add_argument("--feature-id", required=True)
    p.add_argument("--matrix", required=True)
    args = p.parse_args(argv)

    catalog_path = Path(args.catalog)
    feature_id_path = Path(args.feature_id)
    matrix_path = Path(args.matrix)

    feature_ids = parse_feature_ids(feature_id_path)
    if not feature_ids:
        print("Catalog contract violations:")
        print("  - no FeatureId objects found in feature-id file")
        return 1

    # Build matrix contract.
    if matrix_path.suffix.lower() == ".csv":
        matrix = parse_matrix_csv(matrix_path)
    else:
        matrix_ids = parse_matrix_md(matrix_path)
        matrix = {k: {} for k in matrix_ids}

    # Extract LazyFeatureSpec declarations.
    specs: list[dict[str, str]] = []
    for path in collect_catalog_files(catalog_path):
        text = path.read_text(encoding="utf-8")
        for block in balanced_blocks(text, "LazyFeatureSpec"):
            spec = parse_lazy_spec(block)
            if not spec:
                continue
            specs.append(spec)

    spec_ids = [s["id"] for s in specs]
    errors: list[str] = []

    errors += [f"FeatureSpec id duplicate: {v}" for v, c in Counter(spec_ids).items() if c > 1]

    # Map object names to canonical names and validate.
    canonical_ids: list[str] = []
    for spec in specs:
        obj_name = spec["id"]
        canonical = feature_ids.get(obj_name)
        if not canonical:
            errors.append(f"{obj_name}: FeatureId object not found in {feature_id_path.name}")
            continue
        spec["canonical"] = canonical
        canonical_ids.append(canonical)

    # Compare against matrix.
    if isinstance(matrix, dict) and all(isinstance(v, dict) for v in matrix.values()):
        for spec in specs:
            canonical = spec.get("canonical")
            if not canonical:
                continue
            if canonical not in matrix:
                errors.append(f"{canonical}: feature missing from process matrix")
                continue
            row = matrix[canonical]
            for key in ("name", "preferenceKey", "target", "phase"):
                spec_val = spec.get(key, "")
                matrix_val = row.get(key, "")
                if key in ("name", "preferenceKey"):
                    if spec_val != matrix_val:
                        errors.append(f"{canonical}: {key} mismatch (spec={spec_val!r}, matrix={matrix_val!r})")
                else:
                    if spec_val.upper() != matrix_val.upper():
                        errors.append(f"{canonical}: {key} mismatch (spec={spec_val!r}, matrix={matrix_val!r})")

        for canonical in sorted(set(matrix) - set(canonical_ids)):
            errors.append(f"{canonical}: process matrix row without a FeatureSpec")
    else:
        for canonical in sorted(set(canonical_ids) - set(matrix)):
            errors.append(f"{canonical}: feature missing from process matrix")
        for canonical in sorted(set(matrix) - set(canonical_ids)):
            errors.append(f"{canonical}: process matrix row without a FeatureSpec")

    if errors:
        print("Catalog contract violations:")
        for e in errors:
            print(f"  - {e}")
        return 1

    print(
        f"Catalog contract probe passed: "
        f"{len(specs)} specs, {len(feature_ids)} FeatureIds, {len(matrix)} matrix rows"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
