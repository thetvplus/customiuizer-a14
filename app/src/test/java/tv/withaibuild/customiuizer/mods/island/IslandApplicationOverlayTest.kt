package tv.withaibuild.customiuizer.mods.island

import android.content.Context
import android.os.SystemClock
import android.os.UserHandle
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.withaibuild.customiuizer.mods.utils.feature.IslandApplicationOverlayFeature
import tv.withaibuild.customiuizer.utils.PrefMap

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class IslandApplicationOverlayTest {
    private val access = IslandApplicationOverlayAccess(Wms::class.java, Session::class.java,
        Surface::class.java, Animator::class.java, Window::class.java)

    @Test fun nativeInventoryUsesApplicationUidCurrentUserAndDefaultDisplayAndDropsDeadSessions() {
        val wms = Wms()
        val first = Session(wms, 10318, mutableSetOf(Surface(0), Surface(0)))
        val otherUser = Session(wms, 1010318, mutableSetOf(Surface(0)))
        wms.mSessions.addAll(listOf(first, otherUser, Session(wms, 1000, mutableSetOf(Surface(0))),
            Session(wms, 10318, mutableSetOf(Surface(1)))))
        assertEquals(setOf(0, 10), access.snapshot(wms).users)
        assertEquals(setOf(10318, 1010318), access.snapshot(wms).uids)
        assertTrue(access.snapshot(wms).owns(10318))
        assertFalse(access.snapshot(wms).owns(10319))
        first.mAppOverlaySurfaces.remove(first.mAppOverlaySurfaces.first())
        assertEquals(setOf(0, 10), access.snapshot(wms).users)
        first.mAppOverlaySurfaces.clear()
        assertEquals(setOf(10), access.snapshot(wms).users)
        wms.mSessions.remove(otherUser)
        assertEquals(emptySet<Int>(), access.snapshot(wms).users)
    }

    @Test fun overflowingInventoryStaysBoundedAndReportsUnknownRatherThanFalseAbsence() {
        val wms = Wms()
        repeat(65) { wms.mSessions.add(Session(wms, it * 100000 + 10318, mutableSetOf(Surface(0)))) }
        assertFalse(access.snapshot(wms).known)
        assertTrue(access.snapshot(wms).users.isEmpty())
    }

    @Test fun packetsRejectStaleMalformedAndDuplicateUsersAndPreserveNonceAndUserIsolation() {
        val state = IslandApplicationOverlaySignal.Snapshot(setOf(0, 10))
        val message = IslandApplicationOverlaySignal.encode(state, 33, 91)
        val now = SystemClock.uptimeMillis()
        val parsed = IslandApplicationOverlaySignal.decode(message, now)!!
        assertEquals(91L, parsed.nonce)
        assertTrue(parsed.acceptedBy(91L, 32))
        assertFalse(parsed.acceptedBy(92L, 32))
        assertFalse(parsed.acceptedBy(91L, 33))
        assertTrue(parsed.copy(nonce = 0).acceptedBy(92L, -1))
        assertTrue(parsed.state.hasWindow(UserHandle.getUserHandleForUid(1010318)))
        assertFalse(parsed.state.hasWindow(UserHandle.getUserHandleForUid(1110318)))
        assertNull(IslandApplicationOverlaySignal.decode(message, now + 5001))
        assertNull(IslandApplicationOverlaySignal.decode(message.putExtra("users", intArrayOf(0, 0)), now))
        assertNull(IslandApplicationOverlaySignal.decode(message.putExtra("users", intArrayOf(-1)), now))
    }

    @Test fun disabledOrNonIslandModesDoNotInstallTheSystemServerFeature() {
        val prefs = PrefMap()
        assertFalse(IslandApplicationOverlayFeature.evaluateEnabled(prefs))
        prefs.put("system_island_hide_in_floating_window", true)
        for (mode in 0..4) {
            prefs.put("system_strong_toast_mode", mode.toString())
            assertEquals(mode in 3..4, IslandApplicationOverlayFeature.evaluateEnabled(prefs))
        }
    }

    @Test fun ownerPacketsRejectDuplicateUidsCrossUserClaimsAndOverflow() {
        val message = IslandApplicationOverlaySignal.encode(
            IslandApplicationOverlaySignal.Snapshot(setOf(0), uids = setOf(10318)), 80)
        val now = SystemClock.uptimeMillis()
        assertTrue(IslandApplicationOverlaySignal.decode(message, now)!!.state.owns(10318))
        assertFalse(IslandApplicationOverlaySignal.decode(message, now)!!.state.owns(10319))
        assertNull(IslandApplicationOverlaySignal.decode(message.putExtra("uids", intArrayOf(10318, 10318)), now))
        assertNull(IslandApplicationOverlaySignal.decode(message.putExtra("uids", intArrayOf(1010318)), now))
        assertNull(IslandApplicationOverlaySignal.decode(message.putExtra("uids", IntArray(65) { 10000 + it }), now))
    }

    class Wms {
        @JvmField val mGlobalLock = Any()
        @JvmField val mSessions = mutableSetOf<Session>()
        @JvmField val mContext: Context? = null
    }
    class Session(@JvmField val mService: Wms, @JvmField val mUid: Int,
        @JvmField val mAppOverlaySurfaces: MutableSet<Surface>) {
        fun onWindowSurfaceVisibilityChanged(surface: Surface, shown: Boolean, type: Int) = Unit
        fun killSessionLocked() = Unit
    }
    class Surface(display: Int) { @JvmField val mAnimator = Animator(display) }
    class Animator(display: Int) { @JvmField val mWin = Window(display) }
    class Window(private val display: Int) { fun getDisplayId() = display }
}
