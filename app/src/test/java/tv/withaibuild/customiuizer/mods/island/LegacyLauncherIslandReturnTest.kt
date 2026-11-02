package tv.withaibuild.customiuizer.mods.island

import android.os.UserHandle
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class LegacyLauncherIslandReturnTest {
    private val frame = IslandMusicReturnSignal.Frame("example.music", UserHandle.getUserHandleForUid(0),
        400f, 20f, 680f, 120f, 1080, 2460)
    private val task = LegacyLauncherIslandReturn.Task(42, 42, 0, 0, 0, 1,
        "example.music", "example.music", false, false)

    @Test fun onlyTheSelectedFullscreenTaskMayReplaceItsIconTarget() {
        assertTrue(LegacyLauncherIslandReturn.eligible(task, frame))
        for (other in listOf(task.copy(infoTask = 43), task.copy(infoUser = 10), task.copy(user = -1),
            task.copy(user = 10001, infoUser = 10001), task.copy(display = 1), task.copy(windowingMode = 2),
            task.copy(component = "example.other"), task.copy(top = null), task.copy(quickSwitch = true),
            task.copy(autoPip = true))) assertFalse(other.toString(), LegacyLauncherIslandReturn.eligible(other, frame))
        assertFalse(LegacyLauncherIslandReturn.eligible(task, frame.copy(user = UserHandle.getUserHandleForUid(1000000))))
        assertFalse(LegacyLauncherIslandReturn.eligible(task, frame.copy(right = Float.NaN)))
    }

    class Spring {
        @JvmField var mCenterXStiffness = 100f
        @JvmField var mCenterYStiffness = 100f
        @JvmField var mWidthStiffness = 100f
        @JvmField var mRatioStiffness = 200f
        @JvmField var mRadiusStiffness = 100f
        @JvmField var mAlphaStiffness = 100f
        fun getModifyResponse(value: Float) = value
    }

    @Test fun sixTrackTimingRestoresOnlyItsOwnWritesAndPreservesNativeRatios() {
        val timing = LegacyLauncherReturnTiming(Spring::class.java)
        val spring = Spring()
        assertTrue(timing.apply(spring))
        assertEquals(2f * spring.mWidthStiffness, spring.mRatioStiffness, .001f)
        spring.mAlphaStiffness = 999f
        timing.restore(Spring())
        assertNotEquals(100f, spring.mWidthStiffness)
        timing.restore(spring)
        assertEquals(100f, spring.mWidthStiffness, 0f)
        assertEquals(200f, spring.mRatioStiffness, 0f)
        assertEquals(999f, spring.mAlphaStiffness, 0f)
        spring.mWidthStiffness = Float.NaN
        assertFalse(timing.apply(spring))
        assertEquals(100f, spring.mCenterXStiffness, 0f)
    }
}
