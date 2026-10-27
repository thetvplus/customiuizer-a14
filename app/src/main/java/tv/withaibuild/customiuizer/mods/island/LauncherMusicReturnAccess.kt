package tv.withaibuild.customiuizer.mods.island

import android.content.ComponentName
import android.graphics.Rect
import android.graphics.RectF
import android.os.UserHandle
import android.view.MotionEvent
import java.lang.ref.WeakReference
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.FatalErrors

/** Exact HyperOS 1 WindowElement contract; all reflection is resolved before installation. */
internal class LauncherMusicReturnAccess(nav: Class<*>, element: Class<*>,
    private val params: Class<*>, targets: Class<*>, remote: Class<*>, config: Class<*>, fastElement: Class<*>? = null) {
    constructor(loader: ClassLoader) : this(
        XposedHelpers.findClass("com.miui.home.recents.NavStubView", loader),
        XposedHelpers.findClass("com.miui.home.recents.anim.WindowElement", loader),
        XposedHelpers.findClass("com.miui.home.recents.anim.RectFParams", loader),
        XposedHelpers.findClass("com.miui.home.recents.util.RemoteAnimationTargetSet", loader),
        XposedHelpers.findClass("com.android.systemui.shared.recents.system.RemoteAnimationTargetCompat", loader),
        XposedHelpers.findClass("com.miui.home.launcher.DeviceConfig", loader),
        XposedHelpers.findClassIfExists("com.miui.home.recents.anim.FastLaunchWindowElement", loader))

    val attached = nav.getDeclaredMethod("onAttachedToWindow")
    val detached = nav.getDeclaredMethod("onDetachedFromWindow")
    val touch = nav.getDeclaredMethod("onTouchEvent", MotionEvent::class.java)
    val animate = element.getDeclaredMethod("animTo", Any::class.java)
    val fastAnimate = fastElement?.getDeclaredMethod("animTo", Any::class.java)
    private val fastPackage = fastElement?.getDeclaredMethod("getFastLaunchRunningTaskPkgName")
    val closingTarget = nav.getDeclaredMethod("findClosingAnimTarget", Boolean::class.javaPrimitiveType)
    private val closingRect = nav.getDeclaredField("mAppToHomeTargetRect").apply { isAccessible = true }
    private val touchRange = nav.getDeclaredField("mTouchRange").apply { isAccessible = true }
    private val defaultRect = config.getDeclaredMethod("getClosingAppDefaultDisappearRect", Int::class.javaPrimitiveType)
    private val taskId = nav.getDeclaredField("mRunningTaskId").apply { isAccessible = true }
    private val taskUser = nav.getDeclaredField("mRunningTaskUserId").apply { isAccessible = true }
    private val component = nav.getDeclaredField("mRunningTaskComponentName").apply { isAccessible = true }
    private val taskInfo = nav.getDeclaredField("mRunningTaskInfo").apply { isAccessible = true }
    private val windowingMode = taskInfo.type.getMethod("getWindowingMode")
    private val pipParams = XposedHelpers.findField(taskInfo.type, "pictureInPictureParams")
    private val autoEnterPip = pipParams.type.getMethod("isAutoEnterEnabled")
    private val retainedTargets = element.getDeclaredField("remoteAnimationTargetSet").apply { isAccessible = true }
    private val currentParams = element.getDeclaredField("currentRectFParams").apply { isAccessible = true }
    private val animation = element.getDeclaredField("mAnim").apply { isAccessible = true }
    private val animationTarget = animation.type.getMethod("getTargetRect")
    private val returnTiming = ModuleHelper.guarded<LauncherReturnTiming?>(null) { LauncherReturnTiming(animation.type) }
    private val running = element.getMethod("isRunning")
    private val apps = targets.getDeclaredField("apps").apply { isAccessible = true }
    private val remotePackage = remote.getDeclaredField("packageName").apply { isAccessible = true }
    private val remoteTask = remote.getDeclaredField("taskId").apply { isAccessible = true }
    private val remoteMode = remote.getDeclaredField("mode").apply { isAccessible = true }
    private val remoteType = remote.getDeclaredField("activityType").apply { isAccessible = true }
    private val closingMode = remote.getDeclaredField("MODE_CLOSING").getInt(null)
    private val openingMode = remote.getDeclaredField("MODE_OPENING").getInt(null)
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
        require(fastAnimate == null || element.isAssignableFrom(fastElement) && fastAnimate.returnType == Void.TYPE &&
            fastPackage?.returnType == String::class.java)
        require(!closingTarget.returnType.isPrimitive && closingRect.type == Rect::class.java &&
            defaultRect.returnType == Rect::class.java && java.lang.reflect.Modifier.isStatic(defaultRect.modifiers))
        require(animationTarget.returnType == RectF::class.java && running.returnType == Boolean::class.javaPrimitiveType)
        require(component.type == ComponentName::class.java && taskId.type == Int::class.javaPrimitiveType &&
            taskUser.type == Int::class.javaPrimitiveType && fields[7].type.isEnum)
    }

    /** The quick-opening override can bypass WindowElement entirely. If it delegates
     * to super, process the outer override only; failed optional installation keeps base. */
    fun animationEntry(owner: Any?, fastInstalled: Boolean) =
        fastAnimate?.takeIf { fastInstalled && it.declaringClass.isInstance(owner) } ?: animate

    private fun eligibleTask(nav: Any, frame: IslandMusicReturnSignal.Frame): Boolean {
        if (!frame.valid()) return false
        val info = taskInfo.get(nav) ?: return false
        if (windowingMode.invoke(info) != 1 || pipParams.get(info)?.let { autoEnterPip.invoke(it) } == true) return false
        val user = taskUser.getInt(nav)
        return user in 0..10000 && UserHandle.getUserHandleForUid(Math.multiplyExact(user, 100000)) == frame.user &&
            (component.get(nav) as? ComponentName)?.packageName == frame.packageName
    }

    /** Exact native no-icon branch: keep its default fallback rectangle and never look up,
     * snapshot or hide a desktop icon. The later validated window spring supplies the island end. */
    fun selectWindowTarget(nav: Any, frame: IslandMusicReturnSignal.Frame): Boolean {
        if (!eligibleTask(nav, frame)) return false
        val rectangle = defaultRect.invoke(null, touchRange.getInt(nav)) as Rect
        (closingRect.get(nav) as Rect).set(rectangle)
        return true
    }

    /** Prefetch only: never change the native opening target or wait for another process. */
    fun openingPackage(original: Any, owner: Any): String? {
        if (!params.isInstance(original) || (fields[7].get(original) as Enum<*>).name != "OPEN_FROM_HOME" ||
            fields[10].getBoolean(original) || fields[19].getBoolean(original) ||
            fields[15].getInt(original) != 0 || fields[16].getInt(original) != 0) return null
        val explicit = fields[0].get(original)
        val targets = explicit ?: retainedTargets.get(owner)
        val target = targets?.let { (apps.get(it) as? Array<*>)?.singleOrNull() }
        if (target != null && remoteMode.getInt(target) == openingMode && remoteType.getInt(target) == standardType)
            return remotePackage.get(target) as? String
        // A reused fast element can still retain its last CLOSING targets. Only
        // prefetch may use the native opening package; retarget still requires task identity.
        return if (explicit == null && fastAnimate?.declaringClass?.isInstance(owner) == true)
            fastPackage?.invoke(owner) as? String else null
    }

    /** Called once at committed return, never from an animation update. Preserve the original
     * params, callbacks, task leash, velocity and finish/cancel policy in a separate native copy. */
    fun retarget(original: Any, owner: Any, nav: Any, frame: IslandMusicReturnSignal.Frame): Any? {
        if (!params.isInstance(original) || !frame.valid()) return null
        if ((fields[7].get(original) as Enum<*>).name != "CLOSE_TO_HOME" ||
            fields[10].getBoolean(original) || fields[19].getBoolean(original) ||
            fields[15].getInt(original) != 0 || fields[16].getInt(original) != 0) return null
        // A late fresh snapshot must never hijack an already-selected desktop icon.
        // Auto-PiP stays native before Shell can publish its pinned task.
        if (fields[11].get(original) != null || !eligibleTask(nav, frame)) return null
        val selectedTargets = fields[0].get(original) ?: retainedTargets.get(owner) ?: return null
        val selectedApps = apps.get(selectedTargets) as? Array<*> ?: return null
        if (selectedApps.size != 1) return null
        val target = selectedApps.single() ?: return null
        if (remotePackage.get(target) != frame.packageName || remoteTask.getInt(target) != taskId.getInt(nav) ||
            remoteMode.getInt(target) != closingMode || remoteType.getInt(target) != standardType) return null
        val values = fields.map { it.get(original) }.toTypedArray()
        val landing = frame.landingRectangle()
        values[2] = landing
        values[4] = landing.height() * .5f
        // Keep the previous late-build landing and opaque native end. The original
        // start alpha is retained, so a partially faded gesture never jumps opaque.
        // Only the absorption mesh adds a final page fade; unavailable capture retains
        // the same continuous native return inside the shell.
        values[6] = 1f
        // A14's frame callback draws any *already initialized* floating icon without
        // consulting ignoreIcon. A null target makes native initTargetView retire that
        // icon before the window spring starts, restoring its real desktop owner once.
        // Only the app leash then follows this target; no desktop icon is borrowed.
        values[8] = true
        values[11] = null
        return constructor.newInstance(*values)
    }

    /** animTo may return without starting a transition; only acknowledge the adopted,
     * running native target. This is one submission-time read, never a frame callback. */
    fun accepted(copy: Any, owner: Any): Boolean = currentParams.get(owner) === copy &&
        running.invoke(owner) == true && animationTarget.invoke(animation.get(owner)) == fields[2].get(copy)

    fun timeReturn(owner: Any, strength: Float, absorption: Boolean) {
        returnTiming?.apply(animation.get(owner), strength, absorption)
    }
    fun returnTimingLease(owner: Any): Any? = returnTiming?.lease(animation.get(owner))
    fun restoreReturnTiming(owner: Any? = null, lease: Any? = null) { returnTiming?.restore(owner?.let { animation.get(it) }, lease) }
}

