#!/usr/bin/env python3
"""Read-only control-state reconciliation for A14 professional autonomous stewardship.

Exits non-zero on:
- duplicate keys in SMART_OPERATION_STATE.md
- unknown/invalid keys or values in SMART_OPERATION_STATE.md
- non-existent commits referenced by SMART_OPERATION_STATE.md / TASK_STATE.md
- false CI state
- parent/child state mismatch in TASK_STATE.md
- stale issue-queue entries
- empty checkpoint section
- stop-rule conflicts between GOAL.md / AGENTS.md / SMART_CONTINUOUS_OPERATION.md
"""

from __future__ import annotations

import argparse
import io
import re
import subprocess
import sys
from pathlib import Path
from typing import Any

# Ensure stdout can emit UTF-8 text on Windows consoles.
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")


REPO_ROOT = Path(__file__).resolve().parent.parent
SMART_REQUIRED_KEYS = {
    "Mode",
    "CheckpointCount",
    "CheckpointsSinceStandardSweep",
    "CheckpointsSinceDeepSweep",
    "LastQualifyingCheckpoint",
    "LastLightSweepCommit",
    "LastStandardSweepCommit",
    "LastDeepSweepCommit",
    "LastFullVerificationCommit",
    "LastVerifiedTree",
    "LastVerifiedTreeSource",
    "LastVerifiedTreeCommand",
    "LastVerifiedMode",
    "LastCIState",
    "LastCIRun",
    "LastCIJob",
    "LastCICommit",
    "LastCleanupCommit",
    "LastToolCreated",
    "LastFailureClass",
    "CurrentObjective",
    "CurrentObjectiveState",
    "CurrentObjectiveStartEvidence",
    "NextObjectiveFirstAction",
    "ResumeTask",
    "DeepSweepDue",
}
VALID_MODES = {"PROFESSIONAL_AUTONOMOUS_STEWARDSHIP"}
VALID_CI_STATES = {"NOT_CONFIGURED", "PENDING", "PASS", "FAIL", "UNAVAILABLE"}
VALID_OBJECTIVE_STATES = {"ACTIVE", "PAUSED", "BLOCKED", "COMPLETE"}


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def parse_smart_state(text: str) -> str:
    """Extract the first ```text block from SMART_OPERATION_STATE.md."""
    match = re.search(r"```text\s*\n(.*?)\n\s*```", text, re.DOTALL)
    if not match:
        raise ValueError("SMART_OPERATION_STATE.md missing ```text block")
    return match.group(1)


def smart_state_dict(block: str) -> tuple[dict[str, str], list[str]]:
    """Parse key: value lines, reporting duplicate keys and unknown keys."""
    seen: dict[str, str] = {}
    errors: list[str] = []
    for raw in block.splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        if ":" not in line:
            continue
        key, value = line.split(":", 1)
        key = key.strip()
        value = value.strip()
        if key in seen:
            errors.append(f"SMART_OPERATION_STATE duplicate key: {key}")
        else:
            seen[key] = value
        if key not in SMART_REQUIRED_KEYS:
            errors.append(f"SMART_OPERATION_STATE unknown key: {key}")
    return seen, errors


