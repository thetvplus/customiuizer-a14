package tv.withaibuild.customiuizer.mods.island

import android.content.ComponentName
import android.app.PictureInPictureParams
import android.graphics.RectF
import android.graphics.Rect
import android.os.UserHandle
import android.view.MotionEvent
import android.view.View
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LauncherMusicReturnAccessTest {
    enum class Type { CLOSE_TO_HOME, CLOSE_TO_RECENTS, OPEN_FROM_HOME, CLOSE_TO_DRAG }
    class TaskInfo(var mode: Int = 1) {
        @JvmField var pictureInPictureParams: PictureInPictureParams? = null
        fun getWindowingMode() = mode
    }
    class Nav {
        @JvmField var mRunningTaskId = 42
        @JvmField var mRunningTaskUserId = 0
        @JvmField var mRunningTaskComponentName = ComponentName("chosen.music", "Player")
        @JvmField var mRunningTaskInfo: TaskInfo? = TaskInfo()
        @JvmField var mAppToHomeTargetRect = Rect()
        @JvmField var mTouchRange = 1
        fun onAttachedToWindow() {}
        fun onDetachedFromWindow() {}
        fun onTouchEvent(event: MotionEvent): Boolean = false
        fun findClosingAnimTarget(force: Boolean): View? = null
    }
    class DeviceConfig {
        companion object { @JvmStatic fun getClosingAppDefaultDisappearRect(range: Int) = Rect(100, 200, 180, 280) }
    }
    class Remote {
        companion object { const val MODE_OPENING = 0; const val MODE_CLOSING = 1; const val ACTIVITY_TYPE_STANDARD = 1 }
        @JvmField var packageName = "chosen.music"
        @JvmField var taskId = 42
        @JvmField var mode = MODE_CLOSING
        @JvmField var activityType = ACTIVITY_TYPE_STANDARD
    }
    class Targets(@JvmField val apps: Array<Remote>)
    class Animation(var targetRect: RectF = RectF())
    open class Element(@JvmField var remoteAnimationTargetSet: Targets? = null) {
        @JvmField var currentRectFParams: Any? = null
        @JvmField val mAnim = Animation()
        var running = false
        fun isRunning() = running
        open fun animTo(value: Any) {}
        fun updateFloatingIconView(launcher: Any, params: Params) {}
    }
    class FastElement : Element() {
        var packageName = "chosen.music"
        override fun animTo(value: Any) { super.animTo(value) }
        fun getFastLaunchRunningTaskPkgName() = packageName
    }
    class Params(val targetApps: Targets?, val startRect: RectF?, val targetRect: RectF,
        val startRadius: Float, val endRadius: Float, val startAlpha: Float, val endAlpha: Float,
        val animType: Type, val ignoreIcon: Boolean, val showTask: Boolean, val isQuickSwitchMode: Boolean,
        val targetView: View?, val animListener: Any?, val clipAnimationHelper: Any?, val clearLastListener: Boolean,
        val currentDisplayRotation: Int, val homeRotation: Int, val needFinishOnAnimEnd: Boolean,
        val currentTaskIndex: Int, val isSplitModeBack: Boolean, val isUseTranslucentAnim: Boolean)
    private val access = LauncherMusicReturnAccess(Nav::class.java, Element::class.java, Params::class.java,
        Targets::class.java, Remote::class.java, DeviceConfig::class.java)
    private val frame = IslandMusicReturnSignal.Frame("chosen.music", UserHandle.getUserHandleForUid(1000),
        280f, 15f, 800f, 125f, 1080, 2400)

    @Test fun incomingWindowsLandInsideTheIslandWithTheSameCentreAndHeight() {
        val source = params()
        val target = (access.retarget(source, Element(), Nav(), frame) as Params).targetRect
        assertTrue(target.width() < frame.right - frame.left)
        assertTrue(target.left > frame.left)
        assertTrue(target.right < frame.right)
        assertEquals((frame.left + frame.right) * .5f, target.centerX(), .001f)
        assertEquals(frame.top, target.top, 0f)
        assertEquals(frame.bottom, target.bottom, 0f)
        assertEquals(RectF(200f, 1800f, 300f, 1900f), source.targetRect)
    }

    @Test fun musicClockRecorderAndCallWindowsUseTheSameBoundedLandingPolicy() {
        for (pkg in listOf("chosen.music", "com.android.deskclock", "com.android.soundrecorder", "com.android.incallui")) {
            val current = frame.copy(packageName = pkg)
            val source = params(targets = Targets(arrayOf(Remote().apply { packageName = pkg })))
            val nav = Nav().apply { mRunningTaskComponentName = ComponentName(pkg, "Activity") }
            val target = (access.retarget(source, Element(), nav, current) as Params).targetRect
            assertEquals((frame.right - frame.left) * .92f, target.width(), .001f)
            assertEquals((frame.left + frame.right) * .5f, target.centerX(), .001f)
            assertEquals(frame.bottom - frame.top, target.height(), .001f)
        }
        for (width in listOf(38f, 48f, 100f, 160f, 320f)) {
            val current = frame.copy(left = 0f, right = width, top = 0f, bottom = 38f)
            val target = current.landingRectangle()
            assertTrue(target.width() >= 38f)
            assertTrue(target.left >= current.left && target.right <= current.right)
        }
    }

    @Test fun quickOpeningOverrideAndItsSuperDispatchChooseExactlyOneInstalledEntry() {
        val quick = LauncherMusicReturnAccess(Nav::class.java, Element::class.java, Params::class.java,
            Targets::class.java, Remote::class.java, DeviceConfig::class.java, FastElement::class.java)
        assertEquals(Element::class.java, quick.animationEntry(Element(), true).declaringClass)
        assertEquals(FastElement::class.java, quick.animationEntry(FastElement(), true).declaringClass)
        assertEquals("A failed optional hook must leave the base path active", Element::class.java,
            quick.animationEntry(FastElement(), false).declaringClass)
        val owner = FastElement().apply { remoteAnimationTargetSet = Targets(arrayOf(Remote())) }
        assertNotNull(quick.retarget(params(targets = null), owner, Nav(), frame))
    }

    @Test fun quickOpeningCanPrefetchBeforeItsRemoteTargetsArriveWithoutChangingTheWindow() {
        val quick = LauncherMusicReturnAccess(Nav::class.java, Element::class.java, Params::class.java,
            Targets::class.java, Remote::class.java, DeviceConfig::class.java, FastElement::class.java)
        val source = params(type = Type.OPEN_FROM_HOME, targets = null,
            targetView = View(RuntimeEnvironment.getApplication()))
        assertEquals("chosen.music", quick.openingPackage(source, FastElement()))
        val reused = FastElement().apply { remoteAnimationTargetSet = Targets(arrayOf(Remote())) }
        assertEquals("A retained closing target must not hide the next fast opening", "chosen.music",
            quick.openingPackage(source, reused))
        assertNull(quick.openingPackage(params(type = Type.OPEN_FROM_HOME), reused))
        assertNotNull(source.targetView)
        assertFalse(source.ignoreIcon)
        assertNull(quick.retarget(source, FastElement(), Nav(), frame))
    }
    private fun params(type: Type = Type.CLOSE_TO_HOME, quick: Boolean = false, split: Boolean = false,
        rotation: Int = 0, targets: Targets? = Targets(arrayOf(Remote())),
        targetView: View? = null) = Params(targets,
        RectF(0f, 0f, 1080f, 2400f), RectF(200f, 1800f, 300f, 1900f), 32f, 20f, 1f, 0f,
        type, false, true, quick, targetView, Any(), Any(), true, rotation, 0, true, 0, split, false)

    @Test fun closingWindowUsesNativeNoIconTargetWithoutChangingTheOriginal() {
        val source = params()
        val result = access.retarget(source, Element(), Nav(), frame) as Params
        assertNotSame(source, result)
        assertEquals(frame.landingRectangle(), result.targetRect)
        assertEquals(RectF(200f, 1800f, 300f, 1900f), source.targetRect)
        assertEquals(55f, result.endRadius, 0f)
        assertTrue(result.ignoreIcon); assertFalse(source.ignoreIcon)
        assertNull("The window return must not borrow or animate the desktop icon", result.targetView)
        assertNull(source.targetView)
        for (field in Params::class.java.declaredFields) {
            if (field.name in setOf("targetRect", "endRadius", "ignoreIcon", "targetView")) continue
            field.isAccessible = true
            assertEquals("Preserve native ${field.name}", field.get(source), field.get(result))
        }
        assertSame(source.animListener, result.animListener)
        assertSame(source.clipAnimationHelper, result.clipAnimationHelper)
        assertSame(source.targetApps, result.targetApps)
    }

    @Test fun returnFromAnotherPageKeepsTheNativeAbsentIconTarget() {
        val source = params(targetView = null)
        val result = access.retarget(source, Element(), Nav(), frame) as Params
        assertNull(result.targetView)
        assertTrue(result.ignoreIcon)
    }

    @Test fun aDesktopIconAlreadyChosenByNativeCodeCannotBeHijackedLate() {
        val icon = View(RuntimeEnvironment.getApplication())
        val source = params(targetView = icon)
        assertNull(access.retarget(source, Element(), Nav(), frame))
        assertSame(icon, source.targetView)
        assertFalse(source.ignoreIcon)
    }

    @Test fun earlySelectionUsesTheNativeNoIconFallbackAndLeavesOtherTasksUntouched() {
        val nav = Nav()
        assertTrue(access.selectWindowTarget(nav, frame))
        assertEquals(Rect(100, 200, 180, 280), nav.mAppToHomeTargetRect)
        nav.mRunningTaskComponentName = ComponentName("other.app", "Player")
        nav.mAppToHomeTargetRect.set(1, 2, 3, 4)
        assertFalse(access.selectWindowTarget(nav, frame))
        assertEquals(Rect(1, 2, 3, 4), nav.mAppToHomeTargetRect)
    }

    @Test fun prefetchRecognizesOnlyTheNativeStandardHomeOpeningWithoutChangingItsTarget() {
        val remote = Remote().apply { mode = Remote.MODE_OPENING }
        val source = params(type = Type.OPEN_FROM_HOME, targets = Targets(arrayOf(remote)),
            targetView = View(RuntimeEnvironment.getApplication()))
        val rectangle = RectF(source.targetRect)
        assertEquals("chosen.music", access.openingPackage(source, Element()))
        assertEquals(rectangle, source.targetRect)
        assertNotNull(source.targetView)
        assertFalse(source.ignoreIcon)
        assertNull(access.openingPackage(params(), Element()))
        assertNull(access.openingPackage(params(type = Type.OPEN_FROM_HOME, quick = true), Element()))
        assertNull(access.openingPackage(params(type = Type.OPEN_FROM_HOME, split = true), Element()))
        assertNull(access.openingPackage(params(type = Type.OPEN_FROM_HOME, rotation = 1), Element()))
        remote.activityType = 2
        assertNull(access.openingPackage(source, Element()))
        remote.activityType = Remote.ACTIVITY_TYPE_STANDARD
        assertNull(access.openingPackage(params(type = Type.OPEN_FROM_HOME,
            targets = Targets(arrayOf(remote, remote))), Element()))
    }

    @Test fun recentsOpeningDragSplitQuickSwitchAndRotationRemainNative() {
        val candidates = Type.entries.filter { it != Type.CLOSE_TO_HOME }.map { params(type = it) } +
            listOf(params(quick = true), params(split = true), params(rotation = 1))
        for (source in candidates) {
            assertNull(access.retarget(source, Element(), Nav(), frame))
            assertFalse(source.ignoreIcon)
        }
    }

    @Test fun declaredAutoPipKeepsTheNativeReturnInsteadOfAcknowledgingAnEarlyIslandEntrance() {
        val nav = Nav()
        nav.mRunningTaskInfo!!.pictureInPictureParams = PictureInPictureParams.Builder().setAutoEnterEnabled(true).build()
        val original = params()
        assertNull(access.retarget(original, Element(), nav, frame))
        assertNull(original.targetView)
        assertFalse(original.ignoreIcon)
        nav.mRunningTaskInfo!!.pictureInPictureParams = PictureInPictureParams.Builder().setAutoEnterEnabled(false).build()
        assertNotNull(access.retarget(original, Element(), nav, frame))
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

    @Test fun acknowledgingRequiresTheSameAdoptedCopyAndARunningNativeTarget() {
        val owner = Element()
        val copy = access.retarget(params(), owner, Nav(), frame) as Params
        assertFalse(access.accepted(copy, owner))
        owner.currentRectFParams = copy
        owner.mAnim.targetRect = RectF(copy.targetRect)
        assertFalse(access.accepted(copy, owner)) // canPlayingTransition=false does not start the native animation.
        owner.running = true
        assertTrue(access.accepted(copy, owner))
        owner.mAnim.targetRect.offset(1f, 0f)
        assertFalse(access.accepted(copy, owner))
        owner.mAnim.targetRect.set(copy.targetRect)
        owner.currentRectFParams = params()
        assertFalse(access.accepted(copy, owner))
    }
}
