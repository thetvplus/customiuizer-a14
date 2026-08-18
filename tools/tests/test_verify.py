import importlib.util
import json
import sys
import tempfile
import unittest
from pathlib import Path

_REPO_ROOT = Path(__file__).resolve().parent.parent.parent
sys.path.insert(0, str(_REPO_ROOT / "tools"))
_VERIFY_PATH = _REPO_ROOT / "tools" / "verify.py"
_AUDIT_PATH = _REPO_ROOT / "tools" / "audit-feature-semantics.py"
_INVENTORY_PATH = _REPO_ROOT / "feature-semantics" / "a14.json"
_SCHEMA_PATH = _REPO_ROOT / "feature-semantics" / "schema.json"

_spec_verify = importlib.util.spec_from_file_location("verify_local", _VERIFY_PATH)
_verify = importlib.util.module_from_spec(_spec_verify)
_spec_verify.loader.exec_module(_verify)

_spec_audit = importlib.util.spec_from_file_location("audit_feature_semantics", _AUDIT_PATH)
_audit = importlib.util.module_from_spec(_spec_audit)
_spec_audit.loader.exec_module(_audit)


class VerifyFeatureSemanticsTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.repo_root = _REPO_ROOT
        cls.schema = _audit.load_schema(_SCHEMA_PATH)

    def _load_inventory(self):
        with _INVENTORY_PATH.open("r", encoding="utf-8") as f:
            return json.load(f)

    def test_verifyRunsFeatureSemanticsValidation(self):
        """verify.check_feature_semantics runs audit-feature-semantics.py --validate."""
        code = _verify.check_feature_semantics()
        self.assertEqual(code, 0)

    def test_javaVersionParserAcceptsJdk25(self):
        self.assertEqual(
            _verify.parse_java_major('java version "25.0.4" 2026-07-21 LTS'),
            25,
        )
        self.assertEqual(
            _verify.parse_java_major('openjdk version "25.0.2" 2026-01-20'),
            25,
        )

    def test_javaVersionParserRejectsUnrelatedOutput(self):
        self.assertIsNone(_verify.parse_java_major("not a java runtime"))

    def test_hookBodyPrefmapGateIsWired(self):
        self.assertTrue(callable(_verify.check_hook_body_prefmap))
        self.assertEqual(0, _verify.check_hook_body_prefmap())

    def test_commandEnvironmentNormalizesBinJavaHome(self):
        java_name = "java.exe" if _verify.sys.platform == "win32" else "java"
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir) / "jdk-25"
            binary_dir = root / "bin"
            binary_dir.mkdir(parents=True)
            (binary_dir / java_name).touch()
            env = _verify.command_environment({"JAVA_HOME": str(binary_dir)})
            self.assertEqual(env["JAVA_HOME"], str(root))

    def test_successfulValidationReturnsZero(self):
        """audit-feature-semantics --validate exits 0 on the real inventory."""
        code = _audit.main(["--validate"])
        self.assertEqual(code, 0)

    def test_missingDiscoveredPreferenceFails(self):
        """A discovered preference absent from the inventory and exclusions fails validation."""
        inventory = self._load_inventory()
        # Remove the system_charginginfo_fontsize entry so the real discovery fails.
        before = len(inventory["entries"])
        inventory["entries"] = [
            e for e in inventory["entries"]
            if "system_charginginfo_fontsize" not in e.get("preferenceKeys", [])
        ]
        self.assertLess(len(inventory["entries"]), before)

        discovered = {"system_charginginfo_fontsize"}
        code, errors = _audit.validate_inventory(
            self.repo_root, inventory, self.schema, discovered=discovered
        )
        self.assertEqual(code, 1)
        self.assertTrue(
            any("discovered" in e.lower() and "not in inventory" in e.lower() for e in errors),
            f"expected missing discovered key error, got: {errors}"
        )

    def test_chargingFontSizePreferenceIsCovered(self):
        """The real inventory covers the charging info font size preference
        as a single semantic feature linking the runtime canonical key and
        its XML storage alias."""
        inventory = self._load_inventory()
        entries = inventory["entries"]

        found_code = [e for e in entries if "system_charginginfo_fontsize" in e.get("preferenceKeys", [])]
        self.assertEqual(len(found_code), 1, "system_charginginfo_fontsize must appear exactly once")
        entry = found_code[0]

        self.assertEqual(entry.get("defaultValue"), "16")
        self.assertEqual(entry.get("xmlSource"), "app/src/main/res/xml/prefs_system_charginginfo.xml")
        self.assertIn("SystemLockScreenHooks.kt", entry.get("sourceFile", ""))
        self.assertEqual(entry.get("targetPackage"), "com.android.systemui")
        self.assertEqual(entry.get("installPhase"), "PACKAGE_READY")
        # The value is live-applied by the in-hook PreferenceObserver, so no
        # process restart is required for a value change to take effect.
        self.assertEqual(entry.get("restartTarget"), "NONE")
        self.assertEqual(entry.get("hotReloadable"), True)
        self.assertEqual(entry.get("runtimeReadMode"), "OBSERVER_PUSH")

        # The XML storage key is an alias within the same feature.
        self.assertIn("pref_key_system_charginginfo_fontsize", entry.get("preferenceKeys", []))

    def test_observerInferenceIsPerKeyNotPerFile(self):
        """A PreferenceObserver in a source file must not mark unrelated keys
        in the same file as OBSERVER_PUSH / hot reloadable."""
        inventory = self._load_inventory()
        entries = inventory["entries"]

        # Charging info observer lives in SystemLockScreenHooks.kt and explicitly
        # observes system_charginginfo, system_charginginfo_fontsize,
        # system_charginginfo_view.
        charging = [e for e in entries if "system_charginginfo_fontsize" in e.get("preferenceKeys", [])]
        self.assertEqual(len(charging), 1)
        self.assertEqual(charging[0].get("runtimeReadMode"), "OBSERVER_PUSH")
        self.assertEqual(charging[0].get("hotReloadable"), True)

        # system_noscreenlock is also used in SystemLockScreenHooks.kt (isUnlocked),
        # but is NOT in the charging info observer set, so it must not inherit
        # OBSERVER_PUSH semantics from the same file.
        no_screen = [e for e in entries if "system_noscreenlock" in e.get("preferenceKeys", [])]
        # It may be merged with the master act key or standalone; either way it
        # must not claim observer push.
        for entry in no_screen:
            self.assertNotEqual(
                entry.get("runtimeReadMode"), "OBSERVER_PUSH",
                f"system_noscreenlock must not inherit observer semantics: {entry}"
            )
            self.assertEqual(
                entry.get("hotReloadable"), False,
                f"system_noscreenlock must not be marked hot reloadable: {entry}"
            )

    def test_hotReloadableValueDoesNotRequireRestart(self):
        """An observer-backed preference has restartTarget=NONE; no observer
        does not fabricate a restart target from the host process."""
        inventory = self._load_inventory()
        entries = inventory["entries"]

        charging_view = [e for e in entries if "system_charginginfo_view" in e.get("preferenceKeys", [])]
        self.assertEqual(len(charging_view), 1)
        self.assertEqual(charging_view[0].get("hotReloadable"), True)
        self.assertEqual(charging_view[0].get("restartTarget"), "NONE")

        # A non-master, non-observed key must not have its restartTarget
        # fabricated from FeatureTarget (e.g. SYSTEMUI).
        noscreen = [e for e in entries if "system_noscreenlock" in e.get("preferenceKeys", [])]
        for entry in noscreen:
            self.assertEqual(
                entry.get("restartTarget"), "UNKNOWN",
                f"restartTarget must be UNKNOWN without evidence: {entry}"
            )


if __name__ == "__main__":
    unittest.main()
