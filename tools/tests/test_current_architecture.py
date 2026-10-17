#!/usr/bin/env python3
"""Mechanical validation of the current A14 source architecture.

This test replaces the previous markdown-backed architecture evidence with
direct source and JVM-test parsing. It does not read or require
docs/A14_CURRENT_ARCHITECTURE.md and does not modify app/src/main.
"""

from __future__ import annotations

import importlib.util
import re
import sys
import types
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
SOURCE_ROOT = REPO_ROOT / "app" / "src" / "main" / "java"
TEST_ROOT = REPO_ROOT / "app" / "src" / "test" / "java"
RESOURCES_ROOT = REPO_ROOT / "app" / "src" / "main" / "resources"
CHECK_INVARIANTS = REPO_ROOT / "tools" / "check-invariants.py"
SOURCE_HAZARD_SCAN = REPO_ROOT / "tools" / "source_hazard_scan.py"


def _load_module(name: str, path: Path) -> types.ModuleType:
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    if spec.loader is not None:
        spec.loader.exec_module(module)
    return module


CI = _load_module("check_invariants", CHECK_INVARIANTS)
SHS = _load_module("source_hazard_scan", SOURCE_HAZARD_SCAN)


def source(rel: str) -> str:
    """Return the text of a repo-relative source file under app/src/main/java."""
    path = SOURCE_ROOT / rel
    if not path.is_file():
        path = REPO_ROOT / rel
    return path.read_text(encoding="utf-8")


def test_source(rel: str) -> str:
    return (TEST_ROOT / rel).read_text(encoding="utf-8")


def _method_body(text: str, header_pattern: str) -> str:
    """Extract the brace-balanced body of a method starting with header_pattern."""
    m = re.search(header_pattern, text)
    if not m:
        raise AssertionError(f"Method header {header_pattern!r} not found")
    open_brace = text.find("{", m.start())
    if open_brace == -1:
        raise AssertionError(f"Method body start not found for {header_pattern!r}")
    end = SHS.find_block_end(text, open_brace)
    if end == -1:
        raise AssertionError(f"Method body end not found for {header_pattern!r}")
    return text[open_brace : end + 1]


