#!/usr/bin/env python3
"""Audit feature semantics for CustoMIUIzer A14.

Modes:
  --init      Build feature-semantics/a14.json from source extraction.
  --validate  Validate a14.json against the schema and current source.
  (no markdown output; feature matrix is now generated on demand)

Exit codes:
  0  success / valid
  1  validation or coverage error
  2  schema/input error
"""

from __future__ import annotations

import argparse
import html
import json
import os
import re
import sys
from collections import defaultdict
from pathlib import Path
from typing import Any

REPO_ROOT = Path(__file__).resolve().parent.parent

SCHEMA_PATH = REPO_ROOT / "feature-semantics" / "schema.json"
INVENTORY_PATH = REPO_ROOT / "feature-semantics" / "a14.json"

XML_KEY_RE = re.compile(r'android:key="([^"]+)"')
XML_DEFAULT_RE = re.compile(r'android:defaultValue="([^"]*)"')
XML_TAG_RE = re.compile(r'<[^>]*?android:key="[^"]+"[^>]*?>', re.DOTALL)

# Match PrefMap accessors.  Only literal string keys are discovered; constant
# references are intentionally skipped to avoid false positives.
# Variables must be declared as PrefMap or be the known MainModule.mPrefs field.
PREFMAP_GETTER_RE = re.compile(
    r'(?:getInt|getBoolean|getStringAsInt|getString|getLong|getStringSet)'
    r'\s*\(\s*"([^"]+)"',
    re.VERBOSE,
)


def canonical_preference_key(key: str) -> str:
    """Return the runtime canonical form of a preference key.

    Storage/XML keys use the `pref_key_` prefix; source-level getters and
    observers use the short form.  Both must resolve to the same semantic
    feature.
    """
    if key.startswith("pref_key_"):
        return key[len("pref_key_"):]
    return key

THEME_RE = re.compile(r'setThemeValueReplacement\s*\(\s*"([^"]+)"\s*,\s*"([^"]+)"\s*,\s*"([^"]+)"')
FAKE_RE = re.compile(r'addFakeResource\s*\(\s*"([^"]+)"')

JAVA_FUNC_RE = re.compile(
    r'^\s*(?:public|protected|private|static|final|synchronized|abstract|'
    r'@(?:\w+\.)*\w+(?:\([^)]*\))?\s*)*'
    r'(?:[\w<>\[\],\?\s]+)\s+(\w+)\s*\('
)
KT_FUNC_RE = re.compile(r'^\s*(?:[a-zA-Z]+\s+)*fun\s+(?:<[^>]+>\s+)?(\w+)\s*\(')
KT_INIT_RE = re.compile(r'^\s*init\s*\{')

INSTALL_PHASE_BY_FUNCTION = {
    "onSystemServerStarting": "SYSTEM_SERVER_START",
    "onPackageReady": "PACKAGE_READY",
    "onApplicationAttach": "APPLICATION_ATTACH",
    "onSystemUIStarted": "SYSTEMUI_INIT",
    "initSystemUI": "SYSTEMUI_INIT",
    "initResources": "RESOURCE_INIT",
    "onResourcesLoaded": "RESOURCE_INIT",
    "onReceiverEvent": "RECEIVER_EVENT",
    "loadDexKit": "DEXKIT_ONE_SHOT",
}


def _rel(repo_root: Path, target: Path) -> str:
    try:
        return target.relative_to(repo_root).as_posix()
    except ValueError:
        return target.as_posix()


def _file_functions(text: str, language: str) -> list[tuple[int, str]]:
    """Return list of (1-based line number, function name)."""
    functions: list[tuple[int, str]] = []
    for i, line in enumerate(text.splitlines(), start=1):
        if language == "kotlin":
            m = KT_FUNC_RE.match(line)
            if m:
                functions.append((i, m.group(1)))
            m = KT_INIT_RE.match(line)
            if m:
                functions.append((i, "init"))
        else:
            m = JAVA_FUNC_RE.match(line)
            if m:
                name = m.group(1)
                if name in ("if", "while", "for", "switch", "catch", "synchronized"):
                    continue
                functions.append((i, name))
    return functions


def _nearest_function(text: str, line: int, language: str) -> str | None:
    functions = _file_functions(text, language)
    best = None
    for ln, name in functions:
        if ln <= line:
            best = name
    return best


def _sanitize_id(raw: str, seen: set[str]) -> str:
    s = raw.lower()
    s = re.sub(r'[^a-z0-9]', '_', s)
    s = re.sub(r'_+', '_', s).strip('_')
    if not s:
        s = "feature"
    if s[0].isdigit():
        s = "f_" + s
    base = s
    counter = 1
    while s in seen:
        s = f"{base}_{counter}"
        counter += 1
    seen.add(s)
    return s


