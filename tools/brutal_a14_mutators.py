#!/usr/bin/env python3
"""A14-specific source mutators for the brutal mutation runner.

Each function receives a detached git worktree root and a config dict and must
modify the worktree in place. Mutations are expected to be killed by the gate
named in the runner config.
"""
from __future__ import annotations

import re
from pathlib import Path
from typing import Callable


def _read(root: Path, rel: str) -> str:
    return (root / rel).read_text(encoding="utf-8")


def _write(root: Path, rel: str, text: str) -> None:
    (root / rel).write_text(text, encoding="utf-8")


def _replace_first(root: Path, rel: str, pattern: str, repl: str, flags: int = 0) -> None:
    text = _read(root, rel)
    changed, count = re.subn(pattern, repl, text, count=1, flags=flags)
    if count != 1:
        raise RuntimeError(f"mutation pattern not found in {rel}: {pattern!r}")
    _write(root, rel, changed)


def _replace_all(root: Path, rel: str, pattern: str, repl: str, flags: int = 0) -> int:
    text = _read(root, rel)
    changed, count = re.subn(pattern, repl, text, flags=flags)
    if count == 0:
        raise RuntimeError(f"mutation pattern not found in {rel}: {pattern!r}")
    _write(root, rel, changed)
    return count


def _inject_hazard(root: Path, body: str) -> None:
    path = root / "app/src/main/java/brutal_mutation/InjectedHazard.kt"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(f"package brutal_mutation\n\n{body}\n", encoding="utf-8")


def _java_file(rel: str) -> str:
    return f"app/src/main/java/{rel}"


def _kt_file(rel: str) -> str:
    return f"app/src/main/java/{rel}"


# ---------------------------------------------------------------------------
# JVM ABI
# ---------------------------------------------------------------------------

def remove_jvm_static(root: Path, cfg: dict) -> None:
    """Remove @JvmStatic from a Kotlin reflection-cache entry point."""
    _replace_first(
        root,
        _kt_file("tv/withaibuild/customiuizer/mods/utils/ReflectionCache.kt"),
        r"(    @JvmStatic\n)(    fun getDepInstance)",
        r"\2",
    )


def remove_jvm_field(root: Path, cfg: dict) -> None:
    """Remove @JvmField from the ReflectionCache LoaderState dependencyMethod field."""
    _replace_first(
        root,
        _kt_file("tv/withaibuild/customiuizer/mods/utils/ReflectionCache.kt"),
        r"(    @JvmField\n)(    internal var dependencyMethod: Method\?)",
        r"\2",
    )


def object_to_class(root: Path, cfg: dict) -> None:
    """Turn the ReflectionCache object into a class."""
    _replace_first(
        root,
        _kt_file("tv/withaibuild/customiuizer/mods/utils/ReflectionCache.kt"),
        r"^object ReflectionCache \{",
        r"class ReflectionCache private constructor() {",
        flags=re.MULTILINE,
    )


def companion_method_rename(root: Path, cfg: dict) -> None:
    """Rename a @JvmStatic companion method used only from Kotlin."""
    _replace_first(
        root,
        _kt_file("tv/withaibuild/customiuizer/mods/utils/ModuleHelper.kt"),
        r"(@JvmStatic\n        fun findAndHookMethod)\(className: String, classLoader: ClassLoader\?, methodName: String",
        r"\1Renamed(className: String, classLoader: ClassLoader?, methodName: String",
    )


def change_nullable_return(root: Path, cfg: dict) -> None:
    """Change RemotePreferenceSource.get return from nullable to non-null."""
    _replace_first(
        root,
        _kt_file("tv/withaibuild/customiuizer/mods/utils/PreferenceBootstrap.kt"),
        r"fun get\(name: String\): SharedPreferences\?",
        r"fun get(name: String): SharedPreferences",
    )


def change_param_nullability(root: Path, cfg: dict) -> None:
    """Change PreferenceBootstrap RemotePreferenceSource parameter to nullable."""
    _replace_first(
        root,
        _kt_file("tv/withaibuild/customiuizer/mods/utils/PreferenceBootstrap.kt"),
        r"fun get\(name: String\):",
        r"fun get(name: String?):",
    )


