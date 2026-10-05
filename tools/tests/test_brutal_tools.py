import base64
import contextlib
import io
import json
import tempfile
import unittest
import zipfile
from pathlib import Path

from tools import apk_semantic_diff
from tools import brutal_test_runner
from tools import catalog_contract_probe
from tools import ci_contract_scan
from tools import source_hazard_scan


class CIWorkflowRegressionTest(unittest.TestCase):
    WORKFLOWS = Path(__file__).resolve().parents[2] / ".github" / "workflows"

    def scan(self, name: str, text: str) -> list[str]:
        with tempfile.TemporaryDirectory() as td:
            path = Path(td) / name
            path.write_text(text, encoding="utf-8")
            return ci_contract_scan.scan_workflow(path, "main", "main")

    def test_real_workflows_pass(self):
        for name in ("a14-fast-ci.yml", "a14-full-ci.yml"):
            with self.subTest(name=name):
                self.assertEqual([], self.scan(name, (self.WORKFLOWS / name).read_text(encoding="utf-8")))

    def test_disabling_jdk_verification_is_rejected(self):
        name = "a14-fast-ci.yml"
        original = (self.WORKFLOWS / name).read_text(encoding="utf-8")
        for replacement in ("verify-signature: false", "# signature setting removed"):
            with self.subTest(replacement=replacement):
                errors = self.scan(name, original.replace("verify-signature: true", replacement))
                self.assertIn("CI_JDK_SIGNATURE", "\n".join(errors))

    def test_either_cached_reproducibility_build_is_rejected(self):
        name = "a14-full-ci.yml"
        original = (self.WORKFLOWS / name).read_text(encoding="utf-8")
        for flag in (
            "--no-daemon", "--no-build-cache", "--no-configuration-cache",
            "-Pkotlin.compiler.execution.strategy=in-process", "-Pkotlin.incremental=false",
        ):
            for occurrence in (0, 1):
                with self.subTest(flag=flag, occurrence=occurrence):
                    lines = original.splitlines()
                    builds = [i for i, line in enumerate(lines) if "clean :app:assembleDevelop" in line]
                    lines[builds[occurrence]] = lines[builds[occurrence]].replace(flag, "")
                    errors = self.scan(name, "\n".join(lines) + "\n")
                    self.assertIn("CI_REPRO_CACHE", "\n".join(errors))

    def test_missing_mapping_comparison_is_rejected(self):
        name = "a14-full-ci.yml"
        original = (self.WORKFLOWS / name).read_text(encoding="utf-8")
        changed = "\n".join(line for line in original.splitlines() if not line.strip().startswith("cmp ")) + "\n"
        self.assertIn("CI_REPRO_MAPPING", "\n".join(self.scan(name, changed)))

    def test_silent_missing_develop_artifacts_is_rejected(self):
        name = "a14-full-ci.yml"
        original = (self.WORKFLOWS / name).read_text(encoding="utf-8")
        changed = original.replace("if-no-files-found: error", "if-no-files-found: ignore")
        self.assertIn("CI_REQUIRED_ARTIFACT", "\n".join(self.scan(name, changed)))


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

    def test_pinned_sdk_script_satisfies_api37_contract(self):
        with tempfile.TemporaryDirectory() as td:
            path = Path(td) / "good.yml"
            path.write_text(
                """name: good
on:
  push:
    branches:
      - main
jobs:
  x:
    runs-on: ubuntu-24.04
    timeout-minutes: 60
    permissions:
      contents: read
    steps:
      - uses: actions/checkout@v6
        with:
          fetch-depth: 0
      - uses: android-actions/setup-android@v4
        with:
          packages: ''
      - run: bash tools/ci_install_android_sdk.sh
""",
                encoding="utf-8",
            )
            errors = ci_contract_scan.scan_workflow(path, "main", "main")
            text = "\n".join(errors)
            self.assertNotIn("CI_API37_RESOLUTION", text)
            self.assertNotIn("CI_SDK_NONDETERMINISTIC", text)

    def test_requires_checkout_credential_hardening_and_sha_pins(self):
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
    timeout-minutes: 60
    steps:
      - uses: actions/checkout@v6
        with:
          fetch-depth: 0
          persist-credentials: true
      - run: chmod +x ./gradlew
""",
                encoding="utf-8",
            )
            errors = "\n".join(ci_contract_scan.scan_workflow(path, "main", "main"))
            self.assertIn("CI_CHECKOUT_CREDENTIALS", errors)
            self.assertIn("CI_ACTION_PIN", errors)
            self.assertIn("CI_GRADLEW_MODE", errors)

    def test_accepts_sha_pinned_checkout_without_persisted_credentials(self):
        with tempfile.TemporaryDirectory() as td:
            path = Path(td) / "good.yml"
            path.write_text(
                """name: good
on:
  push:
    branches:
      - main
