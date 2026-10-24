package tv.withaibuild.customiuizer.mods

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Window
import android.view.WindowManager
import io.github.libxposed.api.XposedInterface
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class SystemStatusBarBackgroundTest {
    private val activities = ArrayList<ActivityController<Activity>>()
    private fun activity() = Robolectric.buildActivity(Activity::class.java).setup().also(activities::add).get()
    @After fun close() { activities.forEach { it.pause().stop().destroy() } }

    private class DecorOwner(@JvmField val mActivity: Activity)
    private class Call(owner: Any, private val requested: Any?, private val failure: Throwable? = null,
        private val native: (Any?) -> Unit = {}) {
        var calls = 0
        var copies = 0
        var received: Any? = null
        val chain = Proxy.newProxyInstance(XposedInterface.Chain::class.java.classLoader,
            arrayOf(XposedInterface.Chain::class.java)) { _, method, args ->
            when (method.name) {
                "getThisObject" -> owner
                "getArg" -> requested
                "getArgs" -> { copies++; listOf(requested) }
                "proceed" -> {
                    calls++
                    failure?.let { throw it }
                    received = if (args.isNullOrEmpty()) requested else (args[0] as Array<*>)[0]
                    native(received)
                    "native-result"
                }
                else -> error("Unexpected chain call: ${method.name}")
            }
        } as XposedInterface.Chain
    }

    private fun request(window: Window, color: Int): Call = Call(window, color) {
        window.statusBarColor = it as Int
    }.also { assertEquals("native-result", SystemStatusBarBackgroundHooks.statusBarColorHook().intercept(it.chain)) }

    @Test fun previewAlphaSurvivesWithAndWithoutAnActionBarColor() {
        for (cached in listOf(false, true)) {
            val owner = activity()
            if (cached) SystemStatusBarBackgroundHooks.hookWindowDecor(DecorOwner(owner), ColorDrawable(Color.RED))
            for (color in listOf(Color.TRANSPARENT, 0x00665544, 0x66000000, 0xe6334455.toInt())) {
                val call = request(owner.window, color)
                assertEquals("cached=$cached requested=${color.toUInt().toString(16)}", color, owner.window.statusBarColor)
                assertEquals(1, call.calls)
            }
        }
    }

    @Test fun preservingSemiTransparentColorDoesNotActivateTheNativeTransparentContrastScrim() {
        val window = activity().window
        val requested = 0x66000000
        request(window, requested)
        val decor = Class.forName("com.android.internal.policy.DecorView")
        fun nativeColor(color: Int) = XposedHelpers.callStaticMethod(decor, "calculateBarColor",
            WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS,
            WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS, 0x66000000, color, 8, 8, true)
        assertEquals(requested, nativeColor(window.statusBarColor))
        assertNotEquals(requested, nativeColor(Color.TRANSPARENT))
    }

    @Test fun toolbarUpdatesDoNotReplacePreviewColorsAndNonColorBackgroundDropsItsCachedColor() {
        val owner = activity()
        val decor = DecorOwner(owner)
        owner.window.statusBarColor = Color.BLUE
        SystemStatusBarBackgroundHooks.hookWindowDecor(decor, ColorDrawable(Color.RED))
        assertEquals(Color.RED, owner.window.statusBarColor)
        owner.window.statusBarColor = 0x88000000.toInt()
        SystemStatusBarBackgroundHooks.hookWindowDecor(decor, ColorDrawable(Color.GREEN))
        assertEquals(0x88000000.toInt(), owner.window.statusBarColor)
        SystemStatusBarBackgroundHooks.hookWindowDecor(decor, null)
        request(owner.window, Color.BLUE)
        assertEquals(Color.BLUE, owner.window.statusBarColor)
    }

    @Test fun missingOptionalToolbarFieldsStillCallTheNativeMethodExactlyOnce() {
        val call = Call(Any(), ColorDrawable(Color.RED))
        assertEquals("native-result", SystemStatusBarBackgroundHooks.actionBarBackgroundHook(true).intercept(call.chain))
        assertEquals(1, call.calls)
    }

    @Test fun nativeFailuresRemainTheSameExceptionAndAreNeverRetried() {
        val owner = DecorOwner(activity())
        for (failure in listOf(IllegalStateException("native"), OutOfMemoryError("fatal"))) {
            val call = Call(owner, null, failure)
            assertSame(failure, assertThrows(failure.javaClass) {
                SystemStatusBarBackgroundHooks.actionBarBackgroundHook(false).intercept(call.chain)
            })
            assertEquals(1, call.calls)
        }
    }

    @Test fun colorOwnershipIsPerWindowAndLeavesNavigationDimContrastAndAppearanceAlone() {
        val first = activity()
        val second = activity()
        SystemStatusBarBackgroundHooks.hookWindowDecor(DecorOwner(first), ColorDrawable(Color.RED))
        val window = second.window
        window.navigationBarColor = 0x77010203
        window.isStatusBarContrastEnforced = true
        window.isNavigationBarContrastEnforced = true
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.setDimAmount(.32f)
        window.insetsController!!.setSystemBarsAppearance(8 or 16, 8 or 16)
        val appearance = window.insetsController!!.systemBarsAppearance
        request(window, Color.BLUE)
        assertEquals(Color.BLUE, window.statusBarColor)
        assertEquals(0x77010203, window.navigationBarColor)
        assertTrue(window.isStatusBarContrastEnforced)
        assertTrue(window.isNavigationBarContrastEnforced)
        assertEquals(.32f, window.attributes.dimAmount, .0001f)
        assertTrue(window.attributes.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND != 0)
        assertEquals(appearance, window.insetsController!!.systemBarsAppearance)
    }

    @Test fun unchangedColorsNeedNoArgumentListCopy() {
        val call = request(activity().window, Color.BLUE)
        assertEquals(Color.BLUE, call.received)
        assertEquals(0, call.copies)
    }

    @Test fun opaqueActionBarMatchingStillWorksAndOnlyCopiesChangedArguments() {
        val owner = activity()
        owner.window.statusBarColor = Color.BLUE
        SystemStatusBarBackgroundHooks.hookWindowDecor(DecorOwner(owner), ColorDrawable(Color.RED))
        val changed = request(owner.window, Color.BLUE)
        assertEquals(Color.RED, changed.received)
        assertEquals(1, changed.copies)
        val same = request(owner.window, Color.RED)
        assertEquals(Color.RED, same.received)
        assertEquals(0, same.copies)
    }

    @Test fun removingANonColorBackgroundCannotLeaveAnOpaqueActionBarOverride() {
        val owner = activity()
        owner.window.statusBarColor = Color.BLUE
        val decor = DecorOwner(owner)
        SystemStatusBarBackgroundHooks.hookWindowDecor(decor, ColorDrawable(Color.RED))
        SystemStatusBarBackgroundHooks.hookWindowDecor(decor, android.graphics.drawable.GradientDrawable())
        request(owner.window, Color.BLUE)
        assertEquals(Color.BLUE, owner.window.statusBarColor)
    }

    @Test fun layoutCompletionRespectsPreviewAlphaAndFailedNativeLayout() {
        val owner = activity()
        val window = owner.window
        SystemStatusBarBackgroundHooks.hookWindowDecor(DecorOwner(owner), ColorDrawable(Color.RED))
        for (color in listOf(Color.TRANSPARENT, 0x66000000, Color.BLACK)) {
            val call = Call(window, null) { window.statusBarColor = color }
            assertEquals("native-result", SystemStatusBarBackgroundHooks.statusBarLayoutHook().intercept(call.chain))
            assertEquals(color, window.statusBarColor)
            assertEquals(1, call.calls)
        }
        window.statusBarColor = Color.BLUE
        val failure = IllegalStateException("layout failed")
        val failed = Call(window, null, failure)
        assertSame(failure, assertThrows(IllegalStateException::class.java) {
            SystemStatusBarBackgroundHooks.statusBarLayoutHook().intercept(failed.chain)
        })
        assertEquals(Color.BLUE, window.statusBarColor)
        assertEquals(1, failed.calls)
    }
}