def git_object_exists(sha: str, repo_root: Path = REPO_ROOT) -> bool:
    """Check whether a Git object exists in the current repository."""
    if not sha or sha.lower() == "pending":
        return False
    try:
        subprocess.run(
            ["git", "rev-parse", "--verify", f"{sha}^{{commit}}"],
            cwd=repo_root,
            check=True,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
        return True
    except (subprocess.CalledProcessError, FileNotFoundError):
        return False


def check_smart_state(path: Path, raw_text: str, repo_root: Path) -> list[str]:
    errors: list[str] = []
    try:
        block = parse_smart_state(raw_text)
    except ValueError as e:
        return [str(e)]
    state, parse_errors = smart_state_dict(block)
    errors.extend(parse_errors)

    missing = SMART_REQUIRED_KEYS - set(state.keys())
    for key in sorted(missing):
        errors.append(f"SMART_OPERATION_STATE missing key: {key}")

    mode = state.get("Mode", "")
    if mode and mode not in VALID_MODES:
        errors.append(f"SMART_OPERATION_STATE invalid Mode: {mode}")

    ci = state.get("LastCIState", "")
    if ci and ci not in VALID_CI_STATES:
        errors.append(f"SMART_OPERATION_STATE invalid LastCIState: {ci}")

    workflow_files = list((repo_root / ".github" / "workflows").glob("a14-*.yml"))
    has_workflow = bool(workflow_files)

    if ci == "NOT_CONFIGURED" and has_workflow:
        errors.append("SMART_OPERATION_STATE LastCIState=NOT_CONFIGURED but A14 workflows exist")

    if ci == "PENDING" and not has_workflow:
        errors.append("SMART_OPERATION_STATE LastCIState=PENDING but no A14 workflow exists")

    if ci in ("PASS", "FAIL"):
        for key in ("LastCIRun", "LastCIJob", "LastCICommit"):
            value = state.get(key, "")
            if not value or value.lower() == "pending":
                errors.append(f"SMART_OPERATION_STATE LastCIState={ci} but {key} is missing")
        ci_commit = state.get("LastCICommit", "")
        if ci_commit and ci_commit.lower() != "pending" and not git_object_exists(ci_commit, repo_root):
            errors.append(f"SMART_OPERATION_STATE LastCIState={ci} but LastCICommit does not resolve: {ci_commit}")

    if ci == "PASS":
        # PASS must reference a commit that is an ancestor of the current HEAD.
        # State-only bookkeeping commits therefore record the previously-verified
        # qualifying commit without re-breaking governance.
        try:
            ci_commit = state.get("LastCICommit", "")
            if ci_commit and ci_commit.lower() != "pending":
                result = subprocess.run(
                    ["git", "merge-base", "--is-ancestor", ci_commit, "HEAD"],
                    cwd=repo_root,
                    capture_output=True,
                    text=True,
                )
                if result.returncode != 0:
                    errors.append("SMART_OPERATION_STATE LastCIState=PASS but LastCICommit is not an ancestor of the current HEAD")
        except (subprocess.CalledProcessError, FileNotFoundError):
            pass

    for key in ("LastStandardSweepCommit", "LastDeepSweepCommit", "LastFullVerificationCommit"):
        value = state.get(key, "")
        if value and value.lower() != "pending" and not git_object_exists(value, repo_root):
            errors.append(f"SMART_OPERATION_STATE {key} references non-existent commit: {value}")

    last_qualifying = state.get("LastQualifyingCheckpoint", "")
    if last_qualifying and last_qualifying.lower() != "pending" and not git_object_exists(last_qualifying, repo_root):
        errors.append(f"SMART_OPERATION_STATE LastQualifyingCheckpoint references non-existent commit: {last_qualifying}")

    checkpoint_count = state.get("CheckpointCount", "")
    try:
        count = int(checkpoint_count) if checkpoint_count else 0
        if count < 0:
            errors.append("SMART_OPERATION_STATE CheckpointCount must be non-negative")
    except ValueError:
        errors.append(f"SMART_OPERATION_STATE CheckpointCount is not an integer: {checkpoint_count}")

    # Objective handoff invariants (see SMART_CONTINUOUS_OPERATION v3 and A14 CI preflight).
    objective_state = state.get("CurrentObjectiveState", "")
    if objective_state and objective_state not in VALID_OBJECTIVE_STATES:
        errors.append(f"SMART_OPERATION_STATE invalid CurrentObjectiveState: {objective_state}")

    start_evidence = state.get("CurrentObjectiveStartEvidence", "")
    if objective_state == "ACTIVE" and (not start_evidence or start_evidence.lower() == "pending"):
        errors.append("SMART_OPERATION_STATE CurrentObjectiveState=ACTIVE but CurrentObjectiveStartEvidence is missing")

    next_action = state.get("NextObjectiveFirstAction", "")
    if not next_action:
        errors.append("SMART_OPERATION_STATE NextObjectiveFirstAction is missing")
    elif any(token in next_action for token in (" or ", " / ", ";", ",")):
        errors.append("SMART_OPERATION_STATE NextObjectiveFirstAction must be a single action, no 'or' / '/' / ';' / ','")

    resume = state.get("ResumeTask", "")
    if any(token in resume for token in (" or ", " / ")):
        errors.append("SMART_OPERATION_STATE ResumeTask must not contain ' or ' or ' / '")

    # LastVerifiedTree must be an actual 40-character tree hash, not a symbolic reference.
    verified_tree = state.get("LastVerifiedTree", "")
    if not re.fullmatch(r"[0-9a-f]{40}", verified_tree.lower()):
        errors.append(f"SMART_OPERATION_STATE LastVerifiedTree must be a 40-char lowercase hex tree SHA: {verified_tree}")
    else:
        # Confirm it is the tree object of a known commit if possible.
        try:
            result = subprocess.run(
                ["git", "cat-file", "-t", verified_tree],
                cwd=repo_root,
                capture_output=True,
                text=True,
                check=True,
            )
            if result.stdout.strip() != "tree":
                errors.append(f"SMART_OPERATION_STATE LastVerifiedTree is not a tree object: {verified_tree}")
        except (subprocess.CalledProcessError, FileNotFoundError):
            errors.append(f"SMART_OPERATION_STATE LastVerifiedTree does not exist in repo: {verified_tree}")

    # Deep sweep accounting.
    deep_sweep_count = state.get("CheckpointsSinceDeepSweep", "")
    try:
        deep_count = int(deep_sweep_count) if deep_sweep_count else 0
        if deep_count >= 10 and state.get("DeepSweepDue", "").lower() != "true":
            errors.append("SMART_OPERATION_STATE CheckpointsSinceDeepSweep >= 10 but DeepSweepDue is not true")
    except ValueError:
        pass

    return errors


SECTION_RE = re.compile(r"^#{1,2} (P\d+(?:\.\d+)?)(?:\s|—|$)", re.MULTILINE)


def parse_task_sections(text: str) -> dict[str, dict[str, Any]]:
    """Parse TASK_STATE.md sections like P3, P3.1, P5, etc."""
    sections: dict[str, dict[str, Any]] = {}
    matches = list(SECTION_RE.finditer(text))
    for i, match in enumerate(matches):
        sid = match.group(1)
        start = match.end()
        end = matches[i + 1].start() if i + 1 < len(matches) else len(text)
        window = text[start:end]
        state_match = re.search(r"State: `([^`]+)`", window)
        sections[sid] = {
            "state": state_match.group(1) if state_match else "UNKNOWN",
            "children": [],
        }
    return sections


def build_parent_child(sections: dict[str, dict[str, Any]]) -> list[str]:
    """Link P#.# children to P# parents and validate state consistency."""
    errors: list[str] = []
    for sid in sections:
        if "." in sid:
            parent_id = sid.split(".")[0]
            if parent_id in sections:
                sections[parent_id]["children"].append(sid)

    for sid, info in sections.items():
        if "." in sid:
            continue
        children = info.get("children", [])
        parent_state = info["state"]
        child_states = [sections[c]["state"] for c in children]

        if parent_state == "COMPLETE":
            for c, st in zip(children, child_states):
                if st not in ("COMPLETE", "BLOCKED_EXTERNAL", "NOT_APPLICABLE"):
                    errors.append(f"TASK_STATE {sid} is COMPLETE but child {c} is {st}")
        if parent_state == "TODO" and children:
            if any(st == "COMPLETE" for st in child_states):
                errors.append(f"TASK_STATE {sid} is TODO but has COMPLETE children")

    return errors


def parent_is_complete(text: str, parent_id: str) -> bool:
    """Check whether a parent phase like P6 is marked COMPLETE."""
    pattern = re.compile(rf"^# {re.escape(parent_id)} — .+?\n+State: `([^`]+)`", re.MULTILINE | re.DOTALL)
    match = pattern.search(text)
    if match:
        return match.group(1) == "COMPLETE"
    return False


def check_issue_queue(text: str) -> list[str]:
    """Parse the issue queue table and flag stale TODO/COMPLETE mismatches."""
    errors: list[str] = []
    table_match = re.search(r"\|\s*ID\s*\|.*?\n((?:\|[^\n]+\|\n?)+)", text)
    if not table_match:
        return errors

    rows = [line for line in table_match.group(1).strip().splitlines() if line.startswith("|")]
    for row in rows:
        cells = [c.strip() for c in row.split("|")][1:-1]
        if len(cells) < 6:
            continue
        issue_id, priority, area, state, evidence, acceptance = cells[:6]
        if state == "COMPLETE" and ("尚未" in evidence or "未运行" in evidence or evidence.strip() == ""):
            errors.append(f"TASK_STATE issue {issue_id} is COMPLETE but evidence is stale: {evidence}")
        if state == "TODO" and "完成" in acceptance:
            # Only flag if the referenced parent is already COMPLETE or the acceptance
            # is a plain completion phrase with no pending parent.
            parent_match = re.search(r"P\d+(?:\.\d+)?", acceptance)
            if parent_match:
                if not parent_is_complete(text, parent_match.group(0)):
                    continue
            errors.append(f"TASK_STATE issue {issue_id} is TODO but acceptance implies complete: {acceptance}")

    return errors


def check_checkpoint_section(text: str) -> list[str]:
    errors: list[str] = []
    match = re.search(r"## 5\. Checkpoint\s*(.*?)\n##", text, re.DOTALL)
    if not match:
        return ["TASK_STATE missing Checkpoint section"]
    section = match.group(1).strip()
    if "尚无" in section and "```text" not in section:
        errors.append("TASK_STATE Checkpoint section is empty ('尚无'); record qualifying commits")
    return errors


def check_stop_conflicts(texts: dict[str, str]) -> list[str]:
    """Ensure GOAL.md, AGENTS.md and SMART_CONTINUOUS_OPERATION.md do not ask to stop/wait."""
    errors: list[str] = []
    for name, text in texts.items():
        if re.search(r"停止[^，；。]*等待仓库所有者", text):
            errors.append(f"{name} still contains '停止...等待仓库所有者' post-completion action")
    return errors


def _parse_skill_frontmatter(text: str) -> dict[str, str]:
    """Parse a minimal YAML frontmatter block from a SKILL.md file."""
    data: dict[str, str] = {}
    if not text.startswith("---"):
        return data
    end = text.find("---", 3)
    if end == -1:
        return data
    for line in text[3:end].splitlines():
        if ":" in line:
            key, value = line.split(":", 1)
            data[key.strip()] = value.strip()
    return data


def check_control_plane_migration(repo_root: Path = REPO_ROOT) -> list[str]:
    """Validate the A14 Devin Local control-plane migration invariants."""
    errors: list[str] = []

    # 1. Required Skill files exist with correct names and explicit user-only triggers.
    skill_impl = repo_root / ".agents" / "skills" / "a14-safe-implementation" / "SKILL.md"
    skill_review = repo_root / ".agents" / "skills" / "a14-independent-review" / "SKILL.md"
    if not skill_impl.is_file():
        errors.append("Missing .agents/skills/a14-safe-implementation/SKILL.md")
    if not skill_review.is_file():
        errors.append("Missing .agents/skills/a14-independent-review/SKILL.md")

    expected = {
        ".agents/skills/a14-safe-implementation/SKILL.md": "a14-safe-implementation",
        ".agents/skills/a14-independent-review/SKILL.md": "a14-independent-review",
    }
    for rel, name in expected.items():
        path = repo_root / rel
        if not path.is_file():
            continue
        text = read_text(path)
        fm = _parse_skill_frontmatter(text)
        if fm.get("name") != name:
            errors.append(f"{rel} frontmatter name is not {name}")
        if "user" not in (fm.get("triggers", "").lower()):
            errors.append(f"{rel} triggers must be user-only")

    # 2. A14 repository-specific files must not contain A13 repository/branch/Skill names.
    a14_files = [
        repo_root / ".agents" / "skills" / "a14-safe-implementation" / "SKILL.md",
        repo_root / ".agents" / "skills" / "a14-independent-review" / "SKILL.md",
    ]
    a14_docs = repo_root / "docs" / "process"
    if a14_docs.is_dir():
        a14_files.extend(p for p in a14_docs.rglob("*") if p.is_file() and p.suffix == ".md")
    forbidden_a13 = [
        "tomthenpc/customiuizer-a13",
        "devin/a13-rom-intelligence-audit",
        "a13-safe-implementation",
        "a13-independent-review",
    ]
    for path in a14_files:
        if not path.is_file():
            continue
        text = read_text(path)
        for token in forbidden_a13:
            if token in text:
                errors.append(f"{path.relative_to(repo_root).as_posix()} contains A13 reference: {token}")

    # 3. Control documents must declare the atomic session model.
    control_texts = {
        "AGENTS.md": (repo_root / "AGENTS.md"),
        "SMART_CONTINUOUS_OPERATION.md": (repo_root / "SMART_CONTINUOUS_OPERATION.md"),
        "DEVIN_START_PROMPT.md": (repo_root / "DEVIN_START_PROMPT.md"),
    }
    control: dict[str, str] = {}
    for name, path in control_texts.items():
        if not path.is_file():
            errors.append(f"Missing {name}")
            continue
        control[name] = read_text(path)

    agents = control.get("AGENTS.md", "")
    if "a14-safe-implementation" not in agents:
        errors.append("AGENTS.md does not reference a14-safe-implementation")
    if "a14-independent-review" not in agents:
        errors.append("AGENTS.md does not reference a14-independent-review")
    if "当前会话不得自行选择第二个目标" not in agents and "不得自行选择第二个目标" not in agents:
        errors.append("AGENTS.md does not declare one-slice-per-session")
    if "R2" not in agents or "a14-independent-review" not in agents:
        errors.append("AGENTS.md does not declare R2/R3/R4 independent review")

    smart = control.get("SMART_CONTINUOUS_OPERATION.md", "")
    if "ATOMIC_TASK_SLICE" not in smart:
        errors.append("SMART_CONTINUOUS_OPERATION.md does not declare SessionMode: ATOMIC_TASK_SLICE")
    if "AutoStartNextSlice: false" not in smart:
        errors.append("SMART_CONTINUOUS_OPERATION.md does not declare AutoStartNextSlice: false")

    devin = control.get("DEVIN_START_PROMPT.md", "")
    if "@skills:a14-safe-implementation" not in devin:
        errors.append("DEVIN_START_PROMPT.md missing @skills:a14-safe-implementation")
    if "@skills:a14-independent-review" not in devin:
        errors.append("DEVIN_START_PROMPT.md missing @skills:a14-independent-review")
    if "P0.1" in devin or "自动进入下一任务" in devin or "立即执行，不要只输出计划" in devin:
        errors.append("DEVIN_START_PROMPT.md still contains the old autonomous start prompt")

    # 4. No same-context automatic continuation after handoff.
    auto_continue = {
        "AGENTS.md": ["自动进入下一任务", "自动继续下一闭环"],
        "SMART_CONTINUOUS_OPERATION.md": ["自动进入下一任务", "自动继续下一闭环"],
        "GOAL.md": ["随后自动进入"],
        "DEVIN_START_PROMPT.md": ["自动进入下一任务"],
    }
    for doc_name, phrases in auto_continue.items():
        path = repo_root / doc_name
        if not path.is_file():
            continue
        text = read_text(path)
        for phrase in phrases:
            if phrase in text:
                errors.append(f"{doc_name} still requires same-context auto-continuation: {phrase}")

    return errors


def check_task_state(path: Path, raw_text: str) -> list[str]:
    errors: list[str] = []
    sections = parse_task_sections(raw_text)
    errors.extend(build_parent_child(sections))
    errors.extend(check_issue_queue(raw_text))
    errors.extend(check_checkpoint_section(raw_text))
    return sections, errors


def parse_issue_table(text: str) -> list[dict[str, str]]:
    """Parse the issue queue table and return a list of row dicts."""
    table_match = re.search(r"\|\s*ID\s*\|.*\n((?:\|[^\n]+\|\n?)+)", text)
    if not table_match:
        return []

    rows = [line for line in table_match.group(1).strip().splitlines() if line.startswith("|")]
    issues: list[dict[str, str]] = []
    for row in rows:
        cells = [c.strip() for c in row.split("|")][1:-1]
        if len(cells) < 6:
            continue
        issues.append({
            "id": cells[0],
            "priority": cells[1],
            "area": cells[2],
            "state": cells[3],
            "evidence": cells[4],
            "acceptance": cells[5],
        })
    return issues


def check_current_objective(smart_text: str, task_text: str) -> list[str]:
    """Cross-validate SMART CurrentObjective against TASK_STATE.md."""
    errors: list[str] = []
    try:
        block = parse_smart_state(smart_text)
    except ValueError:
        return errors
    state, _ = smart_state_dict(block)

    current = state.get("CurrentObjective", "")
    obj_state = state.get("CurrentObjectiveState", "")

    if obj_state == "COMPLETE":
        errors.append("SMART_OPERATION_STATE CurrentObjectiveState must not be COMPLETE; move to next incomplete objective")

    # Find the matching P# section in TASK_STATE.md.
    sections = parse_task_sections(task_text)
    section_id = None
    objective_prefix = re.match(r"(P\d+(?:\.\d+)?)", current)
    objective_id = objective_prefix.group(1) if objective_prefix else ""
    for sid in sections:
        if sid == objective_id:
            section_id = sid
            break

    if section_id:
        task_state = sections[section_id]["state"]
        if obj_state == "COMPLETE" and task_state != "COMPLETE":
            errors.append(
                f"SMART_OPERATION_STATE CurrentObjectiveState=COMPLETE but {section_id} state in TASK_STATE is {task_state}"
            )
        if obj_state != "COMPLETE" and task_state == "COMPLETE":
            errors.append(
                f"SMART_OPERATION_STATE CurrentObjective is {section_id} ({task_state}) but CurrentObjectiveState is not COMPLETE"
            )
    elif current:
        # Unknown objective that does not map to a task section.
        errors.append(f"SMART_OPERATION_STATE CurrentObjective '{current}' does not match any P# section")

    # Block choosing P2/P3 while an unblocked P1 is not COMPLETE/BLOCKED_EXTERNAL/CORE_COMPLETE.
    allowed_incomplete_p1 = {"COMPLETE", "BLOCKED_EXTERNAL", "NOT_APPLICABLE"}
    issues = parse_issue_table(task_text)
    unblocked_p1 = [
        i for i in issues
        if i["priority"] == "P1" and i["state"] not in allowed_incomplete_p1
    ]
    if unblocked_p1:
        parents = set()
        for i in unblocked_p1:
            m = re.search(r"P\d+(?:\.\d+)?", i["acceptance"])
            if m:
                parents.add(m.group(0))
        # The current objective should explicitly reference at least one parent of an unblocked P1.
        if not any(parent in current for parent in parents):
            errors.append(
                f"SMART_OPERATION_STATE CurrentObjective '{current}' does not address unblocked P1 issues { {i['id'] for i in unblocked_p1} } whose parents are {parents}"
            )

    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description="A14 control-state reconciliation")
    parser.add_argument("--repo-root", type=Path, default=REPO_ROOT)
    args = parser.parse_args()
    repo_root = args.repo_root

    smart_path = repo_root / "SMART_OPERATION_STATE.md"
    task_path = repo_root / "TASK_STATE.md"
    goal_path = repo_root / "GOAL.md"
    agents_path = repo_root / "AGENTS.md"
    smart_op_path = repo_root / "SMART_CONTINUOUS_OPERATION.md"

    all_errors: list[str] = []

    if smart_path.exists():
        all_errors.extend(check_smart_state(smart_path, read_text(smart_path), repo_root))
    else:
        all_errors.append("Missing SMART_OPERATION_STATE.md")

    task_text = ""
    if task_path.exists():
        task_text = read_text(task_path)
        _, task_errors = check_task_state(task_path, task_text)
        all_errors.extend(task_errors)
    else:
        all_errors.append("Missing TASK_STATE.md")

    if smart_path.exists() and task_path.exists():
        all_errors.extend(check_current_objective(read_text(smart_path), task_text))

    progress_snapshot = repo_root / "tools" / "progress_snapshot.py"
    if progress_snapshot.is_file():
        try:
            result = subprocess.run(
                [sys.executable, str(progress_snapshot), "--check"],
                cwd=repo_root,
                capture_output=True,
                text=True,
                check=False,
            )
            if result.returncode != 0:
                all_errors.append(f"progress_snapshot.py --check failed: {result.stdout.strip() or result.stderr.strip()}")
        except (OSError, subprocess.SubprocessError) as e:
            all_errors.append(f"could not run progress_snapshot.py --check: {e}")

    texts = {
        "GOAL.md": read_text(goal_path) if goal_path.exists() else "",
        "AGENTS.md": read_text(agents_path) if agents_path.exists() else "",
        "SMART_CONTINUOUS_OPERATION.md": read_text(smart_op_path) if smart_op_path.exists() else "",
    }
    all_errors.extend(check_stop_conflicts(texts))
    all_errors.extend(check_control_plane_migration(repo_root))

    if all_errors:
        print("CONTROL-STATE INVARIANT VIOLATIONS:")
        for err in all_errors:
            print(f"  - {err}")
        return 1

    print("Control-state invariants pass.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
