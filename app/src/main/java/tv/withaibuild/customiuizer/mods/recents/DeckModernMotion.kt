package tv.withaibuild.customiuizer.mods.recents

import java.lang.reflect.Modifier

import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import tv.withaibuild.customiuizer.mods.utils.CleanupActions

/** 4.39.30's SpringBundle consumer. Never uses the legacy RectAnimType fields.
 * Runs only at native scene initialization/retarget, not on Surface frames. */
internal class DeckModernMotion(type: Class<*>) {
    private val gestures = ThreadLocal<Int>()
    fun enterGesture() { gestures.set((gestures.get() ?: 0) + 1) }
    fun leaveGesture() {
        val depth = (gestures.get() ?: 1) - 1
        if (depth == 0) gestures.remove() else gestures.set(depth)
    }
    fun closingScene() = if ((gestures.get() ?: 0) > 0) DeckSpringScene.GESTURE else DeckSpringScene.TOGGLE
    val configure = type.getDeclaredMethod("setAnimParamByType",
        type.declaredClasses.single { it.simpleName == "AnimType" && it.isEnum }).apply { require(returnType==Void.TYPE && !Modifier.isStatic(modifiers)) }
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

    private class Request(val adapter: DeckModernMotion, val owner: DeckOwner,
        val ticket: Long, val scene: DeckSpringScene) {
        var snapshot: DeckSpringParameters.Snapshot? = null
    }
    fun mark(spring: Any, owner: DeckOwner, ticket: Long, scene: DeckSpringScene) {
        val previous = request(spring)
        if (previous?.owner === owner && previous.ticket == ticket && previous.scene == scene) return
        if (previous != null) previous.adapter.restore(spring, previous)
        XposedHelpers.setAdditionalInstanceField(spring, KEY, Request(this, owner, ticket, scene))
    }
    fun beforeConfigure(spring: Any) {
        request(spring)?.let { restore(spring, it) }
    }
    fun afterConfigure(spring: Any, nativeType: String?, failed: Boolean) {
        val request = request(spring) ?: return
        val expected = if (request.scene == DeckSpringScene.OPEN) "OPEN_FROM_RECENTS" else "CLOSE_TO_RECENTS"
        if (failed || nativeType != expected || !request.owner.customMotionAllowed || request.owner.transition != request.ticket) {
            XposedHelpers.removeAdditionalInstanceField(spring, KEY); return
        }
        request.snapshot = applyParameters(spring, request.scene)
    }
    internal fun applyParameters(spring: Any, scene: DeckSpringScene): DeckSpringParameters.Snapshot {
        val saved = read(spring)
        try {
            for (axis in axes) setter.invoke(spring, axis, scene.damping, scene.response)
            return DeckSpringParameters.Snapshot(saved, read(spring))
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
    private fun restore(spring: Any, request: Request) {
        val snapshot = request.snapshot ?: return
        restoreParameters(spring, snapshot)
        request.snapshot = null
    }
    internal fun restoreParameters(spring: Any, snapshot: DeckSpringParameters.Snapshot) {
        val saved = snapshot.original
        val applied = snapshot.applied
        val cleanup = CleanupActions()
        for (i in bundles.indices) cleanup.run {
            val bundle = bundles[i].get(spring)
            // Keep a newer host write to either member of this native pair.
            if (damping.invoke(bundle) == applied[i*2] && stiffness.invoke(bundle) == applied[i*2+1])
                restore.invoke(bundle, saved[i*2], saved[i*2+1])
        }
        cleanup.finish()
    }
    companion object {
        private const val KEY = "a14.recents.deck.modern.motion"
        private fun request(spring: Any) = XposedHelpers.getAdditionalInstanceField(spring, KEY) as? Request
        fun clear(spring: Any, owner: DeckOwner, ticket: Long) {
            val request = request(spring) ?: return
            if (request.owner === owner && request.ticket == ticket) {
                try { request.adapter.restore(spring, request) }
                finally { XposedHelpers.removeAdditionalInstanceField(spring, KEY) }
            }
        }
    }
}
