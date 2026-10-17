import importlib.util
import json
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

    def test_markdown_generation(self):
        data = _valid_inventory()
        md = _audit.generate_markdown(data)
        self.assertIn("# Feature Effect and Restart Matrix", md)
        self.assertIn("## restartTarget: UNKNOWN", md)
        self.assertIn("### installPhase: UNKNOWN", md)
        self.assertIn("| Feature | preference key | target package | enable | disable | value-change | evidence | confidence |", md)
        self.assertIn("Test Feature", md)
        self.assertIn("pref_test", md)


if __name__ == "__main__":
    unittest.main()
