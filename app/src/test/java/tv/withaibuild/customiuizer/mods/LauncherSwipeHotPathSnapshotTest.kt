package tv.withaibuild.customiuizer.mods

import io.github.libxposed.api.XposedInterface
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import tv.withaibuild.customiuizer.MainModule
import java.lang.reflect.Executable

/**
 * Behavioral tests for the homescreen swipe hot-path preference snapshots.
 */
class LauncherSwipeHotPathSnapshotTest {

    private val downActionKey = "launcher_swipedown_action"
    private val upActionKey = "launcher_swipeup_action"

    private var savedPrefs: Map<String, Any> = emptyMap()

    @Before
    fun setUp() {
        savedPrefs = MainModule.mPrefs.getAll()
        MainModule.mPrefs.clear()
        LauncherGestureHooks.refreshHomescreenSwipeSnapshots()
    }

    @After
    fun tearDown() {
        MainModule.mPrefs.clear()
        if (savedPrefs.isNotEmpty()) {
            (MainModule.mPrefs).replaceSnapshot(savedPrefs)
        } else {
            MainModule.mPrefs.clear()
        }
        LauncherGestureHooks.refreshHomescreenSwipeSnapshots()
    }

    @Test
    fun statusBarSwipeUsesSnapshotForCustomDownAction() {
        MainModule.mPrefs.put(downActionKey, 2)
        LauncherGestureHooks.onHomescreenSwipePreferenceChanged(downActionKey)

        val chain = FakeChain(proceedResult = true)

        val result = LauncherGestureHooks.onStatusBarSwipeCanInterceptTouch(chain)

        assertEquals(false, result)
        assertEquals("custom down action must skip original", 0, chain.proceedCount)
    }

    @Test
    fun statusBarSwipeProceedsWhenDownActionIsDefault() {
        MainModule.mPrefs.put(downActionKey, 1)
        LauncherGestureHooks.onHomescreenSwipePreferenceChanged(downActionKey)

        val chain = FakeChain(proceedResult = true)

        val result = LauncherGestureHooks.onStatusBarSwipeCanInterceptTouch(chain)

        assertEquals(true, result)
        assertEquals("default down action must call original exactly once", 1, chain.proceedCount)
    }

    @Test
    fun allAppsSwipeUsesSnapshotForCustomUpAction() {
        MainModule.mPrefs.put(upActionKey, 2)
        LauncherGestureHooks.onHomescreenSwipePreferenceChanged(upActionKey)

        val chain = FakeChain(proceedResult = true)

        val result = LauncherGestureHooks.onAllAppsSwipeCanInterceptTouch(chain)

        assertEquals(false, result)
        assertEquals("custom up action must skip original", 0, chain.proceedCount)
    }

    @Test
    fun allAppsSwipeProceedsWhenUpActionIsDefault() {
        MainModule.mPrefs.put(upActionKey, 1)
        LauncherGestureHooks.onHomescreenSwipePreferenceChanged(upActionKey)

        val chain = FakeChain(proceedResult = true)

        val result = LauncherGestureHooks.onAllAppsSwipeCanInterceptTouch(chain)

        assertEquals(true, result)
        assertEquals("default up action must call original exactly once", 1, chain.proceedCount)
    }

    @Test
    fun unrelatedKeyDoesNotRefreshSnapshot() {
        MainModule.mPrefs.put(downActionKey, 2)
        MainModule.mPrefs.put(upActionKey, 2)
        LauncherGestureHooks.onHomescreenSwipePreferenceChanged(null)

        MainModule.mPrefs.put(downActionKey, 1)
        MainModule.mPrefs.put(upActionKey, 1)
        LauncherGestureHooks.onHomescreenSwipePreferenceChanged("launcher_some_other_key")

        val downChain = FakeChain(proceedResult = true)
        val upChain = FakeChain(proceedResult = true)

        val downResult = LauncherGestureHooks.onStatusBarSwipeCanInterceptTouch(downChain)
        val upResult = LauncherGestureHooks.onAllAppsSwipeCanInterceptTouch(upChain)

        assertEquals(false, downResult)
        assertEquals(false, upResult)
        assertEquals(0, downChain.proceedCount)
        assertEquals(0, upChain.proceedCount)
    }

    @Test
    fun nullKeyRefreshesBothSnapshots() {
        MainModule.mPrefs.put(downActionKey, 2)
        MainModule.mPrefs.put(upActionKey, 2)

        val downChain = FakeChain(proceedResult = true)
        val upChain = FakeChain(proceedResult = true)

        assertEquals(true, LauncherGestureHooks.onStatusBarSwipeCanInterceptTouch(downChain))
        assertEquals(true, LauncherGestureHooks.onAllAppsSwipeCanInterceptTouch(upChain))
        assertEquals(1, downChain.proceedCount)
        assertEquals(1, upChain.proceedCount)

        LauncherGestureHooks.onHomescreenSwipePreferenceChanged(null)

        val downChain2 = FakeChain(proceedResult = true)
        val upChain2 = FakeChain(proceedResult = true)

        assertEquals(false, LauncherGestureHooks.onStatusBarSwipeCanInterceptTouch(downChain2))
        assertEquals(false, LauncherGestureHooks.onAllAppsSwipeCanInterceptTouch(upChain2))
        assertEquals(0, downChain2.proceedCount)
        assertEquals(0, upChain2.proceedCount)
    }

