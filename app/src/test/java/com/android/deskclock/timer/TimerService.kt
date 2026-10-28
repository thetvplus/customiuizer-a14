package com.android.deskclock.timer

import android.app.Service
import android.content.Intent
import android.os.IBinder

class TimerService : Service() {
    @JvmField var mTimer: Timer? = Timer()
    override fun onBind(intent: Intent?): IBinder? = null
}
