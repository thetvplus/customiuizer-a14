package com.android.systemui.statusbar.policy

import android.content.Context
import android.os.Handler
import android.os.Looper

class NetworkSpeedController(val context: Context) {
    @JvmField val mBgHandler = Handler(Looper.getMainLooper())
}