def default_arg_drift(root: Path, cfg: dict) -> None:
    """Change the default maxDepth of unwrapAndRethrowIfFatal."""
    _replace_first(
        root,
        _kt_file("tv/withaibuild/customiuizer/mods/utils/FatalErrors.kt"),
        r"fun unwrapAndRethrowIfFatal\(t: Throwable, maxDepth: Int = 4\)",
        r"fun unwrapAndRethrowIfFatal(t: Throwable, maxDepth: Int = 3)",
    )


def overload_removed(root: Path, cfg: dict) -> None:
    """Remove the Class<?>[] overload of XposedHelpers.callMethod."""
    _replace_first(
        root,
        _java_file("tv/withaibuild/customiuizer/mods/utils/XposedHelpers.java"),
        r"    public static Object callMethod\(Object obj, String methodName, Class<\?>\[\] parameterTypes, Object\.\.\. args\) \{[\s\S]*?\n    \}\n",
        "",
    )


def internal_name_mangle(root: Path, cfg: dict) -> None:
    """Make StatusbarViewMaths.clampStatusIconInsertIndex internal."""
    _replace_first(
        root,
        _kt_file("tv/withaibuild/customiuizer/mods/utils/StatusbarViewMaths.kt"),
        r"(    @JvmStatic\n)(    fun clampStatusIconInsertIndex)",
        r"\1internal \2",
    )


def sam_lambda_drift(root: Path, cfg: dict) -> None:
    """Change the GestureEffectExecutor SAM config parameter to nullable."""
    _replace_first(
        root,
        _kt_file("tv/withaibuild/customiuizer/mods/utils/gesture/GestureEffectExecutor.kt"),
        r"fun execute\(\s*commands: List<GestureCommand>,\s*dependencies: GestureDependencies,\s*config: GestureConfig,\s*context: Any\?",
        r"fun execute(\n        commands: List<GestureCommand>,\n        dependencies: GestureDependencies,\n        config: GestureConfig?,\n        context: Any?",
    )


def installer_java_abi_drift(root: Path, cfg: dict) -> None:
    """Rename the LauncherInstaller install entry point."""
    _replace_first(
        root,
        _kt_file("tv/withaibuild/customiuizer/installers/LauncherInstaller.kt"),
        r"(    @JvmStatic\n    fun )install\(lpparam: PackageReadyParam, mPrefs: PrefMap\)",
        r"\1install2(lpparam: PackageReadyParam, mPrefs: PrefMap)",
    )


def xposedhelpers_abi_drift(root: Path, cfg: dict) -> None:
    """Rename the XposedHelpers.callMethod family."""
    _replace_all(
        root,
        _java_file("tv/withaibuild/customiuizer/mods/utils/XposedHelpers.java"),
        r"public static Object callMethod\(",
        r"public static Object callMethod2(",
    )


# ---------------------------------------------------------------------------
# Reflection
# ---------------------------------------------------------------------------

def reflection_class_rename(root: Path, cfg: dict) -> None:
    """Change the SystemUI Dependency class-name string."""
    _replace_first(
        root,
        _kt_file("tv/withaibuild/customiuizer/mods/utils/ReflectionCache.kt"),
        r'(internal var dependencyClassName: String = )"com\.android\.systemui\.Dependency"',
        r'\1"com.android.systemui.Dependency2"',
    )


def reflection_method_rename(root: Path, cfg: dict) -> None:
    """Change the init method-name string in SystemUI bootstrap."""
    _replace_first(
        root,
        _kt_file("tv/withaibuild/customiuizer/mods/utils/SystemUiBootstrapCoordinator.kt"),
        r'(ModuleHelper\.findAndHookMethod\(\s*"com\.android\.systemui\.SystemUIInitializer",\s*lpparam\.classLoader,\s*)"init"',
        r'\1"init2"',
    )


def reflection_field_rename(root: Path, cfg: dict) -> None:
    """Change the mContext field-name string in SystemUI bootstrap."""
    _replace_first(
        root,
        _kt_file("tv/withaibuild/customiuizer/mods/utils/SystemUiBootstrapCoordinator.kt"),
        r'(XposedHelpers\.getObjectField\(param\.getThisObject\(\),\s*)"mContext"',
        r'\1"mContext2"',
    )


# ---------------------------------------------------------------------------
# R8 / ProGuard
# ---------------------------------------------------------------------------