def _target_package_from_path(source_file: str) -> str:
    p = source_file.lower()
    if "systemui" in p:
        return "com.android.systemui"
    if "launcher" in p:
        return "com.miui.home"
    if "system" in p and "systemui" not in p:
        return "android"
    if "globalaction" in p:
        return "android"
    if "packagepermissions" in p:
        return "android"
    return "UNKNOWN"


def _infer_install_phase(func_name: str | None, source_file: str | None) -> str:
    if func_name and func_name in INSTALL_PHASE_BY_FUNCTION:
        return INSTALL_PHASE_BY_FUNCTION[func_name]
    if source_file and "MainModule.java" in source_file:
        return "UNKNOWN"
    return "UNKNOWN"


def _extract_xml_keys(repo_root: Path) -> dict[str, dict[str, Any]]:
    xml_dir = repo_root / "app" / "src" / "main" / "res" / "xml"
    found: dict[str, dict[str, Any]] = {}
    if not xml_dir.is_dir():
        return found
    for xml_file in sorted(xml_dir.glob("*.xml")):
        text = xml_file.read_text(encoding="utf-8")
        rel = _rel(repo_root, xml_file)
        # fast per-tag extraction for default value
        for m in XML_TAG_RE.finditer(text):
            tag = m.group(0)
            km = XML_KEY_RE.search(tag)
            if not km:
                continue
            key = html.unescape(km.group(1))
            default_match = XML_DEFAULT_RE.search(tag)
            default_value = html.unescape(default_match.group(1)) if default_match else ""
            if key not in found:
                found[key] = {
                    "xmlSource": rel,
                    "defaultValue": default_value,
                }
            else:
                found[key].setdefault("xmlSource", rel)
    return found


def _find_prefmap_var_names(text: str) -> set[str]:
    """Return variable/parameter names that are declared as PrefMap in the file.

    Covers Kotlin `name: PrefMap` parameters/properties and Java `PrefMap name`.
    `MainModule.mPrefs` is handled separately because it is a field reference.
    """
    names: set[str] = set()
    # Kotlin parameters and properties: `prefs: PrefMap`, `val prefs: PrefMap`, etc.
    for m in re.finditer(r'(?:(?:val|var|)\s+)?(\w+)\s*:\s*PrefMap', text):
        names.add(m.group(1))
    # Java fields/locals: `PrefMap mPrefs = ...`
    for m in re.finditer(r'PrefMap\s+(\w+)', text):
        names.add(m.group(1))
    return names


def _build_prefmap_accessor_regex(var_names: set[str]) -> re.Pattern[str] | None:
    if not var_names:
        return None
    # Names sorted by length descending so that `mPrefs` matches before `prefs`
    # and `MainModule.mPrefs` is matched as a single dotted name.
    name_alts = sorted(var_names, key=len, reverse=True)
    name_pattern = "|".join(re.escape(n) for n in name_alts)
    return re.compile(
        rf'(?:MainModule\.)?(?:{name_pattern})'
        r'\.(?:getInt|getBoolean|getStringAsInt|getString|getLong|getStringSet)'
        r'\s*\(\s*"([^"]+)"'
    )


def _extract_code_keys(
    repo_root: Path,
    file_paths: list[Path],
    language: str,
    source_type: str,
) -> dict[str, dict[str, Any]]:
    found: dict[str, dict[str, Any]] = {}
    for fp in sorted(file_paths):
        text = fp.read_text(encoding="utf-8")
        rel = _rel(repo_root, fp)
        var_names = _find_prefmap_var_names(text)
        # MainModule.mPrefs is a known PrefMap field that may not be declared
        # inside the file being scanned.
        var_names.add("mPrefs")
        pref_re = _build_prefmap_accessor_regex(var_names)
        if pref_re is None:
            continue
        lines = text.splitlines()
        for m in pref_re.finditer(text):
            key = m.group(1)
            line_no = text[:m.start()].count("\n") + 1
            func = _nearest_function(text, line_no, language)
            canonical = canonical_preference_key(key)
            if canonical not in found:
                found[canonical] = {
                    "sourceFile": rel,
                    "installer_func": func,
                    "evidence": f'PrefMap call at {rel}:{line_no}',
                    "line": line_no,
                    "xmlSource": "",
                    "defaultValue": "",
                    "keys": [key],
                }
            else:
                found[canonical].setdefault("sourceFile", rel)
                if key not in found[canonical]["keys"]:
                    found[canonical]["keys"].append(key)
    return found


