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
    private val params: Class<*>, targets: Class<*>, remote: Class<*>, config: Class<*>, fastElement: Class<*>? = null,
    val home: LauncherHomeReturnAccess? = null) {
    constructor(loader: ClassLoader) : this(
        XposedHelpers.findClass("com.miui.home.recents.NavStubView", loader),
        XposedHelpers.findClass("com.miui.home.recents.anim.WindowElement", loader),
        XposedHelpers.findClass("com.miui.home.recents.anim.RectFParams", loader),
        XposedHelpers.findClass("com.miui.home.recents.util.RemoteAnimationTargetSet", loader),
        XposedHelpers.findClass("com.android.systemui.shared.recents.system.RemoteAnimationTargetCompat", loader),
        XposedHelpers.findClass("com.miui.home.launcher.DeviceConfig", loader),
        XposedHelpers.findClassIfExists("com.miui.home.recents.anim.FastLaunchWindowElement", loader),
        ModuleHelper.guarded<LauncherHomeReturnAccess?>(null) { LauncherHomeReturnAccess(loader) })

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
    private val animationAlpha = animation.type.getMethod("getCurrentAlpha")
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
        require(fastAnimate == null || fastElement != null && element.isAssignableFrom(fastElement) && fastAnimate.returnType == Void.TYPE &&
            fastPackage?.returnType == String::class.java)
        require(!closingTarget.returnType.isPrimitive && closingRect.type == Rect::class.java &&
            defaultRect.returnType == Rect::class.java && java.lang.reflect.Modifier.isStatic(defaultRect.modifiers))
        require(animationTarget.returnType == RectF::class.java && animationAlpha.returnType == Float::class.javaPrimitiveType &&
            running.returnType == Boolean::class.javaPrimitiveType)
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
    fun retarget(original: Any, owner: Any, nav: Any, frame: IslandMusicReturnSignal.Frame, absorption: Boolean = false): Any? {
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
        return copyTarget(original, owner, frame, absorption)
    }

    fun retargetHome(original: Any, owner: Any, selected: Any, frame: IslandMusicReturnSignal.Frame, absorption: Boolean = false): Any? {
        val home = home ?: return null
        if (!params.isInstance(original) || !home.eligible(selected, frame) ||
            (fields[7].get(original) as Enum<*>).name != "CLOSE_TO_HOME" ||
            fields[10].getBoolean(original) || fields[19].getBoolean(original) ||
            fields[15].getInt(original) != 0 || fields[16].getInt(original) != 0 ||
            fields[11].get(original) != null) return null
        val targets = fields[0].get(original) ?: retainedTargets.get(owner) ?: return null
        if ((apps.get(targets) as? Array<*>)?.singleOrNull() !== selected) return null
        return copyTarget(original, owner, frame, absorption)
    }

    private fun copyTarget(original: Any, owner: Any, frame: IslandMusicReturnSignal.Frame, absorption: Boolean): Any? {
        val values = fields.map { it.get(original) }.toTypedArray()
        val landing = frame.landingRectangle(absorption)
        values[2] = landing
        values[4] = landing.height() * .5f
        // Hold the opacity established at release. A partially faded gesture must
        // neither flash opaque nor keep fading on its way to the island. Truly
        // translucent tasks retain the ROM's alpha contract and cannot use the mesh.
        if (!fields[20].getBoolean(original)) {
            // An interrupted native spring, rather than a default/sentinel start
            // parameter, owns the page currently on screen. Read it only at submission.
            val current = if (currentParams.get(owner) != null) animationAlpha.invoke(animation.get(owner)) as Float else Float.NaN
            val release = current.takeIf { it.isFinite() && it in 0f..1f }
                ?: (values[5] as Float).takeIf { it.isFinite() && it in 0f..1f } ?: return null
            values[5] = release; values[6] = release
        }
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
        returnTiming?.apply(requireNotNull(animation.get(owner)), strength, absorption)
    }
    fun returnTimingLease(owner: Any): Any? = returnTiming?.lease(requireNotNull(animation.get(owner)))
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

/** Button Home uses the same CLOSE_TO_HOME params via this native provider, without NavStub task state. */
internal class LauncherHomeReturnAccess(provider: Class<*>, launcher: Class<*>, remote: Class<*>) {
    constructor(loader: ClassLoader) : this(
        XposedHelpers.findClass("com.miui.home.recents.anim.WindowAnimParamsProvider", loader),
        XposedHelpers.findClass("com.miui.home.launcher.Launcher", loader),
        XposedHelpers.findClass("com.android.systemui.shared.recents.system.RemoteAnimationTargetCompat", loader))

    val attached = launcher.getDeclaredMethod("onAttachedToWindow")
    val destroyed = launcher.getDeclaredMethod("onDestroy")
    val closingTarget = provider.getDeclaredMethod("findClosingAnimTarget", launcher, remote)
    private val nativeTarget = remote.getDeclaredField("target").apply { isAccessible = true }
    private val info = nativeTarget.type.getDeclaredField("taskInfo").apply { isAccessible = true }
    private val mode = info.type.getMethod("getWindowingMode")
    private val user = info.type.getField("userId")
    private val task = info.type.getField("taskId")
    private val display = info.type.getField("displayId")
    private val pip = info.type.getField("pictureInPictureParams")
    private val autoPip = pip.type.getMethod("isAutoEnterEnabled")
    private val remotePackage = remote.getDeclaredField("packageName").apply { isAccessible = true }
    private val remoteTask = remote.getDeclaredField("taskId").apply { isAccessible = true }
    private val remoteMode = remote.getDeclaredField("mode").apply { isAccessible = true }
    private val remoteType = remote.getDeclaredField("activityType").apply { isAccessible = true }

    init {
        require(attached.returnType == Void.TYPE && destroyed.returnType == Void.TYPE && !closingTarget.returnType.isPrimitive)
    }

    fun eligible(target: Any, frame: IslandMusicReturnSignal.Frame): Boolean {
        if (!frame.valid() || remotePackage.get(target) != frame.packageName || remoteMode.getInt(target) != 1 ||
            remoteType.getInt(target) != 1) return false
        val platform = nativeTarget.get(target) ?: return false
        val taskInfo = info.get(platform) ?: return false
        val userId = user.getInt(taskInfo)
        return userId in 0..10000 && UserHandle.getUserHandleForUid(userId * 100000) == frame.user &&
            task.getInt(taskInfo) == remoteTask.getInt(target) && display.getInt(taskInfo) == 0 && mode.invoke(taskInfo) == 1 &&
            pip.get(taskInfo)?.let { autoPip.invoke(it) } != true
    }
}
