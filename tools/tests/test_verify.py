import importlib.util
import io
import subprocess
import sys
import tempfile
import unittest
from contextlib import ExitStack, redirect_stderr, redirect_stdout
from pathlib import Path
from unittest.mock import patch

_REPO_ROOT = Path(__file__).resolve().parent.parent.parent
sys.path.insert(0, str(_REPO_ROOT / "tools"))
_VERIFY_PATH = _REPO_ROOT / "tools" / "verify.py"

_spec_verify = importlib.util.spec_from_file_location("verify_local", _VERIFY_PATH)
_verify = importlib.util.module_from_spec(_spec_verify)
_spec_verify.loader.exec_module(_verify)


class DiagnosticSummaryTests(unittest.TestCase):
    def test_warnings_survive_success_truncation_and_duplicates_are_counted(self):
        warning = "w: file:///repo/Feature.kt:2:8 API is deprecated"
        output = "\n".join(["task"] * 20 + [warning, warning.replace(":2:8", ":4:2"), "WARNING: Restricted methods will be blocked in a future release"] + ["ok"] * 20)
        summary = _verify._summarize(output)
        self.assertIn(warning + " [x2]", summary)
        self.assertEqual(1, summary.count(warning))
        self.assertIn("[REVIEW] WARNING: Restricted methods", summary)

    def test_unique_diagnostics_are_bounded_and_review_is_prioritized(self):
        output = "\n".join(["task"] * 5 + [f"warning: unique {i}" for i in range(300)] +
                           ["warning: Missing class example.Required"] + ["ok"] * 15)
        summary = _verify._summarize(output)
        self.assertLessEqual(len(summary.splitlines()), 100)
        self.assertIn("[REVIEW] warning: Missing class example.Required", summary)
        self.assertIn("221 diagnostic groups omitted (0 REVIEW)", summary)

    def test_short_output_and_failure_output_are_not_abbreviated(self):
        self.assertEqual("warning: short", _verify._summarize("warning: short\n"))
        output = "\n".join(str(i) for i in range(60))
        result = subprocess.CompletedProcess([], 1, stdout=output, stderr="")
        with patch.object(_verify.subprocess, "run", return_value=result), redirect_stdout(io.StringIO()) as stream:
            self.assertEqual(1, _verify.run(["example"]))
        self.assertIn(output, stream.getvalue())


class BehaviorGateTests(unittest.TestCase):
    def run_gate(self, code, report=None, stale=False):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            xml = root / "app/build/test-results/testDebugUnitTest/TEST-example.RealTest.xml"
            xml.parent.mkdir(parents=True)
            if stale:
                xml.write_text('<testsuite name="example.RealTest" tests="1" failures="1"><testcase name="old"><failure/></testcase></testsuite>')
            def execute(*args):
                self.assertEqual(("testDebugUnitTest", "--rerun", "--tests", "example.RealTest"), args)
                if report is not None:
                    xml.write_text(report)
                return code
            with patch.object(_verify, "REPO_ROOT", root), patch.object(_verify, "check_java_runtime", return_value=0), \
                    patch.object(_verify, "gradle", side_effect=execute), redirect_stdout(io.StringIO()), redirect_stderr(io.StringIO()):
                return _verify.behavior(["example.RealTest"])

    def test_only_fresh_executed_junit_failures_count_as_a_kill(self):
        failing = '<testsuite name="example.RealTest" tests="1"><testcase name="cancel"><failure message="effect committed"/></testcase></testsuite>'
        self.assertEqual(1, self.run_gate(1, failing))
        self.assertEqual(3, self.run_gate(1))  # compilation/tool failure
        self.assertEqual(3, self.run_gate(1, stale=True))
        self.assertEqual(3, self.run_gate(0, failing))  # contradictory result

    def test_success_skip_and_invalid_reports_are_distinct(self):
        self.assertEqual(0, self.run_gate(0, '<testsuite name="example.RealTest" tests="2"/>'))
        self.assertEqual(2, self.run_gate(0, '<testsuite name="example.RealTest" tests="2" skipped="2"/>'))
        self.assertEqual(3, self.run_gate(1, '<broken'))


