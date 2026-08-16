import base64
import json
import tempfile
import unittest
import zipfile
from pathlib import Path

from tools import apk_semantic_diff
from tools import catalog_contract_probe
from tools import ci_contract_scan
from tools import source_hazard_scan


class SourceHazardTest(unittest.TestCase):
    def test_finds_swallowed_throwable(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            path = root / "app/src/main/java/x/Bad.kt"
            path.parent.mkdir(parents=True)
            path.write_text(
                "package x\nobject Bad { fun x() { try {} catch (t: Throwable) { } } }\n",
                encoding="utf-8",
            )
            findings = source_hazard_scan.collect(root, ["app/src/main/java"])
            rules = {f.rule for f in findings}
            self.assertIn("EMPTY_CATCH", rules)
            self.assertIn("CATCH_THROWABLE_NO_FATAL", rules)

    def test_allow_marker_is_narrow(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            path = root / "app/src/main/java/x/Bad.kt"
            path.parent.mkdir(parents=True)
            path.write_text(
                "package x\nfun x() { try {} catch (t: Throwable) { } } "
                "// BRUTAL_ALLOW:EMPTY_CATCH\n",
                encoding="utf-8",
            )
            rules = {f.rule for f in source_hazard_scan.collect(root, ["app/src/main/java"])}
            self.assertNotIn("EMPTY_CATCH", rules)
            self.assertIn("CATCH_THROWABLE_NO_FATAL", rules)


class CIContractTest(unittest.TestCase):
    def test_catches_shallow_signing_and_brittle_sdk(self):
        with tempfile.TemporaryDirectory() as td:
            path = Path(td) / "bad.yml"
            path.write_text(
                """name: bad
on:
  push:
    branches:
      - main
jobs:
  x:
    runs-on: ubuntu-24.04
    steps:
      - uses: actions/checkout@v4
      - run: sdkmanager "platforms;android-37"
      - run: ./gradlew -PofficialRelease=true assembleDevelop
""",
                encoding="utf-8",
            )
            errors = ci_contract_scan.scan_workflow(path, "devin/audit", "main")
            text = "\n".join(errors)
            self.assertIn("CI_FULL_HISTORY", text)
            self.assertIn("CI_SIGNING", text)
            self.assertIn("CI_API37_RESOLUTION", text)
            self.assertIn("CI_EXACT_BRANCH", text)

    def test_catches_windows_only_tool_path(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            path = root / "tools" / "bad.py"
            path.parent.mkdir(parents=True)
            payload = base64.b64decode(
                "ZnJvbSBwYXRobGliIGltcG9ydCBQYXRoCnggPSBQYXRoKCJDOlxcdGVtcFxc"
                "cmVwbyIpCnkgPSAiYS9iIi5yZXBsYWNlKCIvIiwgIlxcIikK"
            ).decode("utf-8")
            path.write_text(payload, encoding="utf-8")
            errors = ci_contract_scan.scan_repo_scripts(root)
            joined = "\n".join(errors)
            self.assertIn("CI_WINDOWS_PATH_REPLACE", joined)
            self.assertIn("CI_HARDCODED_DRIVE", joined)

    def test_catches_schedule_without_explicit_if(self):
        with tempfile.TemporaryDirectory() as td:
            path = Path(td) / "bad.yml"
            path.write_text(
                """name: bad
on:
  schedule:
    - cron: '0 0 * * 0'
  push:
    branches:
      - devin/audit
jobs:
  x:
    runs-on: ubuntu-24.04
    steps:
      - uses: actions/checkout@v4
""",
                encoding="utf-8",
            )
            errors = ci_contract_scan.scan_workflow(path, "devin/audit", "main")
            text = "\n".join(errors)
            self.assertIn("CI_SCHEDULE_CONDITION", text)

    def test_allows_schedule_with_explicit_if(self):
        with tempfile.TemporaryDirectory() as td:
            path = Path(td) / "good.yml"
            path.write_text(
                """name: good
on:
  schedule:
    - cron: '0 0 * * 0'
  workflow_dispatch:
jobs:
  full:
    runs-on: ubuntu-24.04
    if: >-
      github.event_name == 'schedule' ||
      github.event_name == 'workflow_dispatch'
    steps:
      - uses: actions/checkout@v4
""",
                encoding="utf-8",
            )
            errors = ci_contract_scan.scan_workflow(path, "devin/audit", "main")
            text = "\n".join(errors)
            self.assertNotIn("CI_SCHEDULE_CONDITION", text)


class APKDiffTest(unittest.TestCase):
    def _apk(self, path: Path, entries: dict[str, bytes]):
        with zipfile.ZipFile(path, "w") as zf:
            for name, data in entries.items():
                zf.writestr(name, data)

    def test_ignores_signature_but_detects_dex(self):
        with tempfile.TemporaryDirectory() as td:
            old = Path(td) / "old.apk"
            new = Path(td) / "new.apk"
            self._apk(old, {"classes.dex": b"a", "META-INF/X.SF": b"old"})
            self._apk(new, {"classes.dex": b"b", "META-INF/X.SF": b"new"})
            result = apk_semantic_diff.compare(
                apk_semantic_diff.inspect(old), apk_semantic_diff.inspect(new)
            )
            self.assertEqual(["classes.dex"], result["changed"])
            self.assertFalse(result["normalizedEqual"])


class CatalogParserTest(unittest.TestCase):
    def test_balanced_feature_spec_blocks(self):
        text = 'FeatureSpec(id = "a", condition = { x(1) }), FeatureSpec(id = "b")'
        blocks = catalog_contract_probe.balanced_blocks(text, "FeatureSpec")
        self.assertEqual(2, len(blocks))
        self.assertEqual("a", catalog_contract_probe.field(blocks[0], "id"))


if __name__ == "__main__":
    unittest.main()
