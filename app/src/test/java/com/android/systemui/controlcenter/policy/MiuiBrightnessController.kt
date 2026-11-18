package com.android.systemui.controlcenter.policy

import android.content.Context
import com.android.systemui.plugins.miui.controlcenter.ToggleSliderBase

object BrightnessUtils {
    const val GAMMA_SPACE_MAX = 1023
}

@Suppress("UNUSED_PARAMETER")
class MiuiBrightnessController(@JvmField val mContext: Context) {
    fun onStart(value: Int) = Unit
    fun onChanged(slider: ToggleSliderBase, tracking: Boolean, automatic: Boolean, value: Int, stop: Boolean) = Unit
    fun onStop(value: Int) = Unit
}
