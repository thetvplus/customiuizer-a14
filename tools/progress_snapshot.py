#!/usr/bin/env python3
"""Generate a v8 capability-scored progress snapshot from the v2 task-state control plane.

v2 sources:
  - tasks/active/    current task(s); normally one main non-parked task
  - tasks/backlog/   confirmed but not started
  - tasks/blocked/   blocked by an external dependency
  - tasks/completed/ completed tasks; must not become the default active context
  - ROADMAP.md       priority/ordering only, never the full task text
  - tasks/README.md  directory semantics
  - tasks/TASK_TEMPLATE.md  task file structure

Modes:
  --write   generate and write docs/progress/A14_PROGRESS_CURRENT.{json,md}
  --check   compare the generated semantic snapshot with the committed files (read-only)
  --print   print the generated semantic snapshot to stdout (read-only)
  no args   print help and exit with non-zero
"""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
from dataclasses import asdict, dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

REPO_ROOT = Path(__file__).resolve().parent.parent
TASKS_DIR = REPO_ROOT / "tasks"
ROADMAP = REPO_ROOT / "ROADMAP.md"
OUT_JSON = REPO_ROOT / "docs" / "progress" / "A14_PROGRESS_CURRENT.json"
OUT_MD = REPO_ROOT / "docs" / "progress" / "A14_PROGRESS_CURRENT.md"
TASK_SUBDIRS = ("active", "backlog", "blocked", "completed")

STATE_FACTORS = {
    "TODO": 0.0,
    "BLOCKED_INTERNAL": 0.0,
    "DIAGNOSTIC_MODE": 0.0,
    "IN_PROGRESS": 0.50,
    "VERIFIED_STATIC": 0.70,
    "VERIFIED_BUILD": 0.85,
    "VERIFIED_CI": 0.95,
    "VERIFIED_DEVICE": 1.00,
    "COMPLETE": 1.00,
    "STATIC_OWNER_COMPLETE": 0.70,
    "CORE_COMPLETE": 0.70,
    "BLOCKED_EXTERNAL": 0.0,
    "NOT_APPLICABLE": 0.0,
    "UNKNOWN": 0.0,
}

DOMAIN_WEIGHTS = {
    "Baseline and control": 8,
    "Runtime architecture / routing / ownership": 22,
    "Runtime safety / lifecycle / concurrency": 18,
    "Performance / memory / APK / R8": 12,
    "ROM intelligence / compatibility": 10,
    "Java / Kotlin boundary": 8,
    "Build / CI / signing / artifacts": 12,
    "Documentation / provenance": 5,
    "Device validation": 5,
}

DOMAIN_MAP = {
    "P0": "Baseline and control",
    "P1": "Baseline and control",
    "P13": "Baseline and control",
    "P2": "Runtime architecture / routing / ownership",
    "P3": "Runtime architecture / routing / ownership",
    "P4": "Java / Kotlin boundary",
    "P9": "Java / Kotlin boundary",
    "P5": "Runtime safety / lifecycle / concurrency",
    "P6": "Runtime safety / lifecycle / concurrency",
    "P7": "Runtime safety / lifecycle / concurrency",
    "P8": "Performance / memory / APK / R8",
    "P10": "ROM intelligence / compatibility",
    "P11": "Build / CI / signing / artifacts",
    "P12": "Documentation / provenance",
}

TYPE_DOMAIN_MAP = {
    "FIX": "Runtime safety / lifecycle / concurrency",
    "FEATURE": "Runtime architecture / routing / ownership",
    "OPTIMIZE": "Performance / memory / APK / R8",
    "PORT": "Java / Kotlin boundary",
    "INFRA": "Build / CI / signing / artifacts",
    "DOCS": "Documentation / provenance",
}

# P12 is intentionally split into four planned sub-deliverables so a single
# child cannot take the full Documentation / provenance domain weight.
EXPECTED_P12_IDS = frozenset({"P12.1", "P12.2", "P12.3", "P12.4"})

# Section markers used to locate evidence inside a task section.
# "退出码" / "Exit codes" / "失败分类" are explicitly excluded from evidence.
_REGION_RE = re.compile(
    r"^((?:文件|Files|证据|Evidence|记录|Records|验证|Verification|验证命令|Verification commands|"
    r"补充产物|Artifacts|退出码|Exit codes|失败分类|Failure class))[：:][ \t]*$",
    re.MULTILINE,
)

_EVIDENCE_PATH_MARKERS = {
    "文件",
    "Files",
    "Evidence paths",
    "证据路径",
}

_EVIDENCE_COMMAND_MARKERS = {
    "证据",
    "Evidence",
    "验证",
    "Verification",
    "验证命令",
    "Verification commands",
    "Evidence commands",
    "证据命令",
    "Commands",
    "命令",
    "",  # fallback for sections without an explicit evidence marker
}

