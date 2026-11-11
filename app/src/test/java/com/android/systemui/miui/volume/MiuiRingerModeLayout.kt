package com.android.systemui.miui.volume

import android.content.Context
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout

/** Selected native helper type; not a ROM implementation. */
class MiuiRingerModeLayout(context: Context) : LinearLayout(context) {
    class RingerButtonHelper(layout: View, root: View, @JvmField val mIsZen: Boolean) {
        @JvmField var mState=false
        @JvmField val mStandardView = root
        @JvmField val mIcon = ImageView(layout.context)
        fun updateState() = Unit
    }
}
