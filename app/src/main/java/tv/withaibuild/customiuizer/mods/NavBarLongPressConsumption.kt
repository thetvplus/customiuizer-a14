package tv.withaibuild.customiuizer.mods

import io.github.libxposed.api.XposedInterface
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import java.util.concurrent.atomic.AtomicLong

/** One successful press, matching the ROM detector's single active rule. Holds no host objects. */
internal class NavBarLongPressConsumption(
    private val cancelMenuPreload: ((XposedInterface.Chain) -> Unit)? = null,
) {
    private val handledPress = AtomicLong(0L)

    fun canTrack(keyCode: Int, downTime: Long): Boolean = encode(keyCode, downTime) != 0L

    fun hasHandledPress(): Boolean = handledPress.get() != 0L

    fun dispatch(chain: XposedInterface.Chain, keyCode: Int, downTime: Long, action: Int): Any? {
        val outcome = if (action == 0 || action == 1) consumeOutcome(keyCode, downTime, action == 1) else 0
        if (outcome == 0) return chain.proceed()
        if (outcome == 2 && keyCode == 82 && cancelMenuPreload != null) {
            try {
                cancelMenuPreload.invoke(chain)
            } catch (failure: Throwable) {
                FatalErrors.unwrapAndRethrowIfFatal(failure)
                XposedHelpers.log("NavBarLongPressConsumption MENU preload cleanup failed: " + failure.javaClass.simpleName)
                XposedHelpers.log(failure)
            }
        }
        return -1L
    }

    fun markHandled(keyCode: Int, downTime: Long) {
        val marker = encode(keyCode, downTime)
        if (marker != 0L) handledPress.set(marker)
    }

    /** Continues to consume this press even if its preference is disabled before key-up. */
    fun consume(keyCode: Int, downTime: Long, keyUp: Boolean): Boolean =
        consumeOutcome(keyCode, downTime, keyUp) != 0

    /** 0 = unmatched, 1 = consumed, 2 = consumed and uniquely cleared on key-up. */
    private fun consumeOutcome(keyCode: Int, downTime: Long, keyUp: Boolean): Int {
        val marker = encode(keyCode, downTime)
        if (marker == 0L || handledPress.get() != marker) return 0
        if (keyUp && handledPress.compareAndSet(marker, 0L)) return 2
        return 1
    }

    private fun encode(keyCode: Int, downTime: Long): Long {
        if (keyCode != 3 && keyCode != 4 && keyCode != 82) return 0L
        if (downTime < 0L || downTime > MAX_DOWN_TIME) return 0L
        // Zero downTime is valid; the nonzero key code keeps it distinct from an empty slot.
        return (downTime shl 8) or keyCode.toLong()
    }

    companion object {
        internal const val MAX_DOWN_TIME = Long.MAX_VALUE ushr 8
    }
}
