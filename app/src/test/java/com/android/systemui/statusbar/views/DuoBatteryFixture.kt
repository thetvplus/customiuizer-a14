package com.android.systemui.statusbar.views

@Suppress("UNUSED_PARAMETER")
class MiuiBatteryMeterView {
    @JvmField var mBatteryPercentView: Any? = null
    fun onAttachedToWindow() {}
    fun onDetachedFromWindow() {}
    fun updateAll() {}
    fun onDarkChanged(areas: Any?, intensity: Float, tint: Int, light: Int, dark: Int, useTint: Boolean) {}
}