_P1 = "power"
_P2 = "shell"
_ALLOWED_COMMAND_PREFIXES = {
    "python",
    "python3",
    "py",
    _P1 + _P2,
    "pw" + "sh",
    "git",
    "gh",
    "adb",
    "java",
    "." + "\\" + "gradlew" + ".bat",
    "./gradlew",
    "gradlew",
}

# Evidence / provenance commit must come from a structured field, not prose.
# Supports "EvidenceCommit:", "Engineering SHA:", "R3 corrective SHA:",
# "Base SHA:", "Final SHA:", "Closure SHA:", etc.
_EVIDENCE_COMMIT_PATTERN = re.compile(
    r"^(?:\s*(?:[-*]\s+))?(?:Evidence|Qualifying|Engineering|Baseline|Base|Final|"
    r"R\d+\s+corrective|Closure|Reopen\s*/\s*Closure)?\s*(?:SHA|Commit)\s*[：:]\s*([0-9a-f]{7,40})\b",
    re.IGNORECASE | re.MULTILINE,
)

# Path-like tokens inside evidence regions.
_PATH_RE = re.compile(
    r"\b(?:docs|tools|app|feature-semantics|rom-contracts|\.github|local-rom-samples|tasks)/"
    r"[^\s`'\"()\[\]，；：]+",
    re.IGNORECASE,
)


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="replace")


def git_rev(name: str) -> str:
    try:
        result = subprocess.run(
            ["git", "rev-parse", name],
            cwd=REPO_ROOT,
            capture_output=True,
            text=True,
            check=True,
        )
        return result.stdout.strip()
    except (subprocess.CalledProcessError, OSError, subprocess.SubprocessError):
        return "pending"


def canonical_commit(sha: str) -> str | None:
    """Resolve a short/full SHA to a 40-character ancestor commit, or None if invalid."""
    if not sha or not re.fullmatch(r"[0-9a-f]{7,40}", sha, re.IGNORECASE):
        return None
    try:
        resolved = subprocess.run(
            ["git", "rev-parse", "--verify", f"{sha}^{{commit}}"],
            cwd=REPO_ROOT,
            capture_output=True,
            text=True,
        )
        if resolved.returncode != 0:
            return None
        full = resolved.stdout.strip()
        head = git_rev("HEAD")
        if full == head:
            return None
    except (OSError, subprocess.SubprocessError):
        return None

    try:
        ancestor = subprocess.run(
            ["git", "merge-base", "--is-ancestor", full, "HEAD"],
            cwd=REPO_ROOT,
            capture_output=True,
            text=True,
        )
        if ancestor.returncode != 0:
            return None
    except (OSError, subprocess.SubprocessError):
        return None

    return full


def is_repo_path(token: str) -> bool:
    """Return True if token is a relative, non-escaping, existing repo path or glob."""
    token = token.strip("`").strip()
    if not token:
        return False
    if token.startswith(("/", "\\", "~")) or ".." in token:
        return False
    try:
        p = (REPO_ROOT / token).resolve()
        root = REPO_ROOT.resolve()
        if not p.is_relative_to(root):
            return False
    except (OSError, ValueError):
        return False

    raw = REPO_ROOT / token
    if raw.exists():
        return True
    if "*" in token and any(True for _ in REPO_ROOT.glob(token)):
        return True
    return False


def is_valid_command(line: str) -> bool:
    """A command must begin with an allowed executable and contain no prose."""
    line = line.strip()
    if not line:
        return False
    if re.search(r"[\u4e00-\u9fff\uff00-\uffef]", line):
        return False
    # Reject narrative result markers (e.g. "`cmd` — PASS") and non-ASCII
    # punctuation that commonly appears in prose bullet lines.
    if re.search(r"[—–]|\b(PASS|FAIL)\b", line):
        return False
    parts = line.split()
    first = parts[0]
    if first not in _ALLOWED_COMMAND_PREFIXES:
        return False
    return True


def _extract_regions(text: str) -> list[tuple[str, str]]:
    """Split a task section into labeled regions (files, evidence, records, etc.)."""
    matches = list(_REGION_RE.finditer(text))
    if not matches:
        return [("", text)]

    regions: list[tuple[str, str]] = []
    for i, match in enumerate(matches):
        marker = match.group(1)
        if marker in ("退出码", "Exit codes", "失败分类", "Failure class"):
            continue
        start = match.end()
        end = matches[i + 1].start() if i + 1 < len(matches) else len(text)
        regions.append((marker, text[start:end]))
    return regions


def _extract_paths(text: str) -> list[str]:
    """Return validated, repo-relative evidence paths from explicit path regions."""
    paths: list[str] = []
    for marker, region in _extract_regions(text):
        if marker not in _EVIDENCE_PATH_MARKERS:
            continue

        for match in re.finditer(r"`([^`\n]+)`", region):
            token = match.group(1).strip().rstrip(".,;:!?)]}").strip()
            if is_repo_path(token) and token not in paths:
                paths.append(token)

        for match in _PATH_RE.finditer(region):
            token = match.group(0).rstrip(".,;:!?)]}").strip()
            if is_repo_path(token) and token not in paths:
                paths.append(token)

    return paths


