package com.android.systemui.miui.volume

import android.content.Context
import android.widget.FrameLayout

/** Selected native helper type; not a ROM implementation. */
class MiuiRingerModeLayout(context: Context) : FrameLayout(context) {
    class RingerButtonHelper {
        @JvmField var mState=false
        fun updateState() = Unit
    }
}
