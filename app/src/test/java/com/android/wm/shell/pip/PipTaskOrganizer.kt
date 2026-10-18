package com.android.wm.shell.pip

import android.app.ActivityManager
import android.content.Context
import android.view.SurfaceControl

class PipTaskOrganizer(@JvmField val mContext: Context) {
    fun onTaskAppeared(info: ActivityManager.RunningTaskInfo?, leash: SurfaceControl?) = Unit
}
