package tv.withaibuild.customiuizer.mods.island

import android.graphics.RectF
import android.os.UserHandle
import android.view.View
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tv.withaibuild.customiuizer.mods.island.LauncherMusicReturnAccessTest.*

/** Ownership regression: native null-target preparation replaces the old ThreadLocal icon borrowing. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LauncherMusicReturnWindowOwnershipTest {
    private val access = LauncherMusicReturnAccess(Nav::class.java, Element::class.java,
        Params::class.java, Targets::class.java, Remote::class.java, DeviceConfig::class.java)
    private val frame = IslandMusicReturnSignal.Frame("chosen.music", UserHandle.getUserHandleForUid(1000),
        280f, 15f, 800f, 125f, 1080, 2400)
    private fun source() = Params(Targets(arrayOf(Remote())), RectF(0f, 0f, 1080f, 2400f),
        RectF(200f, 1800f, 300f, 1900f), 32f, 20f, 1f, 0f, Type.CLOSE_TO_HOME,
        false, true, false, null, Any(), Any(), true, 0, 0, true, 0, false, false)

    @Test fun submissionDoesNotForceTheDesktopViewsVisibilityOrAlpha() {
        val icon = View(RuntimeEnvironment.getApplication()).apply { visibility = View.INVISIBLE; alpha = .35f }
        assertTrue(access.selectWindowTarget(Nav(), frame))
        val original = source()
        val copy = access.retarget(original, Element(), Nav(), frame) as Params
        assertNull(copy.targetView)
        assertTrue(copy.ignoreIcon)
        assertNull(original.targetView)
        assertEquals(View.INVISIBLE, icon.visibility)
        assertEquals(.35f, icon.alpha, 0f)
        assertSame(original.animListener, copy.animListener)
    }

    @Test fun replacementAndRejectedTargetsCannotStealAnotherTransitionsDesktopOwner() {
        val first = source()
        val second = source()
        val outer = access.retarget(first, Element(), Nav(), frame) as Params
        val nested = access.retarget(second, Element(), Nav(), frame.copy(left = 300f)) as Params
        assertNull(outer.targetView); assertNull(nested.targetView)
        assertNotSame(first, second)
        assertSame(first.animListener, outer.animListener)
        assertSame(second.animListener, nested.animListener)
        assertNull(access.retarget(second, Element(), Nav().apply { mRunningTaskId = 99 }, frame))
        assertNull(first.targetView); assertNull(second.targetView)
        nested.targetRect.offset(10f, 0f)
        assertEquals(frame.landingRectangle(), outer.targetRect)
        assertEquals(RectF(200f, 1800f, 300f, 1900f), first.targetRect)
    }

    @Test fun anotherSubmissionThreadUsesItsOwnCopyWithoutAnyBorrowedIconState() {
        val original = source()
        var copy: Params? = null
        val thread = Thread { copy = access.retarget(original, Element(), Nav(), frame) as Params }
        thread.start(); thread.join()
        assertNotSame(original, copy)
        assertNull(copy!!.targetView)
        assertNull(original.targetView)
        assertFalse(original.ignoreIcon)
        assertSame(original.clipAnimationHelper, copy!!.clipAnimationHelper)
    }
}
