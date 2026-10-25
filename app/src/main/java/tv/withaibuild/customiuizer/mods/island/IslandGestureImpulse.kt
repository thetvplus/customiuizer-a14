package tv.withaibuild.customiuizer.mods.island

/** Two scalar samples from the existing native gesture; never queries or sends during MOVE. */
internal class IslandGestureImpulse {
    private var time = -1L
    private var y = 0f
    private var velocity = 0f
    var strength = 0f
        private set

    fun begin(y: Float, at: Long) { this.y = y; time = at; velocity = 0f; strength = 0f }
    fun move(y: Float, at: Long, density: Float) {
        if (time < 0 || !y.isFinite() || at <= time) return
        val elapsed = at - time
        val measured = ((this.y - y) * 1000f / elapsed).coerceIn(-20_000f, 20_000f)
        velocity = if (elapsed > 120) measured else velocity * .35f + measured * .65f
        strength = (velocity.coerceAtLeast(0f) / (density.coerceAtLeast(.1f) * 2500f)).coerceIn(0f, 1f)
        this.y = y; time = at
    }
    fun cancel() { time = -1L; velocity = 0f; strength = 0f }
}