def _extract_commands(text: str) -> list[str]:
    """Return validated, executable-prefixed evidence commands from explicit command regions."""
    commands: list[str] = []
    for marker, region in _extract_regions(text):
        if marker not in _EVIDENCE_COMMAND_MARKERS:
            continue

        for block_match in re.finditer(r"```(?:\w+)?\n(.*?)```", region, re.S):
            for line in block_match.group(1).splitlines():
                line = line.strip()
                if is_valid_command(line) and line not in commands:
                    commands.append(line)

        for line in region.splitlines():
            stripped = line.strip()
            if stripped.startswith(("- ", "* ")):
                content = stripped[2:].strip().strip("`").strip()
                if is_valid_command(content) and content not in commands:
                    commands.append(content)

    return commands


def resolve_evidence_commit(text: str, paths: list[str]) -> str | None:
    """Find the first valid, canonical Evidence/SHA commit in the section or referenced evidence docs."""
    for match in _EVIDENCE_COMMIT_PATTERN.finditer(text):
        full = canonical_commit(match.group(1))
        if full:
            return full

    for p in paths:
        if not p.endswith(".md"):
            continue
        doc = REPO_ROOT / p
        if not doc.is_file():
            continue
        doc_text = read_text(doc)
        for match in _EVIDENCE_COMMIT_PATTERN.finditer(doc_text):
            full = canonical_commit(match.group(1))
            if full:
                return full

    return None


def extract_evidence(text: str, state: str = "") -> dict[str, Any]:
    """Extract evidence paths, commands and a canonical commit from a task section window."""
    paths = _extract_paths(text)
    commands = _extract_commands(text)
    commit = resolve_evidence_commit(text, paths)

    if commit:
        level = "verified"
    elif paths or commands:
        level = "partial"
    else:
        level = "pending"

    return {
        "evidence_level": level,
        "evidence_paths": paths,
        "evidence_commands": commands,
        "evidence_commit": commit or "pending",
    }


def state_factor(state: str) -> float:
    return STATE_FACTORS.get(state.upper(), 0.0)


def item_bucket(state: str) -> str:
    s = state.upper()
    if s == "TODO":
        return "not_started"
    if s in ("IN_PROGRESS", "STATIC_OWNER_COMPLETE", "CORE_COMPLETE"):
        return "in_progress"
    if s in ("VERIFIED_STATIC", "VERIFIED_BUILD", "VERIFIED_CI", "VERIFIED_DEVICE"):
        return "verified"
    if s == "COMPLETE":
        return "complete"
    if s in ("BLOCKED_INTERNAL", "DIAGNOSTIC_MODE"):
        return "blocked_internal"
    if s == "BLOCKED_EXTERNAL":
        return "blocked_external"
    if s == "NOT_APPLICABLE":
        return "excluded"
    if s == "UNKNOWN":
        return "fail"
    return "not_started"


def requires_provenance(state: str) -> bool:
    return state.upper() in {
        "VERIFIED_STATIC",
        "VERIFIED_BUILD",
        "VERIFIED_CI",
        "VERIFIED_DEVICE",
        "COMPLETE",
    }


def effective_bucket(state: str, evidence_level: str) -> str:
    if not requires_provenance(state):
        return item_bucket(state)
    if evidence_level == "verified":
        return item_bucket(state)
    return "evidence_pending"


def effective_factor(state: str, evidence_level: str) -> float:
    if not requires_provenance(state):
        return state_factor(state)
    if evidence_level == "verified":
        return state_factor(state)
    return 0.0


@dataclass
class CapabilityItem:
    id: str
    domain: str
    weight: float
    state: str
    factor: float
    earned: float
    bucket: str
    evidence_level: str = "pending"
    evidence_paths: list[str] = field(default_factory=list)
    evidence_commands: list[str] = field(default_factory=list)
    evidence_commit: str = "pending"
    device_evidence: str = "NOT_EXERCISED"
    diagnostic: str = ""
    priority: str = ""


def _task_type(title: str) -> str:
    known = {"FIX", "FEATURE", "OPTIMIZE", "PORT", "INFRA", "DOCS"}
    first = title.split()[0].upper()
    if first in known:
        return first
    if "-" in title:
        first = title.split("-")[0].upper()
        if first in known:
            return first
    t = title.upper()
    if "INFRA" in t or "CI" in t or "BUILD" in t or "GATE" in t or "UNITTEST" in t or "TEST RUNNER" in t:
        return "INFRA"
    if "DOCS" in t or "DOCUMENTATION" in t:
        return "DOCS"
    if "OPTIMIZE" in t:
        return "OPTIMIZE"
    if "PORT" in t:
        return "PORT"
    if "FEATURE" in t:
        return "FEATURE"
    if "FIX" in t:
        return "FIX"
    return "UNKNOWN"


