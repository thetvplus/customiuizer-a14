package miui.systemui.controlcenter.panel.main

import android.content.Context
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.LinearLayout
import miui.systemui.util.CommonUtils

class MainPanelController(context: Context) {
    @JvmField var useSeparatedPanels: Boolean? = null
    @JvmField var panelWidth = 120
    @JvmField var panelMargin = 0
    @JvmField val horizontalMainPanel = PanelStack(context)
    @JvmField val leftMainPanel = LinearLayout(context)
    @JvmField val rightMainPanel = LinearLayout(context)
    var configuredMargin = 24

    init {
        leftMainPanel.layoutParams = LinearLayout.LayoutParams(panelWidth, MATCH_PARENT)
        horizontalMainPanel.addView(rightMainPanel, LinearLayout.LayoutParams(panelWidth, MATCH_PARENT))
    }

    fun setUseSeparatedPanels(value: Boolean?) {
        if (value == null || value == useSeparatedPanels) return
        useSeparatedPanels = value
        if (value) {
            horizontalMainPanel.addView(leftMainPanel, 0)
            panelMargin = configuredMargin
            (leftMainPanel.layoutParams as LinearLayout.LayoutParams).marginEnd = panelMargin
        } else {
            horizontalMainPanel.removeView(leftMainPanel)
        }
    }

    fun updatePanelSize() {
        for (panel in listOf(leftMainPanel, rightMainPanel)) {
            panel.layoutParams.width = panelWidth
            panel.layoutParams.height = MATCH_PARENT
        }
    }

    class PanelStack(context: Context) : LinearLayout(context) {
        var removals = 0
            private set

        override fun onViewRemoved(child: View) {
            super.onViewRemoved(child)
            removals++
        }
    }
}

class MainPanelAnimController(private val context: Context) {
    @JvmField val leftMainPanel = View(context)
    @JvmField val rightMainPanel = View(context)
    var panelAlpha = 1f

    fun getContext() = context
    fun onAnimUpdate() {
        if (CommonUtils.getInVerticalMode(context)) rightMainPanel.alpha = panelAlpha
    }
    fun updateVisibility(visibility: Int) { rightMainPanel.visibility = visibility }
    fun forceToShow(@Suppress("UNUSED_PARAMETER") source: Any) { rightMainPanel.alpha = 1f }
    fun onConfigurationChanged(@Suppress("UNUSED_PARAMETER") changes: Int) {}
}
