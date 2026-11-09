package com.android.systemui.statusbar.views

/** Native measurement owner identity for shared spacing installation tests. */
class MiuiStatusBatteryContainer : android.widget.FrameLayout(org.robolectric.RuntimeEnvironment.getApplication()) {
    public override fun onMeasure(width: Int, height: Int) = super.onMeasure(width, height)
}
