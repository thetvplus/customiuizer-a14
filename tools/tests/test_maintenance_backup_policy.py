import unittest
import xml.etree.ElementTree as ET
from pathlib import Path


class MaintenanceBackupPolicyTest(unittest.TestCase):
    def test_local_recovery_is_excluded_from_both_transports_without_disabling_preferences_backup(self):
        root = Path(__file__).resolve().parents[2]
        app = ET.parse(root / "app/src/main/AndroidManifest.xml").getroot().find("application")
        android = "{http://schemas.android.com/apk/res/android}"
        self.assertNotEqual("false", app.get(android + "allowBackup"))
        reference = app.get(android + "dataExtractionRules")
        self.assertTrue(reference and reference.startswith("@xml/"))
        rules = ET.parse(root / "app/src/main/res/xml" / (reference.split("/", 1)[1] + ".xml")).getroot()
        self.assertEqual("data-extraction-rules", rules.tag)
        self.assertEqual({"cloud-backup", "device-transfer"}, {mode.tag for mode in rules})
        for mode in rules:
            self.assertEqual([("exclude", "sharedpref", "maintenance_recovery.xml")],
                             [(item.tag, item.get("domain"), item.get("path")) for item in mode])


if __name__ == "__main__":
    unittest.main()