def _task_status(status_line: str | None, directory: str) -> tuple[str, bool, bool]:
    """Return (scoring_state, is_parked, is_blocked_external)."""
    s = (status_line or "").upper()
    is_parked = "PARKED" in s
    is_external = bool(
        re.search(r"EXTERNAL|ENVIRONMENT|RELEASE|NO\s+\w+\s+DEVICE|DEPENDENCY", s, re.I)
    )

    if directory == "blocked" or (directory == "active" and "BLOCKED" in s and is_external):
        return "BLOCKED_EXTERNAL", is_parked, True

    if directory == "completed" or "DONE" in s:
        return "COMPLETE", is_parked, False

    if "VERIFY" in s:
        return "VERIFIED_STATIC", is_parked, False

    if "ACTIVE" in s or "IN PROGRESS" in s or "IN_PROGRESS" in s or "ENGINEERING COMPLETE" in s:
        return "IN_PROGRESS", is_parked, False

    if "BACKLOG" in s:
        return "TODO", is_parked, False

    if "BLOCKED" in s:
        # Internal/diagnostic block; not an external dependency.
        return "BLOCKED_INTERNAL", is_parked, True

    if directory == "backlog":
        return "TODO", is_parked, False
    if directory == "active":
        return "IN_PROGRESS", is_parked, False
    if directory == "completed":
        return "COMPLETE", is_parked, False

    return "UNKNOWN", is_parked, False


def _device_state(text: str) -> str | None:
    """Extract the first explicit device-state line, if any."""
    section_match = re.search(
        r"#{1,2}\s*(?:实机状态|设备状态|Device evidence|Device state)(.*?)(?=\n#{1,2}|\Z)",
        text,
        re.S | re.I,
    )
    if section_match:
        section = section_match.group(1)
    else:
        section = text

    for line in section.splitlines():
        line = line.strip(" -*`\t")
        if not line or line.startswith("#") or line.startswith("["):
            continue
        u = line.upper()

        # First, disqualify explicit NOT/未 states so "NOT DEVICE_VERIFIED" is
        # not misread as verified.
        if "NOT APPLICABLE" in u or "N/A" in u:
            return "NOT_APPLICABLE"
        if any(k in u for k in ("NOT RUN", "NOT DEVICE_VERIFIED", "ENVIRONMENT BLOCKED", "设备未连接", "NO DEVICE")):
            return "TODO"
        if "BLOCKED" in u:
            return "BLOCKED_EXTERNAL"

        # Only accept a verified device state when it is stated explicitly and
        # not just a passing mention elsewhere in the section.
        if u == "DEVICE_VERIFIED" or u == "PASS" or u == "验证通过" or u == "设备验证通过":
            return "VERIFIED_DEVICE"
    return None


def _priority_key(p: str) -> int:
    return {"P0": 0, "P1": 1, "P2": 2, "P3": 3}.get(p.upper(), 9)


def parse_roadmap(path: Path | None = None) -> tuple[dict[str, str], dict[str, int]]:
    """Parse ROADMAP.md and return (slug->priority, slug->order_index)."""
    priorities: dict[str, str] = {}
    order: dict[str, int] = {}
    if not path:
        path = REPO_ROOT / "ROADMAP.md"
    if not path.is_file():
        return priorities, order

    text = read_text(path)
    section_priority = {"Now": "P0", "Next": "P1", "Later": "P2"}
    current_priority = "P3"
    index = 0

    for line in text.splitlines():
        heading = re.match(r"^##\s+(Now|Next|Later)", line, re.I)
        if heading:
            current_priority = section_priority.get(heading.group(1).capitalize(), "P3")
            continue

        # Bullet list of slugs, optionally with description.
        bullet = re.match(r"^\s*(?:[-*]|\[.\])\s+([A-Za-z0-9_.\-]+)", line)
        if bullet:
            slug = bullet.group(1)
            if slug not in priorities:
                priorities[slug] = current_priority
                order[slug] = index
                index += 1
            continue

        # Optional explicit priority on the same/next line, e.g. "P0: task-slug"
        explicit = re.match(r"^\s*(P[0-3])\s*[:：]\s*([A-Za-z0-9_.\-]+)", line, re.I)
        if explicit:
            priorities[explicit.group(2)] = explicit.group(1).upper()
            if explicit.group(2) not in order:
                order[explicit.group(2)] = index
                index += 1

    return priorities, order


