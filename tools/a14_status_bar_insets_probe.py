#!/usr/bin/env python3
"""A14 status bar / WindowInsets consistency diagnostic probe.

Read-only ADB diagnostic tool. Does not modify the device.
"""
from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
import tempfile
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

SCHEMA_VERSION = 1
PACKAGE_RE = re.compile(r"^[A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)+$")

# base argv lists for ADB commands; serial is inserted after 'adb'
ADB_COMMANDS = {
    "devices": ["adb", "devices", "-l"],
    "wm_size": ["adb", "shell", "wm", "size"],
    "wm_density": ["adb", "shell", "wm", "density"],
    "dumpsys_window_displays": ["adb", "shell", "dumpsys", "window", "displays"],
    "dumpsys_window_windows": ["adb", "shell", "dumpsys", "window", "windows"],
    "dumpsys_window_insets": ["adb", "shell", "dumpsys", "window", "insets"],
    "dumpsys_activity_activities": ["adb", "shell", "dumpsys", "activity", "activities"],
    "dumpsys_display": ["adb", "shell", "dumpsys", "display"],
}


def _is_unsupported_output(stdout: str, stderr: str) -> bool:
    combined = (stdout + "\n" + stderr).lower()
    return any(
        m in combined
        for m in [
            "unrecognized",
            "can't find service",
            "does not exist",
            "unknown command",
            "error: unrecognized",
        ]
    )


class AdbRunner:
    def __init__(
        self,
        serial: str | None = None,
        timeout: float = 30.0,
        verbose: bool = False,
        fixture: dict[str, str] | None = None,
    ) -> None:
        self.serial = serial
        self.timeout = timeout
        self.verbose = verbose
        self.fixture = fixture or {}

    def _build(self, base: list[str]) -> list[str]:
        if self.serial:
            return ["adb", "-s", self.serial] + base[1:]
        return base

    def run(self, name: str, source: str, base: list[str]) -> dict[str, Any]:
        if self.fixture and name in self.fixture:
            out = self.fixture[name]
            return {
                "argv": ["fixture", name],
                "exitCode": 0,
                "stdout": out,
                "stderr": "",
                "supported": not _is_unsupported_output(out, ""),
                "source": source,
            }

        argv = self._build(base)
        try:
            proc = subprocess.run(argv, capture_output=True, text=True, timeout=self.timeout, shell=False)
            stdout, stderr = proc.stdout or "", proc.stderr or ""
            if self.verbose:
                print(f"[adb] {' '.join(argv)} -> {proc.returncode}", file=sys.stderr)
            return {
                "argv": argv,
                "exitCode": proc.returncode,
                "stdout": stdout,
                "stderr": stderr,
                "supported": proc.returncode == 0 and not _is_unsupported_output(stdout, stderr),
                "source": source,
            }
        except FileNotFoundError:
            return {"argv": argv, "exitCode": 127, "stdout": "", "stderr": "adb not found", "supported": False, "source": source}
        except subprocess.TimeoutExpired as exc:
            return {"argv": argv, "exitCode": -1, "stdout": exc.stdout or "", "stderr": exc.stderr or "", "supported": False, "source": source}


def _nums(text: str, count: int = 4) -> list[int] | None:
    vals = re.findall(r"(\d+)", text)
    if len(vals) >= count:
        return [int(v) for v in vals[:count]]
    return None


def _rect(text: str) -> dict[str, int] | None:
    vals = _nums(text, 4)
    return {"left": vals[0], "top": vals[1], "right": vals[2], "bottom": vals[3]} if vals else None


def parse_wm_size(text: str) -> dict[str, Any]:
    out: dict[str, Any] = {}
    m = re.search(r"Override size:\s*(\d+x\d+)", text)
    if not m:
        m = re.search(r"Physical size:\s*(\d+x\d+)", text)
    if m:
        out["overrideSize"] = m.group(1)
        w, h = m.group(1).split("x")
        out["width"] = int(w)
        out["height"] = int(h)
    m = re.search(r"Physical size:\s*(\d+x\d+)", text)
    if m:
        out["physicalSize"] = m.group(1)
    return out


