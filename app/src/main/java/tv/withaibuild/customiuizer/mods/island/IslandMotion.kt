package tv.withaibuild.customiuizer.mods.island

import android.animation.TimeInterpolator
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/** Damped step response: prompt travel followed by one small, decaying overshoot. */
internal object IslandMotion {
    const val ENTER = 360L
    const val MORPH = 320L
    const val REBOUND = 280L
    const val EXIT = 300L
    const val SECONDARY = 440L
    const val MEDIA_DELAY = 750L
    const val MEDIA_RETURN_DELAY = 80L
    const val MEDIA_RETURN = 560L
    const val PAUSE_LIFETIME = 2000L
    val relaxed = TimeInterpolator { progress(it) }
    /** Committed collapse/exit and canceled drags settle without crossing their endpoint. */
    val settle = TimeInterpolator { val t = 1f - it.coerceIn(0f, 1f); 1f - t * t * t }
    /** Cover arrives before the small catch settles; both share the shell's finite clock. */
    fun mediaReturnTravel(progress: Float): Float {
        val t = (progress / .72f).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    fun mediaReturnCatch(progress: Float): Float {
        val t = ((progress - .55f) / .45f).coerceIn(0f, 1f)
        return (sin(Math.PI * t) * (1f - t)).toFloat()
    }
    private const val DECAY = 7.04
    private const val FREQUENCY = 5.28
    private const val PHASE = 4.0 / 3.0
    private val finalResponse = response(1.0)

    private fun response(t: Double): Double =
        1.0 - exp(-DECAY * t) * (cos(FREQUENCY * t) + PHASE * sin(FREQUENCY * t))

    fun progress(input: Float): Float {
        return (response(input.coerceIn(0f, 1f).toDouble()) / finalResponse).toFloat()
    }

    /** Critical damping, solved analytically in physical pixels. The animator is only a
     * finite clock; retargeting preserves measured position and velocity, independent of Hz.
     * No reference implementation is incorporated and no spring library is required. */
    class Spring {
        var velocity = 0f
            private set
        var value = 0f
            private set
        private var target = 0f
        private var displacement = 0.0
        private var impulse = 0.0
        private var omega = 1.0

        fun retarget(current: Float, destination: Float, seconds: Float, carry: Boolean) {
            value = current
            target = destination
            if (!carry) velocity = 0f
            omega = 10.5 / seconds.coerceIn(.1f, 1f)
            displacement = (current - destination).toDouble()
            impulse = velocity + omega * displacement
        }

        fun sample(seconds: Float, minimum: Float = -Float.MAX_VALUE, maximum: Float = Float.MAX_VALUE): Float {
            val time = seconds.coerceAtLeast(0f).toDouble()
            val decay = exp(-omega * time)
            val offset = displacement + impulse * time
            value = (target + offset * decay).toFloat()
            velocity = ((impulse - omega * offset) * decay).toFloat()
            if (value < minimum) { value = minimum; velocity = 0f }
            if (value > maximum) { value = maximum; velocity = 0f }
            return value
        }

        fun finish() { value = target; velocity = 0f }
    }
}