def _extract_resource_names(
    repo_root: Path,
    file_paths: list[Path],
) -> dict[str, dict[str, Any]]:
    found: dict[str, dict[str, Any]] = {}
    for fp in sorted(file_paths):
        text = fp.read_text(encoding="utf-8")
        rel = _rel(repo_root, fp)
        language = "java" if fp.suffix == ".java" else "kotlin"
        for m in THEME_RE.finditer(text):
            pkg = m.group(1)
            rtype = m.group(2)
            name = m.group(3)
            key = f"theme:{pkg}:{rtype}:{name}"
            line_no = text[:m.start()].count("\n") + 1
            func = _nearest_function(text, line_no, language)
            if key not in found:
                found[key] = {
                    "sourceFile": rel,
                    "installer_func": func,
                    "evidence": f'setThemeValueReplacement at {rel}:{line_no}',
                    "line": line_no,
                    "targetPackage": pkg,
                    "kind": "theme",
                    "resourceName": name,
                }
        for m in FAKE_RE.finditer(text):
            name = m.group(1)
            key = f"fake:{name}"
            line_no = text[:m.start()].count("\n") + 1
            func = _nearest_function(text, line_no, language)
            if key not in found:
                found[key] = {
                    "sourceFile": rel,
                    "installer_func": func,
                    "evidence": f'addFakeResource at {rel}:{line_no}',
                    "line": line_no,
                    "kind": "fake",
                    "resourceName": name,
                }
    return found


def _lazy_feature_specs(repo_root: Path) -> dict[str, dict[str, str]]:
    """Parse LazyFeatureSpec registries for canonical feature metadata.

    Returns a map of canonical preference key -> metadata dict with:
    name, target, phase, sourceFile (registry file).
    """
    feature_dir = (
        repo_root
        / "app"
        / "src"
        / "main"
        / "java"
        / "tv"
        / "withaibuild"
        / "customiuizer"
        / "mods"
        / "utils"
        / "feature"
    )
    meta: dict[str, dict[str, str]] = {}
    if not feature_dir.is_dir():
        return meta

    # Match LazyFeatureSpec(...) blocks that contain the standard fields.
    # The regex is intentionally tolerant of newlines and optional trailing commas.
    spec_re = re.compile(
        r'LazyFeatureSpec\('
        r'(?:.|\n)*?'
        r'name\s*=\s*"([^"]+)"'
        r'(?:.|\n)*?'
        r'preferenceKey\s*=\s*(?:"([^"]*)"|null)'
        r'(?:.|\n)*?'
        r'target\s*=\s*FeatureTarget\.(\w+)'
        r'(?:.|\n)*?'
        r'phase\s*=\s*InstallPhase\.(\w+)',
        re.DOTALL,
    )

    for fp in sorted(feature_dir.glob("*.kt")):
        text = fp.read_text(encoding="utf-8")
        rel = _rel(repo_root, fp)
        for m in spec_re.finditer(text):
            name, pref_key, target, phase = m.groups()
            if not pref_key:
                continue
            canonical = canonical_preference_key(pref_key)
            meta[canonical] = {
                "name": name,
                "preferenceKey": canonical,
                "target": target,
                "phase": phase,
                "sourceFile": rel,
            }
    return meta


def _feature_meta_for_key(
    canonical_key: str,
    lazy_specs: dict[str, dict[str, str]],
) -> dict[str, str] | None:
    """Find the nearest LazyFeatureSpec for a canonical key.

    Exact match is preferred; otherwise the longest matching prefix is used so
    that observed sub-keys such as `system_charginginfo_fontsize` inherit the
    metadata of the `system_charginginfo` feature.
    """
    if canonical_key in lazy_specs:
        return lazy_specs[canonical_key]
    best: dict[str, str] | None = None
    best_len = -1
    for spec_key, spec in lazy_specs.items():
        if canonical_key.startswith(spec_key + "_") and len(spec_key) > best_len:
            best = spec
            best_len = len(spec_key)
    return best


