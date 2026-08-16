#!/usr/bin/env python3
r"""Offline LSPosed log analyzer for CustoMIUIzer A14.

Supports plain .txt/.log files, .zip archives, directories, and multi-file input.
Outputs a Markdown summary and a JSON analysis.  No ADB, network, or APK code.

Usage:
    python tools/analyze_lsposed_log.py path/to/full.log --output out
    python tools/analyze_lsposed_log.py log1.txt log2.log logs/ archive.zip --output out
"""

from __future__ import annotations

import argparse
import io
import json
import os
import re
import sys
import zipfile
from collections import Counter, defaultdict, deque
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Dict, Iterable, Iterator, List, Optional, Set, Tuple


# ---------------------------------------------------------------------------
# Constants and patterns
# ---------------------------------------------------------------------------

APPLICATION_ID = "tv.withaibuild.customiuizer.r14"
SOURCE_PACKAGE = "tv.withaibuild.customiuizer"
MODULE_PREFIX = "[Pengeek]"

PROCESSES = {
    "android",
    "system_server",
    "com.android.systemui",
    "com.miui.home",
    "com.mi.android.globallauncher",
    "com.android.settings",
    "com.miui.securitycenter",
    "com.miui.securitycenter:ui",
    "com.android.incallui",
}

MODULE_MARKERS = (
    APPLICATION_ID,
    SOURCE_PACKAGE,
    "CustoMIUIzer",
    MODULE_PREFIX,
    "MainModule",
    "ModuleHelper",
    "XposedHelpers",
    "HookerClassHelper",
    "ResourceHooks",
    "PrefMap",
    "HookDiagnostics",
)

CRASH_ANCHORS = (
    "FATAL EXCEPTION",
    "ANR in",
    "Watchdog",
    "WATCHDOG",
    "system_server crash",
    "Process has died",
    "ProcessRecord died",
    "native crash",
    "Fatal signal",
    "SIGSEGV",
    "SIGABRT",
    "DeadSystemException",
    "TransactionTooLargeException",
    "NoClassDefFoundError",
    "ExceptionInInitializerError",
)

EXCEPTION_CLASSES = (
    "ClassNotFoundException",
    "NoSuchMethodException",
    "NoSuchFieldException",
    "IllegalAccessException",
    "InvocationTargetException",
    "ClassCastException",
    "NullPointerException",
    "IllegalArgumentException",
    "IllegalStateException",
    "AbstractMethodError",
    "IncompatibleClassChangeError",
    "UnsatisfiedLinkError",
    "SecurityException",
    "DeadObjectException",
    "RuntimeException",
    "VerifyError",
    "LinkageError",
)

PREFERENCE_KEYWORDS = (
    "RemotePreferences",
    "Remote preferences",
    "PrefMap",
    "preference",
    "getAll",
    "onPreferenceChanged",
)

PREFERENCE_STATES = (
    "UNINITIALIZED",
    "UNAVAILABLE",
    "SNAPSHOT_PENDING_LISTENER",
    "EMPTY_PENDING",
    "VALID_EMPTY",
    "LOADED",
)

RECEIVER_KEYWORDS = (
    "registerModuleReceiver",
    "registerOwnedReceiver",
    "replaceModuleRegistration",
    "RECEIVER_STALE_DROPPED",
    "RECEIVER_UNREGISTER_FAILED",
    "stale receiver",
    "active receiver",
)

MISS_DEFERRED_RESTART_KEYWORDS = (
    "missed",
    "deferred",
    "restart required",
    "restartRequired",
    "needs restart",
    "restarting",
)

HD_STATUS_KEYWORDS = (
    "TARGET_CLASS_MISSING",
    "TARGET_MEMBER_MISSING",
    "INSTALL_FAILED",
    "SILENTLY_SKIPPED",
    "DEXKIT_FAILED",
    "DEXKIT_NO_MATCH",
    "PREFERENCES_UNAVAILABLE",
    "RECEIVER_UNREGISTER_FAILED",
    "RECEIVER_STALE_DROPPED",
    "DUPLICATE_FEATURE",
)

LOG_RE = re.compile(
    r"^(\d{2}-\d{2})\s+(\d{2}:\d{2}:\d{2}\.\d{3,6})\s+"
    r"(\d+)\s+(\d+)\s+([VDIWEAF])\s+(.*?):\s(.*)$"
)

LOADED_RE = re.compile(r"loaded in\s+(\S+)")

HOOK_DIAGNOSTICS_SUMMARY_RE = re.compile(
    r"HookSummary\s+stage=(\S+)\s+process=(\S+)\s+installed=(\d+)\s+"
    r"classMissing=(\d+)\s+memberMissing=(\d+)\s+failed=(\d+)\s+silentSkipped=(\d+)\s+"
    r"dexkitFailed=(\d+)\s+dexkitNoMatch=(\d+)\s+prefsUnavailable=(\d+)"
)

