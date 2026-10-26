package tv.withaibuild.customiuizer.mods

import android.content.ComponentName
import android.content.Intent
import android.content.res.Resources
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.widget.FrameLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.util.TypedValue
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Executable
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tv.withaibuild.customiuizer.MainModule

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class PassiveFeatureBoundaryTest {
    private var saved = emptyMap<String, Any>()
    @Before fun setUp() { saved = MainModule.mPrefs.getAll(); MainModule.mPrefs.clear(); refresh() }
    @After fun tearDown() { MainModule.mPrefs.replaceSnapshot(saved); refresh() }
    private fun refresh() {
        LauncherLayoutHooks::class.java.getDeclaredMethod("refreshLayoutSnapshot").apply { isAccessible = true }.invoke(LauncherLayoutHooks)
        SystemDisplayHooks::class.java.getDeclaredMethod("refreshDisplaySnapshot").apply { isAccessible = true }.invoke(SystemDisplayHooks)
        SystemLockScreenHooks.refreshNoScreenLockSnapshot()
        Launcher::class.java.getDeclaredMethod("refreshRecentsBlurPreferences").apply { isAccessible = true }.invoke(Launcher)
        LauncherIconHooks.refreshIconStyleSnapshot()
    }
    private fun margin(value: Int) { MainModule.mPrefs.put("launcher_horizwidgetmargin", value); refresh() }
    private fun spec(width: Int, height: Int) = (width.toLong() shl 32) or (height.toLong() and 0xffffffffL)
    class Signature { fun four(a: Int, b: Int, c: Int, d: Int) = Unit; fun one(a: Int) = Unit }
    @Test fun widgetDefaultRestoresBothNativeGeometryAndPaddingAfterAnOverride() {
        val size = LauncherLayoutHooks.horizontalWidgetSizeHook()
        val padding = LauncherLayoutHooks.horizontalWidgetPaddingHook()
        val original = spec(400, 137)
        val nativePadding = Rect(8, 4, 9, 5)
        margin(31)
        val inset = Math.round(10 * Resources.getSystem().displayMetrics.density) * 2
        assertEquals(spec(400 - inset, 137), size.intercept(Chain(result = original)))
        assertEquals(Rect(), padding.intercept(Chain(result = nativePadding)))
        margin(0)
        assertEquals(original, size.intercept(Chain(result = original, signature = "one")))
        assertSame(nativePadding, padding.intercept(Chain(result = nativePadding)))
        assertEquals(Rect(8, 4, 9, 5), nativePadding)
    }
    @Test fun widgetBoundsKeepThePackedHeightAndNeverProduceNonpositiveWidths() {
        margin(41)
        assertEquals(spec(1, Int.MIN_VALUE), LauncherLayoutHooks.horizontalWidgetSizeHook().intercept(Chain(result = spec(1, Int.MIN_VALUE))))
        margin(1)
        assertEquals(spec(Int.MAX_VALUE, 800), LauncherLayoutHooks.horizontalWidgetSizeHook().intercept(Chain(result = spec(Int.MAX_VALUE, 800))))
        val one = Chain(result = spec(100, 200), signature = "one")
        assertEquals(one.result, LauncherLayoutHooks.horizontalWidgetSizeHook().intercept(one))
    }
    @Test fun nativeWidgetFailuresCannotBeReplacedWithSuccessfulPaddingOrDimensions() {
        margin(31)
        for (hook in listOf(LauncherLayoutHooks.horizontalWidgetSizeHook(), LauncherLayoutHooks.horizontalWidgetPaddingHook())) {
            for (failure in listOf(IllegalStateException("native"), OutOfMemoryError("fatal"))) {
                val chain = Chain(failure = failure)
                assertSame(failure, assertThrows(Throwable::class.java) { hook.intercept(chain) })
                assertEquals(1, chain.calls)
            }
        }
    }
    class PowerState(@JvmField var mScreenState: Int = 2)
    class DisplayOwner {
        @JvmField var mUseAutoBrightness = true
        @JvmField var mPowerState = PowerState()
        @JvmField var mHandler = Handler(Looper.getMainLooper())
        var readyReads = 0
        fun readyToUpdateDisplayState(): Boolean { readyReads++; return true }
    }
    @Test fun nestedScreenCallsKeepTheirOwnEligibilityAndController() {
        val hook = SystemDisplayHooks.autoBrightnessScreenOffHook()
        val outer = DisplayOwner(); val inner = DisplayOwner()
        val outerCall = Chain(outer, listOf(1, false), true, native = {
            hook.intercept(Chain(inner, listOf(2, false), true))
            outer.mPowerState.mScreenState = 1
        })
        assertEquals(true, hook.intercept(outerCall))
        assertEquals(1, outerCall.calls); assertEquals(1, outer.readyReads); assertEquals(0, inner.readyReads)
        assertTrue(outer.mHandler.hasMessages(255)); assertFalse(inner.mHandler.hasMessages(255))
        outer.mHandler.removeMessages(255)
    }
    @Test fun unrelatedScreenEventsAndNativeFailuresDoNotRequestAnExtraUpdate() {
        val hook = SystemDisplayHooks.autoBrightnessScreenOffHook()
        for ((state, report) in listOf(2 to false, 1 to true)) {
            val chain = Chain(args = listOf(state, report), result = false)
            assertEquals(false, hook.intercept(chain)); assertEquals(0, chain.ownerReads)
        }
        val owner = DisplayOwner()
        val failure = IllegalStateException("native transition rejected")
        val chain = Chain(owner, listOf(1, false), failure = failure)
        assertSame(failure, assertThrows(IllegalStateException::class.java) { hook.intercept(chain) })
        assertEquals(0, owner.readyReads); assertFalse(owner.mHandler.hasMessages(255))
    }
    class DimOwner {
        @JvmField var mMaximumScreenDimDurationConfig = 7000
        @JvmField var mMaximumScreenDimRatioConfig = .2f
    }
    @Test fun dimDefaultsUseTheCurrentNativeConfigurationAndFailuresDoNotMutateIt() {
        val hook = SystemDisplayHooks.screenDimConfigurationHook(); val owner = DimOwner()
        MainModule.mPrefs.put("system_dimtime", 80); refresh()
        hook.intercept(Chain(owner))
        assertEquals(600000, owner.mMaximumScreenDimDurationConfig); assertEquals(.8f, owner.mMaximumScreenDimRatioConfig)
        MainModule.mPrefs.put("system_dimtime", 0); refresh()
        hook.intercept(Chain(owner, native = { owner.mMaximumScreenDimDurationConfig = 9000; owner.mMaximumScreenDimRatioConfig = .3f }))
        assertEquals(9000, owner.mMaximumScreenDimDurationConfig); assertEquals(.3f, owner.mMaximumScreenDimRatioConfig)
        MainModule.mPrefs.put("system_dimtime", 90); refresh()
        val failure = SecurityException("native")
        assertSame(failure, assertThrows(SecurityException::class.java) { hook.intercept(Chain(owner, failure = failure)) })
        assertEquals(9000, owner.mMaximumScreenDimDurationConfig); assertEquals(.3f, owner.mMaximumScreenDimRatioConfig)
    }
    @Test fun appLockSkipsOnlyTheSelectedComponentAfterASuccessfulNativeDecision() {
        SystemLockScreenHooks.noScreenLockConfig = SystemLockScreenHooks.noScreenLockConfig.copy(skipActivities = setOf("example.app|example.app.Selected"))
        val hook = SystemLockScreenHooks.skipAppLockResultHook()
        for ((activity, expected) in listOf("Selected" to true, "Other" to false)) {
            val intent = Intent().setComponent(ComponentName("example.app", "example.app.$activity"))
            val chain = Chain(args = listOf(intent), result = false)
            assertEquals(expected, hook.intercept(chain)); assertEquals(1, chain.calls)
            val failure = SecurityException("native")
            val failed = Chain(args = listOf(intent), failure = failure)
            assertSame(failure, assertThrows(SecurityException::class.java) { hook.intercept(failed) })
            assertEquals(0, failed.argReads)
        }
        val nativeAllowed = Chain(result = true)
        assertEquals(true, hook.intercept(nativeAllowed)); assertEquals(0, nativeAllowed.argReads)
    }
    class Task(@JvmField val topActivity: ComponentName?, @JvmField val baseIntent: Intent?)
    class Group(@JvmField val mMainTaskInfo: Task)
    @Test fun recentsHideSelectionPreservesNativeDecisionsAndUsesTheNativeFallbackComponent() {
        MainModule.mPrefs.put("system_hidefromrecents_apps", setOf("example.app")); refresh()
        val hook = Launcher.hideFromRecentsResultHook()
        for (top in listOf<ComponentName?>(null, ComponentName("example.app", "Main"))) {
            val group = Group(Task(top, Intent().setComponent(ComponentName("example.app", "Main"))))
            assertEquals(true, hook.intercept(Chain(args = listOf(group), result = false)))
        }
        val failure = SecurityException("native task query rejected")
        val failed = Chain(failure = failure)
        assertSame(failure, assertThrows(SecurityException::class.java) { hook.intercept(failed) })
        assertEquals(0, failed.argReads)
        val allowed = Chain(result = true)
        assertEquals(true, hook.intercept(allowed)); assertEquals(0, allowed.argReads)
    }
    @Test fun clearAllOverrideCannotTurnANativeFailureIntoACleanupApproval() {
        val hook = System.clearAllPerceptibleResultHook()
        val chain = Chain(result = listOf("app"))
        assertNull(hook.intercept(chain)); assertEquals(1, chain.calls)
        for (failure in listOf(SecurityException("native"), OutOfMemoryError("fatal"))) {
            val failed = Chain(failure = failure)
            assertSame(failure, assertThrows(Throwable::class.java) { hook.intercept(failed) }); assertEquals(1, failed.calls)
        }
    }
    class LabelOwner(@JvmField val mTitleContainer: FrameLayout)
    @Test fun titleDefaultsKeepNativeFontAndMarginAndSuccessfulOverridesStayInsideTheirRange() {
        val title = TextView(RuntimeEnvironment.getApplication())
        var resolutions = 0
        val font = LauncherIconHooks.titleFontHook { _, _ -> resolutions++; title }
        val reference = TextView(title.context)
        val fontCall = Chain(native = { title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f) })
        reference.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        font.intercept(fontCall)
        assertEquals(reference.textSize, title.textSize); assertEquals(0, resolutions)
        MainModule.mPrefs.put("launcher_titlefontsize", 12); refresh()
        font.intercept(fontCall); reference.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        assertEquals(reference.textSize, title.textSize); assertEquals(1, resolutions)
        MainModule.mPrefs.put("launcher_titlefontsize", 5); refresh()
        font.intercept(fontCall); reference.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        assertEquals(reference.textSize, title.textSize); assertEquals(1, resolutions)
        val container = FrameLayout(title.context).apply { layoutParams = RelativeLayout.LayoutParams(100, 50).apply { topMargin = 17 } }
        val owner = LabelOwner(container); val hook = LauncherIconHooks.titleTopMarginHook()
        MainModule.mPrefs.put("launcher_titletopmargin", 14); refresh()
        hook.intercept(Chain(owner))
        assertEquals(Math.round(3 * container.resources.displayMetrics.density), (container.layoutParams as RelativeLayout.LayoutParams).topMargin)
        MainModule.mPrefs.put("launcher_titletopmargin", 0); refresh()
        val default = Chain(owner, native = { (container.layoutParams as RelativeLayout.LayoutParams).topMargin = 19 })
        hook.intercept(default)
        assertEquals(19, (container.layoutParams as RelativeLayout.LayoutParams).topMargin); assertEquals(0, default.ownerReads)
        val failure = IllegalStateException("native")
        assertSame(failure, assertThrows(IllegalStateException::class.java) { font.intercept(Chain(failure = failure)) })
        assertEquals(1, resolutions)
    }
    private class Chain(
        private val owner: Any? = null,
        private val args: List<Any?> = emptyList(),
        val result: Any? = null,
        private val failure: Throwable? = null,
        private val signature: String = "four",
        private val native: () -> Unit = {},
    ) : XposedInterface.Chain {
        var calls = 0; var ownerReads = 0; var argReads = 0
        override fun getExecutable(): Executable = Signature::class.java.declaredMethods.single { it.name == signature }
        override fun getThisObject(): Any { ownerReads++; return owner ?: error("unrelated event read owner") }
        override fun getArgs(): List<Any?> = error("must not copy arguments")
        override fun getArg(index: Int): Any? { argReads++; return args[index] }
        override fun proceed(): Any? { calls++; failure?.let { throw it }; native(); return result }
        override fun proceed(args: Array<Any?>): Any? = error("must preserve native arguments")
        override fun proceedWith(receiver: Any): Any? = error("unused")
        override fun proceedWith(receiver: Any, args: Array<Any>): Any? = error("unused")
    }
}