jobs:
  x:
    runs-on: ubuntu-24.04
    timeout-minutes: 60
    permissions:
      contents: read
    steps:
      - uses: actions/checkout@d23441a48e516b6c34aea4fa41551a30e30af803
        with:
          fetch-depth: 0
          persist-credentials: false
""",
                encoding="utf-8",
            )
            errors = "\n".join(ci_contract_scan.scan_workflow(path, "main", "main"))
            self.assertNotIn("CI_CHECKOUT_CREDENTIALS", errors)
            self.assertNotIn("CI_ACTION_PIN", errors)
            self.assertNotIn("CI_GRADLEW_MODE", errors)

    def test_compile_sdk_major_must_match_pinned_platform(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            (root / "app").mkdir()
            (root / "tools").mkdir()
            (root / "app" / "build.gradle.kts").write_text("compileSdk = 36\n", encoding="utf-8")
            (root / "tools" / "ci_install_android_sdk.sh").write_text(
                'PLATFORM_PACKAGE="platforms;android-37.1"\nBUILD_TOOLS_PACKAGE="build-tools;37.0.0"\n',
                encoding="utf-8",
            )
            errors = "\n".join(ci_contract_scan.scan_compile_sdk_contract(root))
            self.assertIn("CI_SDK_COMPILE_MAJOR", errors)

    def test_repo_compile_sdk_matches_pinned_platform(self):
        repo = Path(__file__).resolve().parents[2]
        self.assertEqual([], ci_contract_scan.scan_compile_sdk_contract(repo))

    def test_sdk_build_tools_pin_must_match_actual_selection(self):
        for selected, installed, expected in (
            ('buildToolsVersion = "36.0.0"\n', "36.0.0", False),
            ('buildToolsVersion = "36.0.0"\n', "37.0.0", True),
            ("", "36.0.0", True),
        ):
            with self.subTest(selected=selected, installed=installed), tempfile.TemporaryDirectory() as td:
                root = Path(td)
                (root / "app").mkdir()
                (root / "tools").mkdir()
                (root / "app/build.gradle.kts").write_text("compileSdk = 37\n" + selected, encoding="utf-8")
                (root / "tools/ci_install_android_sdk.sh").write_text(
                    'PLATFORM_PACKAGE="platforms;android-37.1"\n'
                    f'BUILD_TOOLS_PACKAGE="build-tools;{installed}"\n',
                    encoding="utf-8",
                )
                errors = "\n".join(ci_contract_scan.scan_compile_sdk_contract(root))
                self.assertEqual(expected, "CI_SDK_BUILD_TOOLS" in errors, errors)

    def test_pinned_sdk_script_is_stable_and_exact(self):
        repo = Path(__file__).resolve().parents[2]
        errors = ci_contract_scan.scan_android_sdk_script(repo)
        self.assertEqual([], errors)

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

    def test_duplicate_mutation_reaches_specs_after_feature_classes(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            catalog = root / "SystemUiFeatures.kt"
            classes = (
                "class First { override val id = FirstFeatureId }\n"
                "class Second { override val id = SecondFeatureId }\n"
            )
            catalog.write_text(classes + "\n".join(
                f'LazyFeatureSpec(id = {symbol}, name = "{name}", '
                f'preferenceKey = "{name}", target = FeatureTarget.SYSTEM_UI, '
                'phase = InstallPhase.PACKAGE_READY)'
                for symbol, name in [("FirstFeatureId", "first"), ("SecondFeatureId", "second")]
            ), encoding="utf-8")
            ids = root / "FeatureIds.kt"
            ids.write_text("\n".join(
                f'data object {symbol} : FeatureId {{ override val id = {number} '
                f'override val name = "{name}" }}'
                for number, symbol, name in [(1, "FirstFeatureId", "first"), (2, "SecondFeatureId", "second")]
            ), encoding="utf-8")
            matrix = root / "matrix.csv"
            matrix.write_text(
                "featureIdName,name,preferenceKey,target,phase\n"
                "first,first,first,SYSTEM_UI,PACKAGE_READY\n"
                "second,second,second,SYSTEM_UI,PACKAGE_READY\n", encoding="utf-8",
            )
            args = ["--catalog", str(catalog), "--feature-id", str(ids), "--matrix", str(matrix)]
            output = io.StringIO()
            with contextlib.redirect_stdout(output):
                self.assertEqual(0, catalog_contract_probe.main(args))
                brutal_test_runner.mutate_duplicate_feature_id(root, {"catalog_file": catalog.name})
                self.assertEqual(1, catalog_contract_probe.main(args))
            self.assertTrue(catalog.read_text(encoding="utf-8").startswith(classes))
            self.assertIn("FeatureSpec id duplicate: FirstFeatureId", output.getvalue())


if __name__ == "__main__":
    unittest.main()
