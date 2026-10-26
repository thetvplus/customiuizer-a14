package tv.withaibuild.customiuizer.mods

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ExtendedPowerMenuTest {
    @Test fun titleScopeIsOwnerSpecificNestedAndAlwaysReleasedAfterFailure() {
        val a = Any(); val b = Any()
        assertNull(ExtendedPowerMenu.constructorTitle(a))
        ExtendedPowerMenu.construct(a, 10) {
            assertEquals(10, ExtendedPowerMenu.constructorTitle(a)); assertNull(ExtendedPowerMenu.constructorTitle(b))
            val otherThread = java.util.concurrent.atomic.AtomicReference<Int?>(-1)
            Thread { otherThread.set(ExtendedPowerMenu.constructorTitle(a)) }.apply { start(); join() }
            assertNull(otherThread.get())
            assertThrows(IllegalStateException::class.java) {
                ExtendedPowerMenu.construct(b, 20) { throw IllegalStateException("native constructor") }
            }
            assertEquals(10, ExtendedPowerMenu.constructorTitle(a)); assertNull(ExtendedPowerMenu.constructorTitle(b))
        }
        assertNull(ExtendedPowerMenu.constructorTitle(a))
    }

    @Test fun failedPreparationPreservesNativeItemsAndRepeatedCreationDoesNotDuplicateOurActions() {
        val owner = Any(); val native = Any(); val items = mutableListOf(native)
        assertThrows(IllegalArgumentException::class.java) {
            ExtendedPowerMenu.append(owner, items) {
                if (it == ExtendedPowerMenu.Target.RECOVERY) throw IllegalArgumentException("second action")
                Any()
            }
        }
        assertEquals(listOf(native), items)
        var created = 0
        ExtendedPowerMenu.append(owner, items) { created++; Any() }
        ExtendedPowerMenu.append(owner, items) { error("duplicate construction") }
        assertEquals(2, created); assertEquals(3, items.size); assertSame(native, items[0])
    }

    @Test fun onlyTheVerifiedPluginIsReplaced() {
        assertTrue(ExtendedPowerMenu.plugin(ComponentName("miui.systemui.plugin", "miui.systemui.globalactions.GlobalActionsPlugin")))
        assertFalse(ExtendedPowerMenu.plugin(ComponentName("other.plugin", "miui.systemui.globalactions.GlobalActionsPlugin")))
        assertFalse(ExtendedPowerMenu.plugin(ComponentName("miui.systemui.plugin", "other.GlobalActionsSettings")))
        assertEquals("bootloader", ExtendedPowerMenu.Target.FASTBOOT.reason)
        assertEquals("recovery", ExtendedPowerMenu.Target.RECOVERY.reason)
    }

    @Test fun cancellationStaleOwnerAndRepeatedButtonClicksCannotReboot() {
        Robolectric.buildActivity(Activity::class.java).setup().use { controller ->
            val activity = controller.get(); var calls = 0; var allowed = true
            fun show() = ExtendedPowerMenu.confirmation(activity, "Confirm", { allowed }, { calls++ }) { AlertDialog.Builder(it).create() }
            val cancelled = show(); cancelled.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            cancelled.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); assertEquals(0, calls)
            val stale = show(); allowed = false; stale.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            assertEquals(0, calls); stale.dismiss()
            allowed = true; val accepted = show(); accepted.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            accepted.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); assertEquals(1, calls)
            accepted.dismiss()
        }
    }
}
