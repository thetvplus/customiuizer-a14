package tv.withaibuild.customiuizer.mods

import io.github.libxposed.api.XposedInterface
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import java.lang.reflect.Constructor

/**
 * Resolves [com.android.server.wm.WindowSurfaceController] constructor slots by parameter
 * type, never by a hard-coded `args[2] as Int`.
 *
 * AOSP 12+ constructs the surface from a Builder and has no flags argument; mutating
 * `args[2]` there would rewrite `windowType`. Unknown shapes fail closed: the hook
 * proceeds unchanged and `setSecure` remains the FLAG_SECURE path.
 */
internal object WindowSurfaceControlArgs {
    const val FLAG_SECURE = 0x80
    const val SKIP_SCREENSHOT = 0x40

    private val INT = Int::class.javaPrimitiveType!!

    fun parameterTypes(chain: XposedInterface.Chain): Array<Class<*>>? {
        val member = chain.executable ?: return null
        return (member as? Constructor<*>)?.parameterTypes
    }

    /**
     * Index of the SurfaceControl flags `int`, or `-1` if this constructor has none.
     *
     * - AOSP 10/11: `(String name, int w, int h, int format, int flags, …)` → 4
     * - MIUI HyperOS historical contract: flags at 2, windowType at 4
     */
    fun flagsIndex(types: Array<Class<*>>): Int {
        if (isAospLegacySurfaceConstructor(types)) return 4
        if (isMiuiFlagsAtTwo(types)) return 2
        return -1
    }

    /**
     * Index of the window type `int` used by overlay filtering, or `-1` if unknown.
     */
    fun windowTypeIndex(types: Array<Class<*>>, flagsIndex: Int): Int {
        if (flagsIndex == 4 && types.size >= 7 && types[6] == INT) return 6
        if (flagsIndex == 2 && types.size >= 5 && types[4] == INT) return 4
        return -1
    }

    /**
     * Mutates SurfaceControl flags when the constructor actually has them.
     *
     * [transform] receives the current flags and the resolved window type (nullable).
     * Return `null` to leave the constructor unmodified. ClassCast / NPE / OOB in the
     * hook body are logged; the original constructor still runs. Exceptions from
     * `chain.proceed()` propagate to the host unchanged.
     */
    fun interceptFlags(
        chain: XposedInterface.Chain,
        transform: (flags: Int, windowType: Int?) -> Int?,
    ): Any? {
        var proceedArgs: Array<Any?>? = null
        try {
            val types = parameterTypes(chain) ?: return chain.proceed()
            val flagsSlot = flagsIndex(types)
            if (flagsSlot < 0) return chain.proceed()
            val flags = chain.getArg(flagsSlot)
            if (flags !is Int) return chain.proceed()
            val windowTypeIndex = windowTypeIndex(types, flagsSlot)
            val windowType = if (windowTypeIndex >= 0) chain.getArg(windowTypeIndex) as? Int else null
            val newFlags = transform(flags, windowType) ?: return chain.proceed()
            if (newFlags == flags) return chain.proceed()
            val args = XposedHelpers.getArgsArray(chain)
            args[flagsSlot] = newFlags
            proceedArgs = args
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            XposedHelpers.log(t)
        }
        return if (proceedArgs != null) chain.proceed(proceedArgs) else chain.proceed()
    }

    private fun isAospLegacySurfaceConstructor(types: Array<Class<*>>): Boolean {
        return types.size >= 5 &&
            types[0] == String::class.java &&
            types[1] == INT &&
            types[2] == INT &&
            types[3] == INT &&
            types[4] == INT
    }

    private fun isMiuiFlagsAtTwo(types: Array<Class<*>>): Boolean {
        return types.size >= 5 && types[2] == INT && types[4] == INT
    }
}
