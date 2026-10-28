package com.android.deskclock.timer

class Timer(var state: Int = STATE_RUNNING, var deadline: Long = System.currentTimeMillis() + 60_000,
    var remain: Long = 30_000) {
    companion object {
        @JvmField val STATE_RUNNING = 1
        @JvmField val STATE_PAUSE = 2
    }
    fun getTime() = deadline
}