def remove_keep_rule(root: Path, cfg: dict) -> None:
    """Remove the XposedModule keep rule."""
    _replace_first(
        root,
        "app/proguard-rules.pro",
        r"-keep,allowoptimization,allowobfuscation public class \* extends io\.github\.libxposed\.api\.XposedModule \{\s*public <init>\(\);\s*\}\n",
        "",
    )


def remove_consumer_rule(root: Path, cfg: dict) -> None:
    """Remove a keepnames consumer rule."""
    _replace_first(
        root,
        "app/proguard-rules.pro",
        r"-keepnames class tv\.withaibuild\.customiuizer\.MainActivity\n",
        "",
    )


def r8_broad_mods_keep(root: Path, cfg: dict) -> None:
    """Reintroduce the broad mods keep rule removed by the R8 optimization."""
    _replace_first(
        root,
        "app/proguard-rules.pro",
        r"# Obfuscation\n",
        "-keepclassmembers class tv.withaibuild.customiuizer.mods.** {\n"
        "    public static <methods>;\n"
        "    public <fields>;\n"
        "}\n\n"
        "# Obfuscation (broad mods keep mutation)\n",
    )


# ---------------------------------------------------------------------------
# Installer / Process / ClassLoader
# ---------------------------------------------------------------------------

def duplicate_install_all(root: Path, cfg: dict) -> None:
    """Duplicate the common registry installAll call in MainModule."""
    _replace_first(
        root,
        _java_file("tv/withaibuild/customiuizer/MainModule.java"),
        r"(commonRegistry\.installAll\(FeatureTarget\.ANY, InstallPhase\.PACKAGE_READY, mPrefs\);)",
        r"\1\n        \1",
    )


def installer_order_swap(root: Path, cfg: dict) -> None:
    """Swap the PowerKeeper and Settings installer calls in MainModule."""
    _replace_first(
        root,
        _java_file("tv/withaibuild/customiuizer/MainModule.java"),
        r"(        if \(scope == ProcessScope\.POWER_KEEPER\) \{\n            PowerKeeperInstaller\.install\(lpparam, mPrefs\);\n        \}\n\n)(        if \(scope == ProcessScope\.SETTINGS_MAIN\) \{\n            SettingsInstaller\.install\(lpparam, mPrefs\);\n        \}\n)",
        r"\2\1",
    )


def wrong_process_target(root: Path, cfg: dict) -> None:
    """Make the launcher package resolve to a generic app."""
    _replace_first(
        root,
        _kt_file("tv/withaibuild/customiuizer/mods/utils/ProcessRouter.kt"),
        r'("com\.miui\.home" -> )ProcessScope\.LAUNCHER',
        r"\1ProcessScope.GENERIC_APP",
    )


def wrong_install_phase(root: Path, cfg: dict) -> None:
    """Move the SystemUI base feature phase to APPLICATION_ATTACHED."""
    _replace_first(
        root,
        _kt_file("tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt"),
        r"(override val phase = )InstallPhase\.PACKAGE_READY",
        r"\1InstallPhase.APPLICATION_ATTACHED",
    )


def process_router_fallthrough(root: Path, cfg: dict) -> None:
    """Make the ProcessRouter else branch resolve to SYSTEM_UI."""
    _replace_first(
        root,
        _kt_file("tv/withaibuild/customiuizer/mods/utils/ProcessRouter.kt"),
        r"(else -> ProcessScope\.)GENERIC_APP",
        r"\1SYSTEM_UI",
    )


def systemui_plugin_classloader_replaced(root: Path, cfg: dict) -> None:
    """Use the system class loader for class lookup in ReflectionCache."""
    _replace_first(
        root,
        _kt_file("tv/withaibuild/customiuizer/mods/utils/ReflectionCache.kt"),
        r"(XposedHelpers\.findClassIfExists\(className, )classLoader(\))",
        r"\1ClassLoader.getSystemClassLoader()\2",
    )


