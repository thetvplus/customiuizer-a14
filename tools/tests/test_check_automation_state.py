import tempfile
import textwrap
import unittest
from pathlib import Path

from tools import check_automation_state as checker


class SmartStateTests(unittest.TestCase):
    def test_parse_detects_duplicate_keys(self):
        text = textwrap.dedent(
            """
            ```text
            Mode: A
            LastFailureClass: one
            LastFailureClass: two
            ```
            """
        )
        block = checker.parse_smart_state(text)
        _, errors = checker.smart_state_dict(block)
        self.assertIn("SMART_OPERATION_STATE duplicate key: LastFailureClass", errors)

    def test_unknown_key_reported(self):
        text = textwrap.dedent(
            """
            ```text
            Mode: A
            UnknownKey: value
            ```
            """
        )
        block = checker.parse_smart_state(text)
        _, errors = checker.smart_state_dict(block)
        self.assertIn("SMART_OPERATION_STATE unknown key: UnknownKey", errors)

    def test_missing_text_block(self):
        text = "No fenced block here."
        with self.assertRaises(ValueError):
            checker.parse_smart_state(text)


class TaskStateTests(unittest.TestCase):
    def test_parent_complete_with_incomplete_child(self):
        text = """
# P5 — Gesture/Control Center

State: `COMPLETE`

## P5.1 生产状态机

State: `TODO`

## P5.2 事件模型

State: `COMPLETE`
"""
        sections = checker.parse_task_sections(text)
        errors = checker.build_parent_child(sections)
        self.assertTrue(any("P5 is COMPLETE but child P5.1 is TODO" in e for e in errors))

    def test_todo_parent_with_complete_child(self):
        text = """
# P5 — Gesture/Control Center

State: `TODO`

## P5.1 生产状态机

State: `COMPLETE`
"""
        sections = checker.parse_task_sections(text)
        errors = checker.build_parent_child(sections)
        self.assertTrue(any("P5 is TODO but has COMPLETE children" in e for e in errors))

    def test_stale_complete_evidence(self):
        text = """
## 4. 发现的问题队列

| ID | Priority | Area | State | Evidence | Acceptance |
|---|---|---|---|---|---|---|
| GESTURE-001 | P1 | Gesture | COMPLETE | 尚未由本地 Agent 盘点 | P5 完成 |
"""
        errors = checker.check_issue_queue(text)
        self.assertIn(
            "TASK_STATE issue GESTURE-001 is COMPLETE but evidence is stale: 尚未由本地 Agent 盘点",
            errors,
        )

    def test_todo_with_complete_acceptance(self):
        text = """
# P5 — Gesture/Control Center

State: `COMPLETE`

## 4. 发现的问题队列

| ID | Priority | Area | State | Evidence | Acceptance |
|---|---|---|---|---|---|---|
| GESTURE-001 | P1 | Gesture | TODO | 多状态机需盘点 | P5 完成 |
"""
        errors = checker.check_issue_queue(text)
        self.assertIn(
            "TASK_STATE issue GESTURE-001 is TODO but acceptance implies complete: P5 完成",
            errors,
        )

    def test_todo_acceptance_references_incomplete_parent(self):
        text = """
# P5 — Gesture/Control Center

State: `TODO`

## 4. 发现的问题队列

| ID | Priority | Area | State | Evidence | Acceptance |
|---|---|---|---|---|---|---|
| GESTURE-001 | P1 | Gesture | TODO | 多状态机需盘点 | P5 完成 |
"""
        errors = checker.check_issue_queue(text)
        self.assertEqual(errors, [])

    def test_empty_checkpoint(self):
        text = """
## 5. Checkpoint

尚无。

---

## 6. 最终报告
"""
        errors = checker.check_checkpoint_section(text)
        self.assertTrue(any("Checkpoint section is empty" in e for e in errors))