class VerifyChangedInputsTests(unittest.TestCase):
    def run_changed(self, paths, tests=None, staged=False):
        checks = (
            "check_java_runtime", "check_static_rules", "check_eol",
            "check_observer_key_contract", "check_hook_body_prefmap",
            "check_hotpath_alloc_budget", "check_main_source_cleanliness",
            "check_invariants",
        )
        with ExitStack() as stack:
            for name in checks:
                stack.enter_context(patch.object(_verify, name, return_value=0))
            stack.enter_context(patch.object(_verify, "changed_paths", return_value=paths))
            gradle = stack.enter_context(patch.object(_verify, "gradle", return_value=0))
            code = _verify.fast(tests, changed=not staged, staged=staged)
            return code, gradle

    def test_dependency_and_toolchain_inputs_run_unit_tests(self):
        for path in (
            "gradle/libs.versions.toml", "gradle/wrapper/gradle-wrapper.properties",
            "gradle/gradle-daemon-jvm.properties", "gradle.properties",
            "app/build.gradle.kts", "build.gradle.kts", "settings.gradle.kts",
            "app/lib/framework.jar", "gradlew", _verify.GRADLEW,
        ):
            with self.subTest(path=path):
                code, gradle = self.run_changed([path])
                self.assertEqual(0, code)
                gradle.assert_called_once_with("testDebugUnitTest")

    def test_documentation_only_changes_still_skip_gradle(self):
        code, gradle = self.run_changed(["docs/TESTING.md"])
        self.assertEqual(0, code)
        gradle.assert_not_called()

    def test_production_source_changes_still_compile(self):
        code, gradle = self.run_changed(["app/src/main/java/example/Feature.kt"])
        self.assertEqual(0, code)
        gradle.assert_called_once_with("compileDebugKotlin", "compileDebugJavaWithJavac")

    def test_test_source_changes_still_run_unit_tests(self):
        code, gradle = self.run_changed(["app/src/test/java/example/FeatureTest.kt"])
        self.assertEqual(0, code)
        gradle.assert_called_once_with("testDebugUnitTest")

    def test_explicit_tests_run_even_without_android_changes(self):
        for staged in (False, True):
            for paths in ([], ["docs/TESTING.md"]):
                with self.subTest(staged=staged, paths=paths):
                    code, gradle = self.run_changed(paths, ["*FeatureTest"], staged=staged)
                    self.assertEqual(0, code)
                    gradle.assert_called_once_with("testDebugUnitTest", "--tests", "*FeatureTest")

    def test_scope_flags_are_exclusive_and_allow_explicit_tests(self):
        for option in ("--changed", "--staged"):
            with patch.object(sys, "argv", ["verify.py", "fast", option, "--tests", "*FeatureTest"]):
                with patch.object(_verify, "fast", return_value=0) as fast:
                    self.assertEqual(0, _verify.main())
                    fast.assert_called_once_with(
                        ["*FeatureTest"], changed=option == "--changed", staged=option == "--staged"
                    )
        with patch.object(sys, "argv", ["verify.py", "fast", "--changed", "--staged"]):
            with redirect_stderr(io.StringIO()), self.assertRaises(SystemExit) as error:
                _verify.main()
            self.assertEqual(2, error.exception.code)


class VerifyGitChangesTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        import check_main_source_cleanliness
        cls.cleanliness = check_main_source_cleanliness
        spec = importlib.util.spec_from_file_location("invariants_git_test", _REPO_ROOT / "tools/check-invariants.py")
        cls.invariants = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(cls.invariants)

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.source = "app/src/main/java/example/Existing.kt"
        self.git("init", "-q")
        self.write(".gitignore", "app/src/main/java/ignored/\n")
        self.write(self.source, "class Existing\n")
        self.git("add", ".")
        self.git("commit", "-qm", "baseline")

    def git(self, *args):
        return subprocess.check_output([
            "git", "-c", "user.name=Gate Test", "-c", "user.email=gate-test@example.invalid",
            "-c", "core.autocrlf=false", *args,
        ], cwd=self.root)

    def write(self, name, text="class Fixture\n"):
        path = self.root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")
        return path

    def source_paths(self, staged=False):
        with (
            patch.object(self.cleanliness, "REPO_ROOT", self.root),
            patch.object(self.invariants, "REPO_ROOT", self.root),
            patch.object(self.invariants, "SOURCE_ROOT", self.root / "app/src/main/java"),
        ):
            return (
                self.cleanliness._git_changed_files(staged=staged),
                self.invariants.staged_kotlin_files() if staged else self.invariants.changed_kotlin_files(),
            )

    def test_untracked_unicode_source_and_tests_are_included_ignored_files_are_not(self):
        source = "app/src/main/java/example/中文 新功能.kt"
        test = "app/src/test/java/example/新功能 Test.kt"
        self.write(source, "import org.junit.Test\n")
        self.write(test)
        self.write("app/src/main/java/ignored/Skipped.kt")
        self.assertEqual({source, test}, set(_verify.changed_paths(self.root)))
        for paths in self.source_paths():
            self.assertEqual([self.root / source], paths)
        with patch.object(self.cleanliness, "REPO_ROOT", self.root):
            self.assertIn("test-dependency-reference", self.cleanliness._scan_file(self.root / source)[0])

    def test_staged_scope_excludes_unstaged_and_untracked_changes(self):
        source = "app/src/main/java/example/Staged.kt"
        self.write(source)
        self.git("add", "--", source)
        self.write(self.source, "class Changed\n")
        self.write("app/src/test/java/example/UntrackedTest.kt")
        self.assertEqual([source], _verify.changed_paths(self.root, staged=True))
        self.assertEqual(3, len(_verify.changed_paths(self.root)))
        for paths in self.source_paths(staged=True):
            self.assertEqual([self.root / source], paths)

    def test_source_moved_out_of_android_tree_keeps_the_deleted_input(self):
        target = "docs/Existing.kt"
        (self.root / "docs").mkdir()
        self.git("mv", self.source, target)
        for staged in (False, True):
            self.assertEqual({self.source, target}, set(_verify.changed_paths(self.root, staged=staged)))
        for paths in self.source_paths():
            self.assertEqual([], paths)

    def test_git_failure_does_not_report_a_clean_worktree(self):
        with patch.object(subprocess, "check_output", side_effect=subprocess.CalledProcessError(1, "git")):
            with self.assertRaises(subprocess.CalledProcessError):
                _verify.changed_paths(self.root)