def classloader_cache_global(root: Path, cfg: dict) -> None:
    """Replace per-loader keying with a single global loader state."""
    _replace_first(
        root,
        _kt_file("tv/withaibuild/customiuizer/mods/utils/ReflectionCache.kt"),
        r"(    private val lifecycle = AtomicLong\(0L\)\n)(\n    private val loaderStates: MutableMap<ClassLoader\?, LoaderState> = Collections\.synchronizedMap\(\n        object : LinkedHashMap<ClassLoader\?, LoaderState>\(MAX_LOADERS, 0\.75f, true\) \{\n            override fun removeEldestEntry\(eldest: Map\.Entry<ClassLoader\?, LoaderState>\?\): Boolean \{\n                return size > MAX_LOADERS\n            \}\n        \}\n    \)\n)",
        r"\1\2\n\n    private val globalLoaderState = LoaderState()",
    )
    _replace_first(
        root,
        _kt_file("tv/withaibuild/customiuizer/mods/utils/ReflectionCache.kt"),
        r"private fun resolveNewLoader\(classLoader: ClassLoader\?, className: String\): Any\? \{\n        val loaderState = synchronized\(loaderStates\) \{\n            loaderStates\[classLoader\] \?: LoaderState\(\)\.also \{ loaderStates\[classLoader\] = it \}\n        \}\n        return resolve\(loaderState, classLoader, className\)\n    \}",
        r"private fun resolveNewLoader(classLoader: ClassLoader?, className: String): Any? {\n        return resolve(globalLoaderState, classLoader, className)\n    }",
    )


def bootstrap_guard_removed(root: Path, cfg: dict) -> None:
    """Remove the isFirstPackage guard from MainModule.onPackageReady."""
    _replace_first(
        root,
        _java_file("tv/withaibuild/customiuizer/MainModule.java"),
        r"        if \(!lpparam\.isFirstPackage\(\)\) return;\n",
        "",
    )


def receiver_registration_duplicate(root: Path, cfg: dict) -> None:
    """Duplicate a registerReceiver call in WeatherDataController."""
    _replace_first(
        root,
        _kt_file("tv/withaibuild/customiuizer/mods/utils/WeatherDataController.kt"),
        r"(            ctx\.registerReceiver\(\n                timeTickReceiver,\n                IntentFilter\(\"android\.intent\.action\.TIME_TICK\"\),\n                Context\.RECEIVER_NOT_EXPORTED\n            \))",
        r"\1\n            \1",
    )


def preference_bootstrap_eager(root: Path, cfg: dict) -> None:
    """Remove the loaded-state early return in PreferenceBootstrap."""
    _replace_first(
        root,
        _kt_file("tv/withaibuild/customiuizer/mods/utils/PreferenceBootstrap.kt"),
        r"            if \(s == State\.LOADED \|\| s == State\.VALID_EMPTY\) return true\n",
        "",
    )


def fatal_boundary_removed(root: Path, cfg: dict) -> None:
    """Remove the fatal boundary rethrow in MainModule.loadDexKit."""
    _replace_first(
        root,
        _java_file("tv/withaibuild/customiuizer/MainModule.java"),
        r"            FatalErrors\.rethrowIfFatal\(t\);\n",
        "",
    )


# ---------------------------------------------------------------------------
# Gesture
# ---------------------------------------------------------------------------

def consume_same_event_twice(root: Path, cfg: dict) -> None:
    """Execute side effects twice for MOVE/UP events in GestureMachine."""
    _replace_first(
        root,
        _kt_file("tv/withaibuild/customiuizer/mods/utils/gesture/GestureMachine.kt"),
        r"(        val allowed = gate\.filter\(event\.entry, ownerId, event, commands\)\n        effectExecutor\.execute\(allowed, deps, config, event\)\n\n        if \(arbiter != null)",
        r"        val allowed = gate.filter(event.entry, ownerId, event, commands)\n        effectExecutor.execute(allowed, deps, config, event)\n        effectExecutor.execute(allowed, deps, config, event)\n\n        if (arbiter != null",
    )


def remove_event_dedup(root: Path, cfg: dict) -> None:
    """Remove the owner/event fingerprint deduplication guard."""
    _replace_first(
        root,
        _kt_file("tv/withaibuild/customiuizer/mods/utils/gesture/GestureSideEffectGate.kt"),
        r"        val fp = fingerprint\(ownerId, event\)\n        if \(fp in seen\) return emptyList\(\)\n",
        "",
    )


def remove_side_effect_gate(root: Path, cfg: dict) -> None:
    """Drop any command list that contains a business effect."""
    _replace_first(
        root,
        _kt_file("tv/withaibuild/customiuizer/mods/utils/gesture/GestureSideEffectGate.kt"),
        r"(        if \(commands\.isEmpty\(\)\) return commands\n)(        if \(entry !in effectEntries\) return emptyList\(\)\n)",
        r"\1        if (commands.any(::isBusinessEffect)) return emptyList()\n",
    )