class StopConflictTests(unittest.TestCase):
    def test_detects_stop_and_wait(self):
        texts = {
            "GOAL.md": "达到 PROJECT_COMPLETE 后，... 停止并等待仓库所有者。",
            "AGENTS.md": "",
            "SMART_CONTINUOUS_OPERATION.md": "",
        }
        errors = checker.check_stop_conflicts(texts)
        self.assertIn("GOAL.md still contains '停止...等待仓库所有者' post-completion action", errors)


class FixtureRegressionTests(unittest.TestCase):
    def test_current_audit_finding_reproduced_then_fixed(self):
        """Simulate the state captured in CURRENT_AUDIT_FINDINGS.md."""
        text = textwrap.dedent(
            """
            # Smart operation state

            ```text
            Mode: SMART_CONTINUOUS_OPERATION
            CheckpointCount: 3
            CheckpointsSinceStandardSweep: 0
            CheckpointsSinceDeepSweep: 3
            LastLightSweepCommit: pending
            LastStandardSweepCommit: pending
            LastDeepSweepCommit: pending
            LastFullVerificationCommit: pending
            LastStandardSweepCommit: pending
            LastCIState: pending
            LastCleanupCommit: pending
            LastToolCreated: none
            LastFailureClass: none
            ResumeTask: derive from TASK_STATE.md
            ```
            """
        )
        block = checker.parse_smart_state(text)
        _, errors = checker.smart_state_dict(block)
        self.assertIn("SMART_OPERATION_STATE duplicate key: LastStandardSweepCommit", errors)


class ControlPlaneMigrationTests(unittest.TestCase):
    def _write_minimal_repo(self, root: Path, auto_start: bool = False, triggers: bool = True) -> None:
        (root / ".agents" / "skills" / "a14-safe-implementation").mkdir(parents=True, exist_ok=True)
        (root / ".agents" / "skills" / "a14-independent-review").mkdir(parents=True, exist_ok=True)
        (root / "docs" / "process").mkdir(parents=True, exist_ok=True)

        impl_trigger = 'triggers: ["user"]' if triggers else ''
        review_trigger = 'triggers: ["user"]' if triggers else ''
        (root / ".agents" / "skills" / "a14-safe-implementation" / "SKILL.md").write_text(
            f"---\nname: a14-safe-implementation\nargument-hint: <task-slice-path>\n{impl_trigger}\n---\n",
            encoding="utf-8",
        )
        (root / ".agents" / "skills" / "a14-independent-review" / "SKILL.md").write_text(
            f"---\nname: a14-independent-review\nargument-hint: <base> <head> <task-slice-path>\n{review_trigger}\n---\n",
            encoding="utf-8",
        )
        (root / "docs" / "process" / "A14_RISK_GATE_MATRIX.md").write_text("# A14 Risk", encoding="utf-8")

        (root / "AGENTS.md").write_text(
            "a14-safe-implementation\na14-independent-review\n当前会话不得自行选择第二个目标\nR2",
            encoding="utf-8",
        )
        auto = "true" if auto_start else "false"
        (root / "SMART_CONTINUOUS_OPERATION.md").write_text(
            f"SessionMode: ATOMIC_TASK_SLICE\nAutoStartNextSlice: {auto}\n",
            encoding="utf-8",
        )
        (root / "DEVIN_START_PROMPT.md").write_text(
            "@skills:a14-safe-implementation\n@skills:a14-independent-review\n",
            encoding="utf-8",
        )
        (root / "GOAL.md").write_text(
            "在后续会话开始时选择任务",
            encoding="utf-8",
        )

    def test_control_plane_migration_passes(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self._write_minimal_repo(root)
            errors = checker.check_control_plane_migration(root)
            self.assertEqual(errors, [], f"unexpected errors: {errors}")

    def test_missing_triggers_fails(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self._write_minimal_repo(root, triggers=False)
            errors = checker.check_control_plane_migration(root)
            self.assertTrue(any("triggers" in e for e in errors))

    def test_auto_start_next_slice_true_fails(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self._write_minimal_repo(root, auto_start=True)
            errors = checker.check_control_plane_migration(root)
            self.assertTrue(any("AutoStartNextSlice: false" in e for e in errors))


if __name__ == "__main__":
    unittest.main()
