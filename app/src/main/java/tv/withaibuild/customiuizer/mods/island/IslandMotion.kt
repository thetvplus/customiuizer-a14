package tv.withaibuild.customiuizer.mods.island

import android.animation.TimeInterpolator
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/** Damped step response: prompt travel followed by one small, decaying overshoot. */
internal object IslandMotion {
    // Subpixel filter increments on ordinary phone densities, prepared once.
    const val CONTENT_BLUR_STEPS = 64
    const val CONTENT_BLUR_RADIUS = 16.666f
    fun contentBlurLevel(reveal: Float): Int = if(reveal >= .999f) -1 else
        (kotlin.math.ceil((1f-reveal)*CONTENT_BLUR_STEPS).toInt()-1).coerceIn(0,CONTENT_BLUR_STEPS-1)
    fun contentBlurRadius(level: Int,density: Float): Float =
        if(level<0) 0f else CONTENT_BLUR_RADIUS*(level+1)/CONTENT_BLUR_STEPS*density

    const val ENTER = 403L
    const val MORPH = 384L
    const val REBOUND = 384L
    const val EXIT = 307L
    const val SECONDARY = 422L
    const val CONTENT = 230L
    const val CONTENT_ARRIVAL = 403L
    const val ALBUM = 365L
    /** 22G100 ordinary Compact/Custom stored damping. A14 retains its finite
     * clock/omega mapping; this is not Apple's response/behaviorType conversion. */
    const val FORM_DAMPING = .816
    /** 22G100 stored profiles. Period conversion and state assignment are an
     * explicit opt-in experiment; response is never used as a playback duration. */
    data class Profile(val response: Double, val damping: Double) {
        val omega = 2.0 * kotlin.math.PI / response
        fun horizon(distance: Float, velocity: Float, pixel: Double = .25, speed: Double = 2.0): Long {
            if (damping == 1.0) {
                val d = kotlin.math.abs(distance.toDouble())
                val b = kotlin.math.abs(velocity + omega * distance)
                // Critical damping has a polynomial tail, not an oscillatory
                // envelope divided by sqrt(1-zeta²). Prepare only when retargeting.
                for (ms in 160L..2000L step 16) {
                    val t = ms / 1000.0
                    val decay = exp(-omega * t)
                    if ((d + b * t) * decay <= pixel &&
                        (b + omega * (d + b * t)) * decay <= speed) return ms
                }
                return 2000L
            }
            val a = damping * omega
            val w = omega * sqrt(1.0 - damping * damping)
            val b = (velocity + a * distance) / w
            val radius = sqrt(distance.toDouble() * distance + b * b)
            val envelope = maxOf(radius / pixel, omega * radius / speed, 1.0)
            return (kotlin.math.ln(envelope) / a * 1000).toLong().coerceIn(160L, 2000L)
        }
    }
    val IOS_BOUNDS = Profile(.512, .816)
    val IOS_IDLE = Profile(.512, .6)
    val IOS_COLLAPSE = Profile(.45, .816)
    val IOS_CONTENT = Profile(.6, .816)
    val IOS_SLOTS = Profile(.512, .816)
    val IOS_SNAPSHOT = Profile(.3, .816)
    /** 22G100 content-interaction begin; release keeps the existing A14 recoil. */
    val IOS_PRESS = Profile(.85, .694)
    const val MEDIA_DELAY = 750L
    const val RETURN_TIMEOUT = 1600L
    const val ABSORPTION_TIME_SCALE = 1.2f
    const val ABSORPTION_BODY = 567L
    const val ABSORPTION_LANDING_WIDTH_RATIO = .62f
    const val ABSORPTION_LANDING_HEIGHT_RATIO = .44f
    const val DRAG_STRETCH = .08f
    const val DRAG_SQUEEZE = .04f
    const val PAUSE_LIFETIME = 2000L
    const val LOADING_LIFETIME = 30000L
    const val MEDIA_SWITCH_LIFETIME = 8000L
    const val NATIVE_CARD_GAP_LIFETIME = 30000L
    const val EXPANDED_DWELL = 5000L
    /** One narrow response range for an accepted Home return. Native spring geometry,
     * damping and initial velocity remain authoritative; distance never sets the tempo. */
    fun returnResponse(strength: Float): Float =
        (.35f - .006f * strength.coerceIn(0f, 1f)) * ABSORPTION_TIME_SCALE * .96f
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
    private const val CONTENT_CATCH_PEAK = .45f
    fun boundedCatchWeight(width: Float, maximum: Float, requested: Float, nativeWindow: Boolean = true): Float =
        minOf(requested, (maximum - width).coerceAtLeast(0f) /
            (width.coerceAtLeast(1f) * if (nativeWindow) CATCH_PEAK else CONTENT_CATCH_PEAK)).coerceAtLeast(0f)
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

    private const val DECAY = 7.04
    private const val FREQUENCY = 5.28
    private const val PHASE = 4.0 / 3.0
    private val finalResponse = response(1.0)

    private fun response(t: Double): Double =
        1.0 - exp(-DECAY * t) * (cos(FREQUENCY * t) + PHASE * sin(FREQUENCY * t))

    fun progress(input: Float): Float {
        return (response(input.coerceIn(0f, 1f).toDouble()) / finalResponse).toFloat()
    }

    /** Analytic damping in physical pixels. The animator is only a
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
        private var dampingRatio = 1.0
        private var frequencyRatio = 0.0

        fun addVelocity(physicalVelocity: Float) { velocity += physicalVelocity }
        fun setVelocity(physicalVelocity: Float) { velocity = if (physicalVelocity.isFinite()) physicalVelocity else 0f }

        fun retarget(current: Float, destination: Float, seconds: Float, carry: Boolean, elastic: Boolean = false,
            dampingRatio: Double = 1.0) {
            value = current
            target = destination
            if (!carry) velocity = 0f
            velocityAtStart = velocity
            omega = 10.5 / seconds.coerceIn(.1f, 1f)
            this.dampingRatio = if (elastic) .60328 else dampingRatio.coerceIn(.5, 1.0)
            this.elastic = elastic || this.dampingRatio < 1.0
            // Retain the established gesture coefficient exactly. Prepare the
            // ordinary bounds coefficient once, rather than taking sqrt per frame.
            frequencyRatio = if (elastic) .79753 else sqrt(1.0 - this.dampingRatio * this.dampingRatio)
            displacement = (current - destination).toDouble()
            impulse = velocity + omega * displacement
        }

        fun retarget(current: Float, destination: Float, profile: Profile, carry: Boolean) {
            retarget(current, destination, .4f, carry, dampingRatio = profile.damping)
            dampingRatio = profile.damping.coerceIn(.01, 1.0)
            elastic = dampingRatio < 1.0
            frequencyRatio = sqrt(1.0 - dampingRatio * dampingRatio)
            omega = profile.omega
            impulse = velocity + omega * displacement
        }

        fun sample(seconds: Float, minimum: Float = -Float.MAX_VALUE, maximum: Float = Float.MAX_VALUE): Float {
            val time = seconds.coerceAtLeast(0f).toDouble()
            if (elastic) {
                val damping = dampingRatio * omega
                val frequency = omega * frequencyRatio
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
