package tv.withaibuild.customiuizer.mods.island

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tv.withaibuild.customiuizer.utils.BackupRestore
import tv.withaibuild.customiuizer.utils.IslandPreferenceDefaults
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectOutputStream

/** Run unchanged against d6f47fd and the fix to document the original failure paths. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IslandRegressionReproductionTest {
    @Test fun aConflictingLeftAnchorMustNeverBeReportedAsACenteredIsland() {
        assertNull(IslandPosition.left(184, 12, 388, 0, 0, 40, 192, 208, true))
    }

    @Test fun aSlowShortUpwardReleaseWorksWithoutAnIntermediateMove() {
        val host = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        try {
            val shell = IslandActivityView(host).apply { simplified = true }
            host.setContentView(shell)
            shell.geometry(184, 40, 40)
            shell.measure(View.MeasureSpec.makeMeasureSpec(184, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(40, View.MeasureSpec.EXACTLY))
            shell.layout(0, 0, 184, 40)
            var count = 0
            shell.onDismiss = { count++ }
            val now = SystemClock.uptimeMillis()
            fun event(action: Int, y: Float, elapsed: Long) {
                val touch = MotionEvent.obtain(now, now + elapsed, action, 92f, y, 0)
                try { shell.dispatchTouchEvent(touch) } finally { touch.recycle() }
            }
            event(MotionEvent.ACTION_DOWN, 20f, 0)
            event(MotionEvent.ACTION_UP, 20f - 1.8f * ViewConfiguration.get(host).scaledTouchSlop, 500)
            assertEquals(1, count)
        } finally { host.finish() }
    }

    @Test fun legacyModeFourRetainsItsFormerFeedback() {
        val entries = mutableMapOf<String, Any?>("pref_key_system_strong_toast_mode" to "4")
        IslandPreferenceDefaults.migrate(entries)
        assertTrue(IslandPreferenceDefaults.feedbackKeys.all { entries[it] == true })
    }

    @Test fun restoringAnOldBackupRetainsFeedbackBeforeTheNextApplicationStartup() {
        val app = RuntimeEnvironment.getApplication()
        val prefs = app.getSharedPreferences("island-restore-repro", Context.MODE_PRIVATE)
        val bytes = ByteArrayOutputStream().also { stream ->
            ObjectOutputStream(stream).use { it.writeObject(hashMapOf("pref_key_system_strong_toast_mode" to "3")) }
        }.toByteArray()
        val result = BackupRestore.performRestore(ByteArrayInputStream(bytes), prefs, emptySet(), { true })
        assertTrue(result.commitSucceeded)
        assertTrue(IslandPreferenceDefaults.feedbackKeys.all { prefs.getBoolean(it, false) })
    }
}