def action_cancel_as_up(root: Path, cfg: dict) -> None:
    """Route CANCEL through handleUp instead of handleCancel."""
    _replace_first(
        root,
        _kt_file("tv/withaibuild/customiuizer/mods/utils/gesture/GestureStateMachine.kt"),
        r"GestureAction\.CANCEL -> handleCancel\(snapshot\)",
        r"GestureAction.CANCEL -> handleUp(snapshot, event, config, geometry)",
    )


def wrong_threshold_comparison(root: Path, cfg: dict) -> None:
    """Change the ShadeExpansionTracker threshold to >=."""
    _replace_first(
        root,
        _kt_file("tv/withaibuild/customiuizer/mods/utils/ShadeExpansionTracker.kt"),
        r"if \(value > threshold\)",
        r"if (value >= threshold)",
    )


def horizontal_vertical_axis_swap(root: Path, cfg: dict) -> None:
    """Use Y delta instead of X delta for brightness calculation."""
    _replace_first(
        root,
        _kt_file("tv/withaibuild/customiuizer/mods/utils/gesture/GestureStateMachine.kt"),
        r"(val delta = event\.)x( - session\.startX)",
        r"\1y\2",
        flags=re.MULTILINE,
    )


def left_right_action_swap(root: Path, cfg: dict) -> None:
    """Swap the LEFT and RIGHT double-tap position thresholds."""
    _replace_first(
        root,
        _kt_file("tv/withaibuild/customiuizer/mods/utils/gesture/GestureStateMachine.kt"),
        r"(event\.x \* 5f < geometry\.screenWidth -> DoubleTapPosition\.)LEFT(\n)(\s*)(event\.x > geometry\.screenWidth \* 0\.8f -> DoubleTapPosition\.)RIGHT",
        r"\1RIGHT\2\3\4LEFT",
    )


def state_reset_removed(root: Path, cfg: dict) -> None:
    """Remove the Reset command from GestureStateMachine.handleUp."""
    _replace_first(
        root,
        _kt_file("tv/withaibuild/customiuizer/mods/utils/gesture/GestureStateMachine.kt"),
        r"        commands\.add\(GestureCommand\.Reset\)\n",
        "",
    )


def concurrent_reentry(root: Path, cfg: dict) -> None:
    """Remove @Synchronized from FeatureInstallRegistry.installAll."""
    _replace_first(
        root,
        _kt_file("tv/withaibuild/customiuizer/mods/utils/FeatureInstallRegistry.kt"),
        r"(    @Synchronized\n)(    @JvmOverloads\n    fun installAll)",
        r"\2",
    )


def dependency_resolver_fallback_removed(root: Path, cfg: dict) -> None:
    """Remove the passThrough fallback when gesture dependencies are not ready."""
    _replace_first(
        root,
        _kt_file("tv/withaibuild/customiuizer/mods/utils/gesture/GestureMachine.kt"),
        r"if \(dependencies\[ownerId\] == null\) \{\n            passThrough\(event, config\)\n            return\n        \}",
        r"if (dependencies[ownerId] == null) return",
    )


def config_publisher_stale(root: Path, cfg: dict) -> None:
    """Make GestureConfigPublisher.publish keep the first published config."""
    _replace_first(
        root,
        _kt_file("tv/withaibuild/customiuizer/mods/utils/gesture/GestureConfigPublisher.kt"),
        r"if \(next != null\) \{\n            published\.set\(next\)\n        \} else if \(published\.get\(\) == null\) \{",
        r"if (next != null && published.get() == null) {\n            published.set(next)\n        } else if (published.get() == null) {",
    )


def plugin_runtime_lifecycle_leak(root: Path, cfg: dict) -> None:
    """Remove the old machine clear on ControlCenter runtime rebind."""
    _replace_first(
        root,
        _kt_file("tv/withaibuild/customiuizer/mods/utils/gesture/ControlCenterGestureRuntimeHolder.kt"),
        r"        existing\?\.machine\?\.clear\(\)\n",
        "",
    )


def shade_tracker_boundary_drift(root: Path, cfg: dict) -> None:
    """Use a strict >= boundary in ShadeExpansionTracker."""
    _replace_first(
        root,
        _kt_file("tv/withaibuild/customiuizer/mods/utils/ShadeExpansionTracker.kt"),
        r"if \(value > threshold\)",
        r"if (value >= threshold)",
    )


