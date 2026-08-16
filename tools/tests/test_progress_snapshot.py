import json
import os
import shutil
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from tools import progress_snapshot

HEAD_COMMIT = "5febf001a75831163ad75991d8bd52ee570fd804"
PARENT_COMMIT = "1111111111111111111111111111111111111111"
FULL_COMMIT = "cd152365a1b258a7b36e978d1050db71f427fa83"


def _fake_canonical(sha: str) -> str | None:
    if not sha or not re_fullmatch(r"[0-9a-f]{7,40}", sha, 2):
        return None
    s = sha.lower()
    if s == HEAD_COMMIT or s == "0" * 40:
        return None
    if s == FULL_COMMIT or s == PARENT_COMMIT:
        return s
    if FULL_COMMIT.startswith(s):
        return FULL_COMMIT
    if PARENT_COMMIT.startswith(s):
        return PARENT_COMMIT
    return None


def _fake_git_rev(name: str) -> str:
    if name == "HEAD":
        return HEAD_COMMIT
    if name == "HEAD~1":
        return PARENT_COMMIT
    if name == "HEAD^{tree}":
        return HEAD_COMMIT
    return "pending"


# re.fullmatch with flags helper to avoid re import collision
def re_fullmatch(pattern, string, flags=0):
    import re

    return re.fullmatch(pattern, string, flags)


class V2RepoFixtureMixin:
    def _make_repo(self, task_files, roadmap="", git=False):
        td = tempfile.TemporaryDirectory()
        self.addCleanup(td.cleanup)
        root = Path(td.name)

        old_root = progress_snapshot.REPO_ROOT
        old_json = progress_snapshot.OUT_JSON
        old_md = progress_snapshot.OUT_MD

        def restore():
            progress_snapshot.REPO_ROOT = old_root
            progress_snapshot.OUT_JSON = old_json
            progress_snapshot.OUT_MD = old_md

        self.addCleanup(restore)
        progress_snapshot.REPO_ROOT = root
        progress_snapshot.OUT_JSON = root / "A14_PROGRESS_CURRENT.json"
        progress_snapshot.OUT_MD = root / "A14_PROGRESS_CURRENT.md"

        (root / "tasks" / "active").mkdir(parents=True, exist_ok=True)
        (root / "tasks" / "backlog").mkdir(parents=True, exist_ok=True)
        (root / "tasks" / "blocked").mkdir(parents=True, exist_ok=True)
        (root / "tasks" / "completed").mkdir(parents=True, exist_ok=True)
        (root / "docs" / "progress").mkdir(parents=True, exist_ok=True)

        (root / "ROADMAP.md").write_text(roadmap, encoding="utf-8")

        for path_parts, content in task_files.items():
            p = root / path_parts
            p.parent.mkdir(parents=True, exist_ok=True)
            p.write_text(content, encoding="utf-8")

        if git:
            subprocess_git = self._init_git(root)
            if not subprocess_git:
                self.skipTest("git not available")

        return root

    def _init_git(self, root: Path):
        import subprocess

        if not shutil.which("git"):
            return False
        try:
            subprocess.run(["git", "init"], cwd=root, check=True, capture_output=True)
            subprocess.run(
                ["git", "-c", "user.email=test@test.com", "-c", "user.name=Test", "commit", "--allow-empty", "-m", "init"],
                cwd=root,
                check=True,
                capture_output=True,
            )
            return True
        except (subprocess.CalledProcessError, OSError):
            return False

    def _write_task(self, root: Path, directory: str, name: str, body: str) -> None:
        p = root / "tasks" / directory / name
        p.write_text(body, encoding="utf-8")