class VerifyProcessOutputTests(unittest.TestCase):
    def test_unicode_and_invalid_bytes_keep_diagnostics_and_exit_status(self):
        for status in (0, 7):
            with self.subTest(status=status), redirect_stdout(io.StringIO()) as output:
                code = _verify.run([
                    sys.executable, "-c",
                    "import sys; print('中文诊断'); "
                    "sys.stderr.buffer.write(b'bad byte: \\xff\\n'); "
                    f"sys.exit({status})",
                ])
                self.assertEqual(status, code)
                self.assertIn("中文诊断", output.getvalue())
                self.assertIn(r"bad byte: \xff", output.getvalue())


class VerifyJvmTargetTests(unittest.TestCase):
    def check_config(self, build, daemon_version=27):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "gradle").mkdir()
            (root / "gradle/gradle-daemon-jvm.properties").write_text(
                f"toolchainVersion={daemon_version}\n", encoding="utf-8"
            )
            with (
                patch.object(_verify, "REPO_ROOT", root),
                patch.object(_verify, "read_build_gradle", return_value=build),
                patch.object(_verify, "read_module_prop", return_value=(
                    "minApiVersion=102\ntargetApiVersion=102\nstaticScope=true\n"
                )),
            ):
                return _verify.check_static_rules()

    def test_build_uses_jdk27_and_aligned_java_kotlin26(self):
        self.assertEqual(0, self.check_config(_verify.read_build_gradle()))

    def test_each_output_target_must_remain26(self):
        build = _verify.read_build_gradle()
        for setting in ("sourceCompatibility", "targetCompatibility", "jvmTarget"):
            with self.subTest(setting=setting):
                if setting == "jvmTarget":
                    changed = build.replace("JvmTarget.JVM_26", "JvmTarget.JVM_21")
                else:
                    changed = build.replace(
                        f"{setting} = JavaVersion.VERSION_26",
                        f"{setting} = JavaVersion.VERSION_21",
                    )
                self.assertNotEqual(build, changed)
                with self.assertRaises(SystemExit):
                    self.check_config(changed)

    def test_build_jdk_is_independent_of_output_target(self):
        build = _verify.read_build_gradle()
        changed = build.replace("JavaLanguageVersion.of(27)", "JavaLanguageVersion.of(21)")
        self.assertNotEqual(build, changed)
        with self.assertRaises(SystemExit):
            self.check_config(changed)
        with self.assertRaises(SystemExit):
            self.check_config(build, daemon_version=21)


class VerifyEnvironmentTests(unittest.TestCase):


    def test_javaVersionParserAcceptsJdk25(self):
        self.assertEqual(
            _verify.parse_java_major('java version "25.0.4" 2026-07-21 LTS'),
            25,
        )
        self.assertEqual(
            _verify.parse_java_major('openjdk version "25.0.2" 2026-01-20'),
            25,
        )

    def test_javaVersionParserAcceptsJdk27(self):
        self.assertEqual(27, _verify.parse_java_major('openjdk version "27" 2026-09-15'))
        self.assertEqual(27, _verify.REQUIRED_JAVA_MAJOR)

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


if __name__ == "__main__":
    unittest.main()