FAILED_HOOK_METHOD_CF = re.compile(
    r"Failed to hook\s+(.*?)\s+method in\s+(.*?)\s+\(class not found\)"
)
FAILED_HOOK_METHOD_NM = re.compile(
    r"Failed to hook\s+(.*?)\s+method in\s+(.*?)\s+\(no methods found\)"
)
FAILED_HOOK_CTOR_CF = re.compile(
    r"Failed to hook\s+(.*?)\s+constructor\s+\(class not found\)"
)
FAILED_HOOK_CTOR_NC = re.compile(
    r"Failed to hook\s+(.*?)\s+constructor\s+\(no constructors found\)"
)

CLASS_NOT_FOUND_RE = re.compile(r"ClassNotFoundException:\s*([^\s:]+(?:\.[A-Za-z0-9_$]+)+)")
NO_SUCH_METHOD_RE = re.compile(r"NoSuchMethodException:\s*([^\s:]+(?:\.[A-Za-z0-9_$]+)*)")
NO_SUCH_FIELD_RE = re.compile(r"NoSuchFieldException:\s*([^\s:]+(?:\.[A-Za-z0-9_$]+)*)")
NO_FIELD_IN_CLASS_RE = re.compile(r"No field\s+(\w+)\s+in class\s+([\w.$]+)")
NO_METHOD_IN_CLASS_RE = re.compile(r"No method\s+(\w+)\s+in class\s+([\w.$]+)")

MODULE_STACK_RE = re.compile(
    r"at\s+(tv\.withaibuild\.customiuizer(?:\.[A-Za-z_$][\w$]*|\$[A-Za-z_$][\w$]*)+)\."
    r"([A-Za-z_$][\w$]*)\s*\("
)
MODULE_STACK_FALLBACK_RE = re.compile(
    r"at\s+(tv\.withaibuild\.customiuizer(?:\.[A-Za-z_$][\w$]*|\$[A-Za-z_$][\w$]*)+)"
)

HEX_RE = re.compile(r"0x[0-9a-fA-F]+|\b[0-9a-fA-F]{7,}\b")
DEC_RE = re.compile(r"\b\d{4,}\b")
UUID_RE = re.compile(r"[0-9a-fA-F]{8}-(?:[0-9a-fA-F]{4}-){3}[0-9a-fA-F]{12}")
OBJHASH_RE = re.compile(r"@[0-9a-fA-F]{5,}")
TOKEN_RE = re.compile(r"[A-Za-z_$][A-Za-z0-9_$]*")

MAX_FINGERPRINTS = 10_000
MAX_EVENT_LIST = 1_000
MAX_CONTEXT_AFTER = 200


def append_limited(container: list, item: dict, state: Analysis, name: str, limit: int = MAX_EVENT_LIST) -> None:
    if len(container) < limit:
        container.append(item)
    else:
        state.overflow[name] += 1


# ---------------------------------------------------------------------------
# Data model
# ---------------------------------------------------------------------------

@dataclass
class Analysis:
    started: str
    inputs: List[str]
    total_lines: int = 0
    parsed_lines: int = 0
    first_time: Optional[str] = None
    last_time: Optional[str] = None
    a14_markers: List[dict] = field(default_factory=list)
    process_lines: Counter = field(default_factory=Counter)
    process_pids: Dict[str, Set[int]] = field(default_factory=lambda: defaultdict(set))
    process_events: Dict[str, Counter] = field(default_factory=lambda: defaultdict(Counter))
    hook_diagnostics: dict = field(
        default_factory=lambda: {
            "summaries": [],
            "totals": Counter(),
            "by_process": defaultdict(Counter),
            "records": [],
        }
    )
    preferences: dict = field(
        default_factory=lambda: {"events": [], "states": Counter(), "keys": Counter()}
    )
    missed_deferred_restart: List[dict] = field(default_factory=list)
    receiver_events: List[dict] = field(default_factory=list)
    missing: Dict[str, Counter] = field(
        default_factory=lambda: {"class": Counter(), "method": Counter(), "field": Counter()}
    )
    dexkit: List[dict] = field(default_factory=list)
    crashes: List[dict] = field(default_factory=list)
    fingerprints: Dict[str, dict] = field(default_factory=dict)
    source_suggestions: Dict[str, List[str]] = field(default_factory=dict)
    module_stacks: Set[str] = field(default_factory=set)
    overflow: Counter = field(default_factory=Counter)

    def add_event(self, field_name: str, item: dict, limit: int = MAX_EVENT_LIST) -> None:
        lst = getattr(self, field_name)
        if len(lst) < limit:
            lst.append(item)
        else:
            self.overflow[field_name] += 1


# ---------------------------------------------------------------------------
# Log parsing helpers
# ---------------------------------------------------------------------------

def parse_line(raw: str, line_no: int) -> Optional[dict]:
    m = LOG_RE.match(raw.rstrip("\n\r"))
    if not m:
        return None
    date, time, pid, tid, level, tag, msg = m.groups()
    return {
        "line_no": line_no,
        "date": date,
        "time": time,
        "pid": int(pid),
        "tid": int(tid),
        "level": level,
        "tag": tag.strip(),
        "message": msg,
        "raw": raw.rstrip("\n\r"),
        "process": None,
    }


