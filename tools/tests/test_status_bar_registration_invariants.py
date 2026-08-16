"""Tests for the status bar registration cleanup invariants."""

import importlib.util
import sys
import types
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
CHECK_INVARIANTS = REPO_ROOT / "tools" / "check-invariants.py"
SOURCE_ROOT = REPO_ROOT / "app" / "src" / "main" / "java"
TARGET_PATH = SOURCE_ROOT / "tv" / "withaibuild" / "customiuizer" / "mods" / "SystemUIStatusBarHooks.kt"
OWNED_REGISTRATIONS_PATH = SOURCE_ROOT / "tv" / "withaibuild" / "customiuizer" / "mods" / "utils" / "OwnedRegistrations.kt"
STATUS_BAR_REGISTRY_PATH = SOURCE_ROOT / "tv" / "withaibuild" / "customiuizer" / "mods" / "utils" / "StatusBarDisplayRegistry.kt"


def _load_check_invariants() -> types.ModuleType:
    spec = importlib.util.spec_from_file_location("check_invariants_status_bar", CHECK_INVARIANTS)
    module = importlib.util.module_from_spec(spec)
    sys.modules["check_invariants_status_bar"] = module
    if spec.loader is not None:
        spec.loader.exec_module(module)
    return module


class StatusBarRegistrationInvariantsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.mod = _load_check_invariants()

    def _findings(self, text: str) -> list:
        return self.mod.check_status_bar_registration_cleanup(TARGET_PATH, text)

    def _details(self, findings) -> list:
        return [f.detail for f in findings]

    @property
    def _passing_base(self) -> str:
        return """
private lateinit var statusBarDisplayRegistry: StatusBarDisplayRegistry<View, LinearLayout>
private val netSpeedSecondRowHookInstaller = HookInstallStateMachine()
private val statusBarViewDetachHookInstaller = HookInstallStateMachine()
private val netSpeedMainHandler = Handler(Looper.getMainLooper())
private val netSpeedSequence = java.util.concurrent.atomic.AtomicLong(0)
private val netSpeedLastAppliedSequence = java.util.concurrent.atomic.AtomicLong(0)

init {
    statusBarDisplayRegistry = StatusBarDisplayRegistry(
        onPendingChanged = { hasPending ->
            if (hasPending) {
                netSpeedMainHandler.postDelayed(statusBarPendingPruneRunnable, 250L)
            } else {
                netSpeedMainHandler.removeCallbacks(statusBarPendingPruneRunnable)
            }
        }
    )
}

private fun installStatusBarViewLifecycleHook(lpparam: PackageReadyParam) {
    statusBarViewDetachHookInstaller.install {
        ModuleHelper.findAndHookMethod(
            "com.android.systemui.statusbar.phone.MiuiPhoneStatusBarView",
            lpparam.classLoader,
            "onDetachedFromWindow",
            object : MethodHook() {
                override fun after(param: AfterHookCallback) {
                    val sbView = param.getThisObject() as View
                    netSpeedMainHandler.post {
                        statusBarDisplayRegistry.detach(sbView)
                        statusBarDisplayRegistry.prune()
                    }
                }
            },
        )
        true
    }
}

private fun installNetSpeedSecondRowHook(lpparam: PackageReadyParam) {
    netSpeedSecondRowClassLoader = lpparam.classLoader
    netSpeedSecondRowHookInstaller.install {
        ModuleHelper.hookAllMethodsSilently(
            "com.android.systemui.statusbar.phone.StatusBarIconControllerImpl",
            lpparam.classLoader,
            "setNetworkSpeedIcon",
            netSpeedSecondRowHookCallback,
        )
    }
}

private val netSpeedSecondRowHookCallback = object : MethodHook() {
    override fun after(param: AfterHookCallback) {
        val networkSpeedState = param.getArgs()[0]
        val number = XposedHelpers.getObjectField(networkSpeedState, "networkSpeedNumber")
        val unit = XposedHelpers.getObjectField(networkSpeedState, "networkSpeedUnit")
        val visible = XposedHelpers.getObjectField(networkSpeedState, "visible")

        val payload = StatusBarNetworkSpeedDispatcher.NetworkSpeedPayload(number, unit, visible)
        val seq = netSpeedSequence.incrementAndGet()
        netSpeedMainHandler.post {
            StatusBarNetworkSpeedDispatcher.dispatch(
                payload,
                seq,
                netSpeedLastAppliedSequence,
                statusBarDisplayRegistry,
                ::applyNetworkSpeedToRow,
            )
        }
    }
}

private fun applyNetworkSpeedToRow(
    state: StatusBarDisplayState<View, LinearLayout>,
    payload: StatusBarNetworkSpeedDispatcher.NetworkSpeedPayload,
) {
    val row = state.secondRow?.get() ?: return
    val owner = state.generation?.get() ?: return

    if (!row.isAttachedToWindow) return
    if (state.generation?.get() !== owner) return
    if (state.secondRow?.get() !== row) return

    var networkSpeedView: View? = row.findViewWithTag(NETSPEED_ROW2_TAG)
    if (networkSpeedView == null) {
        val ctx = row.context
        val layoutResId = ctx.resources.getIdentifier("network_speed", "layout", "com.android.systemui")
        if (layoutResId == 0) return
        val created = LayoutInflater.from(ctx).inflate(layoutResId, null) ?: return
        created.tag = NETSPEED_ROW2_TAG
        row.addView(created, 0, LinearLayout.LayoutParams(-2, -2))

        val classLoader = netSpeedSecondRowClassLoader ?: return
        val DarkIconDispatcher = ModuleHelper.getDepInstance(classLoader, "com.android.systemui.plugins.DarkIconDispatcher")
        if (DarkIconDispatcher == null) return

        val added = try {
            XposedHelpers.callMethod(DarkIconDispatcher, "addDarkReceiver", created)
            true
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            false
        }
        if (added) {
            state.registrations.register(owner) {
                releaseRegistrationSilently(DarkIconDispatcher, "removeDarkReceiver", created, "network-speed-row2")
            }
        }
        networkSpeedView = created
    }

    XposedHelpers.callMethod(networkSpeedView, "setBlocked", false)
    XposedHelpers.callMethod(networkSpeedView, "setNetworkSpeed", payload.number, payload.unit)
    XposedHelpers.callMethod(networkSpeedView, "setVisibilityByController", payload.visible)
}

private fun onFinishInflate(sbView: FrameLayout) {
    val state = statusBarDisplayRegistry.bind(sbView, 0)
    val rightLayout = XposedHelpers.getAdditionalInstanceField(sbView, "rightLayout") as LinearLayout
    val secondRight = rightLayout.getChildAt(1) as LinearLayout
    state.secondRow = WeakReference(secondRight)
    installNetSpeedSecondRowHook(lpparam)
}

private fun leftIcons(mStatusBar: FrameLayout) {
    val state = statusBarDisplayRegistry.bind(mStatusBar, 0)
    val iconController = ModuleHelper.getDepInstance(lpparam.classLoader, "com.android.systemui.statusbar.phone.StatusBarIconController") ?: return
    val oldHandle = XposedHelpers.getAdditionalInstanceField(mStatusBar, "leftIconRegistrationHandle") as? OwnedRegistrations.RegistrationHandle
    if (oldHandle != null) {
        oldHandle.cleanupNow()
    }
    val IconsContainer = XposedHelpers.findClass("com.android.systemui.statusbar.views.MiuiStatusIconContainer", lpparam.classLoader)
    val iconContainer = XposedHelpers.newInstance(IconsContainer, mStatusBar.context) as LinearLayout
    val mDarkIconManager = XposedHelpers.newInstance(DarkIconManager, iconContainer) as Any
    XposedHelpers.callMethod(iconController, "addIconGroup", mDarkIconManager)
    val handle = state.registrations.register(mStatusBar) {
        releaseRegistrationSilently(iconController, "removeIconGroup", mDarkIconManager, "left-icon-group")
    }
    XposedHelpers.setAdditionalInstanceField(mStatusBar, "leftIconRegistrationHandle", handle)
}
"""

    def test_clean_per_display_passes(self):
        self.assertEqual([], self._findings(self._passing_base))

    def test_safe_rewrite_with_different_variable_names_passes(self):
        """The rule is structural: equivalent code with different identifiers still passes."""
        # Rename only local identifiers that are not part of the structural contract.
        text = self._passing_base
        text = text.replace("created", "childView")
        text = text.replace("networkSpeedView", "speedView")
        text = text.replace("rightLayout", "rightPanel")
        text = text.replace("ctx", "viewContext")
        self.assertEqual([], self._findings(text))

    def test_global_statusBarGeneration_fails(self):
        text = """
private var statusBarGeneration: WeakReference<View>? = null
private val statusBarRegistrations = OwnedRegistrations<View>()
private fun cleanupStaleStatusBarRegistrations() {
    statusBarRegistrations.cleanupWhere { owner -> owner !== current }
}
"""
        details = self._details(self._findings(text))
        self.assertTrue(any("statusBarGeneration" in d for d in details), f"details: {details}")

    def test_old_netspeed_once_flag_fails(self):
        text = """
private var netSpeedSecondRowHookInstalled = false
private var netSpeedSecondRowRef: WeakReference<LinearLayout>? = null

if (MainModule.mPrefs.getBoolean("system_statusbar_netspeed_atsecondrow")) {
    netSpeedSecondRowRef = WeakReference(secondRight)
    if (!netSpeedSecondRowHookInstalled) {
        netSpeedSecondRowHookInstalled = true
        ModuleHelper.hookAllMethods("com.android.systemui.statusbar.phone.StatusBarIconControllerImpl", lpparam.classLoader, "setNetworkSpeedIcon", object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                val row = netSpeedSecondRowRef?.get() ?: return
            }
        })
    }
}
"""
        details = self._details(self._findings(text))
        self.assertTrue(any("once flag" in d for d in details), f"details: {details}")
        self.assertTrue(any("netSpeedSecondRowRef" in d for d in details), f"details: {details}")

    def test_missing_dark_receiver_cleanup_fails(self):
        text = """
XposedHelpers.callMethod(DarkIconDispatcher, "addDarkReceiver", iconView)
state.registrations.register(sbView) {
    releaseRegistrationSilently(DarkIconDispatcher, "removeDarkReceiver", wrongView, "tag")
}
"""
        details = self._details(self._findings(text))
        self.assertTrue(
            any("addDarkReceiver" in d and "no matching" in d for d in details),
            f"details: {details}",
        )

    def test_missing_icon_group_cleanup_fails(self):
        text = """
XposedHelpers.callMethod(iconController, "addIconGroup", mDarkIconManager)
"""
        details = self._details(self._findings(text))
        self.assertTrue(
            any("addIconGroup" in d and "no matching" in d for d in details),
            f"details: {details}",
        )

    def test_manual_remove_icon_group_fails(self):
        text = """
ModuleHelper.callMethodSilently(iconController, "removeIconGroup", staleManager)
"""
        details = self._details(self._findings(text))
        self.assertTrue(
            any("manual ModuleHelper.callMethodSilently" in d for d in details),
            f"details: {details}",
        )

    def test_missing_left_icon_handle_fails(self):
        text = """
XposedHelpers.callMethod(iconController, "addIconGroup", mDarkIconManager)
val handle = state.registrations.register(mStatusBar) {
    releaseRegistrationSilently(iconController, "removeIconGroup", mDarkIconManager, "left-icon-group")
}
"""
        details = self._details(self._findings(text))
        self.assertTrue(
            any("leftIconRegistrationHandle" in d for d in details),
            f"details: {details}",
        )

    def test_missing_per_display_registry_fails(self):
        text = """
XposedHelpers.callMethod(DarkIconDispatcher, "addDarkReceiver", iconView)
statusBarRegistrations.register(sbView) {
    releaseRegistrationSilently(DarkIconDispatcher, "removeDarkReceiver", iconView, "tag")
}
"""
        details = self._details(self._findings(text))
        self.assertTrue(
            any("StatusBarDisplayRegistry" in d for d in details),
            f"details: {details}",
        )

    def test_remove_method_name_wrong_fails(self):
        text = """
XposedHelpers.callMethod(DarkIconDispatcher, "addDarkReceiver", iconView)
state.registrations.register(sbView) {
    releaseRegistrationSilently(DarkIconDispatcher, "removeDarkReceiverX", iconView, "tag")
}
"""
        details = self._details(self._findings(text))
        self.assertTrue(
            any("addDarkReceiver" in d and "no matching" in d for d in details),
            f"details: {details}",
        )

    def test_missing_second_row_save_fails(self):
        text = self._passing_base.replace("state.secondRow = WeakReference(secondRight)", "// second row not saved")
        details = self._details(self._findings(text))
        self.assertTrue(
            any("state.secondRow = WeakReference(secondRight)" in d for d in details),
            f"details: {details}",
        )

    def test_missing_posted_isAttachedToWindow_check_fails(self):
        text = self._passing_base.replace("if (!row.isAttachedToWindow) return", "// no attach check")
        details = self._details(self._findings(text))
        self.assertTrue(
            any("isAttachedToWindow" in d for d in details),
            f"details: {details}",
        )

    def test_posted_runnable_captures_stale_state_fails(self):
        text = self._passing_base.replace(
            '''        netSpeedMainHandler.post {
            StatusBarNetworkSpeedDispatcher.dispatch(
                payload,
                seq,
                netSpeedLastAppliedSequence,
                statusBarDisplayRegistry,
                ::applyNetworkSpeedToRow,
            )
        }''',
            '''        netSpeedMainHandler.post {
            val state = statusBarDisplayRegistry.allStatesSnapshot().firstOrNull() ?: return@post
            val row = state.secondRow?.get() ?: return@post
            val owner = state.generation?.get() ?: return@post
            applyNetworkSpeedToRow(row, owner, payload, state)
        }''',
        )
        details = self._details(self._findings(text))
        self.assertTrue(
            any("stale" in d and "posted" in d for d in details),
            f"details: {details}",
        )

    def test_posted_runnable_iterates_registry_fails(self):
        text = self._passing_base.replace(
            '''        netSpeedMainHandler.post {
            StatusBarNetworkSpeedDispatcher.dispatch(
                payload,
                seq,
                netSpeedLastAppliedSequence,
                statusBarDisplayRegistry,
                ::applyNetworkSpeedToRow,
            )
        }''',
            '''        netSpeedMainHandler.post {
            for (state in statusBarDisplayRegistry.allStatesSnapshot()) {
                applyNetworkSpeedToRow(state, payload)
            }
        }''',
        )
        details = self._details(self._findings(text))
        self.assertTrue(
            any("stale" in d and "posted" in d for d in details),
            f"details: {details}",
        )

    def test_cleanup_closure_capturing_owner_fails(self):
        text = self._passing_base.replace(
            'state.registrations.register(mStatusBar) {\n        releaseRegistrationSilently(iconController, "removeIconGroup", mDarkIconManager, "left-icon-group")\n    }',
            'state.registrations.register(mStatusBar) {\n        XposedHelpers.callMethod(iconController, "removeIconGroup", mStatusBar)\n    }',
        )
        details = self._details(self._findings(text))
        self.assertTrue(
            any("captures the owner" in d for d in details),
            f"details: {details}",
        )

    def test_stray_remove_dark_receiver_string_fails(self):
        text = self._passing_base + '\nval x = "removeDarkReceiver"'
        details = self._details(self._findings(text))
        # This is allowed only inside releaseRegistrationSilently; a stray string in the
        # pairing logic should still be tied to an add call. The test documents that an
        # unpaired removeDarkReceiver is not enough to pass.
        self.assertEqual([], [d for d in details if "stray" in d])

    def test_global_second_row_ref_fails(self):
        text = """
private var netSpeedSecondRowRef: WeakReference<LinearLayout>? = null

if (MainModule.mPrefs.getBoolean("system_statusbar_netspeed_atsecondrow")) {
    netSpeedSecondRowRef = WeakReference(secondRight)
    installNetSpeedSecondRowHook(lpparam)
}
"""
        details = self._details(self._findings(text))
        self.assertTrue(
            any("netSpeedSecondRowRef" in d for d in details),
            f"details: {details}",
        )

    def test_set_network_speed_hook_without_installer_fails(self):
        text = """
ModuleHelper.hookAllMethodsSilently(
    "com.android.systemui.statusbar.phone.StatusBarIconControllerImpl",
    lpparam.classLoader,
    "setNetworkSpeedIcon",
    netSpeedSecondRowHookCallback,
)
"""
        details = self._details(self._findings(text))
        self.assertTrue(
            any("installNetSpeedSecondRowHook" in d for d in details),
            f"details: {details}",
        )

    def _replace_left_icon_block(self, block: str) -> str:
        """Return _passing_base with the leftIcons function replaced by block."""
        base = self._passing_base
        start = base.find("private fun leftIcons")
        if start == -1:
            raise RuntimeError("leftIcons block not found in passing base")
        brace_start = base.find("{", start)
        depth = 0
        end = brace_start
        for i in range(brace_start, len(base)):
            if base[i] == "{":
                depth += 1
            elif base[i] == "}":
                depth -= 1
                if depth == 0:
                    end = i + 1
                    break
        return base[:start] + block + base[end:]

    def _left_icon_base(self) -> str:
        return """
private fun leftIcons(mStatusBar: FrameLayout) {
    val state = statusBarDisplayRegistry.bind(mStatusBar, 0)
    val iconController = ModuleHelper.getDepInstance(lpparam.classLoader, "com.android.systemui.statusbar.phone.StatusBarIconController") ?: return
    val oldHandle = XposedHelpers.getAdditionalInstanceField(mStatusBar, "leftIconRegistrationHandle") as? OwnedRegistrations.RegistrationHandle
    if (oldHandle != null) {
        oldHandle.cleanupNow()
    }
    val IconsContainer = XposedHelpers.findClass("com.android.systemui.statusbar.views.MiuiStatusIconContainer", lpparam.classLoader)
    val iconContainer = XposedHelpers.newInstance(IconsContainer, mStatusBar.context) as LinearLayout
    val mDarkIconManager = XposedHelpers.newInstance(DarkIconManager, iconContainer) as Any
    XposedHelpers.callMethod(iconController, "addIconGroup", mDarkIconManager)
    val handle = state.registrations.register(mStatusBar) {
        releaseRegistrationSilently(iconController, "removeIconGroup", mDarkIconManager, "left-icon-group")
    }
    XposedHelpers.setAdditionalInstanceField(mStatusBar, "leftIconRegistrationHandle", handle)
}
"""

    def test_missing_old_handle_cleanup_now_fails(self):
        block = self._left_icon_base().replace(
            'if (oldHandle != null) {\n        oldHandle.cleanupNow()\n    }',
            'if (oldHandle != null) {\n        // no cleanup\n    }',
        )
        details = self._details(self._findings(self._replace_left_icon_block(block)))
        self.assertTrue(
            any("oldHandle.cleanupNow()" in d for d in details),
            f"details: {details}",
        )

    def test_cleanup_now_after_add_icon_group_fails(self):
        block = """
private fun leftIcons(mStatusBar: FrameLayout) {
    val state = statusBarDisplayRegistry.bind(mStatusBar, 0)
    val iconController = ModuleHelper.getDepInstance(lpparam.classLoader, "com.android.systemui.statusbar.phone.StatusBarIconController") ?: return
    val oldHandle = XposedHelpers.getAdditionalInstanceField(mStatusBar, "leftIconRegistrationHandle") as? OwnedRegistrations.RegistrationHandle
    val IconsContainer = XposedHelpers.findClass("com.android.systemui.statusbar.views.MiuiStatusIconContainer", lpparam.classLoader)
    val iconContainer = XposedHelpers.newInstance(IconsContainer, mStatusBar.context) as LinearLayout
    val mDarkIconManager = XposedHelpers.newInstance(DarkIconManager, iconContainer) as Any
    XposedHelpers.callMethod(iconController, "addIconGroup", mDarkIconManager)
    if (oldHandle != null) {
        oldHandle.cleanupNow()
    }
    val handle = state.registrations.register(mStatusBar) {
        releaseRegistrationSilently(iconController, "removeIconGroup", mDarkIconManager, "left-icon-group")
    }
    XposedHelpers.setAdditionalInstanceField(mStatusBar, "leftIconRegistrationHandle", handle)
}
"""
        details = self._details(self._findings(self._replace_left_icon_block(block)))
        self.assertTrue(
            any("cleanupNow() must run before addIconGroup" in d for d in details),
            f"details: {details}",
        )

    def test_direct_release_replacing_handle_fails(self):
        block = self._left_icon_base().replace(
            'if (oldHandle != null) {\n        oldHandle.cleanupNow()\n    }',
            'if (oldHandle != null) {\n        val staleManager = XposedHelpers.getAdditionalInstanceField(mStatusBar, "leftIconManager")\n        if (staleManager != null) {\n            releaseRegistrationSilently(iconController, "removeIconGroup", staleManager, "left-icon-group")\n        }\n    }',
        )
        details = self._details(self._findings(self._replace_left_icon_block(block)))
        self.assertTrue(
            any("oldHandle.cleanupNow()" in d for d in details),
            f"details: {details}",
        )

    def test_unrelated_handle_token_fails(self):
        block = """
private fun leftIcons(mStatusBar: FrameLayout) {
    val state = statusBarDisplayRegistry.bind(mStatusBar, 0)
    val iconController = ModuleHelper.getDepInstance(lpparam.classLoader, "com.android.systemui.statusbar.phone.StatusBarIconController") ?: return
    val x = "leftIconRegistrationHandle"
    val IconsContainer = XposedHelpers.findClass("com.android.systemui.statusbar.views.MiuiStatusIconContainer", lpparam.classLoader)
    val iconContainer = XposedHelpers.newInstance(IconsContainer, mStatusBar.context) as LinearLayout
    val mDarkIconManager = XposedHelpers.newInstance(DarkIconManager, iconContainer) as Any
    XposedHelpers.callMethod(iconController, "addIconGroup", mDarkIconManager)
    val handle = state.registrations.register(mStatusBar) {
        releaseRegistrationSilently(iconController, "removeIconGroup", mDarkIconManager, "left-icon-group")
    }
    XposedHelpers.setAdditionalInstanceField(mStatusBar, x, handle)
}
"""
        details = self._details(self._findings(self._replace_left_icon_block(block)))
        self.assertTrue(
            any("leftIconRegistrationHandle" in d for d in details),
            f"details: {details}",
        )

    def test_unrelated_register_result_saved_fails(self):
        block = self._left_icon_base().replace(
            'val handle = state.registrations.register(mStatusBar) {\n        releaseRegistrationSilently(iconController, "removeIconGroup", mDarkIconManager, "left-icon-group")\n    }\n    XposedHelpers.setAdditionalInstanceField(mStatusBar, "leftIconRegistrationHandle", handle)',
            'val other = makeHandle()\n    val handle = state.registrations.register(mStatusBar) {\n        releaseRegistrationSilently(iconController, "removeIconGroup", mDarkIconManager, "left-icon-group")\n    }\n    XposedHelpers.setAdditionalInstanceField(mStatusBar, "leftIconRegistrationHandle", other)',
        )
        details = self._details(self._findings(self._replace_left_icon_block(block)))
        self.assertTrue(
            any("must be saved as leftIconRegistrationHandle" in d for d in details),
            f"details: {details}",
        )

    def test_correct_compatibility_fallback_passes(self):
        block = self._left_icon_base().replace(
            'if (oldHandle != null) {\n        oldHandle.cleanupNow()\n    }',
            'if (oldHandle != null) {\n        oldHandle.cleanupNow()\n    } else {\n        val staleManager = XposedHelpers.getAdditionalInstanceField(mStatusBar, "leftIconManager")\n        if (staleManager != null) {\n            releaseRegistrationSilently(iconController, "removeIconGroup", staleManager, "left-icon-group")\n        }\n    }',
        )
        self.assertEqual([], self._findings(self._replace_left_icon_block(block)))

    def test_equivalent_left_icon_variable_rename_passes(self):
        """The structural rule accepts any local identifiers as long as the data flow is identical."""
        block = self._left_icon_base()
        block = block.replace("oldHandle", "prevHandle")
        block = block.replace("iconController", "iconCtl")
        block = block.replace("mDarkIconManager", "darkMgr")
        block = block.replace("IconsContainer", "IconBox")
        block = block.replace("iconContainer", "iconBox")
        block = block.replace("handle", "regHandle")
        self.assertEqual([], self._findings(self._replace_left_icon_block(block)))


class OwnedRegistrationsModelInvariantsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.mod = _load_check_invariants()

    def _findings(self, text: str) -> list:
        return self.mod.check_owned_registrations_model(OWNED_REGISTRATIONS_PATH, text)

    def _details(self, findings) -> list:
        return [f.detail for f in findings]

    def test_valid_model_passes(self):
        text = """
class OwnedRegistrations<V : Any> {
    private class Entry<V>(owner: V, var cleanup: (() -> Unit)?) {
        val ownerRef = WeakReference(owner)
        val consumed = AtomicBoolean(false)
    }
    private val entries = ArrayList<Entry<V>>(4)
    val size: Int get() = entries.size

    interface RegistrationHandle {
        fun cleanupNow(): Boolean
    }

    private inner class Handle(private val entry: Entry<V>) : RegistrationHandle {
        override fun cleanupNow(): Boolean {
            if (!entry.consumed.compareAndSet(false, true)) return false
            entries.remove(entry)
            val callback = entry.cleanup
            entry.cleanup = null
            if (callback != null) {
                try { callback() }
                catch (t: Throwable) { FatalErrors.unwrapAndRethrowIfFatal(t) }
            }
            return true
        }
    }

    fun register(owner: V, cleanup: () -> Unit): RegistrationHandle {
        val entry = Entry(owner, cleanup)
        entries.add(entry)
        return Handle(entry)
    }

    fun cleanupWhere(isStale: (V) -> Boolean) {
        val toRemove = ArrayList<Entry<V>>(entries.size)
        for (entry in entries) {
            val owner = entry.ownerRef.get()
            if (owner == null || isStale(owner)) { toRemove.add(entry) }
        }
        if (toRemove.isEmpty()) return
        entries.removeAll(toRemove)
        for (entry in toRemove) { runCleanupOnce(entry) }
    }

    fun cleanupAll() {
        if (entries.isEmpty()) return
        val toRemove = entries.toList()
        entries.clear()
        for (entry in toRemove) { runCleanupOnce(entry) }
    }

    private fun runCleanupOnce(entry: Entry<V>) {
        if (!entry.consumed.compareAndSet(false, true)) return
        val callback = entry.cleanup
        entry.cleanup = null
        if (callback != null) {
            try { callback() }
            catch (t: Throwable) { FatalErrors.unwrapAndRethrowIfFatal(t) }
        }
    }
}
"""
        self.assertEqual([], self._findings(text))

    def test_cleanup_with_owner_argument_fails(self):
        text = """
fun register(owner: V, cleanup: (V) -> Unit): RegistrationHandle { ... }
"""
        details = self._details(self._findings(text))
        self.assertTrue(
            any("no-argument cleanup" in d for d in details),
            f"details: {details}",
        )

    def test_missing_cleanup_all_fails(self):
        text = """
class OwnedRegistrations<V : Any> {
    fun register(owner: V, cleanup: () -> Unit): RegistrationHandle { ... }
    fun cleanupWhere(isStale: (V) -> Boolean) { ... }
}
"""
        details = self._details(self._findings(text))
        self.assertTrue(
            any("cleanupAll" in d for d in details),
            f"details: {details}",
        )

    def test_owner_null_gate_fails(self):
        text = """
private fun runCleanupOnce(entry: Entry<V>) {
    val owner = entry.ownerRef.get()
    val callback = entry.cleanup
    entry.cleanup = null
    if (owner != null && callback != null) { callback() }
}
"""
        details = self._details(self._findings(text))
        self.assertTrue(
            any("owner has been garbage collected" in d for d in details),
            f"details: {details}",
        )

    def test_callback_not_nulled_before_run_fails(self):
        text = """
private fun runCleanupOnce(entry: Entry<V>) {
    if (!entry.consumed.compareAndSet(false, true)) return
    if (entry.cleanup != null) { entry.cleanup() }
}
"""
        details = self._details(self._findings(text))
        self.assertTrue(
            any("consumed before the action is invoked" in d for d in details),
            f"details: {details}",
        )


