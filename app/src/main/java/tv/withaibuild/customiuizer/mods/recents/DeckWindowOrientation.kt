package tv.withaibuild.customiuizer.mods.recents

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Looper
import android.view.Display
import android.view.Surface
import android.view.View
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.PreparedHookBatch
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** Lets the native rotation arbiter keep the display direction for an app-to-Deck transition. */
internal class DeckWindowOrientation(private val updateNativeRequest: (Any) -> Unit) {
    private class Entry(val activity: Activity, val nav: View, val helper: Any) {
        var container: Any? = null
    }

    private var entry: Entry? = null

    val landscapeWindow: Boolean
        get() = entry?.activity?.resources?.configuration?.orientation == Configuration.ORIENTATION_LANDSCAPE

    fun begin(activity: Activity, nav: View, helper: Any, rotation: Int) {
        val current = entry
        if (!isLandscape(rotation)) {
            current?.let { exit(it.activity) }
            return
        }
        if (current?.activity === activity && current.nav === nav && current.helper === helper) return
        current?.let { exit(it.activity) }
        entry = Entry(activity, nav, helper)
        try {
            updateNativeRequest(helper)
        } catch (error: Throwable) {
            entry = null
            throw error
        }
    }

    fun finishGesture(nav: View, inOverview: Boolean) {
        val current = entry ?: return
        if (current.nav === nav && !inOverview) exit(current.activity)
    }

    fun orientation(helper: Any?, request: Int, native: Int): Int =
        if (entry?.helper === helper && helper != null && request == KEEP_PORTRAIT &&
            native == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) ActivityInfo.SCREEN_ORIENTATION_LOCKED else native

    fun bindLandscapeContainer(activity: Activity, orientation: Int, value: Any): Boolean {
        val current = entry ?: return false
        if (current.activity !== activity || orientation == Configuration.ORIENTATION_UNDEFINED) return false
        current.container = value.takeIf { orientation == Configuration.ORIENTATION_LANDSCAPE }
        return current.container != null
    }

    fun ownsLandscapeContainer(value: Any?) = value != null && landscapeWindow && entry?.container === value

    fun owns(activity: Activity) = entry?.activity === activity

    fun exit(activity: Activity) {
        val current = entry ?: return
        if (current.activity !== activity) return
        entry = null
        updateNativeRequest(current.helper)
    }

