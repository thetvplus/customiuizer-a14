package tv.withaibuild.customiuizer.mods.island

/**
 * Pure upward-recall recognizer for the Dynamic Island capsule.
 *
 * Distance is density-aware (scaled touch slop multiples). A gesture latches after
 * the first trigger; ACTION_CANCEL / a new DOWN resets.
 */
object IslandRecallGesture {

    const val STATE_IDLE = 0
    const val STATE_TRACKING = 1
    const val STATE_TRIGGERED = 2

    data class Snapshot(
        val state: Int = STATE_IDLE,
        val pointerId: Int = -1,
        val downX: Float = 0f,
        val downY: Float = 0f,
    )

    data class Config(
        val scaledTouchSlopPx: Int,
        val minUpDistancePx: Float,
        val maxHorizontalRatio: Float,
    )

    @JvmStatic
    fun configFromSlop(scaledTouchSlopPx: Int, heightPx: Int = Int.MAX_VALUE): Config {
        val slop = scaledTouchSlopPx.coerceAtLeast(1)
        return Config(
            scaledTouchSlopPx = slop,
            minUpDistancePx = minOf(slop * 1.75f, heightPx * .35f).coerceAtLeast(slop * 1.25f),
            maxHorizontalRatio = 0.85f,
        )
    }

    @JvmStatic
    fun onDown(pointerId: Int, x: Float, y: Float): Snapshot =
        Snapshot(STATE_TRACKING, pointerId, x, y)

    @JvmStatic
    fun onMove(current: Snapshot, pointerId: Int, x: Float, y: Float, config: Config): Snapshot {
        if (current.state != STATE_TRACKING) return current
        if (pointerId != current.pointerId) return current
        val dx = x - current.downX
        val dy = current.downY - y
        if (dy < config.minUpDistancePx) return current
        if (dy < config.scaledTouchSlopPx) return current
        val adx = kotlin.math.abs(dx)
        if (adx > dy * config.maxHorizontalRatio) return current
        return current.copy(state = STATE_TRIGGERED)
    }

    /** Complete displacement is authoritative when the dispatcher coalesces all MOVE events. */
    @JvmStatic
    fun onUp(current: Snapshot, pointerId: Int, x: Float, y: Float, config: Config, durationMs: Long): Snapshot {
        val distance = current.downY - y
        val fast = durationMs in 1..160 && distance / durationMs >= config.scaledTouchSlopPx * .05f
        return onMove(current, pointerId, x, y, if (fast)
            config.copy(minUpDistancePx = config.scaledTouchSlopPx * 1.25f) else config)
    }

    @JvmStatic
    fun onCancel(): Snapshot = Snapshot()

    @JvmStatic
    fun isTriggered(current: Snapshot): Boolean = current.state == STATE_TRIGGERED
}
