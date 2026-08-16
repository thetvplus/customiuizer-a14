package tv.withaibuild.customiuizer.mods

import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers

private const val NO_WORD_MODEL_METHOD = "isNoWordModel"
private const val UTILITIES_CLASS = "com.miui.home.launcher.common.Utilities"

/**
 * Thread-scoped marker telling the permanent `Utilities.isNoWordModel` hook that the current
 * thread is executing `DeviceConfig.isCellSizeChangedByTheme`.
 */
internal class UnlockGridsNoWordScope {
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

internal val unlockGridsNoWordScope = UnlockGridsNoWordScope()

/**
 * Resolves the exact `Utilities.isNoWordModel()` ABI: a static, no-argument method returning a
 * primitive boolean. Anything else means the ROM changed and the caller must not install.
 */
internal fun resolveNoWordModelMethod(utilitiesClass: Class<*>): Method {
    val methods = utilitiesClass.declaredMethods.filter { method ->
        method.name == NO_WORD_MODEL_METHOD &&
            method.parameterTypes.isEmpty() &&
            method.returnType == Boolean::class.javaPrimitiveType &&
            Modifier.isStatic(method.modifiers)
    }

    check(methods.size == 1) {
        "Expected exactly one static no-argument $NO_WORD_MODEL_METHOD in ${utilitiesClass.name}, " +
            "found ${methods.size}"
    }
    return methods[0]
}

/**
 * Installs the `Utilities.isNoWordModel` override once, for the whole process lifetime.
 *
 * UnlockGrids only needs `isNoWordModel()` to report false while the launcher evaluates
 * `DeviceConfig.isCellSizeChangedByTheme`. Installing and unhooking a nested hook around every
 * such evaluation mutated the hook topology at runtime, shared one mutable unhooker field
 * between callers, and could unhook another thread's hook. A permanent hook plus a
 * [UnlockGridsNoWordScope] leaves exactly one thread-local boolean check on the runtime path.
 */
internal fun installUnlockGridsNoWordScope(classLoader: ClassLoader?) {
    val utilitiesClass = XposedHelpers.findClass(UTILITIES_CLASS, classLoader)
    val method = resolveNoWordModelMethod(utilitiesClass)
    ModuleHelper.hookMethod(method, object : MethodHook() {
        override fun intercept(chain: XposedInterface.Chain): Any? {
            if (unlockGridsNoWordScope.isActive()) return false
            return chain.proceed()
        }
    })
}
