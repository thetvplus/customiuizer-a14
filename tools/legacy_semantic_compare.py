#!/usr/bin/env python3
"""Compare A14 preference XML/arrays/getter defaults against MonwF v24.10.12."""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
from collections import defaultdict
from pathlib import Path

A14 = Path(__file__).resolve().parent.parent

XML_PREF_RE = re.compile(
    r"<(?P<tag>[\w.]+)\b(?P<body>[^>]*android:key=\"(?P<key>[^\"]+)\"[^>]*)/?>",
    re.DOTALL,
)
ATTR_RE = re.compile(r"android:(?P<name>[\w]+)=\"(?P<value>[^\"]*)\"")
MIUIZER_ATTR_RE = re.compile(r"miuizer:(?P<name>[\w]+)=\"(?P<value>[^\"]*)\"")
ARRAY_RE = re.compile(
    r"<string-array\s+name=\"(?P<name>[^\"]+)\"[^>]*>(?P<body>.*?)</string-array>",
    re.DOTALL,
)
ITEM_RE = re.compile(r"<item>(.*?)</item>", re.DOTALL)
GETTER_RE = re.compile(
    r"(?P<method>getInt|getBoolean|getStringAsInt|getString|getLong|getStringSet)"
    r"\s*\(\s*\"(?P<key>[^\"]+)\"(?:\s*,\s*(?P<default>[^)\n]+))?",
)


def canonical(key: str) -> str:
    return key[len("pref_key_") :] if key.startswith("pref_key_") else key


def parse_prefs(xml_dir: Path) -> dict[str, dict]:
    prefs: dict[str, dict] = {}
    for path in sorted(xml_dir.glob("prefs_*.xml")):
        text = path.read_text(encoding="utf-8")
        for m in XML_PREF_RE.finditer(text):
            key = canonical(m.group("key"))
            attrs = {a.group("name"): a.group("value") for a in ATTR_RE.finditer(m.group("body"))}
            attrs.update(
                {
                    "miuizer:" + a.group("name"): a.group("value")
                    for a in MIUIZER_ATTR_RE.finditer(m.group("body"))
                }
            )
            prefs[key] = {
                "xml_key": m.group("key"),
                "file": path.name,
                "tag": m.group("tag").split(".")[-1],
                "default": attrs.get("defaultValue"),
                "entries": attrs.get("entries"),
                "entryValues": attrs.get("entryValues"),
                "dependency": attrs.get("dependency"),
                "title": attrs.get("title"),
                "minValue": attrs.get("miuizer:minValue"),
                "maxValue": attrs.get("miuizer:maxValue"),
                "stepValue": attrs.get("miuizer:stepValue"),
                "displayDividerValue": attrs.get("miuizer:displayDividerValue"),
                "negativeShift": attrs.get("miuizer:negativeShift"),
            }
    return prefs


def parse_arrays(values_xml: Path) -> dict[str, list[str]]:
    if not values_xml.exists():
        return {}
    text = values_xml.read_text(encoding="utf-8")
    out: dict[str, list[str]] = {}
    for m in ARRAY_RE.finditer(text):
        out[m.group("name")] = [html_unescape(i.group(1).strip()) for i in ITEM_RE.finditer(m.group("body"))]
    return out


def html_unescape(s: str) -> str:
    return (
        s.replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", '"')
        .replace("&#x2F;", "/")
    )


def parse_getters(src_root: Path) -> dict[str, list[dict]]:
    getters: dict[str, list[dict]] = defaultdict(list)
    for path in src_root.rglob("*"):
        if path.suffix not in {".java", ".kt"}:
            continue
        if "test" in path.parts:
            continue
        text = path.read_text(encoding="utf-8", errors="replace")
        for m in GETTER_RE.finditer(text):
            key = canonical(m.group("key"))
            default = (m.group("default") or "").strip().rstrip(",")
            getters[key].append(
                {
                    "file": str(path.relative_to(src_root.parent.parent) if False else path.name),
                    "path": str(path),
                    "method": m.group("method"),
                    "default": default,
                }
            )
    return getters


