import json
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
SCANNER = REPO_ROOT / "tools" / "rom-contract-scan.py"
SCHEMA = REPO_ROOT / "rom-contracts" / "schema.json"
FIXTURES = REPO_ROOT / "tools" / "tests" / "fixtures" / "rom-smali"


class RomContractScanTest(unittest.TestCase):
    def run_scan(self, contract_text, extra_args=None):
        with tempfile.TemporaryDirectory() as tmp:
            contract = Path(tmp) / "contract.json"
            contract.write_text(contract_text, encoding="utf-8")
            args = [sys.executable, str(SCANNER), "--contract", str(contract), "--schema", str(SCHEMA)]
            if extra_args:
                args.extend(extra_args)
            return subprocess.run(args, capture_output=True, text=True)

    def test_all_required_present_exits_0(self):
        contract = {
            "schemaVersion": 1,
            "contractName": "present-test",
            "romFamily": "test",
            "androidApi": 34,
            "targets": [{
                "target": "systemui",
                "class": "Lcom/example/Sample;",
                "methods": [
                    {"name": "foo", "descriptor": "()V", "required": True},
                ],
                "fields": [
                    {"name": "mValue", "type": "I", "required": True},
                ],
            }],
        }
        r = self.run_scan(json.dumps(contract), [
            "--target", f"systemui={FIXTURES / 'systemui'}",
            "--output-json", os.path.join(tempfile.mkdtemp(), "out.json"),
        ])
        self.assertEqual(r.returncode, 0)

    def test_required_method_missing_exits_1(self):
        contract = {
            "schemaVersion": 1,
            "contractName": "missing-test",
            "romFamily": "test",
            "androidApi": 34,
            "targets": [{
                "target": "systemui",
                "class": "Lcom/example/Sample;",
                "methods": [
                    {"name": "missingMethod", "descriptor": "()V", "required": True},
                ],
            }],
        }
        r = self.run_scan(json.dumps(contract), ["--target", f"systemui={FIXTURES / 'systemui'}"])
        self.assertEqual(r.returncode, 1)

    def test_optional_missing_does_not_fail(self):
        contract = {
            "schemaVersion": 1,
            "contractName": "optional-test",
            "romFamily": "test",
            "androidApi": 34,
            "targets": [{
                "target": "systemui",
                "class": "Lcom/example/Sample;",
                "methods": [
                    {"name": "missingMethod", "descriptor": "()V", "required": False},
                ],
            }],
        }
        r = self.run_scan(json.dumps(contract), ["--target", f"systemui={FIXTURES / 'systemui'}"])
        self.assertEqual(r.returncode, 0)

    def test_overload_matching(self):
        contract = {
            "schemaVersion": 1,
            "contractName": "overload-test",
            "romFamily": "test",
            "androidApi": 34,
            "targets": [{
                "target": "systemui",
                "class": "Lcom/example/Sample;",
                "methods": [
                    {"name": "foo", "descriptor": "(I)V", "required": True},
                ],
            }],
        }
        r = self.run_scan(json.dumps(contract), ["--target", f"systemui={FIXTURES / 'systemui'}"])
        self.assertEqual(r.returncode, 0)

    def test_malformed_contract_exits_2(self):
        r = self.run_scan("{not valid json", ["--target", f"systemui={FIXTURES / 'systemui'}"])
        self.assertEqual(r.returncode, 2)

    def test_anyof_alternative(self):
        contract = {
            "schemaVersion": 1,
            "contractName": "anyof-test",
            "romFamily": "test",
            "androidApi": 34,
            "targets": [{
                "target": "systemui",
                "class": "Lcom/example/DoesNotExist;",
                "anyOf": [
                    "Lcom/example/DoesNotExist;",
                    "Lcom/example/Sample;",
                ],
                "methods": [
                    {"name": "foo", "descriptor": "()V", "required": True},
                ],
            }],
        }
        r = self.run_scan(json.dumps(contract), ["--target", f"systemui={FIXTURES / 'systemui'}"])
        self.assertEqual(r.returncode, 0)

    def test_multi_dex_roots(self):
        contract = {
            "schemaVersion": 1,
            "contractName": "multidex-test",
            "romFamily": "test",
            "androidApi": 34,
            "targets": [{
                "target": "systemui",
                "class": "Lcom/example/Other;",
                "methods": [
                    {"name": "getName", "descriptor": "()Ljava/lang/String;", "required": True},
                ],
            }],
        }
        r = self.run_scan(json.dumps(contract), [
            "--target", f"systemui={FIXTURES / 'systemui'}",
            "--target", f"systemui={FIXTURES / 'systemui_classes2'}",
        ])
        self.assertEqual(r.returncode, 0)

    def test_target_not_supplied(self):
        contract = {
            "schemaVersion": 1,
            "contractName": "not-supplied-test",
            "romFamily": "test",
            "androidApi": 34,
            "targets": [{
                "target": "launcher",
                "class": "Lcom/example/Launcher;",
                "methods": [
                    {"name": "onCreate", "descriptor": "()V", "required": True},
                ],
            }],
        }
        # no --target launcher supplied
        r = self.run_scan(json.dumps(contract), ["--target", f"systemui={FIXTURES / 'systemui'}"])
        self.assertEqual(r.returncode, 1)
        self.assertIn("target_not_supplied", r.stdout)

    def test_field_matching(self):
        contract = {
            "schemaVersion": 1,
            "contractName": "field-test",
            "romFamily": "test",
            "androidApi": 34,
            "targets": [{
                "target": "systemui",
                "class": "Lcom/example/Sample;",
                "fields": [
                    {"name": "mValue", "type": "I", "required": True},
                    {"name": "mHidden", "type": "Ljava/lang/String;", "required": True},
                ],
            }],
        }
        r = self.run_scan(json.dumps(contract), ["--target", f"systemui={FIXTURES / 'systemui'}"])
        self.assertEqual(r.returncode, 0)

    def test_markdown_output(self):
        contract = {
            "schemaVersion": 1,
            "contractName": "md-test",
            "romFamily": "test",
            "androidApi": 34,
            "targets": [{
                "target": "systemui",
                "class": "Lcom/example/Sample;",
                "methods": [
                    {"name": "foo", "descriptor": "()V", "required": True},
                ],
            }],
        }
        with tempfile.TemporaryDirectory() as tmp:
            md = Path(tmp) / "report.md"
            r = self.run_scan(json.dumps(contract), [
                "--target", f"systemui={FIXTURES / 'systemui'}",
                "--output-markdown", str(md),
            ])
            self.assertEqual(r.returncode, 0)
            self.assertIn("Present:", md.read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