def discover_process(rec: dict, pid_map: Dict[int, str]) -> str:
    pid = rec["pid"]
    msg = rec["message"]
    tag = rec["tag"]

    # Module load markers explicitly state the process name.
    m = LOADED_RE.search(msg)
    if m:
        proc = m.group(1)
        pid_map[pid] = proc
        return proc

    if tag in ("ActivityManager", "ActivityManagerShell", "am_proc_start", "am_proc_bound"):
        for pat in (
            r"Start proc\s+([\w\.]+)",
            r"am_proc_start.*?\s+([\w\.]+)",
            r"am_proc_bound.*?\s+([\w\.]+)",
            r"Process\s+([\w\.]+)\s+has died",
            r"Killing\s+([\w\.]+):",
            r"Killing\s+\d+:\s+([\w\.]+)/",
        ):
            m = re.search(pat, msg, re.IGNORECASE)
            if m:
                pid_map[pid] = m.group(1)
                return m.group(1)

    if pid in pid_map:
        return pid_map[pid]

    for proc in PROCESSES:
        if proc in msg:
            return proc

    return "unknown"


def normalize(text: str) -> str:
    text = UUID_RE.sub("UUID", text)
    text = HEX_RE.sub("HEX", text)
    text = OBJHASH_RE.sub("@HEX", text)
    text = DEC_RE.sub("NUM", text)
    text = re.sub(r"\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d+", "DATETIME", text)
    text = re.sub(r"\s+", " ", text).strip()
    return text[:140]


def extract_exception_type(text: str) -> str:
    m = re.search(r"([\w\.]+(?:Exception|Error|Death|Crash))", text)
    if m:
        return m.group(1).split(".")[-1]
    return ""


def extract_module_stack(text: str) -> str:
    m = MODULE_STACK_RE.search(text)
    if m:
        return f"{m.group(1)}.{m.group(2)}"
    m = MODULE_STACK_FALLBACK_RE.search(text)
    if m:
        return m.group(1)
    return ""


def is_anchor(rec: dict) -> bool:
    msg = rec["message"]
    tag = rec["tag"]
    text = msg + " " + tag
    if any(m in text for m in MODULE_MARKERS):
        return True
    if any(a in msg or a in tag for a in CRASH_ANCHORS):
        return True
    if any(a in msg for a in EXCEPTION_CLASSES):
        return True
    if "HookSummary" in msg:
        return True
    if any(k in text for k in HD_STATUS_KEYWORDS):
        return True
    if "Failed to hook" in msg or "DexKit" in msg:
        return True
    if any(k in text for k in RECEIVER_KEYWORDS + MISS_DEFERRED_RESTART_KEYWORDS + PREFERENCE_KEYWORDS):
        return True
    return False


# ---------------------------------------------------------------------------
# Source index for class suggestions
# ---------------------------------------------------------------------------

class SourceIndex:
    def __init__(self, repo_root: Path) -> None:
        self.root = repo_root
        self.token_files: Dict[str, Set[str]] = defaultdict(set)
        src = repo_root / "app" / "src"
        if not src.exists():
            return
        for p in src.rglob("*"):
            if p.is_file() and p.suffix in (".kt", ".java"):
                rel = p.relative_to(repo_root).as_posix()
                text = p.read_text(encoding="utf-8", errors="ignore")
                tokens = set(TOKEN_RE.findall(text))
                for t in tokens:
                    self.token_files[t].add(rel)

    def _module_source(self, name: str) -> List[str]:
        if not name.startswith("tv.withaibuild.customiuizer."):
            return []
        parts = name.split("$")[0].split(".")
        # The class simple name is the last part; the package is everything before it.
        if len(parts) < 2:
            return []
        pkg_path = "/".join(parts[:-1])
        cls = parts[-1]
        candidates = [
            f"app/src/main/java/{pkg_path}/{cls}.kt",
            f"app/src/main/java/{pkg_path}/{cls}.java",
        ]
        return [c for c in candidates if (self.root / c).exists()]

    def suggest(self, names: Iterable[str]) -> Dict[str, List[str]]:
        result: Dict[str, List[str]] = {}
        stoplist = {"com", "android", "java", "javax", "org", "tv", "withaibuild", "customiuizer", "miui", "name", "monwf"}
        for name in names:
            name = name.strip()
            if not name:
                continue
            seen: Set[str] = set()
            suggestions: List[str] = []
            for direct in self._module_source(name):
                if direct not in seen:
                    seen.add(direct)
                    suggestions.append(direct)
            # For non-module names, use the simple name (last dotted/inner segment) only.
            simple = re.split(r"[.$#(]", name)[-1].strip()
            if (
                simple
                and len(simple) > 2
                and simple not in stoplist
                and not simple.startswith("<")
            ):
                file_hits: Counter = Counter()
                for f in self.token_files.get(simple, ()):
                    file_hits[f] += 1
                for f, _ in file_hits.most_common(5):
                    if f not in seen:
                        seen.add(f)
                        suggestions.append(f)
            if suggestions:
                result[name] = suggestions[:5]
        return result


