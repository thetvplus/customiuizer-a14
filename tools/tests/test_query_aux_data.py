"""Exercise bounded, read-only queries against the actual canonical inputs."""
import contextlib
import io
import json
import tempfile
import unittest
from pathlib import Path

from tools import query_aux_data as tool


class AuxiliaryDataQueryTest(unittest.TestCase):
    def run_query(self, *arguments):
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            self.assertEqual(0, tool.main(list(arguments)))
        return json.loads(output.getvalue())

    def test_default_summaries_read_original_files_without_changes(self):
        for dataset, spec in tool.DATASETS.items():
            with self.subTest(dataset=dataset):
                path = tool.ROOT / spec["path"]
                before = path.read_bytes()
                result = self.run_query(dataset)
                self.assertEqual(len(json.loads(before)[spec["records"]]), result["matched"])
                self.assertEqual([], result["records"])
                self.assertEqual(0, result["returned"])
                self.assertIsNone(result["nextOffset"])
                self.assertEqual(spec["path"], result["source"])
                self.assertEqual(before, path.read_bytes())

    def test_storage_alias_and_runtime_key_return_same_evidence(self):
        short = self.run_query("semantics", "--key", "system_charginginfo_fontsize", "--full")
        storage = self.run_query("semantics", "--key", "pref_key_system_charginginfo_fontsize", "--full")
        self.assertEqual(short, storage)
        self.assertEqual(1, short["matched"])
        record = short["records"][0]
        original = json.loads((tool.ROOT / short["source"]).read_text(encoding="utf-8"))["entries"]
        self.assertIn(record, original)
        self.assertIn("evidence", record)
        self.assertIn("confidence", record)
        self.assertEqual(0, self.run_query("semantics", "--key", "system_charginginfo_font")["matched"])

    def test_filters_compose_and_unknown_is_not_promoted(self):
        result = self.run_query("semantics", "--target", "com.android.systemui", "--confidence", "UNKNOWN",
                                "--contains", "SYSTEM", "--limit", "2")
        self.assertGreater(result["matched"], 2)
        self.assertEqual(2, result["returned"])
        for record in result["records"]:
            self.assertEqual("UNKNOWN", record["confidence"])
            self.assertEqual("com.android.systemui", record["targetPackage"])
        summary = self.run_query("semantics", "--target", "com.android.systemui", "--confidence", "UNKNOWN",
                                 "--contains", "SYSTEM", "--summary")
        self.assertEqual(result["matched"], summary["matched"])
        self.assertEqual([], summary["records"])

    def test_pagination_and_projection_preserve_source_order(self):
        first = self.run_query("matrix", "--fields", "featureIdInt,featureIdName", "--limit", "3")
        second = self.run_query("matrix", "--fields", "featureIdInt,featureIdName", "--limit", "3", "--offset", "3")
        together = self.run_query("matrix", "--fields", "featureIdInt,featureIdName", "--limit", "6")
        self.assertEqual(3, first["nextOffset"])
        self.assertEqual(first["records"] + second["records"], together["records"])
        self.assertEqual({"featureIdInt", "featureIdName"}, set(first["records"][0]))
        identifier = str(first["records"][0]["featureIdInt"])
        found = self.run_query("matrix", "--feature", identifier)
        self.assertEqual(1, found["matched"])
        self.assertEqual(first["records"][0]["featureIdName"], found["records"][0]["featureIdName"])
        self.assertEqual(first, self.run_query("matrix", "--fields", "featureIdInt,featureIdName", "--limit", "3"))
        past_end = self.run_query("matrix", "--full", "--offset", str(first["total"]))
        self.assertEqual([], past_end["records"])
        self.assertIsNone(past_end["nextOffset"])

    def test_source_and_rom_filters_use_original_field_values(self):
        result = self.run_query("rom", "--target", "launcher", "--source", "Launcher", "--full")
        self.assertGreater(result["matched"], 0)
        for row in result["records"]:
            self.assertEqual("launcher", row["target"])
            self.assertIn("Launcher", row["sourceFile"])
        hazards = self.run_query("hazards", "--source", "mods", "--limit", "1")
        self.assertEqual(1, hazards["returned"])
        self.assertIn("mods", hazards["records"][0]["path"])

    def test_excluded_navigation_keys_remain_distinguishable_from_missing_keys(self):
        excluded = self.run_query("semantics", "--key", "pref_key_system_island_music_cat")
        self.assertEqual(0, excluded["matched"])
        self.assertEqual(1, len(excluded["exclusions"]))
        self.assertIn("reason", excluded["exclusions"][0])
        missing = self.run_query("semantics", "--key", "does_not_exist")
        self.assertEqual(0, missing["matched"])
        self.assertEqual([], missing["exclusions"])

    def test_invalid_arguments_and_input_fail_without_dumping_records(self):
        for arguments in (("matrix", "--fields", "typo"), ("matrix", "--confidence", "UNKNOWN"),
                          ("rom", "--key", "foo"), ("hazards", "--target", "foo"),
                          ("rom", "--feature", "foo"), ("semantics", "--offset", "-1"),
                          ("semantics", "--limit", "0"), ("semantics", "--limit", "101")):
            with self.subTest(arguments=arguments), contextlib.redirect_stderr(io.StringIO()):
                with self.assertRaises(SystemExit) as failure:
                    tool.main(list(arguments))
                self.assertEqual(2, failure.exception.code)
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            path = root / "feature-semantics/a14.json"
            path.parent.mkdir()
            for payload in ("{broken", "[]", '{"entries": [null]}'):
                path.write_text(payload, encoding="utf-8")
                with contextlib.redirect_stderr(io.StringIO()), self.assertRaises(SystemExit) as failure:
                    tool.main(["semantics", "--repo-root", str(root)])
                self.assertEqual(2, failure.exception.code)


if __name__ == "__main__":
    unittest.main()
