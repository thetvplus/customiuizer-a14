package com.miui.systemui.functions

import android.content.ComponentName

class MiuiTopActivityObserver {
    @JvmField var mTopActivity: ComponentName? = null
    fun updateTopActivity() = Unit
}