# ---------------------------------------------------------------------------
# Classification detectors
# ---------------------------------------------------------------------------

def _add_fingerprint(state: Analysis, category: str, process: str, key: str, raw: str) -> None:
    fp = f"{category}|{process}|{normalize(key)}"
    if fp not in state.fingerprints:
        if len(state.fingerprints) >= MAX_FINGERPRINTS:
            state.overflow["fingerprints"] += 1
            return
        state.fingerprints[fp] = {
            "category": category,
            "process": process,
            "key": key,
            "count": 0,
            "samples": [],
        }
    entry = state.fingerprints[fp]
    entry["count"] += 1
    if len(entry["samples"]) < 3:
        entry["samples"].append(raw)


def detect_a14_marker(rec: dict, state: Analysis) -> None:
    text = rec["message"] + " " + rec["tag"]
    if not any(m in text for m in ("CustoMIUIzer", APPLICATION_ID, SOURCE_PACKAGE, "[Pengeek]")):
        return
    m = LOADED_RE.search(rec["message"])
    if m:
        proc = m.group(1)
        marker = {
            "time": rec["time"],
            "pid": rec["pid"],
            "process": proc,
            "message": rec["message"],
        }
        state.add_event("a14_markers", marker)
        state.process_events[proc]["a14_marker"] += 1


def detect_hook_diagnostics(rec: dict, state: Analysis) -> None:
    msg = rec["message"]
    proc = rec.get("process") or "unknown"
    m = HOOK_DIAGNOSTICS_SUMMARY_RE.search(msg)
    if m:
        summary = {
            "time": rec["time"],
            "process": m.group(2),
            "stage": m.group(1),
            "installed": int(m.group(3)),
            "classMissing": int(m.group(4)),
            "memberMissing": int(m.group(5)),
            "failed": int(m.group(6)),
            "silentSkipped": int(m.group(7)),
            "dexkitFailed": int(m.group(8)),
            "dexkitNoMatch": int(m.group(9)),
            "prefsUnavailable": int(m.group(10)),
        }
        append_limited(state.hook_diagnostics["summaries"], summary, state, "hook_diagnostics_summaries")
        for k in ("installed", "classMissing", "memberMissing", "failed", "silentSkipped", "dexkitFailed", "dexkitNoMatch", "prefsUnavailable"):
            state.hook_diagnostics["totals"][k] += summary[k]
            state.hook_diagnostics["by_process"][summary["process"]][k] += summary[k]
        _add_fingerprint(state, "hook_diagnostics", proc, f"stage={summary['stage']}", rec["raw"])
        return

    for status in HD_STATUS_KEYWORDS:
        if status in msg:
            record = {
                "time": rec["time"],
                "process": proc,
                "status": status,
                "message": msg,
            }
            append_limited(state.hook_diagnostics["records"], record, state, "hook_diagnostics_records")
            _add_fingerprint(state, "hook_diagnostics", proc, status, rec["raw"])


def detect_preferences(rec: dict, state: Analysis) -> None:
    text = (rec["message"] + " " + rec["tag"]).lower()
    proc = rec.get("process") or "unknown"
    if not any(k.lower() in text for k in PREFERENCE_KEYWORDS):
        return

    state.process_events[proc]["preference"] += 1

    for s in PREFERENCE_STATES:
        if re.search(r"\b" + s + r"\b", rec["message"]):
            state.preferences["states"][s] += 1

    # Map common preference log phrases to bootstrap states.
    state_map = {
        "empty-pending": "EMPTY_PENDING",
        "valid but empty": "VALID_EMPTY",
        "getAll returned null": "UNAVAILABLE",
        "getRemotePreferences returned null": "UNAVAILABLE",
        "PREFERENCES_UNAVAILABLE": "UNAVAILABLE",
        "Remote preferences missed": "EMPTY_PENDING",
    }
    lower_msg = rec["message"].lower()
    for phrase, st in state_map.items():
        if phrase.lower() in lower_msg:
            state.preferences["states"][st] += 1

    for key in re.findall(r"pref_key_\w+|pref_\w+", rec["message"]):
        state.preferences["keys"][key] += 1

    event = {
        "time": rec["time"],
        "process": proc,
        "message": rec["message"],
    }
    append_limited(state.preferences["events"], event, state, "preferences_events")
    _add_fingerprint(state, "preference", proc, normalize(rec["message"]), rec["raw"])


def _classify_keyword(text: str) -> str:
    t = text.lower()
    if "restart" in t:
        return "restart"
    if "missed" in t:
        return "missed"
    if "defer" in t:
        return "deferred"
    return "other"


