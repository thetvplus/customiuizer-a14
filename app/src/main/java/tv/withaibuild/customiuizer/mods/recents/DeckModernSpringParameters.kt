package tv.withaibuild.customiuizer.mods.recents

import java.lang.reflect.Modifier

import tv.withaibuild.customiuizer.mods.utils.CleanupActions

/** 4.39.30's SpringBundle consumer. Never uses the legacy RectAnimType fields.
 * Runs only at native scene initialization/retarget, not on Surface frames. */
internal class DeckModernSpringParameters(type: Class<*>): DeckSpringTuning {
    private val setter = type.getDeclaredMethod("setAnimParam", String::class.java,
        Float::class.javaPrimitiveType, Float::class.javaPrimitiveType).apply { require(returnType==Void.TYPE && !Modifier.isStatic(modifiers)); isAccessible = true }
    private val axes = arrayOf("centerX", "centerY", "Width", "Ratio")
    private val bundles = arrayOf("mCenterXBundle", "mCenterYBundle", "mWidthBundle", "mRatioBundle")
        .map { type.getDeclaredField(it).apply { require(!Modifier.isStatic(modifiers)); isAccessible = true } }
    private val bundleType = bundles.first().type
    private val damping = bundleType.getDeclaredMethod("getDamping").apply { require(returnType==Float::class.javaPrimitiveType && !Modifier.isStatic(modifiers)) }
    private val stiffness = bundleType.getDeclaredMethod("getStiffness").apply { require(returnType==Float::class.javaPrimitiveType && !Modifier.isStatic(modifiers)) }
    private val restore = bundleType.getDeclaredMethod("setDampingAndStiffness",
        Float::class.javaPrimitiveType, Float::class.javaPrimitiveType).apply { require(returnType==Void.TYPE && !Modifier.isStatic(modifiers)) }
    init { require(bundles.all { it.type==bundleType }) }

    override fun apply(spring: Any, scene: DeckSpringScene): DeckSpringTuning.Snapshot {
        val saved = read(spring)
        try {
            for (axis in axes) setter.invoke(spring, axis, scene.damping, scene.response)
            return DeckSpringTuning.Snapshot(saved, read(spring))
        } catch (error: Throwable) {
            val cleanup = CleanupActions(error)
            for (i in bundles.indices) cleanup.run { restore.invoke(bundles[i].get(spring), saved[i*2], saved[i*2+1]) }
            cleanup.finish()
            throw error
        }
    }
    private fun read(spring: Any) = FloatArray(8).also { values ->
        for (i in bundles.indices) {
            val bundle = bundles[i].get(spring)
            values[i*2] = damping.invoke(bundle) as Float
            values[i*2+1] = stiffness.invoke(bundle) as Float
        }
    }
    override fun restore(spring: Any, saved: DeckSpringTuning.Snapshot) {
        val original = saved.original
        val applied = saved.applied
        val cleanup = CleanupActions()
        for (i in bundles.indices) cleanup.run {
            val bundle = bundles[i].get(spring)
            // Keep a newer host write to either member of this native pair.
            if (damping.invoke(bundle) == applied[i*2] && stiffness.invoke(bundle) == applied[i*2+1])
                restore.invoke(bundle, original[i*2], original[i*2+1])
        }
        cleanup.finish()
    }
}