def discover_features(repo_root: Path) -> dict[str, dict[str, Any]]:
    """Discover all preference/resource keys and their source metadata."""
    features: dict[str, dict[str, Any]] = {}
    lazy_specs = _lazy_feature_specs(repo_root)

    # 1. XML preference keys (used for xmlSource/defaultValue)
    xml_meta = _extract_xml_keys(repo_root)

    # 2. mPrefs keys in MainModule.java
    main_module = repo_root / "app" / "src" / "main" / "java" / "tv" / "withaibuild" / "customiuizer" / "MainModule.java"
    main_keys: dict[str, dict[str, Any]] = {}
    if main_module.is_file():
        main_keys = _extract_code_keys(repo_root, [main_module], "java", "main")

    # 3. mPrefs keys in mods .kt
    mods_dir = repo_root / "app" / "src" / "main" / "java" / "tv" / "withaibuild" / "customiuizer" / "mods"
    kt_files = list(mods_dir.rglob("*.kt")) if mods_dir.is_dir() else []
    kt_keys = _extract_code_keys(repo_root, kt_files, "kotlin", "kt")

    # 4. Resource names in MainModule.java and mods .kt
    resource_files = ([main_module] if main_module.is_file() else []) + kt_files
    resource_keys = _extract_resource_names(repo_root, resource_files)

    def _empty_feature() -> dict[str, Any]:
        return {
            "sourceFile": "",
            "installer_func": None,
            "evidence": "",
            "line": 0,
            "xmlSource": "",
            "defaultValue": "",
            "keys": [],
            "lazy_meta": None,
        }

    def _ensure(canonical: str) -> dict[str, Any]:
        if canonical not in features:
            features[canonical] = _empty_feature()
            features[canonical]["lazy_meta"] = _feature_meta_for_key(canonical, lazy_specs)
        return features[canonical]

    # Merge preference keys from MainModule and mods; prefer code source over XML.
    code_order = [main_keys, kt_keys]
    for code_map in code_order:
        for canonical, meta in code_map.items():
            f = _ensure(canonical)
            if not f.get("sourceFile"):
                f["sourceFile"] = meta.get("sourceFile", "")
                f["installer_func"] = meta.get("installer_func")
                f["evidence"] = meta.get("evidence", "")
                f["line"] = meta.get("line", 0)
            for k in meta.get("keys", []):
                if k not in f["keys"]:
                    f["keys"].append(k)
            f["lazy_meta"] = _feature_meta_for_key(canonical, lazy_specs)

    # Add XML-only keys, merging aliases under their canonical form.
    for key, meta in xml_meta.items():
        canonical = canonical_preference_key(key)
        f = _ensure(canonical)
        if key not in f["keys"]:
            f["keys"].append(key)
        if not f.get("xmlSource"):
            f["xmlSource"] = meta.get("xmlSource", "")
            f["defaultValue"] = meta.get("defaultValue", "")
        if not f.get("evidence"):
            f["evidence"] = f'android:key in {meta.get("xmlSource", "")}'

    # Add resource keys (no canonicalisation; theme/fake keys are synthetic)
    for key, meta in resource_keys.items():
        if key not in features:
            features[key] = {**_empty_feature(), **meta}

    return features


def _build_installer(meta: dict[str, Any]) -> str:
    func = meta.get("installer_func")
    src = meta.get("sourceFile", "")
    if not src or src == "":
        return "UNKNOWN"
    if func:
        return f"{src}:{func}"
    return "UNKNOWN"


TARGET_MAP = {
    "SETTINGS_APP": "tv.withaibuild.customiuizer.r14",
    "SYSTEM_PACKAGE": "android",
    "SYSTEM_UI": "com.android.systemui",
    "LAUNCHER": "com.miui.home",
    "SYSTEM_SERVER": "android",
    "ANY": "UNKNOWN",
}

PHASE_MAP = {
    "MODULE_LOADED": "UNKNOWN",
    "SYSTEM_SERVER_STARTING": "SYSTEM_SERVER_START",
    "PACKAGE_READY": "PACKAGE_READY",
    "PREFS_READY": "UNKNOWN",
    "APPLICATION_ATTACHED": "APPLICATION_ATTACH",
    "SYSTEM_UI_INITIALIZED": "SYSTEMUI_INIT",
    "LAUNCHER_READY": "LAUNCHER_READY",
}


def _extract_set_definitions(text: str) -> dict[str, set[str]]:
    """Find top-level `val NAME = setOf("a", "b")` and const setOf definitions."""
    sets: dict[str, set[str]] = {}
    # Kotlin: val CHARGING_INFO_OBSERVED_KEYS = setOf("...", "...")
    for m in re.finditer(
        r'(?:val|const val)\s+(\w+)\s*=\s*setOf\((.*?)\)',
        text,
        re.DOTALL,
    ):
        name, body = m.groups()
        keys = set(re.findall(r'"([^"]+)"', body))
        if keys:
            sets[name] = keys
    return sets


