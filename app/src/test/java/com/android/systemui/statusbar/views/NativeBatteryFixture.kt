package com.android.systemui.statusbar.views

import android.graphics.Rect
import android.widget.ImageView
import org.robolectric.RuntimeEnvironment

/** Minimal retained native ABI; not a ROM implementation or compatibility proof. */
open class MiuiBatteryMeterIconView : ImageView(RuntimeEnvironment.getApplication()) {
    @JvmField var mLevel = 50
    @JvmField var mBatteryStyle = 0
    @JvmField var mUseLegacyDrawable = false
    @JvmField var mPerformanceMode = false
    @JvmField var mQuickCharging = false
    @JvmField var mCharging = false
    @JvmField var mPowerSave = false
    @JvmField var mLow = false
    @JvmField var mUseTint = true
    @JvmField var mTintColor = -1
    @JvmField var mLightColor = -1
    @JvmField var mDarkColor = -16777216
    @JvmField var mDarkIntensity = 0f
    @JvmField var mTintAreas = ArrayList<Rect>()
    @JvmField var mBatteryLowColor = -65536
    @JvmField var mBatteryPowerSaveColor = -256
    @JvmField var mBatteryChargingColor = -16711936
    fun onDarkChangeInternal() = Unit
    public override fun onDraw(canvas: android.graphics.Canvas) = Unit
}
