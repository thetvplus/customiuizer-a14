#!/usr/bin/env python3
"""Mechanical invariants for A14 process routing and feature reachability.

This test replaces the old deliverable audit that read deleted docs
(A14_PROCESS_EXCEPTIONS.md, A14_FEATURE_RETIREMENT.md/.csv) with direct
source-level checks on installers, the process router, and the feature registry.
"""

from __future__ import annotations

import re
import unittest
from pathlib import Path

from tools.extract_process_matrix import (
    FEATURE_DIR,
    extract_class_body,
    parse_feature_ids,
    parse_lazy_specs,
)

REPO_ROOT = Path(__file__).resolve().parents[2]
APP_ROOT = REPO_ROOT / "app" / "src" / "main" / "java"
PACKAGE_ROOT = APP_ROOT / "tv" / "withaibuild" / "customiuizer"
INSTALLERS_DIR = PACKAGE_ROOT / "installers"
MODS_UTILS_DIR = PACKAGE_ROOT / "mods" / "utils"
MAIN_MODULE = PACKAGE_ROOT / "MainModule.java"

FEATURE_TARGET_KT = MODS_UTILS_DIR / "FeatureTarget.kt"
INSTALL_PHASE_KT = MODS_UTILS_DIR / "InstallPhase.kt"
LAZY_FEATURE_SPEC_KT = MODS_UTILS_DIR / "LazyFeatureSpec.kt"
FEATURE_INSTALL_REGISTRY_KT = MODS_UTILS_DIR / "FeatureInstallRegistry.kt"


RETIRED_DOCS = (
    "A14_PROCESS_EXCEPTIONS.md",
    "A14_FEATURE_RETIREMENT.md",
    "A14_FEATURE_RETIREMENT.csv",
)


def _read_texts(
    *paths: Path,
    dirs: list[Path] | None = None,
    suffixes: tuple[str, ...] = (".kt", ".java"),
    exclude: set[str] | None = None,
) -> str:
    chunks: list[str] = []
    exclude = exclude or set()
    if dirs:
        for d in dirs:
            for p in d.rglob("*"):
                if p.is_file() and p.suffix in suffixes and p.name not in exclude:
                    chunks.append(p.read_text(encoding="utf-8"))
    for p in paths:
        chunks.append(p.read_text(encoding="utf-8"))
    return "\n".join(chunks)


def _enum_values(path: Path, enum_name: str) -> set[str]:
    text = path.read_text(encoding="utf-8")
    body_match = re.search(
        rf"enum class {re.escape(enum_name)} \{{(.*?)\}}", text, re.DOTALL
    )
    if not body_match:
        return set()
    body = re.sub(r"/\*.*?\*/", "", body_match.group(1), flags=re.DOTALL)
    values: set[str] = set()
    for line in body.splitlines():
        token = line.strip().rstrip(",")
        if re.fullmatch(r"[A-Z][A-Z_0-9]*", token):
            values.add(token)
    return values


def _all_lazy_specs() -> list[dict[str, str]]:
    specs: list[dict[str, str]] = []
    for f in sorted(FEATURE_DIR.glob("*Features.kt")):
        if f.name == "FeatureIds.kt":
            continue
        text = f.read_text(encoding="utf-8")
        for spec in parse_lazy_specs(text):
            spec["file"] = f.name
            specs.append(spec)
    return specs


def _for_loop_ranges(text: str) -> list[tuple[int, int, str]]:
    """Return (start, end, loop_variable) ranges for every braced for-loop body."""
    ranges: list[tuple[int, int, str]] = []
    for m in re.finditer(r"\bfor\s*\(", text):
        # Walk the for-header parentheses to its matching ')'.
        header_start = m.end() - 1
        depth = 0
        i = header_start
        while i < len(text):
            if text[i] == "(":
                depth += 1
            elif text[i] == ")":
                depth -= 1
            if depth == 0:
                header_end = i
                break
            i += 1
        else:
            continue

        header = text[m.end():header_end]
        vm = re.search(r"\b(?:val\s+)?(\w+)", header)
        loop_var = vm.group(1) if vm else ""

        # The for body is either a braced block or a single statement.
        j = header_end + 1
        while j < len(text) and text[j].isspace():
            j += 1
        if j < len(text) and text[j] == "{":
            brace = 1
            j += 1
            while j < len(text) and brace > 0:
                if text[j] == "{":
                    brace += 1
                elif text[j] == "}":
                    brace -= 1
                j += 1
            ranges.append((m.start(), j, loop_var))
        else:
            k = j
            while k < len(text) and text[k] not in "\n;":
                k += 1
            ranges.append((m.start(), k, loop_var))
    return ranges