def _extract_literal_string_keys(text: str) -> set[str]:
    """Return all literal string preference keys used in observer conditions."""
    keys: set[str] = set()

    # set definitions for resolving names
    set_defs = _extract_set_definitions(text)

    # Direct `if (key == "foo")` or `if ("foo" == key)`
    for m in re.finditer(
        r'if\s*\(\s*(?:key|\w+)\s*(?:==|!=)\s*"([^"]+)"\s*\)',
        text,
    ):
        keys.add(m.group(1))

    # Java `if ("foo".equals(key))` / `if (!"foo".equals(key))`
    for m in re.finditer(
        r'"([^"]+)"\s*\.\s*equals\s*\(\s*(?:key|\w+)\s*\)',
        text,
    ):
        keys.add(m.group(1))

    # `when (key) { "foo" -> ...; "bar" -> ... }`
    for m in re.finditer(
        r'when\s*\(\s*(?:key|\w+)\s*\)\s*\{([^}]*)\}',
        text,
    ):
        keys.update(re.findall(r'"([^"]+)"', m.group(1)))

    # `if (key !in SET) return`  -> the SET keys are observed
    for m in re.finditer(
        r'if\s*\(\s*(?:key|\w+)\s*!in\s+(\w+)\s*\)\s*(?:return|return@)',
        text,
    ):
        set_name = m.group(1)
        if set_name in set_defs:
            keys.update(set_defs[set_name])

    # `if (key in SET)` / `if (SET.contains(key))` -> the branch handles those keys
    for m in re.finditer(
        r'if\s*\(\s*(?:(?:key|\w+)\s*in\s+(\w+)|(\w+)\.\s*contains\s*\(\s*(?:key|\w+)\s*\))\s*\)',
        text,
    ):
        set_name = m.group(1) or m.group(2)
        if set_name in set_defs:
            keys.update(set_defs[set_name])

    # `if (key != null && key in SET)` / `if (key != null && key in SET) return` handled above for `in SET`
    # also `if (key != null || key in SET) return` -> SET keys observed
    for m in re.finditer(
        r'if\s*\(\s*(?:key|\w+)\s*!=\s*null\s*\|\|\s*(?:key|\w+)\s*in\s+(\w+)\s*\)\s*(?:return|return@)',
        text,
    ):
        set_name = m.group(1)
        if set_name in set_defs:
            keys.update(set_defs[set_name])

    # `if (key != null && key != "foo") return` -> "foo" is the only handled key
    for m in re.finditer(
        r'if\s*\(\s*(?:key|\w+)\s*!=\s*null\s*&&\s*(?:key|\w+)\s*!=\s*"([^"]+)"\s*\)\s*(?:return|return@)',
        text,
    ):
        keys.add(m.group(1))

    return keys


def _extract_observer_keys(repo_root: Path) -> dict[str, set[str]]:
    """Discover observed preference keys per source file.

    Returns a map of relative source path -> set of canonical observed keys.
    The extraction is conservative: it only marks a key as observed when the
    source contains an exact literal match in a preference observer callback.
    """
    observed: dict[str, set[str]] = {}
    mod_dir = repo_root / "app" / "src" / "main" / "java" / "tv" / "withaibuild" / "customiuizer" / "mods"
    if not mod_dir.is_dir():
        return observed
    for fp in sorted(mod_dir.rglob("*.kt")):
        rel = _rel(repo_root, fp)
        text = fp.read_text(encoding="utf-8")
        # Only consider files that actually register an observer.
        if "observePreferenceChange" not in text and "PreferenceObserver" not in text:
            continue
        keys = _extract_literal_string_keys(text)
        if keys:
            observed[rel] = {canonical_preference_key(k) for k in keys}
    return observed


def _is_observed_key(
    canonical_key: str,
    preference_keys: list[str],
    observer_keys: dict[str, set[str]],
    source_file: str,
) -> tuple[bool, str]:
    """Return (is_observed, evidence_source) for the canonical key."""
    if not source_file:
        return False, ""
    keys = observer_keys.get(source_file, set())
    if canonical_key in keys:
        return True, source_file
    for k in preference_keys:
        if canonical_preference_key(k) in keys:
            return True, source_file
    return False, ""


