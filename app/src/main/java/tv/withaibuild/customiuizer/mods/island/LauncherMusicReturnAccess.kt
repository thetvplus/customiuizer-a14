package tv.withaibuild.customiuizer.mods.island

import android.content.ComponentName
import android.graphics.RectF
import android.os.UserHandle
import android.view.MotionEvent
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers

/** Exact HyperOS 1 WindowElement contract; all reflection is resolved before installation. */
internal class LauncherMusicReturnAccess(nav: Class<*>, element: Class<*>,
    private val params: Class<*>, targets: Class<*>, remote: Class<*>) {
    constructor(loader: ClassLoader) : this(
        XposedHelpers.findClass("com.miui.home.recents.NavStubView", loader),
        XposedHelpers.findClass("com.miui.home.recents.anim.WindowElement", loader),
        XposedHelpers.findClass("com.miui.home.recents.anim.RectFParams", loader),
        XposedHelpers.findClass("com.miui.home.recents.util.RemoteAnimationTargetSet", loader),
        XposedHelpers.findClass("com.android.systemui.shared.recents.system.RemoteAnimationTargetCompat", loader))

    val attached = nav.getDeclaredMethod("onAttachedToWindow")
    val detached = nav.getDeclaredMethod("onDetachedFromWindow")
    val touch = nav.getDeclaredMethod("onTouchEvent", MotionEvent::class.java)
    val animate = element.getDeclaredMethod("animTo", Any::class.java)
    val prepareIcon = element.declaredMethods.single {
        it.name == "updateFloatingIconView" && it.parameterTypes.size == 2 && it.parameterTypes[1] == params
    }
    private val taskId = nav.getDeclaredField("mRunningTaskId").apply { isAccessible = true }
    private val taskUser = nav.getDeclaredField("mRunningTaskUserId").apply { isAccessible = true }
    private val component = nav.getDeclaredField("mRunningTaskComponentName").apply { isAccessible = true }
    private val taskInfo = nav.getDeclaredField("mRunningTaskInfo").apply { isAccessible = true }
    private val windowingMode = taskInfo.type.getMethod("getWindowingMode")
    private val retainedTargets = element.getDeclaredField("remoteAnimationTargetSet").apply { isAccessible = true }
    private val currentParams = element.getDeclaredField("currentRectFParams").apply { isAccessible = true }
    private val animation = element.getDeclaredField("mAnim").apply { isAccessible = true }
    private val animationTarget = animation.type.getMethod("getTargetRect")
    private val running = element.getMethod("isRunning")
    private val apps = targets.getDeclaredField("apps").apply { isAccessible = true }
    private val remotePackage = remote.getDeclaredField("packageName").apply { isAccessible = true }
    private val remoteTask = remote.getDeclaredField("taskId").apply { isAccessible = true }
    private val remoteMode = remote.getDeclaredField("mode").apply { isAccessible = true }
    private val remoteType = remote.getDeclaredField("activityType").apply { isAccessible = true }
    private val closingMode = remote.getDeclaredField("MODE_CLOSING").getInt(null)
    private val standardType = remote.getDeclaredField("ACTIVITY_TYPE_STANDARD").getInt(null)
    private val fields = listOf("targetApps", "startRect", "targetRect", "startRadius", "endRadius",
        "startAlpha", "endAlpha", "animType", "ignoreIcon", "showTask", "isQuickSwitchMode", "targetView",
        "animListener", "clipAnimationHelper", "clearLastListener", "currentDisplayRotation", "homeRotation",
        "needFinishOnAnimEnd", "currentTaskIndex", "isSplitModeBack", "isUseTranslucentAnim")
        .map { params.getDeclaredField(it).apply { isAccessible = true } }
    private val constructor = params.getDeclaredConstructor(*fields.map { it.type }.toTypedArray())

    init {
        require(attached.returnType == Void.TYPE && detached.returnType == Void.TYPE && touch.returnType == Boolean::class.javaPrimitiveType)
        require(animate.returnType == Void.TYPE && fields[2].type == RectF::class.java)
        require(prepareIcon.returnType == Void.TYPE && !prepareIcon.parameterTypes[0].isPrimitive)
        require(animationTarget.returnType == RectF::class.java && running.returnType == Boolean::class.javaPrimitiveType)
        require(component.type == ComponentName::class.java && taskId.type == Int::class.javaPrimitiveType &&
            taskUser.type == Int::class.javaPrimitiveType && fields[7].type.isEnum)
    }

    /** Called once at committed return, never from an animation update. Preserve the original
     * params, callbacks, task leash, velocity and finish/cancel policy in a separate native copy. */
    fun retarget(original: Any, owner: Any, nav: Any, frame: IslandMusicReturnSignal.Frame): Any? {
        if (!params.isInstance(original) || !frame.valid()) return null
        if ((fields[7].get(original) as Enum<*>).name != "CLOSE_TO_HOME" ||
            fields[10].getBoolean(original) || fields[19].getBoolean(original) ||
            fields[15].getInt(original) != 0 || fields[16].getInt(original) != 0) return null
        val info = taskInfo.get(nav) ?: return null
        if (windowingMode.invoke(info) != 1) return null // A14 WINDOWING_MODE_FULLSCREEN only.
        val user = taskUser.getInt(nav)
        if (user < 0 || user > 10000 || UserHandle.getUserHandleForUid(Math.multiplyExact(user, 100000)) != frame.user ||
            (component.get(nav) as? ComponentName)?.packageName != frame.packageName) return null
        val selectedTargets = fields[0].get(original) ?: retainedTargets.get(owner) ?: return null
        val selectedApps = apps.get(selectedTargets) as? Array<*> ?: return null
        if (selectedApps.size != 1) return null
        val target = selectedApps.single() ?: return null
        if (remotePackage.get(target) != frame.packageName || remoteTask.getInt(target) != taskId.getInt(nav) ||
            remoteMode.getInt(target) != closingMode || remoteType.getInt(target) != standardType) return null
        val values = fields.map { it.get(original) }.toTypedArray()
        values[2] = frame.rectangle()
        values[4] = (frame.bottom - frame.top) * .5f
        // isUpdate(params) skips the floating copy when ignoreIcon is true.
        // Keep targetView: initTargetView stores it for native finish/cancel icon restoration.
        values[8] = true
        return constructor.newInstance(*values)
    }

    /** animTo may return without starting a transition; only acknowledge the adopted,
     * running native target. This is one submission-time read, never a frame callback. */
    fun accepted(copy: Any, owner: Any): Boolean = currentParams.get(owner) === copy &&
        running.invoke(owner) == true && animationTarget.invoke(animation.get(owner)) == fields[2].get(copy)
}
