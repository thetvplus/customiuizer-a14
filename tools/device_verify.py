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
import statistics
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
import zipfile

try:
    from tools.adb_stress import adb_command, device, sleep_and_lock
except ModuleNotFoundError:
    from adb_stress import adb_command, device, sleep_and_lock

ROOT = Path(__file__).resolve().parent.parent
FIXTURE = "org.example.a14fixture"
MODULE = "tv.withaibuild.customiuizer.r14"
HOSTS = ("com.miui.home", "com.android.systemui", "system_server")
HELPERS = ("com.callstack.agentdevice.snapshothelper", "com.callstack.agentdevice.imehelper")
SCENARIOS = {name: {"script": name + ".ad", "proof": "UI_PATH"} for name in
             ("fixture-smoke", "deck-open-return", "dock-overview-return", "island-notice")}
SCENARIOS["island-takeover"] = {"script": None, "proof": "HOOK_EFFECT", "requires": "root, matched APK, controlled preferences"}
SCENARIOS["gesture-cancel"] = {"script": None, "proof": "UNVERIFIED", "implemented": False}


class AssertionFailure(RuntimeError):
    pass


def junit_passed(path: Path, script: Path | None = None) -> bool:
    """A zero CLI exit is insufficient: demand executed, non-skipped test cases."""
    root = ET.parse(path).getroot()
    cases = list(root.iter("testcase"))
    suites = list(root.iter("testsuite"))
    if script and (len(cases) != 1 or cases[0].get("name") != script.name
                   or Path(cases[0].get("file", "")).resolve() != script.resolve()):
        raise ValueError("JUnit belongs to another scenario")
    return bool(cases) and bool(suites) and all(
        int(suite.get("tests", "0")) == len(list(suite.iter("testcase")))
        and all(int(suite.get(key, "0")) == 0 for key in ("failures", "errors", "skipped"))
        for suite in suites) and not any(
            child.tag in {"failure", "error", "skipped"} for case in cases for child in case)


def scenario_result(code: int, path: Path, script: Path) -> str:
    passed = junit_passed(path, script)
    if code == 0 and passed:
        return "SCENARIO_PASSED"
    if code != 0:
        failures = ET.parse(path).getroot().findall(".//failure")
        # Only a concrete UI assertion failure is a failed scenario. Tool,
        # transport, helper and unknown failures remain insufficient evidence.
        if failures and all(any(reason in (f.text or "") for reason in (
                '"reason": "wait_target_absent"', '"reason": "selector_not_found"',
                '"code": "ASSERTION_FAILED"')) for f in failures):
            return "FAILED"
    return "PENDING"


def unlocked(power: str, policy: str) -> bool:
    return bool(re.search(r"\bmWakefulness=Awake\b", power)
                and re.search(r"\b(?:showing|mIsShowing)=false\b", policy)
                and not re.search(r"\b(?:showing|mIsShowing)=true\b", policy))


def island_window(text: str) -> tuple[int, int, int, int] | None:
    blocks = re.split(r"(?m)(?=\s*Window #\d+ Window\{)", text)
    for block in blocks:
        if not any("CustoMIUIzer Dynamic Island" in line for line in block.splitlines()[:2]):
            continue
        frame = re.search(r"(?:mFrame=|\bframe=)\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]", block)
        if frame and "mHasSurface=true" in block:
            x, y, right, bottom = map(int, frame.groups())
            return x, y, right-x, bottom-y
    return None


def in_island(snapshot: dict, title: str, frame: tuple | None) -> bool:
    if snapshot.get("truncated") or snapshot.get("snapshotQuality", {}).get("state") != "healthy":
        raise RuntimeError("Accessibility capture is incomplete")
    nodes = {n["index"]: n for n in snapshot["nodes"]}
    for node in nodes.values():
        if node.get("label") != title or not node.get("visibleToUser"):
            continue
        root, seen = node, set()
        while root.get("parentIndex") is not None:
            if root["index"] in seen or root["parentIndex"] not in nodes:
                raise RuntimeError("Invalid accessibility ancestry")
            seen.add(root["index"])
            root = nodes[root["parentIndex"]]
        rect = root["rect"]
        if frame and all(abs(rect[k]-v) <= 2 for k, v in zip(("x", "y", "width", "height"), frame)):
            return True
    return False