def detect_missed_deferred_restart(rec: dict, state: Analysis) -> None:
    msg = rec["message"].lower()
    if not any(k.lower() in msg for k in MISS_DEFERRED_RESTART_KEYWORDS):
        return
    proc = rec.get("process") or "unknown"
    event = {
        "time": rec["time"],
        "process": proc,
        "category": _classify_keyword(rec["message"]),
        "message": rec["message"],
    }
    state.add_event("missed_deferred_restart", event)
    state.process_events[proc][event["category"]] += 1
    _add_fingerprint(state, "missed_deferred", proc, f"{event['category']}|{normalize(rec['message'])}", rec["raw"])


def _classify_receiver(text: str) -> str:
    if "RECEIVER_STALE_DROPPED" in text:
        return "stale_dropped"
    if "RECEIVER_UNREGISTER_FAILED" in text:
        return "unregister_failed"
    if "registerModuleReceiver" in text:
        return "register_module"
    if "registerOwnedReceiver" in text:
        return "register_owned"
    if "replaceModuleRegistration" in text:
        return "replace_module"
    if "stale" in text.lower():
        return "stale"
    if "active" in text.lower():
        return "active"
    return "receiver"


def detect_receivers(rec: dict, state: Analysis) -> None:
    text = rec["message"] + " " + rec["tag"]
    if not any(k in text for k in RECEIVER_KEYWORDS + ("stale", "active")):
        return
    if "Receiver" not in text and "receiver" not in text.lower():
        return
    proc = rec.get("process") or "unknown"
    event = {
        "time": rec["time"],
        "process": proc,
        "category": _classify_receiver(text),
        "message": rec["message"],
    }
    state.add_event("receiver_events", event)
    state.process_events[proc]["receiver_" + event["category"]] += 1
    _add_fingerprint(state, "receiver", proc, event["category"], rec["raw"])


def _record_missing(state: Analysis, kind: str, name: str, process: str, raw: str) -> None:
    state.missing[kind][name] += 1
    state.process_events[process][f"missing_{kind}"] += 1
    _add_fingerprint(state, f"missing_{kind}", process, name, raw)


def detect_missing(rec: dict, tail: Optional[List[str]], state: Analysis) -> None:
    text = rec["message"]
    if tail:
        text += " " + " ".join(tail)
    proc = rec.get("process") or "unknown"
    raw = rec["raw"]

    # Module helper explicit hook failures.
    for m in FAILED_HOOK_METHOD_CF.finditer(rec["message"]):
        _record_missing(state, "class", m.group(2), proc, raw)
    for m in FAILED_HOOK_METHOD_NM.finditer(rec["message"]):
        _record_missing(state, "method", f"{m.group(2)}.{m.group(1)}", proc, raw)
    for m in FAILED_HOOK_CTOR_CF.finditer(rec["message"]):
        _record_missing(state, "class", m.group(1), proc, raw)
    for m in FAILED_HOOK_CTOR_NC.finditer(rec["message"]):
        _record_missing(state, "method", f"{m.group(1)}.<init>", proc, raw)

    # Exception class/method/field names.
    for m in CLASS_NOT_FOUND_RE.finditer(text):
        _record_missing(state, "class", m.group(1), proc, raw)
    for m in NO_SUCH_METHOD_RE.finditer(text):
        _record_missing(state, "method", m.group(1), proc, raw)
    for m in NO_SUCH_FIELD_RE.finditer(text):
        _record_missing(state, "field", m.group(1), proc, raw)

    for m in NO_FIELD_IN_CLASS_RE.finditer(text):
        _record_missing(state, "field", f"{m.group(2)}.{m.group(1)}", proc, raw)
    for m in NO_METHOD_IN_CLASS_RE.finditer(text):
        _record_missing(state, "method", f"{m.group(2)}.{m.group(1)}", proc, raw)

    # Extract any module stack for source-class suggestions.
    mod_stack = extract_module_stack(text)
    if mod_stack:
        state.module_stacks.add(mod_stack)


def detect_dexkit(rec: dict, state: Analysis) -> None:
    if "DexKit" not in rec["message"] and "dexkit" not in rec["message"].lower():
        return
    proc = rec.get("process") or "unknown"
    event = {
        "time": rec["time"],
        "process": proc,
        "message": rec["message"],
    }
    state.add_event("dexkit", event)
    state.process_events[proc]["dexkit"] += 1
    _add_fingerprint(state, "dexkit", proc, normalize(rec["message"]), rec["raw"])


def detect_crash_anr(rec: dict, tail: Optional[List[str]], state: Analysis) -> None:
    msg = rec["message"]
    tag = rec["tag"]
    if not (any(a in msg or a in tag for a in CRASH_ANCHORS) or any(a in msg for a in EXCEPTION_CLASSES)):
        return
    proc = rec.get("process") or "unknown"
    text = msg
    if tail:
        text += " " + " ".join(tail)
    exc = extract_exception_type(text) or ("ANR" if "ANR" in msg else "CRASH")
    mod_stack = extract_module_stack(text)
    if mod_stack:
        state.module_stacks.add(mod_stack)
    event = {
        "time": rec["time"],
        "process": proc,
        "exception": exc,
        "module_stack": mod_stack,
        "message": msg,
    }
    state.add_event("crashes", event)
    state.process_events[proc]["crash_anr"] += 1
    key = f"{exc}|{mod_stack or normalize(msg)}"
    _add_fingerprint(state, "crash_anr", proc, key, rec["raw"])


