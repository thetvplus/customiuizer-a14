package tv.withaibuild.customiuizer.mods

import android.app.Activity
import android.app.Notification
import android.content.Context
import android.os.Looper
import android.os.Process
import android.service.notification.StatusBarNotification
import android.view.View
import android.widget.FrameLayout
import java.time.Duration
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HeadsUpExpansionTest {
    private val controller = Robolectric.buildActivity(Activity::class.java).setup()
    private val activity = controller.get()
    private var selected = true
    private val effect = HeadsUpExpansion(Row::class.java) { selected }
    private val container = FrameLayout(activity).also { activity.setContentView(it) }
    private fun posted(id: Int) = StatusBarNotification("app", "app", id, null, Process.myUid(), 0, 0,
        Notification.Builder(activity, "test").build(), Process.myUserHandle(), id.toLong())
    private fun row(id: Int = 1) = Row(activity, Entry(posted(id))).also { container.addView(it) }
    private fun advance() = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
    @After fun close() { controller.pause().stop().destroy() }

    @Test fun repeatedHeadsUpUpdatesExpandOnlyOnceAndRespectExistingExpansion() {
        val row = row()
        assertTrue(row.isAttachedToWindow)
        repeat(5) { effect.update(row) }
        advance()
        assertEquals(1, row.clicks)
        effect.update(row)
        advance()
        assertEquals(1, row.clicks)
    }

    @Test fun removalAndRebindingCancelTheOldWorkWithoutAffectingAnotherRow() {
        val first = row(1)
        val second = row(2)
        effect.update(first); effect.update(second)
        effect.cancel(first)
        first.mRemoved = true
        advance()
        assertEquals(0, first.clicks); assertEquals(1, second.clicks)
        first.mRemoved = false
        effect.update(first)
        first.mEntry = Entry(posted(3)) // Defense even if a native rebind omits explicit cancellation.
        advance()
        assertEquals(0, first.clicks)
        effect.update(first)
        advance()
        assertEquals(1, first.clicks)
    }

    @Test fun anUpdatedNotificationWithTheSameKeyInvalidatesTheOldTask() {
        val row = row()
        effect.update(row)
        row.mEntry.mSbn = posted(1)
        advance()
        assertEquals(0, row.clicks)
    }

    @Test fun remoteInputLockscreenGestureAndDismissalPreventDelayedClicks() {
        for (state in 0..3) {
            val row = row(state + 1)
            effect.update(row)
            when (state) {
                0 -> row.mPrivateLayout.mRemoteInputVisible = true
                1 -> row.mOnKeyguard = true
                2 -> row.mUserLocked = true
                3 -> row.mIsHeadsUp = false
            }
            advance()
            assertEquals(0, row.clicks)
        }
    }

    @Test fun featureDisableAndDetachReleasePendingWork() {
        val row = row()
        effect.update(row)
        selected = false
        advance()
        assertEquals(0, row.clicks)
        selected = true
        effect.update(row)
        container.removeView(row)
        container.addView(row)
        advance()
        assertEquals(0, row.clicks)
    }

    @Test fun ordinaryListenerFailureDoesNotEscapeTheDelayedBoundary() {
        val row = row()
        row.mExpandClickListener = View.OnClickListener { throw IllegalStateException("native listener unavailable") }
        effect.update(row)
        advance()
        assertEquals(0, row.clicks)
        row.mExpandClickListener = View.OnClickListener { row.clicks++ }
        effect.update(row)
        advance()
        assertEquals(1, row.clicks)
    }

    class Entry(@JvmField var mSbn: StatusBarNotification)
    class Content { @JvmField var mRemoteInputVisible = false }
    class Row(context: Context, @JvmField var mEntry: Entry) : FrameLayout(context) {
        @JvmField var mOnKeyguard = false
        @JvmField var mIsHeadsUp = true
        @JvmField var mRemoved = false
        @JvmField var mUserLocked = false
        @JvmField val mPrivateLayout = Content()
        var expanded = false
        var clicks = 0
        @JvmField var mExpandClickListener = View.OnClickListener { clicks++; expanded = true }
        @Suppress("UNUSED_PARAMETER") fun isExpanded(allowOnKeyguard: Boolean) = expanded
    }
}
