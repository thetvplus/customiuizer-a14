package com.android.systemui.miui

import android.view.ViewGroup

/** External native boundary: expand/rotate writes fixed resource dimensions before traversal. */
class ViewStateGroup(private val nativeApply: (ViewGroup) -> Unit) {
    fun apply(root: ViewGroup) = nativeApply(root)
}
