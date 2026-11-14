package tv.withaibuild.customiuizer.mods.recents

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Looper
import android.view.MotionEvent
import android.view.Surface
import android.view.View
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.PreparedHookBatch
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import java.lang.ref.WeakReference
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** The Launcher applies a real Activity orientation only for its current gesture overview. */
internal class DeckWindowOrientation(
    private val request: (Activity, Int) -> Unit,
    private val prepareRequest: (Activity, Int) -> Unit = { activity, orientation -> activity.requestedOrientation = orientation },
) {
    private var gesture: WeakReference<View>? = null
    private var owner: Activity? = null
    private var container: Any? = null
    private var requested = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    private var nativeRequest = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    private var sending = false
    private var committed = false
    var landscapeWindow = false
        private set

    fun capture(activity: Activity, nav: View, displayRotation: Int, reverseDefaultRotation: Boolean) {
        if (owner === activity && committed) return
        owner?.let(::exit)
        val target = orientationFor(displayRotation, reverseDefaultRotation) ?: return
        gesture = WeakReference(nav)
        enter(activity, target)
    }

    fun finishGesture(nav: View) {
        if (gesture?.get() === nav) {
            gesture = null
            if (!committed) owner?.let(::exit)
        }
    }

    fun commit(activity: Activity) {
        if (owner !== activity || gesture?.get() == null) return
        committed = true
        gesture = null
    }

    fun finishPendingGesture() {
        if (!committed) owner?.let(::exit)
    }

    private fun enter(activity: Activity, target: Int) {
        nativeRequest = activity.requestedOrientation
        requested = target
        owner = activity
        landscapeWindow = activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        try {
            // WMS must see the destination orientation before native starts its
            // recents transition. An async request can arrive after the window
            // has already acquired a portrait fixed-rotation transform.
            sending = true
            try { prepareRequest(activity, target) } finally { sending = false }
        } catch (error: Throwable) {
            owner = null
            landscapeWindow = false
            throw error
        }
    }

    fun interceptRequest(activity: Activity, orientation: Int): Int {
        if (sending || activity !== owner) return orientation
        nativeRequest = orientation
        return requested
    }

    fun configuration(activity: Activity, orientation: Int): Boolean {
        if (activity !== owner || orientation == Configuration.ORIENTATION_UNDEFINED) return false
        val next = orientation == Configuration.ORIENTATION_LANDSCAPE
        val entering = next && !landscapeWindow
        landscapeWindow = next
        if (!next) container = null
        return entering
    }

    fun bindLandscapeContainer(activity: Activity, value: Any) {
        if (activity === owner && landscapeWindow) container = value
    }

    fun ownsLandscapeContainer(value: Any?) = landscapeWindow && value != null && container === value

    fun exit(activity: Activity) {
        if (owner !== activity) return
        owner = null
        container = null
        landscapeWindow = false
        committed = false
        gesture = null
        send(activity, nativeRequest)
    }

    private fun send(activity: Activity, orientation: Int) {
        sending = true
        try { request(activity, orientation) } finally { sending = false }
    }

    companion object {
        fun orientationFor(rotation: Int, reverseDefaultRotation: Boolean): Int? = when (rotation) {
            Surface.ROTATION_90 -> if (reverseDefaultRotation) ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            Surface.ROTATION_270 -> if (reverseDefaultRotation) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
            else -> null
        }

        fun install(loader: ClassLoader, api: DeckNativeApi) {
            val prepared = try { prepare(loader, api) } catch (error: Throwable) {
                FatalErrors.unwrapAndRethrowIfFatal(error)
                XposedHelpers.log("Deck: Launcher orientation ABI unavailable", error)
                return
            }
            PreparedHookBatch().install(prepared)
        }

        private fun prepare(loader: ClassLoader, api: DeckNativeApi): List<Pair<Method, MethodHook>> {
            fun native(name: String) = XposedHelpers.findClass("com.miui.home.$name", loader)
            val launcher = native("launcher.Launcher").asSubclass(Activity::class.java)
            val overview = native("recents.OverviewState")
            val nav = native("recents.NavStubView")
            val nativeRequest = api.staticExact(native("launcher.util.UiThreadHelper"), "setOrientationAsync",
                Void.TYPE, Activity::class.java, Int::class.javaPrimitiveType!!)
            val state = DeckWindowOrientation(request = { activity, orientation -> nativeRequest.invoke(null, activity, orientation) })
            val fromGesture = XposedHelpers.findField(overview, "mIsFromFsGesture").apply {
                require(type == Boolean::class.javaPrimitiveType && !Modifier.isStatic(modifiers))
            }
            val navLauncher = XposedHelpers.findField(nav, "mLauncher").apply {
                require(type == launcher && !Modifier.isStatic(modifiers))
            }
            val getContainer = api.exact(launcher, "getRecentsContainer", api.recentsContainer)
            val exitFixed = api.exact(api.recentsContainer, "exitLandscapeOverview", Void.TYPE)
            val skipTouch = XposedHelpers.findField(api.recentsContainer, "mIsNeedSkipTouch").apply {
                require(type == Boolean::class.javaPrimitiveType && !Modifier.isStatic(modifiers))
            }
            val setSkipTouch = api.exact(api.recentsContainer, "setIsNeedSkipTouch", Void.TYPE, Boolean::class.javaPrimitiveType!!)
            val useFixed = api.staticExact(native("launcher.DeviceConfig"), "isKeepRecentsViewPortrait",
                Boolean::class.javaPrimitiveType!!)
            val config = api.exact(launcher, "onConfigurationChanged", Void.TYPE, Configuration::class.java)
            val enter = api.exact(overview, "onStateEnabled", Void.TYPE, launcher)
            val exit = api.exact(overview, "onExitState", Void.TYPE, launcher)
            val stop = api.exact(launcher, "onStop", Void.TYPE)
            val rotationChanged = api.exact(api.recentsContainer, "onDisplayRotationChanged", Void.TYPE,
                Int::class.javaPrimitiveType!!)
            val finish = api.exact(nav, "finalization", Void.TYPE, String::class.java)
            val reverseId = android.content.res.Resources.getSystem()
                .getIdentifier("config_reverseDefaultRotation", "bool", "android")
            require(reverseId != 0)
            val reverse = android.content.res.Resources.getSystem().getBoolean(reverseId)
            return listOf(
                api.gestureMode to IosRecentsDeckHooks.paired({}, { callback ->
                    val view = callback.getThisObject() as View
                    val event = callback.getArg(0) as MotionEvent
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                        state.finishPendingGesture()
                        val display = view.display
                        if (callback.getResult() == 2 && display?.displayId == 0) {
                            val activity = navLauncher.get(view) as? Activity
                            if (activity != null && !activity.isInMultiWindowMode &&
                                activity.resources.configuration.smallestScreenWidthDp < 600) {
                                state.capture(activity, view, display.rotation, reverse)
                            }
                        }
                    }
                }),
                finish to IosRecentsDeckHooks.paired({}, { callback ->
                    val view = callback.getThisObject() as View
                    state.finishGesture(view)
                }, always = true),
                enter to IosRecentsDeckHooks.paired({ callback ->
                    if (fromGesture.getBoolean(callback.getThisObject())) {
                        val activity = callback.getArg(0) as Activity
                        if (!activity.isInMultiWindowMode) state.commit(activity)
                    }
                }, { callback ->
                    if (callback.getThrowable() != null) state.exit(callback.getArg(0) as Activity)
                }, always = true),
                nativeRequest to IosRecentsDeckHooks.paired({ callback ->
                    val activity = callback.getArg(0) as Activity
                    val original = callback.getArg(1) as Int
                    val target = state.interceptRequest(activity, original)
                    if (target != original) callback.getArgs()[1] = target
                }, {}),
                config to IosRecentsDeckHooks.paired({ callback ->
                    val activity = callback.getThisObject() as Activity
                    val orientation = (callback.getArg(0) as Configuration).orientation
                    if (state.configuration(activity, orientation)) {
                        getContainer.invoke(activity)?.let { container ->
                            state.bindLandscapeContainer(activity, container)
                            exitFixed.invoke(container)
                            // The earlier fixed-rotation callback arms a one-shot
                            // touch skip. It no longer owns input in this window.
                            if (skipTouch.getBoolean(container)) setSkipTouch.invoke(container, false)
                        }
                    }
                }, {}),
                rotationChanged to IosRecentsDeckHooks.paired({}, { callback ->
                    val container = callback.getThisObject()
                    if (state.ownsLandscapeContainer(container) && skipTouch.getBoolean(container)) setSkipTouch.invoke(container, false)
                }),
                useFixed to IosRecentsDeckHooks.paired({ callback ->
                    if (Looper.myLooper() == Looper.getMainLooper() && state.landscapeWindow) callback.returnAndSkip(false)
                }, {}),
                exit to IosRecentsDeckHooks.paired({}, { state.exit(it.getArg(0) as Activity) }, always = true),
                stop to IosRecentsDeckHooks.paired({}, { state.exit(it.getThisObject() as Activity) }, always = true),
            )
        }
    }
}
