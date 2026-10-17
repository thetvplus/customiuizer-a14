"""Tests for the A14-6G static invariants in tools/check-invariants.py."""

import importlib.util
import sys
import tempfile
import types
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
CHECK_INVARIANTS = REPO_ROOT / "tools" / "check-invariants.py"
SOURCE_ROOT = REPO_ROOT / "app" / "src" / "main" / "java"


def _load_check_invariants() -> types.ModuleType:
    spec = importlib.util.spec_from_file_location("check_invariants_a14_6g", CHECK_INVARIANTS)
    module = importlib.util.module_from_spec(spec)
    sys.modules["check_invariants_a14_6g"] = module
    if spec.loader is not None:
        spec.loader.exec_module(module)
    return module


class A14_6GInvariantsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.mod = _load_check_invariants()

    def _source_path(self, rel: str) -> Path:
        return SOURCE_ROOT / rel

    def test_feature_install_oom_cleanup_ok(self):
        text = """
    private fun installOne(spec: FeatureSpec, prefs: PrefMap): FeatureInstallResult {
        FeatureInstallState.set(id, FeatureState.INSTALLING)
        val (definition, result) = try {
            val created = spec.create()
            created.install()
        } catch (oom: OutOfMemoryError) {
            FeatureInstallState.set(id, FeatureState.FAILED_TRANSIENT)
            throw oom
        } catch (t: Throwable) {
            XposedHelpers.log(t)
            recordInstallFailure(spec, t)
            Pair(null, FeatureInstallResult.FAILED_TRANSIENT)
        }
        return result
    }
"""
        findings = self.mod.check_feature_install_oom_cleanup(
            self._source_path("tv/withaibuild/customiuizer/mods/utils/FeatureInstallRegistry.kt"),
            text,
        )
        self.assertEqual([], findings)

    def test_feature_install_oom_cleanup_missing_state_rollback(self):
        text = """
    private fun installOne(spec: FeatureSpec, prefs: PrefMap): FeatureInstallResult {
        val (definition, result) = try {
            val created = spec.create()
            created.install()
        } catch (oom: OutOfMemoryError) {
            throw oom
        } catch (t: Throwable) {
            Pair(null, FeatureInstallResult.FAILED_TRANSIENT)
        }
        return result
    }
"""
        findings = self.mod.check_feature_install_oom_cleanup(
            self._source_path("tv/withaibuild/customiuizer/mods/utils/FeatureInstallRegistry.kt"),
            text,
        )
        self.assertEqual(1, len(findings))
        self.assertIn("FAILED_TRANSIENT", findings[0].detail)

    def test_feature_definition_delegates_throwable_boundary(self):
        path = self._source_path(
            "tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt"
        )
        clean = """
    override fun install(): FeatureInstallResult {
        installHook()
        return FeatureInstallResult.INSTALLED
    }
"""
        swallowed = """
    override fun install(): FeatureInstallResult = try {
        installHook()
        FeatureInstallResult.INSTALLED
    } catch (t: Throwable) {
        FeatureInstallResult.FAILED_TRANSIENT
    }
"""

        self.assertEqual([], self.mod.check_feature_install_boundary(path, clean))
        findings = self.mod.check_feature_install_boundary(path, swallowed)
        self.assertEqual(1, len(findings))
        self.assertIn("FeatureInstallRegistry", findings[0].detail)

    def test_dexkit_close_requires_oom_rethrow(self):
        with tempfile.TemporaryDirectory() as tmp:
            helper = Path(tmp) / "XposedHelpers.java"
            helper.write_text(
                """
public static void closeBridge() {
    try { bridge.close(); }
    catch (OutOfMemoryError oom) { throw oom; }
    catch (Throwable t) { log(t); }
}
""",
                encoding="utf-8",
            )
            self.assertEqual([], self.mod.check_dexkit_close_oom(helper))

            helper.write_text(
                """
public static void closeBridge() {
    try { bridge.close(); }
    catch (Throwable t) { log(t); }
}
""",
                encoding="utf-8",
            )
            findings = self.mod.check_dexkit_close_oom(helper)
            self.assertEqual(1, len(findings))
            self.assertIn("rethrow", findings[0].detail)

    def test_java_runtime_boundary_requires_oom_rethrow(self):
        with tempfile.TemporaryDirectory() as tmp:
            boundary = Path(tmp) / "Boundary.java"
            boundary.write_text(
                """
void safe() {
    try { work(); }
    catch (OutOfMemoryError oom) { throw oom; }
    catch (Throwable ignored) {}
}
void propagating() {
    try { work(); }
    catch (Throwable t) { log(t); throw t; }
}
""",
                encoding="utf-8",
            )
            self.assertEqual([], self.mod.check_java_fatal_boundaries((boundary,)))

            boundary.write_text(
                """
void unsafe() {
    try { work(); }
    catch (Throwable ignored) {}
}
""",
                encoding="utf-8",
            )
            findings = self.mod.check_java_fatal_boundaries((boundary,))
            self.assertEqual(1, len(findings))
            self.assertIn("OOM", findings[0].detail)

    def test_throwable_log_overloads_require_oom_rethrow_before_formatting(self):
        with tempfile.TemporaryDirectory() as tmp:
            helper = Path(tmp) / "XposedHelpers.java"
            helper.write_text(
                """
public static void log(Throwable t) {
    if (t instanceof OutOfMemoryError) throw (OutOfMemoryError) t;
    Log.getStackTraceString(t);
}
public static void log(String mod, Throwable t) {
    if (t instanceof OutOfMemoryError) throw (OutOfMemoryError) t;
    Log.getStackTraceString(t);
}
""",
                encoding="utf-8",
            )
            self.assertEqual([], self.mod.check_xposed_throwable_log_oom(helper))

            helper.write_text(
                """
public static void log(Throwable t) { Log.getStackTraceString(t); }
public static void log(String mod, Throwable t) { Log.getStackTraceString(t); }
""",
                encoding="utf-8",
            )
            findings = self.mod.check_xposed_throwable_log_oom(helper)
            self.assertEqual(2, len(findings))
            self.assertTrue(all("OOM" in item.detail for item in findings))

    def test_generic_app_registry_is_created_inside_attach_for_selected_specs(self):
        with tempfile.TemporaryDirectory() as tmp:
            installer = Path(tmp) / "GenericAppInstaller.java"
            installer.write_text(
                """
void install() {
    hook(new MethodHook() {
        protected void after(AfterHookCallback param) {
            FeatureInstallRegistry registry = new FeatureInstallRegistry();
            GenericAppFeatures.selected(param, prefs, true, false, false, false);
        }
    });
}
""",
                encoding="utf-8",
            )
            self.assertEqual([], self.mod.check_generic_app_attach_transaction(installer))

            installer.write_text(
                """
void install() {
    FeatureInstallRegistry registry = new FeatureInstallRegistry();
    GenericAppFeatures.all(param, prefs);
    hook(new MethodHook() {
        protected void after(AfterHookCallback param) {}
    });
}
""",
                encoding="utf-8",
            )
            findings = self.mod.check_generic_app_attach_transaction(installer)
            self.assertEqual(2, len(findings))

    def test_device_monitor_hot_path_rejects_formatter_and_swallowed_oom(self):
        path = self._source_path(
            "tv/withaibuild/customiuizer/mods/utils/DeviceInfoMonitor.kt"
        )
        clean = """
val text = formatMonitorOneDecimal(value)
try { read() }
catch (oom: OutOfMemoryError) { throw oom }
catch (_: Throwable) { return null }
"""
        self.assertEqual([], self.mod.check_device_info_monitor_hot_path(path, clean))

        unsafe = """
val text = String.format(Locale.ROOT, "%.1f", value)
try { read() }
catch (_: Throwable) { return null }
"""
        findings = self.mod.check_device_info_monitor_hot_path(path, unsafe)
        self.assertEqual(2, len(findings))

    def test_method_hook_callbacks_require_oom_rethrow(self):
        path = self._source_path(
            "tv/withaibuild/customiuizer/mods/utils/HookerClassHelper.kt"
        )
        clean = """
override fun beforeHook(callback: BeforeHookCallback) {
    try { before(callback) }
    catch (oom: OutOfMemoryError) { throw oom }
    catch (td: ThreadDeath) { throw td }
    catch (vme: VirtualMachineError) { throw vme }
    catch (t: Throwable) { log(t) }
}
override fun afterHook(callback: AfterHookCallback) {
    try { after(callback) }
    catch (oom: OutOfMemoryError) { throw oom }
    catch (td: ThreadDeath) { throw td }
    catch (vme: VirtualMachineError) { throw vme }
    catch (t: Throwable) { log(t) }
}
"""
        self.assertEqual([], self.mod.check_method_hook_fatal_boundary(path, clean))

        unsafe = """
override fun beforeHook(callback: BeforeHookCallback) {
    try { before(callback) }
    catch (t: Throwable) { log(t) }
}
override fun afterHook(callback: AfterHookCallback) {
    try { after(callback) }
    catch (t: Throwable) { log(t) }
}
"""
        findings = self.mod.check_method_hook_fatal_boundary(path, unsafe)
        self.assertEqual(2, len(findings))
        self.assertTrue(all("OutOfMemoryError" in item.detail for item in findings))

    def test_module_helper_requires_oom_rethrow_before_generic_catch(self):
        path = self._source_path(
            "tv/withaibuild/customiuizer/mods/utils/ModuleHelper.kt"
        )
        clean = """
fun silent(): Any? = try { work() }
catch (oom: OutOfMemoryError) { throw oom }
catch (t: Throwable) { FatalErrors.unwrapAndRethrowIfFatal(t); null }
fun propagating() = try { work() }
catch (t: Throwable) { throw t }
"""
        self.assertEqual([], self.mod.check_module_helper_fatal_boundaries(path, clean))

        unsafe = """
fun silent(): Any? = try { work() }
catch (t: Throwable) { null }
"""
        findings = self.mod.check_module_helper_fatal_boundaries(path, unsafe)
        self.assertEqual(1, len(findings))
        self.assertIn("fatal", findings[0].detail.lower())

    def test_charging_info_skips_disabled_io_and_formatter_allocations(self):
        path = self._source_path(
            "tv/withaibuild/customiuizer/mods/SystemLockScreenHooks.kt"
        )
        clean = """
fun buildChargingInfoDetails(
    charge: Int,
    hint: String,
    prefs: PrefMap,
    isKeyguardCaller: () -> Boolean,
    batteryPropsProvider: () -> Properties?
): String? {
    val showCurr = enabled()
    val showVolt = enabled()
    val showWatt = enabled()
    val showTemp = enabled()
    if (!showCurr && !showVolt && !showWatt && !showTemp) return null
    if (!isKeyguardCaller()) return null
    val values = ArrayList<String>(4)
    val props = batteryPropsProvider()
    values.add(formatMonitorOneDecimal(1f))
}
"""
        self.assertEqual([], self.mod.check_charging_info_hot_path(path, clean))

        missing_disabled = """
fun buildChargingInfoDetails(param: Any) {
    val values = ArrayList<String>(4)
    val props = batteryPropsProvider()
    values.add(formatMonitorOneDecimal(1f))
}
"""
        findings = self.mod.check_charging_info_hot_path(path, missing_disabled)
        self.assertEqual(1, len(findings))

        caller_after_alloc = """
fun buildChargingInfoDetails(
    isKeyguardCaller: () -> Boolean,
    batteryPropsProvider: () -> Properties?
): String? {
    if (!showCurr && !showVolt && !showWatt && !showTemp) return null
    val values = ArrayList<String>(4)
    val props = batteryPropsProvider()
    if (!isKeyguardCaller()) return null
}
"""
        findings = self.mod.check_charging_info_hot_path(path, caller_after_alloc)
        self.assertEqual(1, len(findings))

        uses_string_format = """
fun buildChargingInfoDetails(
    isKeyguardCaller: () -> Boolean,
    batteryPropsProvider: () -> Properties?
): String? {
    if (!showCurr && !showVolt && !showWatt && !showTemp) return null
    if (!isKeyguardCaller()) return null
    val values = ArrayList<String>(4)
    val props = batteryPropsProvider()
    values.add(String.format("%.1f", 1f))
}
"""
        findings = self.mod.check_charging_info_hot_path(path, uses_string_format)
        self.assertEqual(1, len(findings))

    def test_album_art_requires_detach_cleanup_and_owned_bitmap_release(self):
        path = self._source_path(
            "tv/withaibuild/customiuizer/mods/utils/LockScreenAlbumArtController.kt"
        )
        clean = """
current.bitmap === bitmap
onViewDetachedFromWindow(view)
removeAdditionalInstanceField(view, APPLIED_DRAWABLE_FIELD)
recycleIntermediate(blurred, art, processed)
recycleIntermediate(small, art, blurred)
val pixels = art.width.toLong() * art.height.toLong()
try { read() }
catch (oom: OutOfMemoryError) { throw oom }
catch (_: Throwable) { null }
"""
        self.assertEqual([], self.mod.check_album_art_memory_lifecycle(path, clean))

        unsafe = """
try { read() }
catch (_: Throwable) { null }
"""
        findings = self.mod.check_album_art_memory_lifecycle(path, unsafe)
        self.assertEqual(7, len(findings))

    def test_fast_blur_rejects_before_copy_and_accepts_null_config(self):
        path = self._source_path("tv/withaibuild/customiuizer/utils/HookUtils.kt")
        clean = """
fun fastBlur(sentBitmap: Bitmap, radius: Int): Bitmap? {
    if (radius < 1) return null
    val bitmap = sentBitmap.copy(sentBitmap.config ?: Bitmap.Config.ARGB_8888, true)
}
"""
        self.assertEqual([], self.mod.check_album_art_memory_lifecycle(path, clean))

        unsafe = """
fun fastBlur(sentBitmap: Bitmap, radius: Int): Bitmap? {
    val bitmap = sentBitmap.copy(sentBitmap.config!!, true)
    if (radius < 1) return null
}
"""
        findings = self.mod.check_album_art_memory_lifecycle(path, unsafe)
        self.assertEqual(2, len(findings))

    def test_nav_bar_dark_frames_skip_unchanged_drawable_loads(self):
        path = self._source_path("tv/withaibuild/customiuizer/mods/Controls.kt")
        clean = """
fun NavBarButtonsHook(param: Any) {
    val isDark = chain.getArg(0) as Float
    if (previousDark == isDark) return
    ModuleHelper.getModuleContext(navbar.context)
    setAdditionalInstanceField(navbar, NAV_BAR_DARK_STATE_FIELD, isDark)
    removeAdditionalInstanceField(navbar, NAV_BAR_DARK_STATE_FIELD)
}
"""
        self.assertEqual([], self.mod.check_nav_bar_dark_hot_path(path, clean))

        unsafe = """
fun NavBarButtonsHook(param: Any) {
    val isDark = chain.getArgs()[0] as Float
    ModuleHelper.getModuleContext(navbar.context)
}
"""
        findings = self.mod.check_nav_bar_dark_hot_path(path, unsafe)
        self.assertEqual(4, len(findings))

    def test_weather_data_lifecycle_rejects_strong_controller_capture(self):
        path = self._source_path("tv/withaibuild/customiuizer/mods/utils/WeatherDataController.kt")
        clean = """
private var updateTarget: WeakReference<Any>? = null
val appContext = context.applicationContext
updateTarget = WeakReference(clockController)
@Volatile
    var weatherInfo: String = ""
try {
    query()
} catch (oom: OutOfMemoryError) {
    throw oom
} catch (t: Throwable) {
    if (!queryFailureLogged) log(t)
}
"""
        self.assertEqual([], self.mod.check_weather_data_lifecycle(path, clean))

        unsafe = """
private var weakReferenceRunnable: Runnable? = null
private var context: Context? = null
try {
    query()
} catch (t: Throwable) {
}
"""
        findings = self.mod.check_weather_data_lifecycle(path, unsafe)
        self.assertEqual(7, len(findings))

    def test_coroutine_failure_handler_must_rethrow_oom(self):
        path = self._source_path("tv/withaibuild/customiuizer/mods/utils/ModuleHelper.kt")
        clean = """
val coroutineFailureHandler: CoroutineExceptionHandler =
    CoroutineExceptionHandler { _, throwable ->
        if (throwable is OutOfMemoryError) throw throwable
        log(throwable)
    }
"""
        self.assertEqual([], self.mod.check_weather_data_lifecycle(path, clean))

        unsafe = """
val coroutineFailureHandler: CoroutineExceptionHandler =
    CoroutineExceptionHandler { _, throwable -> log(throwable) }
"""
        findings = self.mod.check_weather_data_lifecycle(path, unsafe)
        self.assertEqual(1, len(findings))

    def test_weather_hook_boundary_must_rethrow_oom(self):
        path = self._source_path("tv/withaibuild/customiuizer/mods/SystemClockHooks.kt")
        clean = """
WeatherDataController.initContext(mContext, thisObject)
} catch (oom: OutOfMemoryError) {
    throw oom
} catch (t: Throwable) {
    log(t)
}
"""
        self.assertEqual([], self.mod.check_weather_data_lifecycle(path, clean))

        unsafe = """
WeatherDataController.initContext(mContext, thisObject)
} catch (t: Throwable) {
    log(t)
}
"""
        findings = self.mod.check_weather_data_lifecycle(path, unsafe)
        self.assertEqual(1, len(findings))

    def test_battery_indicator_skips_duplicate_work_and_owns_callbacks(self):
        path = self._source_path("tv/withaibuild/customiuizer/utils/BatteryIndicator.kt")
        clean = """
val charging = isCharging && !isCharged
if (mIsBeingCharged == charging) return
if (updatePosted) return
if (!post(updateRunnable)) updatePosted = false
ModuleHelper.registerOwnedReceiver(
ModuleHelper.unregisterOwnedReceiver(this, RECEIVER_KEY, broadcastReceiver)
removeCallbacks(updateRunnable)
CoroutineScope(SupervisorJob() + Dispatchers.Main + ModuleHelper.coroutineFailureHandler)
"""
        self.assertEqual([], self.mod.check_battery_indicator_lifecycle(path, clean))

        unsafe = """
if (mIsBeingCharged == isCharging && !isCharged) return
viewScope.launch { update() }
context.registerReceiver(broadcastReceiver, filter, flags)
"""
        findings = self.mod.check_battery_indicator_lifecycle(path, unsafe)
        self.assertEqual(11, len(findings))

    def test_preference_callback_only_enqueues_remote_write(self):
        path = self._source_path("tv/withaibuild/customiuizer/utils/XposedServiceManager.kt")
        clean = """
private val prefsChanged = Listener { sharedPreferences, key ->
    requestPreferenceWrite(sharedPreferences, key, generation)
}
val scope = Dispatchers.Default.limitedParallelism(1)
if (throwable is OutOfMemoryError) throw throwable
private fun requestMirrorPass(generation: Long, reason: String) {
    mirrorScope.launch { runMirror(generation, reason) }
}
"""
        self.assertEqual([], self.mod.check_preference_click_feedback(path, clean))

        unsafe = """
private val prefsChanged = Listener { sharedPreferences, key ->
    val value = sharedPreferences.all[key]
    val edit = remote.edit()
    edit.apply()
}
"""
        findings = self.mod.check_preference_click_feedback(path, unsafe)
        self.assertEqual(7, len(findings))

    def test_switch_inherits_row_pressed_state(self):
        path = self._source_path("tv/withaibuild/customiuizer/prefs/CheckBoxPreferenceEx.kt")
        clean = """
holder.findViewById(android.R.id.switch_widget)?.isDuplicateParentStateEnabled = true
"""
        self.assertEqual([], self.mod.check_preference_click_feedback(path, clean))
        self.assertEqual(2, len(self.mod.check_preference_click_feedback(path, "")))

    def test_reflection_cache_get_declared_method_oom_ok(self):
        text = """
    private fun resolveDependencyMethod(loaderState: LoaderState, classLoader: ClassLoader?): Method? {
        val depClass = XposedHelpers.findClassIfExists(dependencyClassName, classLoader)
        val method = if (depClass != null) {
            try {
                depClass.getDeclaredMethod("get", Class::class.java).apply { isAccessible = true }
            } catch (oom: OutOfMemoryError) {
                throw oom
            } catch (t: Throwable) {
                XposedHelpers.log(t)
                null
            }
        } else null
        loaderState.dependencyMethod = method
        loaderState.dependencyMethodResolved = true
        return method
    }
"""
        findings = self.mod.check_reflection_cache_get_declared_method_oom(
            self._source_path("tv/withaibuild/customiuizer/mods/utils/ReflectionCache.kt"),
            text,
        )
        self.assertEqual([], findings)

    def test_reflection_cache_get_declared_method_oom_wrong_order(self):
        text = """
    private fun resolveDependencyMethod(loaderState: LoaderState, classLoader: ClassLoader?): Method? {
        val depClass = XposedHelpers.findClassIfExists(dependencyClassName, classLoader)
        val method = if (depClass != null) {
            try {
                depClass.getDeclaredMethod("get", Class::class.java).apply { isAccessible = true }
            } catch (t: Throwable) {
                XposedHelpers.log(t)
                null
            } catch (oom: OutOfMemoryError) {
                throw oom
            }
        } else null
        loaderState.dependencyMethodResolved = true
        return method
    }
"""
        findings = self.mod.check_reflection_cache_get_declared_method_oom(
            self._source_path("tv/withaibuild/customiuizer/mods/utils/ReflectionCache.kt"),
            text,
        )
        self.assertEqual(1, len(findings))
        self.assertIn("precede", findings[0].detail)

    def test_docs_zero_object_wording(self):
        with tempfile.TemporaryDirectory() as tmp:
            docs_dir = Path(tmp) / "docs"
            docs_dir.mkdir()
            bad = docs_dir / "bad.md"
            bad.write_text("A disabled feature zero running objects is not allowed.\n", encoding="utf-8")
            good = docs_dir / "good.md"
            good.write_text("Zero FeatureDefinition / zero installer / zero Hook.\n", encoding="utf-8")

            findings = self.mod.check_docs_zero_object_wording(docs_dir)
            self.assertEqual(1, len(findings))
            self.assertIn(bad.name, str(findings[0].path))

            clean_dir = Path(tmp) / "clean"
            clean_dir.mkdir()
            (clean_dir / "ok.md").write_text("Only LazyFeatureSpec metadata and lightweight lambdas.\n", encoding="utf-8")
            self.assertEqual([], self.mod.check_docs_zero_object_wording(clean_dir))


if __name__ == "__main__":
    unittest.main()
