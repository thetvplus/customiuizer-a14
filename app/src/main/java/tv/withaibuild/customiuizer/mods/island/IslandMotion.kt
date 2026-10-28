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
    const val MEDIA_RETURN = 540L
    const val RETURN_TIMEOUT = 1600L
    const val ABSORPTION_TIME_SCALE = 1.5f
    const val PAUSE_LIFETIME = 2000L
    const val LOADING_LIFETIME = 30000L
    const val NATIVE_CARD_GAP_LIFETIME = 30000L
    const val EXPANDED_DWELL = 5000L
    const val LANDING_WIDTH_RATIO = .84f
    const val LANDING_HEIGHT_RATIO = .86f
    /** One narrow response range for an accepted Home return. Native spring geometry,
     * damping and initial velocity remain authoritative; distance never sets the tempo. */
    fun returnDuration(strength: Float, absorption: Boolean): Long =
        ((MEDIA_RETURN - 12f * strength.coerceIn(0f, 1f)) * if (absorption) ABSORPTION_TIME_SCALE else 1f).toLong()
    fun returnResponse(strength: Float, absorption: Boolean): Float =
        (.35f - .006f * strength.coerceIn(0f, 1f)) * if (absorption) ABSORPTION_TIME_SCALE else 1f
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
    /** Spatially coordinate the incoming form with the real page tail, then let
     * the existing finite clock settle a physical spring after the 90% peak.
     * Page completion cannot truncate this recoil. No extra animator or polling. */
    class AbsorptionResponse(private val from: Float, private val target: Float,
        private val minimum: Float, private val maximum: Float, amplitude: Float,
        initialVelocity: Float, seconds: Float, private val initialProgress: Float = 0f) {
        private val amplitude = amplitude.coerceIn(0f, (maximum - target).coerceAtLeast(0f))
        private val slope = initialVelocity * seconds * .55f
        private val late = initialProgress >= .9f
        private val baseEnd = if (initialProgress < .55f) .55f
            else initialProgress + (.9f - initialProgress) * .65f
        private val spring = Spring().apply {
            setVelocity(if (late) initialVelocity else 0f)
            retarget(if (late) from else 1f, if (late) target else 0f,
                (if (late) CONTENT_ARRIVAL else REBOUND) / 1000f, late, elastic = !late)
        }
        private var peakAt = Float.NaN
        private var lastTime = 0f
        private var lastProgress = initialProgress
        var value = from
            private set
        var velocity = initialVelocity
            private set

        fun sample(progress: Float, elapsed: Float): Float {
            val time = maxOf(lastTime, elapsed)
            val p = progress.coerceIn(initialProgress, 1f)
            if (!late && peakAt.isNaN() && p >= .9f) {
                val fraction = if (p > lastProgress) ((.9f - lastProgress) / (p - lastProgress)).coerceIn(0f, 1f) else 1f
                peakAt = lastTime + (time - lastTime) * fraction
            }
            val previous = value
            value = if (late) spring.sample(time, minimum, maximum) else if (peakAt.isFinite()) {
                target + amplitude * spring.sample(time - peakAt)
            } else {
                val t = ((p - initialProgress) / (baseEnd - initialProgress).coerceAtLeast(.01f)).coerceIn(0f, 1f)
                val t2 = t * t
                val base = from + (target - from) * t2 * (3f - 2f * t) + slope * t * (1f - t) * (1f - t)
                val contact = maxOf(.55f, initialProgress)
                val catch = ((p - contact) / (.9f - contact).coerceAtLeast(.01f)).coerceIn(0f, 1f)
                base + amplitude * catch * catch * (3f - 2f * catch)
            }
            velocity = if (late) spring.velocity else if (peakAt.isFinite()) amplitude * spring.velocity
                else if (time > lastTime) (value - previous) / (time - lastTime) else velocity
            if (settled(time)) { value = target; velocity = 0f }
            if (value < minimum) { value = minimum; velocity = maxOf(0f, velocity) }
            if (value > maximum) { value = maximum; velocity = minOf(0f, velocity) }
            lastTime = time; lastProgress = p
            return value
        }
        fun settled(time: Float) = if (late) time >= CONTENT_ARRIVAL / 1000f
            else peakAt.isFinite() && time - peakAt >= REBOUND / 1000f
    }

    /** Compression approaches the physical height limit without a flat clamp. */
    fun compressedScale(origin: Float, delta: Float, minimum: Float): Float {
        if (delta >= 0f) return origin + delta
        val room = (origin - minimum).coerceAtLeast(0f)
        return if (room == 0f) origin else origin + room * delta / (room - delta)
    }
    fun compressionOrigin(current: Float, delta: Float, minimum: Float): Float {
        if (delta >= 0f) return current - delta
        val height = (current - minimum).coerceAtLeast(0f)
        return if (height == 0f) current else minimum +
            (height + kotlin.math.sqrt(height * height - 4f * height * delta)) * .5f
    }
    fun compressionVelocity(origin: Float, delta: Float, minimum: Float): Float {
        if (delta >= 0f) return 1f
        val room = (origin - minimum).coerceAtLeast(0f)
        val ratio = if (room > 0f) room / (room - delta) else 0f
        return ratio * ratio
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
