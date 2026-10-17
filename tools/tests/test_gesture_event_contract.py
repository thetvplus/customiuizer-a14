#!/usr/bin/env python3
"""Mechanical validation of the A14 gesture event contract from source and tests.

This gate no longer depends on docs/A14_GESTURE_EVENT_CONTRACT.md. It reads the
Kotlin production source under app/src/main/java/tv/withaibuild/customiuizer/mods/
utils/gesture/ and the JVM tests under app/src/test/java/.../gesture/ and asserts
that the source and test evidence are present.

Mutation-style tests copy the relevant source files into a TemporaryDirectory,
remove a required path, and assert the same mechanical checks fail.
"""

from __future__ import annotations

import re
import shutil
import tempfile
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
MAIN_PKG = (
    REPO_ROOT
    / "app"
    / "src"
    / "main"
    / "java"
    / "tv"
    / "withaibuild"
    / "customiuizer"
    / "mods"
    / "utils"
    / "gesture"
)
TEST_PKG = (
    REPO_ROOT
    / "app"
    / "src"
    / "test"
    / "java"
    / "tv"
    / "withaibuild"
    / "customiuizer"
    / "mods"
    / "utils"
    / "gesture"
)

REQUIRED_TEST_FILES = {
    "GestureStateMachineTest.kt",
    "PhysicalGestureArbiterTest.kt",
    "GestureMachineTest.kt",
    "GestureMachineBehavioralStressTest.kt",
    "GestureSideEffectGateTest.kt",
    "GestureMachineIntegrationTest.kt",
    "ControlCenterGestureRuntimeHolderTest.kt",
}


