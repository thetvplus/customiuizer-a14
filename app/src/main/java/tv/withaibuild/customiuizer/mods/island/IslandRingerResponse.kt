package tv.withaibuild.customiuizer.mods.island

import kotlin.math.abs

/** 22G100 ringer negative response: center kick, recenter, then release width.
 * Native response periods are mapped to our analytic solver. Milestones are
 * normalized spring progress, never delays; private UIKit retargetImpulse is
 * not an Android velocity. The shell's existing finite clock owns sampling. */
internal class IslandRingerResponse(density: Float, rtl: Boolean) {
    private val direction = if (rtl) -1f else 1f
    private val displacement = 8f * density
    private val expansion = 22f * density
    private val kickProfile = IslandMotion.Profile(.222, .141)
    private val stretchProfile = IslandMotion.Profile(.23, 1.0)
    private val releaseProfile = IslandMotion.Profile(.732, 1.0)
    private val kick = IslandMotion.Spring().apply { retarget(0f, -displacement, kickProfile, false) }
    private val recenter = IslandMotion.Spring()
    private val stretch = IslandMotion.Spring().apply { retarget(0f, expansion, stretchProfile, false) }
    private val release = IslandMotion.Spring()
    private val prepareAt: Float
    private val releaseAt: Float
    val durationMillis: Long
    var x = 0f
        private set
    var width = 0f
        private set
    var velocityX = 0f
        private set
    var velocityWidth = 0f
        private set

    init {
        prepareAt = firstCrossing { kick.sample(it) / -displacement >= .923f }
        val from = kick.sample(prepareAt)
        val initialVelocity = kick.velocity
        recenter.setVelocity(initialVelocity)
        recenter.retarget(from, 0f, kickProfile, true)
        releaseAt = prepareAt + firstCrossing { (recenter.sample(it) - from) / -from >= .756f }
        val wide = stretch.sample(releaseAt)
        release.setVelocity(stretch.velocity)
        release.retarget(wide, 0f, releaseProfile, true)
        val centerEnd = prepareAt + kickProfile.horizon(from, initialVelocity) / 1000f
        val widthEnd = releaseAt + firstCrossing { abs(release.sample(it)) <= .25f && abs(release.velocity) <= 2f }
        durationMillis = (maxOf(centerEnd, widthEnd) * 1000).toLong().coerceIn(400L, 2000L)
    }

    fun sample(seconds: Float) {
        if (seconds * 1000 >= durationMillis) { x = 0f; width = 0f; velocityX = 0f; velocityWidth = 0f; return }
        val center = if (seconds < prepareAt) kick else recenter
        val centerTime = if (seconds < prepareAt) seconds else seconds - prepareAt
        val breadth = if (seconds < releaseAt) stretch else release
        val breadthTime = if (seconds < releaseAt) seconds else seconds - releaseAt
        width = breadth.sample(breadthTime).coerceAtLeast(0f)
        velocityWidth = breadth.velocity
        // The sensor's 3-point offset and 22-point widening share one phase.
        x = direction * (center.sample(centerTime) + width * (3f / 22f))
        velocityX = direction * (center.velocity + velocityWidth * (3f / 22f))
    }

    private inline fun firstCrossing(crossed: (Float) -> Boolean): Float {
        var low = 0f
        var high = .004f
        while (high < 2f && !crossed(high)) { low = high; high += .004f }
        repeat(20) { val middle = (low + high) * .5f; if (crossed(middle)) high = middle else low = middle }
        return high
    }
}