class ProgressSnapshotV8Test(V2RepoFixtureMixin, unittest.TestCase):
    def setUp(self):
        self._patchers = [
            patch("tools.progress_snapshot.canonical_commit", side_effect=_fake_canonical),
            patch("tools.progress_snapshot.git_rev", side_effect=_fake_git_rev),
        ]
        for p in self._patchers:
            p.start()
            self.addCleanup(p.stop)

        # Start every test in an isolated, empty v2 repo so --print and other
        # commands never accidentally read the real working tree.
        td = tempfile.TemporaryDirectory()
        self.addCleanup(td.cleanup)
        root = Path(td.name)
        (root / "tasks" / "active").mkdir(parents=True, exist_ok=True)
        (root / "tasks" / "backlog").mkdir(parents=True, exist_ok=True)
        (root / "tasks" / "blocked").mkdir(parents=True, exist_ok=True)
        (root / "tasks" / "completed").mkdir(parents=True, exist_ok=True)
        (root / "docs" / "progress").mkdir(parents=True, exist_ok=True)
        (root / "ROADMAP.md").write_text("# Roadmap\n", encoding="utf-8")

        self._base_root = root
        self._base_old_root = progress_snapshot.REPO_ROOT
        self._base_old_json = progress_snapshot.OUT_JSON
        self._base_old_md = progress_snapshot.OUT_MD

        def restore():
            progress_snapshot.REPO_ROOT = self._base_old_root
            progress_snapshot.OUT_JSON = self._base_old_json
            progress_snapshot.OUT_MD = self._base_old_md

        self.addCleanup(restore)
        progress_snapshot.REPO_ROOT = root
        progress_snapshot.OUT_JSON = root / "A14_PROGRESS_CURRENT.json"
        progress_snapshot.OUT_MD = root / "A14_PROGRESS_CURRENT.md"

    def _task(self, title, status="Active", priority="P0", extras=""):
        return f"""# {title}

- Platform: A14
- Status: {status}
- Priority: {priority}
- Owner: Devin

## 目标

A test task.

## 验收标准

- [ ] pass

{extras}
"""

    def _temp_outputs(self):
        td = tempfile.TemporaryDirectory()
        self.addCleanup(td.cleanup)
        root = Path(td.name)
        old_json = progress_snapshot.OUT_JSON
        old_md = progress_snapshot.OUT_MD

        def restore():
            progress_snapshot.OUT_JSON = old_json
            progress_snapshot.OUT_MD = old_md

        self.addCleanup(restore)
        progress_snapshot.OUT_JSON = root / "A14_PROGRESS_CURRENT.json"
        progress_snapshot.OUT_MD = root / "A14_PROGRESS_CURRENT.md"
        return root

    def test_no_args_prints_help_and_does_not_write(self):
        code = progress_snapshot.main([])
        self.assertEqual(2, code)

    def test_print_is_read_only(self):
        before_json = progress_snapshot.OUT_JSON.stat().st_mtime if progress_snapshot.OUT_JSON.is_file() else None
        before_md = progress_snapshot.OUT_MD.stat().st_mtime if progress_snapshot.OUT_MD.is_file() else None
        code = progress_snapshot.main(["--print"])
        self.assertEqual(0, code)
        if before_json is not None:
            self.assertEqual(before_json, progress_snapshot.OUT_JSON.stat().st_mtime)
        if before_md is not None:
            self.assertEqual(before_md, progress_snapshot.OUT_MD.stat().st_mtime)

    def test_check_is_read_only(self):
        self._temp_outputs()
        self._make_repo({
            Path("tasks/active/T1.md"): self._task("FIX-T1", "Active", "P0"),
        })
        progress_snapshot.main(["--write"])
        before_json = progress_snapshot.OUT_JSON.stat().st_mtime
        before_md = progress_snapshot.OUT_MD.stat().st_mtime
        code = progress_snapshot.main(["--check"])
        self.assertEqual(0, code)
        self.assertEqual(before_json, progress_snapshot.OUT_JSON.stat().st_mtime)
        self.assertEqual(before_md, progress_snapshot.OUT_MD.stat().st_mtime)

    def test_check_detects_drift(self):
        self._temp_outputs()
        self._make_repo({
            Path("tasks/active/T1.md"): self._task("FIX-T1", "Active", "P0"),
        })
        progress_snapshot.main(["--write"])
        existing = json.loads(progress_snapshot.OUT_JSON.read_text(encoding="utf-8"))
        existing["projectProgress"] = -1.0
        progress_snapshot.OUT_JSON.write_text(json.dumps(existing, indent=2), encoding="utf-8")
        code = progress_snapshot.main(["--check"])
        self.assertEqual(1, code)

    def test_parent_child_not_double_counted(self):
        self._make_repo({
            Path("tasks/active/P5.md"): self._task("P5", "Active", "P2"),
            Path("tasks/active/P5.1.md"): self._task("P5.1", "Active", "P2"),
            Path("tasks/active/P5.2.md"): self._task("P5.2", "Active", "P2"),
        })
        leaves = progress_snapshot.load_task_state_v2()["leaves"]
        ids = set(leaves)
        self.assertIn("P5.1", ids)
        self.assertNotIn("P5", ids)

    def test_blocked_external_accounted(self):
        self._make_repo({
            Path("tasks/blocked/T1.md"): self._task("FIX-T1", "Blocked", "P0"),
        })
        leaves = progress_snapshot.load_task_state_v2()["leaves"]
        items = progress_snapshot.build_capability_items(leaves)
        buckets = {b: 0 for b in progress_snapshot.compute_progress([])["taskCounts"] if b != "total"}
        for it in items:
            buckets[it.bucket] = buckets.get(it.bucket, 0) + 1
        self.assertEqual(sum(buckets.values()), progress_snapshot.compute_progress(items)["taskCounts"]["total"])
        self.assertGreaterEqual(buckets["blocked_external"], 1)

    def test_machine_progress_excludes_device(self):
        self._make_repo({
            Path("tasks/completed/T1.md"): self._task("FIX-T1", "Done", "P0"),
            Path("tasks/completed/T2.md"): self._task("FEATURE-T2", "Done", "P0"),
        })
        leaves = progress_snapshot.load_task_state_v2()["leaves"]
        # Completed without commit provenance; device domain earns 0.
        items = progress_snapshot.build_capability_items(leaves)
        progress = progress_snapshot.compute_progress(items)
        device_earned = sum(it.earned for it in items if it.domain == "Device validation")
        non_device_total = 95.0
        non_device_earned = sum(it.earned for it in items if it.domain != "Device validation")
        expected = round(non_device_earned / non_device_total * 100, 1)
        self.assertEqual(expected, progress["machineProgressPercent"])
        self.assertEqual(0.0, device_earned)

    def test_sum_of_buckets_equals_total(self):
        self._make_repo({
            Path("tasks/active/T1.md"): self._task("FIX-T1", "Active", "P0"),
            Path("tasks/backlog/T2.md"): self._task("FEATURE-T2", "Backlog", "P1"),
            Path("tasks/blocked/T3.md"): self._task("FIX-T3", "Blocked", "P0"),
        })
        leaves = progress_snapshot.load_task_state_v2()["leaves"]
        items = progress_snapshot.build_capability_items(leaves)
        progress = progress_snapshot.compute_progress(items)
        counts = progress["taskCounts"]
        total = sum(counts[k] for k in counts if k != "total")
        self.assertEqual(counts["total"], total)

    def test_state_factor_bucket_consistency(self):
        """Every known state maps to a recognized bucket and a non-negative factor."""
        known_buckets = set(progress_snapshot.compute_progress([])["taskCounts"].keys()) - {"total"}
        for state, factor in progress_snapshot.STATE_FACTORS.items():
            bucket = progress_snapshot.item_bucket(state)
            self.assertIn(bucket, known_buckets, f"{state} maps to unknown bucket {bucket}")
            self.assertGreaterEqual(factor, 0.0, f"{state} factor must be non-negative")

    def test_unknown_and_not_applicable_buckets(self):
        self.assertEqual("fail", progress_snapshot.item_bucket("UNKNOWN"))
        self.assertEqual("excluded", progress_snapshot.item_bucket("NOT_APPLICABLE"))

    def test_p12_children_preserved(self):
        """Removing an unfinished P12 child from v2 tasks must fail validation."""
        self._make_repo({
            Path("tasks/completed/P12.1.md"): self._task("P12.1", "Done", "P0"),
            Path("tasks/completed/P12.2.md"): self._task("P12.2", "Done", "P0"),
            Path("tasks/completed/P12.3.md"): self._task("P12.3", "Done", "P0"),
            Path("tasks/completed/P12.4.md"): self._task("P12.4", "Done", "P0"),
        })
        state = progress_snapshot.load_task_state_v2()
        items = progress_snapshot.build_capability_items(state["leaves"])

        # Baseline is valid: all four P12 children exist.
        progress_snapshot.validate_capability_items(items)
        p12_ids = {it.id for it in items if it.id.startswith("P12.")}
        self.assertTrue(
            p12_ids.issuperset(progress_snapshot.EXPECTED_P12_IDS),
            f"Expected {progress_snapshot.EXPECTED_P12_IDS}, got {p12_ids}",
        )

        mutated = {sid: info for sid, info in state["leaves"].items() if sid != "P12.2"}
        mutated_items = progress_snapshot.build_capability_items(mutated)
        with self.assertRaises(ValueError) as ctx:
            progress_snapshot.validate_capability_items(mutated_items)
        self.assertIn("P12.2", str(ctx.exception))

    def test_verified_static_without_evidence_fails(self):
        """A VERIFIED_STATIC item in the verified bucket with pending provenance must fail."""
        item = progress_snapshot.CapabilityItem(
            id="P99.1",
            domain="Documentation / provenance",
            weight=1.0,
            state="VERIFIED_STATIC",
            factor=0.7,
            earned=0.7,
            bucket="verified",
            evidence_level="pending",
            evidence_paths=[],
            evidence_commands=[],
        )
        with self.assertRaises(ValueError) as ctx:
            progress_snapshot.validate_capability_items([item])
        self.assertIn("evidence_level", str(ctx.exception))

    def test_single_p12_child_takes_full_weight_fails(self):
        """A single P12 child must not be weighted as the full Documentation domain."""
        items = [
            progress_snapshot.CapabilityItem(
                id="P12.1",
                domain="Documentation / provenance",
                weight=progress_snapshot.DOMAIN_WEIGHTS["Documentation / provenance"],
                state="VERIFIED_STATIC",
                factor=0.7,
                earned=3.5,
                bucket="verified",
                evidence_level="verified",
                evidence_paths=["docs/some.md"],
                evidence_commands=["python tools/x.py"],
                evidence_commit=FULL_COMMIT,
            ),
            progress_snapshot.CapabilityItem(
                id="P12.2",
                domain="Documentation / provenance",
                weight=0.0,
                state="TODO",
                factor=0.0,
                earned=0.0,
                bucket="not_started",
                evidence_level="pending",
            ),
            progress_snapshot.CapabilityItem(
                id="P12.3",
                domain="Documentation / provenance",
                weight=0.0,
                state="TODO",
                factor=0.0,
                earned=0.0,
                bucket="not_started",
                evidence_level="pending",
            ),
            progress_snapshot.CapabilityItem(
                id="P12.4",
                domain="Documentation / provenance",
                weight=0.0,
                state="TODO",
                factor=0.0,
                earned=0.0,
                bucket="not_started",
                evidence_level="pending",
            ),
        ]
        with self.assertRaises(ValueError) as ctx:
            progress_snapshot.validate_capability_items(items)
        self.assertIn("full Documentation / provenance", str(ctx.exception))

    def test_current_verified_items_have_evidence(self):
        """A v2 repo fixture must produce a valid snapshot without provenance contradictions."""
        self._make_repo({
            Path("tasks/completed/P12.1.md"): self._task("P12.1", "Done", "P0", "\n## 提交\n\n- Final SHA: `1111111111111111111111111111111111111111`\n"),
            Path("tasks/completed/P12.2.md"): self._task("P12.2", "Done", "P0", "\n## 提交\n\n- Final SHA: `1111111111111111111111111111111111111111`\n"),
            Path("tasks/completed/P12.3.md"): self._task("P12.3", "Done", "P0", "\n## 提交\n\n- Final SHA: `1111111111111111111111111111111111111111`\n"),
            Path("tasks/completed/P12.4.md"): self._task("P12.4", "Done", "P0", "\n## 提交\n\n- Final SHA: `1111111111111111111111111111111111111111`\n"),
        })
        state = progress_snapshot.load_task_state_v2()
        items = progress_snapshot.build_capability_items(state["leaves"])
        progress_snapshot.validate_capability_items(items)

    def test_active_context_empty_active_is_none(self):
        self._make_repo({
            Path("tasks/backlog/T1.md"): self._task("FIX-T1", "Backlog", "P0"),
        })
        state = progress_snapshot.load_task_state_v2()
        self.assertIsNone(state["active_context"])

    def test_completed_task_not_active_context(self):
        self._make_repo({
            Path("tasks/completed/T1.md"): self._task("FIX-T1", "Done", "P0"),
        })
        state = progress_snapshot.load_task_state_v2()
        self.assertIsNone(state["active_context"])

    def test_parked_active_not_default_context(self):
        self._make_repo({
            Path("tasks/active/T1.md"): self._task("FIX-T1", "Engineering complete | PARKED — NOT RUN / ENVIRONMENT BLOCKED", "P0"),
            Path("tasks/active/T2.md"): self._task("FEATURE-T2", "Active", "P1"),
        })
        state = progress_snapshot.load_task_state_v2()
        self.assertIsNotNone(state["active_context"])
        self.assertEqual("T2", state["active_context"]["id"])
        self.assertIn("T1", state["parked_tasks"])

    def test_multiple_active_chooses_highest_priority(self):
        self._make_repo({
            Path("tasks/active/T1.md"): self._task("FIX-T1", "Active", "P1"),
            Path("tasks/active/T2.md"): self._task("FEATURE-T2", "Active", "P0"),
        })
        state = progress_snapshot.load_task_state_v2()
        self.assertEqual("T2", state["active_context"]["id"])

    def test_blocked_only_from_blocked_or_external_active(self):
        self._make_repo({
            Path("tasks/blocked/T1.md"): self._task("FIX-T1", "Blocked", "P0"),
            Path("tasks/active/T2.md"): self._task("FEATURE-T2", "Blocked (external dependency)", "P1"),
            Path("tasks/active/T3.md"): self._task("FIX-T3", "Active", "P0"),
        })
        state = progress_snapshot.load_task_state_v2()
        items = progress_snapshot.build_capability_items(state["leaves"])
        buckets = {b: 0 for b in progress_snapshot.compute_progress([])["taskCounts"] if b != "total"}
        for it in items:
            buckets[it.bucket] = buckets.get(it.bucket, 0) + 1
        self.assertEqual(2, buckets["blocked_external"])

    def test_p12_weights_fixed_after_missing_child(self):
        self._make_repo({
            Path("tasks/completed/P12.1.md"): self._task("P12.1", "Done", "P0"),
            Path("tasks/completed/P12.2.md"): self._task("P12.2", "Done", "P0"),
            Path("tasks/completed/P12.3.md"): self._task("P12.3", "Done", "P0"),
            Path("tasks/completed/P12.4.md"): self._task("P12.4", "Done", "P0"),
        })
        state = progress_snapshot.load_task_state_v2()
        # Remove P12.4 but keep the others.
        mutated = {sid: info for sid, info in state["leaves"].items() if sid != "P12.4"}
        items = progress_snapshot.build_capability_items(mutated)
        for it in items:
            if it.id in ("P12.1", "P12.2", "P12.3"):
                self.assertEqual(1.25, it.weight, f"{it.id} must stay at 1.25")
        with self.assertRaises(ValueError) as ctx:
            progress_snapshot.validate_capability_items(items)
        self.assertIn("P12.4", str(ctx.exception))

    def test_non_p12_weights_unchanged(self):
        self._make_repo({
            Path("tasks/active/P5.1.md"): self._task("P5.1", "Active", "P2"),
            Path("tasks/active/P5.2.md"): self._task("P5.2", "Active", "P2"),
            Path("tasks/active/P5.3.md"): self._task("P5.3", "Active", "P2"),
            Path("tasks/active/P5.4.md"): self._task("P5.4", "Active", "P2"),
            Path("tasks/active/P5.5.md"): self._task("P5.5", "Active", "P2"),
        })
        state = progress_snapshot.load_task_state_v2()
        items = progress_snapshot.build_capability_items(state["leaves"])
        for it in items:
            if not it.id.startswith("P12.") and not it.id.startswith("P0"):
                self.assertGreater(it.weight, 0.0, f"{it.id} must have positive weight")
                self.assertNotAlmostEqual(1.25, it.weight, msg=f"{it.id} must not inherit the P12 1.25 split")

    def test_synthetic_complete_cannot_fake_progress_with_prose(self):
        text = """\nState: `COMPLETE`\n\n证据：\n\n- docs/progress/A14_PROGRESS_CURRENT.json 与 .md 已重新生成\n- P12.1 文档已经验证\n- 退出码 0\n- 验证通过\n"""
        ev = progress_snapshot.extract_evidence(text)
        item = progress_snapshot.CapabilityItem(
            id="P99.3",
            domain="Documentation / provenance",
            weight=2.0,
            state="COMPLETE",
            factor=progress_snapshot.effective_factor("COMPLETE", ev["evidence_level"]),
            earned=2.0 * progress_snapshot.effective_factor("COMPLETE", ev["evidence_level"]),
            bucket=progress_snapshot.effective_bucket("COMPLETE", ev["evidence_level"]),
            evidence_level=ev["evidence_level"],
            evidence_paths=ev["evidence_paths"],
            evidence_commands=ev["evidence_commands"],
            evidence_commit=ev["evidence_commit"],
        )
        self.assertEqual("pending", ev["evidence_level"])
        self.assertEqual(0.0, item.factor)
        self.assertEqual(0.0, item.earned)
        self.assertEqual("evidence_pending", item.bucket)


