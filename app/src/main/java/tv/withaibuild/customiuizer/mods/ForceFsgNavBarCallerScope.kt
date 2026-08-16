package tv.withaibuild.customiuizer.mods

import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.CustomMethodUnhooker
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers

private const val SHOW_BACK_STUB_LAMBDA_PREFIX = "lambda\$showBackStubWindow\$"
private const val UPDATE_FSG_VISIBILITY_LAMBDA_PREFIX =
    "lambda\$updateFsgWindowVisibilityState\$"
private const val BASE_RECENTS_LAMBDA_SUFFIX = "\$BaseRecentsImpl"

internal class ForceFsgNavBarCallerScope {
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

internal val forceFsgNavBarCallerScope = ForceFsgNavBarCallerScope()

internal fun installForceFsgNavBarCallerScope(classLoader: ClassLoader?): Class<*> {
    val baseRecentsClass = XposedHelpers.findClass(
        "com.miui.home.recents.BaseRecentsImpl",
        classLoader
    )
    installForceFsgNavBarCallerScope(baseRecentsClass)
    return baseRecentsClass
}

internal fun resolveForceFsgNavBarCallerMethods(baseRecentsClass: Class<*>): List<Method> {
    val methods = baseRecentsClass.declaredMethods.filter { method ->
        method.returnType == Void.TYPE && when {
            method.name == "updateFsgWindowState" -> method.parameterTypes.isEmpty()
            method.name.startsWith(SHOW_BACK_STUB_LAMBDA_PREFIX) &&
                method.name.endsWith(BASE_RECENTS_LAMBDA_SUFFIX) ->
                method.parameterTypes.contentEquals(arrayOf(Boolean::class.javaPrimitiveType!!))
            method.name.startsWith(UPDATE_FSG_VISIBILITY_LAMBDA_PREFIX) &&
                method.name.endsWith(BASE_RECENTS_LAMBDA_SUFFIX) ->
                method.parameterTypes.contentEquals(
                    arrayOf(Boolean::class.javaPrimitiveType!!, String::class.java)
                )
            else -> false
        }
    }

    check(methods.count { it.name == "updateFsgWindowState" } == 1 &&
        methods.count { it.name.startsWith(SHOW_BACK_STUB_LAMBDA_PREFIX) } == 1 &&
        methods.count { it.name.startsWith(UPDATE_FSG_VISIBILITY_LAMBDA_PREFIX) } == 1
    ) {
        "Expected exactly three verified force_fsg_nav_bar callers in ${baseRecentsClass.name}, " +
            "found ${methods.joinToString { it.name }}"
    }
    return methods.sortedBy { it.name }
}

internal fun installForceFsgNavBarCallerScope(baseRecentsClass: Class<*>) {
    val callerMethods = resolveForceFsgNavBarCallerMethods(baseRecentsClass)
    val installed = ArrayList<CustomMethodUnhooker>(callerMethods.size)
    val scopeHook = object : MethodHook() {
        override fun intercept(chain: XposedInterface.Chain): Any? {
            forceFsgNavBarCallerScope.enter()
            return try {
                chain.proceed()
            } finally {
                forceFsgNavBarCallerScope.exit()
            }
        }
    }

    for (method in callerMethods) {
        val unhooker = ModuleHelper.hookMethod(method, scopeHook)
        if (unhooker != null) {
            installed.add(unhooker)
            continue
        }

        for (installedHook in installed.asReversed()) {
            try {
                installedHook.unhook()
            } catch (oom: OutOfMemoryError) {
                throw oom
            } catch (t: Throwable) {
                XposedHelpers.log(FatalErrors.unwrapAndRethrowIfFatal(t))
            }
        }
        error("Failed to install force_fsg_nav_bar caller scope for ${method.name}")
    }
}