class CurrentArchitectureTest(unittest.TestCase):
    """Verify the source-mechanical architecture invariants for A14."""

    def test_main_module_is_xposed_entry_point_and_routes(self) -> None:
        raw = source("tv/withaibuild/customiuizer/MainModule.java")
        text = CI.strip_comments(raw)
        self.assertRegex(text, r"public\s+class\s+MainModule\s+extends\s+XposedModule\b")
        self.assertIn("public void onModuleLoaded(", text)
        self.assertIn("public void onSystemServerStarting(", text)
        self.assertIn("public void onPackageReady(", text)

        on_loaded = _method_body(text, r"public\s+void\s+onModuleLoaded\s*\(")
        self.assertIn("XposedApiCapabilities.initialize(getApiVersion())", on_loaded)
        self.assertIn("PreferenceBootstrap.create(", on_loaded)

        on_server = _method_body(text, r"public\s+void\s+onSystemServerStarting\s*\(")
        self.assertIn("initPrefs()", on_server)
        self.assertIn("SystemServerInstaller.install(", on_server)
        self.assertLess(
            on_server.find("initPrefs()"),
            on_server.find("SystemServerInstaller.install("),
            "preference snapshot must be ready before system_server hooks install",
        )

        on_ready = _method_body(text, r"public\s+void\s+onPackageReady\s*\(")
        self.assertIn("ProcessRouter.resolve(pkg, processName)", on_ready)
        self.assertIn("scope.isInstallable()", on_ready)
        self.assertIn("initPrefs()", on_ready)

        installer_calls = [
            "AndroidPackageInstaller.install(",
            "InputMethodInstaller.install(",
            "FeatureInstallRegistry()",
            "MediaInstaller.install(",
            "SystemUiBootstrapCoordinator.install(",
            "GuardProviderInstaller.install(",
            "PhoneInstaller.install(",
            "SecurityCenterInstaller.install(",
            "PowerKeeperInstaller.install(",
            "SettingsInstaller.install(",
            "PackageInstallerRouter.install(",
            "LauncherInstaller.install(",
            "GenericAppInstaller.installPostAttach(",
        ]
        init_idx = on_ready.find("initPrefs()")
        self.assertGreaterEqual(init_idx, 0)
        for token in installer_calls:
            idx = on_ready.find(token)
            self.assertGreaterEqual(idx, 0, f"{token} missing from MainModule.onPackageReady")
            self.assertLess(init_idx, idx, f"initPrefs() must precede {token}")

        init_list = (
            RESOURCES_ROOT / "META-INF" / "xposed" / "java_init.list"
        ).read_text().strip()
        self.assertEqual("tv.withaibuild.customiuizer.MainModule", init_list)

        module_prop = (RESOURCES_ROOT / "META-INF" / "xposed" / "module.prop").read_text()
        self.assertIn("minApiVersion=101", module_prop)
        self.assertIn("targetApiVersion=102", module_prop)

        jvm_test = test_source("tv/withaibuild/customiuizer/MainModuleSystemServerLoadMarkerTest.kt")
        self.assertIn("systemServerLoadMarkerIsLoggedBeforeAnyHookInstallation", jvm_test)

    def test_process_router_resolves_process_scope(self) -> None:
        text = CI.strip_comments(
            source("tv/withaibuild/customiuizer/mods/utils/ProcessRouter.kt")
        )
        self.assertIn("object ProcessRouter", text)
        self.assertIn("@JvmStatic", text)
        self.assertIn("fun resolve(", text)
        self.assertIn("ProcessScope", text)

        body = _method_body(text, r"\bfun\s+resolve\s*\(")
        for scope in [
            "ProcessScope.SYSTEM_SERVER",
            "ProcessScope.SYSTEM_UI",
            "ProcessScope.LAUNCHER",
            "ProcessScope.SETTINGS_MAIN",
            "ProcessScope.SECURITY_CENTER_MAIN",
            "ProcessScope.POWER_KEEPER",
            "ProcessScope.GUARD_PROVIDER",
            "ProcessScope.WALLPAPER",
            "ProcessScope.MEDIA",
            "ProcessScope.PHONE",
            "ProcessScope.PACKAGE_INSTALLER",
            "ProcessScope.INPUT_METHOD",
            "ProcessScope.GENERIC_APP",
        ]:
            self.assertIn(scope, body, f"ProcessRouter must resolve {scope}")

        scope_text = CI.strip_comments(
            source("tv/withaibuild/customiuizer/mods/utils/ProcessScope.kt")
        )
        self.assertIn("enum class ProcessScope", scope_text)
        self.assertIn("val isInstallable: Boolean", scope_text)

        jvm_test = test_source("tv/withaibuild/customiuizer/mods/utils/ProcessRouterTest.kt")
        self.assertIn("resolvesPrimaryProcesses", jvm_test)

    def test_package_installer_router_exists_and_routes(self) -> None:
        text = CI.strip_comments(
            source("tv/withaibuild/customiuizer/installers/PackageInstallerRouter.kt")
        )
        self.assertIn("object PackageInstallerRouter", text)
        self.assertIn("@JvmStatic", text)
        self.assertIn("fun install(", text)
        body = _method_body(text, r"\bfun\s+install\s*\(")
        self.assertIn("FeatureInstallRegistry()", body)
        self.assertIn("PackageInstallerFeatures.all(", body)
        self.assertIn("registry.installAll(", body)
        self.assertIn("FeatureTarget.SYSTEM_PACKAGE", body)
        self.assertIn("InstallPhase.PACKAGE_READY", body)

    def test_explicit_installer_classes_exist_with_jvm_abi(self) -> None:
        installers = [
            {
                "rel": "tv/withaibuild/customiuizer/mods/utils/SystemServerInstaller.kt",
                "object": "SystemServerInstaller",
                "methods": [
                    {
                        "pattern": r"\bfun\s+install\s*\(",
                        "checks": [
                            "FeatureInstallRegistry()",
                            "FeatureTarget.SYSTEM_SERVER",
                            "InstallPhase.SYSTEM_SERVER_STARTING",
                            "MainModule.mPrefs",
                            "PackagePermissionsFeature(",
                            "SystemServerFeatures.all(",
                            "registry.installAll(",
                        ],
                    },
                ],
            },
            {
                "rel": "tv/withaibuild/customiuizer/installers/SystemUiInstaller.kt",
                "object": "SystemUiInstaller",
                "methods": [
                    {
                        "pattern": r"\bfun\s+install\s*\(",
                        "checks": [
                            "FeatureInstallRegistry()",
                            "FeatureTarget.SYSTEM_UI",
                            "InstallPhase.PACKAGE_READY",
                            "SystemUiFeatures.all(",
                            "registry.installAll(",
                        ],
                    },
                ],
            },
            {
                "rel": "tv/withaibuild/customiuizer/installers/LauncherInstaller.kt",
                "object": "LauncherInstaller",
                "methods": [
                    {
                        "pattern": r"\bfun\s+install\s*\(",
                        "checks": [
                            "FeatureInstallRegistry()",
                            "FeatureTarget.LAUNCHER",
                            "InstallPhase.PACKAGE_READY",
                            "LauncherPackageReadyFeatures.all(",
                            "registry.installAll(",
                        ],
                    },
                    {
                        "pattern": r"\bfun\s+handleLoadLauncher\s*\(",
                        "checks": [
                            "FeatureInstallRegistry()",
                            "FeatureTarget.LAUNCHER",
                            "InstallPhase.APPLICATION_ATTACHED",
                            "LauncherPostAttachFeatures.all(",
                            "registry.installAll(",
                        ],
                    },
                ],
            },
            {
                "rel": "tv/withaibuild/customiuizer/installers/SecurityCenterInstaller.kt",
                "object": "SecurityCenterInstaller",
                "methods": [
                    {
                        "pattern": r"\bfun\s+install\s*\(",
                        "checks": [
                            "FeatureInstallRegistry()",
                            "FeatureTarget.SYSTEM_PACKAGE",
                            "InstallPhase.PACKAGE_READY",
                            "SecurityCenterFeatures.all(",
                            "registry.installAll(",
                        ],
                    },
                ],
            },
            {
                "rel": "tv/withaibuild/customiuizer/installers/GenericAppInstaller.kt",
                "object": "GenericAppInstaller",
                "methods": [
                    {
                        "pattern": r"\bfun\s+installPostAttach\s*\(",
                        "checks": [
                            "ModuleHelper.findAndHookMethod",
                            "Application::class.java",
                            "FeatureInstallRegistry()",
                            "GenericAppFeatures.selected(",
                            "FeatureTarget.LAUNCHER",
                            "FeatureTarget.ANY",
                            "registry.installAll(",
                        ],
                    },
                ],
            },
        ]

        for entry in installers:
            with self.subTest(rel=entry["rel"]):
                path = SOURCE_ROOT / entry["rel"]
                self.assertTrue(path.is_file(), f"{entry['rel']} does not exist")
                text = CI.strip_comments(path.read_text(encoding="utf-8"))
                self.assertIn(
                    f"object {entry['object']}",
                    text,
                    f"{entry['rel']} must declare object {entry['object']}",
                )
                self.assertIn("@JvmStatic", text)
                for method in entry["methods"]:
                    self.assertRegex(text, method["pattern"])
                    body = _method_body(text, method["pattern"])
                    for token in method["checks"]:
                        self.assertIn(
                            token,
                            body,
                            f"{entry['rel']} method body must contain {token}",
                        )

        jvm_test = test_source("tv/withaibuild/customiuizer/installers/InstallerJvmAbiTest.kt")
        for name in ["GenericAppInstaller", "SystemUiInstaller", "LauncherInstaller", "SecurityCenterInstaller"]:
            self.assertIn(name, jvm_test)

    def test_feature_install_registry_owns_feature_installation(self) -> None:
        text = CI.strip_comments(
            source("tv/withaibuild/customiuizer/mods/utils/FeatureInstallRegistry.kt")
        )
        self.assertIn("class FeatureInstallRegistry", text)
        self.assertIn("fun register(", text)
        self.assertIn("fun installAll(", text)

        register_body = _method_body(text, r"\bfun\s+register\s*\(")
        self.assertIn("FeatureInstallState.initialize(feature.id)", register_body)

        install_one = _method_body(text, r"\bfun\s+installOne\s*\(")
        self.assertIn("if (!spec.isEnabled(prefs))", install_one)
        self.assertIn("FeatureInstallState.beginInstall(id)", install_one)
        self.assertIn("spec.create().install()", install_one)
        self.assertIn("FeatureInstallState.set(id, toState(result))", install_one)
        self.assertIn("FatalErrors.unwrapAndRethrowIfFatal(", install_one)
        self.assertIn("recordInstallFailure(", install_one)

        is_enabled = install_one.find("if (!spec.isEnabled(prefs))")
        create = install_one.find("spec.create().install()")
        begin = install_one.find("FeatureInstallState.beginInstall(id)")
        self.assertLess(is_enabled, begin)
        self.assertLess(begin, create)

        install_all = _method_body(text, r"\bfun\s+installAll\s*\(")
        self.assertIn("spec.target != target && spec.target != FeatureTarget.ANY", install_all)
        self.assertIn("spec.phase != phase", install_all)

        jvm_tests = [
            ("tv/withaibuild/customiuizer/mods/utils/FeatureInstallRegistryTest.kt", "installAll_idempotent"),
            ("tv/withaibuild/customiuizer/mods/utils/FeatureInstallBoundaryContractTest.kt", "registryKeepsFatalAndOrdinaryFailurePathsSeparate"),
            ("tv/withaibuild/customiuizer/mods/utils/FeatureRegistryWiringTest.kt", "packagePermissionsFeatureIsRegisteredInSystemServerInstaller"),
        ]
        for rel, token in jvm_tests:
            self.assertIn(token, test_source(rel))

    def test_lazy_feature_spec_defers_creation_until_enabled(self) -> None:
        text = CI.strip_comments(
            source("tv/withaibuild/customiuizer/mods/utils/LazyFeatureSpec.kt")
        )
        self.assertIn("internal data class LazyFeatureSpec", text)
        self.assertIn("private val enabled: (PrefMap) -> Boolean", text)
        self.assertIn("private val factory: () -> FeatureDefinition", text)
        self.assertIn("override fun isEnabled(prefs: PrefMap): Boolean = enabled(prefs)", text)
        self.assertIn("override fun create(): FeatureDefinition = factory()", text)

        registry_text = CI.strip_comments(
            source("tv/withaibuild/customiuizer/mods/utils/FeatureInstallRegistry.kt")
        )
        install_one = _method_body(registry_text, r"\bfun\s+installOne\s*\(")
        is_enabled = install_one.find("if (!spec.isEnabled(prefs))")
        create = install_one.find("spec.create().install()")
        self.assertLess(is_enabled, create, "FeatureInstallRegistry must check isEnabled before create")

        jvm_test = test_source("tv/withaibuild/customiuizer/mods/utils/FeatureInstallRegistryTest.kt")
        self.assertIn("lazySpec_disabledFeatureDoesNotCreateDefinition", jvm_test)
        self.assertIn("lazySpec_enabledFeatureCreatesAndInstalls", jvm_test)

    def test_feature_install_state_is_process_level_state(self) -> None:
        text = CI.strip_comments(
            source("tv/withaibuild/customiuizer/mods/utils/FeatureInstallState.kt")
        )
        self.assertIn("object FeatureInstallState", text)
        self.assertIn("private val states = HashMap<Int, FeatureState>()", text)
        self.assertIn("synchronized(states)", text)
        for m in ["initialize", "beginInstall", "get", "set"]:
            self.assertRegex(text, rf"@JvmStatic\s*\n\s*fun\s+{m}\s*\(")

    def test_preference_bootstrap_initializes_snapshot_before_install(self) -> None:
        pb_text = CI.strip_comments(
            source("tv/withaibuild/customiuizer/mods/utils/PreferenceBootstrap.kt")
        )
        self.assertIn("class PreferenceBootstrap", pb_text)
        self.assertIn("fun bootstrap()", pb_text)
        self.assertIn("fun isReady()", pb_text)
        self.assertIn("enum class State", pb_text)
        self.assertIn("State.LOADED", pb_text)
        self.assertIn("State.VALID_EMPTY", pb_text)

        publish = _method_body(pb_text, r"\bfun\s+publishSecondSnapshotLocked\s*\(")
        self.assertIn("prefs.replaceSnapshot(second)", publish)

        main_text = CI.strip_comments(source("tv/withaibuild/customiuizer/MainModule.java"))
        init_prefs = _method_body(main_text, r"\bprivate\s+boolean\s+initPrefs\s*\(")
        self.assertIn("preferenceBootstrap.bootstrap()", init_prefs)
        self.assertIn("preferenceBootstrap.isReady()", init_prefs)

        on_server = _method_body(main_text, r"\bpublic\s+void\s+onSystemServerStarting\s*\(")
        self.assertLess(
            on_server.find("initPrefs()"),
            on_server.find("SystemServerInstaller.install("),
            "preference bootstrap must complete before system_server install",
        )

        on_ready = _method_body(main_text, r"\bpublic\s+void\s+onPackageReady\s*\(")
        init_idx = on_ready.find("initPrefs()")
        first_installer = min(
            on_ready.find("AndroidPackageInstaller.install("),
            on_ready.find("FeatureInstallRegistry()"),
        )
        self.assertGreaterEqual(init_idx, 0)
        self.assertGreaterEqual(first_installer, 0)
        self.assertLess(
            init_idx,
            first_installer,
            "preference bootstrap must complete before package installers",
        )

        jvm_test = test_source("tv/withaibuild/customiuizer/mods/utils/PreferenceBootstrapTest.kt")
        self.assertIn("bootstrap_nonEmptySnapshot_reachesLoaded", jvm_test)
        self.assertIn("bootstrap_listenerNotRegistered_notReady", jvm_test)

    def test_api102_hook_bridge_is_isolated(self) -> None:
        bridge_text = source("tv/withaibuild/customiuizer/mods/utils/Api102HookBridge.kt")
        self.assertIn("internal object Api102HookBridge", bridge_text)
        self.assertIn("fun setStableHookId(", bridge_text)
        self.assertIn("XposedApiCapabilities.supportsStableHookId()", bridge_text)
        self.assertIn("builder.setId(id)", bridge_text)
        self.assertIn("STABLE_ID_RES_TEXT", bridge_text)

        findings: list = []
        for path in sorted(SOURCE_ROOT.rglob("*.kt")) + sorted(SOURCE_ROOT.rglob("*.java")):
            text = CI.strip_comments(path.read_text(encoding="utf-8"))
            findings.extend(CI.check_api102_isolation(path, text))
        if findings:
            for f in findings:
                print(f)
        self.assertEqual(
            [],
            findings,
            "API 102 setId/replaceHook/hot-reload must not leak outside Api102HookBridge",
        )

        xposed_helpers = source("tv/withaibuild/customiuizer/mods/utils/XposedHelpers.java")
        self.assertNotIn("Api102HookBridge", xposed_helpers)
        self.assertNotIn("setStableHookId", xposed_helpers)
        self.assertNotIn(".setId(", xposed_helpers)

        jvm_test = test_source("tv/withaibuild/customiuizer/mods/utils/Api102CapabilityTest.kt")
        self.assertIn("api101_flagsAreZero", jvm_test)
        self.assertIn("bridge_setStableHookId_appliesToBuilder", jvm_test)

    def test_java_kotlin_boundary_is_preserved(self) -> None:
        java_files = sorted(
            p.relative_to(REPO_ROOT).as_posix()
            for p in (REPO_ROOT / "app" / "src" / "main" / "java").rglob("*.java")
        )
        expected = [
            "app/src/main/java/org/apache/commons/lang3/reflect/MemberUtilsX.java",
            "app/src/main/java/tv/withaibuild/customiuizer/MainModule.java",
            "app/src/main/java/tv/withaibuild/customiuizer/mods/utils/XposedHelpers.java",
        ]
        self.assertEqual(
            expected,
            java_files,
            "only the three allowed Java files may exist in the module",
        )

        self.assertIn(
            "public class MainModule extends XposedModule",
            source("tv/withaibuild/customiuizer/MainModule.java"),
        )
        self.assertIn(
            "public final class XposedHelpers",
            source("tv/withaibuild/customiuizer/mods/utils/XposedHelpers.java"),
        )
        self.assertIn(
            "public class MemberUtilsX",
            source("org/apache/commons/lang3/reflect/MemberUtilsX.java"),
        )

        jvm_test = test_source("tv/withaibuild/customiuizer/mods/utils/XposedHelpersAbiTest.kt")
        self.assertIn("publicAbiSnapshotContainsExpectedMethods", jvm_test)


if __name__ == "__main__":
    unittest.main()