    companion object {
        // RotationHelper's fixed-portrait policy (multi-window requests keep native priority).
        private const val KEEP_PORTRAIT = 3
        private const val WINDOW_MODE_APP = 2

        private fun isLandscape(rotation: Int) = rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270

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
            val launcherState = native("launcher.LauncherState")
            val overview = native("recents.OverviewState")
            val rotation = native("recents.util.RotationHelper")
            val int = Int::class.javaPrimitiveType!!
            val bool = Boolean::class.javaPrimitiveType!!
            fun field(type: Class<*>, name: String, value: Class<*>) = XposedHelpers.findField(type, name).apply {
                require(this.type == value && !Modifier.isStatic(modifiers))
            }
            val overviewState = XposedHelpers.findField(launcherState, "OVERVIEW").apply {
                require(type == overview && Modifier.isStatic(modifiers))
            }.get(null)
            val isInState = api.exact(launcher, "isInState", bool, launcherState)
            val displayRotation = api.exact(launcher, "getCurrentDisplayRotation", int)
            val getHelper = api.exact(launcher, "getRotationHelper", rotation)
            val notify = api.exact(rotation, "notifyChange", Void.TYPE)
            val nativeOrientation = api.exact(rotation, "getScreenOrientationByRequest", int, int)
            val multiWindow = api.exact(rotation, "onMultiWindowModeChanged", Void.TYPE, bool)
            val state = DeckWindowOrientation { notify.invoke(it) }
            val navLauncher = field(api.nav, "mLauncher", launcher)
            val mode = field(api.nav, "mWindowMode", int)
            val getContainer = api.exact(launcher, "getRecentsContainer", api.recentsContainer)
            val isFixedLandscape = api.exact(api.recentsContainer, "isInLandscapeOverview", bool)
            val exitFixed = api.exact(api.recentsContainer, "exitLandscapeOverview", Void.TYPE)
            val skipTouch = field(api.recentsContainer, "mIsNeedSkipTouch", bool)
            val setSkipTouch = api.exact(api.recentsContainer, "setIsNeedSkipTouch", Void.TYPE, bool)
            val useFixed = api.staticExact(native("launcher.DeviceConfig"), "isKeepRecentsViewPortrait", bool)
            val config = api.exact(launcher, "onConfigurationChanged", Void.TYPE, Configuration::class.java)
            val exit = api.exact(overview, "onExitState", Void.TYPE, launcher)
            val stop = api.exact(launcher, "onStop", Void.TYPE)
            val rotationChanged = api.exact(api.recentsContainer, "onDisplayRotationChanged", Void.TYPE, int)
            val finish = api.exact(api.nav, "finalization", Void.TYPE, String::class.java)
            val helperLauncher = field(rotation, "mLauncher", launcher)
            fun isOverview(activity: Activity) = isInState.invoke(activity, overviewState) == true
            fun clearFixedTouch(container: Any) {
                if (skipTouch.getBoolean(container)) setSkipTouch.invoke(container, false)
            }
            fun updateContainer(activity: Activity, orientation: Int) {
                val container = getContainer.invoke(activity) ?: return
                if (!state.bindLandscapeContainer(activity, orientation, container)) return
                if (isFixedLandscape.invoke(container) == true) exitFixed.invoke(container)
                clearFixedTouch(container)
            }
            return listOf(
                api.navToRecents to IosRecentsDeckHooks.paired({ callback ->
                    val nav = callback.getThisObject() as View
                    val display = nav.display
                    if (mode.getInt(nav) == WINDOW_MODE_APP && display?.displayId == Display.DEFAULT_DISPLAY) {
                        val activity = navLauncher.get(nav) as? Activity
                        if (activity != null && !activity.isInMultiWindowMode &&
                            activity.resources.configuration.smallestScreenWidthDp < 600 && useFixed.invoke(null) == true) {
                            getHelper.invoke(activity)?.let { helper ->
                                state.begin(activity, nav, helper, displayRotation.invoke(activity) as Int)
                                if (state.landscapeWindow) updateContainer(activity, Configuration.ORIENTATION_LANDSCAPE)
                            }
                        }
                    }
                }, { callback ->
                    if (callback.getThrowable() != null) state.finishGesture(callback.getThisObject() as View, inOverview = false)
                }, always = true),
                nativeOrientation to IosRecentsDeckHooks.paired({}, { callback ->
                    val original = callback.getResult() as Int
                    val target = state.orientation(callback.getThisObject(), callback.getArg(0) as Int, original)
                    if (target != original) callback.setResult(target)
                }),
                finish to IosRecentsDeckHooks.paired({}, { callback ->
                    val nav = callback.getThisObject() as View
                    val activity = navLauncher.get(nav) as? Activity
                    if (activity != null && state.owns(activity)) state.finishGesture(nav, isOverview(activity))
                }, always = true),
                config to IosRecentsDeckHooks.paired({ callback ->
                    val activity = callback.getThisObject() as Activity
                    val orientation = (callback.getArg(0) as Configuration).orientation
                    if (state.owns(activity)) updateContainer(activity, orientation)
                }, {}),
                rotationChanged to IosRecentsDeckHooks.paired({}, { callback ->
                    val container = callback.getThisObject()
                    if (container != null && state.ownsLandscapeContainer(container)) clearFixedTouch(container)
                }),
                useFixed to IosRecentsDeckHooks.paired({ callback ->
                    if (Looper.myLooper() == Looper.getMainLooper() && state.landscapeWindow) callback.returnAndSkip(false)
                }, {}),
                multiWindow to IosRecentsDeckHooks.paired({}, { callback ->
                    if (callback.getArg(0) == true) {
                        (helperLauncher.get(callback.getThisObject()) as? Activity)?.let(state::exit)
                    }
                }),
                exit to IosRecentsDeckHooks.paired({}, { state.exit(it.getArg(0) as Activity) }, always = true),
                stop to IosRecentsDeckHooks.paired({}, { state.exit(it.getThisObject() as Activity) }, always = true),
            )
        }
    }
}
