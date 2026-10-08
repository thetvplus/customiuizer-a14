import json
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from tools import brutal_test_runner, ci_contract_scan, ci_scope


class CIScopeTest(unittest.TestCase):
    def test_ordinary_code_and_docs_need_only_fast(self):
        for paths in (["app/src/main/java/example/Feature.kt"], ["README.md"], []):
            self.assertEqual({"full": False, "tools": False},
                             ci_scope.select_scope("pull_request", "refs/pull/1/merge", {}, paths))

    def test_build_and_dependency_changes_need_full(self):
        for path in ("app/build.gradle.kts", "app/proguard-rules.pro", "gradle/libs.versions.toml",
                     "build.gradle.kts", "settings.gradle.kts", "gradle.properties", "app/lib/framework.jar"):
            with self.subTest(path=path):
                self.assertEqual({"full": True, "tools": False},
                                 ci_scope.select_scope("push", "refs/heads/main", {}, [path]))

    def test_tool_changes_and_periodic_runs_include_tool_gates(self):
        for path in ("tools/verify.py", ".github/workflows/a14-ci.yml", ".github/dependabot.yml"):
            self.assertEqual({"full": True, "tools": True},
                             ci_scope.select_scope("pull_request", "refs/pull/1/merge", {}, [path]))
        for event, ref in (("schedule", "refs/heads/main"), ("workflow_dispatch", "refs/heads/main"),
                           ("push", "refs/tags/r14.22.5")):
            self.assertEqual({"full": True, "tools": True}, ci_scope.select_scope(event, ref, {}, []))

    def test_catalog_and_matrix_changes_exercise_tool_gates(self):
        for path in ("app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt",
                     "app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/FeatureIds.kt",
                     "docs/rom-intelligence/A14_PROCESS_MATRIX.csv"):
            with self.subTest(path=path):
                self.assertTrue(ci_scope.select_scope("pull_request", "refs/pull/1/merge", {}, [path])["tools"])

    def test_explicit_full_request_does_not_repeat_tool_tests(self):
        event = {"head_commit": {"message": "fix: harden feature [full-ci]"}}
        self.assertEqual({"full": True, "tools": False},
                         ci_scope.select_scope("push", "refs/heads/main", event, []))

    def test_git_diff_covers_all_commits_and_filenames_with_spaces(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)

            def git(*args):
                return subprocess.check_output(["git", "-c", "user.name=CI Test", "-c",
                                                "user.email=ci-test@example.invalid", *args], cwd=root).decode().strip()

            def commit(path):
                file = root / path
                file.parent.mkdir(parents=True, exist_ok=True)
                file.write_text("fixture", encoding="utf-8")
                git("add", "--", path)
                git("commit", "-m", "fixture")
                return git("rev-parse", "HEAD")

            git("init", "-q")
            base = commit("README.md")
            commit("gradle/libs.versions.toml")
            head = commit("docs/a file.md")
            paths = ci_scope.changed_paths("push", {"before": base, "after": head}, root)
            self.assertEqual(["docs/a file.md", "gradle/libs.versions.toml"], paths)
            self.assertTrue(ci_scope.select_scope("push", "refs/heads/main", {}, paths)["full"])
            pr = {"pull_request": {"base": {"sha": base}, "head": {"sha": head}}}
            self.assertEqual(paths, ci_scope.changed_paths("pull_request", pr, root))
            initial = ci_scope.changed_paths("push", {"before": "0" * 40, "after": head}, root)
            self.assertIn("README.md", initial)

    def test_git_failure_is_not_treated_as_no_changes(self):
        with patch("tools.ci_scope.subprocess.check_output", side_effect=subprocess.CalledProcessError(1, "git")):
            with self.assertRaises(subprocess.CalledProcessError):
                ci_scope.changed_paths("push", {"before": "a" * 40, "after": "b" * 40})

    def test_full_reuses_fast_code_checks_at_the_same_revision(self):
        path = Path(__file__).resolve().parents[2] / ".github/workflows/a14-ci.yml"
        workflow = ci_contract_scan.load_workflow(path.read_text(encoding="utf-8"))
        fast, full = workflow["jobs"]["fast"], workflow["jobs"]["full"]
        self.assertEqual("fast", full["needs"])
        self.assertEqual("needs.fast.outputs.full == 'true'", full["if"])
        fast_commands = "\n".join(step.get("run", "") for step in fast["steps"])
        full_commands = "\n".join(step.get("run", "") for step in full["steps"])
        self.assertEqual(1, fast_commands.count("tools/verify.py full"))
        self.assertNotIn("tools/verify.py full", full_commands)
        self.assertNotIn("brutal_test_runner.py", full_commands)
        self.assertNotIn(":app:assembleDebug", fast_commands)
        checks = next(step for step in fast["steps"] if " hermeticity" in step.get("run", ""))
        self.assertNotIn("if", checks, "source contract tests must run for ordinary application changes")
        additional = next(step for step in fast["steps"] if " mutate " in step.get("run", ""))
        self.assertIn(" determinism", additional["run"])

    def test_required_mutations_keep_the_minimum_gate(self):
        root = Path(__file__).resolve().parents[2]
        cfg = json.loads((root / "tools/brutal_test_config.json").read_text(encoding="utf-8"))
        with patch("tools.brutal_test_runner.repo_root", return_value=root), \
                patch("tools.brutal_test_runner.mutation_test", return_value=0) as mutation:
            self.assertEqual(0, brutal_test_runner.main([
                "--config", "tools/brutal_test_config.json", "mutate", "--required-only"]))
            self.assertEqual(set(cfg["required_independent_mutations"]), mutation.call_args.args[3])
            self.assertFalse(mutation.call_args.kwargs["ignore_minimum"])