def _build_entry(
    canonical_key: str,
    meta: dict[str, Any],
    feature_id: str,
    observer_keys: dict[str, set[str]],
) -> dict[str, Any]:
    source_file = meta.get("sourceFile", "")
    installer = _build_installer(meta)
    func = meta.get("installer_func")
    lazy = meta.get("lazy_meta") or {}

    is_resource = canonical_key.startswith(("theme:", "fake:"))
    is_xml_only = not is_resource and meta.get("xmlSource") and not func

    # Prefer LazyFeatureSpec metadata (from canonical feature registry) for
    # target package and install phase.  Restart target is not inherited
    # from FeatureTarget; it must come from per-key runtime evidence.
    if lazy:
        target_enum = lazy.get("target", "")
        phase_enum = lazy.get("phase", "")
        target_package = TARGET_MAP.get(target_enum, _target_package_from_path(source_file))
        install_phase = PHASE_MAP.get(phase_enum, _infer_install_phase(func, source_file))
    else:
        target_package = _target_package_from_path(source_file)
        install_phase = _infer_install_phase(func, source_file)

    preference_keys = meta.get("keys", [canonical_key])
    if canonical_key not in preference_keys:
        preference_keys.append(canonical_key)

    is_master = bool(lazy) and canonical_key == lazy.get("preferenceKey")
    observed, observed_src = _is_observed_key(
        canonical_key, preference_keys, observer_keys, source_file
    )

    if is_resource:
        runtime_mode = "RESOURCE_REPLACEMENT"
        enable_effect = "Replaces or injects the resource value"
        disable_effect = "No resource replacement"
        value_change_effect = "Resource replacement value is updated"
        hot_reloadable = False
        restart_target = "NONE"
        confidence = "INFERRED"
    elif is_xml_only:
        install_phase = "APP_UI_ONLY"
        runtime_mode = "UNKNOWN"
        target_package = "UNKNOWN"
        restart_target = "UNKNOWN"
        enable_effect = "UNKNOWN"
        disable_effect = "UNKNOWN"
        value_change_effect = "UNKNOWN"
        hot_reloadable = False
        confidence = "INFERRED"
    else:
        enable_effect = "Applies hook when enabled"
        disable_effect = "Restores default behavior"

        if is_master:
            # Master key controls feature installation.  The value is read at
            # install time by isEnabled() and may also be observed by an
            # already-installed hook for secondary effects, but the
            # install/uninstall effect is not live.
            runtime_mode = "CAPTURED_AT_INSTALL"
            hot_reloadable = False
            restart_target = "UNKNOWN"
            value_change_effect = "Value change may require process restart to install/uninstall the feature"
        elif observed:
            runtime_mode = "OBSERVER_PUSH"
            hot_reloadable = True
            restart_target = "NONE"
            value_change_effect = "Value change is pushed to the observer and applied immediately"
        else:
            runtime_mode = "UNKNOWN"
            hot_reloadable = False
            restart_target = "UNKNOWN"
            value_change_effect = "Updates applied hook behavior"

        if is_master:
            confidence = "INFERRED"
        elif observed:
            confidence = "CODE_VERIFIED"
        elif install_phase != "UNKNOWN" and ("MainModule.java" in source_file or lazy):
            confidence = "INFERRED"
        else:
            confidence = "UNKNOWN"

    feature_name = meta.get("resourceName", lazy.get("name")) or canonical_key
    # Keep the name human-readable; don't use the synthetic theme:/fake: prefix in display.
    if is_resource and ":" in feature_name:
        feature_name = feature_name.split(":")[-1]

    notes = ""
    if is_master:
        notes = (
            "Master key controls feature installation at the declared install phase. "
            "A value change may require the host process to restart before "
            "install/uninstall takes effect."
        )
    elif observed:
        notes = f"Observed by PreferenceObserver in {observed_src}."

    return {
        "featureId": feature_id,
        "featureName": feature_name,
        "preferenceKeys": preference_keys,
        "xmlSource": meta.get("xmlSource", ""),
        "defaultValue": meta.get("defaultValue", ""),
        "sourceFile": source_file,
        "installer": installer,
        "targetPackage": target_package,
        "installPhase": install_phase,
        "runtimeReadMode": runtime_mode,
        "enableEffect": enable_effect,
        "disableEffect": disable_effect,
        "valueChangeEffect": value_change_effect,
        "restartTarget": restart_target,
        "hotReloadable": hot_reloadable,
        "confidence": confidence,
        "evidence": meta.get("evidence", ""),
        "notes": notes,
    }


def generate_inventory(
    repo_root: Path,
    features: dict[str, dict[str, Any]],
    observer_keys: dict[str, set[str]] | None = None,
) -> dict[str, Any]:
    if observer_keys is None:
        observer_keys = _extract_observer_keys(repo_root)
    seen: set[str] = set()
    entries = []
    for key in sorted(features):
        feature_id = _sanitize_id(key, seen)
        entry = _build_entry(key, features[key], feature_id, observer_keys)
        entries.append(entry)
    return {
        "schemaVersion": 1,
        "contractName": "CustoMIUIzer A14 feature effect and restart semantics",
        "romFamily": "HyperOS 1 / Android 14",
        "androidApi": 34,
        "exclusions": [],
        "entries": entries,
    }


def load_inventory(path: Path) -> dict[str, Any]:
    with path.open("r", encoding="utf-8") as f:
        return json.load(f)


def load_schema(path: Path) -> dict[str, Any]:
    with path.open("r", encoding="utf-8") as f:
        return json.load(f)


def _collect_enum_values(schema: dict[str, Any], field: str) -> set[str]:
    """Walk schema definitions for the enum of a field."""
    entry = schema.get("definitions", {}).get("featureEntry", {})
    props = entry.get("properties", {})
    prop = props.get(field, {})
    return set(prop.get("enum", []))


def _collect_required(schema: dict[str, Any]) -> set[str]:
    entry = schema.get("definitions", {}).get("featureEntry", {})
    return set(entry.get("required", []))


