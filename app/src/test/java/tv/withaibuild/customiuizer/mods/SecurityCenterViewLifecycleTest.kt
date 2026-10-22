package tv.withaibuild.customiuizer.mods

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import java.time.Duration
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class SecurityCenterViewLifecycleTest {
    @Test
    fun hiddenHandleKeepsModuleTouchesAndDoesNotChangeOtherViewInstances() {
        Robolectric.buildActivity(Activity::class.java).setup().use { controller ->
            val activity = controller.get()
            val view = View(activity).apply { background = ColorDrawable(Color.RED) }
            activity.setContentView(view)
            var nativeTouches = 0
            val listener = View.OnTouchListener { _, _ -> nativeTouches++; true }
            view.setOnTouchListener(listener)
            val other = View(activity).apply {
                background = ColorDrawable(Color.RED)
                setOnTouchListener(listener)
            }
            Various.scheduleSideBarHandleHide(view)
            Various.scheduleSideBarHandleHide(view)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(150))
            assertNull(view.background)
            assertNotNull(other.background)
            fun dispatch(target: View, source: Int) {
                val event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0f, 0f, 0).apply {
                    this.source = source
                }
                try { target.dispatchTouchEvent(event) } finally { event.recycle() }
            }
            dispatch(view, 0)
            assertEquals(0, nativeTouches)
            dispatch(view, 9999)
            assertEquals(1, nativeTouches)
            dispatch(other, 0)
            assertEquals(2, nativeTouches)
        }
    }

    @Test
    fun cancellingTheOwnerTaskPreventsLateMutationAndANewOwnerCanStillHide() {
        Robolectric.buildActivity(Activity::class.java).setup().use { controller ->
            val activity = controller.get()
            val old = View(activity).apply { background = ColorDrawable(Color.RED) }
            activity.setContentView(old)
            Various.scheduleSideBarHandleHide(old)
            Various.cancelSideBarHandleHide(old)
            val replacement = View(activity).apply { background = ColorDrawable(Color.RED) }
            activity.setContentView(replacement)
            Various.scheduleSideBarHandleHide(replacement)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(150))
            assertNotNull(old.background)
            assertNull(replacement.background)
        }
    }

    @Test
    fun detachedOrMissingDataLeavesTheNativeTemperatureUntouched() {
        val detached = TemperatureFragment(null)
        Various.updateBatteryTemperature(detached)
        assertEquals("ROM", detached.preference.value)
        Robolectric.buildActivity(BatteryActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            val fragment = TemperatureFragment(activity)
            Various.updateBatteryTemperature(fragment)
            assertEquals("ROM", fragment.preference.value)
            activity.reading = Intent(Intent.ACTION_BATTERY_CHANGED)
            Various.updateBatteryTemperature(fragment)
            assertEquals("ROM", fragment.preference.value)
        }
    }

    @Test
    fun validReadingsIncludingZeroUseTheNativeTemperatureUnits() {
        Robolectric.buildActivity(BatteryActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            val fragment = TemperatureFragment(activity)
            activity.reading = Intent(Intent.ACTION_BATTERY_CHANGED).putExtra("temperature", 372)
            Various.updateBatteryTemperature(fragment)
            assertEquals("37℃", fragment.preference.value)
            activity.reading = Intent(Intent.ACTION_BATTERY_CHANGED).putExtra("temperature", 0)
            Various.updateBatteryTemperature(fragment)
            assertEquals("0℃", fragment.preference.value)
        }
    }

    class BatteryActivity : Activity() {
        var reading: Intent? = null
        override fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter?, flags: Int): Intent? = reading
    }

    private class TemperatureFragment(private val owner: Activity?) {
        val preference = TemperaturePreference()
        fun getActivity(): Activity? = owner
        fun findPreference(key: String): TemperaturePreference? =
            preference.takeIf { key == "reference_current_temp" }
    }

    private class TemperaturePreference {
        var value = "ROM"
            private set
        fun setText(text: String) { value = text }
    }
}
