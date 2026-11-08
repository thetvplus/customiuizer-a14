package com.android.systemui.plugins

import android.graphics.Rect
import android.view.View

class DarkIconDispatcher {
    companion object {
        var failure: Throwable? = null
        var resolves = 0
        @JvmStatic fun getTint(areas: ArrayList<Rect>, view: View, color: Int): Int {
            resolves++
            failure?.let { throw it }
            return color
        }
        @JvmStatic fun isInAreas(areas: ArrayList<Rect>, view: View) = true
    }
}
