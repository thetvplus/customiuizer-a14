"""Tests for tools/audit_hook_ownership.py."""

import os
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

import importlib.util

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
SCRIPT = REPO_ROOT / "tools" / "audit_hook_ownership.py"
EXPECTED_INVENTORY = (
    REPO_ROOT / "docs" / "audit" / "A14_HOOK_OWNERSHIP_INVENTORY.md"
)


def _load_module():
    spec = importlib.util.spec_from_file_location("audit_hook_ownership", SCRIPT)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class AuditHookOwnershipTest(unittest.TestCase):
    def test_checkDoesNotRequireCommittedMarkdown(self):
        """Default --check works and does not require the deleted inventory."""
        self.assertFalse(
            EXPECTED_INVENTORY.exists(),
            "test setup expects no committed A14 hook inventory",
        )
        result = subprocess.run(
            [sys.executable, str(SCRIPT), "--check"],
            cwd=REPO_ROOT,
            capture_output=True,
            text=True,
            env=os.environ.copy(),
        )
        self.assertEqual(
            0,
            result.returncode,
            f"stdout:\n{result.stdout}\nstderr:\n{result.stderr}",
        )
        self.assertIn("Hook ownership scan passes", result.stdout)
        self.assertNotIn("Committed inventory", result.stdout)
        self.assertFalse(EXPECTED_INVENTORY.exists())

    def test_checkDoesNotWriteRepositoryFiles(self):
        """Default check is in-memory and does not create any repository files."""
        with tempfile.TemporaryDirectory() as td:
            temp_root = Path(td)
            tools_dir = temp_root / "tools"
            tools_dir.mkdir()
            shutil.copy(SCRIPT, tools_dir / "audit_hook_ownership.py")

            source = (
                temp_root
                / "app"
                / "src"
                / "main"
                / "java"
                / "tv"
                / "withaibuild"
                / "customiuizer"
                / "mods"
            )
            source.mkdir(parents=True)
            (source / "System.kt").write_text(
                'fun example() { ModuleHelper.findAndHookMethod("a", null, "b", null) }',
                encoding="utf-8",
            )

            result = subprocess.run(
                [sys.executable, str(tools_dir / "audit_hook_ownership.py")],
                cwd=temp_root,
                capture_output=True,
                text=True,
                env=os.environ.copy(),
            )
            self.assertEqual(
                0,
                result.returncode,
                f"stdout:\n{result.stdout}\nstderr:\n{result.stderr}",
            )
            self.assertIn("Hook ownership scan passes", result.stdout)
            self.assertFalse(
                (temp_root / "docs").exists(),
                "check should not create docs/ in the repository",
            )

    def test_unknownCategoryFails(self):
        """A source file whose ownership cannot be determined must fail the scan."""
        with tempfile.TemporaryDirectory() as td:
            temp_root = Path(td)
            tools_dir = temp_root / "tools"
            tools_dir.mkdir()
            shutil.copy(SCRIPT, tools_dir / "audit_hook_ownership.py")

            source = temp_root / "app" / "src" / "main" / "java"
            source.mkdir(parents=True)
            (source / "UnknownSource.kt").write_text(
                'fun example() { findAndHookMethod("a", null, "b", null) }',
                encoding="utf-8",
            )

            result = subprocess.run(
                [sys.executable, str(tools_dir / "audit_hook_ownership.py")],
                cwd=temp_root,
                capture_output=True,
                text=True,
                env=os.environ.copy(),
            )
            self.assertNotEqual(0, result.returncode)
            self.assertNotIn("Hook ownership scan passes", result.stdout)
            self.assertIn("UNKNOWN", result.stdout)

    def test_explicitTemporaryWriteProducesDeterministicOutput(self):
        """--output to a temporary path writes a deterministic diagnostic Markdown."""
        with tempfile.TemporaryDirectory() as td:
            temp_root = Path(td)
            tools_dir = temp_root / "tools"
            tools_dir.mkdir()
            shutil.copy(SCRIPT, tools_dir / "audit_hook_ownership.py")

            source = (
                temp_root
                / "app"
                / "src"
                / "main"
                / "java"
                / "tv"
                / "withaibuild"
                / "customiuizer"
                / "mods"
            )
            source.mkdir(parents=True)
            utils = source / "utils"
            utils.mkdir(parents=True)

            (source / "System.kt").write_text(
                'fun systemHook() { ModuleHelper.findAndHookMethod("sys", null, "m", null) }',
                encoding="utf-8",
            )
            (utils / "XposedHelpers.java").write_text(
                'public void bridge() { XposedHelpers.findAndHookMethod("x", null, "m", null); }',
                encoding="utf-8",
            )
            (utils / "ModuleHelper.kt").write_text(
                'fun helper() { ModuleHelper.hookAllMethods("h", null, "m", null) }',
                encoding="utf-8",
            )
            (utils / "ResourceHooks.kt").write_text(
                'fun res() { ResourceHooks.findAndHookMethod("r", null, "m", null) }',
                encoding="utf-8",
            )

            out1 = temp_root / "out1.md"
            out2 = temp_root / "out2.md"
            for out in (out1, out2):
                result = subprocess.run(
                    [
                        sys.executable,
                        str(tools_dir / "audit_hook_ownership.py"),
                        "--output",
                        str(out),
                    ],
                    cwd=temp_root,
                    capture_output=True,
                    text=True,
                    env=os.environ.copy(),
                )
                self.assertEqual(
                    0,
                    result.returncode,
                    f"stdout:\n{result.stdout}\nstderr:\n{result.stderr}",
                )
                self.assertIn("Hook ownership scan passes", result.stdout)
                self.assertTrue(out.exists())

            text1 = out1.read_text(encoding="utf-8")
            text2 = out2.read_text(encoding="utf-8")
            self.assertEqual(
                text1,
                text2,
                "two runs with the same source should produce identical output",
            )
            self.assertIn("Total hook call sites scanned:", text1)
            self.assertNotIn("| UNKNOWN", text1)

    def test_classify_known_categories(self):
        mod = _load_module()
        source = mod.SOURCE_ROOT

        def rel(rel_path: str) -> Path:
            return source / rel_path

        self.assertEqual(
            "API_BRIDGE",
            mod.classify(
                rel("tv/withaibuild/customiuizer/mods/utils/XposedHelpers.java"), 1, ""
            ),
        )
        self.assertEqual(
            "API_BRIDGE",
            mod.classify(
                rel("tv/withaibuild/customiuizer/mods/utils/HookerClassHelper.kt"),
                1,
                "",
            ),
        )
        self.assertEqual(
            "INSTALLER_INFRASTRUCTURE",
            mod.classify(
                rel("tv/withaibuild/customiuizer/mods/utils/ModuleHelper.kt"), 1, ""
            ),
        )
        self.assertEqual(
            "RESOURCE_INFRASTRUCTURE",
            mod.classify(
                rel("tv/withaibuild/customiuizer/mods/utils/ResourceHooks.kt"), 1, ""
            ),
        )
        self.assertEqual(
            "REGISTRY_FEATURE",
            mod.classify(rel("tv/withaibuild/customiuizer/mods/System.kt"), 1, ""),
        )
        self.assertEqual(
            "REGISTRY_FEATURE",
            mod.classify(rel("tv/withaibuild/customiuizer/mods/Controls.kt"), 1, ""),
        )
        self.assertEqual(
            "INSTALLER_INFRASTRUCTURE",
            mod.classify(
                rel("tv/withaibuild/customiuizer/installers/SystemUiInstaller.java"),
                1,
                "",
            ),
        )
        self.assertEqual(
            "INSTALLER_INFRASTRUCTURE",
            mod.classify(rel("tv/withaibuild/customiuizer/MainModule.java"), 1, ""),
        )


if __name__ == "__main__":
    unittest.main()
