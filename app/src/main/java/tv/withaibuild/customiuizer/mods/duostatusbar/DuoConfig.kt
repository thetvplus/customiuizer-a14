package tv.withaibuild.customiuizer.mods.duostatusbar

import tv.withaibuild.customiuizer.utils.PrefMap

/** Read once at installation; changing these settings requires a SystemUI restart. */
internal class DuoConfig(val showPercent: Boolean, sizeDp: Int,
    val autoSize: Boolean = true, val showAudio: Boolean = true, val bold: Boolean = false,
    verticalOffset: Int = 8, headphoneStyle: Int = 1,
    headphoneScale: Int = 100, val networkFallback: Boolean = true,
    val bluetoothBatteryColor: Boolean = true, cellularStyle: Int = 1,
) {
    val sizeDp = sizeDp.coerceIn(20, 40)
    val offsetDp = (verticalOffset.coerceIn(0, 16) - 8) / 2f
    val headphoneStyle = headphoneStyle.coerceIn(1, 3)
    val headphoneScale = headphoneScale.coerceIn(85, 115) / 100f
    val cellularStyle = cellularStyle.coerceIn(1, 2)

    companion object {
        fun read(prefs: PrefMap) = DuoConfig(
            prefs.getBoolean("system_statusbar_duo_percent", true),
            prefs.getInt("system_statusbar_duo_size", 24),
            prefs.getBoolean("system_statusbar_duo_autosize", true),
            prefs.getBoolean("system_statusbar_duo_audio", true),
            prefs.getBoolean("system_statusbar_duo_bold", false),
            prefs.getInt("system_statusbar_duo_verticaloffset", 8),
            prefs.getStringAsInt("system_statusbar_duo_headphonestyle", 1),
            prefs.getInt("system_statusbar_duo_headphonescale", 100),
            prefs.getBoolean("system_statusbar_duo_networkfallback", true),
            prefs.getBoolean("system_statusbar_duo_btbattery", true),
            prefs.getStringAsInt("system_statusbar_duo_cellularstyle", 1),
        )
    }
}

internal object DuoSizing {
    /** A little room for the ring, bounded by the native row during measurement. */
    fun pixels(config: DuoConfig, nativeHeight: Int, density: Float): Int {
        val dp = if (!config.autoSize) config.sizeDp.toFloat()
            else if (nativeHeight > 0) (nativeHeight / density * 1.14345f).coerceIn(19.8f, 23.76f)
            else 22.869f
        return (dp * density + 0.5f).toInt().coerceAtLeast(1)
    }

    /** Reserve space for the requested shift inside the existing row, including double rows. */
    fun offset(height: Int, requested: Float): Float {
        val limit = height.coerceAtLeast(0) / 8f
        return requested.coerceIn(-limit, limit)
    }

    fun drawingSize(width: Int, height: Int, offset: Float): Float =
        minOf(width.toFloat(), (height - 2f * kotlin.math.abs(offset)).coerceAtLeast(0f))

    /** Use the actual source row, not the smaller stock icon resource. */
    fun sharedHeight(height: Int, sourceHeight: Int): Int =
        if (sourceHeight > 0) minOf(height, sourceHeight) else height

    /** Header rows need enough space for the source drawing and its bounded vertical shift. */
    fun requiredHeight(width: Int, sourceHeight: Int, requestedOffset: Float): Int {
        val offset = offset(sourceHeight, requestedOffset)
        val shift = kotlin.math.abs(offset)
        return kotlin.math.ceil(maxOf(drawingSize(width, sourceHeight, offset) +
            2f * shift, 8f * shift)).toInt()
    }
}

internal object DuoGeometry {
    const val RING_START = 148.7f
    const val BOTTOM_GAP = 117.4f

    fun topGap(showPercent: Boolean, charging: Boolean): Float = when {
        charging -> 55.6f
        showPercent -> 71.3f
        else -> 0f
    }

    fun sideSweep(topGap: Float) = (360f - BOTTOM_GAP - topGap) / 2f
    fun continuousFill(level: Int) = (360f - BOTTOM_GAP) * level.coerceIn(0, 100) / 100f
    fun leftFill(level: Int, side: Float) = side * level.coerceIn(0, 50) / 50f
    fun rightFill(level: Int, side: Float) = side * (level - 50).coerceIn(0, 50) / 50f

    fun batteryColor(foreground: Int, level: Int, charging: Boolean, saver: Boolean): Int = withAlpha(when {
        charging -> 0xff34c759.toInt()
        saver -> 0xfff2b900.toInt()
        level in 0..19 -> 0xffff3b30.toInt()
        else -> foreground
    }, foreground ushr 24)

    fun withAlpha(color: Int, alpha: Int) = (color and 0x00ffffff) or (alpha.coerceIn(0, 255) shl 24)
    fun dim(color: Int) = withAlpha(color, (color ushr 24) * 56 / 255)
    fun signalColor(foreground: Int, network: Int, dot: Int): Int {
        val alpha = foreground ushr 24
        val track = alpha * 56 / 255
        return withAlpha(foreground, track + (alpha - track) * DuoNetworkState.dotCoverage(network, dot) / 255)
    }
    fun bluetoothColor(foreground: Int, batteryStep: Int): Int {
        val lightIcons = ((foreground shr 16 and 255) * 299 +
            (foreground shr 8 and 255) * 587 + (foreground and 255) * 114) >= 128000
        val color = when (batteryStep) {
            in 1..2 -> if (lightIcons) 0xffff6961.toInt() else 0xffcc2924.toInt()
            in 3..5 -> if (lightIcons) 0xffffc857.toInt() else 0xff986400.toInt()
            else -> foreground // Unknown/healthy battery follows the native status bar tint.
        }
        return withAlpha(color, foreground ushr 24)
    }
}