def classify_record(rec: dict, tail: Optional[List[str]], state: Analysis) -> None:
    proc = rec.get("process") or "unknown"
    state.parsed_lines += 1
    state.process_lines[proc] += 1
    state.process_pids[proc].add(rec["pid"])
    if state.first_time is None:
        state.first_time = rec["time"]
    state.last_time = rec["time"]

    detect_a14_marker(rec, state)
    detect_hook_diagnostics(rec, state)
    detect_preferences(rec, state)
    detect_missed_deferred_restart(rec, state)
    detect_receivers(rec, state)
    detect_missing(rec, tail, state)
    detect_dexkit(rec, state)
    detect_crash_anr(rec, tail, state)


# ---------------------------------------------------------------------------
# Streaming input and analysis
# ---------------------------------------------------------------------------

def iter_text_lines(path: Path, source_name: str) -> Iterator[Tuple[str, str]]:
    with open(path, "r", encoding="utf-8", errors="replace") as f:
        for line in f:
            yield source_name, line


def iter_zip_member_lines(zf: zipfile.ZipFile, info: zipfile.ZipInfo, source_name: str) -> Iterator[Tuple[str, str]]:
    with zf.open(info) as f:
        wrapper = io.TextIOWrapper(f, encoding="utf-8", errors="replace")
        for line in wrapper:
            yield source_name, line


def iter_inputs(paths: List[str]) -> Iterator[Tuple[str, str]]:
    for p in paths:
        path = Path(p)
        if not path.exists():
            print(f"Warning: input not found: {p}", file=sys.stderr)
            continue
        if path.is_dir():
            for child in sorted(path.rglob("*")):
                if child.is_file():
                    name = str(child)
                    if child.suffix.lower() in (".log", ".txt") or not child.suffix:
                        yield from iter_text_lines(child, name)
        elif path.suffix.lower() == ".zip":
            with zipfile.ZipFile(path, "r") as zf:
                for info in zf.infolist():
                    if info.is_dir():
                        continue
                    filename = info.filename.split("/")[-1]
                    if filename.lower().endswith((".log", ".txt")) or "." not in filename:
                        source_name = f"{path}!{info.filename}"
                        yield from iter_zip_member_lines(zf, info, source_name)
        else:
            yield from iter_text_lines(path, str(path))


def analyze_stream(stream: Iterator[Tuple[str, str]], state: Analysis, max_after: int) -> None:
    pending_anchor: Optional[dict] = None
    pending_tail: List[str] = []
    line_no = 0
    pid_map: Dict[int, str] = {}

    for _source, raw in stream:
        line_no += 1
        state.total_lines += 1
        if not raw.strip():
            continue

        rec = parse_line(raw, line_no)
        if rec is None:
            if pending_anchor is not None:
                pending_tail.append(raw.rstrip("\n\r"))
                if len(pending_tail) >= max_after:
                    classify_record(pending_anchor, pending_tail, state)
                    pending_anchor = None
                    pending_tail = []
            continue

        rec["process"] = discover_process(rec, pid_map)

        if pending_anchor is not None:
            classify_record(pending_anchor, pending_tail, state)
            pending_anchor = None
            pending_tail = []

        if is_anchor(rec):
            pending_anchor = rec
        else:
            classify_record(rec, None, state)

    if pending_anchor is not None:
        classify_record(pending_anchor, pending_tail, state)


# ---------------------------------------------------------------------------
# Output
# ---------------------------------------------------------------------------

def _top(counter: Counter, n: int = 20) -> List[Tuple[str, int]]:
    return counter.most_common(n)


def _sorted_processes(state: Analysis) -> List[str]:
    return sorted(state.process_lines, key=lambda p: -state.process_lines[p])


def build_source_suggestions(state: Analysis, repo_root: Optional[Path]) -> None:
    if repo_root is None or not repo_root.exists():
        return
    names: Set[str] = set()
    names.update(state.missing["class"].keys())
    names.update(state.missing["method"].keys())
    names.update(state.missing["field"].keys())
    names.update(state.module_stacks)
    for rec in state.hook_diagnostics["records"]:
        if "targetClass" in rec:
            names.add(rec["targetClass"])
    for ev in state.crashes:
        if ev.get("module_stack"):
            names.add(ev["module_stack"])

    index = SourceIndex(repo_root)
    state.source_suggestions = index.suggest(names)


