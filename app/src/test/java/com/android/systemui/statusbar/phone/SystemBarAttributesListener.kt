package com.android.systemui.statusbar.phone

class SystemBarAttributesListener {
    class State { @JvmField var mIsFullscreen = false }
    @JvmField val statusBarStateController = State()
    fun onSystemBarAttributesChanged() = Unit
}