class ContractScanner:
    """Static scanner for gesture source/test invariants.

    The scanner reads only the local working tree and is intentionally independent
    of git history, commit SHAs or Markdown documentation.
    """

    def __init__(self, repo_root: Path) -> None:
        self.repo_root = Path(repo_root)
        self.main_pkg = self.repo_root / "app/src/main/java/tv/withaibuild/customiuizer/mods/utils/gesture"
        self.test_pkg = self.repo_root / "app/src/test/java/tv/withaibuild/customiuizer/mods/utils/gesture"

    # ---------------------------------------------------------------------------------------------
    # helpers
    # ---------------------------------------------------------------------------------------------

    @staticmethod
    def _require(condition: bool, message: str) -> None:
        if not condition:
            raise AssertionError(message)

    def _main_text(self, name: str) -> str:
        path = self.main_pkg / name
        self._require(path.is_file(), f"Missing production source: {name}")
        return path.read_text(encoding="utf-8")

    def _test_text(self, name: str) -> str:
        path = self.test_pkg / name
        self._require(path.is_file(), f"Missing test source: {name}")
        return path.read_text(encoding="utf-8")

    @staticmethod
    def _declared_functions(text: str) -> set[str]:
        return set(re.findall(r"\bfun\s+(\w+)\s*\(", text))

    @staticmethod
    def _test_method_names(text: str) -> list[str]:
        """Return names of @Test-annotated methods in a Kotlin test file."""
        names = []
        for match in re.finditer(r"@Test\b", text):
            snippet = text[match.end() : match.end() + 300]
            found = re.search(r"\bfun\s+(\w+)\s*\(", snippet)
            if found:
                names.append(found.group(1))
        return names

    # ---------------------------------------------------------------------------------------------
    # production invariants
    # ---------------------------------------------------------------------------------------------

    def check_gesture_event_fields(self) -> None:
        """GestureEvent must carry the data boundary fields."""
        text = self._main_text("GestureEvent.kt")
        match = re.search(
            r"\bdata\s+class\s+GestureEvent\s*\((.*)^\s*\)\s*\{",
            text,
            re.DOTALL | re.MULTILINE,
        )
        self._require(match is not None, "GestureEvent data class not found")
        constructor = match.group(1)
        for field in ("actionMasked", "ownerId", "downTime", "eventTime"):
            self._require(
                re.search(rf"\bval\s+{re.escape(field)}\b", constructor) is not None,
                f"GestureEvent primary constructor missing boundary field: {field}",
            )

    def check_gesture_state(self) -> None:
        """GestureState must include at least IDLE and TRACKING."""
        text = self._main_text("GestureState.kt")
        match = re.search(
            r"\benum\s+class\s+GestureState\s*\{(.*)^\s*\}",
            text,
            re.DOTALL | re.MULTILINE,
        )
        self._require(match is not None, "GestureState enum not found")
        body = match.group(1)
        for const in ("IDLE", "TRACKING"):
            self._require(
                re.search(rf"\b{re.escape(const)}\b", body) is not None,
                f"GestureState missing enum constant: {const}",
            )

    def check_gesture_machine(self) -> None:
        """GestureMachine must expose prepare, process, dispatch and clear."""
        text = self._main_text("GestureMachine.kt")
        functions = self._declared_functions(text)
        for method in ("prepare", "dispatch", "clear"):
            self._require(
                method in functions,
                f"GestureMachine missing public method: fun {method}",
            )
        # The machine either has a local process() or delegates to GestureStateMachine.process.
        local_process = re.search(r"\bfun\s+process\s*\(", text) is not None
        delegated_process = re.search(r"\bGestureStateMachine\.process\s*\(", text) is not None
        self._require(
            local_process or delegated_process,
            "GestureMachine must provide a process path (fun process or GestureStateMachine.process call)",
        )

    def check_gesture_state_machine_outputs(self) -> None:
        """GestureStateMachine must emit the canonical tracking commands."""
        text = self._main_text("GestureStateMachine.kt")
        for command in ("BeginTracking", "TriggerDoubleTap", "TriggerLongPress"):
            self._require(
                re.search(rf"\bGestureCommand\.{re.escape(command)}\b", text) is not None,
                f"GestureStateMachine output missing: GestureCommand.{command}",
            )

    def check_physical_arbiter_release(self) -> None:
        """PhysicalGestureArbiter must provide the release path."""
        text = self._main_text("PhysicalGestureArbiter.kt")
        functions = self._declared_functions(text)
        for method in ("tryAcquireOnDown", "release", "releaseOwner", "releaseAll"):
            self._require(
                method in functions,
                f"PhysicalGestureArbiter missing release path: fun {method}",
            )

    def check_side_effect_gate(self) -> None:
        """GestureSideEffectGate must filter and deduplicate side effects per owner."""
        text = self._main_text("GestureSideEffectGate.kt")
        functions = self._declared_functions(text)
        for method in ("filter", "clearOwner"):
            self._require(
                method in functions,
                f"GestureSideEffectGate missing method: fun {method}",
            )
        self._require(
            re.search(r"\bOwnerFingerprint\b", text) is not None,
            "GestureSideEffectGate must define OwnerFingerprint for deduplication",
        )
        # Flexible match for the deduplication early-return.
        self._require(
            re.search(
                r"fp\s+in\s+seen.{0,40}return\s+emptyList\s*\(\s*\)",
                text,
                re.DOTALL,
            )
            is not None,
            "GestureSideEffectGate must deduplicate by returning emptyList for seen fingerprints",
        )

    def check_control_center_runtime_holder(self) -> None:
        """ControlCenterGestureRuntimeHolder must expose bind/unbind/activeRuntime."""
        text = self._main_text("ControlCenterGestureRuntimeHolder.kt")
        functions = self._declared_functions(text)
        for method in ("bind", "unbind", "activeRuntime"):
            self._require(
                method in functions,
                f"ControlCenterGestureRuntimeHolder missing method: fun {method}",
            )

    def check_clear_unbind_paths(self) -> None:
        """Clear and unbind cleanup paths must exist across the runtime."""
        machine_text = self._main_text("GestureMachine.kt")
        machine_functions = self._declared_functions(machine_text)
        self._require(
            "clear" in machine_functions,
            "GestureMachine missing cleanup method: fun clear",
        )

        holder_text = self._main_text("ControlCenterGestureRuntimeHolder.kt")
        holder_functions = self._declared_functions(holder_text)
        self._require(
            "unbind" in holder_functions,
            "ControlCenterGestureRuntimeHolder missing cleanup method: fun unbind",
        )

        gate_text = self._main_text("GestureSideEffectGate.kt")
        gate_functions = self._declared_functions(gate_text)
        self._require(
            "clear" in gate_functions,
            "GestureSideEffectGate missing cleanup method: fun clear",
        )
        self._require(
            "clearOwner" in gate_functions,
            "GestureSideEffectGate missing cleanup method: fun clearOwner",
        )

    # ---------------------------------------------------------------------------------------------
    # test coverage invariants
    # ---------------------------------------------------------------------------------------------

    def check_jvm_test_coverage(self) -> None:
        """The required JVM test files must exist and cover the named scenarios."""
        missing_files = [f for f in REQUIRED_TEST_FILES if not (self.test_pkg / f).is_file()]
        self._require(not missing_files, f"Missing required JVM test files: {missing_files}")

        method_names: list[str] = []
        for f in REQUIRED_TEST_FILES:
            text = self._test_text(f)
            self._require(
                re.search(r"@Test\b", text) is not None,
                f"Required test file {f} contains no @Test methods",
            )
            method_names.extend(self._test_method_names(text))

        categories = [
            (
                "duplicate side effect suppression",
                re.compile(
                    r"(?i)"
                    r"dedup|"
                    r"same.*(?:Event|Touch|Intercept)|"
                    r"duplicate|"
                    r"(?:volume|brightness)Executes.*Once|"
                    r"stateOnlyChangesOnce|"
                    r"brightnessCommits.*Once"
                ),
            ),
            (
                "cancel/clear",
                re.compile(r"(?i)cancel|clear|unbind|detach"),
            ),
            (
                "owner replacement",
                re.compile(
                    r"(?i)"
                    r"replace|"
                    r"ownerChange|"
                    r"newLoader|"
                    r"newValidDown|"
                    r"clearsOldMachine"
                ),
            ),
            (
                "detach/unbind",
                re.compile(r"(?i)unbind|detach"),
            ),
            (
                "repeated loader replacement bounded state",
                re.compile(
                    r"(?i)"
                    r"repeated(?:Loader|Attach).*doesNotGrow|"
                    r"doesNotGrowMaps"
                ),
            ),
        ]

        for label, pattern in categories:
            self._require(
                any(pattern.search(name) for name in method_names),
                f"Missing test coverage for: {label}",
            )


