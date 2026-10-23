"""Release preparation preserves history and refuses incomplete bilingual notes."""
from datetime import date
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from tools import prepare_release, check_release_metadata


class PrepareReleaseTest(unittest.TestCase):
    def fixture(self, root: Path) -> None:
        (root / "app").mkdir()
        (root / "app/build.gradle.kts").write_text('val lastVersion = 246\nval lastVersionName = "r14.25.1"\n', encoding="utf-8")
        for filename in ("README.md", "README_EN.md"):
            (root / filename).write_text("Current: `r14.25.1` (versionCode 246).\n", encoding="utf-8")
        for filename, heading in (("CHANGELOG.md", "Unreleased"), ("CHANGELOG_CN.md", "未发布")):
            (root / filename).write_text(f"# Changelog\n\n## {heading}\n\n- Reviewed change.\n\n## r14.25.1 — 2026-10-22\n\nversionCode 246.\n\n- Historical change.\n", encoding="utf-8")

    def test_one_plan_aligns_five_files_and_preserves_the_historical_section(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.fixture(root)
            changes = prepare_release.plan(root, "r14.26.1", 247, date(2026, 10, 23))
            self.assertEqual(5, len(changes))
            for path, text in changes.items():
                self.assertIn("r14.26.1", text)
                self.assertIn("247", text)
                if path.name.startswith("CHANGELOG"):
                    self.assertIn("## r14.26.1 — 2026-10-23", text)
                    history = path.read_text(encoding="utf-8").split("## r14.25.1", 1)[1]
                    self.assertEqual(history, text.split("## r14.25.1", 1)[1])
                path.write_text(text, encoding="utf-8")
            with patch.object(check_release_metadata, "REPO_ROOT", root), patch.object(check_release_metadata, "GRADLE", root / "app/build.gradle.kts"):
                self.assertEqual([], check_release_metadata.check())

    def test_missing_one_translation_never_writes_a_partial_release(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.fixture(root)
            cn = root / "CHANGELOG_CN.md"
            cn.write_text(cn.read_text(encoding="utf-8").replace("- Reviewed change.", ""), encoding="utf-8")
            before = {path: path.read_text(encoding="utf-8") for path in root.rglob("*") if path.is_file()}
            with self.assertRaisesRegex(ValueError, "reviewed change entries"):
                prepare_release.plan(root, "r14.26.1", 247, date(2026, 10, 23))
            self.assertEqual(before, {path: path.read_text(encoding="utf-8") for path in before})

    def test_duplicate_or_decreasing_versions_are_not_prepared(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.fixture(root)
            for version, code in (("r14.25.1", 247), ("r14.24.9", 247), ("r14.26.1", 246), ("r15.1.1", 247)):
                with self.subTest(version=version, code=code), self.assertRaises(ValueError):
                    prepare_release.plan(root, version, code, date(2026, 10, 23))
            path = root / "CHANGELOG.md"
            path.write_text(path.read_text(encoding="utf-8") + "\n## r14.26.1\n\n- Already prepared.\n", encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "already contains"):
                prepare_release.plan(root, "r14.26.1", 247, date(2026, 10, 23))

    def test_dry_run_keeps_every_file_unchanged(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.fixture(root)
            before = {path: path.read_text(encoding="utf-8") for path in root.rglob("*") if path.is_file()}
            with patch.object(prepare_release, "ROOT", root), patch("sys.argv", ["prepare_release.py", "--version", "r14.26.1", "--code", "247", "--dry-run"]):
                self.assertEqual(0, prepare_release.main())
            self.assertEqual(before, {path: path.read_text(encoding="utf-8") for path in before})

    def test_preparation_writes_repository_lf_on_windows_too(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp)
            self.fixture(root)
            with patch.object(prepare_release,"ROOT",root), patch("sys.argv",["prepare_release.py","--version","r14.26.1","--code","247"]):
                self.assertEqual(0,prepare_release.main())
            for filename in ("app/build.gradle.kts","README.md","README_EN.md","CHANGELOG.md","CHANGELOG_CN.md"):
                content=(root/filename).read_bytes()
                self.assertNotIn(b"\r\n",content,filename)
                self.assertIn(b"r14.26.1",content,filename)

    def test_explicit_consolidation_removes_only_current_section_and_uses_retained_baseline(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.fixture(root)
            for filename, user, engineering in (("CHANGELOG.md", "User changes", "Engineering notes"),
                                                 ("CHANGELOG_CN.md", "用户变化", "工程说明")):
                path = root / filename
                text = path.read_text(encoding="utf-8").replace("- Reviewed change.",
                    f"### {user}\n\n- Reviewed cumulative change.\n\n### {engineering}\n\n- Retained technical facts.")
                path.write_text(text + "\n## r14.24.1\n\nversionCode 245.\n\n- Older retained release.\n", encoding="utf-8")
            changes = prepare_release.plan(root, "r14.25.3", 248, date(2026, 10, 23),
                                           supersede="r14.25.1", base_version="r14.24.1")
            for path, text in changes.items():
                if path.name.startswith("CHANGELOG"):
                    self.assertNotIn("## r14.25.1", text)
                    self.assertIn("r14.24.1", text.split("###", 1)[0])
                    self.assertIn("Reviewed cumulative change.", text)
                    self.assertIn("Retained technical facts.", text)
                    self.assertEqual(path.read_text(encoding="utf-8").split("## r14.24.1", 1)[1],
                                     text.split("## r14.24.1", 1)[1])

    def test_consolidation_refuses_partial_flags_wrong_release_or_missing_baseline(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.fixture(root)
            before = {path: path.read_bytes() for path in root.rglob("*") if path.is_file()}
            for options in ({"supersede": "r14.25.1"}, {"base_version": "r14.24.1"},
                            {"supersede": "r14.24.1", "base_version": "r14.23.5"},
                            {"supersede": "r14.25.1", "base_version": "r14.24.1"}):
                with self.subTest(options=options), self.assertRaises(ValueError):
                    prepare_release.plan(root, "r14.25.3", 248, date(2026, 10, 23), **options)
            self.assertEqual(before, {path: path.read_bytes() for path in before})


if __name__ == "__main__":
    unittest.main()
