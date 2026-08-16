#!/usr/bin/env python3
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
DIFF = REPO_ROOT / "tools" / "rom_target_diff.py"


def _row(
    feature_id: str = "",
    a14: str = "",
    a15: str = "",
    declared: str = "",
    member: str = "",
    kind: str = "",
    process: str = "",
    loader: str = "",
    signature: str = "",
    status: str = "",
) -> dict:
    return {
        "featureId": feature_id,
        "a14_target": a14,
        "a15_target": a15,
        "declaredClass": declared,
        "memberName": member,
        "memberKind": kind,
        "targetProcess": process,
        "classLoader": loader,
        "dexkitSignature": signature,
        "verificationStatus": status,
    }


class RomTargetDiffTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.diff = __import__("tools.rom_target_diff", fromlist=["diff_inputs"])

    def test_unchanged(self):
        old = [
            _row(
                feature_id="f1",
                a14="Lcom/a/A;->m()V",
                process="com.android.systemui",
                signature="()V",
            )
        ]
        new = [
            _row(
                feature_id="f1",
                a14="Lcom/a/A;->m()V",
                process="com.android.systemui",
                signature="()V",
            )
        ]
        report = self.diff.diff_inputs(old, new)
        self.assertEqual(report["summary"]["UNCHANGED"], 1)

    def test_target_add(self):
        old = [_row(feature_id="f1", a14="Lcom/a/A;", process="sys")]
        new = [
            _row(feature_id="f1", a14="Lcom/a/A;", process="sys"),
            _row(feature_id="f2", a14="Lcom/a/B;", process="sys"),
        ]
        report = self.diff.diff_inputs(old, new)
        self.assertEqual(report["summary"]["ADDED"], 1)
        self.assertEqual(report["summary"]["UNCHANGED"], 1)

    def test_target_remove(self):
        old = [
            _row(feature_id="f1", a14="Lcom/a/A;", process="sys"),
            _row(feature_id="f2", a14="Lcom/a/B;", process="sys"),
        ]
        new = [_row(feature_id="f1", a14="Lcom/a/A;", process="sys")]
        report = self.diff.diff_inputs(old, new)
        self.assertEqual(report["summary"]["REMOVED"], 1)

    def test_signature_change(self):
        old = [
            _row(
                feature_id="f1",
                a14="Lcom/a/A;->m()V",
                a15="Lcom/a/A;->m()V",
                process="com.android.systemui",
                signature="()V",
            )
        ]
        new = [
            _row(
                feature_id="f1",
                a14="Lcom/a/A;->m(I)V",
                a15="Lcom/a/A;->m(I)V",
                process="com.android.systemui",
                signature="(I)V",
            )
        ]
        report = self.diff.diff_inputs(old, new)
        self.assertEqual(report["summary"]["SIGNATURE_CHANGED"], 1)

    def test_renamed(self):
        old = [
            _row(
                feature_id="f1",
                a14="Lcom/a/A;->m()V",
                process="com.android.systemui",
            )
        ]
        new = [
            _row(
                feature_id="f1",
                a14="Lcom/a/A;->n()V",
                process="com.android.systemui",
            )
        ]
        report = self.diff.diff_inputs(old, new)
        self.assertEqual(report["summary"]["RENAMED"], 1)

    def test_moved(self):
        old = [
            _row(
                feature_id="f1",
                a14="Lcom/a/A;->m()V",
                process="com.android.systemui",
            )
        ]
        new = [
            _row(
                feature_id="f1",
                a14="Lcom/a/B;->m()V",
                process="com.android.systemui",
            )
        ]
        report = self.diff.diff_inputs(old, new)
        self.assertEqual(report["summary"]["MOVED"], 1)

    def test_process_matrix_missing_entries(self):
        old = [
            _row(
                feature_id="f1",
                a14="Lcom/a/A;->m()V",
                process="com.android.systemui",
            )
        ]
        new = [
            _row(
                feature_id="f1",
                a14="Lcom/a/A;->m()V",
            )
        ]
        report = self.diff.diff_inputs(old, new)
        self.assertEqual(report["summary"]["DEXKIT_REQUIRED"], 1)

    def test_auxiliary_process_mis_routing(self):
        old = [
            _row(
                feature_id="f1",
                a14="Lcom/android/systemui/StatusBar;->show()V",
                process="com.android.systemui",
            )
        ]
        new = [
            _row(
                feature_id="f1",
                a14="Lcom/android/systemui/StatusBar;->show()V",
                process="com.miui.home",
            )
        ]
        report = self.diff.diff_inputs(old, new)
        self.assertEqual(report["summary"]["PROCESS_CHANGED"], 1)

    def test_compare_feature_id_list_with_matrix(self):
        old = ["f1", "f2"]
        new = [
            _row(
                feature_id="f1",
                a14="Lcom/a/A;->m()V",
                process="com.android.systemui",
                signature="()V",
            ),
        ]
        report = self.diff.diff_inputs(old, new)
        types = {c["changeType"] for c in report["changes"]}
        self.assertIn("UNCHANGED", types)
        self.assertIn("REMOVED", types)

    def test_dexkit_required_when_a15_missing(self):
        old = [
            _row(
                feature_id="f1",
                a14="Lcom/a/A;->m()V",
                a15="Lcom/a/A;->m()V",
                process="com.android.systemui",
            )
        ]
        new = [
            _row(
                feature_id="f1",
                a14="Lcom/a/A;->m(I)V",
                a15="",
                process="com.android.systemui",
            )
        ]
        report = self.diff.diff_inputs(old, new)
        # The A14 member changed and the A15 replacement is unknown.
        self.assertEqual(
            report["summary"]["DEXKIT_REQUIRED"], 1, report["changes"]
        )

    def test_cli_outputs(self):
        old = [{"featureId": "f1", "a14_target": "Lcom/a/A;"}]
        new = [{"featureId": "f1", "a14_target": "Lcom/a/A;"}]
        with tempfile.TemporaryDirectory() as tmp:
            old_path = Path(tmp) / "old.json"
            new_path = Path(tmp) / "new.json"
            json_out = Path(tmp) / "out.json"
            md_out = Path(tmp) / "out.md"
            old_path.write_text(json.dumps(old), encoding="utf-8")
            new_path.write_text(json.dumps(new), encoding="utf-8")
            r = subprocess.run(
                [
                    sys.executable,
                    str(DIFF),
                    str(old_path),
                    str(new_path),
                    "--output-json",
                    str(json_out),
                    "--output-markdown",
                    str(md_out),
                ],
                capture_output=True,
                text=True,
            )
            self.assertEqual(r.returncode, 0)
            self.assertTrue(json_out.is_file())
            self.assertTrue(md_out.is_file())


if __name__ == "__main__":
    unittest.main()