class GestureEventContractTest(unittest.TestCase):
    """Run the source/test mechanical contract and mutation tests."""

    @classmethod
    def setUpClass(cls) -> None:
        cls.scanner = ContractScanner(REPO_ROOT)

    # ---- positive source invariants ----

    def test_01_gesture_event_data_fields(self) -> None:
        self.scanner.check_gesture_event_fields()

    def test_02_gesture_state_idle_tracking(self) -> None:
        self.scanner.check_gesture_state()

    def test_03_gesture_machine_provides_process_path(self) -> None:
        self.scanner.check_gesture_machine()

    def test_04_gesture_state_machine_outputs(self) -> None:
        self.scanner.check_gesture_state_machine_outputs()

    def test_05_physical_arbiter_release_path(self) -> None:
        self.scanner.check_physical_arbiter_release()

    def test_06_side_effect_gate_filter_and_dedup(self) -> None:
        self.scanner.check_side_effect_gate()

    def test_07_control_center_runtime_holder_lifecycle(self) -> None:
        self.scanner.check_control_center_runtime_holder()

    def test_08_clear_unbind_paths(self) -> None:
        self.scanner.check_clear_unbind_paths()

    # ---- positive test coverage invariants ----

    def test_09_jvm_tests_exist_and_cover_contract(self) -> None:
        self.scanner.check_jvm_test_coverage()

    # ---- mutation tests ----

    def _mutated_scanner(self, main_changes: dict[str, list[tuple[str, str]]]) -> ContractScanner:
        """Copy the gesture package into a temp directory and apply source mutations."""
        temp_dir = tempfile.TemporaryDirectory()
        self.addCleanup(temp_dir.cleanup)
        root = Path(temp_dir.name)

        main_dst = root / "app/src/main/java/tv/withaibuild/customiuizer/mods/utils/gesture"
        main_dst.mkdir(parents=True, exist_ok=True)
        for src in self.scanner.main_pkg.glob("*.kt"):
            shutil.copy2(src, main_dst / src.name)

        test_dst = root / "app/src/test/java/tv/withaibuild/customiuizer/mods/utils/gesture"
        test_dst.mkdir(parents=True, exist_ok=True)
        for src in self.scanner.test_pkg.glob("*.kt"):
            shutil.copy2(src, test_dst / src.name)

        for filename, replacements in main_changes.items():
            path = main_dst / filename
            text = path.read_text(encoding="utf-8")
            for old, new in replacements:
                text = text.replace(old, new)
            path.write_text(text, encoding="utf-8")

        return ContractScanner(root)

    def test_10_mutation_release_path_removed(self) -> None:
        """If PhysicalGestureArbiter release methods are removed, the gate must fail."""
        scanner = self._mutated_scanner(
            {
                "PhysicalGestureArbiter.kt": [
                    ("fun release(", "fun __removed_release("),
                    ("fun releaseOwner(", "fun __removed_releaseOwner("),
                    ("fun releaseAll(", "fun __removed_releaseAll("),
                ],
            }
        )
        with self.assertRaises(AssertionError):
            scanner.check_physical_arbiter_release()

    def test_11_mutation_clear_unbind_removed(self) -> None:
        """If clear/unbind cleanup methods are removed, the gate must fail."""
        scanner = self._mutated_scanner(
            {
                "GestureMachine.kt": [("fun clear(", "fun __removed_clear(")],
                "ControlCenterGestureRuntimeHolder.kt": [("fun unbind()", "fun __removed_unbind()")],
            }
        )
        with self.assertRaises(AssertionError):
            scanner.check_clear_unbind_paths()

    def test_12_mutation_dedup_evidence_removed(self) -> None:
        """If the side-effect de-duplication return is removed, the gate must fail."""
        scanner = self._mutated_scanner(
            {
                "GestureSideEffectGate.kt": [
                    ("if (fp in seen) return emptyList()", "// DEDUP_REMOVED"),
                ],
            }
        )
        with self.assertRaises(AssertionError):
            scanner.check_side_effect_gate()


if __name__ == "__main__":
    unittest.main()
