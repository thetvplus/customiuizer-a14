package tv.withaibuild.customiuizer.mods.island

import java.lang.ref.WeakReference
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.FatalErrors

/** Older six-track springs read these fields in startAllAnim, after native parameter selection. */
internal class LegacyLauncherReturnTiming(type: Class<*>) {
    private val fields = listOf("mCenterXStiffness", "mCenterYStiffness", "mWidthStiffness",
        "mRatioStiffness", "mRadiusStiffness", "mAlphaStiffness").map { type.getDeclaredField(it).apply { isAccessible = true } }
    private val response = type.getDeclaredMethod("getModifyResponse", Float::class.javaPrimitiveType).apply { isAccessible = true }
    private var owner = WeakReference<Any>(null)
    private var original: FloatArray? = null
    private var factor = 1f

    fun apply(spring: Any): Boolean {
        restore()
        val values = FloatArray(fields.size) { fields[it].getFloat(spring) }
        if (values.any { !it.isFinite() || it <= 0f }) return false
        val duration = response.invoke(spring, IslandMotion.returnResponse(.5f)) as Float
        if (!duration.isFinite() || duration <= 0f) return false
        factor = (39.478418f / (duration * duration * values[2])).coerceIn(.25f, 4f)
        var written = -1
        try {
            for (i in fields.indices) { written = i; fields[i].setFloat(spring, values[i] * factor) }
            owner = WeakReference(spring); original = values
            return true
        } catch (error: Throwable) {
            for (i in 0..written) ModuleHelper.guarded { fields[i].setFloat(spring, values[i]) }
            FatalErrors.unwrapAndRethrowIfFatal(error)
            return false
        }
    }
    fun restore(only: Any? = null) {
        val spring = owner.get() ?: run { original = null; return }
        if (only != null && spring !== only) return
        val values = original ?: return
        owner.clear(); original = null
        for (i in fields.indices) ModuleHelper.guarded {
            if (fields[i].getFloat(spring) == values[i] * factor) fields[i].setFloat(spring, values[i])
        }
    }
}
