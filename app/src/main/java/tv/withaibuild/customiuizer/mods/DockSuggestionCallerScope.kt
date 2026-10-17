package tv.withaibuild.customiuizer.mods

import io.github.libxposed.api.XposedInterface
import java.lang.ref.WeakReference
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.CustomMethodUnhooker
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers

private const val DOCK_APP_EDIT_ACTIVITY = "com.miui.dock.edit.DockAppEditActivity"
private const val EMPTY_SUGGESTION_PLACEHOLDER = "xx.yy.zz"

internal class DockSuggestionCallerScope {
    private val depth = ThreadLocal<Int>()

    fun isActive(): Boolean = (depth.get() ?: 0) > 0

    fun enter() {
        depth.set((depth.get() ?: 0) + 1)
    }

    fun exit() {
        val current = depth.get() ?: return
        if (current <= 1) {
            depth.remove()
        } else {
            depth.set(current - 1)
        }
    }
}

internal val dockSuggestionCallerScope = DockSuggestionCallerScope()

internal inline fun dockSuggestionResult(
    allowRomResult: Boolean,
    proceed: () -> Any?
): Any? {
    if (allowRomResult) return proceed()
    val blackList = ArrayList<String>(1)
    blackList.add(EMPTY_SUGGESTION_PLACEHOLDER)
    return blackList
}

internal fun resolveDockSuggestionRomCallerMethod(dockActivityClass: Class<*>): Method {
    val matches = dockActivityClass.declaredClasses.mapNotNull { candidate ->
        if (!Modifier.isStatic(candidate.modifiers) ||
            !Runnable::class.java.isAssignableFrom(candidate)
        ) {
            return@mapNotNull null
        }
        val fields = candidate.declaredFields
        if (fields.size != 2 ||
            fields.count { it.type == WeakReference::class.java } != 1 ||
            fields.count { it.type == java.lang.Boolean.TYPE } != 1
        ) {
            return@mapNotNull null
        }
        if (candidate.declaredConstructors.singleOrNull()?.parameterTypes?.contentEquals(
                arrayOf(dockActivityClass)
            ) != true
        ) {
            return@mapNotNull null
        }
        candidate.declaredMethods.singleOrNull { method ->
            method.name == "run" &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.isEmpty()
        }
    }
    check(matches.size == 1) {
        "Expected exactly one verified Dock suggestion ROM caller in ${dockActivityClass.name}, " +
            "found ${matches.joinToString { it.declaringClass.name }}"
    }
    return matches.single()
}

internal fun installDockSuggestionCallerScope(
    classLoader: ClassLoader?
): CustomMethodUnhooker? {
    val callerMethod = try {
        val dockActivityClass = XposedHelpers.findClassIfExists(
            DOCK_APP_EDIT_ACTIVITY,
            classLoader
        ) ?: error("Missing $DOCK_APP_EDIT_ACTIVITY")
        resolveDockSuggestionRomCallerMethod(dockActivityClass)
    } catch (t: Throwable) {
        XposedHelpers.log(FatalErrors.unwrapAndRethrowIfFatal(t))
        return null
    }
    return ModuleHelper.hookMethod(callerMethod, dockSuggestionCallerHook)
}

internal fun unhookDockSuggestionCallerScope(unhooker: CustomMethodUnhooker) {
    try {
        unhooker.unhook()
    } catch (t: Throwable) {
        XposedHelpers.log(FatalErrors.unwrapAndRethrowIfFatal(t))
    }
}

private val dockSuggestionCallerHook = object : MethodHook() {
    override fun intercept(chain: XposedInterface.Chain): Any? {
        dockSuggestionCallerScope.enter()
        return try {
            chain.proceed()
        } finally {
            dockSuggestionCallerScope.exit()
        }
    }
}