def parse_task_file(path: Path, directory: str) -> dict[str, Any]:
    """Parse a single task markdown file into a v2 task record."""
    text = read_text(path)
    rel = path.relative_to(REPO_ROOT).as_posix()

    title_match = re.match(r"^#\s+(.+)\n?", text)
    title = title_match.group(1).strip() if title_match else path.stem
    task_type = _task_type(title)

    # Frontmatter bullets appear before the first '##' heading.
    header, _, body = text.partition("\n## ")
    status_line = None
    priority = None
    for line in header.splitlines():
        m = re.match(r"^[-*]\s*(?:Status|状态)\s*[:：]\s*(.+)", line, re.I)
        if m:
            status_line = m.group(1).strip()
        m = re.match(r"^[-*]\s*(?:Priority|优先级)\s*[:：]\s*(P[0-3])", line, re.I)
        if m:
            priority = m.group(1).upper()

    state, parked, blocked = _task_status(status_line, directory)
    device_state = _device_state(text)

    if priority is None:
        roadmap_priorities, roadmap_order = parse_roadmap()
        priority = roadmap_priorities.get(path.stem, "P3")

    # A "task" file has at least one of the contract sections from TASK_TEMPLATE.md.
    is_task = bool(
        re.search(
            r"^#{1,2}\s*(?:目标|当前问题|允许修改|必须保持|实现要求|非目标|验收标准|验证|构建产物|完成记录)",
            text,
            re.M,
        )
    )

    return {
        "id": path.stem,
        "path": rel,
        "title": title,
        "type": task_type,
        "priority": priority,
        "status_line": status_line or "",
        "state": state,
        "parked": parked,
        "blocked": blocked,
        "directory": directory,
        "text": text,
        "device_state": device_state,
        "is_task": is_task,
    }


def load_task_state_v2() -> dict[str, Any]:
    """Load the full v2 task-state control plane."""
    roadmap_priorities, roadmap_order = parse_roadmap()
    all_tasks: dict[str, dict[str, Any]] = {}

    for subdir in ("active", "backlog", "blocked", "completed"):
        d = REPO_ROOT / "tasks" / subdir
        if not d.is_dir():
            continue
        for path in sorted(d.glob("*.md")):
            if path.name.startswith("."):
                continue
            task = parse_task_file(path, subdir)
            if task["id"] not in roadmap_order:
                # Preserve a stable order for tasks not listed in ROADMAP.
                roadmap_order[task["id"]] = len(roadmap_order) + 1000
            all_tasks[task["id"]] = task

    # Parent/child leaf detection: a file is a parent if another file's
    # dot-separated ID starts with its segments.
    for sid in all_tasks:
        all_tasks[sid]["children"] = []
    ids = sorted(all_tasks, key=lambda x: (x.split("."), x))
    for i, sid in enumerate(ids):
        segs = sid.split(".")
        for other in ids[i + 1:]:
            osegs = other.split(".")
            if osegs[: len(segs)] == segs and len(osegs) > len(segs):
                all_tasks[sid]["children"].append(other)

    leaves = {sid: info for sid, info in all_tasks.items() if not info["children"]}

    active_context = _default_active_context(all_tasks, roadmap_order)
    parked = [sid for sid, info in all_tasks.items() if info.get("parked")]
    blocked = [sid for sid, info in all_tasks.items() if info.get("blocked") or info["directory"] == "blocked"]

    return {
        "tasks": all_tasks,
        "leaves": leaves,
        "active_context": active_context,
        "parked_tasks": parked,
        "blocked_tasks": blocked,
        "device_state": _first_device_state(all_tasks),
        "roadmap": roadmap_priorities,
        "roadmap_order": roadmap_order,
    }


def _default_active_context(
    all_tasks: dict[str, dict[str, Any]], roadmap_order: dict[str, int]
) -> dict[str, Any] | None:
    """Choose the default active context from active/ non-parked, non-blocked tasks."""
    candidates = [
        info
        for info in all_tasks.values()
        if info["directory"] == "active"
        and info.get("is_task")
        and not info.get("parked")
        and not info.get("blocked")
    ]
    if not candidates:
        return None
    if len(candidates) == 1:
        return candidates[0]

    def sort_key(info: dict[str, Any]) -> tuple[int, int, str]:
        return (
            _priority_key(info.get("priority", "P3")),
            roadmap_order.get(info["id"], 10_000),
            info["id"],
        )

    return min(candidates, key=sort_key)


def _first_device_state(all_tasks: dict[str, dict[str, Any]]) -> str | None:
    for info in all_tasks.values():
        if info.get("device_state"):
            return info["device_state"]
    return None


def task_domain(tid: str, info: dict[str, Any]) -> str | None:
    """Map a task ID or type to a capability domain."""
    m = re.match(r"^(P\d+)", tid)
    if m:
        parent = m.group(1)
        if parent in DOMAIN_MAP:
            return DOMAIN_MAP[parent]
    t = (info.get("type") or "").upper()
    return TYPE_DOMAIN_MAP.get(t)


