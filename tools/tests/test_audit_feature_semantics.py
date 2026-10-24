import importlib.util
import contextlib
import io
import json
import tempfile
import unittest
from pathlib import Path

# Load the module under test despite the hyphen in its filename.
_REPO_ROOT = Path(__file__).resolve().parent.parent.parent
_TOOL_PATH = _REPO_ROOT / "tools" / "audit-feature-semantics.py"

_spec = importlib.util.spec_from_file_location("audit_feature_semantics", _TOOL_PATH)
_audit = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_audit)


def _base_entry(feature_id="test_feature", feature_name="Test Feature", key="pref_test"):
    return {
        "featureId": feature_id,
        "featureName": feature_name,
        "preferenceKeys": [key],
        "xmlSource": "",
        "defaultValue": "",
        "sourceFile": "feature-semantics/schema.json",
        "installer": "UNKNOWN",
        "targetPackage": "UNKNOWN",
        "installPhase": "UNKNOWN",
        "runtimeReadMode": "UNKNOWN",
        "enableEffect": "UNKNOWN",
        "disableEffect": "UNKNOWN",
        "valueChangeEffect": "UNKNOWN",
        "restartTarget": "UNKNOWN",
        "hotReloadable": False,
        "confidence": "UNKNOWN",
        "evidence": "test evidence",
        "notes": "",
    }


def _valid_inventory(**kwargs):
    return {
        "schemaVersion": 1,
        "contractName": "test",
        "romFamily": "test",
        "androidApi": 34,
        "exclusions": [],
        "entries": [_base_entry(**kwargs)],
    }


class TestAuditFeatureSemantics(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.repo_root = _REPO_ROOT
        cls.schema = _audit.load_schema(cls.repo_root / "feature-semantics" / "schema.json")

    def test_schema_validation_valid(self):
        data = _valid_inventory()
        code, errors = _audit.validate_inventory(self.repo_root, data, self.schema, discovered={"pref_test"})
        self.assertEqual(code, 0, errors)
        self.assertEqual(errors, [])

    def test_duplicate_feature_id(self):
        data = _valid_inventory()
        data["entries"].append(_base_entry(key="pref_other"))
        code, errors = _audit.validate_inventory(self.repo_root, data, self.schema, discovered={"pref_test", "pref_other"})
        self.assertEqual(code, 1)
        self.assertTrue(any("duplicate featureId" in e for e in errors))

    def test_illegal_enum(self):
        data = _valid_inventory()
        data["entries"][0]["installPhase"] = "BOGUS_PHASE"
        code, errors = _audit.validate_inventory(self.repo_root, data, self.schema, discovered={"pref_test"})
        self.assertEqual(code, 1)
        self.assertTrue(any("illegal installPhase" in e for e in errors))

    def test_unlisted_key_without_exclusion_fails(self):
        data = _valid_inventory()
        code, errors = _audit.validate_inventory(
            self.repo_root, data, self.schema, discovered={"pref_test", "pref_unlisted"}
        )
        self.assertEqual(code, 1)
        self.assertTrue(any("unlisted" in e.lower() for e in errors))

    def test_exclusion_without_reason_fails(self):
        data = _valid_inventory()
        data["exclusions"] = [{"preferenceKey": "pref_legacy"}]
        code, errors = _audit.validate_inventory(
            self.repo_root, data, self.schema, discovered={"pref_test"}
        )
        self.assertEqual(code, 1)
        self.assertTrue(any("reason" in e.lower() for e in errors))

    def test_root_shape_and_version_are_input_errors(self):
        for update in ({"schemaVersion": 999}, {"schemaVersion": True}, {"androidApi": "34"},
                       {"contractName": []}, {"entries": {}}, {"exclusions": {}}):
            with self.subTest(update=update):
                data = _valid_inventory()
                data.update(update)
                code, errors = _audit.validate_inventory(self.repo_root, data, self.schema)
                self.assertEqual(2, code)
                self.assertTrue(errors)
        self.assertEqual(2, _audit.validate_inventory(self.repo_root, [], self.schema)[0])

    def test_malformed_fields_fail_without_crashing(self):
        for update in ({"featureId": {}}, {"featureId": "UPPER"}, {"preferenceKeys": [{}]},
                       {"preferenceKeys": "pref_test"}, {"sourceFile": []}, {"installer": []},
                       {"confidence": []}, {"xmlSource": 1}, {"notes": None}, {"exclusions": [None]}):
            with self.subTest(update=update):
                data = _valid_inventory()
                data["entries"][0].update(update)
                code, errors = _audit.validate_inventory(self.repo_root, data, self.schema, {"pref_test"})
                self.assertEqual(1, code)
                self.assertTrue(errors)

    def test_malformed_exclusions_fail_without_crashing(self):
        for exclusion in (None, [], {"preferenceKey": {}, "reason": "test"},
                          {"preferenceKey": "pref_test", "reason": []}):
            with self.subTest(exclusion=exclusion):
                data = _valid_inventory()
                data["exclusions"] = [exclusion]
                code, errors = _audit.validate_inventory(self.repo_root, data, self.schema, {"pref_test"})
                self.assertEqual(1, code)
                self.assertTrue(any("exclusion[0]" in error for error in errors))

    def test_bootstrap_never_overwrites_calibrated_inventory_or_candidate(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            inventory = root / "feature-semantics/a14.json"
            inventory.parent.mkdir()
            inventory.write_text("calibrated", encoding="utf-8")
            with contextlib.redirect_stderr(io.StringIO()):
                self.assertEqual(2, _audit.main(["--repo-root", str(root), "--init"]))
            self.assertEqual("calibrated", inventory.read_text(encoding="utf-8"))
            candidate = root / "candidate.json"
            with contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(0, _audit.main(["--repo-root", str(root), "--init", "--output", str(candidate)]))
            before = candidate.read_bytes()
            self.assertEqual([], json.loads(before)["entries"])
            with contextlib.redirect_stderr(io.StringIO()):
                self.assertEqual(2, _audit.main(["--repo-root", str(root), "--init", "--output", str(candidate)]))
            self.assertEqual(before, candidate.read_bytes())
            self.assertEqual("calibrated", inventory.read_text(encoding="utf-8"))

    def test_validation_uses_selected_repository(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            data = _valid_inventory()
            data["entries"][0]["sourceFile"] = ""
            directory = root / "feature-semantics"
            directory.mkdir()
            (directory / "schema.json").write_text(json.dumps(self.schema), encoding="utf-8")
            (directory / "a14.json").write_text(json.dumps(data), encoding="utf-8")
            with contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(0, _audit.main(["--repo-root", str(root), "--validate"]))
            data["schemaVersion"] = 999
            (directory / "a14.json").write_text(json.dumps(data), encoding="utf-8")
            with contextlib.redirect_stderr(io.StringIO()):
                self.assertEqual(2, _audit.main(["--repo-root", str(root), "--validate"]))


if __name__ == "__main__":
    unittest.main()