def array_ref(name: str | None) -> str | None:
    if not name:
        return None
    return name.split("/")[-1]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--upstream",
        type=Path,
        default=Path(os.environ["LEGACY_UPSTREAM_ROOT"]) if os.environ.get("LEGACY_UPSTREAM_ROOT") else None,
        help="MonwF/customiuizer checkout (or set LEGACY_UPSTREAM_ROOT)",
    )
    parser.add_argument(
        "--output",
        type=Path,
        help="Optional JSON report path. Counts are always printed to stdout.",
    )
    args = parser.parse_args()
    if args.upstream is None:
        print("error: pass --upstream PATH or set LEGACY_UPSTREAM_ROOT", file=sys.stderr)
        return 2
    upstream = args.upstream.resolve()
    if not (upstream / "app/src/main/res/xml").is_dir():
        print(f"error: not an upstream source tree: {upstream}", file=sys.stderr)
        return 2

    a14_prefs = parse_prefs(A14 / "app/src/main/res/xml")
    up_prefs = parse_prefs(upstream / "app/src/main/res/xml")
    a14_arrays = parse_arrays(A14 / "app/src/main/res/values/arrays.xml")
    up_arrays = parse_arrays(upstream / "app/src/main/res/values/arrays.xml")
    a14_getters = parse_getters(A14 / "app/src/main/java")
    up_getters = parse_getters(upstream / "app/src/main/java")

    only_a14 = sorted(set(a14_prefs) - set(up_prefs))
    only_up = sorted(set(up_prefs) - set(a14_prefs))
    shared = sorted(set(a14_prefs) & set(up_prefs))

    default_drift = []
    array_drift = []
    seekbar_drift = []
    getter_default_drift = []
    xml_no_getter = []
    getter_no_xml = []

    for key in shared:
        a = a14_prefs[key]
        u = up_prefs[key]
        if a["default"] != u["default"]:
            default_drift.append({"key": key, "upstream": u["default"], "a14": a["default"], "file": a["file"]})
        for field in ("minValue", "maxValue", "stepValue", "displayDividerValue", "negativeShift"):
            if a[field] != u[field]:
                seekbar_drift.append(
                    {"key": key, "field": field, "upstream": u[field], "a14": a[field], "file": a["file"]}
                )
        a_ev = array_ref(a["entryValues"])
        u_ev = array_ref(u["entryValues"])
        a_en = array_ref(a["entries"])
        u_en = array_ref(u["entries"])
        if a_ev and u_ev:
            if a14_arrays.get(a_ev) != up_arrays.get(u_ev) or a_ev != u_ev:
                array_drift.append(
                    {
                        "key": key,
                        "kind": "entryValues",
                        "upstream_name": u_ev,
                        "a14_name": a_ev,
                        "upstream": up_arrays.get(u_ev),
                        "a14": a14_arrays.get(a_ev),
                    }
                )
        if a_en and u_en and a_en == u_en:
            if a14_arrays.get(a_en) != up_arrays.get(u_en):
                array_drift.append(
                    {
                        "key": key,
                        "kind": "entries",
                        "name": a_en,
                        "upstream": up_arrays.get(u_en),
                        "a14": a14_arrays.get(a_en),
                    }
                )

    skip_prefixes = (
        "pref_key_",  # already canonicalized
    )
    skip_keys = {
        "pref_key",
    }
    for key, a in a14_prefs.items():
        if key in skip_keys:
            continue
        if key not in a14_getters and a["tag"] not in {"PreferenceCategoryEx", "PreferenceEx", "Preference"}:
            # categories / navigation prefs often have no getter
            if a["tag"] in {"CheckBoxPreferenceEx", "ListPreferenceEx", "SeekBarPreference", "DropDownPreferenceEx"}:
                xml_no_getter.append({"key": key, "tag": a["tag"], "file": a["file"], "default": a["default"]})

    production_keys = set(a14_prefs)
    for key, uses in a14_getters.items():
        if key not in production_keys and not key.startswith("pref_"):
            # many internal keys are not XML
            getter_no_xml.append({"key": key, "uses": len(uses), "methods": sorted({u["method"] for u in uses})})

    for key in sorted(set(a14_getters) & set(up_getters)):
        a_defs = sorted({u["method"] + ":" + u["default"] for u in a14_getters[key]})
        u_defs = sorted({u["method"] + ":" + u["default"] for u in up_getters[key]})
        if a_defs != u_defs:
            getter_default_drift.append({"key": key, "upstream": u_defs, "a14": a_defs})

    report = {
        "upstream": "MonwF/customiuizer v24.10.12",
        "counts": {
            "a14_prefs": len(a14_prefs),
            "upstream_prefs": len(up_prefs),
            "shared": len(shared),
            "only_a14": len(only_a14),
            "only_upstream": len(only_up),
            "default_drift": len(default_drift),
            "array_drift": len(array_drift),
            "seekbar_drift": len(seekbar_drift),
            "getter_default_drift": len(getter_default_drift),
            "xml_no_getter": len(xml_no_getter),
            "getter_no_xml": len(getter_no_xml),
        },
        "only_a14": only_a14,
        "only_upstream": only_up,
        "default_drift": default_drift,
        "array_drift": array_drift,
        "seekbar_drift": seekbar_drift,
        "getter_default_drift": getter_default_drift,
        "xml_no_getter": xml_no_getter,
        "getter_no_xml": getter_no_xml,
    }
    print(json.dumps(report["counts"], indent=2))
    if args.output is not None:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        print("wrote", args.output)
    return 0


if __name__ == "__main__":
    sys.exit(main())
