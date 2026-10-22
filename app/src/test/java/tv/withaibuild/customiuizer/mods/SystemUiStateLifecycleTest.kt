package tv.withaibuild.customiuizer.mods

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.telephony.CellInfo
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.android.systemui.controlcenter.phone.ControlPanelWindowManager
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import java.lang.reflect.Executable
import java.lang.reflect.Proxy
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.AfterHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.BeforeHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.PreferenceObserverRegistry
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class SystemUiStateLifecycleTest {
    private val hooks = LinkedHashMap<Executable, MethodHook>()
    private var previousModule: XposedInterface? = null
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val ready: PackageReadyParam get() = proxy(PackageReadyParam::class.java) { name, _ ->
        when (name) {
            "getClassLoader" -> javaClass.classLoader
            "getPackageName" -> "com.android.systemui"
            else -> error(name)
        }
    }

    @Before
    fun bindFramework() {
        MainModule.mPrefs.clear()
        previousModule = XposedHelpers.moduleInst
        XposedHelpers.moduleInst = proxy(XposedInterface::class.java) { name, args ->
            when (name) {
                "hook" -> builder(args!![0] as Executable)
                "log" -> null
                else -> error(name)
            }
        }
    }

    @After
    fun releaseFramework() {
        XposedHelpers.moduleInst = previousModule
        MainModule.mPrefs.clear()
        val field = SystemUIStatusBarHooks::class.java.getDeclaredField("layoutRuntimeState").apply {
            isAccessible = true
        }
        field.get(SystemUIStatusBarHooks)?.let { PreferenceObserverRegistry.unregisterPreferenceObserver(it) }
        field.set(SystemUIStatusBarHooks, null)
    }

    @Test
    fun swappedShadeRoutingUsesTheOriginalTouchSideForTheEntireGesture() {
        SystemUIControlCenterHooks.SwitchCCAndNotificationHook(ready)
        val manager = ControlPanelWindowManager()
        val (member, hook) = installed("dispatchToControlPanel")
        fun dispatch(action: Int, x: Float): Boolean {
            val event = MotionEvent.obtain(0, 0, action, x, 5f, 0)
            return try {
                val callback = before(member, manager, arrayOf(event, 200f))
                hook.beforeHook(callback)
                assertTrue(callback.skipped)
                callback.result as Boolean
            } finally { event.recycle() }
        }
        assertTrue(dispatch(MotionEvent.ACTION_DOWN, 30f))
        assertEquals(30f, manager.downX, 0f)
        assertTrue(dispatch(MotionEvent.ACTION_MOVE, 170f))
        assertEquals(2, manager.windowView!!.dispatches)
        assertFalse(dispatch(MotionEvent.ACTION_DOWN, 170f))
        assertFalse(dispatch(MotionEvent.ACTION_MOVE, 30f))
        assertEquals(2, manager.windowView!!.dispatches)
        manager.windowView = null
        assertFalse(dispatch(MotionEvent.ACTION_DOWN, 30f))
    }

    @Test
    fun clearingAnEmptyMobileStateDoesNotAllocateADigitalView() {
        SystemUIStatusBarHooks.StatusBarDigitalSignalHook(ready)
        val owner = MobileView(context)
        applyMobileState(owner, null)
        assertEquals(1, owner.signalContainer.childCount)
        assertNull(owner.findViewWithTag<TextView>("digitalSignalView"))
    }

    @Test
    fun clearingAndRestoringMobileStateReusesItsDigitalView() {
        SystemUIStatusBarHooks.StatusBarDigitalSignalHook(ready)
        val owner = MobileView(context)
        applyMobileState(owner, MobileState())
        val original = requireNotNull(owner.findViewWithTag<TextView>("digitalSignalView"))
        applyMobileState(owner, null)
        assertEquals(View.GONE, original.visibility)
        repeat(3) { applyMobileState(owner, MobileState()) }
        assertSame(original, owner.findViewWithTag("digitalSignalView"))
        assertEquals(2, owner.signalContainer.childCount)
        assertEquals(View.VISIBLE, original.visibility)
    }

    @Test
    fun nestedMobileViewUpdatesDoNotShareInitializationState() {
        SystemUIStatusBarHooks.StatusBarDigitalSignalHook(ready)
        val first = MobileView(context)
        val second = MobileView(context)
        applyMobileState(first, MobileState()) { applyMobileState(second, MobileState()) }
        assertNotNull(first.findViewWithTag<TextView>("digitalSignalView"))
        assertNotNull(second.findViewWithTag<TextView>("digitalSignalView"))
        assertEquals(2, first.signalContainer.childCount)
        assertEquals(2, second.signalContainer.childCount)
    }

    @Test
    fun anUnmeasuredSignalDoesNotDisplayAFabricatedZeroDbm() {
        SystemUIStatusBarHooks.StatusBarDigitalSignalHook(ready)
        val owner = MobileView(context)
        applyMobileState(owner, MobileState())
        assertEquals("—", owner.findViewWithTag<TextView>("digitalSignalView").text.toString())
        assertEquals("—", formatDigitalSignalLabel(CellInfo.UNAVAILABLE, false))
        assertEquals("-93dBm", formatDigitalSignalLabel(-93, false))
    }

    @Test
    fun cutoutPlacementAndDarknessChangesKeepOneNativeTintedDigitalView() {
        SystemUIStatusBarHooks.StatusBarDigitalSignalHook(ready)
        val owner = MobileView(context)
        owner.mMobileTypeSingle.setTextColor(Color.RED)
        applyMobileState(owner, MobileState())
        val digital = requireNotNull(owner.findViewWithTag<TextView>("digitalSignalView"))
        assertEquals(Color.RED, digital.currentTextColor)
        val (dripMember, dripHook) = installed("setDripEnd")
        repeat(3) {
            owner.mMobile.visibility = View.VISIBLE
            dripHook.afterHook(AfterHookCallback(before(dripMember, owner, arrayOf(true)), null, null))
            assertEquals(View.GONE, owner.mMobile.visibility)
        }
        owner.mMobileTypeSingle.setTextColor(Color.BLUE)
        val (darkMember, darkHook) = installed("applyDarknessInternal")
        darkHook.afterHook(AfterHookCallback(before(darkMember, owner, emptyArray()), null, null))
        assertEquals(Color.BLUE, digital.currentTextColor)
        assertEquals(2, owner.signalContainer.childCount)
    }

    @Test
    fun actionBarColorIsOwnedByItsWindow() {
        val first = Robolectric.buildActivity(Activity::class.java).setup()
        val second = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val firstWindow = first.get().window
            val secondWindow = second.get().window
            val expected = XposedHelpers.callMethod(SystemStatusBarBackgroundHooks,
                "getActionBarColor", secondWindow, Color.BLUE)
            assertNotEquals(Color.RED, expected)
            XposedHelpers.callMethod(SystemStatusBarBackgroundHooks, "hookWindowDecor",
                WindowDecor(first.get()), ColorDrawable(Color.RED))
            assertEquals(Color.RED, XposedHelpers.callMethod(SystemStatusBarBackgroundHooks,
                "getActionBarColor", firstWindow, Color.BLUE))
            assertEquals(expected, XposedHelpers.callMethod(SystemStatusBarBackgroundHooks,
                "getActionBarColor", secondWindow, Color.BLUE))
        } finally {
            first.pause().stop().destroy()
            second.pause().stop().destroy()
        }
    }

    @Test
    fun fatalWindowThemeFailuresPropagateAfterExactlyOneOriginalCall() {
        SystemStatusBarBackgroundHooks.StatusBarBackgroundHook(ready)
        val (member, hook) = installed("generateLayout")
        val fatal = InternalError("window theme")
        val failingContext = object : ContextWrapper(context) {
            var fail = false
            override fun getTheme(): Resources.Theme = if (fail) throw fatal else super.getTheme()
        }
        val window = member.declaringClass.getConstructor(Context::class.java).newInstance(failingContext)
        XposedHelpers.setIntField(window, "mStatusBarColor", Color.BLUE)
        failingContext.fail = true
        var originalCalls = 0
        val chain = proxy(XposedInterface.Chain::class.java) { name, _ ->
            when (name) {
                "getThisObject" -> window
                "proceed" -> { originalCalls++; null }
                else -> error(name)
            }
        }
        assertSame(fatal, assertThrows(InternalError::class.java) { hook.intercept(chain) })
        assertEquals(1, originalCalls)
    }

    private fun applyMobileState(owner: MobileView, state: MobileState?, nested: () -> Unit = {}) {
        val (member, hook) = installed("applyMobileState")
        val callback = before(member, owner, arrayOf(state))
        hook.beforeHook(callback)
        owner.mState = state
        nested()
        hook.afterHook(AfterHookCallback(callback, null, null))
    }

    private fun installed(name: String): Map.Entry<Executable, MethodHook> =
        hooks.entries.single { it.key.name == name }

    private fun before(member: Executable, owner: Any, args: Array<Any?>): BeforeHookCallback =
        BeforeHookCallback(proxy(XposedInterface.Chain::class.java) { name, values ->
            when (name) {
                "getExecutable" -> member
                "getThisObject" -> owner
                "getArgs" -> args.toList()
                "getArg" -> args[values!![0] as Int]
                else -> error(name)
            }
        })

    private fun builder(member: Executable) = object : XposedInterface.HookBuilder {
        override fun setPriority(priority: Int): XposedInterface.HookBuilder = this
        override fun setExceptionMode(mode: XposedInterface.ExceptionMode): XposedInterface.HookBuilder = this
        override fun setId(id: String?): XposedInterface.HookBuilder = this
        override fun intercept(hooker: XposedInterface.Hooker): XposedInterface.HookHandle {
            hooks[member] = hooker as MethodHook
            return proxy(XposedInterface.HookHandle::class.java) { _, _ -> null }
        }
    }

    private fun <T> proxy(type: Class<T>, call: (String, Array<out Any?>?) -> Any?): T =
        type.cast(Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, args ->
            call(method.name, args)
        })

    private class MobileState {
        @JvmField var visible = true
        @JvmField var airplane = false
        @JvmField var subId = 1
    }

    private class WindowDecor(@JvmField val mActivity: Activity)

    private class MobileView(context: Context) : LinearLayout(context) {
        @JvmField var mState: MobileState? = null
        @JvmField val mMobile = ImageView(context)
        @JvmField val mMobileTypeSingle = TextView(context)
        val signalContainer = FrameLayout(context).apply { addView(mMobile) }
        init { addView(signalContainer) }
    }
}