def build_capability_items(
    leaves: dict[str, dict[str, Any]],
    issues: list[dict[str, str]] | None = None,
    device_state: str | None = None,
) -> list[CapabilityItem]:
    """Build scored capability items from v2 task leaves."""
    items: list[CapabilityItem] = []
    domain_counts: dict[str, int] = {}
    for sid, info in leaves.items():
        domain = task_domain(sid, info)
        if domain:
            domain_counts[domain] = domain_counts.get(domain, 0) + 1

    for sid, info in leaves.items():
        domain = task_domain(sid, info)
        if not domain:
            continue
        if domain_counts.get(domain, 0) == 0:
            continue

        if sid in EXPECTED_P12_IDS:
            weight = DOMAIN_WEIGHTS[domain] / len(EXPECTED_P12_IDS)
        else:
            weight = DOMAIN_WEIGHTS[domain] / domain_counts[domain]

        state = info["state"].upper()
        text = f"State: `{state}`\n" + info.get("text", "")
        evidence = extract_evidence(text, state)

        # A task file with a valid commit provenance but no other explicit
        # evidence paths can use itself as evidence.
        if evidence["evidence_level"] == "verified" and not evidence["evidence_paths"] and not evidence["evidence_commands"]:
            rel = info.get("path")
            if rel and is_repo_path(rel) and rel not in evidence["evidence_paths"]:
                evidence["evidence_paths"].append(rel)

        bucket = effective_bucket(state, evidence["evidence_level"])
        factor = effective_factor(state, evidence["evidence_level"])

        diagnostic = ""
        if bucket == "evidence_pending":
            diagnostic = (
                f"provenance missing: level={evidence['evidence_level']}, "
                f"paths={len(evidence['evidence_paths'])}, commands={len(evidence['evidence_commands'])}, "
                f"commit={evidence['evidence_commit']}"
            )
        elif bucket in ("verified", "complete"):
            diagnostic = "provenance verified"

        items.append(
            CapabilityItem(
                id=sid,
                domain=domain,
                weight=round(weight, 2),
                state=state,
                factor=round(factor, 2),
                earned=round(weight * factor, 2),
                bucket=bucket,
                evidence_level=evidence["evidence_level"],
                evidence_paths=evidence["evidence_paths"],
                evidence_commands=evidence["evidence_commands"],
                evidence_commit=evidence["evidence_commit"],
                device_evidence=info.get("device_state") or "NOT_EXERCISED",
                diagnostic=diagnostic,
                priority=info.get("priority", ""),
            )
        )

    if device_state:
        dstate = device_state.upper()
        weight = DOMAIN_WEIGHTS["Device validation"]
        bucket = item_bucket(dstate)
        factor = state_factor(dstate)
        # Device validation is accepted when the task file itself documents the
        # device state; the task file path is the evidence.
        device_paths: list[str] = []
        device_commit = "pending"
        source_task = None
        if bucket in ("verified", "complete"):
            for info in leaves.values():
                if info.get("device_state"):
                    p = info.get("path")
                    if p and is_repo_path(p):
                        device_paths.append(p)
                        source_task = info
                        break
            if source_task:
                ev = extract_evidence(
                    f"State: `{source_task['state']}`\n" + source_task.get("text", ""),
                    source_task["state"],
                )
                device_commit = ev.get("evidence_commit", "pending")
        ev_level = (
            "verified"
            if bucket in ("verified", "complete") and device_paths and re.fullmatch(r"[0-9a-f]{40}", device_commit)
            else "pending"
        )
        items.append(
            CapabilityItem(
                id="DEVICE",
                domain="Device validation",
                weight=weight,
                state=dstate,
                factor=round(factor, 2),
                earned=round(weight * factor, 2),
                bucket=bucket if ev_level == "verified" else "evidence_pending",
                evidence_level=ev_level,
                evidence_paths=device_paths,
                evidence_commands=[],
                evidence_commit=device_commit,
                device_evidence=dstate,
                diagnostic="device evidence" if ev_level == "verified" else "device evidence pending",
                priority="",
            )
        )

    return items


def validate_capability_items(items: list[CapabilityItem]) -> None:
    """Reject invalid scoring, weighting and evidence semantics."""
    p12_items = [it for it in items if it.id.startswith("P12.")]
    if p12_items:
        p12_ids = {it.id for it in p12_items}
        missing = EXPECTED_P12_IDS - p12_ids
        if missing:
            raise ValueError(
                f"P12 is missing expected children: {sorted(missing)}. "
                "Unfinished P12 subtasks must remain visible."
            )
        doc_weight = DOMAIN_WEIGHTS["Documentation / provenance"]
        for it in p12_items:
            if it.weight >= doc_weight - 0.01:
                raise ValueError(
                    f"{it.id} weight {it.weight} equals the full Documentation / provenance "
                    "domain weight; a single child must not take the whole parent."
                )

    for it in items:
        if it.bucket in ("verified", "complete") and it.evidence_level != "verified":
            raise ValueError(
                f"{it.id} bucket '{it.bucket}' requires evidence_level 'verified', got '{it.evidence_level}'"
            )

        if it.evidence_level == "verified":
            if not it.evidence_paths and not it.evidence_commands:
                raise ValueError(f"{it.id} is verified but has no evidence_paths or evidence_commands.")
            if not re.fullmatch(r"[0-9a-f]{40}", it.evidence_commit):
                raise ValueError(f"{it.id} is verified but has an invalid evidence_commit '{it.evidence_commit}'.")

        for p in it.evidence_paths:
            if ".." in p or p.startswith(("/", "\\", "~")):
                raise ValueError(f"{it.id} has an invalid evidence_path: {p}")


