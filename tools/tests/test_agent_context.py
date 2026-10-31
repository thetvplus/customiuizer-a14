"""Actual source consumers and adversarial lexical/provenance regressions."""
import contextlib
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest

from tools import query_aux_data as cli
from tools import agent_context as context
from tools import rom_catalog as rom


class SourceContextTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temporary = tempfile.TemporaryDirectory()
        cls.index = Path(cls.temporary.name) / "index"
        cls.db = rom.connect(cls.index, create=True)
        # Empty ROM catalog deliberately proves code navigation needs no ROM scan/DexSQL.
        with cls.db:
            cls.db.execute("INSERT INTO meta VALUES('root',?)", (str(Path(cls.temporary.name) / "roms"),))
        cls.db.close()

    @classmethod
    def tearDownClass(cls):
        cls.temporary.cleanup()

    def query(self, *arguments):
        with contextlib.redirect_stdout(io.StringIO()) as output:
            self.assertEqual(cli.main(["context", "--index-dir", str(self.index), *arguments]), 0)
        return json.loads(output.getvalue())

    def test_setting_to_actual_block_body_owners_and_source_lines(self):
        result = self.query("--key", "pref_key_system_strong_toast_mode", "--full")
        self.assertEqual(result["matched"], 1)
        row = result["records"][0]
        self.assertEqual(row["feature"], "strong_toast_presentation")
        self.assertEqual({e["owner"] for e in row["entrypoints"]}, {"DynamicIslandActivities", "SystemUIStrongToastHooks"})
        for entry in row["entrypoints"]:
            line = (cli.ROOT / entry["path"]).read_text(encoding="utf-8").splitlines()[entry["line"] - 1]
            self.assertIn("fun install(", line)
        self.assertEqual(row["process"], "com.android.systemui")
        self.assertTrue(row["classLoader"]["sources"])
        self.assertTrue(row["source"][0]["failure"])

    def test_dock_exact_key_is_not_shared_feature_file_false_positive(self):
        result = self.query("--key", "launcher_dock_background", "--full")
        self.assertEqual(result["matched"], 1)
        row = result["records"][0]
        self.assertEqual(row["owners"][0]["symbol"], "LauncherDockBackgroundFeature")
        self.assertEqual([e["owner"] for e in row["entrypoints"]], ["LauncherDockBackground"])
        self.assertTrue(any(e["lifecycle"] for e in row["source"]))
        self.assertTrue(row["verification"]["candidates"])
        self.assertTrue(all(e["evidence"] == "CANDIDATE_LEXICAL" for e in row["dependencies"]))

    def test_secondary_key_uses_current_owner_source_and_storage_alias(self):
        short = self.query("--key", "system_charginginfo_fontsize", "--fields", "feature,source")
        storage = self.query("--key", "pref_key_system_charginginfo_fontsize", "--fields", "feature,source")
        self.assertGreater(short["matched"], 0)
        self.assertEqual(short["records"], storage["records"])
        self.assertTrue(any("SystemLockScreenHooks.kt" in source["path"]
                            for row in short["records"] for source in row["source"]))

    def test_reverse_hook_class_method_and_chinese_ui_choice(self):
        by_class = self.query("--contains", "LauncherMusicReturnHooks")
        self.assertEqual(by_class["matched"], 1)
        self.assertEqual(by_class["records"][0]["feature"], "launcher_music_island_return")
        by_method = self.query("--contains", "onDetachedFromWindow")
        self.assertIn("launcher_dock_background", [r["feature"] for r in by_method["records"]])
        chinese = self.query("--contains", "动态岛")
        self.assertIn("strong_toast_presentation", [r["feature"] for r in chinese["records"]])

    def test_warm_source_facts_reuse_and_explicit_projection_paging(self):
        self.query("--key", "launcher_dock_background")
        warm = self.query("--key", "launcher_dock_background", "--fields", "feature,entrypoints")
        self.assertEqual(warm["index"]["parsed"], 0)
        self.assertGreater(warm["index"]["reused"], 100)
        self.assertEqual(set(warm["records"][0]), {"feature", "entrypoints"})
        first = self.query("--contains", "动态岛", "--limit", "1", "--fields", "feature")
        second = self.query("--contains", "动态岛", "--limit", "1", "--offset", "1", "--fields", "feature")
        self.assertEqual(first["nextOffset"], 1)
        self.assertNotEqual(first["records"], second["records"])
        ten = self.query("--contains", "Launcher", "--limit", "10", "--fields", "feature")
        self.assertEqual(len(ten["records"]), 10)

    def test_source_summary_does_not_load_rom_or_start_dexsql(self):
        from unittest.mock import patch
        with patch.object(rom, "source_files", side_effect=AssertionError("ROM scan")), patch.object(rom, "Engine", side_effect=AssertionError("DexSQL")):
            result = self.query()
        self.assertEqual(result["records"], [])
        self.assertGreater(result["matched"], 200)

    def test_invalid_fields_and_context_options_fail_closed(self):
        for arguments in (["context", "--index-dir", str(self.index), "--key", "launcher_dock_background", "--fields", "typo"],
                          ["matrix", "--device", "dada"], ["context", "--index-dir", str(self.index), "--confidence", "CODE_VERIFIED"]):
            with self.subTest(arguments=arguments), contextlib.redirect_stderr(io.StringIO()), self.assertRaises(SystemExit) as error:
                cli.main(arguments)
            self.assertEqual(error.exception.code, 2)


