#!/usr/bin/env python3
"""A14 preflight + agent-device assertions. 0=scenario passed, 1=failed, 2=pending.

Runs only an installed external CLI and an offline fixture. Never installs the
module, changes LSPosed scope, restarts a host or changes user preferences.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET

try:
    from tools.adb_stress import adb_command, device, sleep_and_lock
except ModuleNotFoundError:
    from adb_stress import adb_command, device, sleep_and_lock

ROOT = Path(__file__).resolve().parent.parent
FIXTURE = "org.example.a14fixture"
MODULE = "tv.withaibuild.customiuizer.r14"
HOSTS = ("com.miui.home", "com.android.systemui", "system_server")
HELPERS = ("com.callstack.agentdevice.snapshothelper", "com.callstack.agentdevice.imehelper")
SCENARIOS = ("fixture-smoke", "deck-open-return", "dock-overview-return", "island-notice")


def junit_passed(path: Path) -> bool:
    """A zero CLI exit is insufficient: demand executed, non-skipped test cases."""
    root = ET.parse(path).getroot()
    cases = list(root.iter("testcase"))
    suites = list(root.iter("testsuite"))
    return bool(cases) and bool(suites) and all(
        int(suite.get("tests", "0")) == len(list(suite.iter("testcase")))
        and all(int(suite.get(key, "0")) == 0 for key in ("failures", "errors", "skipped"))
        for suite in suites) and not any(
            child.tag in {"failure", "error", "skipped"} for case in cases for child in case)


def unlocked(power: str, policy: str) -> bool:
    return bool(re.search(r"\bmWakefulness=Awake\b", power)
                and re.search(r"\b(?:showing|mIsShowing)=false\b", policy)
                and not re.search(r"\b(?:showing|mIsShowing)=true\b", policy))


def exercise(args, output: Path) -> dict:
    report = {"status": "PENDING", "scenario": args.scenario,
              "installation": "UNVERIFIED", "hook_effect": "UNVERIFIED", "performance": "UNVERIFIED"}
    adb, serial, env = args.adb or shutil.which("adb"), None, dict(os.environ)
    fixture_owned, cli_started = False, False
    before_packages = set()
    def call(*parts):
        return adb_command(adb, serial, *parts)
    def packages():
        return set(call("shell", "pm", "list", "packages").splitlines())
    def cli(*parts):
        result = subprocess.run([args.node, str(args.agent_device), *parts], cwd=ROOT, env=env,
                                capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=120)
        return result
    try:
        if not adb:
            raise RuntimeError("ADB unavailable")
        serial = device(adb, args.serial)
        report["serial"] = serial  # private local evidence only
        report["rom"] = {key: call("shell", "getprop", key).strip() for key in (
            "ro.build.fingerprint", "ro.build.version.sdk", "ro.build.version.release",
            "ro.mi.os.version.name", "ro.product.cpu.abilist")}
        if (report["rom"]["ro.build.version.sdk"] != "34"
                or report["rom"]["ro.build.version.release"] != "14"
                or not report["rom"]["ro.mi.os.version.name"].startswith("OS1.")
                or "arm64-v8a" not in report["rom"]["ro.product.cpu.abilist"].split(",")):
            raise RuntimeError("Requires Android 14 / HyperOS 1 / arm64-v8a")
        if args.apk:
            paths = call("shell", "pm", "path", MODULE).strip().splitlines()
            if len(paths) != 1 or not paths[0].startswith("package:"):
                raise RuntimeError("Module base APK is unavailable or split; compare explicitly")
            remote = call("shell", "sha256sum", paths[0][8:]).split()[0]
            with args.apk.open("rb") as source:
                local = hashlib.file_digest(source, "sha256").hexdigest()
            if remote != local:
                raise RuntimeError("Installed module differs from selected APK")
            report["installation"] = {"status": "MATCHED", "sha256": local}
        if not args.run:
            report["status"] = "PREFLIGHT_ONLY"
            return report
        if not unlocked(call("shell", "dumpsys", "power"), call("shell", "dumpsys", "window", "policy")):
            raise RuntimeError("Device locked/asleep; unlock before running deterministic scenarios")
        if not args.agent_device or not args.agent_device.is_file() or not args.fixture_apk:
            raise RuntimeError("Supply installed --agent-device bin/agent-device.mjs and --fixture-apk")
        before_packages = packages()
        if f"package:{FIXTURE}" in before_packages:
            raise RuntimeError("Fixture already exists; refusing to replace unknown test state")
        env["AGENT_DEVICE_HOME"] = str(output / "agent-device-home")
        env["AGENT_DEVICE_NO_UPDATE_NOTIFIER"] = "1"
        # Isolate from existing remote daemon/provider configuration.
        for key in ("AGENT_DEVICE_DAEMON_BASE_URL", "AGENT_DEVICE_DAEMON_AUTH_TOKEN", "AGENT_DEVICE_CONFIG"):
            env.pop(key, None)
        fixture_owned = True  # also clean a partial install if the transport fails
        installed = call("install", str(args.fixture_apk.resolve()))
        if "Success" not in installed:
            raise RuntimeError("Fixture installation did not report Success")
        call("shell", "pm", "grant", FIXTURE, "android.permission.POST_NOTIFICATIONS")
        before = {host: call("shell", "pidof", host).strip() for host in HOSTS}
        if not all(before.values()):
            raise RuntimeError("Expected A14 hosts are not all running")
        report["before_pids"] = before
        junit = output / "junit.xml"
        cli_started = True
        result = cli("test", str(ROOT / "tools/device" / f"{args.scenario}.ad"),
                     "--platform", "android", "--serial", serial, "--no-test-ime", "--retries", "0",
                     "--timeout", "60000", "--artifacts-dir", str(output / "artifacts"), "--reporter", f"junit:{junit}")
        (output / "automation.log").write_text(result.stdout + result.stderr, encoding="utf-8")
        after = {host: call("shell", "pidof", host).strip() for host in HOSTS}
        report["after_pids"] = after
        report["status"] = "SCENARIO_PASSED" if result.returncode == 0 and junit_passed(junit) and before == after else "FAILED"
        report["limits"] = "UI assertions and stable host PIDs only. Enabled feature, exact geometry, per-hook activation, other ROMs and performance require separate evidence."
    except (OSError, RuntimeError, ValueError, IndexError, ET.ParseError, subprocess.SubprocessError) as error:
        report["error"] = str(error)
    finally:
        cleanup = []
        if cli_started:
            try:
                stopped = cli("daemon", "stop", "--clean", "--state-dir", env["AGENT_DEVICE_HOME"])
                if stopped.returncode:
                    cleanup.append("Owned automation daemon cleanup failed")
            except (OSError, subprocess.SubprocessError) as error:
                cleanup.append(str(error))
        if fixture_owned:
            try:
                if "Success" not in call("uninstall", FIXTURE):
                    cleanup.append("Fixture uninstall not confirmed")
                for helper in HELPERS:
                    if f"package:{helper}" not in before_packages and f"package:{helper}" in packages():
                        if "Success" not in call("uninstall", helper):
                            cleanup.append("Owned helper uninstall not confirmed")
                # Unknown additions are never uninstalled automatically.
                report["added_packages"] = sorted(packages() - before_packages)
                if report["added_packages"]:
                    cleanup.append("New packages remain; inspect automation log and remove only owned helpers")
            except (RuntimeError, OSError, subprocess.SubprocessError) as error:
                cleanup.append(str(error))
        if args.run and serial:
            try:
                sleep_and_lock(adb, serial)
                report["screen_locked"] = True
            except (RuntimeError, OSError, subprocess.SubprocessError) as error:
                cleanup.append(str(error))
        if cleanup:
            report.update(status="PENDING", cleanup_errors=cleanup)
    return report


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb")
    parser.add_argument("--serial")
    parser.add_argument("--apk", type=Path, help="candidate to compare against installed module; never installs it")
    parser.add_argument("--run", action="store_true")
    parser.add_argument("--scenario", choices=SCENARIOS, default="fixture-smoke")
    parser.add_argument("--agent-device", type=Path, help="path to installed bin/agent-device.mjs")
    parser.add_argument("--node", default="node")
    parser.add_argument("--fixture-apk", type=Path)
    parser.add_argument("--output", type=Path, default=ROOT / ".di-evidence/device-verify" / datetime.now(timezone.utc).strftime("%Y%m%d-%H%M%S"))
    args = parser.parse_args(argv)
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=False)
    report = exercise(args, output)
    (output / "result.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"{report['status']}: {output}")
    return 0 if report["status"] == "SCENARIO_PASSED" else 1 if report["status"] == "FAILED" else 2


if __name__ == "__main__":
    raise SystemExit(main())
