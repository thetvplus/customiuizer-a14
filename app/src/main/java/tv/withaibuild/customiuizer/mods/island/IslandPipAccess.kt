package tv.withaibuild.customiuizer.mods.island

import android.content.ComponentName
import android.app.ActivityManager
import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.os.UserHandle
import android.view.Display
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.AfterHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.CustomMethodUnhooker
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import java.lang.ref.WeakReference
import java.lang.reflect.Modifier

/** Scalar native PiP identity; it never retains a task, Surface, Activity or controller. */
internal data class IslandPipSnapshot(val generation: Long, val state: Int,
    val component: ComponentName? = null, val user: UserHandle? = null,
    val taskId: Int? = null, val displayId: Int = Display.DEFAULT_DISPLAY,
    val visible: Boolean = true, val stashed: Boolean = false) {
    fun blocks(media: IslandMediaContent): Boolean = state in 1..5 &&
        (state != 4 || visible && !stashed) &&
        displayId == Display.DEFAULT_DISPLAY && component?.packageName == media.packageName &&
        user != null && user == media.user
}

/** Resolved once in the actual Shell ClassLoader; none of these reads run from a draw callback. */
internal class IslandPipAccess(val organizerClass: Class<*>, algorithmClass: Class<*>) {
    constructor(loader: ClassLoader) : this(
        XposedHelpers.findClass("com.android.wm.shell.pip.PipTaskOrganizer", loader),
        XposedHelpers.findClass("com.android.wm.shell.pip.PipBoundsAlgorithm", loader))

    private fun field(owner: Class<*>, name: String) = XposedHelpers.findField(owner, name).apply {
        require(!Modifier.isStatic(modifiers)); isAccessible = true
    }
    private val transition = field(organizerClass, "mPipTransitionState")
    private val bounds = field(organizerClass, "mPipBoundsState")
    private val stashed = field(bounds.type, "mStashedState").apply { require(type == Int::class.javaPrimitiveType) }
    private val task = field(organizerClass, "mTaskInfo").apply { require(type == ActivityManager.RunningTaskInfo::class.java) }
    private val displayLayout = field(organizerClass, "mPipDisplayLayoutState")
    private val displayId = field(displayLayout.type, "mDisplayId").apply { require(type == Int::class.javaPrimitiveType) }
    private val state = field(transition.type, "mState").apply { require(type == Int::class.javaPrimitiveType) }
    private val taskUser = field(ActivityManager.RunningTaskInfo::class.java, "userId")
    val stateChanged = transition.type.getDeclaredMethod("setTransitionState", Int::class.javaPrimitiveType).apply {
        require(returnType == Void.TYPE && !Modifier.isStatic(modifiers)); isAccessible = true
    }
    val entry = bounds.type.getDeclaredMethod("setBoundsStateForEntry", ComponentName::class.java,
        ActivityInfo::class.java, PictureInPictureParams::class.java, algorithmClass).apply {
        require(returnType == Void.TYPE && !Modifier.isStatic(modifiers)); isAccessible = true
    }
    val taskChanged = organizerClass.getDeclaredMethod("onTaskInfoChanged", ActivityManager.RunningTaskInfo::class.java).apply {
        require(returnType == Void.TYPE && !Modifier.isStatic(modifiers)); isAccessible = true
    }
    val stashChanged = bounds.type.getDeclaredMethod("setStashed", Int::class.javaPrimitiveType).apply {
        require(returnType == Void.TYPE && !Modifier.isStatic(modifiers)); isAccessible = true
    }
    fun stateOwner(owner: Any): Any = requireNotNull(transition.get(owner))
    fun boundsOwner(owner: Any): Any = requireNotNull(bounds.get(owner))
    fun state(owner: Any): Int = state.getInt(stateOwner(owner))
    fun display(owner: Any): Int = displayId.getInt(displayLayout.get(owner))
    fun stashed(owner: Any): Boolean = stashed.getInt(boundsOwner(owner)) != 0
    data class Task(val component: ComponentName, val user: UserHandle, val id: Int)
    fun task(owner: Any): Task? {
        val info = task.get(owner) as? ActivityManager.RunningTaskInfo ?: return null
        val component = info.topActivity ?: return null
        val user = taskUser.getInt(info)
        if (user < 0 || info.taskId < 0) return null
        return Task(component, UserHandle.getUserHandleForUid(Math.multiplyExact(user, 100000)), info.taskId)
    }
}