def write_markdown(output_dir: Path, state: Analysis, args: argparse.Namespace) -> None:
    lines: List[str] = []
    lines.append("# LSPosed 日志分析摘要\n")
    lines.append("## 输入与元数据\n")
    lines.append(f"- **分析时间**: {state.started}")
    lines.append(f"- **输入文件数**: {len(state.inputs)}")
    lines.append(f"- **输入路径**: {', '.join(state.inputs)}")
    lines.append(f"- **总行数**: {state.total_lines}")
    lines.append(f"- **可解析行数**: {state.parsed_lines}")
    lines.append(f"- **时间范围**: {state.first_time or 'N/A'} - {state.last_time or 'N/A'}")
    lines.append(f"- **输出目录**: {output_dir}")
    lines.append("")

    # A14 markers
    lines.append("## A14 模块加载标记\n")
    if state.a14_markers:
        for m in state.a14_markers[:20]:
            lines.append(f"- `{m['time']}` `{m['process']}`: {m['message'][:140]}")
        if len(state.a14_markers) > 20:
            lines.append(f"- ... 还有 {len(state.a14_markers) - 20} 条")
    else:
        lines.append("未检测到模块加载标记。")
    lines.append("")

    # Process summary
    lines.append("## 进程概览\n")
    lines.append("| process | lines | pids | a14 | crash/ANR | missing class | missing method | missing field | dexkit |")
    lines.append("|---------|-------|------|-----|-----------|---------------|----------------|---------------|--------|")
    for proc in _sorted_processes(state)[:30]:
        ev = state.process_events[proc]
        pids = len(state.process_pids[proc])
        lines.append(
            f"| {proc} | {state.process_lines[proc]} | {pids} | "
            f"{ev.get('a14_marker', 0)} | {ev.get('crash_anr', 0)} | "
            f"{ev.get('missing_class', 0)} | {ev.get('missing_method', 0)} | "
            f"{ev.get('missing_field', 0)} | {ev.get('dexkit', 0)} |"
        )
    lines.append("")

    # HookDiagnostics
    lines.append("## HookDiagnostics\n")
    totals = state.hook_diagnostics["totals"]
    if totals:
        lines.append(f"- installed: {totals.get('installed', 0)}")
        lines.append(f"- classMissing: {totals.get('classMissing', 0)}")
        lines.append(f"- memberMissing: {totals.get('memberMissing', 0)}")
        lines.append(f"- failed: {totals.get('failed', 0)}")
        lines.append(f"- silentSkipped: {totals.get('silentSkipped', 0)}")
        lines.append(f"- dexkitFailed: {totals.get('dexkitFailed', 0)}")
        lines.append(f"- dexkitNoMatch: {totals.get('dexkitNoMatch', 0)}")
        lines.append(f"- prefsUnavailable: {totals.get('prefsUnavailable', 0)}")
    else:
        lines.append("未检测到 HookDiagnostics 汇总。")
    lines.append("")

    # Preferences
    lines.append("## Preference 状态\n")
    if state.preferences["states"]:
        for s, c in _top(state.preferences["states"]):
            lines.append(f"- {s}: {c}")
    else:
        lines.append("未检测到明确的 Preference 状态。")
    if state.preferences["keys"]:
        lines.append("")
        lines.append("高频 Preference key:")
        for k, c in _top(state.preferences["keys"], 10):
            lines.append(f"- {k}: {c}")
    lines.append("")

    # Missed / deferred / restart
    lines.append("## missed / deferred / restart required\n")
    if state.missed_deferred_restart:
        counts = Counter(e["category"] for e in state.missed_deferred_restart)
        for cat, c in counts.most_common():
            lines.append(f"- {cat}: {c}")
        for e in state.missed_deferred_restart[:5]:
            lines.append(f"  - `{e['time']}` `{e['process']}` {e['message'][:100]}")
    else:
        lines.append("未检测到 missed / deferred / restart 事件。")
    lines.append("")

    # Receiver
    lines.append("## Receiver active / stale\n")
    if state.receiver_events:
        counts = Counter(e["category"] for e in state.receiver_events)
        for cat, c in counts.most_common():
            lines.append(f"- {cat}: {c}")
        for e in state.receiver_events[:5]:
            lines.append(f"  - `{e['time']}` `{e['process']}` {e['message'][:100]}")
    else:
        lines.append("未检测到 Receiver 相关事件。")
    lines.append("")

    # Missing class/method/field
    lines.append("## Class / Method / Field missing\n")
    for kind, title in (("class", "Class"), ("method", "Method"), ("field", "Field")):
        counter = state.missing[kind]
        lines.append(f"### {title} missing ({len(counter)} unique, {sum(counter.values())} total)\n")
        if counter:
            for name, c in _top(counter, 20):
                lines.append(f"- {name}: {c}")
        else:
            lines.append("无。")
        lines.append("")

    # DexKit
    lines.append("## DexKit\n")
    if state.dexkit:
        for e in state.dexkit[:20]:
            lines.append(f"- `{e['time']}` `{e['process']}`: {e['message'][:120]}")
    else:
        lines.append("未检测到 DexKit 相关事件。")
    lines.append("")

    # Crash/ANR
    lines.append("## crash / ANR\n")
    if state.crashes:
        for e in state.crashes[:20]:
            ms = f" (module stack: {e['module_stack']})" if e.get("module_stack") else ""
            lines.append(f"- `{e['time']}` `{e['process']}` **{e['exception']}**{ms}: {e['message'][:120]}")
    else:
        lines.append("未检测到崩溃或 ANR。")
    lines.append("")

    # Duplicate fingerprints
    lines.append("## 重复指纹\n")
    duplicates = [f for f in state.fingerprints.values() if f["count"] > 1]
    duplicates.sort(key=lambda x: -x["count"])
    if duplicates:
        for f in duplicates[:30]:
            lines.append(f"- count={f['count']} category={f['category']} process={f['process']} key={f['key'][:80]}")
    else:
        lines.append("未检测到重复指纹。")
    lines.append("")

    # Source-class suggestions
    lines.append("## 源码类建议\n")
    if state.source_suggestions:
        for name, files in list(state.source_suggestions.items())[:50]:
            lines.append(f"- `{name}` -> {', '.join(files[:5])}")
    else:
        lines.append("无可用的源码建议（未提供 repo-root 或未识别到相关目标）。")
    lines.append("")

    if state.overflow:
        lines.append("## 容量溢出\n")
        lines.append("以下类别因超过有界容量而被截断：")
        for k, c in state.overflow.most_common():
            lines.append(f"- {k}: {c}")
        lines.append("")

    (output_dir / "summary.md").write_text("\n".join(lines), encoding="utf-8")


