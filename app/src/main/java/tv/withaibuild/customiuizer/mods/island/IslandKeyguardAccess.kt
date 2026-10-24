package tv.withaibuild.customiuizer.mods.island

import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import java.lang.reflect.Proxy

/** Read the native keyguard independently of SHADE (which can coexist with an occluded lockscreen). */
internal class IslandKeyguardAccess internal constructor(ownerClass: Class<*>, private val callbackClass: Class<*>) {
    constructor(loader: ClassLoader) : this(
        XposedHelpers.findClass("com.android.systemui.statusbar.policy.KeyguardStateControllerImpl", loader),
        XposedHelpers.findClass("com.android.systemui.statusbar.policy.KeyguardStateController\$Callback", loader))

    private val showing = XposedHelpers.findField(ownerClass, "mShowing")
    private val goingAway = XposedHelpers.findField(ownerClass, "mKeyguardGoingAway")
    private val fadingAway = XposedHelpers.findField(ownerClass, "mKeyguardFadingAway")
    private val bouncer = XposedHelpers.findField(ownerClass, "mPrimaryBouncerShowing")
    private val launchFading = XposedHelpers.findField(ownerClass, "mLaunchTransitionFadingAway")
    private val addCallback = ownerClass.getMethod("addCallback", Any::class.java)
    private val removeCallback = ownerClass.getMethod("removeCallback", Any::class.java)

    fun isUnlocked(owner: Any?): Boolean = owner != null && !showing.getBoolean(owner) &&
        !goingAway.getBoolean(owner) && !fadingAway.getBoolean(owner) &&
        !bouncer.getBoolean(owner) && !launchFading.getBoolean(owner)

    fun listen(owner: Any, changed: () -> Unit): Any {
        val listener = Proxy.newProxyInstance(callbackClass.classLoader, arrayOf(callbackClass)) { proxy, method, args ->
            when (method.name) {
                "equals" -> proxy === args?.getOrNull(0)
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "A14IslandKeyguardListener"
                "onKeyguardShowingChanged", "onKeyguardGoingAwayChanged", "onKeyguardFadingAwayChanged",
                "onPrimaryBouncerShowingChanged", "onLaunchTransitionFadingAwayChanged", "onUnlockedChanged" -> {
                    ModuleHelper.guarded { changed() }; null
                }
                else -> null
            }
        }
        try { addCallback.invoke(owner, listener) }
        catch (error: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(error)
            ModuleHelper.guarded { removeCallback.invoke(owner, listener) }
            throw error
        }
        return listener
    }

    fun unlisten(owner: Any, listener: Any) { removeCallback.invoke(owner, listener) }
}