def _entry_value_types_ok(entry: dict[str, Any]) -> list[str]:
    errors = []
    if not isinstance(entry.get("featureId"), str):
        errors.append("featureId must be a string")
    if not isinstance(entry.get("featureName"), str):
        errors.append("featureName must be a string")
    if not isinstance(entry.get("preferenceKeys"), list) or not entry.get("preferenceKeys"):
        errors.append("preferenceKeys must be a non-empty list")
    if not isinstance(entry.get("sourceFile"), str):
        errors.append("sourceFile must be a string")
    if not isinstance(entry.get("installer"), str):
        errors.append("installer must be a string")
    if not isinstance(entry.get("targetPackage"), str):
        errors.append("targetPackage must be a string")
    if not isinstance(entry.get("installPhase"), str):
        errors.append("installPhase must be a string")
    if not isinstance(entry.get("runtimeReadMode"), str):
        errors.append("runtimeReadMode must be a string")
    if not isinstance(entry.get("enableEffect"), str):
        errors.append("enableEffect must be a string")
    if not isinstance(entry.get("disableEffect"), str):
        errors.append("disableEffect must be a string")
    if not isinstance(entry.get("valueChangeEffect"), str):
        errors.append("valueChangeEffect must be a string")
    if not isinstance(entry.get("restartTarget"), str):
        errors.append("restartTarget must be a string")
    if not isinstance(entry.get("hotReloadable"), bool):
        errors.append("hotReloadable must be a boolean")
    if not isinstance(entry.get("confidence"), str):
        errors.append("confidence must be a string")
    if not isinstance(entry.get("evidence"), str):
        errors.append("evidence must be a string")
    return errors


def _validate_schema_shape(schema: dict[str, Any]) -> list[str]:
    errors = []
    required_root = {"schemaVersion", "contractName", "romFamily", "androidApi", "exclusions", "entries"}
    for k in required_root:
        if k not in schema:
            errors.append(f"Schema requires root field '{k}'")
    if not isinstance(schema.get("exclusions"), list):
        errors.append("root 'exclusions' must be a list")
    return errors


def validate_inventory(
    repo_root: Path,
    inventory: dict[str, Any],
    schema: dict[str, Any],
    discovered: set[str] | None = None,
) -> tuple[int, list[str]]:
    """Validate inventory and compare against discovered keys.

    Returns (exit_code, errors). 0 = ok, 1 = validation/coverage, 2 = schema/input.
    """
    errors: list[str] = []

    shape_errors = _validate_schema_shape(inventory)
    if shape_errors:
        # These are schema/input errors (exit 2)
        return 2, shape_errors

    required = _collect_required(schema)
    phase_enum = _collect_enum_values(schema, "installPhase")
    runtime_enum = _collect_enum_values(schema, "runtimeReadMode")
    restart_enum = _collect_enum_values(schema, "restartTarget")
    confidence_enum = _collect_enum_values(schema, "confidence")

    entries = inventory.get("entries", [])
    if not isinstance(entries, list):
        return 2, ["'entries' must be a list"]

    seen_ids: set[str] = set()
    seen_keys: set[str] = set()

    for i, entry in enumerate(entries):
        if not isinstance(entry, dict):
            errors.append(f"entry[{i}] is not an object")
            continue
        for field in required:
            if field not in entry:
                errors.append(f"entry[{i}] missing required field '{field}'")
        errors.extend(_entry_value_types_ok(entry))

        if entry.get("featureId") in seen_ids:
            errors.append(f"duplicate featureId: {entry.get('featureId')}")
        if "featureId" in entry:
            seen_ids.add(entry["featureId"])

        for key in entry.get("preferenceKeys", []):
            if key in seen_keys:
                errors.append(f"duplicate preferenceKeys value: {key}")
            seen_keys.add(key)

        if entry.get("installPhase") not in phase_enum:
            errors.append(f"entry[{i}] illegal installPhase: {entry.get('installPhase')}")
        if entry.get("runtimeReadMode") not in runtime_enum:
            errors.append(f"entry[{i}] illegal runtimeReadMode: {entry.get('runtimeReadMode')}")
        if entry.get("restartTarget") not in restart_enum:
            errors.append(f"entry[{i}] illegal restartTarget: {entry.get('restartTarget')}")
        if entry.get("confidence") not in confidence_enum:
            errors.append(f"entry[{i}] illegal confidence: {entry.get('confidence')}")

        # sourceFile existence
        src = entry.get("sourceFile", "")
        if src and src != "UNKNOWN" and src != "":
            if not (repo_root / src).is_file():
                errors.append(f"entry[{i}] sourceFile does not exist: {src}")

        # installer existence: if it has a colon, left part is file; if just UNKNOWN, skip
        inst = entry.get("installer", "")
        if inst and inst != "UNKNOWN" and ":" in inst:
            file_part = inst.split(":", 1)[0]
            if file_part and not (repo_root / file_part).is_file():
                errors.append(f"entry[{i}] installer file does not exist: {file_part}")

    # exclusions reason check at root
    for i, ex in enumerate(inventory.get("exclusions", [])):
        reason = ex.get("reason", "")
        if not reason or not isinstance(reason, str):
            errors.append(f"exclusion[{i}] missing a reason")

    # coverage comparison
    if discovered is not None:
        excluded_keys = {ex.get("preferenceKey", "") for ex in inventory.get("exclusions", [])}
        missing = []
        for key in discovered:
            if key not in seen_keys and key not in excluded_keys:
                missing.append(key)
        if missing:
            for key in sorted(missing)[:20]:
                errors.append(f"discovered key not in inventory or exclusions: {key}")
            if len(missing) > 20:
                errors.append(f"... and {len(missing) - 20} more unlisted keys")

    if errors:
        return 1, errors
    return 0, []


