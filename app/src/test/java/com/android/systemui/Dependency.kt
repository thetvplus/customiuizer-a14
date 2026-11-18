package com.android.systemui

import com.android.systemui.controlcenter.phone.ControlPanelWindowManager

object Dependency {
    var controlPanelWindowManager: ControlPanelWindowManager? = null

    @JvmStatic fun get(type: Class<*>): Any? = controlPanelWindowManager?.takeIf(type::isInstance)
}