/** Adjust only an accepted return's existing spring forces, before its next native frame.
 * One weak lease restores only forces still carrying this attempt's exact adjustment.
 * No global spring hook, listener replacement, velocity rewrite or independent clock. */
internal class LauncherReturnTiming(springType: Class<*>) {
    private val bundle = springType.getDeclaredMethod("getSpringBundle", String::class.java).apply { isAccessible = true }
    private val modifyResponse = springType.getDeclaredMethod("getModifyResponse", Float::class.javaPrimitiveType).apply { isAccessible = true }
    private val stiffness = bundle.returnType.getMethod("getStiffness")
    private val setStiffness = bundle.returnType.getMethod("setStiffness", Float::class.javaPrimitiveType)
    private val keys = arrayOf("centerX", "centerY", "Width", "Ratio", "Radius", "Alpha", "SurfaceExtAlpha")
    private var active = WeakReference<Any>(null)
    private var original: FloatArray? = null
    private var factor = 1f

    fun apply(spring: Any, strength: Float = .5f, absorption: Boolean = true): Boolean {
        restore()
        val tracks = arrayOfNulls<Any>(keys.size)
        val values = FloatArray(keys.size)
        var attempted = -1
        try {
            for (i in keys.indices) {
                tracks[i] = bundle.invoke(spring, keys[i]) ?: return false
                values[i] = stiffness.invoke(tracks[i]) as Float
                if (!values[i].isFinite() || values[i] <= 0f) return false
            }
            // Width is the ROM progress reference. Normalize that single response,
            // applying the same factor to every native track to retain their ratios.
            val response = modifyResponse.invoke(spring, IslandMotion.returnResponse(strength, absorption)) as Float
            if (!response.isFinite() || response <= 0f) return false
            factor = (39.478418f / (response * response * values[2])).coerceIn(.25f, 4f)
            for (i in keys.indices) {
                attempted = i
                setStiffness.invoke(tracks[i], values[i] * factor)
            }
            active = WeakReference(spring); original = values
            return true
        } catch (error: Throwable) {
            for (i in 0..attempted) ModuleHelper.guarded { setStiffness.invoke(tracks[i], values[i]) }
            FatalErrors.unwrapAndRethrowIfFatal(error)
            XposedHelpers.log("Island return: retaining native spring timing", error)
            return false
        }
    }

    fun lease(spring: Any): Any? = original.takeIf { active.get() === spring }

    fun restore(only: Any? = null, lease: Any? = null) {
        if (lease != null && lease !== original) return
        val spring = active.get() ?: run { original = null; return }
        if (only != null && only !== spring) return
        val values = original ?: return
        active.clear(); original = null
        for (i in keys.indices) ModuleHelper.guarded {
            val track = bundle.invoke(spring, keys[i]) ?: return@guarded
            if (stiffness.invoke(track) as Float == values[i] * factor) setStiffness.invoke(track, values[i])
        }
    }
}