def generate_markdown(inventory: dict[str, Any]) -> str:
    lines: list[str] = []
    lines.append("# Feature Effect and Restart Matrix")
    lines.append("")
    lines.append("Generated from `feature-semantics/a14.json`.")
    lines.append("")

    # Group by restartTarget, then installPhase
    grouped: dict[str, dict[str, list[dict[str, Any]]]] = defaultdict(lambda: defaultdict(list))
    for entry in inventory.get("entries", []):
        rt = entry.get("restartTarget", "UNKNOWN")
        ip = entry.get("installPhase", "UNKNOWN")
        grouped[rt][ip].append(entry)

    for rt in sorted(grouped):
        lines.append(f"## restartTarget: {rt}")
        for ip in sorted(grouped[rt]):
            lines.append(f"### installPhase: {ip}")
            lines.append("")
            lines.append("| Feature | preference key | target package | enable | disable | value-change | evidence | confidence |")
            lines.append("|--------|----------------|----------------|--------|---------|--------------|----------|------------|")
            for entry in sorted(grouped[rt][ip], key=lambda e: e.get("featureId", "")):
                pkeys = ", ".join(entry.get("preferenceKeys", []))
                ev = entry.get("evidence", "").replace("|", "\\|")
                lines.append(
                    f'| {entry.get("featureName", "").replace("|", "\\|")} '
                    f'| {pkeys.replace("|", "\\|")} '
                    f'| {entry.get("targetPackage", "")} '
                    f'| {entry.get("enableEffect", "").replace("|", "\\|")} '
                    f'| {entry.get("disableEffect", "").replace("|", "\\|")} '
                    f'| {entry.get("valueChangeEffect", "").replace("|", "\\|")} '
                    f'| {ev} '
                    f'| {entry.get("confidence", "")} |'
                )
            lines.append("")

    return "\n".join(lines)


def cmd_init(args: argparse.Namespace) -> int:
    features = discover_features(args.repo_root)
    inventory = generate_inventory(args.repo_root, features)
    INVENTORY_PATH.parent.mkdir(parents=True, exist_ok=True)
    with INVENTORY_PATH.open("w", encoding="utf-8", newline="\n") as f:
        json.dump(inventory, f, indent=2, ensure_ascii=False)
        f.write("\n")
    print(f"Wrote {len(inventory['entries'])} entries to {INVENTORY_PATH}")
    return 0


def cmd_validate(args: argparse.Namespace) -> int:
    if not SCHEMA_PATH.is_file():
        print(f"Schema not found: {SCHEMA_PATH}", file=sys.stderr)
        return 2
    if not INVENTORY_PATH.is_file():
        print(f"Inventory not found: {INVENTORY_PATH}", file=sys.stderr)
        return 2
    try:
        schema = load_schema(SCHEMA_PATH)
    except (json.JSONDecodeError, OSError) as e:
        print(f"Failed to load schema: {e}", file=sys.stderr)
        return 2
    try:
        inventory = load_inventory(INVENTORY_PATH)
    except (json.JSONDecodeError, OSError) as e:
        print(f"Failed to load inventory: {e}", file=sys.stderr)
        return 2
    try:
        discovered = set(discover_features(args.repo_root).keys())
    except OSError as e:
        print(f"Failed to rediscover features: {e}", file=sys.stderr)
        return 2
    code, errors = validate_inventory(args.repo_root, inventory, schema, discovered)
    if errors:
        for e in errors:
            print(e, file=sys.stderr)
    if code == 0:
        print("Validation passed")
    return code


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Feature semantics auditor for CustoMIUIzer A14")
    parser.add_argument("--repo-root", type=Path, default=REPO_ROOT, help="repository root (default: parent of tools/)")
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--init", action="store_true", help="generate feature-semantics/a14.json")
    group.add_argument("--validate", action="store_true", help="validate a14.json")
    args = parser.parse_args(argv)
    if args.init:
        return cmd_init(args)
    if args.validate:
        return cmd_validate(args)
    return 2


if __name__ == "__main__":
    sys.exit(main())
