package miui.systemui.controlcenter.panel.main.qs

import miui.systemui.controlcenter.panel.main.MainPanelContent

class QSListController : MainPanelContent {
    override val priority = 100

    class EditModeDividerTextItem {
        fun getSpanSize() = 4
    }
}

class EditButtonController : MainPanelContent {
    override val priority = 200
    fun getSpanSize() = 4
}