def _is_inside_for_loop(
    text: str,
    pos: int,
    var_name: str,
    ranges: list[tuple[int, int, str]],
) -> bool:
    for start, end, loop_var in ranges:
        if start < pos < end and loop_var == var_name:
            return True
    return False


def _extract_direct_spec(text: str, class_name: str) -> dict[str, str] | None:
    """Return id/target/phase dict if class_name is a FeatureDefinition in text."""
    cls_match = re.search(
        rf"\binternal class\s+{re.escape(class_name)}\b",
        text,
    )
    if not cls_match:
        return None

    # Find the opening brace that begins the class body.
    brace_open = text.find("{", cls_match.end())
    if brace_open == -1:
        return None

    # The superclass/interface list is after the last ':' before the body '{'.
    # Kotlin class headers may have parameter type colons (e.g. 'private val x: T'),
    # so we use the final ':' that separates the header from the supertypes.
    supertype_colon = text.rfind(":", cls_match.start(), brace_open)
    if supertype_colon == -1:
        return None
    supertype_region = text[supertype_colon:brace_open]
    if "FeatureDefinition" not in supertype_region:
        return None

    body = extract_class_body(text, class_name)
    if not body:
        return None
    id_m = re.search(r"override val id\s*=\s*(\w+FeatureId)", body)
    target_m = re.search(r"override val target\s*=\s*FeatureTarget\.(\w+)", body)
    phase_m = re.search(r"override val phase\s*=\s*InstallPhase\.(\w+)", body)
    if not (id_m and target_m and phase_m):
        return None
    return {
        "class": class_name,
        "id": id_m.group(1),
        "target": target_m.group(1),
        "phase": phase_m.group(1),
    }


def _resolve_variable_constructor(
    text: str,
    var_name: str,
    register_pos: int,
) -> str | None:
    """Resolve a registered variable to the concrete FeatureDefinition class it holds.

    Conservative rules to avoid fabricating direct FeatureSpecs:
    - the variable is declared with a constructor call, e.g. `val x = SomeFeature(...)`
    - or with a concrete type and a factory-style rhs, e.g. `val x: SomeFeature = create(...)`
    - if multiple same-name declarations exist anywhere in the source, they must all
      resolve to the same class; otherwise the variable name is overloaded and we skip
    - the chosen declaration must precede the registry.register() call
    - variables used as for-loop iterators are never resolved here (filtered earlier)
    """
    decl_pattern = re.compile(
        rf"\b(?:val|var)\s+{re.escape(var_name)}\b"
        rf"(?:\s*:\s*(\w+)(?:\s*\?)?\s*)?"
        rf"\s*=\s*(\w+)\s*\("
    )
    all_matches = list(decl_pattern.finditer(text))
    if not all_matches:
        return None

    # Same-name local variables in different scopes are only safe if every declaration
    # points to the same concrete class. If classes differ, the source is ambiguous.
    constructor_classes = {m.group(2) for m in all_matches}
    declared_types = {m.group(1) for m in all_matches if m.group(1)}
    if len(constructor_classes) > 1:
        return None

    # Use the last declaration that precedes the registration.
    preceding = [m for m in all_matches if m.start() < register_pos]
    if not preceding:
        return None
    match = preceding[-1]

    # Prefer the concrete constructor class; fall back to the explicit type if the
    # rhs is a factory/function that returns an instance of a known FeatureDefinition.
    constructor_class = match.group(2)
    declared_type = match.group(1)
    if constructor_class and _extract_direct_spec(text, constructor_class):
        return constructor_class
    if declared_type and declared_type != constructor_class:
        if _extract_direct_spec(text, declared_type):
            return declared_type
    return constructor_class


