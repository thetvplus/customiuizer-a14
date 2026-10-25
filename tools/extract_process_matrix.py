#!/usr/bin/env python3
"""Extract the A14 process/feature matrix from Kotlin source files.

This offline scanner does not modify source, build the project or access a device.
Complete JSON and CSV output is written to docs/rom-intelligence/.
"""

from __future__ import annotations

import csv
import json
import re
from pathlib import Path
from typing import Any

REPO_ROOT = Path(__file__).resolve().parent.parent
FEATURE_DIR = (
    REPO_ROOT
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
MAIN_MODULE = (
    REPO_ROOT
    / "app"
    / "src"
    / "main"
    / "java"
    / "tv"
    / "withaibuild"
    / "customiuizer"
    / "MainModule.java"
)
SCOPE_LIST = REPO_ROOT / "app" / "src" / "main" / "resources" / "META-INF" / "xposed" / "scope.list"


def parse_feature_ids() -> dict[str, dict[str, Any]]:
    text = (FEATURE_DIR / "FeatureIds.kt").read_text(encoding="utf-8")
    pattern = re.compile(
        r"data object (\w+) : FeatureId \{\s*override val id = (\d+)\s*override val name = \"([^\"]+)\"\s*\}",
        re.DOTALL,
    )
    result: dict[str, dict[str, Any]] = {}
    for m in pattern.finditer(text):
        result[m.group(1)] = {
            "featureIdInt": int(m.group(2)),
            "featureIdName": m.group(3),
        }
    return result


def parse_lazy_specs(text: str) -> list[dict[str, str]]:
    """Return list of dicts with fields from each LazyFeatureSpec(...) block."""
    specs: list[dict[str, str]] = []
    # Match LazyFeatureSpec( ... ) blocks, ending with a line that is just ) or ),
    pattern = re.compile(r"LazyFeatureSpec\((.*?)^[ \t]*\)(?:,|\s*\))", re.DOTALL | re.MULTILINE)
    for m in pattern.finditer(text):
        body = m.group(1)
        spec: dict[str, str] = {}
        for line in body.splitlines():
            line = line.strip()
            if not line or line in ("(", ")"):
                continue
            m2 = re.match(r"(\w+)\s*=\s*(.+?)(?:,\s*)?$", line)
            if not m2:
                continue
            key, val = m2.group(1), m2.group(2).strip()
            if val.startswith('"') and val.endswith('"'):
                val = val[1:-1]
            elif val.startswith("FeatureTarget."):
                val = val.split(".", 1)[1]
            elif val.startswith("InstallPhase."):
                val = val.split(".", 1)[1]
            spec[key] = val
        if "id" in spec:
            specs.append(spec)
    return specs


def extract_class_body(text: str, class_name: str) -> str:
    """Return the body text of `internal class class_name(...) : ... { ... }`."""
    pattern = re.compile(rf"internal class {re.escape(class_name)}\s*\([^)]*\)\s*:\s*[^{{]+{{", re.DOTALL)
    m = pattern.search(text)
    if not m:
        return ""
    start = m.end()
    brace = 1
    i = start
    while i < len(text) and brace > 0:
        if text[i] == "{":
            brace += 1
        elif text[i] == "}":
            brace -= 1
        i += 1
    return text[start : i - 1]


def extract_rhs(text: str, start: int) -> str:
    """Extract a possibly multiline Kotlin expression after an equals sign."""
    parts: list[str] = []
    balance = 0
    for line in text[start:].splitlines():
        stripped = line.strip()
        if not stripped and not parts:
            continue
        parts.append(stripped)
        balance += sum(stripped.count(ch) for ch in "({[")
        balance -= sum(stripped.count(ch) for ch in ")}]")
        if balance <= 0 and not stripped.endswith(("||", "&&", ",", "(")):
            break
    return " ".join(parts).strip()


def parse_install_hooks(text: str) -> dict[str, str]:
    hooks: dict[str, str] = {}
    # Find all internal class definitions in this file
    for m in re.finditer(r"internal class (\w+)\s*\(", text):
        cls = m.group(1)
        body = extract_class_body(text, cls)
        hm = re.search(r"override fun installHook\(\)\s*=\s*", body)
        if hm:
            hooks[cls] = extract_rhs(body, hm.end())
        else:
            hooks[cls] = "(default base install)"
    return hooks


def parse_evaluate_enabled(text: str) -> dict[str, str]:
    conds: dict[str, str] = {}
    for m in re.finditer(r"internal class (\w+)\s*\(", text):
        cls = m.group(1)
        body = extract_class_body(text, cls)
        em = re.search(r"fun evaluateEnabled\([^)]*\):\s*Boolean\s*=\s*", body)
        if em:
            conds[cls] = extract_rhs(body, em.end())
    return conds


def parse_main_module_routing() -> dict[str, str]:
    routing: dict[str, str] = {
        "android": "AndroidPackageInstaller",
        "com.baidu.input": "InputMethodInstaller",
        "com.baidu.input_mi": "InputMethodInstaller",
        "com.iflytek.inputmethod": "InputMethodInstaller",
        "com.iflytek.inputmethod.miui": "InputMethodInstaller",
        "com.sohu.inputmethod.sogou": "InputMethodInstaller",
        "com.sohu.inputmethod.sogou.xiaomi": "InputMethodInstaller",
        "com.google.android.inputmethod": "InputMethodInstaller",
        "com.touchtype.swiftkey": "InputMethodInstaller",
        "com.tencent.wetype": "InputMethodInstaller",
        "com.miui.miwallpaper": "MediaInstaller",
        "com.miui.screenshot": "MediaInstaller",
        "com.miui.gallery": "MediaInstaller",
        "com.android.systemui": "SystemUiInstaller",
        "com.miui.guardprovider": "GuardProviderInstaller",
        "com.android.incallui": "PhoneInstaller",
        "com.miui.securitycenter": "SecurityCenterInstaller",
        "com.miui.powerkeeper": "PowerKeeperInstaller",
        "com.android.settings": "SettingsInstaller",
        "com.miui.packageinstaller": "PackageInstallerRouter",
        "com.android.permissioncontroller": "PermissionControllerInstaller",
        "com.google.android.permissioncontroller": "PermissionControllerInstaller",
        "com.miui.home": "LauncherInstaller",
    }
    # Any package with enabled CommonPackageFeatures also runs the common registry.
    return routing


def map_processes(target: str, installer_base: str, pref: str, install_hook: str) -> tuple[list[str], list[str]]:
    if target == "SYSTEM_UI":
        return ["com.android.systemui"], ["miui.systemui.plugin (ClassLoader extracted at runtime)"]
    if target == "LAUNCHER":
        return ["com.miui.home"], ["third-party launchers (unless selected app sets)"]
    if target == "SYSTEM_PACKAGE":
        if installer_base == "SecurityCenterFeatures":
            if pref == "system_hidelowbatwarn":
                return ["com.miui.securitycenter", "com.miui.securitycenter.remote"], ["com.miui.securitycenter.bootaware"]
            return ["com.miui.securitycenter"], ["com.miui.securitycenter.bootaware"]
        if installer_base == "SettingsFeatures":
            return ["com.android.settings"], ["com.android.settings:remote"]
        if installer_base == "PowerKeeperFeatures":
            return ["com.miui.powerkeeper"], []
        if installer_base == "GuardProviderFeatures":
            return ["com.miui.guardprovider"], []
        if installer_base == "PhoneFeatures":
            return ["com.android.incallui"], []
        if installer_base == "PackageInstallerFeatures":
            return ["com.miui.packageinstaller"], []
        if installer_base == "MediaFeatures":
            packages = {
                "launcher_disable_wallpaperscale": "com.miui.miwallpaper",
                "system_screenshot": "com.miui.screenshot",
                "system_gallery_screenshots_path": "com.miui.gallery",
            }
            return [packages[pref]], []
        return ["android"], []
    if target == "SETTINGS_APP":
        return ["tv.withaibuild.customiuizer.r14"], []
    if target == "SYSTEM_SERVER":
        return ["system_server"], []
    if target == "ANY":
        if installer_base == "CommonPackageFeatures":
            if pref == "system_statusbarheight":
                return ["android", "com.android.systemui", "com.miui.home"], []
            return ["any scoped package where hasEnabledFeature() is true"], []
        if installer_base == "InputMethodFeatures":
            return [
                "com.baidu.input",
                "com.baidu.input_mi",
                "com.iflytek.inputmethod",
                "com.iflytek.inputmethod.miui",
                "com.sohu.inputmethod.sogou",
                "com.sohu.inputmethod.sogou.xiaomi",
                "com.google.android.inputmethod*",
                "com.touchtype.swiftkey",
                "com.tencent.wetype",
            ], ["packages outside scope.list (no callback with static scope)"]
        if installer_base == "GenericAppFeatures":
            return ["com.miui.home + selected packages"], []
        if installer_base == "AndroidPackageFeatures":
            return ["android"], []
        if installer_base == "MediaFeatures":
            return ["com.miui.miwallpaper", "com.miui.screenshot", "com.miui.gallery"], []
        return ["ANY"], []
    return ["?"], []


def main() -> int:
    feature_ids = parse_feature_ids()
    scope = {line.strip() for line in SCOPE_LIST.read_text(encoding="utf-8").splitlines() if line.strip()}
    routing = parse_main_module_routing()

    rows: list[dict[str, Any]] = []
    for feature_file in sorted(FEATURE_DIR.glob("*Features.kt")):
        if feature_file.name == "FeatureIds.kt":
            continue
        text = feature_file.read_text(encoding="utf-8")
        specs = parse_lazy_specs(text)
        if not specs:
            continue
        hooks = parse_install_hooks(text)
        conds = parse_evaluate_enabled(text)
        base_name = feature_file.stem

        for spec in specs:
            id_obj = spec.get("id", "")
            meta = feature_ids.get(id_obj, {})
            factory = spec.get("factory", "")
            cls = factory.split("(")[0].strip().lstrip("{").rstrip("}").strip() if factory else ""
            install_hook = hooks.get(cls, "")
            enable_cond = conds.get(cls, spec.get("enabled", ""))

            installer = base_name
            if base_name == "CommonPackageFeatures":
                installer = "CommonPackageFeatures (MainModule)"

            target = spec.get("target", "")
            pref = spec.get("preferenceKey", "")
            allowed, denied = map_processes(target, base_name, pref, install_hook)

            rows.append({
                "featureIdInt": meta.get("featureIdInt", -1),
                "featureIdName": meta.get("featureIdName", ""),
                "name": spec.get("name", ""),
                "preferenceKey": pref,
                "target": target,
                "phase": spec.get("phase", ""),
                "installer": installer,
                "installHook": install_hook,
                "enableCondition": enable_cond,
                "allowedProcess": "; ".join(allowed),
                "deniedProcess": "; ".join(denied),
            })

    rows.sort(key=lambda r: (r["featureIdInt"] if r["featureIdInt"] >= 0 else 9999, r["featureIdName"]))

    out_dir = REPO_ROOT / "docs" / "rom-intelligence"
    out_dir.mkdir(parents=True, exist_ok=True)

    (out_dir / "A14_PROCESS_MATRIX.json").write_text(
        json.dumps({"features": rows, "scope": sorted(scope), "routing": routing}, indent=2, ensure_ascii=False),
        encoding="utf-8",
        newline="\n",
    )

    with (out_dir / "A14_PROCESS_MATRIX.csv").open("w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(
            f,
            fieldnames=[
                "featureIdInt",
                "featureIdName",
                "name",
                "preferenceKey",
                "target",
                "phase",
                "installer",
                "installHook",
                "enableCondition",
                "allowedProcess",
                "deniedProcess",
            ],
            lineterminator="\n",
        )
        writer.writeheader()
        writer.writerows(rows)

    print(f"Wrote {len(rows)} features to {out_dir}/A14_PROCESS_MATRIX.json and .csv")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