    @Test
    fun pinchAndFsgSnapshotRefreshWithoutReinstall() {
        MainModule.mPrefs.put("launcher_pinch_action", 1)
        MainModule.mPrefs.put("controls_fsg_swipeandstop_disablevibrate", false)
        MainModule.mPrefs.put("controls_fsg_horiz_apps", emptySet<String>())
        LauncherGestureHooks.onHomescreenSwipePreferenceChanged(null)

        assertFalse(LauncherGestureHooks.shouldBlockPinchScale())
        assertFalse(LauncherGestureHooks.isSwipeAndStopVibrateDisabled())
        assertFalse(LauncherGestureHooks.isFsgHorizApp("com.example.app"))

        MainModule.mPrefs.put("launcher_pinch_action", 2)
        MainModule.mPrefs.put("controls_fsg_swipeandstop_disablevibrate", true)
        MainModule.mPrefs.put("controls_fsg_horiz_apps", setOf("com.example.app"))
        LauncherGestureHooks.onHomescreenSwipePreferenceChanged("launcher_pinch_action")
        LauncherGestureHooks.onHomescreenSwipePreferenceChanged("controls_fsg_swipeandstop_disablevibrate")
        LauncherGestureHooks.onHomescreenSwipePreferenceChanged("controls_fsg_horiz_apps")

        assertTrue(LauncherGestureHooks.shouldBlockPinchScale())
        assertTrue(LauncherGestureHooks.isSwipeAndStopVibrateDisabled())
        assertTrue(LauncherGestureHooks.isFsgHorizApp("com.example.app"))
        assertFalse(LauncherGestureHooks.isFsgHorizApp("com.other.app"))
    }

    @Test
    fun snapshotReflectsPreferenceWithoutReinstallingHooks() {
        MainModule.mPrefs.put(downActionKey, 1)
        LauncherGestureHooks.onHomescreenSwipePreferenceChanged(downActionKey)

        val defaultChain = FakeChain(proceedResult = true)
        assertEquals(true, LauncherGestureHooks.onStatusBarSwipeCanInterceptTouch(defaultChain))

        MainModule.mPrefs.put(downActionKey, 2)
        LauncherGestureHooks.onHomescreenSwipePreferenceChanged(downActionKey)

        val customChain = FakeChain(proceedResult = true)
        assertEquals(false, LauncherGestureHooks.onStatusBarSwipeCanInterceptTouch(customChain))
    }

    @Test
    fun fatalErrorFromOriginalProceedPropagates() {
        MainModule.mPrefs.put(downActionKey, 1)
        LauncherGestureHooks.onHomescreenSwipePreferenceChanged(downActionKey)
        val error = OutOfMemoryError("swipe OOM")
        val chain = FakeChain(proceedThrow = error)

        try {
            LauncherGestureHooks.onStatusBarSwipeCanInterceptTouch(chain)
            assertTrue("fatal error must propagate", false)
        } catch (t: Throwable) {
            assertSame(error, t)
        }

        assertEquals(1, chain.proceedCount)
    }

    @Test
    fun concurrentVisibilityOfSwipeSnapshot() {
        MainModule.mPrefs.put(downActionKey, 1)
        LauncherGestureHooks.refreshHomescreenSwipeSnapshots()

        val latch = java.util.concurrent.CountDownLatch(1)
        Thread {
            MainModule.mPrefs.put(downActionKey, 2)
            LauncherGestureHooks.refreshHomescreenSwipeSnapshots()
            latch.countDown()
        }.start()

        val chain = FakeChain(proceedResult = true)
        var seen = false
        val deadline = java.lang.System.currentTimeMillis() + 5_000
        while (java.lang.System.currentTimeMillis() < deadline) {
            chain.proceedCount = 0
            val result = LauncherGestureHooks.onStatusBarSwipeCanInterceptTouch(chain)
            if (result == false) {
                seen = true
                break
            }
        }
        latch.await()

        assertTrue("snapshot write from another thread must become visible", seen)
    }

    private class FakeChain(
        private val proceedResult: Any? = null,
        private val proceedThrow: Throwable? = null,
    ) : XposedInterface.Chain {

        var proceedCount = 0

        override fun getExecutable(): Executable = error("not used in test")
        override fun getThisObject(): Any? = null
        override fun getArgs(): List<Any?> = emptyList()
        override fun getArg(index: Int): Any? = null

        override fun proceed(): Any? {
            proceedCount++
            proceedThrow?.let { throw it }
            return proceedResult
        }

        override fun proceed(p0: Array<Any>): Any? {
            proceedCount++
            proceedThrow?.let { throw it }
            return proceedResult
        }

        override fun proceedWith(p0: Any): Any? = error("not used in test")
        override fun proceedWith(p0: Any, p1: Array<Any>): Any? = error("not used in test")
    }
}