def parse_wm_density(text: str) -> dict[str, Any]:
    out: dict[str, Any] = {}
    m = re.search(r"Override density:\s*(\d+)", text)
    if not m:
        m = re.search(r"Physical density:\s*(\d+)", text)
    if m:
        out["densityDpi"] = int(m.group(1))
        out["density"] = out["densityDpi"] / 160.0
    return out


def parse_window_displays(text: str) -> dict[str, Any]:
    out: dict[str, Any] = {}
    m = re.search(r"(?:mDisplayId|displayId)\s*=\s*(\d+)", text)
    if m:
        out["displayId"] = int(m.group(1))
    m = re.search(r"(?:rotation|mRotation)\s*=\s*(?:ROTATION_)?(\d+)", text)
    if m:
        out["rotation"] = int(m.group(1))
    return out


def find_window(text: str, package: str) -> dict[str, Any] | None:
    # split on "Window #" or "Window {" lines
    blocks = re.split(r"\n(?=\s*Window\s*(?:#|\{))", text)
    chosen = None
    for block in blocks:
        if package in block:
            chosen = block
            break
    if not chosen:
        return None

    out: dict[str, Any] = {"packageName": package}
    for pattern, key in [
        (r"mActivityComponent\s*=\s*([^\s]+)", "activity"),
        (r"mOwnerPackageName\s*=\s*([^\s]+)", "packageName"),
    ]:
        m = re.search(pattern, chosen)
        if m:
            out[key] = m.group(1)

    m = re.search(r"Window\s*(?:#\s*\d+\s*)?Window\{([^}]+)\}", chosen)
    if m:
        out["windowToken"] = m.group(1)

    m = re.search(r"mWindowingMode\s*=\s*WINDOWING_MODE_(\w+)", chosen)
    if m:
        out["windowingMode"] = f"WINDOWING_MODE_{m.group(1)}"

    m = re.search(r"mDisplayId\s*=\s*(\d+)", chosen)
    if m:
        out["displayId"] = int(m.group(1))

    for key, pat in [
        ("windowFrame", r"mFrame\s*=\s*([^\n]+)"),
        ("contentFrame", r"mContentFrame\s*=\s*([^\n]+)"),
        ("visibleFrame", r"mVisibleFrame\s*=\s*([^\n]+)"),
    ]:
        m = re.search(pat, chosen)
        if m:
            rect = _rect(m.group(1))
            if rect:
                out[key] = rect

    m = re.search(r"layoutInDisplayCutoutMode\s*=\s*(LAYOUT_IN_DISPLAY_CUTOUT_MODE_\w+|\d+)", chosen)
    if m:
        out["layoutInDisplayCutoutMode"] = m.group(1)

    if re.search(r"decorFitsSystemWindows\s*=\s*(true|1)", chosen, re.I):
        out["decorFitsSystemWindows"] = True
    elif re.search(r"decorFitsSystemWindows\s*=\s*(false|0)", chosen, re.I):
        out["decorFitsSystemWindows"] = False

    m = re.search(r"mRequestedVisibleTypes\s*=\s*([\w\|]+)", chosen)
    if m:
        out["requestedVisibleTypes"] = m.group(1).split("|")

    evidence: list[str] = []
    if out.get("layoutInDisplayCutoutMode") == "LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES":
        evidence.append("LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES")
    if out.get("decorFitsSystemWindows") is False:
        evidence.append("decorFitsSystemWindows=false")
    if out.get("requestedVisibleTypes"):
        evidence.append(f"requestedVisibleTypes={','.join(out['requestedVisibleTypes'])}")
    out["edgeToEdgeEvidence"] = evidence

    return out


