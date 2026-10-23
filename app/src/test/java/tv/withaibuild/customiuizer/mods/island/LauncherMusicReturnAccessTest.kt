package tv.withaibuild.customiuizer.mods.island

import android.content.ComponentName
import android.graphics.RectF
import android.os.UserHandle
import android.view.MotionEvent
import android.view.View
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LauncherMusicReturnAccessTest {
    enum class Type { CLOSE_TO_HOME, CLOSE_TO_RECENTS, OPEN_FROM_HOME, CLOSE_TO_DRAG }
    class TaskInfo(var mode: Int = 1) { fun getWindowingMode() = mode }
    class Nav {
        @JvmField var mRunningTaskId = 42
        @JvmField var mRunningTaskUserId = 0
        @JvmField var mRunningTaskComponentName = ComponentName("chosen.music", "Player")
        @JvmField var mRunningTaskInfo: TaskInfo? = TaskInfo()
        fun onAttachedToWindow() {}
        fun onDetachedFromWindow() {}
        fun onTouchEvent(event: MotionEvent): Boolean = false
    }
    class Remote {
        companion object { const val MODE_CLOSING = 1; const val ACTIVITY_TYPE_STANDARD = 1 }
        @JvmField var packageName = "chosen.music"
        @JvmField var taskId = 42
        @JvmField var mode = MODE_CLOSING
        @JvmField var activityType = ACTIVITY_TYPE_STANDARD
    }
    class Targets(@JvmField val apps: Array<Remote>)
    class Element(@JvmField var remoteAnimationTargetSet: Targets? = null) { fun animTo(value: Any) {} }
    class Params(val targetApps: Targets?, val startRect: RectF?, val targetRect: RectF,
        val startRadius: Float, val endRadius: Float, val startAlpha: Float, val endAlpha: Float,
        val animType: Type, val ignoreIcon: Boolean, val showTask: Boolean, val isQuickSwitchMode: Boolean,
        val targetView: View?, val animListener: Any?, val clipAnimationHelper: Any?, val clearLastListener: Boolean,
        val currentDisplayRotation: Int, val homeRotation: Int, val needFinishOnAnimEnd: Boolean,
        val currentTaskIndex: Int, val isSplitModeBack: Boolean, val isUseTranslucentAnim: Boolean)
    private val access = LauncherMusicReturnAccess(Nav::class.java, Element::class.java, Params::class.java,
        Targets::class.java, Remote::class.java)
    private val frame = IslandMusicReturnSignal.Frame("chosen.music", UserHandle.getUserHandleForUid(1000),
        280f, 15f, 800f, 125f, 1080, 2400)
    private fun params(type: Type = Type.CLOSE_TO_HOME, quick: Boolean = false, split: Boolean = false,
        rotation: Int = 0, targets: Targets? = Targets(arrayOf(Remote()))) = Params(targets,
        RectF(0f, 0f, 1080f, 2400f), RectF(200f, 1800f, 300f, 1900f), 32f, 20f, 1f, 0f,
        type, false, true, quick, null, Any(), Any(), true, rotation, 0, true, 0, split, false)

    @Test fun onlyTheClosingWindowDestinationAndIconHandoffChangeInANativeCopy() {
        val source = params()
        val result = access.retarget(source, Element(), Nav(), frame) as Params
        assertNotSame(source, result)
        assertEquals(frame.rectangle(), result.targetRect)
        assertEquals(RectF(200f, 1800f, 300f, 1900f), source.targetRect)
        assertEquals(55f, result.endRadius, 0f)
        assertTrue(result.ignoreIcon); assertFalse(source.ignoreIcon)
        assertNull(result.targetView)
        for (field in Params::class.java.declaredFields) {
            if (field.name in setOf("targetRect", "endRadius", "ignoreIcon", "targetView")) continue
            field.isAccessible = true
            assertEquals("Preserve native ${field.name}", field.get(source), field.get(result))
        }
        assertSame(source.animListener, result.animListener)
        assertSame(source.clipAnimationHelper, result.clipAnimationHelper)
        assertSame(source.targetApps, result.targetApps)
    }

    @Test fun recentsOpeningDragSplitQuickSwitchAndRotationRemainNative() {
        val candidates = Type.entries.filter { it != Type.CLOSE_TO_HOME }.map { params(type = it) } +
            listOf(params(quick = true), params(split = true), params(rotation = 1))
        for (source in candidates) {
            assertNull(access.retarget(source, Element(), Nav(), frame))
            assertFalse(source.ignoreIcon)
        }
    }

    @Test fun packageTaskUserAndSingleClosingTargetMustAllMatch() {
        val source = params()
        for (nav in listOf(Nav().apply { mRunningTaskId = 43 }, Nav().apply { mRunningTaskUserId = 10 },
            Nav().apply { mRunningTaskComponentName = ComponentName("other.app", "Player") },
            Nav().apply { mRunningTaskInfo = null }, Nav().apply { mRunningTaskInfo!!.mode = 2 },
            Nav().apply { mRunningTaskInfo!!.mode = 5 }, Nav().apply { mRunningTaskInfo!!.mode = 6 }))
            assertNull(access.retarget(source, Element(), nav, frame))
        val remotes = listOf(Remote().apply { packageName = "other.app" }, Remote().apply { taskId = 41 },
            Remote().apply { mode = 0 }, Remote().apply { activityType = 2 })
        for (remote in remotes) assertNull(access.retarget(params(targets = Targets(arrayOf(remote))), Element(), Nav(), frame))
        assertNull(access.retarget(params(targets = Targets(arrayOf(Remote(), Remote()))), Element(), Nav(), frame))
        assertNull(access.retarget(params(targets = Targets(emptyArray())), Element(), Nav(), frame))
        assertNull(access.retarget(source, Element(), Nav(), frame.copy(left = Float.NaN)))
    }

    @Test fun aRetainedNativeTargetSetIsAllowedOnlyWithTheSameIdentity() {
        val source = params(targets = null)
        assertNull(access.retarget(source, Element(), Nav(), frame))
        val owner = Element(Targets(arrayOf(Remote())))
        assertNotNull(access.retarget(source, owner, Nav(), frame))
        owner.remoteAnimationTargetSet = Targets(arrayOf(Remote().apply { taskId = 40 }))
        assertNull(access.retarget(source, owner, Nav(), frame))
    }
}
