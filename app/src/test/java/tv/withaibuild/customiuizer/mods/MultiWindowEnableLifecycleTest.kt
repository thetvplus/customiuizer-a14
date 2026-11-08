package tv.withaibuild.customiuizer.mods

import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import java.time.Duration
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=android.app.Application::class)
class MultiWindowEnableLifecycleTest {
    class Menu {
        @JvmField val mHandler=Handler(Looper.getMainLooper())
        @JvmField val mMenuItemMultiWindow=ImageView(RuntimeEnvironment.getApplication())
        @JvmField val mMenuItemSmallWindow=ImageView(RuntimeEnvironment.getApplication())
    }
    private val menu=Menu()
    private val key="multiWindowEnableRunnable"
    @After fun cleanup() {
        menu.mHandler.removeCallbacksAndMessages(null)
        XposedHelpers.removeAdditionalInstanceField(menu,key)
    }
    @Test fun completionReleasesItsSlotAndEnablesBothNativeActions() {
        menu.mMenuItemMultiWindow.isEnabled=false;menu.mMenuItemSmallWindow.isEnabled=false
        SystemWindowHooks.scheduleMultiWindowEnable(menu)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200))
        assertTrue(menu.mMenuItemMultiWindow.isEnabled)
        assertTrue(menu.mMenuItemSmallWindow.isEnabled)
        assertEquals(255,menu.mMenuItemSmallWindow.imageAlpha)
        assertNull("completed work must not pin its weak-key owner",XposedHelpers.getAdditionalInstanceField(menu,key))
    }
    @Test fun staleDeliveryCannotConsumeTheReplacementRequest() {
        SystemWindowHooks.scheduleMultiWindowEnable(menu)
        val old=XposedHelpers.getAdditionalInstanceField(menu,key) as Runnable
        SystemWindowHooks.scheduleMultiWindowEnable(menu)
        val current=XposedHelpers.getAdditionalInstanceField(menu,key)
        menu.mMenuItemMultiWindow.isEnabled=false
        old.run()
        assertFalse(menu.mMenuItemMultiWindow.isEnabled)
        assertSame(current,XposedHelpers.getAdditionalInstanceField(menu,key))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200))
        assertTrue(menu.mMenuItemMultiWindow.isEnabled)
        assertNull(XposedHelpers.getAdditionalInstanceField(menu,key))
    }
}
