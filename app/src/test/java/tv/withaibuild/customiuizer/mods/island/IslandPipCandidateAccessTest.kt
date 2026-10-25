package tv.withaibuild.customiuizer.mods.island

import android.app.ActivityManager
import android.app.PictureInPictureParams
import android.content.ComponentName
import android.os.UserHandle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IslandPipCandidateAccessTest {
    private val user = UserHandle.getUserHandleForUid(1000)
    private fun task(pkg: String = "video", auto: Boolean = true, mode: Int = 1, who: Int = 0, display: Int = 0) =
        ActivityManager.RunningTaskInfo().apply {
            taskId = 17; topActivity = ComponentName(pkg, "Player")
            ReflectionHelpers.setField(this, "userId", who)
            ReflectionHelpers.setField(this, "displayId", display)
            ReflectionHelpers.setField(this, "pictureInPictureParams", PictureInPictureParams.Builder().setAutoEnterEnabled(auto).build())
            val configuration = ReflectionHelpers.getField<Any>(this, "configuration")
            val window = ReflectionHelpers.getField<Any>(configuration, "windowConfiguration")
            ReflectionHelpers.callInstanceMethod<Any>(window, "setWindowingMode", ClassParameter.from(Int::class.javaPrimitiveType!!, mode))
        }

    @Test fun onlyTheOutgoingDefaultDisplayUserAndDeclaredAutoPipAreAccepted() = runTest {
        val valid = task()
        assertEquals(IslandPipCandidate("video", user, 17), IslandPipCandidateAccess { listOf(valid) }.candidate("video", user))
        for (invalid in listOf(task(pkg = "other"), task(auto = false), task(mode = 5), task(who = 10), task(display = 1)))
            assertNull(IslandPipCandidateAccess { listOf(invalid) }.candidate("video", user))
        assertTrue(IslandPipCandidateAccess { listOf(task(mode = 2)) }.candidate("video", user)!!.alreadyPinned)
    }

    @Test fun theQueryIsBoundedAndAnOlderBackgroundTaskCannotOverrideTheFrontTask() = runTest {
        var reads = 0
        val many = List(16) { task(pkg = "other.$it") } + task()
        assertNull(IslandPipCandidateAccess { reads++; many }.candidate("video", user))
        assertEquals(1, reads)
        assertNull(IslandPipCandidateAccess { listOf(task(auto = false), task()) }.candidate("video", user))
    }
}