def statusbar_maths_sign_rounding_drift(root: Path, cfg: dict) -> None:
    """Clamp an out-of-range insert index to childCount - 1."""
    _replace_first(
        root,
        _kt_file("tv/withaibuild/customiuizer/mods/utils/StatusbarViewMaths.kt"),
        r"requested > childCount -> childCount(?=\n)",
        r"requested > childCount -> childCount - 1",
    )


# ---------------------------------------------------------------------------
# Fatal / OOM
# ---------------------------------------------------------------------------

def catch_throwable_no_fatal(root: Path, cfg: dict) -> None:
    """Inject an empty catch(Throwable) block."""
    _inject_hazard(
        root,
        'object InjectedHazard { fun run() { try { error("x") } catch (t: Throwable) { } } }',
    )


def swallow_out_of_memory(root: Path, cfg: dict) -> None:
    """Stop FatalErrors from rethrowing OutOfMemoryError."""
    _replace_first(
        root,
        _kt_file("tv/withaibuild/customiuizer/mods/utils/FatalErrors.kt"),
        r"            is OutOfMemoryError -> throw t\n",
        "",
    )


def swallow_stack_overflow(root: Path, cfg: dict) -> None:
    """Stop FatalErrors from rethrowing VirtualMachineError."""
    _replace_first(
        root,
        _kt_file("tv/withaibuild/customiuizer/mods/utils/FatalErrors.kt"),
        r"            is VirtualMachineError -> throw t\n",
        "",
    )


def swallow_linkage_error(root: Path, cfg: dict) -> None:
    """Inject an empty catch that swallows a LinkageError path."""
    _inject_hazard(
        root,
        'object InjectedHazard { fun run() { try { throw LinkageError("x") } catch (t: Throwable) { } } }',
    )


def swallow_verify_error(root: Path, cfg: dict) -> None:
    """Inject an empty catch that swallows a VerifyError."""
    _inject_hazard(
        root,
        'object InjectedHazard { fun run() { try { throw VerifyError("x") } catch (t: Throwable) { } } }',
    )


def swallow_thread_death(root: Path, cfg: dict) -> None:
    """Stop CallbackGuard from rethrowing ThreadDeath."""
    _replace_all(
        root,
        _kt_file("tv/withaibuild/customiuizer/mods/utils/CallbackGuard.kt"),
        r"if \(t is OutOfMemoryError \|\| t is ThreadDeath \|\| t is VirtualMachineError\) throw t",
        r"if (t is OutOfMemoryError || t is VirtualMachineError) throw t",
    )


def swallow_virtual_machine_error(root: Path, cfg: dict) -> None:
    """Stop CallbackGuard from rethrowing VirtualMachineError."""
    _replace_all(
        root,
        _kt_file("tv/withaibuild/customiuizer/mods/utils/CallbackGuard.kt"),
        r"if \(t is OutOfMemoryError \|\| t is ThreadDeath \|\| t is VirtualMachineError\) throw t",
        r"if (t is OutOfMemoryError || t is ThreadDeath) throw t",
    )


def reflection_wrapped_fatal_unwrap(root: Path, cfg: dict) -> None:
    """Remove the OOM unwrap in ReflectionCache.invoke/resolve."""
    _replace_first(
        root,
        _kt_file("tv/withaibuild/customiuizer/mods/utils/ReflectionCache.kt"),
        r"                if \(cause is OutOfMemoryError\) throw cause\n",
        "",
    )


def callback_guard_fatal_to_normal(root: Path, cfg: dict) -> None:
    """Remove the fatal rethrow from CallbackGuard.guarded."""
    _replace_all(
        root,
        _kt_file("tv/withaibuild/customiuizer/mods/utils/CallbackGuard.kt"),
        r"            if \(t is OutOfMemoryError \|\| t is ThreadDeath \|\| t is VirtualMachineError\) throw t\n",
        "",
    )


def mainmodule_top_boundary_swallow(root: Path, cfg: dict) -> None:
    """Remove the throw t rethrow from MainModule.loadDexKit."""
    _replace_first(
        root,
        _java_file("tv/withaibuild/customiuizer/MainModule.java"),
        r"            throw t;\n",
        "",
    )