def parse_insets(text: str) -> dict[str, Any]:
    out: dict[str, Any] = {
        "statusBarsFrame": None,
        "statusBarsHeightPx": None,
        "statusBarsVisible": None,
        "statusBarsIgnoringVisibilityTopPx": None,
        "stableTopPx": None,
        "contentTopPx": None,
        "displayCutoutSafeTopPx": None,
        "displayCutoutBoundingRects": [],
    }
    blocks = re.split(r"\n(?=\s*(?:InsetsSource|Source))", text)
    for block in blocks:
        if "ITYPE_STATUS_BAR" not in block and ("statusBars" not in block or "Source" not in block):
            continue
        # frame
        m = re.search(r"(?:frame|mFrame)\s*=\s*([^\n]+)", block)
        if m:
            rect = _rect(m.group(1))
            if rect:
                out["statusBarsFrame"] = rect
                out["statusBarsHeightPx"] = rect["bottom"] - rect["top"]
        # visible
        m = re.search(r"visible\s*=\s*(true|false)", block, re.I)
        if m:
            out["statusBarsVisible"] = m.group(1).lower() == "true"
        # top inset
        m = re.search(r"(?:insets=Insets\{[^}]*top\s*=\s*|top\s*=\s*)(-?\d+)", block)
        if m:
            out["statusBarsIgnoringVisibilityTopPx"] = int(m.group(1))

    # fallback for statusBars height from a generic InsetsState block
    if out["statusBarsHeightPx"] is None:
        m = re.search(r"statusBars\s*\[[^\]]*\]\s*Insets\s*\{\s*([\s\S]*?)\}", text)
        if m:
            top = re.search(r"top\s*=\s*(-?\d+)", m.group(1))
            if top:
                out["statusBarsHeightPx"] = int(top.group(1))
                out["statusBarsIgnoringVisibilityTopPx"] = int(top.group(1))

    # stable / content insets
    for marker, key in [
        ("mStableInsets", "stableTopPx"),
        ("mContentInsets", "contentTopPx"),
    ]:
        m = re.search(rf"{re.escape(marker)}\s*=\s*\(?\s*Rect\(([^)]+)\)", text)
        if m:
            rect = _rect(f"Rect({m.group(1)})")
            if rect:
                out[key] = rect["bottom"]

    # display cutout
    cm = re.search(r"DisplayCutout\s*\{?\s*([\s\S]{0,1200})\}?", text)
    if cm:
        cut = cm.group(1)
        m = re.search(r"safeInsets\s*=\s*Rect\(([^)]+)\)", cut)
        if m:
            rect = _rect(f"Rect({m.group(1)})")
            if rect:
                out["displayCutoutSafeTopPx"] = rect["top"]
        for bbox in re.finditer(r"BoundingRect\{[^}]*at (\d+)\s*,\s*(\d+)\s*\[\s*(\d+)\s*x\s*(\d+)\s*\]\}", cut):
            out["displayCutoutBoundingRects"].append(
                {
                    "left": int(bbox.group(1)),
                    "top": int(bbox.group(2)),
                    "width": int(bbox.group(3)),
                    "height": int(bbox.group(4)),
                }
            )

    return out


def parse_cutout_fallback(text: str) -> dict[str, Any]:
    out: dict[str, Any] = {"safeTop": None, "rects": []}
    cm = re.search(r"DisplayCutout\s*\{?\s*([\s\S]{0,800})\}?", text)
    if cm:
        m = re.search(r"safeInsets\s*=\s*Rect\(([^)]+)\)", cm.group(1))
        if m:
            rect = _rect(f"Rect({m.group(1)})")
            if rect:
                out["safeTop"] = rect["top"]
    return out


def _first_serial(text: str) -> str | None:
    for line in text.splitlines():
        if "device" in line and not line.startswith("*"):
            parts = line.split()
            if len(parts) >= 2:
                return parts[0]
    return None


def _device_info(text: str, serial: str | None) -> dict[str, Any]:
    out: dict[str, Any] = {"product": None}
    if not serial:
        return out
    for line in text.splitlines():
        if line.startswith(serial):
            m = re.search(r"product:([^\s]+)", line)
            if m:
                out["product"] = m.group(1)
    return out


