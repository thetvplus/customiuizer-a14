#!/usr/bin/env python3
"""Opt-in, bounded A14 navigation/log capture. Never installs, clears data/logs or uses root."""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import random
import re
import shutil
import subprocess
import time

TARGETS = ("com.miui.home", "com.android.systemui", "system_server", "tv.withaibuild.customiuizer.r14")


def adb_command(adb: str, serial: str | None, *args: str) -> str:
    command = [adb, *(["-s", serial] if serial else []), *args]
    result = subprocess.run(command, capture_output=True, text=True, encoding="utf-8",
                            errors="replace", timeout=20)
    if result.returncode:
        raise RuntimeError(f"adb {' '.join(args[:3])}: {result.stderr.strip()[:200]}")
    return result.stdout


def device(adb: str, requested: str | None) -> str:
    rows = adb_command(adb, None, "devices").splitlines()[1:]
    ready = [row.split()[0] for row in rows if len(row.split()) >= 2 and row.split()[1] == "device"]
    if requested:
        if requested not in ready:
            raise RuntimeError("Requested device is not ready")
        selected = requested
    elif len(ready) == 1:
        selected = ready[0]
    else:
        raise RuntimeError("No ready device; connect one or select --serial when several are connected")
    for _ in range(2):
        if adb_command(adb, selected, "get-state").strip() != "device":
            raise RuntimeError("Device connection is not stable")
    return selected


def navigation(seed: int, steps: int) -> list[str]:
    rng = random.Random(seed)
    # Only reversible system navigation; no taps, icon edits, application data or settings writes.
    return ["KEYCODE_HOME", *[rng.choice(("KEYCODE_APP_SWITCH", "KEYCODE_BACK", "KEYCODE_HOME"))
                              for _ in range(steps - 2)], "KEYCODE_HOME"]


def memory(pss: str, status: str) -> dict:
    total = re.search(r"TOTAL PSS:\s*(\d+)(?=\s|$)", pss)
    threads = re.search(r"(?m)^Threads:\s*(\d+)\s*$", status)
    return {"pss_kb": int(total[1]) if total else None,
            "threads": int(threads[1]) if threads else None}


def capture(adb: str, serial: str, folder: Path, stage: str) -> dict:
    values = {}
    def optional(*args):
        try:
            return adb_command(adb, serial, *args)
        except (RuntimeError, OSError, subprocess.SubprocessError):
            return None
    for target in TARGETS:
        pids = optional("shell", "pidof", target)
        match = re.match(r"^\d+", pids or "")
        pid = match[0] if match else None
        pss = optional("shell", "dumpsys", "meminfo", target) if pid else None
        status = optional("shell", "cat", f"/proc/{pid}/status") if pid else None
        values[target] = {"pid": int(pid) if pid else None, **memory(pss or "", status or "")}
        for suffix, content in (("meminfo", pss), ("status", status)):
            if content is not None:
                (folder / f"{stage}-{target}-{suffix}.txt").write_text(content, encoding="utf-8")
        if target in TARGETS[:2]:
            frames = optional("shell", "dumpsys", "gfxinfo", target, "framestats")
            if frames is not None:
                (folder / f"{stage}-{target}-frames.txt").write_text(frames, encoding="utf-8")
    cpu = optional("shell", "dumpsys", "cpuinfo")
    if cpu is not None:
        (folder / f"{stage}-cpu.txt").write_text(cpu, encoding="utf-8")
    return values


def exercise(args, folder: Path) -> dict:
    report = {"status": "UNAVAILABLE", "scenario": args.scenario, "seed": args.seed,
              "accepted_navigation_commands": 0, "crash_anr_events": None,
              "screen_locked": None,
              "limits": "Command acceptance is not UI/Hook acceptance; snapshots are not a performance benchmark."}
    adb, serial = None, None
    try:
        adb = args.adb or shutil.which("adb")
        if not adb:
            raise RuntimeError("ADB is unavailable")
        serial = device(adb, args.serial)
        report["serial"] = serial
        start = adb_command(adb, serial, "shell", "date", "-Iseconds").strip()
        since = datetime.fromisoformat(start).strftime("%m-%d %H:%M:%S.000")
        report["device_start"] = start
        report["before"] = capture(adb, serial, folder, "before")
        if args.scenario == "navigation":
            for key in navigation(args.seed, args.steps):
                adb_command(adb, serial, "shell", "input", "keyevent", key)
                report["accepted_navigation_commands"] += 1
                time.sleep(args.pause_ms / 1000)
        else:
            time.sleep(args.observe_seconds)
        if adb_command(adb, serial, "get-state").strip() != "device":
            raise RuntimeError("Device disconnected during capture")
        report["after"] = capture(adb, serial, folder, "after")
        log = adb_command(adb, serial, "logcat", "-b", "crash", "-b", "events", "-d", "-v", "threadtime", "-T", since)
        # Events are observations in the retained buffer, never a proof that no crash/ANR occurred.
        report["crash_anr_events"] = [line for line in log.splitlines()
                                      if re.search(r"\bam_(?:crash|anr)\b", line)
                                      and any(target in line for target in TARGETS)]
        (folder / "crash-events.log").write_text("\n".join(report["crash_anr_events"]), encoding="utf-8")
        report["status"] = "COLLECTED"
    except (RuntimeError, OSError, ValueError, subprocess.SubprocessError) as error:
        report["error"] = str(error)
    finally:
        if adb and serial:
            try:
                adb_command(adb, serial, "shell", "input", "keyevent", "223")
                power = adb_command(adb, serial, "shell", "dumpsys", "power")
                policy = adb_command(adb, serial, "shell", "dumpsys", "window", "policy")
                report["screen_locked"] = bool(re.search(r"\bmWakefulness=(?:Asleep|Dozing)\b", power)
                                               and re.search(r"\b(?:showing|mIsShowing)=true\b", policy))
                if not report["screen_locked"]:
                    raise RuntimeError("Sleep/lock state could not be confirmed")
            except (RuntimeError, OSError, ValueError, subprocess.SubprocessError) as error:
                report["status"] = "UNAVAILABLE"
                report["screen_locked"] = False
                report["cleanup_error"] = str(error)
    return report


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb")
    parser.add_argument("--serial")
    parser.add_argument("--scenario", choices=["observe", "navigation"], default="observe")
    parser.add_argument("--seed", type=lambda value: int(value, 0), default=0xA14)
    parser.add_argument("--steps", type=int, default=24)
    parser.add_argument("--pause-ms", type=int, default=200)
    parser.add_argument("--observe-seconds", type=int, default=15)
    parser.add_argument("--output", type=Path, default=Path(".di-evidence/adb-stress") / datetime.now(timezone.utc).strftime("%Y%m%d-%H%M%S"))
    args = parser.parse_args(argv)
    if not (2 <= args.steps <= 100 and 0 <= args.pause_ms <= 500 and 0 <= args.observe_seconds <= 60):
        parser.error("Use 2..100 steps, 0..500 ms pause and 0..60 seconds observation")
    args.output.mkdir(parents=True, exist_ok=False)
    report = exercise(args, args.output)
    (args.output / "result.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"{report['status']}: {args.output}" + (f" ({report['error']})" if report.get("error") else ""))
    return 0 if report["status"] == "COLLECTED" else 2


if __name__ == "__main__":
    raise SystemExit(main())
