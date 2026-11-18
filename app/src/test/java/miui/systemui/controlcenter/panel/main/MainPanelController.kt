package miui.systemui.controlcenter.panel.main

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import miui.systemui.util.CommonUtils

class MainPanelController(context: Context) {
    @JvmField var useSeparatedPanels: Boolean? = null
    @JvmField val horizontalMainPanel = LinearLayout(context)
    @JvmField val leftMainPanel = LinearLayout(context)
    @JvmField val rightMainPanel = LinearLayout(context)
    init {
        horizontalMainPanel.addView(leftMainPanel)
        horizontalMainPanel.addView(rightMainPanel)
    }
    fun setUseSeparatedPanels(value: Boolean?) {
        useSeparatedPanels = value
        if (value == false) horizontalMainPanel.removeView(leftMainPanel)
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