/** One process-owned Shell organizer, one scalar snapshot. Owner replacement invalidates old callbacks. */
internal class IslandPipObserver(private val access: IslandPipAccess,
    private val changed: (IslandPipSnapshot) -> Unit) {
    private val lock = Any()
    private var owner = WeakReference<Any>(null)
    private var component: ComponentName? = null
    private var user: UserHandle? = null
    private var taskId: Int? = null
    private var visible = true
    private var stashed = false
    @Volatile var snapshot = IslandPipSnapshot(0, 0)
        private set

    private fun publish(state: Int, display: Int, force: Boolean = false): IslandPipSnapshot? {
        val previous = snapshot
        if (!force && previous.state == state && previous.component == component && previous.user == user &&
            previous.taskId == taskId && previous.displayId == display && previous.visible == visible && previous.stashed == stashed) return null
        return IslandPipSnapshot(previous.generation + 1, state, component, user, taskId, display, visible, stashed).also { snapshot = it }
    }

    fun capture(native: Any) {
        val update = synchronized(lock) {
            if (owner.get() === native) return
            owner = WeakReference(native)
            component = null; user = null; taskId = null
            visible = true; stashed = access.stashed(native)
            val state = access.state(native)
            if (state in 1..5) access.task(native)?.let { component = it.component; user = it.user; taskId = it.id }
            publish(state, access.display(native), force = true)
        }
        update?.let(changed)
    }

    fun stateChanged(native: Any) {
        val update = synchronized(lock) {
            val current = owner.get() ?: return
            if (access.stateOwner(current) !== native) return
            val state = access.state(current)
            if (state == 0) { component = null; user = null; taskId = null; visible = true; stashed = false }
            // onTaskAppeared assigns mTaskInfo BEFORE state 1. State 3 may precede a task;
            // never reuse mTaskInfo left behind by the previous exited PiP in that case.
            else if (state == 1 || state == 4) access.task(current)?.let {
                component = it.component; user = it.user; taskId = it.id
            }
            publish(state, access.display(current))
        }
        update?.let(changed)
    }

    fun entryChanged(native: Any, next: ComponentName?, info: ActivityInfo?) {
        val update = synchronized(lock) {
            val current = owner.get() ?: return
            if (access.boundsOwner(current) !== native) return
            component = next
            val uid = info?.applicationInfo?.uid
            user = uid?.takeIf { it >= 0 }?.let(UserHandle::getUserHandleForUid)
            val state = access.state(current)
            val task = access.task(current)?.takeIf { state != 3 && it.component == next && (user == null || it.user == user) }
            if (user == null) user = task?.user
            taskId = task?.id
            publish(state, access.display(current))
        }
        update?.let(changed)
    }

    fun taskChanged(native: Any) {
        val update = synchronized(lock) {
            if (owner.get() !== native) return
            val state = access.state(native)
            if (state !in 1..5) return
            val task = access.task(native) ?: return
            component = task.component; user = task.user; taskId = task.id
            publish(state, access.display(native))
        }
        update?.let(changed)
    }

    fun visibilityChanged(native: Any, shown: Boolean) {
        val update = synchronized(lock) {
            if (owner.get() !== native) return
            visible = shown
            publish(access.state(native), access.display(native))
        }
        update?.let(changed)
    }

    internal fun stashHook(): MethodHook = object : MethodHook() {
        override fun after(callback: AfterHookCallback) {
            if (callback.getThrowable() != null) return
            val update = synchronized(lock) {
                val current = owner.get() ?: return
                if (access.boundsOwner(current) !== callback.getThisObject()) return
                stashed = access.stashed(current)
                publish(access.state(current), access.display(current))
            }
            update?.let(changed)
        }
    }

    internal fun stateHook(): MethodHook = object : MethodHook() {
        override fun after(callback: AfterHookCallback) {
            if (callback.getThrowable() == null) callback.getThisObject()?.let(::stateChanged)
        }
    }
    internal fun entryHook(): MethodHook = object : MethodHook() {
        override fun after(callback: AfterHookCallback) {
            if (callback.getThrowable() != null) return
            val native = callback.getThisObject() ?: return
            val args = callback.getArgs()
            entryChanged(native, args[0] as? ComponentName, args[1] as? ActivityInfo)
        }
    }
    internal fun taskHook(): MethodHook = object : MethodHook() {
        override fun after(callback: AfterHookCallback) {
            if (callback.getThrowable() == null) callback.getThisObject()?.let(::taskChanged)
        }
    }

    fun install(): Boolean {
        val installed = ArrayList<CustomMethodUnhooker>()
        return try {
            installed.add(requireNotNull(ModuleHelper.hookMethod(access.stateChanged, stateHook())))
            installed.add(requireNotNull(ModuleHelper.hookMethod(access.entry, entryHook())))
            installed.add(requireNotNull(ModuleHelper.hookMethod(access.taskChanged, taskHook())))
            installed.add(requireNotNull(ModuleHelper.hookMethod(access.stashChanged, stashHook())))
            val capture = object : MethodHook() {
                override fun after(callback: AfterHookCallback) {
                    if (callback.getThrowable() == null) callback.getThisObject()?.let(::capture)
                }
            }
            val constructors = access.organizerClass.declaredConstructors
            check(constructors.isNotEmpty())
            for (constructor in constructors) {
                val parameters = constructor.parameterTypes.map { it as Any? }.toMutableList()
                parameters.add(capture)
                installed.add(requireNotNull(ModuleHelper.findAndHookConstructor(access.organizerClass.name,
                    access.organizerClass.classLoader, *parameters.toTypedArray())))
            }
            true
        } catch (error: Throwable) {
            installed.forEach { ModuleHelper.guarded { it.unhook() } }
            FatalErrors.unwrapAndRethrowIfFatal(error)
            XposedHelpers.log("Dynamic Island: retaining native PiP behavior; observer unavailable", error)
            false
        }
    }
}