def _direct_specs_from_text(text: str, filename: str) -> list[dict[str, str]]:
    """Find direct FeatureDefinition registrations in a single Kotlin source."""
    direct: list[dict[str, str]] = []
    for_ranges = _for_loop_ranges(text)

    # A. inline direct feature: registry.register(SomeFeature(...))
    for m in re.finditer(r"registry\.register\((\w+)(?=\s*\()", text):
        class_name = m.group(1)
        spec = _extract_direct_spec(text, class_name)
        if spec:
            spec["file"] = filename
            direct.append(spec)

    # B. local-variable direct feature: val x = SomeFeature(...); registry.register(x)
    for m in re.finditer(r"registry\.register\((\w+)(?!\s*\()\s*\)", text):
        var_name = m.group(1)
        if _is_inside_for_loop(text, m.start(), var_name, for_ranges):
            continue
        class_name = _resolve_variable_constructor(text, var_name, m.start())
        if not class_name:
            continue
        spec = _extract_direct_spec(text, class_name)
        if spec:
            spec["file"] = filename
            direct.append(spec)

    return direct


def _direct_feature_specs() -> list[dict[str, str]]:
    """Find FeatureDefinitions that are registered directly (not via LazyFeatureSpec)."""
    direct: list[dict[str, str]] = []
    for p in PACKAGE_ROOT.rglob("*.kt"):
        text = p.read_text(encoding="utf-8")
        direct.extend(_direct_specs_from_text(text, p.name))
    return direct


class ProcessRoutingContractTest(unittest.TestCase):
    """A. Process routing contract — source coverage, not docs."""

    @classmethod
    def setUpClass(cls) -> None:
        cls.source = _read_texts(MAIN_MODULE, dirs=[INSTALLERS_DIR, MODS_UTILS_DIR])

    def test_required_process_domains_are_covered(self) -> None:
        required: list[tuple[str, list[str]]] = [
            (
                "SystemUI",
                [
                    "com.android.systemui",
                    "ProcessScope.SYSTEM_UI",
                    "FeatureTarget.SYSTEM_UI",
                    "SystemUiInstaller",
                ],
            ),
            (
                "Launcher",
                [
                    "com.miui.home",
                    "ProcessScope.LAUNCHER",
                    "FeatureTarget.LAUNCHER",
                    "LauncherInstaller",
                ],
            ),
            (
                "SecurityCenter",
                [
                    "com.miui.securitycenter",
                    "ProcessScope.SECURITY_CENTER_MAIN",
                    "SecurityCenterInstaller",
                ],
            ),
            (
                "Settings",
                [
                    "com.android.settings",
                    "ProcessScope.SETTINGS_MAIN",
                    "SettingsInstaller",
                ],
            ),
            (
                "PowerKeeper",
                [
                    "com.miui.powerkeeper",
                    "ProcessScope.POWER_KEEPER",
                    "PowerKeeperInstaller",
                ],
            ),
            (
                "Wallpaper",
                [
                    "com.miui.miwallpaper",
                    "ProcessScope.WALLPAPER",
                    "MediaInstaller",
                ],
            ),
            (
                "NetworkStack",
                [
                    "com.android.networkstack",
                    "ProcessScope.NETWORK_STACK",
                ],
            ),
            (
                "Input method",
                [
                    "ProcessScope.INPUT_METHOD",
                    "InputMethodInstaller",
                    "com.baidu.input",
                ],
            ),
            (
                "Generic application",
                [
                    "ProcessScope.GENERIC_APP",
                    "GenericAppInstaller",
                ],
            ),
        ]
        for name, tokens in required:
            with self.subTest(process=name):
                self.assertTrue(
                    any(token in self.source for token in tokens),
                    f"{name} is not covered by any installer, router, or "
                    "FeatureId/registry source",
                )


