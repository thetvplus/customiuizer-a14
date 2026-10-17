package tv.withaibuild.customiuizer.mods.duostatusbar

/** Two primitive states, no queue. Rapid changes share the current deadline instead of extending it. */
internal class DuoTransition {
    var current = -1
        private set
    var previous = -1
        private set
    private var startedAt = 0L
    private var duration = 0L

    fun change(next: Int, now: Long, durationMs: Long): Boolean {
        if (next == current) return false
        val progress = fraction(now)
        previous = if (progress >= 0.5f) current else previous
        current = next
        if (previous < 0 || durationMs <= 0L) finish()
        else if (progress >= 1f) { startedAt = now; duration = durationMs.coerceAtMost(360L) }
        return true
    }

    fun fraction(now: Long): Float {
        if (duration == 0L) return 1f
        val fraction = ((now - startedAt).toFloat() / duration).coerceIn(0f, 1f)
        if (fraction >= 1f) finish()
        return fraction
    }

    fun finish() { previous = current; duration = 0L }

    /** Fade through one glyph at a fixed size; never superimpose two silhouettes. */
    fun drawKey(progress: Float) = if (progress < 0.5f) previous else current
    fun drawAlpha(progress: Float): Int {
        val part = kotlin.math.abs(2f * progress.coerceIn(0f, 1f) - 1f)
        return (part * part * (3f - 2f * part) * 255f + 0.5f).toInt()
    }
}

/** Only center changes animate. Phone battery and the lower cellular dots remain continuously visible. */
internal object DuoCenter {
    const val WARNING = 128
    fun key(network: Int, audio: Int, config: DuoConfig): Int {
        val glyph = DuoAudioState.glyph(network, if (config.showAudio) audio else 0, config.networkFallback)
        val value = when (glyph) {
            DuoAudioState.WIFI_GLYPH -> DuoNetworkState.wifiLevel(network)
            DuoAudioState.BLUETOOTH_GLYPH -> if (config.bluetoothBatteryColor) DuoAudioState.batteryStep(audio) else 0
            DuoAudioState.CELLULAR_GLYPH -> if (config.cellularStyle == 2) DuoNetworkState.cellularType(network) else 0
            else -> 0
        }
        return glyph or (value shl 3) or (if (network and DuoNetworkState.WIFI_UNVALIDATED != 0) WARNING else 0)
    }
    fun glyph(key: Int) = key and 7
    fun value(key: Int) = (key shr 3) and 15
}
