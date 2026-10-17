package tv.withaibuild.customiuizer.mods.duostatusbar

/** Connection indicators, not a claim about the current media route. No device list or Binder calls. */
internal class DuoAudioState {
    private var wired = false
    private var bluetoothAudio = false
    private var batteryStep = 0
    private var wiredExpiresAt = 0L
    private var bluetoothExpiresAt = 0L
    @Volatile var snapshot = 0
        private set
    @Volatile var expiresAt = 0L
        private set

    @Synchronized fun wired(connected: Boolean, nowMillis: Long, notify: Boolean): Boolean {
        if (connected && !wired && notify) wiredExpiresAt = nowMillis + CONNECTION_HINT_MS
        if (!connected) wiredExpiresAt = 0L
        wired = connected
        expireHints(nowMillis)
        return publish()
    }

    @Synchronized fun bluetooth(enabled: Boolean, connected: Boolean, audioOnly: Boolean, active: Boolean,
        nowMillis: Long, notify: Boolean,
    ): Boolean {
        val next = enabled && connected && (audioOnly || active)
        if (next != bluetoothAudio) batteryStep = 0 // never carry an old device's battery into a new hint
        if (next && !bluetoothAudio && notify) bluetoothExpiresAt = nowMillis + CONNECTION_HINT_MS
        if (!next) bluetoothExpiresAt = 0L
        bluetoothAudio = next
        expireHints(nowMillis)
        return publish()
    }

    @Synchronized fun expire(nowMillis: Long): Boolean {
        if (expiresAt == 0L || nowMillis < expiresAt) return false
        expireHints(nowMillis)
        return publish()
    }

    @Synchronized fun finishHint(): Boolean {
        wiredExpiresAt = 0L
        bluetoothExpiresAt = 0L
        batteryStep = 0
        return publish()
    }

    /** HyperOS reports hands-free battery in ten steps; absent/ambiguous reports stay unknown. */
    @Synchronized fun bluetoothBattery(step: Int, singleDevice: Boolean): Boolean {
        batteryStep = if (bluetoothAudio && bluetoothExpiresAt != 0L && singleDevice && step in 1..10) step else 0
        return publish()
    }

    @Synchronized fun clear(): Boolean {
        wired = false
        bluetoothAudio = false
        batteryStep = 0
        wiredExpiresAt = 0L
        bluetoothExpiresAt = 0L
        return publish()
    }

    private fun expireHints(nowMillis: Long) {
        if (wiredExpiresAt <= nowMillis) wiredExpiresAt = 0L
        if (bluetoothExpiresAt <= nowMillis) {
            bluetoothExpiresAt = 0L
            batteryStep = 0
        }
    }

    private fun publish(): Boolean {
        expiresAt = when {
            wiredExpiresAt == 0L -> bluetoothExpiresAt
            bluetoothExpiresAt == 0L -> wiredExpiresAt
            else -> minOf(wiredExpiresAt, bluetoothExpiresAt)
        }
        val next = (if (wiredExpiresAt != 0L) WIRED else 0) or
            (if (bluetoothExpiresAt != 0L) BLUETOOTH or (batteryStep shl 2) else 0)
        if (next == snapshot) return false
        snapshot = next
        return true
    }

    companion object {
        const val WIRED = 1
        const val BLUETOOTH = 2
        const val CONNECTION_HINT_MS = 3000L
        const val WIFI_GLYPH = 0
        const val PLANE_GLYPH = 1
        const val WIRED_GLYPH = 2
        const val BLUETOOTH_GLYPH = 3
        const val CELLULAR_GLYPH = 4
        const val OFFLINE_GLYPH = 5
        fun batteryStep(audio: Int) = (audio shr 2) and 15

        fun glyph(network: Int, audio: Int, networkFallback: Boolean = true): Int = when {
            network and DuoNetworkState.WIFI_UNVALIDATED != 0 -> WIFI_GLYPH
            network and DuoNetworkState.WIFI_CONNECTED == 0 &&
                network and DuoNetworkState.AIRPLANE != 0 -> PLANE_GLYPH
            audio and WIRED != 0 -> WIRED_GLYPH
            audio and BLUETOOTH != 0 -> BLUETOOTH_GLYPH
            network and DuoNetworkState.WIFI_CONNECTED == 0 && networkFallback ->
                if (network and DuoNetworkState.MOBILE_SERVICE != 0) CELLULAR_GLYPH else OFFLINE_GLYPH
            else -> WIFI_GLYPH
        }
    }
}