class EvidenceProvenanceTest(V2RepoFixtureMixin, unittest.TestCase):
    def setUp(self):
        self._patchers = [
            patch("tools.progress_snapshot.canonical_commit", side_effect=_fake_canonical),
            patch("tools.progress_snapshot.git_rev", side_effect=_fake_git_rev),
        ]
        for p in self._patchers:
            p.start()
            self.addCleanup(p.stop)

        # Start every test in an isolated, empty v2 repo.
        td = tempfile.TemporaryDirectory()
        self.addCleanup(td.cleanup)
        root = Path(td.name)
        (root / "tasks" / "active").mkdir(parents=True, exist_ok=True)
        (root / "tasks" / "backlog").mkdir(parents=True, exist_ok=True)
        (root / "tasks" / "blocked").mkdir(parents=True, exist_ok=True)
        (root / "tasks" / "completed").mkdir(parents=True, exist_ok=True)
        (root / "docs" / "progress").mkdir(parents=True, exist_ok=True)
        (root / "ROADMAP.md").write_text("# Roadmap\n", encoding="utf-8")

        self._base_old_root = progress_snapshot.REPO_ROOT
        self._base_old_json = progress_snapshot.OUT_JSON
        self._base_old_md = progress_snapshot.OUT_MD

        def restore():
            progress_snapshot.REPO_ROOT = self._base_old_root
            progress_snapshot.OUT_JSON = self._base_old_json
            progress_snapshot.OUT_MD = self._base_old_md

        self.addCleanup(restore)
        progress_snapshot.REPO_ROOT = root
        progress_snapshot.OUT_JSON = root / "A14_PROGRESS_CURRENT.json"
        progress_snapshot.OUT_MD = root / "A14_PROGRESS_CURRENT.md"

    def _make_repo_with_doc(self):
        return self._make_repo({
            Path("docs/A14_GESTURE_LIFECYCLE_OWNER_INVENTORY.md"): "# Doc\n\nEvidenceCommit: 1111111111111111111111111111111111111111\n",
            Path("tools/tests/test_gesture_lifecycle_inventory.py"): "# test\n",
        })

    def test_narrative_only_command_rejected(self):
        text = """\nState: `COMPLETE`\n\n证据：\n\n- docs/progress/A14_PROGRESS_CURRENT.json 与 .md 已重新生成\n- P12.1 文档已经验证\n"""
        ev = progress_snapshot.extract_evidence(text)
        self.assertEqual([], ev["evidence_commands"])
        self.assertEqual("pending", ev["evidence_level"])

    def test_path_in_sentence_is_not_command(self):
        text = """\nState: `COMPLETE`\n\n证据：\n\n- `docs/A14_GESTURE_LIFECYCLE_OWNER_INVENTORY.md` 已经被验证\n"""
        ev = progress_snapshot.extract_evidence(text)
        self.assertEqual([], ev["evidence_commands"])

    def test_nonexistent_path_rejected(self):
        root = self._make_repo_with_doc()
        text = """\nState: `VERIFIED_STATIC`\n\n文件：\n\n- `docs/this-does-not-exist.md`\n"""
        ev = progress_snapshot.extract_evidence(text)
        self.assertEqual([], ev["evidence_paths"])

    def test_escape_path_rejected(self):
        root = self._make_repo_with_doc()
        text = """\nState: `VERIFIED_STATIC`\n\n文件：\n\n- `../outside/repo.md`\n- `docs/../TASK_STATE.md`\n"""
        ev = progress_snapshot.extract_evidence(text)
        self.assertEqual([], ev["evidence_paths"])

    def test_real_repo_path_accepted(self):
        root = self._make_repo_with_doc()
        text = """\nState: `VERIFIED_STATIC`\n\n文件：\n\n- `docs/A14_GESTURE_LIFECYCLE_OWNER_INVENTORY.md`\n- `tools/tests/test_gesture_lifecycle_inventory.py`\n"""
        ev = progress_snapshot.extract_evidence(text)
        self.assertIn("docs/A14_GESTURE_LIFECYCLE_OWNER_INVENTORY.md", ev["evidence_paths"])
        self.assertIn("tools/tests/test_gesture_lifecycle_inventory.py", ev["evidence_paths"])

    def test_fenced_python_command_accepted(self):
        text = """\nState: `VERIFIED_STATIC`\n\n证据：\n\n```text\npython -m unittest tools.tests.test_gesture_lifecycle_inventory\npython tools/check-invariants.py\n```\n"""
        ev = progress_snapshot.extract_evidence(text)
        self.assertEqual(
            ["python -m unittest tools.tests.test_gesture_lifecycle_inventory", "python tools/check-invariants.py"],
            ev["evidence_commands"],
        )

    def test_non_allowlist_command_rejected(self):
        text = """\nState: `COMPLETE`\n\n证据：\n\n```text\necho "hello"\nrm -rf /\nnotallowed --flag\n```\n"""
        ev = progress_snapshot.extract_evidence(text)
        self.assertEqual([], ev["evidence_commands"])

    def test_complete_without_evidence_gets_zero_score(self):
        item = progress_snapshot.CapabilityItem(
            id="P99.1",
            domain="Baseline and control",
            weight=2.0,
            state="COMPLETE",
            factor=0.0,
            earned=0.0,
            bucket="evidence_pending",
            evidence_level="pending",
            evidence_paths=[],
            evidence_commands=[],
        )
        self.assertEqual("evidence_pending", progress_snapshot.effective_bucket(item.state, item.evidence_level))
        self.assertEqual(0.0, progress_snapshot.effective_factor(item.state, item.evidence_level))

    def test_verified_static_partial_no_score(self):
        item = progress_snapshot.CapabilityItem(
            id="P99.2",
            domain="Documentation / provenance",
            weight=1.25,
            state="VERIFIED_STATIC",
            factor=0.0,
            earned=0.0,
            bucket="evidence_pending",
            evidence_level="partial",
            evidence_paths=["docs/some.md"],
            evidence_commands=["python tools/some.py"],
            evidence_commit="pending",
        )
        self.assertEqual("evidence_pending", progress_snapshot.effective_bucket(item.state, item.evidence_level))
        self.assertEqual(0.0, progress_snapshot.effective_factor(item.state, item.evidence_level))

    def test_verified_static_with_ancestor_commit_verified(self):
        root = self._make_repo_with_doc()
        text = """\nState: `VERIFIED_STATIC`\n\nEvidenceCommit: cd152365a1b258a7b36e978d1050db71f427fa83\n\n文件：\n\n- `docs/A14_GESTURE_LIFECYCLE_OWNER_INVENTORY.md`\n\n证据：\n\n```text\npython -m unittest tools.tests.test_gesture_lifecycle_inventory\n```\n"""
        ev = progress_snapshot.extract_evidence(text)
        self.assertEqual("verified", ev["evidence_level"])
        self.assertEqual(40, len(ev["evidence_commit"]))
        self.assertEqual("0.70", f"{progress_snapshot.effective_factor('VERIFIED_STATIC', ev['evidence_level']):.2f}")
        self.assertEqual("verified", progress_snapshot.effective_bucket("VERIFIED_STATIC", ev["evidence_level"]))

    def test_nonexistent_commit_rejected(self):
        text = """\nState: `VERIFIED_STATIC`\n\nEvidenceCommit: 0000000000000000000000000000000000000000\n\n证据：\n\n```text\npython -m unittest tools.tests.test_x\n```\n"""
        ev = progress_snapshot.extract_evidence(text)
        self.assertEqual("partial", ev["evidence_level"])
        self.assertEqual("pending", ev["evidence_commit"])

    def test_ambiguous_or_invalid_short_sha_rejected(self):
        text = """\nState: `VERIFIED_STATIC`\n\nEvidenceCommit: deadbeef\n\n证据：\n\n```text\npython -m unittest tools.tests.test_x\n```\n"""
        ev = progress_snapshot.extract_evidence(text)
        self.assertEqual("partial", ev["evidence_level"])
        self.assertEqual("pending", ev["evidence_commit"])

    def test_non_ancestor_commit_rejected(self):
        head = progress_snapshot.git_rev("HEAD")
        text = f"""\nState: `VERIFIED_STATIC`\n\nEvidenceCommit: {head}\n\n证据：\n\n```text\npython -m unittest tools.tests.test_x\n```\n"""
        ev = progress_snapshot.extract_evidence(text)
        # HEAD is rejected by canonical_commit.
        self.assertIn(ev["evidence_level"], ("pending", "partial"))

        parent = progress_snapshot.git_rev("HEAD~1")
        text2 = f"""\nState: `VERIFIED_STATIC`\n\nEvidenceCommit: {parent}\n\n证据：\n\n```text\npython -m unittest tools.tests.test_x\n```\n"""
        ev2 = progress_snapshot.extract_evidence(text2)
        self.assertEqual("verified", ev2["evidence_level"])
        self.assertEqual(40, len(ev2["evidence_commit"]))

    def test_commit_saved_as_full_40_sha(self):
        short = "cd15236"
        text = f"""\nState: `VERIFIED_STATIC`\n\nEvidenceCommit: {short}\n\n证据：\n\n```text\npython -m unittest tools.tests.test_x\n```\n"""
        ev = progress_snapshot.extract_evidence(text)
        self.assertEqual("verified", ev["evidence_level"])
        self.assertRegex(ev["evidence_commit"], r"^[0-9a-f]{40}$")

    def test_prose_random_sha_not_extracted(self):
        text = """\nState: `COMPLETE`\n\n说明：\n\n- 某个历史提交 55fc2a21d0e96f9ef643f53fcc9b74374bd959db 看起来有关\n- 另一个提交 cd152365a1b258a7b36e978d1050db71f427fa83 被提及\n"""
        ev = progress_snapshot.extract_evidence(text)
        self.assertEqual("pending", ev["evidence_commit"])

    def test_referenced_doc_evidence_commit_used_if_path_valid(self):
        root = self._make_repo_with_doc()
        text = """\nState: `VERIFIED_STATIC`\n\n文件：\n\n- `docs/A14_GESTURE_LIFECYCLE_OWNER_INVENTORY.md`\n"""
        ev = progress_snapshot.extract_evidence(text)
        self.assertEqual("verified", ev["evidence_level"])
        self.assertEqual(40, len(ev["evidence_commit"]))

    def test_referenced_doc_evidence_commit_ignored_if_path_invalid(self):
        text = """\nState: `VERIFIED_STATIC`\n\n文件：\n\n- `docs/missing-file.md`\n"""
        ev = progress_snapshot.extract_evidence(text)
        self.assertEqual("pending", ev["evidence_level"])

    def test_mutation_all_bullets_are_commands_fails(self):
        text = """\nState: `COMPLETE`\n\n证据：\n\n- docs/progress/A14_PROGRESS_CURRENT.json 与 .md 已重新生成\n- P12.1 文档已经验证\n"""
        ev = progress_snapshot.extract_evidence(text)
        self.assertEqual([], ev["evidence_commands"])

    def test_mutation_evidence_level_from_state_fails(self):
        for state in ["VERIFIED_STATIC", "VERIFIED_BUILD", "VERIFIED_CI", "COMPLETE"]:
            text = f"""\nState: `{state}`\n\n证据：\n\n```text\necho no evidence\n```\n"""
            ev = progress_snapshot.extract_evidence(text)
            self.assertIn(ev["evidence_level"], ("pending", "partial"))

    def test_mutation_all_commits_pending_fails(self):
        text = """\nState: `VERIFIED_STATIC`\n\n证据：\n\n```text\npython -m unittest tools.tests.test_x\n```\n"""
        ev = progress_snapshot.extract_evidence(text)
        self.assertEqual("partial", ev["evidence_level"])
        self.assertEqual("pending", ev["evidence_commit"])

    def test_mutation_pending_commit_full_factor_fails(self):
        item = progress_snapshot.CapabilityItem(
            id="P99.4",
            domain="Documentation / provenance",
            weight=1.25,
            state="VERIFIED_STATIC",
            factor=progress_snapshot.effective_factor("VERIFIED_STATIC", "partial"),
            earned=1.25 * progress_snapshot.effective_factor("VERIFIED_STATIC", "partial"),
            bucket=progress_snapshot.effective_bucket("VERIFIED_STATIC", "partial"),
            evidence_level="partial",
            evidence_paths=["docs/x.md"],
            evidence_commands=["python tools/x.py"],
            evidence_commit="pending",
        )
        self.assertEqual(0.0, item.factor)
        self.assertEqual(0.0, item.earned)
        self.assertEqual("evidence_pending", item.bucket)


if __name__ == "__main__":
    unittest.main()