class SourceFactBoundaryTest(unittest.TestCase):
    def test_incremental_source_edit_deletion_and_forced_refresh(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary) / "repo"
            root.mkdir()
            rom.run(["git", "init", "--quiet", root])
            file = root / "app/src/main/java/Example.kt"
            file.parent.mkdir(parents=True)
            file.write_text("object Example { fun first() {} }", encoding="utf-8")
            with contextlib.closing(rom.connect(Path(temporary) / "index", create=True)) as db:
                first, one = context.snapshot(db, root)
                self.assertEqual(one["parsed"], 1)
                _, two = context.snapshot(db, root)
                self.assertEqual((two["parsed"], two["reused"]), (0, 1))
                file.write_text("object Example { fun changed() { } }", encoding="utf-8")
                changed, three = context.snapshot(db, root)
                self.assertEqual(three["parsed"], 1)
                self.assertNotEqual(first, changed)
                _, four = context.snapshot(db, root, refresh=True)
                self.assertEqual(four["parsed"], 1)
                file.unlink()
                deleted, _ = context.snapshot(db, root)
                self.assertEqual(deleted, {})
                self.assertEqual(db.execute("SELECT count(*) FROM code_sources").fetchone()[0], 0)

    def test_strings_and_comments_do_not_reassign_nested_method_owners(self):
        code = '''object First {
  // fun fake() {
  val text = "object Other { fun fake2() { }"
  fun install() { val nested = "}" }
}
object Second { fun install() { } }
'''
        facts = context.facts("sample.kt", code)
        methods = [(s["name"], s["owner"]) for s in facts["symbols"] if s["kind"] == "fun"]
        # Declarations from strings are not navigation facts.
        self.assertEqual(methods, [("install", "First"), ("install", "Second")])

    def test_scope_selection_uses_metadata_and_does_not_fan_out(self):
        with tempfile.TemporaryDirectory() as temporary:
            db = rom.connect(Path(temporary), create=True)
            self.addCleanup(db.close)
            with db:
                for rid,device,scope,sdk,osver in [("dada-fake-A14","dada","REFERENCE_ONLY","36","3"),
                        ("odd-fuxi-cn","fuxi","A14_PRODUCTION","34","1"),
                        ("odd-fuxi-tw","fuxi","A14_PRODUCTION","34","1")]:
                    db.execute("INSERT INTO roms VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",(rid,temporary,"v","fp",device,"TW" if rid.endswith('tw') else "CN",sdk,"[]",0,scope,osver,"[]"))
            self.assertEqual(context.select_rom(db, "SYSTEM_UI", "dada")["selected"], [])
            self.assertNotIn("REFERENCE_ONLY", json.dumps(context.select_rom(db, "SYSTEM_UI")))
            db.close()


if __name__ == "__main__":
    unittest.main()