def _load_fixture(path: str) -> dict[str, str]:
    text = Path(path).read_text(encoding="utf-8")
    sections = re.split(r"^\s*=+\s*([A-Z_]+)\s*=+\s*$", text, flags=re.M)
    result: dict[str, str] = {}
    if len(sections) > 1:
        for i in range(1, len(sections), 2):
            name = sections[i].lower()
            body = sections[i + 1].strip()
            # map common section names to command names
            if "dumpsys_window_insets" in name:
                result["dumpsys_window_insets"] = body
            elif "dumpsys_window_windows" in name:
                result["dumpsys_window_windows"] = body
            elif "dumpsys_window_displays" in name:
                result["dumpsys_window_displays"] = body
            elif "dumpsys_display" in name:
                result["dumpsys_display"] = body
            elif "dumpsys_activity_activities" in name:
                result["dumpsys_activity_activities"] = body
            elif "wm_size" in name:
                result["wm_size"] = body
            elif "wm_density" in name:
                result["wm_density"] = body
            elif "devices" in name:
                result["devices"] = body
            else:
                result[name] = body
    return result


def run_probe(args: argparse.Namespace) -> dict[str, Any]:
    package = args.package
    if not PACKAGE_RE.match(package):
        raise ValueError(f"invalid package name: {package}")

    runner = AdbRunner(
        serial=args.serial,
        timeout=args.timeout,
        verbose=args.verbose,
        fixture=_load_fixture(args.fixture) if args.fixture else None,
    )

    raw_dir = Path(tempfile.mkdtemp(prefix="a14-sb-probe-"))

    commands: list[dict[str, Any]] = []
    results: dict[str, str] = {}

    for name, base in ADB_COMMANDS.items():
        result = runner.run(name, base[-2], base)
        if raw_dir:
            (raw_dir / f"{name}.txt").write_text(
                f"argv: {json.dumps(result['argv'])}\n"
                f"exit: {result['exitCode']}\n"
                f"supported: {result['supported']}\n"
                f"--- stdout ---\n{result['stdout']}\n"
                f"--- stderr ---\n{result['stderr']}",
                encoding="utf-8",
            )
        result.pop("argv", None)
        commands.append(result)
        results[name] = result["stdout"]

    # device metrics
    wm_size = parse_wm_size(results["wm_size"])
    wm_density = parse_wm_density(results["wm_density"])
    metrics: dict[str, Any] = {}
    metrics.update(wm_size)
    metrics.update(wm_density)

    serial = args.serial or _first_serial(results["devices"])
    device = _device_info(results["devices"], serial)
    device["sdk"] = None
    device["serial"] = serial
    device["displayId"] = 0
    device["rotation"] = 0

    display = parse_window_displays(results["dumpsys_window_displays"])
    device.update(display)
    if "width" in display and metrics.get("width") is None:
        metrics["width"] = display["width"]
    if "height" in display and metrics.get("height") is None:
        metrics["height"] = display["height"]

    window = find_window(results["dumpsys_window_windows"], package)
    if window:
        if window.get("displayId") is not None:
            device["displayId"] = window["displayId"]

    insets = parse_insets(results["dumpsys_window_insets"])
    if insets["displayCutoutSafeTopPx"] is None:
        cutout = parse_cutout_fallback(results["dumpsys_display"])
        if cutout and cutout["safeTop"] is not None:
            insets["displayCutoutSafeTopPx"] = cutout["safeTop"]

    # configured height
    configured_height_px: int | None = None
    if args.configured_height_dp is not None and metrics.get("densityDpi"):
        configured_height_px = round(args.configured_height_dp * metrics["densityDpi"] / 160)

    # analysis
    analysis: dict[str, Any] = {
        "classification": "INSUFFICIENT_EVIDENCE",
        "mismatchPx": None,
        "mismatchDp": None,
        "confidence": "LOW",
        "notes": [],
    }

    if configured_height_px is not None and insets["statusBarsHeightPx"] is not None:
        mismatch_px = configured_height_px - insets["statusBarsHeightPx"]
        analysis["mismatchPx"] = mismatch_px
        if metrics.get("densityDpi"):
            analysis["mismatchDp"] = round(mismatch_px * 160 / metrics["densityDpi"], 2)

        if abs(mismatch_px) <= 1:
            analysis["classification"] = "CONSISTENT"
            analysis["confidence"] = "HIGH"
            analysis["notes"].append(
                f"configured px ({configured_height_px}) matches statusBars source frame "
                f"height ({insets['statusBarsHeightPx']}) within 1px"
            )
        elif mismatch_px > 1:
            analysis["classification"] = "RESOURCE_GREATER_THAN_INSET"
            analysis["confidence"] = "HIGH"
            analysis["notes"].append(
                f"configured status bar height ({configured_height_px}px) is larger than "
                f"InsetsSource frame ({insets['statusBarsHeightPx']}px) by {analysis['mismatchDp']}dp"
            )
        else:
            analysis["classification"] = "INSET_GREATER_THAN_RESOURCE"
            analysis["confidence"] = "MEDIUM"
            analysis["notes"].append(
                f"InsetsSource frame ({insets['statusBarsHeightPx']}px) is larger than "
                f"configured height ({configured_height_px}px) by {abs(analysis['mismatchDp'] or 0)}dp"
            )
    else:
        analysis["notes"].append("Insufficient evidence to compare configured height with InsetsSource frame")
        if insets["statusBarsHeightPx"] is None:
            analysis["notes"].append("Could not determine statusBars InsetsSource frame")
        if configured_height_px is None:
            analysis["notes"].append("Could not compute configuredHeightPx (missing density or configured dp)")

    output: dict[str, Any] = {
        "schemaVersion": SCHEMA_VERSION,
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "device": {
            "serial": device.get("serial"),
            "product": device.get("product"),
            "sdk": device.get("sdk"),
            "displayId": device.get("displayId"),
            "rotation": device.get("rotation"),
            "densityDpi": metrics.get("densityDpi"),
            "density": metrics.get("density"),
            "physicalSize": metrics.get("physicalSize"),
            "overrideSize": metrics.get("overrideSize"),
        },
        "target": {
            "packageName": package,
            "activity": window.get("activity") if window else None,
            "windowToken": window.get("windowToken") if window else None,
            "windowingMode": window.get("windowingMode") if window else None,
            "edgeToEdgeEvidence": window.get("edgeToEdgeEvidence") if window else [],
        },
        "configured": {
            "heightDp": args.configured_height_dp,
            "heightPx": configured_height_px,
        },
        "insets": insets,
        "analysis": analysis,
        "commands": commands,
    }

    if args.keep_raw and raw_dir:
        output["rawDir"] = str(raw_dir)

    if not args.keep_raw:
        for p in raw_dir.iterdir():
            p.unlink()
        raw_dir.rmdir()

    return output


