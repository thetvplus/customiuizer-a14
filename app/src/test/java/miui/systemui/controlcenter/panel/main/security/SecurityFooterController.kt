package miui.systemui.controlcenter.panel.main.security

import miui.systemui.controlcenter.panel.main.MainPanelContent

class SecurityFooterController(override var available: Boolean = true) : MainPanelContent {
    override val priority = 201
}