def preference_values(document: str) -> dict:
    return {n.get("name"): (n.tag, n.get("value"), n.text if n.tag == "string" else None,
                            tuple(sorted(c.text or "" for c in n))) for n in ET.fromstring(document)}


def performance_verdict(samples: list[dict], comparisons: list[dict]) -> dict:
    valid = len(samples) >= 6 and len(comparisons) * 2 == len(samples) and all(
        c.get("status") == "complete" and c.get("metrics") and all(m.get("comparable") is True for m in c["metrics"])
        for c in comparisons) and all(s["stable"] and s["conditions"] == samples[0]["conditions"] and
                                    s["frames"].get("total_produced_frames", 0) >= 30 for s in samples)
    if not valid: return {"status": "INCONCLUSIVE"}
    groups = [[s["frames"]["avg_frame_dur_ms"] for s in samples if s["admitted"] == admitted] for admitted in (False, True)]
    if any(len(group) < 3 for group in groups): return {"status": "INCONCLUSIVE"}
    noise = max(max(group)-min(group) for group in groups)
    delta = statistics.median(groups[1])-statistics.median(groups[0])
    return {"status": "INCONCLUSIVE_NOISE" if abs(delta) <= noise else "OBSERVED_DIFFERENCE",
            "median_delta_ms": delta, "within_side_spread_ms": noise}


def host_log_matches(log: str, provenance: dict) -> bool:
    marker = (f"CustoMIUIzer {provenance['versionName']} ({provenance['versionCode']}) "
              f"[{provenance['revision']}] loaded in com.android.systemui")
    return marker in log and "systemui.init (confirmed)" in log