def write_json(output_dir: Path, state: Analysis, args: argparse.Namespace) -> None:
    data = {
        "meta": {
            "started": state.started,
            "inputs": state.inputs,
            "total_lines": state.total_lines,
            "parsed_lines": state.parsed_lines,
            "first_time": state.first_time,
            "last_time": state.last_time,
        },
        "a14_markers": state.a14_markers,
        "processes": {
            proc: {
                "lines": state.process_lines[proc],
                "pids": sorted(state.process_pids[proc]),
                "events": dict(state.process_events[proc]),
            }
            for proc in sorted(state.process_lines)
        },
        "hook_diagnostics": {
            "summaries": state.hook_diagnostics["summaries"],
            "totals": dict(state.hook_diagnostics["totals"]),
            "by_process": {
                proc: dict(vals) for proc, vals in state.hook_diagnostics["by_process"].items()
            },
            "records": state.hook_diagnostics["records"],
        },
        "preferences": {
            "events": state.preferences["events"],
            "states": dict(state.preferences["states"]),
            "keys": dict(state.preferences["keys"]),
        },
        "missed_deferred_restart": state.missed_deferred_restart,
        "receiver_events": state.receiver_events,
        "missing": {
            "class": dict(state.missing["class"]),
            "method": dict(state.missing["method"]),
            "field": dict(state.missing["field"]),
        },
        "dexkit": state.dexkit,
        "crashes": state.crashes,
        "fingerprints": {
            fp: {**v, "samples": v["samples"][:3]}
            for fp, v in sorted(state.fingerprints.items(), key=lambda x: -x[1]["count"])
        },
        "source_suggestions": state.source_suggestions,
        "overflows": dict(state.overflow),
    }
    (output_dir / "analysis.json").write_text(
        json.dumps(data, ensure_ascii=False, indent=2, default=str), encoding="utf-8"
    )


def write_outputs(output_dir: Path, state: Analysis, args: argparse.Namespace) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)
    if args.format in ("md", "both"):
        write_markdown(output_dir, state, args)
    if args.format in ("json", "both"):
        write_json(output_dir, state, args)


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

def main() -> int:
    parser = argparse.ArgumentParser(
        description="Offline LSPosed log analyzer for CustoMIUIzer A14."
    )
    parser.add_argument("inputs", nargs="+", help="txt/log/zip files or directories")
    parser.add_argument("-o", "--output", required=True, help="output directory")
    parser.add_argument(
        "--format", choices=["md", "json", "both"], default="both",
        help="output format (default: both)"
    )
    parser.add_argument("--profile", choices=["a14"], default="a14")
    parser.add_argument(
        "--repo-root",
        default=None,
        help="repository root for source-class suggestions (default: parent of tools/)",
    )
    parser.add_argument("--context-after", type=int, default=40, help="max trailing context lines")
    args = parser.parse_args()

    if args.repo_root:
        repo_root = Path(args.repo_root).resolve()
    else:
        repo_root = Path(__file__).resolve().parent.parent

    started = datetime.now(timezone.utc).isoformat()
    state = Analysis(started=started, inputs=[os.path.abspath(p) for p in args.inputs])

    max_after = max(1, min(args.context_after, MAX_CONTEXT_AFTER))
    stream = iter_inputs(args.inputs)
    analyze_stream(stream, state, max_after)

    build_source_suggestions(state, repo_root)

    output_dir = Path(args.output).resolve()
    write_outputs(output_dir, state, args)

    print(f"Done. Output in {output_dir}", file=sys.stderr)
    print(
        f"Lines: {state.total_lines} parsed: {state.parsed_lines} "
        f"crashes: {len(state.crashes)} missing: {dict({k: sum(v.values()) for k, v in state.missing.items()})}",
        file=sys.stderr,
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
