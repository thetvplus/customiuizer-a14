package tv.withaibuild.customiuizer.mods.island

import android.app.PictureInPictureParams
import android.os.UserHandle
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class LauncherHomeReturnAccessTest {
    class Launcher { fun onAttachedToWindow() {} ; fun onDestroy() {} }
    class Provider { fun findClosingAnimTarget(host: Launcher, target: Remote): Any? = null }
    class Info {
        @JvmField var userId = 0
        @JvmField var taskId = 42
        @JvmField var displayId = 0
        @JvmField var pictureInPictureParams: PictureInPictureParams? = null
        var windowMode = 1
        fun getWindowingMode() = windowMode
    }
    class Native { @JvmField var taskInfo: Info? = Info() }
    class Remote {
        @JvmField var target: Native? = Native()
        @JvmField var packageName = "chosen.music"
        @JvmField var taskId = 42
        @JvmField var mode = 1
        @JvmField var activityType = 1
    }
    private val access = LauncherHomeReturnAccess(Provider::class.java, Launcher::class.java, Remote::class.java)
    private val frame = IslandMusicReturnSignal.Frame("chosen.music", UserHandle.getUserHandleForUid(1000),
        280f, 15f, 800f, 125f, 1080, 2400)

    @Test fun buttonHomeUsesTheActualClosingTaskAndNeverNeedsGestureState() {
        assertTrue(access.eligible(Remote(), frame))
        val checks: List<(Remote) -> Unit> = listOf(
            { it.mode = 0 }, { it.activityType = 2 }, { it.packageName = "other" },
            { it.target = null }, { it.target!!.taskInfo = null },
            { it.target!!.taskInfo!!.taskId++ }, { it.target!!.taskInfo!!.userId++ },
            { it.target!!.taskInfo!!.displayId = 1 }, { it.target!!.taskInfo!!.windowMode = 2 },
            { it.target!!.taskInfo!!.pictureInPictureParams = PictureInPictureParams.Builder().setAutoEnterEnabled(true).build() })
        for (change in checks) assertFalse(access.eligible(Remote().also(change), frame))
        assertTrue(access.eligible(Remote().apply {
            target!!.taskInfo!!.pictureInPictureParams = PictureInPictureParams.Builder().setAutoEnterEnabled(false).build()
        }, frame))
    }
}
