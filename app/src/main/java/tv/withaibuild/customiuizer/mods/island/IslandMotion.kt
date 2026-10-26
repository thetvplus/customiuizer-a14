package tv.withaibuild.customiuizer.mods.island

import android.animation.TimeInterpolator
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/** Damped step response: prompt travel followed by one small, decaying overshoot. */
internal object IslandMotion {
    const val ENTER = 360L
    const val MORPH = 320L
    const val REBOUND = 190L
    const val EXIT = 300L
    const val SECONDARY = 380L
    const val CONTENT = 220L
    const val CONTENT_ARRIVAL = 300L
    const val ALBUM = 300L
    const val MEDIA_DELAY = 750L
    const val MEDIA_RETURN_DELAY = 80L
    const val MEDIA_RETURN = 560L
    const val PAUSE_LIFETIME = 2000L
    const val LOADING_LIFETIME = 30000L
    const val LANDING_WIDTH_RATIO = .84f
    const val LANDING_HEIGHT_RATIO = .86f
    /** All accepted native content lands inside the shell, with the same centre and
     * height. Keep at least a round end cap even for the smallest available island. */
    fun landingWidth(width: Float, height: Float) = maxOf(width * LANDING_WIDTH_RATIO, minOf(width, height))
    val relaxed = TimeInterpolator { progress(it) }
    /** Committed collapse/exit and canceled drags settle without crossing their endpoint. */
    val settle = TimeInterpolator { val t = 1f - it.coerceIn(0f, 1f); 1f - t * t * t }
    fun morphDuration(fromWidth: Float, fromHeight: Float, width: Float, height: Float, secondary: Boolean): Long {
        val change = maxOf(kotlin.math.abs(width - fromWidth) / maxOf(width, fromWidth, 1f),
            kotlin.math.abs(height - fromHeight) / maxOf(height, fromHeight, 1f)).coerceIn(0f, 1f)
        if (change < .003f) return CONTENT
        val full = if (secondary) SECONDARY else MORPH
        return (CONTENT + (full - CONTENT) * (change / .55f).coerceAtMost(1f)).toLong()
    }
    /** Similar footprints only exchange content. Elastic travel is proportional to
     * actual visible size change, never the activity kind or text length alone. */
    fun sizeCatch(fromWidth: Float, fromHeight: Float, width: Float, height: Float, minimum: Float): Float {
        val dw = kotlin.math.abs(width - fromWidth)
        val dh = kotlin.math.abs(height - fromHeight)
        if (maxOf(dw, dh) <= minimum) return 0f
        val change = maxOf(dw / maxOf(width, fromWidth, 1f), dh / maxOf(height, fromHeight, 1f))
        return ((change - .08f) / .42f).coerceIn(0f, 1f) * .03f
    }
    /** The native window arrives while the shell's one finite clock makes a small catch. */
    fun mediaReturnCatch(progress: Float, nativeWindow: Boolean = true): Float {
        if (nativeWindow && progress < .55f) {
            val intake = ((progress - .15f) / .4f).coerceIn(0f, 1f)
            return -3.2f * intake * intake * (1f - intake) * (1f - intake)
        }
        val start = if (nativeWindow) .55f else 0f
        val t = ((progress - start) / (1f - start)).coerceIn(0f, 1f)
        val tail = 1f - t
        return 7.2f * t * t * tail * tail
    }
    fun catchDerivative(progress: Float, nativeWindow: Boolean = true): Float {
        if (nativeWindow && progress > .15f && progress < .55f) {
            val intake = (progress - .15f) / .4f
            return -6.4f * intake * (1f - intake) * (1f - 2f * intake) / .4f
        }
        val start = if (nativeWindow) .55f else 0f
        if (progress <= start || progress >= 1f) return 0f
        val t = (progress - start) / (1f - start)
        return 14.4f * t * (1f - t) * (1f - 2f * t) / (1f - start)
    }
    fun arrivalScale(progress: Float): Float = .96f + .04f *
        settle.getInterpolation(((progress - .5f) / .5f).coerceIn(0f, 1f))
    private const val DECAY = 7.04
    private const val FREQUENCY = 5.28
    private const val PHASE = 4.0 / 3.0
    private val finalResponse = response(1.0)

    private fun response(t: Double): Double =
        1.0 - exp(-DECAY * t) * (cos(FREQUENCY * t) + PHASE * sin(FREQUENCY * t))

    fun progress(input: Float): Float {
        return (response(input.coerceIn(0f, 1f).toDouble()) / finalResponse).toFloat()
    }

    /** Analytic damping in physical pixels; overscroll alone uses an elastic response. The animator is only a
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
        private var elastic = false

        fun addVelocity(physicalVelocity: Float) { velocity += physicalVelocity }

        fun retarget(current: Float, destination: Float, seconds: Float, carry: Boolean, elastic: Boolean = false) {
            value = current
            target = destination
            if (!carry) velocity = 0f
            velocityAtStart = velocity
            omega = 10.5 / seconds.coerceIn(.1f, 1f)
            this.elastic = elastic
            displacement = (current - destination).toDouble()
            impulse = velocity + omega * displacement
        }

        fun sample(seconds: Float, minimum: Float = -Float.MAX_VALUE, maximum: Float = Float.MAX_VALUE): Float {
            val time = seconds.coerceAtLeast(0f).toDouble()
            if (elastic) {
                val damping = .56 * omega
                val frequency = omega * kotlin.math.sqrt(1.0 - .56 * .56)
                val b = (velocityAtStart + damping * displacement) / frequency
                val c = cos(frequency * time); val s = sin(frequency * time)
                val offset = displacement * c + b * s
                val decay = exp(-damping * time)
                value = (target + decay * offset).toFloat()
                velocity = (decay * (-damping * offset + frequency * (b * c - displacement * s))).toFloat()
                if (value < minimum) { value = minimum; velocity = 0f }
                if (value > maximum) { value = maximum; velocity = 0f }
                return value
            }
            val decay = exp(-omega * time)
            val offset = displacement + impulse * time
            value = (target + offset * decay).toFloat()
            velocity = ((impulse - omega * offset) * decay).toFloat()
            if (value < minimum) { value = minimum; velocity = 0f }
            if (value > maximum) { value = maximum; velocity = 0f }
            return value
        }

        fun finish() { value = target; velocity = 0f }
        private var velocityAtStart = 0f
    }
}