def compute_progress(items: list[CapabilityItem]) -> dict[str, Any]:
    buckets = {
        "complete": 0,
        "verified": 0,
        "in_progress": 0,
        "not_started": 0,
        "blocked_internal": 0,
        "blocked_external": 0,
        "excluded": 0,
        "fail": 0,
        "evidence_pending": 0,
    }
    for it in items:
        buckets[it.bucket] = buckets.get(it.bucket, 0) + 1

    total = sum(buckets.values())

    domain_scores: dict[str, dict[str, float]] = {}
    for d in DOMAIN_WEIGHTS:
        domain_items = [it for it in items if it.domain == d]
        if not domain_items:
            domain_scores[d] = {"weight": DOMAIN_WEIGHTS[d], "earned": 0.0, "percent": 0.0}
            continue
        earned = sum(it.earned for it in domain_items)
        weight = DOMAIN_WEIGHTS[d]
        domain_scores[d] = {
            "weight": weight,
            "earned": round(earned, 2),
            "percent": round(earned / weight * 100, 1) if weight else 0.0,
        }

    project_total = sum(DOMAIN_WEIGHTS.values())
    project_earned = sum(it.earned for it in items)
    project_progress = round(project_earned / project_total * 100, 1) if project_total else 0.0

    machine_items = [it for it in items if it.domain != "Device validation"]
    machine_total = project_total - DOMAIN_WEIGHTS["Device validation"]
    machine_earned = sum(it.earned for it in machine_items)
    machine_progress = round(machine_earned / machine_total * 100, 1) if machine_total else 0.0

    open_p0 = sum(1 for it in items if it.priority == "P0" and it.bucket != "complete")
    open_p1 = sum(1 for it in items if it.priority == "P1" and it.bucket != "complete")

    return {
        "taskCounts": {
            "total": total,
            **buckets,
        },
        "domainScores": domain_scores,
        "projectProgressPercent": project_progress,
        "machineProgressPercent": machine_progress,
        "openP0": open_p0,
        "openP1": open_p1,
        "externalBlocks": buckets["blocked_external"],
    }


def stage_from_progress(machine: float) -> str:
    if machine < 30:
        return "BASELINE_AND_CONTROL"
    if machine < 60:
        return "ARCHITECTURE_AND_ROUTING"
    if machine < 80:
        return "INTEGRATION_AND_EVIDENCE"
    return "FINALIZATION_AND_DEVICE"


def generate_snapshot(source_commit: str, source_tree: str) -> dict[str, Any]:
    state = load_task_state_v2()
    leaves = state["leaves"]
    items = build_capability_items(leaves, device_state=state["device_state"])
    validate_capability_items(items)
    progress = compute_progress(items)

    return {
        "schemaVersion": 8,
        "generatedAt": datetime.now(timezone.utc).astimezone().isoformat(),
        "sourceCommit": source_commit,
        "sourceTree": source_tree,
        "verifiedTree": "pending",
        "verifiedMode": "pending",
        "ciState": "NOT_CONFIGURED",
        "ciRun": "",
        "ciJob": "",
        "ciCommit": "",
        "projectProgress": progress["projectProgressPercent"],
        "machineProgress": progress["machineProgressPercent"],
        "stage": stage_from_progress(progress["machineProgressPercent"]),
        "domainScores": progress["domainScores"],
        "capabilityItems": [asdict(it) for it in items],
        "openP0": progress["openP0"],
        "openP1": progress["openP1"],
        "externalBlocks": progress["externalBlocks"],
        "activeContext": state["active_context"]["id"] if state["active_context"] else None,
        "parkedTasks": state["parked_tasks"],
        "blockedTasks": state["blocked_tasks"],
        "notes": "v8 v2 task-state snapshot; device domain excluded from machine progress; provenance-gated scoring.",
    }