class FeatureReachabilityContractTest(unittest.TestCase):
    """B. Feature reachability contract — source invariants, not retired docs."""

    @classmethod
    def setUpClass(cls) -> None:
        cls.feature_ids = parse_feature_ids()
        cls.lazy_specs = _all_lazy_specs()
        cls.direct_specs = _direct_feature_specs()
        cls.valid_targets = _enum_values(FEATURE_TARGET_KT, "FeatureTarget")
        cls.valid_phases = _enum_values(INSTALL_PHASE_KT, "InstallPhase")

    def test_feature_ids_are_unique(self) -> None:
        ints = [meta["featureIdInt"] for meta in self.feature_ids.values()]
        names = [meta["featureIdName"] for meta in self.feature_ids.values()]
        self.assertEqual(
            len(ints), len(set(ints)), "Duplicate FeatureId integer found"
        )
        self.assertEqual(
            len(names), len(set(names)), "Duplicate FeatureId name found"
        )

    def test_all_feature_specs_have_explicit_target_and_phase(self) -> None:
        missing: list[str] = []
        for spec in self.lazy_specs:
            target = spec.get("target", "")
            phase = spec.get("phase", "")
            if not target or not phase:
                missing.append(f"{spec['file']}: {spec.get('id', '?')}")
            if target and target not in self.valid_targets:
                self.fail(
                    f"{spec['file']} {spec.get('id', '?')} has invalid target {target!r}"
                )
            if phase and phase not in self.valid_phases:
                self.fail(
                    f"{spec['file']} {spec.get('id', '?')} has invalid phase {phase!r}"
                )
        self.assertFalse(missing, "LazyFeatureSpec missing target or phase: " + str(missing))

        for spec in self.direct_specs:
            self.assertIn(
                spec["target"],
                self.valid_targets,
                f"{spec['file']} {spec['class']} has invalid target {spec['target']!r}",
            )
            self.assertIn(
                spec["phase"],
                self.valid_phases,
                f"{spec['file']} {spec['class']} has invalid phase {spec['phase']!r}",
            )

    def test_enabled_false_does_not_create_definition(self) -> None:
        lazy_text = LAZY_FEATURE_SPEC_KT.read_text(encoding="utf-8")
        # LazyFeatureSpec only stores metadata and lightweight lambdas.
        self.assertIn(
            "override fun isEnabled(prefs: PrefMap): Boolean = enabled(prefs)",
            lazy_text,
        )
        self.assertIn(
            "override fun create(): FeatureDefinition = factory()",
            lazy_text,
        )

        registry_text = FEATURE_INSTALL_REGISTRY_KT.read_text(encoding="utf-8")
        self.assertIn("if (!spec.isEnabled(prefs))", registry_text)
        self.assertIn("return FeatureInstallResult.SKIPPED", registry_text)

        # Every factory at every LazyFeatureSpec call site is a lambda, so the
        # FeatureDefinition is only constructed when create() runs.
        for spec in self.lazy_specs:
            factory = spec.get("factory", "").strip()
            self.assertTrue(
                factory.startswith("{") and factory.endswith("}"),
                f"{spec['file']} factory must be a lambda, got {factory!r}",
            )

    def test_no_feature_id_is_registered_but_never_routed(self) -> None:
        lazy_ids = {spec["id"] for spec in self.lazy_specs}
        direct_ids = {spec["id"] for spec in self.direct_specs}
        used_ids = lazy_ids | direct_ids
        all_ids = set(self.feature_ids.keys())
        self.assertSetEqual(
            used_ids,
            all_ids,
            "Every FeatureId must be used in a FeatureSpec and every "
            "FeatureSpec must map to a known FeatureId",
        )

        # Each *Features.kt object with specs must be called by an installer.
        source = _read_texts(dirs=[PACKAGE_ROOT])
        called = set(re.findall(r"(\w+Features)\.(?:all|selected)\(", source))
        for f in sorted(FEATURE_DIR.glob("*Features.kt")):
            if f.name == "FeatureIds.kt":
                continue
            if parse_lazy_specs(f.read_text(encoding="utf-8")):
                self.assertIn(
                    f.stem,
                    called,
                    f"{f.name} declares FeatureSpecs but is not routed by any installer",
                )

    def test_no_two_feature_specs_reuse_feature_id(self) -> None:
        ids = [spec["id"] for spec in self.lazy_specs]
        for spec in self.direct_specs:
            ids.append(spec["id"])

        seen: dict[str, int] = {}
        for fid in ids:
            seen[fid] = seen.get(fid, 0) + 1
        duplicates = [fid for fid, count in seen.items() if count > 1]
        self.assertFalse(
            duplicates,
            f"FeatureIds reused by more than one FeatureSpec: {duplicates}",
        )

    def test_retired_docs_not_used_as_runtime_fact_source(self) -> None:
        # Scan Kotlin/Java source, Python tooling and JSON configs, but skip
        # this test file because it legitimately names the retired artifacts.
        source = _read_texts(
            dirs=[APP_ROOT, REPO_ROOT / "tools"],
            suffixes=(".kt", ".java", ".py", ".json"),
            exclude={"test_audit_deliverables.py"},
        )
        for name in RETIRED_DOCS:
            self.assertNotIn(
                name,
                source,
                f"Source still references deleted runtime-fact doc {name}",
            )