def installer_boundary_swallow(root: Path, cfg: dict) -> None:
    """Remove the throw oom path in FeatureInstallRegistry.installOne."""
    _replace_first(
        root,
        _kt_file("tv/withaibuild/customiuizer/mods/utils/FeatureInstallRegistry.kt"),
        r"                \} catch \(oom: OutOfMemoryError\) \{\n                    FeatureInstallState\.set\(id, FeatureState\.FAILED_TRANSIENT\)\n                    throw oom\n",
        "",
    )


def systemui_runtime_swallow(root: Path, cfg: dict) -> None:
    """Remove the fatal rethrow in SystemUiBootstrapCoordinator."""
    _replace_first(
        root,
        _kt_file("tv/withaibuild/customiuizer/mods/utils/SystemUiBootstrapCoordinator.kt"),
        r"                    FatalErrors\.rethrowIfFatal\(t\)\n",
        "",
    )


MUTATORS: dict[str, Callable[[Path, dict], None]] = {
    # JVM ABI
    "remove_jvm_static": remove_jvm_static,
    "remove_jvm_field": remove_jvm_field,
    "object_to_class": object_to_class,
    "companion_method_rename": companion_method_rename,
    "change_nullable_return": change_nullable_return,
    "change_param_nullability": change_param_nullability,
    "default_arg_drift": default_arg_drift,
    "overload_removed": overload_removed,
    "internal_name_mangle": internal_name_mangle,
    "sam_lambda_drift": sam_lambda_drift,
    "installer_java_abi_drift": installer_java_abi_drift,
    "xposedhelpers_abi_drift": xposedhelpers_abi_drift,
    # Reflection
    "reflection_class_rename": reflection_class_rename,
    "reflection_method_rename": reflection_method_rename,
    "reflection_field_rename": reflection_field_rename,
    # R8
    "remove_keep_rule": remove_keep_rule,
    "remove_consumer_rule": remove_consumer_rule,
    "r8_broad_mods_keep": r8_broad_mods_keep,
    # Installer / Process / ClassLoader
    "duplicate_install_all": duplicate_install_all,
    "installer_order_swap": installer_order_swap,
    "wrong_process_target": wrong_process_target,
    "wrong_install_phase": wrong_install_phase,
    "process_router_fallthrough": process_router_fallthrough,
    "systemui_plugin_classloader_replaced": systemui_plugin_classloader_replaced,
    "classloader_cache_global": classloader_cache_global,
    "bootstrap_guard_removed": bootstrap_guard_removed,
    "receiver_registration_duplicate": receiver_registration_duplicate,
    "preference_bootstrap_eager": preference_bootstrap_eager,
    "fatal_boundary_removed": fatal_boundary_removed,
    # Gesture
    "consume_same_event_twice": consume_same_event_twice,
    "remove_event_dedup": remove_event_dedup,
    "remove_side_effect_gate": remove_side_effect_gate,
    "action_cancel_as_up": action_cancel_as_up,
    "wrong_threshold_comparison": wrong_threshold_comparison,
    "horizontal_vertical_axis_swap": horizontal_vertical_axis_swap,
    "left_right_action_swap": left_right_action_swap,
    "state_reset_removed": state_reset_removed,
    "concurrent_reentry": concurrent_reentry,
    "dependency_resolver_fallback_removed": dependency_resolver_fallback_removed,
    "config_publisher_stale": config_publisher_stale,
    "plugin_runtime_lifecycle_leak": plugin_runtime_lifecycle_leak,
    "shade_tracker_boundary_drift": shade_tracker_boundary_drift,
    "statusbar_maths_sign_rounding_drift": statusbar_maths_sign_rounding_drift,
    # Fatal / OOM
    "catch_throwable_no_fatal": catch_throwable_no_fatal,
    "swallow_out_of_memory": swallow_out_of_memory,
    "swallow_stack_overflow": swallow_stack_overflow,
    "swallow_linkage_error": swallow_linkage_error,
    "swallow_verify_error": swallow_verify_error,
    "swallow_thread_death": swallow_thread_death,
    "swallow_virtual_machine_error": swallow_virtual_machine_error,
    "reflection_wrapped_fatal_unwrap": reflection_wrapped_fatal_unwrap,
    "callback_guard_fatal_to_normal": callback_guard_fatal_to_normal,
    "mainmodule_top_boundary_swallow": mainmodule_top_boundary_swallow,
    "installer_boundary_swallow": installer_boundary_swallow,
    "systemui_runtime_swallow": systemui_runtime_swallow,
}