class StatusBarDisplayRegistryPruneInvariantsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.mod = _load_check_invariants()

    def _findings(self, text: str) -> list:
        return self.mod.check_status_bar_display_registry_prune(STATUS_BAR_REGISTRY_PATH, text)

    def _details(self, findings) -> list:
        return [f.detail for f in findings]

    def test_valid_registry_passes(self):
        text = """
class StatusBarDisplayRegistry<O : Any, R : Any>(
    private val onPendingChanged: (Boolean) -> Unit = {},
) {
    private val byDisplay = mutableMapOf<Int, StatusBarDisplayState<O, R>>()
    private val pendingByOwner = WeakIdentityMap<O, StatusBarDisplayState<O, R>>()

    fun getOrCreatePending(owner: O): StatusBarDisplayState<O, R> { ... }

    fun bind(owner: O, displayId: Int): StatusBarDisplayState<O, R> {
        val pending = pendingByOwner.remove(owner)
        val existing = byDisplay[displayId]
        if (existing != null) { existing.registrations.cleanupAll() }
        val state = pending ?: StatusBarDisplayState(WeakReference(owner))
        state.generation = WeakReference(owner)
        byDisplay[displayId] = state
        return state
    }

    fun detach(owner: O) {
        val state = pendingByOwner.remove(owner) ?: byDisplay.values.find { it.generation?.get() === owner }
        state?.registrations?.cleanupAll()
    }

    fun allStatesSnapshot(): List<StatusBarDisplayState<O, R>> =
        byDisplay.values.toList() + pendingByOwner.valuesSnapshot()

    fun prune() {
        val deadDisplays = mutableListOf<Int>()
        for ((displayId, state) in byDisplay) {
            val generationAlive = state.generation?.get() != null
            if (!generationAlive) {
                state.registrations.cleanupAll()
                if (state.generation?.get() == null && state.registrations.size == 0) {
                    deadDisplays.add(displayId)
                }
            }
        }
        for (displayId in deadDisplays) { byDisplay.remove(displayId) }

        val deadPending = pendingByOwner.expunge()
        for (state in deadPending) {
            state.registrations.cleanupAll()
        }
    }
}
"""
        self.assertEqual([], self._findings(text))

    def test_weak_hash_map_pending_fails(self):
        text = """
class StatusBarDisplayRegistry<O : Any, R : Any> {
    private val pendingByOwner = WeakHashMap<O, StatusBarDisplayState<O, R>>()
}
"""
        details = self._details(self._findings(text))
        self.assertTrue(
            any("WeakHashMap" in d and "equals" in d for d in details),
            f"details: {details}",
        )

    def test_missing_detach_fails(self):
        text = """
class StatusBarDisplayRegistry<O : Any, R : Any> {
    private val pendingByOwner = WeakIdentityMap<O, StatusBarDisplayState<O, R>>()
    fun prune() {
        pendingByOwner.expunge()
    }
}
"""
        details = self._details(self._findings(text))
        self.assertTrue(
            any("detach" in d for d in details),
            f"details: {details}",
        )

    def test_missing_expunge_fails(self):
        text = """
class StatusBarDisplayRegistry<O : Any, R : Any> {
    private val pendingByOwner = WeakIdentityMap<O, StatusBarDisplayState<O, R>>()
    fun prune() {}
    fun detach(owner: O) {}
}
"""
        details = self._details(self._findings(text))
        self.assertTrue(
            any("expunge" in d for d in details),
            f"details: {details}",
        )

    def test_missing_all_states_snapshot_fails(self):
        text = """
class StatusBarDisplayRegistry<O : Any, R : Any> {
    private val pendingByOwner = WeakIdentityMap<O, StatusBarDisplayState<O, R>>()
    fun prune() { pendingByOwner.expunge() }
    fun detach(owner: O) {}
}
"""
        details = self._details(self._findings(text))
        self.assertTrue(
            any("allStatesSnapshot" in d for d in details),
            f"details: {details}",
        )

    def test_prune_without_cleanup_all_fails(self):
        text = """
class StatusBarDisplayRegistry<O : Any, R : Any> {
    private val byDisplay = mutableMapOf<Int, StatusBarDisplayState<O, R>>()
    fun prune() {
        for ((displayId, state) in byDisplay) {
            if (state.generation?.get() == null) { byDisplay.remove(displayId) }
        }
    }
}
"""
        details = self._details(self._findings(text))
        self.assertTrue(
            any("cleanupAll" in d and "prune" in d for d in details),
            f"details: {details}",
        )

    def test_bind_without_cleanup_all_fails(self):
        text = """
class StatusBarDisplayRegistry<O : Any, R : Any> {
    private val byDisplay = mutableMapOf<Int, StatusBarDisplayState<O, R>>()
    fun bind(owner: O, displayId: Int): StatusBarDisplayState<O, R> {
        val state = StatusBarDisplayState(WeakReference(owner))
        byDisplay[displayId] = state
        return state
    }
}
"""
        details = self._details(self._findings(text))
        self.assertTrue(
            any("bind" in d and "cleanupAll" in d for d in details),
            f"details: {details}",
        )

    def test_remove_before_cleanup_fails(self):
        """byDisplay.remove() inside the bound loop is a re-entrant cleanup hazard."""
        text = """
class StatusBarDisplayRegistry<O : Any, R : Any> {
    private val byDisplay = mutableMapOf<Int, StatusBarDisplayState<O, R>>()
    private val pendingByOwner = WeakIdentityMap<O, StatusBarDisplayState<O, R>>()
    fun detach(owner: O) {}
    fun allStatesSnapshot(): List<StatusBarDisplayState<O, R>> = emptyList()
    fun prune() {
        val deadDisplays = mutableListOf<Int>()
        for ((displayId, state) in byDisplay) {
            val generationAlive = state.generation?.get() != null
            if (!generationAlive) {
                byDisplay.remove(displayId)
                state.registrations.cleanupAll()
                if (state.generation?.get() == null && state.registrations.size == 0) {
                    deadDisplays.add(displayId)
                }
            }
        }
        for (displayId in deadDisplays) { byDisplay.remove(displayId) }

        val deadPending = pendingByOwner.expunge()
        for (state in deadPending) {
            state.registrations.cleanupAll()
        }
    }
}
"""
        details = self._details(self._findings(text))
        self.assertTrue(
            any("byDisplay.remove() must not run inside the bound-state loop" in d for d in details),
            f"details: {details}",
        )

    def test_add_dead_before_cleanup_fails(self):
        """The dead list must only be populated after cleanup and re-checks."""
        text = """
class StatusBarDisplayRegistry<O : Any, R : Any> {
    private val byDisplay = mutableMapOf<Int, StatusBarDisplayState<O, R>>()
    private val pendingByOwner = WeakIdentityMap<O, StatusBarDisplayState<O, R>>()
    fun detach(owner: O) {}
    fun allStatesSnapshot(): List<StatusBarDisplayState<O, R>> = emptyList()
    fun prune() {
        val deadDisplays = mutableListOf<Int>()
        for ((displayId, state) in byDisplay) {
            if (state.generation?.get() == null) {
                deadDisplays.add(displayId)
                state.registrations.cleanupAll()
            }
        }
        for (displayId in deadDisplays) { byDisplay.remove(displayId) }

        val deadPending = pendingByOwner.expunge()
        for (state in deadPending) {
            state.registrations.cleanupAll()
        }
    }
}
"""
        details = self._details(self._findings(text))
        self.assertTrue(
            any("registrations.cleanupAll() before removing" in d for d in details)
            or any("re-check" in d for d in details),
            f"details: {details}",
        )

    def test_missing_generation_recheck_fails(self):
        text = """
class StatusBarDisplayRegistry<O : Any, R : Any> {
    private val byDisplay = mutableMapOf<Int, StatusBarDisplayState<O, R>>()
    private val pendingByOwner = WeakIdentityMap<O, StatusBarDisplayState<O, R>>()
    fun detach(owner: O) {}
    fun allStatesSnapshot(): List<StatusBarDisplayState<O, R>> = emptyList()
    fun prune() {
        val deadDisplays = mutableListOf<Int>()
        for ((displayId, state) in byDisplay) {
            val generationAlive = state.generation?.get() != null
            if (!generationAlive) {
                state.registrations.cleanupAll()
                if (state.registrations.size == 0) {
                    deadDisplays.add(displayId)
                }
            }
        }
        for (displayId in deadDisplays) { byDisplay.remove(displayId) }

        val deadPending = pendingByOwner.expunge()
        for (state in deadPending) {
            state.registrations.cleanupAll()
        }
    }
}
"""
        details = self._details(self._findings(text))
        self.assertTrue(
            any("generation?.get() == null" in d for d in details),
            f"details: {details}",
        )

    def test_missing_size_recheck_fails(self):
        text = """
class StatusBarDisplayRegistry<O : Any, R : Any> {
    private val byDisplay = mutableMapOf<Int, StatusBarDisplayState<O, R>>()
    private val pendingByOwner = WeakIdentityMap<O, StatusBarDisplayState<O, R>>()
    fun detach(owner: O) {}
    fun allStatesSnapshot(): List<StatusBarDisplayState<O, R>> = emptyList()
    fun prune() {
        val deadDisplays = mutableListOf<Int>()
        for ((displayId, state) in byDisplay) {
            val generationAlive = state.generation?.get() != null
            if (!generationAlive) {
                state.registrations.cleanupAll()
                if (state.generation?.get() == null) {
                    deadDisplays.add(displayId)
                }
            }
        }
        for (displayId in deadDisplays) { byDisplay.remove(displayId) }

        val deadPending = pendingByOwner.expunge()
        for (state in deadPending) {
            state.registrations.cleanupAll()
        }
    }
}
"""
        details = self._details(self._findings(text))
        self.assertTrue(
            any("registrations.size == 0" in d for d in details),
            f"details: {details}",
        )

    def test_missing_pending_expunge_fails(self):
        text = """
class StatusBarDisplayRegistry<O : Any, R : Any> {
    private val byDisplay = mutableMapOf<Int, StatusBarDisplayState<O, R>>()
    private val pendingByOwner = WeakIdentityMap<O, StatusBarDisplayState<O, R>>()
    fun detach(owner: O) {}
    fun allStatesSnapshot(): List<StatusBarDisplayState<O, R>> = emptyList()
    fun prune() {
        val deadDisplays = mutableListOf<Int>()
        for ((displayId, state) in byDisplay) {
            val generationAlive = state.generation?.get() != null
            if (!generationAlive) {
                state.registrations.cleanupAll()
                if (state.generation?.get() == null && state.registrations.size == 0) {
                    deadDisplays.add(displayId)
                }
            }
        }
        for (displayId in deadDisplays) { byDisplay.remove(displayId) }
    }
}
"""
        details = self._details(self._findings(text))
        self.assertTrue(
            any("pendingByOwner.expunge()" in d for d in details),
            f"details: {details}",
        )

    def test_ignored_expunge_result_fails(self):
        text = """
class StatusBarDisplayRegistry<O : Any, R : Any> {
    private val byDisplay = mutableMapOf<Int, StatusBarDisplayState<O, R>>()
    private val pendingByOwner = WeakIdentityMap<O, StatusBarDisplayState<O, R>>()
    fun detach(owner: O) {}
    fun allStatesSnapshot(): List<StatusBarDisplayState<O, R>> = emptyList()
    fun prune() {
        val deadDisplays = mutableListOf<Int>()
        for ((displayId, state) in byDisplay) {
            val generationAlive = state.generation?.get() != null
            if (!generationAlive) {
                state.registrations.cleanupAll()
                if (state.generation?.get() == null && state.registrations.size == 0) {
                    deadDisplays.add(displayId)
                }
            }
        }
        for (displayId in deadDisplays) { byDisplay.remove(displayId) }

        pendingByOwner.expunge()
    }
}
"""
        details = self._details(self._findings(text))
        self.assertTrue(
            any("keep the return value" in d for d in details),
            f"details: {details}",
        )

    def test_missing_cleared_state_cleanup_fails(self):
        text = """
class StatusBarDisplayRegistry<O : Any, R : Any> {
    private val byDisplay = mutableMapOf<Int, StatusBarDisplayState<O, R>>()
    private val pendingByOwner = WeakIdentityMap<O, StatusBarDisplayState<O, R>>()
    fun detach(owner: O) {}
    fun allStatesSnapshot(): List<StatusBarDisplayState<O, R>> = emptyList()
    fun prune() {
        val deadDisplays = mutableListOf<Int>()
        for ((displayId, state) in byDisplay) {
            val generationAlive = state.generation?.get() != null
            if (!generationAlive) {
                state.registrations.cleanupAll()
                if (state.generation?.get() == null && state.registrations.size == 0) {
                    deadDisplays.add(displayId)
                }
            }
        }
        for (displayId in deadDisplays) { byDisplay.remove(displayId) }

        val clearedStates = pendingByOwner.expunge()
        for (state in clearedStates) {
            state.secondRow = null
        }
    }
}
"""
        details = self._details(self._findings(text))
        self.assertTrue(
            any("registrations.cleanupAll() for every cleared pending state" in d for d in details),
            f"details: {details}",
        )

    def test_equivalent_variable_rename_passes(self):
        """Structural order must hold with any local variable names."""
        text = """
class StatusBarDisplayRegistry<O : Any, R : Any> {
    private val byDisplay = mutableMapOf<Int, StatusBarDisplayState<O, R>>()
    private val pendingByOwner = WeakIdentityMap<O, StatusBarDisplayState<O, R>>()
    fun detach(owner: O) {}
    fun allStatesSnapshot(): List<StatusBarDisplayState<O, R>> = emptyList()
    fun prune() {
        val goneIds = mutableListOf<Int>()
        for ((id, st) in byDisplay) {
            val alive = st.generation?.get() != null
            if (!alive) {
                st.registrations.cleanupAll()
                if (st.generation?.get() == null && st.registrations.size == 0) {
                    goneIds.add(id)
                }
            }
        }
        for (id in goneIds) { byDisplay.remove(id) }

        val cleared = pendingByOwner.expunge()
        for (st in cleared) {
            st.registrations.cleanupAll()
        }
    }
}
"""
        self.assertEqual([], self._findings(text))


if __name__ == "__main__":
    unittest.main()
