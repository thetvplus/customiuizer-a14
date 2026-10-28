package tv.withaibuild.customiuizer.mods.island

/** Two scalar samples from the existing native gesture; never queries or sends during MOVE. */
internal class IslandGestureImpulse {
    private var time = -1L
    private var y = 0f
    private var velocity = 0f
    var strength = 0f
        private set

    fun begin(y: Float, at: Long) {
        cancel()
        if (y.isFinite() && at >= 0L) { this.y = y; time = at }
    }
    fun move(y: Float, at: Long, density: Float) {
        if (time < 0 || !y.isFinite() || at <= time) return
        val elapsed = at - time
        val measured = ((this.y - y) * 1000f / elapsed).coerceIn(-20_000f, 20_000f)
        // Time-weighted recent velocity is independent of event rate and path length.
        // Reversal and a pause shed the previous impulse through the same finite sample.
        val weight = elapsed / (24f + elapsed)
        velocity += (measured - velocity) * weight
        val speed = velocity.coerceAtLeast(0f) / density.coerceAtLeast(.1f)
        strength = (speed / (speed + 1800f)).coerceIn(0f, 1f)
        this.y = y; time = at
    }
    fun cancel() { time = -1L; velocity = 0f; strength = 0f }
    fun recentStrength(at: Long) = if (time >= 0L && at - time in 0L..200L) strength else 0f
}