def main(argv=None) -> int:
    p = argparse.ArgumentParser(description="A14 status bar height / WindowInsets consistency diagnostic probe")
    p.add_argument("--serial", help="optional adb device serial")
    p.add_argument("--package", required=True, help="target package name")
    p.add_argument("--configured-height-dp", type=float, required=True, help="configured status bar height dp")
    p.add_argument("--output", required=True, help="JSON output path")
    p.add_argument("--timeout", type=float, default=30.0, help="adb command timeout (default 30s)")
    p.add_argument("--verbose", action="store_true", help="print adb command results")
    p.add_argument("--fixture", help="fixture file for offline testing")
    p.add_argument("--keep-raw", action="store_true", help="keep raw dumpsys output in TEMP")
    args = p.parse_args(argv)

    if not PACKAGE_RE.match(args.package):
        print(f"error: invalid package name: {args.package}", file=sys.stderr)
        return 2

    try:
        output = run_probe(args)
    except ValueError as e:
        print(f"error: {e}", file=sys.stderr)
        return 2
    except FileNotFoundError as e:
        print(f"error: fixture not found: {e}", file=sys.stderr)
        return 2

    out_path = Path(args.output)
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(json.dumps(output, indent=2), encoding="utf-8")
    if args.verbose:
        print(json.dumps(output, indent=2))
    print(f"Wrote {out_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
