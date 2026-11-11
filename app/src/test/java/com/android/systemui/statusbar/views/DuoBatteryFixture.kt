package com.android.systemui.statusbar.views

@Suppress("UNUSED_PARAMETER")
class MiuiBatteryMeterView : android.widget.FrameLayout(org.robolectric.RuntimeEnvironment.getApplication()) {
    @JvmField var paddingFailure: Throwable? = null
    override fun setPadding(left: Int, top: Int, right: Int, bottom: Int) {
        val failure = paddingFailure
        paddingFailure = null
        if (failure != null) throw failure
        super.setPadding(left, top, right, bottom)
    }
    @JvmField var mBatteryTextDigitView: android.widget.TextView? = null
    @JvmField var mBatteryPercentView: Any? = null
    public override fun onAttachedToWindow() {}
    public override fun onDetachedFromWindow() {}
    fun updateAll() {}
    fun onDarkChanged(areas: java.util.ArrayList<android.graphics.Rect>?, intensity: Float, tint: Int, light: Int, dark: Int, useTint: Boolean) {}
}
