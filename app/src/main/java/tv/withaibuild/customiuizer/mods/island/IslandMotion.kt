package tv.withaibuild.customiuizer.mods.island

import android.animation.TimeInterpolator
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/** Damped step response: prompt travel followed by one small, decaying overshoot. */
internal object IslandMotion {
    const val ENTER = 360L
    const val MORPH = 320L
    const val REBOUND = 260L
    const val EXIT = 300L
    const val SECONDARY = 380L
    const val CONTENT = 220L
    const val CONTENT_ARRIVAL = 300L
    const val ALBUM = 300L
    const val MEDIA_DELAY = 750L
    const val MEDIA_RETURN_DELAY = 80L
    const val MEDIA_RETURN = 560L
    const val ABSORPTION_TIME_SCALE = 1.18f
    const val PAUSE_LIFETIME = 2000L
    const val LOADING_LIFETIME = 30000L
    const val NATIVE_CARD_GAP_LIFETIME = 30000L
    const val EXPANDED_DWELL = 5000L
    const val LANDING_WIDTH_RATIO = .84f
    const val LANDING_HEIGHT_RATIO = .86f
    /** All accepted native content lands inside the shell, with the same centre and
     * height. Keep at least a round end cap even for the smallest available island. */
    fun landingWidth(width: Float, height: Float) = maxOf(width * LANDING_WIDTH_RATIO, minOf(width, height))
    val relaxed = TimeInterpolator { progress(it) }
    /** Committed collapse/exit and canceled drags settle without crossing their endpoint. */
    val settle = TimeInterpolator { val t = 1f - it.coerceIn(0f, 1f); 1f - t * t * t }
    /** Rubber-band displacement and its derivative share the same units. Recent native
     * pointer velocity supplies inertia; total path length never sets a duration. */
    fun resistance(distance: Float, range: Float): Float = distance / (range + kotlin.math.abs(distance))
    fun resistanceVelocity(distance: Float, velocity: Float, range: Float): Float {
        val denominator = range + kotlin.math.abs(distance)
        return velocity * range / (denominator * denominator)
    }
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
        if (!nativeWindow) {
            val t = progress.coerceIn(0f, 1f)
            return 7.2f * t * t * (1f - t) * (1f - t)
        }
        if (nativeWindow && progress < .5f) {
            val intake = ((progress - .15f) / .35f).coerceIn(0f, 1f)
            return -3.2f * intake * intake * (1f - intake) * (1f - intake)
        }
        val start = if (nativeWindow) .5f else 0f
        val span = if (nativeWindow) .4f else .8f
        val t = ((progress - start) / span).coerceIn(0f, 1f)
        val tail = 1f - t
        // Early kick, one peak, cubic release. The symmetric bell postponed the
        // response and could remain visibly at the width cap for several frames.
        return 5.5f * t * tail * tail * tail
    }
    fun catchDerivative(progress: Float, nativeWindow: Boolean = true): Float {
        if (!nativeWindow) {
            val t = progress.coerceIn(0f, 1f)
            return 14.4f * t * (1f - t) * (1f - 2f * t)
        }
        if (nativeWindow && progress > .15f && progress < .5f) {
            val intake = (progress - .15f) / .35f
            return -6.4f * intake * (1f - intake) * (1f - 2f * intake) / .35f
        }
        val start = if (nativeWindow) .5f else 0f
        val span = if (nativeWindow) .4f else .8f
        if (progress < start || progress >= start + span) return 0f
        val t = (progress - start) / span
        return 5.5f * (1f - t) * (1f - t) * (1f - 4f * t) / span
    }
    const val CATCH_PEAK = .580078125f
    fun boundedCatchWeight(width: Float, maximum: Float, requested: Float, nativeWindow: Boolean = true): Float =
        minOf(requested, (maximum - width).coerceAtLeast(0f) /
            (width.coerceAtLeast(1f) * if (nativeWindow) CATCH_PEAK else .45f)).coerceAtLeast(0f)
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
        fun setVelocity(physicalVelocity: Float) { velocity = if (physicalVelocity.isFinite()) physicalVelocity else 0f }

        fun retarget(current: Float, destination: Float, seconds: Float, carry: Boolean, elastic: Boolean = false) {
            value = current
            target = destination
            if (!carry) velocity = 0f
            velocityAtStart = velocity
            omega = (if (elastic) 12.5 else 10.5) / seconds.coerceIn(.1f, 1f)
            this.elastic = elastic
            displacement = (current - destination).toDouble()
            impulse = velocity + omega * displacement
        }

        fun sample(seconds: Float, minimum: Float = -Float.MAX_VALUE, maximum: Float = Float.MAX_VALUE): Float {
            val time = seconds.coerceAtLeast(0f).toDouble()
            if (elastic) {
                val damping = .60 * omega
                val frequency = omega * .8
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
