package com.android.systemui.miui.volume

import android.content.Context
import android.view.View
import android.widget.LinearLayout

/** Native footer setter writes the mode group and the separate more button. */
class MiuiVolumeDialogView(context: Context) : LinearLayout(context) {
    @JvmField val mRingerModeLayout=MiuiRingerModeLayout(context)
    val more=View(context)
    var expanded = false
    fun isExpanded() = expanded
    fun updateFooterVisibility(show: Boolean) {
        mRingerModeLayout.visibility=if(show) VISIBLE else GONE
        more.visibility=if(show) VISIBLE else GONE
    }
}
