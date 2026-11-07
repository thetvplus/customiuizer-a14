package com.android.systemui.miui.volume

import android.content.Context
import android.view.View
import android.widget.FrameLayout

/** Native footer setter writes the mode group and the separate more button. */
class MiuiVolumeDialogView(context: Context) : FrameLayout(context) {
    @JvmField val mRingerModeLayout=MiuiRingerModeLayout(context)
    val more=View(context)
    fun updateFooterVisibility(show: Boolean) {
        mRingerModeLayout.visibility=if(show) VISIBLE else GONE
        more.visibility=if(show) VISIBLE else GONE
    }
}
