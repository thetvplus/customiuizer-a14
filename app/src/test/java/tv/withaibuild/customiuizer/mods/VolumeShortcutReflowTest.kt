package tv.withaibuild.customiuizer.mods

import android.app.Application
import android.content.ContextWrapper
import android.content.res.Resources
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.android.systemui.miui.ViewStateGroup
import com.android.systemui.miui.volume.MiuiRingerModeLayout.RingerButtonHelper
import com.android.systemui.miui.volume.MiuiVolumeDialogView
import io.github.libxposed.api.XposedInterface
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.utils.PreferenceObserverRegistry
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import java.lang.reflect.Executable
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class VolumeShortcutReflowTest {
    private val hooks = LinkedHashMap<Executable, XposedInterface.Hooker>()
    private val savedPrefs = MainModule.mPrefs.getAll()
    private val previousModule = XposedHelpers.moduleInst
    private val muteKey = "system_volume_hide_mute_shortcut"
    private val dndKey = "system_volume_hide_dnd_shortcut"

    @Before fun installHooks() {
        MainModule.mPrefs.clear()
        XposedHelpers.moduleInst = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(XposedInterface::class.java)) { _, call, args ->
            if (call.name != "hook") return@newProxyInstance null
            val target = args!![0] as Executable
            object : XposedInterface.HookBuilder {
                override fun setPriority(priority: Int): XposedInterface.HookBuilder = this
                override fun setExceptionMode(mode: XposedInterface.ExceptionMode): XposedInterface.HookBuilder = this
                override fun setId(id: String?): XposedInterface.HookBuilder = error("No new ID")
                override fun intercept(hooker: XposedInterface.Hooker): XposedInterface.HookHandle {
                    hooks[target] = hooker
                    return Proxy.newProxyInstance(javaClass.classLoader, arrayOf(XposedInterface.HookHandle::class.java)) { _, _, _ -> null } as XposedInterface.HookHandle
                }
            }
        } as XposedInterface
        SystemUIControlCenterHooks.VolumeModeButtonColorsHook(requireNotNull(javaClass.classLoader))
    }

    @After fun restore() {
        XposedHelpers.moduleInst = previousModule
        MainModule.mPrefs.replaceSnapshot(savedPrefs)
        SystemUIControlCenterHooks.refreshVolumeModeButtonVisibilitySnapshot()
        PreferenceObserverRegistry.observerOwners.clear()
    }

    private fun invoke(target: Executable, receiver: Any, args: List<Any?> = emptyList(), native: () -> Any?) {
        val chain = object : XposedInterface.Chain {
            override fun getExecutable() = target
            override fun getThisObject() = receiver
            override fun getArgs() = args
            override fun getArg(index: Int) = args[index]
            override fun proceed() = native()
            override fun proceed(args: Array<Any?>) = proceed()
            override fun proceedWith(thisObject: Any) = proceed()
            override fun proceedWith(thisObject: Any, args: Array<Any?>) = proceed()
        }
        val hook = hooks[target]
        if (hook == null) chain.proceed() else hook.intercept(chain)
    }

    /** Shapes/resource ownership from the native XML, with deliberately asymmetric content. */
    private inner class Panel {
        private val app = RuntimeEnvironment.getApplication()
        private val ids = mapOf("miui_ringer_state_layout" to 101, "miui_ringer_btn_layout" to 102, "miui_volume_ringer_divider" to 103)
        @Suppress("DEPRECATION") // Resource names belong to the external plugin, not the test APK.
        private val context = object : ContextWrapper(app) {
            private val nativeResources = object : Resources(app.assets, app.resources.displayMetrics, app.resources.configuration) {
                override fun getIdentifier(name: String, defType: String?, defPackage: String?) = ids[name] ?: super.getIdentifier(name, defType, defPackage)
            }
            override fun getResources() = nativeResources
        }
        val root = MiuiVolumeDialogView(context)
        val content = View(context)
        val mode = root.mRingerModeLayout
        val holder = FrameLayout(context).apply { id = 101 }
        val buttons = LinearLayout(context).apply { id = 102 }
        val mute = View(context)
        val divider = View(context).apply { id = 103 }
        val dnd = View(context)
        private val helpers: List<RingerButtonHelper>
        private var landscape = false
        private val states = ViewStateGroup {
            root.orientation = if (root.expanded && landscape) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            mode.orientation = root.orientation
            buttons.orientation = if (root.expanded && landscape) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            content.layoutParams = LinearLayout.LayoutParams(240, 200)
            mode.layoutParams = LinearLayout.LayoutParams(if (landscape) WRAP_CONTENT else 240, if (landscape) 200 else WRAP_CONTENT).apply {
                leftMargin = if (landscape) 12 else 0
                topMargin = if (landscape) 0 else 10
            }
            // Native state group reserves both shortcuts even when either child is GONE.
            holder.layoutParams = LinearLayout.LayoutParams(if (landscape) 126 else 240, if (landscape) MATCH_PARENT else 126)
            buttons.layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            mute.layoutParams = LinearLayout.LayoutParams(if (landscape) 50 else 240, if (landscape) 200 else 50)
            dnd.layoutParams = LinearLayout.LayoutParams(if (landscape) 70 else 240, if (landscape) 200 else 70)
            divider.layoutParams = LinearLayout.LayoutParams(if (landscape) 6 else 240, if (landscape) 200 else 6)
            if (!root.expanded) {
                holder.layoutParams = LinearLayout.LayoutParams(50, WRAP_CONTENT)
            }
        }

        init {
            root.addView(content)
            root.addView(mode)
            mode.addView(holder)
            holder.addView(buttons)
            buttons.addView(mute)
            buttons.addView(divider)
            buttons.addView(dnd)
            states.apply(root)
            helpers = listOf(false to mute, true to dnd).map { (zen, button) ->
                val helper = RingerButtonHelper(mode, button, zen)
                invoke(RingerButtonHelper::class.java.declaredConstructors.single(), helper, listOf(mode, button, zen)) { null }
                helper
            }
        }

        fun applyNative(expanded: Boolean, land: Boolean) {
            root.expanded = expanded
            landscape = land
            invoke(ViewStateGroup::class.java.getDeclaredMethod("apply", ViewGroup::class.java), states, listOf(root)) { states.apply(root) }
            invoke(MiuiVolumeDialogView::class.java.getDeclaredMethod("updateFooterVisibility", Boolean::class.javaPrimitiveType), root, listOf(true)) { root.updateFooterVisibility(true) }
            updateHelpers()
            measure()
        }

        fun updateHelpers() {
            helpers.forEach { helper -> invoke(RingerButtonHelper::class.java.getDeclaredMethod("updateState"), helper) { helper.updateState() } }
        }

        fun measure() {
            val spec = View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.AT_MOST)
            root.measure(spec, spec)
            root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        }

        fun assertCompact(hideMute: Boolean, hideDnd: Boolean, land: Boolean) {
            val surviving = (if (hideMute) 0 else 50) + (if (hideDnd) 0 else 70) + (if (!hideMute && !hideDnd) 6 else 0)
            if (surviving == 0) {
                assertEquals(View.GONE, mode.visibility)
                assertEquals("Empty shortcut group must contribute no margin or track", if (land) 240 else 200, if (land) root.measuredWidth else root.measuredHeight)
            } else {
                assertEquals("Measured shortcut footprint must equal actual surviving children", surviving, if (land) holder.measuredWidth else holder.measuredHeight)
                assertEquals("Root must shrink too, including only the real inter-group margin", (if (land) 252 else 210) + surviving, if (land) root.measuredWidth else root.measuredHeight)
                val first = if (hideMute) dnd else mute
                assertEquals("No leading ghost slot", 0, if (land) first.left else first.top)
                assertEquals("Only the ROM's content/shortcut gap remains", if (land) content.right + 12 else content.bottom + 10, if (land) mode.left else mode.top)
            }
            assertEquals(240, content.measuredWidth)
            assertEquals(200, content.measuredHeight)
        }
    }

    private fun setHidden(mute: Boolean, dnd: Boolean) {
        MainModule.mPrefs.put(muteKey, mute)
        MainModule.mPrefs.put(dndKey, dnd)
        SystemUIControlCenterHooks.refreshVolumeModeButtonVisibilitySnapshot()
    }

    @Test fun nativeStateWritesReflowEveryCombinationAcrossExpansionRotationAndRecreation() {
        for (mute in listOf(false, true)) for (dnd in listOf(false, true)) {
            setHidden(mute, dnd)
            repeat(2) {
                val panel = Panel()
                for (land in listOf(false, true, false, true)) {
                    panel.applyNative(true, land)
                    panel.assertCompact(mute, dnd, land)
                    panel.applyNative(false, land)
                    assertEquals("Collapse keeps the native button width", 50, panel.holder.layoutParams.width)
                    assertEquals("Collapse keeps the native height", WRAP_CONTENT, panel.holder.layoutParams.height)
                    panel.applyNative(true, land)
                    panel.assertCompact(mute, dnd, land)
                }
            }
        }
    }

    @Test fun livePreferenceChangesReflowWithoutAnotherNativeStateCallback() {
        val panel = Panel()
        for (land in listOf(false, true)) {
            panel.applyNative(true, land)
            for ((mute, dnd) in listOf(true to false, true to true, false to true, false to false)) {
                MainModule.mPrefs.put(muteKey, mute)
                MainModule.mPrefs.put(dndKey, dnd)
                PreferenceObserverRegistry.handlePreferenceChanged(muteKey)
                shadowOf(android.os.Looper.getMainLooper()).idle()
                panel.measure()
                panel.assertCompact(mute, dnd, land)
            }
        }
    }

    @Test fun unchangedHelperUpdatesDoNotRequestAnotherLayoutAndUnownedDimensionsSurviveRestore() {
        setHidden(true, false)
        val panel = Panel()
        panel.applyNative(true, true)
        assertFalse(panel.holder.isLayoutRequested)
        repeat(20) { panel.updateHelpers() }
        assertFalse("Stable native state updates must not keep invalidating measure", panel.holder.isLayoutRequested)
        panel.holder.layoutParams = panel.holder.layoutParams.apply { width = 177 }
        setHidden(false, false)
        panel.updateHelpers()
        assertEquals("Do not restore a stale width over a newer ROM/other-owner write", 177, panel.holder.layoutParams.width)
    }
}