def notification_takeover(call, cli, args, output: Path, serial: str, owned_helpers: set[str], provenance: dict) -> dict:
    """Controlled fixture admission; all preference and host mutations are restored."""
    prefs = f"/data/user/0/{MODULE}/shared_prefs/customiuizer_prefs.xml"
    if call("shell", "am", "get-current-user").strip() != "0" or "uid=0" not in call("shell", "su", "-c", "id"):
        raise RuntimeError("Controlled proof requires root and user 0")
    original = call("shell", "su", "-c", f"cat {prefs}")
    (output / "preferences-original.private.xml").write_text(original, encoding="utf-8")
    # Output names are user supplied; never interpolate arbitrary text in a root shell.
    remote_tmp = "/data/local/tmp/a14-verify-" + hashlib.sha256(str(output).encode()).hexdigest()[:16] + ".xml"
    selected = call("shell", "pm", "path", MODULE).strip()[8:]
    session = ("--session", "a14-notification-proof", "--platform", "android", "--serial", serial)
    evidence, touched = {"status": "UNVERIFIED", "rounds": []}, False

    def run(*parts):
        result = cli(*parts, *session, "--json")
        with (output / "proof-automation.log").open("a", encoding="utf-8") as log:
            log.write(result.stdout + result.stderr + "\n")
        data = json.loads(result.stdout)
        if result.returncode or not data.get("success"):
            raise RuntimeError("Automation observation unavailable")
        return data["data"]

    def sync(document: str, name: str):
        local = output / (name + ".private.xml")
        local.write_text(document, encoding="utf-8", newline="\n")
        call("shell", "am", "force-stop", MODULE)
        call("push", str(local), remote_tmp)
        call("shell", "su", "-c", f"cat {remote_tmp} > {prefs}")
        if call("shell", "su", "-c", f"cat {prefs}") != document:
            raise RuntimeError("Preference write not confirmed")
        call("shell", "am", "start", "-W", "-n", f"{MODULE}/tv.withaibuild.customiuizer.MainActivity")
        for _ in range(25):
            pid = call("shell", "pidof", MODULE).strip()
            log = call("logcat", "-d", "--pid", pid, "-t", "500") if pid.isdigit() else ""
            if "setting(s) to the module" in log:
                call("shell", "input", "keyevent", "KEYCODE_HOME")
                return
            time.sleep(.2)
        raise RuntimeError("Remote preference mirror commit not observed")

    def settings(admitted: bool):
        root = ET.fromstring(original)
        values = {"pref_key_system_strong_toast_mode": ("string", "3"),
                  "pref_key_system_island_chat": ("boolean", "true")}
        for key, (kind, value) in values.items():
            node = root.find(f"./*[@name='{key}']")
            if node is not None: root.remove(node)
            node = ET.SubElement(root, kind, name=key)
            if kind == "string": node.text = value
            else: node.set("value", value)
        for key, include in (("pref_key_system_island_notification_apps", True),
                             ("pref_key_system_island_excluded_apps", False),
                             ("pref_key_system_island_chat_excluded_apps", not admitted)):
            node = root.find(f"./*[@name='{key}']")
            if node is None: node = ET.SubElement(root, "set", name=key)
            for child in list(node):
                if child.text == FIXTURE: node.remove(child)
            if include: ET.SubElement(node, "string").text = FIXTURE
        return ET.tostring(root, encoding="unicode")

    def restart():
        old = call("shell", "pidof", "com.android.systemui").strip()
        if not old.isdigit(): raise RuntimeError("SystemUI owner unavailable")
        call("shell", "su", "-c", f"kill -TERM {old}")
        for _ in range(60):
            try:
                pid = call("shell", "pidof", "com.android.systemui").strip()
            except RuntimeError:
                time.sleep(.25)
                continue
            if pid.isdigit() and pid != old:
                maps = call("shell", "su", "-c", f"cat /proc/{pid}/maps")
                log = call("logcat", "-d", "--pid", pid, "-t", "2000")
                if selected in maps and host_log_matches(log, provenance):
                    (output / ("systemui-" + pid + ".private.log")).write_text(log, encoding="utf-8")
                    time.sleep(.6)  # keyguard publication follows SystemUI init
                    if not unlocked(call("shell", "dumpsys", "power"), call("shell", "dumpsys", "window", "policy")):
                        pin = os.environ.get(getattr(args, "unlock_env", "") or "", "")
                        if not re.fullmatch(r"\d{4,16}", pin):
                            raise RuntimeError("Host recreation relocked device; provide --unlock-env for this controlled run")
                        size = re.findall(r"(\d+)x(\d+)", call("shell", "wm", "size"))
                        width, height = map(int, size[-1])
                        call("shell", "input", "keyevent", "KEYCODE_WAKEUP")
                        time.sleep(.3)
                        call("shell", "input", "swipe", str(width//2), str(height*85//100), str(width//2), str(height//4), "300")
                        time.sleep(.3)
                        call("shell", "input", "text", pin)
                        call("shell", "input", "keyevent", "KEYCODE_ENTER")
                        for _ in range(20):
                            if unlocked(call("shell", "dumpsys", "power"), call("shell", "dumpsys", "window", "policy")): break
                            time.sleep(.2)
                        else: raise RuntimeError("Recreated host remained locked")
                    stat = call("shell", "su", "-c", f"cat /proc/{pid}/stat")
                    return {"pid": pid, "start_ticks": int(stat.rsplit(")", 1)[1].split()[19]),
                            "module_mapped": True, "api102_systemui_init": "CONFIRMED"}
            time.sleep(.25)
        raise RuntimeError("New SystemUI did not confirm module mapping and API 102 init")

    def event(action):
        call("shell", "am", "start", "-W", "-n", f"{FIXTURE}/.FixtureActivity", "--es", "event", action)
        call("shell", "input", "keyevent", "KEYCODE_HOME")

    def cancelled():
        for text in ("A14 verification notice", "A14 updated notice"):
            run("wait", "absent", f'text="{text}"', "5000")

    def hit(owner):
        log = call("logcat", "-d", "--pid", owner["pid"], "-t", "2000")
        (output / ("hook-hit-" + owner["pid"] + ".private.log")).write_text(log, encoding="utf-8")
        if "Dynamic Island: notification-taken" not in log:
            raise RuntimeError("Attributed pixels observed, but target callback acceptance log is unavailable")
        owner["notification_taken"] = True

    def observe(title, expected, label):
        last = None
        for attempt in range(5):
            data = run("snapshot", "--raw")
            windows = call("shell", "dumpsys", "window", "windows")
            frame = island_window(windows)
            found = in_island(data, title, frame)
            visible = any(n.get("label") == title and n.get("visibleToUser") for n in data["nodes"])
            last = {"island_content": found, "text_visible": visible, "frame": frame}
            (output / (label + ".snapshot.private.json")).write_text(json.dumps(data), encoding="utf-8")
            (output / (label + ".window.private.txt")).write_text(windows, encoding="utf-8")
            if (found if expected else visible and not found):
                (output / (label + ".snapshot.private.json")).write_text(json.dumps(data), encoding="utf-8")
                (output / (label + ".window.private.txt")).write_text(windows, encoding="utf-8")
                return last
            time.sleep(.15)
        raise AssertionFailure(f"{label}: expected island attribution={expected}; observed={last}")

    def performance():
        """Bounded capture + existing Perfetto graphs, no authored analysis SQL."""
        skill = ROOT / ".agents/skills/Gracker/Perfetto-Skills/skills/perfetto-performance-analysis"
        if not args.trace_processor or not args.trace_processor.is_file():
            raise RuntimeError("Performance sampling requires existing --trace-processor")
        stopped = cli("daemon", "stop", "--clean", "--state-dir", str(output / "agent-device-home"))
        if stopped.returncode: raise RuntimeError("Cannot isolate automation from sampling")
        for helper in owned_helpers:
            call("shell", "am", "force-stop", helper)
        samples, comparisons = [], []
        report = {"status": "INCONCLUSIVE", "kind": "FEATURE_ADMISSION_AB", "samples": samples,
                  "limits": "Same APK, fixture admission off/on; not a candidate build regression verdict or CPU/GPU attribution."}
        evidence["performance"] = report
        adb = args.adb or shutil.which("adb")
        host_path = call("shell", "pm", "path", "com.android.systemui").strip()[8:]
        identities = {"module_sha256": hashlib.sha256(args.apk.read_bytes()).hexdigest(),
                      "host_sha256": call("shell", "sha256sum", host_path).split()[0]}
        def conditions():
            display = call("shell", "dumpsys", "display")
            hz = re.search(r"mActiveRenderFrameRate=([\d.]+)", display)
            thermal = re.search(r"Thermal Status:\s*(\d+)", call("shell", "dumpsys", "thermalservice"))
            if not hz or not thermal: raise RuntimeError("Actual refresh/thermal state unavailable")
            return {**identities, "refresh_hz": float(hz[1]), "thermal": int(thermal[1]),
                    "rom": call("shell", "getprop", "ro.build.fingerprint").strip(),
                    "rotation": call("shell", "wm", "user-rotation").strip(),
                    "host_pid": call("shell", "pidof", "com.android.systemui").strip()}
        def analyze(folder, trace, condition, side):
            def script(name, *parts):
                result = subprocess.run([sys.executable, str(skill / "scripts" / name), *map(str, parts)],
                                        capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=90)
                if result.returncode: raise RuntimeError("Existing Perfetto graph unavailable: " + result.stderr[-500:])
            probe_file = folder / "probe.json"
            script("perfetto_probe.py", trace, "--trace-processor", args.trace_processor, "--output", probe_file)
            probe = json.loads(probe_file.read_text(encoding="utf-8"))
            start = probe["trace"]["start_ns"]
            script("perfetto_skill.py", "run", trace, "--skill", "app_frame_production",
                   "--param", 'package="com.android.systemui"', "--param", f"start_ts={start}",
                   "--param", f"end_ts={start + 8_000_000_000}", "--trace-processor", args.trace_processor,
                   "--trace-side", side, "--output-dir", folder / "frames")
            graph = json.loads((folder / "frames/result.json").read_text(encoding="utf-8"))
            row = graph["steps"][0]["rows"][0] if graph.get("success") else {}
            query = skill / "references/generated/sql/app_frame_production/app_production_stats.sql"
            summary = {"schema_version": 2, "trace": probe["trace"], "limitations": report["limits"].split(";"),
                       "comparison_context": {"identity": {"process": "com.android.systemui"}, "window_duration_ns": 8_000_000_000,
                       "refresh_budget_ns": round(1e9 / condition["refresh_hz"]), "cpu_topology": "same physical device",
                       "capabilities": ["frame_timeline"], "android_profile": {"sdk": 34, **condition}, "processor": graph["processor"]},
                       "metrics": {"avg_frame_dur_ms": {"status": "observed" if row.get("total_produced_frames", 0) >= 30 else "missing_evidence",
                       "value": row.get("avg_frame_dur_ms", 0), "unit": "ms", "definition": "app_frame_production.avg_frame_dur_ms; exact 8s SystemUI scope",
                       "source_sha256": hashlib.sha256(query.read_bytes()).hexdigest(), "evidence_refs": [str(folder / "frames/result.json")]}}}
            (folder / "side.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")
            return row
        for index, admitted in enumerate([False, True] * args.perfetto_rounds):
            sync(settings(admitted), "sample-settings")
            condition = conditions()
            if condition["thermal"] != 0: raise RuntimeError("Thermal throttling prevents a comparable sample")
            folder = output / f"sample-{index}"
            folder.mkdir()
            trace = folder / "trace.perfetto-trace"
            remote = remote_tmp + f"-{index}.trace"
            for helper in HELPERS:
                try:
                    if call("shell", "pidof", helper).strip(): raise AssertionFailure("Automation helper remains active during sampling")
                except RuntimeError as error:
                    if isinstance(error, AssertionFailure): raise
            try:
                with (folder / "capture.log").open("w") as log:
                    process = subprocess.Popen([adb, "-s", serial, "shell", "perfetto", "-o", remote, "-t", "8s",
                                                "sched", "freq", "idle", "am", "wm", "gfx", "view"], stdout=log, stderr=subprocess.STDOUT)
                    try:
                        started = time.monotonic()
                        inputs = []
                        for step, action in enumerate(("post", "update", "cancel")):
                            deadline = started + 1 + step * 1.7
                            time.sleep(max(0, deadline-time.monotonic()))
                            inputs.append(time.monotonic()-started)
                            event(action)
                        if process.wait(timeout=15): raise RuntimeError("Finite trace capture failed")
                    finally:
                        if process.poll() is None: process.wait(timeout=15)
                call("pull", remote, str(trace))
            finally:
                call("shell", "rm", remote)
            after = conditions()
            sample = {"admitted": admitted, "conditions": condition, "stable": after == condition,
                      "input": ["post", "update", "cancel"], "input_offsets_s": inputs,
                      "automation": "STOPPED", "trace_sha256": hashlib.sha256(trace.read_bytes()).hexdigest()}
            sample["stable"] = sample["stable"] and all(abs(actual-(1+i*1.7)) < .1 for i, actual in enumerate(inputs))
            sample["frames"] = analyze(folder, trace, condition, "enabled" if admitted else "native")
            samples.append(sample)
            if index % 2:
                result = subprocess.run([sys.executable, str(skill / "scripts/perfetto_compare.py"), "--side", f"native={output / f'sample-{index-1}/side.json'}",
                                         "--side", f"enabled={folder / 'side.json'}", "--baseline", "native", "--output", str(folder / "comparison.json")],
                                        capture_output=True, text=True, encoding="utf-8", timeout=30)
                if result.returncode: raise RuntimeError("Existing Perfetto comparison rejected side summaries")
                comparisons.append(json.loads((folder / "comparison.json").read_text(encoding="utf-8")))
        report.update(performance_verdict(samples, comparisons))
        (output / "performance.json").write_text(json.dumps(report, indent=2), encoding="utf-8")

    try:
        touched = True
        sync(settings(False), "native-control")
        evidence["initial_host"] = restart()
        run("open", FIXTURE, "--no-test-ime")
        event("post")
        evidence["rounds"].append(observe("A14 verification notice", False, "native-control"))
        event("cancel")
        cancelled()
        sync(settings(True), "enabled")
        evidence["enabled_host"] = restart()
        event("post")
        evidence["rounds"].append(observe("A14 verification notice", True, "enabled"))
        hit(evidence["enabled_host"])
        event("update")
        evidence["rounds"].append(observe("A14 updated notice", True, "same-key-update"))
        event("cancel")
        cancelled()
        # A new process must independently load the same candidate and re-establish
        # the original feature contract, rather than inheriting an old UI owner.
        evidence["recreated_host"] = restart()
        event("post")
        evidence["rounds"].append(observe("A14 verification notice", True, "after-recreation"))
        hit(evidence["recreated_host"])
        event("cancel")
        cancelled()
        evidence["cancellation_verified"] = True
        evidence["status"] = "PROVEN_CONTROLLED_NOTIFICATION_TAKEOVER"
        if getattr(args, "perfetto_rounds", 0):
            try:
                performance()
            except (OSError, RuntimeError, ValueError, KeyError, IndexError, subprocess.SubprocessError) as error:
                evidence.setdefault("performance", {}).update(status="PENDING", error=str(error))
                (output / "performance.json").write_text(json.dumps(evidence["performance"], indent=2), encoding="utf-8")
        return evidence
    except BaseException as error:
        evidence["failure"] = str(error)
        try:
            pid = call("shell", "pidof", "com.android.systemui").strip()
            (output / "failed-host.private.log").write_text(call("logcat", "-d", "--pid", pid, "-t", "2000"), encoding="utf-8")
        except (OSError, RuntimeError, subprocess.SubprocessError):
            pass
        raise
    finally:
        try:
            if touched:
                sync(original, "restore")
                restart()
                if preference_values(call("shell", "su", "-c", f"cat {prefs}")) != preference_values(original):
                    raise RuntimeError("Preference restoration failed; original backup retained")
                call("shell", "rm", remote_tmp)
                evidence["preferences_restored"] = True
        finally:
            (output / "hook-evidence.json").write_text(json.dumps(evidence, indent=2), encoding="utf-8")


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
        spec = SCENARIOS[args.scenario]
        if not spec.get("implemented", True):
            report["status"] = "NOT_IMPLEMENTED"
            return report
        if not adb:
            raise RuntimeError("ADB unavailable")
        serial = device(adb, args.serial)
        report["proof_level"] = spec["proof"]
        report["started_utc"] = datetime.now(timezone.utc).isoformat()
        report["driver_sha256"] = hashlib.sha256(Path(__file__).read_bytes()).hexdigest()
        report["serial"] = serial  # private local evidence only
        report["rom"] = {key: call("shell", "getprop", key).strip() for key in (
            "ro.build.fingerprint", "ro.build.version.sdk", "ro.build.version.release",
            "ro.mi.os.version.name", "ro.product.cpu.abilist")}
        if (report["rom"]["ro.build.version.sdk"] != "34"
                or report["rom"]["ro.build.version.release"] != "14"
                or not report["rom"]["ro.mi.os.version.name"].startswith("OS1.")
                or "arm64-v8a" not in report["rom"]["ro.product.cpu.abilist"].split(",")):
            report.update(status="NOT_APPLICABLE", error="Requires Android 14 / HyperOS 1 / arm64-v8a")
            return report
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
            try:
                from tools.verify_apk_provenance import read_apk_provenance
            except ModuleNotFoundError:
                from verify_apk_provenance import read_apk_provenance
            report["installation"]["provenance"] = read_apk_provenance(args.apk)
        if not args.run:
            report["status"] = "PREFLIGHT_ONLY"
            return report
        if not unlocked(call("shell", "dumpsys", "power"), call("shell", "dumpsys", "window", "policy")):
            raise RuntimeError("Device locked/asleep; unlock before running deterministic scenarios")
        if not args.agent_device or not args.agent_device.is_file() or not args.fixture_apk:
            raise RuntimeError("Supply installed --agent-device bin/agent-device.mjs and --fixture-apk")
        if spec["proof"] == "HOOK_EFFECT" and (not args.apk or not getattr(args, "control_preferences", False)):
            raise RuntimeError("Hook-effect proof requires --apk and --control-preferences")
        try:
            from tools.rom_catalog import manifest_attributes
        except ModuleNotFoundError:
            from rom_catalog import manifest_attributes
        with zipfile.ZipFile(args.fixture_apk) as fixture:
            if manifest_attributes(fixture.read("AndroidManifest.xml")).get("package") != FIXTURE:
                raise RuntimeError("Fixture APK package identity mismatch")
        before_packages = packages()
        if f"package:{FIXTURE}" in before_packages:
            raise RuntimeError("Fixture already exists; refusing to replace unknown test state")
        env["AGENT_DEVICE_HOME"] = str(output / "agent-device-home")
        env["AGENT_DEVICE_NO_UPDATE_NOTIFIER"] = "1"
        # Isolate from existing remote daemon/provider configuration.
        for key in ("AGENT_DEVICE_DAEMON_BASE_URL", "AGENT_DEVICE_DAEMON_AUTH_TOKEN", "AGENT_DEVICE_CONFIG"):
            env.pop(key, None)
        if getattr(args, "unlock_env", None): env.pop(args.unlock_env, None)
        fixture_owned = True  # also clean a partial install if the transport fails
        installed = call("install", str(args.fixture_apk.resolve()))
        if "Success" not in installed:
            raise RuntimeError("Fixture installation did not report Success")
        call("shell", "pm", "grant", FIXTURE, "android.permission.POST_NOTIFICATIONS")
        before = {host: call("shell", "pidof", host).strip() for host in HOSTS}
        if not all(before.values()):
            raise RuntimeError("Expected A14 hosts are not all running")
        report["before_pids"] = before
        report["fixture_sha256"] = hashlib.sha256(args.fixture_apk.read_bytes()).hexdigest()
        junit = output / "junit.xml"
        cli_started = True
        if spec["proof"] == "HOOK_EFFECT":
            report["hook_effect"] = notification_takeover(call, cli, args, output, serial,
                {helper for helper in HELPERS if f"package:{helper}" not in before_packages}, report["installation"]["provenance"])
            if call("shell", "sha256sum", paths[0][8:]).split()[0] != report["installation"]["sha256"]:
                raise RuntimeError("Module changed during controlled proof")
            report["performance"] = report["hook_effect"].get("performance", "UNVERIFIED")
            report["status"] = "PENDING" if isinstance(report["performance"], dict) and report["performance"]["status"] == "PENDING" else "SCENARIO_PASSED"
            return report
        script = ROOT / "tools/device" / spec["script"]
        script_input = script.read_bytes()
        report["script_sha256"] = hashlib.sha256(script_input).hexdigest()
        result = cli("test", str(script),
                     "--platform", "android", "--serial", serial, "--no-test-ime", "--retries", "0",
                     "--timeout", "60000", "--artifacts-dir", str(output / "artifacts"), "--reporter", f"junit:{junit}")
        (output / "automation.log").write_text(result.stdout + result.stderr, encoding="utf-8")
        after = {host: call("shell", "pidof", host).strip() for host in HOSTS}
        report["after_pids"] = after
        report["status"] = scenario_result(result.returncode, junit, script)
        if script.read_bytes() != script_input:
            raise RuntimeError("Scenario changed during execution")
        if before != after:
            report["status"] = "FAILED"
            report["failure"] = "Host process changed during a scenario requiring stable ownership"
        report["limits"] = "UI assertions and stable host PIDs only. Enabled feature, exact geometry, per-hook activation, other ROMs and performance require separate evidence."
    except AssertionFailure as error:
        report.update(status="FAILED", error=str(error))
    except KeyboardInterrupt:
        report.update(status="INTERRUPTED", error="Interrupted; cleanup attempted")
    except (OSError, RuntimeError, ValueError, IndexError, KeyError, zipfile.BadZipFile, ET.ParseError, subprocess.SubprocessError) as error:
        report["status"] = "PENDING"
        report["error"] = str(error)
    finally:
        cleanup = []
        if args.scenario == "island-takeover" and (output / "hook-evidence.json").is_file():
            try:
                report["hook_effect"] = json.loads((output / "hook-evidence.json").read_text(encoding="utf-8"))
            except (OSError, ValueError) as error:
                cleanup.append("Incomplete hook evidence: " + str(error))
        if cli_started:
            try:
                stopped = cli("daemon", "stop", "--clean", "--state-dir", env["AGENT_DEVICE_HOME"])
                if stopped.returncode:
                    cleanup.append("Owned automation daemon cleanup failed")
            except (OSError, subprocess.SubprocessError, KeyboardInterrupt) as error:
                cleanup.append(str(error))
        if fixture_owned:
            # Independent cleanup attempts: fixture failure must not skip helpers.
            for package in (FIXTURE, *HELPERS):
                try:
                    if package == FIXTURE or (f"package:{package}" not in before_packages and f"package:{package}" in packages()):
                        if "Success" not in call("uninstall", package):
                            cleanup.append("Owned package uninstall not confirmed: " + package)
                except (RuntimeError, OSError, subprocess.SubprocessError, KeyboardInterrupt) as error:
                    cleanup.append(str(error))
            try:
                # Unknown additions are never uninstalled automatically.
                report["added_packages"] = sorted(packages() - before_packages)
                if report["added_packages"]:
                    cleanup.append("New packages remain; inspect automation log and remove only owned helpers")
            except (RuntimeError, OSError, subprocess.SubprocessError, KeyboardInterrupt) as error:
                cleanup.append(str(error))
        if args.run and serial:
            try:
                sleep_and_lock(adb, serial)
                report["screen_locked"] = True
            except (RuntimeError, OSError, subprocess.SubprocessError, KeyboardInterrupt) as error:
                cleanup.append(str(error))
        if cleanup:
            report.update(status="PENDING", cleanup_errors=cleanup)
        report["finished_utc"] = datetime.now(timezone.utc).isoformat()
    return report


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb")
    parser.add_argument("--serial")
    parser.add_argument("--apk", type=Path, help="candidate to compare against installed module; never installs it")
    parser.add_argument("--run", action="store_true")
    parser.add_argument("--control-preferences", action="store_true", help="opt in to temporary notification admission changes with verified restoration")
    parser.add_argument("--unlock-env", help="name of an ephemeral PIN environment variable; never recorded or forwarded to automation")
    parser.add_argument("--perfetto-rounds", type=int, choices=range(4), default=0, help="opt-in 1..3 alternating native/island trace pairs after effect proof")
    parser.add_argument("--trace-processor", type=Path, help="existing pinned Perfetto processor used by the existing Skill graphs")
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