class TestDirectFeatureSpecParser(unittest.TestCase):
    """C. Parser regression tests for direct FeatureDefinition registration."""

    def _assert_single_spec(self, specs, expected_class, expected_id, file="test.kt"):
        self.assertEqual(len(specs), 1, f"expected exactly one direct spec, got {specs}")
        spec = specs[0]
        self.assertEqual(spec["file"], file)
        self.assertEqual(spec["class"], expected_class)
        self.assertEqual(spec["id"], expected_id)
        self.assertIn("target", spec)
        self.assertIn("phase", spec)

    def _assert_no_spec(self, specs):
        self.assertEqual(len(specs), 0, f"expected no direct specs, got {specs}")

    def test_inline_direct_feature(self) -> None:
        source = '''
internal class InlineFeatureId : FeatureId { override val id = 1 override val name = "" }
internal class InlineFeature(
    private val lp: Any,
    private val prefs: Any
) : FeatureDefinition {
    override val id = InlineFeatureId
    override val name = "Inline feature"
    override val target = FeatureTarget.SYSTEM_SERVER
    override val phase = InstallPhase.SYSTEM_SERVER_STARTING
    override fun isEnabled(prefs: Any) = true
    override fun install(): Any = Any()
}
fun install() {
    val registry = FeatureInstallRegistry()
    registry.register(InlineFeature(lp, prefs))
}
'''
        specs = _direct_specs_from_text(source, "inline.kt")
        self._assert_single_spec(specs, "InlineFeature", "InlineFeatureId", "inline.kt")
        self.assertEqual(specs[0]["target"], "SYSTEM_SERVER")
        self.assertEqual(specs[0]["phase"], "SYSTEM_SERVER_STARTING")

    def test_variable_direct_feature(self) -> None:
        source = '''
internal class VariableFeatureId : FeatureId { override val id = 2 override val name = "" }
internal class VariableFeature(
    private val lp: Any,
    private val prefs: Any
) : FeatureDefinition {
    override val id = VariableFeatureId
    override val name = "Variable feature"
    override val target = FeatureTarget.SYSTEM_UI
    override val phase = InstallPhase.PACKAGE_READY
    override fun isEnabled(prefs: Any) = true
    override fun install(): Any = Any()
}
fun install() {
    val registry = FeatureInstallRegistry()
    val feature = VariableFeature(lp, prefs)
    registry.register(feature)
}
'''
        specs = _direct_specs_from_text(source, "variable.kt")
        self._assert_single_spec(specs, "VariableFeature", "VariableFeatureId", "variable.kt")
        self.assertEqual(specs[0]["target"], "SYSTEM_UI")
        self.assertEqual(specs[0]["phase"], "PACKAGE_READY")

    def test_typed_variable_direct_feature(self) -> None:
        source = '''
internal class TypedFeatureId : FeatureId { override val id = 3 override val name = "" }
internal class TypedFeature(
    private val lp: Any,
    private val prefs: Any
) : FeatureDefinition {
    override val id = TypedFeatureId
    override val target = FeatureTarget.SYSTEM_SERVER
    override val phase = InstallPhase.SYSTEM_SERVER_STARTING
    override fun isEnabled(prefs: Any) = true
    override fun install(): Any = Any()
}
fun install() {
    val registry = FeatureInstallRegistry()
    val feature: TypedFeature = TypedFeature(lp, prefs)
    registry.register(feature)
}
'''
        specs = _direct_specs_from_text(source, "typed.kt")
        self._assert_single_spec(specs, "TypedFeature", "TypedFeatureId", "typed.kt")

    def test_factory_typed_variable_is_resolved_to_concrete_feature(self) -> None:
        source = '''
internal class FactoryFeatureId : FeatureId { override val id = 4 override val name = "" }
internal class FactoryFeature(
    private val lp: Any,
    private val prefs: Any
) : FeatureDefinition {
    override val id = FactoryFeatureId
    override val target = FeatureTarget.SYSTEM_SERVER
    override val phase = InstallPhase.SYSTEM_SERVER_STARTING
    override fun isEnabled(prefs: Any) = true
    override fun install(): Any = Any()
}
fun createFactoryFeature(lp: Any, prefs: Any): FactoryFeature = FactoryFeature(lp, prefs)
fun install() {
    val registry = FeatureInstallRegistry()
    val feature: FactoryFeature = createFactoryFeature(lp, prefs)
    registry.register(feature)
}
'''
        specs = _direct_specs_from_text(source, "factory.kt")
        self._assert_single_spec(specs, "FactoryFeature", "FactoryFeatureId", "factory.kt")

    def test_arbitrary_non_feature_variable_is_not_fabricated(self) -> None:
        source = '''
internal class SomethingElse(
    private val lp: Any
) {
    fun doIt() {}
}
fun install() {
    val registry = FeatureInstallRegistry()
    val thing = SomethingElse(lp)
    registry.register(thing)
}
'''
        specs = _direct_specs_from_text(source, "arbitrary.kt")
        self._assert_no_spec(specs)

    def test_for_loop_variable_is_not_treated_as_direct_feature(self) -> None:
        source = '''
internal class FeatureSpec(
    val id: Any,
    val target: Any,
    val phase: Any
)
fun install(features: List<FeatureSpec>) {
    val registry = FeatureInstallRegistry()
    for (feature: FeatureSpec in features) {
        registry.register(feature)
    }
}
'''
        specs = _direct_specs_from_text(source, "loop.kt")
        self._assert_no_spec(specs)

    def test_same_name_variable_in_different_scopes_is_not_confused(self) -> None:
        source = '''
internal class OneFeatureId : FeatureId { override val id = 10 override val name = "" }
internal class OneFeature(
    private val lp: Any
) : FeatureDefinition {
    override val id = OneFeatureId
    override val target = FeatureTarget.SYSTEM_SERVER
    override val phase = InstallPhase.SYSTEM_SERVER_STARTING
    override fun isEnabled(prefs: Any) = true
    override fun install(): Any = Any()
}
internal class OtherThing(
    private val lp: Any
) {
    fun doIt() {}
}
fun one() {
    val registry = FeatureInstallRegistry()
    val thing = OneFeature(lp)
    registry.register(thing)
}
fun other() {
    val thing = OtherThing(lp)
    // no registration here
}
'''
        specs = _direct_specs_from_text(source, "same.kt")
        # The parser sees two `val thing` declarations with different classes and must
        # not fabricate a spec for the registered `thing` in `one()`.
        self._assert_no_spec(specs)

    def test_lookalike_with_feature_fields_but_not_feature_definition_is_rejected(self) -> None:
        source = '''
internal class LookalikeFeatureId : FeatureId { override val id = 99 override val name = "" }
internal class Lookalike(
    private val lp: Any
) : NotFeature {
    override val id = LookalikeFeatureId
    override val target = FeatureTarget.SYSTEM_SERVER
    override val phase = InstallPhase.SYSTEM_SERVER_STARTING
}
fun install() {
    val registry = FeatureInstallRegistry()
    registry.register(Lookalike(lp))
}
'''
        specs = _direct_specs_from_text(source, "lookalike.kt")
        self._assert_no_spec(specs)

    def test_package_permissions_feature_is_reachable_in_repository(self) -> None:
        """CASE D: the real repository regression that triggered C-AUDIT-CORRECTIVE-3."""
        specs = _direct_feature_specs()
        direct_ids = {spec["id"] for spec in specs}
        self.assertIn(
            "PackagePermissionsFeatureId",
            direct_ids,
            "PackagePermissionsFeatureId must be recognized as a direct FeatureDefinition",
        )


if __name__ == "__main__":
    unittest.main()