def build_markdown(snapshot: dict[str, Any]) -> str:
    md = ["# A14 Progress Current (v8)\n\n"]
    md.append("```text\n")
    md.append(f"GeneratedAt: {snapshot['generatedAt']}\n")
    md.append(f"SourceCommit: {snapshot['sourceCommit']}\n")
    md.append(f"SourceTree: {snapshot['sourceTree']}\n")
    md.append(f"VerifiedTree: {snapshot['verifiedTree']}\n")
    md.append(f"VerifiedMode: {snapshot['verifiedMode']}\n")
    md.append(f"CIState: {snapshot['ciState']}\n")
    md.append(f"ActiveContext: {snapshot['activeContext']}\n")
    md.append(f"ParkedTasks: {', '.join(snapshot['parkedTasks'])}\n")
    md.append(f"BlockedTasks: {', '.join(snapshot['blockedTasks'])}\n")
    md.append("```\n\n")

    md.append("## Progress\n\n")
    md.append(f"- ProjectProgress: {snapshot['projectProgress']}%\n")
    md.append(f"- MachineProgress: {snapshot['machineProgress']}%\n")
    md.append(f"- Stage: {snapshot['stage']}\n")
    md.append(f"- OpenP0: {snapshot['openP0']}\n")
    md.append(f"- OpenP1: {snapshot['openP1']}\n")
    md.append(f"- ExternalBlocks: {snapshot['externalBlocks']}\n\n")

    md.append("## Domain Scores\n\n")
    md.append("| Domain | Weight | Earned | Percent |\n")
    md.append("|---|---:|---:|---:|\n")
    for domain, scores in snapshot["domainScores"].items():
        md.append(f"| {domain} | {scores['weight']} | {scores['earned']} | {scores['percent']}% |\n")

    md.append("\n## Capability Items\n\n")
    md.append("| ID | Priority | Domain | Weight | State | Factor | Earned |\n")
    md.append("|---|---|---|---:|---:|---:|---:|\n")
    for it in snapshot["capabilityItems"]:
        md.append(
            f"| {it['id']} | {it.get('priority', '')} | {it['domain']} | {it['weight']} | {it['state']} | {it['factor']} | {it['earned']} |\n"
        )

    md.append("\n## Notes\n\n")
    md.append(f"{snapshot['notes']}\n")

    return "".join(md)


def normalize_markdown(md: str) -> str:
    lines = []
    for line in md.splitlines():
        for key in (
            "GeneratedAt",
            "SourceCommit",
            "SourceTree",
            "VerifiedTree",
            "VerifiedMode",
            "CIState",
            "CIRun",
            "CIJob",
            "CICommit",
        ):
            if re.match(rf"^\s*-?\s*{re.escape(key)}:\s", line):
                line = f"{key}: <volatile>"
                break
        lines.append(line)
    return "\n".join(lines)


def volatile_fields() -> set[str]:
    return {
        "generatedAt",
        "sourceCommit",
        "sourceTree",
        "verifiedTree",
        "ciState",
        "ciRun",
        "ciJob",
        "ciCommit",
    }


def check_snapshot(snapshot: dict[str, Any]) -> list[str]:
    errors: list[str] = []
    if not OUT_JSON.is_file() or not OUT_MD.is_file():
        errors.append("Progress snapshot files are missing; run --write to generate")
        return errors

    try:
        existing_json = json.loads(OUT_JSON.read_text(encoding="utf-8"))
    except (json.JSONDecodeError, OSError) as e:
        errors.append(f"Could not read existing {OUT_JSON}: {e}")
        return errors

    fresh = snapshot
    ignore = volatile_fields()

    for key in set(fresh.keys()) | set(existing_json.keys()):
        if key in ignore:
            continue
        if key not in fresh:
            errors.append(f"Generated snapshot missing key {key}")
        elif key not in existing_json:
            errors.append(f"Existing snapshot missing key {key}")
        elif fresh[key] != existing_json[key]:
            errors.append(f"Progress snapshot drift on {key}: generated differs from committed")

    existing_md = OUT_MD.read_text(encoding="utf-8")
    expected_md = build_markdown(snapshot)
    if normalize_markdown(existing_md) != normalize_markdown(expected_md):
        errors.append("Progress markdown does not match generated content")

    return errors


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Generate or check A14 v8 progress snapshot")
    group = parser.add_mutually_exclusive_group()
    group.add_argument("--write", action="store_true", help="write generated snapshot to disk")
    group.add_argument("--check", action="store_true", help="check existing snapshot is fresh (read-only)")
    group.add_argument("--print", action="store_true", help="print generated snapshot to stdout (read-only)")
    args = parser.parse_args(argv)

    if not (args.write or args.check or args.print):
        parser.print_help()
        return 2

    source_commit = git_rev("HEAD")
    source_tree = git_rev("HEAD^{tree}")
    try:
        snapshot = generate_snapshot(source_commit, source_tree)
    except ValueError as e:
        print(f"progress_snapshot: {e}", file=sys.stderr)
        return 1

    if args.print:
        print(json.dumps(snapshot, indent=2))
        return 0

    if args.check:
        errors = check_snapshot(snapshot)
        if errors:
            print("Progress snapshot drift:")
            for e in errors:
                print(f"  - {e}")
            return 1
        print("Progress snapshot is fresh.")
        return 0

    if args.write:
        OUT_JSON.parent.mkdir(parents=True, exist_ok=True)
        OUT_JSON.write_text(json.dumps(snapshot, indent=2), encoding="utf-8", newline="\n")
        OUT_MD.write_text(build_markdown(snapshot), encoding="utf-8", newline="\n")
        print(f"Wrote {OUT_JSON}")
        print(f"Wrote {OUT_MD}")
        return 0

    return 2


if __name__ == "__main__":
    raise SystemExit(main())
