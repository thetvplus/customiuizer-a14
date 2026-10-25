package tv.withaibuild.customiuizer.mods.island

import android.app.ActivityManager
import android.content.ComponentName
import android.os.IBinder
import android.os.UserHandle
import android.view.SurfaceControl
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import java.lang.reflect.Modifier

/** A real Shell TRANSIT_PIP request precedes the organizer's task/animation states. */
internal class IslandPipEntryAccess(organizer: Class<*>, transition: Class<*>, request: Class<*>, transaction: Class<*>) {
    constructor(loader: ClassLoader) : this(
        XposedHelpers.findClass("com.android.wm.shell.pip.PipTaskOrganizer", loader),
        XposedHelpers.findClass("com.android.wm.shell.pip.PipTransition", loader),
        XposedHelpers.findClass("android.window.TransitionRequestInfo", loader),
        XposedHelpers.findClass("android.window.WindowContainerTransaction", loader))

    private val controller = XposedHelpers.findField(organizer, "mPipTransitionController")
    private val requestType = request.getMethod("getType")
    private val triggerTask = request.getMethod("getTriggerTask")
    private val userId = XposedHelpers.findField(ActivityManager.RunningTaskInfo::class.java, "userId")
    private val displayId = XposedHelpers.findField(ActivityManager.RunningTaskInfo::class.java, "displayId")
    val requested = transition.getDeclaredMethod("augmentRequest", IBinder::class.java, request, transaction)
    val consumed = transition.getDeclaredMethod("onTransitionConsumed", IBinder::class.java,
        Boolean::class.javaPrimitiveType, SurfaceControl.Transaction::class.java)

    init {
        require(!Modifier.isStatic(controller.modifiers) && requestType.returnType == Int::class.javaPrimitiveType &&
            triggerTask.returnType == ActivityManager.RunningTaskInfo::class.java &&
            requested.returnType == Void.TYPE && consumed.returnType == Void.TYPE)
    }

    data class Entry(val component: ComponentName, val user: UserHandle, val taskId: Int)
    fun owns(organizer: Any, transition: Any): Boolean = controller.get(organizer) === transition
    fun read(organizer: Any, transition: Any, request: Any): Entry? {
        if (!owns(organizer, transition) || requestType.invoke(request) != 10) return null
        val info = triggerTask.invoke(request) as? ActivityManager.RunningTaskInfo ?: return null
        val component = info.topActivity ?: return null
        val user = userId.getInt(info)
        if (info.taskId < 0 || user !in 0..10000 || displayId.getInt(info) != 0) return null
        return Entry(component, UserHandle.getUserHandleForUid(Math.multiplyExact(user, 100000)), info.taskId)
    }
}
