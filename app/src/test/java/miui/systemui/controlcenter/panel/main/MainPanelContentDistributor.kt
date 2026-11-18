package miui.systemui.controlcenter.panel.main

import miui.systemui.controlcenter.panel.main.recyclerview.MainPanelAdapter

interface MainPanelContent {
    val priority: Int
    val onRight: Boolean get() = true
    val available: Boolean get() = true
}

open class PanelContentLifecycle {
    @JvmField var inited = false
}

class MainPanelContentDistributor(controllers: List<MainPanelContent>) : PanelContentLifecycle() {
    @JvmField val childControllers = ArrayList(controllers.sortedBy { it.priority })
    @JvmField val leftPanelContent = ArrayList<MainPanelContent>()
    @JvmField val rightPanelContent = ArrayList<MainPanelContent>()
    @JvmField val leftAdapter = MainPanelAdapter()
    @JvmField val rightAdapter = MainPanelAdapter()

    fun distributePanels(horizontal: Boolean) {
        if (!inited) return
        leftPanelContent.clear()
        rightPanelContent.clear()
        for (controller in childControllers) {
            if (!controller.available) continue
            val panel = if (horizontal && !controller.onRight) leftPanelContent else rightPanelContent
            panel.add(controller)
        }
    }
}
